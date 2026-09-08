package dev.draftingroom5

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID

internal enum class Destination { FITBOD, JUSTRUN, CUSTOM }

internal data class ScheduledItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val day: DayOfWeek,
    val destination: Destination,
    val routineId: String? = null,
    val enabled: Boolean = true,
)

internal data class Exercise(
    val id: String,
    val name: String,
    val notes: String,
    val sets: String,
    val target: String,
    val timerSeconds: Int? = null,
)

internal data class CustomRoutine(
    val id: String,
    val name: String,
    val exercises: List<Exercise>,
)

internal data class TrainingPlan(
    val schedule: List<ScheduledItem>,
    val routines: List<CustomRoutine>,
)

internal fun defaultTrainingPlan(): TrainingPlan {
    val routine = CustomRoutine(
        id = "forearm",
        name = "Forearm & Grip Conditioning",
        exercises = listOf(
            Exercise("dead-hangs", "Thick-Bar Dead Hangs", "Pull-up bar + thick adapter", "3 sets", "20 sec", 20),
            Exercise("farmers-walks", "Dumbbell Farmer's Walks", "Start 15-20 lb/hand", "3 sets", "30 sec", 30),
            Exercise("grip-holds", "Great Ape Grips Pro Holds", "Pinch & crush", "4 sets", "20 sec", 20),
            Exercise("wrist-curls", "Seated Dumbbell Wrist Curls", "Palms up, start 5-10 lb", "3 sets", "12-15 reps"),
            Exercise("reverse-curls", "Seated Dumbbell Reverse Wrist Curls", "Palms down, start 5-10 lb", "3 sets", "12-15 reps"),
            Exercise("band-extensions", "Finger Extensor Band Extensions", "", "2-3 sets", "15-20 reps"),
            Exercise("wrist-rotations", "Wrist Rotations", "Pronation / supination", "2 sets", "10-12 / side"),
        ),
    )
    return TrainingPlan(
        schedule = listOf(
            ScheduledItem("fitbod-mon", "Fitbod workout", "Strength session", DayOfWeek.MONDAY, Destination.FITBOD),
            ScheduledItem("justrun-mon", "JustRun run", "Running session", DayOfWeek.MONDAY, Destination.JUSTRUN),
            ScheduledItem("fitbod-tue", "Fitbod workout", "Strength session", DayOfWeek.TUESDAY, Destination.FITBOD),
            ScheduledItem("justrun-wed", "JustRun run", "Running session", DayOfWeek.WEDNESDAY, Destination.JUSTRUN),
            ScheduledItem("fitbod-thu", "Fitbod workout", "Strength session", DayOfWeek.THURSDAY, Destination.FITBOD),
            ScheduledItem("justrun-fri", "JustRun run", "Running session", DayOfWeek.FRIDAY, Destination.JUSTRUN),
            ScheduledItem("forearm-sat", routine.name, "Custom workout", DayOfWeek.SATURDAY, Destination.CUSTOM, routine.id),
        ),
        routines = listOf(routine),
    )
}

internal fun TrainingPlan.forDay(day: DayOfWeek): List<ScheduledItem> =
    schedule.filter { it.enabled && it.day == day }

internal fun <T> move(items: List<T>, from: Int, offset: Int): List<T> {
    val to = from + offset
    if (from !in items.indices || to !in items.indices) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun TrainingPlan.removeRoutine(id: String): TrainingPlan = copy(
    schedule = schedule.filterNot { it.routineId == id },
    routines = routines.filterNot { it.id == id },
)

internal fun newId(): String = UUID.randomUUID().toString()

internal class TrainingPlanStore(context: Context) {
    private val preferences = context.getSharedPreferences("training-plan", Context.MODE_PRIVATE)

    fun load(): TrainingPlan {
        val value = preferences.getString(KEY, null) ?: return defaultTrainingPlan()
        return runCatching { decodePlan(value) }.getOrElse { defaultTrainingPlan() }
    }

    fun save(plan: TrainingPlan) {
        preferences.edit().putString(KEY, encodePlan(plan)).apply()
    }

    companion object {
        private const val KEY = "plan-v1"
    }
}

internal fun encodePlan(plan: TrainingPlan): String = JSONObject().apply {
    put("schedule", JSONArray().apply {
        plan.schedule.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("day", item.day.name)
                put("destination", item.destination.name)
                put("routineId", item.routineId ?: JSONObject.NULL)
                put("enabled", item.enabled)
            })
        }
    })
    put("routines", JSONArray().apply {
        plan.routines.forEach { routine ->
            put(JSONObject().apply {
                put("id", routine.id)
                put("name", routine.name)
                put("exercises", JSONArray().apply {
                    routine.exercises.forEach { exercise ->
                        put(JSONObject().apply {
                            put("id", exercise.id)
                            put("name", exercise.name)
                            put("notes", exercise.notes)
                            put("sets", exercise.sets)
                            put("target", exercise.target)
                            put("timerSeconds", exercise.timerSeconds ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
    })
}.toString()

internal fun decodePlan(value: String): TrainingPlan {
    val root = JSONObject(value)
    val scheduleJson = root.getJSONArray("schedule")
    val routinesJson = root.getJSONArray("routines")
    return TrainingPlan(
        schedule = List(scheduleJson.length()) { index ->
            scheduleJson.getJSONObject(index).let { item ->
                ScheduledItem(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    subtitle = item.optString("subtitle"),
                    day = DayOfWeek.valueOf(item.getString("day")),
                    destination = Destination.valueOf(item.getString("destination")),
                    routineId = item.optString("routineId").takeIf { it.isNotBlank() && it != "null" },
                    enabled = item.optBoolean("enabled", true),
                )
            }
        },
        routines = List(routinesJson.length()) { index ->
            routinesJson.getJSONObject(index).let { routine ->
                val exercises = routine.getJSONArray("exercises")
                CustomRoutine(
                    id = routine.getString("id"),
                    name = routine.getString("name"),
                    exercises = List(exercises.length()) { exerciseIndex ->
                        exercises.getJSONObject(exerciseIndex).let { exercise ->
                            Exercise(
                                id = exercise.getString("id"),
                                name = exercise.getString("name"),
                                notes = exercise.optString("notes"),
                                sets = exercise.optString("sets"),
                                target = exercise.optString("target"),
                                timerSeconds = if (exercise.isNull("timerSeconds")) null else exercise.getInt("timerSeconds"),
                            )
                        }
                    },
                )
            }
        },
    )
}
