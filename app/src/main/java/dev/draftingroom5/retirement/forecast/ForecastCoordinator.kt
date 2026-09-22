package dev.draftingroom5.retirement.forecast

import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface ForecastState {
    data object Idle : ForecastState
    data class Calculating(val generation: Long, val previous: ForecastResult?) : ForecastState
    data class Ready(val result: ForecastResult) : ForecastState
    data class NeedsData(val reason: MissingForecastData, val previous: ForecastResult?) : ForecastState
    data class Failed(val previous: ForecastResult?) : ForecastState
    data class Cancelled(val previous: ForecastResult?) : ForecastState
}

/** Own in a ViewModel scope. Latest request and database generation both guard a complete publication. */
class ForecastCoordinator(
    private val repository: RetirementRepository, scope: CoroutineScope,
    private val calculate: suspend (ForecastInput, Int, Long?, Boolean) -> ForecastResult = { input, count, seed, stochastic ->
        RetirementEngine().calculate(input, count, seed, stochastic)
    },
) : AutoCloseable {
    private data class Request(val id: Long, val paths: Int, val seed: Long?, val stochastic: Boolean, val active: Boolean)
    private val guard = Any()
    private var closed = false
    private val requests = MutableStateFlow(Request(0, 1, null, false, false))
    private val mutableState = MutableStateFlow<ForecastState>(ForecastState.Idle)
    val state: StateFlow<ForecastState> = mutableState.asStateFlow()
    private var previous: ForecastResult? = null
    private val job = scope.launch(Dispatchers.Default) {
        combine(requests, repository.changes) { request, _ -> request }.collectLatest { request ->
            if (!request.active) return@collectLatest
            val captured = try { withContext(Dispatchers.IO) { ForecastInputs.capture(repository.load()) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                synchronized(guard) { if (current(request)) mutableState.value = ForecastState.Failed(previous) }
                return@collectLatest
            }
            if (captured is ForecastCapture.NeedsData) {
                synchronized(guard) { if (current(request)) mutableState.value = ForecastState.NeedsData(captured.reason, previous) }
                return@collectLatest
            }
            val input = (captured as ForecastCapture.Ready).input
            synchronized(guard) { if (current(request)) mutableState.value = ForecastState.Calculating(input.generation, previous) }
            try {
                val result = calculate(input, request.paths, request.seed, request.stochastic)
                currentCoroutineContext().ensureActive()
                require(result.generation == input.generation && result.planRevision == input.planRevision)
                withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    repository.ifGeneration(input.generation) {
                        context.ensureActive()
                        synchronized(guard) {
                            if (current(request)) { previous = result; mutableState.value = ForecastState.Ready(result) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                synchronized(guard) { if (current(request)) mutableState.value = ForecastState.Failed(previous) }
            }
        }
    }
    private fun current(request: Request) = !closed && requests.value == request
    fun restart(paths: Int = 10000, seed: Long? = null, stochastic: Boolean = true) = synchronized(guard) {
        check(!closed); require(paths in 1..20000)
        requests.value = Request(Math.addExact(requests.value.id, 1), paths, seed, stochastic, true)
        // A replaced request immediately marks the last complete result stale.
        mutableState.value = ForecastState.Calculating(previous?.generation ?: 0, previous)
    }
    fun cancel() = synchronized(guard) {
        requests.value = requests.value.copy(id = Math.addExact(requests.value.id, 1), active = false)
        mutableState.value = ForecastState.Cancelled(previous)
    }
    override fun close() { synchronized(guard) { closed = true }; job.cancel() }
}

/** Typed, single-setting deltas. Applying a preview never overwrites unrelated settings. */
sealed interface ScenarioDelta {
    fun change(plan: PlanSettings): PlanSettings
    data class Spending(val value: Money) : ScenarioDelta { override fun change(plan: PlanSettings) = plan.copy(annualSpending = value) }
    data class RetirementAge(val value: Int) : ScenarioDelta { override fun change(plan: PlanSettings) = plan.copy(retirementAge = value) }
    data class EquityReturn(val bps: Int) : ScenarioDelta { override fun change(plan: PlanSettings) = plan.copy(expectedReturnBps = bps) }
    data class Inflation(val bps: Int) : ScenarioDelta { override fun change(plan: PlanSettings) = plan.copy(inflationBps = bps) }
    data class Home(val value: HomeDisposition) : ScenarioDelta { override fun change(plan: PlanSettings) = plan.copy(homeDisposition = value) }
    data class Pension(val streamId: String, val annual: Money) : ScenarioDelta {
        override fun change(plan: PlanSettings): PlanSettings {
            require(plan.incomeStreams.any { it.id == streamId && it.taxKind == IncomeTaxKind.ORDINARY })
            return plan.copy(incomeStreams = plan.incomeStreams.map { if (it.id == streamId) it.copy(annualAmount = annual) else it })
        }
    }
}
data class ForecastScenario(val baseGeneration: Long, val basePlanRevision: Long, val delta: ScenarioDelta) {
    fun preview(state: RetirementState): ForecastCapture {
        require(state.generation == baseGeneration)
        val plan = state.planSettings.maxByOrNull { it.revision }
        require(plan?.revision == basePlanRevision)
        return ForecastInputs.capture(state.copy(planSettings = listOf(delta.change(plan!!))))
    }
    fun apply(repository: RetirementRepository): RetirementResult<RetirementState> {
        val state = repository.load()
        if (state.generation != baseGeneration) return RetirementResult.Conflict(state.generation)
        val plan = state.planSettings.maxByOrNull { it.revision }
        if (plan?.revision != basePlanRevision) return RetirementResult.Conflict(state.generation)
        val changed = try { delta.change(plan).copy(revision = Math.addExact(plan.revision, 1), id = java.util.UUID.randomUUID().toString()) }
            catch (error: IllegalArgumentException) { return RetirementResult.Invalid(error) }
        val captured = ForecastInputs.capture(state.copy(planSettings = listOf(changed)))
        if (captured !is ForecastCapture.Ready) return RetirementResult.Invalid(IllegalArgumentException("Scenario needs valid forecast inputs."))
        return repository.savePlan(baseGeneration, changed)
    }
}
