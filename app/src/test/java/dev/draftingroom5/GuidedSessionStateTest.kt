package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate

class GuidedSessionStateTest {
    private val start = SessionClockSample(100_000, 200_000, 7)

    @Test fun duplicateSetCompletionAcceptsOnlyOneRevision() {
        val first = changed(reduceGuidedSession(fixture(), SessionEvent.CompleteSet("A", 1), start))
        assertEquals(mapOf("A" to 1, "B" to 0, "C" to 0), first.session.completedSets)
        assertEquals(1, first.session.eventRevision)
        assertEquals(listOf(SessionOutput.SetCompleted), first.outputs)
        assertTrue(reduceGuidedSession(first.session, SessionEvent.CompleteSet("A", 1), start) is SessionReduction.Rejected)
    }

    @Test fun completionWrapsToIncompleteWorkAndReadyRequiresExplicitFinish() {
        var session = fixture()
        session = changed(reduceGuidedSession(session, SessionEvent.Focus("C"), start)).session
        session = changed(reduceGuidedSession(session, SessionEvent.CompleteSet("C", 1), start)).session
        assertEquals("A", session.focusedExerciseId)
        session = changed(reduceGuidedSession(session, SessionEvent.CompleteSet("A", 1), start)).session
        session = changed(reduceGuidedSession(session, SessionEvent.CompleteSet("A", 2), start)).session
        assertEquals("B", session.focusedExerciseId)
        session = changed(reduceGuidedSession(session, SessionEvent.CompleteSet("B", 1), start)).session
        assertEquals(DurableSessionState.READY_TO_FINISH, session.durableState())
        assertEquals("B", session.focusedExerciseId)
    }

    @Test fun undoEarlierExerciseKeepsLaterProgress() {
        val ready = fixture().copy(completedSets = mapOf("A" to 2, "B" to 1, "C" to 1), focusedExerciseId = "C")
        val result = changed(reduceGuidedSession(ready, SessionEvent.UndoLastSet("A", 2), start)).session
        assertEquals(mapOf("A" to 1, "B" to 1, "C" to 1), result.completedSets)
        assertEquals("A", result.focusedExerciseId)
        assertEquals(DurableSessionState.ACTIVE, result.durableState())
    }

