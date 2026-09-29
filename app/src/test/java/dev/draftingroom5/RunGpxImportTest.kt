package dev.draftingroom5

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RunGpxImportTest {
    private fun parse(xml: String): RunRoute = importGpxRoute(xml.byteInputStream(), "route-id", "Imported route")

    @Test fun trackImportKeepsPointOrderAndEndpoints() {
        val route = parse("""<gpx version="1.1"><trk><name> Park loop </name><trkseg>
            <trkpt lat="41.0000000" lon="-87.0000000"/>
            <trkpt lat="41.0000123" lon="-87.0000456"/>
            <trkpt lat="41.0000200" lon="-87.0000800"/>
            </trkseg></trk></gpx>""")
        assertEquals("Park loop", route.name)
        assertEquals(listOf(0, 2), route.waypointIndices)
        assertEquals(RunRoutePoint(410_000_123, -870_000_456), route.points[1])
        assertEquals(emptyList<RunTurnCue>(), route.turnCues)
        validateRunRoute(route)
    }

    @Test fun routeImportKeepsEveryRoutePointAsWaypoint() {
        val route = parse("""<gpx version="1.1"><rte><name>Riverside</name>
            <rtept lat="40" lon="-87"/><rtept lat="40.1" lon="-87.1"/><rtept lat="40.2" lon="-87.2"/>
            </rte></gpx>""")
        assertEquals("Riverside", route.name)
        assertEquals(listOf(0, 1, 2), route.waypointIndices)
    }

    @Test fun malformedCoordinatesAndEntityDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("<gpx><trk><trkseg><trkpt lat=\"91\" lon=\"0\"/><trkpt lat=\"0\" lon=\"1\"/></trkseg></trk></gpx>")
        }
        assertThrows(Exception::class.java) {
            parse("""<!DOCTYPE gpx [<!ENTITY external SYSTEM "file:///private">]>
                <gpx><rte><name>&external;</name><rtept lat="0" lon="0"/><rtept lat="1" lon="1"/></rte></gpx>""")
        }
    }

    @Test fun emptyAndOversizedFilesAreRejected() {
        assertThrows(Exception::class.java) { parse("<gpx/>") }
        val tooLarge = ByteArrayInputStream(ByteArray(8 * 1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) { importGpxRoute(tooLarge, "route", "Imported") }
    }

    @Test fun routeEditingKeepsEndpointsAndIncrementsRevision() {
        val route = parse("<gpx><rte><rtept lat=\"0\" lon=\"0\"/><rtept lat=\"1\" lon=\"1\"/><rtept lat=\"2\" lon=\"2\"/></rte></gpx>")
        val less = route.withoutWaypoint(1)!!
        assertEquals(listOf(0, 2), less.waypointIndices)
        assertEquals(2L, less.revision)
        assertEquals(null, less.withoutWaypoint(0))
        val cue = less.withTurnCue(RunTurnCue(1, RunTurnKind.LEFT, " Turn left "))!!
        assertEquals("Turn left", cue.turnCues.single().instruction)
        assertEquals(3L, cue.revision)
        validateRunRoute(cue)
    }
}
