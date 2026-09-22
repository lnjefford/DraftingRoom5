package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.forecast.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic CPU/memory/cancellation audit. Does not open or change the user's database or UI. */
internal class ForecastNativeAudit(private val instrumentation: Instrumentation) {
    fun run() {
        val report=StringBuilder()
        var stage="setup"
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        try {
            runBlocking {
                val input=ForecastInput(74,1,LocalDate.of(2026,1,1),40,50,90,Money(8000000),listOf(
                    ForecastAccount(Bucket.TRADITIONAL,Money(65000000),Money(0),Allocation(.6,.15,.2,.05)),
                    ForecastAccount(Bucket.ROTH,Money(15000000),Money(0),Allocation(.6,.15,.2,.05)),
                    ForecastAccount(Bucket.TAXABLE,Money(25000000),Money(16000000),Allocation(.65,.2,.1,.05))))
                val engine=RetirementEngine()
                val ticks=AtomicInteger()
                val handler=Handler(Looper.getMainLooper())
                val pulse=object:Runnable { override fun run() { ticks.incrementAndGet(); handler.postDelayed(this,25) } }
                handler.post(pulse)
                try {
                    val start=SystemClock.elapsedRealtime()
                    stage="calculate"
                    val result=withTimeout(60000) { scope.async { engine.calculate(input,10000,74,true) }.await() }
                    val elapsed=SystemClock.elapsedRealtime()-start
                    check(result.paths==10000 && result.generation==74L)
                    check(ticks.get()>3) { "Main thread did not remain responsive" }
                    check(elapsed<30000) { "Native forecast exceeded 30-second emulator budget" }
                    val runtime=Runtime.getRuntime()
                    val used=runtime.totalMemory()-runtime.freeMemory()
                    report.appendLine("PASS: API ${android.os.Build.VERSION.SDK_INT}; 10000 paths x 50 years with mixed accounts/taxes: ${elapsed}ms; main pulses=${ticks.get()}; process heap used=$used bytes, maximum=${runtime.maxMemory()} bytes")
                    val started=CompletableDeferred<Unit>()
                    stage="cancel"
                    val cancelled=scope.async { started.complete(Unit); engine.calculate(input,10000,75,true) }
                    started.await()
                    val cancelAt=SystemClock.elapsedRealtime()
                    cancelled.cancelAndJoin()
                    check(cancelled.isCancelled)
                    check(SystemClock.elapsedRealtime()-cancelAt<2000)
                    report.appendLine("PASS: cancellation returns within 2 seconds without a partial result")
                    stage="seed-repeat"
                    val a=engine.calculate(input,100,74,true)
                    val b=engine.calculate(input,100,74,true)
                    for(path in 0..99) for(age in 40..90) check(a.value(ForecastChannel.TOTAL,path,age)==b.value(ForecastChannel.TOTAL,path,age))
                    report.appendLine("PASS: seeded Android results repeat at every path/year; no database or UI changes")
                } finally { handler.removeCallbacks(pulse) }
            }
            instrumentation.finish(Activity.RESULT_OK,Bundle().apply { putString("stream",report.toString()) })
        } catch (failure:Throwable) {
            report.appendLine("FAIL: synthetic forecast audit at $stage: ${failure.javaClass.simpleName}; heap maximum=${Runtime.getRuntime().maxMemory()} bytes")
            instrumentation.finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream",report.toString()) })
        } finally { scope.cancel() }
    }
}
