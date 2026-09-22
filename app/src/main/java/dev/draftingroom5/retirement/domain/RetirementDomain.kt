package dev.draftingroom5.retirement.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val MAX_ASSET_CENTS = 100_000_000_000_000L

@JvmInline
value class Money(val cents: Long) {
    operator fun plus(other: Money) = Money(Math.addExact(cents, other.cents))
    operator fun minus(other: Money) = Money(Math.subtractExact(cents, other.cents))

    fun format(): String {
        val negative = cents < 0
        val magnitude = if (negative) BigDecimal.valueOf(cents).negate() else BigDecimal.valueOf(cents)
        val dollars = magnitude.divideToIntegralValue(BigDecimal.valueOf(100)).toPlainString()
        val fraction = magnitude.remainder(BigDecimal.valueOf(100)).toInt().toString().padStart(2, '0')
        return (if (negative) "-" else "") + "\$" + dollars.reversed().chunked(3).joinToString(",").reversed() + "." + fraction
    }

    companion object {
        private val SYNTAX = Regex("^(?:-?\\$?(?:0|[1-9]\\d*|[1-9]\\d{0,2}(?:,\\d{3})+)(?:\\.\\d*)?|\\(\\$?(?:0|[1-9]\\d*|[1-9]\\d{0,2}(?:,\\d{3})+)(?:\\.\\d*)?\\))$")
        fun parse(text: String): Money {
            val trimmed = text.trim()
            require(SYNTAX.matches(trimmed)) { "Invalid money text." }
            val parenthesized = trimmed.startsWith('(')
            val normalized = trimmed.removePrefix("(").removeSuffix(")").removePrefix("-").removePrefix("$").replace(",", "")
            val cents = BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            val signed = if (parenthesized || trimmed.startsWith('-')) Math.negateExact(cents) else cents
            require(signed in -MAX_ASSET_CENTS..MAX_ASSET_CENTS) { "Money is outside the supported range." }
            return Money(signed)
        }
    }
}

enum class AccountOrigin { MANUAL, PLAID, PROPERTY, EPIC }
enum class AccountType { EMPLOYER_401K, EMPLOYER_403B, EMPLOYER_457, PROFIT_SHARING, IRA_TRADITIONAL, IRA_ROTH, HSA, BROKERAGE, CASH, CD, CRYPTO, PROPERTY, EPIC }
enum class TaxTreatment { PRE_TAX, ROTH, TAXABLE, TAX_FREE, PROPERTY, EPIC }
enum class Owner { SELF, SPOUSE, JOINT }
enum class BalanceSource { MANUAL, PLAID }
enum class ValuationSource { MANUAL, RENTCAST }
enum class HoldingAvailability { COMPLETE, UNSUPPORTED, PENDING }
enum class ProviderEnvironment { SANDBOX, DEVELOPMENT, PRODUCTION }
enum class ProviderStatus { READY, ATTENTION, OFFLINE, RATE_LIMITED, UNSUPPORTED }
enum class ProviderError { AUTHENTICATION_REQUIRED, RATE_LIMITED, INVALID_RESPONSE, CONFLICT, UNAVAILABLE }
enum class FilingStatus { SINGLE, MARRIED_FILING_JOINTLY, MARRIED_FILING_SEPARATELY, HEAD_OF_HOUSEHOLD }
enum class HomeDisposition { KEEP, SELL_AT_RETIREMENT }
enum class ImportStatus { NEVER, ACCEPTED, NEEDS_ATTENTION }

data class ProviderIdentity(
    val credentialProfileId: String,
    val environment: ProviderEnvironment,
    val itemId: String,
    val providerAccountId: String,
)

data class AccountRevision(
    val id: String,
    val revision: Long,
    val displayName: String,
    val owner: Owner,
    val type: AccountType,
    val taxTreatment: TaxTreatment,
    val includedInForecast: Boolean,
    val effectiveAt: Instant,
    val previousRevisionId: String? = null,
)

data class BalanceSnapshot(
    val id: String,
    val asOfDate: LocalDate,
    val acceptedAt: Instant,
    val sequence: Long,
    val amount: Money,
    val basis: Money?,
    val source: BalanceSource,
    val batchId: String,
    val supersedesId: String? = null,
)

data class Holding(
    val securityId: String,
    val quantity: String,
    val price: Money?,
    val basis: Money?,
    val assetClass: String,
)

