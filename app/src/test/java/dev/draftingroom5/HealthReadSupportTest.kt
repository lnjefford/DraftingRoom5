package dev.draftingroom5

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class HealthReadSupportTest {
    @Test fun missingPermissionDoesNotReadOrPreventOtherMetrics() = runBlocking {
        val denied = readHealthValue<Double>(false) { error("Must not read without permission") }
        val weight = readHealthValue(true) { 82.5 }
        assertNull(denied.value)
        assertTrue(denied.issue!!.contains("permission"))
        assertEquals(82.5, weight.value!!, 0.0)
    }

    @Test fun readFailureDoesNotDiscardOtherResults() = runBlocking {
        val weight = readHealthValue(true) { 82.5 }
        val failed = readHealthValue<Double>(true) { throw IOException("provider unavailable") }
        assertEquals(82.5, weight.value!!, 0.0)
        assertTrue(failed.issue!!.contains("Read failed"))
    }

    @Test fun emptyAndDeniedReadsHaveDifferentExplanations() = runBlocking {
        val empty = readHealthValue<Double>(true) { null }
        val denied = readHealthValue<Double>(true) { throw SecurityException() }
        assertTrue(empty.issue!!.contains("No readable records"))
        assertTrue(denied.issue!!.contains("denied access"))
    }

    @Test fun cancellationIsNotReportedAsMissingData() = runBlocking {
        try {
            readHealthValue<Double>(true) { throw CancellationException() }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun followsEmptyPagesAndSelectsLatestMeasurement() = runBlocking {
        val older = Instant.parse("2026-09-05T12:00:00Z")
        val newer = older.plusSeconds(3600)
        val tokens = mutableListOf<String?>()
        val result = latestHealthRecord<Instant>({ it }) { token ->
            tokens.add(token)
            if (token == null) HealthRecordPage(emptyList(), "next")
            else HealthRecordPage(listOf(older, newer), null)
        }
        assertEquals(newer, result)
        assertEquals(listOf(null, "next"), tokens)
    }

    @Test fun emptyFinalPageMeansNoData() = runBlocking {
        assertNull(latestHealthRecord<Instant>({ it }) { HealthRecordPage(emptyList(), null) })
    }

    @Test fun repeatedPageTokenDoesNotLoopForever() = runBlocking {
        try {
            latestHealthRecord<Instant>({ it }) { HealthRecordPage(emptyList(), "same") }
            fail("Repeated token should fail")
        } catch (_: IllegalStateException) { }
    }

    @Test fun selectsNewestAcrossMultipleNonEmptyPages() = runBlocking {
        val older = Instant.parse("2026-09-05T12:00:00Z")
        val newer = older.plusSeconds(3600)
        val result = latestHealthRecord<Instant>({ it }) { token ->
            if (token == null) HealthRecordPage(listOf(older), "next")
            else HealthRecordPage(listOf(newer), "")
        }
        assertEquals(newer, result)
    }

    @Test fun emptyStringTerminatesPaginationWithoutAnotherRequest() = runBlocking {
        var calls = 0
        assertNull(latestHealthRecord<Instant>({ it }) {
            calls++
            HealthRecordPage(emptyList(), "")
        })
        assertEquals(1, calls)
    }

    @Test fun emptyTrailingPageDoesNotEraseEarlierMeasurement() = runBlocking {
        val measurement = Instant.parse("2026-09-05T12:00:00Z")
        assertEquals(measurement, latestHealthRecord<Instant>({ it }) { token ->
            if (token == null) HealthRecordPage(listOf(measurement), "next")
            else HealthRecordPage(emptyList(), null)
        })
    }
}
