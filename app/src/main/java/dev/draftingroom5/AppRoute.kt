package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.DayOfWeek

/** Every full-screen destination in the clean application shell. */
internal sealed interface AppRoute {
    data object Dashboard : AppRoute
    data class MetricDetail(val card: DashboardCard) : AppRoute
    data object Settings : AppRoute
    data object DashboardCustomization : AppRoute
    data object PlanManagement : AppRoute
    data class RoutineEditor(val routineId: String) : AppRoute
    data class ScheduleEditor(val entryId: String?, val draftId: String, val anchor: DayOfWeek) : AppRoute
    data class InstalledAppPicker(val ownerDraftId: String) : AppRoute
    // The destination binds a durable session id after resolving this exact occurrence.
    data class GuidedSession(val routineId: String, val scheduleEntryId: String, val scheduledDate: LocalDate) : AppRoute
    data class Completion(val historyId: String) : AppRoute
    data object RetirementOverview : AppRoute
    data object RetirementForecast : AppRoute
    data object RetirementAssets : AppRoute
    data object RetirementAccounts : AppRoute
    data object RetirementAddAsset : AppRoute
    data class RetirementAccountDetail(val accountId: String) : AppRoute
    data class RetirementAccountUpdate(val accountId: String) : AppRoute
    data class RetirementAccountHistory(val accountId: String) : AppRoute
    data class RetirementAccountEdit(val accountId: String) : AppRoute
    data class RetirementPropertyDetail(val propertyId: String) : AppRoute
    data class RetirementPropertyHistory(val propertyId: String) : AppRoute
    data class RetirementPropertyEdit(val propertyId: String) : AppRoute
    data object RetirementEpicDetail : AppRoute
    data object RetirementEpicUpload : AppRoute
    data object RetirementLibrary : AppRoute
    data object RetirementForecastSettings : AppRoute
    data class RetirementForecastRisk(val generation: Long, val planRevision: Long) : AppRoute
    data class RetirementScenarioDetail(val generation: Long, val planRevision: Long, val scenarioId: String) : AppRoute
}

internal enum class AppWorkspace(val label: String) { FITNESS("Fitness"), RETIREMENT("Retirement") }

internal val AppRoute.workspace: AppWorkspace
    get() = when (this) {
        AppRoute.RetirementOverview,
        AppRoute.RetirementForecast,
        AppRoute.RetirementAssets,
        AppRoute.RetirementAccounts,
        AppRoute.RetirementAddAsset,
        is AppRoute.RetirementAccountDetail,
        is AppRoute.RetirementAccountUpdate,
        is AppRoute.RetirementAccountHistory,
        is AppRoute.RetirementAccountEdit,
        is AppRoute.RetirementPropertyDetail,
        is AppRoute.RetirementPropertyHistory,
        is AppRoute.RetirementPropertyEdit,
        AppRoute.RetirementEpicDetail,
        AppRoute.RetirementEpicUpload,
        AppRoute.RetirementLibrary,
        AppRoute.RetirementForecastSettings,
        is AppRoute.RetirementForecastRisk,
        is AppRoute.RetirementScenarioDetail -> AppWorkspace.RETIREMENT
        else -> AppWorkspace.FITNESS
    }

/**
 * Small explicit back stack. It is independent from Activity and repository objects, so the
 * complete navigation state can be saved as primitive strings during recreation.
 */
