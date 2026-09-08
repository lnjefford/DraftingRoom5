package dev.draftingroom5

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal enum class DashboardCard(val title: String, val description: String) {
    WEIGHT("Weight", "Latest weight from Health Connect"),
    BODY_FAT("Body fat", "Latest body-fat percentage"),
    LEAN_MASS("Lean mass", "Latest lean body mass"),
    WORKOUTS("Workouts", "Exercise sessions in the selected range"),
    DISTANCE("Distance", "Distance recorded in the selected range"),
    TODAY("Today's training", "Scheduled workouts for today"),
}

internal data class DashboardCardPreference(
    val card: DashboardCard,
    val visible: Boolean = true,
)

internal data class DashboardLayout(
    val cards: List<DashboardCardPreference> = defaultDashboardCards(),
) {
    val visibleCards: List<DashboardCard>
        get() = cards.filter { it.visible }.map { it.card }
}

internal fun defaultDashboardCards(): List<DashboardCardPreference> =
    DashboardCard.entries.map { DashboardCardPreference(it) }

internal fun normalizeDashboardCards(cards: List<DashboardCardPreference>): List<DashboardCardPreference> {
    val known = cards.distinctBy { it.card }
    return known + DashboardCard.entries
        .filterNot { candidate -> known.any { it.card == candidate } }
        .map { DashboardCardPreference(it) }
}

internal fun DashboardLayout.moveCard(index: Int, offset: Int): DashboardLayout =
    copy(cards = move(cards, index, offset))

internal fun DashboardLayout.setVisible(card: DashboardCard, visible: Boolean): DashboardLayout =
    copy(cards = cards.map { if (it.card == card) it.copy(visible = visible) else it })

internal class DashboardLayoutStore(context: Context) {
    private val preferences = context.getSharedPreferences("dashboard-layout", Context.MODE_PRIVATE)

    fun load(): DashboardLayout {
        val value = preferences.getString(KEY, null) ?: return DashboardLayout()
        return runCatching { decodeDashboardLayout(value) }.getOrElse { DashboardLayout() }
    }

    fun save(layout: DashboardLayout) {
        preferences.edit().putString(KEY, encodeDashboardLayout(layout)).apply()
    }

    companion object {
        private const val KEY = "layout-v1"
    }
}

internal fun encodeDashboardLayout(layout: DashboardLayout): String = JSONObject().apply {
    put("cards", JSONArray().apply {
        layout.cards.forEach { preference ->
            put(JSONObject().apply {
                put("card", preference.card.name)
                put("visible", preference.visible)
            })
        }
    })
}.toString()

internal fun decodeDashboardLayout(value: String): DashboardLayout {
    val cardsJson = JSONObject(value).getJSONArray("cards")
    val cards = buildList {
        repeat(cardsJson.length()) { index ->
            val item = cardsJson.getJSONObject(index)
            val card = runCatching { DashboardCard.valueOf(item.getString("card")) }.getOrNull()
            if (card != null) add(DashboardCardPreference(card, item.optBoolean("visible", true)))
        }
    }
    return DashboardLayout(normalizeDashboardCards(cards))
}
