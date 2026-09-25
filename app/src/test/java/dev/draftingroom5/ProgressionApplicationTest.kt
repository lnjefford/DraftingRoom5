package dev.draftingroom5

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

private class ProgressionStorage(document: AppDocument) : DocumentStorage {
    var value = encodeAppDocument(document)
    var writes = 0
    var fail = false
    override fun exists() = true
    override fun read() = value
    override fun write(value: String) {
        if (fail) throw IOException("disk unavailable")
        this.value = value
        writes++
    }
}

private fun completedExerciseDocument(): AppDocument = progressionDocumentFixture().let { d ->
    d.copy(partialSessions = d.partialSessions.map { s ->
        s.copy(completedSets = s.snapshot.exercises.associate { it.id to it.setCount })
    })
}

private class ProgressionHarness(val initial: AppDocument = completedExerciseDocument()) {
    val storage = ProgressionStorage(initial)
    var repository = repository()
    fun repository() = AppRepository(storage, SessionClock { SessionClockSample(100, 100, 1) }).also { it.load() }
    fun restartProcess() { repository = repository() }
    fun document() = (repository.state.value as LoadState.Ready).value
    fun live() = document().plan.routines.single { it.id == "routine-forearm" }
    fun lease() = requireNotNull(repository.sessionLease())
    fun offer(id: String = live().exercises.first().id, sessionId: String = document().partialSessions.single().id) =
        requireNotNull(document().progressionOffer(sessionId, id))
    fun apply(offer: ProgressionOffer = offer(), choice: ProgressionChoice = offer.options.first().choice): ProgressionReceipt =
        (repository.applyProgression(lease(), offer.request(choice)) as RepositoryResult.Success).value
    fun undo(receipt: ProgressionReceipt) = repository.undoProgression(lease(), receipt.sessionId, receipt.before.id)
    fun edit(transform: (AppDocument) -> AppDocument) {
        assertTrue(repository.update(document().generation, transform) is RepositoryResult.Success)
    }
    fun nextWorkout(): GuidedSession {
        val old = document().partialSessions.single()
        assertTrue(repository.finishGuidedSession(lease(), old.id, old.eventRevision) is SessionRepositoryResult.Complete)
        val result = repository.openGuidedSession(lease(), old.occurrence.copy(scheduledDate = old.occurrence.scheduledDate.plusWeeks(1)),
            live().id, live().revision, "next-${document().history.size}") as SessionRepositoryResult.Partial
        // Complete through repository events, including inserted exercises.
        result.session.snapshot.exercises.forEach { e -> repeat(e.setCount) {
            val s = document().partialSessions.single()
            assertTrue(repository.applySessionEvent(lease(), s.id, s.eventRevision,
                SessionEvent.CompleteSet(e.id, it + 1)) is SessionRepositoryResult.Partial)
        } }
        return document().partialSessions.single()
    }
}

class ProgressionApplicationTest {
    @Test fun manualAdjustmentReplacesAllTargetsWithoutConsumingStepsAndUndoRestoresEverything() {
        val h = ProgressionHarness()
        val before = h.document()
        val source = h.live().exercises.first()
        val prescription = source.prescription().copy(weightPounds = 35, durationSeconds = 45, setCount = 5, reps = 12)
        val request = h.offer().manualRequest(prescription)
        val receipt = (h.repository.applyProgression(h.lease(), request) as RepositoryResult.Success).value
        assertEquals(prescription, h.live().exercises.first().prescription())
        assertEquals(source.progression, h.live().exercises.first().progression)
        assertEquals(before.plan.routines.last().exercises.drop(1), h.live().exercises.drop(1))
        assertEquals(before.partialSessions, h.document().partialSessions)
        assertEquals(before.history, h.document().history)
        assertEquals(2L, h.live().revision)
        assertEquals(1, h.storage.writes)
        h.restartProcess()
        assertEquals(receipt, (h.repository.applyProgression(h.lease(), request) as RepositoryResult.Success).value)
        assertEquals(1, h.storage.writes)
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertEquals(before.plan.routines.last().copy(revision = 3), h.live())
        assertEquals(before.partialSessions, h.document().partialSessions)
        assertEquals(before.history, h.document().history)
        h.nextWorkout()
        assertEquals(source.progression!!.steps.first().replacement, h.apply().option().source.prescription())
    }

