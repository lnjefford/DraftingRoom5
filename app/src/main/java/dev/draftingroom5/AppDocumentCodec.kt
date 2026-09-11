package dev.draftingroom5

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

internal fun encodeAppDocument(document: AppDocument): String {
    validateAppDocument(document)
    val value = JSONObject().apply {
        put("format", document.format)
        put("generation", document.generation)
        put("plan", encodePlan(document.plan))
        put("preferences", encodePreferences(document.preferences))
        put("partialSessions", JSONArray().apply { document.partialSessions.forEach { put(encodeSession(it)) } })
        put("history", JSONArray().apply { document.history.forEach { put(encodeHistory(it)) } })
    }.toString()
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
    return value
}

internal fun decodeAppDocument(value: String): AppDocument = try {
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
    inspectJsonStructure(value)
    val root = JSONObject(value).exact("format", "generation", "plan", "preferences", "partialSessions", "history")
    val document = AppDocument(
        format = root.strictString("format"),
        generation = root.strictLong("generation"),
        plan = decodePlan(root.strictObject("plan")),
        preferences = decodePreferences(root.strictObject("preferences")),
        partialSessions = root.strictArray("partialSessions").objects(::decodeSession),
        history = root.strictArray("history").objects(::decodeHistory),
    )
    val normalized = document.copy(
        preferences = document.preferences.copy(
            dashboardLayout = DashboardLayout(normalizeDashboardCards(document.preferences.dashboardLayout.cards)),
            voice = document.preferences.voice.copy(rate = normalizedVoiceRate(document.preferences.voice.rate)),
        ),
    )
    validateAppDocument(normalized)
    normalized
} catch (error: org.json.JSONException) {
    throw IllegalArgumentException("Malformed current document.", error)
} catch (error: java.time.DateTimeException) {
    throw IllegalArgumentException("Invalid occurrence date.", error)
}

private fun encodePlan(plan: TrainingPlan) = JSONObject().apply {
    put("routines", JSONArray().apply { plan.routines.forEach { put(encodeRoutine(it)) } })
    put("schedule", JSONArray().apply { plan.schedule.forEach { entry ->
        put(JSONObject().apply {
            put("id", entry.id); put("routineId", entry.routineId)
            put("days", JSONArray().apply { entry.days.sortedBy(DayOfWeek::getValue).forEach { put(it.name) } })
        })
    } })
}

private fun decodePlan(value: JSONObject): TrainingPlan {
    value.exact("routines", "schedule")
    return TrainingPlan(
        routines = value.strictArray("routines").objects(::decodeRoutine),
        schedule = value.strictArray("schedule").objects { entry ->
            entry.exact("id", "routineId", "days")
            val days = entry.strictArray("days").strings().map { DayOfWeek.valueOf(it) }
            require(days.distinct().size == days.size) { "Schedule weekdays must be unique." }
            ScheduleEntry(
                entry.strictString("id"), entry.strictString("routineId"),
                days.toCollection(linkedSetOf()),
            )
        },
    )
}

internal fun encodeRoutine(routine: Routine) = JSONObject().apply {
    put("id", routine.id); put("revision", routine.revision); put("name", routine.name); put("artworkId", routine.artworkId)
    put("execution", routine.execution.name)
    put("appLink", routine.appLink?.let { JSONObject().apply { put("packageName", it.packageName); put("deepLink", it.deepLink ?: JSONObject.NULL) } } ?: JSONObject.NULL)
    put("exercises", JSONArray().apply { routine.exercises.forEach { exercise ->
        put(JSONObject().apply {
            put("id", exercise.id); put("name", exercise.name); put("notes", exercise.notes); put("setCount", exercise.setCount)
            put("target", exercise.target); put("timerSeconds", exercise.timerSeconds ?: JSONObject.NULL); put("artworkId", exercise.artworkId)
        })
    } })
}

