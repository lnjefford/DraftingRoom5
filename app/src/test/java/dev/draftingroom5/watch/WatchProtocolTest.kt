package dev.draftingroom5.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchProtocolTest {
    @Test
    fun snapshotRoundTripsEveryOwnedTarget() {
        val snapshot = fixtureSnapshot().copy(today = WatchTodayBriefing(
            "2026-10-01", "Fitbod workout", 1, 7, 0,
            listOf(0 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0),
            "Madison", 64, "CLOUDY",
            listOf(WatchTodayStock("AAPL", 265.13, 1.02, listOf(263.1, 264.2, 265.13))),
            listOf(WatchTodayGame("Green Bay Packers", "Bears", 1_759_500_000_000)), 100,
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
