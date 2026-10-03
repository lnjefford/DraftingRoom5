package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayVisualDataTest {
    @Test fun weekStartsSundayAndUsesActualScheduledSessions() {
        val week = todayWeek(defaultAppDocument(), LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 9, 27), week.first().date)
        assertEquals(LocalDate.of(2026, 10, 3), week.last().date)
        assertEquals(listOf(0, 2, 1, 1, 1, 1, 1), week.map { it.planned })
        assertEquals(0, week.sumOf { it.completed })
    }

}
