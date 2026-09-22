package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.draftingroom5.retirement.ui.RetirementAccountsHost
import dev.draftingroom5.retirement.ui.RetirementForecastHost
import dev.draftingroom5.retirement.ui.RetirementIntegratedHost
import dev.draftingroom5.retirement.domain.RetirementState

internal val RetirementBackground = Color(0xFF061511)
internal val RetirementBackgroundDeep = Color(0xFF020B09)
internal val RetirementSurface = Color(0xFF0F241D)
internal val RetirementSurfaceRaised = Color(0xFF17352B)
internal val RetirementBorder = Color(0xFF295142)
internal val RetirementText = Color(0xFFEFF8F3)
internal val RetirementTextSecondary = Color(0xFFB8CEC4)
internal val RetirementPrimary = Color(0xFF72D9A8)
internal val RetirementHighlight = Color(0xFFB7EC82)

private val RetirementColors = darkColorScheme(
    primary = RetirementPrimary,
    onPrimary = RetirementBackgroundDeep,
    primaryContainer = RetirementSurfaceRaised,
    onPrimaryContainer = RetirementText,
    secondary = RetirementHighlight,
    onSecondary = RetirementBackgroundDeep,
    secondaryContainer = RetirementSurface,
    onSecondaryContainer = RetirementText,
    background = RetirementBackground,
    onBackground = RetirementText,
    surface = RetirementSurface,
    onSurface = RetirementText,
    surfaceVariant = RetirementSurfaceRaised,
    onSurfaceVariant = RetirementTextSecondary,
    outline = RetirementTextSecondary,
    outlineVariant = RetirementBorder,
)

@Composable
internal fun RetirementTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetirementColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

internal data class WorkspaceChoice(
    val workspace: AppWorkspace,
    val icon: ImageVector,
)

internal val WorkspaceChoices = listOf(
    WorkspaceChoice(AppWorkspace.FITNESS, Icons.Default.FitnessCenter),
    WorkspaceChoice(AppWorkspace.RETIREMENT, Icons.Default.Savings),
)

private data class RetirementTab(val route: AppRoute, val label: String, val icon: ImageVector)

