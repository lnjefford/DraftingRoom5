package dev.draftingroom5

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Corrupt(val error: Throwable) : LoadState<Nothing>
    data class Failed(val error: IOException) : LoadState<Nothing>
}

internal sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Conflict(val currentGeneration: Long) : RepositoryResult<Nothing>
    data class Invalid(val error: IllegalArgumentException) : RepositoryResult<Nothing>
    data class Failed(val error: IOException) : RepositoryResult<Nothing>
}

internal sealed interface SessionRepositoryResult {
    data class Partial(val session: GuidedSession, val created: Boolean, val outputs: List<SessionOutput> = emptyList()) : SessionRepositoryResult
    data class Complete(val history: WorkoutHistoryEntry, val newlyCompleted: Boolean) : SessionRepositoryResult
    data class Conflict(val current: GuidedSession) : SessionRepositoryResult
    data object RefreshRequired : SessionRepositoryResult
    data object Missing : SessionRepositoryResult
    data object AlreadyComplete : SessionRepositoryResult
    data class Invalid(val reason: String) : SessionRepositoryResult
    data class Failed(val error: IOException) : SessionRepositoryResult
}

internal interface DocumentStorage {
    fun exists(): Boolean
    @Throws(IOException::class) fun read(): String
    @Throws(IOException::class) fun write(value: String)
}

internal class AppDocumentStore(context: Context) : DocumentStorage by AtomicJsonStorage(
    File(context.filesDir, "training-current/document.json"),
)

internal class AtomicJsonStorage(baseFile: File) : DocumentStorage {
    private val file = AtomicFile(baseFile)

    override fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    @Throws(IOException::class)
    override fun read(): String = file.openRead().use(::readBoundedCurrentJson)

    @Throws(IOException::class)
    override fun write(value: String) {
        file.baseFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create the current-data directory.")
        }
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.flush()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            if (error is IOException) throw error
            throw IOException("Could not write app data.", error)
        }
    }
}

