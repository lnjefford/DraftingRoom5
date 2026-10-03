package dev.draftingroom5.watch

import org.json.JSONArray
import org.json.JSONObject

const val WATCH_SNAPSHOT_PATH = "/draftingroom5/phone/snapshot"
const val WATCH_COMMAND_PATH_PREFIX = "/draftingroom5/watch/command/"
const val WATCH_PROTOCOL_VERSION = 1

enum class WatchWorkoutStatus { NONE, AVAILABLE, ACTIVE, READY_TO_FINISH, COMPLETE, ERROR }
enum class WatchCommandType { SYNC, REFRESH_TODAY, START_TODAY, COMPLETE_SET, FINISH_SESSION }

data class WatchExercise(
    val id: String,
    val name: String,
    val notes: String,
    val setCount: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val weightPounds: Int?,
    val artworkId: String,
    val completedSets: Int,
)

data class WatchTodayStock(val symbol: String, val price: Double?, val changePercent: Double?,
    val points: List<Double> = emptyList())
data class WatchTodayGame(val team: String, val opponent: String?, val startsAtMillis: Long?)
data class WatchTodayBriefing(
    val date: String,
    val workoutName: String?,
    val workoutCount: Int,
    val weekPlanned: Int,
    val weekCompleted: Int,
    val weekDays: List<Pair<Int, Int>>,
    val location: String?,
    val temperatureF: Int?,
    val weatherKind: String?,
    val stocks: List<WatchTodayStock>,
    val games: List<WatchTodayGame>,
    val updatedAtMillis: Long,
)

data class WatchSnapshot(
    val status: WatchWorkoutStatus,
    val generation: Long,
    val eventRevision: Long,
    val sessionId: String?,
    val routineId: String?,
    val routineName: String?,
    val scheduleEntryId: String?,
    val scheduledDate: String?,
    val focusedExerciseId: String?,
    val exercises: List<WatchExercise>,
    val hapticsEnabled: Boolean = true,
    val acknowledgedCommandIds: Set<String> = emptySet(),
    val message: String? = null,
    val updatedAtMillis: Long,
    val today: WatchTodayBriefing? = null,
) {
    val focusedExercise: WatchExercise?
        get() = exercises.firstOrNull { it.id == focusedExerciseId } ?: exercises.firstOrNull()
}

data class WatchCommand(
    val id: String,
    val type: WatchCommandType,
    val sessionId: String? = null,
    val exerciseId: String? = null,
    val setNumber: Int? = null,
    val createdAtMillis: Long,
)

fun encodeWatchSnapshot(snapshot: WatchSnapshot): ByteArray = JSONObject().apply {
    put("protocol", WATCH_PROTOCOL_VERSION)
    put("status", snapshot.status.name)
    put("generation", snapshot.generation)
    put("eventRevision", snapshot.eventRevision)
    putNullable("sessionId", snapshot.sessionId)
    putNullable("routineId", snapshot.routineId)
    putNullable("routineName", snapshot.routineName)
    putNullable("scheduleEntryId", snapshot.scheduleEntryId)
    putNullable("scheduledDate", snapshot.scheduledDate)
    putNullable("focusedExerciseId", snapshot.focusedExerciseId)
    put("exercises", JSONArray().apply { snapshot.exercises.forEach { put(encodeExercise(it)) } })
    put("hapticsEnabled", snapshot.hapticsEnabled)
    put("acknowledgedCommandIds", JSONArray().apply { snapshot.acknowledgedCommandIds.sorted().forEach(::put) })
    putNullable("message", snapshot.message)
    put("updatedAtMillis", snapshot.updatedAtMillis)
    put("today", snapshot.today?.let(::encodeToday) ?: JSONObject.NULL)
}.toString().toByteArray(Charsets.UTF_8)

