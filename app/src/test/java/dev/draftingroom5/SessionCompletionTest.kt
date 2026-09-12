package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCompletionTest {
    @Test fun presentationUsesOnlyPersistedCompletionFacts() {
        val routine = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }
        val history = WorkoutHistoryEntry(
            id = "completed-session",
            occurrence = OccurrenceKey("schedule-forearm", LocalDate.of(2026, 9, 12)),
            snapshot = routine,
            startedAtMillis = 10,
            completedAtMillis = 20,
        )

        assertEquals(
            SessionCompletionPresentation(routine.name, "${routine.exercises.size} of ${routine.exercises.size}", "Progress saved"),
            sessionCompletionPresentation(history),
        )
    }
}
