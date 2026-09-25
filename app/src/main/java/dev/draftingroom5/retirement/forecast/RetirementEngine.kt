package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.domain.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Random
import kotlin.math.*

/** Private primitive arrays never escape. Money is rounded only at the read/publication boundary. */
class ForecastResult internal constructor(
    val generation: Long, val planRevision: Long, val currentAge: Int, val endAge: Int,
    val paths: Int, val seed: Long?, val stochastic: Boolean,
    private val series: Array<DoubleArray>, failures: List<PathFailure>,
    val maximumTaxFundingResidual: Money, val taxResidualYears: Int,
    warnings: List<String>, spendingByAge: DoubleArray,
) {
    val engineVersion = "dr5-household-engine-3"
    val taxPolicyId = PlanningTaxPolicy.ID
    val failures = frozen(failures)
    val warnings = frozen(warnings)
    private val spending = spendingByAge.copyOf()
    val successRate: Double = (paths - failures.size).toDouble() / paths
    val taxFundingConverged get() = taxResidualYears == 0
    private val width = endAge - currentAge + 1
    fun value(channel: ForecastChannel, path: Int, age: Int): Money = forecastMoney(raw(channel, path, age))
    internal fun raw(channel: ForecastChannel, path: Int, age: Int): Double {
        require(path in 0 until paths && age in currentAge..endAge)
        return series[channel.ordinal][path * width + age - currentAge]
    }
    fun percentile(channel: ForecastChannel, age: Int, percentage: Double): Money {
        require(percentage.isFinite() && percentage in 0.0..100.0)
        val sorted = DoubleArray(paths) { raw(channel, it, age) }.also { it.sort() }
        val index = (paths - 1) * percentage / 100
        val low = floor(index).toInt(); val high = ceil(index).toInt()
        return forecastMoney(sorted[low] + (sorted[high] - sorted[low]) * (index - low))
    }
    fun mean(channel: ForecastChannel, age: Int): Money = forecastMoney((0 until paths).sumOf { raw(channel, it, age) / paths })
    fun annualSpending(age: Int): Money {
        require(age in currentAge..endAge)
        return forecastMoney(spending[age - currentAge])
    }
    internal fun writeSeries(write: (Double) -> Unit) {
        for (channel in ForecastChannel.entries) for (path in 0 until paths) for (age in currentAge..endAge) {
            write(raw(channel, path, age))
        }
    }
    internal fun writeSpending(write: (Double) -> Unit) { spending.forEach(write) }
    fun depletionAge(path: Int): Int { require(path in 0 until paths); return failures.firstOrNull { it.path == path }?.age ?: endAge + 1 }
    override fun toString() = "ForecastResult(generation=$generation, paths=$paths, [private])"
}
enum class ForecastChannel { TRADITIONAL, ROTH, HSA, TAXABLE, NON_RETIREMENT, TOTAL, EPIC, EPIC_AFTER_TAX, PROPERTY, UNMET, TAX, ACA, TAX_RESIDUAL }
enum class FailureCause { ACCESS_OR_RULE_LIMIT, ASSETS_EXHAUSTED }
data class PathFailure(val path: Int, val age: Int, val unmet: Money, val retainedAssets: Money, val cause: FailureCause)

/** Copied path/year/asset arithmetic returns. Used only for reproducible comparisons, never persisted. */
class ReturnTape(val paths: Int, val years: Int, returns: DoubleArray) {
    private val values = returns.copyOf()
    init { require(paths in 1..20000 && years in 1..130 && paths.toLong() * years <= 1_000_000)
        require(values.size == paths * years * 6 && values.all { it.isFinite() && abs(it) <= 100 }) }
    internal fun at(path: Int, year: Int, asset: Int) = values[(path * years + year) * 6 + asset]
}

/** Standard-normal shocks shared with reference tests; Epic is path/year, property is property/path. */
internal class ForecastNoiseTape(val paths: Int, val years: Int, val properties: Int, epic: DoubleArray, property: DoubleArray) {
    private val epicValues = epic.copyOf()
    private val propertyValues = property.copyOf()
    init {
        require(paths in 1..20000 && years in 1..130 && properties in 0..100)
        require(epicValues.size == paths * years && propertyValues.size == properties * paths)
        require(epicValues.all { it.isFinite() && abs(it) <= 20 } && propertyValues.all { it.isFinite() && abs(it) <= 20 })
    }
    fun epic(path: Int, year: Int) = epicValues[path * years + year]
    fun property(index: Int, path: Int) = propertyValues[index * paths + path]
}