    @Test fun manualAdjustmentCanClearAllOptionalTargetsWithoutProgression() {
        val h = ProgressionHarness()
        val source = h.live().exercises[1]
        assertNull(source.progression)
        val cleared = source.prescription().copy(weightPounds = null, durationSeconds = null, reps = null, setCount = 1)
        val request = h.offer(source.id).manualRequest(cleared)
        val receipt = (h.repository.applyProgression(h.lease(), request) as RepositoryResult.Success).value
        assertEquals(cleared, h.live().exercises[1].prescription())
        h.restartProcess()
        assertEquals(cleared, h.live().exercises[1].prescription())
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertEquals(source, h.live().exercises[1])
    }

    @Test fun manualAdjustmentAfterInsertionPreservesIndependentChildrenAndRemainingStepOrder() {
        val h = ProgressionHarness()
        val first = h.apply()
        h.nextWorkout()
        val source = h.live().exercises.first()
        val additions = h.live().exercises.drop(1)
        val prescription = source.prescription().copy(weightPounds = null, durationSeconds = null, setCount = 2, reps = 6)
        val receipt = (h.repository.applyProgression(h.lease(), h.offer().manualRequest(prescription)) as RepositoryResult.Success).value
        assertEquals(source.progression, h.live().exercises.first().progression)
        assertEquals(additions, h.live().exercises.drop(1))
        h.nextWorkout()
        val last = h.apply()
        assertEquals(source.progression!!.steps.single().replacement, last.option().source.prescription())
        assertNull(h.live().exercises.first().progression)
        assertEquals(first.option().additions.single(), h.live().exercises[1])
        assertTrue(h.undo(receipt) is RepositoryResult.Conflict)
    }

    @Test fun invalidManualTargetsAndMalformedChoicesNeverWriteOrConsumeSteps() {
        val h = ProgressionHarness()
        val before = h.document()
        val bytes = h.storage.value
        val p = h.live().exercises.first().prescription()
        val offer = h.offer()
        val invalid = listOf(p.copy(weightPounds = 37), p.copy(weightPounds = 0), p.copy(weightPounds = -5),
            p.copy(durationSeconds = 0), p.copy(reps = 0), p.copy(setCount = 0))
        invalid.forEach {
            assertTrue(h.repository.applyProgression(h.lease(), offer.manualRequest(it)) is RepositoryResult.Invalid)
        }
        assertTrue(h.repository.applyProgression(h.lease(), offer.request(ProgressionChoice.MANUAL)) is RepositoryResult.Invalid)
        assertTrue(h.repository.applyProgression(h.lease(), offer.request(ProgressionChoice.CUSTOM).copy(manualPrescription = p)) is RepositoryResult.Invalid)
        assertEquals(before, h.document())
        assertEquals(bytes, h.storage.value)
        assertEquals(0, h.storage.writes)
    }

    @Test fun failedManualWriteIsAtomicAndCanRetryThenUndo() {
        val h = ProgressionHarness()
        val before = h.document()
        val bytes = h.storage.value
        val request = h.offer().manualRequest(h.live().exercises.first().prescription().copy(reps = 12, weightPounds = 40))
        h.storage.fail = true
        assertTrue(h.repository.applyProgression(h.lease(), request) is RepositoryResult.Failed)
        assertEquals(before, h.document())
        assertEquals(bytes, h.storage.value)
        h.storage.fail = false
        h.restartProcess()
        val receipt = (h.repository.applyProgression(h.lease(), request) as RepositoryResult.Success).value
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertEquals(before.plan.routines.last().copy(revision = 3), h.live())
    }

