package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.importer.*
import dev.draftingroom5.retirement.ui.*
import java.io.File
import java.time.Instant
import java.util.UUID

/** Uses fictional assets and an isolated database, never the user's Retirement database. */
internal class EpicNativeAudit(private val instrumentation: Instrumentation) {
    fun run() {
        val parent = instrumentation.targetContext.noBackupFilesDir
        val directory = File(parent, "epic-audit-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) { override fun getNoBackupFilesDir() = directory }
        var database: RetirementDatabase? = null
        var activity: MainActivity? = null
        var stage = "parse"
        val report = StringBuilder()
        try {
            fun fixture(name: String) = instrumentation.context.assets.open(name)
            val candidate = ShareworksImporter().parse(fixture("cached.xlsm"))
            check(candidate.workbook.sharePrice == Money(1000))
            check(candidate.workbook == ShareworksImporter().parse(fixture("values.xlsm")).workbook)
            check(runCatching { ShareworksImporter().parse("invalid".byteInputStream()) }.exceptionOrNull() is WorkbookRejected)
            report.appendLine("PASS: Android SAX and ZIP parse both sanitized golden workbooks with exact cached-result parity; malformed data rejected")
            stage = "atomic-storage"
            database = RetirementDatabase.open(context)
            var repository = RetirementRepository(database.dao)
            var session = EpicImportSession(repository)
            val first = session.review(fixture("cached.xlsm"), repository.load())
            val time = Instant.parse("2026-09-20T18:00:00Z")
            val accepted = (session.confirm(first, time) as RetirementResult.Success).value
            val second = session.review(fixture("values.xlsm"), accepted)
            SQLiteDatabase.openDatabase(File(directory, "retirement/retirement.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
                sql.execSQL("CREATE TRIGGER epic_test_failure BEFORE UPDATE ON retirement_state BEGIN SELECT RAISE(ABORT,'synthetic'); END")
                check(runCatching { session.confirm(second, time.plusSeconds(1)) }.isFailure)
                check(repository.load() == accepted && database!!.dao.epicImportCount() == 1)
                sql.execSQL("DROP TRIGGER epic_test_failure")
                val replaced = (session.confirm(second, time.plusSeconds(2)) as RetirementResult.Success).value
                check(replaced.epicImports.last().replacesImportId == accepted.activeEpicImportId)
                check(runCatching { sql.execSQL("UPDATE epic_imports SET parserVersion=2") }.isFailure)
                check(runCatching { sql.execSQL("DELETE FROM epic_imports") }.isFailure)
                database!!.close(); database = RetirementDatabase.open(context)
                repository = RetirementRepository(database!!.dao); session = EpicImportSession(repository)
                check(repository.load() == replaced && database!!.dao.epicImportCount() == 2)
                check(session.confirm(second, time.plusSeconds(3)) is RetirementResult.Conflict)
            }
            report.appendLine("PASS: real SQLite rollback after import-ledger insertion, atomic pointer/metadata replacement, append-only history, stale review rejection and database recreation")
            stage = "accessible-ui"
            // Connect accessibility before composing; a fresh emulator has no service enabled yet.
            val automation = instrumentation.uiAutomation
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            var chosen = false; var confirmed = false; var cancelled = false
            val chooseComposed = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                activity!!.setContent { RetirementTheme { Surface {
                    EpicUploadPage(null, false, false, null, { chosen = true }, { confirmed = true }, { cancelled = true })
                    androidx.compose.runtime.SideEffect { chooseComposed.countDown() }
                } } }
            }
            check(chooseComposed.await(20, java.util.concurrent.TimeUnit.SECONDS))
            stage = "accessible-choose"
            click("Choose .xlsm workbook")
            check(chosen && !confirmed)
            stage = "accessible-cancel"
            click("Cancel"); check(cancelled && !confirmed)
            val replaceComposed = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                activity!!.setContent { RetirementTheme { Surface {
                    EpicUploadPage(candidate.workbook, true, false, null, {}, { confirmed = true }, {})
                    androidx.compose.runtime.SideEffect { replaceComposed.countDown() }
                } } }
            }
            check(replaceComposed.await(20, java.util.concurrent.TimeUnit.SECONDS))
            check(!confirmed)
            stage = "accessible-replace"
            click("Replace Epic data"); check(confirmed)
            check(!hasEditable(automation.rootInActiveWindow))
            stage = "capture-enabled-window"
            val composed = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                activity!!.setContent {
                    RetirementWorkspaceScreen(AppRoute.RetirementEpicDetail, {}, {}, {}, {}, {}, accountsPreviewState = accepted)
                    androidx.compose.runtime.SideEffect { composed.countDown() }
                }
            }
            // Main-looper idleness alone does not await a Compose frame after setContent.
            check(composed.await(20, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                check(activity!!.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE == 0)
            }
            report.appendLine("PASS: native Choose/Cancel/Replace accessibility actions; explicit confirmation; no editable Epic controls; Retirement window allows screenshots")
            instrumentation.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            val line = failure.stackTrace.firstOrNull { it.className.startsWith("dev.draftingroom5.EpicNativeAudit") }?.lineNumber
            report.appendLine("FAIL: Epic audit at $stage (${failure.javaClass.simpleName}, check line $line); private exception details suppressed")
            instrumentation.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        } finally {
            database?.close()
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            check(directory.canonicalFile.parentFile == parent.canonicalFile && directory.name.startsWith("epic-audit-"))
            directory.deleteRecursively()
        }
    }
    private fun find(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }
    private fun hasEditable(node: AccessibilityNodeInfo?): Boolean = node != null && (node.isEditable || (0 until node.childCount).any { hasEditable(node.getChild(it)) })
    private fun scroll(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        return (0 until node.childCount).any { scroll(node.getChild(it)) }
    }
    private fun click(label: String) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var node: AccessibilityNodeInfo?
        do {
            node = find(instrumentation.uiAutomation.rootInActiveWindow, label)
            if (node != null) break
            check(SystemClock.uptimeMillis() < deadline)
            scroll(instrumentation.uiAutomation.rootInActiveWindow); SystemClock.sleep(150)
        } while (true)
        var target = checkNotNull(node)
        while (!target.isClickable) target = checkNotNull(target.parent)
        val bounds = android.graphics.Rect().also(target::getBoundsInScreen)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        check(bounds.height() >= 48 * density - 1)
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
}
