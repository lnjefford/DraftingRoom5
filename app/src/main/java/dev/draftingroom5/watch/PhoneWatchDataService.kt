package dev.draftingroom5.watch

import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks
import dev.draftingroom5.AppDocument
import dev.draftingroom5.AppRepository
import dev.draftingroom5.DashboardSession
import dev.draftingroom5.DurableSessionState
import dev.draftingroom5.GuidedSession
import dev.draftingroom5.LoadState
import dev.draftingroom5.RoutineExecution
import dev.draftingroom5.SessionAction
import dev.draftingroom5.SessionEvent
import dev.draftingroom5.SessionRepositoryResult
import dev.draftingroom5.RepositoryResult
import dev.draftingroom5.dashboardSessions
import dev.draftingroom5.durableState
import dev.draftingroom5.TodayLocationPreferences
import dev.draftingroom5.TodayPreferences
import dev.draftingroom5.TodayWeatherSource
import dev.draftingroom5.TodayMarketSource
import dev.draftingroom5.TodayTeamSource
import dev.draftingroom5.todayWeek
import java.time.LocalDate
import java.time.Instant
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/** Phone-side authority for the compact, private watch protocol. */
class PhoneWatchDataService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val commands = mutableListOf<Pair<WatchCommand, Uri>>()
        val runResults = mutableListOf<Pair<com.google.android.gms.wearable.DataItemAsset, String>>()
        try {
            dataEvents.forEach { event ->
                val item = event.dataItem
                if (event.type == DataEvent.TYPE_CHANGED && item.uri.path?.startsWith(WATCH_COMMAND_PATH_PREFIX) == true) {
                    runCatching { decodeWatchCommand(checkNotNull(item.data)) }
                        .onSuccess { commands += it to item.uri }
                }
                if (event.type == DataEvent.TYPE_CHANGED && item.uri.path?.startsWith(WATCH_RUN_RESULT_PATH_PREFIX) == true) {
                    val id = item.uri.path?.removePrefix(WATCH_RUN_RESULT_PATH_PREFIX).orEmpty()
                    item.assets[WATCH_RUN_ASSET_KEY]?.let { asset -> runResults.add(asset to id) }
                }
            }
        } finally {
            dataEvents.release()
        }
        commands.sortedBy { it.first.createdAtMillis }.forEach { (command, uri) -> process(command, uri) }
        runResults.forEach { (asset, id) -> runCatching { importRun(asset, id) } }
    }

    private fun importRun(asset: com.google.android.gms.wearable.DataItemAsset, pathId: String) {
        val response = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset), 30, TimeUnit.SECONDS)
        val bytes = try {
            response.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 16 * 1024 * 1024) { "Run result is too large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { response.release() }
        val result = decodeWatchRunResult(bytes)
        require(result.id == pathId)
        val repository = AppRepository.get(applicationContext)
        check(repository.ensureLoaded() is LoadState.Ready)
        when (val imported = repository.importWatchRun(result)) {
            is RepositoryResult.Success -> {
                Wearable.getDataClient(this).putDataItem(PutDataRequest.create(WATCH_RUN_ACK_PATH_PREFIX + result.id)
                    .setData(result.id.toByteArray(Charsets.UTF_8)).setUrgent())
                runCatching { publishWatchRunCatalog(this, imported.value) }
            }
            is RepositoryResult.Invalid -> throw imported.error
            is RepositoryResult.Failed -> throw imported.error
            is RepositoryResult.Conflict -> error("Run import changed concurrently.")
        }
    }

    private fun process(command: WatchCommand, commandUri: Uri) = synchronized(processLock) {
        val acknowledgements = acknowledgedIds().toMutableSet()
        if (command.id !in acknowledgements) {
            val repository = AppRepository.get(applicationContext)
            if (runCatching {
                check(repository.ensureLoaded() is LoadState.Ready) { "Phone data is unavailable." }
                applyCommand(repository, command)
            }.isSuccess) {
                acknowledgements += command.id
                saveAcknowledgedIds(acknowledgements)
            }
        }
        val cachedToday = readToday()
        if (command.type == WatchCommandType.REFRESH_TODAY || cachedToday == null ||
            (command.type == WatchCommandType.SYNC &&
                System.currentTimeMillis() - cachedToday.updatedAtMillis >= TODAY_REFRESH_MILLIS)) {
            runCatching { refreshToday() }
        }
        val snapshot = currentSnapshot(
            acknowledgements,
            completedSessionId = command.sessionId.takeIf { command.type == WatchCommandType.FINISH_SESSION },
        ).copy(today = readToday())
        val request = PutDataRequest.create(WATCH_SNAPSHOT_PATH)
            .setData(encodeWatchSnapshot(snapshot))
            .setUrgent()
        Wearable.getDataClient(this).putDataItem(request).addOnSuccessListener {
            if (command.id in acknowledgements) Wearable.getDataClient(this).deleteDataItems(commandUri)
        }
        (AppRepository.get(applicationContext).state.value as? LoadState.Ready)?.value?.let { document ->
            runCatching { publishWatchRunCatalog(this, document) }
        }
    }

    private fun applyCommand(repository: AppRepository, command: WatchCommand) {
        when (command.type) {
            WatchCommandType.SYNC, WatchCommandType.REFRESH_TODAY -> Unit
            WatchCommandType.START_TODAY -> ensureTodaySession(repository)
            WatchCommandType.COMPLETE_SET -> {
                val session = findSession(repository, command.sessionId) ?: ensureTodaySession(repository) ?: return
                val exerciseId = command.exerciseId ?: return
                val setNumber = command.setNumber ?: return
                val completed = session.completedSets[exerciseId] ?: return
                if (completed >= setNumber) return
                if (setNumber != completed + 1) return
                repository.sessionLease()?.let { lease ->
                    when (val result = repository.applySessionEvent(
                        lease,
                        session.id,
                        session.eventRevision,
                        SessionEvent.CompleteSet(exerciseId, setNumber),
                    )) {
                        is SessionRepositoryResult.Failed -> throw result.error
                        SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
                        else -> Unit
                    }
                }
            }
            WatchCommandType.FINISH_SESSION -> {
                val session = findSession(repository, command.sessionId) ?: return
                if (session.durableState() != DurableSessionState.READY_TO_FINISH) return
                repository.sessionLease()?.let { lease ->
                    when (val result = repository.finishGuidedSession(lease, session.id, session.eventRevision)) {
                        is SessionRepositoryResult.Failed -> throw result.error
                        SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun ensureTodaySession(repository: AppRepository): GuidedSession? {
        findSession(repository, null)?.let { return it }
        val document = (repository.state.value as? LoadState.Ready)?.value ?: return null
        val candidate = todayCandidate(document) ?: return null
        if (candidate.action == SessionAction.RESUME) {
            return document.partialSessions.firstOrNull { it.occurrence == candidate.occurrence }
        }
        val lease = repository.sessionLease() ?: return null
        return when (val result = repository.openGuidedSession(
            lease = lease,
            occurrence = candidate.occurrence,
            displayedRoutineId = candidate.routine.id,
            displayedRoutineRevision = candidate.routine.revision,
            newSessionId = UUID.randomUUID().toString(),
        )) {
            is SessionRepositoryResult.Partial -> result.session
            is SessionRepositoryResult.Conflict -> result.current
            is SessionRepositoryResult.Failed -> throw result.error
            SessionRepositoryResult.RefreshRequired -> error("Session lease expired.")
            else -> null
        }
    }

    private fun findSession(repository: AppRepository, requestedId: String?): GuidedSession? {
        val document = (repository.state.value as? LoadState.Ready)?.value ?: return null
        return if (requestedId != null) {
            document.partialSessions.firstOrNull { it.id == requestedId }
        } else {
            document.partialSessions.filter { it.effectiveDate == LocalDate.now() }.maxByOrNull { it.updatedAtMillis }
        }
    }

    private fun currentSnapshot(acknowledgements: Set<String>, completedSessionId: String? = null): WatchSnapshot {
        val repository = AppRepository.get(applicationContext)
        val document = (repository.ensureLoaded() as? LoadState.Ready)?.value
            ?: return emptySnapshot(WatchWorkoutStatus.ERROR, acknowledgements, "Phone data is unavailable.")
        val today = LocalDate.now()
        val preferredHistory = completedSessionId?.let { id -> document.history.firstOrNull { it.id == id } }
        if (preferredHistory != null) return historySnapshot(document, preferredHistory, acknowledgements)
        val session = document.partialSessions.filter { it.effectiveDate == today }.maxByOrNull { it.updatedAtMillis }
        if (session != null) return sessionSnapshot(document, session, acknowledgements)
        val candidate = todayCandidate(document)
        if (candidate != null) return routineSnapshot(document, candidate, acknowledgements)
        val todayHistory = document.history.filter { it.effectiveDate == today }.maxByOrNull { it.completedAtMillis }
        if (todayHistory != null && todayHistory.snapshot.execution == RoutineExecution.GUIDED) {
            return historySnapshot(document, todayHistory, acknowledgements)
        }
        return emptySnapshot(WatchWorkoutStatus.NONE, acknowledgements, "No guided workout today.", document.generation)
    }

    private fun todayCandidate(document: AppDocument): DashboardSession? =
        dashboardSessions(document, LocalDate.now())
            .filter { it.routine.execution == RoutineExecution.GUIDED && it.action != SessionAction.DONE }
            .sortedBy { if (it.action == SessionAction.RESUME) 0 else 1 }
            .firstOrNull()

    private fun readToday(): WatchTodayBriefing? = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        .getString(TODAY_BRIEFING, null)
        ?.let { runCatching { decodeWatchTodayBriefing(it) }.getOrNull() }
        ?.takeIf { it.date == LocalDate.now().toString() }

    private fun refreshToday() {
        val document = (AppRepository.get(applicationContext).ensureLoaded() as? LoadState.Ready)?.value ?: return
        val date = LocalDate.now()
        val sessions = dashboardSessions(document, date)
        val week = todayWeek(document, date)
        val previous = readToday()
        val favorites = TodayPreferences(this)
        val symbols = favorites.symbols()
        val teams = favorites.teams()
        val city = TodayLocationPreferences(this).selectedCity()
        val (weather, stocks, games) = runBlocking {
            supervisorScope {
                val weatherJob = async {
                    withTimeoutOrNull(10_000) {
                        val target = city ?: TodayWeatherSource.currentLocation(this@PhoneWatchDataService)?.let {
                            dev.draftingroom5.TodayCity("Current location", it.latitude, it.longitude)
                        }
                        target?.let { runCatching { TodayWeatherSource.fetch(it.latitude, it.longitude, it.name) }.getOrNull() }
                    }
                }
                val stockJobs = symbols.map { symbol -> async {
                    withTimeoutOrNull(10_000) { runCatching { TodayMarketSource.fetch(symbol) }.getOrNull() }
                } }
                val gameJobs = teams.map { team -> async {
                    withTimeoutOrNull(10_000) { runCatching { TodayTeamSource.fetch(team, date, Instant.now()) }.getOrNull() }
                } }
                Triple(weatherJob.await(), stockJobs.awaitAll(), gameJobs.awaitAll())
            }
        }
        val briefing = WatchTodayBriefing(
            date = date.toString(),
            workoutName = sessions.firstOrNull()?.routine?.name,
            workoutCount = sessions.size,
            weekPlanned = week.sumOf { it.planned },
            weekCompleted = week.sumOf { it.completed },
            weekDays = week.map { it.planned to it.completed },
            location = weather?.location ?: previous?.location,
            temperatureF = weather?.temperatureF ?: previous?.temperatureF,
            weatherKind = weather?.kind?.name ?: previous?.weatherKind,
            stocks = symbols.mapIndexed { index, symbol ->
                val quote = stocks[index]
                val old = previous?.stocks?.firstOrNull { it.symbol == symbol }
                WatchTodayStock(symbol, quote?.price ?: old?.price, quote?.changePercent ?: old?.changePercent)
            },
            games = teams.mapIndexed { index, team ->
                val game = games[index]
                val old = previous?.games?.firstOrNull { it.team == team.displayName }
                WatchTodayGame(team.displayName, game?.opponent ?: old?.opponent,
                    game?.startsAt?.toEpochMilli() ?: old?.startsAtMillis)
            },
            updatedAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
        )
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(TODAY_BRIEFING, encodeWatchTodayBriefing(briefing)).apply()
    }

    private fun routineSnapshot(
        document: AppDocument,
        candidate: DashboardSession,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = WatchWorkoutStatus.AVAILABLE,
        generation = document.generation,
        eventRevision = 0,
        sessionId = null,
        routineId = candidate.routine.id,
        routineName = candidate.routine.name,
        scheduleEntryId = candidate.occurrence.scheduleEntryId,
        scheduledDate = candidate.occurrence.scheduledDate.toString(),
        focusedExerciseId = candidate.routine.exercises.first().id,
        exercises = candidate.routine.exercises.map { it.toWatchExercise(0) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
    )

    private fun sessionSnapshot(
        document: AppDocument,
        session: GuidedSession,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = if (session.durableState() == DurableSessionState.READY_TO_FINISH) {
            WatchWorkoutStatus.READY_TO_FINISH
        } else WatchWorkoutStatus.ACTIVE,
        generation = document.generation,
        eventRevision = session.eventRevision,
        sessionId = session.id,
        routineId = session.routineId,
        routineName = session.snapshot.name,
        scheduleEntryId = session.occurrence.scheduleEntryId,
        scheduledDate = session.occurrence.scheduledDate.toString(),
        focusedExerciseId = session.focusedExerciseId,
        exercises = session.snapshot.exercises.map { it.toWatchExercise(session.completedSets.getValue(it.id)) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = session.updatedAtMillis,
    )

    private fun dev.draftingroom5.Exercise.toWatchExercise(completed: Int) = WatchExercise(
        id, name, notes, setCount, reps, durationSeconds, weightPounds, artworkId, completed,
    )

    private fun historySnapshot(
        document: AppDocument,
        history: dev.draftingroom5.WorkoutHistoryEntry,
        acknowledgements: Set<String>,
    ) = WatchSnapshot(
        status = WatchWorkoutStatus.COMPLETE,
        generation = document.generation,
        eventRevision = 0,
        sessionId = history.id,
        routineId = history.snapshot.id,
        routineName = history.snapshot.name,
        scheduleEntryId = history.occurrence.scheduleEntryId,
        scheduledDate = history.occurrence.scheduledDate.toString(),
        focusedExerciseId = history.snapshot.exercises.lastOrNull()?.id,
        exercises = history.snapshot.exercises.map { it.toWatchExercise(it.setCount) },
        hapticsEnabled = document.preferences.hapticsEnabled,
        acknowledgedCommandIds = acknowledgements,
        updatedAtMillis = history.completedAtMillis,
    )

    private fun emptySnapshot(
        status: WatchWorkoutStatus,
        acknowledgements: Set<String>,
        message: String,
        generation: Long = 0,
    ) = WatchSnapshot(
        status = status,
        generation = generation,
        eventRevision = 0,
        sessionId = null,
        routineId = null,
        routineName = null,
        scheduleEntryId = null,
        scheduledDate = null,
        focusedExerciseId = null,
        exercises = emptyList(),
        hapticsEnabled = false,
        acknowledgedCommandIds = acknowledgements,
        message = message,
        updatedAtMillis = System.currentTimeMillis().coerceAtLeast(0L),
    )

    private fun acknowledgedIds(): Set<String> = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        .getStringSet(ACKNOWLEDGED, emptySet()).orEmpty()

    private fun saveAcknowledgedIds(ids: Set<String>) {
        val retained = ids.toList().takeLast(MAX_ACKNOWLEDGEMENTS).toSet()
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putStringSet(ACKNOWLEDGED, retained).apply()
    }

    private companion object {
        const val PREFERENCES = "watch_sync"
        const val ACKNOWLEDGED = "acknowledged_commands"
        const val TODAY_BRIEFING = "today_briefing"
        const val MAX_ACKNOWLEDGEMENTS = 128
        const val TODAY_REFRESH_MILLIS = 15 * 60 * 1000L
        val processLock = Any()
    }
}
