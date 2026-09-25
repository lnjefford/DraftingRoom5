package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class HouseholdForecastTest {
    private fun m(d: Long)=Money(d*100)
    private val date=LocalDate.of(2026,1,1)
    private fun account(bucket: Bucket, amount: Long, basis: Long = amount)=ForecastAccount(bucket,m(amount),m(basis),Allocation(us=1.0))
    private fun run(input: ForecastInput, returns: DoubleArray=DoubleArray(input.years*6))=
        RetirementEngine().compute(input,1,42,true,ReturnTape(1,input.years,returns))
    private fun input(balance: Long, spend: Long, bucket: Bucket=Bucket.TRADITIONAL,
        incomes: List<ForecastIncome> = emptyList(), premium: Long=0, end:Int=61)=
        ForecastInput(1,1,date,60,60,end,m(spend),listOf(account(bucket,balance)),incomes=incomes,acaPremium=m(premium))

    @Test fun taxesAreFullyFundedIncludingTaxOnTaxWithdrawals() {
        val result=run(input(1_000_000,150_000))
        val withdrawn=1_000_000.0-result.value(ForecastChannel.TRADITIONAL,0,61).cents/100.0
        assertEquals(150_000.0+result.value(ForecastChannel.TAX,0,60).cents/100.0,withdrawn,.011)
        assertEquals(0L,result.maximumTaxFundingResidual.cents)
        assertEquals(1.0,result.successRate,0.0)
    }
    @Test fun insufficientMoneyForTaxesFailsAndCarriesTheLiability() {
        val result=run(input(190_000,150_000,end=62))
        assertEquals(0.0,result.successRate,0.0)
        assertTrue(result.value(ForecastChannel.UNMET,0,60).cents>0)
        assertTrue(result.value(ForecastChannel.TOTAL,0,61).cents<0)
        assertTrue(result.value(ForecastChannel.UNMET,0,61).cents>m(150_000).cents)
    }
    @Test fun incomePaysItsTaxesAndRemainingCashIsSaved() {
        val result=run(input(0,50_000,incomes=listOf(ForecastIncome(m(100_000),60,60,IncomeKind.ORDINARY))))
        // Independently evaluated 2026 single federal brackets + WI sliding deduction/exemption.
        assertEquals(17_597.52,result.value(ForecastChannel.TAX,0,60).cents/100.0,.01)
        assertEquals(32_402.48,result.value(ForecastChannel.TAXABLE,0,61).cents/100.0,.01)
        assertEquals(1.0,result.successRate,0.0)
    }
    @Test fun traditionalWithdrawalsRemoveIneligibleAcaCredit() {
        val result=run(input(1_000_000,100_000,incomes=listOf(ForecastIncome(m(25_000),60,60,IncomeKind.ORDINARY)),premium=12_000))
        assertEquals(0L,result.value(ForecastChannel.ACA,0,60).cents)
        assertEquals(0L,result.maximumTaxFundingResidual.cents)
    }
    @Test fun acaFindsFundedSolutionBelowCliffEvenIfLargerWithdrawalWouldLoseCredit() {
        val result=run(input(70_000,60_000,premium=20_000))
        val withdrawn=70_000.0-result.value(ForecastChannel.TRADITIONAL,0,61).cents/100.0
        assertTrue(withdrawn<62_600)
        assertTrue(result.value(ForecastChannel.ACA,0,60).cents>0)
        assertEquals(1.0,result.successRate,0.0)
        assertEquals(60_000.0+result.value(ForecastChannel.TAX,0,60).cents/100.0,
            withdrawn+result.value(ForecastChannel.ACA,0,60).cents/100.0,.02)
    }
    @Test fun currentAcaPercentagesInterpolateAndIncludeExactlyFourHundredPercent() {
        val fpl=21_150.0
        assertEquals(20_000-2*fpl*.066,PlanningTaxPolicy.aca(2*fpl,20_000.0,2,FilingStatus.MARRIED_FILING_JOINTLY),.001)
        assertEquals(20_000-4*fpl*.0996,PlanningTaxPolicy.aca(4*fpl,20_000.0,2,FilingStatus.MARRIED_FILING_JOINTLY),.001)
        assertEquals(0.0,PlanningTaxPolicy.aca(4*fpl+.01,20_000.0,2,FilingStatus.MARRIED_FILING_JOINTLY),0.0)
        assertEquals(0.0,PlanningTaxPolicy.aca(2*fpl,20_000.0,2,FilingStatus.MARRIED_FILING_SEPARATELY),0.0)
    }
    @Test fun spouseRetainsAcaUntilHerOwnMedicareAge() {
        val result=run(ForecastInput(1,1,date,64,64,70,m(30_000),listOf(account(Bucket.ROTH,500_000)),
            incomes=listOf(ForecastIncome(m(30_000),0,130,IncomeKind.ORDINARY)),
            filing=FilingStatus.MARRIED_FILING_JOINTLY,acaHousehold=2,acaPremium=m(24_000),spouseCurrentAge=61))
        assertTrue(result.value(ForecastChannel.ACA,0,65).cents>0)
        assertTrue(result.value(ForecastChannel.ACA,0,65).cents<result.value(ForecastChannel.ACA,0,64).cents)
        assertEquals(0L,result.value(ForecastChannel.ACA,0,68).cents)
        // Healthcare budget is not silently removed when Medicare starts.
        assertEquals(m(30_000),result.annualSpending(68))
    }
    @Test fun socialSecurityStartUsesRecipientsAge() {
        val result=run(ForecastInput(1,1,date,65,65,68,m(10_000),listOf(account(Bucket.ROTH,100_000)),
            incomes=listOf(ForecastIncome(m(10_000),67,130,IncomeKind.SOCIAL_SECURITY,Owner.SPOUSE)),spouseCurrentAge=66))
        assertEquals(m(90_000),result.value(ForecastChannel.ROTH,0,66))
        assertEquals(m(90_000),result.value(ForecastChannel.ROTH,0,67))
    }
    @Test fun recoveryToPurchaseCostCreatesNoTaxableGain() {
        val returns=DoubleArray(12);returns[0]=-.5;returns[6]=1.0
        val result=run(input(100_000,70_000,Bucket.TAXABLE,
            listOf(ForecastIncome(m(70_000),60,60,IncomeKind.TAX_FREE)),end=62),returns)
        assertEquals(0L,result.value(ForecastChannel.TAX,0,61).cents)
        assertEquals(m(30_000),result.value(ForecastChannel.TAXABLE,0,62))
    }
    @Test fun capitalLossesCarryForwardAndOffsetLaterRealizedGains() {
        val returns=DoubleArray(18); returns[12]=2.0
        val result=run(ForecastInput(1,1,date,60,60,63,m(100_000),listOf(account(Bucket.TAXABLE,100_000,200_000)),
            incomes=listOf(ForecastIncome(m(90_000),60,61,IncomeKind.TAX_FREE))),returns)
        assertEquals(0L,result.value(ForecastChannel.TAX,0,62).cents)
        assertEquals(m(140_000),result.value(ForecastChannel.TAXABLE,0,63))
    }
    @Test fun inflationDoesNotEraseNominalTaxableGains() {
        fun projection(inflation: Int)=run(ForecastInput(1,1,date,60,60,62,m(100_000),listOf(account(Bucket.TAXABLE,200_000)),
            incomes=listOf(ForecastIncome(m(100_000),60,61,IncomeKind.ORDINARY)),inflationBps=inflation))
        val flat=projection(0); val inflated=projection(1000)
        // Identical real returns and real-indexed brackets: only the nominal gain on sales changes.
        assertEquals(flat.value(ForecastChannel.TAX,0,60),inflated.value(ForecastChannel.TAX,0,60))
        assertTrue(inflated.value(ForecastChannel.TAX,0,61).cents>flat.value(ForecastChannel.TAX,0,61).cents)
        assertTrue(inflated.value(ForecastChannel.TAXABLE,0,62).cents<flat.value(ForecastChannel.TAXABLE,0,62).cents)
    }
    @Test fun epicProceedsImmediatelyReceiveSixtyFortyReturnAndNewBasis() {
        val epic=listOf(ForecastEpicYear(2026,m(125_000),m(100_000),m(100_000)))
        val returns=doubleArrayOf(.1,.1,0.0,0.0,0.0,0.0)
        val result=run(ForecastInput(1,1,date,45,45,46,m(0),epicYears=epic),returns)
        assertEquals(m(106_000),result.value(ForecastChannel.TAXABLE,0,46))
        assertEquals(m(25_000),result.value(ForecastChannel.TAX,0,45))
        assertEquals(0L,result.value(ForecastChannel.EPIC,0,46).cents)
    }
    @Test fun epicSaleIncomeCountsForAcaWithoutDoubleTaxingNetProceeds() {
        val result=run(ForecastInput(1,1,date,45,45,46,m(10_000),
            epicYears=listOf(ForecastEpicYear(2026,m(200_000),m(150_000),m(190_000))),acaPremium=m(20_000)))
        assertEquals(0L,result.value(ForecastChannel.ACA,0,45).cents)
        assertEquals(m(140_000),result.value(ForecastChannel.TAXABLE,0,46))
        assertEquals(m(50_000),result.value(ForecastChannel.TAX,0,45))
    }
    @Test fun pastRetirementSaleDoesNotDeleteMortgageWithoutSellingHome() {
        val result=run(ForecastInput(1,1,date,65,60,66,m(50_000),listOf(account(Bucket.ROTH,1_000_000)),
            properties=listOf(ForecastProperty(m(300_000),m(100_000),0,m(1000),100,10000)),sellHome=true,spendingIncludesMortgage=true))
        assertEquals(m(50_000),result.annualSpending(65))
        assertEquals(m(212_000),result.value(ForecastChannel.PROPERTY,0,66))
    }
    @Test fun mortgagePaymentStopsAtActualPayoffAndLeavesOtherHousingCosts() {
        val result=run(ForecastInput(1,1,date,60,60,62,m(60_000),listOf(account(Bucket.ROTH,1_000_000)),
            properties=listOf(ForecastProperty(m(300_000),m(6_000),0,m(1000),360,10000)),
            sellHome=false,spendingIncludesMortgage=true))
        assertEquals(m(54_000),result.annualSpending(60))
        assertEquals(m(48_000),result.annualSpending(61))
        assertEquals(m(300_000),result.value(ForecastChannel.PROPERTY,0,61))
    }
    @Test fun realHomeEquityUsesRealMortgageDebt() {
        val result=run(ForecastInput(1,1,date,60,70,71,m(0),
            properties=listOf(ForecastProperty(m(500_000),m(300_000),0,m(1000),300,10000)),inflationBps=1000,sellHome=false))
        assertEquals(430_602.21,result.value(ForecastChannel.PROPERTY,0,70).cents/100.0,.01)
    }
    @Test fun retirementDistributionStartDependsOnBirthCohortAndContinuesPast120() {
        assertNull(PlanningTaxPolicy.rmdDivisor(73,1960))
        assertEquals(24.6,PlanningTaxPolicy.rmdDivisor(75,1960)!!,0.0)
        assertEquals(26.5,PlanningTaxPolicy.rmdDivisor(73,1953)!!,0.0)
        assertEquals(2.0,PlanningTaxPolicy.rmdDivisor(125,1960)!!,0.0)
        val result=run(ForecastInput(1,1,date,75,80,76,m(0),listOf(account(Bucket.TRADITIONAL,24_600)),birthYear=1951))
        assertEquals(m(1000),result.value(ForecastChannel.TAXABLE,0,76))
    }
    @Test fun unusedFederalDeductionOffsetsCapitalGains() {
        assertEquals(0.0,PlanningTaxPolicy.federal(0.0,65_550.0,FilingStatus.SINGLE),0.0)
        assertEquals(.15,PlanningTaxPolicy.federal(0.0,65_551.0,FilingStatus.SINGLE),.00001)
    }
    @Test fun wisconsinExcludesSocialSecurityAndRecognizesRetirementSubtraction() {
        val result=run(input(0,30_000,incomes=listOf(ForecastIncome(m(30_000),0,130,IncomeKind.SOCIAL_SECURITY))))
        assertEquals(0L,result.value(ForecastChannel.TAX,0,60).cents)
        assertEquals(0.0,PlanningTaxPolicy.wisconsin(24_000.0,0.0,FilingStatus.SINGLE,age=67,retirementIncome=24_000.0),0.0)
    }
    @Test fun householdUpgradeIsIdempotentAndPreservesOldRevision() {
        val repo=forecastRepository(); val old=repo.load()
        val changed=repo.upgradePlanningAssumptions()
        assertEquals(old.planSettings.size+1,changed.planSettings.size)
        assertEquals(old.planSettings,changed.planSettings.dropLast(1))
        assertEquals(HomeDisposition.KEEP,changed.planSettings.last().homeDisposition)
        assertEquals(45,changed.planSettings.last().retirementAge)
        assertEquals(1988,changed.planSettings.last().spouseBirthYear)
        assertEquals(changed,repo.upgradePlanningAssumptions())
    }
    @Test fun spouseAssetsExcludedAndCashBasisRetainedWhileTaxableInvestmentsUseZeroBasis() {
        val state=forecastState()
        val self=state.accounts.first().let { a-> a.copy(revisions=a.revisions.map { it.copy(type=AccountType.BROKERAGE,taxTreatment=TaxTreatment.TAXABLE) }) }
        val spouse=state.accounts[1].let { a-> a.copy(revisions=a.revisions.map { it.copy(includedInForecast=true) }) }
        val ready=ForecastInputs.capture(state.copy(accounts=listOf(self,spouse)),date) as ForecastCapture.Ready
        assertEquals(1,ready.input.accounts.size)
        assertEquals(Money(0),ready.input.accounts.single().basis)
        assertEquals(Allocation(us=1.0),ready.input.accounts.single().allocation)
    }
    @Test fun oldSavedDocumentsDecodeWithNoInventedSpouseBenefits() {
        val state=forecastState()
        val json=JSONObject(RetirementCodec.encode(state))
        val plans=json.getJSONArray("planSettings")
        for(i in 0 until plans.length()) {
            val plan=plans.getJSONObject(i); plan.remove("spouseBirthYear")
            val incomes=plan.getJSONArray("incomeStreams")
            for(j in 0 until incomes.length()) incomes.getJSONObject(j).remove("owner")
        }
        assertEquals(state,RetirementCodec.decode(json.toString()))
        val plan=editableIncomePlan(state.planSettings.single().copy(spouseBirthYear=1988,incomeStreams=emptyList()))
        assertEquals(setOf(Owner.SELF,Owner.SPOUSE),plan.incomeStreams.map { it.owner }.toSet())
        assertTrue(plan.incomeStreams.all { it.annualAmount.cents==0L })
        assertEquals(plan,editableIncomePlan(plan))
    }
}
