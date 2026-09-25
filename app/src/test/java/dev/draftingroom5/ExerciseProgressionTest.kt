package dev.draftingroom5

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal fun progressionRoutineFixture(): Routine {
    val routine = defaultTrainingPlan().routines.single { it.id == "routine-forearm" }
    val child = Exercise("exercise-three-finger-drag", "Three-finger drag", "Open hand", 3, null, 10, "hangboard", 5)
    val inserted = child.copy(progression = CustomExerciseProgression(listOf(
        CustomProgressionStep(child.prescription().copy(weightPounds = 10, durationSeconds = 15)),
        CustomProgressionStep(child.prescription().copy(weightPounds = 15, durationSeconds = 20)),
    )))
    val source = routine.exercises.first().copy(progression = CustomExerciseProgression(listOf(
        CustomProgressionStep(
            ExercisePrescription("Hangboard Holds — half crimp", "Half crimp on the 20 mm edge", 4, 8, 25, "hangboard", 10),
            listOf(inserted),
        ),
        CustomProgressionStep(ExercisePrescription("Hangboard Holds — small edge", "Controlled 15 mm edge hold", 3, null, 20, "hangboard", 10)),
    )))
    return routine.copy(exercises = listOf(source, routine.exercises[1].copy(weightPounds = 25)) + routine.exercises.drop(2))
}

internal fun progressionDocumentFixture(routine: Routine = progressionRoutineFixture()): AppDocument {
    val base = defaultAppDocument()
    val plan = base.plan.copy(routines = base.plan.routines.map { if (it.id == routine.id) routine else it })
    val partial = partialFixture().copy(snapshot = routine, focusedExerciseId = routine.exercises.first().id,
        completedSets = routine.exercises.associate { it.id to 0 })
    val history = WorkoutHistoryEntry("progression-history",
        partial.occurrence.copy(scheduledDate = partial.occurrence.scheduledDate.minusWeeks(1)), routine, 1, 2)
    return base.copy(plan = plan, partialSessions = listOf(partial), history = listOf(history))
}

class ExerciseProgressionTest {
    @Test fun handAuthoredCurrentFixtureDecodesExactSlotsAndOrderedSteps() {
        val json = checkNotNull(javaClass.getResource("/structured-exercise.json")).readText()
        val exercise = decodeExercise(JSONObject(json))
        assertEquals(ExercisePrescription("Loaded hold", "Controlled", 3, 8, 30, "grip_hold", 35), exercise.prescription())
        assertEquals(listOf(40, 45), exercise.progression!!.steps.map { it.replacement.weightPounds })
        assertEquals(12, exercise.progression.steps.first().insertedExercises.single().reps)
        val routine = progressionRoutineFixture().copy(exercises = listOf(exercise))
        val document = progressionDocumentFixture(routine)
        assertEquals(document, decodeAppDocument(encodeAppDocument(document)))
        val first = exercise.progressionOptions().single()
        assertEquals(exercise.progression.steps.first().replacement, first.source.prescription())
        assertNull(first.source.reps)
        assertEquals("added-curl", first.additions.single().id)
        assertEquals(45, first.source.progressionOptions().single().source.weightPounds)
    }
    @Test fun structuredTargetsAndNestedCustomStepsRoundTripInAllSnapshots() {
        val document = progressionDocumentFixture()
        val restored = decodeAppDocument(encodeAppDocument(document))
        assertEquals(document, restored)
        assertEquals(document.plan.routines.last(), restored.partialSessions.single().snapshot)
        assertEquals(document.partialSessions.single().snapshot, restored.history.single().snapshot)
        val step = restored.plan.routines.last().exercises.first().progression!!.steps.first()
        assertEquals(ExercisePrescription("Hangboard Holds — half crimp", "Half crimp on the 20 mm edge", 4, 8, 25, "hangboard", 10), step.replacement)
        assertEquals(listOf(10, 15), step.insertedExercises.single().progression!!.steps.map { it.replacement.weightPounds })
    }

    @Test fun optionalTargetsAreIndependentAndDoNotRequireProgression() {
        val base = progressionRoutineFixture()
        for (weight in listOf(null, 35)) for (duration in listOf(null, 30)) for (reps in listOf(null, 12)) {
            val source = base.exercises.first().copy(weightPounds = weight, durationSeconds = duration, reps = reps, progression = null)
            val document = progressionDocumentFixture(base.copy(exercises = listOf(source)))
            assertEquals(document, decodeAppDocument(encodeAppDocument(document)))
            assertTrue(source.progressionOptions().isEmpty())
        }
    }

