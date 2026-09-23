package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.draftingroom5.*
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.max

@Composable
internal fun RetirementForecastHost(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    previewState: RetirementState? = null,
) {
    val context = LocalContext.current
    var repository by remember { mutableStateOf<RetirementRepository?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(previewState) {
        if (previewState == null) {
            repository = withContext(Dispatchers.IO) { runCatching { RetirementProviders.get(context).repository }.getOrNull() }
            loadFailed = repository == null
        }
    }
    when {
        previewState != null -> ForecastPreviewRouter(route, previewState, onNavigate, onBack)
        repository != null -> LiveForecastRouter(route, repository!!, onNavigate, onBack)
        else -> ForecastMessagePage(if (loadFailed) "Forecast unavailable" else "Loading forecast",
            if (loadFailed) "Unlock the phone and try again. Saved financial data was not changed." else "Opening the private planning model.")
    }
}

@Composable
private fun LiveForecastRouter(route: AppRoute, repository: RetirementRepository, onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val coordinator = remember(repository) { ForecastCoordinator(repository, scope) }
    DisposableEffect(coordinator) { onDispose { coordinator.close() } }
    var snapshot by remember { mutableStateOf<RetirementState?>(null) }
    var saveMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val forecastState by coordinator.state.collectAsState()
    LaunchedEffect(repository) {
        snapshot = withContext(Dispatchers.IO) { repository.load() }
        val plan = snapshot!!.planSettings.maxByOrNull { it.revision }
        coordinator.restart(paths = forecastPathCount(plan))
    }
    LaunchedEffect(forecastState) {
        if (forecastState is ForecastState.Ready || forecastState is ForecastState.NeedsData) {
            snapshot = withContext(Dispatchers.IO) { repository.load() }
        }
    }
    val state = snapshot
    if (state == null) { ForecastMessagePage("Loading forecast", "Reading one coherent plan snapshot."); return }
    val plan = state.planSettings.maxByOrNull { it.revision }
    val displayedResult = when (val value = forecastState) {
        is ForecastState.Ready -> value.result
        is ForecastState.Calculating -> value.previous
        is ForecastState.NeedsData -> value.previous
        is ForecastState.Failed -> value.previous
        is ForecastState.Cancelled -> value.previous
        ForecastState.Idle -> null
    }
    val resultPlan = displayedResult?.let { result -> state.planSettings.singleOrNull { it.revision == result.planRevision } }
    when (route) {
        AppRoute.RetirementForecast -> ForecastStatePage(forecastState, resultPlan,
            onRetry = { coordinator.restart(paths = forecastPathCount(plan)) }, onCancel = coordinator::cancel,
            onRisk = { result -> onNavigate(AppRoute.RetirementForecastRisk(result.generation, result.planRevision)) })
        AppRoute.RetirementForecastSettings -> {
            val formPlan = plan ?: newPlanTemplate()
            ForecastSettingsPage(formPlan, saveMessage, newPlan = plan == null) { candidate ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val latest = repository.load(); val current = latest.planSettings.maxByOrNull { it.revision }
                        if (current?.revision != plan?.revision) RetirementResult.Conflict(latest.generation)
                        else repository.savePlan(latest.generation, candidate.copy(id = UUID.randomUUID().toString(), revision = (current?.revision ?: 0) + 1))
                    }
                    if (result is RetirementResult.Success) { snapshot = result.value; saveMessage = null; onBack() }
                    else saveMessage = "The plan changed before this form was saved. Review the latest values and try again."
                }
            }
        }
        is AppRoute.RetirementForecastRisk -> ResultForRoute(route.generation, route.planRevision, forecastState) { result ->
            ForecastRiskPage(result, resultPlan,
                onScenario = { id -> onNavigate(AppRoute.RetirementScenarioDetail(result.generation, result.planRevision, id)) })
        }
        is AppRoute.RetirementScenarioDetail -> ResultForRoute(route.generation, route.planRevision, forecastState) { result ->
            if (resultPlan == null) ForecastMessagePage("Scenario unavailable", "The base plan is no longer available.")
            else ScenarioPage(repository, state, resultPlan, result, route.scenarioId, onBack)
        }
        else -> error("Not a Forecast route")
    }
}

