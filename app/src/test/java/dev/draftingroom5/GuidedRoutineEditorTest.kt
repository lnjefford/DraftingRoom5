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
    fun automaticWeightProgressionSavesRestoresAndPreviewsExactPounds() {
        val draft = ExerciseDraft(
            id = "weighted-carry",
            name = "Farmer carry",
            sets = "3",
            target = "Heavy carry",
            automaticProgression = true,
            weightProgression = true,
            currentPounds = "35",
            poundsIncrement = "2.5",
            minimumPounds = "25",
            maximumPounds = "40",
        )
        val saved = draft.savedExercise()!!
        assertEquals(ExerciseMeasurements(weightPounds = 35.0), saved.measurements)
        assertEquals(AutomaticPoundsProgression(2.5, 25.0, 40.0), (saved.progression as AutomaticExerciseProgression).weightPounds)
        assertEquals("More weight: 37.5 lb", draft.nextAutomaticPrescriptionPreview())
        assertEquals(saved, saved.exerciseDraft().savedExercise())
    }

    @Test
    fun automaticDurationProgressionSynchronizesStructuredDurationAndTimer() {
        val draft = ExerciseDraft(
            id = "timed-hold",
            name = "Timed hold",
            sets = "4",
            target = "Controlled hold",
            timed = false,
            timerSeconds = "999",
            automaticProgression = true,
            durationProgression = true,
            currentDurationSeconds = "20",
            secondsIncrement = "5",
            minimumSeconds = "10",
            maximumSeconds = "30",
        )
        val saved = draft.savedExercise()!!
        assertEquals(20, saved.timerSeconds)
        assertEquals(20, saved.measurements.durationSeconds)
        assertEquals("More time: 25 seconds", draft.nextAutomaticPrescriptionPreview())
        assertEquals(saved, saved.exerciseDraft().savedExercise())
    }

    @Test
    fun automaticDualMeasureProgressionPersistsAndClampsExactPreviewToBounds() {
        val saved = ExerciseDraft(
            id = "loaded-hold",
            name = "Loaded hold",
            sets = "3",
            target = "Heavy timed hold",
            automaticProgression = true,
            weightProgression = true,
            currentPounds = "39",
            poundsIncrement = "2.5",
            maximumPounds = "40",
            durationProgression = true,
            currentDurationSeconds = "28",
            secondsIncrement = "5",
            maximumSeconds = "30",
        )
        assertEquals("More weight: 40 lb · 28 seconds\nMore time: 39 lb · 30 seconds\nHeavier, shorter: 40 lb · 23 seconds", saved.nextAutomaticPrescriptionPreview())
        val exercise = saved.savedExercise()!!
        assertEquals(39.0, exercise.measurements.weightPounds!!, 0.0)
        assertEquals(28, exercise.measurements.durationSeconds)
        assertEquals(28, exercise.timerSeconds)
    }

    @Test
    fun automaticProgressionRejectsIncompleteInvalidAndContradictoryRules() {
        val base = ExerciseDraft(
            id = "invalid-progression",
            name = "Hold",
            sets = "3",
            target = "Hold",
            automaticProgression = true,
        )
        assertNull(base.savedExercise())
        val weight = base.copy(weightProgression = true, currentPounds = "20", poundsIncrement = "5")
        assertTrue(weight.savedExercise() != null)
        assertNull(weight.copy(currentPounds = "").savedExercise())
        assertNull(weight.copy(poundsIncrement = "0").savedExercise())
        assertNull(weight.copy(minimumPounds = "25").savedExercise())
        assertNull(weight.copy(minimumPounds = "20", maximumPounds = "15").savedExercise())
        val duration = base.copy(durationProgression = true, currentDurationSeconds = "20", secondsIncrement = "5")
        assertTrue(duration.savedExercise() != null)
        assertNull(duration.copy(currentDurationSeconds = "0").savedExercise())
        assertNull(duration.copy(secondsIncrement = "").savedExercise())
        assertNull(duration.copy(maximumSeconds = "15").savedExercise())
    }

    @Test
    fun disablingProgressionRestoresNoProgressionWorkflow() {
        val ordinary = ExerciseDraft(
            id = "ordinary",
            name = "Repetitions",
            sets = "3",
            target = "12 reps",
            automaticProgression = false,
            weightProgression = true,
            currentPounds = "20",
            poundsIncrement = "5",
        ).savedExercise()!!
        assertNull(ordinary.progression)
        assertEquals(ExerciseMeasurements(weightPounds = 20.0), ordinary.measurements)
        assertNull(ordinary.timerSeconds)
        assertNull(ordinary.exerciseDraft().copy(timed = true, timerSeconds = "", durationProgression = true).savedExercise())
    }

    @Test
    fun customProgressionSavesCurrentMeasuresAndCompleteOrderedReplacements() {
        val inserted = ExerciseDraft(
            id = "future-three-finger",
            name = "Three-finger drag",
            notes = "Open hand",
            sets = "3",
            target = "15 sec",
            timed = true,
            timerSeconds = "15",
            automaticProgression = true,
            durationProgression = true,
            currentDurationSeconds = "15",
            secondsIncrement = "5",
        ).savedExercise()!!
        val steps = CustomExerciseProgression(listOf(
            CustomProgressionStep(
                ExercisePrescription("20 mm edge", "Half crimp", 3, "20 sec", 20, "hangboard", ExerciseMeasurements(durationSeconds = 20)),
                listOf(inserted),
            ),
            CustomProgressionStep(
                ExercisePrescription("15 mm edge", "Controlled grip", 4, "15 sec", 15, "hangboard", ExerciseMeasurements(weightPounds = 5.0, durationSeconds = 15)),
            ),
        ))
        val saved = ExerciseDraft(
            id = "fingerboard-source", name = "25 mm edge", notes = "Open hand", sets = "3", target = "20 sec",
            timed = true, timerSeconds = "20", artworkId = "hangboard", currentPounds = "2.5", currentDurationSeconds = "20",
            customProgressionEnabled = true, customProgression = steps,
        ).savedExercise()!!

        assertEquals(ExerciseMeasurements(2.5, 20), saved.measurements)
        assertEquals(steps, saved.progression)
        assertEquals(listOf("20 mm edge", "15 mm edge"), (saved.progression as CustomExerciseProgression).steps.map { it.replacement.name })
        assertEquals("future-three-finger", steps.steps.first().insertedExercises.single().id)
        assertTrue(inserted.progression is AutomaticExerciseProgression)
        assertEquals(saved, saved.exerciseDraft().savedExercise())
    }

    @Test
    fun customProgressionRejectsEmptyInvalidOrDuplicateFutureExercises() {
        val base = ExerciseDraft("source", "Hang", "", "3", "20 sec", customProgressionEnabled = true)
        assertNull(base.copy(customProgression = CustomExerciseProgression(emptyList())).savedExercise())
        val prescription = ExercisePrescription("Smaller edge", "", 3, "20 sec", 20, "hangboard")
        val duplicate = Exercise("same-id", "Added grip", "", 2, "10 sec", 10, "hangboard")
        val invalid = CustomExerciseProgression(listOf(
            CustomProgressionStep(prescription, listOf(duplicate)),
            CustomProgressionStep(prescription.copy(name = "Smallest edge"), listOf(duplicate)),
        ))
        assertFalse(invalid.isEditorValid())
        assertNull(base.copy(customProgression = invalid).savedExercise())
        assertNull(base.copy(customProgression = CustomExerciseProgression(listOf(CustomProgressionStep(prescription.copy(target = " "))))).savedExercise())
    }

    @Test
    fun customStepsAndAddedExercisesReorderEditAndDeleteWithoutChangingIdentity() {
        val a = Exercise("future-a", "Sloper", "", 3, "15 sec", 15, "hangboard")
        val b = Exercise("future-b", "Pinch", "", 3, "15 sec", 15, "grip_hold")
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
            target = "30 sec",
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
            sets = "4", target = "45 sec", timed = true, timerSeconds = "45",
        ).savedExercise()!!
        val selected = original.exerciseDraft().copy(artworkId = "rice_bag").savedExercise()!!
        assertEquals("rice_bag", selected.artworkId)
        assertEquals(original.copy(artworkId = "rice_bag"), selected)
        val edited = selected.exerciseDraft().copy(notes = "Keep wrist neutral").savedExercise()!!
        assertEquals("rice_bag", edited.artworkId)
        assertEquals(4, edited.setCount)
        assertEquals("45 sec", edited.target)
        assertEquals(45, edited.timerSeconds)
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
        val exercise = ExerciseDraft("hangboard-only", "Hangboard Holds", "Controlled edge hold", "3", "20 sec", true, "20", "hangboard").savedExercise()!!
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
