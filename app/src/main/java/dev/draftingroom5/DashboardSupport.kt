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
) {
    val actionLabel: String
        get() = when (action) {
            SessionAction.START -> "Start"
            SessionAction.RESUME -> "Resume"
            SessionAction.DONE -> "Done"
        }

    val accessibilityAction: String
        get() = "$actionLabel ${routine.name}"

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
): List<DashboardSession> {
    val partialByOccurrence = partialSessions.associateBy { it.occurrence }
    val completedByOccurrence = history.associateBy { it.occurrence }
    return plan.forDay(date.dayOfWeek).map { entry ->
        val liveRoutine = plan.routineFor(entry)
        val occurrence = OccurrenceKey(entry.id, date)
        val partial = partialByOccurrence[occurrence]
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
        )
    }
}

internal fun savedDashboardSessions(
    plan: TrainingPlan,
    partialSessions: List<GuidedSession>,
    selectedDate: LocalDate,
): List<DashboardSession> {
    val representedOccurrences = plan.forDay(selectedDate.dayOfWeek)
        .mapTo(hashSetOf()) { OccurrenceKey(it.id, selectedDate) }
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
            )
        }
        .toList()
}

internal fun dashboardWeek(today: LocalDate): List<LocalDate> {
    val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L..6L).map(monday::plusDays)
}

internal fun linkedAppDisplayName(packageName: String): String = when (packageName) {
    "com.fitbod.fitbod" -> "FITBOD"
    "com.jupli.run" -> "JUST RUN"
    else -> packageName.substringAfterLast('.').replace('_', ' ').uppercase()
}
