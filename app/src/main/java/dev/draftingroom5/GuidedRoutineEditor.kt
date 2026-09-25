package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ExerciseDraft(
    val id: String,
    val name: String = "",
    val notes: String = "",
    val sets: String = "",
    val reps: String = "",
    val timed: Boolean = false,
    val durationSeconds: String = "20",
    val artworkId: String = ExerciseArtworkCatalog.FALLBACK_ID,
    val currentPounds: String = "",
    val customProgressionEnabled: Boolean = false,
    val customProgression: CustomExerciseProgression? = null,
)

internal fun Exercise.exerciseDraft() = ExerciseDraft(
    id, name, notes, setCount.toString(), reps?.toString().orEmpty(), durationSeconds != null,
    durationSeconds?.toString() ?: "20", artworkId, weightPounds?.toString().orEmpty(),
    progression != null, progression,
)

internal fun ExerciseDraft.savedExercise(): Exercise? {
    val count = sets.toIntOrNull() ?: return null
    val repetitions = if (reps.isBlank()) null else reps.toIntOrNull() ?: return null
    val pounds = if (currentPounds.isBlank()) null else currentPounds.toIntOrNull() ?: return null
    val seconds = if (timed) durationSeconds.toIntOrNull() ?: return null else null
    if (id.isBlank()) return null
    val exercise = Exercise(id, name.trim(), notes.trim(), count, repetitions, seconds,
        ExerciseArtworkCatalog.resolve(artworkId).storageId, pounds,
        customProgression.takeIf { customProgressionEnabled })
    if (customProgressionEnabled && (customProgression == null || !customProgression.isEditorValid())) return null
    return try { validatePrescription(exercise.prescription()); exercise } catch (_: IllegalArgumentException) { null }
}

internal fun steppedWeight(value: String, direction: Int): String {
    val current = value.toIntOrNull()
    if (value.isBlank()) return if (direction > 0) "35" else ""
    if (current == null || current <= 0 || current % 5 != 0 || direction == 0) return value
    val next = current.toLong() + direction.coerceIn(-1, 1) * 5L
    return when {
        next <= 0L -> ""
        next > 2_147_483_645L -> value
        else -> next.toString()
    }
}

internal fun steppedPositive(value: String, direction: Int, defaultValue: Int, step: Int = 1): String {
    val current = value.toIntOrNull()
    if (value.isBlank()) return if (direction > 0) defaultValue.toString() else ""
    if (current == null || current <= 0 || direction == 0) return value
    val next = current.toLong() + direction.coerceIn(-1, 1) * step.toLong()
    return when {
        next <= 0L -> ""
        next > Int.MAX_VALUE -> value
        else -> next.toString()
    }
}

internal fun Routine.withExercise(exercise: Exercise): Routine? {
    if (execution != RoutineExecution.GUIDED) return null
    val next = if (exercises.any { it.id == exercise.id }) {
        exercises.map { if (it.id == exercise.id) exercise else it }
    } else exercises + exercise
    val identities = next.flatMap(Exercise::progressionIdentityIds)
    if (identities.distinct().size != identities.size) return null
    return if (next == exercises) this else copy(revision = revision + 1, exercises = next)
}

/** Live roots and every not-yet-inserted custom child share one routine-wide identity namespace. */
internal fun Exercise.progressionIdentityIds(): List<String> = buildList {
    fun collect(candidate: Exercise) {
        add(candidate.id)
        (candidate.progression as? CustomExerciseProgression)?.steps?.forEach { step ->
            step.insertedExercises.forEach(::collect)
        }
    }
    collect(this@progressionIdentityIds)
}

internal fun Routine.moveExercise(exerciseId: String, offset: Int): Routine {
    val moved = move(exercises, exercises.indexOfFirst { it.id == exerciseId }, offset)
    return if (moved == exercises) this else copy(revision = revision + 1, exercises = moved)
}

internal fun Routine.withoutExercise(exerciseId: String, allowEmpty: Boolean = false): Routine? {
    if (execution != RoutineExecution.GUIDED) return null
    val next = exercises.filterNot { it.id == exerciseId }
    if (next.size == exercises.size || (!allowEmpty && next.isEmpty())) return null
    return copy(revision = revision + 1, exercises = next)
}

