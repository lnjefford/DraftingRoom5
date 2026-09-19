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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun CustomExerciseProgression.isEditorValid(): Boolean {
    if (steps.isEmpty() || steps.any { !it.replacement.isEditorValid() }) return false
    val ids = mutableSetOf<String>()
    fun validExercise(exercise: Exercise): Boolean {
        if (!ids.add(exercise.id) || !exercise.prescription().isEditorValid()) return false
        return when (val nested = exercise.progression) {
            null -> true
            is AutomaticExerciseProgression ->
                (nested.weightPounds != null || nested.durationSeconds != null) &&
                    (nested.weightPounds == null || exercise.measurements.weightPounds != null) &&
                    (nested.durationSeconds == null || exercise.measurements.durationSeconds != null)
            is CustomExerciseProgression -> nested.steps.isNotEmpty() && nested.steps.all { step ->
                step.replacement.isEditorValid() && step.insertedExercises.all(::validExercise)
            }
        }
    }
    return steps.all { it.insertedExercises.all(::validExercise) }
}

private fun ExercisePrescription.isEditorValid(): Boolean =
    name.trim().isNotEmpty() && name.length <= 200 && notes.length <= 4_000 && setCount > 0 &&
        target.trim().isNotEmpty() && target.length <= 500 &&
        (timerSeconds == null || timerSeconds > 0) &&
        (measurements.weightPounds == null || measurements.weightPounds.isFinite() && measurements.weightPounds > 0.0) &&
        (measurements.durationSeconds == null || measurements.durationSeconds > 0)

internal fun CustomExerciseProgression.moveStep(index: Int, offset: Int): CustomExerciseProgression {
    val moved = move(steps, index, offset)
    return if (moved == steps) this else copy(steps = moved)
}

internal fun CustomProgressionStep.withInsertedExercise(exercise: Exercise): CustomProgressionStep {
    val next = if (insertedExercises.any { it.id == exercise.id }) {
        insertedExercises.map { if (it.id == exercise.id) exercise else it }
    } else insertedExercises + exercise
    return copy(insertedExercises = next)
}

internal fun CustomProgressionStep.moveInsertedExercise(id: String, offset: Int): CustomProgressionStep =
    copy(insertedExercises = move(insertedExercises, insertedExercises.indexOfFirst { it.id == id }, offset))

internal fun CustomProgressionStep.withoutInsertedExercise(id: String): CustomProgressionStep =
    copy(insertedExercises = insertedExercises.filterNot { it.id == id })

private data class PrescriptionDraft(
    val name: String,
    val notes: String,
    val sets: String,
    val target: String,
    val timed: Boolean,
    val timerSeconds: String,
    val artworkId: String,
    val weightPounds: String,
    val durationSeconds: String,
)

private fun ExercisePrescription.draft() = PrescriptionDraft(
    name, notes, setCount.toString(), target, timerSeconds != null, timerSeconds?.toString() ?: "20",
    artworkId, measurements.weightPounds?.let(::formatCustomPounds).orEmpty(), measurements.durationSeconds?.toString().orEmpty(),
)

private fun PrescriptionDraft.saved(): ExercisePrescription? {
    val count = sets.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val timer = if (timed) timerSeconds.toIntOrNull()?.takeIf { it > 0 } ?: return null else null
    val weight = if (weightPounds.isBlank()) null else weightPounds.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val duration = if (durationSeconds.isBlank()) null else durationSeconds.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return ExercisePrescription(
        name.trim(), notes.trim(), count, target.trim(), timer,
        ExerciseArtworkCatalog.resolve(artworkId).storageId, ExerciseMeasurements(weight, duration),
    ).takeIf { it.isEditorValid() }
}

