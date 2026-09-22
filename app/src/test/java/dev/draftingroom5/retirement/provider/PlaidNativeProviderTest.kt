package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import javax.crypto.KeyGenerator

class PlaidNativeProviderTest {
    @Test fun liabilitiesCannotBeClassifiedAsPositiveRetirementAssets() {
        val f = Fixture(); val item = f.link()
        f.transport.accountType = "loan"
        assertEquals(ProviderFailure.UNSUPPORTED,
            assertThrows(ProviderException::class.java) { f.provider.fetchSnapshot(item) }.failure)
        assertTrue(f.repo.load().accounts.isEmpty())
    }
    @Test fun linkExchangeRequiresReviewAndPersistsNoPlaintext() {
        val f = Fixture(); val id = f.link()
        assertTrue(f.repo.load().accounts.isEmpty())
        assertEquals(listOf(id), f.provider.pendingItems())
        val batch = f.provider.fetchSnapshot(id)
        assertThrows(ProviderException::class.java) { f.provider.accept(batch, 0, null) }
        val state = f.accept(batch)
        assertEquals(2, state.accounts.size)
        assertEquals(1235L, state.accounts.first().currentBalance!!.amount.cents)
        val disk = String(f.storage.bytes!!)
        listOf("synthetic-client", "synthetic-secret", "synthetic-access", "synthetic-remote").forEach { assertFalse(disk.contains(it)); assertFalse(f.dao.state()!!.document.contains(it)) }
        assertEquals(1, f.transport.exchanges)
    }
    @Test fun repeatedSyncDoesNotDuplicateAccountsHoldingsOrSameDayBalances() {
        val f = Fixture(); val id = f.link(); val first = f.accept(f.provider.fetchSnapshot(id))
        repeat(4) { assertNull(f.provider.refresh(id)) }
        val state = f.repo.load()
        assertEquals(2, state.accounts.size)
        assertEquals(2, f.dao.balanceCount())
        assertEquals(first.accounts.map { it.holdingSnapshots }, state.accounts.map { it.holdingSnapshots })
        assertTrue(state.providerItems.single().revision > first.providerItems.single().revision)
    }
    @Test fun changedSameDayBalanceAppendsAndLocalClassificationsSurvive() {
        val f = Fixture(); val id = f.link(); val first = f.accept(f.provider.fetchSnapshot(id))
        val a = first.accounts.first(); val revision = a.currentRevision.copy(id = "local-revision", revision = 2, displayName = "My chosen name", previousRevisionId = a.currentRevision.id)
        f.repo.reviseAccount(first.generation, a.id, revision)
        f.transport.balance = "22.50"
        assertNull(f.provider.refresh(id)); assertNull(f.provider.refresh(id))
        assertEquals(2, f.repo.load().accounts.first().balances.size)
        assertEquals("My chosen name", f.repo.load().accounts.first().currentRevision.displayName)
    }
    @Test fun duplicateMasksAreSeparateIdentities() {
        val f = Fixture(); val batch = f.provider.fetchSnapshot(f.link())
        assertEquals(1, batch.accounts.map { it.mask }.distinct().size)
        assertEquals(2, f.accept(batch).accounts.map { it.providerIdentity }.distinct().size)
    }
    @Test fun cancellationAndAmbiguousExchangeNeverReplay() {
        val f = Fixture(); val attempt = f.provider.beginLink(f.profile.id)
        f.provider.cancelLink(attempt.id)
        assertThrows(ProviderException::class.java) { f.provider.completeLink(attempt.id, "synthetic-public".toCharArray()) }
        assertEquals(0, f.transport.exchanges)
        val another = f.provider.beginLink(f.profile.id)
        f.transport.exchangeFailure = true
        assertThrows(ProviderException::class.java) { f.provider.completeLink(another.id, "synthetic-public".toCharArray()) }
        assertThrows(ProviderException::class.java) { f.provider.completeLink(another.id, "synthetic-public".toCharArray()) }
        assertEquals(1, f.transport.exchanges)
        assertTrue(f.provider.pendingItems().isEmpty())
    }
    @Test fun pendingExchangeSurvivesProviderRecreationWithoutReexchange() {
        val f = Fixture(); val id = f.link()
        val recreated = PlaidNativeProvider(ProviderCredentialVault(f.storage, f.key), f.transport, f.repo) { NOW }
        assertEquals(listOf(id), recreated.pendingItems())
        val batch = recreated.fetchSnapshot(id)
        assertTrue(recreated.accept(batch, 0, selections(batch)) is RetirementResult.Success)
        assertEquals(1, f.transport.exchanges)
    }
    @Test fun partialAccountsDoNotRemoveLastGoodData() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.partialAccounts = true
        assertEquals(ProviderFailure.INVALID_RESPONSE, f.provider.refresh(id))
        assertEquals(before.accounts, f.repo.load().accounts)
    }
    @Test fun truncatedHoldingsOrUnknownCurrencyRejectEntireSync() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.partialHoldings = true
        assertEquals(ProviderFailure.INVALID_RESPONSE, f.provider.refresh(id))
        assertEquals(before.accounts, f.repo.load().accounts)
        assertEquals(before.providerItems.single().lastAcceptedAt, f.repo.load().providerItems.single().lastAcceptedAt)
        f.transport.partialHoldings = false; f.transport.currency = "EUR"
        assertEquals(ProviderFailure.INVALID_RESPONSE, f.provider.refresh(id))
        assertEquals(before.accounts, f.repo.load().accounts)
    }
    @Test fun offlineAndStaleTokenKeepAccountValuesAndSetScopedAttention() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.failure = ProviderFailure.OFFLINE
        assertEquals(ProviderFailure.OFFLINE, f.provider.refresh(id))
        assertEquals(ProviderStatus.OFFLINE, f.repo.load().providerItems.single().status)
        f.transport.failure = ProviderFailure.NEEDS_CREDENTIALS
        assertEquals(ProviderFailure.NEEDS_CREDENTIALS, f.provider.refresh(id))
        assertEquals(ProviderStatus.ATTENTION, f.repo.load().providerItems.single().status)
        assertEquals(before.accounts, f.repo.load().accounts)
    }
    @Test fun unsupportedHoldingsAreExplicitAndManualStillWorks() {
        val f = Fixture(); f.transport.unsupported = true
        val batch = f.provider.fetchSnapshot(f.link()); val state = f.accept(batch)
        assertTrue(state.accounts.all { it.holdingSnapshots.single().availability == HoldingAvailability.UNSUPPORTED })
        val a = manualAccount()
        assertTrue(f.repo.addManualAccount(state.generation, a) is RetirementResult.Success)
        assertEquals(3, f.repo.load().accounts.size)
    }
    @Test fun manualMatchingIsExplicitAndPreservesHistory() {
        val f = Fixture(); val manual = manualAccount().copy(holdingSnapshots = listOf(HoldingSnapshot("manual-holdings", NOW,
            HoldingAvailability.COMPLETE, listOf(Holding("manual-security", "1", Money(100), null, "equity")))))
        f.repo.addManualAccount(0, manual)
        val batch = f.provider.fetchSnapshot(f.link())
        val chosen = selections(batch).take(1).map { it.copy(replaceManualId = manual.id) }
        assertTrue(f.provider.accept(batch, 1, chosen) is RetirementResult.Success)
        val a = f.repo.load().accounts.single()
        assertEquals(manual.id, a.id); assertEquals(AccountOrigin.PLAID, a.origin)
        assertEquals(manual.balances.first(), a.balances.first())
        assertEquals(2, a.balances.size)
        assertEquals(manual.holdingSnapshots.single(), a.holdingSnapshots.first())
        assertEquals(2, a.holdingSnapshots.size)
    }
    @Test fun credentialsReplacedDuringFetchPreventCommit() {
        val f = Fixture(); val batch = f.provider.fetchSnapshot(f.link())
        f.provider.saveCredentials("new-synthetic-client".toCharArray(), "new-synthetic-secret".toCharArray(), ProviderEnvironment.SANDBOX, f.profile)
        assertThrows(ProviderException::class.java) { f.accept(batch) }
        assertTrue(f.repo.load().accounts.isEmpty())
    }
    @Test fun laterFetchAndReconnectInvalidateOlderResultsOnlyForTheirItem() {
        val f = Fixture(); val id = f.link(); f.accept(f.provider.fetchSnapshot(id))
        val stale = f.provider.fetchSnapshot(id)
        val attempt = f.provider.beginLink(f.profile.id, id)
        assertEquals(id, f.provider.completeLink(attempt.id, null))
        assertThrows(ProviderException::class.java) { f.provider.accept(stale, f.repo.load().generation, null) }
        assertNull(f.provider.refresh(id))
        assertEquals(1, f.transport.exchanges)
    }
    @Test fun failedAtomicCommitPublishesNothingAndCanRetryReview() {
        val f = Fixture(); val batch = f.provider.fetchSnapshot(f.link()); val before = f.repo.load()
        for (stage in 1..3) {
            f.dao.failStage = stage
            assertThrows(IllegalStateException::class.java) { f.accept(batch) }
            assertEquals(before, f.repo.load()); assertEquals(0, f.dao.balanceCount())
        }
        f.dao.failStage = 0
        assertEquals(2, f.accept(batch).accounts.size)
    }
    @Test fun offlineDisconnectDeletesTokenButDoesNotClaimRemoteRevocation() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.failure = ProviderFailure.OFFLINE
        assertFalse(f.provider.disconnect(id))
        assertTrue(f.provider.items().isEmpty())
        assertEquals(before.accounts, f.repo.load().accounts)
        assertNotNull(f.repo.load().providerItems.single().revokedAt)
    }
    @Test fun diagnosticsCannotContainProviderValuesAndUnknownSubtypeRequiresReview() {
        val d = ProviderDiagnostic(ProviderOperation.SYNC, ProviderFailure.OFFLINE, 1)
        assertFalse(d.toString().contains("synthetic"))
        assertEquals("INVALID_RESPONSE", ProviderException(ProviderFailure.INVALID_RESPONSE).message)
        assertNull(AccountClassification.suggest("unknown"))
        assertEquals(AccountType.EMPLOYER_403B, AccountClassification.suggest("403b")!!.first)
        assertFalse(AccountClassification.valid(AccountType.HSA, TaxTreatment.TAXABLE))
    }
    @Test fun decimalJsonNeverUsesDoubleOrLosesHalfCent() {
        val json = parseProviderJson("""{"amount":999999999999.995,"zero":0,"text":"abc123"}""")
        assertEquals("999999999999.995", json.get("amount")); assertEquals("0", json.get("zero")); assertEquals("abc123", json.getString("text"))
    }
    @Test fun reconnectAndFailureNeverMutateAnotherItem() {
        val f = Fixture(); val firstId = f.link(); f.accept(f.provider.fetchSnapshot(firstId))
        val first = f.repo.load()
        f.transport.remoteId = "synthetic-second-item"
        val secondId = f.link(); f.accept(f.provider.fetchSnapshot(secondId))
        f.transport.failure = ProviderFailure.NEEDS_CREDENTIALS
        assertEquals(ProviderFailure.NEEDS_CREDENTIALS, f.provider.refresh(secondId))
        f.transport.failure = null
        val attempt = f.provider.beginLink(f.profile.id, secondId)
        f.provider.completeLink(attempt.id, null)
        assertNull(f.provider.refresh(secondId))
        val result = f.repo.load()
        assertEquals(first.accounts, result.accounts.filter { it.providerIdentity?.itemId == firstId })
        assertEquals(first.providerItems.single(), result.providerItems.single { it.id == firstId })
    }
    @Test fun cancelledWorkerCannotPublishFetchedBatch() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.balance = "50.00"
        assertEquals(ProviderFailure.CANCELLED, f.provider.refresh(id) { false })
        assertEquals(before, f.repo.load())
    }
    @Test fun rateLimitPreservesFreshnessAndStopsRepeatedRequests() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.failure = ProviderFailure.RATE_LIMITED
        assertEquals(ProviderFailure.RATE_LIMITED, f.provider.refresh(id))
        f.transport.failure = null
        assertEquals(ProviderFailure.RATE_LIMITED, f.provider.refresh(id))
        assertEquals(before.accounts, f.repo.load().accounts)
        assertEquals(before.providerItems.single().lastAcceptedAt, f.repo.load().providerItems.single().lastAcceptedAt)
        assertEquals(NOW.plusSeconds(86_400), f.repo.load().providerItems.single().retryAfter)
    }
    @Test fun explicitResetRetainsSnapshotsAndAllowsMatchingDisconnectedAccount() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.vault.resetAll(beforeErase = { f.repo.revokeProviderCredentials(before.generation, NOW) }, deleteKey = { f.key.secret = MemoryVaultKey().secret })
        assertEquals(before.accounts, f.repo.load().accounts)
        val newProfile = f.provider.saveCredentials("synthetic-new-client".toCharArray(), "synthetic-new-secret".toCharArray(), ProviderEnvironment.SANDBOX, null)
        val attempt = f.provider.beginLink(newProfile.id)
        val newId = f.provider.completeLink(attempt.id, "synthetic-public".toCharArray())
        val batch = f.provider.fetchSnapshot(newId)
        val selected = selections(batch).map { choice -> choice.copy(replaceManualId = before.accounts.single { it.providerIdentity!!.providerAccountId == choice.providerAccountId }.id) }
        assertTrue(f.provider.accept(batch, f.repo.load().generation, selected) is RetirementResult.Success)
        assertEquals(2, f.repo.load().accounts.size)
        assertEquals(before.accounts.map { it.id }, f.repo.load().accounts.map { it.id })
    }
    @Test fun acceptedOperationReplayIsNoWriteEvenAfterLaterSyncAndRecreation() {
        val f = Fixture(); val id = f.link(); val batch = f.provider.fetchSnapshot(id); f.accept(batch)
        assertNull(f.provider.refresh(id))
        val before = f.repo.load()
        val recreated = PlaidNativeProvider(ProviderCredentialVault(f.storage, f.key), f.transport, RetirementRepository(f.dao)) { NOW }
        val replay = recreated.accept(batch, 0, selections(batch))
        assertEquals(before, (replay as RetirementResult.Success).value)
        assertEquals(before, f.repo.load()); assertEquals(2, f.dao.balanceCount())
        assertTrue(batch.operationId in before.acceptedProviderOperations)
    }
    @Test fun failureAfterFinancialCommitRecoversPendingActivationWithoutDuplicates() {
        val f = Fixture(); val id = f.link(); val batch = f.provider.fetchSnapshot(id)
        f.storage.fail = true
        assertThrows(ProviderException::class.java) { f.accept(batch) }
        assertEquals(2, f.repo.load().accounts.size)
        f.storage.fail = false
        val recreated = PlaidNativeProvider(ProviderCredentialVault(f.storage, f.key), f.transport, f.repo) { NOW }
        assertNull(recreated.refresh(id))
        assertEquals(2, f.dao.balanceCount()); assertTrue(recreated.pendingItems().isEmpty())
    }
    @Test fun duplicateItemCannotCreateASecondConnection() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        assertThrows(ProviderException::class.java) { f.link() }
        assertEquals(before, f.repo.load()); assertEquals(1, f.provider.items().size)
    }
    @Test fun newConnectionCannotTakeOverAnActiveLinkedAccount() {
        val f = Fixture(); val firstId = f.link(); f.accept(f.provider.fetchSnapshot(firstId))
        val before = f.repo.load(); f.transport.remoteId = "synthetic-new-item"
        val second = f.provider.fetchSnapshot(f.link())
        val selected = selections(second).take(1).map { it.copy(replaceManualId = before.accounts.first().id) }
        assertTrue(f.provider.accept(second, before.generation, selected) is RetirementResult.Invalid)
        assertEquals(before, f.repo.load())
    }
    @Test fun authenticatedProfileRemovalDropsAllItsTokensAndRetainsHistory() {
        val f = Fixture(); f.accept(f.provider.fetchSnapshot(f.link()))
        f.transport.remoteId = "synthetic-second-item"; f.accept(f.provider.fetchSnapshot(f.link()))
        val before = f.repo.load()
        assertTrue(f.provider.removeCredentials(f.profile))
        assertTrue(f.provider.profiles().isEmpty()); assertTrue(f.provider.items().isEmpty())
        assertEquals(before.accounts, f.repo.load().accounts)
        assertTrue(f.repo.load().providerItems.all { it.revokedAt != null })
    }
    @Test fun cancelledWorkerDoesNotPublishAnErrorEither() {
        val f = Fixture(); val id = f.link(); val before = f.accept(f.provider.fetchSnapshot(id))
        f.transport.failure = ProviderFailure.OFFLINE
        assertEquals(ProviderFailure.CANCELLED, f.provider.refresh(id) { false })
        assertEquals(before, f.repo.load())
    }
}

