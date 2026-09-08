package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.ZoneId

class BackupSupportTest {
    @Test
    fun statusWaitsForFirstSuccessfulSnapshot() {
        val status = backupStatusDetail(AutomaticBackupStatus(), ZoneId.of("UTC"))

        assertTrue(status.contains("Waiting for the first automatic backup"))
        assertTrue(status.contains("including while offline"))
    }

    @Test
    fun statusShowsSuccessAndRetryFailureSeparately() {
        val status = backupStatusDetail(
            AutomaticBackupStatus(
                lastSuccessfulMillis = 1_780_272_000_000L,
                lastFailureMillis = 1_780_275_600_000L,
                lastFailureMessage = "Storage unavailable",
            ),
            ZoneId.of("UTC"),
        )

        assertTrue(status.contains("Last successful backup:"))
        assertTrue(status.contains("will retry automatically: Storage unavailable"))
    }

    @Test
    fun snapshotRoundTripIncludesSettingsPlanAndWorkoutHistory() {
        val snapshot = BackupSnapshot(
            createdAtMillis = 1_780_272_000_000L,
            plan = defaultTrainingPlan(),
            dashboardLayout = DashboardLayout().setVisible(DashboardCard.BODY_FAT, false),
            healthDateRange = HealthDateRange.YEAR,
            workoutHistory = listOf(
                WorkoutHistoryEntry("history-1", "forearm-sat", "Forearm", Destination.CUSTOM, 1_780_272_000_000L),
            ),
            hapticsEnabled = false,
        )

        val restored = decodeBackupSnapshot(encodeBackupSnapshot(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun staleStatusDetectsMissingOrOldSnapshots() {
        val now = 1_780_444_800_000L

        assertTrue(backupIsStale(AutomaticBackupStatus(), now))
        assertTrue(backupIsStale(AutomaticBackupStatus(lastSuccessfulMillis = now - 48 * 60 * 60 * 1000), now))
    }

    @Test
    fun olderSnapshotDefaultsHapticsToEnabled() {
        val snapshot = BackupSnapshot(
            createdAtMillis = 1L,
            plan = defaultTrainingPlan(),
            dashboardLayout = DashboardLayout(),
            healthDateRange = HealthDateRange.MONTH,
            workoutHistory = emptyList(),
        )
        val oldSnapshot = JSONObject(encodeBackupSnapshot(snapshot)).apply { remove("hapticsEnabled") }.toString()

        assertTrue(decodeBackupSnapshot(oldSnapshot).hapticsEnabled)
    }
}
