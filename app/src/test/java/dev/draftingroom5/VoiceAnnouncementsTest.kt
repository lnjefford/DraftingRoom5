package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAnnouncementsTest {
    @Test
    fun voiceRateIsClampedAndLabeled() {
        assertEquals(MIN_VOICE_RATE, normalizedVoiceRate(0.2f))
        assertEquals(MAX_VOICE_RATE, normalizedVoiceRate(2f))
        assertEquals("Slow", voiceRateLabel(0.8f))
        assertEquals("Normal", voiceRateLabel(1f))
        assertEquals("Fast", voiceRateLabel(1.2f))
    }

    @Test
    fun timerAndSetAnnouncementsDescribeTransitions() {
        assertEquals("Get ready.", voiceAnnouncementText(VoiceCue.CountdownStarted))
        assertEquals("3", voiceAnnouncementText(VoiceCue.CountdownTick(3)))
        assertEquals(
            "Plank timer started. 1 minute and 5 seconds.",
            voiceAnnouncementText(VoiceCue.TimerStarted("Plank", 65)),
        )
        assertEquals(
            "Curl. All 3 sets complete. Next exercise: Press.",
            voiceAnnouncementText(VoiceCue.SetCompleted("Curl", 3, 3, "Press")),
        )
    }
}
