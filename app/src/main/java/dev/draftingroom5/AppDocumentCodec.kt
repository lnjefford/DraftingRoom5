package dev.draftingroom5

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

private const val CURRENT_SCHEMA_VERSION = 2

internal fun encodeAppDocument(document: AppDocument): String {
    validateAppDocument(document)
    val value = JSONObject().apply {
        put("format", document.format)
        put("schemaVersion", CURRENT_SCHEMA_VERSION)
        put("generation", document.generation)
        put("plan", encodePlan(document.plan))
        put("preferences", encodePreferences(document.preferences))
        put("partialSessions", JSONArray().apply { document.partialSessions.forEach { put(encodeSession(it)) } })
        put("history", JSONArray().apply { document.history.forEach { put(encodeHistory(it)) } })
        put("progressionReceipts", JSONArray().apply { document.progressionReceipts.forEach { receipt ->
            put(JSONObject().apply {
                put("sessionId", receipt.sessionId); put("routineId", receipt.routineId)
                put("before", encodeExercise(receipt.before)); put("choice", receipt.choice.name)
                put("manualPrescription", receipt.manualPrescription?.let(::encodePrescription) ?: JSONObject.NULL)
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
    val schema = detectDocumentSchema(root)
    when (schema) {
        DocumentSchema.CURRENT -> {
            root.exact(*CURRENT_DOCUMENT_FIELDS.toTypedArray())
            require(root.strictInt("schemaVersion") == CURRENT_SCHEMA_VERSION) { "Unsupported document schema version." }
        }
        DocumentSchema.RELEASED_V02731,
        DocumentSchema.RELEASED_V02730 -> root.exact(*PREVERSION_DOCUMENT_FIELDS.toTypedArray())
        DocumentSchema.RELEASED_V025 -> root.exact(*V025_DOCUMENT_FIELDS.toTypedArray())
    }
    val document = AppDocument(
        format = root.strictString("format"),
        generation = root.strictLong("generation"),
        plan = decodePlan(root.strictObject("plan"), schema),
        preferences = decodePreferences(root.strictObject("preferences")),
        partialSessions = root.strictArray("partialSessions").objects { decodeSession(it, schema) },
        history = root.strictArray("history").objects { decodeHistory(it, schema) },
        progressionReceipts = if (schema == DocumentSchema.RELEASED_V025) emptyList() else
            root.strictArray("progressionReceipts").objects { decodeProgressionReceipt(it, schema) },
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
        exercises = value.strictArray("exercises").objects { exercise -> decodeExercise(exercise, schema) },
    )
}

internal fun encodeExercise(exercise: Exercise) = encodePrescription(exercise.prescription()).apply {
    put("id", exercise.id)
    put("progression", exercise.progression?.let(::encodeProgression) ?: JSONObject.NULL)
}

internal fun decodeExercise(value: JSONObject): Exercise {
    return decodeExercise(value, DocumentSchema.CURRENT)
}

private fun decodeExercise(value: JSONObject, schema: DocumentSchema): Exercise {
    if (schema == DocumentSchema.RELEASED_V025) return decodeV025Exercise(value)
    if (schema == DocumentSchema.RELEASED_V02730) return decodeV02730Exercise(value)
    value.exact("id", "name", "notes", "setCount", "reps", "durationSeconds", "artworkId", "weightPounds", "progression")
    val prescription = decodePrescription(value)
    return Exercise(
        id = value.strictString("id"),
        name = prescription.name,
        notes = prescription.notes,
        setCount = prescription.setCount,
        reps = prescription.reps,
        durationSeconds = prescription.durationSeconds,
        artworkId = prescription.artworkId,
        weightPounds = prescription.weightPounds,
        progression = value.nullableObject("progression")?.let(::decodeProgression),
    )
}

private fun decodeV025Exercise(value: JSONObject): Exercise {
    value.exact("id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId")
    val target = value.strictString("target")
    val duration = value.nullableInt("timerSeconds")
    val reps = legacyReps(target)
    return Exercise(
        id = value.strictString("id"), name = value.strictString("name"),
        notes = preserveLegacyTarget(value.strictString("notes"), target, reps, duration),
        setCount = value.strictInt("setCount"), reps = reps, durationSeconds = duration,
        artworkId = value.strictString("artworkId"), weightPounds = null, progression = null,
    )
}

private fun decodeV02730Exercise(value: JSONObject): Exercise {
    value.exact("id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId", "measurements", "progression")
    val prescription = decodeV02730Prescription(value)
    val progression = value.nullableObject("progression")?.let { legacy ->
        when (legacy.strictString("kind")) {
            "AUTOMATIC" -> {
                legacy.exact("kind", "weightPounds", "durationSeconds")
                null
            }
            "CUSTOM" -> decodeV02730CustomProgression(legacy)
            else -> throw IllegalArgumentException("Unsupported legacy exercise progression kind.")
        }
    }
    return Exercise(
        id = value.strictString("id"), name = prescription.name, notes = prescription.notes,
        setCount = prescription.setCount, reps = prescription.reps, durationSeconds = prescription.durationSeconds,
        artworkId = prescription.artworkId, weightPounds = prescription.weightPounds, progression = progression,
    )
}

private fun decodeV02730Prescription(value: JSONObject): ExercisePrescription {
    val measurements = value.strictObject("measurements").also { it.exact("weightPounds", "durationSeconds") }
    val target = value.strictString("target")
    val timer = value.nullableInt("timerSeconds")
    val duration = measurements.nullableInt("durationSeconds") ?: timer
    val reps = legacyReps(target)
    return ExercisePrescription(
        name = value.strictString("name"),
        notes = preserveLegacyTarget(value.strictString("notes"), target, reps, duration),
        setCount = value.strictInt("setCount"), reps = reps, durationSeconds = duration,
        artworkId = value.strictString("artworkId"),
        weightPounds = measurements.nullableFiniteDouble("weightPounds")?.let(::normalizeLegacyWeight),
    )
}

private fun decodeV02730CustomProgression(value: JSONObject): CustomExerciseProgression {
    value.exact("kind", "steps")
    return CustomExerciseProgression(value.strictArray("steps").objects { step ->
        step.exact("replacement", "insertedExercises")
        val replacement = step.strictObject("replacement").also {
            it.exact("name", "notes", "setCount", "target", "timerSeconds", "artworkId", "measurements")
        }
        CustomProgressionStep(
            replacement = decodeV02730Prescription(replacement),
            insertedExercises = step.strictArray("insertedExercises").objects { decodeV02730Exercise(it) },
        )
    })
}

private fun legacyReps(target: String): Int? {
    val match = LEGACY_REPS.matchEntire(target.trim()) ?: return null
    return match.groupValues[1].toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}

private fun preserveLegacyTarget(notes: String, target: String, reps: Int?, duration: Int?): String {
    val trimmed = target.trim()
    if (trimmed.isEmpty()) return notes
    val canonical = when {
        duration != null && trimmed.matches(Regex("(?i)^$duration\\s*(?:s|sec|secs|second|seconds)$")) -> true
        reps != null && trimmed.matches(Regex("(?i)^$reps\\s*reps?$")) -> true
        else -> false
    }
    if (canonical || notes.contains("Previous target: $trimmed")) return notes
    return listOf("Previous target: $trimmed", notes).filter(String::isNotBlank).joinToString("\n").take(4_000)
}

private fun normalizeLegacyWeight(value: Double): Int {
    val maximum = Int.MAX_VALUE - Int.MAX_VALUE % 5
    return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(5)).max(BigDecimal.valueOf(5)).min(BigDecimal.valueOf(maximum.toLong())).toInt()
}

internal fun encodePrescription(value: ExercisePrescription) = JSONObject().apply {
    put("name", value.name); put("notes", value.notes); put("setCount", value.setCount)
    put("reps", value.reps ?: JSONObject.NULL); put("durationSeconds", value.durationSeconds ?: JSONObject.NULL)
    put("artworkId", value.artworkId); put("weightPounds", value.weightPounds ?: JSONObject.NULL)
}

internal fun decodePrescription(value: JSONObject): ExercisePrescription = ExercisePrescription(
    name = value.strictString("name"), notes = value.strictString("notes"), setCount = value.strictInt("setCount"),
    reps = value.nullableInt("reps"), durationSeconds = value.nullableInt("durationSeconds"),
    artworkId = value.strictString("artworkId"), weightPounds = value.nullableInt("weightPounds"),
)

internal fun encodeProgression(value: CustomExerciseProgression): JSONObject = JSONObject().apply {
    put("steps", JSONArray().apply { value.steps.forEach { step -> put(JSONObject().apply {
        put("replacement", encodePrescription(step.replacement))
        put("insertedExercises", JSONArray().apply { step.insertedExercises.forEach { put(encodeExercise(it)) } })
    }) } })
}

internal fun decodeProgression(value: JSONObject): CustomExerciseProgression {
    value.exact("steps")
    return CustomExerciseProgression(value.strictArray("steps").objects { step ->
        step.exact("replacement", "insertedExercises")
        CustomProgressionStep(
            decodePrescription(step.strictObject("replacement").also {
                it.exact("name", "notes", "setCount", "reps", "durationSeconds", "artworkId", "weightPounds")
            }),
            step.strictArray("insertedExercises").objects(::decodeExercise),
        )
    })
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
    if (schema == DocumentSchema.RELEASED_V025) {
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
        if (schema == DocumentSchema.RELEASED_V025) emptySet() else
            value.strictArray("handledProgressionExerciseIds").strings().toSet(),
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

private fun decodeProgressionReceipt(value: JSONObject, schema: DocumentSchema): ProgressionReceipt {
    if (schema != DocumentSchema.RELEASED_V02730) {
        value.exact("sessionId", "routineId", "before", "choice", "appliedRevision", "undone", "manualPrescription")
        return ProgressionReceipt(
            value.strictString("sessionId"), value.strictString("routineId"),
            decodeExercise(value.strictObject("before"), schema), ProgressionChoice.valueOf(value.strictString("choice")),
            value.strictLong("appliedRevision"), value.strictBoolean("undone"),
            value.nullableObject("manualPrescription")?.let { prescription ->
                prescription.exact("name", "notes", "setCount", "reps", "durationSeconds", "artworkId", "weightPounds")
                decodePrescription(prescription)
            },
        )
    }
    value.exact("sessionId", "routineId", "before", "choice", "appliedRevision", "undone")
    val beforeJson = value.strictObject("before")
    val before = decodeV02730Exercise(beforeJson)
    val oldChoice = value.strictString("choice")
    val choice = if (oldChoice == "CUSTOM") ProgressionChoice.CUSTOM else ProgressionChoice.MANUAL
    val manual = if (choice == ProgressionChoice.MANUAL) legacyAutomaticOutcome(beforeJson, oldChoice) else null
    return ProgressionReceipt(
        value.strictString("sessionId"), value.strictString("routineId"), before, choice,
        value.strictLong("appliedRevision"), value.strictBoolean("undone"), manual,
    )
}

private fun legacyAutomaticOutcome(beforeJson: JSONObject, choice: String): ExercisePrescription {
    require(choice in setOf("WEIGHT", "DURATION", "HEAVIER_SHORTER")) { "Unsupported legacy progression choice." }
    val before = decodeV02730Prescription(beforeJson)
    val progression = beforeJson.strictObject("progression").also { it.exact("kind", "weightPounds", "durationSeconds") }
    require(progression.strictString("kind") == "AUTOMATIC")
    var weight = before.weightPounds
    var duration = before.durationSeconds
    if (choice == "WEIGHT" || choice == "HEAVIER_SHORTER") {
        val rule = progression.strictObject("weightPounds").also { it.exact("increment", "minimum", "maximum") }
        val current = beforeJson.strictObject("measurements").strictFiniteDouble("weightPounds")
        val maximum = rule.nullableFiniteDouble("maximum") ?: Double.MAX_VALUE
        weight = normalizeLegacyWeight(minOf(current + rule.strictFiniteDouble("increment"), maximum))
    }
    if (choice == "DURATION") {
        val rule = progression.strictObject("durationSeconds").also { it.exact("increment", "minimum", "maximum") }
        duration = minOf(
            requireNotNull(before.durationSeconds).toLong() + rule.strictInt("increment"),
            (rule.nullableInt("maximum") ?: Int.MAX_VALUE).toLong(),
        ).toInt()
    } else if (choice == "HEAVIER_SHORTER") {
        val rule = progression.strictObject("durationSeconds").also { it.exact("increment", "minimum", "maximum") }
        duration = maxOf(
            requireNotNull(before.durationSeconds).toLong() - rule.strictInt("increment"),
            (rule.nullableInt("minimum") ?: 1).toLong(),
        ).toInt()
    }
    return before.copy(weightPounds = weight, durationSeconds = duration)
}

private fun detectDocumentSchema(root: JSONObject): DocumentSchema {
    val fields = root.keys().asSequence().toSet()
    if (fields == CURRENT_DOCUMENT_FIELDS) return DocumentSchema.CURRENT
    if (fields == V025_DOCUMENT_FIELDS) return DocumentSchema.RELEASED_V025
    require(fields == PREVERSION_DOCUMENT_FIELDS) { "Unexpected or missing fields." }
    val shapes = mutableSetOf<DocumentSchema>()
    fun inspectRoutine(routine: JSONObject) {
        routine.strictArray("exercises").objects { exercise ->
            shapes += when (exercise.keys().asSequence().toSet()) {
                CURRENT_EXERCISE_FIELDS -> DocumentSchema.RELEASED_V02731
                V02730_EXERCISE_FIELDS -> DocumentSchema.RELEASED_V02730
                else -> throw IllegalArgumentException("Unexpected or missing exercise fields.")
            }
        }
    }
    root.strictObject("plan").strictArray("routines").objects(::inspectRoutine)
    root.strictArray("partialSessions").objects { inspectRoutine(it.strictObject("snapshot")) }
    root.strictArray("history").objects { inspectRoutine(it.strictObject("snapshot")) }
    root.strictArray("progressionReceipts").objects { receipt ->
        val before = receipt.strictObject("before")
        shapes += when (before.keys().asSequence().toSet()) {
            CURRENT_EXERCISE_FIELDS -> DocumentSchema.RELEASED_V02731
            V02730_EXERCISE_FIELDS -> DocumentSchema.RELEASED_V02730
            else -> throw IllegalArgumentException("Unexpected or missing exercise fields.")
        }
    }
    require(shapes.size <= 1) { "Mixed document schemas are not supported." }
    return shapes.singleOrNull() ?: DocumentSchema.RELEASED_V02731
}

private enum class DocumentSchema { CURRENT, RELEASED_V02731, RELEASED_V02730, RELEASED_V025 }

private val PREVERSION_DOCUMENT_FIELDS = setOf(
    "format", "generation", "plan", "preferences", "partialSessions", "history", "occurrenceExceptions", "progressionReceipts",
)
private val CURRENT_DOCUMENT_FIELDS = PREVERSION_DOCUMENT_FIELDS + "schemaVersion"
private val V025_DOCUMENT_FIELDS = PREVERSION_DOCUMENT_FIELDS - "progressionReceipts"
private val CURRENT_EXERCISE_FIELDS = setOf(
    "id", "name", "notes", "setCount", "reps", "durationSeconds", "artworkId", "weightPounds", "progression",
)
private val V02730_EXERCISE_FIELDS = setOf(
    "id", "name", "notes", "setCount", "target", "timerSeconds", "artworkId", "measurements", "progression",
)
private val LEGACY_REPS = Regex("(?i)^\\s*(\\d+)(?:\\s*-\\s*\\d+)?(?:\\s*reps?)?(?:\\s*/\\s*side)?\\s*$")

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