object CorrelatedReturns {
    private val means = doubleArrayOf(.065, .055, .015, .055, .005, .08)
    private val deviations = doubleArrayOf(.17, .19, .07, .18, .01, .70)
    private val correlation = arrayOf(
        doubleArrayOf(1.0,.85,.05,.65,0.0,0.0), doubleArrayOf(.85,1.0,.10,.60,0.0,0.0),
        doubleArrayOf(.05,.10,1.0,.10,.20,0.0), doubleArrayOf(.65,.60,.10,1.0,0.0,0.0),
        doubleArrayOf(0.0,0.0,.20,0.0,1.0,0.0), doubleArrayOf(0.0,0.0,0.0,0.0,0.0,1.0),
    )
    private val cholesky = Array(6) { DoubleArray(6) }.also { l ->
        for (i in 0..5) for (j in 0..i) {
            var value = correlation[i][j]
            for (k in 0 until j) value -= l[i][k] * l[j][k]
            l[i][j] = if (i == j) sqrt(value) else value / l[j][j]
        }
    }
    internal fun draw(random: Random, stochastic: Boolean, shift: Int, scale: Int, out: DoubleArray, z: DoubleArray) {
        if (stochastic) for (i in 0..5) z[i] = random.nextGaussian()
        for (i in 0..5) {
            var noise = 0.0
            if (stochastic) for (j in 0..i) noise += cholesky[i][j] * z[j]
            out[i] = means[i] + (if (i == 0 || i == 1 || i == 3 || i == 5) shift / 10000.0 else 0.0) + noise * deviations[i] * scale / 10000.0
        }
    }
}

