package dev.draftingroom5

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

internal enum class HapticCue {
    TIMER_START,
    COUNTDOWN_COMPLETE,
    TIMER_COMPLETE,
    SET_COMPLETE,
    WORKOUT_COMPLETE,
}

internal class HapticEventGate(private val minimumIntervalMillis: Long = 500L) {
    private val lastPlayedAt = mutableMapOf<HapticCue, Long>()

    fun allow(cue: HapticCue, nowMillis: Long): Boolean {
        val last = lastPlayedAt[cue]
        if (last != null && nowMillis - last < minimumIntervalMillis) return false
        lastPlayedAt[cue] = nowMillis
        return true
    }
}

internal class WorkoutHaptics(context: Context) {
    private val appContext = context.applicationContext
    private val gate = HapticEventGate()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun perform(cue: HapticCue, enabled: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        if (!enabled || !systemHapticsEnabled() || vibrator?.hasVibrator() != true || !gate.allow(cue, nowMillis)) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                when (cue) {
                    HapticCue.SET_COMPLETE -> VibrationEffect.EFFECT_TICK
                    HapticCue.TIMER_START -> VibrationEffect.EFFECT_CLICK
                    HapticCue.COUNTDOWN_COMPLETE, HapticCue.TIMER_COMPLETE -> VibrationEffect.EFFECT_HEAVY_CLICK
                    HapticCue.WORKOUT_COMPLETE -> VibrationEffect.EFFECT_DOUBLE_CLICK
                },
            )
        } else {
            VibrationEffect.createOneShot(
                when (cue) {
                    HapticCue.SET_COMPLETE -> 20L
                    HapticCue.TIMER_START -> 35L
                    HapticCue.COUNTDOWN_COMPLETE, HapticCue.TIMER_COMPLETE -> 70L
                    HapticCue.WORKOUT_COMPLETE -> 110L
                },
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
        }
        vibrator.vibrate(effect)
    }

    fun settingsPresentation(): HapticSettingsPresentation = hapticSettingsPresentation(
        hasVibrator = vibrator?.hasVibrator() == true,
        systemEnabled = systemHapticsEnabled(),
    )

    private fun systemHapticsEnabled(): Boolean = Settings.System.getInt(
        appContext.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) != 0
}
