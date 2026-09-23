package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.ui.editorialFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceVisualsTest {
    @Test
    fun editorialCurrencyKeepsSixFigureTotalsReadable() {
        assertEquals("$815,583", Money(81_558_290).editorialFormat())
    }

    @Test
    fun editorialCurrencyAbbreviatesMillionDollarForecasts() {
        assertEquals("$1.04M", Money(104_489_000).editorialFormat())
    }
}
