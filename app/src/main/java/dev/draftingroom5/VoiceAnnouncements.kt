package dev.draftingroom5

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

internal data class VoiceAnnouncementSettings(
    val enabled: Boolean = true,
    val rate: Float = DEFAULT_VOICE_RATE,
)

internal enum class VoiceAvailability {
    INITIALIZING,
    READY,
    UNAVAILABLE,
}

internal sealed interface VoiceCue {
    data object CountdownStarted : VoiceCue
    data class CountdownTick(val seconds: Int) : VoiceCue
    data class TimerStarted(val exerciseName: String, val durationSeconds: Int) : VoiceCue
    data class TimerCompleted(val exerciseName: String) : VoiceCue
    data class SetCompleted(
        val exerciseName: String,
        val completedSets: Int,
        val totalSets: Int,
        val nextExerciseName: String?,
    ) : VoiceCue
    data object WorkoutCompleted : VoiceCue
}

internal class WorkoutVoiceAnnouncements(
    context: Context,
    private val onAvailabilityChanged: (VoiceAvailability) -> Unit,
) {
    private var availability = VoiceAvailability.INITIALIZING
    private var textToSpeech: TextToSpeech? = null
    private var closed = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (closed) return@TextToSpeech
            val engine = textToSpeech
            availability = if (status == TextToSpeech.SUCCESS && engine != null) {
                val languageResult = engine.setLanguage(Locale.getDefault())
                if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    VoiceAvailability.UNAVAILABLE
                } else {
                    VoiceAvailability.READY
                }
            } else {
                VoiceAvailability.UNAVAILABLE
            }
            onAvailabilityChanged(availability)
        }
    }

    fun announce(cue: VoiceCue, settings: VoiceAnnouncementSettings) {
        val engine = textToSpeech ?: return
        if (!settings.enabled || availability != VoiceAvailability.READY) return
        engine.setSpeechRate(normalizedVoiceRate(settings.rate))
        engine.speak(voiceAnnouncementText(cue), TextToSpeech.QUEUE_FLUSH, null, cue.javaClass.simpleName)
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        closed = true
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

internal const val DEFAULT_VOICE_RATE = 1.0f
internal const val MIN_VOICE_RATE = 0.75f
internal const val MAX_VOICE_RATE = 1.5f

internal fun normalizedVoiceRate(rate: Float): Float =
    if (rate.isFinite()) rate.coerceIn(MIN_VOICE_RATE, MAX_VOICE_RATE) else DEFAULT_VOICE_RATE

internal fun voiceRateLabel(rate: Float): String = when {
    rate < 0.9f -> "Slow"
    rate > 1.1f -> "Fast"
    else -> "Normal"
}

internal fun voiceAnnouncementText(cue: VoiceCue): String = when (cue) {
    VoiceCue.CountdownStarted -> "Get ready."
    is VoiceCue.CountdownTick -> cue.seconds.toString()
    is VoiceCue.TimerStarted -> "${cue.exerciseName} timer started. ${durationText(cue.durationSeconds)}."
    is VoiceCue.TimerCompleted -> "${cue.exerciseName} timer complete."
    is VoiceCue.SetCompleted -> when {
        cue.completedSets < cue.totalSets ->
            "${cue.exerciseName}. Set ${cue.completedSets} of ${cue.totalSets} complete. Next set."
        cue.nextExerciseName != null ->
            "${cue.exerciseName}. All ${cue.totalSets} sets complete. Next exercise: ${cue.nextExerciseName}."
        else -> "${cue.exerciseName}. All ${cue.totalSets} sets complete."
    }
    VoiceCue.WorkoutCompleted -> "Workout complete."
}

private fun durationText(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return when {
        minutes == 0 -> "$seconds ${if (seconds == 1) "second" else "seconds"}"
        remainingSeconds == 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
        else -> "$minutes ${if (minutes == 1) "minute" else "minutes"} and $remainingSeconds seconds"
    }
}
