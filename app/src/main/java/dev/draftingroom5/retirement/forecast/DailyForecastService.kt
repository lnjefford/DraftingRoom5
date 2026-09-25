package dev.draftingroom5.retirement.forecast

import android.content.Context
import android.util.AtomicFile
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.domain.Money
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.time.LocalDate

internal data class CachedForecast(val date: LocalDate, val result: ForecastResult)

internal interface ForecastCacheStorage {
    fun read(): CachedForecast?
    fun write(value: CachedForecast)
    fun clear()
}

internal class AndroidForecastCacheStorage(context: Context) : ForecastCacheStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "retirement/forecast-cache.bin"))

    override fun read(): CachedForecast? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        require(file.baseFile.length() <= MAX_BYTES)
        return file.openRead().use(ForecastCacheCodec::decode)
    }

    override fun write(value: CachedForecast) {
        check(file.baseFile.parentFile!!.let { it.exists() || it.mkdirs() })
        val stream = file.startWrite()
        try { ForecastCacheCodec.encode(value, stream); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        require(file.baseFile.length() <= MAX_BYTES)
    }

    override fun clear() = file.delete()

    private companion object { const val MAX_BYTES = 160L * 1024 * 1024 }
}

/** One process-wide forecast result, restored from private storage and refreshed at most once per civil day. */
internal class DailyForecastService(
    private val repository: RetirementRepository,
    private val scope: CoroutineScope,
    private val storage: ForecastCacheStorage,
    private val today: () -> LocalDate = LocalDate::now,
    private val calculate: suspend (ForecastInput, Int, Long?, Boolean) -> ForecastResult = { input, paths, seed, stochastic ->
        RetirementEngine().calculate(input, paths, seed, stochastic)
    },
) {
    private val guard = Any()
    private val mutableState = MutableStateFlow<ForecastState>(ForecastState.Idle)
    val state: StateFlow<ForecastState> = mutableState.asStateFlow()
    private var requestId = 0L
    private var active: Job? = null
    private var loaded = false
    private var resultDate: LocalDate? = null
    private var previous: ForecastResult? = null

    fun ensure(paths: Int = 10_000) = start(paths, force = false)
    fun restart(paths: Int = 10_000) = start(paths, force = true)

    private fun start(paths: Int, force: Boolean) = synchronized(guard) {
        require(paths in 1..20_000)
        val date = today()
        if (!force && resultDate == date && mutableState.value !is ForecastState.Calculating) return@synchronized
        if (!force && active?.isActive == true) return@synchronized
        active?.cancel()
        val id = ++requestId
        mutableState.value = ForecastState.Calculating(previous?.generation ?: 0, previous)
        active = scope.launch(Dispatchers.Default) {
            if (!force && !loaded) {
                loaded = true
                val cached = withContext(Dispatchers.IO) { runCatching { storage.read() }.getOrNull() }
                if (cached != null && cached.date == date) {
                    synchronized(guard) {
                        if (id == requestId) {
                            previous = cached.result
                            resultDate = cached.date
                            mutableState.value = ForecastState.Ready(cached.result)
                            active = null
                        }
                    }
                    return@launch
                }
            }
            val captured = try { withContext(Dispatchers.IO) { ForecastInputs.capture(repository.load(), date) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publish(id, ForecastState.Failed(previous), date); return@launch }
            if (captured is ForecastCapture.NeedsData) {
                publish(id, ForecastState.NeedsData(captured.reason, previous), date)
                return@launch
            }
            val input = (captured as ForecastCapture.Ready).input
            synchronized(guard) { if (id == requestId) mutableState.value = ForecastState.Calculating(input.generation, previous) }
            try {
                val result = calculate(input, paths, null, true)
                currentCoroutineContext().ensureActive()
                require(result.generation == input.generation && result.planRevision == input.planRevision)
                val current = withContext(Dispatchers.IO) {
                    repository.ifGeneration(input.generation) {
                        runCatching { storage.write(CachedForecast(date, result)) }
                    }
                }
                if (current) publish(id, ForecastState.Ready(result), date, result)
                else publish(id, ForecastState.Failed(previous), date)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publish(id, ForecastState.Failed(previous), date) }
        }
    }

    private fun publish(id: Long, value: ForecastState, date: LocalDate, result: ForecastResult? = null) = synchronized(guard) {
        if (id != requestId) return@synchronized
        if (result != null) previous = result
        resultDate = date
        mutableState.value = value
        active = null
    }
}

internal object ForecastCacheCodec {
    private const val MAGIC = 0x44523546
    private const val VERSION = 1

    fun encode(cached: CachedForecast, output: OutputStream) {
        val data = DataOutputStream(BufferedOutputStream(output))
        val result = cached.result
        data.writeInt(MAGIC); data.writeInt(VERSION); data.writeLong(cached.date.toEpochDay())
        data.writeUTF(result.engineVersion); data.writeLong(result.generation); data.writeLong(result.planRevision)
        data.writeInt(result.currentAge); data.writeInt(result.endAge); data.writeInt(result.paths)
        data.writeBoolean(result.seed != null); result.seed?.let(data::writeLong); data.writeBoolean(result.stochastic)
        data.writeLong(result.maximumTaxFundingResidual.cents); data.writeInt(result.taxResidualYears)
        data.writeInt(result.failures.size)
        result.failures.forEach { failure ->
            data.writeInt(failure.path); data.writeInt(failure.age); data.writeLong(failure.unmet.cents)
            data.writeLong(failure.retainedAssets.cents); data.writeInt(failure.cause.ordinal)
        }
        data.writeInt(result.warnings.size); result.warnings.forEach(data::writeUTF)
        result.writeSpending(data::writeDouble)
        result.writeSeries(data::writeDouble)
        data.flush()
    }

    fun decode(input: InputStream): CachedForecast = DataInputStream(BufferedInputStream(input)).use { data ->
        require(data.readInt() == MAGIC && data.readInt() == VERSION)
        val date = LocalDate.ofEpochDay(data.readLong())
        require(data.readUTF() == "dr5-household-engine-3")
        val generation = data.readLong(); val revision = data.readLong()
        val currentAge = data.readInt(); val endAge = data.readInt(); val paths = data.readInt()
        val width = endAge - currentAge + 1
        require(generation >= 0 && revision > 0 && currentAge in 0..130 && endAge in currentAge..130)
        require(paths in 1..20_000 && paths.toLong() * width <= 1_000_000)
        val seed = if (data.readBoolean()) data.readLong() else null
        val stochastic = data.readBoolean()
        val residual = Money(data.readLong()); val residualYears = data.readInt()
        val failureCount = data.readInt(); require(failureCount in 0..paths)
        val failures = List(failureCount) {
            val path = data.readInt(); val age = data.readInt(); val unmet = Money(data.readLong()); val retained = Money(data.readLong())
            val cause = FailureCause.entries.getOrNull(data.readInt()) ?: error("Invalid failure cause")
            require(path in 0 until paths && age in currentAge..endAge)
            PathFailure(path, age, unmet, retained, cause)
        }
        val warningCount = data.readInt(); require(warningCount in 0..100)
        val warnings = List(warningCount) { data.readUTF() }
        val spending = DoubleArray(width) { data.readDouble().also { require(it.isFinite()) } }
        val series = Array(ForecastChannel.entries.size) { DoubleArray(paths * width) }
        for (channel in ForecastChannel.entries) for (index in series[channel.ordinal].indices) {
            series[channel.ordinal][index] = data.readDouble().also { require(it.isFinite()) }
        }
        require(data.read() == -1)
        CachedForecast(date, ForecastResult(generation, revision, currentAge, endAge, paths, seed, stochastic,
            series, failures, residual, residualYears, warnings, spending))
    }
}
