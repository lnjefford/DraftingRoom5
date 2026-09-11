package dev.draftingroom5

import java.io.IOException
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditorTest {
    private val plan = defaultTrainingPlan()

    @Test fun everyRepeatShortcutProducesOnlyTheAuthoritativeWeekdaySet() {
        val initial = ScheduleEditorDraft.initial(null, plan.routines, DayOfWeek.THURSDAY)

        assertEquals(setOf(DayOfWeek.THURSDAY), initial.applyRepeat(ScheduleRepeat.WEEKLY).days)
        assertEquals(DayOfWeek.entries.take(5).toSet(), initial.applyRepeat(ScheduleRepeat.WEEKDAYS).days)
        assertEquals(DayOfWeek.entries.toSet(), initial.applyRepeat(ScheduleRepeat.DAILY).days)
        assertEquals(initial.days, initial.applyRepeat(ScheduleRepeat.CUSTOM).days)
    }

    @Test fun manualDayChangesBecomeCustomAndCanBeInvalidUntilCorrected() {
        val initial = ScheduleEditorDraft.initial(null, plan.routines, DayOfWeek.SATURDAY)
        val empty = initial.toggleDay(DayOfWeek.SATURDAY)

        assertEquals(ScheduleRepeat.CUSTOM, empty.repeat)
        assertTrue(empty.days.isEmpty())
        assertNull(empty.toEntry(null, "new-entry", plan.routines))

        val corrected = empty.toggleDay(DayOfWeek.SUNDAY)
        assertEquals(setOf(DayOfWeek.SUNDAY), corrected.days)
        assertEquals("Every Sunday", corrected.days.recurrenceDescription(Locale.ENGLISH))
    }

    @Test fun recurrencePreviewCoversWeeklyWeekdaysDailyAndCustom() {
        assertEquals("Every Monday", setOf(DayOfWeek.MONDAY).recurrenceDescription(Locale.ENGLISH))
        assertEquals("Every weekday", DayOfWeek.entries.take(5).toSet().recurrenceDescription(Locale.ENGLISH))
        assertEquals("Every day", DayOfWeek.entries.toSet().recurrenceDescription(Locale.ENGLISH))
        assertEquals("Every Tuesday, Thursday", setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY).recurrenceDescription(Locale.ENGLISH))
        assertEquals("Choose at least one day", emptySet<DayOfWeek>().recurrenceDescription(Locale.ENGLISH))
    }

    @Test fun addAndEditRequireExplicitValidSaveAndPreserveEntryIdentityAndOrder() {
        val initial = ScheduleEditorDraft.initial(null, plan.routines, DayOfWeek.SUNDAY)
        assertEquals(3, plan.schedule.size) // Opening/cancelling has no canonical mutation.

        val added = requireNotNull(plan.saveScheduleDraft(null, initial, "schedule-new"))
        assertEquals(4, added.schedule.size)
        assertEquals("schedule-new", added.schedule.last().id)
        assertEquals(setOf(DayOfWeek.SUNDAY), added.schedule.last().days)

        val original = added.schedule[1]
        val editedDraft = ScheduleEditorDraft.initial(original, added.routines, DayOfWeek.MONDAY)
            .selectRoutine(added.routines.last().id)
            .applyRepeat(ScheduleRepeat.DAILY)
        val edited = requireNotNull(added.saveScheduleDraft(original.id, editedDraft, "ignored"))
        assertEquals(added.schedule.map { it.id }, edited.schedule.map { it.id })
        assertEquals(original.id, edited.schedule[1].id)
        assertEquals(added.routines.last().id, edited.schedule[1].routineId)
        assertEquals(DayOfWeek.entries.toSet(), edited.schedule[1].days)
    }

    @Test fun missingRoutineAndMissingEditTargetCannotSave() {
        val missingRoutine = ScheduleEditorDraft("missing", setOf(DayOfWeek.MONDAY), ScheduleRepeat.WEEKLY, DayOfWeek.MONDAY)
        assertNull(plan.saveScheduleDraft(null, missingRoutine, "new"))

        val valid = missingRoutine.selectRoutine(plan.routines.first().id)
        assertNull(plan.saveScheduleDraft("missing-entry", valid, "ignored"))
    }

    @Test fun addedAndEditedRecurrencesPersistThroughCurrentDocumentStore() {
        val storage = ScheduleEditorStorage()
        val repository = AppRepository(storage)
        val document = (repository.load() as LoadState.Ready).value
        val draft = ScheduleEditorDraft.initial(null, document.plan.routines, DayOfWeek.SUNDAY)
            .applyRepeat(ScheduleRepeat.WEEKDAYS)
        val updated = requireNotNull(document.plan.saveScheduleDraft(null, draft, "schedule-editor-test"))

        repository.replacePlan(document.generation, updated) as RepositoryResult.Success
        val restored = decodeAppDocument(checkNotNull(storage.value)).plan

        assertEquals(updated, restored)
        assertEquals(DayOfWeek.entries.take(5).toSet(), restored.schedule.last().days)
        assertEquals(draft.routineId, restored.schedule.last().routineId)
    }
}

private class ScheduleEditorStorage : DocumentStorage {
    var value: String? = null
    override fun exists() = value != null
    override fun read(): String = value ?: throw IOException("missing")
    override fun write(value: String) { this.value = value }
}
