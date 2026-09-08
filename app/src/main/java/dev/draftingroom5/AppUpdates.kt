package dev.draftingroom5

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AppUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("Check for the latest version without leaving the app.") }
    var pendingInstall by rememberSaveable { mutableStateOf(false) }
    val apk = File(context.cacheDir, "updates/latest.apk")
    fun install() {
        try {
            check(apk.isFile) { "Download the update again." }
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            message = "Confirm the update in Android's installer. If you cancel, tap the button to try again."
        } catch (error: Exception) {
            message = "Could not open the installer: ${error.message}"
        }
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingInstall && context.packageManager.canRequestPackageInstalls()) install()
        else message = "Installation permission was not granted. Tap the button to try again."
        pendingInstall = false
    }
    BrandedCard(Modifier.fillMaxWidth(), containerColor = AppSurfaceRaised) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("App updates · ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    message = "Checking for updates…"
                    try {
                        val available = downloadUpdate(context) { status -> message = status }
                        if (!available) message = "You're running the latest version."
                        else if (context.packageManager.canRequestPackageInstalls()) install()
                        else {
                            message = "Allow DraftingRoom5 to install updates, then return to the app."
                            pendingInstall = true
                            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        message = "Update failed: ${error.message ?: "Check your connection and try again."}"
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "Please wait…" else "Check & install update") }
        }
    }
}

private fun connection(url: String): HttpURLConnection {
    require(url.startsWith("https://")) { "The update URL must use HTTPS." }
    return (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "DraftingRoom5/${BuildConfig.VERSION_NAME}")
    }
}

private suspend fun downloadUpdate(context: Context, status: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    val api = connection("https://api.github.com/repos/lnjefford/DraftingRoom5/releases/latest")
    val release = try {
        when (api.responseCode) {
            404 -> error("No published release is available yet.")
            403, 429 -> error("GitHub's request limit was reached. Try again later.")
            200 -> JSONObject(api.inputStream.bufferedReader().use { it.readText() })
            else -> error("Release server returned ${api.responseCode}.")
        }
    } finally { api.disconnect() }
    val tag = release.getString("tag_name").removePrefix("v")
    if (tag == BuildConfig.VERSION_NAME) return@withContext false
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
        val manager = context.packageManager
        val candidate = manager.getPackageArchiveInfo(partial.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("The downloaded APK is invalid.")
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(candidate.packageName == context.packageName) { "The update is for a different app." }
        if (candidate.longVersionCode <= installed.longVersionCode) return@withContext false
        val currentSigners = installed.signingInfo?.apkContentsSigners?.toSet()
        check(!currentSigners.isNullOrEmpty() && candidate.signingInfo?.apkContentsSigners?.toSet() == currentSigners) {
            "This release uses a different signing key. A one-time manual installation is required before in-app updates can work."
        }
        partial.copyTo(File(directory, "latest.apk"), overwrite = true)
        true
    } finally {
        download.disconnect()
        partial.delete()
    }
}
