package dev.draftingroom5

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CoreShellAuditTest {
    private val date = LocalDate.of(2026, 9, 1)

    @Test fun endpointDirectionCannotContradictDelta() {
        val points = listOf(10.0, 100.0, 2.0, 11.0).mapIndexed { i, value -> HealthTrendPoint(date.plusDays(i.toLong()), value) }
        assertEquals(HealthTrendDirection.UP, healthTrendDirection(points))
        assertTrue(metricDeltaDescription(DashboardCard.WEIGHT, healthTrendSummary(points), HealthDateRange.WEEK, Locale.US).startsWith("Up 1.0"))
    }

    @Test fun oldReadingsAnchorTheRangeAndNonfiniteValuesAreExcluded() {
        val points = listOf(HealthTrendPoint(date, 180.0), HealthTrendPoint(date.plusDays(1), 182.0),
            HealthTrendPoint(date.plusDays(10), null), HealthTrendPoint(date.plusDays(11), Double.NaN))
        assertEquals(listOf(182.0), visibleMetricTrend(points.reversed(), HealthDateRange.DAY).map { it.value })
        assertEquals(181.0, healthTrendSummary(points)!!.average, 0.0)
        assertNotNull(metricChartDomain(points))
        assertEquals("No comparison available", metricDeltaDescription(DashboardCard.WEIGHT, null, HealthDateRange.DAY))
    }

    @Test fun measurementPointsRetainTimestampAndChronology() {
        val morning = Instant.parse("2026-09-01T08:00:00Z")
        val evening = morning.plusSeconds(3600)
        val points = measurementHealthTrend(listOf(TimedHealthValue(evening, 179.0), TimedHealthValue(morning, 180.0)), date, date, ZoneId.of("UTC"))
        assertEquals(listOf(morning, evening), points.map { it.recordedAt })
        assertEquals(HealthTrendDirection.DOWN, healthTrendDirection(points))
    }

    @Test fun chartSpacingUsesElapsedTimeInsteadOfReadingIndex() {
        val points = listOf(0L, 1L, 10L).map { HealthTrendPoint(date.plusDays(it), 180.0) }
        assertEquals(listOf(0f, .1f, 1f), metricChartFractions(points))
        assertEquals(listOf(.5f), metricChartFractions(points.take(1)))
    }

    @Test fun refreshFailureRetainsValueButRevocationClearsIt() {
        val time = Instant.parse("2026-09-01T08:00:00Z")
        val previous = healthMetric("180.0", HealthReadOutcome.SUCCESS, time, time)
        val failure = healthMetric(null, HealthReadOutcome.ERROR, time.plusSeconds(60))
        val retained = retainHealthMetricOnError(failure, previous)
        assertEquals(previous.value, retained.value)
        assertEquals(time, retained.syncedAt)
        assertNotNull(retained.refreshError)
        val revoked = retainHealthMetricOnError(HealthMetric(), previous)
        assertEquals("--", revoked.value)
        assertNull(revoked.recordedAt)
    }

    @Test fun yesterdayMeansPreviousCalendarDate() {
        assertEquals("Synced Sep 8", metricFreshness(Instant.parse("2026-09-08T23:59:00Z"),
            Instant.parse("2026-09-10T00:01:00Z"), ZoneId.of("UTC"), Locale.US))
    }

    @Test fun cachedUpdatesRequireSameIdentityAndAnIncreasingVersion() {
        validateUpdateIdentity("app", "app", 3, 2, setOf("key"), setOf("key"))
        fun rejected(block: () -> Unit) {
            try { block(); fail("Untrusted update accepted") } catch (_: IllegalStateException) { }
        }
        rejected { validateUpdateIdentity("other", "app", 3, 2, setOf("key"), setOf("key")) }
        rejected { validateUpdateIdentity("app", "app", 2, 2, setOf("key"), setOf("key")) }
        rejected { validateUpdateIdentity("app", "app", 3, 2, setOf("other"), setOf("key")) }
        rejected { validateUpdateIdentity<String>("app", "app", 3, 2, emptySet(), emptySet()) }
    }
}
