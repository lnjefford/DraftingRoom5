package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import dev.draftingroom5.retirement.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ForecastPresentationTest {
    @Test fun alreadyRetiredPlanUsesCurrentBoundaryWithoutCrashing() {
        val result = result()
        assertEquals(availableAtRetirement(result, result.currentAge), availableAtRetirement(result, result.currentAge - 5))
    }
    @Test fun newPlanCanEnterAndPersistSocialSecurityAndPension() {
        val base = forecastState().planSettings.single().copy(incomeStreams = emptyList())
        val editable = editableIncomePlan(base)
        val ss = editable.incomeStreams.single { it.taxKind == IncomeTaxKind.SOCIAL_SECURITY }
        val pension = editable.incomeStreams.single { it.taxKind == IncomeTaxKind.ORDINARY }
        val draft = ForecastSettingsDraft.from(editable).copy(incomeAmounts = mapOf(ss.id to "32000", pension.id to "18000"))
        val changed = draft.validated(editable).getOrThrow()
        assertEquals(Money(3200000), changed.incomeStreams.single { it.id == ss.id }.annualAmount)
        assertEquals(Money(1800000), changed.incomeStreams.single { it.id == pension.id }.annualAmount)
        assertEquals(changed, editableIncomePlan(changed))
        val captured = ForecastInputs.capture(forecastState().copy(planSettings = listOf(changed))) as ForecastCapture.Ready
        assertEquals(2, captured.input.incomes.size)
    }
    private fun result() = runBlocking {
        val input = (ForecastInputs.capture(forecastState()) as ForecastCapture.Ready).input
        RetirementEngine().calculate(input, 40, 75, true)
    }

    @Test fun chartPublishesOrderedRangeAndIncludesRetirementMarker() {
        val plan = forecastState().planSettings.single()
        val points = forecastChartPoints(result(), plan.retirementAge)
        assertTrue(points.any { it.age == plan.retirementAge })
        assertEquals(points.map { it.age }.sorted(), points.map { it.age })
        points.forEach { assertTrue(it.low.cents <= it.middle.cents); assertTrue(it.middle.cents <= it.high.cents) }
        val description = forecastChartDescription(points, plan.retirementAge)
        assertTrue(description.contains("10th percentile"))
        assertTrue(description.contains("median"))
        assertTrue(description.contains("90th percentile"))
        assertTrue(description.endsWith("Retirement begins at age ${plan.retirementAge}."))
    }

    @Test fun availableBreakdownUsesEngineChannelsWithoutDoubleCountingLabels() {
        val plan = forecastState().planSettings.single()
        val parts = availableAtRetirement(result(), plan.retirementAge)
        assertEquals(parts.map { it.label }.distinct(), parts.map { it.label })
        assertTrue(parts.all { it.amount.cents != 0L })
        assertFalse(parts.any { it.label.contains("holding", ignoreCase = true) })
    }

    @Test fun riskSummaryReportsOnlyFactualEngineDiagnostics() {
        val result = result()
        val risk = summarizeRisk(result)
        assertEquals(result.failures.size, risk.failedPaths)
        assertEquals(result.failures.minOfOrNull { it.age }, risk.firstFailureAge)
        assertEquals(result.failures.size, risk.exhaustedPaths + risk.accessLimitedPaths)
    }

    @Test fun failureChartIsCumulativeByAgeAndEndsAtModeledFailureRate() {
        val result = result()
        val points = forecastFailurePoints(result)
        assertEquals(result.currentAge, points.first().age)
        assertEquals(result.endAge, points.last().age)
        assertTrue(points.zipWithNext().all { (left, right) -> left.rate <= right.rate })
        assertEquals(1.0 - result.successRate, points.last().rate, 1e-12)
    }

    @Test fun settingsDraftValidatesAsOneCompletePlanAndPreservesWorkbookOwnership() {
        val plan = forecastState().planSettings.single()
        val draft = ForecastSettingsDraft.from(plan).copy(
            annualSpending = "\$51,000.00", expectedReturnPercent = "5.25", retirementAge = "62",
            incomeAmounts = mapOf("ss" to "\$32,000.00"),
            incomeStartAges = mapOf("ss" to "68"), incomeEndAges = mapOf("ss" to "95"),
        )
        val changed = draft.validated(plan).getOrThrow()
        assertEquals(Money(5_100_000), changed.annualSpending)
        assertEquals(525, changed.expectedReturnBps)
        assertEquals(62, changed.retirementAge)
        assertEquals(Money(3_200_000), changed.incomeStreams.single().annualAmount)
        assertEquals(68, changed.incomeStreams.single().startAge)
        assertEquals(95, changed.incomeStreams.single().endAge)
        assertEquals(plan.id, changed.id)
        assertEquals(plan.revision, changed.revision)
        assertTrue(draft.copy(endAge = "20").validated(plan).isFailure)
        assertTrue(draft.copy(stateCode = "Wisconsin").validated(plan).isFailure)
    }

    @Test fun eachScenarioIsOneDisplayedDeltaFromTheBasePlan() {
        val plan = forecastState().planSettings.single()
        val scenarios = scenarioSpecs(plan)
        assertEquals(listOf("higher-spending", "lower-return", "retire-earlier"), scenarios.map { it.id })
        scenarios.forEach { scenario ->
            val changed = scenario.delta.change(plan)
            when (scenario.id) {
                "higher-spending" -> assertEquals(plan.copy(annualSpending = changed.annualSpending), changed)
                "lower-return" -> assertEquals(plan.copy(expectedReturnBps = changed.expectedReturnBps), changed)
                "retire-earlier" -> assertEquals(plan.copy(retirementAge = changed.retirementAge), changed)
            }
            assertTrue(scenario.change.contains("→"))
        }
    }
}
