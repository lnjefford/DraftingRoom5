package dev.draftingroom5

import java.time.LocalDate

internal enum class OccurrenceDisposition { SCHEDULED, DEFERRED, SKIPPED }

/** The key always names the source repetition. Absence of an exception means scheduled. */
internal data class OccurrenceException(
    val occurrence: OccurrenceKey,
    val disposition: OccurrenceDisposition,
    val effectiveDate: LocalDate?,
)

internal fun AppDocument.occurrenceDisposition(key: OccurrenceKey): OccurrenceDisposition =
    occurrenceExceptions.firstOrNull { it.occurrence == key }?.disposition ?: OccurrenceDisposition.SCHEDULED

/** Civil dates are stored, never recalculated from timestamps after a timezone change. */
internal fun AppDocument.occurrenceDate(key: OccurrenceKey): LocalDate? {
    occurrenceExceptions.firstOrNull { it.occurrence == key }?.let { return it.effectiveDate }
    return key.scheduledDate.takeIf { date ->
        plan.schedule.any { it.id == key.scheduleEntryId && date.dayOfWeek in it.days }
    }
}

/** Removed schedules cancel unstarted exceptions; snapshots keep started/completed dates durable. */
internal fun AppDocument.pruneOccurrenceExceptions(): AppDocument {
    val liveIds = plan.schedule.mapTo(hashSetOf()) { it.id }
    val durableKeys = partialSessions.map { it.occurrence }.toSet() + history.map { it.occurrence }
    return copy(occurrenceExceptions = occurrenceExceptions.filter {
        it.occurrence.scheduleEntryId in liveIds || it.occurrence in durableKeys
    })
}

internal fun validateOccurrenceExceptions(document: AppDocument) {
    val exceptions = document.occurrenceExceptions
    require(exceptions.map { it.occurrence }.distinct().size == exceptions.size) { "Occurrence exceptions must be unique." }
    val liveIds = document.plan.schedule.mapTo(hashSetOf()) { it.id }
    val durableDates = document.partialSessions.map { it.occurrence to it.effectiveDate } +
        document.history.map { it.occurrence to it.effectiveDate }
    exceptions.forEach { exception ->
        val key = exception.occurrence
        require(key.scheduleEntryId.isNotBlank() && key.scheduleEntryId.length <= 128) { "Occurrence ID is invalid." }
        require(key.scheduleEntryId in liveIds || durableDates.any { it.first == key }) { "Exception has no schedule or durable session." }
        when (exception.disposition) {
            OccurrenceDisposition.SCHEDULED -> require(false) { "Scheduled occurrences must not store an exception." }
            OccurrenceDisposition.DEFERRED -> require(key.scheduledDate < LocalDate.MAX &&
                exception.effectiveDate == key.scheduledDate.plusDays(1)) { "Deferred date must be the next civil day." }
            OccurrenceDisposition.SKIPPED -> require(exception.effectiveDate == null && durableDates.none { it.first == key }) {
                "Skipped occurrences cannot have an effective date, progress, or history."
            }
        }
    }
    val byKey = exceptions.associateBy { it.occurrence }
    durableDates.forEach { (key, date) ->
        require(date == (byKey[key]?.effectiveDate ?: key.scheduledDate)) { "Session effective date disagrees with its source exception." }
    }
}
