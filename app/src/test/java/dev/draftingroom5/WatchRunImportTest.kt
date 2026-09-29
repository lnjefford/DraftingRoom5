package dev.draftingroom5

import dev.draftingroom5.watch.WatchRunCapture
import dev.draftingroom5.watch.WatchRunCatalog
import dev.draftingroom5.watch.WatchRunCue
import dev.draftingroom5.watch.WatchRunInterval
import dev.draftingroom5.watch.WatchRunPlan
import dev.draftingroom5.watch.WatchRunPoint
import dev.draftingroom5.watch.WatchRunRoute
import dev.draftingroom5.watch.WatchRunSample
import dev.draftingroom5.watch.decodeWatchRunCapture
import dev.draftingroom5.watch.decodeWatchRunCatalog
import dev.draftingroom5.watch.decodeWatchRunResult
import dev.draftingroom5.watch.encodeWatchRunCapture
import dev.draftingroom5.watch.encodeWatchRunCatalog
import dev.draftingroom5.watch.encodeWatchRunResult
import dev.draftingroom5.watch.withWatchRunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRunImportTest {
    private val plan = WatchRunPlan("run", "Morning run", "run-schedule", "2026-09-29", "2026-09-29",
        listOf(WatchRunInterval("WALK", 30), WatchRunInterval("RUN", 60)), "park")
    private val route = WatchRunRoute("park", "Park", listOf(
        WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_100, -870_000_100)),
        listOf(WatchRunCue(1, "ARRIVE", "Finish")))

    @Test fun catalogAndCaptureRoundTrip() {
        val catalog = WatchRunCatalog(12, listOf(plan), listOf(route), "park")
        assertEquals(catalog, decodeWatchRunCatalog(encodeWatchRunCatalog(catalog)))
        val capture = WatchRunCapture("watch-run", plan, route, 1_000, 0, 1_000, 0, emptyList())
            .record(WatchRunSample(route.points.first(), 1_100, 5))
        assertEquals(capture, decodeWatchRunCapture(encodeWatchRunCapture(capture)))
        assertNotNull(capture.routePosition())
    }

    @Test fun resultImportsOnceAndSurvivesDocumentRoundTrip() {
        val capture = WatchRunCapture("watch-run", plan, route, 1_000, 0, 1_000, 0, emptyList())
            .record(WatchRunSample(route.points.first(), 1_100, 5)).finish(4_000)
        val result = checkNotNull(capture.result())
        assertEquals(result, decodeWatchRunResult(encodeWatchRunResult(result)))
        val imported = defaultAppDocument().withWatchRunResult(result)
        assertEquals(imported, imported.withWatchRunResult(result))
        assertEquals("watch-run", imported.history.single().id)
        assertEquals(route.id, imported.runSessions.single().route?.id)
        assertEquals(imported, decodeAppDocument(encodeAppDocument(imported)))
        assertTrue(runCatching { imported.withWatchRunResult(result.copy(id = "other")) }.isFailure)
    }
}