    @Test fun manualAdjustmentsRejectStaleSessionRoutineAndRevokedLease() {
        val h = ProgressionHarness()
        val request = h.offer().manualRequest(h.live().exercises.first().prescription().copy(weightPounds = 35))
        assertTrue(h.repository.applyProgression(h.lease(), request.copy(expectedRoutineRevision = 0)) is RepositoryResult.Conflict)
        assertTrue(h.repository.applyProgression(h.lease(), request.copy(expectedSessionRevision = 100)) is RepositoryResult.Conflict)
        val lease = h.lease()
        h.repository.restore(h.document())
        assertTrue(h.repository.applyProgression(lease, request) is RepositoryResult.Invalid)
        assertEquals(h.initial.plan, h.document().plan)
        assertTrue(h.document().progressionReceipts.isEmpty())
    }

    @Test fun keepCurrentConsumesNoStepAndRejectsStaleAdjustmentForHandledCompletion() {
        val h = ProgressionHarness()
        val before = h.document()
        val session = before.partialSessions.single()
        val offer = h.offer()
        val result = h.repository.applySessionEvent(h.lease(), session.id, session.eventRevision,
            SessionEvent.ContinueWorkout(offer.exerciseId)) as SessionRepositoryResult.Partial
        assertEquals(before.plan, h.document().plan)
        assertEquals(before.history, h.document().history)
        assertTrue(h.document().progressionReceipts.isEmpty())
        assertEquals(session.snapshot, result.session.snapshot)
        val request = offer.manualRequest(session.snapshot.exercises.first().prescription().copy(weightPounds = 35))
        assertTrue(h.repository.applyProgression(h.lease(), request) is RepositoryResult.Invalid)
        h.restartProcess()
        assertEquals(before.plan, h.document().plan)
        h.nextWorkout()
        assertEquals(before.plan.routines.last().exercises.first().progression!!.steps.first().replacement, h.apply().option().source.prescription())
    }

