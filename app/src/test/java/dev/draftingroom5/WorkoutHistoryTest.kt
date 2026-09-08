package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkoutHistoryTest {
    @Test
    fun workoutHistoryRoundTripPreservesCompletionDetails() {
        val entries = listOf(
            WorkoutHistoryEntry("one", "schedule-one", "Workout one", Destination.FITBOD, 1_780_272_000_000L),
            WorkoutHistoryEntry("two", "schedule-two", "Workout two", Destination.CUSTOM, 1_780_358_400_000L),
        )

        assertEquals(entries, decodeWorkoutHistory(encodeWorkoutHistory(entries)))
    }

    @Test
    fun completedScheduleIdsOnlyReturnsTheRequestedLocalDay() {
        val entries = listOf(
            WorkoutHistoryEntry("one", "schedule-one", "Workout one", Destination.FITBOD, 1_780_272_000_000L),
            WorkoutHistoryEntry("two", "schedule-two", "Workout two", Destination.CUSTOM, 1_780_358_400_000L),
        )

        assertEquals(
            listOf("schedule-one"),
            completedScheduleIdsForDate(entries, LocalDate.of(2026, 6, 1), ZoneId.of("UTC")),
        )
    }
}
