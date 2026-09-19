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
        put("progressionReceipts", JSONArray().apply { document.progressionReceipts.forEach { receipt ->
            put(JSONObject().apply {
                put("sessionId", receipt.sessionId); put("routineId", receipt.routineId)
                put("before", encodeExercise(receipt.before)); put("choice", receipt.choice.name)
                put("appliedRevision", receipt.appliedRevision); put("undone", receipt.undone)
            })
        } })
        put("occurrenceExceptions", JSONArray().apply { document.occurrenceExceptions.forEach { exception ->
            put(JSONObject().apply {
                put("occurrence", encodeOccurrence(exception.occurrence))
                put("disposition", exception.disposition.name)
                put("effectiveDate", exception.effectiveDate?.toString() ?: JSONObject.NULL)
            })
        } })
    }.toString()
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
    // Recursive custom additions must remain readable, including inside a backup envelope.
    inspectJsonStructure(value, rootDepth = 1)
    return value
}

internal fun decodeAppDocument(value: String): AppDocument = try {
    require(value.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "Document exceeds the 16 MiB limit." }
    inspectJsonStructure(value)
    val root = JSONObject(value)
    val schema = when (root.keys().asSequence().toSet()) {
        CURRENT_DOCUMENT_FIELDS -> DocumentSchema.CURRENT
        V025_DOCUMENT_FIELDS -> DocumentSchema.V025
        else -> throw IllegalArgumentException("Unexpected or missing fields.")
    }
    val document = AppDocument(
        format = root.strictString("format"),
        generation = root.strictLong("generation"),
        plan = decodePlan(root.strictObject("plan"), schema),
        preferences = decodePreferences(root.strictObject("preferences")),
        partialSessions = root.strictArray("partialSessions").objects { decodeSession(it, schema) },
        history = root.strictArray("history").objects { decodeHistory(it, schema) },
        progressionReceipts = if (schema == DocumentSchema.V025) emptyList() else root.strictArray("progressionReceipts").objects {
            it.exact("sessionId", "routineId", "before", "choice", "appliedRevision", "undone")
            ProgressionReceipt(it.strictString("sessionId"), it.strictString("routineId"),
                decodeExercise(it.strictObject("before")), ProgressionChoice.valueOf(it.strictString("choice")),
                it.strictLong("appliedRevision"), it.strictBoolean("undone"))
        },
        occurrenceExceptions = root.strictArray("occurrenceExceptions").objects {
            it.exact("occurrence", "disposition", "effectiveDate")
            OccurrenceException(decodeOccurrence(it.strictObject("occurrence")),
                OccurrenceDisposition.valueOf(it.strictString("disposition")),
                it.nullableString("effectiveDate")?.let(LocalDate::parse))
        },
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

private fun decodePlan(value: JSONObject, schema: DocumentSchema): TrainingPlan {
    value.exact("routines", "schedule")
    return TrainingPlan(
        routines = value.strictArray("routines").objects { decodeRoutine(it, schema) },
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
    put("exercises", JSONArray().apply { routine.exercises.forEach { put(encodeExercise(it)) } })
}

internal fun decodeRoutine(value: JSONObject): Routine = decodeRoutine(value, DocumentSchema.CURRENT)

private fun decodeRoutine(value: JSONObject, schema: DocumentSchema): Routine {
    value.exact("id", "revision", "name", "artworkId", "execution", "appLink", "exercises")
    return Routine(
        id = value.strictString("id"), revision = value.strictLong("revision"), name = value.strictString("name"),
        artworkId = value.strictString("artworkId"), execution = RoutineExecution.valueOf(value.strictString("execution")),
        appLink = value.nullableObject("appLink")?.let {
            it.exact("packageName", "deepLink")
            AppLink(it.strictString("packageName"), it.nullableString("deepLink"))
        },
        exercises = value.strictArray("exercises").objects { exercise ->
            if (schema == DocumentSchema.V025) decodeV025Exercise(exercise) else decodeExercise(exercise)
        },
    )
}

private fun decodeV025Exercise(value: JSONObject): Exercise {
    value.exact("id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId")
    return Exercise(
        id = value.strictString("id"),
        name = value.strictString("name"),
        notes = value.strictString("notes"),
        setCount = value.strictInt("setCount"),
        target = value.strictString("target"),
        timerSeconds = value.nullableInt("timerSeconds"),
        artworkId = value.strictString("artworkId"),
        measurements = ExerciseMeasurements(),
        progression = null,
    )
}

internal fun encodeExercise(exercise: Exercise) = encodePrescription(exercise.prescription()).apply {
    put("id", exercise.id)
    put("progression", exercise.progression?.let(::encodeProgression) ?: JSONObject.NULL)
}

internal fun decodeExercise(value: JSONObject): Exercise {
    value.exact("id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId", "measurements", "progression")
    val prescription = decodePrescription(value)
    return Exercise(
        id = value.strictString("id"),
        name = prescription.name,
        notes = prescription.notes,
        setCount = prescription.setCount,
        target = prescription.target,
        timerSeconds = prescription.timerSeconds,
        artworkId = prescription.artworkId,
        measurements = prescription.measurements,
        progression = value.nullableObject("progression")?.let(::decodeProgression),
    )
}

private fun encodePrescription(value: ExercisePrescription) = JSONObject().apply {
    put("name", value.name); put("notes", value.notes); put("setCount", value.setCount)
    put("target", value.target); put("timerSeconds", value.timerSeconds ?: JSONObject.NULL); put("artworkId", value.artworkId)
    put("measurements", encodeMeasurements(value.measurements))
}

private fun decodePrescription(value: JSONObject): ExercisePrescription {
    val measurements = value.strictObject("measurements").also { it.exact("weightPounds", "durationSeconds") }
    return ExercisePrescription(
        name = value.strictString("name"),
        notes = value.strictString("notes"),
        setCount = value.strictInt("setCount"),
        target = value.strictString("target"),
        timerSeconds = value.nullableInt("timerSeconds"),
        artworkId = value.strictString("artworkId"),
        measurements = ExerciseMeasurements(
            weightPounds = measurements.nullableFiniteDouble("weightPounds"),
            durationSeconds = measurements.nullableInt("durationSeconds"),
        ),
    )
}

private fun encodeMeasurements(value: ExerciseMeasurements) = JSONObject().apply {
    put("weightPounds", value.weightPounds ?: JSONObject.NULL)
    put("durationSeconds", value.durationSeconds ?: JSONObject.NULL)
}

internal fun encodeProgression(value: ExerciseProgression): JSONObject = when (value) {
    is AutomaticExerciseProgression -> JSONObject().apply {
        put("kind", "AUTOMATIC")
        put("weightPounds", value.weightPounds?.let { rule -> JSONObject().apply {
            put("increment", rule.increment); put("minimum", rule.minimum ?: JSONObject.NULL); put("maximum", rule.maximum ?: JSONObject.NULL)
        } } ?: JSONObject.NULL)
        put("durationSeconds", value.durationSeconds?.let { rule -> JSONObject().apply {
            put("increment", rule.increment); put("minimum", rule.minimum ?: JSONObject.NULL); put("maximum", rule.maximum ?: JSONObject.NULL)
        } } ?: JSONObject.NULL)
    }
    is CustomExerciseProgression -> JSONObject().apply {
        put("kind", "CUSTOM")
        put("steps", JSONArray().apply { value.steps.forEach { step -> put(JSONObject().apply {
            put("replacement", encodePrescription(step.replacement))
            put("insertedExercises", JSONArray().apply { step.insertedExercises.forEach { put(encodeExercise(it)) } })
        }) } })
    }
}

internal fun decodeProgression(value: JSONObject): ExerciseProgression = when (value.strictString("kind")) {
    "AUTOMATIC" -> {
        value.exact("kind", "weightPounds", "durationSeconds")
        AutomaticExerciseProgression(
            weightPounds = value.nullableObject("weightPounds")?.let { rule ->
                rule.exact("increment", "minimum", "maximum")
                AutomaticPoundsProgression(
                    increment = rule.strictFiniteDouble("increment"),
                    minimum = rule.nullableFiniteDouble("minimum"),
                    maximum = rule.nullableFiniteDouble("maximum"),
                )
            },
            durationSeconds = value.nullableObject("durationSeconds")?.let { rule ->
                rule.exact("increment", "minimum", "maximum")
                AutomaticSecondsProgression(
                    increment = rule.strictInt("increment"),
                    minimum = rule.nullableInt("minimum"),
                    maximum = rule.nullableInt("maximum"),
                )
            },
        )
    }
    "CUSTOM" -> {
        value.exact("kind", "steps")
        CustomExerciseProgression(value.strictArray("steps").objects { step ->
            step.exact("replacement", "insertedExercises")
            val replacement = step.strictObject("replacement").also {
                it.exact("name", "notes", "setCount", "target", "timerSeconds", "artworkId", "measurements")
            }
            CustomProgressionStep(
                replacement = decodePrescription(replacement),
                insertedExercises = step.strictArray("insertedExercises").objects(::decodeExercise),
            )
        })
    }
    else -> throw IllegalArgumentException("Unsupported exercise progression kind.")
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
    put("effectiveDate", value.effectiveDate.toString())
    put("handledProgressionExerciseIds", JSONArray(value.handledProgressionExerciseIds.sorted()))
}

private fun decodeSession(value: JSONObject, schema: DocumentSchema): GuidedSession {
    if (schema == DocumentSchema.V025) {
        value.exact("id", "occurrence", "routineId", "snapshot", "focusedExerciseId", "completedSets", "timer", "startedAtMillis", "updatedAtMillis", "eventRevision", "effectiveDate")
    } else {
        value.exact("id", "occurrence", "routineId", "snapshot", "focusedExerciseId", "completedSets", "timer", "startedAtMillis", "updatedAtMillis", "eventRevision", "effectiveDate", "handledProgressionExerciseIds")
    }
    val sets = value.strictObject("completedSets")
    return GuidedSession(
        value.strictString("id"), decodeOccurrence(value.strictObject("occurrence")), value.strictString("routineId"),
        decodeRoutine(value.strictObject("snapshot"), schema), value.strictString("focusedExerciseId"),
        sets.keys().asSequence().associateWith { sets.strictInt(it) }, decodeTimer(value.strictObject("timer")),
        value.strictLong("startedAtMillis"), value.strictLong("updatedAtMillis"), value.strictLong("eventRevision"),
        LocalDate.parse(value.strictString("effectiveDate")),
        if (schema == DocumentSchema.V025) emptySet() else value.strictArray("handledProgressionExerciseIds").strings().toSet(),
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
    put("effectiveDate", value.effectiveDate.toString())
}

private fun decodeHistory(value: JSONObject, schema: DocumentSchema): WorkoutHistoryEntry {
    value.exact("id", "occurrence", "snapshot", "startedAtMillis", "completedAtMillis", "effectiveDate")
    return WorkoutHistoryEntry(value.strictString("id"), decodeOccurrence(value.strictObject("occurrence")),
        decodeRoutine(value.strictObject("snapshot"), schema), value.strictLong("startedAtMillis"), value.strictLong("completedAtMillis"),
        LocalDate.parse(value.strictString("effectiveDate")))
}

private enum class DocumentSchema { V025, CURRENT }

private val V025_DOCUMENT_FIELDS = setOf(
    "format", "generation", "plan", "preferences", "partialSessions", "history", "occurrenceExceptions",
)
private val CURRENT_DOCUMENT_FIELDS = V025_DOCUMENT_FIELDS + "progressionReceipts"

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
private fun JSONObject.nullableFiniteDouble(name: String) = if (isNull(name)) null else strictFiniteDouble(name)
private fun JSONObject.strictObject(name: String) = get(name).let { require(it is JSONObject) { "$name must be an object." }; it }
private fun JSONObject.nullableObject(name: String) = if (isNull(name)) null else strictObject(name)
private fun JSONObject.strictArray(name: String) = get(name).let { require(it is JSONArray) { "$name must be an array." }; it }
private fun JSONArray.strings() = List(length()) { index -> get(index).let { require(it is String); it } }
private fun <T> JSONArray.objects(transform: (JSONObject) -> T) = List(length()) { index ->
    val item = get(index); require(item is JSONObject) { "Array item must be an object." }; transform(item)
}
