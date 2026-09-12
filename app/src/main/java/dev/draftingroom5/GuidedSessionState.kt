package dev.draftingroom5

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.max

internal data class SessionClockSample(
    val elapsedMillis: Long,
    val wallMillis: Long,
    val bootCount: Long?,
)

internal fun interface SessionClock {
    fun sample(): SessionClockSample
}

internal class AndroidSessionClock(private val context: Context) : SessionClock {
    override fun sample() = SessionClockSample(
        elapsedMillis = SystemClock.elapsedRealtime(),
        wallMillis = System.currentTimeMillis().coerceAtLeast(0L),
        bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toLong()
        }.getOrNull(),
    )
}

internal data class SessionLease(val value: Long)

internal enum class DurableSessionState { ACTIVE, READY_TO_FINISH }

internal fun GuidedSession.durableState(): DurableSessionState =
    if (snapshot.exercises.all { completedSets.getValue(it.id) == it.setCount }) {
        DurableSessionState.READY_TO_FINISH
    } else {
        DurableSessionState.ACTIVE
    }

internal sealed interface SessionEvent {
    data class Focus(val exerciseId: String) : SessionEvent
    data class CompleteSet(val exerciseId: String, val setNumber: Int) : SessionEvent
    data class UndoLastSet(val exerciseId: String, val expectedCompletedCount: Int) : SessionEvent
    data class StartTimer(val newRunId: String, val expectedRunId: String? = null) : SessionEvent
    data class CancelTimer(val expectedRunId: String) : SessionEvent
    data class Reconcile(
        val expectedRunId: String,
        val sameProcess: Boolean,
        val visibleForFeedback: Boolean,
    ) : SessionEvent
    data class SaveCheckpoint(val sameProcess: Boolean) : SessionEvent
}

internal sealed interface SessionOutput {
    data class TimerCue(val runId: String, val ordinal: Int) : SessionOutput
    data object SetCompleted : SessionOutput
    data object TimerCouldNotResume : SessionOutput
}

internal sealed interface SessionReduction {
    data class Changed(val session: GuidedSession, val outputs: List<SessionOutput> = emptyList()) : SessionReduction
    data class Unchanged(val session: GuidedSession) : SessionReduction
    data class Rejected(val reason: String, val session: GuidedSession) : SessionReduction
}

