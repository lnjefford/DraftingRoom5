package dev.draftingroom5

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
import android.os.IBinder
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** The foreground recorder owns GPS while a phone run is active and survives screen-off. */
internal class RunLocationService : Service(), LocationListener {
    private val repository by lazy { AppRepository.get(this) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val locationThread = HandlerThread("run-location")
    private lateinit var handler: Handler
    private val haptics by lazy { WorkoutHaptics(this) }
    private var sessionId: String? = null
    private var announcedIntervalIndex = 0
    private val tick = object : Runnable {
        override fun run() {
            val active = (repository.state.value as? LoadState.Ready)?.value?.runSessions
                ?.firstOrNull { it.id == sessionId && it.isRunning }
            if (active == null) { stopSelf(); return }
            val index = active.intervalAt(System.currentTimeMillis())?.first ?: checkNotNull(active.routine.run).intervals.size
            if (index > announcedIntervalIndex) {
                announcedIntervalIndex = index
                val enabled = (repository.state.value as? LoadState.Ready)?.value?.preferences?.hapticsEnabled ?: true
                haptics.perform(HapticCue.TIMER_COMPLETE, enabled)
            }
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationThread.start()
        handler = Handler(locationThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val document = (repository.ensureLoaded() as? LoadState.Ready)?.value
        val active = document?.runSessions?.firstOrNull { it.id == intent?.getStringExtra(EXTRA_SESSION_ID) && it.isRunning }
            ?: document?.runSessions?.firstOrNull { it.isRunning }
        if (active == null || !hasPermission()) { stopSelf(); return START_NOT_STICKY }
        sessionId = active.id
        announcedIntervalIndex = active.intervalAt(System.currentTimeMillis())?.first ?: checkNotNull(active.routine.run).intervals.size
        val channel = NotificationChannel(CHANNEL_ID, "Active run", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(NOTIFICATION_ID, notification(active), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        handler.removeCallbacks(tick)
        handler.post(tick)
        try {
            locationManager.removeUpdates(this)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 2f, this, locationThread.looper)
            } else stopSelf()
        } catch (_: SecurityException) { stopSelf() }
        catch (_: IllegalArgumentException) { stopSelf() }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val id = sessionId ?: return
        if (!location.hasAccuracy() || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        val sample = RunLocationSample(
            point = RunRoutePoint((location.latitude * 10_000_000).roundToInt(), (location.longitude * 10_000_000).roundToInt()),
            recordedAtMillis = location.time.coerceAtLeast(0L),
            accuracyMeters = location.accuracy.roundToInt(),
        )
        when (repository.recordRunLocation(id, sample)) {
            is RepositoryResult.Success -> {
                val active = (repository.state.value as? LoadState.Ready)?.value?.runSessions
                    ?.firstOrNull { it.id == id && it.isRunning }
                if (active == null) stopSelf()
            }
            else -> stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { locationManager.removeUpdates(this) }
        locationThread.quitSafely()
        super.onDestroy()
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun notification(session: RunSession): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Run in progress")
            .setContentText(session.routine.name)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "active_run"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_SESSION_ID = "runSessionId"

        fun start(context: Context, sessionId: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, RunLocationService::class.java).putExtra(EXTRA_SESSION_ID, sessionId))
        }

        fun stop(context: Context) { context.stopService(Intent(context, RunLocationService::class.java)) }
    }
}
