package dev.draftingroom5

internal fun Exercise.measurementSummary(): String = buildList {
    weightPounds?.let { add("$it lb") }
    durationSeconds?.let { add("$it seconds") }
}.joinToString(" · ")

internal fun Exercise.targetSummary(): String = buildList {
    reps?.let { add("$it reps") }
    measurementSummary().takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ")

internal enum class ProgressionChoice { CUSTOM, MANUAL }

internal data class ProgressionOption(
    val choice: ProgressionChoice,
    val source: Exercise,
    val additions: List<Exercise> = emptyList(),
)

internal fun Exercise.withPrescription(p: ExercisePrescription): Exercise = copy(
    name = p.name, notes = p.notes, setCount = p.setCount, reps = p.reps,
    durationSeconds = p.durationSeconds, artworkId = p.artworkId, weightPounds = p.weightPounds,
)

internal fun Exercise.progressionOptions(): List<ProgressionOption> = progression?.steps?.firstOrNull()?.let { step ->
    listOf(ProgressionOption(ProgressionChoice.CUSTOM, withPrescription(step.replacement).copy(
        progression = progression.steps.drop(1).takeIf { it.isNotEmpty() }?.let(::CustomExerciseProgression),
    ), step.insertedExercises))
}.orEmpty()

internal data class ProgressionRequest(
    val sessionId: String,
    val exerciseId: String,
    val expectedSessionRevision: Long,
    val expectedRoutineRevision: Long,
    val choice: ProgressionChoice,
    val manualPrescription: ExercisePrescription? = null,
)

/** Durable idempotency and insertion lineage; retained after Undo, restart and session completion. */
internal data class ProgressionReceipt(
    val sessionId: String,
    val routineId: String,
    val before: Exercise,
    val choice: ProgressionChoice,
    val appliedRevision: Long,
    val undone: Boolean = false,
    val manualPrescription: ExercisePrescription? = null,
) {
    fun option(): ProgressionOption = when (choice) {
        ProgressionChoice.CUSTOM -> before.progressionOptions().single()
        ProgressionChoice.MANUAL -> ProgressionOption(choice, before.withPrescription(requireNotNull(manualPrescription)))
    }
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

    fun manualRequest(prescription: ExercisePrescription) = ProgressionRequest(
        sessionId, exerciseId, sessionRevision, routineRevision, ProgressionChoice.MANUAL, prescription,
    )
}

/** Both custom and manual changes require a completed, unchanged source and current revisions. */
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
    val options = source.progressionOptions()
    return ProgressionOffer(sessionId, exerciseId, session.eventRevision, routine.revision, options)
}