internal fun Routine.withGuidedIdentity(name: String, artworkId: String): Routine? {
    val cleanName = name.trim()
    if (execution != RoutineExecution.GUIDED || cleanName.isEmpty() || cleanName.length > 200) return null
    if (cleanName == this.name && RoutineArtworkCatalog.resolve(artworkId).storageId == this.artworkId) return this
    return copy(
        revision = revision + 1,
        name = cleanName,
        artworkId = RoutineArtworkCatalog.resolve(artworkId).storageId,
    )
}

internal fun Routine.withGuidedArtwork(artworkId: String): Routine? {
    if (execution != RoutineExecution.GUIDED) return null
    val resolved = RoutineArtworkCatalog.resolve(artworkId).storageId
    return if (resolved == this.artworkId) this else copy(revision = revision + 1, artworkId = resolved)
}

internal fun Routine.forEditorSave(original: Routine, isNew: Boolean): Routine =
    copy(revision = if (isNew) 1 else if (copy(revision = original.revision) == original) original.revision else original.revision + 1)

internal fun Routine.isSaveableGuidedRoutine(): Boolean =
    execution == RoutineExecution.GUIDED && name.trim().isNotEmpty() && name.trim().length <= 200 && exercises.isNotEmpty()

// Drafts may have no name/exercises yet; canonical validation still runs at Save.
internal val RoutineDraftSaver = Saver<Routine, String>(
    save = { encodeRoutine(it).toString() },
    restore = { decodeRoutine(org.json.JSONObject(it)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuidedRoutineEditorScreen(
    routine: Routine,
    scheduleSummary: String,
    isNew: Boolean,
    hapticsEnabled: Boolean = true,
    onPersist: (Routine) -> Boolean,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var working by rememberSaveable(routine.id, stateSaver = RoutineDraftSaver) { mutableStateOf(routine) }
    LaunchedEffect(routine) { if (!isNew) working = routine }
    var editingExerciseId by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var addingExercise by rememberSaveable(routine.id) { mutableStateOf(false) }
    var renaming by rememberSaveable(routine.id) { mutableStateOf(false) }
    var artworkPicker by rememberSaveable(routine.id) { mutableStateOf(false) }
    var deleteRoutine by rememberSaveable(routine.id) { mutableStateOf(false) }
    var deleteExerciseId by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var discardRequested by rememberSaveable(routine.id) { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var actionMessage by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf<Routine?>(null) }
    var pendingFocusId by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { 54.dp.toPx() }
    val changed = working != routine

    fun applyChange(updated: Routine, message: String = "Changes saved."): Boolean {
        if (updated == working) return true
        if (isNew || onPersist(updated)) {
            working = updated
            actionMessage = if (isNew) "Draft updated · not saved" else message
            return true
        }
        actionMessage = "Couldn’t save changes. Try again."
        return false
    }

    val requestBack = {
        if (isNew && changed) discardRequested = true else onBack()
    }
    BackHandler(onBack = requestBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            SecondaryTopBar("Routine editor", requestBack) {
                Box {
                    IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isNew) "Discard routine" else "Delete routine") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { overflowExpanded = false; if (isNew) discardRequested = true else deleteRoutine = true },
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("GUIDED ROUTINE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            working.name.ifBlank { "New routine" },
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${working.exercises.size} ${if (working.exercises.size == 1) "exercise" else "exercises"} · $scheduleSummary",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (LocalDensity.current.fontScale <= 1.3f && LocalConfiguration.current.screenWidthDp >= 360) Image(
                        painter = painterResource(RoutineArtworkCatalog.resolve(working.artworkId).resource(RoutineArtworkCrop.HEADER)),
                        contentDescription = null,
                        modifier = Modifier.size(110.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                RoutineIdentityActions(onRename = { renaming = true }, onChangeArtwork = { artworkPicker = true })
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("EXERCISES", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Text("Drag to reorder", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (working.exercises.isEmpty()) item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("No exercises yet", fontWeight = FontWeight.Bold)
                        Text("Add one valid exercise to save this guided routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        working.exercises.forEachIndexed { index, exercise ->
                            key(exercise.id) {
                            val requester = remember(exercise.id) { FocusRequester() }
                            LaunchedEffect(pendingFocusId, index) {
                                if (pendingFocusId == exercise.id) {
                                    requester.requestFocus()
                                    pendingFocusId = null
                                }
                            }
                            ManagedExerciseRow(
                                exercise = exercise,
                                position = index,
                                total = working.exercises.size,
                                focusRequester = requester,
                                dragged = draggedId == exercise.id,
                                onEdit = { editingExerciseId = exercise.id },
                                onDelete = { deleteExerciseId = exercise.id },
                                onMove = { offset ->
                                    pendingFocusId = exercise.id
                                    applyChange(working.moveExercise(exercise.id, offset), "Exercise order saved.")
                                },
                                onDragStart = {
                                    dragOrigin = working
                                    draggedId = exercise.id
                                    pendingFocusId = exercise.id
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStep = { offset -> working = working.moveExercise(exercise.id, offset) },
                                onDragEnd = {
                                    val origin = dragOrigin
                                    if (origin != null) working = working.forEditorSave(origin, isNew)
                                    draggedId = null
                                    dragOrigin = null
                                    if (!isNew && origin != null && origin.exercises != working.exercises && !onPersist(working)) {
                                        working = origin
                                        actionMessage = "Couldn’t save the new order."
                                    } else if (origin != null && origin.exercises != working.exercises) {
                                        actionMessage = if (isNew) "Draft order updated · not saved" else "Exercise order saved."
                                    }
                                },
                                onDragCancel = { dragOrigin?.let { working = it }; dragOrigin = null; draggedId = null },
                                dragThreshold = dragThreshold,
                            )
                            if (index < working.exercises.lastIndex) HorizontalDivider(color = AppBorder)
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { addingExercise = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
                Text(
                    if (isNew) "Save the routine when it is ready" else "Changes save automatically",
                    color = AppMint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                actionMessage?.let { Text(it, color = AppMint, style = MaterialTheme.typography.bodySmall) }
            }
            if (isNew) item {
                Button(
                    enabled = working.isSaveableGuidedRoutine(),
                    onClick = { if (onPersist(working.forEditorSave(routine, isNew = true))) onBack() else actionMessage = "Couldn’t save routine. Try again." },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Save routine") }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    val editing = working.exercises.firstOrNull { it.id == editingExerciseId }
    if (addingExercise || editing != null) ExerciseEditorScreen(
        original = editing,
        onDismiss = { addingExercise = false; editingExerciseId = null },
        onSave = { exercise ->
            if (working.withExercise(exercise)?.let { applyChange(it, "${exercise.name} saved.") } == true) {
                addingExercise = false
                editingExerciseId = null
                pendingFocusId = exercise.id
            }
        },
    )
    if (renaming) RoutineNameDialog(
        initial = working.name,
        onDismiss = { renaming = false },
        onSave = { name -> if (working.withGuidedIdentity(name, working.artworkId)?.let(::applyChange) == true) renaming = false },
    )
    if (artworkPicker) RoutineArtworkPickerSheet(
        selectedId = working.artworkId,
        onDismiss = { artworkPicker = false },
        onDone = { id -> if (working.withGuidedArtwork(id)?.let(::applyChange) == true) artworkPicker = false },
    )
    deleteExerciseId?.let { id ->
        val exercise = working.exercises.firstOrNull { it.id == id }
        if (exercise != null) AppConfirmationDialog(
            title = "Delete ${exercise.name}?",
            message = when {
                working.exercises.size == 1 && !isNew -> "A guided routine must keep at least one exercise."
                exercise.progression is CustomExerciseProgression -> "This removes the exercise and its not-yet-added custom progression exercises. Exercises already added by an earlier progression stay independent."
                else -> "This removes only this exercise from ${working.name.ifBlank { "this routine" }}. Exercises already added by progression stay independent."
            },
            confirmLabel = "Delete exercise",
            onConfirm = {
                working.withoutExercise(id, allowEmpty = isNew)?.let { applyChange(it, "${exercise.name} deleted.") }
                deleteExerciseId = null
            },
            onDismiss = { deleteExerciseId = null },
        )
    }
    if (deleteRoutine) AppConfirmationDialog(
        title = "Delete ${working.name}?",
        message = "This deletes the routine, every scheduled entry that references it, and its saved incomplete sessions. Completed workout history remains available.",
        confirmLabel = "Delete routine",
        onConfirm = onDelete,
        onDismiss = { deleteRoutine = false },
    )
    if (discardRequested) AppConfirmationDialog(
        title = "Discard this routine draft?",
        message = "Nothing from this guided routine has been saved.",
        confirmLabel = "Discard draft",
        onConfirm = onBack,
        onDismiss = { discardRequested = false },
    )
}

@Composable
private fun ManagedExerciseRow(
    exercise: Exercise,
    position: Int,
    total: Int,
    focusRequester: FocusRequester,
    dragged: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Boolean,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    dragThreshold: Float,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onDragStart)
    val currentStep by rememberUpdatedState(onDragStep)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    Row(
        Modifier.fillMaxWidth().background(if (dragged) AppBlue.copy(alpha = .10f) else Color.Transparent)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) false
                else when (it.key) { Key.DirectionUp -> onMove(-1); Key.DirectionDown -> onMove(1); else -> false }
            }
            .focusRequester(focusRequester).focusable().semantics {
                stateDescription = "${exercise.name}, ${exercise.setCount} sets, ${exercise.targetSummary()}, ${if (exercise.durationSeconds == null) "not timed" else "${exercise.durationSeconds} second timer"}, position ${position + 1} of $total"
                customActions = buildList {
                    if (position > 0) add(CustomAccessibilityAction("Move ${exercise.name} up") { onMove(-1) })
                    if (position < total - 1) add(CustomAccessibilityAction("Move ${exercise.name} down") { onMove(1) })
                }
            }.clickable(onClickLabel = "Edit ${exercise.name}", onClick = onEdit).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = "Reorder ${exercise.name}, position ${position + 1} of $total",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).pointerInput(exercise.id, total) {
                var distance = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { distance = 0f; currentStart() },
                    onDragCancel = { currentCancel() },
                    onDragEnd = { currentEnd() },
                ) { change, amount ->
                    change.consume()
                    distance += amount.y
                    while (distance <= -dragThreshold) { currentStep(-1); distance += dragThreshold }
                    while (distance >= dragThreshold) { currentStep(1); distance -= dragThreshold }
                }
            },
        )
        if (LocalDensity.current.fontScale <= 1.3f) {
            Image(
                painter = painterResource(ExerciseArtworkCatalog.resolve(exercise.artworkId).resource(ExerciseArtworkCrop.LIST)),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(exercise.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${exercise.setCount} sets · ${exercise.targetSummary()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (exercise.durationSeconds != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = AppBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${exercise.durationSeconds}s timer", color = AppBlue, style = MaterialTheme.typography.labelSmall)
            }
            if (exercise.notes.isNotBlank()) Text(exercise.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "More options for ${exercise.name}") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.semantics { contentDescription = "Edit ${exercise.name}" }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, modifier = Modifier.semantics { contentDescription = "Delete ${exercise.name}" }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseEditorScreen(
    original: Exercise?,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit,
    initialScrollPx: Int = 0,
) {
    val draftId = rememberSaveable(original?.id) { original?.id ?: newId() }
    val initial = remember(draftId) { original?.exerciseDraft() ?: ExerciseDraft(draftId) }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var notes by rememberSaveable(initial.id) { mutableStateOf(initial.notes) }
    var sets by rememberSaveable(initial.id) { mutableStateOf(initial.sets) }
    var reps by rememberSaveable(initial.id) { mutableStateOf(initial.reps) }
    var timed by rememberSaveable(initial.id) { mutableStateOf(initial.timed) }
    var seconds by rememberSaveable(initial.id) { mutableStateOf(initial.durationSeconds) }
    var artworkId by rememberSaveable(initial.id) { mutableStateOf(initial.artworkId) }
    var currentPounds by rememberSaveable(initial.id) { mutableStateOf(initial.currentPounds) }
    var customProgressionEnabled by rememberSaveable(initial.id) { mutableStateOf(initial.customProgressionEnabled) }
    var customProgression by rememberSaveable(initial.id, stateSaver = NullableCustomProgressionStateSaver) { mutableStateOf(initial.customProgression) }
    var editingCustomProgression by rememberSaveable(initial.id) { mutableStateOf(false) }
    var artworkPicker by rememberSaveable(initial.id) { mutableStateOf(false) }
    val draft = ExerciseDraft(
        id = initial.id,
        name = name,
        notes = notes,
        sets = sets,
        reps = reps,
        timed = timed,
        durationSeconds = seconds,
        artworkId = artworkId,
        currentPounds = currentPounds,
        customProgressionEnabled = customProgressionEnabled,
        customProgression = customProgression,
    )
    val candidate = draft.savedExercise()
    var confirmDiscard by rememberSaveable(initial.id) { mutableStateOf(false) }
    val requestBack = { if (draft != initial) confirmDiscard = true else onDismiss() }
    BackHandler(onBack = requestBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(if (original == null) "Add exercise" else "Edit exercise", requestBack) },
        containerColor = AppBackground,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState(initial = initialScrollPx)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { if (it.length <= 200) name = it }, Modifier.fillMaxWidth(), label = { Text("Exercise name") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(notes, { if (it.length <= 4_000) notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
            AppSurfaceCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(ExerciseArtworkCatalog.resolve(artworkId).resource(ExerciseArtworkCrop.LIST)), null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(ExerciseArtworkCatalog.resolve(artworkId).displayNameRes), fontWeight = FontWeight.Bold)
                        Text("Paired list and session artwork", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { artworkPicker = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change") }
                }
            }
            Text("TARGETS", color = AppGold, style = MaterialTheme.typography.labelMedium)
            StructuredTargetControls(
                weight = currentPounds,
                onWeightChange = { currentPounds = it },
                durationEnabled = timed,
                onDurationEnabledChange = { timed = it },
                duration = seconds,
                onDurationChange = { seconds = it },
                sets = sets,
                onSetsChange = { sets = it },
                reps = reps,
                onRepsChange = { reps = it },
            )
            CustomProgressionControl(
                enabled = customProgressionEnabled,
                onEnabledChange = { customProgressionEnabled = it },
                progression = customProgression,
                onConfigure = { editingCustomProgression = true },
                canConfigure = draft.copy(customProgressionEnabled = false, customProgression = null).savedExercise() != null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = requestBack, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                Button(enabled = candidate != null, onClick = { candidate?.let(onSave) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save exercise") }
            }
        }
    }
    if (confirmDiscard) AppConfirmationDialog(
        title = "Discard exercise changes?",
        message = "These exercise changes have not been saved.",
        confirmLabel = "Discard changes",
        onConfirm = onDismiss,
        onDismiss = { confirmDiscard = false },
    )
    if (artworkPicker) ExerciseArtworkPickerSheet(
        selectedId = artworkId,
        onDismiss = { artworkPicker = false },
        onDone = { artworkId = it; artworkPicker = false },
    )
    if (editingCustomProgression) {
        val source = draft.copy(customProgressionEnabled = false, customProgression = null).savedExercise()
        if (source != null) CustomProgressionEditorScreen(
            source = source,
            initial = customProgression,
            onDismiss = { editingCustomProgression = false },
            onSave = { customProgression = it; customProgressionEnabled = true; editingCustomProgression = false },
        ) else editingCustomProgression = false
    }
}

@Composable
internal fun StructuredTargetControls(
    weight: String,
    onWeightChange: (String) -> Unit,
    durationEnabled: Boolean,
    onDurationEnabledChange: (Boolean) -> Unit,
    duration: String,
    onDurationChange: (String) -> Unit,
    sets: String,
    onSetsChange: (String) -> Unit,
    reps: String,
    onRepsChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TargetStepper("Weight", "lb · optional", weight.ifBlank { "Not set" },
            decrementLabel = "Decrease weight by 5 pounds", incrementLabel = "Increase weight by 5 pounds",
            onDecrement = { onWeightChange(steppedWeight(weight, -1)) }, onIncrement = { onWeightChange(steppedWeight(weight, 1)) },
            onClear = if (weight.isNotBlank()) {{ onWeightChange("") }} else null)
        TargetStepper("Duration", "seconds · controls the timer", if (durationEnabled) duration else "Not timed",
            decrementLabel = "Decrease duration by 5 seconds", incrementLabel = "Increase duration by 5 seconds",
            onDecrement = { if (durationEnabled) {
                val next = steppedPositive(duration, -1, 20, 5)
                if (next.isBlank()) onDurationEnabledChange(false) else onDurationChange(next)
            } },
            onIncrement = { if (!durationEnabled) { onDurationEnabledChange(true); onDurationChange("20") } else onDurationChange(steppedPositive(duration, 1, 20, 5)) },
            onClear = if (durationEnabled) {{ onDurationEnabledChange(false) }} else null)
        TargetStepper("Sets", "required", sets.ifBlank { "Not set" },
            decrementLabel = "Decrease sets by 1", incrementLabel = "Increase sets by 1",
            onDecrement = { steppedPositive(sets, -1, 3).takeIf { it.isNotBlank() }?.let(onSetsChange) },
            onIncrement = { onSetsChange(steppedPositive(sets, 1, 3)) })
        TargetStepper("Reps", "optional", reps.ifBlank { "Not set" },
            decrementLabel = "Decrease reps by 1", incrementLabel = "Increase reps by 1",
            onDecrement = { onRepsChange(steppedPositive(reps, -1, 10)) }, onIncrement = { onRepsChange(steppedPositive(reps, 1, 10)) },
            onClear = if (reps.isNotBlank()) {{ onRepsChange("") }} else null)
    }
}

@Composable
private fun TargetStepper(
    title: String,
    unit: String,
    value: String,
    decrementLabel: String,
    incrementLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    AppSurfaceCard(Modifier.fillMaxWidth().semantics { stateDescription = "$title, $value $unit" }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            onClear?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Clear $title" }) { Text("Clear") } }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onDecrement, modifier = Modifier.size(48.dp).semantics { contentDescription = decrementLabel }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−") }
                Text(value, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    .semantics { contentDescription = "$title value $value" },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onIncrement, modifier = Modifier.size(48.dp).semantics { contentDescription = incrementLabel }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseArtworkPickerSheet(selectedId: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var pending by rememberSaveable(selectedId) { mutableStateOf(ExerciseArtworkCatalog.resolve(selectedId).storageId) }
    PersistentSelectionSheet(
        title = "Choose exercise artwork",
        description = "One choice supplies the paired list and session images.",
        onDismiss = onDismiss,
        onSave = { onDone(pending) },
    ) {
        ExerciseArtworkSelection(pending) { pending = it }
    }
}

@Composable
internal fun ExerciseArtworkSelection(pending: String, onSelect: (String) -> Unit) {
            ExerciseArtworkCatalog.entries.chunked(2).forEach { rowAssets ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowAssets.forEach { asset ->
                        val chosen = asset.storageId == pending
                        val label = stringResource(asset.displayNameRes)
                        Card(
                            onClick = { onSelect(asset.storageId) },
                            modifier = Modifier.weight(1f).heightIn(min = 174.dp).semantics {
                                selected = chosen
                                contentDescription = "$label, ${if (chosen) "Selected" else "Not selected"}"
                            },
                            colors = CardDefaults.cardColors(containerColor = AppSurfaceRaised),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("LIST", style = MaterialTheme.typography.labelSmall, color = AppGold)
                                        Image(painterResource(asset.resource(ExerciseArtworkCrop.LIST)), null, Modifier.size(62.dp), contentScale = ContentScale.Fit)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("HEADER", style = MaterialTheme.typography.labelSmall, color = AppGold)
                                        Image(painterResource(asset.resource(ExerciseArtworkCrop.HEADER)), null, Modifier.size(62.dp), contentScale = ContentScale.Fit)
                                    }
                                }
                                Text(label, style = MaterialTheme.typography.labelMedium)
                                if (chosen) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, null, tint = AppMint, modifier = Modifier.size(16.dp))
                                    Text("Selected", color = AppMint, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (rowAssets.size == 1) Spacer(Modifier.weight(1f))
                }
            }
}
