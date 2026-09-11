package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WorkoutHistoryTest {
    @Test fun completionUsesScheduledOccurrenceDateNotCompletionClockDate() {
        val routine = defaultTrainingPlan().routines.single { it.execution == RoutineExecution.GUIDED }
        val entries = listOf(
            WorkoutHistoryEntry("one", OccurrenceKey("schedule-one", LocalDate.of(2026, 6, 1)), routine, 1, 2),
            WorkoutHistoryEntry("two", OccurrenceKey("schedule-two", LocalDate.of(2026, 6, 2)), routine, 3, 4),
        )
        assertEquals(listOf("schedule-one"), completedScheduleIdsForDate(entries, LocalDate.of(2026, 6, 1)))
    }
}
