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

    @Test fun dashboardWindowIsAlwaysTheLatestThirtyCalendarDays() {
        val today = LocalDate.of(2026, 9, 12)
        HealthDateRange.entries.forEach { storedDetailRange ->
            val dashboard = dashboardHealthWindow(today)
            val detail = metricDetailHealthWindow(storedDetailRange)

            assertEquals(HealthDateRange.MONTH, dashboard.range)
            assertEquals(today, dashboard.fixedEndDate)
            assertEquals(today.minusDays(29), dashboard.startDate(null, today.minusYears(2), utc))
            assertEquals(storedDetailRange, detail.range)
            assertNull(detail.fixedEndDate)
        }
    }

    @Test fun dashboardTrendExcludesOlderAndFutureHistoryButPreservesGaps() {
        val today = LocalDate.of(2026, 9, 12)
        val points = (0L..31L).map { offset ->
            HealthTrendPoint(today.minusDays(offset), if (offset == 4L) null else offset.toDouble())
        } + HealthTrendPoint(today.plusDays(1), 99.0)

        val visible = dashboardHealthTrend(points, today)

        assertEquals(30, visible.size)
        assertEquals(today.minusDays(29), visible.first().date)
        assertEquals(today, visible.last().date)
        assertNull(visible.single { it.date == today.minusDays(4) }.value)
    }

    @Test fun dashboardWindowChangesAtCivilMidnightWhileDetailRangeStaysIndependent() {
        val date = LocalDate.of(2026, 12, 31)
        val before = dashboardHealthWindow(date)
        val after = dashboardHealthWindow(date.plusDays(1))
        org.junit.Assert.assertNotEquals(before, after)
        assertEquals(LocalDate.of(2027, 1, 1), after.endDate(null, date, utc))
        assertEquals(date.minusDays(28), after.startDate(null, date, utc))
        val reading = Instant.parse("2026-12-15T12:00:00Z")
        HealthDateRange.entries.forEach { range ->
            val detail = metricDetailHealthWindow(range)
            assertEquals(detail.startDate(reading, date, utc), detail.startDate(reading, date.plusDays(1), utc))
        }
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
