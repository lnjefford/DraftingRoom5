package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.importer.*
import dev.draftingroom5.retirement.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.*
import java.time.LocalDate
import org.json.JSONObject

internal fun goldenWorkbook(name: String = "cached.xlsm"): ByteArray {
    val path = "docs/design/retirement-workspace/parity/shareworks/$name"
    return sequenceOf(File(path), File("../$path")).first { it.isFile }.readBytes()
}
internal fun rewriteWorkbook(bytes: ByteArray = goldenWorkbook(), change: (MutableMap<String, String>) -> Unit): ByteArray {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val e = zip.nextEntry ?: break; entries[e.name] = zip.readBytes().toString(Charsets.UTF_8) } }
    change(entries)
    return ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> entries.forEach { (name, body) ->
        zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
    } } }.toByteArray()
}

class ShareworksImporterTest {
    private fun parse(bytes: ByteArray = goldenWorkbook()) = ShareworksImporter().parse(bytes.inputStream())
    private fun rejected(bytes: ByteArray, reason: WorkbookFailure? = null) {
        val error = assertThrows(WorkbookRejected::class.java) { parse(bytes) }
        reason?.let { assertEquals(it, error.reason) }
        assertFalse(error.toString().contains("Synthetic Class"))
        assertNull(error.cause)
    }
    @Test fun cachedAndLiteralArchivesMatchEverySavedFieldAndParityGolden() {
        val cached = parse(); val values = parse(goldenWorkbook("values.xlsm"))
        assertEquals(values.workbook, cached.workbook)
        val xlsx = parse(rewriteWorkbook { entries ->
            entries["[Content_Types].xml"] = entries.getValue("[Content_Types].xml").replace(
                "application/vnd.ms-excel.sheet.macroEnabled.main+xml",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
            )
        })
        assertEquals(cached.workbook, xlsx.workbook)
        val b = cached.workbook
        assertEquals(Money(1000), b.sharePrice)
        assertEquals(EpicTotals("100", "60", Money(60000), "40", Money(40000), Money(5000), Money(55000), Money(60000), Money(100000)), b.totals)
        assertEquals(listOf(EpicBreakdown("Synthetic Class A", b.totals)), b.breakdown)
        assertEquals(EpicProjection(LocalDate.parse("2030-01-01"), "0.1", "0.2", "Yes", Money(120000), Money(10000), Money(110000), Money(22000), Money(88000)), b.projection)
        assertEquals(listOf(EpicYear(2026, "10", Money(60000), Money(40000), Money(100000), Money(5000), Money(95000), Money(50000), Money(45000), Money(9000), Money(0), Money(86000))), b.years)
        assertEquals(7.0710678118654755, b.historicalVolatilityPct!!.toDouble(), 1e-12)
        assertTrue(cached.digest.matches(Regex("[0-9a-f]{64}")))
        assertEquals("ValidatedEpicCandidate([private])", cached.toString())
    }
    @Test fun requiredCellsSheetsAndFormulaCachesFailClosed() {
        listOf<(MutableMap<String,String>) -> Unit>(
            { it.remove("xl/worksheets/sheet1.xml") },
            { it["xl/workbook.xml"] = it.getValue("xl/workbook.xml").replace("2026 Consolidated Statement", "2027 Consolidated Statement") },
            { it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replace("<c r=\"E6\" t=\"n\"><f>10</f><v>10</v></c>", "") },
            { it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replaceFirst("<v>10</v>", "<v/>") },
            { it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replaceFirst("<v>10</v>", "<v>NaN</v>") },
            { it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replace("<c r=\"E19\" t=\"n\"><f>60</f><v>60</v>", "<c r=\"E19\" t=\"n\"><f>60</f><v>59</v>") },
            { it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replace("<c r=\"C19\" t=\"n\"><f>100</f><v>100</v></c>", "<c r=\"C19\" t=\"inlineStr\"><is><t>N/A</t></is></c>") },
            { it["xl/worksheets/sheet2.xml"] = it.getValue("xl/worksheets/sheet2.xml").replace("2030-01-01", "2030-02-30") },
            { it["xl/worksheets/sheet3.xml"] = it.getValue("xl/worksheets/sheet3.xml").replace("</sheetData>", "<row r=\"15\"><c r=\"D15\"><v>2026</v></c></row></sheetData>") },
            { it["xl/worksheets/sheet4.xml"] = it.getValue("xl/worksheets/sheet4.xml").replace("<v>0.2</v>", "<v/>") },
        ).forEach { rejected(rewriteWorkbook(change = it)) }
    }
    @Test fun recalcIndicatorsDoNotHideValidSavedResults() {
        val expected = parse().workbook
        listOf("fullCalcOnLoad=\"0\"" to "fullCalcOnLoad=\"1\"", "forceFullCalc=\"0\"" to "forceFullCalc=\"true\"", "calcMode=\"auto\"" to "calcMode=\"manual\"", "calcCompleted=\"1\"" to "calcCompleted=\"0\"").forEach { (a,b) ->
            assertEquals(expected, parse(rewriteWorkbook { it["xl/workbook.xml"] = it.getValue("xl/workbook.xml").replace(a,b) }).workbook)
        }
        assertEquals(expected, parse(rewriteWorkbook {
            it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replaceFirst("<f>", "<f ca=\"1\">")
        }).workbook)
        assertEquals(expected, parse(rewriteWorkbook {
            it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replaceFirst("t=\"n\"><f>", "t=\"str\"><f>")
        }).workbook)
        rejected(rewriteWorkbook {
            it["xl/worksheets/sheet1.xml"] = it.getValue("xl/worksheets/sheet1.xml").replaceFirst("<v>10</v>", "<v/>")
        }, WorkbookFailure.RECALCULATE)
    }
    @Test fun optionalAbsentHistoryAndAnnualSheetsStayUnavailable() {
        val b = parse(rewriteWorkbook { entries ->
            entries["xl/workbook.xml"] = entries.getValue("xl/workbook.xml").replace(Regex("<sheet name=\"(?:Detailed Projections|Class B Share Price History)\"[^>]*/>"), "")
        }).workbook
        assertTrue(b.years.isEmpty()); assertNull(b.historicalVolatilityPct)
        assertTrue(epicDateNotice(b, LocalDate.parse("2030-01-01"))!!.contains("no annual projection"))
        assertTrue(epicDateNotice(b, LocalDate.parse("2031-01-01"))!!.contains("no annual projection"))
    }
    @Test fun malformedAndHostileArchivesNeverEscapeToFilesystemOrNetwork() {
        rejected(goldenWorkbook().dropLast(1).toByteArray())
        rejected("not a workbook".toByteArray())
        rejected(rewriteWorkbook { it["../escape.xml"] = "bad" })
        rejected(rewriteWorkbook { it["xl/worksheets/bomb.xml"] = "x".repeat(1_000_000) }, WorkbookFailure.TOO_LARGE)
        rejected(rewriteWorkbook { it["xl/workbook.xml"] = "<!DOCTYPE workbook [<!ENTITY xxe SYSTEM 'file:///private'>]>" + it.getValue("xl/workbook.xml") })
        rejected(rewriteWorkbook { it["xl/_rels/workbook.xml.rels"] = it.getValue("xl/_rels/workbook.xml.rels").replace("Target=", "TargetMode=\"External\" Target=") })
        rejected(rewriteWorkbook { it["[Content_Types].xml"] = it.getValue("[Content_Types].xml").replace("application/vnd.ms-excel.sheet.macroEnabled.main+xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml") }, WorkbookFailure.UNSUPPORTED)
        val encrypted = goldenWorkbook().clone()
        for (i in 0..encrypted.size-10) if (encrypted[i] == 0x50.toByte() && encrypted[i+1] == 0x4b.toByte() && encrypted[i+2] == 1.toByte() && encrypted[i+3] == 2.toByte()) encrypted[i+8] = 1
        rejected(encrypted)
    }
    @Test fun inputIsBoundedClosedAndCancellationDoesNotLeakSource() {
        var closed = false
        val huge = object : InputStream() { var remaining = ShareworksImporter.MAX_COMPRESSED + 1
            override fun read() = if (remaining-- > 0) 0 else -1
            override fun close() { closed = true }
        }
        assertEquals(WorkbookFailure.TOO_LARGE, assertThrows(WorkbookRejected::class.java) { ShareworksImporter().parse(huge) }.reason)
        assertTrue(closed)
        assertEquals(WorkbookFailure.CANCELLED, assertThrows(WorkbookRejected::class.java) { ShareworksImporter().parse(goldenWorkbook().inputStream()) { true } }.reason)
    }
    @Test fun exactDecimalCentsAndDateSystemsAreSupported() {
        val b = parse(rewriteWorkbook { e ->
            e["xl/worksheets/sheet1.xml"] = e.getValue("xl/worksheets/sheet1.xml").replaceFirst("<v>10</v>", "<v>1.0005E1</v>")
            e["xl/worksheets/sheet2.xml"] = e.getValue("xl/worksheets/sheet2.xml").replace("<c r=\"C38\" t=\"inlineStr\"><is><t>2030-01-01</t></is></c>", "<c r=\"C38\"><v>47484</v></c>")
            e["xl/worksheets/sheet4.xml"] = e.getValue("xl/worksheets/sheet4.xml").replaceFirst("<v>0.2</v>", "<v>0.200000000000000000</v>")
        }).workbook
        assertEquals(Money(1001), b.sharePrice); assertEquals(LocalDate.parse("2030-01-01"), b.projection.date)
    }

