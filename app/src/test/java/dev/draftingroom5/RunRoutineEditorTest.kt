package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRoutineEditorTest {
    private val original = Routine(
        id = "run",
        revision = 4,
        name = "Morning intervals",
        artworkId = "running_shoe",
        execution = RoutineExecution.RUN,
        exercises = emptyList(),
        appLink = null,
        run = RunRoutine(listOf(
            RunInterval("walk", RunIntervalKind.WALK, 60),
            RunInterval("run", RunIntervalKind.RUN, 120),
        )),
    )

    @Test fun durationFieldsAcceptOnlyAValidPositiveDayOrLess() {
        assertEquals(1, runIntervalDurationSeconds("", "1"))
        assertEquals(3_661, runIntervalDurationSeconds("61", "1"))
        assertEquals(86_400, runIntervalDurationSeconds("1440", "0"))
        assertNull(runIntervalDurationSeconds("0", "0"))
        assertNull(runIntervalDurationSeconds("1", "60"))
        assertNull(runIntervalDurationSeconds("1440", "1"))
        assertNull(runIntervalDurationSeconds("no", "1"))
    }

    @Test fun durationAndSummaryLabelsStayCompact() {
        assertEquals("45s", formatRunDuration(45))
        assertEquals("2m", formatRunDuration(120))
        assertEquals("1h 1m 1s", formatRunDuration(3_661))
        assertEquals("2 intervals · 3m · Scheduled Mon", runRoutineSummary(requireNotNull(original.run), "Scheduled Mon"))
    }

    @Test fun intervalEditsPreserveIdsAndSupportOrderedMoves() {
        val updated = original.withRunInterval(RunInterval("run", RunIntervalKind.RUN, 180))!!
        assertEquals(listOf("walk", "run"), updated.run!!.intervals.map { it.id })
        assertEquals(180, updated.run.intervals.last().durationSeconds)

        val moved = updated.moveRunInterval("run", -1)
        assertEquals(listOf("run", "walk"), moved.run!!.intervals.map { it.id })
        assertSame(moved, moved.moveRunInterval("run", -1))
    }

    @Test fun canonicalRoutineCannotLoseItsLastInterval() {
        val one = original.copy(run = RunRoutine(listOf(original.run!!.intervals.first())))
        assertNull(one.withoutRunInterval("walk"))
        assertTrue(one.withoutRunInterval("walk", allowEmpty = true)!!.run!!.intervals.isEmpty())
    }

    @Test fun newDraftNormalizesRevisionOnlyAtExplicitSave() {
        val draft = original.copy(revision = 1, name = "", run = RunRoutine(emptyList()))
        assertFalse(draft.isSaveableRunRoutine())
        val ready = draft.withRunIdentity("  Park intervals  ", "running_shoe")!!
            .withRunInterval(RunInterval("run", RunIntervalKind.RUN, 60))!!
            .forRunEditorSave(draft, isNew = true)
        assertEquals(1, ready.revision)
        assertEquals("Park intervals", ready.name)
        assertTrue(ready.isSaveableRunRoutine())
    }

    @Test fun nonRunRoutinesRejectRunMutations() {
        val guided = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }
        assertNull(guided.withRunIdentity("Run", "running_shoe"))
        assertNull(guided.withRunInterval(RunInterval("run", RunIntervalKind.RUN, 60)))
        assertSame(guided, guided.moveRunInterval("run", 1))
    }
}
