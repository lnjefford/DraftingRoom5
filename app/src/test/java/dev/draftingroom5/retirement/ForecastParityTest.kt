package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

internal fun parityFile(name: String): File = sequenceOf(File("docs/design/retirement-workspace/parity/$name"),
    File("../docs/design/retirement-workspace/parity/$name")).first { it.isFile }
internal fun filing(name: String) = when (name) { "single" -> FilingStatus.SINGLE; "mfj" -> FilingStatus.MARRIED_FILING_JOINTLY
    "mfs" -> FilingStatus.MARRIED_FILING_SEPARATELY; "hoh" -> FilingStatus.HEAD_OF_HOUSEHOLD; else -> error("Unknown fixture status") }
internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun inputCase(c: JSONObject): ForecastInput {
    val contrib = c.getJSONArray("contributions")
    return ForecastInput(74, 1, LocalDate.of(2026,1,1), c.getInt("age"), c.getInt("retire"), c.getInt("end"), Money(c.getLong("spending_cents")),
        c.getJSONArray("accounts").objects().map { a ->
            val w = a.getJSONArray("allocation")
            ForecastAccount(Bucket.entries[a.getInt("bucket")], Money(a.getLong("cents")), Money(a.getLong("basis_cents")),
                Allocation(w.getDouble(0),w.getDouble(1),w.getDouble(2),w.getDouble(3),w.getDouble(4),w.getDouble(5)))
        }, ForecastContributions(Money(contrib.getLong(0)), Money(contrib.getLong(1)), Money(contrib.getLong(3)), Money(contrib.getLong(2))),
        c.getJSONArray("incomes").objects().map { ForecastIncome(Money(it.getLong("cents")), it.getInt("start"), it.getInt("end"), IncomeKind.valueOf(it.getString("kind").uppercase())) },
        c.getJSONArray("properties").objects().map { ForecastProperty(Money(it.getLong("value")), Money(it.getLong("mortgage")), it.getInt("rate_bps"),
            Money(it.getLong("payment")), it.getInt("months"), it.getInt("ownership_bps"), it.getInt("appreciation_bps"),
            if(it.has("low")) Money(it.getLong("low")) else null, if(it.has("high")) Money(it.getLong("high")) else null) },
        c.getJSONArray("epic").let { rows -> (0 until rows.length()).map { rows.getJSONArray(it).let { r -> ForecastEpicYear(r.getInt(0),Money(r.getLong(1)),Money(r.getLong(2))) } } },
        epicVolatility = c.optDouble("epic_volatility",0.0), inflationBps = c.getInt("inflation_bps"), filing = filing(c.getString("filing")), stateCode = c.getString("state"),
        acaHousehold = c.getInt("household"), acaPremium = Money(c.getLong("premium")), acaExtended = c.getBoolean("extended"),
        sellHome = c.getBoolean("sell_home"), rules = WithdrawalPolicy.defaultRules(Money(c.getLong("medical"))))
}

