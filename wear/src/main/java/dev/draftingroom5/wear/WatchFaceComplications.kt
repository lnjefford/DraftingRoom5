package dev.draftingroom5.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

private const val STAND_INTERVAL_MILLIS = 30 * 60 * 1000L
private const val STAND_PREFERENCES = "watch_face_stand"
private const val STAND_RESET_AT = "reset_at"

internal fun standMinutesRemaining(now: Long, resetAt: Long): Int =
    ((STAND_INTERVAL_MILLIS - (now - resetAt).coerceAtLeast(0)).coerceAtLeast(0) + 59_999L)
        .div(60_000L).toInt()

internal fun readStandResetAt(context: Context, now: Long = System.currentTimeMillis()): Long {
    val preferences = context.getSharedPreferences(STAND_PREFERENCES, Context.MODE_PRIVATE)
    val existing = preferences.getLong(STAND_RESET_AT, 0)
    if (existing in 1..now) return existing
    preferences.edit().putLong(STAND_RESET_AT, now).apply()
    return now
}

internal fun resetStandTimer(context: Context, now: Long = System.currentTimeMillis()) {
    context.getSharedPreferences(STAND_PREFERENCES, Context.MODE_PRIVATE).edit()
        .putLong(STAND_RESET_AT, now).apply()
    updateStandComplication(context)
}

internal fun updateStandComplication(context: Context) {
    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, StandComplicationService::class.java))
        .requestUpdateAll()
}

internal fun updateWeatherComplication(context: Context) {
    ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, WeatherComplicationService::class.java))
        .requestUpdateAll()
}

private fun plain(value: String) = PlainComplicationText.Builder(value).build()

private fun shortText(value: String, description: String, tapAction: PendingIntent): ComplicationData =
    ShortTextComplicationData.Builder(plain(value), plain(description))
        .setTapAction(tapAction)
        .build()

class WeatherComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val temperature = WatchSyncRepository.get(this).state.value?.today?.temperatureF
        val value = temperature?.let { "$it°" } ?: "--"
        val weatherIntent = sequenceOf("com.google.android.apps.weather", "com.google.android.wearable.weather")
            .mapNotNull { packageManager.getLaunchIntentForPackage(it) }.firstOrNull()
            ?: Intent(this, WatchActivity::class.java)
        val tapAction = PendingIntent.getActivity(this, 1, weatherIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return shortText(value, "Weather. Open forecast.", tapAction)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val tap = PendingIntent.getActivity(this, 1, Intent(this, WatchActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return shortText("64°", "Weather", tap)
    }
}

class StandComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val schedule = readStandSchedule(this)
        val active = schedule.activeAt(Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime())
        val remaining = if (active) standMinutesRemaining(now,
            schedule.effectiveResetAt(now, readStandResetAt(this, now), zone)) else 0
        val value = if (!active) "OFF" else if (remaining == 0) "STAND" else "${remaining}m"
        val description = if (!active) "Stand reminder paused outside scheduled hours."
            else if (remaining == 0) "Time to stand. Tap to reset."
            else "Stand in $remaining minutes. Tap to reset."
        val tap = PendingIntent.getActivity(this, 2, Intent(this, StandResetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return shortText(value, description, tap)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val tap = PendingIntent.getActivity(this, 2, Intent(this, StandResetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return shortText("08m", "Stand reminder", tap)
    }
}
