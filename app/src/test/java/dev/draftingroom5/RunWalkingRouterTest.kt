package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RunWalkingRouterTest {
    @Test fun parsesStreetGeometryAndStopIndices() {
        val result = parseWalkingRoute("""
            {"features":[{"geometry":{"type":"LineString","coordinates":[[-87.0,41.0],[-87.001,41.001],[-87.002,41.002]]},"properties":{"way_points":[0,2],"segments":[{"steps":[{"type":11,"instruction":"Head north","way_points":[0,0]},{"type":8,"instruction":"Continue straight","way_points":[0,1]},{"type":1,"instruction":"Turn right onto Main Street","way_points":[1,2]},{"type":10,"instruction":"Arrive at your destination","way_points":[2,2]}]}]}}]}
        """.trimIndent())
        assertEquals(listOf(RunRoutePoint(410_000_000, -870_000_000),
            RunRoutePoint(410_010_000, -870_010_000),
            RunRoutePoint(410_020_000, -870_020_000)), result.points)
        assertEquals(listOf(0, 2), result.waypointIndices)
        assertEquals(listOf(RunTurnCue(1, RunTurnKind.RIGHT, "Turn right onto Main Street")), result.turnCues)
    }

    @Test fun requestsTurnInstructions() {
        val request = walkingRouteRequest(listOf(RunRoutePoint(410_000_000, -870_000_000),
            RunRoutePoint(410_010_000, -870_010_000)))
        assertEquals(true, request.getBoolean("instructions"))
        assertEquals("en", request.getString("language"))
    }

    @Test fun ignoresArrivalAtIntermediateAndFinalStops() {
        val result = parseWalkingRoute("""
            {"features":[{"geometry":{"coordinates":[[0,0],[0.001,0],[0.001,0.001],[0.002,0.001]]},"properties":{"way_points":[0,1,3],"segments":[
                {"steps":[{"type":10,"instruction":"Arrive at the first stop","way_points":[1,1]}]},
                {"steps":[{"type":0,"instruction":"Turn left","way_points":[2,3]},{"type":10,"instruction":"Arrive at destination","way_points":[3,3]}]}
            ]}}]}
        """.trimIndent())
        assertEquals(listOf(RunTurnCue(2, RunTurnKind.LEFT, "Turn left")), result.turnCues)
    }

    @Test fun addsDirectionsWithoutReplacingSavedGeometry() {
        val points = listOf(RunRoutePoint(410_000_000, -870_000_000),
            RunRoutePoint(410_010_000, -870_010_000))
        val cue = RunTurnCue(1, RunTurnKind.LEFT, "Turn left")
        val route = RunRoute("saved", 1, "Loop", points, listOf(0, 1), emptyList())
        assertEquals(route.copy(revision = 2, turnCues = listOf(cue)),
            route.withGeneratedDirections(WalkingRoute(points, listOf(0, 1), listOf(cue))))
        assertNull(route.withGeneratedDirections(WalkingRoute(points.reversed(), listOf(0, 1), listOf(cue))))
    }

    @Test fun rejectsTurnOutsideRouteGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            parseWalkingRoute("""{"features":[{"geometry":{"coordinates":[[0,0],[1,1]]},"properties":{"way_points":[0,1],"segments":[{"steps":[{"type":0,"instruction":"Turn left","way_points":[3,3]}]}]}}]}""")
        }
    }

    @Test fun rejectsAnInvalidWaypointIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            parseWalkingRoute("""{"features":[{"geometry":{"coordinates":[[0,0],[1,1]]},"properties":{"way_points":[0,3]}}]}""")
        }
    }

    @Test fun rejectsOutOfBoundsCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            parseWalkingRoute("""{"features":[{"geometry":{"coordinates":[[0,0],[181,1]]},"properties":{"way_points":[0,1]}}]}""")
        }
    }
}
