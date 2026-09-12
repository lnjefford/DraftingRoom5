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
}

/**
 * Small explicit back stack. It is independent from Activity and repository objects, so the
 * complete navigation state can be saved as primitive strings during recreation.
 */
@Stable
internal class AppNavigationState(initialStack: List<AppRoute>) {
    var backStack by mutableStateOf(normalizeRouteStack(initialStack))
        private set

    val current: AppRoute get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(route: AppRoute) {
        if (route != current) backStack = backStack + route
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        backStack = backStack.dropLast(1)
        return true
    }

    fun dashboard() {
        backStack = listOf(AppRoute.Dashboard)
    }

    /** Removes invalid restored/editor routes while preserving the nearest valid parent. */
    fun retainRoutes(predicate: (AppRoute) -> Boolean) {
        backStack = normalizeRouteStack(backStack.takeWhile(predicate))
    }

    companion object {
        val Saver: Saver<AppNavigationState, Any> = listSaver(
            save = { state -> state.backStack.map(::encodeAppRoute) },
            restore = { encoded -> AppNavigationState(encoded.mapNotNull(::decodeAppRoute)) },
        )
    }
}

@Composable
internal fun rememberAppNavigationState(): AppNavigationState = rememberSaveable(saver = AppNavigationState.Saver) {
    AppNavigationState(listOf(AppRoute.Dashboard))
}

internal fun normalizeRouteStack(routes: List<AppRoute>): List<AppRoute> {
    val fromDashboard = routes.dropWhile { it != AppRoute.Dashboard }
    return if (fromDashboard.firstOrNull() == AppRoute.Dashboard) fromDashboard else listOf(AppRoute.Dashboard)
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
        else -> null
    }
}
