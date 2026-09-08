package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleDataTest {
    @Test
    fun defaultPlan_preservesBuiltInWeekAndRoutine() {
        val plan = defaultTrainingPlan()

        assertEquals(7, plan.schedule.size)
        assertEquals(1, plan.routines.size)
        assertEquals(7, plan.routines.single().exercises.size)
        assertEquals(2, plan.forDay(DayOfWeek.MONDAY).size)
        assertEquals("Forearm & Grip Conditioning", plan.forDay(DayOfWeek.SATURDAY).single().title)
        assertTrue(plan.forDay(DayOfWeek.SUNDAY).isEmpty())
    }

    @Test
    fun disabledItems_areHiddenFromToday() {
        val plan = defaultTrainingPlan()
        val disabled = plan.schedule.first().copy(enabled = false)
        val updated = plan.copy(schedule = listOf(disabled) + plan.schedule.drop(1))

        assertEquals(1, updated.forDay(DayOfWeek.MONDAY).size)
        assertFalse(updated.forDay(DayOfWeek.MONDAY).any { it.id == disabled.id })
    }

    @Test
    fun move_reordersWithoutDroppingItems() {
        assertEquals(listOf("b", "a", "c"), move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a", "b", "c"), move(listOf("a", "b", "c"), 0, -1))
    }

    @Test
    fun removingRoutine_removesSchedulesThatReferenceIt() {
        val plan = defaultTrainingPlan()
        val updated = plan.removeRoutine("forearm")

        assertTrue(updated.routines.isEmpty())
        assertFalse(updated.schedule.any { it.destination == Destination.CUSTOM })
    }

    @Test
    fun repeatPresetsCreateExpectedWeeklyDays() {
        assertEquals(setOf(DayOfWeek.THURSDAY), repeatDays(ScheduleRepeat.WEEKLY, DayOfWeek.THURSDAY, emptySet()))
        assertEquals(DayOfWeek.entries.take(5).toSet(), repeatDays(ScheduleRepeat.WEEKDAYS, DayOfWeek.SUNDAY, emptySet()))
        assertEquals(DayOfWeek.entries.toSet(), repeatDays(ScheduleRepeat.DAILY, DayOfWeek.MONDAY, emptySet()))
    }

    @Test
    fun repeatedScheduleAppearsOnEachSelectedDay() {
        val item = ScheduledItem(
            "repeat", "Mobility", "", DayOfWeek.MONDAY, Destination.CUSTOM,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val plan = TrainingPlan(listOf(item), emptyList())

        assertEquals(listOf(item), plan.forDay(DayOfWeek.WEDNESDAY))
        assertTrue(plan.forDay(DayOfWeek.TUESDAY).isEmpty())
    }

    @Test
    fun olderSavedPlansDefaultToTheirSingleAssignedDay() {
        val legacyJson = """{"schedule":[{"id":"one","title":"Workout","subtitle":"","day":"TUESDAY","destination":"FITBOD","routineId":null,"enabled":true}],"routines":[]}"""

        assertEquals(setOf(DayOfWeek.TUESDAY), decodePlan(legacyJson).schedule.single().activeDays())
    }
}