private fun newPlanTemplate(): PlanSettings {
    val today = java.time.LocalDate.now()
    return PlanSettings("new", 1, today, today, 65, 95, Money(0), 250, 650,
        FilingStatus.SINGLE, "WI", ReferenceTaxPolicy.ID, 1, Money(0), "CLIFF",
        Money(0), Money(0), Money(0), emptyList(), HomeDisposition.KEEP)
}

private fun forecastPathCount(plan: PlanSettings?): Int {
    if (plan == null) return 10_000
    val age = java.time.Period.between(plan.birthDate, plan.referenceDate).years
    return if (plan.endAge - age > 60) 5_000 else 10_000
}

@Composable
private fun ResultForRoute(generation: Long, revision: Long, state: ForecastState, content: @Composable (ForecastResult) -> Unit) {
    if (state !is ForecastState.Ready) {
        ForecastMessagePage("Forecast changed or is recalculating", "Go back to the Forecast tab to review its current state before opening details.")
        return
    }
    val result = when (state) {
        is ForecastState.Ready -> state.result
        is ForecastState.Calculating -> state.previous
        is ForecastState.NeedsData -> state.previous
        is ForecastState.Failed -> state.previous
        is ForecastState.Cancelled -> state.previous
        ForecastState.Idle -> null
    }
    if (result == null) ForecastMessagePage("Restoring forecast", "Recomputing the plan before reopening this detail.")
    else if (result.generation != generation || result.planRevision != revision) ForecastMessagePage("Forecast changed", "This detail belongs to an older plan. Go back to review the current forecast.")
    else content(result)
}

@Composable
internal fun ForecastStatePage(state: ForecastState, plan: PlanSettings?, onRetry: () -> Unit, onCancel: () -> Unit, onRisk: (ForecastResult) -> Unit) {
    val previous = when (state) {
        is ForecastState.Calculating -> state.previous
        is ForecastState.NeedsData -> state.previous
        is ForecastState.Failed -> state.previous
        is ForecastState.Cancelled -> state.previous
        else -> null
    }
    val banner = when (state) {
        is ForecastState.Calculating -> "Calculating a new forecast. The result below is stale until this finishes."
        is ForecastState.NeedsData -> missingDataMessage(state.reason) + if (previous != null) " The previous result below is stale." else ""
        is ForecastState.Failed -> "The forecast could not be calculated. Saved inputs were not changed." + if (previous != null) " The previous result below is stale." else ""
        is ForecastState.Cancelled -> "Calculation stopped safely. No partial result was published." + if (previous != null) " The previous result below is stale." else ""
        ForecastState.Idle -> "Preparing the forecast."
        is ForecastState.Ready -> null
    }
    ForecastPage {
        if (banner != null) StatusCard(banner,
            primary = if (state is ForecastState.Calculating) ({ onCancel() }) else ({ onRetry() }),
            primaryLabel = if (state is ForecastState.Calculating) "Stop calculation" else "Recalculate")
        val result = (state as? ForecastState.Ready)?.result ?: previous
        if (result != null && plan != null) ForecastResultContent(result, plan, state !is ForecastState.Ready) { onRisk(result) }
        else if (state is ForecastState.NeedsData) EmptyForecastCard(missingDataMessage(state.reason))
    }
}

private fun missingDataMessage(reason: MissingForecastData) = when (reason) {
    MissingForecastData.PLAN -> "Plan settings are required before a forecast can run."
    MissingForecastData.BALANCE -> "Every included account and property needs an accepted value."
    MissingForecastData.EPIC_DATE -> "The Shareworks projection date must match the retirement birthday."
    MissingForecastData.EPIC_PROJECTION -> "The accepted workbook does not cover every required projection year."
    MissingForecastData.UNSUPPORTED_POLICY -> "The selected tax policy or state is not supported by this model."
    MissingForecastData.INVALID_INPUT -> "One or more plan values are outside the supported forecast range."
}

