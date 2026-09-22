package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RentCastNativeProviderTest {
    private val now = Instant.parse("2026-09-20T18:00:00Z")

    @Test fun searchReturnsConfirmedMatchRangeFactsAndComparableMetadataInExactCents() {
        val f = fixture()
        val match = f.provider.find("500 Fixture Way, Madison, WI 53703")
        assertEquals("rentcast-property-72", match.providerPropertyId)
        assertEquals("500 Fixture Way, Madison, WI 53703", match.formattedAddress)
        assertEquals(Money(48_512_345), match.estimate)
        assertEquals(Money(46_000_000), match.rangeLow)
        assertEquals(Money(51_000_000), match.rangeHigh)
        assertEquals(2, match.comparableCount)
        assertEquals("Single Family · 3 beds · 2 baths · 1840 sq ft", match.facts)
        assertEquals(listOf(RentCastEndpoint.PROPERTIES, RentCastEndpoint.VALUE), f.transport.calls)
        assertTrue(f.transport.keys.all { it == "synthetic-rentcast-key" })
    }

    @Test fun acceptedRefreshAppendsAndFailureKeepsLastGoodValueWithHonestState() {
        val f = fixture(); val match = f.provider.find("500 Fixture Way, Madison, WI 53703")
        val input = PropertyInput(match.formattedAddress, match.estimate, Money(20_000_000), Money(145_000), 625, 240)
        val records = buildPropertyRecords(input, match, f.handle, now, LocalDate.parse("2026-09-20"))
        assertTrue(f.repo.addProviderProperty(0, records.first, records.second,
            ProviderItemState(records.second.id, records.first.id, ProviderStatus.READY, now, now, null, null, 1, null)) is RetirementResult.Success)

        f.transport.price = "490000.01"
        assertNull(f.provider.refresh(records.second.id))
        var state = f.repo.load(); var property = state.properties.single()
        assertEquals(listOf(Money(48_512_345), Money(49_000_001)), property.valuations.map { it.estimate })
        assertEquals(Money(29_000_001), property.equity())

        f.transport.failure = ProviderFailure.OFFLINE
        assertEquals(ProviderFailure.OFFLINE, f.provider.refresh(property.id))
        state = f.repo.load(); property = state.properties.single()
        assertEquals(2, property.valuations.size)
        assertEquals(Money(49_000_001), property.currentValuation!!.estimate)
        assertEquals(ProviderStatus.OFFLINE, state.providerItems.single().status)
        assertEquals(ProviderError.UNAVAILABLE, state.providerItems.single().error)
    }

    @Test fun manualRecordsNeverClaimAutomaticUpdatesAndUseExactEquity() {
        val input = PropertyInput("700 Manual Ave", Money(30_000_001), Money(10_000_000), Money(90_000), 500, 120)
        val records = buildPropertyRecords(input, null, null, now, LocalDate.parse("2026-09-20"))
        assertFalse(records.second.currentRevision.automaticValueEnabled)
        assertNull(records.second.currentRevision.providerPropertyId)
        assertEquals(ValuationSource.MANUAL, records.second.currentValuation!!.source)
        assertEquals(Money(20_000_001), records.second.equity())
    }

    @Test fun credentialReplacementInvalidatesOldRevisionAndWipesCallerBuffer() {
        val f = fixture()
        val key = "replacement-synthetic-key".toCharArray()
        val next = f.provider.provision(key, f.handle)
        assertTrue(key.all { it == '\u0000' })
        assertEquals(f.handle.id, next.id)
        assertEquals(f.handle.revision + 1, next.revision)
        assertThrows(ProviderException::class.java) { f.vault.use(f.handle) { fail("old credential decrypted") } }
    }

    @Test fun configuredCredentialsDoNotContaminateManualFallback() {
        val f = fixture()
        val records = buildPropertyRecords(PropertyInput("700 Manual Ave", Money(30000000), Money(0), Money(0), 0, 0),
            null, f.handle, now, LocalDate.parse("2026-09-20"))
        assertNull(records.second.currentRevision.credentialProfileId)
        assertTrue(f.repo.addManualProperty(0, records.first, records.second) is RetirementResult.Success)
    }

    @Test fun invalidAndPartialValuationsFailClosedBeforeReviewAndRefresh() {
        listOf("not-money", "1e99", "1000000000001", "-1").forEach { price ->
            val f = fixture(); val id = addProperty(f); val before = f.repo.load().properties
            f.transport.price = price
            assertEquals(ProviderFailure.INVALID_RESPONSE, f.provider.refresh(id))
            assertEquals(before, f.repo.load().properties)
            assertEquals(ProviderError.INVALID_RESPONSE, f.repo.load().providerItems.single().error)
        }
        val f = fixture()
        f.transport.mutate = { it.remove("priceRangeHigh") }
        assertEquals(ProviderFailure.INVALID_RESPONSE,
            assertThrows(ProviderException::class.java) { f.provider.find("500 Fixture Way") }.failure)
    }

    @Test fun mismatchedPropertyCannotReplaceAcceptedValue() {
        val f = fixture(); val id = addProperty(f); val before = f.repo.load().properties
        f.transport.mutate = { it.getJSONObject("subjectProperty").put("id", "another-property") }
        assertEquals(ProviderFailure.INVALID_RESPONSE, f.provider.refresh(id))
        assertEquals(before, f.repo.load().properties)
    }

    @Test fun rotatedCredentialsAndCancelledResponsesNeverPublish() {
        val f = fixture(); val id = addProperty(f); val before = f.repo.load()
        f.transport.mutate = { f.provider.provision("replacement-key".toCharArray(), f.handle) }
        assertEquals(ProviderFailure.CONFLICT, f.provider.refresh(id))
        assertEquals(before, f.repo.load())
        f.transport.mutate = {}
        var active = true
        f.transport.mutate = { active = false; throw ProviderException(ProviderFailure.OFFLINE) }
        assertEquals(ProviderFailure.CANCELLED, f.provider.refresh(id) { active })
        assertEquals(before, f.repo.load())
    }

    @Test fun duplicateRefreshKeepsOneSnapshotAndForegroundHonorsRetryWindow() {
        val f = fixture(); val id = addProperty(f)
        assertNull(f.provider.refresh(id)); assertNull(f.provider.refresh(id))
        assertEquals(1, f.repo.load().properties.single().valuations.size)
        val state = f.repo.load(); val item = state.providerItems.single()
        assertTrue(f.repo.setProviderItem(state.generation, item.copy(status = ProviderStatus.RATE_LIMITED,
            error = ProviderError.RATE_LIMITED, retryAfter = now.plusSeconds(3600))) is RetirementResult.Success)
        val calls = f.transport.calls.size
        assertEquals(ProviderFailure.RATE_LIMITED, f.provider.refresh(id))
        assertEquals(calls, f.transport.calls.size)
    }

    private fun addProperty(f: Fixture): String {
        val match = f.provider.find("500 Fixture Way, Madison, WI 53703")
        val records = buildPropertyRecords(PropertyInput(match.formattedAddress, match.estimate, Money(20000000), Money(145000), 625, 240),
            match, f.handle, now, LocalDate.parse("2026-09-20"))
        assertTrue(f.repo.addProviderProperty(0, records.first, records.second,
            ProviderItemState(records.second.id, records.first.id, ProviderStatus.READY, now, now, null, null, 1, null)) is RetirementResult.Success)
        return records.second.id
    }

    private fun fixture(): Fixture {
        val vault = ProviderCredentialVault(MemoryVaultStorage(), MemoryVaultKey())
        val handle = vault.put(CredentialKind.RENTCAST, ProviderEnvironment.PRODUCTION, JSONObject().put("api_key", "synthetic-rentcast-key"))
        val repo = RetirementRepository(PropertyTestDao())
        val transport = FixtureRentCastTransport()
        return Fixture(vault, handle, repo, transport, RentCastNativeProvider(vault, transport, repo) { now })
    }

    private data class Fixture(val vault: ProviderCredentialVault, val handle: CredentialHandle, val repo: RetirementRepository,
        val transport: FixtureRentCastTransport, val provider: RentCastNativeProvider)
}

