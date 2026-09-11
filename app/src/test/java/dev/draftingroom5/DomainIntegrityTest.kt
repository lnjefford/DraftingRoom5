package dev.draftingroom5

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

internal fun partialFixture(): GuidedSession {
    val routine = defaultTrainingPlan().routines.last()
    return GuidedSession("session", OccurrenceKey("schedule-forearm", LocalDate.of(2026, 9, 12)),
        routine.id, routine, routine.exercises.first().id,
        routine.exercises.associate { it.id to 0 }, SessionTimer(), 1, 2, 0)
}

internal fun timerFixture(session: GuidedSession = partialFixture()) = SessionTimer(
    TimerPhase.READY, "run", session.focusedExerciseId, 1, 1, 10_000, 30_000, 0, 0)

class DomainIntegrityTest {
    @Test fun partialAndHistoryRoundTripAfterScheduleDeletionAndRoutineEdit() {
        val partial = partialFixture()
        val history = WorkoutHistoryEntry("finished", partial.occurrence.copy(scheduledDate = partial.occurrence.scheduledDate.minusWeeks(1)),
            partial.snapshot, 1, 3)
        val document = defaultAppDocument().copy(
            plan = defaultTrainingPlan().let { plan -> plan.copy(schedule = emptyList(), routines = plan.routines.map {
                if (it.id == partial.routineId) it.copy(revision = 2, name = "Renamed") else it
            }) }, partialSessions = listOf(partial), history = listOf(history))
        assertEquals(document, decodeAppDocument(encodeAppDocument(document)))
    }

    @Test fun invalidPartialOriginAndCompletedExerciseTimerAreRejected() {
        val session = partialFixture()
        val invalid = listOf(session.copy(occurrence = session.occurrence.copy(scheduleEntryId = "")),
            session.copy(completedSets = session.completedSets + (session.focusedExerciseId to 3), timer = timerFixture().copy(setNumber = 4)),
            session.copy(timer = timerFixture().copy(readyDeadlineElapsedMillis = Long.MAX_VALUE - 10,
                activeDeadlineElapsedMillis = Long.MIN_VALUE + 19_989)))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(defaultAppDocument().copy(partialSessions = listOf(it)))
        } }
    }

    @Test fun duplicateOccurrencesAndPartialHistoryOverlapAreRejected() {
        val session = partialFixture()
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(defaultAppDocument().copy(partialSessions = listOf(session, session.copy(id = "other"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(defaultAppDocument().copy(partialSessions = listOf(session),
                history = listOf(WorkoutHistoryEntry("finished", session.occurrence, session.snapshot, 1, 2))))
        }
    }
}