internal fun decodeRoutine(value: JSONObject): Routine {
    value.exact("id", "revision", "name", "artworkId", "execution", "appLink", "exercises")
    return Routine(
        id = value.strictString("id"), revision = value.strictLong("revision"), name = value.strictString("name"),
        artworkId = value.strictString("artworkId"), execution = RoutineExecution.valueOf(value.strictString("execution")),
        appLink = value.nullableObject("appLink")?.let {
            it.exact("packageName", "deepLink")
            AppLink(it.strictString("packageName"), it.nullableString("deepLink"))
        },
        exercises = value.strictArray("exercises").objects { exercise ->
            exercise.exact("id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId")
            Exercise(
                exercise.strictString("id"), exercise.strictString("name"), exercise.strictString("notes"),
                exercise.strictInt("setCount"), exercise.strictString("target"), exercise.nullableInt("timerSeconds"),
                exercise.strictString("artworkId"),
            )
        },
    )
}

private fun encodePreferences(value: AppPreferences) = JSONObject().apply {
    put("dashboardLayout", JSONObject().apply { put("cards", JSONArray().apply { value.dashboardLayout.cards.forEach {
        put(JSONObject().apply { put("card", it.card.name); put("visible", it.visible) })
    } }) })
    put("healthDateRange", value.healthDateRange.name); put("hapticsEnabled", value.hapticsEnabled)
    put("voice", JSONObject().apply { put("enabled", value.voice.enabled); put("rate", value.voice.rate.toDouble()) })
    put("automaticBackupsEnabled", value.automaticBackupsEnabled)
}

private fun decodePreferences(value: JSONObject): AppPreferences {
    value.exact("dashboardLayout", "healthDateRange", "hapticsEnabled", "voice", "automaticBackupsEnabled")
    val layout = value.strictObject("dashboardLayout").also { it.exact("cards") }
    val voice = value.strictObject("voice").also { it.exact("enabled", "rate") }
    return AppPreferences(
        dashboardLayout = DashboardLayout(buildList {
            val cards = layout.strictArray("cards")
            repeat(cards.length()) { index ->
                val item = cards.get(index)
                require(item is JSONObject) { "Dashboard card must be an object." }
                item.exact("card", "visible")
                val name = item.strictString("card")
                val visible = item.strictBoolean("visible")
                DashboardCard.entries.firstOrNull { it.name == name }
                    ?.let { add(DashboardCardPreference(it, visible)) }
            }
        }),
        healthDateRange = HealthDateRange.valueOf(value.strictString("healthDateRange")),
        hapticsEnabled = value.strictBoolean("hapticsEnabled"),
        voice = VoiceAnnouncementSettings(voice.strictBoolean("enabled"), voice.strictFiniteDouble("rate").coerceIn(0.75, 1.5).toFloat()),
        automaticBackupsEnabled = value.strictBoolean("automaticBackupsEnabled"),
    )
}

private fun encodeOccurrence(value: OccurrenceKey) = JSONObject().apply {
    put("scheduleEntryId", value.scheduleEntryId); put("scheduledDate", value.scheduledDate.toString())
}

private fun decodeOccurrence(value: JSONObject): OccurrenceKey {
    value.exact("scheduleEntryId", "scheduledDate")
    return OccurrenceKey(value.strictString("scheduleEntryId"), LocalDate.parse(value.strictString("scheduledDate")))
}

private fun encodeSession(value: GuidedSession) = JSONObject().apply {
    put("id", value.id); put("occurrence", encodeOccurrence(value.occurrence)); put("routineId", value.routineId)
    put("snapshot", encodeRoutine(value.snapshot)); put("focusedExerciseId", value.focusedExerciseId)
    put("completedSets", JSONObject().apply { value.completedSets.forEach { (id, count) -> put(id, count) } })
    put("timer", encodeTimer(value.timer)); put("startedAtMillis", value.startedAtMillis); put("updatedAtMillis", value.updatedAtMillis)
    put("eventRevision", value.eventRevision)
}

private fun decodeSession(value: JSONObject): GuidedSession {
    value.exact("id", "occurrence", "routineId", "snapshot", "focusedExerciseId", "completedSets", "timer", "startedAtMillis", "updatedAtMillis", "eventRevision")
    val sets = value.strictObject("completedSets")
    return GuidedSession(
        value.strictString("id"), decodeOccurrence(value.strictObject("occurrence")), value.strictString("routineId"),
        decodeRoutine(value.strictObject("snapshot")), value.strictString("focusedExerciseId"),
        sets.keys().asSequence().associateWith { sets.strictInt(it) }, decodeTimer(value.strictObject("timer")),
        value.strictLong("startedAtMillis"), value.strictLong("updatedAtMillis"), value.strictLong("eventRevision"),
    )
}

