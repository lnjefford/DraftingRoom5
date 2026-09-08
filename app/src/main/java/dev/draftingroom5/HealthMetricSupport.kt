package dev.draftingroom5

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class HealthMetricState { CURRENT, STALE, MISSING, UNAVAILABLE }

internal data class HealthMetric(
    val value: String = "--",
    val state: HealthMetricState = HealthMetricState.UNAVAILABLE,
    val syncedAt: Instant? = null,
    val recordedAt: Instant? = null,
    val source: String? = null,
)

internal fun healthMetric(
    value: String?,
    outcome: HealthReadOutcome,
    syncedAt: Instant,
    recordedAt: Instant? = null,
    source: String? = null,
    staleAfter: Duration = Duration.ofDays(7),
): HealthMetric {
    if (outcome == HealthReadOutcome.UNAVAILABLE) return HealthMetric()
    if (outcome == HealthReadOutcome.MISSING || value == null || recordedAt == null) {
        return HealthMetric(state = HealthMetricState.MISSING, syncedAt = syncedAt)
    }
    val isStale = Duration.between(recordedAt, syncedAt) > staleAfter
    return HealthMetric(
        value = value,
        state = if (isStale) HealthMetricState.STALE else HealthMetricState.CURRENT,
        syncedAt = syncedAt,
        recordedAt = recordedAt,
        source = source,
    )
}

internal fun HealthMetric.detail(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val syncTime = syncedAt?.atZone(zoneId)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return when (state) {
        HealthMetricState.UNAVAILABLE -> "Not synced • Permission or provider unavailable"
        HealthMetricState.MISSING -> "Synced $syncTime • No data found"
        HealthMetricState.CURRENT -> listOfNotNull("Synced $syncTime", source).joinToString(" • ")
        HealthMetricState.STALE -> {
            val recordDate = recordedAt?.atZone(zoneId)?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            listOfNotNull("Synced $syncTime", source, "Stale • data from $recordDate").joinToString(" • ")
        }
    }
}
