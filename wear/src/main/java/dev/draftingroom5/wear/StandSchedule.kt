package dev.draftingroom5.wear

import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val SETTINGS = "watch_face_stand_schedule"
private const val DAYS = "days"
private const val START = "start"
private const val END = "end"

/** Monday is bit zero. The end hour is exclusive, in the watch's local timezone. */
internal data class StandSchedule(val days: Int = 0b0011111, val startHour: Int = 9, val endHour: Int = 17) {
    init {
        require(days in 0..127)
        require(startHour in 0..23 && endHour in 1..24 && startHour < endHour)
    }

    fun activeAt(time: LocalDateTime): Boolean =
        days and (1 shl (time.dayOfWeek.value - 1)) != 0 && time.hour in startHour until endHour

    fun effectiveResetAt(now: Long, resetAt: Long, zone: ZoneId): Long {
        val todayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(startHour, 0)
            .atZone(zone).toInstant().toEpochMilli()
        return maxOf(resetAt, todayStart)
    }
}

internal fun readStandSchedule(context: Context): StandSchedule {
    val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
    return runCatching {
        StandSchedule(settings.getInt(DAYS, 0b0011111), settings.getInt(START, 9), settings.getInt(END, 17))
    }.getOrDefault(StandSchedule())
}

internal fun saveStandSchedule(context: Context, schedule: StandSchedule) {
    context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
        .putInt(DAYS, schedule.days).putInt(START, schedule.startHour).putInt(END, schedule.endHour).apply()
    updateStandComplication(context)
}
