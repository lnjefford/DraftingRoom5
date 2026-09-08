package dev.draftingroom5

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class WorkoutHistoryEntry(
    val id: String,
    val scheduleId: String,
    val title: String,
    val destination: Destination,
    val completedAtMillis: Long,
)

internal class WorkoutHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("workout-history", Context.MODE_PRIVATE)

    fun load(): List<WorkoutHistoryEntry> = preferences.getString(KEY, null)
        ?.let { runCatching { decodeWorkoutHistory(it) }.getOrDefault(emptyList()) }
        ?: emptyList()

    fun save(entries: List<WorkoutHistoryEntry>) {
        preferences.edit().putString(KEY, encodeWorkoutHistory(entries)).apply()
    }

    companion object {
        private const val KEY = "history-v1"
    }
}

internal fun completedScheduleIdsForDate(
    entries: List<WorkoutHistoryEntry>,
    date: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<String> = entries.filter {
    Instant.ofEpochMilli(it.completedAtMillis).atZone(zoneId).toLocalDate() == date
}.map { it.scheduleId }.distinct()

internal fun encodeWorkoutHistory(entries: List<WorkoutHistoryEntry>): String = JSONArray().apply {
    entries.forEach { entry ->
        put(JSONObject().apply {
            put("id", entry.id)
            put("scheduleId", entry.scheduleId)
            put("title", entry.title)
            put("destination", entry.destination.name)
            put("completedAtMillis", entry.completedAtMillis)
        })
    }
}.toString()

internal fun decodeWorkoutHistory(value: String): List<WorkoutHistoryEntry> {
    val array = JSONArray(value)
    return List(array.length()) { index ->
        array.getJSONObject(index).let { item ->
            WorkoutHistoryEntry(
                id = item.getString("id"),
                scheduleId = item.getString("scheduleId"),
                title = item.getString("title"),
                destination = Destination.valueOf(item.getString("destination")),
                completedAtMillis = item.getLong("completedAtMillis"),
            )
        }
    }
}
