package dev.draftingroom5

/** Stable review fixtures for the exceptional states required by the approved handoffs. */
internal enum class HardeningState(val fixtureName: String, val recovery: String?) {
    LOADING("Loading", null),
    EMPTY("Empty", null),
    APP_PICKER_LOADING("App picker loading", null),
    APP_PICKER_EMPTY("App picker empty", "Check again after installing a launchable app"),
    APP_PICKER_FAILURE("App picker failure", "Retry the installed-app query"),
    CORRUPT_CURRENT_DATA("Corrupt current data", "Restore a snapshot or explicitly reset app data"),
    HEALTH_PERMISSION("Health permission", "Open Health Connect permissions"),
    HEALTH_UNAVAILABLE("Health unavailable", "Retry availability check"),
    HEALTH_UPDATE_REQUIRED("Health provider update", "Open the provider installer"),
    LINKED_APP_UNINSTALLED("Linked app uninstalled", "Change the app or open its store listing"),
    TTS_UNAVAILABLE("TTS unavailable", "Continue with visual timers and optional haptics"),
    BACKUP_FAILURE("Backup failure", "Retry the backup; existing snapshots remain available"),
    UPDATE_FAILURE("Update failure", "Retry the verified update check"),
    MISSING_ARTWORK("Missing artwork", "Use the catalog's generic paired fallback"),
    NO_ROUTINES("No routines", "Add a guided or linked-app routine"),
    RECOVERY_DAY("Recovery day", "Add a scheduled item if wanted"),
    DESTRUCTIVE_CONFIRMATION("Destructive confirmation", "Cancel safely or explicitly confirm"),
    PERSISTENCE_FAILURE("Save failure", "Keep the draft visible and retry"),
}

internal fun hardeningStateForFixture(name: String): HardeningState? =
    HardeningState.entries.firstOrNull { it.fixtureName == name }
