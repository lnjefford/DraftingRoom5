package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.draftingroom5.AppRoute
import dev.draftingroom5.RetirementBorder
import dev.draftingroom5.RetirementBackground
import dev.draftingroom5.RetirementBackgroundDeep
import dev.draftingroom5.RetirementBlue
import dev.draftingroom5.RetirementHighlight
import dev.draftingroom5.RetirementGold
import dev.draftingroom5.RetirementPrimary
import dev.draftingroom5.RetirementSurface
import dev.draftingroom5.RetirementSurfaceRaised
import dev.draftingroom5.RetirementTextSecondary
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.domain.PlanSettings
import dev.draftingroom5.retirement.forecast.ForecastCapture
import dev.draftingroom5.retirement.forecast.ForecastChannel
import dev.draftingroom5.retirement.forecast.ForecastCoordinator
import dev.draftingroom5.retirement.forecast.ForecastInputs
import dev.draftingroom5.retirement.forecast.ForecastResult
import dev.draftingroom5.retirement.forecast.ForecastState
import dev.draftingroom5.retirement.forecast.RetirementEngine
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Period

internal enum class IntegratedLoadState { LOADING, READY, ERROR }

@Composable
internal fun RetirementIntegratedHost(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    previewState: RetirementState? = null,
) {
    val context = LocalContext.current
    var repository by remember { mutableStateOf<RetirementRepository?>(null) }
    var state by remember { mutableStateOf(previewState ?: RetirementState()) }
    var loadState by remember { mutableStateOf(if (previewState == null) IntegratedLoadState.LOADING else IntegratedLoadState.READY) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previewState) {
        if (previewState != null) {
            state = previewState
            loadState = IntegratedLoadState.READY
        } else {
            val runtime = runCatching { withContext(Dispatchers.IO) { RetirementProviders.get(context) } }.getOrNull()
            if (runtime == null) loadState = IntegratedLoadState.ERROR else {
                repository = runtime.repository
                runCatching { withContext(Dispatchers.IO) { runtime.repository.load() } }
                    .onSuccess { state = it; loadState = IntegratedLoadState.READY }
                    .onFailure { loadState = IntegratedLoadState.ERROR }
            }
        }
    }
    LaunchedEffect(repository) {
        repository?.changes?.collect {
            runCatching { withContext(Dispatchers.IO) { repository!!.load() } }
                .onSuccess { state = it; loadState = IntegratedLoadState.READY }
                .onFailure { loadState = IntegratedLoadState.ERROR }
        }
    }
    RetirementIntegratedPage(route, state, loadState, message, onNavigate, repository, previewState != null)
}

@Composable
internal fun RetirementIntegratedPage(
    route: AppRoute,
    state: RetirementState,
    loadState: IntegratedLoadState,
    message: String? = null,
    onNavigate: (AppRoute) -> Unit = {},
    repository: RetirementRepository? = null,
    preview: Boolean = true,
) {
    when (loadState) {
        IntegratedLoadState.LOADING -> IntegratedMessage("Loading retirement", "Reading the latest accepted accounts, workbook, property, and plan.")
        IntegratedLoadState.ERROR -> IntegratedMessage("Retirement data unavailable", "Unlock the phone and try again. Saved values were not changed.", error = true)
        IntegratedLoadState.READY -> when (route) {
            AppRoute.RetirementOverview -> OverviewPage(state, repository, preview, onNavigate)
            AppRoute.RetirementAssets -> AssetsPage(state, onNavigate)
            AppRoute.RetirementLibrary -> LibraryPage(message)
            else -> error("Not an integrated Retirement route")
        }
    }
}

