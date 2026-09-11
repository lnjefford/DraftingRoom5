package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedRoutineEditorTest {
    private val guided = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }

    @Test
    fun exerciseDraftRequiresNamePositiveSetsTargetAndPositiveTimedDuration() {
        val base = ExerciseDraft("exercise-new", name = "Carry", sets = "3", target = "30 sec")
        assertEquals("generic", base.savedExercise()?.artworkId)
        assertNull(base.copy(name = " ").savedExercise())
        assertNull(base.copy(sets = "0").savedExercise())
        assertNull(base.copy(sets = "nope").savedExercise())
        assertNull(base.copy(target = " ").savedExercise())
        assertNull(base.copy(timed = true, timerSeconds = "0").savedExercise())
        assertEquals(45, base.copy(timed = true, timerSeconds = "45").savedExercise()?.timerSeconds)
        assertNull(base.copy(timed = false, timerSeconds = "45").savedExercise()?.timerSeconds)
    }

    @Test
    fun exerciseArtworkIsOnePairedCatalogChoiceWithGenericFallback() {
        val saved = ExerciseDraft(
            id = "exercise-new",
            name = "Carry",
            sets = "3",
            target = "30 sec",
            artworkId = "missing-artwork",
        ).savedExercise()
        assertEquals(ExerciseArtworkCatalog.FALLBACK_ID, saved?.artworkId)
        val asset = ExerciseArtworkCatalog.resolve(saved?.artworkId)
        assertTrue(asset.resource(ExerciseArtworkCrop.LIST) != 0)
        assertTrue(asset.resource(ExerciseArtworkCrop.HEADER) != 0)
    }

    @Test
    fun addEditReorderAndDeletePreserveStableExerciseIdentity() {
        val added = ExerciseDraft("exercise-new", "Carry", "Slow", "3", "30 sec").savedExercise()!!
        val withAdded = guided.withExercise(added)!!
        assertEquals(guided.exercises.size + 1, withAdded.exercises.size)
        assertEquals("exercise-new", withAdded.exercises.last().id)

        val edited = added.copy(name = "Suitcase carry", timerSeconds = 30)
        val withEdit = withAdded.withExercise(edited)!!
        assertEquals("Suitcase carry", withEdit.exercises.last().name)
        assertEquals("exercise-new", withEdit.exercises.last().id)

        val moved = withEdit.moveExercise("exercise-new", -1)
        assertEquals("exercise-new", moved.exercises[moved.exercises.lastIndex - 1].id)
        assertSame(moved, moved.moveExercise("exercise-new", -99))

        val deleted = moved.withoutExercise("exercise-new")!!
        assertEquals(guided.exercises.map { it.id }, deleted.exercises.map { it.id })
    }

    @Test
    fun canonicalGuidedRoutineCannotDeleteItsOnlyExercise() {
        val one = guided.copy(exercises = listOf(guided.exercises.first()))
        assertNull(one.withoutExercise(one.exercises.single().id))
        assertTrue(one.withoutExercise(one.exercises.single().id, allowEmpty = true)!!.exercises.isEmpty())
    }

    @Test
    fun routineIdentityTrimsNameAndResolvesArtwork() {
        val changed = guided.withGuidedIdentity("  Grip power  ", "missing")!!
        assertEquals("Grip power", changed.name)
        assertEquals(RoutineArtworkCatalog.FALLBACK_ID, changed.artworkId)
        assertEquals(guided.revision + 1, changed.revision)
        assertNull(guided.withGuidedIdentity(" ", guided.artworkId))
        val artworkOnly = guided.withGuidedArtwork("running_shoe")!!
        assertEquals(guided.name, artworkOnly.name)
        assertEquals("running_shoe", artworkOnly.artworkId)
    }

    @Test
    fun newRoutineIsSaveableOnlyAfterNameAndExerciseExist() {
        val draft = Routine("new-routine", 1, "", "dumbbell", RoutineExecution.GUIDED, emptyList(), null)
        assertFalse(draft.isSaveableGuidedRoutine())
        val named = draft.copy(name = "Strength")
        assertFalse(named.isSaveableGuidedRoutine())
        assertTrue(named.copy(exercises = listOf(guided.exercises.first())).isSaveableGuidedRoutine())
    }

    @Test
    fun repositoryPersistsACompleteNewRoutineAndLeavesCancelledDraftOut() {
        val storage = MemoryDocumentStorage()
        val repository = AppRepository(storage)
        val initial = (repository.load() as LoadState.Ready).value
        val transient = Routine("draft-only", 1, "Draft", "dumbbell", RoutineExecution.GUIDED, emptyList(), null)
        assertFalse(initial.plan.routines.any { it.id == transient.id })

        val complete = transient.copy(exercises = listOf(guided.exercises.first().copy(id = "draft-exercise")))
        val result = repository.replacePlan(initial.generation, initial.plan.copy(routines = initial.plan.routines + complete))
        assertTrue(result is RepositoryResult.Success)
        assertEquals(complete, repository.currentOrDefaults().plan.routines.last())
    }
}

private class MemoryDocumentStorage : DocumentStorage {
    private var value: String? = null
    override fun exists() = value != null
    override fun read(): String = checkNotNull(value)
    override fun write(value: String) { this.value = value }
}