data class HoldingSnapshot(
    val batchId: String,
    val acceptedAt: Instant,
    val availability: HoldingAvailability,
    val holdings: List<Holding>,
)

data class Account(
    val id: String,
    val origin: AccountOrigin,
    val archivedAt: Instant?,
    val providerIdentity: ProviderIdentity?,
    val revisions: List<AccountRevision>,
    val balances: List<BalanceSnapshot>,
    val holdingSnapshots: List<HoldingSnapshot> = emptyList(),
) {
    val currentRevision get() = revisions.maxByOrNull { it.revision } ?: error("Account has no revision.")
    val currentBalance get() = balances.maxWithOrNull(compareBy<BalanceSnapshot>({ it.asOfDate }, { it.sequence }))
}

data class MortgageTerms(
    val outstanding: Money,
    val asOfDate: LocalDate,
    val originalPrincipal: Money?,
    val annualRateBps: Int,
    val payment: Money,
    val remainingMonths: Int,
    val contractualTermMonths: Int,
)

data class PropertyRevision(
    val id: String,
    val revision: Long,
    val address: String,
    val facts: String?,
    val owner: Owner,
    val ownershipBps: Int,
    val mortgage: MortgageTerms,
    val automaticValueEnabled: Boolean,
    val includedInForecast: Boolean,
    val effectiveAt: Instant,
    val previousRevisionId: String? = null,
    val providerPropertyId: String? = null,
    val credentialProfileId: String? = null,
)

data class PropertyValuationSnapshot(
    val id: String,
    val estimate: Money,
    val rangeLow: Money?,
    val rangeHigh: Money?,
    val comparableCount: Int?,
    val source: ValuationSource,
    val providerAsOf: LocalDate?,
    val acceptedAt: Instant,
    val sequence: Long,
    val batchId: String,
    val supersedesId: String? = null,
)

data class Property(
    val id: String,
    val accountId: String,
    val archivedAt: Instant?,
    val revisions: List<PropertyRevision>,
    val valuations: List<PropertyValuationSnapshot>,
) {
    val currentRevision get() = revisions.maxByOrNull { it.revision } ?: error("Property has no revision.")
    val currentValuation get() = valuations.maxWithOrNull(compareBy<PropertyValuationSnapshot>(
        { it.providerAsOf ?: it.acceptedAt.atZone(ZoneOffset.UTC).toLocalDate() }, { it.sequence },
    ))
    fun equity(): Money? = currentValuation?.let { valuation ->
        val net = BigDecimal.valueOf(Math.subtractExact(valuation.estimate.cents, currentRevision.mortgage.outstanding.cents))
        Money(net.multiply(BigDecimal.valueOf(currentRevision.ownershipBps.toLong())).divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact())
    }
}

data class ProviderItemState(
    val id: String,
    val accountId: String?,
    val status: ProviderStatus,
    val attemptedAt: Instant?,
    val lastAcceptedAt: Instant?,
    val retryAfter: Instant?,
    val error: ProviderError?,
    val revision: Long,
    val revokedAt: Instant?,
)

data class AcceptedEpicImport(
    val id: String,
    val formatId: String,
    val parserVersion: Int,
    val acceptedAt: Instant,
    val contentDigest: String,
    val workbook: EpicWorkbook,
    val replacesImportId: String?,
) {
    val vestedValue get() = workbook.totals.vestedValue
    val unvestedValue get() = workbook.totals.unvestedValue
    val loans get() = workbook.totals.loans
    override fun toString() = "AcceptedEpicImport([private])"
}

data class ImportMetadata(
    val source: String,
    val status: ImportStatus,
    val acceptedImportId: String?,
    val attemptedAt: Instant?,
    val lastAcceptedAt: Instant?,
    val safeError: ProviderError?,
)

enum class IncomeTaxKind { ORDINARY, SOCIAL_SECURITY, TAX_FREE }
data class IncomeStream(val id: String, val annualAmount: Money, val startAge: Int, val endAge: Int, val taxKind: IncomeTaxKind)
data class PlanSettings(
    val id: String,
    val revision: Long,
    val birthDate: LocalDate,
    val referenceDate: LocalDate,
    val retirementAge: Int,
    val endAge: Int,
    val annualSpending: Money,
    val inflationBps: Int,
    val expectedReturnBps: Int,
    val filingStatus: FilingStatus,
    val stateCode: String,
    val taxPolicyId: String,
    val acaHouseholdSize: Int,
    val acaAnnualPremium: Money,
    val acaRegime: String,
    val annualPreTaxContribution: Money,
    val annualRothContribution: Money,
    val annualTaxableContribution: Money,
    val incomeStreams: List<IncomeStream>,
    val homeDisposition: HomeDisposition,
    val annualHsaContribution: Money = Money(0),
    val annualMedicalSpending: Money = Money(0),
    val homeRealAppreciationBps: Int = 0,
    val volatilityScaleBps: Int = 10000,
)