@Composable
private fun ForecastResultContent(result: ForecastResult, plan: PlanSettings, stale: Boolean, onRisk: () -> Unit) {
    EditorialHeading("Forecast", "The future has a shape")
    if (stale) Text("STALE RESULT", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    val retirementAge = plan.retirementAge.coerceIn(result.currentAge, result.endAge)
    val medianAtRetirement = result.percentile(ForecastChannel.TOTAL, retirementAge, 50.0)
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
        Column {
            Text("MODELED SUCCESS", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
            Text("${(result.successRate * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = RetirementHighlight)
        }
        Column {
            Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(medianAtRetirement.editorialFormat(), style = MaterialTheme.typography.headlineLarge)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("MODELED SUCCESS", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text("${(result.successRate * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = RetirementHighlight)
            }
            Column(Modifier.weight(1f)) {
                Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(medianAtRetirement.editorialFormat(), style = MaterialTheme.typography.headlineLarge)
            }
        }
    }
    val points = forecastChartPoints(result, plan.retirementAge)
    RetirementCard {
        Text("10TH–90TH PERCENTILE", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
        ForecastFanChart(points, plan.retirementAge, Modifier.height(250.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today\n${result.currentAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("Retire\n${plan.retirementAge}", color = RetirementGold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text("Plan\n${result.endAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
        Text("${String.format(java.util.Locale.US, "%,d", result.paths)} modeled paths · real dollars", color = RetirementTextSecondary)
    }
    RetirementFlowActionCard(
        eyebrow = "Forecast",
        title = "Explore risk",
        body = "See when modeled paths fall short and compare focused scenarios.",
        icon = Icons.Default.QueryStats,
        featured = true,
        onClick = onRisk,
    )
    RetirementCard {
        Text("Available at retirement", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Median modeled amounts at age ${plan.retirementAge.coerceIn(result.currentAge, result.endAge)}" +
            if (plan.retirementAge < result.currentAge) " (already retired; current modeled boundary)" else "", color = RetirementTextSecondary)
        availableAtRetirement(result, plan.retirementAge).forEach { LabelValue(it.label, it.amount.format()) }
        if (availableAtRetirement(result, plan.retirementAge).isEmpty()) Text("No modeled accounts are available at retirement.", color = RetirementTextSecondary)
    }
    RetirementCard {
        Text("Lifestyle spending", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(plan.annualSpending.format(), style = MaterialTheme.typography.headlineMedium)
        Text("per year in today's dollars, beginning at age ${plan.retirementAge}", color = RetirementTextSecondary)
    }
    RetirementCard {
        Text("Model limits", style = MaterialTheme.typography.titleMedium)
        Text("${result.taxPolicyId} · ${result.engineVersion}", color = RetirementTextSecondary)
        Text("Reference-policy approximation; this is planning output, not current-law tax or financial advice.", color = RetirementTextSecondary)
        if (!result.taxFundingConverged) Text("Tax funding residual: up to ${result.maximumTaxFundingResidual.format()} across ${result.taxResidualYears} path-years. Modeled success does not certify fully funded taxes.", color = RetirementHighlight)
        result.warnings.forEach { Text(it, color = RetirementTextSecondary) }
    }
}

@Composable
internal fun ForecastFanChart(points: List<ForecastChartPoint>, retirementAge: Int, modifier: Modifier = Modifier) {
    val description = forecastChartDescription(points, retirementAge)
    Canvas(modifier.fillMaxWidth().heightIn(min = 190.dp).semantics { contentDescription = description }) {
        if (points.size < 2) return@Canvas
        val maxValue = max(1L, points.maxOf { it.high.cents }).toFloat()
        fun x(index: Int) = size.width * index / (points.size - 1)
        fun y(cents: Long) = size.height - 8.dp.toPx() - (cents.coerceAtLeast(0).toFloat() / maxValue * (size.height - 20.dp.toPx()))
        fun smooth(path: Path, values: List<Offset>, move: Boolean) {
            if (move) path.moveTo(values.first().x, values.first().y) else path.lineTo(values.first().x, values.first().y)
            for (index in 0 until values.lastIndex) {
                val p0 = values[maxOf(0, index - 1)]
                val p1 = values[index]
                val p2 = values[index + 1]
                val p3 = values[minOf(values.lastIndex, index + 2)]
                path.cubicTo(
                    p1.x + (p2.x - p0.x) / 6f,
                    p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f,
                    p2.y - (p3.y - p1.y) / 6f,
                    p2.x,
                    p2.y,
                )
            }
        }
        fun values(selector: (ForecastChartPoint) -> Long) = points.mapIndexed { index, point -> Offset(x(index), y(selector(point))) }
        fun band(upper: List<Offset>, lower: List<Offset>) = Path().apply {
            smooth(this, upper, true)
            smooth(this, lower.reversed(), false)
            close()
        }
        val high = values { it.high.cents }
        val low = values { it.low.cents }
        val middle = values { it.middle.cents }
        val innerHigh = points.mapIndexed { index, point -> Offset(x(index), y((point.middle.cents + point.high.cents) / 2)) }
        val innerLow = points.mapIndexed { index, point -> Offset(x(index), y((point.middle.cents + point.low.cents) / 2)) }
        drawPath(band(high, low), RetirementPrimary.copy(alpha = .16f))
        drawPath(band(innerHigh, innerLow), RetirementBlue.copy(alpha = .20f))
        val median = Path().also { smooth(it, middle, true) }
        drawPath(median, RetirementHighlight, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        val marker = points.indexOfFirst { it.age == retirementAge }
        if (marker >= 0) {
            drawLine(RetirementGold, Offset(x(marker), 0f), Offset(x(marker), size.height), 1.5.dp.toPx())
            drawCircle(RetirementGold, 5.dp.toPx(), middle[marker])
        }
    }
}

@Composable
internal fun ForecastRiskPage(result: ForecastResult, plan: PlanSettings?, onScenario: (String) -> Unit) = ForecastPage {
    Text("FORECAST RISK", color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
    Text("What the model observed", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    val risk = summarizeRisk(result)
    RetirementCard {
        LabelValue("Paths with unmet spending", "${risk.failedPaths} of ${result.paths}")
        LabelValue("First observed failure age", risk.firstFailureAge?.toString() ?: "None")
        LabelValue("Accounts exhausted", risk.exhaustedPaths.toString())
        LabelValue("Access or rule limits", risk.accessLimitedPaths.toString())
        LabelValue("Largest unmet annual amount", risk.largestUnmet.format())
    }
    Text("These are factual path diagnostics, not a qualitative risk score or a prediction of what caused a market outcome.", color = RetirementTextSecondary)
    if (!result.taxFundingConverged) StatusCard("Tax funding was short by up to ${result.maximumTaxFundingResidual.format()} in ${result.taxResidualYears} path-years. This is separate from the failure counts above.")
    if (plan != null) {
        Text("Focused stress scenarios", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        scenarioSpecs(plan).forEach { scenario ->
            OutlinedButton(onClick = { onScenario(scenario.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.fillMaxWidth()) { Text(scenario.title); Text(scenario.change, color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ScenarioPage(repository: RetirementRepository, state: RetirementState, plan: PlanSettings, base: ForecastResult, scenarioId: String, onBack: () -> Unit) {
    val scenario = scenarioSpecs(plan).singleOrNull { it.id == scenarioId }
    if (scenario == null) { ForecastMessagePage("Scenario unavailable", "This scenario is not supported by the current app version."); return }
    val scope = rememberCoroutineScope()
    var preview by remember(base.generation, scenarioId) { mutableStateOf<ForecastResult?>(null) }
    var failure by remember(base.generation, scenarioId) { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(base.generation, scenarioId) {
        val capture = runCatching { ForecastScenario(base.generation, base.planRevision, scenario.delta).preview(state) }.getOrNull()
        if (capture is ForecastCapture.Ready) preview = runCatching {
            RetirementEngine().calculate(capture.input, base.paths, base.seed, base.stochastic)
        }.getOrElse { failure = true; null } else failure = true
    }
    ForecastPage {
        Text("SCENARIO", color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
        Text(scenario.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        RetirementCard { Text("Exact change", style = MaterialTheme.typography.titleMedium); Text(scenario.change, color = RetirementHighlight) }
        when {
            preview != null -> {
                val compared = preview!!
                RetirementCard {
                    LabelValue("Base modeled success", "${(base.successRate * 100).toInt()}%")
                    LabelValue("Scenario modeled success", "${(compared.successRate * 100).toInt()}%")
                    LabelValue("Exact difference", String.format(java.util.Locale.US, "%+.1f percentage points", (compared.successRate - base.successRate) * 100))
                }
                Text("The comparison reused the base forecast's random seed so the displayed difference reflects this setting change.", color = RetirementTextSecondary)
                Button(enabled = !applying, onClick = {
                    applying = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ForecastScenario(base.generation, base.planRevision, scenario.delta).apply(repository) }
                        applying = false
                        if (result is RetirementResult.Success) onBack()
                        else message = "The base plan changed. Nothing was applied; return to the current forecast and try again."
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (applying) "Applying…" else "Apply only this change") }
            }
            failure -> StatusCard("This scenario could not be calculated. The base plan was not changed.")
            else -> StatusCard("Calculating the scenario with the base forecast's seed.")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ScenarioComparisonPreview(base: ForecastResult, compared: ForecastResult, scenario: ScenarioSpec) = ForecastPage {
    Text("SCENARIO", color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
    Text(scenario.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    RetirementCard { Text("Exact change", style = MaterialTheme.typography.titleMedium); Text(scenario.change, color = RetirementHighlight) }
    RetirementCard {
        LabelValue("Base modeled success", "${(base.successRate * 100).toInt()}%")
        LabelValue("Scenario modeled success", "${(compared.successRate * 100).toInt()}%")
        LabelValue("Exact difference", String.format(java.util.Locale.US, "%+.1f percentage points", (compared.successRate - base.successRate) * 100))
    }
    Text("The comparison reused the base forecast's random seed so the displayed difference reflects this setting change.", color = RetirementTextSecondary)
    Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply only this change") }
}

@Composable
internal fun ForecastSettingsPage(plan: PlanSettings, message: String?, newPlan: Boolean = false, onSave: (PlanSettings) -> Unit) {
    val incomePlan = remember(plan) { editableIncomePlan(plan) }
    val saver = listSaver<ForecastSettingsDraft, String>(
        save = { d -> listOf(d.birthDate,d.referenceDate,d.retirementAge,d.endAge,d.annualSpending,d.inflationPercent,d.expectedReturnPercent,d.volatilityPercent,d.filingStatus.name,d.stateCode,d.acaHouseholdSize,d.acaAnnualPremium,d.acaRegime,d.preTaxContribution,d.rothContribution,d.taxableContribution,d.hsaContribution,d.medicalSpending,d.homeAppreciationPercent,d.homeDisposition.name) + d.incomeAmounts.keys.sorted().flatMap { listOf(it, d.incomeAmounts.getValue(it), d.incomeStartAges.getValue(it), d.incomeEndAges.getValue(it)) } },
        restore = { v ->
            val rows = v.drop(20).chunked(4)
            ForecastSettingsDraft(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],FilingStatus.valueOf(v[8]),v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17],v[18],HomeDisposition.valueOf(v[19]),
                rows.associate { it[0] to it[1] }, rows.associate { it[0] to it[2] }, rows.associate { it[0] to it[3] })
        },
    )
    var draft by rememberSaveable(plan.revision, newPlan, stateSaver = privateDraftSaver("forecast:${plan.id}:${plan.revision}", saver)) {
        mutableStateOf(if (newPlan) ForecastSettingsDraft.blank(incomePlan) else ForecastSettingsDraft.from(incomePlan))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    ForecastPage {
        Text("PLAN INPUTS", color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
        Text("Forecast settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text("Save once to validate the whole form and trigger one coherent recomputation.", color = RetirementTextSecondary)
        SettingsSection("Timing") {
            DraftField("Birth date (YYYY-MM-DD)", draft.birthDate) { draft = draft.copy(birthDate = it) }
            DraftField("As-of date (YYYY-MM-DD)", draft.referenceDate) { draft = draft.copy(referenceDate = it) }
            DraftField("Retirement age", draft.retirementAge) { draft = draft.copy(retirementAge = it) }
            DraftField("Plan through age", draft.endAge) { draft = draft.copy(endAge = it) }
        }
        SettingsSection("Lifestyle and market") {
            DraftField("Annual lifestyle spending", draft.annualSpending) { draft = draft.copy(annualSpending = it) }
            DraftField("Inflation (%)", draft.inflationPercent) { draft = draft.copy(inflationPercent = it) }
            DraftField("Expected equity return (%)", draft.expectedReturnPercent) { draft = draft.copy(expectedReturnPercent = it) }
            DraftField("Market volatility scale (%)", draft.volatilityPercent) { draft = draft.copy(volatilityPercent = it) }
        }
        SettingsSection("Tax and health coverage") {
            EnumSelector("Filing status", draft.filingStatus, FilingStatus.entries) { draft = draft.copy(filingStatus = it) }
            DraftField("State code", draft.stateCode) { draft = draft.copy(stateCode = it) }
            DraftField("ACA household size", draft.acaHouseholdSize) { draft = draft.copy(acaHouseholdSize = it) }
            DraftField("Annual benchmark premium", draft.acaAnnualPremium) { draft = draft.copy(acaAnnualPremium = it) }
            EnumSelector("ACA model", draft.acaRegime, listOf("CLIFF", "EXTENDED")) { draft = draft.copy(acaRegime = it) }
            Text("Policy: ${plan.taxPolicyId}. Reference approximation, not current-law tax advice.", color = RetirementTextSecondary)
        }
        SettingsSection("Annual contributions") {
            DraftField("Pre-tax", draft.preTaxContribution) { draft = draft.copy(preTaxContribution = it) }
            DraftField("Roth", draft.rothContribution) { draft = draft.copy(rothContribution = it) }
            DraftField("Taxable", draft.taxableContribution) { draft = draft.copy(taxableContribution = it) }
        }
        SettingsSection("Social Security and pension income") {
            incomePlan.incomeStreams.forEach { stream ->
                Text(stream.taxKind.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                DraftField("Annual amount", draft.incomeAmounts[stream.id].orEmpty()) {
                    draft = draft.copy(incomeAmounts = draft.incomeAmounts + (stream.id to it))
                }
                DraftField("Start age", draft.incomeStartAges[stream.id].orEmpty()) {
                    draft = draft.copy(incomeStartAges = draft.incomeStartAges + (stream.id to it))
                }
                DraftField("End age", draft.incomeEndAges[stream.id].orEmpty()) {
                    draft = draft.copy(incomeEndAges = draft.incomeEndAges + (stream.id to it))
                }
            }
            Text("Income type remains fixed; amount and modeled timing are local plan assumptions.", color = RetirementTextSecondary)
        }
        SettingsSection("Home") {
            DraftField("Real home appreciation (%)", draft.homeAppreciationPercent) { draft = draft.copy(homeAppreciationPercent = it) }
            EnumSelector("At retirement", draft.homeDisposition, HomeDisposition.entries) { draft = draft.copy(homeDisposition = it) }
            Text("Epic position, growth, tax, sale, and projection values are workbook-owned and cannot be edited here.", color = RetirementTextSecondary)
        }
        (error ?: message)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            draft.validated(incomePlan).fold(onSuccess = { error = null; onSave(it) }, onFailure = { error = "Review dates, money, percentages, ages, state, and household size." })
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save and recalculate") }
    }
}

@Composable private fun DraftField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, onValueChange, singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

@Composable private fun <T> EnumSelector(label: String, value: T, values: List<T>, onValue: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: ${value.toString().lowercase().replace('_',' ')}") }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { option -> DropdownMenuItem({ Text(option.toString().lowercase().replace('_',' ')) }, { expanded = false; onValue(option) }) } }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) = RetirementCard {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }); content()
}

@Composable private fun ForecastPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable private fun RetirementCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, RetirementBorder), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(RetirementSurfaceRaised.copy(alpha = .82f), RetirementSurface))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable private fun LabelValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = RetirementTextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun StatusCard(text: String, primary: (() -> Unit)? = null, primaryLabel: String = "Retry") = RetirementCard {
    Text(text, color = RetirementTextSecondary)
    if (primary != null) OutlinedButton(onClick = primary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(primaryLabel) }
}

@Composable private fun EmptyForecastCard(text: String) = RetirementCard { Text("Forecast needs attention", style = MaterialTheme.typography.titleLarge); Text(text, color = RetirementTextSecondary) }

@Composable private fun ForecastMessagePage(title: String, body: String) = ForecastPage { Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() }); Text(body, color = RetirementTextSecondary) }

@Composable
private fun ForecastPreviewRouter(route: AppRoute, state: RetirementState, onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val plan = state.planSettings.maxByOrNull { it.revision }
    if (route == AppRoute.RetirementForecastSettings && plan != null) ForecastSettingsPage(plan, null) {}
    else ForecastMessagePage("Forecast preview", "Live modeled results are shown in the dedicated Forecast screenshot cases.")
}
