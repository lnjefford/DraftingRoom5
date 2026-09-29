package dev.draftingroom5

import dev.draftingroom5.watch.toWatchRunCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRunCatalogTest {
    @Test fun upcomingPlansAndPreferredRouteAreCachedWithinBounds() {
        val today = LocalDate.parse("2026-09-29")
        val routePoints = listOf(RunRoutePoint(410_000_000, -870_000_000),
            RunRoutePoint(410_000_100, -870_000_100))
        val routes = (0 until 70).map { index ->
            RunRoute("route-$index", 1, "Route $index", routePoints, listOf(0, 1), emptyList())
        }
        val routine = Routine("run", 1, "Intervals", "running_shoe", RoutineExecution.RUN,
            emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 30)), "route-69"))
        val document = defaultAppDocument().copy(
            plan = TrainingPlan(listOf(routine), listOf(ScheduleEntry("schedule", routine.id, setOf(today.dayOfWeek)))),
            runRoutes = routes, lastRunRouteId = "route-69")
        val catalog = document.toWatchRunCatalog(today)
        assertEquals("Intervals", catalog.plans.first().routineName)
        assertEquals("route-69", catalog.lastRouteId)
        assertTrue(catalog.routes.size <= 64)
        assertTrue(catalog.routes.any { it.id == "route-69" })
    }
}
