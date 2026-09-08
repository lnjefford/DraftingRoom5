package dev.draftingroom5

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BackupSupportTest {
    @Test
    fun statusExplainsAutomaticGoogleBackupWithoutClaimingCompletion() {
        val status = backupStatusDetail(null, ZoneId.of("UTC"))

        assertTrue(status.contains("Google account"))
        assertTrue(status.contains("online and idle"))
        assertFalse(status.contains("Last local change"))
    }

    @Test
    fun statusLabelsLocalChangeSeparatelyFromCloudBackup() {
        val status = backupStatusDetail(0L + 1_780_272_000_000L, ZoneId.of("UTC"))

        assertTrue(status.contains("Last local change:"))
        assertFalse(status.contains("Last backup:"))
    }
}