    @Test fun annualCoverageDoesNotRequireTheWorkbookDateToMatchTheRetirementDay() {
        val b = parse().workbook
        assertNull(epicDateNotice(b, LocalDate.parse("2026-12-31")))
    }

    @Test fun sarRowsAndWorkbookSpecificAfterTaxFormulasAreSupported() {
        val row = """<row r="18"><c r="B18" t="inlineStr"><is><t>Synthetic SAR</t></is></c><c r="C18" t="inlineStr"><is><t>N/A</t></is></c><c r="E18" t="n"><v>0</v></c><c r="F18" t="n"><v>0</v></c><c r="G18" t="n"><v>0</v></c><c r="H18" t="n"/><c r="I18" t="inlineStr"><is><t>N/A</t></is></c><c r="J18" t="n"><v>0</v></c><c r="L18" t="n"><v>0</v></c><c r="M18" t="n"><v>0</v></c></row>"""
        val b = parse(rewriteWorkbook { e ->
            e["xl/worksheets/sheet1.xml"] = e.getValue("xl/worksheets/sheet1.xml").replace("<row r=\"19\">", row + "<row r=\"19\">")
            e["xl/worksheets/sheet3.xml"] = e.getValue("xl/worksheets/sheet3.xml")
                .replace("<c r=\"C51\" t=\"n\"><f>860</f><v>860</v></c>", "<c r=\"C51\" t=\"n\"><f>870</f><v>870</v></c>")
        }).workbook
        assertEquals(EpicTotals("N/A", "0", Money(0), "0", Money(0), Money(0), Money(0), Money(0), Money(0)), b.breakdown.last().totals)
        assertEquals(Money(87000), b.years.single().afterTax)
    }

