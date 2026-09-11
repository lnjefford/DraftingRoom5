package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleDataTest {
    @Test fun defaultsHaveSharedRoutineIdentityAndSevenOccurrences() {
        val plan = defaultTrainingPlan()
        assertEquals(3, plan.routines.size)
        assertEquals(3, plan.schedule.size)
        assertEquals(7, DayOfWeek.entries.sumOf { plan.forDay(it).size })
        assertEquals(2, plan.forDay(DayOfWeek.MONDAY).size)
        assertEquals("Forearm & Grip Conditioning", plan.routineFor(plan.forDay(DayOfWeek.SATURDAY).single()).name)
        assertTrue(plan.forDay(DayOfWeek.SUNDAY).isEmpty())
        assertEquals(3, plan.routines.single { it.id == "routine-forearm" }.exercises.single { it.id == "exercise-finger-extension" }.setCount)
    }

    @Test fun removingRoutineCascadesOnlyItsLiveScheduleReferences() {
        val plan = defaultTrainingPlan()
        val updated = plan.removeRoutine("routine-forearm")
        assertFalse(updated.routines.any { it.id == "routine-forearm" })
        assertFalse(updated.schedule.any { it.routineId == "routine-forearm" })
        assertEquals(2, updated.routines.size)
    }

    @Test fun recurrencePresetsDoNotRepairAnEmptyCustomSelection() {
        assertEquals(setOf(DayOfWeek.THURSDAY), repeatDays(ScheduleRepeat.WEEKLY, DayOfWeek.THURSDAY, emptySet()))
        assertEquals(DayOfWeek.entries.take(5).toSet(), repeatDays(ScheduleRepeat.WEEKDAYS, DayOfWeek.SUNDAY, emptySet()))
        assertEquals(DayOfWeek.entries.toSet(), repeatDays(ScheduleRepeat.DAILY, DayOfWeek.MONDAY, emptySet()))
        assertTrue(repeatDays(ScheduleRepeat.CUSTOM, DayOfWeek.MONDAY, emptySet()).isEmpty())
    }

    @Test fun filteredDayReorderWritesBackIntoOccupiedGlobalSlots() {
        val routine = defaultTrainingPlan().routines.first()
        val a = ScheduleEntry("a", routine.id, setOf(DayOfWeek.MONDAY))
        val x = ScheduleEntry("x", routine.id, setOf(DayOfWeek.TUESDAY))
        val b = ScheduleEntry("b", routine.id, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        val plan = TrainingPlan(listOf(routine), listOf(a, x, b))
        val moved = plan.moveScheduleOnDay(DayOfWeek.MONDAY, "b", -1)
        assertEquals(listOf("b", "x", "a"), moved.schedule.map { it.id })
        assertEquals(listOf("b", "x"), moved.forDay(DayOfWeek.TUESDAY).map { it.id })
    }
}
