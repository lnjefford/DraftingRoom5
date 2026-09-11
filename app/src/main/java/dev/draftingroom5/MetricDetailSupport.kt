package dev.draftingroom5

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class MetricChartDomain(val minimum: Double, val maximum: Double)

internal data class MetricDateSpan(val first: LocalDate, val last: LocalDate)

/** X coordinates reflect elapsed time, including distinct same-day measurements. */
internal fun metricChartFractions(points: List<HealthTrendPoint>): List<Float> {
    fun time(point: HealthTrendPoint): Double = point.recordedAt?.let {
        it.epochSecond.toDouble() + it.nano / 1_000_000_000.0
    } ?: (point.date.toEpochDay() * 86_400.0)
    if (points.isEmpty()) return emptyList()
    val start = time(points.first())
    val span = time(points.last()) - start
    return if (span <= 0) points.map { .5f } else points.map { ((time(it) - start) / span).toFloat() }
}

internal fun visibleMetricTrend(
    points: List<HealthTrendPoint>,
    range: HealthDateRange,
): List<HealthTrendPoint> {
    val recorded = orderedNumericPoints(points)
    val end = recorded.lastOrNull()?.date ?: return emptyList()
    val start = range.startDate(end)
    return recorded.filter { it.date in start..end }
}

internal fun metricDateSpan(points: List<HealthTrendPoint>): MetricDateSpan? {
    val recorded = orderedNumericPoints(points)
    return if (recorded.isEmpty()) null else MetricDateSpan(recorded.first().date, recorded.last().date)
}

internal fun metricChartDomain(points: List<HealthTrendPoint>): MetricChartDomain? {
    val values = orderedNumericPoints(points).map { checkNotNull(it.value) }
    if (values.isEmpty()) return null
    val low = values.min()
    val high = values.max()
    val padding = if (high > low) (high - low) * 0.1 else max(abs(high) * 0.05, 1.0)
    return MetricChartDomain(low - padding, high + padding)
}

internal fun metricDecimals(card: DashboardCard): Int = if (card == DashboardCard.WORKOUTS) 0 else 1

internal fun formatMetricNumber(
    card: DashboardCard,
    value: Double,
    locale: Locale = Locale.getDefault(),
): String = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = metricDecimals(card)
    maximumFractionDigits = metricDecimals(card)
}.format(value)

internal fun metricDeltaDescription(
    card: DashboardCard,
    summary: HealthTrendSummary?,
    range: HealthDateRange,
    locale: Locale = Locale.getDefault(),
): String {
    if (summary == null) return "No comparison available"
    if (summary.first == summary.last) return "Steady over ${range.displayLabel}"
    val delta = summary.last - summary.first
    val direction = if (delta > 0) "Up" else "Down"
    return "$direction ${formatMetricNumber(card, abs(delta), locale)} ${card.metricUnit} over ${range.displayLabel}"
}

internal fun metricChartDescription(
    card: DashboardCard,
    points: List<HealthTrendPoint>,
    locale: Locale = Locale.getDefault(),
): String {
    val summary = healthTrendSummary(points) ?: return "${card.title} trend, no data"
    val direction = when {
        summary.last > summary.first -> "up"
        summary.last < summary.first -> "down"
        else -> "steady"
    }
    return buildString {
        append("${card.title} trend, ${points.count { it.value != null }} recorded values, ")
        append("first ${formatMetricNumber(card, summary.first, locale)} ${card.metricUnit}, ")
        append("last ${formatMetricNumber(card, summary.last, locale)} ${card.metricUnit}, $direction, ")
        append("high ${formatMetricNumber(card, summary.high, locale)}, low ${formatMetricNumber(card, summary.low, locale)}")
    }
}

internal fun metricFreshness(
    syncedAt: Instant?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (syncedAt == null) return "Not synced"
    val syncDate = syncedAt.atZone(zoneId).toLocalDate()
    val today = now.atZone(zoneId).toLocalDate()
    return when {
        syncDate >= today -> "Synced today"
        syncDate == today.minusDays(1) -> "Synced yesterday"
        else -> "Synced ${syncedAt.atZone(zoneId).format(DateTimeFormatter.ofPattern("MMM d", locale))}"
    }
}

internal val DashboardCard.metricUnit: String
    get() = when (this) {
        DashboardCard.WEIGHT, DashboardCard.LEAN_MASS -> "lb"
        DashboardCard.BODY_FAT -> "%"
        DashboardCard.WORKOUTS -> "sessions"
        DashboardCard.DISTANCE -> "mi"
        DashboardCard.TODAY -> ""
    }
