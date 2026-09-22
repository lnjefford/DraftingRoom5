package dev.draftingroom5.retirement.ui

import dev.draftingroom5.AppRoute
import dev.draftingroom5.retirement.domain.AccountOrigin
import dev.draftingroom5.retirement.domain.AssetAggregator
import dev.draftingroom5.retirement.domain.ImportStatus
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.domain.ProviderStatus
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.domain.TaxTreatment
import java.time.Duration
import java.time.Instant

internal data class AssetSummaryRow(val treatment: TaxTreatment, val amount: Money)

internal data class IntegratedAssetSummary(
    val tracked: Money,
    val forecastEligible: Money,
    val rows: List<AssetSummaryRow>,
) {
    init { require(rows.fold(Money(0)) { total, row -> total + row.amount } == tracked) }
}

internal fun integratedAssetSummary(state: RetirementState): IntegratedAssetSummary {
    val totals = AssetAggregator.totals(state)
    val amounts = TaxTreatment.entries.associateWith { Money(0) }.toMutableMap()
    state.accounts
        .filter { it.archivedAt == null && it.origin in setOf(AccountOrigin.MANUAL, AccountOrigin.PLAID) }
        .forEach { account ->
            account.currentBalance?.amount?.let { value ->
                val treatment = account.currentRevision.taxTreatment
                amounts[treatment] = amounts.getValue(treatment) + value
            }
        }
    state.activeEpicImportId
        ?.let { id -> state.epicImports.singleOrNull { it.id == id } }
        ?.let { epic -> amounts[TaxTreatment.EPIC] = epic.vestedValue - epic.loans }
    state.properties.filter { it.archivedAt == null }.forEach { property ->
        property.equity()?.let { equity -> amounts[TaxTreatment.PROPERTY] = amounts.getValue(TaxTreatment.PROPERTY) + equity }
    }
    return IntegratedAssetSummary(
        tracked = totals.tracked,
        forecastEligible = totals.forecastEligible,
        rows = TaxTreatment.entries.map { AssetSummaryRow(it, amounts.getValue(it)) },
    )
}

internal enum class DataHealthKind { HEALTHY, ACCOUNT, PROPERTY, EPIC_IMPORT }

internal data class DataHealth(
    val kind: DataHealthKind,
    val title: String,
    val detail: String,
    val route: AppRoute?,
    val stale: Boolean = false,
)

internal fun retirementDataHealth(
    state: RetirementState,
    now: Instant = Instant.now(),
): DataHealth {
    val problems = state.providerItems
        .filter { it.status != ProviderStatus.READY || it.revokedAt != null }
        .sortedWith(compareBy({ it.status == ProviderStatus.OFFLINE }, { it.id }))
    for (problem in problems) {
        val property = state.properties.singleOrNull { it.accountId == problem.accountId && it.archivedAt == null }
        if (property != null) return DataHealth(
            DataHealthKind.PROPERTY,
            "Property estimate needs attention",
            "The last accepted equity remains in totals.",
            AppRoute.RetirementPropertyDetail(property.id),
        )
        val account = state.accounts.singleOrNull { it.id == problem.accountId && it.archivedAt == null }
            ?: state.accounts.firstOrNull { it.providerIdentity?.itemId == problem.id && it.archivedAt == null }
        if (account != null) return DataHealth(
            DataHealthKind.ACCOUNT,
            "${account.currentRevision.displayName} needs attention",
            "Review the affected account; other accounts remain available.",
            AppRoute.RetirementAccountDetail(account.id),
        )
    }
    if (state.importMetadata.any { it.source == "SHAREWORKS" && it.status == ImportStatus.NEEDS_ATTENTION }) {
        return DataHealth(
            DataHealthKind.EPIC_IMPORT,
            "Epic workbook needs attention",
            "Review the failed import. The last accepted workbook remains in use.",
            AppRoute.RetirementEpicUpload,
        )
    }
    val staleAccount = state.accounts
        .filter { it.archivedAt == null && it.origin == AccountOrigin.PLAID }
        .firstOrNull { account ->
            val accepted = state.providerItems.singleOrNull { it.id == account.providerIdentity?.itemId }?.lastAcceptedAt
                ?: account.currentBalance?.acceptedAt
            accepted == null || Duration.between(accepted, now).toDays() >= 2
        }
    if (staleAccount != null) return DataHealth(
        DataHealthKind.ACCOUNT,
        "${staleAccount.currentRevision.displayName} is stale",
        "Open the account to refresh its last accepted value.",
        AppRoute.RetirementAccountDetail(staleAccount.id),
        stale = true,
    )
    val staleProperty = state.properties
        .filter { it.archivedAt == null && it.currentRevision.automaticValueEnabled }
        .firstOrNull { property ->
            property.currentValuation?.acceptedAt?.let { Duration.between(it, now).toDays() >= 8 } != false
        }
    if (staleProperty != null) return DataHealth(
        DataHealthKind.PROPERTY,
        "Property estimate is stale",
        "Open the property to refresh its last accepted estimate.",
        AppRoute.RetirementPropertyDetail(staleProperty.id),
        stale = true,
    )
    val hasAnyValue = integratedAssetSummary(state).tracked.cents != 0L
    return if (hasAnyValue) DataHealth(
        DataHealthKind.HEALTHY,
        "Data is up to date",
        "No linked account, workbook, or property needs attention.",
        null,
    ) else DataHealth(
        DataHealthKind.HEALTHY,
        "Add your first account",
        "Accounts, Epic stock, and property equity will appear here.",
        AppRoute.RetirementAccounts,
    )
}

internal data class LibraryResource(
    val id: String,
    val title: String,
    val organization: String,
    val url: String,
    val checklistPrompt: String,
)

internal const val RETIREMENT_LIBRARY_REVIEW_DATE = "September 21, 2026"

internal val RetirementLibraryResources = listOf(
    LibraryResource(
        "ssa-retirement",
        "Plan for retirement",
        "Social Security Administration",
        "https://www.ssa.gov/retirement/plan-for-retirement",
        "Review my Social Security estimate",
    ),
    LibraryResource(
        "medicare-start",
        "Get started with Medicare",
        "Medicare.gov",
        "https://www.medicare.gov/basics/get-started-with-medicare",
        "Review my Medicare timing",
    ),
    LibraryResource(
        "irs-retirement",
        "Retirement plans",
        "Internal Revenue Service",
        "https://www.irs.gov/retirement-plans",
        "Review retirement tax guidance",
    ),
    LibraryResource(
        "investor-retirement",
        "Saving for retirement",
        "Investor.gov — U.S. SEC",
        "https://www.investor.gov/introduction-investing/investing-basics/investment-accounts/tax-advantaged-accounts/retirement-savings",
        "Review fees and investment basics",
    ),
)
