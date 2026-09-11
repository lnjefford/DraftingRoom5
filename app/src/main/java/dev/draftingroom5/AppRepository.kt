package dev.draftingroom5

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Corrupt(val error: Throwable) : LoadState<Nothing>
    data class Failed(val error: IOException) : LoadState<Nothing>
}

internal sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Conflict(val currentGeneration: Long) : RepositoryResult<Nothing>
    data class Invalid(val error: IllegalArgumentException) : RepositoryResult<Nothing>
    data class Failed(val error: IOException) : RepositoryResult<Nothing>
}

internal interface DocumentStorage {
    fun exists(): Boolean
    @Throws(IOException::class) fun read(): String
    @Throws(IOException::class) fun write(value: String)
}

internal class AppDocumentStore(context: Context) : DocumentStorage {
    private val file = AtomicFile(File(context.filesDir, "training-current/document.json"))

    override fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    @Throws(IOException::class)
    override fun read(): String = file.openRead().use(::readBoundedCurrentJson)

    @Throws(IOException::class)
    override fun write(value: String) {
        file.baseFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create the current-data directory.")
        }
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.flush()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            if (error is IOException) throw error
            throw IOException("Could not write app data.", error)
        }
    }
}

internal class AppRepository(private val store: DocumentStorage) {
    private val mutableState = MutableStateFlow<LoadState<AppDocument>>(LoadState.Loading)
    val state: StateFlow<LoadState<AppDocument>> = mutableState

    fun load(): LoadState<AppDocument> = synchronized(processLock) {
        val loaded = try {
            if (!store.exists()) {
                defaultAppDocument().also { store.write(encodeAppDocument(it)) }
            } else {
                val encoded = store.read()
                decodeAppDocument(encoded).also { normalized ->
                    val normalizedEncoded = encodeAppDocument(normalized)
                    if (normalizedEncoded != encoded) store.write(normalizedEncoded)
                }
            }
        } catch (error: FileNotFoundException) {
            return@synchronized LoadState.Failed(error).also { mutableState.value = it }
        } catch (error: IOException) {
            return@synchronized LoadState.Failed(error).also { mutableState.value = it }
        } catch (error: IllegalArgumentException) {
            return@synchronized LoadState.Corrupt(error).also { mutableState.value = it }
        }
        LoadState.Ready(loaded).also { mutableState.value = it }
    }

    fun currentOrDefaults(): AppDocument = (state.value as? LoadState.Ready)?.value
        ?: (load() as? LoadState.Ready)?.value
        ?: defaultAppDocument()

    fun update(expectedGeneration: Long, transform: (AppDocument) -> AppDocument): RepositoryResult<AppDocument> = synchronized(processLock) {
        val current = (mutableState.value as? LoadState.Ready)?.value
            ?: return@synchronized RepositoryResult.Invalid(IllegalArgumentException("App data is not ready."))
        if (current.generation != expectedGeneration) return@synchronized RepositoryResult.Conflict(current.generation)
        val candidate: AppDocument
        val encoded: String
        try {
            candidate = transform(current).copy(generation = current.generation + 1)
            encoded = encodeAppDocument(candidate)
        } catch (error: IllegalArgumentException) {
            return@synchronized RepositoryResult.Invalid(error)
        }
        try {
            store.write(encoded)
        } catch (error: IOException) {
            return@synchronized RepositoryResult.Failed(error)
        }
        mutableState.value = LoadState.Ready(candidate)
        RepositoryResult.Success(candidate)
    }

    fun restore(document: AppDocument): RepositoryResult<AppDocument> = synchronized(processLock) {
        val currentGeneration = (mutableState.value as? LoadState.Ready)?.value?.generation ?: 0L
        val candidate: AppDocument
        val encoded: String
        try {
            validateAppDocument(document)
            candidate = document.copy(generation = currentGeneration + 1,
                partialSessions = document.partialSessions.map { it.copy(timer = SessionTimer()) })
            encoded = encodeAppDocument(candidate)
        } catch (error: IllegalArgumentException) {
            return@synchronized RepositoryResult.Invalid(error)
        }
        try {
            store.write(encoded)
        } catch (error: IOException) {
            return@synchronized RepositoryResult.Failed(error)
        }
        mutableState.value = LoadState.Ready(candidate)
        RepositoryResult.Success(candidate)
    }

    fun resetToDefaults(): RepositoryResult<AppDocument> = restore(defaultAppDocument())

    fun replacePlan(expectedGeneration: Long, plan: TrainingPlan, acknowledgeSavedSessions: Boolean = false) = update(expectedGeneration) { current ->
        validatePlanEdit(current.plan, plan)
        val changedIds = current.plan.routines.filter { old -> plan.routines.firstOrNull { it.id == old.id } != old }.map { it.id }.toSet()
        require(acknowledgeSavedSessions || current.partialSessions.none { it.routineId in changedIds }) {
            "Confirm changes to routines with saved sessions before saving."
        }
        current.copy(plan = plan, partialSessions = current.partialSessions.filter { session -> plan.routines.any { it.id == session.routineId } })
    }

    fun deleteRoutine(expectedGeneration: Long, routineId: String) = update(expectedGeneration) {
        require(it.plan.routines.any { routine -> routine.id == routineId }) { "Routine does not exist." }
        it.copy(plan = it.plan.removeRoutine(routineId),
            partialSessions = it.partialSessions.filterNot { session -> session.routineId == routineId })
    }

    fun deleteScheduleEntry(expectedGeneration: Long, scheduleEntryId: String) = update(expectedGeneration) {
        require(it.plan.schedule.any { entry -> entry.id == scheduleEntryId }) { "Scheduled item does not exist." }
        it.copy(plan = it.plan.removeScheduleEntry(scheduleEntryId))
    }

    fun resetPlan(expectedGeneration: Long) = update(expectedGeneration) {
        it.copy(plan = defaultTrainingPlan(), partialSessions = emptyList(), history = emptyList())
    }

    companion object {
        private val processLock = Any()
        @Volatile private var instance: AppRepository? = null

        // Activity, settings, backup managers and workers observe the same committed generation.
        fun get(context: Context): AppRepository = instance ?: synchronized(processLock) {
            instance ?: AppRepository(AppDocumentStore(context.applicationContext)).also { instance = it }
        }
    }
}
