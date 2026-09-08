package dev.draftingroom5

import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

internal data class AutomaticBackupStatus(
    val enabled: Boolean = true,
    val lastSuccessfulMillis: Long? = null,
    val lastFailureMillis: Long? = null,
    val lastFailureMessage: String? = null,
    val hasRecoverySnapshot: Boolean = false,
)

internal data class BackupSnapshot(
    val createdAtMillis: Long,
    val plan: TrainingPlan,
    val dashboardLayout: DashboardLayout,
    val healthDateRange: HealthDateRange,
    val workoutHistory: List<WorkoutHistoryEntry>,
    val hapticsEnabled: Boolean = true,
)

internal class AutomaticBackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val backupDirectory = File(appContext.filesDir, BACKUP_DIRECTORY)
    private val latestFile = File(backupDirectory, LATEST_FILE)
    private val previousFile = File(backupDirectory, PREVIOUS_FILE)

    fun status(): AutomaticBackupStatus = AutomaticBackupStatus(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        lastSuccessfulMillis = preferences.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
        lastFailureMillis = preferences.getLong(KEY_LAST_FAILURE, 0L).takeIf { it > 0L },
        lastFailureMessage = preferences.getString(KEY_LAST_FAILURE_MESSAGE, null),
        hasRecoverySnapshot = latestFile.isFile || previousFile.isFile,
    )

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        val workManager = WorkManager.getInstance(appContext)
        if (enabled) {
            schedule()
            requestBackup()
        } else {
            workManager.cancelUniqueWork(ONE_TIME_WORK)
            workManager.cancelUniqueWork(PERIODIC_WORK)
        }
        BackupManager(appContext).dataChanged()
    }

    fun schedule() {
        if (!status().enabled) return
        val periodic = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun requestBackup() {
        if (!status().enabled) return
        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun createBackup(nowMillis: Long = System.currentTimeMillis()): Result<AutomaticBackupStatus> {
        if (!status().enabled) return Result.success(status())
        return runCatching {
            check(backupDirectory.exists() || backupDirectory.mkdirs()) { "Could not create the backup directory." }
            val snapshot = BackupSnapshot(
                createdAtMillis = nowMillis,
                plan = TrainingPlanStore(appContext).load(),
                dashboardLayout = DashboardLayoutStore(appContext).load(),
                healthDateRange = HealthDateRangeStore(appContext).load(),
                workoutHistory = WorkoutHistoryStore(appContext).load(),
                hapticsEnabled = HapticSettingsStore(appContext).isEnabled(),
            )
            val temporary = File(backupDirectory, "$LATEST_FILE.tmp")
            temporary.writeText(encodeBackupSnapshot(snapshot), Charsets.UTF_8)
            if (latestFile.exists()) latestFile.copyTo(previousFile, overwrite = true)
            check(!latestFile.exists() || latestFile.delete()) { "Could not rotate the previous recovery snapshot." }
            check(temporary.renameTo(latestFile)) { "Could not finalize the recovery snapshot." }
            preferences.edit()
                .putLong(KEY_LAST_SUCCESS, nowMillis)
                .remove(KEY_LAST_FAILURE)
                .remove(KEY_LAST_FAILURE_MESSAGE)
                .commit()
            BackupManager(appContext).dataChanged()
            status()
        }.onFailure { error ->
            preferences.edit()
                .putLong(KEY_LAST_FAILURE, nowMillis)
                .putString(KEY_LAST_FAILURE_MESSAGE, error.message ?: "Unknown backup error")
                .apply()
        }
    }

    fun restoreLatest(): Result<BackupSnapshot> = runCatching {
        val candidates = listOf(latestFile, previousFile).filter { it.isFile }
        check(candidates.isNotEmpty()) { "No recovery snapshot is available yet." }
        val snapshot = candidates.firstNotNullOfOrNull { candidate ->
            runCatching { decodeBackupSnapshot(candidate.readText(Charsets.UTF_8)) }.getOrNull()
        } ?: error("The available recovery snapshots could not be read.")
        TrainingPlanStore(appContext).save(snapshot.plan)
        DashboardLayoutStore(appContext).save(snapshot.dashboardLayout)
        HealthDateRangeStore(appContext).save(snapshot.healthDateRange)
        WorkoutHistoryStore(appContext).save(snapshot.workoutHistory)
        HapticSettingsStore(appContext).saveEnabled(snapshot.hapticsEnabled)
        snapshot
    }

    fun restoreAfterAndroidTransferIfNeeded(): BackupSnapshot? {
        val planExists = appContext.getSharedPreferences("training-plan", Context.MODE_PRIVATE).contains("plan-v1")
        if (planExists || !status().enabled || !status().hasRecoverySnapshot) return null
        return restoreLatest().getOrNull()
    }

    companion object {
        private const val PREFERENCES = "automatic-backup"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SUCCESS = "last-success"
        private const val KEY_LAST_FAILURE = "last-failure"
        private const val KEY_LAST_FAILURE_MESSAGE = "last-failure-message"
        private const val BACKUP_DIRECTORY = "automatic-backups"
        private const val LATEST_FILE = "latest.json"
        private const val PREVIOUS_FILE = "previous.json"
        private const val ONE_TIME_WORK = "automatic-backup-after-change"
        private const val PERIODIC_WORK = "automatic-backup-daily"
    }
}

internal class AutomaticBackupWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val manager = AutomaticBackupManager(applicationContext)
        if (!manager.status().enabled) return Result.success()
        return if (manager.createBackup().isSuccess) Result.success() else Result.retry()
    }
}

