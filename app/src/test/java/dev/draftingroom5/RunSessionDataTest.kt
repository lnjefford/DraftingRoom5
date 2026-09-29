package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionDataTest {
    private val routine = Routine("run", 1, "Intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(
            RunInterval("walk", RunIntervalKind.WALK, 60),
            RunInterval("run", RunIntervalKind.RUN, 120),
        )))
    private val occurrence = OccurrenceKey("schedule", LocalDate.parse("2026-09-29"))

    @Test fun clockSurvivesPauseResumeAndEndsAfterFinalInterval() {
        val start = startRunSession("session", occurrence, routine, null, 10_000)
        assertEquals(0, start.intervalAt(10_000)!!.first)
        assertEquals(1, start.intervalAt(70_000)!!.first)
        val paused = start.pause(80_000)
        assertFalse(paused.isRunning)
        assertEquals(70_000L, paused.elapsedMillis(1_000_000))
        val resumed = paused.resume(1_000_000)
        assertEquals(1, resumed.intervalAt(1_000_000)!!.first)
        assertNull(resumed.intervalAt(1_110_000))
        val finished = resumed.finish(1_010_000)
        assertEquals(80_000L, finished.elapsedMillis(2_000_000))
        assertFalse(finished.isRunning)
    }

    @Test fun inaccurateAndImpossibleLocationJumpsAreIgnored() {
        val start = startRunSession("session", occurrence, routine, null, 10_000)
        val first = RunLocationSample(RunRoutePoint(410_000_000, -870_000_000), 11_000, 5)
        val accepted = start.record(first)
        assertEquals(1, accepted.samples.size)
        assertEquals(accepted, accepted.record(first.copy(recordedAtMillis = 12_000, accuracyMeters = 80)))
        assertEquals(accepted, accepted.record(first.copy(point = RunRoutePoint(420_000_000, -870_000_000), recordedAtMillis = 12_000)))
        val next = accepted.record(first.copy(point = RunRoutePoint(410_000_300, -870_000_000), recordedAtMillis = 13_000))
        assertEquals(2, next.samples.size)
        assertTrue(next.distanceMeters > 0)
        validateRunSession(next)
        val paused = next.pause(15_000)
        assertEquals(paused, paused.record(first.copy(recordedAtMillis = 16_000)))
    }

    @Test fun longRouteSnapshotPreservesTurnsAndIsBounded() {
        val points = (0 until 10_000).map { RunRoutePoint(410_000_000 + it, -870_000_000) }
        val route = RunRoute("long", 1, "Long path", points, listOf(0, 5_000, 9_999),
            listOf(RunTurnCue(5_000, RunTurnKind.LEFT, "Turn left")))
        val bounded = route.forRunSnapshot()
        assertTrue(bounded.points.size <= 2_000)
        assertEquals(points.first(), bounded.points.first())
        assertEquals(points.last(), bounded.points.last())
        assertEquals(points[5_000], bounded.points[bounded.turnCues.single().pointIndex])
        validateRunRoute(bounded)
    }
}
