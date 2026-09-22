package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.AccountOrigin
import dev.draftingroom5.retirement.domain.AccountType
import dev.draftingroom5.retirement.domain.ProviderStatus
import dev.draftingroom5.retirement.ui.AccountGroup
import dev.draftingroom5.retirement.ui.activeAccountsByGroup
import dev.draftingroom5.retirement.ui.group
import dev.draftingroom5.retirement.ui.needsAttention
import dev.draftingroom5.retirement.ui.sourceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AccountsPresentationTest {
    @Test fun approvedGroupsCoverEveryAccountClassification() {
        val expected = mapOf(
            AccountType.EMPLOYER_401K to AccountGroup.EMPLOYER,
            AccountType.EMPLOYER_403B to AccountGroup.EMPLOYER,
            AccountType.EMPLOYER_457 to AccountGroup.EMPLOYER,
            AccountType.PROFIT_SHARING to AccountGroup.EMPLOYER,
            AccountType.IRA_TRADITIONAL to AccountGroup.IRA,
            AccountType.IRA_ROTH to AccountGroup.IRA,
            AccountType.HSA to AccountGroup.HEALTH,
            AccountType.BROKERAGE to AccountGroup.BROKERAGE,
            AccountType.CRYPTO to AccountGroup.BROKERAGE,
            AccountType.EPIC to AccountGroup.BROKERAGE,
            AccountType.CASH to AccountGroup.CASH,
            AccountType.CD to AccountGroup.CASH,
            AccountType.PROPERTY to AccountGroup.PROPERTY,
        )
        val fixture = retirementFixture()
        val template = fixture.accounts.first()
        expected.forEach { (type, group) ->
            val account = template.copy(revisions = listOf(template.currentRevision.copy(type = type)))
            assertEquals(group, account.group())
        }
        assertEquals(AccountGroup.entries.toSet(), expected.values.toSet())
        assertEquals(AccountType.entries.toSet(), expected.keys)
    }

    @Test fun listExcludesArchivedAndKeepsSourcesExplicit() {
        val fixture = retirementFixture()
        val archived = fixture.accounts.first().copy(id = "archived", archivedAt = NOW)
        val grouped = fixture.copy(accounts = fixture.accounts + archived).activeAccountsByGroup()
        assertFalse(grouped.values.flatten().any { it.id == "archived" })
        assertFalse(grouped.values.flatten().any { it.origin == AccountOrigin.EPIC })
        assertEquals(fixture.properties.map { it.accountId }, grouped.getValue(AccountGroup.PROPERTY).map { it.id })
        assertEquals("Manual account", sourceLabel(fixture.accounts.first { it.origin == AccountOrigin.MANUAL }))
        assertEquals("Linked account", sourceLabel(fixture.accounts.first { it.origin == AccountOrigin.PLAID }))
    }

    @Test fun attentionStaysAttachedToOnlyTheAffectedLinkedAccount() {
        val base = retirementFixture()
        val linked = base.accounts.first { it.origin == AccountOrigin.PLAID }
        val itemId = linked.providerIdentity!!.itemId
        val ready = base.providerItems.single().copy(id = itemId, accountId = linked.id, status = ProviderStatus.READY,
            lastAcceptedAt = Instant.parse("2026-09-19T18:00:00Z"))
        val state = base.copy(providerItems = listOf(ready))
        val now = Instant.parse("2026-09-19T19:00:00Z")
        assertFalse(state.needsAttention(linked, now))
        assertFalse(state.needsAttention(base.accounts.first { it.origin == AccountOrigin.MANUAL }, now))
        assertTrue(state.copy(providerItems = listOf(ready.copy(status = ProviderStatus.ATTENTION))).needsAttention(linked, now))
        assertTrue(state.copy(providerItems = listOf(ready.copy(lastAcceptedAt = now.minusSeconds(172_801)))).needsAttention(linked, now))
    }
}
