package dev.draftingroom5

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class SettingsStatusTone { NEUTRAL, POSITIVE, ATTENTION }

internal enum class HealthSettingsAction { NONE, CONNECT, MANAGE, RETRY, UPDATE }

internal data class HealthSettingsPresentation(
    val summary: String,
    val actionLabel: String,
    val action: HealthSettingsAction,
    val tone: SettingsStatusTone,
)

internal fun healthSettingsPresentation(
    connection: HealthConnection,
    isLoading: Boolean,
    sources: List<String> = emptyList(),
): HealthSettingsPresentation = when (connection) {
    HealthConnection.CHECKING -> HealthSettingsPresentation(
        summary = "Checking availability",
        actionLabel = "Checking…",
        action = HealthSettingsAction.NONE,
        tone = SettingsStatusTone.NEUTRAL,
    )
    HealthConnection.NEEDS_PERMISSION -> HealthSettingsPresentation(
        summary = "Permission required",
        actionLabel = "Connect",
        action = HealthSettingsAction.CONNECT,
        tone = SettingsStatusTone.ATTENTION,
    )
    HealthConnection.CONNECTED -> HealthSettingsPresentation(
        summary = if (isLoading) {
            "Connected · Syncing"
        } else {
            when (val distinctSources = sources.filter { it.isNotBlank() }.distinct()) {
                emptyList<String>() -> "Connected"
                else -> if (distinctSources.size == 1) "Connected · ${distinctSources.single()}" else "Connected · ${distinctSources.size} sources"
            }
        },
        actionLabel = "Manage",
        action = HealthSettingsAction.MANAGE,
        tone = SettingsStatusTone.POSITIVE,
    )
    HealthConnection.UPDATE_REQUIRED -> HealthSettingsPresentation(
        summary = "Provider update required",
        actionLabel = "Update",
        action = HealthSettingsAction.UPDATE,
        tone = SettingsStatusTone.ATTENTION,
    )
    HealthConnection.UNAVAILABLE -> HealthSettingsPresentation(
        summary = "Unavailable on this device",
        actionLabel = "Retry",
        action = HealthSettingsAction.RETRY,
        tone = SettingsStatusTone.ATTENTION,
    )
    HealthConnection.ERROR -> HealthSettingsPresentation(
        summary = "Could not check connection",
        actionLabel = "Retry",
        action = HealthSettingsAction.RETRY,
        tone = SettingsStatusTone.ATTENTION,
    )
}

internal data class BackupSettingsPresentation(
    val summary: String,
    val tone: SettingsStatusTone,
)

internal fun backupSettingsPresentation(
    status: AutomaticBackupStatus,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): BackupSettingsPresentation {
    if (!status.enabled) return BackupSettingsPresentation("Off · Recovery copies kept", SettingsStatusTone.NEUTRAL)
    val lastSuccess = status.lastSuccessfulMillis
    val latestAttemptFailed = status.lastFailureMillis?.let { failure -> failure > (lastSuccess ?: Long.MIN_VALUE) } == true
    val lastBackup = lastSuccess?.let { compactBackupTime(it, nowMillis, zoneId) }
    return when {
        latestAttemptFailed -> BackupSettingsPresentation(
            "Needs attention${lastBackup?.let { " · Last backup $it" }.orEmpty()}",
            SettingsStatusTone.ATTENTION,
        )
        lastSuccess == null -> BackupSettingsPresentation("On · Waiting for first backup", SettingsStatusTone.ATTENTION)
        backupIsStale(status, nowMillis) -> BackupSettingsPresentation("On · Last backup $lastBackup", SettingsStatusTone.ATTENTION)
        else -> BackupSettingsPresentation("On · Last backup $lastBackup", SettingsStatusTone.POSITIVE)
    }
}

private fun compactBackupTime(millis: Long, nowMillis: Long, zoneId: ZoneId): String {
    val value = Instant.ofEpochMilli(millis).atZone(zoneId)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when (value.toLocalDate()) {
        today -> "today at ${DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(value)}"
        today.minusDays(1) -> "yesterday at ${DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(value)}"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(value)
    }
}

internal data class UpdateSettingsPresentation(
    val summary: String,
    val actionLabel: String,
    val enabled: Boolean,
    val tone: SettingsStatusTone,
)

internal fun updateSettingsPresentation(
    status: AppUpdateStatus,
    busy: Boolean,
    actionMessage: String?,
    versionName: String,
): UpdateSettingsPresentation = when {
    busy -> UpdateSettingsPresentation(actionMessage ?: "Checking for updates…", "Working…", false, SettingsStatusTone.NEUTRAL)
    actionMessage?.let { it.contains("failed", ignoreCase = true) || it.contains("could not", ignoreCase = true) } == true -> UpdateSettingsPresentation(
        actionMessage,
        "Retry",
        true,
        SettingsStatusTone.ATTENTION,
    )
    status.availableVersion != null -> UpdateSettingsPresentation(
        actionMessage ?: "Version ${status.availableVersion} available",
        "Install",
        true,
        SettingsStatusTone.ATTENTION,
    )
    actionMessage == null && status.lastError != null -> UpdateSettingsPresentation(
        status.lastError,
        "Retry",
        true,
        SettingsStatusTone.ATTENTION,
    )
    else -> UpdateSettingsPresentation(
        actionMessage ?: if (status.lastCheckedMillis == null) "Version $versionName · Not checked yet" else "Version $versionName · Up to date",
        "Check",
        true,
        SettingsStatusTone.POSITIVE,
    )
}

internal data class HapticSettingsPresentation(val detail: String, val available: Boolean)

internal fun hapticSettingsPresentation(hasVibrator: Boolean, systemEnabled: Boolean): HapticSettingsPresentation = when {
    !hasVibrator -> HapticSettingsPresentation("Tactile cues unavailable on this device", false)
    !systemEnabled -> HapticSettingsPresentation("Tactile cues are off in Android system settings", false)
    else -> HapticSettingsPresentation("Tactile cues during guided sessions", true)
}
