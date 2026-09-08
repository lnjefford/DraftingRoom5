package dev.draftingroom5

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackTest {
    @Test
    fun repeatedCueIsThrottledButDistinctEventsRemainAvailable() {
        val gate = HapticEventGate(minimumIntervalMillis = 500)

        assertTrue(gate.allow(HapticCue.SET_COMPLETE, 1_000))
        assertFalse(gate.allow(HapticCue.SET_COMPLETE, 1_250))
        assertTrue(gate.allow(HapticCue.TIMER_START, 1_250))
        assertTrue(gate.allow(HapticCue.SET_COMPLETE, 1_500))
    }

    @Test
    fun setCountUsesLeadingNumberAndFallsBackSafely() {
        assertTrue(exerciseSetCount(Exercise("id", "name", "", "4 sets", "10 reps")) == 4)
        assertTrue(exerciseSetCount(Exercise("id", "name", "", "AMRAP", "10 reps")) == 1)
    }
}