    @Test fun concurrentManualAndCustomCommandsPublishExactlyOneWholeResult() {
        val h = ProgressionHarness()
        val offer = h.offer()
        val prescription = h.live().exercises.first().prescription().copy(weightPounds = 45, reps = 10, durationSeconds = null)
        val requests = listOf(offer.request(ProgressionChoice.CUSTOM), offer.manualRequest(prescription))
        val start = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<RepositoryResult<ProgressionReceipt>>())
        val workers = requests.map { request -> Thread {
            start.await(); results.add(h.repository.applyProgression(h.lease(), request))
        }.also { it.start() } }
        start.countDown()
        workers.forEach { it.join() }
        assertEquals(1, results.count { it is RepositoryResult.Success })
        assertEquals(1, results.count { it is RepositoryResult.Conflict })
        assertEquals(1, h.storage.writes)
        val receipt = h.document().progressionReceipts.single()
        assertEquals(receipt.option().source, h.live().exercises.first())
        assertEquals(h.initial.partialSessions, h.document().partialSessions)
        assertEquals(if (receipt.choice == ProgressionChoice.CUSTOM) h.initial.plan.routines.last().exercises.size + 1
            else h.initial.plan.routines.last().exercises.size, h.live().exercises.size)
    }

    @Test fun customReplacementPreservesSourceIdAndInsertsOrderedStableIdsOnlyOnce() {
        val base = completedExerciseDocument()
        val s = base.partialSessions.single().snapshot.exercises.first()
        val custom = s.progression as CustomExerciseProgression
        val first = custom.steps.first()
        val extra = first.insertedExercises.single().copy(id = "second-addition")
        val source = s.copy(progression = custom.copy(steps = listOf(first.copy(insertedExercises = first.insertedExercises + extra)) + custom.steps.drop(1)))
        val routine = base.partialSessions.single().snapshot.copy(exercises = listOf(source) + base.partialSessions.single().snapshot.exercises.drop(1))
        val fixture = progressionDocumentFixture(routine).let { d -> d.copy(partialSessions = d.partialSessions.map {
            it.copy(completedSets = routine.exercises.associate { e -> e.id to e.setCount })
        }) }
        val h = ProgressionHarness(fixture)
        val offer = h.offer()
        val receipt = h.apply(offer)
        val result = h.document()
        assertEquals(listOf(s.id, first.insertedExercises.single().id, extra.id) + routine.exercises.drop(1).map { it.id }, h.live().exercises.map { it.id })
        assertEquals(first.replacement, h.live().exercises.first().prescription())
        assertEquals(CustomExerciseProgression(custom.steps.drop(1)), h.live().exercises.first().progression)
        assertEquals(receipt, h.apply(offer))
        assertEquals(result, h.document())
        assertEquals(1, h.storage.writes)
        h.restartProcess()
        assertEquals(result, h.document())
        assertEquals(receipt, h.apply(offer))
        assertEquals(1, h.storage.writes)
    }

    @Test fun futureInsertedExerciseProgressesIndependentlyAndSourceFinalStepLeavesItUntouched() {
        val h = ProgressionHarness()
        val first = h.apply()
        val inserted = first.option().additions.single()
        h.nextWorkout()
        val insertedReceipt = h.apply(h.offer(inserted.id), ProgressionChoice.CUSTOM)
        val advanced = h.live().exercises.single { it.id == inserted.id }
        assertEquals(10, advanced.weightPounds)
        val final = h.apply(h.offer(first.before.id))
        assertNull(final.option().source.progression)
        assertEquals(advanced, h.live().exercises.single { it.id == inserted.id })
        assertEquals(4L, h.live().revision)
        assertTrue(h.undo(insertedReceipt) is RepositoryResult.Conflict)
        h.nextWorkout()
        assertTrue(h.document().progressionOffer(h.document().partialSessions.single().id, first.before.id)!!.options.isEmpty())
        assertNotNull(h.document().progressionOffer(h.document().partialSessions.single().id, inserted.id))
    }

    @Test fun undoRestoresOnlyTransitionSourceAndAdditionsAndIsDurableAndIdempotent() {
        val h = ProgressionHarness()
        val before = h.live()
        val receipt = h.apply()
        h.edit { it.copy(preferences = it.preferences.copy(hapticsEnabled = false)) }
        val snapshots = h.document().partialSessions
        h.restartProcess()
        val undone = (h.undo(receipt) as RepositoryResult.Success).value
        assertTrue(undone.undone)
        assertEquals(before.copy(revision = 3), h.live())
        assertFalse(h.document().preferences.hapticsEnabled)
        assertEquals(snapshots, h.document().partialSessions)
        val after = h.document()
        val writes = h.storage.writes
        assertEquals(undone, (h.undo(receipt) as RepositoryResult.Success).value)
        h.restartProcess()
        assertEquals(undone, h.apply(ProgressionOffer(receipt.sessionId, receipt.before.id, 0, 1, receipt.before.progressionOptions())))
        assertEquals(after, h.document())
        assertEquals(writes, h.storage.writes)
        assertNull(h.document().progressionOffer(receipt.sessionId, receipt.before.id))
    }

    @Test fun undoOfLaterStepKeepsEarlierInsertionsAndIndependentProgress() {
        val h = ProgressionHarness()
        val first = h.apply()
        h.nextWorkout()
        h.apply(h.offer(first.option().additions.single().id), ProgressionChoice.CUSTOM)
        val before = h.live()
        val last = h.apply(h.offer(first.before.id))
        assertTrue(h.undo(last) is RepositoryResult.Success)
        assertEquals(before.copy(revision = before.revision + 2), h.live())
    }

    @Test fun failedApplyAndUndoKeepLiveStateBytesAndSnapshotsRecoverable() {
        val h = ProgressionHarness()
        val offer = h.offer()
        val before = h.document()
        val bytes = h.storage.value
        h.storage.fail = true
        assertTrue(h.repository.applyProgression(h.lease(), offer.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Failed)
        assertEquals(before, h.document())
        assertEquals(bytes, h.storage.value)
        h.storage.fail = false
        h.restartProcess()
        val receipt = h.apply(offer)
        val progressed = h.document()
        val progressedBytes = h.storage.value
        h.storage.fail = true
        assertTrue(h.undo(receipt) is RepositoryResult.Failed)
        assertEquals(progressed, h.document())
        assertEquals(progressedBytes, h.storage.value)
        h.storage.fail = false
        h.restartProcess()
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertEquals(before.plan.routines.last().copy(revision = 3), h.live())
    }

    @Test fun staleRoutineAndSessionRevisionsCannotCommitAndRefreshedUnchangedSourceCan() {
        val h = ProgressionHarness()
        val offer = h.offer()
        h.edit { it.copy(plan = it.plan.copy(routines = it.plan.routines.map { r ->
            if (r.id == h.live().id) r.copy(revision = 2, name = "Renamed") else r
        })) }
        assertTrue(h.repository.applyProgression(h.lease(), offer.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Conflict)
        val refreshed = h.offer()
        h.edit { it.copy(partialSessions = it.partialSessions.map { s -> s.copy(eventRevision = s.eventRevision + 1) }) }
        assertTrue(h.repository.applyProgression(h.lease(), refreshed.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Conflict)
        h.apply(h.offer())
        assertEquals("Renamed", h.live().name)
        assertEquals(3L, h.live().revision)
    }

    @Test fun incompleteUndoneSetsMissingDisabledAndChangedSourcesCannotProgress() {
        val h = ProgressionHarness(progressionDocumentFixture())
        val s = h.document().partialSessions.single()
        val source = s.snapshot.exercises.first()
        assertNull(h.document().progressionOffer(s.id, source.id))
        assertTrue(h.repository.applyProgression(h.lease(), ProgressionRequest(s.id, source.id, 0, 1, ProgressionChoice.CUSTOM)) is RepositoryResult.Invalid)
        val complete = ProgressionHarness()
        val offer = complete.offer()
        val session = complete.document().partialSessions.single()
        complete.repository.applySessionEvent(complete.lease(), session.id, session.eventRevision,
            SessionEvent.UndoLastSet(source.id, source.setCount))
        assertTrue(complete.repository.applyProgression(complete.lease(), offer.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Invalid)
        assertNull(complete.document().progressionOffer(session.id, "missing"))
        assertTrue(complete.document().progressionOffer(session.id, session.snapshot.exercises.last().id)!!.options.isEmpty())
        val changed = ProgressionHarness()
        changed.edit { it.copy(plan = it.plan.copy(routines = it.plan.routines.map { r ->
            if (r.id == changed.live().id) r.copy(revision = 2, exercises = r.exercises.map { e ->
                if (e.id == source.id) e.copy(notes = "Changed") else e
            }) else r
        })) }
        assertNull(changed.document().progressionOffer(session.id, source.id))
    }

    @Test fun duplicateConcurrentTapsProduceOneWriteAndOneReceipt() {
        val h = ProgressionHarness()
        val request = h.offer().request(ProgressionChoice.CUSTOM)
        val start = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<RepositoryResult<ProgressionReceipt>>())
        val workers = List(2) { Thread { start.await(); results.add(h.repository.applyProgression(h.lease(), request)) }.also { it.start() } }
        start.countDown()
        workers.forEach { it.join() }
        assertEquals(2, results.size)
        assertTrue(results.all { it is RepositoryResult.Success })
        assertEquals(results.first(), results.last())
        assertEquals(1, h.storage.writes)
        assertEquals(1, h.document().progressionReceipts.size)
    }

    @Test fun finishAndRetryPreserveOriginalHistoryAndUndoNeverRewritesIt() {
        val h = ProgressionHarness()
        val session = h.document().partialSessions.single()
        val offer = h.offer()
        val receipt = h.apply(offer)
        val completed = h.repository.finishGuidedSession(h.lease(), session.id, session.eventRevision) as SessionRepositoryResult.Complete
        assertEquals(session.snapshot, completed.history.snapshot)
        val history = h.document().history
        h.restartProcess()
        assertEquals(receipt, h.apply(offer))
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertEquals(history, h.document().history)
        assertEquals(h.initial.history.single(), history.first())
    }

    @Test fun restartingWorkoutAdoptsProgressedRoutineAndOldCommandsCannotAdvanceAgain() {
        val h = ProgressionHarness()
        val offer = h.offer()
        val receipt = h.apply(offer)
        val previous = h.document().partialSessions.single()
        val restarted = h.repository.restartGuidedSession(h.lease(), previous.id, previous.eventRevision,
            h.live().revision, "restarted") as SessionRepositoryResult.Partial
        assertEquals(h.live(), restarted.session.snapshot)
        assertEquals(receipt, h.apply(offer))
        assertNull(h.document().progressionOffer(restarted.session.id, receipt.before.id))
        assertTrue(h.repository.restartGuidedSession(h.lease(), restarted.session.id, restarted.session.eventRevision,
            h.live().revision, previous.id) is SessionRepositoryResult.Invalid)
    }

    @Test fun oldParallelSessionCannotAdvanceAfterAnotherSessionAdvancesThenUndoes() {
        val base = completedExerciseDocument()
        val nextDate = base.partialSessions.single().occurrence.scheduledDate.plusWeeks(1)
        val other = base.partialSessions.single().copy(id = "other", occurrence = base.partialSessions.single().occurrence.copy(
            scheduledDate = nextDate), effectiveDate = nextDate)
        val h = ProgressionHarness(base.copy(partialSessions = base.partialSessions + other))
        val offer = h.offer(sessionId = base.partialSessions.single().id)
        val stale = h.offer(sessionId = other.id)
        val receipt = h.apply(offer)
        assertTrue(h.undo(receipt) is RepositoryResult.Success)
        assertNull(h.document().progressionOffer(other.id, receipt.before.id))
        assertTrue(h.repository.applyProgression(h.lease(), stale.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Invalid)
    }

    @Test fun restoreRevokesOldLeaseAndPreservesReceiptsWhileResetClearsThem() {
        val h = ProgressionHarness()
        val offer = h.offer()
        val receipt = h.apply(offer)
        val saved = decodeAppDocument(encodeAppDocument(h.document()))
        val oldLease = h.lease()
        assertTrue(h.repository.restore(saved) is RepositoryResult.Success)
        assertTrue(h.repository.applyProgression(oldLease, offer.request(ProgressionChoice.CUSTOM)) is RepositoryResult.Invalid)
        assertEquals(receipt, h.apply(offer))
        assertTrue(h.repository.resetPlan(h.document().generation) is RepositoryResult.Success)
        assertTrue(h.document().progressionReceipts.isEmpty())
    }

    @Test fun receiptsRequireStrictCurrentSchemaAndValidUniqueChoices() {
        val h = ProgressionHarness()
        val receipt = h.apply()
        assertEquals(h.document(), decodeAppDocument(encodeAppDocument(h.document())))
        assertThrows(IllegalArgumentException::class.java) {
            encodeAppDocument(h.document().copy(progressionReceipts = listOf(receipt, receipt)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeAppDocument(h.document().copy(progressionReceipts = listOf(receipt.copy(choice = ProgressionChoice.MANUAL))))
        }
        val root = JSONObject(h.storage.value)
        root.remove("progressionReceipts")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun unavailableChoiceAndExpiredUndoDoNotWrite() {
        val h = ProgressionHarness()
        val offer = h.offer()
        assertTrue(h.repository.applyProgression(h.lease(), offer.request(ProgressionChoice.MANUAL)) is RepositoryResult.Invalid)
        assertEquals(0, h.storage.writes)
        val receipt = h.apply(offer)
        h.edit { d -> d.copy(plan = d.plan.copy(routines = d.plan.routines.map { r ->
            if (r.id == receipt.routineId) r.copy(name = "Edited later", revision = r.revision + 1) else r
        })) }
        val before = h.document()
        assertTrue(h.undo(receipt) is RepositoryResult.Conflict)
        assertEquals(before, h.document())
        assertEquals(2, h.storage.writes)
    }

    @Test fun undoAllowsNextWorkoutToAdvanceAgainWithSameReservedInsertionIds() {
        val h = ProgressionHarness()
        val original = h.apply()
        assertTrue(h.undo(original) is RepositoryResult.Success)
        h.nextWorkout()
        val next = h.apply()
        assertNotEquals(original.sessionId, next.sessionId)
        assertEquals(original.option().additions, next.option().additions)
        assertEquals(4L, next.appliedRevision)
        assertEquals(h.live().exercises.size, h.live().exercises.map { it.id }.distinct().size)
    }

    @Test fun insertionIsImmediateEvenWhenEarlierStepAlreadyInsertedAndHasNestedProgression() {
        val h = ProgressionHarness()
        val first = h.apply()
        val oldAddition = first.option().additions.single()
        val nested = oldAddition.copy(id = "nested", progression = null)
        val newer = oldAddition.copy(id = "newer", progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(oldAddition.prescription().copy(name = "Nested prescription"), listOf(nested)))))
        h.edit { d -> d.copy(plan = d.plan.copy(routines = d.plan.routines.map { r ->
            if (r.id != first.routineId) r else r.copy(revision = r.revision + 1, exercises = r.exercises.map { e ->
                if (e.id != first.before.id) e else e.copy(progression = CustomExerciseProgression(listOf(
                    CustomProgressionStep(e.prescription(), listOf(newer)))))
            })
        })) }
        h.nextWorkout()
        h.apply(h.offer(first.before.id))
        assertEquals(listOf(first.before.id, newer.id, oldAddition.id), h.live().exercises.take(3).map { it.id })
        h.nextWorkout()
        h.apply(h.offer(newer.id))
        assertEquals(listOf(first.before.id, newer.id, nested.id, oldAddition.id), h.live().exercises.take(4).map { it.id })
    }

    @Test fun progressedSourceAndInsertedExerciseDeleteAndReorderIndependently() {
        val h = ProgressionHarness()
        val receipt = h.apply()
        val inserted = receipt.option().additions.single()
        val afterProgression = h.live()
        val reordered = afterProgression.moveExercise(inserted.id, 1)
        assertEquals(inserted.id, reordered.exercises[2].id)
        assertEquals(receipt.option().source, reordered.exercises.first())

        val withoutSource = reordered.withoutExercise(receipt.before.id)!!
        assertTrue(withoutSource.exercises.any { it.id == inserted.id })
        val withoutInserted = afterProgression.withoutExercise(inserted.id)!!
        assertEquals(receipt.option().source, withoutInserted.exercises.first())
        assertFalse(withoutInserted.exercises.any { it.id == inserted.id })
    }

    @Test fun deletingProgressedRoutinePrunesOnlyItsUnusableReceipts() {
        val h = ProgressionHarness()
        val receipt = h.apply()
        val result = h.repository.deleteRoutine(h.document().generation, receipt.routineId) as RepositoryResult.Success
        assertTrue(result.value.progressionReceipts.none { it.routineId == receipt.routineId })
        assertTrue(result.value.partialSessions.none { it.routineId == receipt.routineId })
        assertTrue(result.value.history.any { it.snapshot.id == receipt.routineId })
    }
}
