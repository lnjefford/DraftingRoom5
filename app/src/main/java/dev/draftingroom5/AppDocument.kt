package dev.draftingroom5

internal const val APP_DOCUMENT_FORMAT = "draftingroom5.current"
internal const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024

internal data class AppPreferences(
    val dashboardLayout: DashboardLayout = DashboardLayout(),
    val healthDateRange: HealthDateRange = HealthDateRange.MONTH,
    val hapticsEnabled: Boolean = true,
    val voice: VoiceAnnouncementSettings = VoiceAnnouncementSettings(),
    val automaticBackupsEnabled: Boolean = true,
)

internal enum class TimerPhase { IDLE, READY, RUNNING, FINISHED }

internal data class SessionTimer(
    val phase: TimerPhase = TimerPhase.IDLE,
    val runId: String? = null,
    val exerciseId: String? = null,
    val setNumber: Int? = null,
    val bootCount: Long? = null,
    val readyDeadlineElapsedMillis: Long? = null,
    val activeDeadlineElapsedMillis: Long? = null,
    val lastObservedElapsedMillis: Long? = null,
    val lastHandledCueOrdinal: Int? = null,
)

internal data class GuidedSession(
    val id: String,
    val occurrence: OccurrenceKey,
    val routineId: String,
    val snapshot: Routine,
    val focusedExerciseId: String,
    val completedSets: Map<String, Int>,
    val timer: SessionTimer,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val eventRevision: Long,
)

internal data class AppDocument(
    val format: String = APP_DOCUMENT_FORMAT,
    val generation: Long = 1,
    val plan: TrainingPlan = defaultTrainingPlan(),
    val preferences: AppPreferences = AppPreferences(),
    val partialSessions: List<GuidedSession> = emptyList(),
    val history: List<WorkoutHistoryEntry> = emptyList(),
)

internal fun defaultAppDocument() = AppDocument()

internal fun validateAppDocument(document: AppDocument) {
    require(document.format == APP_DOCUMENT_FORMAT) { "Unsupported document format." }
    require(document.generation >= 1 && document.generation < Long.MAX_VALUE) { "Generation must be positive and incrementable." }
    require(document.plan.routines.map { it.id }.distinct().size == document.plan.routines.size) { "Routine IDs must be unique." }
    require(document.plan.schedule.map { it.id }.distinct().size == document.plan.schedule.size) { "Schedule IDs must be unique." }
    document.plan.routines.forEach(::validateRoutine)
    val routineIds = document.plan.routines.mapTo(hashSetOf()) { it.id }
    document.plan.schedule.forEach { entry ->
        validateId(entry.id)
        validateId(entry.routineId)
        require(entry.routineId in routineIds) { "Schedule ${entry.id} references a missing routine." }
        require(entry.days.isNotEmpty()) { "Schedule ${entry.id} has no weekdays." }
    }
    val cards = document.preferences.dashboardLayout.cards
    require(cards.map { it.card }.toSet() == DashboardCard.entries.toSet() && cards.size == DashboardCard.entries.size && cards.any { it.visible }) { "Dashboard layout is invalid." }
    require(document.preferences.voice.rate.isFinite()) { "Voice rate must be finite." }
    val sessionIds = document.partialSessions.map { it.id }
    val historyIds = document.history.map { it.id }
    require(sessionIds.distinct().size == sessionIds.size && historyIds.distinct().size == historyIds.size) { "Session IDs must be unique." }
    require(sessionIds.intersect(historyIds.toSet()).isEmpty()) { "A session cannot be partial and complete." }
    val partialOccurrences = document.partialSessions.map { it.occurrence }
    val historyOccurrences = document.history.map { it.occurrence }
    require(partialOccurrences.distinct().size == partialOccurrences.size) { "Partial occurrences must be unique." }
    require(historyOccurrences.distinct().size == historyOccurrences.size) { "History occurrences must be unique." }
    require(partialOccurrences.intersect(historyOccurrences.toSet()).isEmpty()) { "An occurrence cannot be partial and complete." }
    document.partialSessions.forEach { validateSession(it, document.plan) }
    document.history.forEach(::validateHistory)
}

private fun validateRoutine(routine: Routine) {
    validateId(routine.id)
    require(routine.revision >= 1) { "Routine revision must be positive." }
    validateText(routine.name, 200, "Routine name")
    validateText(routine.artworkId, 128, "Routine artwork")
    require(routine.exercises.map { it.id }.distinct().size == routine.exercises.size) { "Exercise IDs must be unique." }
    when (routine.execution) {
        RoutineExecution.GUIDED -> require(routine.appLink == null && routine.exercises.isNotEmpty()) { "Guided routines need exercises and no app link." }
        RoutineExecution.LINKED_APP -> require(routine.appLink != null && routine.exercises.isEmpty()) { "Linked routines need an app link and no exercises." }
    }
    routine.appLink?.let { link ->
        validateText(link.packageName, 255, "Package name")
        link.deepLink?.let { validateText(it, 2_048, "Deep link") }
    }
    routine.exercises.forEach { exercise ->
        validateId(exercise.id)
        validateText(exercise.name, 200, "Exercise name")
        require(exercise.notes.length <= 4_000) { "Exercise notes are too long." }
        require(exercise.setCount > 0) { "Set count must be positive." }
        validateText(exercise.target, 500, "Exercise target")
        require(exercise.timerSeconds == null || exercise.timerSeconds > 0) { "Timer duration must be positive." }
        validateText(exercise.artworkId, 128, "Exercise artwork")
    }
}