internal fun reduceGuidedSession(
    committed: GuidedSession,
    event: SessionEvent,
    clock: SessionClockSample,
    previousElapsedMillis: Long? = null,
): SessionReduction {
    if (clock.elapsedMillis < 0 || clock.wallMillis < 0 || (clock.bootCount != null && clock.bootCount < 0)) {
        return SessionReduction.Rejected("Clock sample is invalid.", committed)
    }
    val timerEventRunId = when (event) {
        is SessionEvent.CancelTimer -> event.expectedRunId
        is SessionEvent.Reconcile -> event.expectedRunId
        else -> null
    }
    if (timerEventRunId != null && timerEventRunId != committed.timer.runId) {
        return SessionReduction.Rejected("Timer run is stale.", committed)
    }

    val sameProcess = when (event) {
        is SessionEvent.Reconcile -> event.sameProcess
        is SessionEvent.SaveCheckpoint -> event.sameProcess
        else -> true
    }
    val reconciled = reconcileTimer(committed, clock, sameProcess, previousElapsedMillis)
    val effective = reconciled.session
    val suppressTimerCue = event is SessionEvent.Focus || event is SessionEvent.CompleteSet ||
        event is SessionEvent.UndoLastSet || event is SessionEvent.CancelTimer

    val candidate = when (event) {
        is SessionEvent.Focus -> {
            if (effective.snapshot.exercises.none { it.id == event.exerciseId }) {
                return SessionReduction.Rejected("Exercise is missing from the session snapshot.", committed)
            }
            if (event.exerciseId == effective.focusedExerciseId) effective
            else effective.copy(focusedExerciseId = event.exerciseId, timer = SessionTimer())
        }
        is SessionEvent.CompleteSet -> {
            val exercise = effective.snapshot.exercises.firstOrNull { it.id == event.exerciseId }
                ?: return SessionReduction.Rejected("Exercise is missing from the session snapshot.", committed)
            val completed = effective.completedSets.getValue(exercise.id)
            if (exercise.id != effective.focusedExerciseId || event.setNumber != completed + 1 || completed >= exercise.setCount) {
                return SessionReduction.Rejected("Set completion no longer matches current progress.", committed)
            }
            if (effective.timer.phase == TimerPhase.READY) {
                return SessionReduction.Rejected("Complete set is unavailable during Get ready.", committed)
            }
            val counts = effective.completedSets.toMutableMap().apply { this[exercise.id] = completed + 1 }
            val nextFocus = if (completed + 1 < exercise.setCount) exercise.id else {
                val exercises = effective.snapshot.exercises
                val currentIndex = exercises.indexOfFirst { it.id == exercise.id }
                (exercises.drop(currentIndex + 1) + exercises.take(currentIndex + 1))
                    .firstOrNull { counts.getValue(it.id) < it.setCount }?.id ?: exercise.id
            }
            effective.copy(completedSets = counts, focusedExerciseId = nextFocus, timer = SessionTimer())
        }
        is SessionEvent.UndoLastSet -> {
            val exercise = effective.snapshot.exercises.firstOrNull { it.id == event.exerciseId }
                ?: return SessionReduction.Rejected("Exercise is missing from the session snapshot.", committed)
            val completed = effective.completedSets.getValue(exercise.id)
            if (completed <= 0 || event.expectedCompletedCount != completed) {
                return SessionReduction.Rejected("Undo no longer matches current progress.", committed)
            }
            effective.copy(
                completedSets = effective.completedSets.toMutableMap().apply { this[exercise.id] = completed - 1 },
                focusedExerciseId = exercise.id,
                timer = SessionTimer(),
            )
        }
        is SessionEvent.StartTimer -> {
            val exercise = effective.snapshot.exercises.first { it.id == effective.focusedExerciseId }
            if (exercise.timerSeconds == null || effective.completedSets.getValue(exercise.id) >= exercise.setCount ||
                effective.timer.phase == TimerPhase.READY || effective.timer.phase == TimerPhase.RUNNING) {
                return SessionReduction.Rejected("Timer cannot start for the focused exercise.", committed)
            }
            if ((effective.timer.phase == TimerPhase.IDLE && event.expectedRunId != null) ||
                (effective.timer.phase == TimerPhase.FINISHED && event.expectedRunId != effective.timer.runId)) {
                return SessionReduction.Rejected("Timer run is stale.", committed)
            }
            if (event.newRunId == committed.timer.runId || event.newRunId.isBlank() || event.newRunId.length > 128 || clock.elapsedMillis > Long.MAX_VALUE - 10_000L) {
                return SessionReduction.Rejected("Timer identity or deadline is invalid.", committed)
            }
            val ready = clock.elapsedMillis + 10_000L
            val duration = exercise.timerSeconds.toLong() * 1_000L
            if (ready > Long.MAX_VALUE - duration) return SessionReduction.Rejected("Timer deadline overflows.", committed)
            effective.copy(timer = SessionTimer(
                phase = TimerPhase.READY,
                runId = event.newRunId,
                exerciseId = exercise.id,
                setNumber = effective.completedSets.getValue(exercise.id) + 1,
                bootCount = clock.bootCount,
                readyDeadlineElapsedMillis = ready,
                activeDeadlineElapsedMillis = ready + duration,
                lastObservedElapsedMillis = clock.elapsedMillis,
                lastHandledCueOrdinal = 0,
            ))
        }
        is SessionEvent.CancelTimer -> effective.copy(timer = SessionTimer())
        is SessionEvent.Reconcile -> effective
        is SessionEvent.SaveCheckpoint -> if (effective.timer.phase == TimerPhase.IDLE) effective else effective.copy(
            timer = effective.timer.copy(lastObservedElapsedMillis = clock.elapsedMillis)
        )
    }

    val changed = candidate != committed
    if (!changed) return SessionReduction.Unchanged(committed)
    if (committed.eventRevision == Long.MAX_VALUE) return SessionReduction.Rejected("Session revision overflows.", committed)
    val updated = candidate.copy(
        updatedAtMillis = max(committed.updatedAtMillis, clock.wallMillis),
        eventRevision = committed.eventRevision + 1,
    )
    val outputs = buildList {
        if (!suppressTimerCue && reconciled.output != null && event is SessionEvent.Reconcile && event.visibleForFeedback) add(reconciled.output)
        if (reconciled.recovery && event is SessionEvent.Reconcile) add(SessionOutput.TimerCouldNotResume)
        if (event is SessionEvent.StartTimer) add(SessionOutput.TimerCue(event.newRunId, 0))
        if (event is SessionEvent.CompleteSet) add(SessionOutput.SetCompleted)
    }
    return SessionReduction.Changed(updated, outputs)
}