    @Test fun sharedStringsAnd1904DatesRetainReferenceValues() {
        val changed = rewriteWorkbook { e ->
            e["xl/sharedStrings.xml"] = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><r><t>Synthetic </t></r><r><t>Class A</t></r></si><si><t>Yes</t></si></sst>"
            e["xl/worksheets/sheet1.xml"] = e.getValue("xl/worksheets/sheet1.xml").replace("<c r=\"B12\" t=\"inlineStr\"><is><t>Synthetic Class A</t></is></c>", "<c r=\"B12\" t=\"s\"><v>0</v></c>")
            e["xl/worksheets/sheet2.xml"] = e.getValue("xl/worksheets/sheet2.xml")
                .replace("<c r=\"C44\" t=\"inlineStr\"><is><t>Yes</t></is></c>", "<c r=\"C44\" t=\"s\"><v>1</v></c>")
                .replace("<c r=\"C38\" t=\"inlineStr\"><is><t>2030-01-01</t></is></c>", "<c r=\"C38\"><v>46022</v></c>")
            e["xl/workbook.xml"] = e.getValue("xl/workbook.xml").replace("<sheets>", "<workbookPr date1904=\"1\"/><sheets>")
        }
        assertEquals(parse().workbook, parse(changed).workbook)
    }

    @Test fun duplicateEntriesBadCrcAndPartialAnnualColumnsAreRejected() {
        val duplicate = rewriteWorkbook { it["xl/worksheets/sheet5.xml"] = "<unused/>" }
        val from = "xl/worksheets/sheet5.xml".toByteArray(); val to = "xl/worksheets/sheet1.xml".toByteArray()
        for (i in 0..duplicate.size-from.size) if (from.indices.all { duplicate[i+it] == from[it] }) to.copyInto(duplicate, i)
        rejected(duplicate)
        val crc = goldenWorkbook().clone()
        val central = (0..crc.size-20).first { crc[it] == 0x50.toByte() && crc[it+1] == 0x4b.toByte() && crc[it+2] == 1.toByte() && crc[it+3] == 2.toByte() }
        crc[central+16] = (crc[central+16].toInt() xor 1).toByte(); rejected(crc)
        rejected(rewriteWorkbook { it["xl/worksheets/sheet3.xml"] = it.getValue("xl/worksheets/sheet3.xml").replace("<c r=\"C15\" t=\"n\"><f>2026</f><v>2026</v></c>", "") })
        rejected(rewriteWorkbook { it["xl/worksheets/sheet3.xml"] = it.getValue("xl/worksheets/sheet3.xml").replace("r=\"C", "r=\"ZZ") })
    }
}