class ForecastParityTest {
    @Test fun everyBoundaryBucketUnmetYearPercentileAndDepletionMatchesPinnedReference() {
        val cases = JSONArray(parityFile("forecast-cases.json").readText()).objects()
        val expected = JSONObject(parityFile("forecast-expected.json").readText()).getJSONArray("cases").objects()
        var assertions = 0; var worst = 0L
        cases.zip(expected).forEach { (case, golden) ->
            val input = inputCase(case); val paths = case.getInt("paths")
            val tape = if (paths == 1) null else golden.getJSONArray("tape").let { ReturnTape(paths, input.years, DoubleArray(it.length()) { i -> it.getDouble(i) }) }
            fun numbers(key:String) = golden.getJSONArray(key).let { a -> DoubleArray(a.length()) { a.getDouble(it) } }
            val noise = if(paths==1) null else ForecastNoiseTape(paths,input.years,input.properties.size,numbers("epic_noise"),numbers("property_noise"))
            val result = RetirementEngine().compute(input, paths, case.getLong("seed"), paths > 1, tape, noise)
            fun cents(actual: Money, target: Long) {
                val difference = abs(actual.cents - target); worst = maxOf(worst, difference); assertions++
                assertTrue("${case.getString("name")}: cents differ by $difference", difference <= 1)
            }
            for (p in 0 until paths) {
                for (y in 0..input.years) {
                    val age = input.currentAge + y
                    cents(result.value(ForecastChannel.TOTAL,p,age), golden.getJSONArray("totals").getJSONArray(p).getLong(y))
                    cents(result.value(ForecastChannel.UNMET,p,age), golden.getJSONArray("unmet").getJSONArray(p).getLong(y))
                    for (b in 0..4) cents(result.value(ForecastChannel.entries[b],p,age), golden.getJSONArray("buckets").getJSONArray(p).getJSONArray(y).getLong(b))
                    if (!golden.isNull("epic")) cents(result.value(ForecastChannel.EPIC,p,age),golden.getJSONArray("epic").getJSONArray(p).getLong(y))
                    if (!golden.isNull("property")) cents(result.value(ForecastChannel.PROPERTY,p,age),golden.getJSONArray("property").getJSONArray(p).getLong(y))
                }
                assertEquals(golden.getJSONArray("depletion").getInt(p),result.depletionAge(p)); assertions++
            }
            for (q in listOf(10,50,90)) for (y in 0..input.years) cents(result.percentile(ForecastChannel.TOTAL,input.currentAge+y,q.toDouble()),golden.getJSONArray("p$q").getLong(y))
            assertEquals(golden.getDouble("success"),result.successRate,1e-12); assertions++
        }
        println("DR5-074 PARITY: $assertions full-engine comparisons; maximum observed cent difference=$worst")
    }
    @Test fun scalarTaxesSocialSecurityAllFilingStatusesMatchReferenceGridExactly() {
        JSONObject(parityFile("forecast-expected.json").readText()).getJSONArray("tax_grid").objects().forEach { row ->
            val status = filing(row.getString("filing")); val ordinary = row.getDouble("ordinary"); val gain = row.getDouble("gain")
            assertEquals(row.getLong("federal"), forecastMoney(ReferenceTaxPolicy.federal(ordinary,gain,status)).cents)
            assertEquals(row.getLong("wi"), forecastMoney(ReferenceTaxPolicy.wisconsin(ordinary,gain,status)).cents)
            assertEquals(row.getLong("ss"), forecastMoney(ReferenceTaxPolicy.taxableSocialSecurity(ordinary,gain,row.getDouble("benefits"),status)).cents)
        }
    }
    @Test fun handAuthoredTaxAcaMortgageAndWithdrawalGoldensAreExactCents() {
        val cases = JSONObject(parityFile("cases.json").readText())
        for (section in listOf("federal","wisconsin")) cases.getJSONArray(section).objects().forEach { r ->
            val ordinary = r.getLong("ordinary_cents")/100.0; val gain = r.getLong("ltcg_cents")/100.0; val status = filing(r.getString("filing"))
            assertEquals(r.getLong("tax_cents"),forecastMoney(if(section=="federal") ReferenceTaxPolicy.federal(ordinary,gain,status) else ReferenceTaxPolicy.wisconsin(ordinary,gain,status)).cents)
        }
        cases.getJSONArray("aca").objects().forEach { r -> assertEquals(r.getLong("credit_cents"),forecastMoney(ReferenceTaxPolicy.aca(r.getLong("magi_cents")/100.0,r.getLong("premium_cents")/100.0,r.getInt("household"),r.getBoolean("extended"))).cents) }
        cases.getJSONArray("amortization").objects().forEach { r ->
            val principal = r.getLong("principal_cents")/100.0; val rate = r.getInt("rate_bps"); val term = r.getInt("term_months")
            assertEquals(r.getLong("payment_cents"),forecastMoney(MortgageCalculator.monthlyPayment(principal,rate,term)).cents)
            val rows = MortgageCalculator.schedule(principal,rate,term,r.getInt("months"))
            assertEquals(r.getLong("balance_cents"),forecastMoney(rows.last().balance).cents)
            if(r.has("interest_cents")) assertEquals(r.getLong("interest_cents"),forecastMoney(rows.first().interest).cents)
        }
        cases.getJSONArray("withdrawal").objects().forEach { r ->
            val balances = DoubleArray(5); r.getJSONObject("balances_cents").let { o -> o.keys().forEach { balances[Bucket.valueOf(it.uppercase()).ordinal] = o.getLong(it)/100.0 } }
            val rules = r.getJSONArray("rules").objects().map { WithdrawalRule(Bucket.valueOf(it.getString("bucket").uppercase()),it.optInt("min_age",0),annualCap=if(it.has("annual_cap_real")) forecastMoney(it.getDouble("annual_cap_real")) else null) }
            val result = WithdrawalPolicy.apply(balances,r.getInt("age"),r.getLong("need_cents")/100.0,rules)
            r.getJSONObject("remaining_cents").let { o -> o.keys().forEach { assertEquals(o.getLong(it),forecastMoney(result.balances[Bucket.valueOf(it.uppercase()).ordinal]).cents) } }
            assertEquals(r.getLong("ordinary_cents"),forecastMoney(result.ordinary).cents)
            assertEquals(r.getLong("ltcg_cents"),forecastMoney(result.gains).cents)
            assertEquals(r.getLong("tax_free_cents"),forecastMoney(result.taxFree).cents)
            assertEquals(r.getLong("unmet_cents"),forecastMoney(result.unmet).cents)
        }
    }
    @Test fun independentlySpecifiedGrowthSequenceAndTransferArithmetic() {
        fun input(age:Int,retire:Int,end:Int,spend:Long,accounts:List<ForecastAccount> = emptyList(), epic:List<ForecastEpicYear> = emptyList(),properties:List<ForecastProperty> = emptyList(), inflation:Int = 0) =
            ForecastInput(1,1,LocalDate.of(2026,1,1),age,retire,end,Money(spend),accounts,epicYears=epic,properties=properties,inflationBps=inflation)
        val roth = ForecastAccount(Bucket.ROTH,Money(100000),Money(0),Allocation(us=1.0))
        val growth = RetirementEngine().compute(input(30,100,32,0,listOf(roth)),1,0,false)
        assertEquals(listOf(100000L,106500L,113423L),(30..32).map { growth.value(ForecastChannel.TOTAL,0,it).cents })
        val tape = doubleArrayOf(.1,-.2,-.2,.1,-1.0,0.0)
        val result = RetirementEngine().compute(input(60,60,62,10000,listOf(roth)),3,0,true,ReturnTape(3,2,DoubleArray(36) { if(it%6==0) tape[it/6] else 0.0 }))
        assertEquals(listOf(70000L,67000L,0L),(0..2).map { result.value(ForecastChannel.TOTAL,it,62).cents })
        assertEquals(13400L,result.percentile(ForecastChannel.TOTAL,62,10.0).cents)
        assertEquals(2.0/3,result.successRate,0.0)
        val epic = (0..2).map { ForecastEpicYear(2026+it,Money(100000L+it*10000),Money(80000L+it*8000)) }
        val epicResult = RetirementEngine().compute(input(44,45,46,0,epic=epic,inflation=1000),1,0,false)
        assertEquals(80000L,epicResult.value(ForecastChannel.TAXABLE,0,46).cents)
        assertEquals(0L,epicResult.value(ForecastChannel.EPIC,0,46).cents)
        val home = RetirementEngine().compute(input(44,45,46,0,properties=listOf(ForecastProperty(Money(200000),Money(100000),0,Money(0),0,5000))),1,0,false)
        assertEquals(50250L,home.value(ForecastChannel.TAXABLE,0,46).cents)
    }

    @Test fun allInSpendingUsesSavedMortgageScheduleAndStopsAtPayoff() {
        val home = ForecastProperty(
            value = Money(50_000_000), mortgage = Money(20_000_000), rateBps = 0,
            payment = Money(100_000), months = 18, ownershipBps = 10_000,
        )
        val input = ForecastInput(
            generation = 1, planRevision = 1, referenceDate = LocalDate.of(2026, 1, 1),
            currentAge = 60, retirementAge = 60, endAge = 63,
            spending = Money(6_000_000),
            accounts = listOf(ForecastAccount(Bucket.TAXABLE, Money(100_000_000), Money(100_000_000), Allocation(cash = 1.0))),
            properties = listOf(home), inflationBps = 1_000, sellHome = false, spendingIncludesMortgage = true,
        )
        val result = RetirementEngine().compute(input, 1, 0, false)

        assertEquals(Money(6_000_000), result.annualSpending(60))
        assertEquals(Money(5_345_455), result.annualSpending(61))
        assertEquals(Money(4_800_000), result.annualSpending(62))
    }
}
