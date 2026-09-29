package dev.draftingroom5

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** This listener grants read-only access to active media sessions when the user enables it. */
class SpotifyNotificationListener : NotificationListenerService()

internal data class SpotifyTrack(val title: String, val artist: String)

internal fun spotifyListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val component = ComponentName(context, SpotifyNotificationListener::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

internal fun currentSpotifyTrack(context: Context): SpotifyTrack? {
    if (!spotifyListenerEnabled(context)) return null
    val manager = context.getSystemService(MediaSessionManager::class.java)
    return runCatching {
        manager.getActiveSessions(ComponentName(context, SpotifyNotificationListener::class.java))
            .firstOrNull { it.packageName == "com.spotify.music" && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.metadata?.let { metadata ->
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
                if (title.isEmpty()) null else SpotifyTrack(title,
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().trim())
            }
    }.getOrNull()
}

internal fun openSpotify(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
    runCatching { context.startActivity(launch ?: Intent(Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.spotify.music")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun SpotifyNowPlayingRow() {
    val context = LocalContext.current
    var track by remember { mutableStateOf<SpotifyTrack?>(null) }
    var enabled by remember { mutableStateOf(spotifyListenerEnabled(context)) }
    LaunchedEffect(context) {
        while (true) {
            enabled = spotifyListenerEnabled(context)
            track = currentSpotifyTrack(context)
            delay(2_000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SpotifyStatus(track) { openSpotify(context) }
            if (!enabled) {
                Spacer(Modifier.size(7.dp))
                Text("Enable song info", color = AppMint,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }, style = MaterialTheme.typography.labelMedium)
            }
    }
}

@Composable
internal fun SpotifyPreviewRow() { SpotifyStatus(null, {}) }

@Composable
private fun SpotifyStatus(track: SpotifyTrack?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SpotifyMark()
        Column {
            Text(track?.title ?: "Nothing playing", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            track?.artist?.takeIf(String::isNotBlank)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpotifyMark() {
    Canvas(Modifier.size(28.dp)) {
        val green = Color(0xFF1ED760)
        val dark = Color(0xFF09200D)
        drawCircle(green)
        listOf(.39f, .52f, .65f).forEachIndexed { index, y ->
            val path = Path().apply {
                moveTo(size.width * .20f, size.height * y)
                cubicTo(size.width * .42f, size.height * (y - .10f),
                    size.width * .65f, size.height * (y - .04f), size.width * .81f, size.height * (y + .04f))
            }
            drawPath(path, dark, style = Stroke(width = size.width * (.075f - index * .008f), cap = StrokeCap.Round))
        }
    }
}
