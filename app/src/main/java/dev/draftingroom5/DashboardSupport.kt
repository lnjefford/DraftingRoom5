package dev.draftingroom5

import java.time.DayOfWeek
import java.time.LocalDate

internal enum class SessionAction { START, RESUME, DONE }

internal data class DashboardSession(
    val scheduleEntry: ScheduleEntry,
    val routine: Routine,
    val action: SessionAction,
    val completedExerciseCount: Int = 0,
    val totalExerciseCount: Int = routine.exercises.size,
    val savedOriginDate: LocalDate? = null,
    val occurrence: OccurrenceKey,
    val effectiveDate: LocalDate,
) {
    val actionLabel: String
        get() = when (action) {
            SessionAction.START -> if (routine.execution == RoutineExecution.LINKED_APP) "Open & complete" else "Start"
            SessionAction.RESUME -> "Resume"
            SessionAction.DONE -> "Done"
        }

    val accessibilityAction: String
        get() = "$actionLabel ${routine.name}"

    val undoCompletionAction: String?
        get() = if (action == SessionAction.DONE && routine.execution == RoutineExecution.LINKED_APP) {
            "Undo completion for ${routine.name}"
        } else null

    val progressLabel: String?
        get() = if (action == SessionAction.RESUME) {
            "$completedExerciseCount of $totalExerciseCount exercises complete"
        } else null
}

internal fun dashboardSessions(
    plan: TrainingPlan,
    partialSessions: List<GuidedSession>,
    history: List<WorkoutHistoryEntry>,
    date: LocalDate,
    occurrenceExceptions: List<OccurrenceException> = emptyList(),
): List<DashboardSession> {
    val partialByOccurrence = partialSessions.associateBy { it.occurrence }
    val completedByOccurrence = history.associateBy { it.occurrence }
    val exceptionsByOccurrence = occurrenceExceptions.associateBy { it.occurrence }
    val occurrences = plan.forDay(date.dayOfWeek).map { OccurrenceKey(it.id, date) }
        .filter { it !in exceptionsByOccurrence } + occurrenceExceptions
        .filter { it.disposition == OccurrenceDisposition.DEFERRED && it.effectiveDate == date }
        .sortedWith(compareBy({ it.occurrence.scheduledDate }, { it.occurrence.scheduleEntryId }))
        .map { it.occurrence }
    return occurrences.mapNotNull { occurrence ->
        val partial = partialByOccurrence[occurrence]
        val completed = completedByOccurrence[occurrence]
        val entry = plan.schedule.firstOrNull { it.id == occurrence.scheduleEntryId }
            ?: completed?.let { ScheduleEntry(occurrence.scheduleEntryId, it.snapshot.id, setOf(date.dayOfWeek)) }
            ?: return@mapNotNull null // Orphaned partials remain reachable in Saved sessions.
        val liveRoutine = completed?.snapshot ?: partial?.snapshot ?: plan.routineFor(entry)
        val action = when {
            occurrence in completedByOccurrence -> SessionAction.DONE
            partial != null -> SessionAction.RESUME
            else -> SessionAction.START
        }
        DashboardSession(
            scheduleEntry = if (partial != null) entry.copy(routineId = partial.routineId) else entry,
            routine = completedByOccurrence[occurrence]?.snapshot ?: partial?.snapshot ?: liveRoutine,
            action = action,
            completedExerciseCount = partial?.snapshot?.exercises?.count { exercise ->
                partial.completedSets.getValue(exercise.id) == exercise.setCount
            } ?: 0,
            totalExerciseCount = partial?.snapshot?.exercises?.size ?: liveRoutine.exercises.size,
            occurrence = occurrence,
            effectiveDate = date,
        )
    }
}

internal fun dashboardSessions(document: AppDocument, date: LocalDate): List<DashboardSession> =
    dashboardSessions(document.plan, document.partialSessions, document.history, date, document.occurrenceExceptions)

internal fun savedDashboardSessions(
    plan: TrainingPlan,
    partialSessions: List<GuidedSession>,
    selectedDate: LocalDate,
    occurrenceExceptions: List<OccurrenceException> = emptyList(),
): List<DashboardSession> {
    val representedOccurrences = dashboardSessions(plan, partialSessions, emptyList(), selectedDate, occurrenceExceptions)
        .mapTo(hashSetOf()) { it.occurrence }
    val liveRoutineIds = plan.routines.mapTo(hashSetOf()) { it.id }
    return partialSessions
        .asSequence()
        .filter { it.routineId in liveRoutineIds && it.occurrence !in representedOccurrences }
        .sortedByDescending(GuidedSession::updatedAtMillis)
        .map { partial ->
            DashboardSession(
                scheduleEntry = ScheduleEntry(
                    id = partial.occurrence.scheduleEntryId,
                    routineId = partial.routineId,
                    days = setOf(partial.occurrence.scheduledDate.dayOfWeek),
                ),
                routine = partial.snapshot,
                action = SessionAction.RESUME,
                completedExerciseCount = partial.snapshot.exercises.count { exercise ->
                    partial.completedSets.getValue(exercise.id) == exercise.setCount
                },
                totalExerciseCount = partial.snapshot.exercises.size,
                savedOriginDate = partial.occurrence.scheduledDate,
                occurrence = partial.occurrence,
                effectiveDate = partial.effectiveDate,
            )
        }
        .toList()
}

internal fun dashboardWeek(today: LocalDate): List<LocalDate> {
    val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L..6L).map(monday::plusDays)
}

/** Follow today across midnight, but preserve an explicitly selected day in the visible week. */
internal fun resolveDashboardDate(selected: LocalDate, previousToday: LocalDate, today: LocalDate): LocalDate =
    if (selected == previousToday || selected !in dashboardWeek(today)) today else selected

internal fun linkedAppDisplayName(packageName: String): String = when (packageName) {
    "com.fitbod.fitbod" -> "FITBOD"
    "com.jupli.run" -> "JUST RUN"
    else -> packageName.substringAfterLast('.').replace('_', ' ').uppercase()
}
