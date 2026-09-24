package dev.draftingroom5.retirement.ui

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class ForecastChartPoint(val age: Int, val low: Money, val middle: Money, val high: Money)

internal data class ForecastSuccessPoint(val age: Int, val rate: Double)

internal fun forecastSuccessPoints(result: ForecastResult): List<ForecastSuccessPoint> {
    val failuresByAge = result.failures.groupingBy { it.age }.eachCount()
    var cumulativeFailures = 0
    return (result.currentAge..result.endAge).map { age ->
        cumulativeFailures += failuresByAge[age] ?: 0
        ForecastSuccessPoint(age, (result.paths - cumulativeFailures).toDouble() / result.paths)
    }
}

internal fun forecastChartPoints(result: ForecastResult, retirementAge: Int): List<ForecastChartPoint> {
    val step = maxOf(1, (result.endAge - result.currentAge) / 10)
    val ages = ((result.currentAge..result.endAge step step).toList() + retirementAge + result.endAge)
        .filter { it in result.currentAge..result.endAge }.distinct().sorted()
    return ages.map { age -> ForecastChartPoint(age,
        result.percentile(ForecastChannel.TOTAL, age, 10.0),
        result.percentile(ForecastChannel.TOTAL, age, 50.0),
        result.percentile(ForecastChannel.TOTAL, age, 90.0)) }
}

internal fun forecastChartDescription(points: List<ForecastChartPoint>, retirementAge: Int): String = buildString {
    append("Modeled account range chart. ")
    points.forEach { append("Age ${it.age}: 10th percentile ${it.low.format()}, median ${it.middle.format()}, 90th percentile ${it.high.format()}. ") }
    append("Retirement begins at age $retirementAge.")
}

internal data class AvailablePart(val label: String, val amount: Money)

internal fun availableAtRetirement(result: ForecastResult, requestedAge: Int): List<AvailablePart> {
    val retirementAge = requestedAge.coerceIn(result.currentAge, result.endAge)
    return listOf(
    AvailablePart("Pre-tax", result.percentile(ForecastChannel.TRADITIONAL, retirementAge, 50.0)),
    AvailablePart("Roth", result.percentile(ForecastChannel.ROTH, retirementAge, 50.0)),
    AvailablePart("HSA", result.percentile(ForecastChannel.HSA, retirementAge, 50.0)),
    AvailablePart("Taxable", result.percentile(ForecastChannel.TAXABLE, retirementAge, 50.0)),
    AvailablePart("Other", result.percentile(ForecastChannel.NON_RETIREMENT, retirementAge, 50.0)),
    AvailablePart("Property", result.percentile(ForecastChannel.PROPERTY, retirementAge, 50.0)),
    AvailablePart("Epic stock", result.percentile(ForecastChannel.EPIC_AFTER_TAX, retirementAge, 50.0)),
).filter { it.amount.cents != 0L }
}

internal data class ForecastRiskSummary(
    val failedPaths: Int,
    val firstFailureAge: Int?,
    val exhaustedPaths: Int,
    val accessLimitedPaths: Int,
    val largestUnmet: Money,
)

internal fun summarizeRisk(result: ForecastResult): ForecastRiskSummary {
    val failures = result.failures
    return ForecastRiskSummary(
        failures.size,
        failures.minOfOrNull { it.age },
        failures.count { it.cause == FailureCause.ASSETS_EXHAUSTED },
        failures.count { it.cause == FailureCause.ACCESS_OR_RULE_LIMIT },
        failures.maxByOrNull { it.unmet.cents }?.unmet ?: Money(0),
    )
}

internal data class ScenarioSpec(val id: String, val title: String, val change: String, val delta: ScenarioDelta)

internal fun scenarioSpecs(plan: PlanSettings): List<ScenarioSpec> = listOf(
    ScenarioSpec("higher-spending", "Spend 10% more", "Annual lifestyle spending ${plan.annualSpending.format()} → ${scaleMoney(plan.annualSpending, 110).format()}",
        ScenarioDelta.Spending(scaleMoney(plan.annualSpending, 110))),
    ScenarioSpec("lower-return", "Lower market return", "Expected equity return ${formatBps(plan.expectedReturnBps)} → ${formatBps(plan.expectedReturnBps - 100)}",
        ScenarioDelta.EquityReturn(plan.expectedReturnBps - 100)),
    ScenarioSpec("retire-earlier", "Retire two years earlier", "Retirement age ${plan.retirementAge} → ${maxOf(0, plan.retirementAge - 2)}",
        ScenarioDelta.RetirementAge(maxOf(0, plan.retirementAge - 2))),
)

private fun scaleMoney(value: Money, percent: Int) = Money(
    BigDecimal.valueOf(value.cents).multiply(BigDecimal.valueOf(percent.toLong()))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact(),
)

