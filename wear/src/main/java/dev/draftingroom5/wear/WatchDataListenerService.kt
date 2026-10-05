package dev.draftingroom5.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dev.draftingroom5.watch.WATCH_SNAPSHOT_PATH
import dev.draftingroom5.watch.decodeWatchSnapshot
import dev.draftingroom5.watch.WATCH_RUN_CATALOG_PATH
import dev.draftingroom5.watch.WATCH_RUN_ACK_PATH_PREFIX

class WatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_SNAPSHOT_PATH) {
                    WatchSyncRepository.get(applicationContext).acceptDataItem(event.dataItem.freeze())
                }
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_RUN_CATALOG_PATH) {
                    WatchRunRepository.get(applicationContext).acceptDataItem(event.dataItem.freeze())
                }
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path?.startsWith(WATCH_RUN_ACK_PATH_PREFIX) == true) {
                    val id = event.dataItem.uri.path?.removePrefix(WATCH_RUN_ACK_PATH_PREFIX).orEmpty()
                    WatchRunRecorder.get(applicationContext).acknowledge(id)
                }
            }
        } finally {
            dataEvents.release()
        }
    }
}
