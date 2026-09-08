package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutTest {
    @Test
    fun defaults_showEveryCardInExpectedOrder() {
        val layout = DashboardLayout()

        assertEquals(DashboardCard.entries, layout.visibleCards)
    }

    @Test
    fun visibilityAndOrder_canBeCustomizedIndependently() {
        val changed = DashboardLayout()
            .setVisible(DashboardCard.BODY_FAT, false)
            .moveCard(DashboardCard.entries.indexOf(DashboardCard.TODAY), -1)

        assertEquals(DashboardCard.TODAY, changed.cards[DashboardCard.entries.lastIndex - 1].card)
        assertFalse(changed.cards.first { it.card == DashboardCard.BODY_FAT }.visible)
        assertFalse(DashboardCard.BODY_FAT in changed.visibleCards)
    }

    @Test
    fun normalize_removesDuplicatesAndAddsMissingCards() {
        val restored = normalizeDashboardCards(
            listOf(
                DashboardCardPreference(DashboardCard.WEIGHT, false),
                DashboardCardPreference(DashboardCard.WEIGHT, true),
            ),
        )

        assertEquals(DashboardCard.entries.size, restored.size)
        assertFalse(restored.first().visible)
        assertTrue(restored.drop(1).all { it.visible })
    }
}
