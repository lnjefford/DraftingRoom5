package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.domain.Money
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
    warnings: List<String>,
) {
    val engineVersion = "dr5-reference-engine-1"
    val taxPolicyId = ReferenceTaxPolicy.ID
    val failures = frozen(failures)
    val warnings = frozen(warnings)
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
        for (p in 0 until paths) { initial.copyInto(balances[p]); basis[p] = min(initialBasis, initial[3]) }
        projectProperties(input, paths, stochastic, Random(seed xor 0x50524f50L), series[8], noise, checkpoint)
        projectEpic(input, paths, stochastic, Random(seed xor 0x45504943L), series[6], series[7], noise, checkpoint)
        fun record(p: Int, y: Int) {
            val index = p * width + y
            for (b in 0..4) series[b][index] = checkedAmount(balances[p][b])
            series[5][index] = checkedAmount(balances[p].sum() + series[6][index] + series[8][index])
        }
        for (p in 0 until paths) record(p, 0)
        val contributions = input.contributions.array()
        val random = Random(seed)
        val assetReturns = DoubleArray(6); val gaussian = DoubleArray(6)
        val rmd = DoubleArray(paths); val ratios = DoubleArray(paths)
        val failures = ArrayList<PathFailure>(); val failed = BooleanArray(paths)
        var maxResidual = 0.0; var residualYears = 0
        for (y in 0 until input.years) {
            checkpoint()
            val age = input.currentAge + y; val retired = age >= input.retirementAge
            var income = 0.0; var ordinaryIncome = 0.0; var ss = 0.0
            for (stream in input.incomes) if (age in stream.startAge..stream.endAge) {
                val amount = dollars(stream.annual); income += amount
                when (stream.kind) { IncomeKind.ORDINARY -> ordinaryIncome += amount; IncomeKind.SOCIAL_SECURITY -> ss += amount; else -> Unit }
            }
            checkedAmount(income)
            for (p in 0 until paths) {
                if (p % 128 == 0) checkpoint()
                val bal = balances[p]; val index = p * width + y
                if (!retired) { for (b in 0..4) bal[b] += contributions[b]; basis[p] += contributions[3] }
                if (age == input.retirementAge && input.sellHome) {
                    bal[3] += series[8][index]; basis[p] += series[8][index]
                    for (future in y + 1 until width) series[8][p * width + future] = 0.0
                }
                val divisor = if (retired) ReferenceTaxPolicy.rmdDivisor(age) else null
                rmd[p] = if (divisor == null) 0.0 else bal[0] / divisor
                bal[0] -= rmd[p]; bal[3] += rmd[p]; basis[p] += rmd[p]
                CorrelatedReturns.draw(random, stochastic && tape == null, input.equityMeanShiftBps, input.volatilityScaleBps, assetReturns, gaussian)
                for (b in 0..3) {
                    var rate = 0.0
                    for (a in 0..5) rate += allocation[b][a] * (tape?.at(p, y, a) ?: assetReturns[a])
                    // Arithmetic normal tails cannot create a negative asset or erase another bucket's cash.
                    bal[b] = checkedAmount(max(bal[b] * (1 + rate), 0.0))
                }
                if (age == input.retirementAge && input.epicYears.isNotEmpty()) {
                    bal[3] = checkedAmount(bal[3] + series[7][index]); basis[p] += series[7][index]
                    for (future in y + 1 until width) { series[6][p * width + future] = 0.0; series[7][p * width + future] = 0.0 }
                }
                ratios[p] = if (bal[3] > 0) max(bal[3] - basis[p], 0.0) / bal[3] else 0.0
            }
            val netNeed = max(dollars(input.spending) - income, 0.0)
            val subsidy = if (retired && age < 65 && input.acaPremium.cents > 0) {
                val sorted = ratios.copyOf().also { it.sort() }
                val median = (sorted[(paths - 1) / 2] + sorted[paths / 2]) / 2
                ReferenceTaxPolicy.aca(max(ordinaryIncome + ss * .85 + netNeed * median, 0.0), dollars(input.acaPremium), input.acaHousehold, input.acaExtended)
            } else 0.0
            for (p in 0 until paths) {
                if (p % 128 == 0) checkpoint()
                if (retired) {
                    val index = p * width + y; val need = max(netNeed - subsidy, 0.0)
                    var gross = need
                    fun tax(withdrawal: WithdrawalPolicy.Result): Double {
                        val ordinary = withdrawal.ordinary + ordinaryIncome + rmd[p]
                        val taxableSS = ReferenceTaxPolicy.taxableSocialSecurity(ordinary, withdrawal.gains, ss, input.filing)
                        return ReferenceTaxPolicy.federal(ordinary + taxableSS, withdrawal.gains, input.filing) +
                            ReferenceTaxPolicy.state(ordinary + taxableSS, withdrawal.gains, input.filing, input.stateCode)
                    }
                    repeat(2) { gross = checkedAmount(need + tax(WithdrawalPolicy.apply(balances[p], age, gross, input.rules, ratios[p]))) }
                    val final = WithdrawalPolicy.apply(balances[p], age, gross, input.rules, ratios[p])
                    val actualTax = tax(final)
                    val residual = max(actualTax - (gross - need), 0.0)
                    series[9][index] = final.unmet
                    series[10][index] = actualTax
                    series[11][index] = subsidy
                    series[12][index] = residual
                    maxResidual = max(maxResidual, residual)
                    if (residual > .01) residualYears++
                    balances[p] = final.balances
                    basis[p] = min(max(basis[p] - final.taxableWithdrawn * (1 - ratios[p]), 0.0), final.balances[3])
                    if (!failed[p] && final.unmet > .01) {
                        failed[p] = true
                        val retained = final.balances.sum() + series[6][p * width + y + 1] + series[8][p * width + y + 1]
                        failures += PathFailure(p, age, forecastMoney(final.unmet), forecastMoney(retained),
                            if (retained > .01) FailureCause.ACCESS_OR_RULE_LIMIT else FailureCause.ASSETS_EXHAUSTED)
                    }
                }
                record(p, y + 1)
            }
        }
        checkpoint()
        val warnings = input.warnings + listOf("Reference two-pass tax funding and cross-path ACA estimate are approximations.",
            "Mortgage balances/payments use the reference fixed-dollar approximation; no separate inflation deflation.",
            "Age 60 withdrawal access and age 73–120 RMD table are annual approximations.") +
            if (residualYears > 0) listOf("Tax funding has a measured residual; modeled success does not certify fully funded taxes.") else emptyList()
        return ForecastResult(input.generation, input.planRevision, input.currentAge, input.endAge, paths,
            if (stochastic) seed else null, stochastic, series, failures, forecastMoney(maxResidual), residualYears, warnings.distinct())
    }

    private fun projectProperties(input: ForecastInput, paths: Int, stochastic: Boolean, rng: Random, out: DoubleArray, noise: ForecastNoiseTape?, check: () -> Unit) {
        val width = input.years + 1
        for ((propertyIndex, property) in input.properties.withIndex()) {
            val mortgage = DoubleArray(width); mortgage[0] = dollars(property.mortgage)
            var balance = mortgage[0]; var months = property.months
            for (y in 1 until width) {
                repeat(12) {
                    if (months > 0 && balance > 0) {
                        val principal = dollars(property.payment) - balance * property.rateBps / 10000.0 / 12
                        if (principal > 0) { balance = max(balance - principal, 0.0); months-- }
                    }
                }
                mortgage[y] = balance
            }
            val volatility = if (property.low != null && property.value.cents > 0)
                ((dollars(property.high!!) - dollars(property.low)) / (2.5632 * dollars(property.value))).coerceIn(0.0, .20) else 0.0
            for (p in 0 until paths) {
                if (p % 128 == 0) check()
                var value = dollars(property.value)
                for (y in 0 until width) {
                    val index = p * width + y
                    out[index] = checkedAmount(out[index] + max(value - mortgage[y], 0.0) * property.ownershipBps / 10000)
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
        val sigma = input.epicVolatility.coerceIn(0.0, .10)
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