internal fun encodeBackupSnapshot(snapshot: BackupSnapshot): String = JSONObject().apply {
    put("schema", 1)
    put("createdAtMillis", snapshot.createdAtMillis)
    put("trainingPlan", JSONObject(encodePlan(snapshot.plan)))
    put("dashboardLayout", JSONObject(encodeDashboardLayout(snapshot.dashboardLayout)))
    put("healthDateRange", snapshot.healthDateRange.name)
    put("workoutHistory", JSONArray(encodeWorkoutHistory(snapshot.workoutHistory)))
    put("hapticsEnabled", snapshot.hapticsEnabled)
}.toString()

internal fun decodeBackupSnapshot(value: String): BackupSnapshot {
    val root = JSONObject(value)
    check(root.getInt("schema") == 1) { "This backup was created by an unsupported app version." }
    return BackupSnapshot(
        createdAtMillis = root.getLong("createdAtMillis"),
        plan = decodePlan(root.getJSONObject("trainingPlan").toString()),
        dashboardLayout = decodeDashboardLayout(root.getJSONObject("dashboardLayout").toString()),
        healthDateRange = HealthDateRange.valueOf(root.getString("healthDateRange")),
        workoutHistory = decodeWorkoutHistory(root.getJSONArray("workoutHistory").toString()),
        hapticsEnabled = root.optBoolean("hapticsEnabled", true),
    )
}

internal fun backupStatusDetail(status: AutomaticBackupStatus, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (!status.enabled) return "Automatic backups are off. Your most recent recovery snapshot stays available until app data is removed."
    val successful = status.lastSuccessfulMillis?.let { "Last successful backup: ${formatBackupTime(it, zoneId)}." }
        ?: "Waiting for the first automatic backup."
    val failure = status.lastFailureMillis?.let {
        val message = status.lastFailureMessage?.let { detail -> ": $detail" } ?: "."
        " Last attempt failed ${formatBackupTime(it, zoneId)} and will retry automatically$message"
    }.orEmpty()
    return "$successful$failure Backups run after changes and daily, including while offline."
}

private fun formatBackupTime(millis: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        .format(Instant.ofEpochMilli(millis).atZone(zoneId))

internal fun backupIsStale(status: AutomaticBackupStatus, nowMillis: Long): Boolean =
    status.enabled && status.lastSuccessfulMillis?.let {
        Duration.between(Instant.ofEpochMilli(it), Instant.ofEpochMilli(nowMillis)).toHours() >= 48
    } != false

internal fun openAndroidBackupSettings(context: Context) {
    val privacySettings = Intent(Settings.ACTION_PRIVACY_SETTINGS)
    context.startActivity(
        if (privacySettings.resolveActivity(context.packageManager) != null) privacySettings
        else Intent(Settings.ACTION_SETTINGS),
    )
}
