package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.ZoneId

class BackupSupportTest {
    @Test fun manualFailureDoesNotPromiseAutomaticRetryWhenBackupsAreOff() {
        assertTrue(backupFailureMessage(false).contains("use Back up now to retry"))
        assertTrue(backupFailureMessage(true).contains("will retry automatically"))
    }

    private class SnapshotMemory(var value: String? = null) : DocumentStorage {
        var failWrites = false
        override fun exists() = value != null
        override fun read(): String = value ?: throw java.io.FileNotFoundException()
        override fun write(value: String) {
            if (failWrites) throw java.io.IOException("Storage full")
            this.value = value
        }
    }

    @Test fun failedRotationPreservesLatestAndPreviousSnapshots() {
        val older = BackupSnapshot(1, defaultAppDocument())
        val current = BackupSnapshot(2, defaultAppDocument())
        val latest = SnapshotMemory(encodeBackupSnapshot(current))
        val previous = SnapshotMemory(encodeBackupSnapshot(older)).apply { failWrites = true }
        val store = RecoverySnapshotStore(latest, previous)
        assertThrows(java.io.IOException::class.java) { store.write(BackupSnapshot(3, defaultAppDocument())) }
        assertEquals(current, store.read())
        assertEquals(older, decodeBackupSnapshot(previous.read()))
    }

    @Test fun failedLatestWriteKeepsTheLastCommittedSnapshotRecoverable() {
        val current = BackupSnapshot(2, defaultAppDocument())
        val latest = SnapshotMemory(encodeBackupSnapshot(current)).apply { failWrites = true }
        val previous = SnapshotMemory()
        val store = RecoverySnapshotStore(latest, previous)
        assertThrows(java.io.IOException::class.java) { store.write(BackupSnapshot(3, defaultAppDocument())) }
        assertEquals(current, store.read())
        latest.value = "broken"
        assertEquals(current, store.read())
    }

    @Test fun corruptLatestNeverOverwritesGoodFallbackEvenWhenNextWriteFails() {
        val fallback = BackupSnapshot(1, defaultAppDocument())
        val latest = SnapshotMemory("broken").apply { failWrites = true }
        val previous = SnapshotMemory(encodeBackupSnapshot(fallback))
        val store = RecoverySnapshotStore(latest, previous)
        assertThrows(java.io.IOException::class.java) { store.write(BackupSnapshot(3, defaultAppDocument())) }
        assertEquals(fallback, store.read())
        latest.failWrites = false
        val next = BackupSnapshot(4, defaultAppDocument())
        store.write(next)
        assertEquals(next, store.read())
        assertEquals(fallback, decodeBackupSnapshot(previous.read()))
    }

    @Test fun invalidNewSnapshotDoesNotTouchEitherRecoveryCopy() {
        val original = BackupSnapshot(1, defaultAppDocument())
        val latest = SnapshotMemory(encodeBackupSnapshot(original))
        val previous = SnapshotMemory()
        val store = RecoverySnapshotStore(latest, previous)
        assertThrows(IllegalArgumentException::class.java) { store.write(BackupSnapshot(-1, defaultAppDocument())) }
        assertEquals(original, store.read())
        assertEquals(null, previous.value)
    }

    @Test fun backupRejectsNestedDuplicateKeysAndNegativeTimestamps() {
        val valid = encodeBackupSnapshot(BackupSnapshot(1, defaultAppDocument()))
        val duplicate = valid.replaceFirst("\"generation\":", "\"generation\":1,\"generation\":")
        assertThrows(IllegalArgumentException::class.java) { decodeBackupSnapshot(duplicate) }
        val negative = JSONObject(valid).put("createdAtMillis", -1).toString()
        assertThrows(IllegalArgumentException::class.java) { decodeBackupSnapshot(negative) }
    }

    @Test fun statusWaitsForFirstSuccessfulSnapshot() {
        val status = backupStatusDetail(AutomaticBackupStatus(), ZoneId.of("UTC"))
        assertTrue(status.contains("Waiting for the first automatic backup"))
        assertTrue(status.contains("including while offline"))
    }

    @Test fun currentSnapshotRoundTripIncludesPlanSessionsHistoryAndAllAppPreferences() {
        val base = defaultAppDocument()
        val routine = base.plan.routines.first { it.execution == RoutineExecution.GUIDED }
        val occurrence = OccurrenceKey(
            base.plan.schedule.first { it.routineId == routine.id }.id,
            java.time.LocalDate.of(2026, 9, 12),
        )
        val partial = GuidedSession(
            "partial",
            occurrence,
            routine.id,
            routine,
            routine.exercises.first().id,
            routine.exercises.associate { it.id to 0 },
            SessionTimer(),
            10,
            20,
            0,
        )
        val history = WorkoutHistoryEntry(
            "complete",
            occurrence.copy(scheduledDate = occurrence.scheduledDate.minusWeeks(1)),
            routine,
            1,
            2,
        )
        val document = base.copy(
            preferences = AppPreferences(
                healthDateRange = HealthDateRange.YEAR,
                hapticsEnabled = false,
                voice = VoiceAnnouncementSettings(enabled = false, rate = 1.25f),
                automaticBackupsEnabled = false,
            ),
            partialSessions = listOf(partial),
            history = listOf(history),
        )
        val snapshot = BackupSnapshot(1_780_272_000_000L, document)
        assertEquals(snapshot, decodeBackupSnapshot(encodeBackupSnapshot(snapshot)))
    }

    @Test fun backupRejectsTheSupersededEnvelopeName() {
        val root = JSONObject(encodeBackupSnapshot(BackupSnapshot(1L, defaultAppDocument())))
        root.put("format", "draftingroom5.backup-current")
        assertThrows(IllegalArgumentException::class.java) { decodeBackupSnapshot(root.toString()) }
    }

    @Test fun backupRejectsMissingCurrentFieldsInsteadOfApplyingCompatibilityDefaults() {
        val root = JSONObject(encodeBackupSnapshot(BackupSnapshot(1L, defaultAppDocument())))
        root.getJSONObject("document").getJSONObject("preferences").remove("hapticsEnabled")
        assertThrows(IllegalArgumentException::class.java) { decodeBackupSnapshot(root.toString()) }
    }

    @Test fun staleStatusDetectsMissingOrOldSnapshots() {
        val now = 1_780_444_800_000L
        assertTrue(backupIsStale(AutomaticBackupStatus(), now))
        assertTrue(backupIsStale(AutomaticBackupStatus(lastSuccessfulMillis = now - 48 * 60 * 60 * 1000), now))
    }
}
