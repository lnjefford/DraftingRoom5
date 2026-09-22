package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import dev.draftingroom5.retirement.ui.retirementAccountsPreviewState
import dev.draftingroom5.retirement.ui.PrivateDraftStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** All rendered values are hand-authored preview fixtures. Never captures live Retirement data. */
internal class RetirementReviewNativeAudit(private val instrumentation: Instrumentation) {
    fun run() {
        var activity: MainActivity? = null
        var stage = "launch"
        try {
            val automation = instrumentation.uiAutomation
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val output = File(instrumentation.targetContext.filesDir, "retirement-review").apply { mkdirs() }
            val fixtures = retirementAccountsPreviewState()
            val routes = listOf("overview" to AppRoute.RetirementOverview,
                "forecast" to AppRoute.RetirementForecast, "forecast-settings" to AppRoute.RetirementForecastSettings,
                "assets" to AppRoute.RetirementAssets, "accounts" to AppRoute.RetirementAccounts,
                "property" to AppRoute.RetirementPropertyDetail("preview-property"),
                "library" to AppRoute.RetirementLibrary,
                "property-edit" to AppRoute.RetirementPropertyEdit("preview-property"))
            for ((name, route) in routes) {
                stage = name
                val composed = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    activity!!.setContent {
                        RetirementWorkspaceScreen(route, {}, {}, {}, {}, {}, accountsPreviewState = fixtures)
                        SideEffect { composed.countDown() }
                    }
                }
                check(composed.await(20, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                SystemClock.sleep(300)
                check(automation.rootInActiveWindow?.packageName?.toString() == instrumentation.targetContext.packageName)
                instrumentation.runOnMainSync {
                    check(activity!!.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    // Only the synthetic composition above is captureable; production secure behavior stays asserted.
                    activity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                SystemClock.sleep(250)
                val image = checkNotNull(automation.takeScreenshot())
                File(output, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
                instrumentation.runOnMainSync { activity!!.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }
            stage = "private-draft-bundle"
            val saved = Bundle()
            instrumentation.runOnMainSync { instrumentation.callActivityOnSaveInstanceState(activity!!, saved) }
            val parcel = Parcel.obtain()
            val bytes = try { parcel.writeBundle(saved); parcel.marshall() } finally { parcel.recycle() }
            try {
                fun contains(needle: ByteArray): Boolean = (0..(bytes.size - needle.size)).any { start ->
                    needle.indices.all { index -> bytes[start + index] == needle[index] }
                }
                listOf("500 Fixture Way", "198,430.55", "1,745.20").forEach { value ->
                    listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).forEach { charset ->
                        check(!contains(value.toByteArray(charset)))
                    }
                }
            } finally { bytes.fill(0) }
            val drafts = File(instrumentation.targetContext.noBackupFilesDir, "retirement-drafts")
            val store = PrivateDraftStore(drafts)
            check(drafts.listFiles().orEmpty().filter { it.extension == "json" }.any { file ->
                store.restore(file.nameWithoutExtension, "preview-property-r1")?.any { it.contains("500 Fixture Way") } == true
            })
            instrumentation.finish(Activity.RESULT_OK, Bundle().apply {
                putString("stream", "PASS: eight synthetic native Retirement surfaces rendered; secure-window assertion passed before every test-only capture on API ${android.os.Build.VERSION.SDK_INT}\n")
                putString("drafts", "PASS: Android saved-state Bundle excludes property address and amounts; opaque draft restores from no-backup storage")
            })
        } catch (failure: Throwable) {
            instrumentation.finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", "FAIL: synthetic native review at $stage (${failure.javaClass.simpleName})\n")
            })
        } finally { activity?.let { instrumentation.runOnMainSync { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); it.finish() } } }
    }
}