data class ChecklistState(val catalogId: String, val checked: Boolean, val updatedAt: Instant)

data class RetirementState(
    val generation: Long = 0,
    val accounts: List<Account> = emptyList(),
    val properties: List<Property> = emptyList(),
    val epicImports: List<AcceptedEpicImport> = emptyList(),
    val activeEpicImportId: String? = null,
    val importMetadata: List<ImportMetadata> = emptyList(),
    val planSettings: List<PlanSettings> = emptyList(),
    val checklist: List<ChecklistState> = emptyList(),
    val providerItems: List<ProviderItemState> = emptyList(),
    val acceptedProviderOperations: List<String> = emptyList(),
)

data class AssetTotals(val tracked: Money, val forecastEligible: Money)

object AssetAggregator {
    fun totals(state: RetirementState): AssetTotals {
        var tracked = Money(0)
        var forecast = Money(0)
        state.accounts.filter { it.archivedAt == null && it.origin in setOf(AccountOrigin.MANUAL, AccountOrigin.PLAID) }.forEach { account ->
            val value = account.currentBalance?.amount ?: return@forEach
            tracked += value
            if (account.currentRevision.includedInForecast) forecast += value
        }
        state.activeEpicImportId?.let { id -> state.epicImports.singleOrNull { it.id == id } }?.let { epic ->
            val value = epic.vestedValue - epic.loans
            tracked += value
            forecast += value
        }
        state.properties.filter { it.archivedAt == null }.forEach { property ->
            property.equity()?.let { equity ->
                tracked += equity
                if (property.currentRevision.includedInForecast) forecast += equity
            }
        }
        return AssetTotals(tracked, forecast)
    }
}

