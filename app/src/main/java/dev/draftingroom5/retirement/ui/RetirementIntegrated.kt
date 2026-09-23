package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoStories
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import dev.draftingroom5.RetirementBlue
import dev.draftingroom5.RetirementHighlight
import dev.draftingroom5.RetirementPrimary
import dev.draftingroom5.RetirementSurface
import dev.draftingroom5.RetirementSurfaceRaised
import dev.draftingroom5.RetirementTextSecondary
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.forecast.ForecastCoordinator
import dev.draftingroom5.retirement.forecast.ForecastState
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.Dispatchers
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
    FinancePage {
        EditorialHeading("Finance", "Your financial outlook")
        Text(
            if (summary.tracked.cents == 0L) "$0" else summary.tracked.editorialFormat(),
            color = RetirementHighlight,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { contentDescription = "Tracked total, ${summary.tracked.format()}" },
        )
        Text(
            if (summary.tracked.cents == 0L) "Add assets to build your outlook" else "working toward the plan",
            color = RetirementTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (plan != null) {
            val currentAge = Period.between(plan.birthDate, plan.referenceDate).years
            PlanHorizon(currentAge, plan.retirementAge, plan.endAge)
        } else {
            Text("Set your plan timing to see the retirement horizon.", color = RetirementTextSecondary)
        }
        TargetCard(state, repository, preview) { onNavigate(AppRoute.RetirementForecast) }
        ActionCard(
            title = "Financial map",
            subtitle = if (summary.tracked.cents == 0L) "Add an account, property, or workbook to begin."
                else "See how each asset contributes to the total.",
            actionLabel = "Open assets",
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = RetirementBlue) },
        ) { onNavigate(AppRoute.RetirementAssets) }
        DataHealthCard(health) { health.route?.let(onNavigate) }
    }
}

@Composable
private fun TargetCard(
    state: RetirementState,
    repository: RetirementRepository?,
    preview: Boolean,
    onOpen: () -> Unit,
) {
    val plan = state.planSettings.maxByOrNull { it.revision }
    val targetAge = plan?.retirementAge?.toString() ?: "Not set"
    val liveState = if (repository != null && plan != null) {
        val scope = rememberCoroutineScope()
        val coordinator = remember(repository) { ForecastCoordinator(repository, scope) }
        val observed by coordinator.state.collectAsState()
        DisposableEffect(coordinator) { onDispose { coordinator.close() } }
        LaunchedEffect(state.generation, plan.revision) { coordinator.restart(paths = overviewForecastPathCount(plan)) }
        observed
    } else ForecastState.Idle
    val success = when {
        preview && plan != null -> "82%"
        liveState is ForecastState.Ready -> "${((liveState as ForecastState.Ready).result.successRate * 100).toInt()}%"
        else -> "—"
    }
    val status = when {
        preview && plan != null -> "20 modeled paths · real dollars"
        liveState is ForecastState.Ready -> "${String.format(java.util.Locale.US, "%,d", (liveState as ForecastState.Ready).result.paths)} modeled paths · real dollars"
        liveState is ForecastState.Calculating -> "Calculating modeled success"
        liveState is ForecastState.NeedsData -> "Forecast needs data"
        liveState is ForecastState.Failed -> "Forecast unavailable"
        liveState is ForecastState.Cancelled -> "Forecast calculation stopped"
        else -> if (plan == null) "Set plan timing in Forecast" else "Open Forecast to calculate"
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).semantics(mergeDescendants = true) { role = Role.Button },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, RetirementBorder),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(RetirementSurfaceRaised, RetirementSurface))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (LocalDensity.current.fontScale >= 1.5f) {
                MetricTile("MODELED CONFIDENCE", success, RetirementHighlight)
                MetricTile("RETIREMENT HORIZON", "Age $targetAge", dev.draftingroom5.RetirementText)
                Text(status, color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("Open forecast  →", color = RetirementBlue, fontWeight = FontWeight.SemiBold)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("MODELED CONFIDENCE", success, RetirementHighlight, Modifier.weight(1f))
                    MetricTile("RETIREMENT HORIZON", "Age $targetAge", dev.draftingroom5.RetirementText, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = RetirementTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("Open forecast  →", color = RetirementBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.headlineLarge)
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
        EditorialHeading("Assets", "Built from many streams", "How each asset contributes to the plan.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("TRACKED TOTAL", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(summary.tracked.editorialFormat(), style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { contentDescription = "Tracked total, ${summary.tracked.format()}" })
            }
            Text("${summary.forecastEligible.format()}\nin Forecast", color = RetirementTextSecondary, textAlign = TextAlign.End)
        }
        AssetStreams(rows)
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
        Button(onClick = { onNavigate(AppRoute.RetirementAccounts) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Open accounts")
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(Modifier.height(16.dp))
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
