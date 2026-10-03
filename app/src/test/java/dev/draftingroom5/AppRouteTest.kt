package dev.draftingroom5

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteTest {
    @Test fun runRouteLibraryRestoresWithinFitnessWorkspace() {
        assertEquals(AppRoute.RunRoutes, decodeAppRoute(encodeAppRoute(AppRoute.RunRoutes)))
        assertEquals(AppWorkspace.FITNESS, AppRoute.RunRoutes.workspace)
        assertNull(decodeAppRoute("run-routes:unexpected"))
    }
    @Test
    fun everyDestinationRoundTripsThroughSavedPrimitiveState() {
        val routes = listOf(
            AppRoute.Today,
            AppRoute.Dashboard,
            AppRoute.Settings,
            AppRoute.DashboardCustomization,
            AppRoute.PlanManagement,
            AppRoute.RoutineEditor("routine-1"),
            AppRoute.RunStart("run-1", "schedule-1", java.time.LocalDate.of(2026, 9, 10)),
            AppRoute.RunSession("run-session-1"),
            AppRoute.ScheduleEditor(null, "draft-1", java.time.DayOfWeek.THURSDAY),
            AppRoute.ScheduleEditor("schedule-1", "draft-2", java.time.DayOfWeek.MONDAY),
            AppRoute.InstalledAppPicker("draft-3"),
            AppRoute.GuidedSession("routine-2", "schedule-2", java.time.LocalDate.of(2026, 9, 10)),
            AppRoute.Completion("history-1"),
            AppRoute.RetirementOverview,
            AppRoute.RetirementForecast,
            AppRoute.RetirementAssets,
            AppRoute.RetirementAccounts,
            AppRoute.RetirementAddAsset,
            AppRoute.RetirementAccountDetail("account-1"),
            AppRoute.RetirementAccountUpdate("account-1"),
            AppRoute.RetirementAccountHistory("account-1"),
            AppRoute.RetirementAccountEdit("account-1"),
            AppRoute.RetirementPropertyDetail("property-1"),
            AppRoute.RetirementPropertyHistory("property-1"),
            AppRoute.RetirementPropertyEdit("property-1"),
            AppRoute.RetirementLibrary,
            AppRoute.RetirementForecastSettings,
            AppRoute.RetirementForecastRisk(7, 3),
            AppRoute.RetirementScenarioDetail(7, 3, "lower-return"),
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
        assertEquals(AppRoute.Dashboard, decodeAppRoute("metric:WEIGHT"))
        assertNull(decodeAppRoute("routine:"))
        assertNull(decodeAppRoute("session:only-one-id"))
        assertNull(decodeAppRoute("run-session"))
        assertNull(decodeAppRoute("future:screen"))
        assertEquals(listOf(AppRoute.Dashboard), normalizeRouteStack(emptyList()))
        assertEquals(listOf(AppRoute.Dashboard), normalizeRouteStack(listOf(AppRoute.Settings)))
    }

    @Test
    fun workspaceSwitchingPreservesAnIndependentRouteStackForEachWorkspace() {
        val navigation = AppNavigationState(listOf(AppRoute.Dashboard))
        navigation.navigate(AppRoute.Settings)

        navigation.switchWorkspace(AppWorkspace.RETIREMENT)
        assertEquals(AppRoute.RetirementOverview, navigation.current)
        navigation.selectRetirementTab(AppRoute.RetirementForecast)
        navigation.navigate(AppRoute.RetirementForecastSettings)

        navigation.switchWorkspace(AppWorkspace.FITNESS)
        assertEquals(AppRoute.Settings, navigation.current)
        navigation.switchWorkspace(AppWorkspace.RETIREMENT)
        assertEquals(AppRoute.RetirementForecastSettings, navigation.current)
        assertTrue(navigation.back())
        assertEquals(AppRoute.RetirementForecast, navigation.current)
    }

    @Test fun todayIsTheFreshLaunchWorkspaceAndPreservesOtherStacks() {
        val initial = AppNavigationState(listOf(AppRoute.Today))
        assertEquals(AppWorkspace.TODAY, initial.activeWorkspace)
        assertEquals(AppRoute.Today, initial.current)
        initial.switchWorkspace(AppWorkspace.FITNESS)
        initial.navigate(AppRoute.Settings)
        initial.switchWorkspace(AppWorkspace.RETIREMENT)
        initial.selectRetirementTab(AppRoute.RetirementAssets)
        initial.switchWorkspace(AppWorkspace.TODAY)
        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(initial) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!
        assertEquals(AppRoute.Today, restored.current)
        restored.switchWorkspace(AppWorkspace.FITNESS)
        assertEquals(AppRoute.Settings, restored.current)
        restored.switchWorkspace(AppWorkspace.RETIREMENT)
        assertEquals(AppRoute.RetirementAssets, restored.current)
    }

    @Test fun restoredNavigationKeepsTheVisibleWorkoutInsteadOfReturningToToday() {
        val workout = AppRoute.GuidedSession("routine-1", "schedule-1", java.time.LocalDate.of(2026, 10, 3))
        val navigation = AppNavigationState(listOf(AppRoute.Today))
        navigation.switchWorkspace(AppWorkspace.FITNESS)
        navigation.navigate(workout)

        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(navigation) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!

        assertEquals(AppWorkspace.FITNESS, restored.activeWorkspace)
        assertEquals(workout, restored.current)
        assertTrue(restored.back())
        assertEquals(AppRoute.Dashboard, restored.current)
    }

    @Test
    fun retirementTabsAreRestorableRootsAndCannotCrossWorkspaceByNavigate() {
        retirementTopLevelRoutes.forEach { route ->
            assertEquals(listOf(route), normalizeRouteStack(listOf(route), AppWorkspace.RETIREMENT))
        }
        val navigation = AppNavigationState(listOf(AppRoute.Dashboard))
        assertTrue(runCatching { navigation.navigate(AppRoute.RetirementAssets) }.isFailure)
        assertFalse(AppRoute.RetirementForecastSettings in retirementTopLevelRoutes)
    }

    @Test
    fun accountFlowRestoresAndBacksOutThroughApprovedHierarchy() {
        val navigation = AppNavigationState(listOf(AppRoute.RetirementAssets), AppWorkspace.RETIREMENT)
        navigation.navigate(AppRoute.RetirementAccounts)
        navigation.navigate(AppRoute.RetirementAccountDetail("account-1"))
        navigation.navigate(AppRoute.RetirementAccountHistory("account-1"))

        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(navigation) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!
        assertEquals(AppRoute.RetirementAccountHistory("account-1"), restored.current)
        assertTrue(restored.back())
        assertEquals(AppRoute.RetirementAccountDetail("account-1"), restored.current)
        assertTrue(restored.back())
        assertEquals(AppRoute.RetirementAccounts, restored.current)
        assertTrue(restored.back())
        assertEquals(AppRoute.RetirementAssets, restored.current)
    }
    @Test fun epicUploadRestoresOnlyRoutesAndBackReturnsToDetail() {
        val navigation = AppNavigationState(listOf(AppRoute.RetirementAssets), AppWorkspace.RETIREMENT)
        navigation.navigate(AppRoute.RetirementAccounts)
        navigation.navigate(AppRoute.RetirementEpicDetail)
        navigation.navigate(AppRoute.RetirementEpicUpload)
        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(navigation) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!
        assertEquals(AppRoute.RetirementEpicUpload, restored.current)
        assertTrue(restored.back()); assertEquals(AppRoute.RetirementEpicDetail, restored.current)
        assertTrue(restored.back()); assertEquals(AppRoute.RetirementAccounts, restored.current)
        assertFalse(encoded.toString().contains("content://"))
    }

    @Test fun forecastRiskAndScenarioRestoreAndBackThroughForecast() {
        val navigation = AppNavigationState(listOf(AppRoute.RetirementForecast), AppWorkspace.RETIREMENT)
        navigation.navigate(AppRoute.RetirementForecastRisk(7, 3))
        navigation.navigate(AppRoute.RetirementScenarioDetail(7, 3, "higher-spending"))
        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(navigation) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!
        assertEquals(AppRoute.RetirementScenarioDetail(7, 3, "higher-spending"), restored.current)
        assertTrue(restored.back()); assertEquals(AppRoute.RetirementForecastRisk(7, 3), restored.current)
        assertTrue(restored.back()); assertEquals(AppRoute.RetirementForecast, restored.current)
        assertNull(decodeAppRoute("retirement-scenario:-1:3:lower-return"))
        assertNull(decodeAppRoute("retirement-forecast-risk:7:0"))
    }

    @Test fun overviewAssetsAccountsAndLibraryUseOrdinaryRestorableBackNavigation() {
        val navigation = AppNavigationState(listOf(AppRoute.RetirementOverview), AppWorkspace.RETIREMENT)
        navigation.selectRetirementTab(AppRoute.RetirementAssets)
        navigation.navigate(AppRoute.RetirementAccounts)
        assertTrue(navigation.back())
        assertEquals(AppRoute.RetirementAssets, navigation.current)

        navigation.selectRetirementTab(AppRoute.RetirementOverview)
        navigation.navigate(AppRoute.RetirementLibrary)
        val encoded = with(AppNavigationState.Saver) { SaverScope { true }.save(navigation) }!!
        val restored = AppNavigationState.Saver.restore(encoded)!!
        assertEquals(AppRoute.RetirementLibrary, restored.current)
        assertTrue(restored.back())
        assertEquals(AppRoute.RetirementOverview, restored.current)
    }
}