private data class TimerReconciliation(
    val session: GuidedSession,
    val output: SessionOutput.TimerCue? = null,
    val recovery: Boolean = false,
)

private fun reconcileTimer(
    session: GuidedSession,
    clock: SessionClockSample,
    sameProcess: Boolean,
    previousElapsedMillis: Long?,
): TimerReconciliation {
    val timer = session.timer
    if (timer.phase == TimerPhase.IDLE) return TimerReconciliation(session)
    val observation = requireNotNull(timer.lastObservedElapsedMillis)
    val originValid = clock.elapsedMillis >= maxOf(observation, previousElapsedMillis ?: observation) && if (sameProcess) {
        timer.bootCount == null || clock.bootCount == null || timer.bootCount == clock.bootCount
    } else {
        timer.bootCount != null && clock.bootCount != null && timer.bootCount == clock.bootCount
    }
    if (!originValid) return TimerReconciliation(session.copy(timer = SessionTimer()), recovery = true)
    val ready = requireNotNull(timer.readyDeadlineElapsedMillis)
    val active = requireNotNull(timer.activeDeadlineElapsedMillis)
    val ordinal = cueOrdinal(clock.elapsedMillis, ready, active)
    val phase = when {
        clock.elapsedMillis < ready -> TimerPhase.READY
        clock.elapsedMillis < active -> TimerPhase.RUNNING
        else -> TimerPhase.FINISHED
    }
    val oldOrdinal = requireNotNull(timer.lastHandledCueOrdinal)
    if (phase == timer.phase && ordinal == oldOrdinal) return TimerReconciliation(session)
    val updatedTimer = timer.copy(
        phase = phase,
        lastObservedElapsedMillis = clock.elapsedMillis,
        lastHandledCueOrdinal = ordinal,
    )
    return TimerReconciliation(
        session.copy(timer = updatedTimer),
        if (ordinal > oldOrdinal) SessionOutput.TimerCue(requireNotNull(timer.runId), ordinal) else null,
    )
}

private fun cueOrdinal(elapsed: Long, ready: Long, active: Long): Int = when {
    elapsed >= active -> 5
    elapsed >= ready -> 4
    elapsed >= ready - 1_000L -> 3
    elapsed >= ready - 2_000L -> 2
    elapsed >= ready - 3_000L -> 1
    else -> 0
}

internal fun timerDisplaySeconds(timer: SessionTimer, elapsedMillis: Long, configuredSeconds: Int): Int = when (timer.phase) {
    TimerPhase.IDLE -> configuredSeconds
    TimerPhase.READY -> ceilSeconds(requireNotNull(timer.readyDeadlineElapsedMillis) - elapsedMillis).coerceIn(1, 10)
    TimerPhase.RUNNING -> ceilSeconds(requireNotNull(timer.activeDeadlineElapsedMillis) - elapsedMillis).coerceIn(1, configuredSeconds)
    TimerPhase.FINISHED -> 0
}

private fun ceilSeconds(millis: Long): Int {
    if (millis <= 0) return 0
    return ((millis - 1L) / 1_000L + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
