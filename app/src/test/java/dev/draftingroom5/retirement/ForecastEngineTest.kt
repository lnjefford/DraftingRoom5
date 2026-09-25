package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Random
import kotlin.math.*
import kotlin.system.measureNanoTime

internal fun simpleInput(age: Int = 40, retire: Int = 50, end: Int = 90, spending: Long = 8000000,
    accounts: List<ForecastAccount> = listOf(ForecastAccount(Bucket.ROTH,Money(75000000),Money(0),Allocation(us=1.0))),
    contributions: ForecastContributions = ForecastContributions(), incomes: List<ForecastIncome> = emptyList(),
    epic: List<ForecastEpicYear> = emptyList(), volatility: Double = 0.0, shift: Int = 0,
    rules: List<WithdrawalRule> = WithdrawalPolicy.defaultRules(), properties: List<ForecastProperty> = emptyList()) =
    ForecastInput(74,1,LocalDate.of(2026,1,1),age,retire,end,Money(spending),accounts,contributions,incomes,
        properties,epic,volatility,equityMeanShiftBps=shift,rules=rules)

class ForecastEngineTest {
    private val engine = RetirementEngine()
    @Test fun acaStopsAtSixtyFiveAndFullBasisIsNotOrdinaryIncome() {
        val input=ForecastInput(1,1,LocalDate.of(2026,1,1),64,64,66,Money(1000000),
            listOf(ForecastAccount(Bucket.TAXABLE,Money(10000000),Money(10000000),Allocation(cash=1.0))),
            acaPremium=Money(1200000),acaExtended=true)
        val result=engine.compute(input,1,0,false)
        assertEquals(0L,result.value(ForecastChannel.ACA,0,64).cents) // No Marketplace credit below WI eligibility income.
        assertEquals(0L,result.value(ForecastChannel.ACA,0,65).cents)
        assertEquals(0L,result.value(ForecastChannel.TAX,0,65).cents)
        assertEquals(8095250L,result.value(ForecastChannel.TAXABLE,0,66).cents)
    }
    @Test fun seedRepeatabilityDifferentSeedAndStableScenarioDraws() {
        val input = simpleInput()
        val a = engine.compute(input,100,74,true); val b = engine.compute(input,100,74,true)
        val c = engine.compute(input,100,75,true)
        for(p in 0..99) for(age in 40..90) assertEquals(a.value(ForecastChannel.TOTAL,p,age),b.value(ForecastChannel.TOTAL,p,age))
        assertNotEquals(a.mean(ForecastChannel.TOTAL,90),c.mean(ForecastChannel.TOTAL,90))
        val lower = engine.compute(simpleInput(spending=7000000),100,74,true)
        for(p in 0..99) assertTrue(lower.value(ForecastChannel.TOTAL,p,90).cents >= a.value(ForecastChannel.TOTAL,p,90).cents)
    }
    @Test fun correlationMeanVarianceAndIndependentCryptoMatchSampleSizeBounds() {
        val rng = Random(74); val out = DoubleArray(6); val z=DoubleArray(6)
        val sum=DoubleArray(6); val square=DoubleArray(6); var cross=0.0; var cryptoCross=0.0
        val n=100000
        repeat(n) {
            CorrelatedReturns.draw(rng,true,0,10000,out,z)
            for(i in 0..5) { sum[i]+=out[i]; square[i]+=out[i]*out[i] }
            cross+=out[0]*out[1]; cryptoCross+=out[0]*out[5]
        }
        val means=doubleArrayOf(.065,.055,.015,.055,.005,.08); val sd=doubleArrayOf(.17,.19,.07,.18,.01,.7)
        for(i in 0..5) {
            assertEquals(means[i],sum[i]/n,5*sd[i]/sqrt(n.toDouble()))
            assertEquals(sd[i],sqrt(square[i]/n-(sum[i]/n).pow(2)),sd[i]*.02)
        }
        assertEquals(.85,(cross/n-sum[0]*sum[1]/n/n)/sd[0]/sd[1],.015)
        assertEquals(0.0,(cryptoCross/n-sum[0]*sum[5]/n/n)/sd[0]/sd[5],.015)
    }
    @Test fun arithmeticMonteCarloMeanAndVolatilityDrag() {
        val result=engine.compute(simpleInput(age=30,retire=100,end=60,spending=0),20000,42,true)
        val expected=750000.0*1.065.pow(30)
        assertEquals(expected,result.mean(ForecastChannel.TOTAL,60).cents/100.0,expected*.05)
        assertTrue(result.percentile(ForecastChannel.TOTAL,60,50.0).cents<result.mean(ForecastChannel.TOTAL,60).cents)
    }
    @Test fun immutableCollectionsTapeAndResultCannotBeMutatedByCaller() {
        val accounts=mutableListOf(ForecastAccount(Bucket.ROTH,Money(100000),Money(0),Allocation(us=1.0)))
        val input=simpleInput(age=60,retire=100,end=61,spending=0,accounts=accounts)
        accounts.clear()
        assertEquals(1,input.accounts.size)
        assertThrows(UnsupportedOperationException::class.java) { (input.accounts as MutableList).clear() }
        val source=DoubleArray(6); val tape=ReturnTape(1,1,source); source[0]=-1.0
        val result=engine.compute(input,1,0,true,tape)
        assertEquals(100000L,result.value(ForecastChannel.TOTAL,0,61).cents)
        assertThrows(UnsupportedOperationException::class.java) { (result.warnings as MutableList).clear() }
    }
    @Test fun contributionsGrowAtStartAndStopExactlyAtRetirement() {
        val c=ForecastContributions(Money(10000),Money(20000),Money(30000),Money(40000))
        val result=engine.compute(simpleInput(age=59,retire=60,end=61,spending=0,accounts=emptyList(),contributions=c),1,0,false)
        assertEquals(10050L,result.value(ForecastChannel.TRADITIONAL,0,60).cents)
        assertEquals(10100L,result.value(ForecastChannel.TRADITIONAL,0,61).cents)
        assertEquals(31290L,result.value(ForecastChannel.TAXABLE,0,60).cents)
    }
    @Test fun pensionAndSocialSecurityUseClosedAgeRangeAndIncomeDoesNotBecomeAnAsset() {
        val account=listOf(ForecastAccount(Bucket.ROTH,Money(10000000),Money(0),Allocation(cash=1.0)))
        val income=listOf(ForecastIncome(Money(120000),65,67,IncomeKind.ORDINARY),ForecastIncome(Money(240000),67,130,IncomeKind.SOCIAL_SECURITY))
        val input=simpleInput(age=64,retire=64,end=69,spending=500000,accounts=account,incomes=income)
        val result=engine.compute(input,1,0,false)
        val totals=doubleArrayOf(0.0,1200.0,1200.0,3600.0,2400.0)
        var balance=100000.0
        for(y in 0..4) { balance=balance*1.005-5000+totals[y]; assertEquals(forecastMoney(balance),result.value(ForecastChannel.TOTAL,0,65+y)) }
    }
    @Test fun rmdIsAConservingTransferBeforeGrowthAndTaxFunding() {
        val account=listOf(ForecastAccount(Bucket.TRADITIONAL,Money(2650000),Money(0),Allocation(cash=1.0)))
        val result=engine.compute(simpleInput(age=73,retire=73,end=74,spending=0,accounts=account),1,0,false)
        assertEquals(104300L,result.value(ForecastChannel.TAXABLE,0,74).cents)
        assertEquals(2562750L,result.value(ForecastChannel.TRADITIONAL,0,74).cents)
        assertEquals(2667050L,result.value(ForecastChannel.TOTAL,0,74).cents)
    }
    @Test fun taxFundingReconcilesActualWithdrawalToSpendingAndTax() {
        val result=engine.compute(simpleInput(age=60,retire=60,end=61,spending=15000000,
            accounts=listOf(ForecastAccount(Bucket.TRADITIONAL,Money(100000000),Money(0),Allocation(cash=1.0)))),1,0,false)
        assertTrue(result.taxFundingConverged)
        assertEquals(0L,result.maximumTaxFundingResidual.cents)
        val withdrawn=1000000*1.005-result.raw(ForecastChannel.TRADITIONAL,0,61)
        assertEquals(150000+result.raw(ForecastChannel.TAX,0,60),withdrawn,.0001)
        assertEquals(1.0,result.successRate,0.0)
    }
    @Test fun medicalCapsLockedAssetsAndFailureTimingRemainVisible() {
        val input=simpleInput(age=59,retire=59,end=61,spending=100000,accounts=listOf(
            ForecastAccount(Bucket.TRADITIONAL,Money(10000000),Money(0),Allocation(cash=1.0)),
            ForecastAccount(Bucket.HSA,Money(100000),Money(0),Allocation(cash=1.0))),rules=WithdrawalPolicy.defaultRules(Money(25000)))
        val result=engine.compute(input,1,0,false)
        assertEquals(59,result.depletionAge(0)); assertEquals(0.0,result.successRate,0.0)
        assertEquals(FailureCause.ACCESS_OR_RULE_LIMIT,result.failures.single().cause)
        assertEquals(75000L,result.value(ForecastChannel.UNMET,0,59).cents)
        assertEquals(0L,result.value(ForecastChannel.UNMET,0,60).cents)
    }
    @Test fun boundedEpicUncertaintyAndNoLocalExtension() {
        val years=(2026..2034).map { ForecastEpicYear(it,Money(500000000),Money(400000000)) }
        val input=simpleInput(age=37,retire=45,end=46,spending=0,accounts=emptyList(),epic=years,volatility=.50)
        val result=engine.compute(input,10000,42,true)
        assertTrue(result.percentile(ForecastChannel.EPIC,45,10.0).cents>430000000)
        assertTrue(result.percentile(ForecastChannel.EPIC,45,90.0).cents<570000000)
        assertEquals(500000000.0,result.mean(ForecastChannel.EPIC,45).cents.toDouble(),5000000.0)
        assertEquals(0L,result.value(ForecastChannel.EPIC_AFTER_TAX,0,46).cents)
        assertThrows(IllegalArgumentException::class.java) { simpleInput(age=37,retire=45,end=46,epic=years.drop(1)) }
    }
    @Test fun finiteBoundsNegativeReturnFloorAndNonAmortizingMortgageAreSafe() {
        assertThrows(IllegalArgumentException::class.java) { forecastMoney(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { forecastMoney(Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { forecastMoney(1e20) }
        assertThrows(IllegalArgumentException::class.java) { engine.compute(simpleInput(end=130),20000,0,true) }
        assertThrows(IllegalArgumentException::class.java) { engine.compute(simpleInput(retire=200,spending=0,shift=30000),1,0,false) }
        val crash=engine.compute(simpleInput(age=60,retire=60,end=61,spending=10000),1,0,true,
            ReturnTape(1,1,doubleArrayOf(-2.0,0.0,0.0,0.0,0.0,0.0)))
        assertEquals(-10000L,crash.value(ForecastChannel.TOTAL,0,61).cents) // Unpaid spending remains a liability.
        assertEquals(10000L,crash.value(ForecastChannel.UNMET,0,60).cents)
        val home=ForecastProperty(Money(200000),Money(300000),1000,Money(0),360,10000)
        val result=engine.compute(simpleInput(age=40,retire=100,end=42,spending=0,accounts=emptyList(),properties=listOf(home)),1,0,false)
        assertEquals(0L,result.value(ForecastChannel.PROPERTY,0,42).cents)
    }
    @Test fun tenThousandPathsFiftyYearsFitHostPerformanceBudget() {
        val input=inputCase(org.json.JSONArray(parityFile("forecast-cases.json").readText()).getJSONObject(0))
        engine.compute(input,100,74,true) // Warm classes/JIT before measured representative work.
        lateinit var result:ForecastResult
        val elapsed=measureNanoTime { result=engine.compute(simpleInput(accounts=listOf(
            ForecastAccount(Bucket.TRADITIONAL,Money(65000000),Money(0),Allocation(.6,.15,.2,.05)),
            ForecastAccount(Bucket.ROTH,Money(15000000),Money(0),Allocation(.6,.15,.2,.05)),
            ForecastAccount(Bucket.TAXABLE,Money(25000000),Money(16000000),Allocation(.65,.2,.1,.05)))),10000,74,true) }/1e9
        println("DR5-074 PERFORMANCE: 10000 x 50 years, mixed accounts/taxes, ${"%.3f".format(java.util.Locale.ROOT,elapsed)} seconds; retained primitive history=53,040,000 bytes")
        assertEquals(10000,result.paths)
        assertTrue("Host forecast exceeded 10-second budget: $elapsed",elapsed<10.0)
    }
}
