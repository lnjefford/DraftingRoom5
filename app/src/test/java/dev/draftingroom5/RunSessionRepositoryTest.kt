package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionRepositoryTest {
    private val date = LocalDate.parse("2026-09-29")
    private val occurrence = OccurrenceKey("run-schedule", date)
    private val route = RunRoute("park", 1, "Park route",
        listOf(RunRoutePoint(410_000_000, -870_000_000), RunRoutePoint(410_000_100, -870_000_100)),
        listOf(0, 1), emptyList())
    private val routine = Routine("run", 1, "Park run", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("run-interval", RunIntervalKind.RUN, 120))))

    @Test fun startTrackPauseResumeAndFinishAreDurableAndIdempotent() {
        val base = defaultAppDocument()
        val document = base.copy(plan = TrainingPlan(listOf(routine), listOf(
            ScheduleEntry("run-schedule", routine.id, setOf(date.dayOfWeek)))), runRoutes = listOf(route))
        val storage = RunMemoryStorage(encodeAppDocument(document))
        val repository = AppRepository(storage)
        repository.load()

        val started = (repository.startRun(occurrence, routine.id, route.id, 1_000) as RepositoryResult.Success).value
        val id = started.runSessions.single().id
        assertEquals(route.id, started.lastRunRouteId)
        assertEquals(route, started.runSessions.single().route)
        assertTrue(repository.startRun(occurrence, routine.id, null, 2_000) is RepositoryResult.Invalid)

        val sampled = (repository.recordRunLocation(id,
            RunLocationSample(route.points.first(), 2_000, 5)) as RepositoryResult.Success).value
        assertEquals(1, sampled.runSessions.single().samples.size)
        val paused = (repository.pauseRun(id, 11_000) as RepositoryResult.Success).value
        assertEquals(10_000L, paused.runSessions.single().elapsedBeforeResumeMillis)
        val resumed = (repository.resumeRun(id, 20_000) as RepositoryResult.Success).value
        assertTrue(resumed.runSessions.single().isRunning)
        val finished = (repository.finishRun(id, 30_000) as RepositoryResult.Success).value
        assertEquals(20_000L, finished.runSessions.single().elapsedBeforeResumeMillis)
        assertEquals(id, finished.history.single().id)
        assertEquals(SessionAction.DONE, dashboardSessions(finished, date).single().action)
        assertEquals(finished, (repository.finishRun(id, 31_000) as RepositoryResult.Success).value)
        assertEquals(finished, (AppRepository(storage).load() as LoadState.Ready).value)
    }
}

private class RunMemoryStorage(initial: String) : DocumentStorage {
    var value = initial
    override fun exists() = true
    override fun read() = value
    override fun write(value: String) { this.value = value }
}
