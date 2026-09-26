package dev.draftingroom5.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dev.draftingroom5.watch.WATCH_SNAPSHOT_PATH
import dev.draftingroom5.watch.decodeWatchSnapshot

class WatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_SNAPSHOT_PATH) {
                    runCatching { decodeWatchSnapshot(checkNotNull(event.dataItem.data)) }
                        .onSuccess { WatchSyncRepository.storeIncoming(applicationContext, it) }
                }
            }
        } finally {
            dataEvents.release()
        }
    }
}
