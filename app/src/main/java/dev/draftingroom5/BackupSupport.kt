package dev.draftingroom5

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class BackupStatusStore(context: Context) {
    private val preferences = context.getSharedPreferences("backup-local-status", Context.MODE_PRIVATE)

    fun lastLocalChange(): Long? = preferences.getLong(KEY_LAST_LOCAL_CHANGE, 0L).takeIf { it > 0L }

    fun recordLocalChange(nowMillis: Long = System.currentTimeMillis()): Long {
        preferences.edit().putLong(KEY_LAST_LOCAL_CHANGE, nowMillis).apply()
        return nowMillis
    }

    companion object {
        private const val KEY_LAST_LOCAL_CHANGE = "last-local-change"
    }
}

internal fun backupStatusDetail(lastLocalChangeMillis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val base = "Android automatically backs up schedules and custom routines to the Google account selected in system backup settings when the device is online and idle."
    if (lastLocalChangeMillis == null) return base
    val formatted = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        .format(Instant.ofEpochMilli(lastLocalChangeMillis).atZone(zoneId))
    return "$base Last local change: $formatted."
}

internal fun openAndroidBackupSettings(context: Context) {
    val privacySettings = Intent(Settings.ACTION_PRIVACY_SETTINGS)
    val intent = if (privacySettings.resolveActivity(context.packageManager) != null) {
        privacySettings
    } else {
        Intent(Settings.ACTION_SETTINGS)
    }
    context.startActivity(intent)
}
