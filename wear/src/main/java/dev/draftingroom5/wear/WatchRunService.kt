package dev.draftingroom5.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import dev.draftingroom5.watch.WatchRunCapture
import dev.draftingroom5.watch.WatchRunPoint
import dev.draftingroom5.watch.WatchRunSample
import dev.draftingroom5.watch.WATCH_TREADMILL_ROUTE_ID
import kotlin.math.roundToInt

/** Watch-owned GPS and interval cues keep working without a connected phone. */
internal class WatchRunService : Service(), LocationListener {
    private val recorder by lazy { WatchRunRecorder.get(this) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val thread = HandlerThread("watch-run")
    private lateinit var handler: Handler
    private var captureId: String? = null
    private val tick = object : Runnable {
        override fun run() {
            val capture = recorder.capture.value
            if (capture == null || capture.id != captureId || !capture.isRunning) { stopSelf(); return }
            val index = capture.intervalAt(System.currentTimeMillis())?.first ?: capture.plan.intervals.size
            if (index > capture.announcedIntervalIndex) {
                recorder.markAnnouncedInterval(index)
                vibrate(index == capture.plan.intervals.size)
            }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread.start()
        handler = Handler(thread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val capture = recorder.capture.value?.takeIf { it.isRunning &&
            (intent?.getStringExtra(EXTRA_ID) == null || it.id == intent.getStringExtra(EXTRA_ID)) }
        if (capture == null) { stopSelf(); return START_NOT_STICKY }
        captureId = capture.id
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Active run", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification(capture), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        handler.removeCallbacks(tick)
        handler.post(tick)
        if (capture.plan.preferredRouteId != WATCH_TREADMILL_ROUTE_ID &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                locationManager.removeUpdates(this)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 2f, this, thread.looper)
            }
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (!location.hasAccuracy() || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        val sample = WatchRunSample(
            WatchRunPoint((location.latitude * 10_000_000).roundToInt(),
                (location.longitude * 10_000_000).roundToInt()),
            location.time.coerceAtLeast(0L), location.accuracy.roundToInt(),
        )
        recorder.record(sample)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { locationManager.removeUpdates(this) }
        thread.quitSafely()
        super.onDestroy()
    }

    private fun vibrate(completed: Boolean) {
        val enabled = WatchRunRepository.get(this).catalog.value?.hapticsEnabled ?: true
        if (!enabled) return
        getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createOneShot(if (completed) 300L else 150L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun notification(capture: WatchRunCapture): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.launcher_five_monochrome)
            .setContentTitle("Run in progress")
            .setContentText(capture.plan.routineName)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "active_watch_run"
        private const val NOTIFICATION_ID = 4202
        private const val EXTRA_ID = "runId"
        fun start(context: Context, id: String) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            context.startForegroundService(Intent(context, WatchRunService::class.java).putExtra(EXTRA_ID, id))
        }
        fun stop(context: Context) { context.stopService(Intent(context, WatchRunService::class.java)) }
    }
}
