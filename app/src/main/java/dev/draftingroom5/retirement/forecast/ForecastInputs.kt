package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.domain.*
import java.time.LocalDate
import java.time.Period
import java.util.Collections
import kotlin.math.abs

internal fun <T> frozen(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** Value type prevents caller-owned mutable allocation arrays from entering a generation. */
data class Allocation(val us: Double = 0.0, val international: Double = 0.0, val bonds: Double = 0.0,
    val reits: Double = 0.0, val cash: Double = 0.0, val crypto: Double = 0.0) {
    init { val w = array(); require(w.all { it.isFinite() && it >= 0 } && abs(w.sum() - 1.0) < 1e-9) }
    internal fun array() = doubleArrayOf(us, international, bonds, reits, cash, crypto)
    companion object {
        fun defaultFor(type: AccountType): Allocation = when (type) {
            AccountType.CASH, AccountType.CD -> Allocation(cash = 1.0)
            AccountType.CRYPTO -> Allocation(crypto = 1.0)
            AccountType.HSA -> Allocation(.70, .15, .10, .05)
            AccountType.BROKERAGE -> Allocation(.65, .20, .10, .05)
            else -> Allocation(.60, .15, .20, .05)
        }
    }
}
data class ForecastAccount(val bucket: Bucket, val balance: Money, val basis: Money, val allocation: Allocation) {
    init { require(balance.cents in 0..MAX_ASSET_CENTS && basis.cents in 0..MAX_ASSET_CENTS) }
}
data class ForecastProperty(val value: Money, val mortgage: Money, val rateBps: Int, val payment: Money,
    val months: Int, val ownershipBps: Int, val realAppreciationBps: Int = 0,
    val low: Money? = null, val high: Money? = null) {
    init {
        require(value.cents in 0..MAX_ASSET_CENTS && mortgage.cents in 0..MAX_ASSET_CENTS && payment.cents in 0..MAX_ASSET_CENTS)
        require(rateBps in 0..2500 && months in 0..600 && ownershipBps in 0..10000 && realAppreciationBps in -5000..10000)
        require((low == null) == (high == null))
        if (low != null) require(low.cents >= 0 && low.cents <= value.cents && high!!.cents >= value.cents && high.cents <= MAX_ASSET_CENTS)
    }
}
data class ForecastEpicYear(val year: Int, val pretax: Money, val afterTax: Money) {
    init { require(year in 1900..2500 && pretax.cents in 0..MAX_ASSET_CENTS && afterTax.cents in 0..pretax.cents) }
}
enum class IncomeKind { ORDINARY, SOCIAL_SECURITY, TAX_FREE }
data class ForecastIncome(val annual: Money, val startAge: Int, val endAge: Int, val kind: IncomeKind) {
    init { require(annual.cents in 0..MAX_ASSET_CENTS && startAge in 0..130 && endAge in startAge..200) }
}
data class ForecastContributions(val traditional: Money = Money(0), val roth: Money = Money(0),
    val taxable: Money = Money(0), val hsa: Money = Money(0)) {
    init { require(listOf(traditional, roth, taxable, hsa).all { it.cents in 0..MAX_ASSET_CENTS }) }
    internal fun array() = doubleArrayOf(dollars(traditional), dollars(roth), dollars(hsa), dollars(taxable), 0.0)
}

/** One immutable, detached generation. Inputs are cents; computation alone uses real Double dollars. */
class ForecastInput(
    val generation: Long, val planRevision: Long, val referenceDate: LocalDate,
    val currentAge: Int, val retirementAge: Int, val endAge: Int,
    val spending: Money, accounts: List<ForecastAccount> = emptyList(),
    val contributions: ForecastContributions = ForecastContributions(), incomes: List<ForecastIncome> = emptyList(),
    properties: List<ForecastProperty> = emptyList(), epicYears: List<ForecastEpicYear> = emptyList(),
    val epicVolatility: Double = 0.0, val inflationBps: Int = 0,
    val equityMeanShiftBps: Int = 0, val volatilityScaleBps: Int = 10000,
    val filing: FilingStatus = FilingStatus.SINGLE, val stateCode: String = "WI",
    val acaHousehold: Int = 1, val acaPremium: Money = Money(0), val acaExtended: Boolean = false,
    val sellHome: Boolean = true, rules: List<WithdrawalRule> = WithdrawalPolicy.defaultRules(),
    warnings: List<String> = emptyList(), val policyId: String = ReferenceTaxPolicy.ID,
) {
    val accounts = frozen(accounts)
    val incomes = frozen(incomes)
    val properties = frozen(properties)
    val epicYears = frozen(epicYears)
    val rules = frozen(rules)
    val warnings = frozen(warnings)
    val years get() = endAge - currentAge
    init {
        require(generation >= 0 && planRevision >= 0 && currentAge in 0..129 && endAge in currentAge + 1..130)
        require(retirementAge in 0..200 && referenceDate.year in 1900..2300)
        require(spending.cents in 0..MAX_ASSET_CENTS && acaPremium.cents in 0..MAX_ASSET_CENTS)
        require(inflationBps in -1000..10000 && equityMeanShiftBps in -10000..30000 && volatilityScaleBps in 0..30000)
        require(epicVolatility.isFinite() && epicVolatility >= 0 && acaHousehold in 1..20)
        require(policyId == ReferenceTaxPolicy.ID && stateCode in ReferenceTaxPolicy.supportedStates)
        require(accounts.size <= 1000 && properties.size <= 100 && incomes.size <= 100 && rules.size <= 20)
        require(epicYears.map { it.year }.distinct().size == epicYears.size)
        // No invented Epic extensions: rows must cover each boundary through sale, or the full horizon.
        if (epicYears.isNotEmpty()) {
            require(retirementAge >= currentAge) { "Epic workbook requires an explicit post-retirement disposition." }
            val last = minOf(years, retirementAge - currentAge)
            require((0..last).all { offset -> epicYears.any { it.year == referenceDate.year + offset } }) { "Epic workbook projection years are missing." }
        }
    }
    override fun toString() = "ForecastInput(generation=$generation, [private])"
}

sealed interface ForecastCapture {
    data class Ready(val input: ForecastInput) : ForecastCapture
    data class NeedsData(val reason: MissingForecastData) : ForecastCapture
}
enum class MissingForecastData { PLAN, UNSUPPORTED_POLICY, BALANCE, INVALID_INPUT, EPIC_PROJECTION }

object ForecastInputs {
    /** Repository.load reads the entire committed document in one snapshot; never load individual entities. */
    fun capture(state: RetirementState, today: LocalDate = LocalDate.now()): ForecastCapture {
        val plan = state.planSettings.maxByOrNull { it.revision } ?: return ForecastCapture.NeedsData(MissingForecastData.PLAN)
        if (plan.taxPolicyId != ReferenceTaxPolicy.ID || plan.stateCode !in ReferenceTaxPolicy.supportedStates ||
            plan.acaRegime !in setOf("CLIFF", "EXTENDED")) return ForecastCapture.NeedsData(MissingForecastData.UNSUPPORTED_POLICY)
        val notes = mutableListOf("Frozen reference tax/ACA approximations; planning software, not advice.")
        return try {
            val accounts = state.accounts.filter { it.archivedAt == null && it.currentRevision.includedInForecast &&
                it.origin in setOf(AccountOrigin.MANUAL, AccountOrigin.PLAID) }.sortedBy { it.id }.map { account ->
                val balance = account.currentBalance ?: return ForecastCapture.NeedsData(MissingForecastData.BALANCE)
                val revision = account.currentRevision
                val bucket = when (revision.taxTreatment) {
                    TaxTreatment.PRE_TAX -> Bucket.TRADITIONAL
                    TaxTreatment.ROTH -> Bucket.ROTH
                    TaxTreatment.TAX_FREE -> if (revision.type == AccountType.HSA) Bucket.HSA else return ForecastCapture.NeedsData(MissingForecastData.INVALID_INPUT)
                    TaxTreatment.TAXABLE -> Bucket.TAXABLE
                    else -> return ForecastCapture.NeedsData(MissingForecastData.INVALID_INPUT)
                }
                val basis = balance.basis ?: if (revision.type == AccountType.CASH) balance.amount else {
                    if (bucket == Bucket.TAXABLE) notes += "Missing taxable basis uses the reference 70% basis assumption."
                    forecastMoney(dollars(balance.amount) * .70)
                }
                // Providers may report unknown/security-level classifications. No guessed holding allocation.
                notes += "Account-type reference allocations are used; holdings are not extra balances."
                ForecastAccount(bucket, balance.amount, basis, Allocation.defaultFor(revision.type))
            }
            val properties = state.properties.filter { it.archivedAt == null && it.currentRevision.includedInForecast }.sortedBy { it.id }.map {
                val revision = it.currentRevision; val mortgage = revision.mortgage
                val value = it.currentValuation ?: return ForecastCapture.NeedsData(MissingForecastData.BALANCE)
                ForecastProperty(value.estimate, mortgage.outstanding, mortgage.annualRateBps, mortgage.payment,
                    mortgage.remainingMonths, revision.ownershipBps, plan.homeRealAppreciationBps, value.rangeLow, value.rangeHigh)
            }
            val age = Period.between(plan.birthDate, today).years
            val book = state.epicImports.singleOrNull { it.id == state.activeEpicImportId }?.workbook
            val epic = book?.years.orEmpty().map { ForecastEpicYear(it.year, it.netPretax, it.afterTax) }
            if (book != null && (epic.isEmpty() || plan.retirementAge < age ||
                (0..minOf(plan.endAge - age, plan.retirementAge - age)).any { offset -> epic.none { it.year == today.year + offset } }))
                return ForecastCapture.NeedsData(MissingForecastData.EPIC_PROJECTION)
            if (book != null && book.historicalVolatilityPct == null) notes += "Epic historical uncertainty is unavailable; workbook schedule is deterministic."
            ForecastCapture.Ready(ForecastInput(state.generation, plan.revision, today, age,
                plan.retirementAge, plan.endAge, plan.annualSpending, accounts,
                ForecastContributions(plan.annualPreTaxContribution, plan.annualRothContribution, plan.annualTaxableContribution, plan.annualHsaContribution),
                plan.incomeStreams.map { ForecastIncome(it.annualAmount, it.startAge, it.endAge, when (it.taxKind) {
                    IncomeTaxKind.ORDINARY -> IncomeKind.ORDINARY
                    IncomeTaxKind.SOCIAL_SECURITY -> IncomeKind.SOCIAL_SECURITY
                    IncomeTaxKind.TAX_FREE -> IncomeKind.TAX_FREE
                }) }, properties, epic, (book?.historicalVolatilityPct?.toDouble() ?: 0.0) / 100,
                plan.inflationBps, plan.expectedReturnBps - 650, plan.volatilityScaleBps, plan.filingStatus,
                plan.stateCode, plan.acaHouseholdSize, plan.acaAnnualPremium, plan.acaRegime == "EXTENDED",
                plan.homeDisposition == HomeDisposition.SELL_AT_RETIREMENT, WithdrawalPolicy.defaultRules(plan.annualMedicalSpending), notes.distinct()))
        } catch (_: IllegalArgumentException) { ForecastCapture.NeedsData(MissingForecastData.INVALID_INPUT) }
    }
}
