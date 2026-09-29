package dev.draftingroom5

import org.junit.Assert.assertThrows
import org.junit.Test

class RunDataTest {
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
