package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import java.time.Instant
import java.time.LocalDate

internal val NOW: Instant = Instant.parse("2026-09-19T19:00:00Z")
internal val TODAY: LocalDate = LocalDate.parse("2026-09-19")

internal fun retirementFixture(): RetirementState {
    val manual = Account("manual", AccountOrigin.MANUAL, null, null,
        listOf(AccountRevision("manual-r1", 1, "Roth IRA", Owner.SELF, AccountType.IRA_ROTH, TaxTreatment.ROTH, true, NOW)),
        listOf(BalanceSnapshot("balance-1", TODAY, NOW, 1, Money(100_000), Money(75_000), BalanceSource.MANUAL, "manual-batch")),
        listOf(HoldingSnapshot("holdings-1", NOW, HoldingAvailability.COMPLETE,
            listOf(Holding("security-1", "2.500", Money(40_000), Money(30_000), "US_STOCKS")))))
    val linked = Account("linked", AccountOrigin.PLAID, null, ProviderIdentity("profile", ProviderEnvironment.PRODUCTION, "item", "provider-account"),
        listOf(AccountRevision("linked-r1", 1, "Employer 401k", Owner.SPOUSE, AccountType.EMPLOYER_401K, TaxTreatment.PRE_TAX, false, NOW)),
        listOf(BalanceSnapshot("balance-2", TODAY.minusDays(1), NOW, 2, Money(200_000), null, BalanceSource.PLAID, "plaid-batch")),
        listOf(HoldingSnapshot("holdings-2", NOW, HoldingAvailability.UNSUPPORTED, emptyList())))
    val wrapper = Account("property-account", AccountOrigin.PROPERTY, null, null,
        listOf(AccountRevision("property-account-r1", 1, "Home", Owner.JOINT, AccountType.PROPERTY, TaxTreatment.PROPERTY, true, NOW)), emptyList())
    val epicWrapper = Account("epic-account", AccountOrigin.EPIC, null, null,
        listOf(AccountRevision("epic-account-r1", 1, "Epic stock", Owner.SELF, AccountType.EPIC, TaxTreatment.EPIC, true, NOW)), emptyList())
    val mortgage = MortgageTerms(Money(10_000_000), TODAY, Money(12_000_000), 625, Money(80_000), 240, 360)
    val property = Property("property", wrapper.id, null,
        listOf(PropertyRevision("property-r1", 1, "123 Synthetic Ave", "single family", Owner.JOINT, 5_000, mortgage, false, true, NOW)),
        listOf(PropertyValuationSnapshot("valuation-1", Money(25_000_000), Money(24_000_000), Money(26_000_000), 7, ValuationSource.MANUAL, TODAY, NOW, 1, "property-batch")))
    val totals = EpicTotals("14.75", "10.5", Money(60_000), "4.25", Money(40_000), Money(5_000), Money(55_000), Money(60_000), Money(100_000))
    val book = EpicWorkbook(Money(100), totals, listOf(EpicBreakdown("Class A", totals)),
        EpicProjection(LocalDate.parse("2030-01-02"), "0.12", "0.32", "Yes", Money(80_000), Money(5_000), Money(75_000), Money(10_000), Money(65_000)), emptyList(), null)
    val epic = AcceptedEpicImport("epic-1", "shareworks-2026", 1, NOW, "a".repeat(64), book, null)
    val plan = PlanSettings("plan-r1", 1, LocalDate.parse("1980-01-02"), TODAY, 50, 95, Money(8_000_000), 250, 650,
        FilingStatus.MARRIED_FILING_JOINTLY, "WI", "finance-reference-v1", 2, Money(2_400_000), "EXTENDED",
        Money(2_000_000), Money(500_000), Money(250_000), listOf(IncomeStream("pension", Money(1_200_000), 65, 95, IncomeTaxKind.ORDINARY)), HomeDisposition.SELL_AT_RETIREMENT)
    return RetirementState(7, listOf(manual, linked, wrapper, epicWrapper), listOf(property), listOf(epic), epic.id,
        listOf(ImportMetadata("SHAREWORKS", ImportStatus.ACCEPTED, epic.id, NOW, NOW, null)), listOf(plan),
        listOf(ChecklistState("ssa-account", true, NOW)),
        listOf(ProviderItemState("item-state", linked.id, ProviderStatus.READY, NOW, NOW, null, null, 3, null)))
}