@Stable
internal class AppNavigationState(
    initialStack: List<AppRoute>,
    initialWorkspace: AppWorkspace = initialStack.lastOrNull()?.workspace ?: AppWorkspace.FITNESS,
    initialInactiveStack: List<AppRoute>? = null,
) {
    private var fitnessStack by mutableStateOf(
        normalizeRouteStack(if (initialWorkspace == AppWorkspace.FITNESS) initialStack else initialInactiveStack.orEmpty(), AppWorkspace.FITNESS),
    )
    private var retirementStack by mutableStateOf(
        normalizeRouteStack(if (initialWorkspace == AppWorkspace.RETIREMENT) initialStack else initialInactiveStack.orEmpty(), AppWorkspace.RETIREMENT),
    )
    var activeWorkspace by mutableStateOf(initialWorkspace)
        private set

    val backStack: List<AppRoute>
        get() = if (activeWorkspace == AppWorkspace.FITNESS) fitnessStack else retirementStack

    val current: AppRoute get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(route: AppRoute) {
        require(route.workspace == activeWorkspace) { "Cannot navigate across workspaces without switching." }
        if (route != current) updateActiveStack(backStack + route)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        updateActiveStack(backStack.dropLast(1))
        return true
    }

    fun dashboard() {
        activeWorkspace = AppWorkspace.FITNESS
        fitnessStack = listOf(AppRoute.Dashboard)
    }

    fun switchWorkspace(workspace: AppWorkspace) {
        activeWorkspace = workspace
    }

    fun selectRetirementTab(route: AppRoute) {
        require(route in retirementTopLevelRoutes)
        activeWorkspace = AppWorkspace.RETIREMENT
        retirementStack = listOf(route)
    }

    /** Removes invalid restored/editor routes while preserving the nearest valid parent. */
    fun retainRoutes(predicate: (AppRoute) -> Boolean) {
        updateActiveStack(normalizeRouteStack(backStack.takeWhile(predicate), activeWorkspace))
    }

    private fun updateActiveStack(stack: List<AppRoute>) {
        if (activeWorkspace == AppWorkspace.FITNESS) fitnessStack = stack else retirementStack = stack
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state ->
                listOf("workspace:${state.activeWorkspace.name}") +
                    state.fitnessStack.map { "fitness:${encodeAppRoute(it)}" } +
                    state.retirementStack.map { "retirement:${encodeAppRoute(it)}" }
            },
            restore = { encoded ->
                val workspace = encoded.firstOrNull()?.removePrefix("workspace:")
                    ?.let { runCatching { AppWorkspace.valueOf(it) }.getOrNull() } ?: AppWorkspace.FITNESS
                val fitness = encoded.filter { it.startsWith("fitness:") }.mapNotNull { decodeAppRoute(it.removePrefix("fitness:")) }
                val retirement = encoded.filter { it.startsWith("retirement:") }.mapNotNull { decodeAppRoute(it.removePrefix("retirement:")) }
                AppNavigationState(
                    initialStack = if (workspace == AppWorkspace.FITNESS) fitness else retirement,
                    initialWorkspace = workspace,
                    initialInactiveStack = if (workspace == AppWorkspace.FITNESS) retirement else fitness,
                )
            },
        )
    }
}

@Composable
internal fun rememberAppNavigationState(): AppNavigationState = rememberSaveable(saver = AppNavigationState.Saver) {
    AppNavigationState(listOf(AppRoute.Dashboard))
}

internal val retirementTopLevelRoutes = listOf(
    AppRoute.RetirementOverview,
    AppRoute.RetirementForecast,
    AppRoute.RetirementAssets,
)

internal fun normalizeRouteStack(routes: List<AppRoute>, workspace: AppWorkspace = AppWorkspace.FITNESS): List<AppRoute> {
    val root = if (workspace == AppWorkspace.FITNESS) AppRoute.Dashboard else AppRoute.RetirementOverview
    val matching = routes.filter { it.workspace == workspace }
    if (matching.isEmpty()) return listOf(root)
    val firstRoot = matching.indexOfFirst { it == root || (workspace == AppWorkspace.RETIREMENT && it in retirementTopLevelRoutes) }
    return if (firstRoot >= 0) matching.drop(firstRoot) else listOf(root)
}

internal fun encodeAppRoute(route: AppRoute): String = when (route) {
    AppRoute.Dashboard -> "dashboard"
    is AppRoute.MetricDetail -> "metric:${route.card.name}"
    AppRoute.Settings -> "settings"
    AppRoute.DashboardCustomization -> "customization"
    AppRoute.PlanManagement -> "planning"
    is AppRoute.RoutineEditor -> "routine:${route.routineId}"
    is AppRoute.ScheduleEditor -> "schedule:${route.entryId.orEmpty()}:${route.draftId}:${route.anchor.name}"
    is AppRoute.InstalledAppPicker -> "apps:${route.ownerDraftId}"
    is AppRoute.GuidedSession -> "session:${route.routineId}:${route.scheduleEntryId}:${route.scheduledDate}"
    is AppRoute.Completion -> "completion:${route.historyId}"
    AppRoute.RetirementOverview -> "retirement-overview"
    AppRoute.RetirementForecast -> "retirement-forecast"
    AppRoute.RetirementAssets -> "retirement-assets"
    AppRoute.RetirementAccounts -> "retirement-accounts"
    AppRoute.RetirementAddAsset -> "retirement-add-asset"
    is AppRoute.RetirementAccountDetail -> "retirement-account:${route.accountId}"
    is AppRoute.RetirementAccountUpdate -> "retirement-account-update:${route.accountId}"
    is AppRoute.RetirementAccountHistory -> "retirement-account-history:${route.accountId}"
    is AppRoute.RetirementAccountEdit -> "retirement-account-edit:${route.accountId}"
    is AppRoute.RetirementPropertyDetail -> "retirement-property:${route.propertyId}"
    is AppRoute.RetirementPropertyHistory -> "retirement-property-history:${route.propertyId}"
    is AppRoute.RetirementPropertyEdit -> "retirement-property-edit:${route.propertyId}"
    AppRoute.RetirementEpicDetail -> "retirement-epic"
    AppRoute.RetirementEpicUpload -> "retirement-epic-upload"
    AppRoute.RetirementLibrary -> "retirement-library"
    AppRoute.RetirementForecastSettings -> "retirement-forecast-settings"
    is AppRoute.RetirementForecastRisk -> "retirement-forecast-risk:${route.generation}:${route.planRevision}"
    is AppRoute.RetirementScenarioDetail -> "retirement-scenario:${route.generation}:${route.planRevision}:${route.scenarioId}"
}

