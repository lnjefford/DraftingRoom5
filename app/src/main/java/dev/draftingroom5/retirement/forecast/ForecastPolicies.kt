package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.domain.FilingStatus
import dev.draftingroom5.retirement.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

/** Frozen behavioral policy, NOT a current-law tax calculator. See DR5-074.md. */
object ReferenceTaxPolicy {
    const val ID = "finance-reference-v1"
    const val FEDERAL_ID = "federal-2026-reference-v1"
    const val STATE_ID = "wi-2024-reference-v1"
    const val ACA_ID = "aca-2024-fpl-step-reference-v1"
    private val rates = doubleArrayOf(.10, .12, .22, .24, .32, .35, .37)
    private val fed = arrayOf(
        doubleArrayOf(12400.0, 50400.0, 105700.0, 201775.0, 256225.0, 640600.0),
        doubleArrayOf(24800.0, 100800.0, 211400.0, 403550.0, 512450.0, 768700.0),
        doubleArrayOf(12400.0, 50400.0, 105700.0, 201775.0, 256225.0, 384350.0),
        doubleArrayOf(17700.0, 67450.0, 105700.0, 201750.0, 256200.0, 640600.0),
    )
    private val deductions = doubleArrayOf(16100.0, 32200.0, 16100.0, 24150.0)
    private val gain0 = doubleArrayOf(49450.0, 98900.0, 49450.0, 66200.0)
    private val gain15 = doubleArrayOf(545500.0, 613700.0, 306850.0, 579600.0)
    private val niit = doubleArrayOf(200000.0, 250000.0, 125000.0, 200000.0)
    private val wi = arrayOf(
        doubleArrayOf(14320.0, 28640.0, 315310.0),
        doubleArrayOf(19090.0, 38190.0, 420420.0),
        doubleArrayOf(9550.0, 19090.0, 210210.0),
        doubleArrayOf(14320.0, 28640.0, 315310.0),
    )
    private val wiRates = doubleArrayOf(.035, .044, .053, .0765)
    private val wiDeduction = doubleArrayOf(13460.0, 24950.0, 11870.0, 17390.0)
    val supportedStates = setOf("WI", "AK", "FL", "NV", "NH", "SD", "TN", "TX", "WA", "WY")

    private fun bracket(amount: Double, tops: DoubleArray, rates: DoubleArray): Double {
        var tax = 0.0
        var bottom = 0.0
        for (i in rates.indices) {
            val top = tops.getOrElse(i) { Double.POSITIVE_INFINITY }
            tax += max(0.0, min(amount, top) - bottom) * rates[i]
            if (amount <= top) break
            bottom = top
        }
        return tax
    }
    fun federal(ordinary: Double, gains: Double, filing: FilingStatus): Double {
        checkedAmount(ordinary); checkedAmount(gains)
        val i = filing.ordinal
        val base = max(ordinary - deductions[i], 0.0)
        val gain = max(gains, 0.0)
        return bracket(base, fed[i], rates) +
            max(0.0, min(base + gain, gain15[i]) - max(base, gain0[i])) * .15 +
            max(0.0, base + gain - max(base, gain15[i])) * .20 +
            min(gain, max(ordinary + gain - niit[i], 0.0)) * .038
    }
    fun wisconsin(ordinary: Double, gains: Double, filing: FilingStatus): Double {
        checkedAmount(ordinary); checkedAmount(gains)
        val i = filing.ordinal
        return bracket(max(ordinary + max(gains, 0.0) * .70 - wiDeduction[i], 0.0), wi[i], wiRates)
    }
    fun state(ordinary: Double, gains: Double, filing: FilingStatus, state: String): Double {
        require(state in supportedStates) { "Unsupported state policy." }
        return if (state == "WI") wisconsin(ordinary, gains, filing) else 0.0
    }
    fun taxableSocialSecurity(ordinary: Double, gains: Double, benefits: Double, filing: FilingStatus): Double {
        checkedAmount(ordinary); checkedAmount(gains); checkedAmount(benefits)
        if (benefits <= 0) return 0.0
        val (base, upper) = when (filing) {
            FilingStatus.MARRIED_FILING_JOINTLY -> 32000.0 to 44000.0
            FilingStatus.MARRIED_FILING_SEPARATELY -> 0.0 to 0.0
            else -> 25000.0 to 34000.0
        }
        val provisional = ordinary + gains + benefits * .5
        if (upper <= base) return min(benefits * .85, provisional * .85)
        return if (provisional <= upper) min(benefits * .5, max(provisional - base, 0.0) * .5)
        else min(benefits * .85, max(provisional - upper, 0.0) * .85 + min(benefits * .5, (upper - base) * .5))
    }
    fun aca(magi: Double, premium: Double, household: Int, extended: Boolean): Double {
        checkedAmount(magi); checkedAmount(premium); require(household in 1..20)
        if (premium <= 0 || magi < 0) return 0.0
        val ratio = magi / (15060.0 + (household - 1) * 5380.0)
        val rate = if (extended) when {
            ratio < 1.5 -> 0.0; ratio < 2 -> .02; ratio < 2.5 -> .04
            ratio < 3 -> .06; else -> .085
        } else when {
            ratio < 1.33 || ratio >= 4 -> return 0.0
            ratio < 1.5 -> .0306; ratio < 2 -> .0408; ratio < 2.5 -> .0612
            ratio < 3 -> .0810; else -> .0956
        }
        return max(premium - magi * rate, 0.0)
    }
    private val divisors = doubleArrayOf(26.5,25.5,24.6,23.7,22.9,22.0,21.1,20.2,19.4,18.5,17.7,16.8,16.0,15.2,14.4,13.7,12.9,12.2,11.5,10.8,10.1,9.5,8.9,8.4,7.8,7.3,6.8,6.4,6.0,5.6,5.2,4.9,4.6,4.3,4.1,3.9,3.7,3.5,3.4,3.3,3.1,3.0,2.9,2.8,2.7,2.5,2.3,2.0)
    fun rmdDivisor(age: Int): Double? = divisors.getOrNull(age - 73)
}