fun decodeWatchSnapshot(bytes: ByteArray): WatchSnapshot {
    require(bytes.size <= 100_000) { "Watch snapshot is too large." }
    val value = JSONObject(bytes.toString(Charsets.UTF_8))
    require(value.getInt("protocol") == WATCH_PROTOCOL_VERSION) { "Unsupported watch protocol." }
    val exercises = value.getJSONArray("exercises").objects(::decodeExercise)
    require(exercises.map(WatchExercise::id).distinct().size == exercises.size) { "Exercise IDs must be unique." }
    val status = WatchWorkoutStatus.valueOf(value.getString("status"))
    require(status == WatchWorkoutStatus.NONE || status == WatchWorkoutStatus.ERROR || exercises.isNotEmpty()) {
        "Workout snapshots require exercises."
    }
    return WatchSnapshot(
        status = status,
        generation = value.nonNegativeLong("generation"),
        eventRevision = value.nonNegativeLong("eventRevision"),
        sessionId = value.nullableString("sessionId"),
        routineId = value.nullableString("routineId"),
        routineName = value.nullableString("routineName"),
        scheduleEntryId = value.nullableString("scheduleEntryId"),
        scheduledDate = value.nullableString("scheduledDate"),
        focusedExerciseId = value.nullableString("focusedExerciseId"),
        exercises = exercises,
        hapticsEnabled = value.getBoolean("hapticsEnabled"),
        acknowledgedCommandIds = value.getJSONArray("acknowledgedCommandIds").strings().toSet(),
        message = value.nullableString("message"),
        updatedAtMillis = value.nonNegativeLong("updatedAtMillis"),
        today = value.optJSONObject("today")?.let(::decodeToday),
    ).also(::validateSnapshot)
}

private fun encodeToday(today: WatchTodayBriefing) = JSONObject().apply {
    put("date", today.date)
    putNullable("workoutName", today.workoutName)
    put("workoutCount", today.workoutCount)
    put("weekPlanned", today.weekPlanned)
    put("weekCompleted", today.weekCompleted)
    put("weekDays", JSONArray().apply { today.weekDays.forEach { (planned, done) ->
        put(JSONArray().put(planned).put(done))
    } })
    putNullable("location", today.location)
    put("temperatureF", today.temperatureF ?: JSONObject.NULL)
    putNullable("weatherKind", today.weatherKind)
    put("stocks", JSONArray().apply { today.stocks.forEach { stock -> put(JSONObject().apply {
        put("symbol", stock.symbol)
        put("price", stock.price ?: JSONObject.NULL)
        put("changePercent", stock.changePercent ?: JSONObject.NULL)
        put("points", JSONArray().apply { stock.points.takeLast(45).forEach(::put) })
    }) } })
    put("games", JSONArray().apply { today.games.forEach { game -> put(JSONObject().apply {
        put("team", game.team)
        putNullable("opponent", game.opponent)
        put("startsAtMillis", game.startsAtMillis ?: JSONObject.NULL)
    }) } })
    put("updatedAtMillis", today.updatedAtMillis)
}

private fun decodeToday(value: JSONObject) = WatchTodayBriefing(
    date = value.getString("date"),
    workoutName = value.nullableString("workoutName"),
    workoutCount = value.getInt("workoutCount"),
    weekPlanned = value.getInt("weekPlanned"),
    weekCompleted = value.getInt("weekCompleted"),
    weekDays = value.getJSONArray("weekDays").let { days -> (0 until days.length()).map { i ->
        days.getJSONArray(i).let { it.getInt(0) to it.getInt(1) }
    } },
    location = value.nullableString("location"),
    temperatureF = value.nullableInt("temperatureF"),
    weatherKind = value.nullableString("weatherKind"),
    stocks = value.getJSONArray("stocks").objects { WatchTodayStock(
        it.getString("symbol"), it.optDouble("price", Double.NaN).takeIf(Double::isFinite),
        it.optDouble("changePercent", Double.NaN).takeIf(Double::isFinite),
        it.optJSONArray("points")?.let { points -> (0 until points.length()).map { index -> points.getDouble(index) } }
            ?: emptyList(),
    ) },
    games = value.getJSONArray("games").objects { WatchTodayGame(
        it.getString("team"), it.nullableString("opponent"),
        if (it.isNull("startsAtMillis")) null else it.getLong("startsAtMillis"),
    ) },
    updatedAtMillis = value.nonNegativeLong("updatedAtMillis"),
).also {
    require(it.date.length == 10 && it.workoutCount in 0..100 && it.weekPlanned in 0..700)
    require(it.weekCompleted in 0..it.weekPlanned && it.weekDays.size == 7)
    require(it.weekDays.all { (planned, done) -> planned in 0..100 && done in 0..planned })
    require(it.workoutName == null || it.workoutName.length <= 200)
    require(it.location == null || it.location.length <= 100)
    require(it.temperatureF == null || it.temperatureF in -150..150)
    require(it.stocks.size <= 8 && it.games.size <= 6)
    require(it.stocks.all { stock -> stock.symbol.length in 1..12 &&
        (stock.price == null || stock.price > 0.0) &&
        (stock.changePercent == null || stock.changePercent in -1000.0..1000.0) &&
        stock.points.size <= 45 && stock.points.all { point -> point.isFinite() && point > 0.0 } })
    require(it.games.all { game -> game.team.length in 1..100 && (game.opponent?.length ?: 0) <= 100 })
}

