package dev.draftingroom5

internal fun validatePlanEdit(previous: TrainingPlan, updated: TrainingPlan) {
    val existing = previous.routines.associateBy { it.id }
    updated.routines.forEach { routine ->
        val old = existing[routine.id]
        if (old == null) {
            require(routine.revision == 1L) { "New routine revision must be one." }
        } else {
            require(routine.execution == old.execution) { "Routine execution type cannot change." }
            val changed = routine.copy(revision = old.revision) != old
            require(routine.revision == if (changed) old.revision + 1 else old.revision) {
                "Routine revision does not match its changes."
            }
        }
    }
}
