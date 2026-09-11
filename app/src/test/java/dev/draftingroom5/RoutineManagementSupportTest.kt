package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RoutineManagementSupportTest {
    @Test fun unifiedMetadataDescribesGuidedAndLinkedRoutinesWithoutOriginLabels() {
        val plan = defaultTrainingPlan()
        val guided = plan.routines.single { it.execution == RoutineExecution.GUIDED }
        val linked = plan.routines.first { it.execution == RoutineExecution.LINKED_APP }

        assertEquals("Guided Routine · 7 exercises", guided.routineListMetadata())
        assertTrue(linked.routineListMetadata().startsWith("Linked App · "))
        assertTrue("built-in" !in guided.routineListMetadata().lowercase())
        assertTrue("custom" !in guided.routineListMetadata().lowercase())
    }

    @Test fun scheduleSummaryUsesDistinctRecurringWeekdaysAndHandlesUnscheduledRoutines() {
        val plan = defaultTrainingPlan()
        val guided = plan.routines.single { it.execution == RoutineExecution.GUIDED }
        val duplicated = plan.copy(schedule = plan.schedule + ScheduleEntry(
            id = "another-forearm",
            routineId = guided.id,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
        ))

        assertEquals("Scheduled Mon · Sat", duplicated.routineScheduleSummary(guided.id))
        assertEquals("Not scheduled", plan.routineScheduleSummary("missing"))
    }

    @Test fun deleteConfirmationNamesScheduleAndPartialSessionCascades() {
        val plan = defaultTrainingPlan()
        val guided = plan.routines.single { it.execution == RoutineExecution.GUIDED }
        val twoEntries = plan.copy(schedule = plan.schedule + ScheduleEntry(
            id = "another-forearm",
            routineId = guided.id,
            days = setOf(DayOfWeek.SUNDAY),
        ))

        assertEquals(
            "This permanently deletes the routine, 2 scheduled entries and 1 saved partial session, and their references. Workout history remains available.",
            routineDeletionMessage(twoEntries, guided, partialSessionCount = 1),
        )
        assertEquals(
            "This permanently deletes the routine. Workout history remains available.",
            routineDeletionMessage(plan.copy(schedule = emptyList()), guided, partialSessionCount = 0),
        )
    }
}
