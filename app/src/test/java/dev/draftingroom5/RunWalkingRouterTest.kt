package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RunWalkingRouterTest {
    @Test fun parsesStreetGeometryAndStopIndices() {
        val result = parseWalkingRoute("""
            {"features":[{"geometry":{"type":"LineString","coordinates":[[-87.0,41.0],[-87.001,41.001],[-87.002,41.002]]},"properties":{"way_points":[0,2]}}]}
        """.trimIndent())
        assertEquals(listOf(RunRoutePoint(410_000_000, -870_000_000),
            RunRoutePoint(410_010_000, -870_010_000),
            RunRoutePoint(410_020_000, -870_020_000)), result.points)
        assertEquals(listOf(0, 2), result.waypointIndices)
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
