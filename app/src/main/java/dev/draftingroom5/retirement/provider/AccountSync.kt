package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.domain.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LinkedAccountData(
    val providerAccountId: String, val name: String, val mask: String?, val subtype: String?,
    val amount: Money?, val asOf: LocalDate, val holdings: List<Holding>, val availability: HoldingAvailability,
) { override fun toString() = "LinkedAccountData(redacted)" }
data class AccountBatch(
    val item: CredentialHandle, val profile: CredentialHandle, val operationId: String,
    val accounts: List<LinkedAccountData>, val acceptedAt: Instant,
) { override fun toString() = "AccountBatch(redacted)" }
data class AccountSelection(
    val providerAccountId: String, val name: String, val owner: Owner, val type: AccountType,
    val tax: TaxTreatment, val included: Boolean, val replaceManualId: String? = null,
) { override fun toString() = "AccountSelection(redacted)" }

object AccountClassification {
    fun suggest(subtype: String?): Pair<AccountType, TaxTreatment>? = when (subtype?.lowercase()) {
        "401k" -> AccountType.EMPLOYER_401K to TaxTreatment.PRE_TAX
        "403b" -> AccountType.EMPLOYER_403B to TaxTreatment.PRE_TAX
        "457b" -> AccountType.EMPLOYER_457 to TaxTreatment.PRE_TAX
        "profit sharing plan" -> AccountType.PROFIT_SHARING to TaxTreatment.PRE_TAX
        "ira", "sep ira", "simple ira" -> AccountType.IRA_TRADITIONAL to TaxTreatment.PRE_TAX
        "roth", "roth ira" -> AccountType.IRA_ROTH to TaxTreatment.ROTH
        "hsa" -> AccountType.HSA to TaxTreatment.TAX_FREE
        "brokerage" -> AccountType.BROKERAGE to TaxTreatment.TAXABLE
        "checking", "savings", "money market", "cash management" -> AccountType.CASH to TaxTreatment.TAXABLE
        "cd" -> AccountType.CD to TaxTreatment.TAXABLE
        "crypto exchange" -> AccountType.CRYPTO to TaxTreatment.TAXABLE
        else -> null
    }
    fun valid(type: AccountType, tax: TaxTreatment): Boolean = when (type) {
        AccountType.EMPLOYER_401K, AccountType.EMPLOYER_403B, AccountType.EMPLOYER_457, AccountType.PROFIT_SHARING -> tax in setOf(TaxTreatment.PRE_TAX, TaxTreatment.ROTH)
        AccountType.IRA_TRADITIONAL -> tax == TaxTreatment.PRE_TAX
        AccountType.IRA_ROTH -> tax == TaxTreatment.ROTH
        AccountType.HSA -> tax == TaxTreatment.TAX_FREE
        AccountType.BROKERAGE, AccountType.CASH, AccountType.CD, AccountType.CRYPTO -> tax == TaxTreatment.TAXABLE
        else -> false
    }
}

/** Pure reconciliation invoked only inside the repository transaction. Masks are never identity. */
internal fun reconcileAccounts(state: RetirementState, batch: AccountBatch, selections: List<AccountSelection>?): RetirementState {
    require(batch.accounts.isNotEmpty() && batch.accounts.size <= 500)
    require(batch.accounts.map { it.providerAccountId }.distinct().size == batch.accounts.size)
    require(batch.profile.kind == CredentialKind.PLAID && batch.item.kind == CredentialKind.PLAID_ITEM)
    require(batch.profile.environment == batch.item.environment)
    require(batch.operationId == "${batch.item.id}:${batch.item.revision}" && batch.operationId !in state.acceptedProviderOperations)
    val prior = state.providerItems.singleOrNull { it.id == batch.item.id }
    require(prior?.revokedAt == null && (prior == null || batch.item.revision > prior.revision))
    val scoped = state.accounts.filter { it.providerIdentity?.itemId == batch.item.id && it.archivedAt == null }
    // An incomplete authenticated response must never clear accepted balances or holdings.
    require(scoped.all { a -> batch.accounts.any { it.providerAccountId == a.providerIdentity!!.providerAccountId } })
    selections?.let { selected ->
        require(selected.isNotEmpty() && selected.map { it.providerAccountId }.distinct().size == selected.size)
        require(selected.mapNotNull { it.replaceManualId }.let { it.size == it.distinct().size })
        require(selected.all { s -> batch.accounts.any { it.providerAccountId == s.providerAccountId } && AccountClassification.valid(s.type, s.tax) })
    }
    val accounts = state.accounts.toMutableList()
    batch.accounts.forEach { remote ->
        val identity = ProviderIdentity(batch.profile.id, batch.profile.environment, batch.item.id, remote.providerAccountId)
        var existing = accounts.singleOrNull { it.providerIdentity == identity }
        val selection = selections?.singleOrNull { it.providerAccountId == remote.providerAccountId }
        if (existing == null && selection == null) return@forEach
        if (existing?.archivedAt != null) return@forEach
        if (existing == null) {
            val chosen = requireNotNull(selection)
            val manual = chosen.replaceManualId?.let { id -> accounts.single { it.id == id }.also { priorAccount ->
                val disconnected = priorAccount.origin == AccountOrigin.PLAID && state.providerItems.any {
                    it.id == priorAccount.providerIdentity?.itemId && it.revokedAt != null
                }
                require((priorAccount.origin == AccountOrigin.MANUAL || disconnected) && priorAccount.archivedAt == null)
            } }
            val revision = AccountRevision(UUID.randomUUID().toString(), (manual?.currentRevision?.revision ?: 0) + 1,
                chosen.name, chosen.owner, chosen.type, chosen.tax, chosen.included, batch.acceptedAt, manual?.currentRevision?.id)
            existing = Account(manual?.id ?: UUID.randomUUID().toString(), AccountOrigin.PLAID, null, identity,
                manual?.revisions.orEmpty() + revision, manual?.balances.orEmpty(), manual?.holdingSnapshots.orEmpty())
        }
        val old = existing
        val latest = old.currentBalance
        val appendBalance = remote.amount != null && (latest == null || latest.asOfDate != remote.asOf || latest.amount != remote.amount || latest.source != BalanceSource.PLAID)
        val balance = if (appendBalance) listOf(BalanceSnapshot(UUID.randomUUID().toString(), remote.asOf, batch.acceptedAt,
            (old.balances.maxOfOrNull { it.sequence } ?: 0) + 1, remote.amount!!, null, BalanceSource.PLAID, batch.operationId)) else emptyList()
        val lastHoldings = old.holdingSnapshots.lastOrNull()
        val appendHoldings = lastHoldings == null || (remote.availability != HoldingAvailability.PENDING &&
            (lastHoldings.availability != remote.availability || lastHoldings.holdings != remote.holdings))
        val holdings = if (appendHoldings) listOf(HoldingSnapshot(batch.operationId, batch.acceptedAt, remote.availability, remote.holdings)) else emptyList()
        val next = old.copy(balances = old.balances + balance, holdingSnapshots = old.holdingSnapshots + holdings)
        val index = accounts.indexOfFirst { it.id == next.id }
        if (index < 0) accounts.add(next) else accounts[index] = next
    }
    val item = ProviderItemState(batch.item.id, null, ProviderStatus.READY, batch.acceptedAt, batch.acceptedAt, null, null, batch.item.revision, null)
    return state.copy(accounts = accounts, providerItems = state.providerItems.filterNot { it.id == item.id } + item,
        acceptedProviderOperations = state.acceptedProviderOperations + batch.operationId)
}
