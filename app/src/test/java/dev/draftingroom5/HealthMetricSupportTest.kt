package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HealthMetricSupportTest {
    private val syncedAt = Instant.parse("2026-09-08T19:30:00Z")

    @Test fun currentDataShowsSyncTimeAndSource() {
        val metric = healthMetric(
            value = "180.0",
            outcome = HealthReadOutcome.SUCCESS,
            syncedAt = syncedAt,
            recordedAt = syncedAt.minus(Duration.ofHours(2)),
            source = "Withings",
        )

        assertEquals(HealthMetricState.CURRENT, metric.state)
        assertEquals("Synced 7:30 PM • Withings", metric.detail(ZoneOffset.UTC))
    }

    @Test fun oldRecordIsStaleButKeepsItsValue() {
        val metric = healthMetric(
            value = "180.0",
            outcome = HealthReadOutcome.SUCCESS,
            syncedAt = syncedAt,
            recordedAt = Instant.parse("2026-08-20T12:00:00Z"),
            source = "Withings",
        )

        assertEquals(HealthMetricState.STALE, metric.state)
        assertEquals("180.0", metric.value)
        assertTrue(metric.detail(ZoneOffset.UTC).contains("Stale • data from Aug 20"))
    }

    @Test fun successfulEmptyReadIsDifferentFromUnavailableRead() {
        val missing = healthMetric(null, HealthReadOutcome.MISSING, syncedAt)
        val unavailable = healthMetric(null, HealthReadOutcome.UNAVAILABLE, syncedAt)

        assertEquals(HealthMetricState.MISSING, missing.state)
        assertEquals("Synced 7:30 PM • No data found", missing.detail(ZoneOffset.UTC))
        assertEquals(HealthMetricState.UNAVAILABLE, unavailable.state)
        assertTrue(unavailable.detail(ZoneOffset.UTC).startsWith("Not synced"))
    }
}
