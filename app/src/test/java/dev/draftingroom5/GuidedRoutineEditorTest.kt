package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class GuidedRoutineEditorTest {
    private val guided = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }

    @Test
    fun exerciseDraftRequiresNamePositiveSetsAndPositiveOptionalTargets() {
        val base = ExerciseDraft("exercise-new", name = "Carry", sets = "3", reps = "")
        assertEquals("generic", base.savedExercise()?.artworkId)
        assertNull(base.copy(name = " ").savedExercise())
        assertNull(base.copy(sets = "0").savedExercise())
        assertNull(base.copy(sets = "nope").savedExercise())
        assertNull(base.copy(reps = "0").savedExercise())
        assertNull(base.copy(timed = true, durationSeconds = "0").savedExercise())
        assertEquals(45, base.copy(timed = true, durationSeconds = "45").savedExercise()?.durationSeconds)
        assertNull(base.copy(timed = false, durationSeconds = "45").savedExercise()?.durationSeconds)
    }

    @Test
    fun targetSteppersKeepWeightOnFivePoundBoundariesAndOtherTargetsPositive() {
        assertEquals("35", steppedWeight("", 1))
        assertEquals("40", steppedWeight("35", 1))
        assertEquals("35", steppedWeight("40", -1))
        assertEquals("", steppedWeight("5", -1))
        assertEquals("37", steppedWeight("37", 1))
        assertEquals("2147483645", steppedWeight("2147483645", 1))
        assertEquals("3", steppedPositive("", 1, 3))
        assertEquals("", steppedPositive("1", -1, 3))
        assertEquals("25", steppedPositive("20", 1, 20, 5))
        assertEquals("15", steppedPositive("20", -1, 20, 5))
    }

    @Test
    fun futureStepChangeSummaryNamesEveryChangedStructuredAndPresentationField() {
        val before = ExercisePrescription("Carry", "Slow", 3, 8, 20, "generic", 35)
        val after = ExercisePrescription("Suitcase carry", "Tall posture", 4, 10, 25, "farmers_walk", 40)
        assertEquals(
            "Name Carry → Suitcase carry · Weight 35 lb → 40 lb · Duration 20 sec → 25 sec · " +
                "Sets 3 → 4 · Reps 8 → 10 · Notes Slow → Tall posture · Artwork generic → farmers_walk",
            prescriptionChanges(before, after),
        )
        assertEquals("No target or exercise-detail changes", prescriptionChanges(before, before))
    }

    @Test
    fun customProgressionSavesCurrentMeasuresAndCompleteOrderedReplacements() {
        val inserted = ExerciseDraft(
            id = "future-three-finger",
            name = "Three-finger drag",
            notes = "Open hand",
            sets = "3",
            reps = "",
            timed = true,
            durationSeconds = "15",
        ).savedExercise()!!
        val steps = CustomExerciseProgression(listOf(
            CustomProgressionStep(
                ExercisePrescription("20 mm edge", "Half crimp", 3, null, 20, "hangboard", null),
                listOf(inserted),
            ),
            CustomProgressionStep(
                ExercisePrescription("15 mm edge", "Controlled grip", 4, null, 15, "hangboard", 45),
            ),
        ))
        val saved = ExerciseDraft(
            id = "fingerboard-source", name = "25 mm edge", notes = "Open hand", sets = "3", reps = "",
            timed = true, durationSeconds = "20", artworkId = "hangboard", currentPounds = "35",
            customProgressionEnabled = true, customProgression = steps,
        ).savedExercise()!!

        assertEquals(35, saved.weightPounds)
        assertEquals(20, saved.durationSeconds)
        assertEquals(steps, saved.progression)
        assertEquals(listOf("20 mm edge", "15 mm edge"), (saved.progression as CustomExerciseProgression).steps.map { it.replacement.name })
        assertEquals("future-three-finger", steps.steps.first().insertedExercises.single().id)
        assertNull(inserted.progression)
        assertEquals(saved, saved.exerciseDraft().savedExercise())
    }

    @Test
    fun customProgressionRejectsEmptyInvalidOrDuplicateFutureExercises() {
        val base = ExerciseDraft("source", "Hang", "", "3", "", customProgressionEnabled = true)
        assertNull(base.copy(customProgression = CustomExerciseProgression(emptyList())).savedExercise())
        val prescription = ExercisePrescription("Smaller edge", "", 3, null, 20, "hangboard")
        val duplicate = Exercise("same-id", "Added grip", "", 2, null, 10, "hangboard")
        val invalid = CustomExerciseProgression(listOf(
            CustomProgressionStep(prescription, listOf(duplicate)),
            CustomProgressionStep(prescription.copy(name = "Smallest edge"), listOf(duplicate)),
        ))
        assertFalse(invalid.isEditorValid())
        assertNull(base.copy(customProgression = invalid).savedExercise())
        assertNull(base.copy(customProgression = CustomExerciseProgression(listOf(CustomProgressionStep(prescription.copy(reps = 0))))).savedExercise())
    }

    @Test
    fun customStepsAndAddedExercisesReorderEditAndDeleteWithoutChangingIdentity() {
        val a = Exercise("future-a", "Sloper", "", 3, null, 15, "hangboard")
        val b = Exercise("future-b", "Pinch", "", 3, null, 15, "grip_hold")
        val first = CustomProgressionStep(a.prescription(), listOf(a, b))
        val second = CustomProgressionStep(b.prescription())
        val progression = CustomExerciseProgression(listOf(first, second))

        assertEquals(second, progression.moveStep(1, -1).steps.first())
        assertSame(progression, progression.moveStep(0, -1))
        val moved = first.moveInsertedExercise("future-b", -1)
        assertEquals(listOf("future-b", "future-a"), moved.insertedExercises.map { it.id })
        val edited = b.copy(name = "Wide pinch")
        assertEquals("future-b", moved.withInsertedExercise(edited).insertedExercises.first().id)
        assertEquals("Wide pinch", moved.withInsertedExercise(edited).insertedExercises.first().name)
        assertEquals(listOf("future-a"), first.withoutInsertedExercise("future-b").insertedExercises.map { it.id })
    }

    @Test
    fun exerciseArtworkIsOnePairedCatalogChoiceWithGenericFallback() {
        val saved = ExerciseDraft(
            id = "exercise-new",
            name = "Carry",
            sets = "3",
            reps = "",
            artworkId = "missing-artwork",
        ).savedExercise()
        assertEquals(ExerciseArtworkCatalog.FALLBACK_ID, saved?.artworkId)
        val asset = ExerciseArtworkCatalog.resolve(saved?.artworkId)
        assertTrue(asset.resource(ExerciseArtworkCrop.LIST) != 0)
        assertTrue(asset.resource(ExerciseArtworkCrop.HEADER) != 0)
    }

    @Test
    fun riceBagArtworkSelectionPreservesExercisePrescriptionAndNotes() {
        val original = ExerciseDraft(
            id = "rice-grip", name = "Rice bag grip work", notes = "Own pace",
            sets = "4", reps = "", timed = true, durationSeconds = "45",
        ).savedExercise()!!
        val selected = original.exerciseDraft().copy(artworkId = "rice_bag").savedExercise()!!
        assertEquals("rice_bag", selected.artworkId)
        assertEquals(original.copy(artworkId = "rice_bag"), selected)
        val edited = selected.exerciseDraft().copy(notes = "Keep wrist neutral").savedExercise()!!
        assertEquals("rice_bag", edited.artworkId)
        assertEquals(4, edited.setCount)
        assertNull(edited.reps)
        assertEquals(45, edited.durationSeconds)
    }

    @Test
    fun addEditReorderAndDeletePreserveStableExerciseIdentity() {
        val added = ExerciseDraft("exercise-new", "Carry", "Slow", "3", "").savedExercise()!!
        val withAdded = guided.withExercise(added)!!
        assertEquals(guided.exercises.size + 1, withAdded.exercises.size)
        assertEquals("exercise-new", withAdded.exercises.last().id)

        val edited = added.copy(name = "Suitcase carry", durationSeconds = 30)
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
    fun customFutureIdentitiesCannotCollideAndSourceMutationsKeepChildrenAttached() {
        val source = progressionDocumentFixture().plan.routines.single { it.id == "routine-forearm" }.exercises.first()
        val insertedId = (source.progression as CustomExerciseProgression).steps.first().insertedExercises.single().id
        val routine = guided.copy(exercises = listOf(source) + guided.exercises.drop(1))

        assertNull(routine.withExercise(guided.exercises[1].copy(id = insertedId)))
        val moved = routine.moveExercise(source.id, 1)
        assertEquals(source.progressionIdentityIds(), moved.exercises[1].progressionIdentityIds())
        val deleted = moved.withoutExercise(source.id)!!
        assertFalse(deleted.exercises.any { it.id == insertedId })
        assertFalse(deleted.exercises.any { it.id == source.id })
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

    @Test
    fun userCreatedHangboardingRoutineKeepsItsOwnArtworkAndRecurrenceThroughStorage() {
        val storage = MemoryDocumentStorage()
        val repository = AppRepository(storage)
        val initial = (repository.load() as LoadState.Ready).value
        val exercise = ExerciseDraft("hangboard-only", "Hangboard Holds", "Controlled edge hold", "3", "", true, "20", "hangboard").savedExercise()!!
        val newRoutine = Routine("routine-hangboard", 1, "Hangboard session", "hangboard", RoutineExecution.GUIDED, listOf(exercise), null)
        assertTrue(newRoutine.isSaveableGuidedRoutine())
        val recurrence = ScheduleEntry("schedule-hangboard", newRoutine.id, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        val plan = initial.plan.copy(routines = initial.plan.routines + newRoutine, schedule = initial.plan.schedule + recurrence)

        assertTrue(repository.replacePlan(initial.generation, plan) is RepositoryResult.Success)
        val restored = (AppRepository(storage).load() as LoadState.Ready).value.plan
        assertEquals("hangboard", restored.routineFor(recurrence).artworkId)
        assertEquals("hangboard", restored.routineFor(recurrence).exercises.single().artworkId)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), restored.schedule.single { it.id == recurrence.id }.days)
        assertEquals(initial.plan.schedule, restored.schedule.filter { it.id != recurrence.id })
        val backup = decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(1234, AppDocument(plan = restored))))
        assertEquals(newRoutine, backup.document.plan.routines.single { it.id == newRoutine.id })
        assertEquals(recurrence, backup.document.plan.schedule.single { it.id == recurrence.id })
    }
}

private class MemoryDocumentStorage : DocumentStorage {
    private var value: String? = null
    override fun exists() = value != null
    override fun read(): String = checkNotNull(value)
    override fun write(value: String) { this.value = value }
}
