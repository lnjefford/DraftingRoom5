package dev.draftingroom5.wear

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dev.draftingroom5.watch.WATCH_RUN_ASSET_KEY
import dev.draftingroom5.watch.WATCH_RUN_RESULT_PATH_PREFIX
import dev.draftingroom5.watch.WatchRunCapture
import dev.draftingroom5.watch.WatchRunPlan
import dev.draftingroom5.watch.WatchRunResult
import dev.draftingroom5.watch.WatchRunRoute
import dev.draftingroom5.watch.WatchRunSample
import dev.draftingroom5.watch.decodeWatchRunCapture
import dev.draftingroom5.watch.decodeWatchRunResult
import dev.draftingroom5.watch.encodeWatchRunCapture
import dev.draftingroom5.watch.encodeWatchRunResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/** Watch-owned run journal. Pending uploads survive process death and disconnection. */
internal class WatchRunRecorder private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val captureFile = AtomicFile(File(appContext.filesDir, "active-run.json"))
    private val pendingDirectory = File(appContext.filesDir, "pending-runs")
    private val client = Wearable.getDataClient(appContext)
    private val mutableCapture = MutableStateFlow(readCapture())
    val capture: StateFlow<WatchRunCapture?> = mutableCapture
    private val mutablePendingCount = MutableStateFlow(countPending())
    val pendingCount: StateFlow<Int> = mutablePendingCount

    fun start(plan: WatchRunPlan, route: WatchRunRoute?, now: Long): WatchRunCapture? = synchronized(lock) {
        if (mutableCapture.value?.completedAtMillis == null && mutableCapture.value != null) return@synchronized null
        val next = WatchRunCapture(UUID.randomUUID().toString(), plan, route, now, 0, now, 0, emptyList())
        if (writeCapture(next)) next else null
    }

    fun pause(now: Long) = change { it.pause(now) }
    fun resume(now: Long) = change { it.resume(now) }
    fun record(sample: WatchRunSample) = change { it.record(sample) }
    fun markAnnouncedInterval(index: Int) = change { it.copy(announcedIntervalIndex = index) }

    fun finish(now: Long): WatchRunCapture? = synchronized(lock) {
        val current = mutableCapture.value ?: return@synchronized null
        val finished = current.finish(now)
        val result = finished.result() ?: return@synchronized null
        if (!savePending(result) || !writeCapture(finished)) return@synchronized null
        mutablePendingCount.value = countPending()
        publish(result)
        finished
    }

    fun resendPending() {
        pendingDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach { file ->
            runCatching { decodeWatchRunResult(file.readBytes()) }.onSuccess(::publish)
        }
    }

    fun acknowledge(id: String) {
        if (!UUID_PATTERN.matches(id)) return
        File(pendingDirectory, "$id.json").delete()
        mutablePendingCount.value = countPending()
        client.deleteDataItems(Uri.parse("wear://*$WATCH_RUN_RESULT_PATH_PREFIX$id"))
    }

    private fun change(transform: (WatchRunCapture) -> WatchRunCapture): WatchRunCapture? = synchronized(lock) {
        val current = mutableCapture.value ?: return@synchronized null
        val updated = transform(current)
        if (updated == current || writeCapture(updated)) updated else null
    }

    private fun writeCapture(capture: WatchRunCapture): Boolean = runCatching {
        val bytes = encodeWatchRunCapture(capture)
        val output = captureFile.startWrite()
        try {
            output.write(bytes)
            captureFile.finishWrite(output)
            mutableCapture.value = capture
        } catch (error: Throwable) {
            captureFile.failWrite(output)
            throw error
        }
    }.isSuccess

    private fun readCapture(): WatchRunCapture? = runCatching {
        captureFile.openRead().use { decodeWatchRunCapture(it.readBytes()) }
    }.getOrNull()

    private fun savePending(result: WatchRunResult): Boolean = runCatching {
        if (!pendingDirectory.exists()) check(pendingDirectory.mkdirs())
        val file = AtomicFile(File(pendingDirectory, "${result.id}.json"))
        val output = file.startWrite()
        try {
            output.write(encodeWatchRunResult(result))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }.isSuccess

    private fun publish(result: WatchRunResult) {
        val request = PutDataRequest.create(WATCH_RUN_RESULT_PATH_PREFIX + result.id)
            .setData(result.id.toByteArray(Charsets.UTF_8))
            .putAsset(WATCH_RUN_ASSET_KEY, Asset.createFromBytes(encodeWatchRunResult(result)))
            .setUrgent()
        client.putDataItem(request)
    }

    private fun countPending(): Int = pendingDirectory.listFiles()?.count { it.isFile && it.name.endsWith(".json") } ?: 0

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
        private val lock = Any()
        @Volatile private var instance: WatchRunRecorder? = null
        fun get(context: Context): WatchRunRecorder = instance ?: synchronized(lock) {
            instance ?: WatchRunRecorder(context).also { instance = it }
        }
    }
}
