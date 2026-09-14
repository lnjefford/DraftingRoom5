package dev.draftingroom5

import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OccurrenceExceptionsTest {
    private val day = LocalDate.of(2026, 9, 12)
    private val linked = defaultTrainingPlan().routines.first()
    private val guided = defaultTrainingPlan().routines.last().let {
        it.copy(exercises = listOf(it.exercises.first().copy(setCount = 1)))
    }
    private val plan = TrainingPlan(listOf(linked, guided), listOf(
        ScheduleEntry("daily", linked.id, DayOfWeek.entries.toSet()),
        ScheduleEntry("other", linked.id, DayOfWeek.entries.toSet()),
        ScheduleEntry("guided", guided.id, DayOfWeek.entries.toSet()),
    ))
    private fun key(date: LocalDate = day, id: String = "daily") = OccurrenceKey(id, date)
    private fun repository(store: Store = Store(), clock: SessionClock? = null) = AppRepository(store, clock).also { it.load() }
    private fun AppRepository.document() = (state.value as LoadState.Ready).value
    private fun AppRepository.cards(date: LocalDate) = dashboardSessions(document(), date)

    @Test fun dailyDeferPreservesBothTomorrowIdentitiesAndEveryOtherRepetition() {
        val store = Store()
        val repo = repository(store)
        val original = repo.document()
        assertEquals(OccurrenceDisposition.SCHEDULED, original.occurrenceDisposition(key()))
        assertTrue(repo.deferOccurrence(key(), day) is RepositoryResult.Success)
        assertEquals(original.plan, repo.document().plan)
        assertEquals(listOf("other", "guided"), repo.cards(day).map { it.scheduleEntry.id })
        val tomorrow = repo.cards(day.plusDays(1))
        assertEquals(4, tomorrow.size)
        assertEquals(4, tomorrow.map { it.occurrence }.distinct().size)
        assertEquals(setOf(key(), key(day.plusDays(1))), tomorrow.filter { it.scheduleEntry.id == "daily" }.map { it.occurrence }.toSet())
        assertTrue(tomorrow.all { it.effectiveDate == day.plusDays(1) })
        for (date in listOf(day.minusWeeks(1), day.minusDays(1), day.plusDays(2), day.plusWeeks(1))) {
            assertEquals(dashboardSessions(original, date), repo.cards(date))
        }
        val saved = repo.document()
        val writes = store.writes
        assertTrue(repo.deferOccurrence(key(), day.plusDays(1)) is RepositoryResult.Success)
        assertEquals(saved, repo.document())
        assertEquals(writes, store.writes)
        assertTrue(repo.skipOccurrence(key(), day) is RepositoryResult.Invalid)
        assertEquals(saved, repository(store).document())
    }

    @Test fun skipCreatesNoHistoryAndRejectsStaleLaunchOrSessionStart() {
        val repo = repository(clock = clock())
        assertTrue(repo.skipOccurrence(key(), day) is RepositoryResult.Success)
        assertTrue(repo.skipOccurrence(key(), day.plusDays(1)) is RepositoryResult.Success)
        assertEquals(2, repo.cards(day).size)
        assertEquals(3, repo.cards(day.plusWeeks(1)).size)
        assertTrue(repo.document().history.isEmpty())
        assertNull(repo.document().occurrenceDate(key()))
        assertTrue(repo.completeLinkedOccurrence(key(), linked.id, 100) is RepositoryResult.Invalid)
        assertTrue(repo.skipOccurrence(key(id = "guided"), day) is RepositoryResult.Success)
        assertTrue(repo.openGuidedSession(repo.sessionLease()!!, key(id = "guided"), guided.id, 1, "new") is SessionRepositoryResult.Invalid)
    }

    @Test fun undoIsAtomicIdempotentAndCannotUndoADifferentDisposition() {
        val store = Store()
        val repo = repository(store)
        repo.deferOccurrence(key(), day)
        val exception = repo.document().occurrenceExceptions.single()
        val saved = repo.document()
        store.fail = true
        assertTrue(repo.undoOccurrenceException(exception) is RepositoryResult.Failed)
        assertEquals(saved, repo.document())
        assertEquals(saved, decodeAppDocument(store.value))
        store.fail = false
        assertTrue(repo.undoOccurrenceException(exception) is RepositoryResult.Success)
        val undone = repo.document()
        assertTrue(repo.undoOccurrenceException(exception) is RepositoryResult.Success)
        assertEquals(undone, repo.document())
        assertEquals(3, repo.cards(day).size)
        assertEquals(3, repo.cards(day.plusDays(1)).size)
        repo.skipOccurrence(key(), day)
        assertTrue(repo.undoOccurrenceException(exception) is RepositoryResult.Invalid)
        assertTrue(repo.undoOccurrenceException(repo.document().occurrenceExceptions.single()) is RepositoryResult.Success)
    }

    @Test fun failedDeferAndSkipPublishNothingAndRetriesWriteOnlyOnce() {
        for (defer in listOf(true, false)) {
            val store = Store()
            val repo = repository(store)
            val original = repo.document()
            store.fail = true
            val result = if (defer) repo.deferOccurrence(key(), day) else repo.skipOccurrence(key(), day)
            assertTrue(result is RepositoryResult.Failed)
            assertEquals(original, repo.document())
            assertEquals(original, decodeAppDocument(store.value))
            store.fail = false
            assertTrue((if (defer) repo.deferOccurrence(key(), day) else repo.skipOccurrence(key(), day)) is RepositoryResult.Success)
            assertEquals(1, store.writes)
        }
    }

    @Test fun deferredLinkedCompletionAndUndoResolveSourceOnly() {
        val repo = repository()
        repo.deferOccurrence(key(), day)
        val exception = repo.document().occurrenceExceptions.single()
        assertTrue(repo.completeLinkedOccurrence(key(), linked.id, 100) is RepositoryResult.Success)
        val record = repo.document().history.single()
        assertEquals(key(), record.occurrence)
        assertEquals(day.plusDays(1), record.effectiveDate)
        assertEquals(SessionAction.DONE, repo.cards(day.plusDays(1)).single { it.occurrence == key() }.action)
        assertEquals(SessionAction.START, repo.cards(day.plusDays(1)).single { it.occurrence == key(day.plusDays(1)) }.action)
        assertTrue(completedOccurrencesForDate(repo.document().history, day).isEmpty())
        assertEquals(listOf(key()), completedOccurrencesForDate(repo.document().history, day.plusDays(1)))
        val saved = repo.document()
        assertTrue(repo.completeLinkedOccurrence(key(), linked.id, 101) is RepositoryResult.Success)
        assertEquals(saved, repo.document())
        assertTrue(repo.deferOccurrence(key(), day) is RepositoryResult.Invalid)
        assertTrue(repo.skipOccurrence(key(), day) is RepositoryResult.Invalid)
        assertTrue(repo.undoOccurrenceException(exception) is RepositoryResult.Invalid)
        assertTrue(repo.undoLinkedOccurrence(key()) is RepositoryResult.Success)
        assertEquals(SessionAction.START, repo.cards(day.plusDays(1)).single { it.occurrence == key() }.action)
        assertEquals(listOf(exception), repo.document().occurrenceExceptions)
    }

    @Test fun deferredGuidedSessionSurvivesRestartRecreationAndCompletionWriteFailure() {
        val store = Store()
        var repo = repository(store, clock())
        val source = key(id = "guided")
        repo.deferOccurrence(source, day)
        var session = (repo.openGuidedSession(repo.sessionLease()!!, source, guided.id, 1, "s") as SessionRepositoryResult.Partial).session
        assertEquals(day.plusDays(1), session.effectiveDate)
        assertTrue(repo.deferOccurrence(source, day) is RepositoryResult.Invalid)
        assertTrue(repo.skipOccurrence(source, day) is RepositoryResult.Invalid)
        assertTrue(repo.undoOccurrenceException(repo.document().occurrenceExceptions.single()) is RepositoryResult.Invalid)
        assertTrue(savedDashboardSessions(plan, repo.document().partialSessions, day.plusDays(1), repo.document().occurrenceExceptions).isEmpty())
        assertEquals(1, savedDashboardSessions(plan, repo.document().partialSessions, day, repo.document().occurrenceExceptions).size)
        session = (repo.restartGuidedSession(repo.sessionLease()!!, session.id, 0, 1, "restart") as SessionRepositoryResult.Partial).session
        assertEquals(day.plusDays(1), session.effectiveDate)
        repo = repository(store, clock())
        session = (repo.applySessionEvent(repo.sessionLease()!!, session.id, 0,
            SessionEvent.CompleteSet(guided.exercises.single().id, 1)) as SessionRepositoryResult.Partial).session
        val beforeFinish = repo.document()
        store.fail = true
        assertTrue(repo.finishGuidedSession(repo.sessionLease()!!, session.id, session.eventRevision) is SessionRepositoryResult.Failed)
        assertEquals(beforeFinish, repo.document())
        store.fail = false
        val done = repo.finishGuidedSession(repo.sessionLease()!!, session.id, session.eventRevision) as SessionRepositoryResult.Complete
        assertEquals(source, done.history.occurrence)
        assertEquals(day.plusDays(1), done.history.effectiveDate)
        assertEquals(SessionAction.DONE, repo.cards(day.plusDays(1)).single { it.occurrence == source }.action)
        assertEquals(SessionAction.START, repo.cards(day.plusDays(1)).single { it.occurrence == key(day.plusDays(1), "guided") }.action)
        assertFalse((repo.finishGuidedSession(repo.sessionLease()!!, session.id, session.eventRevision) as SessionRepositoryResult.Complete).newlyCompleted)
    }

    @Test fun twoCompletedCardsOnSameEffectiveDayRemainTwoHistoryOccurrences() {
        val repo = repository()
        repo.deferOccurrence(key(), day)
        repo.completeLinkedOccurrence(key(), linked.id, 100)
        repo.completeLinkedOccurrence(key(day.plusDays(1)), linked.id, 101)
        assertEquals(setOf(key(), key(day.plusDays(1))),
            completedOccurrencesForDate(repo.document().history, day.plusDays(1)).toSet())
        assertEquals(2, repo.cards(day.plusDays(1)).count { it.action == SessionAction.DONE })
        repo.undoLinkedOccurrence(key(day.plusDays(1)))
        assertEquals(listOf(key()), completedOccurrencesForDate(repo.document().history, day.plusDays(1)))
        assertEquals(SessionAction.DONE, repo.cards(day.plusDays(1)).single { it.occurrence == key() }.action)
    }

    @Test fun failedDeletionResetAndRestoreKeepExceptionsAndLease() {
        val store = Store()
        val repo = repository(store)
        repo.deferOccurrence(key(), day)
        repo.skipOccurrence(key(id = "other"), day)
        val saved = repo.document()
        val lease = repo.sessionLease()
        store.fail = true
        assertTrue(repo.deleteScheduleEntry(saved.generation, "daily") is RepositoryResult.Failed)
        assertTrue(repo.deleteRoutine(saved.generation, linked.id) is RepositoryResult.Failed)
        assertTrue(repo.resetPlan(saved.generation) is RepositoryResult.Failed)
        assertTrue(repo.restore(defaultAppDocument()) is RepositoryResult.Failed)
        assertEquals(saved, repo.document())
        assertEquals(saved, decodeAppDocument(store.value))
        assertEquals(lease, repo.sessionLease())
    }

    @Test fun partialCodecRequiresEffectiveDateAndRejectsSkippedOrContradictoryProgress() {
        val repo = repository(clock = clock())
        repo.deferOccurrence(key(id = "guided"), day)
        repo.openGuidedSession(repo.sessionLease()!!, key(id = "guided"), guided.id, 1, "s")
        val encoded = encodeAppDocument(repo.document())
        assertEquals(repo.document(), decodeAppDocument(encoded))
        listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("partialSessions").getJSONObject(0).remove("effectiveDate") },
            { it.getJSONArray("partialSessions").getJSONObject(0).put("effectiveDate", day.toString()) },
            { it.exception().put("disposition", "SKIPPED").put("effectiveDate", JSONObject.NULL) },
        ).forEach { corrupt ->
            val root = JSONObject(encoded)
            corrupt(root)
            assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        }
    }

    @Test fun backupRestoresDeferredPartialWithFixedDatesAndClearsOnlyItsTimer() {
        val repo = repository(clock = clock())
        val source = key(id = "guided")
        repo.deferOccurrence(source, day)
        val session = repo.openGuidedSession(repo.sessionLease()!!, source, guided.id, 1, "s") as SessionRepositoryResult.Partial
        val timed = repo.applySessionEvent(repo.sessionLease()!!, session.session.id, 0, SessionEvent.StartTimer("timer")) as SessionRepositoryResult.Partial
        assertEquals(TimerPhase.READY, timed.session.timer.phase)
        val backup = decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(300, repo.document()))).document
        assertEquals(repo.document(), backup)
        val restored = repository(clock = clock())
        assertTrue(restored.restore(backup) is RepositoryResult.Success)
        assertEquals(timed.session.copy(timer = SessionTimer()), restored.document().partialSessions.single())
        assertEquals(repo.document().occurrenceExceptions, restored.document().occurrenceExceptions)
        assertEquals(SessionAction.RESUME, restored.cards(day.plusDays(1)).single { it.occurrence == source }.action)
    }

    @Test fun civilDatesSurviveDstTimezoneChangesAndMidnightWithoutRetargeting() {
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1), LocalDate.of(2028, 2, 29), LocalDate.of(2026, 12, 31))) {
            val store = Store()
            val repo = repository(store)
            repo.deferOccurrence(key(date), date)
            val snapshot = decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(500, repo.document())))
            val restored = repository().also { assertTrue(it.restore(snapshot.document) is RepositoryResult.Success) }
            val next = date.plusDays(1)
            assertEquals(next, restored.document().occurrenceDate(key(date)))
            assertEquals(4, restored.cards(next).size)
            assertEquals(2, restored.cards(date).size)
        }
        val instant = Instant.parse("2026-09-13T04:59:59Z")
        val chicago = instant.atZone(ZoneId.of("America/Chicago")).toLocalDate()
        val tokyo = instant.atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
        assertNotEquals(chicago, tokyo)
        val repo = repository()
        repo.deferOccurrence(key(chicago), chicago)
        assertEquals(chicago.plusDays(1), decodeAppDocument(encodeAppDocument(repo.document())).occurrenceDate(key(chicago)))
        assertEquals(4, repo.cards(tokyo).size)
        assertEquals(tokyo, resolveDashboardDate(chicago, chicago, tokyo))
        assertTrue(repo.deferOccurrence(key(), day.plusDays(1)) is RepositoryResult.Success) // retry keeps original tomorrow
        assertEquals(day.plusDays(1), repo.document().occurrenceDate(key()))
    }

    @Test fun deletionPrunesUnstartedExceptionsButPreservesDurableSourceAndDate() {
        val repo = repository(clock = clock())
        repo.deferOccurrence(key(), day)
        repo.skipOccurrence(key(id = "other"), day)
        repo.deferOccurrence(key(id = "guided"), day)
        val partial = repo.openGuidedSession(repo.sessionLease()!!, key(id = "guided"), guided.id, 1, "s") as SessionRepositoryResult.Partial
        repo.completeLinkedOccurrence(key(), linked.id, 100)
        for (id in listOf("daily", "other", "guided")) {
            assertTrue(repo.deleteScheduleEntry(repo.document().generation, id) is RepositoryResult.Success)
        }
        assertEquals(2, repo.document().occurrenceExceptions.size)
        assertEquals(key(), repo.cards(day.plusDays(1)).single().occurrence)
        val saved = savedDashboardSessions(repo.document().plan, repo.document().partialSessions, day.plusDays(1), repo.document().occurrenceExceptions).single()
        assertEquals(partial.session.occurrence, saved.occurrence)
        assertEquals(day.plusDays(1), saved.effectiveDate)
        assertTrue(repo.deleteRoutine(repo.document().generation, guided.id) is RepositoryResult.Success)
        assertEquals(listOf(key()), repo.document().occurrenceExceptions.map { it.occurrence })
        assertTrue(repo.deleteRoutine(repo.document().generation, linked.id) is RepositoryResult.Success)
        assertEquals(day.plusDays(1), repo.cards(day.plusDays(1)).single().effectiveDate)
        assertEquals(repo.document(), decodeAppDocument(encodeAppDocument(repo.document())))
        assertTrue(repo.undoLinkedOccurrence(key()) is RepositoryResult.Success)
        assertTrue(repo.document().occurrenceExceptions.isEmpty())
    }

    @Test fun scheduleEditsRetainOneOffAndPlanReplacementPrunesRemovedSources() {
        val repo = repository()
        repo.deferOccurrence(key(), day)
        val changed = plan.copy(schedule = plan.schedule.map { it.copy(days = setOf(DayOfWeek.MONDAY)) })
        assertTrue(repo.replacePlan(repo.document().generation, changed) is RepositoryResult.Success)
        assertEquals(key(), repo.cards(day.plusDays(1)).single().occurrence)
        assertTrue(repo.replacePlan(repo.document().generation, changed.copy(schedule = emptyList())) is RepositoryResult.Success)
        assertTrue(repo.document().occurrenceExceptions.isEmpty())
    }

    @Test fun resetsClearExceptionsAndBackupRestoresExactCurrentSchema() {
        val repo = repository()
        repo.deferOccurrence(key(), day)
        repo.skipOccurrence(key(id = "other"), day)
        repo.completeLinkedOccurrence(key(), linked.id, 100)
        val saved = repo.document()
        val decoded = decodeBackupSnapshot(encodeBackupSnapshot(BackupSnapshot(200, saved))).document
        assertEquals(saved, decoded)
        assertTrue(repo.resetPlan(saved.generation) is RepositoryResult.Success)
        assertTrue(repo.document().occurrenceExceptions.isEmpty())
        assertTrue(repo.document().history.isEmpty())
        assertEquals(saved.preferences, repo.document().preferences)
        assertTrue(repo.restore(decoded) is RepositoryResult.Success)
        assertEquals(saved.copy(generation = repo.document().generation), repo.document())
        assertTrue(repo.resetToDefaults() is RepositoryResult.Success)
        assertEquals(defaultAppDocument().copy(generation = repo.document().generation), repo.document())
    }

    @Test fun strictCodecRejectsMissingUnknownDuplicateAndContradictoryExceptions() {
        val repo = repository()
        repo.deferOccurrence(key(), day)
        val encoded = encodeAppDocument(repo.document())
        val corruptions: List<(JSONObject) -> Unit> = listOf(
            { it.remove("occurrenceExceptions") },
            { it.put("occurrenceExceptions", JSONObject.NULL) },
            { it.getJSONArray("occurrenceExceptions").put(it.getJSONArray("occurrenceExceptions").get(0)) },
            { it.exception().put("extra", 1) },
            { it.exception().remove("effectiveDate") },
            { it.exception().put("effectiveDate", JSONObject.NULL) },
            { it.exception().put("effectiveDate", day.toString()) },
            { it.exception().put("effectiveDate", day.plusDays(2).toString()) },
            { it.exception().put("effectiveDate", "2026-02-30") },
            { it.exception().put("effectiveDate", 1) },
            { it.exception().put("disposition", "unknown") },
            { it.exception().put("disposition", "SCHEDULED") },
            { it.exception().put("disposition", "SKIPPED") },
            { it.exception().getJSONObject("occurrence").put("scheduleEntryId", "missing") },
        )
        corruptions.forEach { corrupt ->
            val root = JSONObject(encoded)
            corrupt(root)
            assertThrows(root.toString(), IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        }
        repo.completeLinkedOccurrence(key(), linked.id, 100)
        val complete = encodeAppDocument(repo.document())
        listOf<(JSONObject) -> Unit>(
            { it.exception().put("disposition", "SKIPPED").put("effectiveDate", JSONObject.NULL) },
            { it.getJSONArray("history").getJSONObject(0).remove("effectiveDate") },
            { it.getJSONArray("history").getJSONObject(0).put("effectiveDate", day.toString()) },
            { it.put("occurrenceExceptions", org.json.JSONArray()) },
        ).forEach { corrupt ->
            val root = JSONObject(complete)
            corrupt(root)
            assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        }
    }

    @Test fun invalidDatesMissingEntriesAndCompletedOrdinaryOccurrencesCannotChange() {
        val repo = repository()
        assertTrue(repo.deferOccurrence(key(day.plusDays(1)), day) is RepositoryResult.Invalid)
        assertTrue(repo.skipOccurrence(key(day.minusDays(1)), day) is RepositoryResult.Invalid)
        assertTrue(repo.deferOccurrence(key(id = "missing"), day) is RepositoryResult.Invalid)
        assertTrue(repo.deferOccurrence(key(LocalDate.MAX), LocalDate.MAX) is RepositoryResult.Invalid)
        repo.completeLinkedOccurrence(key(), linked.id, 100)
        assertTrue(repo.deferOccurrence(key(), day) is RepositoryResult.Invalid)
        assertTrue(repo.skipOccurrence(key(), day) is RepositoryResult.Invalid)
        assertTrue(repo.document().occurrenceExceptions.isEmpty())
    }

    private fun JSONObject.exception() = getJSONArray("occurrenceExceptions").getJSONObject(0)
    private fun clock() = object : SessionClock { override fun sample() = SessionClockSample(100, 200, 1) }
    private inner class Store : DocumentStorage {
        var value = encodeAppDocument(AppDocument(plan = plan))
        var writes = 0
        var fail = false
        override fun exists() = true
        override fun read() = value
        override fun write(value: String) {
            if (fail) throw IOException("injected write failure")
            this.value = value
            writes++
        }
    }
}
