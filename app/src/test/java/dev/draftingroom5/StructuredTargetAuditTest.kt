package dev.draftingroom5

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StructuredTargetAuditTest {
    @Test fun malformedWeightCannotEnterAnyPersistedPrescriptionOrBackup() {
        val document = progressionDocumentFixture()
        val paths: List<(JSONObject) -> JSONObject> = listOf(
            { it.getJSONObject("plan").getJSONArray("routines").getJSONObject(2).getJSONArray("exercises").getJSONObject(0) },
            { it.getJSONArray("partialSessions").getJSONObject(0).getJSONObject("snapshot").getJSONArray("exercises").getJSONObject(0) },
            { it.getJSONArray("history").getJSONObject(0).getJSONObject("snapshot").getJSONArray("exercises").getJSONObject(0) },
            { it.getJSONObject("plan").getJSONArray("routines").getJSONObject(2).getJSONArray("exercises").getJSONObject(0)
                .getJSONObject("progression").getJSONArray("steps").getJSONObject(0).getJSONObject("replacement") },
            { it.getJSONObject("plan").getJSONArray("routines").getJSONObject(2).getJSONArray("exercises").getJSONObject(0)
                .getJSONObject("progression").getJSONArray("steps").getJSONObject(0).getJSONArray("insertedExercises").getJSONObject(0) },
        )
        for (path in paths) for (bad in listOf(-5, 0, 36, 37.5, "35", true, 2147483650L)) {
            val root = JSONObject(encodeAppDocument(document))
            path(root).put("weightPounds", bad)
            assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
            val backup = JSONObject(encodeBackupSnapshot(BackupSnapshot(1, document))).put("document", root)
            assertThrows(IllegalArgumentException::class.java) { decodeBackupSnapshot(backup.toString()) }
        }
    }

    @Test fun malformedReceiptWeightIsRejectedBeforeRestoreCanPublish() {
        val initial = progressionDocumentFixture().let { d -> d.copy(partialSessions = d.partialSessions.map { s ->
            s.copy(completedSets = s.snapshot.exercises.associate { it.id to it.setCount })
        }) }
        var bytes = encodeAppDocument(initial)
        val storage = object : DocumentStorage {
            override fun exists() = true
            override fun read() = bytes
            override fun write(value: String) { bytes = value }
        }
        val repository = AppRepository(storage, SessionClock { SessionClockSample(100, 100, 1) }).also { it.load() }
        val session = initial.partialSessions.single()
        val source = session.snapshot.exercises.first()
        val offer = initial.progressionOffer(session.id, source.id)!!
        assertTrue(repository.applyProgression(repository.sessionLease()!!,
            offer.manualRequest(source.prescription().copy(weightPounds = 40))) is RepositoryResult.Success)
        val committed = bytes
        val root = JSONObject(committed)
        root.getJSONArray("progressionReceipts").getJSONObject(0).getJSONObject("manualPrescription").put("weightPounds", 37)
        assertThrows(IllegalArgumentException::class.java) { decodeAppDocument(root.toString()) }
        val document = (repository.state.value as LoadState.Ready).value
        val receipt = document.progressionReceipts.single()
        val invalid = document.copy(progressionReceipts = listOf(receipt.copy(manualPrescription = receipt.manualPrescription!!.copy(weightPounds = 37))))
        assertTrue(repository.restore(invalid) is RepositoryResult.Invalid)
        assertEquals(committed, bytes)
        assertEquals(document, (repository.state.value as LoadState.Ready).value)
    }

    @Test fun integerLexemesAndEveryDraftBoundaryRejectFractionalOrOverflowWeights() {
        val source = progressionRoutineFixture().exercises.first()
        for (bad in listOf("37.5", "35.0", "3.5e1", "2147483650", "-5", "0", "35 lb")) {
            assertNull(source.exerciseDraft().copy(currentPounds = bad).savedExercise())
            assertNull(manualAdjustmentPrescription(source, bad, false, "20", "3", "12"))
        }
        val json = encodeAppDocument(progressionDocumentFixture())
        for (token in listOf("35.0", "3.5e1", "2147483650", "-5", "0", "37.5")) {
            assertThrows(token, IllegalArgumentException::class.java) {
                decodeAppDocument(json.replace("\"weightPounds\":25", "\"weightPounds\":$token"))
            }
        }
    }
}
