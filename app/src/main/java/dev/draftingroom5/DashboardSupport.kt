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
    val completedOccurrences = history.mapTo(hashSetOf()) { it.occurrence }
    return plan.forDay(date.dayOfWeek).map { entry ->
        val routine = plan.routineFor(entry)
        val occurrence = OccurrenceKey(entry.id, date)
        val partial = partialByOccurrence[occurrence]
        val action = when {
            occurrence in completedOccurrences -> SessionAction.DONE
            routine.execution == RoutineExecution.GUIDED && partial != null -> SessionAction.RESUME
            else -> SessionAction.START
        }
        DashboardSession(
            scheduleEntry = entry,
            routine = routine,
            action = action,
            completedExerciseCount = partial?.snapshot?.exercises?.count { exercise ->
                partial.completedSets.getValue(exercise.id) == exercise.setCount
            } ?: 0,
            totalExerciseCount = partial?.snapshot?.exercises?.size ?: routine.exercises.size,
        )
    }
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
