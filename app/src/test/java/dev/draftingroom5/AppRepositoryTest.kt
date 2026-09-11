package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppRepositoryTest {
    @Test fun routineEditsRequireRevisionAndSavedSessionAcknowledgement() {
        val session = partialFixture()
        val original = defaultAppDocument().copy(partialSessions = listOf(session))
        val repository = AppRepository(FakeDocumentStorage(encodeAppDocument(original)))
        repository.load()
        fun renamed(revision: Long) = original.plan.copy(routines = original.plan.routines.map {
            if (it.id == session.routineId) it.copy(name = "Renamed", revision = revision) else it
        })
        assertTrue(repository.replacePlan(1, renamed(1), true) is RepositoryResult.Invalid)
        assertTrue(repository.replacePlan(1, renamed(2)) is RepositoryResult.Invalid)
        val edited = (repository.replacePlan(1, renamed(2), true) as RepositoryResult.Success).value
        assertEquals(session, edited.partialSessions.single())
        assertTrue(repository.replacePlan(1, original.plan, true) is RepositoryResult.Conflict)
        assertTrue(repository.replacePlan(edited.generation, edited.plan) is RepositoryResult.Success)
    }

    @Test fun concurrentSameGenerationWritesCommitExactlyOnce() {
        val storage = FakeDocumentStorage()
        val repository = AppRepository(storage)
        repository.load()
        val results = java.util.Collections.synchronizedList(mutableListOf<RepositoryResult<AppDocument>>())
        val start = java.util.concurrent.CountDownLatch(1)
        val workers = List(2) { index -> Thread {
            start.await()
            results.add(repository.update(1) { it.copy(preferences = it.preferences.copy(hapticsEnabled = index == 0)) })
        }.also { it.start() } }
        start.countDown()
        workers.forEach { it.join() }
        assertEquals(1, results.count { it is RepositoryResult.Success })
        assertEquals(1, results.count { it is RepositoryResult.Conflict })
        assertEquals(2L, decodeAppDocument(storage.value!!).generation)
    }

    @Test fun ioFailureNeverInstallsDefaultsOverAnUnreadableDocument() {
        val storage = object : DocumentStorage {
            override fun exists() = true
            override fun read(): String = throw IOException("read failed")
            override fun write(value: String) { throw AssertionError("Must not write") }
        }
        val repository = AppRepository(storage)
        assertTrue(repository.load() is LoadState.Failed)
        assertTrue(repository.update(1) { it } is RepositoryResult.Invalid)
    }

    @Test fun malformedSyntaxAndDateReturnCorruptWithoutDestroyingBytes() {
        val session = partialFixture()
        val valid = encodeAppDocument(defaultAppDocument().copy(partialSessions = listOf(session)))
        listOf("{", valid.replace("2026-09-12", "2026-02-30")).forEach { encoded ->
            val storage = FakeDocumentStorage(encoded)
            assertTrue(AppRepository(storage).load() is LoadState.Corrupt)
            assertEquals(encoded, storage.value)
        }
    }

    @Test fun oversizedMutationReturnsInvalidAndKeepsCommittedBytes() {
        val storage = FakeDocumentStorage()
        val repository = AppRepository(storage)
        val current = (repository.load() as LoadState.Ready).value
        val bytes = storage.value
        val routine = current.plan.routines.last()
        val hugeRoutine = routine.copy(exercises = List(4_200) {
            routine.exercises.first().copy(id = "exercise-$it", notes = "a".repeat(4_000))
        })
        assertTrue(repository.update(current.generation) { it.copy(plan = it.plan.copy(routines = listOf(hugeRoutine), schedule = emptyList())) }
            is RepositoryResult.Invalid)
        assertEquals(bytes, storage.value)
        assertEquals(current, (repository.state.value as LoadState.Ready).value)
    }

    @Test fun deletingRoutineCascadesPartialsButPreservesHistoryAndPreferences() {
        val session = partialFixture()
        val history = WorkoutHistoryEntry("finished", session.occurrence.copy(scheduledDate = session.occurrence.scheduledDate.minusWeeks(1)), session.snapshot, 1, 2)
        val base = defaultAppDocument()
        val document = base.copy(plan = base.plan.copy(schedule = base.plan.schedule + ScheduleEntry(
            "second-forearm", session.routineId, setOf(java.time.DayOfWeek.SUNDAY),
        )), partialSessions = listOf(session), history = listOf(history),
            preferences = AppPreferences(hapticsEnabled = false))
        val repository = AppRepository(FakeDocumentStorage(encodeAppDocument(document)))
        repository.load()
        val deleted = (repository.deleteRoutine(1, session.routineId) as RepositoryResult.Success).value
        assertTrue(deleted.partialSessions.isEmpty())
        assertFalse(deleted.plan.routines.any { it.id == session.routineId })
        assertFalse(deleted.plan.schedule.any { it.routineId == session.routineId })
        assertEquals(listOf(history), deleted.history)
        val reset = (repository.resetPlan(deleted.generation) as RepositoryResult.Success).value
        assertEquals(defaultTrainingPlan(), reset.plan)
        assertTrue(reset.history.isEmpty())
        assertFalse(reset.preferences.hapticsEnabled)
    }

    @Test fun deletingScheduleEntryPreservesRoutinePartialHistoryAndPreferences() {
        val session = partialFixture()
        val history = WorkoutHistoryEntry("finished", session.occurrence.copy(scheduledDate = session.occurrence.scheduledDate.minusWeeks(1)), session.snapshot, 1, 2)
        val original = defaultAppDocument().copy(
            partialSessions = listOf(session),
            history = listOf(history),
            preferences = AppPreferences(hapticsEnabled = false),
        )
        val repository = AppRepository(FakeDocumentStorage(encodeAppDocument(original)))
        repository.load()

        val deleted = (repository.deleteScheduleEntry(original.generation, session.occurrence.scheduleEntryId) as RepositoryResult.Success).value

        assertFalse(deleted.plan.schedule.any { it.id == session.occurrence.scheduleEntryId })
        assertTrue(deleted.plan.routines.any { it.id == session.routineId })
        assertEquals(listOf(session), deleted.partialSessions)
        assertEquals(listOf(history), deleted.history)
        assertFalse(deleted.preferences.hapticsEnabled)
    }

    @Test fun failedRoutineCascadePublishesNeitherDeletionNorPartialRemoval() {
        val session = partialFixture()
        val original = defaultAppDocument().copy(partialSessions = listOf(session))
        val storage = FakeDocumentStorage(encodeAppDocument(original))
        val repository = AppRepository(storage)
        repository.load()
        storage.failWrites = true

        assertTrue(repository.deleteRoutine(original.generation, session.routineId) is RepositoryResult.Failed)
        assertEquals(original, (repository.state.value as LoadState.Ready).value)
        assertEquals(original, decodeAppDocument(checkNotNull(storage.value)))
    }

    @Test fun resetPlanInstallsOnlyCurrentDefaultsAndPreservesPreferences() {
        val original = defaultAppDocument().copy(
            partialSessions = listOf(partialFixture()),
            history = listOf(WorkoutHistoryEntry("finished", partialFixture().occurrence.copy(scheduledDate = java.time.LocalDate.of(2026, 9, 5)), partialFixture().snapshot, 1, 2)),
            preferences = AppPreferences(hapticsEnabled = false),
        )
        val storage = FakeDocumentStorage(encodeAppDocument(original))
        val repository = AppRepository(storage)
        repository.load()

        val reset = (repository.resetPlan(original.generation) as RepositoryResult.Success).value

        assertEquals(defaultTrainingPlan(), reset.plan)
        assertTrue(reset.partialSessions.isEmpty())
        assertTrue(reset.history.isEmpty())
        assertEquals(original.preferences, reset.preferences)
        val encoded = checkNotNull(storage.value)
        assertFalse(encoded.contains("destination"))
        assertFalse(encoded.contains("customRoutine"))
    }

    @Test fun restoringSnapshotClearsForeignBootTimersAndRejectsWrongFormat() {
        val repository = AppRepository(FakeDocumentStorage())
        repository.load()
        val document = defaultAppDocument().copy(partialSessions = listOf(partialFixture().copy(timer = timerFixture())))
        val restored = (repository.restore(document) as RepositoryResult.Success).value
        assertEquals(SessionTimer(), restored.partialSessions.single().timer)
        assertEquals(document.partialSessions.single().completedSets, restored.partialSessions.single().completedSets)
        assertTrue(repository.restore(document.copy(format = "old")) is RepositoryResult.Invalid)
    }

    @Test fun missingDocumentInstallsAndPublishesDefaults() {
        val storage = FakeDocumentStorage()
        val repository = AppRepository(storage)
        val state = repository.load()
        assertTrue(state is LoadState.Ready)
        assertEquals(defaultAppDocument(), (state as LoadState.Ready).value)
        assertTrue(storage.exists())
    }

    @Test fun corruptCurrentDocumentIsPreservedUntilExplicitReset() {
        val storage = FakeDocumentStorage("{\"format\":\"old\"}")
        val repository = AppRepository(storage)
        assertTrue(repository.load() is LoadState.Corrupt)
        assertEquals("{\"format\":\"old\"}", storage.value)
        assertTrue(repository.resetToDefaults() is RepositoryResult.Success)
        assertEquals(defaultAppDocument().copy(generation = 1), decodeAppDocument(storage.value!!))
    }

    @Test fun failedWriteKeepsThePublishedGenerationAndOldBytes() {
        val storage = FakeDocumentStorage()
        val repository = AppRepository(storage)
        val current = (repository.load() as LoadState.Ready).value
        val oldBytes = storage.value
        storage.failWrites = true
        val result = repository.update(current.generation) { it.copy(plan = it.plan.copy(schedule = emptyList())) }
        assertTrue(result is RepositoryResult.Failed)
        assertEquals(oldBytes, storage.value)
        assertEquals(current, (repository.state.value as LoadState.Ready).value)
    }

    @Test fun staleGenerationCannotOverwriteACompletedMutation() {
        val repository = AppRepository(FakeDocumentStorage())
        val current = (repository.load() as LoadState.Ready).value
        assertTrue(repository.update(current.generation) { it.copy(plan = it.plan.copy(schedule = emptyList())) } is RepositoryResult.Success)
        assertTrue(repository.update(current.generation) { it } is RepositoryResult.Conflict)
    }
}

private class FakeDocumentStorage(initial: String? = null) : DocumentStorage {
    var value: String? = initial
    var failWrites = false
    override fun exists() = value != null
    override fun read(): String = value ?: throw IOException("missing")
    override fun write(value: String) {
        if (failWrites) throw IOException("write failed")
        this.value = value
    }
}