fun encodeWatchTodayBriefing(today: WatchTodayBriefing): String = encodeToday(today).toString()
fun decodeWatchTodayBriefing(value: String): WatchTodayBriefing = decodeToday(JSONObject(value))

fun encodeWatchCommand(command: WatchCommand): ByteArray = JSONObject().apply {
    put("protocol", WATCH_PROTOCOL_VERSION)
    put("id", command.id)
    put("type", command.type.name)
    putNullable("sessionId", command.sessionId)
    putNullable("exerciseId", command.exerciseId)
    if (command.setNumber == null) put("setNumber", JSONObject.NULL) else put("setNumber", command.setNumber)
    put("createdAtMillis", command.createdAtMillis)
}.toString().toByteArray(Charsets.UTF_8)

fun decodeWatchCommand(bytes: ByteArray): WatchCommand {
    require(bytes.size <= 8_192) { "Watch command is too large." }
    val value = JSONObject(bytes.toString(Charsets.UTF_8))
    require(value.getInt("protocol") == WATCH_PROTOCOL_VERSION) { "Unsupported watch protocol." }
    return WatchCommand(
        id = value.getString("id").also { require(it.isNotBlank() && it.length <= 128) },
        type = WatchCommandType.valueOf(value.getString("type")),
        sessionId = value.nullableString("sessionId"),
        exerciseId = value.nullableString("exerciseId"),
        setNumber = if (value.isNull("setNumber")) null else value.getInt("setNumber"),
        createdAtMillis = value.nonNegativeLong("createdAtMillis"),
    ).also(::validateCommand)
}

fun encodeWatchCommands(commands: List<WatchCommand>): String = JSONArray().apply {
    commands.forEach { put(JSONObject(encodeWatchCommand(it).toString(Charsets.UTF_8))) }
}.toString()

fun decodeWatchCommands(value: String): List<WatchCommand> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    require(array.length() <= 128) { "Too many pending watch commands." }
    return array.objects { decodeWatchCommand(it.toString().toByteArray(Charsets.UTF_8)) }
}

fun projectPendingCommands(base: WatchSnapshot?, commands: List<WatchCommand>): WatchSnapshot? {
    var projected = base ?: return null
    commands.sortedBy(WatchCommand::createdAtMillis).forEach { command ->
        projected = when (command.type) {
            WatchCommandType.SYNC, WatchCommandType.REFRESH_TODAY -> projected
            WatchCommandType.START_TODAY -> if (projected.status == WatchWorkoutStatus.AVAILABLE) {
                projected.copy(status = WatchWorkoutStatus.ACTIVE)
            } else projected
            WatchCommandType.COMPLETE_SET -> projectCompleteSet(projected, command)
            WatchCommandType.FINISH_SESSION -> if (projected.status == WatchWorkoutStatus.READY_TO_FINISH) {
                projected.copy(status = WatchWorkoutStatus.COMPLETE)
            } else projected
        }
    }
    return projected
}