private class FixtureRentCastTransport : RentCastTransport {
    val calls = mutableListOf<RentCastEndpoint>(); val keys = mutableListOf<String>()
    var price = "485123.45"; var failure: ProviderFailure? = null
    var mutate: (JSONObject) -> Unit = {}
    override fun get(endpoint: RentCastEndpoint, query: Map<String, String>, apiKey: String): JSONObject {
        failure?.let { throw ProviderException(it) }
        calls += endpoint; keys += apiKey
        return when (endpoint) {
            RentCastEndpoint.PROPERTIES -> JSONObject("""{"items":[{"id":"rentcast-property-72","formattedAddress":"500 Fixture Way, Madison, WI 53703","propertyType":"Single Family","bedrooms":"3","bathrooms":"2","squareFootage":"1840"}]}""")
            RentCastEndpoint.VALUE -> JSONObject("""{"price":"$price","priceRangeLow":"460000","priceRangeHigh":"510000","subjectProperty":{"id":"rentcast-property-72","formattedAddress":"500 Fixture Way, Madison, WI 53703","propertyType":"Single Family","bedrooms":"3","bathrooms":"2","squareFootage":"1840"},"comparables":[{"id":"comp-a"},{"id":"comp-b"}]}""")
        }.also { if (endpoint == RentCastEndpoint.VALUE) mutate(it) }
    }
}

private class PropertyTestDao : RetirementDao {
    private var row: RetirementStateEntity? = null
    override fun state() = row
    override fun initialize(value: RetirementStateEntity): Long { if (row == null) row = value; return 1 }
    override fun compareAndSet(expectedGeneration: Long, newGeneration: Long, document: String): Int {
        if (row?.generation != expectedGeneration) return 0
        row = RetirementStateEntity(generation = newGeneration, document = document); return 1
    }
    override fun appendBalances(values: List<BalanceLedgerEntity>) = Unit
    override fun appendValuations(values: List<ValuationLedgerEntity>) = Unit
    override fun appendEpicImports(values: List<EpicImportLedgerEntity>) = Unit
    override fun balanceCount() = 0
    override fun valuationCount() = 0
    override fun epicImportCount() = 0
    override fun commit(expectedGeneration: Long, state: RetirementState, balances: List<BalanceLedgerEntity>, valuations: List<ValuationLedgerEntity>, imports: List<EpicImportLedgerEntity>) =
        compareAndSet(expectedGeneration, state.generation, RetirementCodec.encode(state)) == 1
}
