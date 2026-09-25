package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.draftingroom5.*
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun RetirementForecastHost(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    previewState: RetirementState? = null,
) {
    val context = LocalContext.current
    var repository by remember { mutableStateOf<RetirementRepository?>(null) }
    var forecast by remember { mutableStateOf<DailyForecastService?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(previewState) {
        if (previewState == null) {
            val runtime = withContext(Dispatchers.IO) { runCatching { RetirementProviders.get(context) }.getOrNull() }
            repository = runtime?.repository
            forecast = runtime?.forecast
            loadFailed = repository == null
        }
    }
    when {
        previewState != null -> ForecastPreviewRouter(route, previewState, onNavigate, onBack)
        repository != null && forecast != null -> LiveForecastRouter(route, repository!!, forecast!!, onNavigate, onBack)
        else -> ForecastMessagePage(if (loadFailed) "Forecast unavailable" else "Loading forecast",
            if (loadFailed) "Unlock the phone and try again. Saved financial data was not changed." else "Opening the private planning model.")
    }
}

@Composable
private fun LiveForecastRouter(route: AppRoute, repository: RetirementRepository, forecast: DailyForecastService, onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<RetirementState?>(null) }
    var saveMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val forecastState by forecast.state.collectAsState()
    LaunchedEffect(repository) {
        snapshot = withContext(Dispatchers.IO) { repository.upgradePlanningAssumptions() }
        val plan = snapshot!!.planSettings.maxByOrNull { it.revision }
        forecast.ensure(paths = forecastPathCount(plan))
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
            onRetry = { forecast.restart(paths = forecastPathCount(plan)) },
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
                    if (result is RetirementResult.Success) {
                        snapshot = result.value
                        saveMessage = null
                        forecast.restart(paths = forecastPathCount(result.value.planSettings.maxByOrNull { it.revision }))
                        onBack()
                    }
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
    return PlanSettings("new", 1, today, today, 45, 95, Money(0), 250, 650,
        FilingStatus.MARRIED_FILING_JOINTLY, "WI", PlanningTaxPolicy.ID, 2, Money(0), "CLIFF",
        Money(0), Money(0), Money(0), emptyList(), HomeDisposition.KEEP, spouseBirthYear = 1988)
}

private fun forecastPathCount(plan: PlanSettings?): Int {
    if (plan == null) return 10_000
    val age = java.time.Period.between(plan.birthDate, java.time.LocalDate.now()).years
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
internal fun ForecastStatePage(
    state: ForecastState,
    plan: PlanSettings?,
    onRetry: () -> Unit,
    onRisk: (ForecastResult) -> Unit,
    animateCalculation: Boolean = true,
) {
    if (state is ForecastState.Calculating || state is ForecastState.Idle) {
        ForecastCalculationScreen(animateCalculation)
        return
    }
    val previous = when (state) {
        is ForecastState.NeedsData -> state.previous
        is ForecastState.Failed -> state.previous
        is ForecastState.Cancelled -> state.previous
        else -> null
    }
    val banner = when (state) {
        is ForecastState.NeedsData -> missingDataMessage(state.reason) + if (previous != null) " The previous result below is stale." else ""
        is ForecastState.Failed -> "The forecast could not be calculated. Saved inputs were not changed." + if (previous != null) " The previous result below is stale." else ""
        is ForecastState.Cancelled -> "Calculation stopped safely. No partial result was published." + if (previous != null) " The previous result below is stale." else ""
        is ForecastState.Ready -> null
        else -> null
    }
    ForecastPage(hero = true) {
        if (banner != null) StatusCard(banner, primary = { onRetry() }, primaryLabel = "Recalculate")
        val result = (state as? ForecastState.Ready)?.result ?: previous
        if (result != null && plan != null) ForecastResultContent(result, plan, state !is ForecastState.Ready) { onRisk(result) }
        else if (state is ForecastState.NeedsData) EmptyForecastCard(missingDataMessage(state.reason))
    }
}

private fun missingDataMessage(reason: MissingForecastData) = when (reason) {
    MissingForecastData.PLAN -> "Plan settings are required before a forecast can run."
    MissingForecastData.BALANCE -> "Every included account and property needs an accepted value."
    MissingForecastData.EPIC_PROJECTION -> "The accepted workbook does not cover every required projection year."
    MissingForecastData.UNSUPPORTED_POLICY -> "The selected tax policy or state is not supported by this model."
    MissingForecastData.INVALID_INPUT -> "One or more plan values are outside the supported forecast range."
}

private enum class ProjectionSection(val label: String) { BALANCE("Balance"), CASH_FLOW("Cash flow"), RISK("Risk") }

@Composable
private fun ForecastResultContent(result: ForecastResult, plan: PlanSettings, stale: Boolean, onRisk: () -> Unit) {
    Spacer(Modifier.width(52.dp).height(3.dp).background(RetirementGold))
    EditorialHeading("Projections", "Plan projection")
    if (stale) Text("STALE RESULT", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    var section by rememberSaveable { mutableStateOf(ProjectionSection.BALANCE) }
    ProjectionSectionPicker(section) { section = it }
    val retirementAge = plan.retirementAge.coerceIn(result.currentAge, result.endAge)
    val medianAtRetirement = result.percentile(ForecastChannel.TOTAL, retirementAge, 50.0)
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
        Column {
            Text("${(result.successRate * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = RetirementHighlight)
            Text("MODELED SUCCESS", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Column {
            Text(medianAtRetirement.editorialFormat(), style = MaterialTheme.typography.headlineLarge)
            Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${(result.successRate * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = RetirementHighlight)
                Text("MODELED SUCCESS", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(medianAtRetirement.editorialFormat(), style = MaterialTheme.typography.headlineLarge)
                Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    when (section) {
        ProjectionSection.BALANCE -> BalanceProjection(result, plan, retirementAge)
        ProjectionSection.CASH_FLOW -> CashFlowProjection(result, retirementAge)
        ProjectionSection.RISK -> RiskProjection(result)
    }
}

@Composable
private fun ProjectionSectionPicker(selected: ProjectionSection, onSelect: (ProjectionSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).border(1.dp, RetirementBorder, RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProjectionSection.entries.forEach { item ->
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (item == selected) RetirementPrimary.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(18.dp))
                    .then(if (item == selected) Modifier.border(1.dp, RetirementHighlight, RoundedCornerShape(18.dp)) else Modifier)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(item.label, color = if (item == selected) RetirementHighlight else RetirementText, fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun BalanceProjection(result: ForecastResult, plan: PlanSettings, retirementAge: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val points = forecastChartPoints(result, plan.retirementAge)
        ForecastFanChart(points, plan.retirementAge, Modifier.height(270.dp), editorialGuides = true, interactive = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today\n${result.currentAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("Plan\n${result.endAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        }
        val low = result.percentile(ForecastChannel.TOTAL, retirementAge, 10.0)
        val middle = result.percentile(ForecastChannel.TOTAL, retirementAge, 50.0)
        val high = result.percentile(ForecastChannel.TOTAL, retirementAge, 90.0)
        ProjectionValueRow("10th percentile", low.editorialFormat())
        ProjectionValueRow("Median", middle.editorialFormat())
        ProjectionValueRow("90th percentile", high.editorialFormat())
        RiskOutlook(result)
        Text("${String.format(java.util.Locale.US, "%,d", result.paths)} modeled paths · today's dollars", color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CashFlowProjection(result: ForecastResult, retirementAge: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Assets at retirement", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        availableAtRetirement(result, retirementAge).forEach { ProjectionValueRow(it.label, it.amount.editorialFormat()) }
        ProjectionValueRow("Annual spending at retirement", result.annualSpending(retirementAge).editorialFormat())
        Text("Home equity is included above but cannot fund spending while you keep the home. Mortgage principal/interest stops at payoff. All values use today's dollars.", color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RiskProjection(result: ForecastResult) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RiskOutlook(result)
        if (!result.taxFundingConverged) {
            Text("Unfunded taxes up to ${result.maximumTaxFundingResidual.format()} across ${result.taxResidualYears} path-years; these count as failures.", color = RetirementGold)
        }
        result.warnings.forEach { Text(it, color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun RiskOutlook(result: ForecastResult) {
    val risk = summarizeRisk(result)
    Text("Risk outlook", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
    ProjectionValueRow("Paths fully funded", "${result.paths - risk.failedPaths} of ${result.paths}")
    ProjectionValueRow("First observed shortfall", risk.firstFailureAge?.let { "Age $it" } ?: "None")
}

@Composable
private fun ProjectionValueRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = RetirementTextSecondary)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = RetirementBorder)
    }
}

@Composable
internal fun ForecastFanChart(
    points: List<ForecastChartPoint>,
    retirementAge: Int,
    modifier: Modifier = Modifier,
    editorialGuides: Boolean = false,
    interactive: Boolean = false,
) {
    val description = forecastChartDescription(points, retirementAge)
    if (points.size < 2) {
        Canvas(modifier.fillMaxWidth().heightIn(min = 190.dp).semantics { contentDescription = description }) {}
        return
    }
    val retirementIndex = points.indexOfFirst { it.age == retirementAge }.coerceAtLeast(0)
    var selectedIndex by remember(points, retirementAge) { mutableStateOf(retirementIndex) }
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 190.dp).semantics { contentDescription = description }) {
        val minValue = points.minOf { it.low.cents }.coerceAtLeast(0L).toFloat()
        val maxValue = max(minValue + 1f, points.maxOf { it.high.cents }.toFloat())
        val range = maxValue - minValue
        val firstAge = points.first().age
        val ageSpan = maxOf(1, points.last().age - firstAge)
        fun selectAt(x: Float, width: Float) {
            selectedIndex = ((x.coerceIn(0f, width) / width) * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
        }
        val gestureModifier = if (interactive) {
            Modifier
                .pointerInput(points) { detectTapGestures { selectAt(it.x, size.width.toFloat()) } }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { selectAt(it.x, size.width.toFloat()) },
                        onHorizontalDrag = { change, _ -> selectAt(change.position.x, size.width.toFloat()) },
                    )
                }
        } else Modifier
        Canvas(Modifier.matchParentSize().then(gestureModifier)) {
            fun x(index: Int) = size.width * (points[index].age - firstAge) / ageSpan.toFloat()
            fun y(cents: Long) = size.height - 14.dp.toPx() -
                ((cents.coerceAtLeast(0).toFloat() - minValue) / range * (size.height - 34.dp.toPx()))
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
        drawPath(band(high, low), RetirementPrimary.copy(alpha = .25f))
        drawPath(band(innerHigh, innerLow), RetirementBlue.copy(alpha = .28f))
        val median = Path().also { smooth(it, middle, true) }
        drawPath(median, RetirementHighlight, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawLine(
            RetirementTextSecondary.copy(alpha = .22f),
            Offset(0f, size.height - 2.dp.toPx()),
            Offset(size.width, size.height - 2.dp.toPx()),
            1.dp.toPx(),
        )
        val marker = if (interactive) selectedIndex else retirementIndex
        if (marker >= 0) {
            val markerPoint = middle[marker]
            if (editorialGuides) {
                val baseline = size.height - 2.dp.toPx()
                val guideStroke = 1.5.dp.toPx()
                val dotted = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 6.dp.toPx()))
                drawLine(
                    RetirementGold,
                    Offset(markerPoint.x, markerPoint.y + 8.dp.toPx()),
                    Offset(markerPoint.x, baseline),
                    guideStroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = dotted,
                )
                repeat(6) { tick ->
                    val tickX = size.width * tick / 5f
                    drawLine(
                        RetirementTextSecondary.copy(alpha = .34f),
                        Offset(tickX, baseline - 3.dp.toPx()),
                        Offset(tickX, baseline + 3.dp.toPx()),
                        1.dp.toPx(),
                    )
                }
            } else {
                drawLine(RetirementGold, Offset(x(marker), 0f), Offset(x(marker), size.height), 1.5.dp.toPx())
            }
            drawCircle(RetirementGold, 5.dp.toPx(), markerPoint)
        }
        }
        if (interactive) {
            val selected = points[selectedIndex]
            val xFraction = (selected.age - firstAge) / ageSpan.toFloat()
            val yFraction = ((selected.middle.cents.coerceAtLeast(0L).toFloat() - minValue) / range).coerceIn(0f, 1f)
            val markerX = maxWidth * xFraction
            val markerY = maxHeight - 14.dp - (maxHeight - 34.dp) * yFraction
            val tooltipWidth = if (LocalDensity.current.fontScale >= 1.5f) 188.dp else 150.dp
            val tooltipX = (markerX + 10.dp).coerceAtMost((maxWidth - tooltipWidth).coerceAtLeast(0.dp))
            val tooltipY = (markerY - 82.dp).coerceIn(0.dp, (maxHeight - 96.dp).coerceAtLeast(0.dp))
            Column(
                Modifier.offset(x = tooltipX, y = tooltipY).width(tooltipWidth)
                    .background(RetirementBackgroundDeep.copy(alpha = .92f), RoundedCornerShape(10.dp))
                    .border(1.dp, RetirementBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text("Age ${selected.age}", color = RetirementGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Median", color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(selected.middle.chartFormat(), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Range", color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("${selected.low.chartFormat()}–${selected.high.chartFormat()}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
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
    if (!result.taxFundingConverged) StatusCard("Unfunded taxes reached ${result.maximumTaxFundingResidual.format()} in ${result.taxResidualYears} path-years. These are included in the failure counts above.")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForecastSettingsPage(
    plan: PlanSettings,
    message: String?,
    newPlan: Boolean = false,
    onSave: (PlanSettings) -> Unit,
) {
    val incomePlan = remember(plan) { editableIncomePlan(plan) }
    val saver = listSaver<ForecastSettingsDraft, String>(
        save = { d -> listOf(d.birthDate,d.retirementAge,d.endAge,d.annualSpending,d.inflationPercent,d.expectedReturnPercent,d.volatilityPercent,d.filingStatus.name,d.stateCode,d.acaHouseholdSize,d.acaAnnualPremium,d.acaRegime,d.preTaxContribution,d.rothContribution,d.taxableContribution,d.hsaContribution,d.medicalSpending,d.homeAppreciationPercent,d.homeDisposition.name,d.spouseBirthYear) + d.incomeAmounts.keys.sorted().flatMap { listOf(it, d.incomeAmounts.getValue(it), d.incomeStartAges.getValue(it), d.incomeEndAges.getValue(it)) } },
        restore = { v ->
            val rows = v.drop(20).chunked(4)
            ForecastSettingsDraft(v[0],v[1],v[2],v[3],v[4],v[5],v[6],FilingStatus.valueOf(v[7]),v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17],HomeDisposition.valueOf(v[18]),
                rows.associate { it[0] to it[1] }, rows.associate { it[0] to it[2] }, rows.associate { it[0] to it[3] }, v[19])
        },
    )
    var draft by rememberSaveable(plan.revision, newPlan, stateSaver = privateDraftSaver("forecast:${plan.id}:${plan.revision}", saver)) {
        mutableStateOf(if (newPlan) ForecastSettingsDraft.blank(incomePlan) else ForecastSettingsDraft.from(incomePlan))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    ForecastPage {
        Text("PLAN INPUTS", color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
        Text("Forecast settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text("Set the assumptions that shape your projection. Saving recalculates the plan once.", color = RetirementTextSecondary)
        SettingsSection("Timing") {
            DateSetting("Birth date", draft.birthDate, plan.birthDate) { draft = draft.copy(birthDate = it) }
            IntegerStepper("Spouse birth year (annual age estimate)", draft.spouseBirthYear, 1900, LocalDate.now().year, 1, 1988) { draft = draft.copy(spouseBirthYear = it) }
            IntegerStepper("Retirement age", draft.retirementAge, 18, 120, 1, plan.retirementAge) { draft = draft.copy(retirementAge = it) }
            IntegerStepper("Plan through age", draft.endAge, 18, 130, 1, plan.endAge) { draft = draft.copy(endAge = it) }
        }
        SettingsSection("Lifestyle and market") {
            MoneySetting("Annual household spending (before subsidies and income tax)", draft.annualSpending) { draft = draft.copy(annualSpending = it) }
            Text("Includes gross insurance premiums for both spouses and mortgage principal/interest. Income taxes are calculated separately.", color = RetirementTextSecondary)
            PercentStepper("Inflation", draft.inflationPercent, -10.0, 100.0, .25) { draft = draft.copy(inflationPercent = it) }
            PercentStepper("Expected real equity return", draft.expectedReturnPercent, -100.0, 300.0, .25) { draft = draft.copy(expectedReturnPercent = it) }
            PercentStepper("Market volatility scale", draft.volatilityPercent, 0.0, 300.0, 5.0) { draft = draft.copy(volatilityPercent = it) }
        }
        SettingsSection("Tax and health coverage") {
            EnumSelector("Filing status", draft.filingStatus, FilingStatus.entries) { draft = draft.copy(filingStatus = it) }
            EnumSelector("State", draft.stateCode, PlanningTaxPolicy.supportedStates.sorted()) { draft = draft.copy(stateCode = it) }
            IntegerStepper("ACA household size", draft.acaHouseholdSize, 1, 20, 1) { draft = draft.copy(acaHouseholdSize = it) }
            MoneySetting("Full household annual benchmark premium (already in spending)", draft.acaAnnualPremium) { draft = draft.copy(acaAnnualPremium = it) }
            MoneySetting("Annual medical spending", draft.medicalSpending) { draft = draft.copy(medicalSpending = it) }
            Text("ACA: Wisconsin Marketplace, individual Medicare transitions; equal premium shares assumed. No employer coverage.", color = RetirementTextSecondary)
            Text("2026 federal/Wisconsin planning rules; future indexed limits follow inflation.", color = RetirementTextSecondary)
        }
        SettingsSection("Annual contributions") {
            MoneySetting("Pre-tax", draft.preTaxContribution) { draft = draft.copy(preTaxContribution = it) }
            MoneySetting("Roth", draft.rothContribution) { draft = draft.copy(rothContribution = it) }
            MoneySetting("Taxable", draft.taxableContribution) { draft = draft.copy(taxableContribution = it) }
            MoneySetting("HSA", draft.hsaContribution) { draft = draft.copy(hsaContribution = it) }
        }
        SettingsSection("Social Security income") {
            incomePlan.incomeStreams.forEach { stream ->
                Text("${if (stream.owner == Owner.SPOUSE) "Spouse" else "Your"} ${stream.taxKind.name.lowercase().replace('_',' ')}", fontWeight = FontWeight.SemiBold)
                MoneySetting("Annual amount (today's dollars)", draft.incomeAmounts[stream.id].orEmpty()) {
                    draft = draft.copy(incomeAmounts = draft.incomeAmounts + (stream.id to it))
                }
                IntegerStepper("Start age", draft.incomeStartAges[stream.id].orEmpty(), 0, 130, 1) {
                    draft = draft.copy(incomeStartAges = draft.incomeStartAges + (stream.id to it))
                }
                IntegerStepper("End age", draft.incomeEndAges[stream.id].orEmpty(), 0, 130, 1) {
                    draft = draft.copy(incomeEndAges = draft.incomeEndAges + (stream.id to it))
                }
            }
            Text("Amounts use today's dollars and estimates assuming work stops at retirement. Ages apply to each recipient. Age 67 is a placeholder until you choose; no benefit is invented for a zero amount.", color = RetirementTextSecondary)
        }
        SettingsSection("Home") {
            PercentStepper("Real home appreciation", draft.homeAppreciationPercent, -50.0, 100.0, .25) { draft = draft.copy(homeAppreciationPercent = it) }
            EnumSelector("At retirement", draft.homeDisposition, HomeDisposition.entries) { draft = draft.copy(homeDisposition = it) }
            Text("Saved mortgage principal/interest stops at payoff. Property tax, insurance and maintenance remain in spending. Home equity remains in total wealth.", color = RetirementTextSecondary)
            Text("Epic is sold at retirement using workbook net proceeds and at most 1% uncertainty. Proceeds and surplus immediately enter 60% stock / 40% bond index funds. Spouse investments are excluded; existing taxable investments assume zero basis.", color = RetirementTextSecondary)
        }
        (error ?: message)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            draft.validated(incomePlan).fold(onSuccess = { error = null; onSave(it) }, onFailure = { error = "Review dates, money, percentages, ages, state, and household size." })
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save and recalculate") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateSetting(label: String, value: String, initialIfBlank: LocalDate, onValue: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val parsed = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "$label, ${parsed ?: "not set"}" },
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(parsed?.let { "${it.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${it.dayOfMonth}, ${it.year}" } ?: "Choose date", Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
    if (open) {
        val initial = parsed ?: initialIfBlank
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onValue(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

@Composable private fun MoneySetting(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value,
            { onValueChange(it.take(24)) },
            singleLine = true,
            suffix = { Text("USD", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

@Composable private fun IntegerStepper(
    label: String,
    value: String,
    minimum: Int,
    maximum: Int,
    step: Int,
    initialIfBlank: Int = minimum,
    onValue: (String) -> Unit,
) {
    val current = value.toIntOrNull()
    SettingControl(label) {
        IconButton(
            onClick = { onValue((current?.minus(step) ?: initialIfBlank).coerceIn(minimum, maximum).toString()) },
            enabled = current == null || current > minimum,
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease $label") }
        Text(current?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onValue((current?.plus(step) ?: initialIfBlank).coerceIn(minimum, maximum).toString()) },
            enabled = current == null || current < maximum,
        ) { Icon(Icons.Default.Add, contentDescription = "Increase $label") }
    }
}

@Composable private fun PercentStepper(label: String, value: String, minimum: Double, maximum: Double, step: Double, onValue: (String) -> Unit) {
    val current = value.toDoubleOrNull()
    fun adjusted(delta: Double): String = BigDecimal.valueOf(((current ?: minimum) + delta).coerceIn(minimum, maximum))
        .setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    SettingControl(label) {
        IconButton(onClick = { onValue(adjusted(-step)) }, enabled = current == null || current > minimum) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text(current?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() + "%" } ?: "—", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = { onValue(adjusted(step)) }, enabled = current == null || current < maximum) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable private fun SettingControl(label: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).border(1.dp, RetirementBorder, RoundedCornerShape(16.dp)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable private fun <T> EnumSelector(label: String, value: T, values: List<T>, onValue: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(value.toString().lowercase().replace('_',' ').replaceFirstChar(Char::uppercase), Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded, { expanded = false }) {
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.toString().lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)) },
                        onClick = { expanded = false; onValue(option) },
                    )
                }
            }
        }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) = RetirementCard {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }); content()
}

@Composable private fun ForecastPage(hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(RetirementSurfaceRaised.copy(alpha = .48f), RetirementBackground, RetirementBackgroundDeep),
                radius = 980f,
            ),
        ),
    ) {
        if (hero) {
            Image(
                painter = painterResource(dev.draftingroom5.R.drawable.finance_planning_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = .52f,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(.78f).height(520.dp),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
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
    if (route == AppRoute.RetirementForecastSettings && plan != null) {
        ForecastSettingsPage(plan, null) {}
    } else if (route == AppRoute.RetirementForecast && plan != null) {
        val preview = remember(state.generation, plan.revision) {
            when (val capture = ForecastInputs.capture(state)) {
                is ForecastCapture.Ready -> runCatching {
                    ForecastState.Ready(runBlocking { RetirementEngine().calculate(capture.input, 20, 27L, true) })
                }.getOrElse { ForecastState.Failed(null) }
                is ForecastCapture.NeedsData -> ForecastState.NeedsData(capture.reason, null)
            }
        }
        ForecastStatePage(preview, plan, {}, {})
    } else {
        ForecastMessagePage("Projection unavailable", "Add plan timing and balances to calculate projections.")
    }
}
