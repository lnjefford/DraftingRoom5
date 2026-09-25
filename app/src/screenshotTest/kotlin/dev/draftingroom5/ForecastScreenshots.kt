package dev.draftingroom5

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import dev.draftingroom5.retirement.ui.*
import java.time.LocalDate

class ForecastCases : PreviewParameterProvider<Int> { override val values = (0..7).asSequence() }

private fun screenshotPlan() = PlanSettings(
    "plan", 3, LocalDate.of(1980, 1, 2), LocalDate.of(2026, 1, 1), 60, 90,
    Money(5_400_000), 250, 650, FilingStatus.MARRIED_FILING_JOINTLY, "WI", PlanningTaxPolicy.ID,
    2, Money(1_800_000), "CLIFF", Money(2_300_000), Money(700_000), Money(600_000),
    listOf(IncomeStream("ss", Money(3_200_000), 67, 90, IncomeTaxKind.SOCIAL_SECURITY),
        IncomeStream("spouse-ss", Money(1_200_000), 67, 130, IncomeTaxKind.SOCIAL_SECURITY, Owner.SPOUSE)),
    HomeDisposition.KEEP, Money(400_000), Money(250_000), 100, 10_000, spouseBirthYear = 1988,
)

private fun screenshotResult(successPaths: Int = 16): ForecastResult {
    val currentAge = 45; val endAge = 90; val paths = 20; val width = endAge - currentAge + 1
    val series = Array(ForecastChannel.entries.size) { DoubleArray(paths * width) }
    for (path in 0 until paths) for (offset in 0 until width) {
        val age = currentAge + offset
        val total = (800_000.0 + path * 9_000) * (1.0 + offset * .012) - maxOf(0, age - 60) * 19_000
        val safe = maxOf(0.0, total)
        series[ForecastChannel.TRADITIONAL.ordinal][path * width + offset] = safe * .40
        series[ForecastChannel.ROTH.ordinal][path * width + offset] = safe * .18
        series[ForecastChannel.HSA.ordinal][path * width + offset] = safe * .05
        series[ForecastChannel.TAXABLE.ordinal][path * width + offset] = safe * .22
        series[ForecastChannel.PROPERTY.ordinal][path * width + offset] = if (age <= 60) safe * .12 else 0.0
        series[ForecastChannel.EPIC_AFTER_TAX.ordinal][path * width + offset] = if (age <= 60) safe * .03 else 0.0
        series[ForecastChannel.TOTAL.ordinal][path * width + offset] = safe
    }
    val failures = (successPaths until paths).mapIndexed { index, path ->
        PathFailure(path, 78 + index, Money(250_000L + index * 50_000L), Money(index * 100_000L),
            if (index % 2 == 0) FailureCause.ASSETS_EXHAUSTED else FailureCause.ACCESS_OR_RULE_LIMIT)
    }
    return ForecastResult(7, 3, currentAge, endAge, paths, 75, true, series, failures, Money(18_400), 37,
        listOf("Unfunded taxes are included in failed paths."),
        DoubleArray(width) { 54_000.0 })
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun ForecastScreenshots(@PreviewParameter(ForecastCases::class) case: Int) {
    val plan = screenshotPlan(); val result = screenshotResult()
    RetirementTheme { Surface {
        when (case) {
            0 -> ForecastStatePage(ForecastState.Ready(result), plan, {}, {})
            1 -> ForecastStatePage(ForecastState.Calculating(8, result), plan, {}, {}, animateCalculation = false)
            2 -> ForecastStatePage(ForecastState.NeedsData(MissingForecastData.BALANCE, null), plan, {}, {})
            3 -> ForecastStatePage(ForecastState.Failed(result), plan, {}, {})
            4 -> ForecastStatePage(ForecastState.Cancelled(result), plan, {}, {})
            5 -> ForecastRiskPage(result, plan) {}
            6 -> ForecastSettingsPage(plan, null) {}
            else -> ScenarioComparisonPreview(result, screenshotResult(14), scenarioSpecs(plan).first())
        }
    } }
}