class RetirementEngine {
    /** Always dispatches CPU work away from the caller, including a Main-thread caller. */
    suspend fun calculate(input: ForecastInput, paths: Int = 10000, seed: Long? = null,
        stochastic: Boolean = true, tape: ReturnTape? = null): ForecastResult = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        compute(input, if (stochastic) paths else 1, seed ?: SecureRandom().nextLong(), stochastic, tape) { context.ensureActive() }
    }

    internal fun compute(input: ForecastInput, paths: Int, seed: Long, stochastic: Boolean,
        tape: ReturnTape? = null, noise: ForecastNoiseTape? = null, checkpoint: () -> Unit = {}): ForecastResult {
        require(paths in 1..20000 && (stochastic || paths == 1))
        val width = input.years + 1
        require(paths.toLong() * width <= 1_000_000) { "Forecast memory budget exceeded." }
        // Reserve room for the previous generation, temporary allocations, Android and the rest of the app.
        require(paths.toLong() * width * ForecastChannel.entries.size * 8 <= Runtime.getRuntime().maxMemory() / 3) {
            "Forecast exceeds this device's memory budget."
        }
        require(tape == null || (tape.paths == paths && tape.years == input.years && stochastic))
        require(noise == null || (noise.paths == paths && noise.years == input.years && noise.properties == input.properties.size && stochastic))
        checkpoint()
        val series = Array(ForecastChannel.entries.size) { DoubleArray(paths * width) }
        val balances = Array(paths) { DoubleArray(5) }
        val basis = DoubleArray(paths)
        val indexFunds = DoubleArray(paths)
        val lossCarry = DoubleArray(paths) // nominal capital losses
        val liabilities = DoubleArray(paths)
        val indexAllocation = Allocation.INDEX_FUNDS.array()
        val allocation = Array(5) { DoubleArray(6) }
        val initial = DoubleArray(5)
        var initialBasis = 0.0
        for (account in input.accounts) {
            val bucket = account.bucket.ordinal; val amount = dollars(account.balance)
            initial[bucket] = checkedAmount(initial[bucket] + amount)
            account.allocation.array().forEachIndexed { index, weight -> allocation[bucket][index] += amount * weight }
            if (account.bucket == Bucket.TAXABLE) initialBasis = checkedAmount(initialBasis + dollars(account.basis))
        }
        for (b in 0..4) if (initial[b] > 0) for (a in 0..5) allocation[b][a] /= initial[b] else allocation[b][4] = 1.0
        for (p in 0 until paths) { initial.copyInto(balances[p]); basis[p] = initialBasis }
        val mortgages = input.properties.map { mortgageProjection(it, width) }
        projectProperties(input, mortgages, paths, stochastic, Random(seed xor 0x50524f50L), series[8], noise, checkpoint)
        projectEpic(input, paths, stochastic, Random(seed xor 0x45504943L), series[6], series[7], noise, checkpoint)
        fun record(p: Int, y: Int) {
            val index = p * width + y
            for (b in 0..4) series[b][index] = checkedAmount(balances[p][b])
            series[5][index] = checkedAmount(balances[p].sum() + series[6][index] + series[8][index] - liabilities[p])
        }
        for (p in 0 until paths) record(p, 0)
        val contributions = input.contributions.array()
        val random = Random(seed)
        val assetReturns = DoubleArray(6); val gaussian = DoubleArray(6)
        val rmd = DoubleArray(paths)
        val failures = ArrayList<PathFailure>(); val failed = BooleanArray(paths)
        var maxResidual = 0.0; var residualYears = 0
        val spendingByAge = DoubleArray(width) { y -> annualRealSpending(input, mortgages, y) }
        for (y in 0 until input.years) {
            checkpoint()
            val age = input.currentAge + y
            val retired = age >= input.retirementAge
            val spouseAge = input.spouseCurrentAge?.plus(y)
            val year = input.referenceDate.year + y
            val price = (1 + input.inflationBps / 10000.0).pow(y)
            val policyIndex = (1 + input.inflationBps / 10000.0).pow(year - 2026)
            var income = 0.0; var ordinaryIncome = 0.0; var ss = 0.0
            for (stream in input.incomes) {
                val recipientAge = if (stream.owner == Owner.SPOUSE) spouseAge ?: continue else age
                if (recipientAge !in stream.startAge..stream.endAge) continue
                val amount = dollars(stream.annual); income += amount
                when (stream.kind) { IncomeKind.ORDINARY -> ordinaryIncome += amount; IncomeKind.SOCIAL_SECURITY -> ss += amount; else -> Unit }
            }
            checkedAmount(income)
            val coverageMembers = if (input.spouseCurrentAge == null) 1 else 2
            val eligibleMembers = (if (age < 65) 1 else 0) + (if (spouseAge != null && spouseAge < 65) 1 else 0)
            // Full household premium is already in spending. Only the still-eligible share earns a credit.
            val eligiblePremium = dollars(input.acaPremium) * eligibleMembers / coverageMembers
            val fpl = (15650.0 + (input.acaHousehold-1)*5500) * policyIndex / price
            val magiBreaks = if (retired && eligiblePremium > 0) listOf(1.0,1.33,1.5,2.0,2.5,3.0,4.0).map { it*fpl } else emptyList()
            for (p in 0 until paths) {
                if (p % 128 == 0) checkpoint()
                val bal = balances[p]; val index = p * width + y
                if (y > 0) { basis[p] /= 1 + input.inflationBps / 10000.0; liabilities[p] /= 1 + input.inflationBps / 10000.0 }
                fun deposit(amount: Double) {
                    bal[3] = checkedAmount(bal[3] + amount); basis[p] += amount; indexFunds[p] += amount
                }
                val divisor = PlanningTaxPolicy.rmdDivisor(age, input.birthYear)
                rmd[p] = if (divisor == null) 0.0 else bal[0] / divisor
                bal[0] -= rmd[p]
                deposit(rmd[p])
                if (!retired) {
                    for (bucket in 0..2) bal[bucket] += contributions[bucket]
                    deposit(contributions[3])
                }
                if (age == input.retirementAge && input.sellHome) {
                    deposit(series[8][index])
                    for (future in y + 1 until width) series[8][p * width + future] = 0.0
                }
                val epicSale = age == input.retirementAge && input.epicYears.isNotEmpty()
                val epicRow = if (epicSale) input.epicYears.single { it.year == year } else null
                val epicFactor = if (epicRow != null && epicRow.pretax.cents > 0) series[6][index] / dollars(epicRow.pretax) else 0.0
                val epicGain = epicRow?.let { dollars(it.capitalGain) * epicFactor } ?: 0.0
                val epicOrdinary = epicRow?.let { dollars(it.ordinaryIncome) * epicFactor } ?: 0.0
                val epicTaxPaid = if (epicSale) series[6][index] - series[7][index] else 0.0
                if (epicSale) {
                    deposit(series[7][index])
                    for (future in y + 1 until width) { series[6][p * width + future] = 0.0; series[7][p * width + future] = 0.0 }
                }
                CorrelatedReturns.draw(random, stochastic && tape == null, input.equityMeanShiftBps, input.volatilityScaleBps, assetReturns, gaussian)
                fun growth(weights: DoubleArray): Double = max(1 + weights.indices.sumOf { a -> weights[a] * (tape?.at(p,y,a) ?: assetReturns[a]) },0.0)
                for (bucket in 0..2) bal[bucket] = checkedAmount(bal[bucket] * growth(allocation[bucket]))
                val originalTaxable = max(bal[3] - indexFunds[p],0.0) * growth(allocation[3])
                indexFunds[p] = checkedAmount(indexFunds[p] * growth(indexAllocation))
                bal[3] = checkedAmount(originalTaxable + indexFunds[p])
                if (retired || rmd[p] > 0) {
                    val gainRatio = if (bal[3] > 0) 1 - basis[p] / bal[3] else 0.0
                    fun taxes(ordinary: Double, gains: Double, benefits: Double, retirement: Double): Double {
                        val taxableSS = PlanningTaxPolicy.taxableSocialSecurity(ordinary, gains, benefits, input.filing)
                        return (PlanningTaxPolicy.federal(ordinary + taxableSS, gains, input.filing, age, spouseAge, year, policyIndex) +
                            PlanningTaxPolicy.wisconsin(ordinary, gains, input.filing, age, spouseAge, retirement, policyIndex, ordinary + taxableSS + gains)) / price
                    }
                    // Workbook owns Epic's standalone sale tax; regular income uses marginal stacking on top.
                    // Its income also enters ACA MAGI. Never tax the net proceeds as a second capital gain.
                    val epicStandaloneTax = taxes(epicOrdinary*price, max(epicGain,0.0)*price, 0.0, 0.0)
                    val currentSpending = (if (retired) spendingByAge[y] else 0.0) + liabilities[p]
                    val currentIncome = if (retired) income else 0.0
                    val funding = AnnualFunding.solve(bal,age,currentSpending,currentIncome,input.rules,gainRatio,magiBreaks) { withdrawal ->
                        val netGains = (withdrawal.gains + epicGain) * price - lossCarry[p]
                        val lossLimit = if (input.filing == dev.draftingroom5.retirement.domain.FilingStatus.MARRIED_FILING_SEPARATELY) 1500.0 else 3000.0
                        val ordinaryBeforeLoss=(withdrawal.ordinary + ordinaryIncome + rmd[p] + epicOrdinary)*price
                        val lossDeduction = min(min(max(-netGains,0.0),lossLimit),ordinaryBeforeLoss)
                        val ordinary = max((withdrawal.ordinary + ordinaryIncome + rmd[p] + epicOrdinary)*price - lossDeduction,0.0)
                        val gains = max(netGains,0.0)
                        val tax = max(taxes(ordinary,gains,ss*price,(withdrawal.ordinary+rmd[p])*price) - epicStandaloneTax,0.0)
                        val magi = max(ordinary + gains + ss*price,0.0) / price
                        val credit = if (retired && eligiblePremium > 0) PlanningTaxPolicy.aca(magi*price,eligiblePremium*price,input.acaHousehold,input.filing,policyIndex)/price else 0.0
                        AnnualFunding.Assessment(tax,credit,magi,max(-netGains-lossDeduction,0.0))
                    }
                    val final = funding.withdrawal
                    val taxableFraction = if (bal[3] > 0) (final.taxableWithdrawn / bal[3]).coerceIn(0.0,1.0) else 0.0
                    basis[p] *= 1 - taxableFraction
                    indexFunds[p] *= 1 - taxableFraction
                    balances[p] = final.balances
                    balances[p][3] += funding.surplus; basis[p] += funding.surplus; indexFunds[p] += funding.surplus
                    lossCarry[p] = funding.assessment.lossCarry
                    liabilities[p] = funding.unmet
                    series[9][index] = funding.unmet
                    series[10][index] = funding.assessment.tax + epicTaxPaid
                    series[11][index] = funding.assessment.credit
                    val unpaidTax = min(funding.unmet,funding.assessment.tax)
                    series[12][index] = unpaidTax
                    maxResidual = max(maxResidual, unpaidTax)
                    if (unpaidTax > .01) residualYears++
                    if (!failed[p] && funding.unmet > .01) {
                        failed[p] = true
                        val retained = balances[p].sum() + series[6][p * width + y + 1] + series[8][p * width + y + 1]
                        failures += PathFailure(p,age,forecastMoney(funding.unmet),forecastMoney(retained),
                            if (retained > .01) FailureCause.ACCESS_OR_RULE_LIMIT else FailureCause.ASSETS_EXHAUSTED)
                    }
                }
                record(p, y + 1)
            }
        }
        checkpoint()
        val warnings = input.warnings + listOf(
            "Spending includes gross household insurance and mortgage principal/interest, but excludes income taxes.",
            "All balances are in today's dollars. Home equity is included in wealth and is unavailable for spending while retained.",
            "2026 tax rules are projected with assumed inflation; future law is unknown. Withdrawal access uses whole years (age 60).",
            "ACA assumes no employer coverage and equal premium shares for spouses; Medicare-era spending retains the entered healthcare budget.")
        return ForecastResult(input.generation, input.planRevision, input.currentAge, input.endAge, paths,
            if (stochastic) seed else null, stochastic, series, failures, forecastMoney(maxResidual), residualYears,
            warnings.distinct(), spendingByAge)
    }

    private data class MortgageProjection(val balances: DoubleArray, val payments: DoubleArray)

    private fun mortgageProjection(property: ForecastProperty, width: Int): MortgageProjection {
        val balances=DoubleArray(width); val payments=DoubleArray(width)
        var balance=dollars(property.mortgage); var months=property.months
        balances[0]=balance
        for (y in 0 until width) {
            repeat(12) {
                if (months > 0 && balance > 0) {
                    val due=balance*(1+property.rateBps/10000.0/12)
                    // Honor an actual early payoff and any balance due at the contractual maturity.
                    val payment=if (months==1) due else min(dollars(property.payment),due)
                    payments[y]+=payment
                    balance=max(due-payment,0.0); months--
                }
            }
            if (y+1<width) balances[y+1]=balance
        }
        return MortgageProjection(balances,payments)
    }

    /** All-in annual spending includes twelve current P&I payments, not escrow or income tax. */
    private fun annualRealSpending(input: ForecastInput, mortgages: List<MortgageProjection>, year: Int): Double {
        val base=dollars(input.spending)
        if (!input.spendingIncludesMortgage) return base
        val baselineMortgage=input.properties.sumOf { dollars(it.payment)*12*it.ownershipBps/10000.0 }
        val currentMortgage=input.properties.indices.sumOf { i ->
            if (input.sellHome && input.retirementAge >= input.currentAge && input.currentAge+year >= input.retirementAge) 0.0
            else mortgages[i].payments[year]*input.properties[i].ownershipBps/10000.0 /
                (1+input.inflationBps/10000.0).pow(year)
        }
        return checkedAmount(max(base-baselineMortgage,0.0)+currentMortgage)
    }

    private fun projectProperties(input: ForecastInput, mortgages: List<MortgageProjection>, paths: Int,
        stochastic: Boolean, rng: Random, out: DoubleArray, noise: ForecastNoiseTape?, check: () -> Unit) {
        val width = input.years + 1
        for ((propertyIndex, property) in input.properties.withIndex()) {
            val mortgage=mortgages[propertyIndex].balances
            val volatility = if (property.low != null && property.value.cents > 0)
                ((dollars(property.high!!) - dollars(property.low)) / (2.5632 * dollars(property.value))).coerceIn(0.0, .20) else 0.0
            for (p in 0 until paths) {
                if (p % 128 == 0) check()
                var value = dollars(property.value)
                for (y in 0 until width) {
                    val index = p * width + y
                    out[index] = checkedAmount(out[index] + max(value - mortgage[y] / (1 + input.inflationBps / 10000.0).pow(y), 0.0) * property.ownershipBps / 10000)
                    val shock = if (stochastic && y == 0 && volatility > 0) (noise?.property(propertyIndex, p) ?: rng.nextGaussian()) * volatility else 0.0
                    if (y < input.years) value = checkedAmount(value * (1 + max(property.realAppreciationBps / 10000.0 + shock, -.50)))
                }
            }
        }
    }
    private fun projectEpic(input: ForecastInput, paths: Int, stochastic: Boolean, rng: Random,
        pretax: DoubleArray, after: DoubleArray, noise: ForecastNoiseTape?, check: () -> Unit) {
        if (input.epicYears.isEmpty()) return
        val rows = input.epicYears.associateBy { it.year }
        val sigma = input.epicVolatility.coerceIn(0.0, .01)
        val innovation = sigma * sqrt(1 - .7 * .7)
        val width = input.years + 1
        for (p in 0 until paths) {
            if (p % 128 == 0) check()
            var deviation = 0.0; var variance = 0.0
            for (y in 0..min(input.years, input.retirementAge - input.currentAge)) {
                if (stochastic && sigma > 0 && y > 0) {
                    deviation = .7 * deviation + (noise?.epic(p, y - 1) ?: rng.nextGaussian()) * innovation
                    variance = .49 * variance + innovation * innovation
                }
                val factor = exp(deviation - .5 * variance) / (1 + input.inflationBps / 10000.0).pow(y)
                val row = rows.getValue(input.referenceDate.year + y); val index = p * width + y
                pretax[index] = checkedAmount(dollars(row.pretax) * factor)
                after[index] = checkedAmount(dollars(row.afterTax) * factor)
            }
        }
    }
}
