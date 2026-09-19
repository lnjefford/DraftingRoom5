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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ExerciseDraft(
    val id: String,
    val name: String = "",
    val notes: String = "",
    val sets: String = "",
    val target: String = "",
    val timed: Boolean = false,
    val timerSeconds: String = "20",
    val artworkId: String = ExerciseArtworkCatalog.FALLBACK_ID,
    val automaticProgression: Boolean = false,
    val weightProgression: Boolean = false,
    val currentPounds: String = "",
    val poundsIncrement: String = "",
    val minimumPounds: String = "",
    val maximumPounds: String = "",
    val durationProgression: Boolean = false,
    val currentDurationSeconds: String = "",
    val secondsIncrement: String = "",
    val minimumSeconds: String = "",
    val maximumSeconds: String = "",
    val customProgressionEnabled: Boolean = false,
    val customProgression: CustomExerciseProgression? = null,
)

internal fun Exercise.exerciseDraft(): ExerciseDraft {
    val automatic = progression as? AutomaticExerciseProgression
    return ExerciseDraft(
        id = id,
        name = name,
        notes = notes,
        sets = setCount.toString(),
        target = target,
        timed = timerSeconds != null,
        timerSeconds = timerSeconds?.toString() ?: "20",
        artworkId = artworkId,
        automaticProgression = automatic != null,
        weightProgression = automatic?.weightPounds != null,
        currentPounds = measurements.weightPounds?.let(::formatPounds).orEmpty(),
        poundsIncrement = automatic?.weightPounds?.increment?.let(::formatPounds).orEmpty(),
        minimumPounds = automatic?.weightPounds?.minimum?.let(::formatPounds).orEmpty(),
        maximumPounds = automatic?.weightPounds?.maximum?.let(::formatPounds).orEmpty(),
        durationProgression = automatic?.durationSeconds != null,
        currentDurationSeconds = measurements.durationSeconds?.toString().orEmpty(),
        secondsIncrement = automatic?.durationSeconds?.increment?.toString().orEmpty(),
        minimumSeconds = automatic?.durationSeconds?.minimum?.toString().orEmpty(),
        maximumSeconds = automatic?.durationSeconds?.maximum?.toString().orEmpty(),
        customProgressionEnabled = progression is CustomExerciseProgression,
        customProgression = progression as? CustomExerciseProgression,
    )
}

internal fun ExerciseDraft.savedExercise(): Exercise? {
    val cleanName = name.trim()
    val cleanNotes = notes.trim()
    val cleanTarget = target.trim()
    val setCount = sets.toIntOrNull()
    val configuredSeconds = timerSeconds.toIntOrNull()
    val durationRuleSelected = automaticProgression && durationProgression
    if (id.isBlank() || cleanName.isEmpty() || cleanName.length > 200 || cleanNotes.length > 4_000) return null
    if (setCount == null || setCount <= 0 || cleanTarget.isEmpty() || cleanTarget.length > 500) return null
    if (timed && !durationRuleSelected && (configuredSeconds == null || configuredSeconds <= 0)) return null

    val weightRule = if (automaticProgression && weightProgression) {
        val current = currentPounds.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val increment = poundsIncrement.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val minimum = optionalPositiveDouble(minimumPounds) ?: return null
        val maximum = optionalPositiveDouble(maximumPounds) ?: return null
        if (minimum.value != null && current < minimum.value || maximum.value != null && current > maximum.value ||
            minimum.value != null && maximum.value != null && minimum.value > maximum.value) return null
        AutomaticPoundsProgression(increment, minimum.value, maximum.value)
    } else null
    val durationRule = if (durationRuleSelected) {
        val current = currentDurationSeconds.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val increment = secondsIncrement.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val minimum = optionalPositiveInt(minimumSeconds) ?: return null
        val maximum = optionalPositiveInt(maximumSeconds) ?: return null
        if (minimum.value != null && current < minimum.value || maximum.value != null && current > maximum.value ||
            minimum.value != null && maximum.value != null && minimum.value > maximum.value) return null
        AutomaticSecondsProgression(increment, minimum.value, maximum.value)
    } else null
    if (automaticProgression && weightRule == null && durationRule == null) return null
    val customWeight = optionalPositiveDouble(currentPounds) ?: return null
    val customDuration = optionalPositiveInt(currentDurationSeconds) ?: return null
    if (customProgressionEnabled && (customProgression == null || !customProgression.isEditorValid())) return null
    val structuredDuration = if (durationRule != null) currentDurationSeconds.toInt() else customDuration.value
    val seconds = if (durationRule != null) structuredDuration else configuredSeconds.takeIf { timed }
    return Exercise(
        id = id,
        name = cleanName,
        notes = cleanNotes,
        setCount = setCount,
        target = cleanTarget,
        timerSeconds = seconds,
        artworkId = ExerciseArtworkCatalog.resolve(artworkId).storageId,
        measurements = ExerciseMeasurements(
            weightPounds = if (weightRule != null) currentPounds.toDouble() else customWeight.value,
            durationSeconds = structuredDuration,
        ),
        progression = when {
            automaticProgression -> AutomaticExerciseProgression(weightRule, durationRule)
            customProgressionEnabled -> customProgression
            else -> null
        },
    )
}

