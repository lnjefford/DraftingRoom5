package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.domain.FilingStatus
import kotlin.math.*

/** 2026 federal/Wisconsin planning policy. Future indexed thresholds follow assumed inflation.
 * Fixed statutory thresholds remain nominal. Sources and exclusions: docs/RETIREMENT_MODEL.md.
 */
object PlanningTaxPolicy {
    const val ID = "household-2026-v2"
    val supportedStates = setOf("WI")
    private val federalTops = arrayOf(
        doubleArrayOf(12400.0,50400.0,105700.0,201775.0,256225.0,640600.0),
        doubleArrayOf(24800.0,100800.0,211400.0,403550.0,512450.0,768700.0),
        doubleArrayOf(12400.0,50400.0,105700.0,201775.0,256225.0,384350.0),
        doubleArrayOf(17700.0,67450.0,105700.0,201750.0,256200.0,640600.0))
    private val federalRates = doubleArrayOf(.10,.12,.22,.24,.32,.35,.37)
    private val deductions = doubleArrayOf(16100.0,32200.0,16100.0,24150.0)
    private val gainZero = doubleArrayOf(49450.0,98900.0,49450.0,66200.0)
    private val gainFifteen = doubleArrayOf(545500.0,613700.0,306850.0,579600.0)
    private val niit = doubleArrayOf(200000.0,250000.0,125000.0,200000.0)

    private fun bracket(amount: Double, tops: DoubleArray, rates: DoubleArray, index: Double): Double {
        var total = 0.0; var bottom = 0.0
        for (i in rates.indices) {
            val top = tops.getOrElse(i) { Double.POSITIVE_INFINITY } * index
            total += max(0.0, min(amount, top) - bottom) * rates[i]
            if (amount <= top) break
            bottom = top
        }
        return total
    }

    fun taxableSocialSecurity(ordinary: Double, gains: Double, benefits: Double, filing: FilingStatus): Double =
        ReferenceTaxPolicy.taxableSocialSecurity(ordinary, gains, benefits, filing)

    /** Inputs and result are nominal dollars for this tax year. */
    fun federal(ordinary: Double, gains: Double, filing: FilingStatus, age: Int = 60,
        spouseAge: Int? = null, year: Int = 2026, index: Double = 1.0): Double {
        val i = filing.ordinal
        val joint = filing == FilingStatus.MARRIED_FILING_JOINTLY
        val seniors = (if (age >= 65) 1 else 0) + (if (joint && spouseAge != null && spouseAge >= 65) 1 else 0)
        val unmarried = filing == FilingStatus.SINGLE || filing == FilingStatus.HEAD_OF_HOUSEHOLD
        val standard = (deductions[i] + seniors * if (unmarried) 2050.0 else 1650.0) * index
        val enhanced = if (year in 2025..2028 && filing != FilingStatus.MARRIED_FILING_SEPARATELY)
            seniors * max(6000.0 - .06 * max(ordinary + gains - if (joint) 150000.0 else 75000.0, 0.0), 0.0) else 0.0
        val deduction = standard + enhanced
        val base = max(ordinary - deduction, 0.0)
        val gain = max(gains - max(deduction - ordinary, 0.0), 0.0)
        return bracket(base, federalTops[i], federalRates, index) +
            max(0.0, min(base + gain, gainFifteen[i] * index) - max(base, gainZero[i] * index)) * .15 +
            max(0.0, base + gain - max(base, gainFifteen[i] * index)) * .20 +
            min(max(gains, 0.0), max(ordinary + gains - niit[i], 0.0)) * .038
    }

    /** Social Security is excluded before calling. Retirement subtraction applies to SELF income only. */
    fun wisconsin(ordinary: Double, gains: Double, filing: FilingStatus, age: Int = 60,
        spouseAge: Int? = null, retirementIncome: Double = 0.0, index: Double = 1.0,
        federalAgi: Double = ordinary + gains): Double {
        val joint = filing == FilingStatus.MARRIED_FILING_JOINTLY
        val subtraction = when {
            age >= 67 -> min(retirementIncome, 24000.0)
            age >= 65 && federalAgi < if (joint) 30000.0 else 15000.0 -> min(retirementIncome,5000.0)
            else -> 0.0
        }
        val income = max(ordinary + max(gains,0.0)*.70 - subtraction, 0.0)
        val real = income / index
        val standard = when (filing) {
            FilingStatus.SINGLE -> (13960.0 - .12 * max(real - 20120,0.0)).coerceIn(0.0,13960.0)
            FilingStatus.MARRIED_FILING_JOINTLY -> (25840.0 - .19778 * max(real - 29040,0.0)).coerceIn(0.0,25840.0)
            FilingStatus.MARRIED_FILING_SEPARATELY -> (12280.0 - .19778 * max(real - 13780,0.0)).coerceIn(0.0,12280.0)
            FilingStatus.HEAD_OF_HOUSEHOLD -> if (real <= 58827) (18030.0 - .22515 * max(real - 20120,0.0)).coerceIn(0.0,18030.0)
                else max(13960.0 - .12 * (real - 20120),0.0)
        } * index
        val exemptions = 700.0 * (if (joint) 2 else 1) + (if (age >= 65) 250 else 0) +
            (if (joint && spouseAge != null && spouseAge >= 65) 250 else 0)
        val tops = when (filing) {
            FilingStatus.MARRIED_FILING_JOINTLY -> doubleArrayOf(20150.0,69260.0,443630.0)
            FilingStatus.MARRIED_FILING_SEPARATELY -> doubleArrayOf(10080.0,34630.0,221820.0)
            else -> doubleArrayOf(15110.0,51950.0,332720.0)
        }
        return bracket(max(income-standard-exemptions,0.0),tops,doubleArrayOf(.035,.044,.053,.0765),index)
    }

    /** Wisconsin Marketplace assumption, no employer coverage; household size includes Medicare spouse.
     * 2025 FPL is used for 2026 coverage. No speculative extension of the expired enhanced credits.
     */
    fun aca(magi: Double, premium: Double, household: Int, filing: FilingStatus, index: Double = 1.0): Double {
        require(household in 1..20 && magi.isFinite() && premium.isFinite() && index > 0)
        if (premium <= 0 || filing == FilingStatus.MARRIED_FILING_SEPARATELY) return 0.0
        val ratio = magi / ((15650.0 + (household-1)*5500.0)*index)
        if (ratio <= 1.0 || ratio > 4.0) return 0.0 // WI BadgerCare through 100%; not a Marketplace credit.
        fun lerp(lo: Double, hi: Double, a: Double, b: Double) = a + (b-a)*(ratio-lo)/(hi-lo)
        val rate = when {
            ratio < 1.33 -> .021
            ratio < 1.5 -> lerp(1.33,1.5,.0314,.0419)
            ratio < 2 -> lerp(1.5,2.0,.0419,.066)
            ratio < 2.5 -> lerp(2.0,2.5,.066,.0844)
            ratio < 3 -> lerp(2.5,3.0,.0844,.0996)
            else -> .0996
        }
        return max(premium - max(magi,0.0)*rate,0.0)
    }

    fun rmdDivisor(age: Int, birthYear: Int): Double? {
        val start = when { birthYear >= 1960 -> 75; birthYear >= 1951 -> 73; else -> 72 }
        if (age < start) return null
        return if (age >= 120) 2.0 else if (age == 72) 27.4 else ReferenceTaxPolicy.rmdDivisor(age)
    }
}
