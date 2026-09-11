package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedAppRoutineTest {
    @Test
    fun editorDraftValidatesAndBuildsOneRevision() {
        val original = linkedRoutine(deepLink = "fitbod://workout")
        val unchangedPackage = original.withLinkedAppDraft(
            LinkedAppRoutineDraft("  Strength day  ", "push_day", "com.fitbod.fitbod"),
        )
        val replacement = original.withLinkedAppDraft(
            LinkedAppRoutineDraft("Run", "running_shoe", "com.example.running"),
        )

        assertEquals(4L, unchangedPackage?.revision)
        assertEquals("Strength day", unchangedPackage?.name)
        assertEquals("push_day", unchangedPackage?.artworkId)
        assertEquals("fitbod://workout", unchangedPackage?.appLink?.deepLink)
        assertNull(replacement?.appLink?.deepLink)
        assertNull(original.withLinkedAppDraft(LinkedAppRoutineDraft("", "generic", "com.example.app")))
        assertNull(original.withLinkedAppDraft(LinkedAppRoutineDraft("Run", "generic", "not a package")))
    }

    @Test
    fun launchUsesDeepLinkThenSafeLauncherFallback() {
        val attempts = mutableListOf<LinkedAppLaunchTarget>()
        val result = launchLinkedApp(AppLink("com.example.running", "runner://today")) { target ->
            attempts += target
            target is LinkedAppLaunchTarget.Launcher
        }

        assertTrue(result is LinkedAppLaunchResult.Opened)
        assertEquals(
            listOf(
                LinkedAppLaunchTarget.DeepLink("runner://today", "com.example.running"),
                LinkedAppLaunchTarget.Launcher("com.example.running"),
            ),
            attempts,
        )
    }

    @Test
    fun invalidDeepLinkIsSkippedButPackageLaunchRemainsAvailable() {
        val attempts = mutableListOf<LinkedAppLaunchTarget>()
        val result = launchLinkedApp(AppLink("com.example.running", "javascript:alert(1)")) {
            attempts += it
            true
        }

        assertTrue(result is LinkedAppLaunchResult.Opened)
        assertEquals(listOf(LinkedAppLaunchTarget.Launcher("com.example.running")), attempts)
        assertFalse(validDeepLink("file:///private/data"))
        assertTrue(validDeepLink("https://example.com/start"))
    }

    @Test
    fun unavailableAndInvalidTargetsReturnActionableFailures() {
        assertEquals(
            LinkedAppLaunchFailure.UNAVAILABLE,
            (launchLinkedApp(AppLink("com.example.running", null)) { false } as LinkedAppLaunchResult.Failed).reason,
        )
        assertEquals(
            LinkedAppLaunchFailure.INVALID_PACKAGE,
            (launchLinkedApp(AppLink("broken", null)) { true } as LinkedAppLaunchResult.Failed).reason,
        )
    }

    @Test
    fun linkedAppEditPersistsNameArtworkAndReplacementAtomically() {
        val store = MemoryStore()
        val repository = AppRepository(store)
        val loaded = (repository.load() as LoadState.Ready).value
        val original = loaded.plan.routines.first { it.execution == RoutineExecution.LINKED_APP }
        val updated = original.withLinkedAppDraft(
            LinkedAppRoutineDraft("Evening run", "stopwatch", "com.example.runner"),
        )!!
        val result = repository.replacePlan(
            loaded.generation,
            loaded.plan.copy(routines = loaded.plan.routines.map { if (it.id == updated.id) updated else it }),
        )

        assertTrue(result is RepositoryResult.Success<AppDocument>)
        val persisted = decodeAppDocument(store.value).plan.routines.first { it.id == original.id }
        assertEquals("Evening run", persisted.name)
        assertEquals("stopwatch", persisted.artworkId)
        assertEquals(AppLink("com.example.runner", null), persisted.appLink)
    }

    private fun linkedRoutine(deepLink: String?) = Routine(
        id = "linked",
        revision = 3,
        name = "Strength",
        artworkId = "dumbbell",
        execution = RoutineExecution.LINKED_APP,
        exercises = emptyList(),
        appLink = AppLink("com.fitbod.fitbod", deepLink),
    )

    private class MemoryStore : DocumentStorage {
        var value = ""
        override fun exists() = value.isNotEmpty()
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
}
