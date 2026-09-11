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

internal data class BackupSnapshot(val createdAtMillis: Long, val document: AppDocument)

internal class AutomaticBackupManager(context: Context) {
    private val appContext = context.applicationContext
    private val documentExistedAtStartup = AppDocumentStore(appContext).exists()
    private val repository = AppRepository.get(appContext).also { it.load() }
    private val statusPreferences = appContext.getSharedPreferences("backup-status-current", Context.MODE_PRIVATE)
    private val backupDirectory = File(appContext.filesDir, "recovery-current")
    private val latestFile = File(backupDirectory, "latest.json")
    private val previousFile = File(backupDirectory, "previous.json")

    fun status(): AutomaticBackupStatus = AutomaticBackupStatus(
        enabled = currentDocument().preferences.automaticBackupsEnabled,
        lastSuccessfulMillis = statusPreferences.getLong("last-success", 0L).takeIf { it > 0L },
        lastFailureMillis = statusPreferences.getLong("last-failure", 0L).takeIf { it > 0L },
        lastFailureMessage = statusPreferences.getString("last-failure-message", null),
        hasRecoverySnapshot = latestFile.isFile || previousFile.isFile,
    )

    fun setEnabled(enabled: Boolean) {
        val current = currentDocument()
        check(repository.update(current.generation) { it.copy(preferences = it.preferences.copy(automaticBackupsEnabled = enabled)) }
            is RepositoryResult.Success) { "Could not save backup settings." }
        val workManager = WorkManager.getInstance(appContext)
        if (enabled) { schedule(); requestBackup() } else {
            workManager.cancelUniqueWork("current-backup-after-change")
            workManager.cancelUniqueWork("current-backup-daily")
        }
        BackupManager(appContext).dataChanged()
    }

    fun schedule() {
        if (!status().enabled) return
        val periodic = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "current-backup-daily", ExistingPeriodicWorkPolicy.UPDATE, periodic,
        )
    }

    fun requestBackup() {
        if (!status().enabled) return
        val request = OneTimeWorkRequestBuilder<AutomaticBackupWorker>().setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "current-backup-after-change", ExistingWorkPolicy.REPLACE, request,
        )
    }

    fun createBackup(nowMillis: Long = System.currentTimeMillis()): Result<AutomaticBackupStatus> {
        return runCatching {
            check(backupDirectory.exists() || backupDirectory.mkdirs()) { "Could not create the backup directory." }
            val snapshot = BackupSnapshot(nowMillis, requireCurrentDocument())
            val temporary = File(backupDirectory, "latest.tmp")
            temporary.writeText(encodeBackupSnapshot(snapshot), Charsets.UTF_8)
            if (latestFile.exists()) latestFile.copyTo(previousFile, overwrite = true)
            check(!latestFile.exists() || latestFile.delete()) { "Could not rotate the previous recovery snapshot." }
            check(temporary.renameTo(latestFile)) { "Could not finalize the recovery snapshot." }
            statusPreferences.edit().putLong("last-success", nowMillis).remove("last-failure")
                .remove("last-failure-message").commit()
            BackupManager(appContext).dataChanged()
            status()
        }.onFailure { error ->
            statusPreferences.edit().putLong("last-failure", nowMillis)
                .putString("last-failure-message", error.message ?: "Unknown backup error").apply()
        }
    }

    fun restoreLatest(): Result<BackupSnapshot> = runCatching {
        val candidates = listOf(latestFile, previousFile).filter { it.isFile }
        check(candidates.isNotEmpty()) { "No recovery snapshot is available yet." }
        val snapshot = candidates.firstNotNullOfOrNull { runCatching { decodeBackupSnapshot(it.inputStream().use(::readBoundedCurrentJson)) }.getOrNull() }
            ?: error("The available recovery snapshots could not be read.")
        check(repository.restore(snapshot.document) is RepositoryResult.Success) { "Could not restore app data." }
        snapshot
    }

    fun restoreAfterAndroidTransferIfNeeded(): BackupSnapshot? {
        if (documentExistedAtStartup || !status().hasRecoverySnapshot) return null
        return restoreLatest().getOrNull()
    }

    private fun currentDocument(): AppDocument =
        (repository.load() as? LoadState.Ready)?.value ?: repository.currentOrDefaults()

    private fun requireCurrentDocument(): AppDocument =
        checkNotNull((repository.load() as? LoadState.Ready)?.value) { "App data must be readable before creating a backup." }
}

internal class AutomaticBackupWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val manager = AutomaticBackupManager(applicationContext)
        if (!manager.status().enabled) return Result.success()
        return if (manager.createBackup().isSuccess) Result.success() else Result.retry()
    }
}

internal fun encodeBackupSnapshot(snapshot: BackupSnapshot): String = JSONObject().apply {
    put("format", "draftingroom5.backup-current")
    put("createdAtMillis", snapshot.createdAtMillis)
    put("document", JSONObject(encodeAppDocument(snapshot.document)))
}.toString().also {
    require(snapshot.createdAtMillis >= 0) { "Backup timestamp must be nonnegative." }
    require(it.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Backup exceeds the 16 MiB limit." }
}

internal fun decodeBackupSnapshot(value: String): BackupSnapshot {
    inspectJsonStructure(value)
    val root = JSONObject(value)
    require(root.keys().asSequence().toSet() == setOf("format", "createdAtMillis", "document"))
    require(root.get("format") == "draftingroom5.backup-current") { "Unsupported backup format." }
    val created = root.get("createdAtMillis")
    require(created is Int || created is Long) { "Backup timestamp must be an integer." }
    require((created as Number).toLong() >= 0) { "Backup timestamp must be nonnegative." }
    return BackupSnapshot((created as Number).toLong(), decodeAppDocument(root.getJSONObject("document").toString()))
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
    DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").format(Instant.ofEpochMilli(millis).atZone(zoneId))

internal fun backupIsStale(status: AutomaticBackupStatus, nowMillis: Long): Boolean =
    status.enabled && status.lastSuccessfulMillis?.let { Duration.between(Instant.ofEpochMilli(it), Instant.ofEpochMilli(nowMillis)).toHours() >= 48 } != false

internal fun openAndroidBackupSettings(context: Context) {
    val privacySettings = Intent(Settings.ACTION_PRIVACY_SETTINGS)
    context.startActivity(if (privacySettings.resolveActivity(context.packageManager) != null) privacySettings else Intent(Settings.ACTION_SETTINGS))
}
