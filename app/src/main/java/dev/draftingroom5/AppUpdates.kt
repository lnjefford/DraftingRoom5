package dev.draftingroom5

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal data class AppUpdateStatus(
    val lastCheckedMillis: Long? = null,
    val availableVersion: String? = null,
    val lastError: String? = null,
)

internal class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun status(): AppUpdateStatus = AppUpdateStatus(
        lastCheckedMillis = preferences.getLong(KEY_LAST_CHECKED, 0L).takeIf { it > 0L },
        availableVersion = preferences.getString(KEY_AVAILABLE_VERSION, null),
        lastError = preferences.getString(KEY_LAST_ERROR, null),
    )

    fun schedule() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun requestCheckIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val lastChecked = status().lastCheckedMillis
        if (lastChecked != null && nowMillis - lastChecked < STALE_AFTER_MILLIS) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun checkNow(nowMillis: Long = System.currentTimeMillis()): Result<AppUpdateStatus> = withContext(Dispatchers.IO) { runCatching {
        val releaseVersion = fetchLatestRelease().first
        coroutineContext.ensureActive()
        val available = releaseVersion.takeIf { compareReleaseVersions(it, BuildConfig.VERSION_NAME) > 0 }
        preferences.edit()
            .putLong(KEY_LAST_CHECKED, nowMillis)
            .apply {
                if (available == null) remove(KEY_AVAILABLE_VERSION) else putString(KEY_AVAILABLE_VERSION, available)
            }
            .remove(KEY_LAST_ERROR)
            .commit()
        status()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        preferences.edit().putString(KEY_LAST_ERROR, error.message ?: "Update check failed.").apply()
    } }

    fun clearAvailable() {
        preferences.edit().remove(KEY_AVAILABLE_VERSION).apply()
    }

    private companion object {
        const val PREFERENCES = "app-updates"
        const val KEY_LAST_CHECKED = "last-checked"
        const val KEY_AVAILABLE_VERSION = "available-version"
        const val KEY_LAST_ERROR = "last-error"
        const val PERIODIC_WORK = "app-update-check-twice-daily"
        const val ONE_TIME_WORK = "app-update-check-on-launch"
        const val STALE_AFTER_MILLIS = 6 * 60 * 60 * 1000L
    }
}

internal class AppUpdateCheckWorker(appContext: Context, parameters: WorkerParameters) :
    CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result =
        if (AppUpdateManager(applicationContext).checkNow().isSuccess) Result.success() else Result.retry()
}

internal suspend fun openDownloadedUpdateInstaller(context: Context) {
    val apk = File(context.cacheDir, "updates/latest.apk")
    withContext(Dispatchers.IO) {
        check(apk.isFile && apk.length() in 1..200L * 1024 * 1024) { "Download the update again." }
        verifyUpdateApk(context, apk)
    }
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(
            FileProvider.getUriForFile(context, "${context.packageName}.updates", apk),
            "application/vnd.android.package-archive",
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

internal fun <T> validateUpdateIdentity(
    candidatePackage: String,
    installedPackage: String,
    candidateVersion: Long,
    installedVersion: Long,
    candidateSigners: Set<T>?,
    installedSigners: Set<T>?,
) {
    check(candidatePackage == installedPackage) { "The update is for a different app." }
    check(candidateVersion > installedVersion) { "The update is no longer newer. Check for updates again." }
    check(!installedSigners.isNullOrEmpty() && candidateSigners == installedSigners) { "The update uses a different signing key." }
}

private fun verifyUpdateApk(context: Context, apk: File) {
    val manager = context.packageManager
    val candidate = manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        ?: error("The downloaded APK is invalid.")
    val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    validateUpdateIdentity(candidate.packageName, context.packageName, candidate.longVersionCode,
        installed.longVersionCode, candidate.signingInfo?.apkContentsSigners?.toSet(), installed.signingInfo?.apkContentsSigners?.toSet())
}

private val updateDownloadMutex = Mutex()

private fun connection(url: String): HttpURLConnection {
    require(url.startsWith("https://")) { "The update URL must use HTTPS." }
    return (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "DraftingRoom5/${BuildConfig.VERSION_NAME}")
    }
}

private fun fetchLatestRelease(): Pair<String, JSONObject> {
    val api = connection("https://api.github.com/repos/lnjefford/DraftingRoom5/releases/latest")
    val release = try {
        when (api.responseCode) {
            404 -> error("No published release is available yet.")
            403, 429 -> error("GitHub's request limit was reached. Try again later.")
            200 -> JSONObject(api.inputStream.bufferedReader().use { it.readText() })
            else -> error("Release server returned ${api.responseCode}.")
        }
    } finally {
        api.disconnect()
    }
    return release.getString("tag_name").removePrefix("v") to release
}

internal fun compareReleaseVersions(left: String, right: String): Int {
    val leftParts = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    return (0 until maxOf(leftParts.size, rightParts.size))
        .firstNotNullOfOrNull { index ->
            (leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 }).takeIf { it != 0 }
        } ?: 0
}

internal suspend fun downloadUpdate(context: Context, status: suspend (String) -> Unit): Boolean = updateDownloadMutex.withLock { withContext(Dispatchers.IO) {
    val (tag, release) = fetchLatestRelease()
    if (compareReleaseVersions(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext false
    val assets = release.getJSONArray("assets")
    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
        .firstOrNull { it.getString("name") == "DraftingRoom5.apk" }
        ?: error("This release does not contain an update APK.")
    val url = asset.getString("browser_download_url")
    require(url.startsWith("https://github.com/lnjefford/DraftingRoom5/releases/download/")) { "Unexpected update source." }
    withContext(Dispatchers.Main) { status("Downloading version $tag…") }
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val partial = File(directory, "latest.part")
    val download = connection(url)
    try {
        check(download.responseCode == 200) { "Download server returned ${download.responseCode}." }
        download.inputStream.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 200L * 1024 * 1024) { "Update exceeds the download size limit." }
                    output.write(buffer, 0, count)
                }
                check(total == asset.getLong("size")) { "Download was incomplete. Try again." }
            }
        }
        verifyUpdateApk(context, partial)
        partial.copyTo(File(directory, "latest.apk"), overwrite = true)
        true
    } finally {
        download.disconnect()
        partial.delete()
    }
} }