private data class OptionalValue<T>(val value: T?)

private fun optionalPositiveDouble(text: String): OptionalValue<Double>? {
    if (text.isBlank()) return OptionalValue(null)
    val value = text.toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && it > 0.0 }?.let { OptionalValue(it) }
}

private fun optionalPositiveInt(text: String): OptionalValue<Int>? {
    if (text.isBlank()) return OptionalValue(null)
    return text.toIntOrNull()?.takeIf { it > 0 }?.let { OptionalValue(it) }
}

private fun formatPounds(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun ExerciseDraft.nextAutomaticPrescriptionPreview(): String? {
    val exercise = savedExercise() ?: return null
    if (exercise.progression !is AutomaticExerciseProgression) return null
    return exercise.progressionOptions().joinToString("\n") {
        "${progressionChoiceLabel(it.choice)}: ${it.source.measurementSummary()}"
    }.ifEmpty { "At configured limits" }
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
                stateDescription = "${exercise.name}, ${exercise.setCount} sets, ${exercise.targetSummary()}, ${if (exercise.timerSeconds == null) "not timed" else "${exercise.timerSeconds} second timer"}, position ${position + 1} of $total"
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
            if (exercise.timerSeconds != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = AppBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${exercise.timerSeconds}s timer", color = AppBlue, style = MaterialTheme.typography.labelSmall)
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
    var target by rememberSaveable(initial.id) { mutableStateOf(initial.target) }
    var timed by rememberSaveable(initial.id) { mutableStateOf(initial.timed) }
    var seconds by rememberSaveable(initial.id) { mutableStateOf(initial.timerSeconds) }
    var artworkId by rememberSaveable(initial.id) { mutableStateOf(initial.artworkId) }
    var automaticProgression by rememberSaveable(initial.id) { mutableStateOf(initial.automaticProgression) }
    var weightProgression by rememberSaveable(initial.id) { mutableStateOf(initial.weightProgression) }
    var currentPounds by rememberSaveable(initial.id) { mutableStateOf(initial.currentPounds) }
    var poundsIncrement by rememberSaveable(initial.id) { mutableStateOf(initial.poundsIncrement) }
    var minimumPounds by rememberSaveable(initial.id) { mutableStateOf(initial.minimumPounds) }
    var maximumPounds by rememberSaveable(initial.id) { mutableStateOf(initial.maximumPounds) }
    var durationProgression by rememberSaveable(initial.id) { mutableStateOf(initial.durationProgression) }
    var currentDurationSeconds by rememberSaveable(initial.id) { mutableStateOf(initial.currentDurationSeconds) }
    var secondsIncrement by rememberSaveable(initial.id) { mutableStateOf(initial.secondsIncrement) }
    var minimumSeconds by rememberSaveable(initial.id) { mutableStateOf(initial.minimumSeconds) }
    var maximumSeconds by rememberSaveable(initial.id) { mutableStateOf(initial.maximumSeconds) }
    var customProgressionEnabled by rememberSaveable(initial.id) { mutableStateOf(initial.customProgressionEnabled) }
    var customProgression by rememberSaveable(initial.id, stateSaver = NullableCustomProgressionStateSaver) { mutableStateOf(initial.customProgression) }
    var editingCustomProgression by rememberSaveable(initial.id) { mutableStateOf(false) }
    var artworkPicker by rememberSaveable(initial.id) { mutableStateOf(false) }
    val draft = ExerciseDraft(
        id = initial.id,
        name = name,
        notes = notes,
        sets = sets,
        target = target,
        timed = timed,
        timerSeconds = seconds,
        artworkId = artworkId,
        automaticProgression = automaticProgression,
        weightProgression = weightProgression,
        currentPounds = currentPounds,
        poundsIncrement = poundsIncrement,
        minimumPounds = minimumPounds,
        maximumPounds = maximumPounds,
        durationProgression = durationProgression,
        currentDurationSeconds = currentDurationSeconds,
        secondsIncrement = secondsIncrement,
        minimumSeconds = minimumSeconds,
        maximumSeconds = maximumSeconds,
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
            OutlinedTextField(sets, { sets = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Sets") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(target, { if (it.length <= 500) target = it }, Modifier.fillMaxWidth(), label = { Text("Target") }, singleLine = true)
            OutlinedTextField(notes, { if (it.length <= 4_000) notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Timed exercise", Modifier.weight(1f))
                Switch(
                    checked = timed || automaticProgression && durationProgression,
                    enabled = !(automaticProgression && durationProgression),
                    onCheckedChange = { timed = it },
                    modifier = Modifier.semantics {
                        stateDescription = if (automaticProgression && durationProgression) "On, synchronized with current duration" else if (timed) "On" else "Off"
                    },
                )
            }
            if (timed && !(automaticProgression && durationProgression)) OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Timer seconds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            AutomaticProgressionEditor(
                enabled = automaticProgression,
                onEnabledChange = { enabled ->
                    automaticProgression = enabled
                    if (enabled) customProgressionEnabled = false
                    if (!enabled) {
                        weightProgression = false
                        durationProgression = false
                    }
                },
                weightEnabled = weightProgression,
                onWeightEnabledChange = { weightProgression = it },
                currentPounds = currentPounds,
                onCurrentPoundsChange = { currentPounds = it.decimalInput() },
                poundsIncrement = poundsIncrement,
                onPoundsIncrementChange = { poundsIncrement = it.decimalInput() },
                minimumPounds = minimumPounds,
                onMinimumPoundsChange = { minimumPounds = it.decimalInput() },
                maximumPounds = maximumPounds,
                onMaximumPoundsChange = { maximumPounds = it.decimalInput() },
                durationEnabled = durationProgression,
                onDurationEnabledChange = { enabled ->
                    durationProgression = enabled
                    if (enabled) {
                        timed = true
                        if (currentDurationSeconds.isBlank()) currentDurationSeconds = seconds
                    }
                },
                currentDurationSeconds = currentDurationSeconds,
                onCurrentDurationSecondsChange = { value -> currentDurationSeconds = value.filter(Char::isDigit); seconds = value.filter(Char::isDigit) },
                secondsIncrement = secondsIncrement,
                onSecondsIncrementChange = { secondsIncrement = it.filter(Char::isDigit) },
                minimumSeconds = minimumSeconds,
                onMinimumSecondsChange = { minimumSeconds = it.filter(Char::isDigit) },
                maximumSeconds = maximumSeconds,
                onMaximumSecondsChange = { maximumSeconds = it.filter(Char::isDigit) },
                preview = draft.nextAutomaticPrescriptionPreview(),
            )
            if (!automaticProgression && !customProgressionEnabled &&
                (initial.currentPounds.isNotBlank() || initial.currentDurationSeconds.isNotBlank() ||
                    currentPounds.isNotBlank() || currentDurationSeconds.isNotBlank())) {
                Text("Current measurements", fontWeight = FontWeight.Bold)
                ProgressionNumberField(currentPounds, { currentPounds = it }, "Current pounds (optional)", KeyboardType.Decimal)
                ProgressionNumberField(currentDurationSeconds, { currentDurationSeconds = it }, "Current seconds (optional)", KeyboardType.Number)
            }
            CustomProgressionControl(
                enabled = customProgressionEnabled,
                onEnabledChange = { enabled ->
                    customProgressionEnabled = enabled
                    if (enabled) {
                        automaticProgression = false
                        weightProgression = false
                        durationProgression = false
                    }
                },
                currentPounds = currentPounds,
                onCurrentPoundsChange = { currentPounds = it.decimalInput() },
                currentDurationSeconds = currentDurationSeconds,
                onCurrentDurationSecondsChange = { currentDurationSeconds = it.filter(Char::isDigit) },
                progression = customProgression,
                onConfigure = { editingCustomProgression = true },
                canConfigure = draft.copy(customProgressionEnabled = false, customProgression = null).savedExercise() != null,
            )
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
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

private fun String.decimalInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val point = filtered.indexOf('.')
    return if (point < 0) filtered else filtered.take(point + 1) + filtered.drop(point + 1).replace(".", "")
}

@Composable
private fun AutomaticProgressionEditor(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    weightEnabled: Boolean,
    onWeightEnabledChange: (Boolean) -> Unit,
    currentPounds: String,
    onCurrentPoundsChange: (String) -> Unit,
    poundsIncrement: String,
    onPoundsIncrementChange: (String) -> Unit,
    minimumPounds: String,
    onMinimumPoundsChange: (String) -> Unit,
    maximumPounds: String,
    onMaximumPoundsChange: (String) -> Unit,
    durationEnabled: Boolean,
    onDurationEnabledChange: (Boolean) -> Unit,
    currentDurationSeconds: String,
    onCurrentDurationSecondsChange: (String) -> Unit,
    secondsIncrement: String,
    onSecondsIncrementChange: (String) -> Unit,
    minimumSeconds: String,
    onMinimumSecondsChange: (String) -> Unit,
    maximumSeconds: String,
    onMaximumSecondsChange: (String) -> Unit,
    preview: String?,
) {
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic progression", fontWeight = FontWeight.Bold)
                    Text("Configure this exercise only", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { stateDescription = if (enabled) "Enabled" else "Disabled" },
                )
            }
            if (!enabled) {
                Text("Off · the existing target and timer stay unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("Choose weight, duration, or both. Bounds are optional.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            ProgressionMeasureHeader("Weight", "Pounds only", weightEnabled, onWeightEnabledChange)
            if (weightEnabled) {
                ProgressionNumberField(currentPounds, onCurrentPoundsChange, "Current pounds", KeyboardType.Decimal)
                ProgressionNumberField(poundsIncrement, onPoundsIncrementChange, "Increase by pounds", KeyboardType.Decimal)
                ProgressionBoundsFields(
                    minimumPounds, onMinimumPoundsChange, maximumPounds, onMaximumPoundsChange, KeyboardType.Decimal,
                )
            }
            HorizontalDivider(color = AppBorder)
            ProgressionMeasureHeader("Duration", "Seconds", durationEnabled, onDurationEnabledChange)
            if (durationEnabled) {
                Text("Current seconds also controls the exercise timer.", color = AppMint, style = MaterialTheme.typography.bodySmall)
                ProgressionNumberField(currentDurationSeconds, onCurrentDurationSecondsChange, "Current seconds", KeyboardType.Number)
                ProgressionNumberField(secondsIncrement, onSecondsIncrementChange, "Increase by seconds", KeyboardType.Number)
                ProgressionBoundsFields(
                    minimumSeconds, onMinimumSecondsChange, maximumSeconds, onMaximumSecondsChange, KeyboardType.Number,
                )
            }
            AppSurfaceCard(Modifier.fillMaxWidth().semantics {
                contentDescription = preview?.let { "Next prescription, $it" } ?: "Next prescription unavailable until progression is valid"
            }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("NEXT PRESCRIPTION", color = AppGold, style = MaterialTheme.typography.labelSmall)
                    Text(preview ?: "Complete the selected progression fields", color = if (preview == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ProgressionBoundsFields(
    minimum: String,
    onMinimumChange: (String) -> Unit,
    maximum: String,
    onMaximumChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressionNumberField(minimum, onMinimumChange, "Minimum (optional)", keyboardType)
            ProgressionNumberField(maximum, onMaximumChange, "Maximum (optional)", keyboardType)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressionNumberField(minimum, onMinimumChange, "Minimum (optional)", keyboardType, Modifier.weight(1f))
            ProgressionNumberField(maximum, onMaximumChange, "Maximum (optional)", keyboardType, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgressionMeasureHeader(title: String, unit: String, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = "$title progression"; stateDescription = if (enabled) "Enabled" else "Disabled" },
        )
    }
}

@Composable
private fun ProgressionNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
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