/** Maximum publication magnitude: 9 quadrillion cents, below exact integer Double range. */
internal const val MAX_FORECAST_DOLLARS = 90_000_000_000_000.0
internal fun checkedAmount(value: Double): Double {
    require(value.isFinite() && abs(value) <= MAX_FORECAST_DOLLARS) { "Forecast exceeds supported numerical range." }
    return value
}
internal fun dollars(value: Money): Double = checkedAmount(value.cents / 100.0)
fun forecastMoney(value: Double): Money = Money(BigDecimal.valueOf(checkedAmount(value)).movePointRight(2)
    .setScale(0, RoundingMode.HALF_UP).longValueExact())

object MortgageCalculator {
    data class Row(val month: Int, val payment: Double, val interest: Double, val principal: Double, val balance: Double)
    fun monthlyPayment(principal: Double, rateBps: Int, termMonths: Int): Double {
        checkedAmount(principal); require(principal >= 0 && rateBps in 0..2500 && termMonths in 0..600)
        if (principal == 0.0 || termMonths == 0) return 0.0
        val r = rateBps / 10000.0 / 12
        return if (r == 0.0) principal / termMonths else principal * r / -expm1(-termMonths * ln1p(r))
    }
    fun schedule(principal: Double, rateBps: Int, termMonths: Int, months: Int = termMonths): List<Row> {
        require(months in 0..600)
        val payment = monthlyPayment(principal, rateBps, termMonths)
        var balance = principal
        return buildList {
            for (month in 1..min(months, termMonths)) {
                val interest = balance * rateBps / 10000.0 / 12
                val paid = min(max(payment - interest, 0.0), balance)
                balance = max(balance - paid, 0.0)
                add(Row(month, min(payment, paid + interest), interest, paid, balance))
                if (balance <= 0) break
            }
        }
    }
    fun balanceAfterMonths(principal: Double, rateBps: Int, termMonths: Int, months: Int): Double =
        schedule(principal, rateBps, termMonths, months).lastOrNull()?.balance ?: principal
}

enum class Bucket { TRADITIONAL, ROTH, HSA, TAXABLE, NON_RETIREMENT }
data class WithdrawalRule(val bucket: Bucket, val minAge: Int = 0, val maxAge: Int = 200, val annualCap: Money? = null) {
    init { require(minAge in 0..200 && maxAge in minAge..200 && (annualCap == null || annualCap.cents >= 0)) }
}
object WithdrawalPolicy {
    fun defaultRules(medical: Money = Money(0)) = listOf(
        WithdrawalRule(Bucket.TAXABLE), WithdrawalRule(Bucket.HSA, annualCap = medical),
        WithdrawalRule(Bucket.ROTH, minAge = 60), WithdrawalRule(Bucket.TRADITIONAL, minAge = 60),
        WithdrawalRule(Bucket.NON_RETIREMENT, minAge = 60),
    )
    data class Result(val balances: DoubleArray, val ordinary: Double, val gains: Double, val taxFree: Double, val unmet: Double, val taxableWithdrawn: Double)
    fun apply(balances: DoubleArray, age: Int, need: Double, rules: List<WithdrawalRule>, gainRatio: Double = .30): Result {
        require(balances.size == 5 && balances.all { it.isFinite() && it >= 0 }); checkedAmount(need)
        require(need >= 0 && gainRatio.isFinite() && gainRatio <= 1.0)
        val out = balances.copyOf()
        var remaining = need; var ordinary = 0.0; var gains = 0.0; var free = 0.0; var taxable = 0.0
        for (rule in rules) {
            if (age !in rule.minAge..rule.maxAge) continue
            val take = min(remaining, min(out[rule.bucket.ordinal], rule.annualCap?.let(::dollars) ?: Double.POSITIVE_INFINITY))
            out[rule.bucket.ordinal] -= take; remaining -= take
            when (rule.bucket) {
                Bucket.TRADITIONAL, Bucket.NON_RETIREMENT -> ordinary += take
                Bucket.ROTH, Bucket.HSA -> free += take
                Bucket.TAXABLE -> { taxable += take; gains += take * gainRatio }
            }
        }
        return Result(out, ordinary, gains, free, remaining, taxable)
    }
}