internal class AppRepository(
    private val store: DocumentStorage,
    private val sessionClock: SessionClock? = null,
) {
    private val mutableState = MutableStateFlow<LoadState<AppDocument>>(LoadState.Loading)
    val state: StateFlow<LoadState<AppDocument>> = mutableState
    private var leaseValue = 0L
    private val processOwnedTimerRuns = mutableSetOf<String>()
    private val pendingSessionOutputs = mutableMapOf<String, List<SessionOutput>>()
    private val silentRecoveryRuns = mutableSetOf<String>()
    private val lastTimerObservations = mutableMapOf<String, Long>()

    // Readers share the committed document. Only explicit recovery reloads revoke leases.
    fun ensureLoaded(): LoadState<AppDocument> = synchronized(processLock) {
        if (mutableState.value is LoadState.Loading) load() else mutableState.value
    }

    fun sessionLease(): SessionLease? = synchronized(processLock) {
        if (mutableState.value is LoadState.Ready) SessionLease(leaseValue) else null
    }

    fun load(): LoadState<AppDocument> = synchronized(processLock) {
        pendingSessionOutputs.clear()
        val loaded = try {
            if (!store.exists()) {
                defaultAppDocument().also { store.write(encodeAppDocument(it)) }
            } else {
                val encoded = store.read()
                decodeAppDocument(encoded).let { decoded ->
                    val normalized = reconcileLoadedSessions(decoded)
                    val normalizedEncoded = encodeAppDocument(normalized)
                    if (normalizedEncoded != encoded) store.write(normalizedEncoded)
                    normalized
                }
            }
        } catch (error: FileNotFoundException) {
            return@synchronized LoadState.Failed(error).also { mutableState.value = it }
        } catch (error: IOException) {
            return@synchronized LoadState.Failed(error).also { mutableState.value = it }
        } catch (error: IllegalArgumentException) {
            return@synchronized LoadState.Corrupt(error).also { mutableState.value = it }
        }
        nextLease()
        processOwnedTimerRuns.clear()
        LoadState.Ready(loaded).also { mutableState.value = it }
    }

    fun currentOrDefaults(): AppDocument = (state.value as? LoadState.Ready)?.value
        ?: (load() as? LoadState.Ready)?.value
        ?: defaultAppDocument()

    fun update(expectedGeneration: Long, transform: (AppDocument) -> AppDocument): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        if (current.generation != expectedGeneration) return@synchronized RepositoryResult.Conflict(current.generation)
        val candidate: AppDocument
        val encoded: String
        try {
            require(current.generation < Long.MAX_VALUE) { "Document generation overflows." }
            candidate = transform(current).copy(generation = current.generation + 1)
            encoded = encodeAppDocument(candidate)
        } catch (error: IllegalArgumentException) {
            return@synchronized RepositoryResult.Invalid(error)
        }
        try {
            store.write(encoded)
        } catch (error: IOException) {
            return@synchronized RepositoryResult.Failed(error)
        }
        mutableState.value = LoadState.Ready(candidate)
        RepositoryResult.Success(candidate)
    }

    fun deferOccurrence(occurrence: OccurrenceKey, today: java.time.LocalDate): RepositoryResult<AppDocument> =
        changeOccurrence(occurrence, today, OccurrenceDisposition.DEFERRED)

    fun skipOccurrence(occurrence: OccurrenceKey, today: java.time.LocalDate): RepositoryResult<AppDocument> =
        changeOccurrence(occurrence, today, OccurrenceDisposition.SKIPPED)

    private fun changeOccurrence(
        occurrence: OccurrenceKey,
        today: java.time.LocalDate,
        disposition: OccurrenceDisposition,
    ): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        if (current.history.any { it.occurrence == occurrence } || current.partialSessions.any { it.occurrence == occurrence }) {
            return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Started or completed occurrences cannot be moved or skipped."))
        }
        val existing = current.occurrenceExceptions.firstOrNull { it.occurrence == occurrence }
        if (existing?.disposition == disposition) return@synchronized RepositoryResult.Success(current)
        update(current.generation) {
            require(existing == null) { "Undo the existing exception before changing it." }
            require(occurrence.scheduledDate == today && current.occurrenceDate(occurrence) == today) {
                "Only today's scheduled occurrence can be moved or skipped."
            }
            require(disposition != OccurrenceDisposition.DEFERRED || today < java.time.LocalDate.MAX) { "Tomorrow is outside the supported date range." }
            it.copy(occurrenceExceptions = it.occurrenceExceptions + OccurrenceException(occurrence, disposition,
                if (disposition == OccurrenceDisposition.DEFERRED) today.plusDays(1) else null))
        }
    }

    /** Compare the exact saved exception so stale Undo cannot alter a different disposition. */
    fun undoOccurrenceException(exception: OccurrenceException): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        val existing = current.occurrenceExceptions.firstOrNull { it.occurrence == exception.occurrence }
            ?: return@synchronized RepositoryResult.Success(current)
        update(current.generation) {
            require(existing == exception) { "Occurrence exception changed; refresh before undoing." }
            require(it.partialSessions.none { session -> session.occurrence == exception.occurrence } &&
                it.history.none { history -> history.occurrence == exception.occurrence }) { "Started or completed occurrences cannot be moved back." }
            it.copy(occurrenceExceptions = it.occurrenceExceptions - existing)
        }
    }

    /** Records only the selected linked occurrence after Android has accepted its launch. */
    fun completeLinkedOccurrence(occurrence: OccurrenceKey, routineId: String, atMillis: Long): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        if (current.history.any { it.occurrence == occurrence }) return@synchronized RepositoryResult.Success(current)
        val entry = current.plan.schedule.firstOrNull { it.id == occurrence.scheduleEntryId }
        val routine = current.plan.routines.firstOrNull { it.id == routineId }
        if (entry == null || routine == null || entry.routineId != routineId || current.occurrenceDate(occurrence) == null ||
            routine.execution != RoutineExecution.LINKED_APP || current.partialSessions.any { it.occurrence == occurrence } || atMillis < 0
        ) return@synchronized RepositoryResult.Invalid(IllegalArgumentException("This linked occurrence is no longer available."))
        val history = WorkoutHistoryEntry(UUID.randomUUID().toString(), occurrence, routine, atMillis, atMillis,
            requireNotNull(current.occurrenceDate(occurrence)))
        update(current.generation) { it.copy(history = it.history + history) }
    }

    /** Undo is restricted to linked launches, leaving guided completion immutable. */
    fun undoLinkedOccurrence(occurrence: OccurrenceKey): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        val history = current.history.firstOrNull { it.occurrence == occurrence }
            ?: return@synchronized RepositoryResult.Success(current)
        if (history.snapshot.execution != RoutineExecution.LINKED_APP) {
            return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Guided completion cannot be undone here."))
        }
        update(current.generation) { it.copy(history = it.history.filterNot { record -> record.occurrence == occurrence }).pruneOccurrenceExceptions() }
    }

    fun restore(document: AppDocument): RepositoryResult<AppDocument> = synchronized(processLock) {
        val currentGeneration = (mutableState.value as? LoadState.Ready)?.value?.generation ?: 0L
        val candidate: AppDocument
        val encoded: String
        try {
            validateAppDocument(document)
            require(currentGeneration < Long.MAX_VALUE) { "Document generation overflows." }
            candidate = document.copy(generation = currentGeneration + 1,
                partialSessions = document.partialSessions.map { it.copy(timer = SessionTimer()) })
            encoded = encodeAppDocument(candidate)
        } catch (error: IllegalArgumentException) {
            return@synchronized RepositoryResult.Invalid(error)
        }
        try {
            store.write(encoded)
        } catch (error: IOException) {
            return@synchronized RepositoryResult.Failed(error)
        }
        mutableState.value = LoadState.Ready(candidate)
        nextLease()
        processOwnedTimerRuns.clear()
        pendingSessionOutputs.clear()
        RepositoryResult.Success(candidate)
    }

    fun resetToDefaults(): RepositoryResult<AppDocument> = restore(defaultAppDocument())

    fun replacePlan(expectedGeneration: Long, plan: TrainingPlan, acknowledgeSavedSessions: Boolean = false) = update(expectedGeneration) { current ->
        validatePlanEdit(current.plan, plan)
        val changedIds = current.plan.routines.filter { old -> plan.routines.firstOrNull { it.id == old.id } != old }.map { it.id }.toSet()
        require(acknowledgeSavedSessions || current.partialSessions.none { it.routineId in changedIds }) {
            "Confirm changes to routines with saved sessions before saving."
        }
        val retainedRoutineIds = plan.routines.mapTo(hashSetOf()) { it.id }
        current.copy(
            plan = plan,
            partialSessions = current.partialSessions.filter { it.routineId in retainedRoutineIds },
            progressionReceipts = current.progressionReceipts.filter { it.routineId in retainedRoutineIds },
        ).pruneOccurrenceExceptions()
    }

    fun deleteRoutine(expectedGeneration: Long, routineId: String) = update(expectedGeneration) {
        require(it.plan.routines.any { routine -> routine.id == routineId }) { "Routine does not exist." }
        it.copy(
            plan = it.plan.removeRoutine(routineId),
            partialSessions = it.partialSessions.filterNot { session -> session.routineId == routineId },
            progressionReceipts = it.progressionReceipts.filterNot { receipt -> receipt.routineId == routineId },
        ).pruneOccurrenceExceptions()
    }

    fun deleteScheduleEntry(expectedGeneration: Long, scheduleEntryId: String) = update(expectedGeneration) {
        require(it.plan.schedule.any { entry -> entry.id == scheduleEntryId }) { "Scheduled item does not exist." }
        it.copy(plan = it.plan.removeScheduleEntry(scheduleEntryId)).pruneOccurrenceExceptions()
    }

    fun resetPlan(expectedGeneration: Long): RepositoryResult<AppDocument> = synchronized(processLock) {
        val result = update(expectedGeneration) {
            it.copy(plan = defaultTrainingPlan(), partialSessions = emptyList(), history = emptyList(), occurrenceExceptions = emptyList(), progressionReceipts = emptyList())
        }
        if (result is RepositoryResult.Success) {
            nextLease()
            processOwnedTimerRuns.clear()
            pendingSessionOutputs.clear()
        }
        result
    }

    /** The receipt and the live routine are published only after one successful atomic write. */
    fun applyProgression(lease: SessionLease, request: ProgressionRequest): RepositoryResult<ProgressionReceipt> = synchronized(processLock) {
        val current = readyForSession(lease)
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Refresh the workout before progressing."))
        current.progressionReceipts.firstOrNull { it.sessionId == request.sessionId && it.before.id == request.exerciseId }?.let {
            if (it.choice != request.choice || it.manualPrescription != request.manualPrescription) {
                return@synchronized RepositoryResult.Conflict(current.generation)
            }
            return@synchronized RepositoryResult.Success(it)
        }
        val offer = current.progressionOffer(request.sessionId, request.exerciseId)
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("This completion cannot progress the current exercise."))
        if (offer.sessionRevision != request.expectedSessionRevision || offer.routineRevision != request.expectedRoutineRevision) {
            return@synchronized RepositoryResult.Conflict(current.generation)
        }
        val option = when (request.choice) {
            ProgressionChoice.CUSTOM -> if (request.manualPrescription == null) offer.options.firstOrNull() else null
            ProgressionChoice.MANUAL -> request.manualPrescription?.let { prescription ->
                val source = current.partialSessions.single { it.id == request.sessionId }.snapshot.exercises.single { it.id == request.exerciseId }
                ProgressionOption(ProgressionChoice.MANUAL, source.withPrescription(prescription))
            }
        }
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("This progression choice is unavailable."))
        val session = current.partialSessions.single { it.id == request.sessionId }
        val routine = current.plan.routines.single { it.id == session.routineId }
        val index = routine.exercises.indexOfFirst { it.id == request.exerciseId }
        val receipt = ProgressionReceipt(session.id, routine.id, routine.exercises[index], request.choice, routine.revision + 1, manualPrescription = request.manualPrescription)
        val progressed = routine.copy(revision = receipt.appliedRevision, exercises =
            routine.exercises.take(index) + option.source + option.additions + routine.exercises.drop(index + 1))
        when (val result = update(current.generation) { it.copy(
            plan = it.plan.copy(routines = it.plan.routines.map { old -> if (old.id == routine.id) progressed else old }),
            progressionReceipts = it.progressionReceipts + receipt,
        ) }) {
            is RepositoryResult.Success -> RepositoryResult.Success(receipt)
            is RepositoryResult.Conflict -> result
            is RepositoryResult.Invalid -> result
            is RepositoryResult.Failed -> result
        }
    }

    /** Routine edits/other progression expire immediate Undo; session and preference writes do not. */
    fun undoProgression(lease: SessionLease, sessionId: String, exerciseId: String): RepositoryResult<ProgressionReceipt> = synchronized(processLock) {
        val current = readyForSession(lease)
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Refresh the workout before undoing."))
        val receipt = current.progressionReceipts.firstOrNull { it.sessionId == sessionId && it.before.id == exerciseId }
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Progression receipt is missing."))
        if (receipt.undone) return@synchronized RepositoryResult.Success(receipt)
        val routine = current.plan.routines.firstOrNull { it.id == receipt.routineId }
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Routine no longer exists."))
        if (routine.revision != receipt.appliedRevision || routine.revision == Long.MAX_VALUE) {
            return@synchronized RepositoryResult.Conflict(current.generation)
        }
        val option = receipt.option()
        if (routine.exercises.firstOrNull { it.id == exerciseId } != option.source ||
            option.additions.any { added -> routine.exercises.firstOrNull { it.id == added.id } != added }) {
            return@synchronized RepositoryResult.Invalid(IllegalArgumentException("Progressed exercises changed; Undo has expired."))
        }
        val addedIds = option.additions.mapTo(hashSetOf()) { it.id }
        val restored = routine.copy(revision = routine.revision + 1, exercises = routine.exercises
            .filterNot { it.id in addedIds }.map { if (it.id == exerciseId) receipt.before else it })
        val undone = receipt.copy(undone = true)
        when (val result = update(current.generation) { it.copy(
            plan = it.plan.copy(routines = it.plan.routines.map { old -> if (old.id == routine.id) restored else old }),
            progressionReceipts = it.progressionReceipts.map { old -> if (old == receipt) undone else old },
        ) }) {
            is RepositoryResult.Success -> RepositoryResult.Success(undone)
            is RepositoryResult.Conflict -> result
            is RepositoryResult.Invalid -> result
            is RepositoryResult.Failed -> result
        }
    }

    fun openGuidedSession(
        lease: SessionLease,
        occurrence: OccurrenceKey,
        displayedRoutineId: String,
        displayedRoutineRevision: Long,
        newSessionId: String,
        existingSessionId: String? = null,
    ): SessionRepositoryResult = synchronized(processLock) {
        val current = readyForSession(lease) ?: return@synchronized leaseFailure(lease)
        if (existingSessionId != null) {
            current.history.firstOrNull { it.id == existingSessionId && it.occurrence == occurrence }?.let {
                return@synchronized SessionRepositoryResult.Complete(it, newlyCompleted = false)
            }
            val existing = current.partialSessions.firstOrNull { it.id == existingSessionId && it.occurrence == occurrence }
                ?: return@synchronized SessionRepositoryResult.Missing
            return@synchronized reconcileExisting(current, existing, visible = false)
        }
        current.history.firstOrNull { it.occurrence == occurrence }?.let {
            return@synchronized SessionRepositoryResult.Complete(it, newlyCompleted = false)
        }
        current.partialSessions.firstOrNull { it.occurrence == occurrence }?.let { existing ->
            return@synchronized reconcileExisting(current, existing, visible = false)
        }
        val entry = current.plan.schedule.firstOrNull { it.id == occurrence.scheduleEntryId }
            ?: return@synchronized SessionRepositoryResult.Invalid("Scheduled item no longer exists.")
        val routine = current.plan.routines.firstOrNull { it.id == entry.routineId }
            ?: return@synchronized SessionRepositoryResult.Invalid("Routine no longer exists.")
        if (routine.id != displayedRoutineId || routine.revision != displayedRoutineRevision ||
            routine.execution != RoutineExecution.GUIDED || current.occurrenceDate(occurrence) == null) {
            return@synchronized SessionRepositoryResult.Invalid("Displayed occurrence is stale or is not guided.")
        }
        if (newSessionId.isBlank() || newSessionId.length > 128) return@synchronized SessionRepositoryResult.Invalid("Session ID is invalid.")
        if (current.partialSessions.any { it.id == newSessionId } || current.history.any { it.id == newSessionId } ||
            current.progressionReceipts.any { it.sessionId == newSessionId }) {
            return@synchronized SessionRepositoryResult.Invalid("Session ID already exists.")
        }
        val now = sampleClock() ?: return@synchronized SessionRepositoryResult.Invalid("Session clock is unavailable.")
        val created = GuidedSession(
            id = newSessionId,
            occurrence = occurrence,
            routineId = routine.id,
            snapshot = routine,
            focusedExerciseId = routine.exercises.first().id,
            completedSets = routine.exercises.associate { it.id to 0 },
            timer = SessionTimer(),
            startedAtMillis = now.wallMillis.coerceAtLeast(0L),
            updatedAtMillis = now.wallMillis.coerceAtLeast(0L),
            eventRevision = 0,
            effectiveDate = requireNotNull(current.occurrenceDate(occurrence)),
        )
        commitSessionDocument(current.copy(partialSessions = current.partialSessions + created))?.let { failure ->
            return@synchronized failure
        }
        SessionRepositoryResult.Partial(created, created = true)
    }

    fun applySessionEvent(
        lease: SessionLease,
        sessionId: String,
        expectedEventRevision: Long,
        event: SessionEvent,
    ): SessionRepositoryResult = synchronized(processLock) {
        val current = readyForSession(lease) ?: return@synchronized leaseFailure(lease)
        if (current.history.any { it.id == sessionId }) return@synchronized SessionRepositoryResult.AlreadyComplete
        val existing = current.partialSessions.firstOrNull { it.id == sessionId }
            ?: return@synchronized SessionRepositoryResult.Missing
        if (existing.eventRevision != expectedEventRevision) return@synchronized SessionRepositoryResult.Conflict(existing)
        val now = sampleClock() ?: return@synchronized SessionRepositoryResult.Invalid("Session clock is unavailable.")
        val effectiveEvent = when (event) {
            is SessionEvent.Reconcile -> event.copy(
                sameProcess = event.expectedRunId in processOwnedTimerRuns,
                visibleForFeedback = event.visibleForFeedback && event.expectedRunId !in silentRecoveryRuns,
            )
            is SessionEvent.SaveCheckpoint -> event.copy(sameProcess = existing.timer.runId in processOwnedTimerRuns)
            else -> event
        }
        val previousElapsed = existing.timer.runId?.let(lastTimerObservations::get)
        val reduction = reduceGuidedSession(existing, effectiveEvent, now, previousElapsed)
        existing.timer.runId?.let { lastTimerObservations[it] = maxOf(previousElapsed ?: 0L, now.elapsedMillis) }
        when (reduction) {
            is SessionReduction.Rejected -> SessionRepositoryResult.Invalid(reduction.reason)
            is SessionReduction.Unchanged -> SessionRepositoryResult.Partial(
                existing,
                created = false,
                outputs = pendingSessionOutputs.remove(existing.id).orEmpty(),
            )
            is SessionReduction.Changed -> {
                val candidate = current.copy(partialSessions = current.partialSessions.map {
                    if (it.id == sessionId) reduction.session else it
                })
                commitSessionDocument(candidate)?.let {
                    existing.timer.runId?.let(silentRecoveryRuns::add)
                    return@synchronized it
                }
                existing.timer.runId?.let(silentRecoveryRuns::remove)
                when (event) {
                    is SessionEvent.StartTimer -> {
                        existing.timer.runId?.let(processOwnedTimerRuns::remove)
                        processOwnedTimerRuns += event.newRunId
                    }
                    is SessionEvent.CancelTimer, is SessionEvent.CompleteSet, is SessionEvent.Focus,
                    is SessionEvent.UndoLastSet -> if (existing.timer.runId != reduction.session.timer.runId) {
                        existing.timer.runId?.let(processOwnedTimerRuns::remove)
                    }
                    else -> Unit
                }
                SessionRepositoryResult.Partial(
                    reduction.session,
                    created = false,
                    outputs = pendingSessionOutputs.remove(existing.id).orEmpty() + reduction.outputs,
                )
            }
        }
    }

    fun restartGuidedSession(
        lease: SessionLease,
        sessionId: String,
        expectedEventRevision: Long,
        expectedLiveRoutineRevision: Long,
        newSessionId: String,
    ): SessionRepositoryResult = synchronized(processLock) {
        val current = readyForSession(lease) ?: return@synchronized leaseFailure(lease)
        val existing = current.partialSessions.firstOrNull { it.id == sessionId }
            ?: return@synchronized if (current.history.any { it.id == sessionId }) SessionRepositoryResult.AlreadyComplete else SessionRepositoryResult.Missing
        if (existing.eventRevision != expectedEventRevision) return@synchronized SessionRepositoryResult.Conflict(existing)
        val live = current.plan.routines.firstOrNull { it.id == existing.routineId }
            ?: return@synchronized SessionRepositoryResult.Invalid("Routine no longer exists.")
        if (live.execution != RoutineExecution.GUIDED || live.revision != expectedLiveRoutineRevision) {
            return@synchronized SessionRepositoryResult.Invalid("Routine changed; refresh restart confirmation.")
        }
        if (newSessionId.isBlank() || newSessionId.length > 128 || current.partialSessions.any { it.id == newSessionId } ||
            current.history.any { it.id == newSessionId } || current.progressionReceipts.any { it.sessionId == newSessionId }) {
            return@synchronized SessionRepositoryResult.Invalid("New session ID is invalid or already used.")
        }
        val now = sampleClock() ?: return@synchronized SessionRepositoryResult.Invalid("Session clock is unavailable.")
        val restarted = GuidedSession(newSessionId, existing.occurrence, live.id, live, live.exercises.first().id,
            live.exercises.associate { it.id to 0 }, SessionTimer(), now.wallMillis, now.wallMillis, 0, existing.effectiveDate)
        commitSessionDocument(current.copy(partialSessions = current.partialSessions.map { if (it.id == sessionId) restarted else it }))
            ?.let { return@synchronized it }
        existing.timer.runId?.let(processOwnedTimerRuns::remove)
        SessionRepositoryResult.Partial(restarted, created = true)
    }

    fun finishGuidedSession(
        lease: SessionLease,
        sessionId: String,
        expectedEventRevision: Long,
    ): SessionRepositoryResult = synchronized(processLock) {
        val current = readyForSession(lease) ?: return@synchronized leaseFailure(lease)
        current.history.firstOrNull { it.id == sessionId }?.let {
            return@synchronized SessionRepositoryResult.Complete(it, newlyCompleted = false)
        }
        val existing = current.partialSessions.firstOrNull { it.id == sessionId }
            ?: return@synchronized SessionRepositoryResult.Missing
        if (existing.eventRevision != expectedEventRevision) return@synchronized SessionRepositoryResult.Conflict(existing)
        if (existing.durableState() != DurableSessionState.READY_TO_FINISH) {
            return@synchronized SessionRepositoryResult.Invalid("Every set must be completed before finishing.")
        }
        val now = sampleClock() ?: return@synchronized SessionRepositoryResult.Invalid("Session clock is unavailable.")
        val history = WorkoutHistoryEntry(existing.id, existing.occurrence, existing.snapshot, existing.startedAtMillis,
            maxOf(existing.updatedAtMillis, now.wallMillis), existing.effectiveDate)
        val candidate = current.copy(
            partialSessions = current.partialSessions.filterNot { it.id == sessionId },
            history = current.history + history,
        )
        commitSessionDocument(candidate)?.let { return@synchronized it }
        existing.timer.runId?.let(processOwnedTimerRuns::remove)
        SessionRepositoryResult.Complete(history, newlyCompleted = true)
    }

    private fun reconcileExisting(current: AppDocument, existing: GuidedSession, visible: Boolean): SessionRepositoryResult {
        if (existing.timer.phase == TimerPhase.IDLE) return SessionRepositoryResult.Partial(
            existing,
            created = false,
            outputs = pendingSessionOutputs.remove(existing.id).orEmpty(),
        )
        val clock = sampleClock() ?: return SessionRepositoryResult.Invalid("Session clock is unavailable.")
        val runId = requireNotNull(existing.timer.runId)
        val reduction = reduceGuidedSession(existing, SessionEvent.Reconcile(
            expectedRunId = runId,
            sameProcess = runId in processOwnedTimerRuns,
            visibleForFeedback = visible,
        ), clock)
        return when (reduction) {
            is SessionReduction.Rejected -> SessionRepositoryResult.Invalid(reduction.reason)
            is SessionReduction.Unchanged -> SessionRepositoryResult.Partial(existing, created = false)
            is SessionReduction.Changed -> {
                val candidate = current.copy(partialSessions = current.partialSessions.map { if (it.id == existing.id) reduction.session else it })
                commitSessionDocument(candidate)?.let { return it }
                SessionRepositoryResult.Partial(reduction.session, created = false, outputs = reduction.outputs)
            }
        }
    }

    private fun reconcileLoadedSessions(document: AppDocument): AppDocument {
        val clock = sessionClock?.sample() ?: return document
        var changed = false
        val sessions = document.partialSessions.map { session ->
            if (session.timer.phase == TimerPhase.IDLE) session else {
                val reduction = reduceGuidedSession(session, SessionEvent.Reconcile(
                    requireNotNull(session.timer.runId), sameProcess = false, visibleForFeedback = false), clock)
                if (reduction is SessionReduction.Changed) {
                    changed = true
                    if (reduction.outputs.isNotEmpty()) pendingSessionOutputs[session.id] = reduction.outputs
                    reduction.session
                } else session
            }
        }
        if (!changed) return document
        require(document.generation < Long.MAX_VALUE) { "Document generation overflows." }
        return document.copy(generation = document.generation + 1, partialSessions = sessions)
    }

    private fun readyForSession(lease: SessionLease): AppDocument? =
        (mutableState.value as? LoadState.Ready)?.value?.takeIf { lease.value == leaseValue }

    private fun leaseFailure(lease: SessionLease): SessionRepositoryResult =
        if (lease.value != leaseValue) SessionRepositoryResult.RefreshRequired
        else SessionRepositoryResult.Invalid("App data is not ready.")

    private fun sampleClock(): SessionClockSample? = sessionClock?.sample()?.takeIf {
        it.elapsedMillis >= 0 && it.wallMillis >= 0 && (it.bootCount == null || it.bootCount >= 0)
    }

    private fun commitSessionDocument(document: AppDocument): SessionRepositoryResult.Failed? {
        val current = (mutableState.value as LoadState.Ready).value
        if (document == current) return null
        if (current.generation == Long.MAX_VALUE) return SessionRepositoryResult.Failed(IOException("Document generation overflows."))
        val candidate = document.copy(generation = current.generation + 1)
        val encoded = try { encodeAppDocument(candidate) } catch (error: IllegalArgumentException) {
            return SessionRepositoryResult.Failed(IOException("Session data is invalid.", error))
        }
        try { store.write(encoded) } catch (error: IOException) { return SessionRepositoryResult.Failed(error) }
        mutableState.value = LoadState.Ready(candidate)
        return null
    }

    private fun nextLease() {
        silentRecoveryRuns.clear()
        lastTimerObservations.clear()
        leaseValue = if (leaseValue == Long.MAX_VALUE) 1L else leaseValue + 1L
    }

    companion object {
        private val processLock = Any()
        @Volatile private var instance: AppRepository? = null

        // Activity, settings, backup managers and workers observe the same committed generation.
        fun get(context: Context): AppRepository = instance ?: synchronized(processLock) {
            instance ?: AppRepository(
                AppDocumentStore(context.applicationContext),
                AndroidSessionClock(context.applicationContext),
            ).also { instance = it }
        }
    }
}
