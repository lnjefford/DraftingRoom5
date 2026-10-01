package dev.draftingroom5

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class RunDataTest {
    @org.junit.Test
    fun previewSamplingKeepsEndsAndBoundsNativeMapWork() {
        val points = (0 until 10_000).map { RunRoutePoint(it, it) }
        val preview = routePreviewPoints(points)
        assertEquals(points.first(), preview.first())
        assertEquals(points.last(), preview.last())
        org.junit.Assert.assertTrue(preview.size <= 3_000)
        assertEquals(points.take(10), routePreviewPoints(points.take(10)))
    }
    @Test fun runRoutineRequiresUniquePositiveIntervals() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRunRoutine(RunRoutine(listOf(
                RunInterval("same", RunIntervalKind.RUN, 60),
                RunInterval("same", RunIntervalKind.WALK, 60),
            )))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRunRoutine(RunRoutine(listOf(RunInterval("run", RunIntervalKind.RUN, 0))))
        }
    }

    @Test fun routeRequiresOrderedEndpointsAndValidCoordinates() {
        val valid = routeFixture()
        validateRunRoute(valid)
        assertThrows(IllegalArgumentException::class.java) {
            validateRunRoute(valid.copy(waypointIndices = listOf(1, 2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRunRoute(valid.copy(points = valid.points.toMutableList().apply {
                this[1] = RunRoutePoint(900_000_001, 0)
            }))
        }
    }

    @Test fun reversingRoutePreservesShapeButClearsUnsafeTurnInstructions() {
        val route = routeFixture().copy(waypointIndices = listOf(0, 1, 2))
        val reversed = route.reversedDirection()
        validateRunRoute(reversed)
        assertEquals(route.points.reversed(), reversed.points)
        assertEquals(listOf(0, 1, 2), reversed.waypointIndices)
        assertEquals(emptyList<RunTurnCue>(), reversed.turnCues)
        assertEquals(route.revision + 1, reversed.revision)
    }

    @Test fun duplicatedRouteGetsIndependentIdentity() {
        val route = routeFixture()
        val copy = route.duplicate("new-route")
        validateRunRoute(copy)
        assertEquals("new-route", copy.id)
        assertEquals("Test route copy", copy.name)
        assertEquals(1L, copy.revision)
        assertEquals(route.points, copy.points)
    }

    @Test fun runSnapshotDropsLegacyArrivalAndContinueCues() {
        val route = routeFixture().copy(turnCues = listOf(
            RunTurnCue(0, RunTurnKind.CONTINUE, "Continue straight"),
            RunTurnCue(1, RunTurnKind.RIGHT, "Turn right"),
            RunTurnCue(2, RunTurnKind.ARRIVE, "Arrive at destination"),
        ))
        assertEquals(listOf(RunTurnCue(1, RunTurnKind.RIGHT, "Turn right")),
            route.forRunSnapshot().turnCues)
    }

    @Test fun routineExecutionShapesCannotBeMixed() {
        val base = defaultAppDocument()
        val invalid = Routine(
            id = "mixed",
            revision = 1,
            name = "Mixed routine",
            artworkId = "running_shoe",
            execution = RoutineExecution.RUN,
            exercises = base.plan.routines.first { it.execution == RoutineExecution.GUIDED }.exercises,
            appLink = null,
            run = RunRoutine(listOf(RunInterval("run", RunIntervalKind.RUN, 60))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(base.copy(plan = base.plan.copy(routines = base.plan.routines + invalid)))
        }
    }

    @Test fun missingSelectedRouteIsRejected() {
        val base = defaultAppDocument()
        val run = Routine("run", 1, "Park run", "running_shoe", RoutineExecution.RUN,
            emptyList(), null, RunRoutine(listOf(RunInterval("first", RunIntervalKind.RUN, 60)), "missing"))
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(base.copy(plan = base.plan.copy(routines = base.plan.routines + run)))
        }
    }

    @Test fun deletingRouteClearsAssignmentsWithoutChangingOtherRoutines() {
        val base = defaultAppDocument()
        val selected = Routine("run", 1, "Park run", "running_shoe", RoutineExecution.RUN,
            emptyList(), null, RunRoutine(listOf(RunInterval("first", RunIntervalKind.RUN, 60)), "route"))
        val document = base.copy(
            plan = base.plan.copy(routines = base.plan.routines + selected),
            runRoutes = listOf(routeFixture()),
        )
        val cleared = document.withoutRunRoute("route")
        validateAppDocument(cleared)
        org.junit.Assert.assertEquals(emptyList<RunRoute>(), cleared.runRoutes)
        org.junit.Assert.assertEquals(2, cleared.plan.routines.last().revision)
        org.junit.Assert.assertEquals(null, cleared.plan.routines.last().run!!.routeId)
        org.junit.Assert.assertEquals(base.plan.routines, cleared.plan.routines.dropLast(1))
    }

    private fun routeFixture() = RunRoute(
        id = "route",
        revision = 1,
        name = "Test route",
        points = listOf(RunRoutePoint(0, 0), RunRoutePoint(1, 1), RunRoutePoint(2, 2)),
        waypointIndices = listOf(0, 2),
        turnCues = listOf(RunTurnCue(1, RunTurnKind.CONTINUE, "Continue straight")),
    )
}
