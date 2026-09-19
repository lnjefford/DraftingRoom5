package dev.draftingroom5

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private fun progressionRoutineFixture(): Routine {
    val routine = defaultTrainingPlan().routines.single { it.id == "routine-forearm" }
    val inserted = Exercise(
        id = "exercise-three-finger-drag",
        name = "Three-finger drag",
        notes = "Open hand",
        setCount = 3,
        target = "10 sec",
        timerSeconds = 10,
        artworkId = "hangboard",
        measurements = ExerciseMeasurements(weightPounds = 5.0, durationSeconds = 10),
        progression = AutomaticExerciseProgression(
            weightPounds = AutomaticPoundsProgression(increment = 2.5, minimum = 5.0, maximum = 20.0),
            durationSeconds = AutomaticSecondsProgression(increment = 5, minimum = 10, maximum = 30),
        ),
    )
    val source = routine.exercises.first().copy(
        measurements = ExerciseMeasurements(durationSeconds = 20),
        progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(
                replacement = routine.exercises.first().prescription().copy(
                    name = "Hangboard Holds — half crimp",
                    notes = "Half crimp on the 20 mm edge",
                    setCount = 4,
                    target = "25 sec",
                    timerSeconds = 25,
                    artworkId = "hangboard",
                    measurements = ExerciseMeasurements(weightPounds = 10.0, durationSeconds = 25),
                ),
                insertedExercises = listOf(inserted),
            ),
            CustomProgressionStep(
                replacement = routine.exercises.first().prescription().copy(
                    name = "Hangboard Holds — small edge",
                    notes = "Controlled 15 mm edge hold",
                    target = "20 sec",
                    timerSeconds = 20,
                    measurements = ExerciseMeasurements(weightPounds = 10.0, durationSeconds = 20),
                ),
            ),
        )),
    )
    val automatic = routine.exercises[1].copy(
        measurements = ExerciseMeasurements(weightPounds = 25.0, durationSeconds = 20),
        progression = AutomaticExerciseProgression(
            weightPounds = AutomaticPoundsProgression(5.0, minimum = 20.0, maximum = 50.0),
            durationSeconds = AutomaticSecondsProgression(5, minimum = 10, maximum = 60),
        ),
    )
    return routine.copy(exercises = listOf(source, automatic) + routine.exercises.drop(2))
}

internal fun progressionDocumentFixture(routine: Routine = progressionRoutineFixture()): AppDocument {
    val base = defaultAppDocument()
    val plan = base.plan.copy(routines = base.plan.routines.map { if (it.id == routine.id) routine else it })
    val partial = partialFixture().copy(
        snapshot = routine,
        focusedExerciseId = routine.exercises.first().id,
        completedSets = routine.exercises.associate { it.id to 0 },
    )
    val history = WorkoutHistoryEntry(
        id = "progression-history",
        occurrence = partial.occurrence.copy(scheduledDate = partial.occurrence.scheduledDate.minusWeeks(1)),
        snapshot = routine,
        startedAtMillis = 1,
        completedAtMillis = 2,
    )
    return base.copy(plan = plan, partialSessions = listOf(partial), history = listOf(history))
}

class ExerciseProgressionTest {
    @Test fun automaticAndCustomProgressionRoundTripInLiveSessionAndHistorySnapshots() {
        val document = progressionDocumentFixture()

        val encoded = encodeAppDocument(document)
        val restored = decodeAppDocument(encoded)

        assertEquals(document, restored)
        assertEquals(document.plan.routines.single { it.id == "routine-forearm" }, restored.partialSessions.single().snapshot)
        assertEquals(document.partialSessions.single().snapshot, restored.history.single().snapshot)
        val inserted = ((restored.partialSessions.single().snapshot.exercises.first().progression as CustomExerciseProgression)
            .steps.first().insertedExercises.single())
        assertEquals("exercise-three-finger-drag", inserted.id)
        assertEquals(AutomaticExerciseProgression(
            AutomaticPoundsProgression(2.5, 5.0, 20.0),
            AutomaticSecondsProgression(5, 10, 30),
        ), inserted.progression)
    }

    @Test fun currentSchemaRequiresMeasurementsAndProgressionEvenWhenBothAreEmpty() {
        val root = JSONObject(encodeAppDocument(defaultAppDocument()))
        val exercise = root.getJSONObject("plan").getJSONArray("routines").getJSONObject(2)
            .getJSONArray("exercises").getJSONObject(0)
        exercise.remove("progression")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }

        exercise.put("progression", JSONObject.NULL)
        exercise.remove("measurements")
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
    }

    @Test fun invalidMeasurementsRulesBoundsAndCustomStepsAreRejected() {
        val base = progressionRoutineFixture()
        val source = base.exercises.first()
        val automatic = base.exercises[1]
        val invalid = listOf(
            source.copy(measurements = ExerciseMeasurements(weightPounds = Double.NaN)),
            automatic.copy(progression = AutomaticExerciseProgression(
                weightPounds = AutomaticPoundsProgression(Double.POSITIVE_INFINITY),
            )),
            automatic.copy(progression = AutomaticExerciseProgression(
                weightPounds = AutomaticPoundsProgression(5.0, minimum = 50.0, maximum = 40.0),
            )),
            automatic.copy(progression = AutomaticExerciseProgression(
                durationSeconds = AutomaticSecondsProgression(0),
            )),
            source.copy(progression = CustomExerciseProgression(emptyList())),
            source.copy(progression = CustomExerciseProgression(listOf(
                CustomProgressionStep(source.prescription().copy(target = " ")),
            ))),
        )

        invalid.forEach { exercise ->
            val routine = base.copy(exercises = listOf(exercise) + base.exercises.drop(1))
            assertThrows(IllegalArgumentException::class.java) { validateAppDocument(progressionDocumentFixture(routine)) }
        }
    }

    @Test fun automaticRulesRequireTheirCurrentStructuredMeasurements() {
        val routine = progressionRoutineFixture()
        val source = routine.exercises.first().copy(
            measurements = ExerciseMeasurements(),
            progression = AutomaticExerciseProgression(
                weightPounds = AutomaticPoundsProgression(5.0),
                durationSeconds = AutomaticSecondsProgression(5),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(progressionDocumentFixture(routine.copy(exercises = listOf(source) + routine.exercises.drop(1))))
        }
    }

    @Test fun customInsertionIdsAreUniqueAcrossCurrentAndFutureExercises() {
        val routine = progressionRoutineFixture()
        val source = routine.exercises.first()
        val duplicate = routine.exercises[1].copy(progression = null)
        val progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(source.prescription(), listOf(duplicate)),
        ))
        assertThrows(IllegalArgumentException::class.java) {
            validateAppDocument(progressionDocumentFixture(routine.copy(
                exercises = listOf(source.copy(progression = progression)) + routine.exercises.drop(1),
            )))
        }
    }

    @Test fun linkedAppRoutinesCannotCarryExerciseProgression() {
        val base = defaultAppDocument()
        val linked = base.plan.routines.first { it.execution == RoutineExecution.LINKED_APP }.copy(
            exercises = listOf(progressionRoutineFixture().exercises[1]),
        )
        val plan = base.plan.copy(routines = base.plan.routines.map { if (it.id == linked.id) linked else it })
        assertThrows(IllegalArgumentException::class.java) { validateAppDocument(base.copy(plan = plan)) }
    }
}