private fun encodeTimer(value: SessionTimer) = JSONObject().apply {
    put("phase", value.phase.name)
    if (value.phase != TimerPhase.IDLE) {
        put("runId", value.runId); put("exerciseId", value.exerciseId); put("setNumber", value.setNumber)
        put("bootCount", value.bootCount ?: JSONObject.NULL); put("readyDeadlineElapsedMillis", value.readyDeadlineElapsedMillis)
        put("activeDeadlineElapsedMillis", value.activeDeadlineElapsedMillis); put("lastObservedElapsedMillis", value.lastObservedElapsedMillis)
        put("lastHandledCueOrdinal", value.lastHandledCueOrdinal)
    }
}

private fun decodeTimer(value: JSONObject): SessionTimer {
    val phase = TimerPhase.valueOf(value.strictString("phase"))
    if (phase == TimerPhase.IDLE) { value.exact("phase"); return SessionTimer() }
    value.exact("phase", "runId", "exerciseId", "setNumber", "bootCount", "readyDeadlineElapsedMillis", "activeDeadlineElapsedMillis", "lastObservedElapsedMillis", "lastHandledCueOrdinal")
    return SessionTimer(
        phase, value.strictString("runId"), value.strictString("exerciseId"), value.strictInt("setNumber"),
        value.nullableLong("bootCount"), value.strictLong("readyDeadlineElapsedMillis"), value.strictLong("activeDeadlineElapsedMillis"),
        value.strictLong("lastObservedElapsedMillis"), value.strictInt("lastHandledCueOrdinal"),
    )
}

private fun encodeHistory(value: WorkoutHistoryEntry) = JSONObject().apply {
    put("id", value.id); put("occurrence", encodeOccurrence(value.occurrence)); put("snapshot", encodeRoutine(value.snapshot))
    put("startedAtMillis", value.startedAtMillis); put("completedAtMillis", value.completedAtMillis)
}

private fun decodeHistory(value: JSONObject): WorkoutHistoryEntry {
    value.exact("id", "occurrence", "snapshot", "startedAtMillis", "completedAtMillis")
    return WorkoutHistoryEntry(value.strictString("id"), decodeOccurrence(value.strictObject("occurrence")),
        decodeRoutine(value.strictObject("snapshot")), value.strictLong("startedAtMillis"), value.strictLong("completedAtMillis"))
}

private fun JSONObject.exact(vararg expected: String): JSONObject {
    require(keys().asSequence().toSet() == expected.toSet()) { "Unexpected or missing fields." }
    return this
}

private fun JSONObject.strictString(name: String) = get(name).let { require(it is String) { "$name must be a string." }; it }
private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else strictString(name)
private fun JSONObject.strictBoolean(name: String) = get(name).let { require(it is Boolean) { "$name must be a boolean." }; it }
private fun JSONObject.strictInt(name: String) = get(name).let { require(it is Int) { "$name must be an integer." }; it }
private fun JSONObject.nullableInt(name: String) = if (isNull(name)) null else strictInt(name)
private fun JSONObject.strictLong(name: String) = get(name).let { require(it is Int || it is Long) { "$name must be an integer." }; (it as Number).toLong() }
private fun JSONObject.nullableLong(name: String) = if (isNull(name)) null else strictLong(name)
private fun JSONObject.strictFiniteDouble(name: String) = get(name).let { require(it is Number && it.toDouble().isFinite()) { "$name must be finite." }; it.toDouble() }
private fun JSONObject.strictObject(name: String) = get(name).let { require(it is JSONObject) { "$name must be an object." }; it }
private fun JSONObject.nullableObject(name: String) = if (isNull(name)) null else strictObject(name)
private fun JSONObject.strictArray(name: String) = get(name).let { require(it is JSONArray) { "$name must be an array." }; it }
private fun JSONArray.strings() = List(length()) { index -> get(index).let { require(it is String); it } }
private fun <T> JSONArray.objects(transform: (JSONObject) -> T) = List(length()) { index ->
    val item = get(index); require(item is JSONObject) { "Array item must be an object." }; transform(item)
}
