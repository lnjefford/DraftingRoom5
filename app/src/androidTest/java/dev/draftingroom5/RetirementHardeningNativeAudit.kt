package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import androidx.work.WorkManager
import dev.draftingroom5.retirement.provider.RetirementAccountSyncWorker
import dev.draftingroom5.retirement.provider.RetirementPropertySyncWorker
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Synthetic scheduler audit. Work input is opaque local identity only; no provider call runs. */
internal class RetirementHardeningNativeAudit(private val instrumentation: Instrumentation) {
    fun run() {
        val report = StringBuilder()
        val account = UUID.randomUUID().toString()
        val property = UUID.randomUUID().toString()
        val accountName = "retirement-account-$account"
        val propertyName = "retirement-property-$property"
        val manager = WorkManager.getInstance(instrumentation.targetContext)
        try {
            RetirementAccountSyncWorker.schedule(instrumentation.targetContext, account)
            RetirementAccountSyncWorker.schedule(instrumentation.targetContext, account)
            RetirementPropertySyncWorker.schedule(instrumentation.targetContext, property)
            RetirementPropertySyncWorker.schedule(instrumentation.targetContext, property)
            val accountRows = manager.getWorkInfosForUniqueWork(accountName).get(20, TimeUnit.SECONDS)
            val propertyRows = manager.getWorkInfosForUniqueWork(propertyName).get(20, TimeUnit.SECONDS)
            check(accountRows.size == 1 && propertyRows.size == 1)
            check(accountRows.single().tags.any { it.startsWith("retirement-sync-v") })
            check(propertyRows.single().tags.any { it.startsWith("retirement-sync-v") })
            check(accountRows.single().state.name in setOf("ENQUEUED", "BLOCKED"))
            check(propertyRows.single().state.name in setOf("ENQUEUED", "BLOCKED"))
            report.appendLine("PASS: API ${android.os.Build.VERSION.SDK_INT} unique versioned account/property periodic work remains single after repeated scheduling")
            instrumentation.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            report.appendLine("FAIL: Retirement hardening scheduler audit (${failure.javaClass.simpleName}); private exception details suppressed")
            instrumentation.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        } finally {
            manager.cancelUniqueWork(accountName)
            manager.cancelUniqueWork(propertyName)
        }
    }
}
