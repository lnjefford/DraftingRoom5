package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import org.junit.Assert.*
import org.junit.Test

class RetirementRepositoryTest {
    @Test fun malformedExtremeBalancesAndMortgageValuesCannotEnterStorage() {
        val repository = RetirementRepository(FakeRetirementDao())
        val fixture = retirementFixture(); val account = fixture.accounts.first()
        val invalid = account.copy(balances = listOf(account.balances.single().copy(amount = Money(Long.MIN_VALUE))))
        assertTrue(repository.addManualAccount(0, invalid) is RetirementResult.Invalid)
        val property = fixture.properties.single()
        val revision = property.currentRevision
        val invalidProperty = property.copy(revisions = listOf(revision.copy(mortgage = revision.mortgage.copy(outstanding = Money(Long.MAX_VALUE)))))
        assertTrue(repository.addManualProperty(0, fixture.accounts.single { it.id == property.accountId }, invalidProperty) is RetirementResult.Invalid)
        assertEquals(0L, repository.load().generation)
    }
    @Test fun manualHistoryCorrectionsAppendAndSurviveRepositoryRecreation() {
        val dao = FakeRetirementDao()
        var repository = RetirementRepository(dao)
        val account = retirementFixture().accounts.first()
        val added = success(repository.addManualAccount(0, account))
        val correction = account.balances.single().copy(id = "balance-correction", sequence = 2, amount = Money(125_000), supersedesId = "balance-1")
        val corrected = success(repository.appendManualBalance(added.generation, account.id, correction))
        assertEquals(listOf("balance-1", "balance-correction"), corrected.accounts.single().balances.map { it.id })
        assertEquals(125_000L, corrected.accounts.single().currentBalance!!.amount.cents)
        repository = RetirementRepository(dao)
        assertEquals(corrected, repository.load())
        assertEquals(2, dao.balanceCount())
    }

    @Test fun propertyCorrectionArchiveAndAggregateNeverDoubleCountWrapper() {
        val dao = FakeRetirementDao(); val repository = RetirementRepository(dao)
        val fixture = retirementFixture(); val wrapper = fixture.accounts.single { it.origin == AccountOrigin.PROPERTY }; val property = fixture.properties.single()
        var state = success(repository.addManualProperty(0, wrapper, property))
        val correction = property.currentValuation!!.copy(id = "valuation-2", estimate = Money(26_000_000), rangeLow = null, rangeHigh = null,
            sequence = 2, supersedesId = property.currentValuation!!.id)
        state = success(repository.appendManualValuation(state.generation, property.id, correction))
        assertEquals(8_000_000L, AssetAggregator.totals(state).tracked.cents)
        state = success(repository.archiveAccount(state.generation, wrapper.id, NOW.plusSeconds(1)))
        assertEquals(0L, AssetAggregator.totals(state).tracked.cents)
        assertEquals(2, dao.valuationCount())
    }

    @Test fun invalidOrStaleMutationsLeaveAtomicStorageUntouched() {
        val dao = FakeRetirementDao(); val repository = RetirementRepository(dao); val account = retirementFixture().accounts.first()
        val state = success(repository.addManualAccount(0, account))
        val before = dao.state()
        val bad = account.balances.single().copy(id = "bad", sequence = 2, source = BalanceSource.PLAID)
        assertTrue(repository.appendManualBalance(state.generation, account.id, bad) is RetirementResult.Invalid)
        assertEquals(before, dao.state())
        assertTrue(repository.setChecklist(0, ChecklistState("x", true, NOW)) is RetirementResult.Conflict)
        dao.failCommit = true
        assertThrows(IllegalStateException::class.java) { repository.setChecklist(state.generation, ChecklistState("x", true, NOW)) }
        assertEquals(before, dao.state())
    }

    @Test fun planChecklistEpicAndMetadataPersistExactly() {
        val dao = FakeRetirementDao(); val repository = RetirementRepository(dao); val fixture = retirementFixture()
        var state = success(repository.savePlan(0, fixture.planSettings.single()))
        state = success(repository.setChecklist(state.generation, fixture.checklist.single()))
        state = success(repository.acceptEpicImport(state.generation, fixture.epicImports.single(), fixture.importMetadata.single()))
        assertEquals(fixture.planSettings, state.planSettings)
        assertEquals(fixture.checklist, state.checklist)
        assertEquals(fixture.epicImports, state.epicImports)
        assertEquals(fixture.importMetadata, state.importMetadata)
        assertEquals(1, dao.epicImportCount())
        assertEquals(state, RetirementRepository(dao).load())
    }