    @Test fun timerThresholdsWriteOnlyWhenWatermarkChanges() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start))
        assertEquals(listOf(SessionOutput.TimerCue("R", 0)), started.outputs)
        val before = reduceGuidedSession(started.session, SessionEvent.Reconcile("R", true, true), start.copy(elapsedMillis = 106_999))
        assertTrue(before is SessionReduction.Unchanged)
        val three = changed(reduceGuidedSession(started.session, SessionEvent.Reconcile("R", true, true), start.copy(elapsedMillis = 107_000)))
        assertEquals(1, three.session.timer.lastHandledCueOrdinal)
        assertEquals(3, timerDisplaySeconds(three.session.timer, 107_000, 20))
        assertEquals(listOf(SessionOutput.TimerCue("R", 1)), three.outputs)
        assertTrue(reduceGuidedSession(three.session, SessionEvent.Reconcile("R", true, true), start.copy(elapsedMillis = 107_500)) is SessionReduction.Unchanged)
    }

    @Test fun readinessBoundaryGatesCompletionAndTimerNeverCompletesSet() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        assertTrue(reduceGuidedSession(started, SessionEvent.CompleteSet("A", 1), start.copy(elapsedMillis = 109_999)) is SessionReduction.Rejected)
        val completed = changed(reduceGuidedSession(started, SessionEvent.CompleteSet("A", 1), start.copy(elapsedMillis = 110_000)))
        assertEquals(1, completed.session.completedSets.getValue("A"))
        assertEquals(SessionTimer(), completed.session.timer)
        assertEquals(listOf(SessionOutput.SetCompleted), completed.outputs)

        val finished = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", true, true), start.copy(elapsedMillis = 130_000)))
        assertEquals(TimerPhase.FINISHED, finished.session.timer.phase)
        assertEquals(0, finished.session.completedSets.getValue("A"))
        assertEquals(listOf(SessionOutput.TimerCue("R", 5)), finished.outputs)
    }

    @Test fun skippedTickEmitsOnlyLatestCue() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        val jumped = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", true, true), start.copy(elapsedMillis = 111_500)))
        assertEquals(TimerPhase.RUNNING, jumped.session.timer.phase)
        assertEquals(4, jumped.session.timer.lastHandledCueOrdinal)
        assertEquals(listOf(SessionOutput.TimerCue("R", 4)), jumped.outputs)
        assertEquals(19, timerDisplaySeconds(jumped.session.timer, 111_500, 20))
    }

    @Test fun cancelAndEarlyCompletionInvalidateOldRun() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        val cancelled = changed(reduceGuidedSession(started, SessionEvent.CancelTimer("R"), start)).session
        assertEquals(SessionTimer(), cancelled.timer)
        assertTrue(reduceGuidedSession(cancelled, SessionEvent.Reconcile("R", true, true), start) is SessionReduction.Rejected)

        val running = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", true, false), start.copy(elapsedMillis = 115_000))).session
        val completed = changed(reduceGuidedSession(running, SessionEvent.CompleteSet("A", 1), start.copy(elapsedMillis = 115_000)))
        assertEquals(SessionTimer(), completed.session.timer)
        assertEquals(listOf(SessionOutput.SetCompleted), completed.outputs)
    }

    @Test fun processRestoreUsesTrustedBootOrClearsTimer() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        val resumed = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", false, false), start.copy(elapsedMillis = 115_000))).session
        assertEquals(TimerPhase.RUNNING, resumed.timer.phase)
        assertEquals(0, resumed.completedSets.getValue("A"))

        val rebooted = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", false, false), start.copy(elapsedMillis = 105_000, bootCount = 8)))
        assertEquals(SessionTimer(), rebooted.session.timer)
        assertEquals(listOf(SessionOutput.TimerCouldNotResume), rebooted.outputs)
        val unknown = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", false, false), start.copy(elapsedMillis = 105_000, bootCount = null)))
        assertEquals(SessionTimer(), unknown.session.timer)
    }

    @Test fun auditClockNeverRegressesAndOccurrenceNeverMoves() {
        val session = fixture().copy(updatedAtMillis = 300_000)
        val changed = changed(reduceGuidedSession(session, SessionEvent.Focus("B"), start.copy(wallMillis = 1))).session
        assertEquals(300_000, changed.updatedAtMillis)
        assertEquals(session.occurrence, changed.occurrence)
    }

    @Test fun strictTimerValidationRejectsPhaseWatermarkAndOverflow() {
        val session = fixture()
        val invalid = listOf(
            timer(session).copy(lastHandledCueOrdinal = 6),
            timer(session).copy(phase = TimerPhase.RUNNING),
            timer(session).copy(lastObservedElapsedMillis = 107_000, lastHandledCueOrdinal = 0),
            timer(session).copy(readyDeadlineElapsedMillis = Long.MAX_VALUE - 1, activeDeadlineElapsedMillis = Long.MAX_VALUE),
        )
        invalid.forEach { bad -> assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(documentFor(session.copy(timer = bad)))
        } }
    }

    @Test fun repositoryOpenResumeRestartFinishAndLeaseAreAtomic() {
        val storage = SessionStorage(encodeAppDocument(documentFor()))
        val clock = MutableClock(start)
        val repository = AppRepository(storage, clock)
        repository.load()
        val lease = checkNotNull(repository.sessionLease())
        val opened = repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S") as SessionRepositoryResult.Partial
        assertTrue(opened.created)
        assertEquals(1, storage.writesAfterLoad)
        val duplicate = repository.openGuidedSession(lease, occurrence, "routine-test", 1, "different") as SessionRepositoryResult.Partial
        assertEquals("S", duplicate.session.id)
        assertEquals(1, storage.writesAfterLoad)

        var session = opened.session
        session = partial(repository.applySessionEvent(lease, session.id, session.eventRevision, SessionEvent.CompleteSet("A", 1)))
        assertTrue(repository.applySessionEvent(lease, session.id, 0, SessionEvent.CompleteSet("A", 1)) is SessionRepositoryResult.Conflict)
        session = partial(repository.applySessionEvent(lease, session.id, session.eventRevision, SessionEvent.CompleteSet("A", 2)))
        session = partial(repository.applySessionEvent(lease, session.id, session.eventRevision, SessionEvent.CompleteSet("B", 1)))
        session = partial(repository.applySessionEvent(lease, session.id, session.eventRevision, SessionEvent.CompleteSet("C", 1)))
        assertEquals(DurableSessionState.READY_TO_FINISH, session.durableState())
        val finished = repository.finishGuidedSession(lease, session.id, session.eventRevision) as SessionRepositoryResult.Complete
        assertTrue(finished.newlyCompleted)
        val again = repository.finishGuidedSession(lease, session.id, session.eventRevision) as SessionRepositoryResult.Complete
        assertFalse(again.newlyCompleted)
        assertEquals(finished.history, again.history)

        val oldLease = lease
        repository.restore((repository.state.value as LoadState.Ready).value)
        assertTrue(repository.finishGuidedSession(oldLease, "S", session.eventRevision) is SessionRepositoryResult.RefreshRequired)
    }

    @Test fun repositoryFailureKeepsBytesAndPublishedState() {
        val storage = SessionStorage(encodeAppDocument(documentFor()))
        val repository = AppRepository(storage, MutableClock(start))
        val loaded = (repository.load() as LoadState.Ready).value
        val lease = checkNotNull(repository.sessionLease())
        val bytes = storage.value
        storage.failWrites = true
        assertTrue(repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S") is SessionRepositoryResult.Failed)
        assertEquals(bytes, storage.value)
        assertEquals(loaded, (repository.state.value as LoadState.Ready).value)
    }

    @Test fun restartAdoptsLatestRoutineOnlyAfterFreshConfirmationAndResetRevokesLease() {
        val storage = SessionStorage(encodeAppDocument(documentFor()))
        val repository = AppRepository(storage, MutableClock(start))
        repository.load()
        val lease = checkNotNull(repository.sessionLease())
        var session = (repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S") as SessionRepositoryResult.Partial).session
        session = partial(repository.applySessionEvent(lease, "S", 0, SessionEvent.CompleteSet("A", 1)))
        val beforeEdit = (repository.state.value as LoadState.Ready).value
        val editedRoutine = testRoutine().copy(revision = 2, name = "Edited")
        val editedPlan = beforeEdit.plan.copy(routines = listOf(editedRoutine))
        assertTrue(repository.replacePlan(beforeEdit.generation, editedPlan, acknowledgeSavedSessions = true) is RepositoryResult.Success)
        assertTrue(repository.restartGuidedSession(lease, "S", session.eventRevision, 1, "S2") is SessionRepositoryResult.Invalid)
        val restarted = repository.restartGuidedSession(lease, "S", session.eventRevision, 2, "S2") as SessionRepositoryResult.Partial
        assertEquals(2, restarted.session.snapshot.revision)
        assertEquals(setOf(0), restarted.session.completedSets.values.toSet())
        assertEquals(0, restarted.session.eventRevision)
        assertTrue(repository.applySessionEvent(lease, "S", session.eventRevision, SessionEvent.Focus("B")) is SessionRepositoryResult.Missing)

        val beforeReset = (repository.state.value as LoadState.Ready).value
        assertTrue(repository.resetPlan(beforeReset.generation) is RepositoryResult.Success)
        assertTrue(repository.applySessionEvent(lease, "S2", 0, SessionEvent.Focus("B")) is SessionRepositoryResult.RefreshRequired)
    }

    @Test fun loadReconcilesSameBootSilentlyAndUnknownBootToIdle() {
        val active = fixture().copy(timer = timer(fixture()))
        val storage = SessionStorage(encodeAppDocument(documentFor(active)))
        val repository = AppRepository(storage, MutableClock(start.copy(elapsedMillis = 115_000)))
        val loaded = (repository.load() as LoadState.Ready).value.partialSessions.single()
        assertEquals(TimerPhase.RUNNING, loaded.timer.phase)
        assertEquals(0, loaded.completedSets.getValue("A"))

        val unknownStorage = SessionStorage(encodeAppDocument(documentFor(active)))
        val unknown = AppRepository(unknownStorage, MutableClock(start.copy(elapsedMillis = 105_000, bootCount = null)))
        assertEquals(SessionTimer(), ((unknown.load() as LoadState.Ready).value.partialSessions.single().timer))
        val notice = unknown.openGuidedSession(checkNotNull(unknown.sessionLease()), occurrence, "ignored", -1, "ignored")
            as SessionRepositoryResult.Partial
        assertEquals(listOf(SessionOutput.TimerCouldNotResume), notice.outputs)
    }

    @Test fun backupReadsKeepSessionLeaseAndUnknownBootTimerOwnership() {
        val storage = SessionStorage(encodeAppDocument(documentFor()))
        val clock = MutableClock(start.copy(bootCount = null))
        val repository = AppRepository(storage, clock)
        repository.ensureLoaded()
        val lease = checkNotNull(repository.sessionLease())
        repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S")
        val running = partial(repository.applySessionEvent(lease, "S", 0, SessionEvent.StartTimer("R")))
        repeat(3) {
            val read = (repository.ensureLoaded() as LoadState.Ready).value
            assertEquals(read, decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(1, read))).document)
        }
        assertEquals(lease, repository.sessionLease())
        clock.current = clock.current.copy(elapsedMillis = 110_000)
        val result = repository.applySessionEvent(lease, "S", running.eventRevision, SessionEvent.Reconcile("R", true, true))
            as SessionRepositoryResult.Partial
        assertEquals(TimerPhase.RUNNING, result.session.timer.phase)
        assertEquals(listOf(SessionOutput.TimerCue("R", 4)), result.outputs)
    }

    @Test fun failedBoundaryCatchesUpSilentlyThenAllowsNewCues() {
        val storage = SessionStorage(encodeAppDocument(documentFor()))
        val clock = MutableClock(start)
        val repository = AppRepository(storage, clock)
        repository.load()
        val lease = checkNotNull(repository.sessionLease())
        repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S")
        var session = partial(repository.applySessionEvent(lease, "S", 0, SessionEvent.StartTimer("R")))
        clock.current = start.copy(elapsedMillis = 107_000)
        storage.failWrites = true
        assertTrue(repository.applySessionEvent(lease, "S", session.eventRevision, SessionEvent.Reconcile("R", true, true)) is SessionRepositoryResult.Failed)
        storage.failWrites = false
        val caughtUp = repository.applySessionEvent(lease, "S", session.eventRevision, SessionEvent.Reconcile("R", true, true)) as SessionRepositoryResult.Partial
        assertTrue(caughtUp.outputs.isEmpty())
        session = caughtUp.session
        clock.current = start.copy(elapsedMillis = 108_000)
        val fresh = repository.applySessionEvent(lease, "S", session.eventRevision, SessionEvent.Reconcile("R", true, true)) as SessionRepositoryResult.Partial
        assertEquals(listOf(SessionOutput.TimerCue("R", 2)), fresh.outputs)
    }

    @Test fun restoredRouteNeverRecreatesOrRetargetsMissingSession() {
        val repository = AppRepository(SessionStorage(encodeAppDocument(documentFor())), MutableClock(start))
        repository.load()
        val lease = checkNotNull(repository.sessionLease())
        repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S")
        repository.restartGuidedSession(lease, "S", 0, 1, "replacement")
        val before = repository.state.value
        assertTrue(repository.openGuidedSession(lease, occurrence, "routine-test", 1, "new", existingSessionId = "S") is SessionRepositoryResult.Missing)
        assertEquals(before, repository.state.value)
        val resumed = repository.openGuidedSession(lease, occurrence, "routine-test", 1, "new", existingSessionId = "replacement") as SessionRepositoryResult.Partial
        assertEquals("replacement", resumed.session.id)
    }

    @Test fun boundaryAndCompletionRaceAcceptsOnlyOneCommandInEitherOrder() {
        listOf(true, false).forEach { tickFirst ->
            val clock = MutableClock(start)
            val repository = AppRepository(SessionStorage(encodeAppDocument(documentFor())), clock)
            repository.load()
            val lease = checkNotNull(repository.sessionLease())
            repository.openGuidedSession(lease, occurrence, "routine-test", 1, "S")
            val session = partial(repository.applySessionEvent(lease, "S", 0, SessionEvent.StartTimer("R")))
            clock.current = start.copy(elapsedMillis = 110_000)
            val tick = SessionEvent.Reconcile("R", true, true)
            val complete = SessionEvent.CompleteSet("A", 1)
            assertTrue(repository.applySessionEvent(lease, "S", session.eventRevision, if (tickFirst) tick else complete) is SessionRepositoryResult.Partial)
            assertTrue(repository.applySessionEvent(lease, "S", session.eventRevision, if (tickFirst) complete else tick) is SessionRepositoryResult.Conflict)
            assertEquals(if (tickFirst) 0 else 1, (repository.state.value as LoadState.Ready).value.partialSessions.single().completedSets.getValue("A"))
        }
    }

    @Test fun failedSessionMutationsRetainCommittedBytesAndCanRetry() {
        val ready = fixture().copy(completedSets = mapOf("A" to 2, "B" to 1, "C" to 1))
        listOf("complete", "undo", "restart", "finish", "restore").forEach { action ->
            val initial = if (action == "finish" || action == "undo") ready else fixture()
            val storage = SessionStorage(encodeAppDocument(documentFor(initial)))
            val repository = AppRepository(storage, MutableClock(start))
            repository.load()
            val lease = checkNotNull(repository.sessionLease())
            val before = repository.state.value
            val bytes = storage.value
            storage.failWrites = true
            val result = when (action) {
                "complete" -> repository.applySessionEvent(lease, "S", 0, SessionEvent.CompleteSet("A", 1))
                "undo" -> repository.applySessionEvent(lease, "S", 0, SessionEvent.UndoLastSet("A", 2))
                "restart" -> repository.restartGuidedSession(lease, "S", 0, 1, "new")
                "finish" -> repository.finishGuidedSession(lease, "S", 0)
                else -> repository.restore(documentFor())
            }
            assertTrue(result is SessionRepositoryResult.Failed || result is RepositoryResult.Failed)
            assertEquals(before, repository.state.value)
            assertEquals(bytes, storage.value)
            assertEquals(lease, repository.sessionLease())
        }
    }

    @Test fun finishedTimerRequiresFreshRunIdentity() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        val finished = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", true, false), start.copy(elapsedMillis = 130_000))).session
        assertTrue(reduceGuidedSession(finished, SessionEvent.StartTimer("R", "R"), start.copy(elapsedMillis = 130_000)) is SessionReduction.Rejected)
    }

    @Test fun elapsedRegressionBetweenPersistedThresholdsClearsTimerWithoutLosingSets() {
        val started = changed(reduceGuidedSession(fixture(), SessionEvent.StartTimer("R"), start)).session
        val regressed = changed(reduceGuidedSession(started, SessionEvent.Reconcile("R", true, true),
            start.copy(elapsedMillis = 104_000), previousElapsedMillis = 105_000))
        assertEquals(SessionTimer(), regressed.session.timer)
        assertEquals(started.completedSets, regressed.session.completedSets)
        assertEquals(listOf(SessionOutput.TimerCouldNotResume), regressed.outputs)
    }

    private fun changed(value: SessionReduction) = value as SessionReduction.Changed
    private fun partial(value: SessionRepositoryResult) = (value as SessionRepositoryResult.Partial).session
}

