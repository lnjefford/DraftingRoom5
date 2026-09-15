package dev.draftingroom5

import android.app.Activity
import android.app.ActivityManager
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import java.io.File

/** Dependency-free native audit runner. See docs/reviews/DR5-056 for emulator setup. */
class WidgetAuditInstrumentation : Instrumentation() {
    private val report = StringBuilder()
    private lateinit var output: File
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        output = File(targetContext.filesDir, "widget-audit").apply { mkdirs() }
        try {
            audit()
            report.appendLine("PASS: all native widget assertions on API ${Build.VERSION.SDK_INT}")
            File(output, "results.txt").writeText(report.toString())
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            report.appendLine("FAIL: ${failure.stackTraceToString()}")
            File(output, "results.txt").writeText(report.toString())
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        }
    }

    private fun audit() {
        val context = targetContext
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, LivingIconWidgetProvider::class.java)
        val widgetProcess = "${context.packageName}:living_icon"
        check(context.packageManager.getReceiverInfo(component, 0).processName == widgetProcess)
        check(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
            .providers.orEmpty().none { it.processName == widgetProcess })
        val initialIds = manager.getAppWidgetIds(component).toSet()
        val info = manager.installedProviders.single { it.provider == component }
        check(info.updatePeriodMillis == 3_600_000)
        check(info.resizeMode == AppWidgetProviderInfo.RESIZE_NONE)
        if (Build.VERSION.SDK_INT >= 31) check(info.targetCellWidth == 1 && info.targetCellHeight == 1)
        check(LivingIconWidgetProvider.images.size == 50)
        val captures = mutableMapOf<Int, CaptureView>()
        fun newHost() = object : AppWidgetHost(context, 560056) {
            override fun onCreateView(c: Context, id: Int, i: AppWidgetProviderInfo): AppWidgetHostView =
                CaptureView(c).also { captures[id] = it }
        }
        lateinit var host: AppWidgetHost
        runOnMainSync {
            host = newHost()
            host.startListening()
        }
        val ids = intArrayOf(host.allocateAppWidgetId(), host.allocateAppWidgetId())
        try {
            ids.forEach { id ->
                check(manager.bindAppWidgetIdIfAllowed(id, component)) {
                    "Run adb shell appwidget grantbind --package dev.draftingroom5 first"
                }
                runOnMainSync { host.createView(context, id, info) }
            }
            val provider = LivingIconWidgetProvider()
            // Await the real system broadcasts after binding, not an in-process test update.
            await { ids.all { captures[it]?.latest != null } }
            check(manager.getAppWidgetIds(component).toSet().containsAll(ids.toList()))
            val first = imagePixels(captures.getValue(ids[0]).latest!!)
            check(first.contentEquals(imagePixels(captures.getValue(ids[1]).latest!!)))
            report.appendLine("PASS: framework broadcasts update two instances through the private receiver process")

            // Native RemoteViews application, WebP decoder, fitCenter geometry and alpha at 56 px.
            val sheet = Bitmap.createBitmap(800, 700, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.rgb(225, 225, 225))
            val paint = Paint().apply { color = Color.BLACK; textSize = 12f }
            val renderedHashes = mutableSetOf<Int>()
            LivingIconWidgetProvider.images.forEachIndexed { index, resource ->
                val views = RemoteViews(context.packageName, R.layout.living_icon_widget).apply {
                    setImageViewResource(R.id.living_icon_image, resource)
                }
                val rendered = render(views, 56, 56)
                check(Color.alpha(rendered.getPixel(0, 0)) == 0)
                check(Color.alpha(rendered.getPixel(55, 55)) == 0)
                val pixels = IntArray(56 * 56)
                rendered.getPixels(pixels, 0, 56, 0, 0, 56, 56)
                renderedHashes.add(pixels.contentHashCode())
                val x = (index % 5) * 160
                val y = (index / 5) * 70
                paint.color = Color.WHITE
                canvas.drawRect(x.toFloat(), y.toFloat(), x + 70f, y + 56f, paint)
                paint.color = Color.rgb(28, 34, 44)
                canvas.drawRect(x + 74f, y.toFloat(), x + 144f, y + 56f, paint)
                canvas.drawBitmap(rendered, x + 7f, y.toFloat(), null)
                canvas.drawBitmap(rendered, x + 81f, y.toFloat(), null)
                paint.color = Color.BLACK
                canvas.drawText("%02d".format(index + 1), x + 7f, y + 68f, paint)
                File(output, "image-%02d.png".format(index + 1)).outputStream().use {
                    rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                rendered.recycle()
            }
            check(renderedHashes.size == 50)
            File(output, "android-56px-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            report.appendLine("PASS: 50 distinct native 56px renders, alpha corners, light/dark sheet")

            val tileViews = captures.getValue(ids[0]).latest!!
            runOnMainSync {
                val tile = tileViews.apply(context, null)
                check(tile.contentDescription.toString() == "Open DraftingRoom5")
                check(tile.isClickable)
                check(tile.findViewById<ImageView>(R.id.living_icon_image).importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            }
            val beforeResize = imagePixels(tileViews)
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 120)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 60)
            }
            val prior = captures.getValue(ids[0]).updates
            manager.updateAppWidgetOptions(ids[0], options)
            await { captures.getValue(ids[0]).updates > prior }
            check(beforeResize.contentEquals(imagePixels(captures.getValue(ids[0]).latest!!)))
            val wide = render(tileViews, 120, 60)
            check(Color.alpha(wide.getPixel(1, 30)) == 0 && Color.alpha(wide.getPixel(118, 30)) == 0)
            report.appendLine("PASS: host options callback and rectangular fitCenter preserve artwork; fixed-size picker contract")

            for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED)) {
                val count = captures.getValue(ids[1]).updates
                runOnMainSync { provider.onReceive(context, Intent(action)) }
                await { captures.getValue(ids[1]).updates > count }
            }
            val restoreCount = captures.getValue(ids[1]).updates
            runOnMainSync { provider.onRestored(context, intArrayOf(-99, -98), ids) }
            await { captures.getValue(ids[1]).updates > restoreCount }
            report.appendLine("PASS: boot/package/time/restore callback paths refresh bound host views (simulated callbacks)")
            waitForIdleSync()
            val processes = context.getSystemService(ActivityManager::class.java)
            val previousPid = processes.runningAppProcesses.orEmpty()
                .firstOrNull { it.processName == widgetProcess }?.pid
            if (previousPid != null) {
                check(previousPid != android.os.Process.myPid())
                android.os.Process.killProcess(previousPid)
                await { processes.runningAppProcesses.orEmpty().none { it.pid == previousPid } }
            }
            val coldCount = captures.getValue(ids[0]).updates
            val coldStarted = SystemClock.elapsedRealtime()
            manager.updateAppWidgetOptions(ids[0], Bundle(options).apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 121)
            })
            await { captures.getValue(ids[0]).updates > coldCount }
            check(processes.runningAppProcesses.orEmpty().any {
                it.processName == widgetProcess && it.pid != previousPid
            })
            report.appendLine("PASS: cold private-process framework callback in ${SystemClock.elapsedRealtime() - coldStarted} ms")
            // A new host object must receive the platform's cached views without app-owned state.
            val cached = imagePixels(captures.getValue(ids[1]).latest!!)
            runOnMainSync {
                host.stopListening()
                captures.clear()
                host = newHost()
                host.startListening()
                ids.forEach { host.createView(context, it, info) }
            }
            await { ids.all { captures[it]?.latest != null } }
            check(cached.contentEquals(imagePixels(captures.getValue(ids[1]).latest!!)))
            host.deleteAppWidgetId(ids[0])
            check(!manager.getAppWidgetIds(component).contains(ids[0]))
            runOnMainSync { provider.onUpdate(context, manager, intArrayOf(ids[1])) }
            check(manager.getAppWidgetIds(component).contains(ids[1]))
            report.appendLine("PASS: recreated host receives cached artwork; removing one instance preserves the other")
        } finally {
            runOnMainSync { host.stopListening(); host.deleteHost() }
            check(manager.getAppWidgetIds(component).toSet() == initialIds)
        }
        LivingIconWidgetProvider.refreshIfPresent(context)
        report.appendLine("PASS: audit host removed; original widget IDs preserved; no-widget refresh exercised when initial IDs empty")
    }

    private fun imagePixels(views: RemoteViews): IntArray {
        val bitmap = render(views, 56, 56)
        return IntArray(3136).also { bitmap.getPixels(it, 0, 56, 0, 0, 56, 56); bitmap.recycle() }
    }
    private fun render(views: RemoteViews, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        runOnMainSync {
            val view = views.apply(targetContext, null)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50)
        check(condition()) { "Timed out waiting for native host update" }
    }
    private class CaptureView(context: Context) : AppWidgetHostView(context) {
        @Volatile var latest: RemoteViews? = null
        @Volatile var updates = 0
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)
            if (remoteViews != null) { latest = remoteViews; updates++ }
        }
    }
}
