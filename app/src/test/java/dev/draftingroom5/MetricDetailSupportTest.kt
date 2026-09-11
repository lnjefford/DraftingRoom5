package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MetricDetailSupportTest {
    private val end = LocalDate.of(2026, 9, 10)

    @Test fun everyRangeUsesItsOwnBoundaryAndNewestPointAnchor() {
        val points = (0L..364L).map { HealthTrendPoint(end.minusDays(364L - it), it.toDouble()) }
        val expected = mapOf(
            HealthDateRange.DAY to 1,
            HealthDateRange.WEEK to 7,
            HealthDateRange.MONTH to 30,
            HealthDateRange.THREE_MONTHS to 90,
            HealthDateRange.YEAR to 365,
        )

        expected.forEach { (range, count) ->
            val visible = visibleMetricTrend(points, range)
            assertEquals(count, visible.size)
            assertEquals(end, visible.last().date)
        }
    }

    @Test fun rangeSummaryHandlesEmptySingleRepeatedAndIrregularData() {
        assertNull(healthTrendSummary(emptyList()))
        val single = healthTrendSummary(listOf(HealthTrendPoint(end, 12.0)))!!
        assertEquals(12.0, single.average, 0.0)

        val repeated = healthTrendSummary((0L..2L).map { HealthTrendPoint(end.minusDays(it), 7.0) })!!
        assertEquals(7.0, repeated.high, 0.0)
        assertEquals(7.0, repeated.low, 0.0)

        val irregular = healthTrendSummary(listOf(
            HealthTrendPoint(end.minusDays(9), 2.0),
            HealthTrendPoint(end.minusDays(2), null),
            HealthTrendPoint(end, 8.0),
        ))!!
        assertEquals(5.0, irregular.average, 0.0)
    }

    @Test fun chartDomainPadsValuesWithoutForcingZero() {
        val domain = metricChartDomain(listOf(
            HealthTrendPoint(end.minusDays(1), 180.0),
            HealthTrendPoint(end, 182.0),
        ))!!
        assertTrue(domain.minimum > 0.0)
        assertTrue(domain.minimum < 180.0)
        assertTrue(domain.maximum > 182.0)

        val flat = metricChartDomain(listOf(HealthTrendPoint(end, 20.0)))!!
        assertTrue(flat.minimum < 20.0)
        assertTrue(flat.maximum > 20.0)
        assertNull(metricChartDomain(emptyList()))
    }

    @Test fun formattingUsesMetricPrecisionAndSelectedRange() {
        assertEquals("12", formatMetricNumber(DashboardCard.WORKOUTS, 12.4, Locale.US))
        assertEquals("12.4", formatMetricNumber(DashboardCard.DISTANCE, 12.4, Locale.US))
        val summary = HealthTrendSummary(182.0, 181.0, 180.0, 182.0, 180.0)
        assertEquals("Down 2.0 lb over 7 days", metricDeltaDescription(DashboardCard.WEIGHT, summary, HealthDateRange.WEEK, Locale.US))
    }

    @Test fun chartAccessibilitySummarizesEndpointsDirectionAndRange() {
        val points = listOf(
            HealthTrendPoint(end.minusDays(1), 10.0),
            HealthTrendPoint(end, 12.0),
        )
        val description = metricChartDescription(DashboardCard.DISTANCE, points, Locale.US)
        assertTrue(description.contains("first 10.0 mi"))
        assertTrue(description.contains("last 12.0 mi"))
        assertTrue(description.contains("up"))
        assertTrue(description.contains("high 12.0, low 10.0"))
    }

    @Test fun freshnessDistinguishesTodayYesterdayAndOlderSyncs() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-09-10T18:00:00Z")
        assertEquals("Synced today", metricFreshness(Instant.parse("2026-09-10T08:00:00Z"), now, zone, Locale.US))
        assertEquals("Synced yesterday", metricFreshness(Instant.parse("2026-09-09T20:00:00Z"), now, zone, Locale.US))
        assertEquals("Synced Sep 7", metricFreshness(Instant.parse("2026-09-07T08:00:00Z"), now, zone, Locale.US))
    }
}
