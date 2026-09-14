package dev.draftingroom5

import java.io.IOException
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
        val forearm = plan.routines.single { it.id == "routine-forearm" }
        assertEquals(8, forearm.exercises.size)
        assertEquals(Exercise("exercise-hangboard", "Hangboard Holds", "Controlled edge hold", 3, "20 sec", 20, "hangboard"), forearm.exercises.first())
    }

    @Test fun removingRoutineCascadesOnlyItsLiveScheduleReferences() {
        val plan = defaultTrainingPlan()
        val updated = plan.removeRoutine("routine-forearm")
        assertFalse(updated.routines.any { it.id == "routine-forearm" })
        assertFalse(updated.schedule.any { it.routineId == "routine-forearm" })
        assertEquals(2, updated.routines.size)
    }

    @Test fun weekdayCountsMatchTheVisibleRecurringItems() {
        val plan = defaultTrainingPlan()

        assertEquals(2, plan.scheduleCounts().getValue(DayOfWeek.MONDAY))
        assertEquals(1, plan.scheduleCounts().getValue(DayOfWeek.WEDNESDAY))
        assertEquals(0, plan.scheduleCounts().getValue(DayOfWeek.SUNDAY))
        assertEquals(plan.forDay(DayOfWeek.THURSDAY).size, plan.scheduleCounts().getValue(DayOfWeek.THURSDAY))
    }

    @Test fun deletingScheduleEntryLeavesItsRoutineAndOtherRecurrencesIntact() {
        val plan = defaultTrainingPlan()
        val removed = plan.removeScheduleEntry("schedule-running")

        assertEquals(plan.routines, removed.routines)
        assertFalse(removed.schedule.any { it.id == "schedule-running" })
        assertTrue(removed.schedule.any { it.id == "schedule-strength" })
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

    @Test fun everyVisibleScheduleItemMovesToValidPositionsAndStopsAtBounds() {
        val routine = defaultTrainingPlan().routines.first()
        val entries = (1..4).map { ScheduleEntry("entry-$it", routine.id, setOf(DayOfWeek.MONDAY)) }
        val plan = TrainingPlan(listOf(routine), entries)

        assertEquals("entry-1", plan.moveScheduleOnDay(DayOfWeek.MONDAY, "entry-1", -1).schedule.first().id)
        assertEquals("entry-4", plan.moveScheduleOnDay(DayOfWeek.MONDAY, "entry-4", 1).schedule.last().id)
        assertEquals(listOf("entry-2", "entry-1", "entry-3", "entry-4"), plan.moveScheduleOnDay(DayOfWeek.MONDAY, "entry-2", -1).schedule.map { it.id })
        assertEquals(listOf("entry-1", "entry-3", "entry-2", "entry-4"), plan.moveScheduleOnDay(DayOfWeek.MONDAY, "entry-2", 1).schedule.map { it.id })
    }

    @Test fun dragPreviewsMultipleBoundariesAndWritesOnlyOnceOnDrop() {
        val routine = defaultTrainingPlan().routines.first()
        val original = TrainingPlan(listOf(routine), (1..4).map {
            ScheduleEntry("entry-$it", routine.id, setOf(DayOfWeek.MONDAY))
        })
        val drag = ScheduleDragSession(original, DayOfWeek.MONDAY, "entry-1")
        var writes = 0

        assertTrue(drag.move(1))
        assertTrue(drag.move(1))
        assertTrue(drag.move(1))
        assertFalse(drag.move(1))
        assertEquals(listOf("entry-2", "entry-3", "entry-4", "entry-1"), drag.preview.schedule.map { it.id })
        assertEquals(listOf("entry-1", "entry-2", "entry-3", "entry-4"), original.schedule.map { it.id })

        val committed = drag.drop { writes++; true }
        assertEquals(1, writes)
        assertEquals(drag.preview, committed)
        assertEquals(committed, drag.drop { writes++; true })
        assertEquals(committed, drag.cancel())
        assertEquals(1, writes)
    }

    @Test fun canceledAndFailedDragsRestoreTheOriginalOrder() {
        val original = defaultTrainingPlan()
        val canceled = ScheduleDragSession(original, DayOfWeek.MONDAY, "schedule-strength")
        assertTrue(canceled.move(1))
        assertEquals(original, canceled.cancel())
        assertEquals(original, canceled.drop { error("Canceled drag must not persist") })

        val failed = ScheduleDragSession(original, DayOfWeek.MONDAY, "schedule-strength")
        assertTrue(failed.move(1))
        var writes = 0
        assertEquals(original, failed.drop { writes++; false })
        assertEquals(1, writes)
        assertEquals(original, failed.drop { writes++; true })
        assertEquals(1, writes)
    }

    @Test fun droppedDragOrderSurvivesRepositoryRecreation() {
        val storage = ScheduleDocumentStorage()
        val repository = AppRepository(storage)
        val initial = (repository.load() as LoadState.Ready).value
        val drag = ScheduleDragSession(initial.plan, DayOfWeek.MONDAY, "schedule-strength")
        assertTrue(drag.move(1))
        val committed = drag.drop {
            repository.replacePlan(initial.generation, it) is RepositoryResult.Success
        }
        val recreated = (AppRepository(storage).load() as LoadState.Ready).value.plan
        assertEquals(committed, recreated)
        assertEquals(listOf("schedule-running", "schedule-strength"), recreated.forDay(DayOfWeek.MONDAY).map { it.id })
    }

    @Test fun scheduleReorderDeleteAndDaysPersistThroughCurrentStore() {
        val storage = ScheduleDocumentStorage()
        val repository = AppRepository(storage)
        val original = (repository.load() as LoadState.Ready).value
        val moved = original.plan.moveScheduleOnDay(DayOfWeek.MONDAY, "schedule-running", -1)
        val edited = moved.copy(
            schedule = moved.schedule.map {
                if (it.id == "schedule-running") it.copy(days = setOf(DayOfWeek.SUNDAY)) else it
            },
        ).removeScheduleEntry("schedule-strength")

        val saved = repository.replacePlan(original.generation, edited) as RepositoryResult.Success
        val restored = decodeAppDocument(checkNotNull(storage.value)).plan

        assertEquals(edited, saved.value.plan)
        assertEquals(edited, restored)
        assertEquals(listOf("schedule-running"), restored.forDay(DayOfWeek.SUNDAY).map { it.id })
        assertFalse(restored.schedule.any { it.id == "schedule-strength" })
    }
}

private class ScheduleDocumentStorage : DocumentStorage {
    var value: String? = null
    override fun exists() = value != null
    override fun read(): String = value ?: throw IOException("missing")
    override fun write(value: String) { this.value = value }
}
