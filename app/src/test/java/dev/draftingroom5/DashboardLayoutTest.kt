package dev.draftingroom5

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutTest {
    @Test
    fun defaults_useApprovedOrderAndHideWorkouts() {
        val layout = DashboardLayout()

        assertEquals(
            listOf(DashboardCard.TODAY, DashboardCard.WEIGHT, DashboardCard.BODY_FAT, DashboardCard.DISTANCE, DashboardCard.LEAN_MASS),
            layout.visibleCards,
        )
        assertFalse(layout.cards.single { it.card == DashboardCard.WORKOUTS }.visible)
        assertEquals("Distance", DashboardCard.DISTANCE.title)
    }

    @Test
    fun visibilityAndOrder_canBeCustomizedIndependently() {
        val changed = DashboardLayout()
            .setVisible(DashboardCard.BODY_FAT, false)
            .moveCard(0, 1)

        assertEquals(DashboardCard.TODAY, changed.cards[1].card)
        assertFalse(changed.cards.first { it.card == DashboardCard.BODY_FAT }.visible)
        assertFalse(DashboardCard.BODY_FAT in changed.visibleCards)
    }

    @Test
    fun everyCardCanMoveToFirstMiddleAndLastWithoutChangingVisibility() {
        DashboardCard.entries.forEach { card ->
            listOf(0, DashboardCard.entries.size / 2, DashboardCard.entries.lastIndex).forEach { target ->
                val original = DashboardLayout().setVisible(DashboardCard.WORKOUTS, true)
                val moved = original.moveCard(card, target)

                assertEquals(card, moved.cards[target].card)
                assertEquals(original.cards.associate { it.card to it.visible }, moved.cards.associate { it.card to it.visible })
            }
        }
    }

    @Test
    fun hiddenCardCanMoveAndDashboardSectionsFollowSavedOrderExactly() {
        val layout = DashboardLayout()
            .moveCard(DashboardCard.WORKOUTS, 0)
            .moveCard(DashboardCard.TODAY, 3)
        val visible = layout.setVisible(DashboardCard.WORKOUTS, true)

        assertFalse(layout.cards.first().visible)
        assertEquals(
            listOf(
                DashboardSection.Metrics(listOf(DashboardCard.WORKOUTS, DashboardCard.WEIGHT, DashboardCard.BODY_FAT)),
                DashboardSection.Training,
                DashboardSection.Metrics(listOf(DashboardCard.DISTANCE, DashboardCard.LEAN_MASS)),
            ),
            visible.dashboardSections(),
        )
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
        assertEquals(defaultDashboardCards().drop(1).map { it.visible }, restored.drop(1).map { it.visible })
    }

    @Test
    fun atLeastOneDashboardSectionRemainsVisible() {
        val onlyWeight = DashboardLayout(
            DashboardCard.entries.map { DashboardCardPreference(it, it == DashboardCard.WEIGHT) },
        )

        assertEquals(onlyWeight, onlyWeight.setVisible(DashboardCard.WEIGHT, false))
    }

    @Test
    fun orderVisibilityAndResetPersistThroughCurrentDocumentStore() {
        val storage = DashboardDocumentStorage()
        val repository = AppRepository(storage)
        val original = (repository.load() as LoadState.Ready).value
        val customized = original.preferences.dashboardLayout
            .moveCard(DashboardCard.DISTANCE, 0)
            .setVisible(DashboardCard.TODAY, false)

        val saved = (repository.update(original.generation) {
            it.copy(preferences = it.preferences.copy(dashboardLayout = customized))
        } as RepositoryResult.Success).value
        assertEquals(customized, decodeAppDocument(storage.value!!).preferences.dashboardLayout)

        // Cancelling the reset performs no repository mutation.
        assertEquals(customized, (repository.state.value as LoadState.Ready).value.preferences.dashboardLayout)

        val reset = (repository.update(saved.generation) {
            it.copy(preferences = it.preferences.copy(dashboardLayout = DashboardLayout()))
        } as RepositoryResult.Success).value
        assertEquals(DashboardLayout(), reset.preferences.dashboardLayout)
        assertEquals(DashboardLayout(), decodeAppDocument(storage.value!!).preferences.dashboardLayout)
    }
}

private class DashboardDocumentStorage : DocumentStorage {
    var value: String? = null
    override fun exists() = value != null
    override fun read(): String = value ?: throw IOException("missing")
    override fun write(value: String) { this.value = value }
}
