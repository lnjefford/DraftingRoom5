package dev.draftingroom5.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StandTimerTest {
    @Test fun countsDownFromResetAndStopsAtZero() {
        val reset = 1_000_000L
        assertEquals(30, standMinutesRemaining(reset, reset))
        assertEquals(8, standMinutesRemaining(reset + 22 * 60_000L, reset))
        assertEquals(1, standMinutesRemaining(reset + 29 * 60_000L + 1, reset))
        assertEquals(0, standMinutesRemaining(reset + 30 * 60_000L, reset))
        assertEquals(0, standMinutesRemaining(reset + 90 * 60_000L, reset))
    }

    @Test fun defaultScheduleRunsOnlyWeekdayBusinessHours() {
        val schedule = StandSchedule()
        assertEquals(false, schedule.activeAt(LocalDateTime.of(2026, 11, 1, 10, 0))) // Sunday
        assertEquals(false, schedule.activeAt(LocalDateTime.of(2026, 11, 2, 8, 59)))
        assertEquals(true, schedule.activeAt(LocalDateTime.of(2026, 11, 2, 9, 0)))
        assertEquals(true, schedule.activeAt(LocalDateTime.of(2026, 11, 2, 16, 59)))
        assertEquals(false, schedule.activeAt(LocalDateTime.of(2026, 11, 2, 17, 0)))
    }

    @Test fun customDaysAndHoursAndNewDayRestart() {
        val zone = ZoneId.of("America/Chicago")
        val schedule = StandSchedule(days = 1 shl 5, startHour = 10, endHour = 14)
        val saturday = LocalDateTime.of(2026, 11, 7, 10, 5)
        assertEquals(true, schedule.activeAt(saturday))
        assertEquals(false, schedule.activeAt(saturday.minusDays(1)))
        val now = saturday.atZone(zone).toInstant().toEpochMilli()
        assertEquals(25, standMinutesRemaining(now, schedule.effectiveResetAt(now, now - 86_400_000, zone)))
    }
}
