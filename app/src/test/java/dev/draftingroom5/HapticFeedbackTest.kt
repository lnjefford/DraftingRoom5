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

}
