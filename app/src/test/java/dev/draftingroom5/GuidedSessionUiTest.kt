package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSessionUiTest {
    @Test fun longestSupportedTimerKeepsEveryDigitWithoutChangingTheDuration() {
        val base = fixture()
        val longest = base.copy(snapshot = base.snapshot.copy(exercises = base.snapshot.exercises.map {
            it.copy(timerSeconds = Int.MAX_VALUE)
        }))
        assertEquals("35791394:07", guidedSessionPresentation(longest, 100_000).timer!!.display)
        assertEquals("Start timer", guidedSessionPresentation(longest, 100_000).timer!!.primaryLabel)
    }
    @Test fun progressCountsEverySetWithoutIntegerOverflowAndCompletedFocusCannotStartTimer() {
        val base = fixture()
        val partial = base.copy(completedSets = mapOf("A" to 1, "B" to 0, "C" to 0))
        assertEquals(0.25f, guidedSessionPresentation(partial, 100_000).setProgress)
        val full = base.copy(completedSets = mapOf("A" to 2, "B" to 0, "C" to 0))
        assertEquals(null, guidedSessionPresentation(full, 100_000).timer)
        val large = base.copy(
            snapshot = base.snapshot.copy(exercises = base.snapshot.exercises.map { it.copy(setCount = Int.MAX_VALUE) }),
            completedSets = base.completedSets.mapValues { Int.MAX_VALUE },
        )
        assertEquals(1f, guidedSessionPresentation(large, 100_000).setProgress)
    }
    @Test fun reopeningOccurrenceRestoresDurablyCommittedFocusAndSets() {
        val document = defaultAppDocument()
        val routine = document.plan.routines.first { it.execution == RoutineExecution.GUIDED }
        val entry = document.plan.schedule.first { it.routineId == routine.id }
        val storage = UiSessionStorage(encodeAppDocument(document))
        val repository = AppRepository(storage) { SessionClockSample(100_000, 200_000, 7) }
        repository.load()
        val lease = checkNotNull(repository.sessionLease())
        val occurrence = OccurrenceKey(entry.id, LocalDate.of(2026, 9, 12))
        val opened = repository.openGuidedSession(lease, occurrence, routine.id, routine.revision, "first") as SessionRepositoryResult.Partial
        val firstExercise = routine.exercises.first()
        val changed = repository.applySessionEvent(
            lease,
            opened.session.id,
            opened.session.eventRevision,
            SessionEvent.CompleteSet(firstExercise.id, 1),
        ) as SessionRepositoryResult.Partial

        val reopened = repository.openGuidedSession(lease, occurrence, routine.id, routine.revision, "ignored") as SessionRepositoryResult.Partial
        assertEquals(changed.session.id, reopened.session.id)
        assertEquals(1, reopened.session.completedSets.getValue(firstExercise.id))
        assertEquals(changed.session.focusedExerciseId, reopened.session.focusedExerciseId)

        var progressed = reopened.session
        for (setNumber in 2..firstExercise.setCount) {
            progressed = (repository.applySessionEvent(
                lease, progressed.id, progressed.eventRevision, SessionEvent.CompleteSet(firstExercise.id, setNumber),
            ) as SessionRepositoryResult.Partial).session
        }
        val recreatedRepository = AppRepository(storage) { SessionClockSample(100_000, 200_000, 7) }
        recreatedRepository.load()
        val recreated = recreatedRepository.openGuidedSession(
            checkNotNull(recreatedRepository.sessionLease()), occurrence, routine.id, routine.revision, "ignored",
        ) as SessionRepositoryResult.Partial
        val sections = guidedSessionPresentation(recreated.session, 100_000)
        assertEquals(firstExercise.setCount, recreated.session.completedSets.getValue(firstExercise.id))
        assertEquals(listOf(firstExercise.id), sections.completed.map(Exercise::id))
        assertFalse(sections.upcoming.any { it.id == firstExercise.id })
    }

    @Test fun timerPresentationKeepsReadinessDistinctAndGatesSetCompletion() {
        val base = fixture()
        val ready = base.copy(timer = timer(base, TimerPhase.READY, 110_000, 130_000, 0))
        val atStart = guidedSessionPresentation(ready, 100_000).timer!!
        assertEquals("Get ready", atStart.phaseLabel)
        assertEquals("10", atStart.display)
        assertTrue(atStart.faded)
        assertFalse(atStart.completeSetEnabled)
        assertEquals("Cancel countdown", atStart.cancelLabel)

        val lastReadySecond = guidedSessionPresentation(ready, 109_999).timer!!
        assertEquals("1", lastReadySecond.display)
        assertTrue(lastReadySecond.faded)

        val running = ready.copy(timer = ready.timer.copy(phase = TimerPhase.RUNNING, lastHandledCueOrdinal = 4))
        val active = guidedSessionPresentation(running, 110_000).timer!!
        assertEquals("Timer running", active.phaseLabel)
        assertEquals("00:20", active.display)
        assertFalse(active.faded)
        assertTrue(active.completeSetEnabled)
    }

    @Test fun idleFinishedAndUntimedControlsAreExplicit() {
        val idle = guidedSessionPresentation(fixture(), 100_000).timer!!
        assertEquals("Timer ready", idle.phaseLabel)
        assertEquals("00:20", idle.display)
        assertEquals("Start timer", idle.primaryLabel)

        val base = fixture()
        val finishedSession = base.copy(timer = timer(base, TimerPhase.FINISHED, 90_000, 100_000, 5))
        val finished = guidedSessionPresentation(finishedSession, 120_000).timer!!
        assertEquals("Timer complete", finished.phaseLabel)
        assertEquals("00:00", finished.display)
        assertEquals("Restart timer", finished.primaryLabel)
        assertTrue(finished.completeSetEnabled)

        val untimed = base.copy(focusedExerciseId = "B")
        assertEquals(null, guidedSessionPresentation(untimed, 100_000).timer)
    }

    @Test fun everyTimerPhaseExposesOnlyItsRelevantControlsWithoutChangingSetEligibility() {
        val base = fixture().copy(completedSets = mapOf("A" to 1, "B" to 0, "C" to 0))
        val idle = guidedSessionPresentation(base, 100_000).timer!!
        assertEquals("Start timer", idle.primaryLabel)
        assertEquals(null, idle.cancelLabel)
        assertTrue(idle.completeSetEnabled)

        val timed = timer(base, TimerPhase.READY, 110_000, 130_000, 0)
        val ready = guidedSessionPresentation(base.copy(timer = timed), 100_000).timer!!
        assertEquals(null, ready.primaryLabel)
        assertEquals("Cancel countdown", ready.cancelLabel)
        assertFalse(ready.completeSetEnabled)

        val running = guidedSessionPresentation(base.copy(timer = timed.copy(phase = TimerPhase.RUNNING)), 110_000).timer!!
        assertEquals(null, running.primaryLabel)
        assertEquals("Cancel timer", running.cancelLabel)
        assertTrue(running.completeSetEnabled)

        val finished = guidedSessionPresentation(base.copy(timer = timed.copy(phase = TimerPhase.FINISHED)), 130_000).timer!!
        assertEquals("Restart timer", finished.primaryLabel)
        assertEquals(null, finished.cancelLabel)
        assertTrue(finished.completeSetEnabled)
        assertEquals("00:00", finished.display)
    }

    @Test fun presentationReportsFocusProgressAndKeepsEveryOtherExerciseReachable() {
        val session = fixture().copy(
            focusedExerciseId = "B",
            completedSets = mapOf("A" to 2, "B" to 0, "C" to 1),
        )
        val state = guidedSessionPresentation(session, 100_000)
        assertEquals("B", state.focused.id)
        assertEquals(1, state.focusedIndex)
        assertEquals(2, state.completedExercises)
        assertTrue(state.upcoming.isEmpty())
        assertEquals(listOf("A", "C"), state.completed.map(Exercise::id))
        assertFalse(state.readyToFinish)

        val complete = session.copy(completedSets = mapOf("A" to 2, "B" to 1, "C" to 1))
        assertTrue(guidedSessionPresentation(complete, 100_000).readyToFinish)
    }

    @Test fun exerciseSectionsFollowCommittedCompletionAndUndoWithoutChangingOtherProgress() {
        val clock = SessionClockSample(100_000, 200_000, 7)
        val base = fixture().copy(focusedExerciseId = "B", completedSets = mapOf("A" to 1, "B" to 0, "C" to 0))
        val initial = guidedSessionPresentation(base, clock.elapsedMillis)
        assertEquals(listOf("A", "C"), initial.upcoming.map(Exercise::id))
        assertTrue(initial.completed.isEmpty())

        val focusedA = (reduceGuidedSession(base, SessionEvent.Focus("A"), clock) as SessionReduction.Changed).session
        val afterCompletion = (reduceGuidedSession(focusedA, SessionEvent.CompleteSet("A", 2), clock) as SessionReduction.Changed).session
        val completed = guidedSessionPresentation(afterCompletion, clock.elapsedMillis)
        assertEquals("B", completed.focused.id)
        assertEquals(listOf("C"), completed.upcoming.map(Exercise::id))
        assertEquals(listOf("A"), completed.completed.map(Exercise::id))
        assertEquals(2, afterCompletion.completedSets.getValue("A"))
        assertEquals(0, afterCompletion.completedSets.getValue("B"))

        val reviewed = (reduceGuidedSession(afterCompletion, SessionEvent.Focus("A"), clock) as SessionReduction.Changed).session
        assertEquals(listOf("B", "C"), guidedSessionPresentation(reviewed, clock.elapsedMillis).upcoming.map(Exercise::id))
        assertTrue(guidedSessionPresentation(reviewed, clock.elapsedMillis).completed.isEmpty())
        val undone = (reduceGuidedSession(reviewed, SessionEvent.UndoLastSet("A", 2), clock) as SessionReduction.Changed).session
        val corrected = (reduceGuidedSession(undone, SessionEvent.Focus("B"), clock) as SessionReduction.Changed).session
        assertEquals(listOf("A", "C"), guidedSessionPresentation(corrected, clock.elapsedMillis).upcoming.map(Exercise::id))
        assertTrue(guidedSessionPresentation(corrected, clock.elapsedMillis).completed.isEmpty())
        assertEquals(mapOf("A" to 1, "B" to 0, "C" to 0), corrected.completedSets)
    }

    @Test fun feedbackControllerRequiresCurrentOwnerAndConsumesEachCueOnce() {
        val base = fixture()
        val started = (reduceGuidedSession(base, SessionEvent.StartTimer("run"), SessionClockSample(100_000, 200_000, 7))
            as SessionReduction.Changed).session
        val controller = SessionFeedbackController()
        val currentOwner = SessionFeedbackOwner(started.id, 1, 2)
        val staleOwner = currentOwner.copy(foregroundEpoch = 1)
        val haptics = mutableListOf<HapticCue>()
        val voices = mutableListOf<VoiceCue>()
        controller.claim(currentOwner)

        controller.dispatch(staleOwner, started, listOf(SessionOutput.TimerCue("run", 0)), haptic = haptics::add, voice = voices::add)
        assertTrue(haptics.isEmpty())
        controller.dispatch(currentOwner, started, listOf(SessionOutput.TimerCue("run", 0)), haptic = haptics::add, voice = voices::add)
        controller.dispatch(currentOwner, started, listOf(SessionOutput.TimerCue("run", 0)), haptic = haptics::add, voice = voices::add)
        assertEquals(listOf(HapticCue.TIMER_START), haptics)
        assertEquals(listOf(VoiceCue.CountdownStarted), voices)

        controller.revoke(currentOwner)
        controller.dispatch(currentOwner, started, listOf(SessionOutput.TimerCue("run", 1)), haptic = haptics::add, voice = voices::add)
        assertEquals(1, voices.size)
    }

    @Test fun setFeedbackUsesCommittedCountAndAdvancedFocusOnce() {
        val base = fixture().copy(completedSets = mapOf("A" to 1, "B" to 0, "C" to 0))
        val completed = (reduceGuidedSession(base, SessionEvent.CompleteSet("A", 2), SessionClockSample(100_000, 200_000, 7))
            as SessionReduction.Changed).session
        val controller = SessionFeedbackController()
        val owner = SessionFeedbackOwner(completed.id, 3, 1)
        val voices = mutableListOf<VoiceCue>()
        val haptics = mutableListOf<HapticCue>()
        controller.claim(owner)
        controller.dispatch(owner, completed, listOf(SessionOutput.SetCompleted), "A", haptics::add, voices::add)
        controller.dispatch(owner, completed, listOf(SessionOutput.SetCompleted), "A", haptics::add, voices::add)

        assertEquals(listOf(HapticCue.SET_COMPLETE), haptics)
        assertEquals(listOf(VoiceCue.SetCompleted("A", 2, 2, "B")), voices)
    }
}

