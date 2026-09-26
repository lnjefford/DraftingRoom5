package dev.draftingroom5.wear

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dev.draftingroom5.watch.WATCH_COMMAND_PATH_PREFIX
import dev.draftingroom5.watch.WATCH_SNAPSHOT_PATH
import dev.draftingroom5.watch.WatchCommand
import dev.draftingroom5.watch.WatchCommandType
import dev.draftingroom5.watch.WatchSnapshot
import dev.draftingroom5.watch.decodeWatchCommands
import dev.draftingroom5.watch.decodeWatchSnapshot
import dev.draftingroom5.watch.encodeWatchCommand
import dev.draftingroom5.watch.encodeWatchCommands
import dev.draftingroom5.watch.encodeWatchSnapshot
import dev.draftingroom5.watch.projectPendingCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

internal class WatchSyncRepository private constructor(context: Context) : DataClient.OnDataChangedListener {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val dataClient = Wearable.getDataClient(appContext)
    private var baseSnapshot: WatchSnapshot? = readSnapshot(preferences)
    private var pendingCommands: List<WatchCommand> = readCommands(preferences)
    private val mutableState = MutableStateFlow(projectPendingCommands(baseSnapshot, pendingCommands))
    val state: StateFlow<WatchSnapshot?> = mutableState

    fun start() {
        reload()
        dataClient.addListener(this)
        resendPending()
        enqueue(WatchCommandType.SYNC)
    }

    fun stop() {
        dataClient.removeListener(this)
    }

    fun startToday() = enqueue(WatchCommandType.START_TODAY)

    fun sync() = enqueue(WatchCommandType.SYNC)

    fun completeSet(exerciseId: String, setNumber: Int) = enqueue(
        type = WatchCommandType.COMPLETE_SET,
        sessionId = mutableState.value?.sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
    )

    fun finishSession() = enqueue(
        type = WatchCommandType.FINISH_SESSION,
        sessionId = mutableState.value?.sessionId,
    )

    fun acceptSnapshot(snapshot: WatchSnapshot) = synchronized(lock) {
        val current = baseSnapshot
        if (current != null && snapshot.generation < current.generation) return@synchronized
        baseSnapshot = snapshot
        pendingCommands = pendingCommands.filterNot { it.id in snapshot.acknowledgedCommandIds }
        persist()
        publish()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WATCH_SNAPSHOT_PATH) {
                    runCatching { decodeWatchSnapshot(checkNotNull(event.dataItem.data)) }.onSuccess(::acceptSnapshot)
                }
            }
        } finally {
            dataEvents.release()
        }
    }

    private fun enqueue(
        type: WatchCommandType,
        sessionId: String? = null,
        exerciseId: String? = null,
        setNumber: Int? = null,
    ) = synchronized(lock) {
        if (type == WatchCommandType.SYNC && pendingCommands.any { it.type == WatchCommandType.SYNC }) return@synchronized
        val command = WatchCommand(
            id = UUID.randomUUID().toString(),
            type = type,
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = setNumber,
            createdAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
        )
        pendingCommands = (pendingCommands + command).takeLast(MAX_PENDING_COMMANDS)
        persist()
        publish()
        send(command)
    }

    private fun resendPending() = synchronized(lock) { pendingCommands.forEach(::send) }

    private fun send(command: WatchCommand) {
        val request = PutDataRequest.create(WATCH_COMMAND_PATH_PREFIX + command.id)
            .setData(encodeWatchCommand(command))
            .setUrgent()
        dataClient.putDataItem(request)
    }

    private fun reload() = synchronized(lock) {
        baseSnapshot = readSnapshot(preferences)
        pendingCommands = readCommands(preferences)
        publish()
    }

    private fun persist() {
        preferences.edit()
            .putString(SNAPSHOT, baseSnapshot?.let { encodeWatchSnapshot(it).toString(Charsets.UTF_8) })
            .putString(COMMANDS, encodeWatchCommands(pendingCommands))
            .apply()
    }

    private fun publish() {
        mutableState.value = projectPendingCommands(baseSnapshot, pendingCommands)
    }

    companion object {
        private const val PREFERENCES = "watch_state"
        private const val SNAPSHOT = "snapshot"
        private const val COMMANDS = "commands"
        private const val MAX_PENDING_COMMANDS = 128
        private val lock = Any()
        @Volatile private var instance: WatchSyncRepository? = null

        fun get(context: Context): WatchSyncRepository = instance ?: synchronized(lock) {
            instance ?: WatchSyncRepository(context).also { instance = it }
        }

        fun storeIncoming(context: Context, snapshot: WatchSnapshot) {
            get(context).acceptSnapshot(snapshot)
        }

        private fun readSnapshot(preferences: SharedPreferences): WatchSnapshot? =
            preferences.getString(SNAPSHOT, null)?.let { encoded ->
                runCatching { decodeWatchSnapshot(encoded.toByteArray(Charsets.UTF_8)) }.getOrNull()
            }

        private fun readCommands(preferences: SharedPreferences): List<WatchCommand> =
            preferences.getString(COMMANDS, null)?.let { runCatching { decodeWatchCommands(it) }.getOrNull() }.orEmpty()
    }
}
