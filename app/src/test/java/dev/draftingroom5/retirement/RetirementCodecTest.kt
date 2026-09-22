package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.data.RetirementCodec
import dev.draftingroom5.retirement.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetirementCodecTest {
    @Test fun everyClassificationAndFreshnessEnumRoundTrips() {
        val base = retirementFixture()
        val manual = base.accounts.first()
        val ordinaryTypes = AccountType.entries - setOf(AccountType.PROPERTY, AccountType.EPIC)
        val ordinaryTaxes = TaxTreatment.entries - setOf(TaxTreatment.PROPERTY, TaxTreatment.EPIC)
        ordinaryTypes.forEachIndexed { index, type ->
            val revision = manual.currentRevision.copy(id = "type-$index", type = type, taxTreatment = ordinaryTaxes[index % ordinaryTaxes.size], owner = Owner.entries[index % Owner.entries.size])
            val state = RetirementState(accounts = listOf(manual.copy(id = "account-$index", revisions = listOf(revision))))
            assertEquals(state, RetirementCodec.decode(RetirementCodec.encode(state)))
        }
        val providers = ProviderStatus.entries.mapIndexed { index, status -> ProviderItemState("provider-$index", null, status, NOW, if (index % 2 == 0) NOW else null,
            if (status == ProviderStatus.RATE_LIMITED) NOW.plusSeconds(60) else null, ProviderError.entries.getOrNull(index), index + 1L, null) }
        val holdings = HoldingAvailability.entries.mapIndexed { index, availability -> HoldingSnapshot("holding-batch-$index", NOW, availability,
            if (availability == HoldingAvailability.COMPLETE) listOf(Holding("security-$index", "1.25", null, null, "CASH")) else emptyList()) }
        val state = base.copy(accounts = base.accounts.map { if (it.id == manual.id) it.copy(holdingSnapshots = holdings) else it }, providerItems = providers)
        assertEquals(state, RetirementCodec.decode(RetirementCodec.encode(state)))
    }

    @Test fun sanitizedManualFoundationFixtureMatchesDomainRules() {
        val path = "docs/design/retirement-workspace/parity/manual-foundations.json"
        val file = sequenceOf(File(path), File("../$path")).first { it.isFile }
        val fixture = JSONObject(file.readText())
        fixture.getJSONArray("money").let { rows -> repeat(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            assertEquals(row.getLong("cents"), Money.parse(row.getString("text")).cents)
        } }
        val property = fixture.getJSONObject("property")
        val equity = java.math.BigDecimal.valueOf(property.getLong("valueCents") - property.getLong("mortgageCents"))
            .multiply(java.math.BigDecimal.valueOf(property.getLong("ownershipBps")))
            .divide(java.math.BigDecimal.valueOf(10_000), 0, java.math.RoundingMode.HALF_UP).longValueExact()
        assertEquals(property.getLong("equityCents"), equity)
        assertFalse(fixture.toString().contains("accessToken", ignoreCase = true))
        assertFalse(fixture.toString().contains("workbookBytes", ignoreCase = true))
    }

    @Test fun exactRoundTripPreservesEveryManualFoundation() {
        val fixture = retirementFixture()
        val encoded = RetirementCodec.encode(fixture)
        val restored = RetirementCodec.decode(encoded)
        assertEquals(fixture, restored)
        assertTrue(encoded.contains("\"amountCents\":100000"))
        assertFalse(encoded.contains("1000.0"))
        assertFalse(encoded.contains("filename"))
        assertFalse(encoded.contains("workbookBytes"))
        assertFalse(encoded.contains("accessToken"))
    }

    @Test fun schemaRejectsUnknownMissingWrongAndDuplicateFields() {
        val encoded = RetirementCodec.encode(retirementFixture())
        val root = JSONObject(encoded)
        root.put("future", true)
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.decode(root.toString()) }
        val missing = JSONObject(encoded).apply { remove("checklist") }
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.decode(missing.toString()) }
        val wrong = JSONObject(encoded).apply { put("generation", 7.5) }
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.decode(wrong.toString()) }
        val duplicate = encoded.replaceFirst("\"format\":", "\"format\":\"bad\",\"format\":")
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.decode(duplicate) }
    }

    @Test fun invalidRangesAndDuplicateProviderIdentityAreRejected() {
        val fixture = retirementFixture()
        val linked = fixture.accounts.single { it.origin == AccountOrigin.PLAID }
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.encode(fixture.copy(accounts = fixture.accounts + linked.copy(id = "duplicate"))) }
        val property = fixture.properties.single()
        val invalid = property.valuations.single().copy(rangeLow = Money(30_000_000))
        assertThrows(IllegalArgumentException::class.java) { RetirementCodec.encode(fixture.copy(properties = listOf(property.copy(valuations = listOf(invalid))))) }
    }

    @Test fun moneyParityUsesHalfUpIntegerCentsAndStrictText() {
        mapOf("$1,234.56" to 123_456L, "(12.345)" to -1_235L, "0.005" to 1L, "-0.005" to -1L, "0" to 0L, " 42.1 " to 4_210L)
            .forEach { (text, cents) -> assertEquals(cents, Money.parse(text).cents) }
        assertEquals("-$12.35", Money(-1_235).format())
        listOf("1e3", "12,34.00", "--1", "($1)-", "NaN", "Infinity", "1000000000000.01").forEach { text ->
            assertThrows(text, IllegalArgumentException::class.java) { Money.parse(text) }
        }
    }

    @Test fun aggregateCountsOnlyOrdinaryBalancesEpicNetAndPropertyEquity() {
        val totals = AssetAggregator.totals(retirementFixture())
        assertEquals(7_855_000L, totals.tracked.cents)
        assertEquals(7_655_000L, totals.forecastEligible.cents)
        val property = retirementFixture().properties.single()
        assertEquals(7_500_000L, property.equity()!!.cents)
        assertEquals(-1_000_000L, property.copy(revisions = listOf(property.currentRevision.copy(
            ownershipBps = 5_000, mortgage = property.currentRevision.mortgage.copy(outstanding = Money(12_000_000)))),
            valuations = listOf(property.currentValuation!!.copy(estimate = Money(10_000_000), rangeLow = null, rangeHigh = null))).equity()!!.cents)
    }
}
