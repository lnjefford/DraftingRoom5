package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.ZoneId

class BackupSupportTest {
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
