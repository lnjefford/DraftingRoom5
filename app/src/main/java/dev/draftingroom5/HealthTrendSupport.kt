package dev.draftingroom5

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class TimedHealthValue(
    val recordedAt: Instant,
    val value: Double,
)

internal data class HealthTrendPoint(
    val date: LocalDate,
    val value: Double?,
    val recordedAt: Instant? = null,
)

internal enum class HealthTrendDirection { UP, DOWN, NEUTRAL }

internal data class HealthTrendSummary(
    val high: Double,
    val average: Double,
    val low: Double,
    val first: Double,
    val last: Double,
)

internal fun Double.toPounds() = this * 2.2046226218

internal fun measurementHealthTrend(
    samples: List<TimedHealthValue>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<HealthTrendPoint> {
    if (endDate.isBefore(startDate)) return emptyList()

    return samples.filter { it.value.isFinite() }
        .sortedBy { it.recordedAt }
        .map { HealthTrendPoint(it.recordedAt.atZone(zoneId).toLocalDate(), it.value, it.recordedAt) }
        .filter { it.date in startDate..endDate }
}

internal fun dailyHealthTotals(
    samples: List<TimedHealthValue>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<HealthTrendPoint> {
    if (endDate.isBefore(startDate)) return emptyList()
    val totalsByDate = samples
        .map { sample -> sample.recordedAt.atZone(zoneId).toLocalDate() to sample.value }
        .filter { (date, _) -> !date.isBefore(startDate) && !date.isAfter(endDate) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

    return generateSequence(startDate) { date ->
        date.plusDays(1).takeUnless { it.isAfter(endDate) }
    }.map { date -> HealthTrendPoint(date, totalsByDate[date] ?: 0.0) }.toList()
}

internal fun healthTrendDirection(points: List<HealthTrendPoint>): HealthTrendDirection {
    val values = orderedNumericPoints(points).map { checkNotNull(it.value) }
    if (values.size < 2) return HealthTrendDirection.NEUTRAL
    return when {
        values.last() > values.first() -> HealthTrendDirection.UP
        values.last() < values.first() -> HealthTrendDirection.DOWN
        else -> HealthTrendDirection.NEUTRAL
    }
}

internal fun healthTrendSummary(points: List<HealthTrendPoint>): HealthTrendSummary? {
    val values = orderedNumericPoints(points).map { checkNotNull(it.value) }
    if (values.isEmpty()) return null
    return HealthTrendSummary(values.max(), values.average(), values.min(), values.first(), values.last())
}

internal fun orderedNumericPoints(points: List<HealthTrendPoint>): List<HealthTrendPoint> =
    points.filter { it.value?.isFinite() == true }
        .sortedWith(compareBy<HealthTrendPoint> { it.date }.thenBy { it.recordedAt })