    @Test fun accountEditsAppendRevisionArchiveAndLinkedValuesStayReadOnly() {
        val dao = FakeRetirementDao(); val repository = RetirementRepository(dao)
        val fixture = retirementFixture(); val linked = fixture.accounts.first { it.origin == AccountOrigin.PLAID }
        val itemId = "00000000-0000-0000-0000-000000000071"
        var state = success(repository.addManualAccount(0, fixture.accounts.first { it.origin == AccountOrigin.MANUAL }))
        state = success(repository.acceptProviderBatch(state.generation,
            dev.draftingroom5.retirement.provider.AccountBatch(
                dev.draftingroom5.retirement.provider.CredentialHandle(itemId, dev.draftingroom5.retirement.provider.CredentialKind.PLAID_ITEM, ProviderEnvironment.PRODUCTION, 1),
                dev.draftingroom5.retirement.provider.CredentialHandle("profile", dev.draftingroom5.retirement.provider.CredentialKind.PLAID, ProviderEnvironment.PRODUCTION, 1),
                "$itemId:1",
                listOf(dev.draftingroom5.retirement.provider.LinkedAccountData(linked.providerIdentity!!.providerAccountId,
                    linked.currentRevision.displayName, null, "401k", linked.currentBalance!!.amount, linked.currentBalance!!.asOfDate,
                    emptyList(), HoldingAvailability.UNSUPPORTED)), NOW,
            ),
            listOf(dev.draftingroom5.retirement.provider.AccountSelection(linked.providerIdentity!!.providerAccountId,
                linked.currentRevision.displayName, Owner.SPOUSE, AccountType.EMPLOYER_401K, TaxTreatment.PRE_TAX, true))),
        )
        val imported = state.accounts.first { it.origin == AccountOrigin.PLAID }
        val manualOverwrite = imported.currentBalance!!.copy(id = "manual-overwrite", sequence = 2, source = BalanceSource.MANUAL)
        assertTrue(repository.appendManualBalance(state.generation, imported.id, manualOverwrite) is RetirementResult.Invalid)
        val old = imported.currentRevision
        val edited = old.copy(id = "linked-r2", revision = 2, displayName = "Household 401(k)", includedInForecast = false,
            effectiveAt = NOW.plusSeconds(1), previousRevisionId = old.id)
        state = success(repository.reviseAccount(state.generation, imported.id, edited))
        assertEquals(listOf(old.id, edited.id), state.accounts.first { it.id == imported.id }.revisions.map { it.id })
        state = success(repository.archiveAccount(state.generation, imported.id, NOW.plusSeconds(2)))
        assertNotNull(state.accounts.first { it.id == imported.id }.archivedAt)
    }

    private fun success(result: RetirementResult<RetirementState>) = (result as RetirementResult.Success).value
}

internal class FakeRetirementDao : RetirementDao {
    private var row: RetirementStateEntity? = null
    private val balances = mutableListOf<BalanceLedgerEntity>()
    private val valuations = mutableListOf<ValuationLedgerEntity>()
    private val imports = mutableListOf<EpicImportLedgerEntity>()
    var failCommit = false
    override fun state() = row
    override fun initialize(value: RetirementStateEntity): Long { if (row != null) return -1; row = value; return 1 }
    override fun compareAndSet(expectedGeneration: Long, newGeneration: Long, document: String): Int {
        if (row?.generation != expectedGeneration) return 0
        row = RetirementStateEntity(generation = newGeneration, document = document); return 1
    }
    override fun appendBalances(values: List<BalanceLedgerEntity>) { require(values.none { next -> balances.any { it.id == next.id } }); balances += values }
    override fun appendValuations(values: List<ValuationLedgerEntity>) { require(values.none { next -> valuations.any { it.id == next.id } }); valuations += values }
    override fun appendEpicImports(values: List<EpicImportLedgerEntity>) { require(values.none { next -> imports.any { it.id == next.id } }); imports += values }
    override fun balanceCount() = balances.size
    override fun valuationCount() = valuations.size
    override fun epicImportCount() = imports.size
    override fun commit(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean {
        if (failCommit) return false
        val oldRow = row; val oldBalances = this.balances.toList(); val oldValuations = this.valuations.toList(); val oldImports = this.imports.toList()
        return try {
            appendBalances(balances); appendValuations(valuations); appendEpicImports(imports)
            compareAndSet(expectedGeneration, state.generation, RetirementCodec.encode(state)) == 1
        } catch (failure: Throwable) {
            row = oldRow; this.balances.apply { clear(); addAll(oldBalances) }; this.valuations.apply { clear(); addAll(oldValuations) }; this.imports.apply { clear(); addAll(oldImports) }; throw failure
        }
    }
}
