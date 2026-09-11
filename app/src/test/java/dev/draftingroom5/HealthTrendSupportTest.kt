package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthTrendSupportTest {
    private val utc = ZoneId.of("UTC")

    @Test fun measurementsContainOnlyRecordedPoints() {
        val trend = measurementHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-09-01T08:00:00Z"), 180.0)),
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-03"),
            zoneId = utc,
        )

        assertEquals(1, trend.size)
        assertEquals(180.0, trend[0].value!!, 0.0)


    }

    @Test fun everyReadingSurvivesWithinTheSameDay() {
        val trend = measurementHealthTrend(
            samples = listOf(
                TimedHealthValue(Instant.parse("2026-09-02T08:00:00Z"), 180.0),
                TimedHealthValue(Instant.parse("2026-09-02T20:00:00Z"), 179.2),
            ),
            startDate = LocalDate.parse("2026-09-02"),
            endDate = LocalDate.parse("2026-09-02"),
            zoneId = utc,
        )

        assertEquals(listOf(180.0, 179.2), trend.map { it.value })
    }

    @Test fun conversionCanBeAppliedBeforeDailyAggregation() {
        val kilograms = 81.0
        val trend = measurementHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-09-02T08:00:00Z"), kilograms.toPounds())),
            startDate = LocalDate.parse("2026-09-02"),
            endDate = LocalDate.parse("2026-09-02"),
            zoneId = utc,
        )

        assertEquals(178.574, trend.single().value!!, 0.001)
    }

    @Test fun samplesOutsideTheRangeAreIgnored() {
        val trend = measurementHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-08-31T23:59:00Z"), 180.0)),
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-01"),
            zoneId = utc,
        )

        assertEquals(emptyList<HealthTrendPoint>(), trend)
    }

    @Test fun dailyTotalsIncludeZeroDaysAndSumRecordedValues() {
        val trend = dailyHealthTotals(
            samples = listOf(
                TimedHealthValue(Instant.parse("2026-09-02T08:00:00Z"), 1.2),
                TimedHealthValue(Instant.parse("2026-09-02T20:00:00Z"), 0.8),
            ),
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-03"),
            zoneId = utc,
        )

        assertEquals(listOf(0.0, 2.0, 0.0), trend.map { it.value })
    }

    @Test fun trendDirectionComparesEndpoints() {
        val dates = (1..4).map { LocalDate.of(2026, 9, it) }

        assertEquals(HealthTrendDirection.UP, healthTrendDirection(dates.mapIndexed { index, date -> HealthTrendPoint(date, index.toDouble()) }))
        assertEquals(HealthTrendDirection.DOWN, healthTrendDirection(dates.mapIndexed { index, date -> HealthTrendPoint(date, (4 - index).toDouble()) }))
        assertEquals(HealthTrendDirection.NEUTRAL, healthTrendDirection(dates.map { HealthTrendPoint(it, 10.0) }))
        assertEquals(HealthTrendDirection.NEUTRAL, healthTrendDirection(listOf(HealthTrendPoint(dates.first(), null))))
    }

    @Test fun summaryReportsHighAverageLowAndEndpoints() {
        val points = listOf(
            HealthTrendPoint(LocalDate.of(2026, 9, 1), 10.0),
            HealthTrendPoint(LocalDate.of(2026, 9, 2), null),
            HealthTrendPoint(LocalDate.of(2026, 9, 3), 14.0),
            HealthTrendPoint(LocalDate.of(2026, 9, 4), 12.0),
        )

        val summary = healthTrendSummary(points)!!
        assertEquals(14.0, summary.high, 0.001)
        assertEquals(12.0, summary.average, 0.001)
        assertEquals(10.0, summary.low, 0.001)
        assertEquals(10.0, summary.first, 0.001)
        assertEquals(12.0, summary.last, 0.001)
        assertNull(healthTrendSummary(emptyList()))
    }
}
