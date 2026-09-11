package dev.draftingroom5

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class PlanningAuditTest {
    private val guided = defaultTrainingPlan().routines.last()

    @Test fun newGuidedDraftSavesAfterSeveralChildEditsAndRecreation() {
        val original = guided.copy(id = "new", name = "", exercises = emptyList())
        val edited = original.withGuidedIdentity("My routine", "dumbbell")!!
            .withExercise(guided.exercises.first())!!
            .withExercise(guided.exercises.last())!!
        val encoded = with(RoutineDraftSaver) { SaverScope { true }.save(edited) }!!
        val restored = RoutineDraftSaver.restore(encoded)!!
        assertEquals(edited, restored)
        val ready = restored.forEditorSave(original, isNew = true)
        assertEquals(1L, ready.revision)
        val storage = PlanningStorage()
        val repo = AppRepository(storage)
        val document = (repo.load() as LoadState.Ready).value
        assertTrue(repo.replacePlan(document.generation, document.plan.copy(routines = document.plan.routines + ready)) is RepositoryResult.Success)
        assertEquals(ready, (AppRepository(storage).load() as LoadState.Ready).value.plan.routines.last())
    }

    @Test fun incompleteGuidedDraftRoundTripsWithoutBecomingCanonical() {
        val blank = guided.copy(id = "draft", name = "", exercises = emptyList())
        val encoded = with(RoutineDraftSaver) { SaverScope { true }.save(blank) }!!
        assertEquals(blank, RoutineDraftSaver.restore(encoded))
        assertFalse(blank.isSaveableGuidedRoutine())
    }

    @Test fun multiPositionDragCommitsOneRevisionAndKeepsUnchangedSnapshots() {
        val session = partialFixture()
        val storage = PlanningStorage(encodeAppDocument(defaultAppDocument().copy(partialSessions = listOf(session))))
        val repo = AppRepository(storage)
        val initial = (repo.load() as LoadState.Ready).value
        val id = guided.exercises.first().id
        val dragged = guided.moveExercise(id, 1).moveExercise(id, 1).moveExercise(id, 1)
        val saved = dragged.forEditorSave(guided, false)
        assertEquals(guided.revision + 1, saved.revision)
        assertEquals(id, saved.exercises[3].id)
        val plan = initial.plan.copy(routines = initial.plan.routines.map { if (it.id == guided.id) saved else it })
        assertTrue(repo.replacePlan(initial.generation, plan) is RepositoryResult.Invalid)
        val result = repo.replacePlan(initial.generation, plan, true) as RepositoryResult.Success
        assertEquals(session, result.value.partialSessions.single())
        assertEquals(saved, result.value.plan.routines.last())
    }

    @Test fun dragReturningToOriginAndUnchangedChildSavesAreNoOps() {
        val id = guided.exercises.first().id
        assertEquals(guided, guided.moveExercise(id, 1).moveExercise(id, -1).forEditorSave(guided, false))
        assertSame(guided, guided.withExercise(guided.exercises.first()))
        assertSame(guided, guided.withGuidedIdentity(guided.name, guided.artworkId))
        assertSame(guided, guided.withGuidedArtwork(guided.artworkId))
    }

    @Test fun unsafeDeepLinksNeverReachTheLauncherAdapter() {
        listOf("intent://x", "data:text/plain,hello", "file:///x", "content://x", "javascript:alert(1)",
            "http://example.com", "https://user:secret@example.com", "https:relative", "custom:\u0001x").forEach { uri ->
            assertFalse(uri, validDeepLink(uri))
            assertEquals(listOf(LinkedAppLaunchTarget.Launcher("com.example.app")), linkedAppLaunchTargets(AppLink("com.example.app", uri)))
        }
        assertTrue(validDeepLink("https://example.com/workout"))
        assertTrue(validDeepLink("workout://start/session"))
    }

    @Test fun launcherCandidatesMustBeEnabledExportedAndPermissionAccessible() {
        assertTrue(safeLauncherActivity(true, true, true, true))
        repeat(4) { denied ->
            val flags = List(4) { it != denied }
            assertFalse(safeLauncherActivity(flags[0], flags[1], flags[2], flags[3]))
        }
    }

    @Test fun linkedDraftRejectsMalformedTargetsAndLaunchFailureKeepsTheLink() {
        val draft = RoutineDraft("draft", RoutineExecution.LINKED_APP, "not a package", "Example")
        assertNull(draft.savedLinkedRoutine("Example"))
        val link = AppLink("com.example.app", "workout://start")
        val attempted = mutableListOf<LinkedAppLaunchTarget>()
        val result = launchLinkedApp(link) { attempted += it; throw SecurityException("Unavailable") }
        assertTrue(result is LinkedAppLaunchResult.Failed)
        assertEquals(linkedAppLaunchTargets(link), attempted)
        assertEquals("workout://start", link.deepLink)
    }

    @Test fun reassigningRecurrenceToLinkedAppStillResumesOriginalGuidedSnapshot() {
        val session = partialFixture()
        val plan = defaultTrainingPlan()
        val reassigned = plan.copy(schedule = plan.schedule.map {
            if (it.id == session.occurrence.scheduleEntryId) it.copy(routineId = plan.routines.first().id) else it
        })
        val card = dashboardSessions(reassigned, listOf(session), emptyList(), session.occurrence.scheduledDate).single()
        assertEquals(SessionAction.RESUME, card.action)
        assertEquals(session.routineId, card.scheduleEntry.routineId)
        assertEquals(session.snapshot, card.routine)
        val history = WorkoutHistoryEntry("done", session.occurrence, session.snapshot, 1, 2)
        val done = dashboardSessions(reassigned, emptyList(), listOf(history), session.occurrence.scheduledDate).single()
        assertEquals(SessionAction.DONE, done.action)
        assertEquals(session.snapshot, done.routine)
    }
}

private class PlanningStorage(var value: String? = null) : DocumentStorage {
    override fun exists() = value != null
    override fun read() = checkNotNull(value)
    override fun write(value: String) { this.value = value }
}
