package dev.draftingroom5

import android.content.Context
import java.time.LocalDate

internal enum class HealthDateRange(
    val days: Long,
    val buttonLabel: String,
    val displayLabel: String,
) {
    WEEK(7, "1W", "7 days"),
    MONTH(30, "1M", "30 days"),
    THREE_MONTHS(90, "3M", "3 months"),
    YEAR(365, "1Y", "1 year"),
    ;

    fun startDate(endDate: LocalDate): LocalDate = endDate.minusDays(days - 1)
}

internal class HealthDateRangeStore(context: Context) {
    private val preferences = context.getSharedPreferences("health-date-range", Context.MODE_PRIVATE)

    fun load(): HealthDateRange = preferences.getString(KEY, null)
        ?.let { saved -> HealthDateRange.entries.firstOrNull { it.name == saved } }
        ?: HealthDateRange.MONTH

    fun save(range: HealthDateRange) {
        preferences.edit().putString(KEY, range.name).apply()
    }

    companion object {
        private const val KEY = "selected-range-v1"
    }
}