@Composable
private fun OverviewPage(
    state: RetirementState,
    repository: RetirementRepository?,
    preview: Boolean,
    onNavigate: (AppRoute) -> Unit,
) {
    val summary = integratedAssetSummary(state)
    val health = retirementDataHealth(state, if (preview) Instant.parse("2026-09-21T12:00:00Z") else Instant.now())
    val plan = state.planSettings.maxByOrNull { it.revision }
    val forecastState = overviewForecastState(state, repository, plan, preview)
    FinancePage {
        Spacer(Modifier.width(52.dp).height(3.dp).background(RetirementGold))
        Text(
            "Financial overview",
            style = if (LocalDensity.current.fontScale >= 1.5f) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (summary.tracked.cents == 0L) "$0" else summary.tracked.wholeDollarEditorialFormat(),
            color = RetirementHighlight,
            style = if (LocalDensity.current.fontScale >= 1.5f) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { contentDescription = "Tracked total, ${summary.tracked.format()}" },
        )
        if (summary.tracked.cents == 0L) {
            Text("Add assets to build your outlook", color = RetirementTextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
        if (plan != null) {
            OverviewForecastChart(forecastState, plan) { onNavigate(AppRoute.RetirementForecast) }
        } else {
            Text("Set your plan timing to see a modeled forecast range.", color = RetirementTextSecondary)
        }
        TargetCard(
            plan = plan,
            liveState = forecastState,
            onOpenForecast = { onNavigate(AppRoute.RetirementForecast) },
            onOpenSettings = { onNavigate(AppRoute.RetirementForecastSettings) },
        )
        if (health.kind != DataHealthKind.HEALTHY) DataHealthCard(health) { health.route?.let(onNavigate) }
    }
}

@Composable
private fun overviewForecastState(
    state: RetirementState,
    repository: RetirementRepository?,
    plan: PlanSettings?,
    preview: Boolean,
): ForecastState {
    if (plan == null) return ForecastState.Idle
    if (repository != null) {
        val scope = rememberCoroutineScope()
        val coordinator = remember(repository) { ForecastCoordinator(repository, scope) }
        val observed by coordinator.state.collectAsState()
        DisposableEffect(coordinator) { onDispose { coordinator.close() } }
        LaunchedEffect(state.generation, plan.revision) { coordinator.restart(paths = overviewForecastPathCount(plan)) }
        return observed
    }
    if (preview) {
        return remember(state.generation, plan.revision) {
            when (val capture = ForecastInputs.capture(state)) {
                is ForecastCapture.NeedsData -> ForecastState.NeedsData(capture.reason, null)
                is ForecastCapture.Ready -> runCatching {
                    ForecastState.Ready(runBlocking { RetirementEngine().calculate(capture.input, 20, 27L, true) })
                }.getOrElse { ForecastState.Failed(null) }
            }
        }
    }
    return ForecastState.Idle
}

private fun ForecastState.displayedResult(): ForecastResult? = when (this) {
    is ForecastState.Ready -> result
    is ForecastState.Calculating -> previous
    is ForecastState.NeedsData -> previous
    is ForecastState.Failed -> previous
    is ForecastState.Cancelled -> previous
    ForecastState.Idle -> null
}

@Composable
private fun OverviewForecastChart(state: ForecastState, plan: PlanSettings, onOpen: () -> Unit) {
    val result = state.displayedResult()
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).semantics(mergeDescendants = true) { role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (result != null) {
            val retirementAge = plan.retirementAge.coerceIn(result.currentAge, result.endAge)
            val median = result.percentile(ForecastChannel.TOTAL, retirementAge, 50.0)
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("10TH–90TH PERCENTILE", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(median.editorialFormat(), style = MaterialTheme.typography.titleLarge)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("10TH–90TH PERCENTILE", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                Column(horizontalAlignment = Alignment.End) {
                    Text("MEDIAN AT $retirementAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(median.editorialFormat(), style = MaterialTheme.typography.titleLarge)
                }
            }
            OverviewForecastGraphic(forecastChartPoints(result, plan.retirementAge), retirementAge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today\n${result.currentAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("Plan\n${result.endAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            Text(
                "${String.format(java.util.Locale.US, "%,d", result.paths)} modeled paths · real dollars",
                color = RetirementTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val message = when (state) {
                is ForecastState.Calculating -> "Calculating the modeled range…"
                is ForecastState.NeedsData -> "The forecast needs more plan data."
                is ForecastState.Failed -> "The forecast is temporarily unavailable."
                is ForecastState.Cancelled -> "Forecast calculation stopped."
                else -> "Open Forecast to calculate a modeled range."
            }
            Text("MODELED FORECAST", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(message, color = RetirementTextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun OverviewForecastGraphic(points: List<ForecastChartPoint>, retirementAge: Int) {
    val chartHeight = 290.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
        ForecastFanChart(
            points = points,
            retirementAge = retirementAge,
            modifier = Modifier.fillMaxSize(),
            editorialGuides = true,
        )
        val marker = points.firstOrNull { it.age == retirementAge }
        if (marker != null && points.size >= 2) {
            val firstAge = points.first().age
            val ageSpan = maxOf(1, points.last().age - firstAge)
            val markerX = maxWidth * ((retirementAge - firstAge) / ageSpan.toFloat())
            val minValue = points.minOf { it.low.cents }.coerceAtLeast(0L).toFloat()
            val maxValue = maxOf(minValue + 1f, points.maxOf { it.high.cents }.toFloat())
            val markerFraction = ((marker.middle.cents.coerceAtLeast(0L).toFloat() - minValue) /
                (maxValue - minValue)).coerceIn(0f, 1f)
            val markerY = chartHeight - 14.dp - (chartHeight - 34.dp) * markerFraction
            val largeText = LocalDensity.current.fontScale >= 1.5f
            val annotationWidth = if (largeText) 176.dp else 142.dp
            val annotationX = (markerX + 14.dp).coerceAtMost((maxWidth - annotationWidth).coerceAtLeast(0.dp))
            val annotationY = (markerY - if (largeText) 126.dp else 84.dp)
                .coerceIn(0.dp, chartHeight - 96.dp)
            Column(
                Modifier
                    .offset(x = annotationX, y = annotationY)
                    .width(annotationWidth),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text("Age $retirementAge", color = RetirementGold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "RETIREMENT HORIZON",
                    color = RetirementTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TargetCard(
    plan: PlanSettings?,
    liveState: ForecastState,
    onOpenForecast: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val targetAge = plan?.retirementAge?.toString() ?: "Not set"
    val result = liveState.displayedResult()
    val success = result?.let { "${(it.successRate * 100).toInt()}%" } ?: "—"
    val status = when {
        result != null -> "Probability of funding modeled spending through age ${result.endAge}"
        liveState is ForecastState.Calculating -> "Calculating modeled success"
        liveState is ForecastState.NeedsData -> "Forecast needs data"
        liveState is ForecastState.Failed -> "Forecast unavailable"
        liveState is ForecastState.Cancelled -> "Forecast calculation stopped"
        else -> if (plan == null) "Set plan timing in Forecast" else "Open Forecast to calculate"
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            ConfidencePanel(success, status, result, onOpenForecast)
            HorizonPanel(targetAge, onOpenSettings)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfidencePanel(success, status, result, onOpenForecast, Modifier.weight(1f))
                HorizonPanel(targetAge, onOpenSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfidencePanel(
    value: String,
    status: String,
    result: ForecastResult?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.clickable(onClick = onOpen).semantics(mergeDescendants = true) { role = Role.Button },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, RetirementBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 238.dp)
                .background(Brush.verticalGradient(listOf(RetirementSurface, RetirementBackground.copy(alpha = .96f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(value, color = RetirementHighlight, style = MaterialTheme.typography.displaySmall)
            Text("MODELED SUCCESS", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            if (result != null) FailureRateChart(result) else Text(status, color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FailureRateChart(result: ForecastResult) {
    val points = forecastFailurePoints(result)
    val endingRate = points.lastOrNull()?.rate ?: 0.0
    Text("FAILURE RATE BY AGE", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
    Canvas(
        Modifier.fillMaxWidth().height(68.dp).semantics {
            contentDescription = "Cumulative modeled failure rate from age ${result.currentAge} to ${result.endAge}, ending at ${String.format(java.util.Locale.US, "%.0f", endingRate * 100)} percent"
        },
    ) {
        if (points.size < 2) return@Canvas
        val topRate = maxOf(.05, endingRate).toFloat()
        val baseline = size.height - 4.dp.toPx()
        fun x(index: Int) = size.width * index / points.lastIndex
        fun y(rate: Double) = baseline - (rate.toFloat() / topRate * (size.height - 10.dp.toPx()))
        val line = Path().apply {
            moveTo(x(0), y(points.first().rate))
            points.drop(1).forEachIndexed { index, point -> lineTo(x(index + 1), y(point.rate)) }
        }
        val area = Path().apply {
            moveTo(0f, baseline)
            lineTo(0f, y(points.first().rate))
            points.drop(1).forEachIndexed { index, point -> lineTo(x(index + 1), y(point.rate)) }
            lineTo(size.width, baseline)
            close()
        }
        drawLine(RetirementTextSecondary.copy(alpha = .22f), Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        drawPath(area, RetirementGold.copy(alpha = .13f))
        drawPath(line, RetirementGold, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(RetirementGold, 3.dp.toPx(), Offset(size.width, y(endingRate)))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Age ${result.currentAge}", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            "${String.format(java.util.Locale.US, "%.0f", endingRate * 100)}% by ${result.endAge}",
            color = RetirementGold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun HorizonPanel(targetAge: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.clickable(onClick = onOpen).semantics(mergeDescendants = true) { role = Role.Button },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, RetirementBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 238.dp)
                .background(Brush.verticalGradient(listOf(RetirementSurface, RetirementBackground.copy(alpha = .96f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Age $targetAge", style = MaterialTheme.typography.displaySmall)
            Text("RETIREMENT HORIZON", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(46.dp).background(RetirementGold.copy(alpha = .14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = RetirementGold)
                }
                Text("Adjust retirement age", color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun overviewForecastPathCount(plan: dev.draftingroom5.retirement.domain.PlanSettings): Int {
    val currentAge = Period.between(plan.birthDate, plan.referenceDate).years
    return if (plan.endAge - currentAge > 60) 5_000 else 10_000
}

@Composable
private fun DataHealthCard(health: DataHealth, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (health.route != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) { if (health.route != null) role = Role.Button },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (health.kind == DataHealthKind.HEALTHY) RetirementSurface else RetirementSurfaceRaised),
        border = BorderStroke(1.dp, if (health.kind == DataHealthKind.HEALTHY) RetirementBorder else RetirementHighlight),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (health.kind == DataHealthKind.HEALTHY) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null, tint = if (health.kind == DataHealthKind.HEALTHY) RetirementPrimary else RetirementHighlight)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(health.title, fontWeight = FontWeight.SemiBold)
                Text(health.detail, color = RetirementTextSecondary)
            }
            if (health.route != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun AssetsPage(state: RetirementState, onNavigate: (AppRoute) -> Unit) {
    val summary = integratedAssetSummary(state)
    val rows = summary.rows.filter { it.amount.cents != 0L }.map { taxLabel(it.treatment) to it.amount }
    FinancePage {
        EditorialHeading("Assets", "Built from many streams", "Different paths. One complete financial picture.")
        AssetStreams(rows, summary.tracked)
        Text("Asset mix", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = RetirementSurface), border = BorderStroke(1.dp, RetirementBorder)) {
            Column {
                rows.forEachIndexed { index, row ->
                    AssetValueRow(row.first, row.second.format(), FinanceStreamColors[index % FinanceStreamColors.size])
                    if (index != rows.lastIndex) HorizontalDivider(color = RetirementBorder)
                }
            }
        }
        if (summary.tracked.cents == 0L) Text("No tracked accounts yet. Accounts can be linked or added manually.", color = RetirementTextSecondary)
        Row(
            Modifier.fillMaxWidth().clickable { onNavigate(AppRoute.RetirementAccounts) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Open accounts", color = RetirementPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = RetirementPrimary)
        }
    }
}

@Composable
private fun AssetValueRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
        Text(label, color = RetirementTextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FinancePage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(RetirementSurfaceRaised.copy(alpha = .48f), RetirementBackground, RetirementBackgroundDeep),
                radius = 980f,
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LibraryPage(message: String?) {
    val uriHandler = LocalUriHandler.current
    IntegratedPage(
        "Reference library",
        "Good sources, ready when you are",
        "A small, government-first reading shelf for the decisions that come with retirement.",
        Icons.Default.AutoStories,
    ) {
        Text("Reviewed $RETIREMENT_LIBRARY_REVIEW_DATE", color = RetirementPrimary, fontWeight = FontWeight.SemiBold)
        message?.let { Text(it, color = RetirementHighlight) }
        RetirementLibraryResources.forEach { resource ->
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, RetirementBorder)) {
                Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(RetirementSurface, RetirementSurfaceRaised.copy(alpha = .78f))))
                    .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).background(RetirementHighlight.copy(alpha = .12f), androidx.compose.foundation.shape.RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, tint = RetirementHighlight)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(resource.organization.uppercase(), color = RetirementPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(resource.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(resource.description, color = RetirementTextSecondary)
                    OutlinedButton(onClick = { uriHandler.openUri(resource.url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Open official resource")
                    }
                }
            }
        }
        Text("Reference links only. Nothing here is a task or completion list.", color = RetirementTextSecondary)
    }
}

@Composable
private fun IntegratedPage(eyebrow: String, title: String, body: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RetirementFlowHero(eyebrow, title, body, icon)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, RetirementBorder)) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(RetirementSurface, RetirementSurfaceRaised.copy(alpha = .82f))))
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                icon()
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text(subtitle, color = RetirementTextSecondary)
            Text(actionLabel, color = RetirementPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SummaryRows(rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = RetirementSurface),
        border = BorderStroke(1.dp, RetirementBorder)) {
        Column {
            rows.forEachIndexed { index, (label, value) ->
                AdaptiveValueRow(label, value)
                if (index != rows.lastIndex) HorizontalDivider(color = RetirementBorder)
            }
        }
    }
}

@Composable
private fun AdaptiveValueRow(label: String, value: String) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$label, $value" }
    if (largeText) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = RetirementTextSecondary)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = RetirementTextSecondary, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IntegratedMessage(title: String, body: String, error: Boolean = false) {
    IntegratedPage(if (error) "Attention" else "Retirement", title, body,
        if (error) Icons.Default.ErrorOutline else Icons.Default.AccountBalanceWallet) {
        Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.AccountBalanceWallet, contentDescription = null,
            tint = if (error) RetirementHighlight else RetirementPrimary)
    }
}

private fun taxLabel(treatment: dev.draftingroom5.retirement.domain.TaxTreatment) = when (treatment) {
    dev.draftingroom5.retirement.domain.TaxTreatment.PRE_TAX -> "Pre-tax"
    dev.draftingroom5.retirement.domain.TaxTreatment.ROTH -> "Roth"
    dev.draftingroom5.retirement.domain.TaxTreatment.TAXABLE -> "Taxable"
    dev.draftingroom5.retirement.domain.TaxTreatment.TAX_FREE -> "Tax-free"
    dev.draftingroom5.retirement.domain.TaxTreatment.PROPERTY -> "Property equity"
    dev.draftingroom5.retirement.domain.TaxTreatment.EPIC -> "Epic stock"
}

internal fun retirementIntegratedPreviewState(attention: Boolean = false): RetirementState {
    val base = retirementAccountsPreviewState()
    val plan = dev.draftingroom5.retirement.domain.PlanSettings(
        id = "preview-plan",
        revision = 1,
        birthDate = LocalDate.parse("1982-06-15"),
        referenceDate = LocalDate.parse("2026-09-21"),
        retirementAge = 65,
        endAge = 95,
        annualSpending = dev.draftingroom5.retirement.domain.Money(7_200_000),
        inflationBps = 250,
        expectedReturnBps = 650,
        filingStatus = dev.draftingroom5.retirement.domain.FilingStatus.MARRIED_FILING_JOINTLY,
        stateCode = "WI",
        taxPolicyId = "finance-reference-v1",
        acaHouseholdSize = 2,
        acaAnnualPremium = dev.draftingroom5.retirement.domain.Money(2_400_000),
        acaRegime = "EXTENDED",
        annualPreTaxContribution = dev.draftingroom5.retirement.domain.Money(2_000_000),
        annualRothContribution = dev.draftingroom5.retirement.domain.Money(500_000),
        annualTaxableContribution = dev.draftingroom5.retirement.domain.Money(250_000),
        incomeStreams = emptyList(),
        homeDisposition = dev.draftingroom5.retirement.domain.HomeDisposition.KEEP,
    )
    return base.copy(
        generation = 12,
        planSettings = listOf(plan),
        providerItems = base.providerItems.map { item ->
            if (item.accountId == "preview-linked") item.copy(
                status = if (attention) dev.draftingroom5.retirement.domain.ProviderStatus.ATTENTION else dev.draftingroom5.retirement.domain.ProviderStatus.READY,
                lastAcceptedAt = Instant.parse("2026-09-21T11:00:00Z"),
                error = if (attention) dev.draftingroom5.retirement.domain.ProviderError.AUTHENTICATION_REQUIRED else null,
            ) else item
        },
    )
}
