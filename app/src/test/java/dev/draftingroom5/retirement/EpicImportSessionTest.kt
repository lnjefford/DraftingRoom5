package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.importer.*
import dev.draftingroom5.retirement.ui.epicWorkbookPickerMimeTypes
import org.junit.Assert.*
import org.junit.Test

class EpicImportSessionTest {
    @Test fun pickerAllowsProviderSpecificWorkbookMimeTypes() {
        val types = epicWorkbookPickerMimeTypes().toSet()
        assertTrue("application/vnd.ms-excel.sheet.macroenabled.12" in types)
        assertTrue("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" in types)
        assertTrue("application/octet-stream" in types)
        assertTrue("*/*" in types)
    }

    @Test fun reviewIsTransientAndConfirmedImportIsAtomicDurableAndIdempotent() {
        val dao = FakeRetirementDao(); val repo = RetirementRepository(dao); val session = EpicImportSession(repo)
        val initial = repo.load()
        val review = session.review(goldenWorkbook().inputStream(), initial)
        assertEquals(initial, repo.load()); assertEquals(0, dao.epicImportCount())
        val accepted = (session.confirm(review, NOW) as RetirementResult.Success).value
        assertEquals(1, dao.epicImportCount()); assertEquals(1L, accepted.generation)
        assertEquals(accepted, RetirementRepository(dao).load())
        val duplicate = session.review(goldenWorkbook().inputStream(), accepted)
        assertEquals(accepted, (session.confirm(duplicate, NOW.plusSeconds(2)) as RetirementResult.Success).value)
        assertEquals(1, dao.epicImportCount())
        assertTrue(session.confirm(review, NOW) is RetirementResult.Conflict)
        val encoded = RetirementCodec.encode(accepted)
        listOf("sourceUri", "filename", "<worksheet", "<f>", "workbookBytes").forEach { assertFalse(encoded.contains(it)) }
        assertEquals(55_000L, AssetAggregator.totals(accepted).tracked.cents)
    }
    @Test fun badCancelledStaleAndFailedWritesPreserveLastGoodDataAndFreshness() {
        val dao = FakeRetirementDao(); val repo = RetirementRepository(dao); val session = EpicImportSession(repo)
        val review = session.review(goldenWorkbook().inputStream(), repo.load())
        val accepted = (session.confirm(review, NOW) as RetirementResult.Success).value
        assertThrows(WorkbookRejected::class.java) { session.review("truncated".byteInputStream(), accepted) }
        assertThrows(WorkbookRejected::class.java) { session.review(goldenWorkbook().inputStream(), accepted) { true } }
        assertEquals(accepted, repo.load())
        val changed = rewriteWorkbook(goldenWorkbook("values.xlsm")) { entries ->
            fun scale(path: String, addresses: Set<String>) {
                entries[path] = entries.getValue(path).replace(Regex("<c r=\"([^\"]+)\"[^>]*>.*?</c>")) { cell ->
                    if (cell.groupValues[1] !in addresses) cell.value else cell.value.replace(Regex("<v>([^<]+)</v>")) { value ->
                        "<v>${java.math.BigDecimal(value.groupValues[1]).multiply(java.math.BigDecimal(2)).toPlainString()}</v>"
                    }
                }
            }
            scale("xl/worksheets/sheet1.xml", setOf("E6") + listOf(12, 19).flatMap { row -> listOf("F", "H", "I", "J", "L", "M").map { "$it$row" } })
            scale("xl/worksheets/sheet2.xml", (9..13).map { "C$it" }.toSet())
            scale("xl/worksheets/sheet3.xml", listOf(23, 24, 31, 36, 37, 46, 47, 48, 49, 51).map { "C$it" }.toSet())
        }
        val replacement = session.review(changed.inputStream(), accepted)
        dao.failCommit = true
        assertThrows(IllegalStateException::class.java) { session.confirm(replacement, NOW.plusSeconds(3)) }
        assertEquals(accepted, repo.load()); assertEquals(1, dao.epicImportCount())
        dao.failCommit = false
        val failed = (session.rejected(accepted, NOW.plusSeconds(4)) as RetirementResult.Success).value
        assertEquals(accepted.epicImports, failed.epicImports); assertEquals(accepted.activeEpicImportId, failed.activeEpicImportId)
        assertEquals(NOW, failed.importMetadata.single().lastAcceptedAt)
        assertEquals(ImportStatus.NEEDS_ATTENTION, failed.importMetadata.single().status)
        assertTrue(session.confirm(replacement, NOW.plusSeconds(5)) is RetirementResult.Conflict)
        val fresh = session.review(changed.inputStream(), failed)
        val replaced = (session.confirm(fresh, NOW.plusSeconds(6)) as RetirementResult.Success).value
        assertEquals(fresh.candidate.workbook, replaced.epicImports.single { it.id == replaced.activeEpicImportId }.workbook)
        assertEquals(accepted.epicImports.single(), replaced.epicImports.first())
        assertEquals(110_000L, AssetAggregator.totals(replaced).tracked.cents)
        assertEquals(accepted.activeEpicImportId, replaced.epicImports.last().replacesImportId)
        assertEquals(2, dao.epicImportCount()); assertEquals(ImportStatus.ACCEPTED, replaced.importMetadata.single().status)
    }
    @Test fun twoReviewsCannotPublishMixedGenerations() {
        val dao = FakeRetirementDao(); val repo = RetirementRepository(dao); val initial = repo.load()
        val first = EpicImportSession(repo); val second = EpicImportSession(RetirementRepository(dao))
        val a = first.review(goldenWorkbook().inputStream(), initial)
        val b = second.review(goldenWorkbook("values.xlsm").inputStream(), initial)
        val gate = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<RetirementResult<RetirementState>>())
        val threads = listOf(Thread { gate.await(); results += first.confirm(a, NOW) }, Thread { gate.await(); results += second.confirm(b, NOW) })
        threads.forEach(Thread::start); gate.countDown(); threads.forEach(Thread::join)
        assertEquals(1, results.count { it is RetirementResult.Success }); assertEquals(1, results.count { it is RetirementResult.Conflict })
        assertEquals(1, dao.epicImportCount()); assertEquals(1L, repo.load().generation)
    }
}