private val RetirementTabs = listOf(
    RetirementTab(AppRoute.RetirementOverview, "Overview", Icons.Default.Home),
    RetirementTab(AppRoute.RetirementForecast, "Forecast", Icons.Default.QueryStats),
    RetirementTab(AppRoute.RetirementAssets, "Accounts", Icons.Default.AccountBalanceWallet),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RetirementWorkspaceScreen(
    route: AppRoute,
    onSwitchWorkspace: (AppWorkspace) -> Unit,
    onSelectTab: (AppRoute) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenForecastSettings: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit = {},
    accountsPreviewState: RetirementState? = null,
) {
    require(route.workspace == AppWorkspace.RETIREMENT)
    val activity = androidx.activity.compose.LocalActivity.current
    androidx.compose.runtime.DisposableEffect(activity) {
        val wasSecure = activity?.window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val detail = route !in retirementTopLevelRoutes
    if (detail) BackHandler(onBack = onBack)
    RetirementTheme {
        WorkspaceDrawer(AppWorkspace.RETIREMENT, onSwitchWorkspace) { openWorkspaceDrawer ->
        Scaffold(
            modifier = Modifier.fillMaxSize().background(RetirementBackground),
            containerColor = RetirementBackground,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (detail) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        if (detail) {
                            Text(when (route) {
                                AppRoute.RetirementEpicDetail -> "Epic stock"
                                AppRoute.RetirementEpicUpload -> "Upload workbook"
                                AppRoute.RetirementLibrary -> "Library"
                                AppRoute.RetirementForecastSettings -> "Forecast settings"
                                is AppRoute.RetirementForecastRisk -> "Forecast risk"
                                is AppRoute.RetirementScenarioDetail -> "Scenario"
                                AppRoute.RetirementAccounts -> "Accounts"
                                AppRoute.RetirementAddAsset -> "Add account"
                                is AppRoute.RetirementAccountDetail -> "Account"
                                is AppRoute.RetirementAccountUpdate -> "Update balance"
                                is AppRoute.RetirementAccountHistory -> "Balance history"
                                is AppRoute.RetirementAccountEdit -> "Edit account"
                                is AppRoute.RetirementPropertyDetail -> "Property"
                                is AppRoute.RetirementPropertyHistory -> "Value history"
                                is AppRoute.RetirementPropertyEdit -> "Edit property"
                                else -> "Retirement"
                            })
                        } else BrandTitle(
                            title = "Retirement",
                            onLogoClick = openWorkspaceDrawer,
                            activeWorkspace = AppWorkspace.RETIREMENT,
                        )
                    },
                    actions = {
                        if (!detail) {
                            IconButton(onClick = onOpenLibrary) {
                                Icon(Icons.Default.AutoStories, contentDescription = "Retirement library")
                            }
                            IconButton(onClick = onOpenForecastSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Forecast settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RetirementBackgroundDeep),
                )
            },
            bottomBar = {
                if (!detail) {
                    NavigationBar(containerColor = RetirementBackgroundDeep) {
                        RetirementTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { onSelectTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            if (route == AppRoute.RetirementForecast || route == AppRoute.RetirementForecastSettings ||
                route is AppRoute.RetirementForecastRisk || route is AppRoute.RetirementScenarioDetail) {
                Box(Modifier.padding(padding)) {
                    RetirementForecastHost(route, onNavigate, onBack, accountsPreviewState)
                }
            } else if (route == AppRoute.RetirementOverview || route == AppRoute.RetirementAssets ||
                route == AppRoute.RetirementLibrary) {
                Box(Modifier.padding(padding)) {
                    RetirementIntegratedHost(route, onNavigate, accountsPreviewState)
                }
            } else if (route == AppRoute.RetirementEpicDetail || route == AppRoute.RetirementEpicUpload) {
                Box(Modifier.padding(padding)) {
                    if (accountsPreviewState != null) dev.draftingroom5.retirement.ui.EpicDetailPage(accountsPreviewState) {}
                    else dev.draftingroom5.retirement.ui.RetirementEpicHost(route, onNavigate, onBack)
                }
            } else if (route in setOf(
                    AppRoute.RetirementAccounts, AppRoute.RetirementAddAsset,
                ) || route is AppRoute.RetirementAccountDetail || route is AppRoute.RetirementAccountUpdate ||
                route is AppRoute.RetirementAccountHistory || route is AppRoute.RetirementAccountEdit
                    || route is AppRoute.RetirementPropertyDetail || route is AppRoute.RetirementPropertyHistory
                    || route is AppRoute.RetirementPropertyEdit
            ) {
                Box(Modifier.padding(padding)) { RetirementAccountsHost(route, onNavigate, onBack, accountsPreviewState) }
            } else RetirementPlaceholder(route, Modifier.padding(padding))
        }
        }
    }
}

@Composable
private fun RetirementPlaceholder(route: AppRoute, modifier: Modifier = Modifier) {
    val (eyebrow, title, body) = when (route) {
        AppRoute.RetirementOverview -> Triple("Your plan", "A clear view of retirement", "Your financial map, retirement target, and data health will live here.")
        AppRoute.RetirementForecast -> Triple("Forecast", "Plan with a range, not a promise", "Modeled outcomes, lifestyle spending, and focused risks will appear here.")
        AppRoute.RetirementAssets -> Triple("Accounts", "Everything working toward the plan", "Tracked accounts and their tax treatment will be summarized here.")
        AppRoute.RetirementLibrary -> Triple("Research", "Retirement library", "Curated, government-first resources and your private checklist will live here.")
        AppRoute.RetirementForecastSettings -> Triple("Plan inputs", "Forecast settings", "Retirement age, spending, income, tax, and home assumptions will be edited here.")
        else -> error("Not a Retirement route")
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(eyebrow.uppercase(), color = RetirementPrimary, style = MaterialTheme.typography.labelMedium)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(body, color = RetirementTextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetirementSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, RetirementBorder),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = RetirementHighlight, modifier = Modifier.size(34.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Foundation ready", fontWeight = FontWeight.SemiBold)
                    Text("Financial data arrives in the next implementation steps.", color = RetirementTextSecondary)
                }
            }
        }
    }
}

@Preview(name = "Retirement compact", widthDp = 320, heightDp = 800)
@Preview(name = "Retirement tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Retirement landscape", widthDp = 800, heightDp = 360)
@Preview(name = "Retirement large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
internal fun RetirementWorkspacePreview() {
    RetirementWorkspaceScreen(AppRoute.RetirementOverview, {}, {}, {}, {}, {})
}
