package dev.draftingroom5.wear

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class WatchSpotifyNotificationListener : NotificationListenerService()

private fun listenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val component = ComponentName(context, WatchSpotifyNotificationListener::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

private fun spotifyTitle(context: Context): String? {
    if (!listenerEnabled(context)) return null
    return runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, WatchSpotifyNotificationListener::class.java))
            .firstOrNull { it.packageName == "com.spotify.music" && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf(String::isNotBlank)
    }.getOrNull()
}

@Composable
internal fun WatchSpotifyRow() {
    val context = LocalContext.current
    var title by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(listenerEnabled(context)) }
    LaunchedEffect(context) {
        while (true) {
            enabled = listenerEnabled(context)
            title = spotifyTitle(context)
            delay(2_000)
        }
    }
    WatchSpotifyStatus(title ?: "Nothing playing") {
        val launch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        runCatching { context.startActivity(launch ?: Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.spotify.music")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (!enabled) BasicText("Enable song info", Modifier.clickable {
        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }, style = TextStyle(color = Color(0xFF64E6B5), fontSize = 10.sp))
}

@Composable
internal fun WatchSpotifyStatus(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(20.dp)) {
            drawCircle(Color(0xFF1ED760))
            listOf(.39f, .53f, .67f).forEachIndexed { index, y ->
                val path = Path().apply {
                    moveTo(size.width * .20f, size.height * y)
                    cubicTo(size.width * .42f, size.height * (y - .10f),
                        size.width * .65f, size.height * (y - .04f), size.width * .81f, size.height * (y + .04f))
                }
                drawPath(path, Color(0xFF09200D), style = Stroke(size.width * (.075f - index * .008f), cap = StrokeCap.Round))
            }
        }
        BasicText(title, modifier = Modifier.weight(1f),
            style = TextStyle(color = Color(0xFFF4F0E8), fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