    @Test fun everyCurrentSlotIsRequiredEvenWhenNullAndExtraFieldsAreRejected() {
        listOf("reps", "weightPounds", "durationSeconds", "setCount", "progression").forEach { field ->
            val root = JSONObject(encodeAppDocument(defaultAppDocument()))
            root.exercise().remove(field)
            assertThrows(field, IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        }
        listOf("target", "measurements", "timerSeconds", "unknown").forEach { field ->
            val root = JSONObject(encodeAppDocument(defaultAppDocument()))
            root.exercise().put(field, "obsolete")
            assertThrows(field, IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        }
    }

    @Test fun malformedStoredTargetsAreRejectedWithoutCoercion() {
        val invalid = mapOf(
            "weightPounds" to listOf(0, -5, 1, 37, 37.5, "35", "35 lb", true, 2_147_483_650L),
            "durationSeconds" to listOf(0, -1, 0.5, "30", true, 2_147_483_648L),
            "reps" to listOf(0, -1, 1.5, "12", false, 2_147_483_648L),
            "setCount" to listOf(JSONObject.NULL, 0, -1, 1.5, "3", false, 2_147_483_648L),
        )
        invalid.forEach { (field, values) -> values.forEach { value ->
            val root = JSONObject(encodeAppDocument(progressionDocumentFixture()))
            root.exercise().put(field, value)
            assertThrows("$field=$value", IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
            val nested = JSONObject(encodeAppDocument(progressionDocumentFixture()))
            nested.exercise().getJSONObject("progression").getJSONArray("steps").getJSONObject(0)
                .getJSONObject("replacement").put(field, value)
            assertThrows("replacement $field=$value", IllegalArgumentException::class.java) { decodeAppDocument(nested.toString()) }
        } }
    }

    @Test fun invalidDomainTargetsAreRejectedForLiveCustomAndSnapshotPrescriptions() {
        val base = progressionRoutineFixture()
        val source = base.exercises.first()
        val invalid = listOf(source.copy(setCount = 0), source.copy(reps = 0), source.copy(reps = -1),
            source.copy(durationSeconds = 0), source.copy(durationSeconds = -1),
            source.copy(weightPounds = 0), source.copy(weightPounds = -5), source.copy(weightPounds = 37))
        invalid.forEach { exercise ->
            assertThrows(IllegalArgumentException::class.java) {
                encodeAppDocument(progressionDocumentFixture(base.copy(exercises = listOf(exercise))))
            }
            val step = source.copy(progression = CustomExerciseProgression(listOf(CustomProgressionStep(exercise.prescription()))))
            assertThrows(IllegalArgumentException::class.java) {
                encodeAppDocument(progressionDocumentFixture(base.copy(exercises = listOf(step))))
            }
            val document = progressionDocumentFixture()
            assertThrows(IllegalArgumentException::class.java) {
                encodeAppDocument(document.copy(partialSessions = document.partialSessions.map { session ->
                    session.copy(snapshot = session.snapshot.copy(exercises = listOf(exercise) + session.snapshot.exercises.drop(1)))
                }))
            }
        }
    }

    @Test fun integerBoundaryWeightsRemainExact() {
        val routine = progressionRoutineFixture().let { it.copy(exercises = listOf(it.exercises.first().copy(
            weightPounds = 2_147_483_645, reps = Int.MAX_VALUE, durationSeconds = Int.MAX_VALUE, setCount = Int.MAX_VALUE))) }
        val document = progressionDocumentFixture(routine)
        assertEquals(document, decodeAppDocument(encodeAppDocument(document)))
    }

    @Test fun emptyCustomQueueAndDuplicateFutureIdentitiesAreRejected() {
        val routine = progressionRoutineFixture()
        val source = routine.exercises.first()
        listOf(CustomExerciseProgression(emptyList()), CustomExerciseProgression(listOf(
            CustomProgressionStep(source.prescription(), listOf(routine.exercises[1])),
        ))).forEach { progression ->
            assertThrows(IllegalArgumentException::class.java) {
                encodeAppDocument(progressionDocumentFixture(routine.copy(exercises = listOf(source.copy(progression = progression)) + routine.exercises.drop(1))))
            }
        }
    }

    @Test fun linkedAppRoutinesCannotCarryExercises() {
        val base = defaultAppDocument()
        val linked = base.plan.routines.first().copy(exercises = listOf(progressionRoutineFixture().exercises.first()))
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(base.copy(plan = base.plan.copy(routines = listOf(linked) + base.plan.routines.drop(1))))
        }
    }

    private fun JSONObject.exercise() = getJSONObject("plan").getJSONArray("routines").getJSONObject(2).getJSONArray("exercises").getJSONObject(0)
}
