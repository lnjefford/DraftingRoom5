package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPreferencesTest {
    @Test fun acceptsAndNormalizesUniqueWatchlistSymbols() {
        assertEquals(listOf("AAPL", "^IXIC"), parseSymbols("aapl, ^ixic"))
        assertTrue(runCatching { parseSymbols("AAPL,AAPL") }.isFailure)
        assertTrue(runCatching { parseSymbols("AAPL, bad/symbol") }.isFailure)
        assertTrue(runCatching { parseSymbols("") }.isFailure)
    }
}
