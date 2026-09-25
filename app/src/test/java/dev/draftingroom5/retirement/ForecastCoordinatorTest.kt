package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.forecast.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

internal fun forecastState(): RetirementState {
    val state=retirementFixture()
    val plan=state.planSettings.single().copy(birthDate=LocalDate.of(1980,1,2),referenceDate=LocalDate.of(2026,1,1),
        retirementAge=60,endAge=90,incomeStreams=listOf(IncomeStream("ss",Money(3000000),67,130,IncomeTaxKind.SOCIAL_SECURITY)))
    return state.copy(epicImports=emptyList(),activeEpicImportId=null,importMetadata=emptyList(),
        accounts=state.accounts.filter { it.origin != AccountOrigin.EPIC },planSettings=listOf(plan))
}
private val FORECAST_TEST_TODAY: LocalDate = LocalDate.of(2026, 1, 1)
private fun captureForecast(state: RetirementState, today: LocalDate = FORECAST_TEST_TODAY) = ForecastInputs.capture(state, today)
internal fun forecastRepository(): RetirementRepository {
    val state=forecastState(); val dao=FakeRetirementDao()
    dao.initialize(RetirementStateEntity(generation=state.generation,document=RetirementCodec.encode(state)))
    return RetirementRepository(dao)
}

class ForecastCoordinatorTest {
    private class MemoryForecastCache : ForecastCacheStorage {
        var bytes: ByteArray? = null
        override fun read() = bytes?.let { ForecastCacheCodec.decode(ByteArrayInputStream(it)) }
        override fun write(value: CachedForecast) {
            bytes = ByteArrayOutputStream().also { ForecastCacheCodec.encode(value, it) }.toByteArray()
        }
        override fun clear() { bytes = null }
    }

    @Test fun dailyForecastReusesMemoryAndDiskUntilTomorrowOrManualRestart() = runBlocking {
        val repository = forecastRepository()
        val cache = MemoryForecastCache()
        var date = FORECAST_TEST_TODAY
        val calls = AtomicInteger()
        suspend fun calculated(input: ForecastInput): ForecastResult {
            calls.incrementAndGet()
            return RetirementEngine().calculate(input, 1, 74, true)
        }
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = DailyForecastService(repository, firstScope, cache, { date }) { input, _, _, _ -> calculated(input) }
        try {
            first.ensure(1)
            withTimeout(5000) { first.state.first { it is ForecastState.Ready } }
            first.ensure(1)
            delay(100)
            assertEquals(1, calls.get())

            first.restart(1)
            withTimeout(5000) { first.state.first { it is ForecastState.Ready && calls.get() == 2 } }
            assertEquals(2, calls.get())

            date = date.plusDays(1)
            first.ensure(1)
            withTimeout(5000) { first.state.first { it is ForecastState.Ready && calls.get() == 3 } }
            assertEquals(3, calls.get())
        } finally { firstScope.cancel() }

        val restoredCalls = AtomicInteger()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val restored = DailyForecastService(repository, secondScope, cache, { date }) { input, _, _, _ ->
            restoredCalls.incrementAndGet(); RetirementEngine().calculate(input, 1, 74, true)
        }
        try {
            restored.ensure(1)
            val ready = withTimeout(5000) { restored.state.first { it is ForecastState.Ready } } as ForecastState.Ready
            assertEquals(0, restoredCalls.get())
            assertEquals(repository.load().generation, ready.result.generation)
            assertEquals(ready.result.percentile(ForecastChannel.TOTAL, ready.result.endAge, 50.0),
                ForecastCacheCodec.decode(ByteArrayInputStream(cache.bytes!!)).result.percentile(ForecastChannel.TOTAL, ready.result.endAge, 50.0))
        } finally { secondScope.cancel() }
    }

