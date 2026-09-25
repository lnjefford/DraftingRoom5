package dev.draftingroom5

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasedDocumentCompatibilityTest {
    @Test fun everyReleasedCompatibilityFixtureLoadsAndReencodesAsCurrentSchema() {
        listOf("v0.25.1.json", "v0.27.30.json", "v0.27.31.json").forEach { release ->
            val decoded = decodeAppDocument(compatibilityFixture(release))
            val current = encodeAppDocument(decoded)

            assertEquals(2, JSONObject(current).getInt("schemaVersion"))
            assertEquals(decoded, decodeAppDocument(current))
        }
    }

    @Test fun v025UpgradePreservesRoutineSessionAndHistoryData() {
        val upgraded = decodeAppDocument(compatibilityFixture("v0.25.1.json"))

        assertEquals(5, upgraded.generation)
        assertEquals("legacy-v025-routine", upgraded.plan.routines.single().id)
        assertEquals(12, upgraded.plan.routines.single().exercises.single().reps)
        assertTrue(upgraded.plan.routines.single().exercises.single().notes.contains("Previous target: 12-15 reps"))
        assertEquals("legacy-v025-session", upgraded.partialSessions.single().id)
        assertEquals(emptySet<String>(), upgraded.partialSessions.single().handledProgressionExerciseIds)
        assertEquals("legacy-v025-history", upgraded.history.single().id)
        assertEquals(emptyList<ProgressionReceipt>(), upgraded.progressionReceipts)
    }

    @Test fun v02730UpgradePreservesPlanSessionsHistoryAndRecoverableProgressionState() {
        val upgraded = decodeAppDocument(compatibilityFixture("v0.27.30.json"))
        val routine = upgraded.plan.routines.single()
        val custom = routine.exercises.first()
        val automatic = routine.exercises.last()

        assertEquals(42, upgraded.generation)
        assertEquals(setOf("legacy-custom", "legacy-auto"), routine.exercises.mapTo(linkedSetOf()) { it.id })
        assertEquals(8, custom.reps)
        assertEquals(40, custom.weightPounds)
        assertTrue(custom.notes.contains("Previous target: 8-12 reps"))
        assertEquals(45, custom.progression!!.steps.single().replacement.weightPounds)
        assertEquals(5, custom.progression!!.steps.single().insertedExercises.single().weightPounds)
        assertTrue(custom.progression!!.steps.single().insertedExercises.single().notes.contains("Previous target: AMRAP"))
        assertEquals(20, automatic.durationSeconds)
        assertEquals(25, automatic.weightPounds)
        assertEquals(null, automatic.progression)
        assertEquals("legacy-session", upgraded.partialSessions.single().id)
        assertEquals("legacy-history", upgraded.history.single().id)

        val receipt = upgraded.progressionReceipts.single()
        assertEquals(ProgressionChoice.MANUAL, receipt.choice)
        assertEquals(30, receipt.manualPrescription!!.weightPounds)
        assertEquals(20, receipt.manualPrescription!!.durationSeconds)
        assertNotNull(receipt.option())
    }

    @Test fun repositoryAtomicallyRewritesAReleasedDocumentOnlyAfterSuccessfulUpgrade() {
        val storage = CompatibilityStorage(compatibilityFixture("v0.27.30.json"))
        val loaded = AppRepository(storage).load()

        assertTrue(loaded is LoadState.Ready)
        assertEquals(1, storage.writes)
        assertEquals(2, JSONObject(storage.value).getInt("schemaVersion"))
        assertEquals(42, decodeAppDocument(storage.value).generation)
    }

    @Test fun priorReleaseDocumentInsideAnExistingRecoveryBackupAlsoUpgrades() {
        val envelope = JSONObject()
            .put("format", "draftingroom5.backup.current")
            .put("createdAtMillis", 123L)
            .put("document", JSONObject(compatibilityFixture("v0.27.30.json")))

        val restored = decodeBackupSnapshot(envelope.toString())

        assertEquals(123L, restored.createdAtMillis)
        assertEquals(42, restored.document.generation)
        assertEquals("legacy-session", restored.document.partialSessions.single().id)
        assertEquals(2, JSONObject(encodeAppDocument(restored.document)).getInt("schemaVersion"))
    }

    private fun compatibilityFixture(release: String): String = checkNotNull(
        javaClass.getResource("/compatibility/$release"),
    ).readText()
}

private class CompatibilityStorage(initial: String) : DocumentStorage {
    var value = initial
    var writes = 0

    override fun exists() = true
    override fun read() = value
    override fun write(value: String) {
        this.value = value
        writes += 1
    }
}
