package dev.draftingroom5

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

internal sealed interface DashboardSection {
    data object Training : DashboardSection
    data class Metrics(val cards: List<DashboardCard>) : DashboardSection
}

internal fun defaultDashboardCards(): List<DashboardCardPreference> =
    listOf(
        DashboardCardPreference(DashboardCard.TODAY),
        DashboardCardPreference(DashboardCard.WEIGHT),
        DashboardCardPreference(DashboardCard.BODY_FAT),
        DashboardCardPreference(DashboardCard.DISTANCE),
        DashboardCardPreference(DashboardCard.LEAN_MASS),
        DashboardCardPreference(DashboardCard.WORKOUTS, visible = false),
    )

internal fun normalizeDashboardCards(cards: List<DashboardCardPreference>): List<DashboardCardPreference> {
    val known = cards.distinctBy { it.card }
    val completed = known + defaultDashboardCards()
        .filterNot { candidate -> known.any { it.card == candidate.card } }
    return if (completed.any { it.visible }) completed else defaultDashboardCards()
}

internal fun DashboardLayout.moveCard(index: Int, offset: Int): DashboardLayout =
    copy(cards = move(cards, index, offset))

internal fun DashboardLayout.moveCard(card: DashboardCard, targetIndex: Int): DashboardLayout {
    val from = cards.indexOfFirst { it.card == card }
    if (from !in cards.indices || targetIndex !in cards.indices || from == targetIndex) return this
    return copy(cards = cards.toMutableList().apply { add(targetIndex, removeAt(from)) })
}

internal fun DashboardLayout.setVisible(card: DashboardCard, visible: Boolean): DashboardLayout =
    if (!visible && visibleCards.size == 1 && card in visibleCards) this
    else copy(cards = cards.map { if (it.card == card) it.copy(visible = visible) else it })

/** Keeps every saved card in order while retaining compact metric grids between training sections. */
internal fun DashboardLayout.dashboardSections(): List<DashboardSection> = buildList {
    val metrics = mutableListOf<DashboardCard>()
    fun flushMetrics() {
        if (metrics.isNotEmpty()) {
            add(DashboardSection.Metrics(metrics.toList()))
            metrics.clear()
        }
    }
    visibleCards.forEach { card ->
        if (card == DashboardCard.TODAY) {
            flushMetrics()
            add(DashboardSection.Training)
        } else {
            metrics += card
        }
    }
    flushMetrics()
}
