package dev.draftingroom5

import android.content.Context

internal enum class TodayTeam(val displayName: String, val scheduleUrl: String) {
    BREWERS("Milwaukee Brewers", "https://www.mlb.com/brewers/schedule"),
    PACKERS("Green Bay Packers", "https://www.packers.com/schedule/"),
    INDIANA_FOOTBALL("Indiana football", "https://iuhoosiers.com/sports/football/schedule"),
    INDIANA_BASKETBALL("Indiana basketball", "https://iuhoosiers.com/sports/mens-basketball/schedule"),
    WISCONSIN_FOOTBALL("Wisconsin football", "https://uwbadgers.com/sports/football/schedule"),
    WISCONSIN_BASKETBALL("Wisconsin basketball", "https://uwbadgers.com/sports/mens-basketball/schedule"),
}

internal fun marketUrl(symbol: String): String = when (symbol) {
    "AAPL" -> "https://www.nasdaq.com/market-activity/stocks/aapl"
    "^IXIC" -> "https://www.nasdaq.com/market-activity/index/comp"
    else -> "https://www.google.com/finance/search?q=${java.net.URLEncoder.encode(symbol, "UTF-8")}"
}

internal class TodayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("today-favorites", Context.MODE_PRIVATE)

    fun symbols(): List<String> {
        val raw = preferences.getString("symbols", null) ?: return listOf("AAPL", "^IXIC")
        return runCatching { parseSymbols(raw) }.getOrDefault(listOf("AAPL", "^IXIC"))
    }

    fun setSymbols(symbols: List<String>) {
        preferences.edit().putString("symbols", symbols.joinToString(",")).apply()
    }

    fun teams(): List<TodayTeam> {
        val raw = preferences.getString("teams", null) ?: return TodayTeam.entries
        return raw.split(',').mapNotNull { name -> TodayTeam.entries.firstOrNull { it.name == name } }.distinct()
    }

    fun setTeams(teams: List<TodayTeam>) {
        preferences.edit().putString("teams", teams.joinToString(",") { it.name }).apply()
    }
}

internal fun <T> moveTodayItem(items: List<T>, index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in items.indices || target !in items.indices) return items
    return items.toMutableList().apply { add(target, removeAt(index)) }
}

internal fun parseSymbols(input: String): List<String> {
    val symbols = input.split(',', ' ', '\n').map { it.trim().uppercase() }.filter { it.isNotBlank() }
    require(symbols.size in 1..8 && symbols.distinct().size == symbols.size) { "Enter 1–8 unique symbols." }
    require(symbols.all { it.matches(Regex("\\^?[A-Z0-9][A-Z0-9.-]{0,11}")) }) { "Use valid market symbols separated by commas." }
    return symbols
}
