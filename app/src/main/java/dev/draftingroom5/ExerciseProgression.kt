package dev.draftingroom5

import java.math.BigDecimal

internal fun Exercise.measurementSummary(): String = buildList {
    measurements.weightPounds?.let { add("${BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()} lb") }
    measurements.durationSeconds?.let { add("$it seconds") }
}.joinToString(" · ")

internal fun Exercise.targetSummary(): String = listOf(target, measurementSummary())
    .filter { it.isNotBlank() }.joinToString(" · ")

internal enum class ProgressionChoice { WEIGHT, DURATION, HEAVIER_SHORTER, CUSTOM }

internal data class ProgressionOption(
    val choice: ProgressionChoice,
    val source: Exercise,
    val additions: List<Exercise> = emptyList(),
)

/** Exact alternatives shared by previews and the commit boundary. No increase-both default. */
internal fun Exercise.progressionOptions(): List<ProgressionOption> = when (val rule = progression) {
    null -> emptyList()
    is CustomExerciseProgression -> rule.steps.firstOrNull()?.let { step ->
        val p = step.replacement
        listOf(ProgressionOption(ProgressionChoice.CUSTOM, copy(
            name = p.name, notes = p.notes, setCount = p.setCount, target = p.target,
            timerSeconds = p.timerSeconds, artworkId = p.artworkId, measurements = p.measurements,
            progression = rule.steps.drop(1).takeIf { it.isNotEmpty() }?.let(::CustomExerciseProgression),
        ), step.insertedExercises))
    }.orEmpty()
    is AutomaticExerciseProgression -> {
        val weight = measurements.weightPounds
        val duration = measurements.durationSeconds
        val heavier = rule.weightPounds?.let { r -> weight?.let {
            // Decimal arithmetic avoids accumulating binary rounding in user-entered increments.
            BigDecimal.valueOf(it).add(BigDecimal.valueOf(r.increment))
                .min(BigDecimal.valueOf(r.maximum ?: Double.MAX_VALUE)).toDouble()
                .takeIf { next -> next.isFinite() && next > it }
        } }
        val longer = rule.durationSeconds?.let { r -> duration?.let {
            minOf(it.toLong() + r.increment, (r.maximum ?: Int.MAX_VALUE).toLong()).toInt()
                .takeIf { next -> next > it }
        } }
        val shorter = rule.durationSeconds?.let { r -> duration?.let {
            maxOf(it.toLong() - r.increment, (r.minimum ?: 1).toLong()).toInt()
                .takeIf { next -> next < it }
        } }
        fun option(choice: ProgressionChoice, pounds: Double?, seconds: Int?) = ProgressionOption(choice,
            copy(measurements = ExerciseMeasurements(pounds, seconds),
                // A weight-only choice must not alter even a separately configured timer.
                timerSeconds = if (seconds != duration) seconds else timerSeconds))
        buildList {
            if (heavier != null) add(option(ProgressionChoice.WEIGHT, heavier, duration))
            if (longer != null) add(option(ProgressionChoice.DURATION, weight, longer))
            if (heavier != null && shorter != null) add(option(ProgressionChoice.HEAVIER_SHORTER, heavier, shorter))
        }
    }
}

internal data class ProgressionRequest(
    val sessionId: String,
    val exerciseId: String,
    val expectedSessionRevision: Long,
    val expectedRoutineRevision: Long,
    val choice: ProgressionChoice,
)

/** Durable idempotency and insertion lineage; retained after Undo, restart and session completion. */
internal data class ProgressionReceipt(
    val sessionId: String,
    val routineId: String,
    val before: Exercise,
    val choice: ProgressionChoice,
    val appliedRevision: Long,
    val undone: Boolean = false,
) {
    fun option(): ProgressionOption = before.progressionOptions().single { it.choice == choice }
}

internal data class ProgressionOffer(
    val sessionId: String,
    val exerciseId: String,
    val sessionRevision: Long,
    val routineRevision: Long,
    val options: List<ProgressionOption>,
) {
    fun request(choice: ProgressionChoice) = ProgressionRequest(
        sessionId, exerciseId, sessionRevision, routineRevision, choice,
    )
}

/** Re-evaluate against the committed snapshot and live source, never a caller-supplied prescription. */
internal fun AppDocument.progressionOffer(sessionId: String, exerciseId: String): ProgressionOffer? {
    if (progressionReceipts.any { it.sessionId == sessionId && it.before.id == exerciseId }) return null
    val session = partialSessions.firstOrNull { it.id == sessionId } ?: return null
    if (exerciseId in session.handledProgressionExerciseIds) return null
    val source = session.snapshot.exercises.firstOrNull { it.id == exerciseId } ?: return null
    if (session.completedSets[exerciseId] != source.setCount) return null
    val routine = plan.routines.firstOrNull { it.id == session.routineId } ?: return null
    if (routine.execution != RoutineExecution.GUIDED || routine.revision == Long.MAX_VALUE) return null
    if (routine.exercises.firstOrNull { it.id == exerciseId } != source) return null
    // Even an advance followed by Undo cannot make an older workout eligible again.
    if (progressionReceipts.any { it.routineId == routine.id && it.before.id == exerciseId &&
            it.appliedRevision > session.snapshot.revision }) return null
    val options = source.progressionOptions().takeIf { it.isNotEmpty() } ?: return null
    return ProgressionOffer(sessionId, exerciseId, session.eventRevision, routine.revision, options)
}