private fun validateSession(session: GuidedSession, plan: TrainingPlan) {
    validateId(session.id)
    validateId(session.occurrence.scheduleEntryId)
    val live = plan.routines.firstOrNull { it.id == session.routineId }
    require(live?.execution == RoutineExecution.GUIDED) { "Partial session routine is missing or not guided." }
    require(session.snapshot.id == session.routineId && session.snapshot.execution == RoutineExecution.GUIDED) { "Session snapshot identity is invalid." }
    require(session.snapshot.revision <= live.revision) { "Session snapshot is newer than its routine." }
    validateRoutine(session.snapshot)
    val exercises = session.snapshot.exercises.associateBy { it.id }
    require(session.focusedExerciseId in exercises) { "Focused exercise is missing." }
    require(session.completedSets.keys == exercises.keys) { "Completed sets must cover exactly the snapshot exercises." }
    session.completedSets.forEach { (id, count) -> require(count in 0..checkNotNull(exercises[id]).setCount) { "Completed set count is invalid." } }
    require(session.startedAtMillis >= 0 && session.updatedAtMillis >= session.startedAtMillis && session.eventRevision >= 0 && session.eventRevision < Long.MAX_VALUE) { "Session timestamps or revision are invalid." }
    validateTimer(session.timer, session)
}

private fun validateTimer(timer: SessionTimer, session: GuidedSession) {
    if (timer.phase == TimerPhase.IDLE) {
        require(listOf(timer.runId, timer.exerciseId, timer.setNumber, timer.bootCount, timer.readyDeadlineElapsedMillis,
            timer.activeDeadlineElapsedMillis, timer.lastObservedElapsedMillis, timer.lastHandledCueOrdinal).all { it == null }) { "Idle timer has active fields." }
        return
    }
    validateId(requireNotNull(timer.runId))
    val exerciseId = requireNotNull(timer.exerciseId)
    require(exerciseId == session.focusedExerciseId) { "Timer must belong to the focused exercise." }
    val exercise = checkNotNull(session.snapshot.exercises.firstOrNull { it.id == exerciseId })
    require(exercise.timerSeconds != null) { "Timer exercise has no duration." }
    val completed = session.completedSets.getValue(exerciseId)
    require(completed < exercise.setCount && timer.setNumber == completed + 1) { "Timer set number is invalid." }
    val ready = requireNotNull(timer.readyDeadlineElapsedMillis)
    val active = requireNotNull(timer.activeDeadlineElapsedMillis)
    val duration = exercise.timerSeconds.toLong() * 1_000L
    require(ready >= 0 && ready <= Long.MAX_VALUE - duration && active == ready + duration) { "Timer deadlines are invalid." }
    val observed = requireNotNull(timer.lastObservedElapsedMillis)
    val ordinal = requireNotNull(timer.lastHandledCueOrdinal)
    require(observed >= 0 && ordinal in 0..5) { "Timer progress is invalid." }
    require(timer.bootCount == null || timer.bootCount >= 0) { "Boot count is invalid." }
    val expectedPhase = when {
        observed < ready -> TimerPhase.READY
        observed < active -> TimerPhase.RUNNING
        else -> TimerPhase.FINISHED
    }
    val expectedOrdinal = when {
        observed >= active -> 5
        observed >= ready -> 4
        observed >= ready - 1_000L -> 3
        observed >= ready - 2_000L -> 2
        observed >= ready - 3_000L -> 1
        else -> 0
    }
    require(timer.phase == expectedPhase && ordinal == expectedOrdinal) { "Timer phase or cue watermark is invalid." }
}

private fun validateHistory(entry: WorkoutHistoryEntry) {
    validateId(entry.id)
    validateId(entry.occurrence.scheduleEntryId)
    validateRoutine(entry.snapshot)
    require(entry.snapshot.execution == RoutineExecution.GUIDED) { "History snapshot must be guided." }
    require(entry.startedAtMillis >= 0 && entry.completedAtMillis >= entry.startedAtMillis) { "History timestamps are invalid." }
}

private fun validateId(value: String) = require(value.isNotBlank() && value.length <= 128) { "ID is invalid." }

private fun validateText(value: String, max: Int, label: String) =
    require(value.isNotBlank() && value == value.trim() && value.length <= max) { "$label is invalid." }
