package dev.draftingroom5.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchProtocolTest {
    @Test
    fun largeTodayScheduleRoundTripsAllWorkoutsWithoutTruncation() {
        val base = fixtureSnapshot()
        val workouts = List(40) { index -> WatchTodayWorkout("$index@2026-10-01", "$index", "Workout $index",
            "$index", "2026-10-01", List(5) { exercise -> base.exercises.first().copy(
                id = "exercise-$exercise", notes = "n".repeat(1000)) }) }
        val today = WatchTodayBriefing("2026-10-01", "Workouts", 40, 40, 0, List(7) { 0 to 0 },
            null, null, null, emptyList(), emptyList(), 100, workouts = workouts)
        val encoded = encodeWatchSnapshot(base.copy(today = today))
        org.junit.Assert.assertTrue(encoded.size > 100_000)
        assertEquals(workouts, decodeWatchSnapshot(encoded).today?.workouts)
    }
    @Test
    fun snapshotRoundTripsEveryOwnedTarget() {
        val snapshot = fixtureSnapshot().copy(today = WatchTodayBriefing(
            "2026-10-01", "Fitbod workout", 1, 7, 0,
            listOf(0 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0),
            "Madison", 64, "CLOUDY",
            listOf(WatchTodayStock("AAPL", 265.13, 1.02, listOf(263.1, 264.2, 265.13))),
            listOf(WatchTodayGame("Green Bay Packers", "Bears", 1_759_500_000_000,
                "https://a.espncdn.com/i/teamlogos/nfl/500/gb.png",
                "https://a.espncdn.com/i/teamlogos/nfl/500/chi.png")), 100, localHour = 18,
        ))

        assertEquals(snapshot, decodeWatchSnapshot(encodeWatchSnapshot(snapshot)))
    }

    @Test
    fun oldWorkoutSnapshotWithoutTodayStillDecodes() {
        val json = encodeWatchSnapshot(fixtureSnapshot()).toString(Charsets.UTF_8)
            .replace("\"today\":null,", "")
            .replace(",\"today\":null", "")

        assertNull(decodeWatchSnapshot(json.toByteArray()).today)
    }

    @Test
    fun oldTodayStockWithoutChartStillDecodes() {
        val today = WatchTodayBriefing("2026-10-01", null, 0, 0, 0,
            List(7) { 0 to 0 }, "Madison", 64, "CLOUDY",
            listOf(WatchTodayStock("AAPL", 265.13, 1.02)), emptyList(), 100)
        val encoded = encodeWatchTodayBriefing(today).replace(",\"points\":[]", "")

        assertEquals(emptyList<Double>(), decodeWatchTodayBriefing(encoded).stocks.single().points)
    }

    @Test
    fun olderTodayPayloadWithoutHourOrLogosStillDecodes() {
        val today = WatchTodayBriefing("2026-10-01", null, 0, 0, 0,
            List(7) { 0 to 0 }, "Madison", 64, "CLOUDY", emptyList(),
            listOf(WatchTodayGame("Packers", "Bears", 1000)), 100)
        val json = org.json.JSONObject(encodeWatchTodayBriefing(today))
        json.remove("localHour")
        json.getJSONArray("games").getJSONObject(0).apply {
            remove("teamLogo"); remove("opponentLogo")
        }
        assertEquals(today, decodeWatchTodayBriefing(json.toString()))
    }

    @Test
    fun offlineCommandsProjectSetsFocusAndCompletionInOrder() {
        val base = fixtureSnapshot().copy(status = WatchWorkoutStatus.AVAILABLE)
        val commands = listOf(
            command("start", WatchCommandType.START_TODAY, at = 1),
            command("one", WatchCommandType.COMPLETE_SET, "hang", 1, 2),
            command("two", WatchCommandType.COMPLETE_SET, "hang", 2, 3),
            command("three", WatchCommandType.COMPLETE_SET, "walk", 1, 4),
        )

        val projected = checkNotNull(projectPendingCommands(base, commands))

        assertEquals(WatchWorkoutStatus.READY_TO_FINISH, projected.status)
        assertEquals("walk", projected.focusedExerciseId)
        assertEquals(listOf(2, 1), projected.exercises.map(WatchExercise::completedSets))
    }

    @Test
    fun selectedWorkoutStartsItsOwnExercisesOfflineAndSurvivesRoundTrip() {
        val base = fixtureSnapshot().copy(status = WatchWorkoutStatus.AVAILABLE)
        val selected = WatchTodayWorkout("second@2026-10-01", "second", "Second workout", "second",
            "2026-10-01", listOf(base.exercises.last()))
        val today = WatchTodayBriefing("2026-10-01", "First", 2, 2, 0, List(7) { 0 to 0 },
            null, null, null, emptyList(), emptyList(), 100, workouts = listOf(selected))
        val snapshot = base.copy(today = today)
        assertEquals(snapshot, decodeWatchSnapshot(encodeWatchSnapshot(snapshot)))
        val start = WatchCommand("start-second", WatchCommandType.START_TODAY, createdAtMillis = 1,
            todayWorkoutId = selected.id)
        assertEquals(start, decodeWatchCommand(encodeWatchCommand(start)))
        val active = checkNotNull(projectPendingCommands(snapshot, listOf(start)))
        assertEquals("second", active.routineId)
        assertEquals(selected.exercises, active.exercises)
        assertEquals("walk", active.focusedExerciseId)
        assertEquals(WatchWorkoutStatus.ACTIVE, active.status)
        val finished = projectPendingCommands(snapshot, listOf(start,
            WatchCommand("walk-set", WatchCommandType.COMPLETE_SET, exerciseId = "walk", setNumber = 1, createdAtMillis = 2),
            WatchCommand("done", WatchCommandType.FINISH_SESSION, createdAtMillis = 3)))
        assertEquals(WatchWorkoutStatus.COMPLETE, finished?.status)
        assertEquals(emptyList<WatchTodayWorkout>(), finished?.today?.workouts)
        val unknown = start.copy(todayWorkoutId = "missing")
        assertEquals(snapshot, projectPendingCommands(snapshot, listOf(unknown)))
    }

    @Test
    fun duplicateSetCommandsAreIdempotent() {
        val command = command("one", WatchCommandType.COMPLETE_SET, "hang", 1, 1)

        val projected = checkNotNull(projectPendingCommands(fixtureSnapshot(), listOf(command, command.copy(id = "retry"))))

        assertEquals(1, projected.exercises.first().completedSets)
        assertEquals(WatchWorkoutStatus.ACTIVE, projected.status)
    }

    @Test
    fun finishProjectsOnlyAfterAllSets() {
        val early = projectPendingCommands(fixtureSnapshot(), listOf(command("finish", WatchCommandType.FINISH_SESSION, at = 1)))
        val ready = fixtureSnapshot().copy(
            status = WatchWorkoutStatus.READY_TO_FINISH,
            exercises = fixtureSnapshot().exercises.map { it.copy(completedSets = it.setCount) },
        )
        val finished = projectPendingCommands(ready, listOf(command("finish", WatchCommandType.FINISH_SESSION, at = 1)))

        assertEquals(WatchWorkoutStatus.ACTIVE, early?.status)
        assertEquals(WatchWorkoutStatus.COMPLETE, finished?.status)
    }

    @Test
    fun malformedSetCommandFailsClosed() {
        val encoded = """{"protocol":1,"id":"bad","type":"COMPLETE_SET","sessionId":null,"exerciseId":"hang","setNumber":0,"createdAtMillis":1}"""

        assertThrows(IllegalArgumentException::class.java) { decodeWatchCommand(encoded.toByteArray()) }
    }

    @Test
    fun nullBaseDoesNotInventWorkoutData() {
        assertNull(projectPendingCommands(null, listOf(command("start", WatchCommandType.START_TODAY, at = 1))))
    }

    private fun fixtureSnapshot() = WatchSnapshot(
        status = WatchWorkoutStatus.ACTIVE,
        generation = 8,
        eventRevision = 2,
        sessionId = "session",
        routineId = "routine",
        routineName = "Forearm & Grip",
        scheduleEntryId = "schedule",
        scheduledDate = "2026-09-25",
        focusedExerciseId = "hang",
        exercises = listOf(
            WatchExercise("hang", "Dead Hangs", "Thick adapter", 2, null, 20, null, "dead_hang", 0),
            WatchExercise("walk", "Farmer's Walk", "", 1, null, 30, 20, "farmers_walk", 0),
        ),
        acknowledgedCommandIds = setOf("old"),
        updatedAtMillis = 100,
    )

    private fun command(
        id: String,
        type: WatchCommandType,
        exerciseId: String? = null,
        setNumber: Int? = null,
        at: Long,
    ) = WatchCommand(id, type, "session", exerciseId, setNumber, at)
}