private fun formatCustomPounds(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun ExercisePrescription.detail(): String = buildList {
    add("$setCount sets · $target")
    timerSeconds?.let { add("${it}s timer") }
    measurements.weightPounds?.let { add("${formatCustomPounds(it)} lb") }
    measurements.durationSeconds?.let { add("$it sec structured") }
}.joinToString(" · ")

@Composable
internal fun CustomProgressionControl(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    currentPounds: String,
    onCurrentPoundsChange: (String) -> Unit,
    currentDurationSeconds: String,
    onCurrentDurationSecondsChange: (String) -> Unit,
    progression: CustomExerciseProgression?,
    onConfigure: () -> Unit,
    canConfigure: Boolean,
) {
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Custom progression", fontWeight = FontWeight.Bold)
                    Text("Ordered grip or prescription changes", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics {
                    stateDescription = if (enabled) "Enabled" else "Disabled"
                })
            }
            if (!enabled) {
                Text("Off · use this for changes that are not a fixed numeric increment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("CURRENT STRUCTURED MEASURES", color = AppGold, style = MaterialTheme.typography.labelSmall)
            Text("Optional; the readable target and timer remain authoritative display fields.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(currentPounds, onCurrentPoundsChange, Modifier.fillMaxWidth(), label = { Text("Current pounds (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(currentDurationSeconds, onCurrentDurationSecondsChange, Modifier.fillMaxWidth(), label = { Text("Current seconds (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            AppSurfaceCard(Modifier.fillMaxWidth().semantics {
                contentDescription = progression?.let { "${it.steps.size} ordered future progression steps" } ?: "No future progression steps"
            }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("FUTURE PRESCRIPTIONS", color = AppGold, style = MaterialTheme.typography.labelSmall)
                    Text(
                        progression?.let { "${it.steps.size} ordered ${if (it.steps.size == 1) "step" else "steps"} · ${it.steps.sumOf { step -> step.insertedExercises.size }} added exercises" }
                            ?: "Add at least one valid future step",
                        color = if (progression == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            OutlinedButton(onClick = onConfigure, enabled = canConfigure, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (progression == null) "Add future steps" else "Review and edit steps")
            }
        }
    }
}

@Composable
internal fun CustomProgressionEditorScreen(
    source: Exercise,
    initial: CustomExerciseProgression?,
    onDismiss: () -> Unit,
    onSave: (CustomExerciseProgression) -> Unit,
) {
    val original = remember(source.id) { initial ?: CustomExerciseProgression(emptyList()) }
    var working by rememberSaveable(source.id, stateSaver = CustomProgressionStateSaver) { mutableStateOf(original) }
    var stepKeys by rememberSaveable(source.id) { mutableStateOf(List(original.steps.size) { newId() }) }
    var editingIndex by rememberSaveable(source.id) { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable(source.id) { mutableStateOf(false) }
    var deleteIndex by rememberSaveable(source.id) { mutableStateOf<Int?>(null) }
    var discard by rememberSaveable(source.id) { mutableStateOf(false) }
    var message by rememberSaveable(source.id) { mutableStateOf<String?>(null) }
    val changed = working != original
    val requestBack = { if (changed) discard = true else onDismiss() }
    BackHandler(onBack = requestBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Custom progression", requestBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("CURRENT PRESCRIPTION", color = AppGold, style = MaterialTheme.typography.labelMedium)
                PrescriptionCard(source.prescription(), "Current · unchanged until progression is applied")
            }
            item {
                ProgressionListHeader("ORDERED FUTURE STEPS", "Drag to reorder")
            }
            if (working.steps.isEmpty()) item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No future steps yet", fontWeight = FontWeight.Bold)
                        Text("Add the next complete grip or exercise prescription.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(working.steps.size, key = { stepKeys[it] }) { index ->
                val stepKey = stepKeys[index]
                CustomStepCard(
                    step = working.steps[index], position = index, total = working.steps.size,
                    onEdit = { editingIndex = index }, onDelete = { deleteIndex = index },
                    onMove = { offset ->
                        val currentIndex = stepKeys.indexOf(stepKey)
                        val moved = working.moveStep(currentIndex, offset)
                        if (moved != working) {
                            working = moved
                            stepKeys = move(stepKeys, currentIndex, offset)
                            message = "Step order updated · save progression to apply"
                            true
                        } else false
                    },
                )
            }
            item {
                Button(onClick = { adding = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add future step")
                }
                Text("Changes apply when you save the exercise", color = AppMint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                message?.let { Text(it, color = AppMint, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = requestBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                    Button(enabled = working.isEditorValid(), onClick = { onSave(working) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save progression") }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
    val edit = editingIndex?.let { working.steps.getOrNull(it) }
    if (adding || edit != null) CustomStepEditorScreen(
        source = source,
        original = edit,
        onDismiss = { adding = false; editingIndex = null },
        onSave = { step ->
            if (editingIndex == null) stepKeys = stepKeys + newId()
            working = if (editingIndex == null) working.copy(steps = working.steps + step)
            else working.copy(steps = working.steps.mapIndexed { index, current -> if (index == editingIndex) step else current })
            message = if (editingIndex == null) "Future step added · save progression to apply" else "Future step updated · save progression to apply"
            adding = false; editingIndex = null
        },
    )
    deleteIndex?.let { index ->
        val step = working.steps.getOrNull(index)
        if (step != null) AppConfirmationDialog(
            title = "Delete step ${index + 1}?",
            message = "This removes ${step.replacement.name} and its ${step.insertedExercises.size} added exercises from the future sequence.",
            confirmLabel = "Delete step",
            onConfirm = {
                working = working.copy(steps = working.steps.filterIndexed { i, _ -> i != index })
                stepKeys = stepKeys.filterIndexed { i, _ -> i != index }
                deleteIndex = null; message = "Future step deleted · save progression to apply"
            },
            onDismiss = { deleteIndex = null },
        )
    }
    if (discard) AppConfirmationDialog(
        title = "Discard custom progression changes?",
        message = "The edited future steps have not been applied to this exercise.",
        confirmLabel = "Discard changes",
        onConfirm = onDismiss,
        onDismiss = { discard = false },
    )
}

@Composable
private fun CustomStepCard(step: CustomProgressionStep, position: Int, total: Int, onEdit: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Boolean) {
    var menu by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 54.dp.toPx() }
    var distance by remember { mutableStateOf(0f) }
    AppSurfaceCard(Modifier.fillMaxWidth().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) false else when (it.key) {
            Key.DirectionUp -> onMove(-1); Key.DirectionDown -> onMove(1); else -> false
        }
    }.focusable().semantics {
        stateDescription = "Future step ${position + 1} of $total, ${step.replacement.name}, ${step.insertedExercises.size} added exercises"
        customActions = buildList {
            if (position > 0) add(CustomAccessibilityAction("Move step up") { onMove(-1) })
            if (position < total - 1) add(CustomAccessibilityAction("Move step down") { onMove(1) })
        }
    }) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit future step ${position + 1}", onClick = onEdit).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragIndicator, "Reorder future step ${position + 1}", modifier = Modifier.size(48.dp).pointerInput(total) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = { distance = 0f },
                ) { change, amount ->
                    change.consume(); distance += amount.y
                    while (distance <= -threshold) { onMove(-1); distance += threshold }
                    while (distance >= threshold) { onMove(1); distance -= threshold }
                }
            })
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("STEP ${position + 1}", color = AppGold, style = MaterialTheme.typography.labelSmall)
                Text(step.replacement.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(step.replacement.detail(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (step.insertedExercises.isNotEmpty()) Text("Adds ${step.insertedExercises.joinToString { it.name }}", color = AppMint, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }, Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "More options for step ${position + 1}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PrescriptionCard(prescription: ExercisePrescription, subtitle: String? = null) {
    AppSurfaceCard(Modifier.fillMaxWidth().semantics { contentDescription = "${prescription.name}, ${prescription.detail()}" }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(ExerciseArtworkCatalog.resolve(prescription.artworkId).resource(ExerciseArtworkCrop.LIST)), null, Modifier.size(58.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(prescription.name, fontWeight = FontWeight.Bold)
                Text(prescription.detail(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (prescription.notes.isNotBlank()) Text(prescription.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = AppMint, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun CustomStepEditorScreen(source: Exercise, original: CustomProgressionStep?, onDismiss: () -> Unit, onSave: (CustomProgressionStep) -> Unit) {
    val initial = remember(source.id, original) { (original?.replacement ?: source.prescription()).draft() }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var sets by rememberSaveable { mutableStateOf(initial.sets) }
    var target by rememberSaveable { mutableStateOf(initial.target) }
    var timed by rememberSaveable { mutableStateOf(initial.timed) }
    var timerSeconds by rememberSaveable { mutableStateOf(initial.timerSeconds) }
    var artworkId by rememberSaveable { mutableStateOf(initial.artworkId) }
    var weightPounds by rememberSaveable { mutableStateOf(initial.weightPounds) }
    var durationSeconds by rememberSaveable { mutableStateOf(initial.durationSeconds) }
    var inserted by rememberSaveable(source.id, original, stateSaver = InsertedExerciseStateSaver) { mutableStateOf(original?.insertedExercises.orEmpty()) }
    var addingExercise by rememberSaveable { mutableStateOf(false) }
    var editingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var artworkPicker by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val draft = PrescriptionDraft(name, notes, sets, target, timed, timerSeconds, artworkId, weightPounds, durationSeconds)
    val candidate = draft.saved()?.let { CustomProgressionStep(it, inserted) }
    val changed = draft != initial || inserted != original?.insertedExercises.orEmpty()
    val requestBack = { if (changed) discard = true else onDismiss() }
    BackHandler(onBack = requestBack)
    Scaffold(Modifier.fillMaxSize().appScreenBackground(), topBar = { SecondaryTopBar(if (original == null) "Add future step" else "Edit future step", requestBack) }, containerColor = Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SOURCE REPLACEMENT", color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text("This complete prescription replaces ${source.name} when this step is applied.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { if (it.length <= 200) name = it }, Modifier.fillMaxWidth(), label = { Text("Exercise name or grip") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(sets, { sets = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Sets") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(target, { if (it.length <= 500) target = it }, Modifier.fillMaxWidth(), label = { Text("Target") }, singleLine = true)
            OutlinedTextField(notes, { if (it.length <= 4_000) notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes or grip description") }, minLines = 2)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Text("Timed exercise", Modifier.weight(1f)); Switch(timed, { timed = it }) }
            if (timed) OutlinedTextField(timerSeconds, { timerSeconds = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Timer seconds") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text("STRUCTURED MEASURES", color = AppGold, style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(weightPounds, { weightPounds = it.decimalProgressionInput() }, Modifier.fillMaxWidth(), label = { Text("Pounds (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(durationSeconds, { durationSeconds = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Seconds (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            AppSurfaceCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(ExerciseArtworkCatalog.resolve(artworkId).resource(ExerciseArtworkCrop.LIST)), null, Modifier.size(58.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f)) { Text("Step artwork", fontWeight = FontWeight.Bold); Text(ExerciseArtworkCatalog.resolve(artworkId).storageId.replace('_', ' '), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { artworkPicker = true }, Modifier.heightIn(min = 48.dp)) { Text("Change") }
                }
            }
            HorizontalDivider(color = AppBorder)
            ProgressionListHeader("ADDED EXERCISES", "Inserted immediately after the source · drag to reorder")
            inserted.forEachIndexed { index, exercise ->
                androidx.compose.runtime.key(exercise.id) {
                InsertedExerciseRow(exercise, index, inserted.size, onEdit = { editingExerciseId = exercise.id }, onDelete = { deletingExerciseId = exercise.id }, onMove = { offset ->
                    val moved = move(inserted, inserted.indexOfFirst { it.id == exercise.id }, offset)
                    if (moved != inserted) { inserted = moved; true } else false
                })
                }
            }
            OutlinedButton(onClick = { addingExercise = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise after source") }
            Text("Added exercises are independent and can have their own progression.", color = AppMint, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = requestBack, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                Button(enabled = candidate != null, onClick = { candidate?.let(onSave) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save step") }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    val editing = inserted.firstOrNull { it.id == editingExerciseId }
    if (addingExercise || editing != null) ExerciseEditorScreen(editing, onDismiss = { addingExercise = false; editingExerciseId = null }, onSave = { exercise ->
        inserted = if (inserted.any { it.id == exercise.id }) inserted.map { if (it.id == exercise.id) exercise else it } else inserted + exercise
        addingExercise = false; editingExerciseId = null
    })
    deletingExerciseId?.let { id -> inserted.firstOrNull { it.id == id }?.let { exercise -> AppConfirmationDialog(
        title = "Delete ${exercise.name}?", message = "This removes the added exercise from this future step.", confirmLabel = "Delete exercise",
        onConfirm = { inserted = inserted.filterNot { it.id == id }; deletingExerciseId = null }, onDismiss = { deletingExerciseId = null },
    ) } }
    if (artworkPicker) ExerciseArtworkPickerSheet(artworkId, { artworkPicker = false }, { artworkId = it; artworkPicker = false })
    if (discard) AppConfirmationDialog("Discard step changes?", "This future prescription and its added exercises have not been saved.", "Discard changes", onDismiss, { discard = false })
}

@Composable
private fun InsertedExerciseRow(exercise: Exercise, position: Int, total: Int, onEdit: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Boolean) {
    var menu by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 54.dp.toPx() }
    var distance by remember { mutableStateOf(0f) }
    AppSurfaceCard(Modifier.fillMaxWidth().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) false else when (it.key) {
            Key.DirectionUp -> onMove(-1); Key.DirectionDown -> onMove(1); else -> false
        }
    }.focusable().semantics {
        stateDescription = "${exercise.name}, added exercise ${position + 1} of $total"
        customActions = buildList {
            if (position > 0) add(CustomAccessibilityAction("Move ${exercise.name} up") { onMove(-1) })
            if (position < total - 1) add(CustomAccessibilityAction("Move ${exercise.name} down") { onMove(1) })
        }
    }) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit ${exercise.name}", onClick = onEdit).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragIndicator, "Reorder ${exercise.name}", Modifier.size(48.dp).pointerInput(exercise.id, total) {
                detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f }, onDragEnd = { distance = 0f }) { change, amount ->
                    change.consume(); distance += amount.y
                    while (distance <= -threshold) { onMove(-1); distance += threshold }
                    while (distance >= threshold) { onMove(1); distance -= threshold }
                }
            })
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(exercise.name, fontWeight = FontWeight.Bold)
                Text("${exercise.setCount} sets · ${exercise.targetSummary()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(when (exercise.progression) { is AutomaticExerciseProgression -> "Independent automatic progression"; is CustomExerciseProgression -> "Independent custom progression"; null -> "No progression" }, color = AppMint, style = MaterialTheme.typography.labelSmall)
            }
            Box { IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "More options for ${exercise.name}") }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
            } }
        }
    }
}

private fun String.decimalProgressionInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val point = filtered.indexOf('.')
    return if (point < 0) filtered else filtered.take(point + 1) + filtered.drop(point + 1).replace(".", "")
}

@Composable
private fun ProgressionListHeader(title: String, detail: String) {
    if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