private fun projectCompleteSet(snapshot: WatchSnapshot, command: WatchCommand): WatchSnapshot {
    if (snapshot.status !in setOf(WatchWorkoutStatus.ACTIVE, WatchWorkoutStatus.READY_TO_FINISH)) return snapshot
    val exerciseId = command.exerciseId ?: return snapshot
    val setNumber = command.setNumber ?: return snapshot
    val index = snapshot.exercises.indexOfFirst { it.id == exerciseId }
    if (index < 0) return snapshot
    val exercise = snapshot.exercises[index]
    if (setNumber !in 1..exercise.setCount || setNumber <= exercise.completedSets) return snapshot
    val updatedExercises = snapshot.exercises.toMutableList().apply {
        this[index] = exercise.copy(completedSets = setNumber)
    }
    val allComplete = updatedExercises.all { it.completedSets == it.setCount }
    val nextFocus = if (setNumber < exercise.setCount) exercise.id else {
        (updatedExercises.drop(index + 1) + updatedExercises.take(index + 1))
            .firstOrNull { it.completedSets < it.setCount }?.id ?: exercise.id
    }
    return snapshot.copy(
        status = if (allComplete) WatchWorkoutStatus.READY_TO_FINISH else WatchWorkoutStatus.ACTIVE,
        focusedExerciseId = nextFocus,
        exercises = updatedExercises,
    )
}

private fun encodeExercise(exercise: WatchExercise) = JSONObject().apply {
    put("id", exercise.id)
    put("name", exercise.name)
    put("notes", exercise.notes)
    put("setCount", exercise.setCount)
    putNullableInt("reps", exercise.reps)
    putNullableInt("durationSeconds", exercise.durationSeconds)
    putNullableInt("weightPounds", exercise.weightPounds)
    put("artworkId", exercise.artworkId)
    put("completedSets", exercise.completedSets)
}

private fun decodeExercise(value: JSONObject) = WatchExercise(
    id = value.getString("id"),
    name = value.getString("name"),
    notes = value.getString("notes"),
    setCount = value.getInt("setCount"),
    reps = value.nullableInt("reps"),
    durationSeconds = value.nullableInt("durationSeconds"),
    weightPounds = value.nullableInt("weightPounds"),
    artworkId = value.getString("artworkId"),
    completedSets = value.getInt("completedSets"),
).also {
    require(it.id.isNotBlank() && it.id.length <= 128)
    require(it.name.isNotBlank() && it.name.length <= 200)
    require(it.notes.length <= 1_000 && it.artworkId.isNotBlank() && it.artworkId.length <= 128)
    require(it.setCount in 1..100 && it.completedSets in 0..it.setCount)
    require(it.reps == null || it.reps in 1..10_000)
    require(it.durationSeconds == null || it.durationSeconds in 1..86_400)
    require(it.weightPounds == null || it.weightPounds in 0..10_000)
}

private fun validateSnapshot(snapshot: WatchSnapshot) {
    require(snapshot.generation >= 0 && snapshot.eventRevision >= 0 && snapshot.updatedAtMillis >= 0)
    require(snapshot.acknowledgedCommandIds.size <= 128)
    require(snapshot.acknowledgedCommandIds.all { it.isNotBlank() && it.length <= 128 })
    snapshot.focusedExerciseId?.let { id -> require(snapshot.exercises.any { it.id == id }) }
    snapshot.routineName?.let { require(it.isNotBlank() && it.length <= 200) }
    snapshot.message?.let { require(it.length <= 500) }
}

private fun validateCommand(command: WatchCommand) {
    require(command.createdAtMillis >= 0)
    when (command.type) {
        WatchCommandType.COMPLETE_SET -> {
            require(!command.exerciseId.isNullOrBlank())
            require(command.setNumber != null && command.setNumber in 1..100)
        }
        WatchCommandType.SYNC, WatchCommandType.REFRESH_TODAY, WatchCommandType.START_TODAY, WatchCommandType.FINISH_SESSION -> {
            require(command.exerciseId == null && command.setNumber == null)
        }
    }
}

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.putNullableInt(key: String, value: Int?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.nonNegativeLong(key: String): Long = getLong(key).also { require(it >= 0) }

private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
