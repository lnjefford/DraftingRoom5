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

    @Test fun currentSnapshotRoundTripIncludesTheWholeDocument() {
        val document = defaultAppDocument().copy(preferences = AppPreferences(healthDateRange = HealthDateRange.YEAR, hapticsEnabled = false))
        val snapshot = BackupSnapshot(1_780_272_000_000L, document)
        assertEquals(snapshot, decodeBackupSnapshot(encodeBackupSnapshot(snapshot)))
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
