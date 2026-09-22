package dev.draftingroom5.retirement.ui

import dev.draftingroom5.retirement.domain.Account
import dev.draftingroom5.retirement.domain.AccountOrigin
import dev.draftingroom5.retirement.domain.AccountType
import dev.draftingroom5.retirement.domain.ProviderItemState
import dev.draftingroom5.retirement.domain.ProviderStatus
import dev.draftingroom5.retirement.domain.RetirementState
import java.time.Instant
import java.time.temporal.ChronoUnit

internal enum class AccountGroup(val label: String) {
    EMPLOYER("Employer plans"),
    IRA("IRAs"),
    HEALTH("Health savings"),
    BROKERAGE("Brokerage"),
    CASH("Cash"),
    PROPERTY("Properties"),
}

internal fun Account.group(): AccountGroup = when (currentRevision.type) {
    AccountType.EMPLOYER_401K, AccountType.EMPLOYER_403B, AccountType.EMPLOYER_457, AccountType.PROFIT_SHARING -> AccountGroup.EMPLOYER
    AccountType.IRA_TRADITIONAL, AccountType.IRA_ROTH -> AccountGroup.IRA
    AccountType.HSA -> AccountGroup.HEALTH
    AccountType.BROKERAGE, AccountType.CRYPTO, AccountType.EPIC -> AccountGroup.BROKERAGE
    AccountType.CASH, AccountType.CD -> AccountGroup.CASH
    AccountType.PROPERTY -> AccountGroup.PROPERTY
}

internal fun RetirementState.activeAccountsByGroup(): Map<AccountGroup, List<Account>> =
    accounts.filter { it.archivedAt == null && it.origin in setOf(AccountOrigin.MANUAL, AccountOrigin.PLAID, AccountOrigin.PROPERTY) }.groupBy(Account::group)

internal fun RetirementState.providerItem(account: Account): ProviderItemState? =
    account.providerIdentity?.itemId?.let { itemId -> providerItems.singleOrNull { it.id == itemId } }

internal fun RetirementState.needsAttention(account: Account, now: Instant = Instant.now()): Boolean {
    if (account.origin != AccountOrigin.PLAID) return false
    val item = providerItem(account) ?: return true
    return item.status != ProviderStatus.READY || item.revokedAt != null ||
        item.lastAcceptedAt?.isBefore(now.minus(2, ChronoUnit.DAYS)) != false
}

internal fun sourceLabel(account: Account): String = when (account.origin) {
    AccountOrigin.PLAID -> "Linked account"
    AccountOrigin.MANUAL -> "Manual account"
    AccountOrigin.PROPERTY -> "Property"
    AccountOrigin.EPIC -> "Epic workbook"
}

internal fun enumLabel(value: Enum<*>): String = value.name.lowercase().replace('_', ' ')
    .replaceFirstChar { it.uppercase() }
