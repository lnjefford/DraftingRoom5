package dev.draftingroom5

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class ProgressionAuditTest {
    private val scope = SaverScope { true }

    @Test fun recursiveCustomInsertionsCannotCommitAnUnreadableDocument() {
        val base = defaultAppDocument()
        val routine = base.plan.routines.last()
        var exercise = routine.exercises.first().copy(id = "nested-0")
        var accepted = 0
        var rejected = 0
        repeat(9) { depth ->
            val candidate = base.copy(plan = base.plan.copy(routines = base.plan.routines.dropLast(1) +
                routine.copy(exercises = listOf(exercise))))
            val encoded = try { encodeAppDocument(candidate) } catch (error: IllegalArgumentException) {
                assertTrue(error.message.orEmpty().contains("nesting"))
                rejected++
                null
            }
            if (encoded != null) {
                assertEquals(candidate, decodeAppDocument(encoded))
                assertEquals(candidate, decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(1, candidate))).document)
                accepted++
            }
            exercise = routine.exercises.first().copy(id = "nested-${depth + 1}",
                progression = CustomExerciseProgression(listOf(CustomProgressionStep(exercise.prescription(), listOf(exercise)))))
        }
        assertTrue(accepted >= 3)
        assertTrue(rejected >= 1)
    }

    @Test fun continueFailureRetryRecreationAndLaterWorkoutUseCommittedState() {
        val base = progressionDocumentFixture()
        val session = base.partialSessions.single().let { s -> s.copy(
            completedSets = s.snapshot.exercises.associate { it.id to it.setCount }) }
        var bytes = encodeAppDocument(base.copy(partialSessions = listOf(session)))
        var fail = true
        val storage = object : DocumentStorage {
            override fun exists() = true
            override fun read() = bytes
            override fun write(value: String) { if (fail) throw java.io.IOException("full"); bytes = value }
        }
        fun repository() = AppRepository(storage, SessionClock { SessionClockSample(100, 100, 1) }).also { it.load() }
        var repo = repository()
        val source = session.snapshot.exercises.first()
        val event = SessionEvent.ContinueWorkout(source.id)
        assertTrue(repo.applySessionEvent(repo.sessionLease()!!, session.id, session.eventRevision, event) is SessionRepositoryResult.Failed)
        assertNotNull((repo.state.value as LoadState.Ready).value.progressionOffer(session.id, source.id))
        fail = false
        val accepted = repo.applySessionEvent(repo.sessionLease()!!, session.id, session.eventRevision, event) as SessionRepositoryResult.Partial
        assertTrue(repo.applySessionEvent(repo.sessionLease()!!, session.id, session.eventRevision, event) is SessionRepositoryResult.Conflict)
        repo = repository()
        val current = (repo.state.value as LoadState.Ready).value
        assertEquals(base.plan, current.plan)
        assertNull(current.progressionOffer(session.id, source.id))
        assertTrue(repo.finishGuidedSession(repo.sessionLease()!!, session.id, accepted.session.eventRevision) is SessionRepositoryResult.Complete)
        val next = repo.openGuidedSession(repo.sessionLease()!!, session.occurrence.copy(scheduledDate = session.occurrence.scheduledDate.plusWeeks(1)),
            session.routineId, session.snapshot.revision, "later") as SessionRepositoryResult.Partial
        var latest = next.session
        repeat(source.setCount) { index -> latest = (repo.applySessionEvent(repo.sessionLease()!!, latest.id, latest.eventRevision,
            SessionEvent.CompleteSet(source.id, index + 1)) as SessionRepositoryResult.Partial).session }
        assertNotNull((repo.state.value as LoadState.Ready).value.progressionOffer(latest.id, source.id))
    }

    @Test fun terminalCustomPrescriptionSurvivesOrdinaryEditAndRemainsVisible() {
        val source = progressionDocumentFixture().plan.routines.last().exercises.first()
        val final = source.progressionOptions().single().source.progressionOptions().single().source
        assertNull(final.progression)
        val edited = final.exerciseDraft().copy(notes = "Audited note").savedExercise()!!
        assertEquals(final.measurements, edited.measurements)
        assertEquals(final.timerSeconds, edited.timerSeconds)
        final.measurements.durationSeconds?.let { assertTrue(edited.targetSummary().contains("$it seconds")) }
    }

    @Test fun draftRecreationRetainsNestedStepsInsertionsAndEmptyDraft() {
        val source = progressionDocumentFixture().plan.routines.last().exercises.first()
        val rule = source.progression as CustomExerciseProgression
        listOf(rule, CustomExerciseProgression(emptyList())).forEach {
            val saved = with(CustomProgressionStateSaver) { scope.save(it) }!!
            assertEquals(it, CustomProgressionStateSaver.restore(saved))
        }
        val additions = rule.steps.first().insertedExercises
        val saved = with(InsertedExerciseStateSaver) { scope.save(additions) }!!
        assertEquals(additions, InsertedExerciseStateSaver.restore(saved))
        val nullable = with(NullableCustomProgressionStateSaver) { scope.save(rule) }!!
        assertEquals(rule, NullableCustomProgressionStateSaver.restore(nullable))
    }

    @Test fun visibleCustomResultIncludesInsertedExerciseAndTimer() {
        val source = progressionDocumentFixture().plan.routines.last().exercises.first()
        val option = source.progressionOptions().single()
        val result = progressionResult(option)
        assertTrue(result.contains(option.source.name))
        option.additions.forEach { assertTrue(result.contains("Add: ${it.name}")) }
        option.source.timerSeconds?.let { assertTrue(result.contains("Timer: $it seconds")) }
    }

    @Test fun previewUsesDecimalCalculationAndShowsNoOptionAtBound() {
        val draft = ExerciseDraft("decimal", "Hold", sets = "1", target = "Controlled",
            automaticProgression = true, weightProgression = true, currentPounds = "0.1", poundsIncrement = "0.2")
        assertEquals("More weight: 0.3 lb", draft.nextAutomaticPrescriptionPreview())
        assertEquals("At configured limits", draft.copy(maximumPounds = "0.1").nextAutomaticPrescriptionPreview())
        val huge = draft.copy(currentPounds = "1.0E20").savedExercise()!!
        assertEquals(huge, huge.exerciseDraft().savedExercise())
        assertTrue(progressionPrescription(huge).contains("100000000000000000000 lb"))
    }
}
