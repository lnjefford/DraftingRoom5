package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthTrendSupportTest {
    private val utc = ZoneId.of("UTC")

    @Test fun missingDaysRemainVisibleAsGaps() {
        val trend = dailyHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-09-01T08:00:00Z"), 180.0)),
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-03"),
            zoneId = utc,
        )

        assertEquals(3, trend.size)
        assertEquals(180.0, trend[0].value!!, 0.0)
        assertNull(trend[1].value)
        assertNull(trend[2].value)
    }

    @Test fun latestRecordWinsWhenADayHasMultipleValues() {
        val trend = dailyHealthTrend(
            samples = listOf(
                TimedHealthValue(Instant.parse("2026-09-02T08:00:00Z"), 180.0),
                TimedHealthValue(Instant.parse("2026-09-02T20:00:00Z"), 179.2),
            ),
            startDate = LocalDate.parse("2026-09-02"),
            endDate = LocalDate.parse("2026-09-02"),
            zoneId = utc,
        )

        assertEquals(179.2, trend.single().value!!, 0.0)
    }

    @Test fun conversionCanBeAppliedBeforeDailyAggregation() {
        val kilograms = 81.0
        val trend = dailyHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-09-02T08:00:00Z"), kilograms.toPounds())),
            startDate = LocalDate.parse("2026-09-02"),
            endDate = LocalDate.parse("2026-09-02"),
            zoneId = utc,
        )

        assertEquals(178.574, trend.single().value!!, 0.001)
    }

    @Test fun samplesOutsideTheRangeAreIgnored() {
        val trend = dailyHealthTrend(
            samples = listOf(TimedHealthValue(Instant.parse("2026-08-31T23:59:00Z"), 180.0)),
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-01"),
            zoneId = utc,
        )

        assertNull(trend.single().value)
    }
}
