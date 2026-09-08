package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HealthDateRangeTest {
    @Test fun rangesIncludeTheCurrentDay() {
        val today = LocalDate.parse("2026-09-08")

        assertEquals(today, HealthDateRange.DAY.startDate(today))
        assertEquals(LocalDate.parse("2026-09-02"), HealthDateRange.WEEK.startDate(today))
        assertEquals(LocalDate.parse("2026-08-10"), HealthDateRange.MONTH.startDate(today))
        assertEquals(LocalDate.parse("2026-06-11"), HealthDateRange.THREE_MONTHS.startDate(today))
        assertEquals(LocalDate.parse("2025-09-09"), HealthDateRange.YEAR.startDate(today))
    }
}
