package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test
    fun everyDestinationRoundTripsThroughSavedPrimitiveState() {
        val routes = listOf(
            AppRoute.Dashboard,
            AppRoute.MetricDetail(DashboardCard.WEIGHT),
            AppRoute.Settings,
            AppRoute.DashboardCustomization,
            AppRoute.PlanManagement,
            AppRoute.RoutineEditor("routine-1"),
            AppRoute.ScheduleEditor(null, "draft-1", java.time.DayOfWeek.THURSDAY),
            AppRoute.ScheduleEditor("schedule-1", "draft-2", java.time.DayOfWeek.MONDAY),
            AppRoute.InstalledAppPicker("draft-3"),
            AppRoute.ExerciseEditor("draft-4", null),
            AppRoute.ExerciseEditor("draft-4", "exercise-1"),
            AppRoute.GuidedSession("routine-2", "schedule-2", java.time.LocalDate.of(2026, 9, 10)),
            AppRoute.Completion("history-1"),
        )

        routes.forEach { route -> assertEquals(route, decodeAppRoute(encodeAppRoute(route))) }
    }

    @Test
    fun backUsesOneStackForToolbarAndSystemNavigation() {
        val navigation = AppNavigationState(listOf(AppRoute.Dashboard))
        navigation.navigate(AppRoute.Settings)
        navigation.navigate(AppRoute.PlanManagement)

        assertTrue(navigation.back())
        assertEquals(AppRoute.Settings, navigation.current)
        assertTrue(navigation.back())
        assertEquals(AppRoute.Dashboard, navigation.current)
        assertFalse(navigation.back())
    }

    @Test
    fun dashboardReplacementPreventsFinishedWorkFromReopening() {
        val navigation = AppNavigationState(listOf(AppRoute.Dashboard))
        navigation.navigate(AppRoute.GuidedSession("routine", "schedule", java.time.LocalDate.of(2026, 9, 10)))
        navigation.navigate(AppRoute.Completion("history"))

        navigation.dashboard()

        assertEquals(listOf(AppRoute.Dashboard), navigation.backStack)
        assertFalse(navigation.canGoBack)
    }

    @Test
    fun malformedOrUnknownSavedRoutesAreRejected() {
        assertNull(decodeAppRoute("metric:NOT_A_CARD"))
        assertNull(decodeAppRoute("routine:"))
        assertNull(decodeAppRoute("session:only-one-id"))
        assertNull(decodeAppRoute("future:screen"))
        assertEquals(listOf(AppRoute.Dashboard), normalizeRouteStack(emptyList()))
        assertEquals(listOf(AppRoute.Dashboard), normalizeRouteStack(listOf(AppRoute.Settings)))
    }
}
