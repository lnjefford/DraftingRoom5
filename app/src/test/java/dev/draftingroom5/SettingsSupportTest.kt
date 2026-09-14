package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SettingsSupportTest {
    @Test fun healthStatesExposeTheCorrectActionAndNeverUseColorAlone() {
        assertEquals(HealthSettingsAction.NONE, healthSettingsPresentation(HealthConnection.CHECKING, false).action)
        assertEquals(HealthSettingsAction.CONNECT, healthSettingsPresentation(HealthConnection.NEEDS_PERMISSION, false).action)
        assertEquals(HealthSettingsAction.UPDATE, healthSettingsPresentation(HealthConnection.UPDATE_REQUIRED, false).action)
        assertEquals(HealthSettingsAction.RETRY, healthSettingsPresentation(HealthConnection.UNAVAILABLE, false).action)
        assertEquals(HealthSettingsAction.RETRY, healthSettingsPresentation(HealthConnection.ERROR, false).action)
        val connected = healthSettingsPresentation(HealthConnection.CONNECTED, false, listOf("Withings", "Withings"))
        assertEquals("Connected · Withings", connected.summary)
        assertEquals(HealthSettingsAction.MANAGE, connected.action)
        assertEquals(SettingsStatusTone.POSITIVE, connected.tone)
    }

    @Test fun backupSummaryDistinguishesOffCurrentStaleAndFailed() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-09-11T13:00:00Z").toEpochMilli()
        assertEquals("Off · Recovery copies kept", backupSettingsPresentation(AutomaticBackupStatus(enabled = false), now, zone).summary)
        val current = backupSettingsPresentation(AutomaticBackupStatus(lastSuccessfulMillis = now - 60_000), now, zone)
        assertTrue(current.summary.startsWith("On · Last backup today at"))
        assertEquals(SettingsStatusTone.POSITIVE, current.tone)
        assertEquals(SettingsStatusTone.ATTENTION, backupSettingsPresentation(AutomaticBackupStatus(lastSuccessfulMillis = now - 49 * 60 * 60 * 1000), now, zone).tone)
        assertTrue(backupSettingsPresentation(AutomaticBackupStatus(lastSuccessfulMillis = now - 60_000, lastFailureMillis = now), now, zone).summary.startsWith("Needs attention"))
    }

    @Test fun updateSummaryUsesRuntimeVersionAndStateSpecificActions() {
        val initial = updateSettingsPresentation(AppUpdateStatus(), false, null, "2.3.4")
        assertEquals("Version 2.3.4 · Not checked yet", initial.summary)
        assertEquals("Check", initial.actionLabel)
        assertEquals("Install", updateSettingsPresentation(AppUpdateStatus(availableVersion = "2.4.0"), false, null, "2.3.4").actionLabel)
        assertEquals("Retry", updateSettingsPresentation(AppUpdateStatus(lastError = "Offline"), false, null, "2.3.4").actionLabel)
        assertEquals("Check", updateSettingsPresentation(AppUpdateStatus(lastError = "Old error"), false, "You're running the latest version.", "2.3.4").actionLabel)
        assertFalse(updateSettingsPresentation(AppUpdateStatus(), true, null, "2.3.4").enabled)
        val opening = updateSettingsPresentation(AppUpdateStatus(availableVersion = "2.4.0"), true, "Opening Android's installer…", "2.3.4")
        assertEquals("Working…", opening.actionLabel)
        assertEquals("Opening Android's installer…", opening.summary)
        assertFalse(opening.enabled)
        assertEquals("Retry", updateSettingsPresentation(AppUpdateStatus(availableVersion = "2.4.0"), false, "Could not open the installer", "2.3.4").actionLabel)
    }

    @Test fun interruptedUpdateWorkRecoversAfterRecreationWithoutAStuckBusyState() {
        listOf("Checking for updates…", "Downloading version 2.4.0…", "Opening Android's installer…").forEach { working ->
            assertEquals(working, dashboardUpdateAnnouncement("2.4.0", true, working))
            val restored = updateMessageAfterRecreation(working)
            assertTrue(restored.contains("interrupted"))
            assertEquals("Install", updateSettingsPresentation(AppUpdateStatus(availableVersion = "2.4.0"), false, restored, "2.3.4").actionLabel)
        }
        assertEquals("Update 2.4.0 available. Tap to install.", dashboardUpdateAnnouncement("2.4.0", false, null))
        assertEquals(null, dashboardUpdateAnnouncement(null, false, null))
        assertEquals("Confirm the update in Android's installer.", updateMessageAfterRecreation("Confirm the update in Android's installer."))
    }

    @Test fun hapticSummaryReflectsAndroidAndDeviceCapability() {
        assertTrue(hapticSettingsPresentation(true, true).available)
        assertEquals("Tactile cues are off in Android system settings", hapticSettingsPresentation(true, false).detail)
        assertEquals("Tactile cues unavailable on this device", hapticSettingsPresentation(false, true).detail)
    }
}