private class UiSessionStorage(var value: String) : DocumentStorage {
    override fun exists() = true
    override fun read() = value
    override fun write(value: String) {
        this.value = value
    }
}

private fun fixture(): GuidedSession {
    val routine = Routine(
        id = "routine-test",
        revision = 1,
        name = "Test routine",
        artworkId = RoutineArtworkCatalog.FALLBACK_ID,
        execution = RoutineExecution.GUIDED,
        exercises = listOf(
            Exercise("A", "A", "Timed", 2, "20 sec", 20, "dead_hang"),
            Exercise("B", "B", "Untimed", 1, "10 reps", null, "wrist_curl"),
            Exercise("C", "C", "Short", 1, "1 sec", 1, "grip_hold"),
        ),
        appLink = null,
    )
    return GuidedSession(
        id = "session",
        occurrence = OccurrenceKey("schedule-test", LocalDate.of(2026, 9, 12)),
        routineId = routine.id,
        snapshot = routine,
        focusedExerciseId = "A",
        completedSets = mapOf("A" to 0, "B" to 0, "C" to 0),
        timer = SessionTimer(),
        startedAtMillis = 200_000,
        updatedAtMillis = 200_000,
        eventRevision = 0,
    )
}

private fun timer(
    session: GuidedSession,
    phase: TimerPhase,
    readyDeadline: Long,
    activeDeadline: Long,
    ordinal: Int,
) = SessionTimer(
    phase = phase,
    runId = "run",
    exerciseId = session.focusedExerciseId,
    setNumber = 1,
    bootCount = 7,
    readyDeadlineElapsedMillis = readyDeadline,
    activeDeadlineElapsedMillis = activeDeadline,
    lastObservedElapsedMillis = 100_000,
    lastHandledCueOrdinal = ordinal,
)
