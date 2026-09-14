package dev.draftingroom5

import java.time.LocalDate

internal data class OccurrenceKey(val scheduleEntryId: String, val scheduledDate: LocalDate)

internal data class WorkoutHistoryEntry(
    val id: String,
    val occurrence: OccurrenceKey,
    val snapshot: Routine,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val effectiveDate: LocalDate = occurrence.scheduledDate,
)

internal fun completedOccurrencesForDate(entries: List<WorkoutHistoryEntry>, date: LocalDate): List<OccurrenceKey> =
    entries.filter { it.effectiveDate == date }
        .map { it.occurrence }
        .distinct()
