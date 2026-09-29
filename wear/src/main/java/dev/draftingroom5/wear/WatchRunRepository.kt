package dev.draftingroom5.wear

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import dev.draftingroom5.watch.WATCH_RUN_ASSET_KEY
import dev.draftingroom5.watch.WATCH_RUN_CATALOG_PATH
import dev.draftingroom5.watch.WatchRunCatalog
import dev.draftingroom5.watch.decodeWatchRunCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** A route catalog is persisted on the watch before it is offered for offline running. */
internal class WatchRunRepository private constructor(context: Context) : DataClient.OnDataChangedListener {
    private val appContext = context.applicationContext
    private val client = Wearable.getDataClient(appContext)
    private val file = AtomicFile(File(appContext.filesDir, "run-catalog.json"))
    private val worker = Executors.newSingleThreadExecutor()
    private val mutableCatalog = MutableStateFlow(readCatalog())
    val catalog: StateFlow<WatchRunCatalog?> = mutableCatalog

    fun start() {
        client.addListener(this)
        client.getDataItems(Uri.parse("wear://*$WATCH_RUN_CATALOG_PATH")).addOnSuccessListener { items ->
            try { items.forEach { acceptDataItem(it.freeze()) } } finally { items.release() }
        }
    }

    fun stop() { client.removeListener(this) }

    override fun onDataChanged(events: DataEventBuffer) {
        try {
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_RUN_CATALOG_PATH) {
                    acceptDataItem(event.dataItem.freeze())
                }
            }
        } finally { events.release() }
    }

    fun acceptDataItem(item: DataItem) {
        val asset = item.assets[WATCH_RUN_ASSET_KEY] ?: return
        client.getFdForAsset(asset).addOnSuccessListener { response ->
            worker.execute {
                try {
                    val bytes = response.inputStream.use { readBounded(it) }
                    val decoded = decodeWatchRunCatalog(bytes)
                    synchronized(lock) {
                        if (decoded.generation < (mutableCatalog.value?.generation ?: 0)) return@synchronized
                        val output = file.startWrite()
                        try {
                            output.write(bytes)
                            file.finishWrite(output)
                            mutableCatalog.value = decoded
                        } catch (error: Throwable) {
                            file.failWrite(output)
                        }
                    }
                } catch (_: Exception) {
                    // Retain the last usable offline catalog.
                } finally { response.release() }
            }
        }
    }

    private fun readCatalog(): WatchRunCatalog? = runCatching {
        file.openRead().use { decodeWatchRunCatalog(readBounded(it)) }
    }.getOrNull()

    companion object {
        private const val MAX_BYTES = 16 * 1024 * 1024
        private val lock = Any()
        @Volatile private var instance: WatchRunRepository? = null

        fun get(context: Context): WatchRunRepository = instance ?: synchronized(lock) {
            instance ?: WatchRunRepository(context).also { instance = it }
        }

        private fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "Run catalog is too large." }
                output.write(chunk, 0, count)
            }
            return output.toByteArray()
        }
    }
}
