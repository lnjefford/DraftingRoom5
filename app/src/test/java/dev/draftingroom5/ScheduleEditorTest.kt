package dev.draftingroom5

import java.io.IOException
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditorTest {
    private val plan = defaultTrainingPlan()

    @Test fun newActivityRequiresSelectionAndSavesOnlyOnChosenDay() {
        val initial = ScheduleEditorDraft.initial(null, DayOfWeek.SUNDAY)
        assertNull(plan.saveScheduleDraft(null, initial, "schedule-new"))

        val updated = requireNotNull(plan.saveScheduleDraft(null, initial.selectRoutine(plan.routines.first().id), "schedule-new"))
        assertEquals(4, updated.schedule.size)
        assertEquals(ScheduleEntry("schedule-new", plan.routines.first().id, setOf(DayOfWeek.SUNDAY)), updated.schedule.last())
        assertEquals(plan.forDay(DayOfWeek.MONDAY).map { it.id }, updated.forDay(DayOfWeek.MONDAY).map { it.id })
    }

    @Test fun editingOneDayOfLegacyMultiDayEntryKeepsOtherDays() {
        val original = plan.schedule.first()
        val replacement = plan.routines.last().id
        val draft = ScheduleEditorDraft.initial(original, DayOfWeek.TUESDAY).selectRoutine(replacement)

        val updated = requireNotNull(plan.saveScheduleDraft(original.id, draft, "schedule-tuesday"))
        assertEquals(plan.schedule.size + 1, updated.schedule.size)
        assertEquals(original.copy(days = original.days - DayOfWeek.TUESDAY), updated.schedule.first())
        assertEquals(ScheduleEntry("schedule-tuesday", replacement, setOf(DayOfWeek.TUESDAY)), updated.schedule[1])
        assertEquals(plan.forDay(DayOfWeek.MONDAY).map { it.id }, updated.forDay(DayOfWeek.MONDAY).map { it.id })
    }

    @Test fun editingSingleDayPreservesIdentityAndOrder() {
        val original = plan.schedule.last()
        val draft = ScheduleEditorDraft.initial(original, DayOfWeek.SATURDAY).selectRoutine(plan.routines.first().id)
        val updated = requireNotNull(plan.saveScheduleDraft(original.id, draft, "unused"))

        assertEquals(plan.schedule.map { it.id }, updated.schedule.map { it.id })
        assertEquals(original.id, updated.schedule.last().id)
        assertEquals(setOf(DayOfWeek.SATURDAY), updated.schedule.last().days)
    }

    @Test fun invalidRoutineOrWrongDayCannotSave() {
        val missingRoutine = ScheduleEditorDraft("missing", DayOfWeek.MONDAY)
        assertNull(plan.saveScheduleDraft(null, missingRoutine, "new"))
        assertNull(plan.saveScheduleDraft("missing-entry", missingRoutine.selectRoutine(plan.routines.first().id), "new"))
        assertNull(plan.saveScheduleDraft(plan.schedule.first().id, ScheduleEditorDraft(plan.routines.first().id, DayOfWeek.SUNDAY), "new"))
    }

    @Test fun removingOneDayLeavesOtherDaysScheduled() {
        val original = plan.schedule.first()
        val updated = plan.removeScheduleOnDay(original.id, DayOfWeek.TUESDAY)
        assertEquals(original.copy(days = original.days - DayOfWeek.TUESDAY), updated.schedule.first())
        assertEquals(plan.forDay(DayOfWeek.MONDAY).map { it.id }, updated.forDay(DayOfWeek.MONDAY).map { it.id })
        assertTrue(updated.forDay(DayOfWeek.TUESDAY).none { it.id == original.id })
        assertEquals(plan.schedule.size - 1, plan.removeScheduleOnDay(plan.schedule.last().id, DayOfWeek.SATURDAY).schedule.size)
    }

    @Test fun daySpecificActivityPersistsThroughDocumentStore() {
        val storage = ScheduleEditorStorage()
        val repository = AppRepository(storage)
        val document = (repository.load() as LoadState.Ready).value
        val draft = ScheduleEditorDraft.initial(null, DayOfWeek.SUNDAY).selectRoutine(document.plan.routines.first().id)
        val updated = requireNotNull(document.plan.saveScheduleDraft(null, draft, "schedule-editor-test"))

        repository.replacePlan(document.generation, updated) as RepositoryResult.Success
        val restored = decodeAppDocument(checkNotNull(storage.value)).plan

        assertEquals(updated, restored)
        assertEquals(setOf(DayOfWeek.SUNDAY), restored.schedule.last().days)
    }
}

private class ScheduleEditorStorage : DocumentStorage {
    var value: String? = null
    override fun exists() = value != null
    override fun read(): String = value ?: throw IOException("missing")
    override fun write(value: String) { this.value = value }
}
