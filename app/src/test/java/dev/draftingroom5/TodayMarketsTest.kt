package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TodayMarketsTest {
    private val chart = """{
        "chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":123.5,
        "previousClose":120.0,"regularMarketTime":1790899200,"currency":"USD"},
        "indicators":{"quote":[{"close":[118.0,null,121.0,123.5]}]}}]}
    }"""

    @Test fun parsesLatestAvailableQuoteAndSkipsMissingChartPoints() {
        val quote = parseTodayMarketQuote("AAPL", chart)
        assertEquals(123.5, quote.price, 0.0)
        assertEquals(listOf(118.0, 121.0, 123.5), quote.points)
        assertEquals(2.9166666667, quote.changePercent!!, 0.00001)
        assertEquals(1790899200L, quote.updatedAtSeconds)
    }

    @Test fun rejectsMismatchedSymbolAndMissingPrice() {
        assertThrows(IllegalArgumentException::class.java) { parseTodayMarketQuote("MSFT", chart) }
        assertThrows(IllegalArgumentException::class.java) {
            parseTodayMarketQuote("AAPL", chart.replace("\"regularMarketPrice\":123.5", "\"regularMarketPrice\":null"))
        }
    }
}
