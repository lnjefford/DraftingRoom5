package dev.draftingroom5.watch

import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.draftingroom5.AppDocument
import dev.draftingroom5.AppRepository
import dev.draftingroom5.DashboardSession
import dev.draftingroom5.DurableSessionState
import dev.draftingroom5.GuidedSession
import dev.draftingroom5.LoadState
import dev.draftingroom5.RoutineExecution
import dev.draftingroom5.SessionAction
import dev.draftingroom5.SessionEvent
import dev.draftingroom5.SessionRepositoryResult
import dev.draftingroom5.dashboardSessions
import dev.draftingroom5.durableState
import java.time.LocalDate
import java.util.UUID

/** Phone-side authority for the compact, private watch protocol. */
class PhoneWatchDataService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val commands = mutableListOf<Pair<WatchCommand, Uri>>()
        try {
            dataEvents.forEach { event ->
                val item = event.dataItem
                if (event.type == DataEvent.TYPE_CHANGED && item.uri.path?.startsWith(WATCH_COMMAND_PATH_PREFIX) == true) {
                    runCatching { decodeWatchCommand(checkNotNull(item.data)) }
                        .onSuccess { commands += it to item.uri }
                }
            }
        } finally {
            dataEvents.release()
        }
        commands.sortedBy { it.first.createdAtMillis }.forEach { (command, uri) -> process(command, uri) }
    }

    private fun process(command: WatchCommand, commandUri: Uri) = synchronized(processLock) {
        val acknowledgements = acknowledgedIds().toMutableSet()
        if (command.id !in acknowledgements) {
            val repository = AppRepository.get(applicationContext)
            if (runCatching {
                check(repository.ensureLoaded() is LoadState.Ready) { "Phone data is unavailable." }
                applyCommand(repository, command)
            }.isSuccess) {
                acknowledgements += command.id
                saveAcknowledgedIds(acknowledgements)
            }
        }
        val snapshot = currentSnapshot(
            acknowledgements,
            completedSessionId = command.sessionId.takeIf { command.type == WatchCommandType.FINISH_SESSION },
        )
        val request = PutDataRequest.create(WATCH_SNAPSHOT_PATH)
            .setData(encodeWatchSnapshot(snapshot))
            .setUrgent()
        Wearable.getDataClient(this).putDataItem(request).addOnSuccessListener {
            if (command.id in acknowledgements) Wearable.getDataClient(this).deleteDataItems(commandUri)
        }
    }

    private fun applyCommand(repository: AppRepository, command: WatchCommand) {
        when (command.type) {
            WatchCommandType.SYNC -> Unit
            WatchCommandType.START_TODAY -> ensureTodaySession(repository)
            WatchCommandType.COMPLETE_SET -> {
                val session = findSession(repository, command.sessionId) ?: ensureTodaySession(repository) ?: return
                val exerciseId = command.exerciseId ?: return
                val setNumber = command.setNumber ?: return
                val completed = session.completedSets[exerciseId] ?: return
                if (completed >= setNumber) return
                if (setNumber != completed + 1) return
                repository.sessionLease()?.let { lease ->
                    when (val result = repository.applySessionEvent(
                        lease,
                        session.id,
                        session.eventRevision,
                        SessionEvent.CompleteSet(exerciseId, setNumber),
                    )) {
                        is SessionRepositoryResult.Failed -> throw result.error
                        SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
                        else -> Unit
                    }
                }
            }
            WatchCommandType.FINISH_SESSION -> {
                val session = findSession(repository, command.sessionId) ?: return
                if (session.durableState() != DurableSessionState.READY_TO_FINISH) return
                repository.sessionLease()?.let { lease ->
                    when (val result = repository.finishGuidedSession(lease, session.id, session.eventRevision)) {
                        is SessionRepositoryResult.Failed -> throw result.error
                        SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun ensureTodaySession(repository: AppRepository): GuidedSession? {
        findSession(repository, null)?.let { return it }
        val document = (repository.state.value as? LoadState.Ready)?.value ?: return null
        val candidate = todayCandidate(document) ?: return null
        if (candidate.action == SessionAction.RESUME) {
            return document.partialSessions.firstOrNull { it.occurrence == candidate.occurrence }
        }
        val lease = repository.sessionLease() ?: return null
        return when (val result = repository.openGuidedSession(
            lease = lease,
            occurrence = candidate.occurrence,
            displayedRoutineId = candidate.routine.id,
            displayedRoutineRevision = candidate.routine.revision,
            newSessionId = UUID.randomUUID().toString(),
        )) {
            is SessionRepositoryResult.Partial -> result.session
            is SessionRepositoryResult.Conflict -> result.current
            is SessionRepositoryResult.Failed -> throw result.error
            SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
            else -> null
        }
    }

    private fun findSession(repository: AppRepository, requestedId: String?): GuidedSession? {
        val document = (repository.state.value as? LoadState.Ready)?.value ?: return null
        return if (requestedId != null) {
            document.partialSessions.firstOrNull { it.id == requestedId }
        } else {
            document.partialSessions.filter { it.effectiveDate == LocalDate.now() }.maxByOrNull { it.updatedAtMillis }
        }
    }

    private fun currentSnapshot(acknowledgements: Set<String>, completedSessionId: String? = null): WatchSnapshot {
        val repository = AppRepository.get(applicationContext)
        val document = (repository.ensureLoaded() as? LoadState.Ready)?.value
            ?: return emptySnapshot(WatchWorkoutStatus.ERROR, acknowledgements, "Phone data is unavailable.")
        val today = LocalDate.now()
        val preferredHistory = completedSessionId?.let { id -> document.history.firstOrNull { it.id == id } }
        if (preferredHistory != null) return historySnapshot(document, preferredHistory, acknowledgements)
        val session = document.partialSessions.filter { it.effectiveDate == today }.maxByOrNull { it.updatedAtMillis }
        if (session != null) return sessionSnapshot(document, session, acknowledgements)
        val candidate = todayCandidate(document)
        if (candidate != null) return routineSnapshot(document, candidate, acknowledgements)
        val todayHistory = document.history.filter { it.effectiveDate == today }.maxByOrNull { it.completedAtMillis }
        if (todayHistory != null && todayHistory.snapshot.execution == RoutineExecution.GUIDED) {
            return historySnapshot(document, todayHistory, acknowledgements)
        }
        return emptySnapshot(WatchWorkoutStatus.NONE, acknowledgements, "No guided workout today.", document.generation)
    }

    private fun todayCandidate(document: AppDocument): DashboardSession? =
        dashboardSessions(document, LocalDate.now())
            .filter { it.routine.execution == RoutineExecution.GUIDED && it.action != SessionAction.DONE }
            .sortedBy { if (it.action == SessionAction.RESUME) 0 else 1 }
            .firstOrNull()

    private fun routineSnapshot(
        document: AppDocument,
        candidate: DashboardSession,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = WatchWorkoutStatus.AVAILABLE,
        generation = document.generation,
        eventRevision = 0,
        sessionId = null,
        routineId = candidate.routine.id,
        routineName = candidate.routine.name,
        scheduleEntryId = candidate.occurrence.scheduleEntryId,
        scheduledDate = candidate.occurrence.scheduledDate.toString(),
        focusedExerciseId = candidate.routine.exercises.first().id,
        exercises = candidate.routine.exercises.map { it.toWatchExercise(0) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
    )

    private fun sessionSnapshot(
        document: AppDocument,
        session: GuidedSession,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = if (session.durableState() == DurableSessionState.READY_TO_FINISH) {
            WatchWorkoutStatus.READY_TO_FINISH
        } else WatchWorkoutStatus.ACTIVE,
        generation = document.generation,
        eventRevision = session.eventRevision,
        sessionId = session.id,
        routineId = session.routineId,
        routineName = session.snapshot.name,
        scheduleEntryId = session.occurrence.scheduleEntryId,
        scheduledDate = session.occurrence.scheduledDate.toString(),
        focusedExerciseId = session.focusedExerciseId,
        exercises = session.snapshot.exercises.map { it.toWatchExercise(session.completedSets.getValue(it.id)) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = session.updatedAtMillis,
    )

    private fun dev.draftingroom5.Exercise.toWatchExercise(completed: Int) = WatchExercise(
        id, name, notes, setCount, reps, durationSeconds, weightPounds, artworkId, completed,
    )

    private fun historySnapshot(
        document: AppDocument,
        history: dev.draftingroom5.WorkoutHistoryEntry,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = WatchWorkoutStatus.COMPLETE,
        generation = document.generation,
        eventRevision = 0,
        sessionId = history.id,
        routineId = history.snapshot.id,
        routineName = history.snapshot.name,
        scheduleEntryId = history.occurrence.scheduleEntryId,
        scheduledDate = history.occurrence.scheduledDate.toString(),
        focusedExerciseId = history.snapshot.exercises.lastOrNull()?.id,
        exercises = history.snapshot.exercises.map { it.toWatchExercise(it.setCount) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = history.completedAtMillis,
    )

    private fun emptySnapshot(
        status: WatchWorkoutStatus,
        acknowledgements: Set<String>,
        message: String,
        generation: Long = 0,
    ) = WatchSnapshot(
        status = status,
        generation = generation,
        eventRevision = 0,
        sessionId = null,
        routineId = null,
        routineName = null,
        scheduleEntryId = null,
        scheduledDate = null,
        focusedExerciseId = null,
        exercises = emptyList(),
        hapticsEnabled = false,
        acknowledgedCommandIds = acknowledgements,
        message = message,
        updatedAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
    )

    private fun acknowledgedIds(): Set<String> = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        .getStringSet(ACKNOWLEDGED, emptySet()).orEmpty()

    private fun saveAcknowledgedIds(ids: Set<String>) {
        val retained = ids.toList().takeLast(MAX_ACKNOWLEDGEMENTS).toSet()
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putStringSet(ACKNOWLEDGED, retained).apply()
    }

    private companion object {
        const val PREFERENCES = "watch_sync"
        const val ACKNOWLEDGED = "acknowledged_commands"
        const val MAX_ACKNOWLEDGEMENTS = 128
        val processLock = Any()
    }
}
