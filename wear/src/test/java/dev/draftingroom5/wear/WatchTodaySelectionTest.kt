package dev.draftingroom5.wear

import dev.draftingroom5.watch.*
import org.junit.Assert.*
import org.junit.Test

class WatchTodaySelectionTest {
    @Test fun crossingMidnightUsesCachedNextDayPlansAndHidesYesterdayGuidedWorkouts() {
        val exercise = WatchExercise("hang", "Dead Hangs", "", 3, null, 20, null, "dead_hang", 0)
        val yesterday = "2026-10-01"
        val today = "2026-10-02"
        val guided = WatchTodayWorkout("hang@$yesterday", "hang", "Dead Hangs", "hang", yesterday, listOf(exercise))
        val briefing = WatchTodayBriefing(yesterday, "Dead Hangs", 1, 1, 0,
            List(7) { if (it == 0) 1 to 0 else 0 to 0 }, "Madison", 64, "CLEAR", emptyList(), emptyList(), 1, 18, listOf(guided))
        val snapshot = WatchSnapshot(WatchWorkoutStatus.AVAILABLE, 1, 0, null, "hang", "Dead Hangs",
            "hang", yesterday, "hang", listOf(exercise), updatedAtMillis=1, today=briefing)
        val oldRun = WatchRunPlan("run", "Morning intervals", "run", yesterday, yesterday,
            listOf(WatchRunInterval("RUN", 1200)), null)
        val nextRun = oldRun.copy(scheduledDate=today, effectiveDate=today)
        val catalog = WatchRunCatalog(1, listOf(oldRun, nextRun), emptyList(), null)

        val previousDay = watchFitnessActivities(snapshot, catalog, yesterday)
        assertEquals(listOf(oldRun), previousDay.plans)
        assertEquals(listOf(guided), previousDay.workouts)
        assertNull(previousDay.legacyWorkout)
        val currentDay = watchFitnessActivities(snapshot, catalog, today)
        assertEquals(listOf(nextRun), currentDay.plans)
        assertTrue(currentDay.workouts.isEmpty())
        assertNull(currentDay.legacyWorkout)
        assertFalse(currentDay.completed)
    }
    @Test fun seasonalScenesPreserveDaypartAndSeason() {
        assertEquals(R.drawable.watch_today_autumn_day, watchSceneResource(10, 12))
        assertEquals(R.drawable.watch_today_autumn_night, watchSceneResource(10, 23))
        assertEquals(R.drawable.watch_today_spring_dusk, watchSceneResource(4, 6))
        assertEquals(R.drawable.watch_today_summer_day, watchSceneResource(7, 15))
        assertEquals(R.drawable.watch_today_winter_night, watchSceneResource(1, 2))
        assertEquals(R.drawable.watch_today_winter_dusk, watchSceneResource(12, 18))
    }
    @Test fun logoReadAcceptsTheBoundaryAndRejectsOversizedDownloads() {
        val bytes = ByteArray(300_000) { (it % 255).toByte() }
        assertArrayEquals(bytes, readWatchLogo(bytes.inputStream()))
        assertThrows(IllegalArgumentException::class.java) { readWatchLogo(ByteArray(300_001).inputStream()) }
    }
    @Test fun greatestAbsoluteMovementIncludesLosses() {
        val stocks = listOf(WatchTodayStock("AAPL", 265.13, 1.02),
            WatchTodayStock("MSFT", 517.72, -3.8), WatchTodayStock("NVDA", 188.46, null))
        assertEquals("MSFT", topWatchStock(stocks)?.symbol)
    }
    @Test fun emptyAndUnpricedListsRemainReachable() {
        assertNull(topWatchStock(emptyList()))
        assertEquals("AAPL", topWatchStock(listOf(WatchTodayStock("AAPL", null, null)))?.symbol)
    }
    @Test fun summaryUsesEarliestFutureGameAcrossFollowedTeams() {
        val games = listOf(WatchTodayGame("Packers", "Bears", 300),
            WatchTodayGame("Badgers", "Iowa", 90), WatchTodayGame("Bucks", "Knicks", 200),
            WatchTodayGame("Brewers", null, null))
        assertEquals("Bucks", nextWatchGame(games, 100)?.team)
        assertNull(nextWatchGame(games, 400))
    }
}
