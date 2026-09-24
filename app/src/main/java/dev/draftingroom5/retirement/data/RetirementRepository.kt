package dev.draftingroom5.retirement.data

import dev.draftingroom5.retirement.domain.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RetirementResult<out T> {
    data class Success<T>(val value: T) : RetirementResult<T>
    data class Conflict(val currentGeneration: Long) : RetirementResult<Nothing>
    data class Invalid(val error: IllegalArgumentException) : RetirementResult<Nothing>
}

class RetirementRepository(
    private val dao: RetirementDao,
    private val onChanged: () -> Unit = {},
) {
    /** Wakeup signal only. Consumers must reload this repository's coherent committed document. */
    val changes get() = changeSignal.asStateFlow()
    internal fun ifGeneration(expected: Long, publish: () -> Unit): Boolean = synchronized(lock) {
        if (load().generation != expected) false else { publish(); true }
    }
    internal fun revokeProviderCredentials(expectedGeneration: Long, at: Instant): RetirementResult<RetirementState> = mutate(expectedGeneration) { state ->
        state.copy(providerItems = state.providerItems.map { it.copy(status = ProviderStatus.ATTENTION,
            error = ProviderError.AUTHENTICATION_REQUIRED, revokedAt = at, revision = Math.addExact(it.revision, 1)) })
    }
    internal fun acceptProviderBatch(expectedGeneration: Long, batch: dev.draftingroom5.retirement.provider.AccountBatch,
        selections: List<dev.draftingroom5.retirement.provider.AccountSelection>?): RetirementResult<RetirementState> = synchronized(lock) {
        val state = load()
        if (batch.operationId in state.acceptedProviderOperations) RetirementResult.Success(state)
        else mutate(expectedGeneration) { dev.draftingroom5.retirement.provider.reconcileAccounts(it, batch, selections) }
    }

    fun load(): RetirementState = synchronized(lock) {
        dao.state()?.let { row ->
            RetirementCodec.decode(row.document).also { require(it.generation == row.generation) { "Retirement generation mismatch." } }
        } ?: RetirementState().also { initial ->
            dao.initialize(RetirementStateEntity(generation = 0, document = RetirementCodec.encode(initial)))
        }
    }

    fun addManualAccount(expectedGeneration: Long, account: Account): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        require(account.origin == AccountOrigin.MANUAL && account.providerIdentity == null && account.archivedAt == null)
        require(account.balances.all { it.source == BalanceSource.MANUAL })
        current.copy(accounts = current.accounts + account)
    }

    fun appendManualBalance(expectedGeneration: Long, accountId: String, snapshot: BalanceSnapshot): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val account = current.accounts.singleOrNull { it.id == accountId } ?: throw IllegalArgumentException("Account not found.")
        require(account.origin == AccountOrigin.MANUAL && account.archivedAt == null) { "Only active manual accounts accept manual balances." }
        require(snapshot.source == BalanceSource.MANUAL && snapshot.id !in account.balances.map { it.id })
        snapshot.supersedesId?.let { id -> require(account.balances.any { it.id == id }) { "Correction target is missing." } }
        current.copy(accounts = current.accounts.map { if (it.id == accountId) it.copy(balances = it.balances + snapshot) else it })
    }

    fun reviseAccount(expectedGeneration: Long, accountId: String, revision: AccountRevision): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val account = current.accounts.singleOrNull { it.id == accountId } ?: throw IllegalArgumentException("Account not found.")
        require(revision.revision == account.currentRevision.revision + 1 && revision.previousRevisionId == account.currentRevision.id)
        current.copy(accounts = current.accounts.map { if (it.id == accountId) it.copy(revisions = it.revisions + revision) else it })
    }

    fun archiveAccount(expectedGeneration: Long, accountId: String, archivedAt: Instant): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val account = current.accounts.singleOrNull { it.id == accountId } ?: throw IllegalArgumentException("Account not found.")
        require(account.archivedAt == null)
        current.copy(
            accounts = current.accounts.map { if (it.id == accountId) it.copy(archivedAt = archivedAt) else it },
            properties = current.properties.map { if (it.accountId == accountId) it.copy(archivedAt = archivedAt) else it },
        )
    }

    fun addManualProperty(expectedGeneration: Long, wrapper: Account, property: Property): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        require(wrapper.origin == AccountOrigin.PROPERTY && wrapper.balances.isEmpty() && wrapper.providerIdentity == null)
        require(property.accountId == wrapper.id && property.archivedAt == null)
        require(property.valuations.all { it.source == ValuationSource.MANUAL })
        current.copy(accounts = current.accounts + wrapper, properties = current.properties + property)
    }

    internal fun addProviderProperty(expectedGeneration: Long, wrapper: Account, property: Property, item: ProviderItemState): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        require(wrapper.origin == AccountOrigin.PROPERTY && wrapper.balances.isEmpty() && wrapper.providerIdentity == null)
        require(property.accountId == wrapper.id && property.archivedAt == null && property.currentRevision.automaticValueEnabled)
        require(property.valuations.isNotEmpty() && property.valuations.all { it.source == ValuationSource.RENTCAST })
        require(item.id == property.id && item.accountId == wrapper.id && item.status == ProviderStatus.READY)
        current.copy(accounts = current.accounts + wrapper, properties = current.properties + property, providerItems = current.providerItems + item)
    }

    internal fun acceptProviderValuation(expectedGeneration: Long, propertyId: String, expectedPropertyRevision: Long,
        valuation: PropertyValuationSnapshot, attemptedAt: Instant): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val property = current.properties.singleOrNull { it.id == propertyId } ?: throw IllegalArgumentException("Property not found.")
        require(property.archivedAt == null && property.currentRevision.revision == expectedPropertyRevision && property.currentRevision.automaticValueEnabled)
        require(valuation.source == ValuationSource.RENTCAST && valuation.id !in property.valuations.map { it.id })
        val oldItem = current.providerItems.singleOrNull { it.id == propertyId }
        val item = ProviderItemState(propertyId, property.accountId, ProviderStatus.READY, attemptedAt, valuation.acceptedAt, null, null,
            Math.addExact(oldItem?.revision ?: 0, 1), null)
        val latest = property.currentValuation
        val duplicate = latest != null && latest.source == valuation.source && latest.providerAsOf == valuation.providerAsOf &&
            latest.estimate == valuation.estimate && latest.rangeLow == valuation.rangeLow && latest.rangeHigh == valuation.rangeHigh &&
            latest.comparableCount == valuation.comparableCount
        current.copy(properties = current.properties.map { if (it.id == propertyId && !duplicate) it.copy(valuations = it.valuations + valuation) else it },
            providerItems = current.providerItems.filterNot { it.id == propertyId } + item)
    }

    internal fun recordPropertyFailure(expectedGeneration: Long, propertyId: String, attemptedAt: Instant, error: ProviderError,
        retryAfter: Instant? = null): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val property = current.properties.singleOrNull { it.id == propertyId } ?: throw IllegalArgumentException("Property not found.")
        val old = current.providerItems.singleOrNull { it.id == propertyId }
        val status = when (error) {
            ProviderError.AUTHENTICATION_REQUIRED -> ProviderStatus.ATTENTION
            ProviderError.RATE_LIMITED -> ProviderStatus.RATE_LIMITED
            ProviderError.UNAVAILABLE -> ProviderStatus.OFFLINE
            else -> ProviderStatus.ATTENTION
        }
        val item = ProviderItemState(propertyId, property.accountId, status, attemptedAt, old?.lastAcceptedAt, retryAfter, error,
            Math.addExact(old?.revision ?: 0, 1), null)
        current.copy(providerItems = current.providerItems.filterNot { it.id == propertyId } + item)
    }

    fun appendManualValuation(expectedGeneration: Long, propertyId: String, valuation: PropertyValuationSnapshot): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val property = current.properties.singleOrNull { it.id == propertyId } ?: throw IllegalArgumentException("Property not found.")
        require(property.archivedAt == null && valuation.source == ValuationSource.MANUAL)
        valuation.supersedesId?.let { id -> require(property.valuations.any { it.id == id }) { "Correction target is missing." } }
        current.copy(properties = current.properties.map { if (it.id == propertyId) it.copy(valuations = it.valuations + valuation) else it })
    }

    fun reviseProperty(expectedGeneration: Long, propertyId: String, revision: PropertyRevision): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val property = current.properties.singleOrNull { it.id == propertyId } ?: throw IllegalArgumentException("Property not found.")
        require(revision.revision == property.currentRevision.revision + 1 && revision.previousRevisionId == property.currentRevision.id)
        current.copy(properties = current.properties.map { if (it.id == propertyId) it.copy(revisions = it.revisions + revision) else it })
    }

    internal fun acceptEpicImport(expectedGeneration: Long, import: AcceptedEpicImport, metadata: ImportMetadata): RetirementResult<RetirementState> = synchronized(lock) {
        val current = load()
        if (current.generation != expectedGeneration) return@synchronized RetirementResult.Conflict(current.generation)
        val duplicate = current.epicImports.singleOrNull { it.id == current.activeEpicImportId && it.contentDigest == import.contentDigest }
        if (duplicate != null) {
            if (current.importMetadata.any { it.source == "SHAREWORKS" && it.status == ImportStatus.NEEDS_ATTENTION }) {
                return@synchronized mutate(expectedGeneration) { state -> state.copy(importMetadata = state.importMetadata.filterNot { it.source == "SHAREWORKS" } +
                    ImportMetadata("SHAREWORKS", ImportStatus.ACCEPTED, duplicate.id, metadata.attemptedAt, duplicate.acceptedAt, null)) }
            }
            return@synchronized RetirementResult.Success(current)
        }
        mutate(expectedGeneration) { current ->
        require(import.id == metadata.acceptedImportId && metadata.status == ImportStatus.ACCEPTED)
        require(metadata.source == "SHAREWORKS" && metadata.lastAcceptedAt == import.acceptedAt && metadata.safeError == null)
        require(import.replacesImportId == current.activeEpicImportId)
        current.copy(epicImports = current.epicImports + import, activeEpicImportId = import.id,
            importMetadata = current.importMetadata.filterNot { it.source == metadata.source } + metadata)
        }
    }

    internal fun recordEpicFailure(expectedGeneration: Long, at: Instant): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val active = current.epicImports.singleOrNull { it.id == current.activeEpicImportId }
        current.copy(importMetadata = current.importMetadata.filterNot { it.source == "SHAREWORKS" } +
            ImportMetadata("SHAREWORKS", ImportStatus.NEEDS_ATTENTION, active?.id, at, active?.acceptedAt, ProviderError.INVALID_RESPONSE))
    }

    fun savePlan(expectedGeneration: Long, settings: PlanSettings): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        val prior = current.planSettings.maxByOrNull { it.revision }
        require(settings.revision == (prior?.revision ?: 0) + 1)
        current.copy(planSettings = current.planSettings + settings)
    }

    fun setChecklist(expectedGeneration: Long, state: ChecklistState): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        require(state.catalogId.isNotBlank())
        current.copy(checklist = current.checklist.filterNot { it.catalogId == state.catalogId } + state)
    }

    fun setProviderItem(expectedGeneration: Long, item: ProviderItemState): RetirementResult<RetirementState> = mutate(expectedGeneration) { current ->
        require(item.revision > 0 && (item.accountId == null || current.accounts.any { it.id == item.accountId }))
        current.copy(providerItems = current.providerItems.filterNot { it.id == item.id } + item)
    }

    fun restore(snapshot: RetirementState): RetirementResult<RetirementState> = synchronized(lock) {
        val current = load()
        val restored = try {
            require(current.generation < Long.MAX_VALUE)
            snapshot.copy(generation = current.generation + 1).also(::validateRetirementState)
        } catch (error: IllegalArgumentException) {
            return@synchronized RetirementResult.Invalid(error)
        }
        if (!dao.restore(
                current.generation,
                restored,
                restored.accounts.flatMap { account -> account.balances.map { balance ->
                    BalanceLedgerEntity(balance.id, account.id, balance.asOfDate.toString(), balance.acceptedAt.toString(), balance.sequence,
                        balance.amount.cents, balance.basis?.cents, balance.source.name, balance.batchId, balance.supersedesId)
                } },
                restored.properties.flatMap { property -> property.valuations.map { valuation ->
                    ValuationLedgerEntity(valuation.id, property.id, valuation.estimate.cents, valuation.rangeLow?.cents, valuation.rangeHigh?.cents,
                        valuation.source.name, valuation.acceptedAt.toString(), valuation.sequence, valuation.batchId, valuation.supersedesId)
                } },
                restored.epicImports.map { import -> EpicImportLedgerEntity(import.id, import.formatId, import.parserVersion,
                    import.acceptedAt.toString(), import.contentDigest, import.replacesImportId) },
            )) {
            return@synchronized RetirementResult.Conflict(load().generation)
        }
        changeSignal.value = Math.addExact(changeSignal.value, 1)
        onChanged()
        RetirementResult.Success(restored)
    }

    private fun mutate(expectedGeneration: Long, transform: (RetirementState) -> RetirementState): RetirementResult<RetirementState> = synchronized(lock) {
        val current = load()
        if (current.generation != expectedGeneration) return@synchronized RetirementResult.Conflict(current.generation)
        val candidate = try {
            require(current.generation < Long.MAX_VALUE)
            transform(current).copy(generation = current.generation + 1).also(::validateRetirementState)
        } catch (error: IllegalArgumentException) {
            return@synchronized RetirementResult.Invalid(error)
        }
        val balances = appendedBalances(current, candidate)
        val valuations = appendedValuations(current, candidate)
        val imports = appendedImports(current, candidate)
        check(dao.commit(expectedGeneration, candidate, balances, valuations, imports)) { "Concurrent Retirement write escaped process lock." }
        changeSignal.value = Math.addExact(changeSignal.value, 1)
        onChanged()
        RetirementResult.Success(candidate)
    }

    private fun appendedBalances(before: RetirementState, after: RetirementState): List<BalanceLedgerEntity> = after.accounts.flatMap { account ->
        val old = before.accounts.singleOrNull { it.id == account.id }?.balances.orEmpty()
        require(account.balances.take(old.size) == old) { "Balance history is append-only." }
        account.balances.drop(old.size).map { BalanceLedgerEntity(it.id, account.id, it.asOfDate.toString(), it.acceptedAt.toString(), it.sequence,
            it.amount.cents, it.basis?.cents, it.source.name, it.batchId, it.supersedesId) }
    }

    private fun appendedValuations(before: RetirementState, after: RetirementState): List<ValuationLedgerEntity> = after.properties.flatMap { property ->
        val old = before.properties.singleOrNull { it.id == property.id }?.valuations.orEmpty()
        require(property.valuations.take(old.size) == old) { "Property valuation history is append-only." }
        property.valuations.drop(old.size).map { ValuationLedgerEntity(it.id, property.id, it.estimate.cents, it.rangeLow?.cents, it.rangeHigh?.cents,
            it.source.name, it.acceptedAt.toString(), it.sequence, it.batchId, it.supersedesId) }
    }

    private fun appendedImports(before: RetirementState, after: RetirementState): List<EpicImportLedgerEntity> {
        require(after.epicImports.take(before.epicImports.size) == before.epicImports) { "Epic import history is append-only." }
        return after.epicImports.drop(before.epicImports.size).map { EpicImportLedgerEntity(it.id, it.formatId, it.parserVersion, it.acceptedAt.toString(), it.contentDigest, it.replacesImportId) }
    }

    companion object {
        private val lock = Any()
        private val changeSignal = MutableStateFlow(0L)
    }
}