    @Test fun anniversaryAndLeapDayAgePolicyAreExplicit() {
        val original=forecastState()
        fun age(birth:String,reference:String):Int {
            val plan=original.planSettings.single().copy(birthDate=LocalDate.parse(birth),referenceDate=LocalDate.parse(reference))
            return (captureForecast(original.copy(planSettings=listOf(plan)), LocalDate.parse(reference)) as ForecastCapture.Ready).input.currentAge
        }
        assertEquals(45,age("1980-01-02","2026-01-01"))
        assertEquals(46,age("1980-01-02","2026-01-02"))
        assertEquals(44,age("1980-02-29","2025-02-28"))
        assertEquals(45,age("1980-02-29","2025-03-01"))
    }
    @Test fun capturedSnapshotUsesConfirmedTaxTreatmentAndStrictRequiredInputs() {
        val state=forecastState()
        val stalePlan = state.planSettings.single().copy(referenceDate = LocalDate.of(2000, 1, 1))
        val ready=captureForecast(state.copy(planSettings = listOf(stalePlan))) as ForecastCapture.Ready
        assertEquals(FORECAST_TEST_TODAY, ready.input.referenceDate)
        assertEquals(45,ready.input.currentAge) // Birthday is tomorrow: no approximate year subtraction.
        assertEquals(IncomeKind.SOCIAL_SECURITY,ready.input.incomes.single().kind)
        val account=state.accounts.first().let { a -> a.copy(revisions=a.revisions.map { it.copy(taxTreatment=TaxTreatment.TAXABLE) }) }
        val reclassified=captureForecast(state.copy(accounts=listOf(account),properties=emptyList())) as ForecastCapture.Ready
        assertEquals(Bucket.TAXABLE,reclassified.input.accounts.single().bucket)
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.PLAN),captureForecast(state.copy(planSettings=emptyList())))
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.UNSUPPORTED_POLICY),captureForecast(state.copy(planSettings=listOf(state.planSettings.single().copy(taxPolicyId="current-law")))))
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.BALANCE),captureForecast(state.copy(accounts=listOf(account.copy(balances=emptyList())),properties=emptyList())))
        val negative=account.copy(balances=account.balances.map { it.copy(amount=Money(-1)) })
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.INVALID_INPUT),captureForecast(state.copy(accounts=listOf(negative),properties=emptyList())))
        val inconsistent=account.copy(revisions=account.revisions.map { it.copy(taxTreatment=TaxTreatment.TAX_FREE) })
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.INVALID_INPUT),captureForecast(state.copy(accounts=listOf(inconsistent),properties=emptyList())))
        val bookState=retirementFixture()
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.EPIC_PROJECTION),captureForecast(bookState))
    }
    @Test fun epicAnnualCoverageAllowsADifferentWorkbookProjectionDay() {
        val state = forecastState()
        val plan = state.planSettings.single().copy(referenceDate = LocalDate.of(2026, 1, 3), retirementAge = 60)
        val imported = retirementFixture().epicImports.single()
        val years = (2026..2040).map { year -> EpicYear(year, "0", Money(0), Money(0), Money(0), Money(0), Money(0), Money(0), Money(0), Money(0), Money(0), Money(0)) }
        val book = imported.workbook.copy(projection = imported.workbook.projection.copy(date = LocalDate.of(2040, 1, 1)), years = years)
        val accepted = imported.copy(workbook = book)
        val withBook = state.copy(planSettings = listOf(plan), epicImports = listOf(accepted), activeEpicImportId = accepted.id)
        val today = LocalDate.of(2026, 1, 3)
        assertTrue(captureForecast(withBook, today) is ForecastCapture.Ready)
        val missingYear = accepted.copy(workbook = book.copy(years = years.filterNot { it.year == 2035 }))
        assertEquals(ForecastCapture.NeedsData(MissingForecastData.EPIC_PROJECTION),
            captureForecast(withBook.copy(epicImports = listOf(missingYear)), today))
    }
    @Test fun cleanSchemaRoundTripsEveryIncomeKindAndForecastSettingWithoutBooleanInference() {
        val state=forecastState()
        val plan=state.planSettings.single().copy(annualHsaContribution=Money(430000),annualMedicalSpending=Money(200000),homeRealAppreciationBps=125,volatilityScaleBps=12000,
            incomeStreams=IncomeTaxKind.entries.mapIndexed { i,kind -> IncomeStream("income-$i",Money(10000),60,90,kind) })
        val changed=state.copy(planSettings=listOf(plan))
        assertEquals(changed,RetirementCodec.decode(RetirementCodec.encode(changed)))
        assertThrows(IllegalArgumentException::class.java) { validateRetirementState(changed.copy(planSettings=listOf(plan.copy(annualHsaContribution=Money(-1))))) }
    }
    @Test fun typedScenarioPreviewIsIsolatedAndApplyRejectsStaleGenerations() {
        val repository=forecastRepository(); val base=repository.load(); val plan=base.planSettings.single()
        val scenario=ForecastScenario(base.generation,plan.revision,ScenarioDelta.Spending(Money(5000000)))
        val preview=scenario.preview(base) as ForecastCapture.Ready
        assertEquals(Money(5000000),preview.input.spending)
        assertEquals(base,repository.load())
        val saved=(scenario.apply(repository) as RetirementResult.Success).value
        assertEquals(plan.copy(id=saved.planSettings.last().id,revision=plan.revision+1,annualSpending=Money(5000000)),saved.planSettings.last())
        assertTrue(scenario.apply(repository) is RetirementResult.Conflict)
        assertThrows(IllegalArgumentException::class.java) { scenario.preview(saved) }
        val bad=ForecastScenario(saved.generation,plan.revision+1,ScenarioDelta.Spending(Money(-1)))
        assertTrue(bad.apply(repository) is RetirementResult.Invalid)
        assertEquals(saved,repository.load())
    }
    @Test fun calculationDispatchesOffCallerAndCancellationNeverReturnsPartialResult() = runBlocking {
        val caller=Thread.currentThread().name
        var worker=""
        withContext(Dispatchers.Default) { worker=Thread.currentThread().name }
        assertNotEquals(caller,worker)
        val job=async(start=CoroutineStart.LAZY) { RetirementEngine().calculate(simpleInput(),10000,74) }
        job.cancel(); assertTrue(job.isCancelled)
        assertThrows(CancellationException::class.java) { runBlocking { job.await() } }
        var checks=0
        assertThrows(CancellationException::class.java) {
            RetirementEngine().compute(simpleInput(),10000,74,true,checkpoint={ if(++checks==4) throw CancellationException("synthetic") })
        }
        assertEquals(4,checks)
    }
    @Test fun restartAndRepositoryMutationOnlyPublishLatestCompleteGeneration() = runBlocking {
        val repository=forecastRepository(); val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        data class Attempt(val input:ForecastInput,val release:CompletableDeferred<Unit>)
        val attempts=Channel<Attempt>(Channel.UNLIMITED)
        val coordinator=ForecastCoordinator(repository,scope) { input,_,_,_ ->
            val gate=CompletableDeferred<Unit>(); attempts.send(Attempt(input,gate))
            // Deliberately uncooperative dependency must still fail publication guards.
            withContext(NonCancellable) { gate.await() }
            RetirementEngine().compute(input,1,0,false)
        }
        try {
            coordinator.restart(stochastic=false)
            val first=withTimeout(5000) { attempts.receive() }
            coordinator.restart(stochastic=false,seed=2)
            first.release.complete(Unit)
            val second=withTimeout(5000) { attempts.receive() }
            assertTrue(coordinator.state.value is ForecastState.Calculating)
            val old=repository.load(); val plan=old.planSettings.last()
            repository.savePlan(old.generation,plan.copy(id="next",revision=plan.revision+1,annualSpending=Money(5000000)))
            second.release.complete(Unit)
            val third=withTimeout(5000) { attempts.receive() }
            assertTrue(third.input.generation>second.input.generation)
            third.release.complete(Unit)
            val ready=withTimeout(5000) { coordinator.state.first { it is ForecastState.Ready } } as ForecastState.Ready
            assertEquals(repository.load().generation,ready.result.generation)
            coordinator.restart(stochastic=false)
            val fourth=withTimeout(5000) { attempts.receive() }
            val calculating=coordinator.state.value as ForecastState.Calculating
            assertSame(ready.result,calculating.previous)
            coordinator.cancel(); fourth.release.complete(Unit)
            assertTrue(coordinator.state.value is ForecastState.Cancelled)
            assertSame(ready.result,(coordinator.state.value as ForecastState.Cancelled).previous)
        } finally { coordinator.close(); scope.cancel() }
    }
    @Test fun errorsAndNeedsDataRetainOnlyLastCompleteStaleResult() = runBlocking {
        val repository=forecastRepository(); val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val calls=AtomicInteger()
        val coordinator=ForecastCoordinator(repository,scope) { input,_,_,_ ->
            if(calls.incrementAndGet()>1) throw IllegalArgumentException("private synthetic error should not cross state")
            RetirementEngine().compute(input,1,0,false)
        }
        try {
            coordinator.restart(stochastic=false)
            val ready=withTimeout(5000) { coordinator.state.first { it is ForecastState.Ready } } as ForecastState.Ready
            coordinator.restart(stochastic=false)
            val failed=withTimeout(5000) { coordinator.state.first { it is ForecastState.Failed } } as ForecastState.Failed
            assertSame(ready.result,failed.previous)
            assertFalse(failed.toString().contains("synthetic error"))
        } finally { coordinator.close(); scope.cancel() }
    }
}
