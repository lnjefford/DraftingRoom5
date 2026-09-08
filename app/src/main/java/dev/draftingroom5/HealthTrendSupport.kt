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
)

internal enum class HealthTrendDirection { UP, DOWN, NEUTRAL }

internal fun Double.toPounds() = this * 2.2046226218

internal fun dailyHealthTrend(
    samples: List<TimedHealthValue>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<HealthTrendPoint> {
    if (endDate.isBefore(startDate)) return emptyList()

    val latestByDate = samples
        .map { sample -> sample.recordedAt.atZone(zoneId).toLocalDate() to sample }
        .filter { (date, _) -> !date.isBefore(startDate) && !date.isAfter(endDate) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, dailySamples) -> dailySamples.maxBy { it.recordedAt }.value }

    return generateSequence(startDate) { date ->
        date.plusDays(1).takeUnless { it.isAfter(endDate) }
    }.map { date -> HealthTrendPoint(date, latestByDate[date]) }.toList()
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
    val values = points.mapNotNull { it.value }
    if (values.size < 2) return HealthTrendDirection.NEUTRAL
    val midpoint = values.size / 2
    val earlier = values.take(midpoint).average()
    val later = values.drop(midpoint).average()
    val tolerance = maxOf(kotlin.math.abs(values.average()) * 0.001, 0.01)
    return when {
        later - earlier > tolerance -> HealthTrendDirection.UP
        earlier - later > tolerance -> HealthTrendDirection.DOWN
        else -> HealthTrendDirection.NEUTRAL
    }
}
