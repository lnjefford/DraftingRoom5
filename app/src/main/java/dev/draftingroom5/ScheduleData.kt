package dev.draftingroom5

import java.time.DayOfWeek
import java.util.UUID

internal enum class RoutineExecution { GUIDED, LINKED_APP }

internal enum class ScheduleRepeat { WEEKLY, WEEKDAYS, DAILY, CUSTOM }

internal data class AppLink(val packageName: String, val deepLink: String?)

internal data class Exercise(
    val id: String,
    val name: String,
    val notes: String,
    val setCount: Int,
    val target: String,
    val timerSeconds: Int?,
    val artworkId: String,
)

internal data class Routine(
    val id: String,
    val revision: Long,
    val name: String,
    val artworkId: String,
    val execution: RoutineExecution,
    val exercises: List<Exercise>,
    val appLink: AppLink?,
)

internal data class ScheduleEntry(val id: String, val routineId: String, val days: Set<DayOfWeek>)

internal data class TrainingPlan(val routines: List<Routine>, val schedule: List<ScheduleEntry>)

internal fun defaultTrainingPlan(): TrainingPlan {
    val forearm = Routine(
        id = "routine-forearm", revision = 1, name = "Forearm & Grip Conditioning",
        artworkId = "grip_trainer", execution = RoutineExecution.GUIDED, appLink = null,
        exercises = listOf(
            Exercise("exercise-dead-hang", "Thick-Bar Dead Hangs", "Pull-up bar + thick adapter", 3, "20 sec", 20, "dead_hang"),
            Exercise("exercise-farmers-walk", "Dumbbell Farmer's Walks", "Start 15-20 lb/hand", 3, "30 sec", 30, "farmers_walk"),
            Exercise("exercise-grip-hold", "Grip Holds", "Pinch & crush", 4, "20 sec", 20, "grip_hold"),
            Exercise("exercise-wrist-curl", "Seated Dumbbell Wrist Curls", "Palms up, start 5-10 lb", 3, "12-15 reps", null, "wrist_curl"),
            Exercise("exercise-reverse-wrist-curl", "Seated Dumbbell Reverse Wrist Curls", "Palms down, start 5-10 lb", 3, "12-15 reps", null, "reverse_wrist_curl"),
            Exercise("exercise-finger-extension", "Finger Extensor Band Extensions", "", 3, "15-20 reps", null, "finger_extension"),
            Exercise("exercise-wrist-rotation", "Wrist Rotations", "Pronation / supination", 2, "10-12 / side", null, "wrist_rotation"),
        ),
    )
    val strength = Routine(
        "routine-strength", 1, "Fitbod workout", "dumbbell", RoutineExecution.LINKED_APP,
        emptyList(), AppLink("com.fitbod.fitbod", null),
    )
    val running = Routine(
        "routine-running", 1, "JustRun run", "running_shoe", RoutineExecution.LINKED_APP,
        emptyList(), AppLink("com.jupli.run", null),
    )
    return TrainingPlan(
        routines = listOf(strength, running, forearm),
        schedule = listOf(
            ScheduleEntry("schedule-strength", strength.id, linkedSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            ScheduleEntry("schedule-running", running.id, linkedSetOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            ScheduleEntry("schedule-forearm", forearm.id, setOf(DayOfWeek.SATURDAY)),
        ),
    )
}

internal fun TrainingPlan.forDay(day: DayOfWeek): List<ScheduleEntry> = schedule.filter { day in it.days }

internal fun TrainingPlan.scheduleCounts(): Map<DayOfWeek, Int> =
    DayOfWeek.entries.associateWith { day -> schedule.count { day in it.days } }

internal fun TrainingPlan.routineFor(entry: ScheduleEntry): Routine =
    checkNotNull(routines.firstOrNull { it.id == entry.routineId }) { "Schedule ${entry.id} references a missing routine." }

internal fun repeatDays(pattern: ScheduleRepeat, anchor: DayOfWeek, customDays: Set<DayOfWeek>): Set<DayOfWeek> = when (pattern) {
    ScheduleRepeat.WEEKLY -> setOf(anchor)
    ScheduleRepeat.WEEKDAYS -> DayOfWeek.entries.filterTo(linkedSetOf()) { it.value <= DayOfWeek.FRIDAY.value }
    ScheduleRepeat.DAILY -> DayOfWeek.entries.toCollection(linkedSetOf())
    ScheduleRepeat.CUSTOM -> customDays
}

internal fun repeatPattern(days: Set<DayOfWeek>): ScheduleRepeat = when (days) {
    DayOfWeek.entries.toSet() -> ScheduleRepeat.DAILY
    DayOfWeek.entries.filterTo(linkedSetOf()) { it.value <= DayOfWeek.FRIDAY.value } -> ScheduleRepeat.WEEKDAYS
    else -> if (days.size == 1) ScheduleRepeat.WEEKLY else ScheduleRepeat.CUSTOM
}

internal fun <T> move(items: List<T>, from: Int, offset: Int): List<T> {
    val to = from + offset
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun TrainingPlan.moveScheduleOnDay(day: DayOfWeek, entryId: String, offset: Int): TrainingPlan {
    val visibleIds = forDay(day).map { it.id }
    val movedIds = move(visibleIds, visibleIds.indexOf(entryId), offset)
    if (movedIds == visibleIds) return this
    val occupied = schedule.indices.filter { day in schedule[it].days }
    val byId = schedule.associateBy { it.id }
    val result = schedule.toMutableList()
    occupied.forEachIndexed { index, slot -> result[slot] = checkNotNull(byId[movedIds[index]]) }
    return copy(schedule = result)
}

internal fun TrainingPlan.removeScheduleEntry(id: String): TrainingPlan =
    copy(schedule = schedule.filterNot { it.id == id })

internal fun TrainingPlan.removeRoutine(id: String): TrainingPlan = copy(
    routines = routines.filterNot { it.id == id },
    schedule = schedule.filterNot { it.routineId == id },
)

internal fun newId(): String = UUID.randomUUID().toString()
