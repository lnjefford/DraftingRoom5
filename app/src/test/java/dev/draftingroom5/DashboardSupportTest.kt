package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSupportTest {
    @Test fun todaySelectionFollowsMidnightAndWeekRollover() {
        val sunday = LocalDate.of(2026, 9, 13)
        assertEquals(saturday, resolveDashboardDate(saturday.minusDays(1), saturday.minusDays(1), saturday))
        assertEquals(sunday.plusDays(1), resolveDashboardDate(saturday, sunday, sunday.plusDays(1)))
    }

    @Test fun explicitlySelectedDaySurvivesReturningWithinTheSameWeek() {
        val friday = saturday.minusDays(1)
        assertEquals(friday, resolveDashboardDate(friday, saturday, saturday.plusDays(1)))
        assertEquals(friday, resolveDashboardDate(friday, saturday, saturday))
    }

    private val saturday = LocalDate.of(2026, 9, 12)
    private val plan = defaultTrainingPlan()
    private val entry = plan.forDay(saturday.dayOfWeek).single()
    private val routine = plan.routineFor(entry)

    @Test
    fun linkedAndGuidedSessionsResolveIdentityAndActionFromRoutine() {
        val monday = LocalDate.of(2026, 9, 7)
        val sessions = dashboardSessions(plan, emptyList(), emptyList(), monday)

        assertEquals(2, sessions.size)
        assertEquals(listOf(RoutineExecution.LINKED_APP, RoutineExecution.LINKED_APP), sessions.map { it.routine.execution })
        assertEquals(listOf(SessionAction.START, SessionAction.START), sessions.map { it.action })
        assertEquals("FITBOD", linkedAppDisplayName(sessions.first().routine.appLink!!.packageName))
    }

    @Test
    fun partialGuidedOccurrenceResumesWithCompletedExerciseProgress() {
        val completedSets = routine.exercises.associate { exercise ->
            exercise.id to if (exercise == routine.exercises.first()) exercise.setCount else 0
        }
        val partial = GuidedSession(
            id = "partial", occurrence = OccurrenceKey(entry.id, saturday), routineId = routine.id,
            snapshot = routine, focusedExerciseId = routine.exercises[1].id, completedSets = completedSets,
            timer = SessionTimer(), startedAtMillis = 1, updatedAtMillis = 2, eventRevision = 1,
        )

        val session = dashboardSessions(plan, listOf(partial), emptyList(), saturday).single()

        assertEquals(SessionAction.RESUME, session.action)
        assertEquals("1 of 7 exercises complete", session.progressLabel)
        assertEquals("Resume Forearm & Grip Conditioning", session.accessibilityAction)
        val editedPlan = plan.copy(routines = plan.routines.map {
            if (it.id == routine.id) it.copy(exercises = it.exercises.take(2), revision = it.revision + 1) else it
        })
        assertEquals("1 of 7 exercises complete", dashboardSessions(editedPlan, listOf(partial), emptyList(), saturday).single().progressLabel)
    }

    @Test
    fun completionWinsOverPartialForTheSameOccurrence() {
        val partial = GuidedSession(
            "partial", OccurrenceKey(entry.id, saturday), routine.id, routine, routine.exercises.first().id,
            routine.exercises.associate { it.id to 0 }, SessionTimer(), 1, 2, 1,
        )
        val completed = WorkoutHistoryEntry("done", partial.occurrence, routine, 1, 3)

        val session = dashboardSessions(plan, listOf(partial), listOf(completed), saturday).single()

        assertEquals(SessionAction.DONE, session.action)
        assertNull(session.progressLabel)
    }

    @Test
    fun selectedDateControlsOccurrencesAndRecoveryDays() {
        val week = dashboardWeek(LocalDate.of(2026, 9, 10))

        assertEquals(LocalDate.of(2026, 9, 7), week.first())
        assertEquals(LocalDate.of(2026, 9, 13), week.last())
        assertEquals(0, dashboardSessions(plan, emptyList(), emptyList(), week.last()).size)
    }

    @Test
    fun removedAndOffDateOccurrencesRemainAvailableAsSavedSessions() {
        val older = partialFixture().copy(updatedAtMillis = 3)
        val removedPlan = plan.removeScheduleEntry(older.occurrence.scheduleEntryId)
        val newer = older.copy(
            id = "newer",
            occurrence = OccurrenceKey("removed-entry", saturday.minusWeeks(1)),
            updatedAtMillis = 4,
        )

        val saved = savedDashboardSessions(removedPlan, listOf(older, newer), saturday)

        assertEquals(listOf(newer.occurrence.scheduledDate, older.occurrence.scheduledDate), saved.map { it.savedOriginDate })
        assertTrue(saved.all { it.action == SessionAction.RESUME })
        assertTrue(saved.all { it.routine == older.snapshot })
    }

    @Test
    fun selectedDateOccurrenceIsNotDuplicatedInSavedSessions() {
        val partial = partialFixture()

        assertTrue(savedDashboardSessions(plan, listOf(partial), saturday).isEmpty())
        assertEquals(1, dashboardSessions(plan, listOf(partial), emptyList(), saturday).size)
    }
}