fun validateRetirementState(state: RetirementState) {
    require(state.generation >= 0)
    fun unique(values: List<String>, label: String) = require(values.size == values.toSet().size) { "Duplicate $label." }
    unique(state.accounts.map { it.id }, "account ID")
    unique(state.properties.map { it.id }, "property ID")
    unique(state.properties.map { it.accountId }, "property wrapper")
    unique(state.properties.filter { it.archivedAt == null }.mapNotNull { it.currentRevision.providerPropertyId }, "active provider property")
    unique(state.epicImports.map { it.id }, "Epic import ID")
    unique(state.checklist.map { it.catalogId }, "checklist ID")
    unique(state.providerItems.map { it.id }, "provider item ID")
    unique(state.acceptedProviderOperations, "provider operation")
    require(state.acceptedProviderOperations.all { it.matches(Regex("[0-9a-f-]{36}:[1-9][0-9]*")) })
    val identities = state.accounts.mapNotNull { it.providerIdentity }
    require(identities.size == identities.toSet().size) { "Duplicate provider identity." }
    val accountIds = state.accounts.mapTo(hashSetOf()) { it.id }
    state.accounts.forEach { account ->
        require(account.id.isNotBlank() && account.revisions.isNotEmpty())
        require(account.origin == AccountOrigin.PLAID == (account.providerIdentity != null))
        unique(account.revisions.map { it.id }, "account revision ID")
        unique(account.balances.map { it.id }, "balance snapshot ID")
        require(account.revisions.map { it.revision }.distinct().size == account.revisions.size)
        account.revisions.forEach {
            require(it.displayName.isNotBlank() && it.displayName.length <= 120 && it.revision > 0)
            require((account.origin == AccountOrigin.PROPERTY) == (it.type == AccountType.PROPERTY && it.taxTreatment == TaxTreatment.PROPERTY))
            require((account.origin == AccountOrigin.EPIC) == (it.type == AccountType.EPIC && it.taxTreatment == TaxTreatment.EPIC))
            if (account.origin in setOf(AccountOrigin.MANUAL, AccountOrigin.PLAID)) require(it.type !in setOf(AccountType.PROPERTY, AccountType.EPIC) && it.taxTreatment !in setOf(TaxTreatment.PROPERTY, TaxTreatment.EPIC))
        }
        if (account.origin == AccountOrigin.PROPERTY) require(account.balances.isEmpty()) { "Property wrapper cannot carry a second balance." }
        account.balances.forEach { require(it.amount.cents in -MAX_ASSET_CENTS..MAX_ASSET_CENTS &&
            (it.basis == null || it.basis.cents in -MAX_ASSET_CENTS..MAX_ASSET_CENTS)) }
        account.holdingSnapshots.forEach { snapshot ->
            require(snapshot.availability == HoldingAvailability.COMPLETE || snapshot.holdings.isEmpty())
            unique(snapshot.holdings.map { it.securityId }, "holding security")
            snapshot.holdings.forEach { BigDecimal(it.quantity); require(it.assetClass.isNotBlank()) }
        }
    }
    state.properties.forEach { property ->
        require(property.accountId in accountIds)
        require(state.accounts.single { it.id == property.accountId }.origin == AccountOrigin.PROPERTY)
        require(property.revisions.isNotEmpty() && property.valuations.isNotEmpty())
        property.revisions.forEach { revision ->
            require(revision.address.isNotBlank() && revision.ownershipBps in 0..10_000)
            require(revision.address.length <= 240 && (revision.facts?.length ?: 0) <= 500)
            require(revision.mortgage.outstanding.cents in 0..MAX_ASSET_CENTS && revision.mortgage.annualRateBps in 0..2500)
            require(revision.mortgage.remainingMonths in 0..600 && revision.mortgage.contractualTermMonths in 0..600)
            require(revision.mortgage.payment.cents in 0..MAX_ASSET_CENTS)
            require(revision.mortgage.originalPrincipal == null || revision.mortgage.originalPrincipal.cents in 0..MAX_ASSET_CENTS)
            require((revision.providerPropertyId == null) == (revision.credentialProfileId == null))
            require(!revision.automaticValueEnabled || revision.providerPropertyId != null)
            require(revision.providerPropertyId?.isNotBlank() != false && revision.credentialProfileId?.isNotBlank() != false)
        }
        property.valuations.forEach { value ->
            require(value.estimate.cents in 0..MAX_ASSET_CENTS && value.comparableCount?.let { it >= 0 } != false)
            require((value.rangeLow == null) == (value.rangeHigh == null))
            if (value.rangeLow != null) require(value.rangeLow.cents in 0..value.estimate.cents && value.rangeHigh!!.cents in value.estimate.cents..MAX_ASSET_CENTS)
        }
    }
    val epicWrappers = state.accounts.filter { it.origin == AccountOrigin.EPIC }
    require(epicWrappers.size <= 1 && epicWrappers.all { it.balances.isEmpty() }) { "Epic wrapper cannot carry a second balance." }
    state.activeEpicImportId?.let { active -> require(state.epicImports.count { it.id == active } == 1) }
    state.epicImports.forEach {
        require(it.formatId == "shareworks-2026" && it.parserVersion == 1 && it.id.isNotBlank())
        require(it.contentDigest.matches(Regex("[0-9a-f]{64}")))
        validateEpicWorkbook(it.workbook)
    }
    state.planSettings.forEach { plan ->
        require(plan.revision > 0 && plan.retirementAge in 0..120 && plan.endAge in plan.retirementAge..130)
        require(plan.inflationBps in -1000..10_000 && plan.expectedReturnBps in -10_000..30_000)
        require(plan.stateCode.matches(Regex("[A-Z]{2}")) && plan.acaHouseholdSize in 1..20)
        unique(plan.incomeStreams.map { it.id }, "income stream ID")
        require(plan.referenceDate >= plan.birthDate && plan.referenceDate.year in 1900..2300)
        require(plan.homeRealAppreciationBps in -5000..10000 && plan.volatilityScaleBps in 0..30000)
        require(listOf(plan.annualSpending, plan.acaAnnualPremium, plan.annualPreTaxContribution,
            plan.annualRothContribution, plan.annualTaxableContribution, plan.annualHsaContribution,
            plan.annualMedicalSpending).all { it.cents in 0..MAX_ASSET_CENTS })
        plan.incomeStreams.forEach { require(it.annualAmount.cents in 0..MAX_ASSET_CENTS && it.startAge in 0..130 && it.endAge in it.startAge..200) }
    }
}
