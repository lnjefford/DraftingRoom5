package dev.draftingroom5

import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedOccurrenceCompletionTest {
    private val date = LocalDate.of(2026, 9, 10)

    @Test fun acceptedLaunchRecordsOnlySelectedOccurrenceAndIsIdempotent() {
        val store = MemoryStore()
        val repository = AppRepository(store)
        val initial = (repository.load() as LoadState.Ready).value
        val entries = initial.plan.forDay(date.dayOfWeek).filter { initial.plan.routineFor(it).execution == RoutineExecution.LINKED_APP }
        val first = entries.first()
        val key = OccurrenceKey(first.id, date)
        val routine = initial.plan.routineFor(first)

        assertTrue(repository.completeLinkedOccurrence(key, routine.id, 100) is RepositoryResult.Success)
        val afterFirst = (repository.state.value as LoadState.Ready).value
        assertEquals(1, afterFirst.history.size)
        assertEquals(key, afterFirst.history.single().occurrence)
        assertEquals(RoutineExecution.LINKED_APP, afterFirst.history.single().snapshot.execution)
        val openCard = dashboardSessions(initial.plan, emptyList(), emptyList(), date).first { it.scheduleEntry.id == first.id }
        assertEquals("Open & complete", openCard.actionLabel)
        assertEquals("Open & complete ${routine.name}", openCard.accessibilityAction)
        val doneCard = dashboardSessions(afterFirst.plan, emptyList(), afterFirst.history, date).first { it.scheduleEntry.id == first.id }
        assertEquals(SessionAction.DONE, doneCard.action)
        assertEquals("Undo completion for ${routine.name}", doneCard.undoCompletionAction)
        assertTrue(repository.completeLinkedOccurrence(key, routine.id, 101) is RepositoryResult.Success)
        assertEquals(afterFirst, (repository.state.value as LoadState.Ready).value)
        assertEquals(2, store.writes) // initial document plus one completion
        assertEquals(1, AppRepository(store).load().let { (it as LoadState.Ready).value.history.size })
    }

    @Test fun failedWritePublishesNeitherCompletionNorUndo() {
        val store = MemoryStore()
        val repository = AppRepository(store)
        val initial = (repository.load() as LoadState.Ready).value
        val entry = initial.plan.forDay(date.dayOfWeek).first { initial.plan.routineFor(it).execution == RoutineExecution.LINKED_APP }
        val key = OccurrenceKey(entry.id, date)
        val routine = initial.plan.routineFor(entry)
        store.failWrites = true
        assertTrue(repository.completeLinkedOccurrence(key, routine.id, 100) is RepositoryResult.Failed)
        assertEquals(initial, (repository.state.value as LoadState.Ready).value)
        assertTrue(decodeAppDocument(store.value).history.isEmpty())
        store.failWrites = false
        assertTrue(repository.completeLinkedOccurrence(key, routine.id, 100) is RepositoryResult.Success)
        store.failWrites = true
        assertTrue(repository.undoLinkedOccurrence(key) is RepositoryResult.Failed)
        assertEquals(1, (repository.state.value as LoadState.Ready).value.history.size)
        assertEquals(1, decodeAppDocument(store.value).history.size)
    }

    @Test fun undoRemovesOnlyLinkedOccurrenceAndSurvivesBackupRestore() {
        val store = MemoryStore()
        val repository = AppRepository(store)
        val initial = (repository.load() as LoadState.Ready).value
        val linked = initial.plan.forDay(date.dayOfWeek).first { initial.plan.routineFor(it).execution == RoutineExecution.LINKED_APP }
        val routine = initial.plan.routineFor(linked)
        val first = OccurrenceKey(linked.id, date)
        val nextWeek = first.copy(scheduledDate = date.plusWeeks(1))
        repository.completeLinkedOccurrence(first, routine.id, 100)
        repository.completeLinkedOccurrence(nextWeek, routine.id, 200)
        val both = (repository.state.value as LoadState.Ready).value
        assertEquals(both, decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(300, both))).document)
        assertTrue(repository.undoLinkedOccurrence(first) is RepositoryResult.Success)
        val remaining = (repository.state.value as LoadState.Ready).value
        assertEquals(listOf(nextWeek), remaining.history.map { it.occurrence })
        assertEquals("Open & complete", dashboardSessions(remaining.plan, emptyList(), remaining.history, date).first { it.scheduleEntry.id == linked.id }.actionLabel)
        assertTrue(repository.undoLinkedOccurrence(first) is RepositoryResult.Success)
        assertEquals(remaining, (repository.state.value as LoadState.Ready).value)
        val restored = AppRepository(MemoryStore()).also { it.load() }
        assertTrue(restored.restore(decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(301, remaining))).document) is RepositoryResult.Success)
        assertEquals(listOf(nextWeek), (restored.state.value as LoadState.Ready).value.history.map { it.occurrence })
    }

    @Test fun invalidOrGuidedOccurrenceCannotBeCompletedOrUndoneAsLinked() {
        val store = MemoryStore()
        val repository = AppRepository(store)
        val initial = (repository.load() as LoadState.Ready).value
        val guided = initial.plan.routines.first { it.execution == RoutineExecution.GUIDED }
        val linked = initial.plan.routines.first { it.execution == RoutineExecution.LINKED_APP }
        val guidedEntry = initial.plan.schedule.first { it.routineId == guided.id }
        assertTrue(repository.completeLinkedOccurrence(OccurrenceKey(guidedEntry.id, date), guided.id, 100) is RepositoryResult.Invalid)
        assertTrue(repository.completeLinkedOccurrence(OccurrenceKey("missing", date), linked.id, 100) is RepositoryResult.Invalid)
        assertFalse((repository.state.value as LoadState.Ready).value.history.isNotEmpty())
        val guidedRecord = WorkoutHistoryEntry("guided", OccurrenceKey(guidedEntry.id, date), guided, 1, 2)
        repository.update(initial.generation) { it.copy(history = listOf(guidedRecord)) }
        assertTrue(repository.undoLinkedOccurrence(guidedRecord.occurrence) is RepositoryResult.Invalid)
        assertEquals(listOf(guidedRecord), (repository.state.value as LoadState.Ready).value.history)
    }

    private class MemoryStore : DocumentStorage {
        var value = ""
        var writes = 0
        var failWrites = false
        override fun exists() = value.isNotEmpty()
        override fun read() = value
        override fun write(value: String) {
            if (failWrites) throw IOException("storage failed")
            this.value = value
            writes++
        }
    }
}
