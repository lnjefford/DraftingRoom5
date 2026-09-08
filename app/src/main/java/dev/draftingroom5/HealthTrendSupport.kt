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
