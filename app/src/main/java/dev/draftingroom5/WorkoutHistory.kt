package dev.draftingroom5

import java.time.LocalDate

internal data class OccurrenceKey(val scheduleEntryId: String, val scheduledDate: LocalDate)

internal data class WorkoutHistoryEntry(
    val id: String,
    val occurrence: OccurrenceKey,
    val snapshot: Routine,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
)

internal fun completedScheduleIdsForDate(entries: List<WorkoutHistoryEntry>, date: LocalDate): List<String> =
    entries.filter { it.occurrence.scheduledDate == date }
        .map { it.occurrence.scheduleEntryId }
        .distinct()
