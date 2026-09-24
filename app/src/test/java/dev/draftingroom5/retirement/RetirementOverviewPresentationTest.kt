package dev.draftingroom5.retirement

import dev.draftingroom5.AppRoute
import dev.draftingroom5.retirement.domain.ImportMetadata
import dev.draftingroom5.retirement.domain.ImportStatus
import dev.draftingroom5.retirement.domain.ProviderError
import dev.draftingroom5.retirement.domain.ProviderItemState
import dev.draftingroom5.retirement.domain.ProviderStatus
import dev.draftingroom5.retirement.domain.TaxTreatment
import dev.draftingroom5.retirement.ui.DataHealthKind
import dev.draftingroom5.retirement.ui.RetirementLibraryResources
import dev.draftingroom5.retirement.ui.LibraryCategory
import dev.draftingroom5.retirement.ui.integratedAssetSummary
import dev.draftingroom5.retirement.ui.retirementDataHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RetirementOverviewPresentationTest {
    @Test fun multiAccountItemAndOrphanedAttentionStillRouteToActiveAccount() {
        val base = retirementFixture()
        val linked = base.accounts.single { it.id == "linked" }
        val itemId = linked.providerIdentity!!.itemId
        val state = base.copy(accounts = base.accounts + linked.copy(id = "linked-second"), providerItems = listOf(
            ProviderItemState("a-orphan", null, ProviderStatus.ATTENTION, NOW, NOW, null, ProviderError.UNAVAILABLE, 1, null),
            ProviderItemState(itemId, null, ProviderStatus.ATTENTION, NOW, NOW, null, ProviderError.AUTHENTICATION_REQUIRED, 1, null),
        ))
        val health = retirementDataHealth(state, NOW)
        assertEquals(AppRoute.RetirementAccountDetail("linked"), health.route)
        assertTrue(health.title.contains("needs attention"))
        assertFalse(health.stale)
    }
    @Test
    fun trackedAndForecastTotalsReconcileWithoutPropertyOrEpicWrapperDoubleCounting() {
        val summary = integratedAssetSummary(retirementFixture())

        assertEquals(7_855_000L, summary.tracked.cents)
        assertEquals(7_655_000L, summary.forecastEligible.cents)
        assertEquals(summary.tracked.cents, summary.rows.sumOf { it.amount.cents })
        assertEquals(100_000L, summary.rows.single { it.treatment == TaxTreatment.ROTH }.amount.cents)
        assertEquals(200_000L, summary.rows.single { it.treatment == TaxTreatment.PRE_TAX }.amount.cents)
        assertEquals(55_000L, summary.rows.single { it.treatment == TaxTreatment.EPIC }.amount.cents)
        assertEquals(7_500_000L, summary.rows.single { it.treatment == TaxTreatment.PROPERTY }.amount.cents)
    }

    @Test
    fun accountProviderAttentionRoutesToOnlyTheAffectedAccount() {
        val state = retirementFixture().copy(providerItems = listOf(
            ProviderItemState("item", "linked", ProviderStatus.ATTENTION, NOW, NOW, null,
                ProviderError.AUTHENTICATION_REQUIRED, 4, null),
        ))

        val health = retirementDataHealth(state, NOW)

        assertEquals(DataHealthKind.ACCOUNT, health.kind)
        assertEquals(AppRoute.RetirementAccountDetail("linked"), health.route)
    }

    @Test
    fun propertyAndImportAttentionRouteToTheirRepairFlows() {
        val base = retirementFixture()
        val propertyHealth = retirementDataHealth(base.copy(providerItems = listOf(
            ProviderItemState("property", "property-account", ProviderStatus.OFFLINE, NOW, NOW, null,
                ProviderError.UNAVAILABLE, 2, null),
        )), NOW)
        assertEquals(DataHealthKind.PROPERTY, propertyHealth.kind)
        assertEquals(AppRoute.RetirementPropertyDetail("property"), propertyHealth.route)

        val importHealth = retirementDataHealth(base.copy(
            providerItems = emptyList(),
            importMetadata = listOf(ImportMetadata("SHAREWORKS", ImportStatus.NEEDS_ATTENTION,
                base.activeEpicImportId, NOW, NOW, ProviderError.INVALID_RESPONSE)),
        ), NOW)
        assertEquals(DataHealthKind.EPIC_IMPORT, importHealth.kind)
        assertEquals(AppRoute.RetirementEpicUpload, importHealth.route)
    }

    @Test
    fun staleLinkedValueRoutesToItsAccountAndNeverClaimsFreshness() {
        val old = Instant.parse("2026-09-15T12:00:00Z")
        val base = retirementFixture()
        val state = base.copy(providerItems = listOf(
            base.providerItems.single().copy(id = "item", accountId = "linked", status = ProviderStatus.READY,
                lastAcceptedAt = old, error = null),
        ))

        val health = retirementDataHealth(state, Instant.parse("2026-09-21T12:00:00Z"))

        assertTrue(health.stale)
        assertEquals(AppRoute.RetirementAccountDetail("linked"), health.route)
        assertTrue(health.title.contains("stale"))
    }

    @Test
    fun libraryIsBundledGovernmentFirstMetadataWithChecklistIdsOnly() {
        assertEquals(15, RetirementLibraryResources.size)
        assertEquals(LibraryCategory.entries.toSet(), RetirementLibraryResources.map { it.category }.toSet())
        assertTrue(RetirementLibraryResources.all { resource ->
            resource.url.contains(".gov/") || resource.url.contains(".gov")
        })
        assertEquals(RetirementLibraryResources.size, RetirementLibraryResources.map { it.id }.distinct().size)
        assertTrue(RetirementLibraryResources.all { it.url.startsWith("https://") })
        assertFalse(RetirementLibraryResources.joinToString().contains("document", ignoreCase = true))
    }
}
