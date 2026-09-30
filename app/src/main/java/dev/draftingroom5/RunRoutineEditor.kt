package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun RunRoutineEditorScreen(
    routine: Routine,
    routes: List<RunRoute>,
    scheduleSummary: String,
    isNew: Boolean,
    onPersist: (Routine) -> Boolean,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onOpenRoutes: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    var working by rememberSaveable(routine.id, stateSaver = RoutineDraftSaver) { mutableStateOf(routine) }
    LaunchedEffect(routine) { if (!isNew) working = routine }
    var editingIntervalId by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var addingInterval by rememberSaveable(routine.id) { mutableStateOf(false) }
    var renaming by rememberSaveable(routine.id) { mutableStateOf(false) }
    var artworkPicker by rememberSaveable(routine.id) { mutableStateOf(false) }
    var deleteRoutine by rememberSaveable(routine.id) { mutableStateOf(false) }
    var discardRequested by rememberSaveable(routine.id) { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var routePicker by remember { mutableStateOf(false) }
    var actionMessage by rememberSaveable(routine.id) { mutableStateOf<String?>(null) }
    var draggedIntervalId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf<Routine?>(null) }
    val haptics = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { 54.dp.toPx() }
    val changed = working != routine

    fun applyChange(updated: Routine, message: String): Boolean {
        if (updated == working) return true
        val candidate = if (isNew) updated else updated.forRunEditorSave(working, false)
        if (isNew || onPersist(candidate)) {
            working = candidate
            actionMessage = if (isNew) "Draft updated · not saved" else message
            return true
        }
        actionMessage = "Couldn’t save changes. Try again."
        return false
    }

    val requestBack = { if (isNew && changed) discardRequested = true else onBack() }
    BackHandler(onBack = requestBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            SecondaryTopBar("Run routine", requestBack) {
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("RUN ROUTINE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            working.name.ifBlank { "New run" },
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(runRoutineSummary(checkNotNull(working.run), scheduleSummary), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (LocalDensity.current.fontScale <= 1.3f && LocalConfiguration.current.screenWidthDp >= 360) Image(
                        painter = painterResource(RoutineArtworkCatalog.resolve(working.artworkId).resource(RoutineArtworkCrop.HEADER)),
                        contentDescription = null,
                        modifier = Modifier.size(104.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                RoutineIdentityActions(onRename = { renaming = true }, onChangeArtwork = { artworkPicker = true })
                if (!isNew) TextButton(onClick = onOpenHistory) { Text("Previous runs") }
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROUTE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        val selected = routes.firstOrNull { it.id == working.run?.routeId }
                        Text(selected?.name ?: "No route selected", fontWeight = FontWeight.Bold)
                        Text(
                            if (selected == null) "Choose a saved route for this run routine." else
                                "${selected.points.size} route points · ${selected.turnCues.size} turn cues",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { routePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (selected == null) "Choose route" else "Change route")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onOpenRoutes, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Manage run routes")
                }
            }
            item {
                RunIntervalSectionHeader()
            }
            if (working.run?.intervals.isNullOrEmpty()) item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("No intervals yet", fontWeight = FontWeight.Bold)
                        Text("Add at least one walk or run interval before saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val intervals = checkNotNull(working.run).intervals
                items(intervals.size, key = { intervals[it].id }) { index ->
                    val interval = intervals[index]
                    RunIntervalRow(
                        interval = interval,
                        position = index,
                        total = intervals.size,
                        onEdit = { editingIntervalId = interval.id },
                        onMove = { offset -> applyChange(working.moveRunInterval(interval.id, offset), "Interval order saved.") },
                        dragged = draggedIntervalId == interval.id,
                        dragThreshold = dragThreshold,
                        onDragStart = {
                            dragOrigin = working
                            draggedIntervalId = interval.id
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStep = { offset -> working = working.moveRunInterval(interval.id, offset) },
                        onDragEnd = {
                            val origin = dragOrigin
                            if (origin != null) {
                                val updated = working.forRunEditorSave(origin, isNew)
                                working = updated
                                if (origin.run?.intervals != updated.run?.intervals) {
                                    if (!isNew && !onPersist(updated)) {
                                        working = origin
                                        actionMessage = "Couldn’t save the new order."
                                    } else actionMessage = if (isNew) "Draft order updated · not saved" else "Interval order saved."
                                }
                            }
                            dragOrigin = null
                            draggedIntervalId = null
                        },
                        onDragCancel = {
                            dragOrigin?.let { working = it }
                            dragOrigin = null
                            draggedIntervalId = null
                        },
                        onDelete = {
                            working.withoutRunInterval(interval.id, allowEmpty = isNew)?.let {
                                applyChange(it, "Interval deleted.")
                            }
                        },
                        deleteEnabled = isNew || intervals.size > 1,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { addingInterval = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add interval")
                }
            }
            if (!working.run?.intervals.isNullOrEmpty()) item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TOTAL", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(formatRunClock(checkNotNull(working.run).totalDurationSeconds),
                            style = MaterialTheme.typography.displaySmall)
                        val walk = checkNotNull(working.run).intervals.count { it.kind == RunIntervalKind.WALK }
                        val run = checkNotNull(working.run).intervals.size - walk
                        Text("$run run intervals · $walk walk intervals",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            actionMessage?.let { message -> item { Text(message, color = AppMint, style = MaterialTheme.typography.bodySmall) } }
            if (isNew) item {
                Button(
                    enabled = working.isSaveableRunRoutine(),
                    onClick = {
                        val saved = working.forRunEditorSave(routine, true)
                        if (saved.isSaveableRunRoutine() && onPersist(saved)) onBack()
                        else actionMessage = "Couldn’t save routine. Try again."
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Save routine") }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    val editing = working.run?.intervals?.firstOrNull { it.id == editingIntervalId }
    if (addingInterval || editing != null) RunIntervalDialog(
        original = editing,
        onDismiss = { addingInterval = false; editingIntervalId = null },
        onSave = { interval ->
            if (working.withRunInterval(interval)?.let { applyChange(it, "Interval saved.") } == true) {
                addingInterval = false
                editingIntervalId = null
            }
        },
    )
    if (renaming) RoutineNameDialog(
        initial = working.name,
        onDismiss = { renaming = false },
        onSave = { name -> if (working.withRunIdentity(name, working.artworkId)?.let { applyChange(it, "Name saved.") } == true) renaming = false },
    )
    if (artworkPicker) RoutineArtworkPickerSheet(
        selectedId = working.artworkId,
        onDismiss = { artworkPicker = false },
        onDone = { id -> if (working.withRunArtwork(id)?.let { applyChange(it, "Artwork saved.") } == true) artworkPicker = false },
    )
    if (deleteRoutine) AppConfirmationDialog(
        title = "Delete ${working.name}?",
        message = "This deletes the routine and every scheduled entry that references it. Completed run history remains available.",
        confirmLabel = "Delete routine",
        onConfirm = onDelete,
        onDismiss = { deleteRoutine = false },
    )
    if (discardRequested) AppConfirmationDialog(
        title = "Discard this run draft?",
        message = "Nothing from this run routine has been saved.",
        confirmLabel = "Discard draft",
        onConfirm = onBack,
        onDismiss = { discardRequested = false },
    )
    if (routePicker) AlertDialog(
        onDismissRequest = { routePicker = false },
        title = { Text("Choose a route") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TextButton(onClick = {
                        working.withRunRoute(null)?.let { if (applyChange(it, "Route cleared.")) routePicker = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text("No route") }
                }
                items(routes, key = { it.id }) { route ->
                    TextButton(onClick = {
                        working.withRunRoute(route.id)?.let { if (applyChange(it, "Route saved.")) routePicker = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text(route.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { routePicker = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RunIntervalSectionHeader() {
    val stackLabels = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.3f
    if (stackLabels) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("INTERVALS", color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text("Drag to reorder", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("INTERVALS", color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text("Drag to reorder", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RunIntervalRow(
    interval: RunInterval,
    position: Int,
    total: Int,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    dragged: Boolean,
    dragThreshold: Float,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onDragStart)
    val currentStep by rememberUpdatedState(onDragStep)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    AppSurfaceCard(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit ${interval.kind.displayName()} interval", onClick = onEdit)
            .semantics {
                customActions = buildList {
                    if (position > 0) add(CustomAccessibilityAction("Move interval up") { onMove(-1); true })
                    if (position < total - 1) add(CustomAccessibilityAction("Move interval down") { onMove(1); true })
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Reorder ${interval.kind.displayName()} interval, position ${position + 1} of $total",
                tint = if (dragged) AppBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp).pointerInput(interval.id) {
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
                }.padding(10.dp),
            )
            Box(Modifier.width(4.dp).height(42.dp).background(
                if (interval.kind == RunIntervalKind.WALK) AppMint else AppBlue, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Text(interval.kind.displayName(), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text(formatRunClock(interval.durationSeconds), style = MaterialTheme.typography.titleMedium)
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Interval options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                        enabled = deleteEnabled, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun RunIntervalDialog(original: RunInterval?, onDismiss: () -> Unit, onSave: (RunInterval) -> Unit) {
    var kindName by rememberSaveable(original?.id) { mutableStateOf((original?.kind ?: RunIntervalKind.RUN).name) }
    var minutes by rememberSaveable(original?.id) { mutableStateOf(((original?.durationSeconds ?: 60) / 60).toString()) }
    var seconds by rememberSaveable(original?.id) { mutableStateOf(((original?.durationSeconds ?: 60) % 60).toString()) }
    val duration = runIntervalDurationSeconds(minutes, seconds)
    val kind = RunIntervalKind.valueOf(kindName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add interval" else "Edit interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RunIntervalKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kindName = option.name },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Text(
                    if (duration == null) "Enter a duration from 1 second to 24 hours." else formatRunDuration(duration),
                    color = if (duration == null) MaterialTheme.colorScheme.error else AppMint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = duration != null,
                onClick = { duration?.let { onSave(RunInterval(original?.id ?: newId(), kind, it)) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
    )
}

internal fun runIntervalDurationSeconds(minutes: String, seconds: String): Int? {
    val minuteValue = minutes.ifBlank { "0" }.toLongOrNull() ?: return null
    val secondValue = seconds.ifBlank { "0" }.toLongOrNull() ?: return null
    if (minuteValue < 0 || secondValue !in 0..59) return null
    return (minuteValue * 60L + secondValue).takeIf { it in 1..86_400 }?.toInt()
}

internal fun formatRunDuration(seconds: Int): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (remainder > 0 || isEmpty()) add("${remainder}s")
    }.joinToString(" ")
}

internal fun formatRunClock(seconds: Int): String =
    java.lang.String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60)

internal fun runRoutineSummary(run: RunRoutine, scheduleSummary: String): String {
    val duration = if (run.intervals.isEmpty()) "No duration" else formatRunDuration(run.totalDurationSeconds)
    return "${run.intervalCountLabel()} · $duration · $scheduleSummary"
}

private fun RunIntervalKind.displayName() = name.lowercase().replaceFirstChar(Char::uppercase)