internal fun formatBps(value: Int): String = BigDecimal.valueOf(value.toLong(), 2).stripTrailingZeros().toPlainString() + "%"

/** New plans must expose income entry even before any streams have been saved. */
internal fun editableIncomePlan(plan: PlanSettings): PlanSettings {
    val streams = plan.incomeStreams.toMutableList()
    listOf(IncomeTaxKind.SOCIAL_SECURITY to 67, IncomeTaxKind.ORDINARY to plan.retirementAge).forEach { (kind, start) ->
        if (streams.none { it.taxKind == kind }) {
            var id = "income-${kind.name.lowercase()}"
            while (streams.any { it.id == id }) id += "-new"
            streams += IncomeStream(id, Money(0), start, maxOf(start, plan.endAge), kind)
        }
    }
    return plan.copy(incomeStreams = streams)
}

internal data class ForecastSettingsDraft(
    val birthDate: String, val retirementAge: String, val endAge: String,
    val annualSpending: String, val inflationPercent: String, val expectedReturnPercent: String,
    val volatilityPercent: String, val filingStatus: FilingStatus, val stateCode: String,
    val acaHouseholdSize: String, val acaAnnualPremium: String, val acaRegime: String,
    val preTaxContribution: String, val rothContribution: String, val taxableContribution: String,
    val hsaContribution: String, val medicalSpending: String, val homeAppreciationPercent: String,
    val homeDisposition: HomeDisposition, val incomeAmounts: Map<String, String>,
    val incomeStartAges: Map<String, String>, val incomeEndAges: Map<String, String>,
) {
    fun validated(base: PlanSettings, today: LocalDate = LocalDate.now()): Result<PlanSettings> = runCatching {
        fun money(text: String) = Money.parse(text).also { require(it.cents >= 0) }
        fun integer(text: String) = text.trim().toInt()
        fun bps(text: String): Int = BigDecimal(text.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
        val birth = LocalDate.parse(birthDate.trim())
        val retirement = integer(retirementAge); val end = integer(endAge)
        require(today >= birth && retirement in 0..120 && end in retirement..130)
        require(stateCode.trim().uppercase() in ReferenceTaxPolicy.supportedStates)
        base.copy(
            birthDate = birth, referenceDate = today, retirementAge = retirement, endAge = end,
            annualSpending = money(annualSpending), inflationBps = bps(inflationPercent),
            expectedReturnBps = bps(expectedReturnPercent), volatilityScaleBps = bps(volatilityPercent),
            filingStatus = filingStatus, stateCode = stateCode.trim().uppercase(),
            acaHouseholdSize = integer(acaHouseholdSize), acaAnnualPremium = money(acaAnnualPremium), acaRegime = acaRegime,
            annualPreTaxContribution = money(preTaxContribution), annualRothContribution = money(rothContribution),
            annualTaxableContribution = money(taxableContribution), annualHsaContribution = money(hsaContribution),
            annualMedicalSpending = money(medicalSpending), homeRealAppreciationBps = bps(homeAppreciationPercent),
            homeDisposition = homeDisposition,
            incomeStreams = base.incomeStreams.map { it.copy(
                annualAmount = money(incomeAmounts[it.id] ?: it.annualAmount.format()),
                startAge = integer(incomeStartAges[it.id] ?: it.startAge.toString()),
                endAge = integer(incomeEndAges[it.id] ?: it.endAge.toString()),
            ) },
        ).also { validateRetirementState(RetirementState(planSettings = listOf(it))) }
    }

    companion object {
        fun from(plan: PlanSettings) = ForecastSettingsDraft(
            plan.birthDate.toString(), plan.retirementAge.toString(), plan.endAge.toString(),
            plan.annualSpending.format(), formatBps(plan.inflationBps).removeSuffix("%"),
            formatBps(plan.expectedReturnBps).removeSuffix("%"), formatBps(plan.volatilityScaleBps).removeSuffix("%"),
            plan.filingStatus, plan.stateCode, plan.acaHouseholdSize.toString(), plan.acaAnnualPremium.format(), plan.acaRegime,
            plan.annualPreTaxContribution.format(), plan.annualRothContribution.format(), plan.annualTaxableContribution.format(),
            plan.annualHsaContribution.format(), plan.annualMedicalSpending.format(),
            formatBps(plan.homeRealAppreciationBps).removeSuffix("%"), plan.homeDisposition,
            plan.incomeStreams.associate { it.id to it.annualAmount.format() },
            plan.incomeStreams.associate { it.id to it.startAge.toString() },
            plan.incomeStreams.associate { it.id to it.endAge.toString() },
        )
        fun blank(plan: PlanSettings) = from(plan).copy(
            birthDate = "", retirementAge = "", endAge = "", annualSpending = "",
        )
    }
}