internal fun decodeAppRoute(value: String): AppRoute? {
    val parts = value.split(':')
    return when (parts.firstOrNull()) {
        "dashboard" -> AppRoute.Dashboard.takeIf { parts.size == 1 }
        "metric" -> parts.getOrNull(1)?.let { runCatching { DashboardCard.valueOf(it) }.getOrNull() }?.let { AppRoute.MetricDetail(it) }
        "settings" -> AppRoute.Settings.takeIf { parts.size == 1 }
        "customization" -> AppRoute.DashboardCustomization.takeIf { parts.size == 1 }
        "planning" -> AppRoute.PlanManagement.takeIf { parts.size == 1 }
        "routine" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { AppRoute.RoutineEditor(it) }
        "schedule" -> if (parts.size == 4 && parts[2].isNotBlank()) {
            runCatching { DayOfWeek.valueOf(parts[3]) }.getOrNull()?.let {
                AppRoute.ScheduleEditor(parts[1].ifBlank { null }, parts[2], it)
            }
        } else null
        "apps" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { AppRoute.InstalledAppPicker(it) }
        "session" -> if (parts.size == 4 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
            runCatching { LocalDate.parse(parts[3]) }.getOrNull()?.let { AppRoute.GuidedSession(parts[1], parts[2], it) }
        } else null
        "completion" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { AppRoute.Completion(it) }
        "retirement-overview" -> AppRoute.RetirementOverview.takeIf { parts.size == 1 }
        "retirement-forecast" -> AppRoute.RetirementForecast.takeIf { parts.size == 1 }
        "retirement-assets" -> AppRoute.RetirementAssets.takeIf { parts.size == 1 }
        "retirement-accounts" -> AppRoute.RetirementAccounts.takeIf { parts.size == 1 }
        "retirement-add-asset" -> AppRoute.RetirementAddAsset.takeIf { parts.size == 1 }
        "retirement-account" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementAccountDetail)
        "retirement-account-update" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementAccountUpdate)
        "retirement-account-history" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementAccountHistory)
        "retirement-account-edit" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementAccountEdit)
        "retirement-property" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementPropertyDetail)
        "retirement-property-history" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementPropertyHistory)
        "retirement-property-edit" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(AppRoute::RetirementPropertyEdit)
        "retirement-epic" -> AppRoute.RetirementEpicDetail.takeIf { parts.size == 1 }
        "retirement-epic-upload" -> AppRoute.RetirementEpicUpload.takeIf { parts.size == 1 }
        "retirement-library" -> AppRoute.RetirementLibrary.takeIf { parts.size == 1 }
        "retirement-forecast-settings" -> AppRoute.RetirementForecastSettings.takeIf { parts.size == 1 }
        "retirement-forecast-risk" -> if (parts.size == 3) {
            val generation = parts[1].toLongOrNull()
            val revision = parts[2].toLongOrNull()
            if (generation != null && generation >= 0 && revision != null && revision > 0) AppRoute.RetirementForecastRisk(generation, revision) else null
        } else null
        "retirement-scenario" -> if (parts.size == 4 && parts[3].matches(Regex("[a-z-]+"))) {
            val generation = parts[1].toLongOrNull()
            val revision = parts[2].toLongOrNull()
            if (generation != null && generation >= 0 && revision != null && revision > 0) AppRoute.RetirementScenarioDetail(generation, revision, parts[3]) else null
        } else null
        else -> null
    }
}
