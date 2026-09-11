package dev.draftingroom5

import java.time.LocalDate

internal enum class HealthDateRange(
    val days: Long,
    val buttonLabel: String,
    val displayLabel: String,
) {
    DAY(1, "1D", "1 day"),
    WEEK(7, "1W", "7 days"),
    MONTH(30, "1M", "30 days"),
    THREE_MONTHS(90, "3M", "3 months"),
    YEAR(365, "1Y", "1 year"),
    ;

    fun startDate(endDate: LocalDate): LocalDate = endDate.minusDays(days - 1)
}
