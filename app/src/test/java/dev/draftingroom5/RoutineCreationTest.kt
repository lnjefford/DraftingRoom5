package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCreationTest {
    private val apps = listOf(
        InstalledAppOption("com.example.zeta", "Zeta Fitness", "Health & fitness"),
        InstalledAppOption("com.example.alpha", "Alpha Run", "Health & fitness"),
    )

    @Test fun chooserHasExactlyTheTwoApprovedImmediateActions() {
        assertEquals(listOf(AddRoutineChoice.GUIDED, AddRoutineChoice.LINKED_APP), addRoutineOptions.map { it.choice })
        assertEquals("Guided routine", addRoutineOptions[0].title)
        assertEquals("Linked-app routine", addRoutineOptions[1].title)
    }

    @Test fun localSearchUsesVisibleLabelsAndHandlesNoMatches() {
        assertEquals(listOf("Alpha Run"), filterInstalledApps(apps, " alpha ").map { it.label })
        assertEquals(listOf("Zeta Fitness"), filterInstalledApps(apps, "FIT").map { it.label })
        assertTrue(filterInstalledApps(apps, "missing").isEmpty())
        assertEquals(apps, filterInstalledApps(apps, "  "))
    }

    @Test fun inventoryDropsSelfInvalidAndDuplicatePackagesThenSortsLabels() {
        val normalized = normalizeInstalledApps(
            apps + listOf(
                InstalledAppOption("com.example.alpha", "Duplicate", "App"),
                InstalledAppOption("dev.draftingroom5", "DraftingRoom5", "App"),
                InstalledAppOption("", "Broken", "App"),
                InstalledAppOption("com.example.blank", "", "App"),
            ),
            ownPackageName = "dev.draftingroom5",
        )

        assertEquals(listOf("Alpha Run", "Zeta Fitness"), normalized.map { it.label })
    }

    @Test fun packageQueryUsesTypedFlagsOnlyOnApi33AndNewer() {
        assertEquals(PackageQueryApi.LEGACY_FLAGS, packageQueryApi(28))
        assertEquals(PackageQueryApi.LEGACY_FLAGS, packageQueryApi(32))
        assertEquals(PackageQueryApi.TYPED_FLAGS, packageQueryApi(33))
        assertEquals(PackageQueryApi.TYPED_FLAGS, packageQueryApi(36))
    }

    @Test fun selectingAnAppOnlyUpdatesTheTransientDraft() {
        val plan = defaultTrainingPlan()
        val draft = RoutineDraft("draft-1", RoutineExecution.LINKED_APP)

        val selected = draft.withSelectedApp(apps.first())

        assertEquals(plan, defaultTrainingPlan())
        assertEquals("com.example.zeta", selected.packageName)
        assertEquals("Zeta Fitness", selected.appLabel)
        assertNull(draft.packageName)
        assertThrows(IllegalArgumentException::class.java) {
            RoutineDraft("guided", RoutineExecution.GUIDED).withSelectedApp(apps.first())
        }
    }

    @Test fun onlyExplicitValidLinkedSaveCreatesCanonicalRoutine() {
        val incomplete = RoutineDraft("draft-1", RoutineExecution.LINKED_APP)
        assertNull(incomplete.savedLinkedRoutine("Run"))
        assertNull(incomplete.withSelectedApp(apps.first()).savedLinkedRoutine("   "))
        assertNull(RoutineDraft("guided", RoutineExecution.GUIDED).savedLinkedRoutine("Guided"))

        val saved = incomplete.withSelectedApp(apps.first()).savedLinkedRoutine("  Morning run  ")

        assertEquals("Morning run", saved?.name)
        assertEquals(RoutineExecution.LINKED_APP, saved?.execution)
        assertEquals("com.example.zeta", saved?.appLink?.packageName)
        assertTrue(saved?.exercises?.isEmpty() == true)
        validateAppDocument(defaultAppDocument().copy(
            plan = defaultTrainingPlan().copy(routines = defaultTrainingPlan().routines + requireNotNull(saved)),
        ))
    }
}