private val NOW = Instant.parse("2026-09-20T01:00:00Z")
private fun selections(batch: AccountBatch) = batch.accounts.map { AccountSelection(it.providerAccountId, it.name, Owner.SELF, AccountType.BROKERAGE, TaxTreatment.TAXABLE, true) }
private fun manualAccount() = Account("manual", AccountOrigin.MANUAL, null, null,
    listOf(AccountRevision("manual-r", 1, "Synthetic investment", Owner.SELF, AccountType.BROKERAGE, TaxTreatment.TAXABLE, true, NOW)),
    listOf(BalanceSnapshot("manual-b", NOW.atZone(java.time.ZoneOffset.UTC).toLocalDate(), NOW, 1, Money(100), null, BalanceSource.MANUAL, "manual-batch")))
private class Fixture {
    val storage = MemoryVaultStorage(); val key = MemoryVaultKey(); val vault = ProviderCredentialVault(storage, key)
    val dao = AtomicTestDao(); val repo = RetirementRepository(dao); val transport = FakePlaidTransport()
    val provider = PlaidNativeProvider(vault, transport, repo) { NOW }
    val profile = provider.saveCredentials("synthetic-client".toCharArray(), "synthetic-secret".toCharArray(), ProviderEnvironment.SANDBOX, null)
    fun link(): String { val a = provider.beginLink(profile.id); return provider.completeLink(a.id, "synthetic-public".toCharArray()) }
    fun accept(batch: AccountBatch) = (provider.accept(batch, repo.load().generation, selections(batch)) as RetirementResult.Success).value
}
internal class MemoryVaultStorage : VaultStorage {
    var bytes: ByteArray? = null; var fail = false
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) { if (fail) throw IllegalStateException("synthetic-write-failure"); this.bytes = bytes.copyOf() }
}
internal class MemoryVaultKey : VaultKey {
    var secret = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    var unlocked = true
    override fun key(create: Boolean) = secret
    override fun available() = unlocked
}
private class FakePlaidTransport : ProviderTransport {
    var accountType = "investment"
    var exchanges = 0; var exchangeFailure = false; var failure: ProviderFailure? = null
    var balance = "12.345"; var partialAccounts = false; var partialHoldings = false; var currency = "USD"; var unsupported = false
    var remoteId = "synthetic-remote"
    override fun post(environment: ProviderEnvironment, endpoint: PlaidEndpoint, body: JSONObject): JSONObject {
        failure?.let { throw ProviderException(it) }
        return when (endpoint) {
            PlaidEndpoint.LINK -> JSONObject().put("link_token", "synthetic-link").put("expiration", NOW.plusSeconds(600).toString())
            PlaidEndpoint.EXCHANGE -> { exchanges++; if (exchangeFailure) throw ProviderException(ProviderFailure.OFFLINE)
                JSONObject().put("item_id", remoteId).put("access_token", "synthetic-access-$remoteId") }
            PlaidEndpoint.ACCOUNTS -> parseProviderJson("""{"item":{"item_id":"$remoteId"},"accounts":[${account("a", balance)}${if (partialAccounts) "" else "," + account("b", "0") }]}""")
            PlaidEndpoint.HOLDINGS -> {
                if (unsupported) throw ProviderException(ProviderFailure.UNSUPPORTED)
                parseProviderJson("""{"item":{"item_id":"$remoteId"},"accounts":[{"account_id":"a"},{"account_id":"b"}],"securities":[{"security_id":"s","type":"equity","iso_currency_code":"USD"}],${if (partialHoldings) "" else "\"holdings\":[{\"account_id\":\"a\",\"security_id\":\"s\",\"quantity\":1.25,\"institution_price\":9.876,\"cost_basis\":null,\"iso_currency_code\":\"USD\"}],"}"request_id":"synthetic-request"}""")
            }
            PlaidEndpoint.REMOVE -> JSONObject()
        }
    }
    private fun account(id: String, value: String) = """{"account_id":"$id","type":"$accountType","name":"Synthetic investment","mask":"1234","subtype":"brokerage","balances":{"current":$value,"iso_currency_code":"$currency"}}"""
}

/** Transaction fake stages all writes; failure injection represents failure before commit publication. */
private class AtomicTestDao : RetirementDao {
    private var row: RetirementStateEntity? = null; private var balances = listOf<BalanceLedgerEntity>(); var failStage = 0
    override fun state() = row
    override fun initialize(value: RetirementStateEntity): Long { row = value; return 1 }
    override fun compareAndSet(expectedGeneration: Long, newGeneration: Long, document: String): Int = error("Use commit")
    override fun appendBalances(values: List<BalanceLedgerEntity>) = error("Use commit")
    override fun appendValuations(values: List<ValuationLedgerEntity>) = error("Use commit")
    override fun appendEpicImports(values: List<EpicImportLedgerEntity>) = error("Use commit")
    override fun balanceCount() = balances.size
    override fun valuationCount() = 0
    override fun epicImportCount() = 0
    override fun commit(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>): Boolean {
        check(failStage != 1)
        val stagedBalances = this.balances + balances
        check(failStage != 2)
        val stagedRow = RetirementStateEntity(generation = state.generation, document = RetirementCodec.encode(state))
        check(failStage != 3)
        if (row?.generation != expectedGeneration) return false
        this.balances = stagedBalances; row = stagedRow; return true
    }
}
