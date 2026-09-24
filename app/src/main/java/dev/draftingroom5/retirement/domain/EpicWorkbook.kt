package dev.draftingroom5.retirement.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Saved workbook results only. No formula text, URI, filename, or private source bytes. */
data class EpicTotals(
    val sharesGranted: String, val vestedShares: String, val vestedValue: Money,
    val unvestedShares: String, val unvestedValue: Money, val loans: Money,
    val pretaxMinusLoans: Money, val pretaxToday: Money, val pretaxAllVested: Money,
)
data class EpicBreakdown(val name: String, val totals: EpicTotals)
data class EpicProjection(
    val date: LocalDate, val growth: String, val incomeTaxRate: String, val paydownWithShares: String,
    val pretax: Money, val loans: Money, val netPretax: Money, val taxAtSale: Money, val afterTax: Money,
)
data class EpicYear(
    val year: Int, val sharesVesting: String, val vested: Money, val unvested: Money,
    val totalBeforeLoans: Money, val loans: Money, val netPretax: Money, val costBasis: Money,
    val taxableGain: Money, val capitalGainsTax: Money, val sarOrdinaryTax: Money, val afterTax: Money,
)
data class EpicWorkbook(
    val sharePrice: Money, val totals: EpicTotals, val breakdown: List<EpicBreakdown>,
    val projection: EpicProjection, val years: List<EpicYear>, val historicalVolatilityPct: String?,
) {
    override fun toString() = "EpicWorkbook([private])"
}

fun validateEpicWorkbook(book: EpicWorkbook) {
    fun money(value: Money, negative: Boolean = false) = require(value.cents in (if (negative) -MAX_ASSET_CENTS else 0)..MAX_ASSET_CENTS)
    fun decimal(value: String, min: String = "0", max: String = "1000000000000") {
        require(value.length <= 40 && value.matches(Regex("-?\\d+(\\.\\d+)?")))
        require(BigDecimal(value) >= BigDecimal(min) && BigDecimal(value) <= BigDecimal(max))
    }
    fun totals(value: EpicTotals, allowNotApplicable: Boolean = false) {
        val counts = listOf(value.sharesGranted, value.vestedShares, value.unvestedShares)
        val hasNotApplicable = counts.any { it == "N/A" }
        require(!hasNotApplicable || allowNotApplicable)
        counts.filterNot { it == "N/A" }.forEach(::decimal)
        listOf(value.vestedValue, value.unvestedValue, value.loans, value.pretaxToday, value.pretaxAllVested).forEach { money(it) }
        money(value.pretaxMinusLoans, true)
        if (!hasNotApplicable) require(BigDecimal(value.sharesGranted).compareTo(BigDecimal(value.vestedShares) + BigDecimal(value.unvestedShares)) == 0)
        require(value.pretaxMinusLoans == value.pretaxToday - value.loans)
    }
    money(book.sharePrice); require(book.sharePrice.cents > 0); totals(book.totals)
    require(book.breakdown.size in 1..7 && book.breakdown.map { it.name }.distinct().size == book.breakdown.size)
    book.breakdown.forEach { require(it.name.isNotBlank() && it.name.length <= 120 && it.name.none(Char::isISOControl)); totals(it.totals, allowNotApplicable = true) }
    // Compare displayed cents; each independently rounded row can differ by half a cent.
    fun sumMoney(selector: (EpicTotals) -> Money) = require(kotlin.math.abs(book.breakdown.sumOf { selector(it.totals).cents } - selector(book.totals).cents) <= book.breakdown.size / 2)
    listOf<(EpicTotals) -> Money>({ it.vestedValue }, { it.unvestedValue }, { it.loans }, { it.pretaxMinusLoans }, { it.pretaxToday }, { it.pretaxAllVested }).forEach(::sumMoney)
    listOf<(EpicTotals) -> String>({ it.sharesGranted }, { it.vestedShares }, { it.unvestedShares }).forEach { get ->
        require(book.breakdown.fold(BigDecimal.ZERO) { sum, row ->
            sum + (get(row.totals).takeUnless { it == "N/A" }?.let(::BigDecimal) ?: BigDecimal.ZERO)
        }.compareTo(BigDecimal(get(book.totals))) == 0)
    }
    with(book.projection) {
        require(date.year in 1900..2500); decimal(growth, "-1", "10"); decimal(incomeTaxRate, "0", "1")
        require(paydownWithShares in setOf("Yes", "No"))
        listOf(pretax, loans, taxAtSale).forEach { money(it) }; money(netPretax, true); money(afterTax, true)
        require(kotlin.math.abs((pretax - loans - netPretax).cents) <= 1)
        require(kotlin.math.abs((netPretax - taxAtSale - afterTax).cents) <= 1)
    }
    require(book.years.size <= 500 && book.years.map { it.year }.distinct().size == book.years.size)
    require(book.years.zipWithNext().all { (a, b) -> a.year < b.year })
    book.years.forEach {
        require(it.year in 1900..2500); decimal(it.sharesVesting)
        listOf(it.vested, it.unvested, it.totalBeforeLoans, it.loans, it.costBasis, it.capitalGainsTax, it.sarOrdinaryTax).forEach { v -> money(v) }
        listOf(it.netPretax, it.taxableGain, it.afterTax).forEach { v -> money(v, true) }
        require(kotlin.math.abs((it.totalBeforeLoans - it.loans - it.netPretax).cents) <= 1)
    }
    book.historicalVolatilityPct?.let { decimal(it, "0", "100000") }
}