private val occurrence = OccurrenceKey("schedule-test", LocalDate.of(2026, 9, 12))

private fun testRoutine() = Routine("routine-test", 1, "Test", "generic", RoutineExecution.GUIDED, listOf(
    Exercise("A", "A", "", 2, "target", 20, "generic"),
    Exercise("B", "B", "", 1, "target", null, "generic"),
    Exercise("C", "C", "", 1, "target", 1, "generic"),
), null)

private fun fixture(): GuidedSession {
    val routine = testRoutine()
    return GuidedSession("S", occurrence, routine.id, routine, "A", mapOf("A" to 0, "B" to 0, "C" to 0),
        SessionTimer(), 200_000, 200_000, 0)
}

private fun timer(session: GuidedSession) = SessionTimer(TimerPhase.READY, "R", session.focusedExerciseId, 1, 7,
    110_000, 130_000, 100_000, 0)

private fun documentFor(session: GuidedSession? = null): AppDocument {
    val routine = testRoutine()
    return defaultAppDocument().copy(
        plan = TrainingPlan(listOf(routine), listOf(ScheduleEntry("schedule-test", routine.id, setOf(DayOfWeek.SATURDAY)))),
        partialSessions = listOfNotNull(session),
    )
}

private class MutableClock(var current: SessionClockSample) : SessionClock {
    override fun sample() = current
}

private class SessionStorage(initial: String) : DocumentStorage {
    var value = initial
    var failWrites = false
    var writesAfterLoad = 0
    override fun exists() = true
    override fun read() = value
    override fun write(value: String) {
        if (failWrites) throw IOException("write failed")
        this.value = value
        writesAfterLoad++
    }
}
