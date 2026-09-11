package dev.draftingroom5

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class HealthMetricState { CURRENT, STALE, MISSING, UNAVAILABLE, ERROR }

internal data class HealthMetric(
    val value: String = "--",
    val state: HealthMetricState = HealthMetricState.UNAVAILABLE,
    val syncedAt: Instant? = null,
    val recordedAt: Instant? = null,
    val source: String? = null,
    val sources: List<String> = listOfNotNull(source),
    val refreshError: String? = null,
)

internal fun healthMetric(
    value: String?,
    outcome: HealthReadOutcome,
    syncedAt: Instant,
    recordedAt: Instant? = null,
    source: String? = null,
    sources: List<String> = listOfNotNull(source),
    staleAfter: Duration = Duration.ofDays(7),
): HealthMetric {
    if (outcome == HealthReadOutcome.UNAVAILABLE) return HealthMetric()
    if (outcome == HealthReadOutcome.ERROR) return HealthMetric(state = HealthMetricState.ERROR, refreshError = "Could not refresh this metric. Retry.")
    if (outcome == HealthReadOutcome.MISSING || value == null || recordedAt == null) {
        return HealthMetric(state = HealthMetricState.MISSING, syncedAt = syncedAt)
    }
    val isStale = Duration.between(recordedAt, syncedAt) > staleAfter
    val distinctSources = (listOfNotNull(source) + sources).filter(String::isNotBlank).distinct().sorted()
    return HealthMetric(
        value = value,
        state = if (isStale) HealthMetricState.STALE else HealthMetricState.CURRENT,
        syncedAt = syncedAt,
        recordedAt = recordedAt,
        source = if (distinctSources.size > 1) "Multiple sources" else source ?: distinctSources.singleOrNull(),
        sources = distinctSources,
    )
}

internal fun HealthMetric.detail(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val syncTime = syncedAt?.atZone(zoneId)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return when (state) {
        HealthMetricState.UNAVAILABLE -> "Not synced • Permission or provider unavailable"
        HealthMetricState.ERROR -> "Could not refresh • Retry"
        HealthMetricState.MISSING -> "Synced $syncTime • No data found"
        HealthMetricState.CURRENT -> listOfNotNull("Synced $syncTime", source).joinToString(" • ")
        HealthMetricState.STALE -> {
            val recordDate = recordedAt?.atZone(zoneId)?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            listOfNotNull("Synced $syncTime", source, "Stale • data from $recordDate").joinToString(" • ")
        }
    }
}

/** Failed reads retain the last successful timestamp; revoked permission clears the value. */
internal fun retainHealthMetricOnError(current: HealthMetric, previous: HealthMetric): HealthMetric =
    if (current.state == HealthMetricState.ERROR && previous.recordedAt != null) {
        previous.copy(refreshError = current.refreshError)
    } else current
