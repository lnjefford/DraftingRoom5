package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanManagementScreen(
    plan: TrainingPlan,
    partialSessionCounts: Map<String, Int>,
    onChange: (TrainingPlan) -> Boolean,
    onReset: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onAddGuidedRoutine: () -> Unit,
    onAddLinkedRoutine: () -> Unit,
    onRenameRoutine: (String, String) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onAddSchedule: (DayOfWeek) -> Unit,
    onEditSchedule: (String) -> Unit,
    onBack: () -> Unit,
    initialTab: Int = 0,
    hapticsEnabled: Boolean = true,
) {
    BackHandler(onBack = onBack)
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var selectedDayName by rememberSaveable { mutableStateOf(DayOfWeek.MONDAY.name) }
    val selectedDay = runCatching { DayOfWeek.valueOf(selectedDayName) }.getOrDefault(DayOfWeek.MONDAY)
    var addingRoutine by rememberSaveable { mutableStateOf(false) }
    var renamingRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    val renamingRoutine = plan.routines.firstOrNull { it.id == renamingRoutineId }
    var deletingRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    val deletingRoutine = plan.routines.firstOrNull { it.id == deletingRoutineId }
    var resetRequested by rememberSaveable { mutableStateOf(false) }
    var deletingScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    val deletingSchedule = plan.schedule.firstOrNull { it.id == deletingScheduleId }
    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            SecondaryTopBar("Schedules & routines", onBack) {
                Box {
                    IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset built-in plan") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                            onClick = { overflowExpanded = false; resetRequested = true },
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, divider = {}) {
                listOf("Schedule", "Routines").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            if (selectedTab == 0) {
                RecurringScheduleTab(
                    plan = plan,
                    selectedDay = selectedDay,
                    hapticsEnabled = hapticsEnabled,
                    onSelectDay = { selectedDayName = it.name },
                    onChange = onChange,
                    onAdd = { onAddSchedule(selectedDay) },
                    onEdit = { onEditSchedule(it.id) },
                    onDelete = { deletingScheduleId = it.id },
                )
            } else {
                CurrentRoutinesTab(
                    plan = plan,
                    onAdd = { addingRoutine = true },
                    onEdit = onEditRoutine,
                    onRename = { renamingRoutineId = it.id },
                    onDelete = { deletingRoutineId = it.id },
                )
            }
        }
    }

    if (addingRoutine) AddRoutineChooserSheet(
        onChoose = { choice ->
            addingRoutine = false
            when (choice) {
                AddRoutineChoice.GUIDED -> onAddGuidedRoutine()
                AddRoutineChoice.LINKED_APP -> onAddLinkedRoutine()
            }
        },
        onDismiss = { addingRoutine = false },
    )
    renamingRoutine?.let { routine ->
        NameDialog(
            title = "Rename routine",
            initial = routine.name,
            warning = if ((partialSessionCounts[routine.id] ?: 0) > 0) "Saved incomplete sessions keep the original routine name and exercises." else null,
            onDismiss = { renamingRoutineId = null },
            onSave = { name ->
                if (name != routine.name) onRenameRoutine(routine.id, name)
                renamingRoutineId = null
            },
        )
    }
    deletingRoutine?.let { routine ->
        AppConfirmationDialog(
            title = "Delete ${routine.name}?",
            message = routineDeletionMessage(
                plan = plan,
                routine = routine,
                partialSessionCount = partialSessionCounts[routine.id] ?: 0,
            ),
            confirmLabel = "Delete routine",
            onConfirm = {
                onDeleteRoutine(routine.id)
                deletingRoutineId = null
            },
            onDismiss = { deletingRoutineId = null },
        )
    }
    if (resetRequested) {
        AlertDialog(
            onDismissRequest = { resetRequested = false },
            title = { Text("Reset schedules and routines?") },
            text = { Text("This restores the default routines and schedule, and deletes all saved sessions and workout history. Your other settings stay unchanged.") },
            confirmButton = { TextButton(onClick = { onReset(); resetRequested = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetRequested = false }) { Text("Cancel") } },
        )
    }
    deletingSchedule?.let { entry ->
        val routine = plan.routineFor(entry)
        AppConfirmationDialog(
            title = "Delete ${routine.name}?",
            message = "This removes the scheduled item from ${entry.days.weekdayList()}. The routine remains available.",
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteSchedule(entry.id)
                deletingScheduleId = null
            },
            onDismiss = { deletingScheduleId = null },
        )
    }
}

@Composable
private fun RecurringScheduleTab(
    plan: TrainingPlan,
    selectedDay: DayOfWeek,
    hapticsEnabled: Boolean,
    onSelectDay: (DayOfWeek) -> Unit,
    onChange: (TrainingPlan) -> Boolean,
    onAdd: () -> Unit,
    onEdit: (ScheduleEntry) -> Unit,
    onDelete: (ScheduleEntry) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { 52.dp.toPx() }
    var workingPlan by remember { mutableStateOf(plan) }
    var draggedEntryId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf<TrainingPlan?>(null) }
    var pendingFocusId by remember { mutableStateOf<String?>(null) }
    val visibleItems = workingPlan.forDay(selectedDay)

    LaunchedEffect(plan, draggedEntryId) {
        if (draggedEntryId == null) workingPlan = plan
    }

    fun moveAndSave(entry: ScheduleEntry, offset: Int): Boolean {
        val current = workingPlan.forDay(selectedDay)
        val index = current.indexOfFirst { it.id == entry.id }
        if (index !in current.indices || index + offset !in current.indices) return false
        val updated = workingPlan.moveScheduleOnDay(selectedDay, entry.id, offset)
        if (!onChange(updated)) { workingPlan = plan; return false }
        workingPlan = updated
        pendingFocusId = entry.id
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            PlanSectionLabel("WEEKLY SCHEDULE")
            Spacer(Modifier.height(10.dp))
            Text("Choose a day to review and edit its sessions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { RecurringWeekSelector(workingPlan.scheduleCounts(), selectedDay, onSelectDay) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(selectedDay.fullName(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("${visibleItems.size} scheduled", color = AppBlue, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (visibleItems.isEmpty()) {
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recovery day", style = MaterialTheme.typography.titleMedium)
                        Text("Nothing recurs on ${selectedDay.fullName()}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(visibleItems, key = { _, entry -> entry.id }) { index, entry ->
                val focusRequester = remember(entry.id) { FocusRequester() }
                LaunchedEffect(pendingFocusId, index) {
                    if (pendingFocusId == entry.id) {
                        focusRequester.requestFocus()
                        pendingFocusId = null
                    }
                }
                ManagedScheduleCard(
                    entry = entry,
                    routine = workingPlan.routineFor(entry),
                    position = index,
                    total = visibleItems.size,
                    focusRequester = focusRequester,
                    dragged = draggedEntryId == entry.id,
                    onMove = { offset -> moveAndSave(entry, offset) },
                    onDragStart = {
                        dragOrigin = workingPlan
                        draggedEntryId = entry.id
                        pendingFocusId = entry.id
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStep = { offset ->
                        val current = workingPlan.forDay(selectedDay)
                        val from = current.indexOfFirst { it.id == entry.id }
                        if (from in current.indices && from + offset in current.indices) {
                            workingPlan = workingPlan.moveScheduleOnDay(selectedDay, entry.id, offset)
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = {
                        val completed = workingPlan
                        draggedEntryId = null
                        dragOrigin = null
                        pendingFocusId = entry.id
                        if (completed != plan && !onChange(completed)) workingPlan = plan
                    },
                    onDragCancel = {
                        workingPlan = dragOrigin ?: plan
                        draggedEntryId = null
                        dragOrigin = null
                    },
                    dragThreshold = dragThreshold,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add scheduled item")
            }
        }
        item {
            Text(
                "✓  Changes save automatically",
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                color = AppMint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun RecurringWeekSelector(counts: Map<DayOfWeek, Int>, selectedDay: DayOfWeek, onSelectDay: (DayOfWeek) -> Unit) {
    val selectedDiameter = if (LocalDensity.current.fontScale > 1.3f) 44.dp else 32.dp
    BrandedCard(Modifier.fillMaxWidth(), containerColor = AppSurfaceRaised) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val count = counts.getValue(day)
                val selected = day == selectedDay
                Column(
                    Modifier.width(48.dp).heightIn(min = 64.dp).clickable { onSelectDay(day) }.semantics {
                        stateDescription = "${day.fullName()}, ${if (count == 1) "1 scheduled item" else "$count scheduled items"}${if (selected) ", selected" else ""}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(selectedDiameter).clip(CircleShape).background(if (selected) AppBlueStrong else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(day.fullName().take(1), fontWeight = FontWeight.Bold) }
                    Text(if (count == 0) "—" else count.toString(), color = if (selected) AppBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ManagedScheduleCard(
    entry: ScheduleEntry,
    routine: Routine,
    position: Int,
    total: Int,
    focusRequester: FocusRequester,
    dragged: Boolean,
    onMove: (Int) -> Boolean,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    dragThreshold: Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(onDragStart)
    val currentStep by rememberUpdatedState(onDragStep)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentCancel by rememberUpdatedState(onDragCancel)
    val showArtwork = LocalDensity.current.fontScale <= 1.3f
    BrandedCard(
        Modifier.fillMaxWidth().onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown || !it.isCtrlPressed) false
            else when (it.key) { Key.DirectionUp -> onMove(-1); Key.DirectionDown -> onMove(1); else -> false }
        }.focusRequester(focusRequester).focusable().semantics {
            stateDescription = "${routine.name}, position ${position + 1} of $total"
            customActions = buildList {
                if (position > 0) add(CustomAccessibilityAction("Move up") { onMove(-1) })
                if (position < total - 1) add(CustomAccessibilityAction("Move down") { onMove(1) })
            }
        }.background(if (dragged) AppBlue.copy(alpha = .10f) else Color.Transparent),
        containerColor = if (dragged) AppSurfaceRaised else AppSurface,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 124.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Reorder ${routine.name}, position ${position + 1} of $total",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp).pointerInput(entry.id, total) {
                    var distance = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { distance = 0f; currentStart() },
                        onDragCancel = { currentCancel() },
                        onDragEnd = { currentEnd() },
                    ) { change, amount ->
                        change.consume()
                        distance += amount.y
                        if (kotlin.math.abs(distance) >= dragThreshold) {
                            currentStep(if (distance > 0) 1 else -1)
                            distance = 0f
                        }
                    }
                }.padding(10.dp),
            )
            Column(Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(routine.scheduleEyebrow(), color = AppMint, style = MaterialTheme.typography.labelSmall)
                Text(routine.name, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(routine.scheduleMetadata(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (showArtwork) {
                Image(
                    painter = painterResource(RoutineArtworkCatalog.resolve(routine.artworkId).cardAsset),
                    contentDescription = null,
                    modifier = Modifier.width(92.dp).height(116.dp).clip(MaterialTheme.shapes.medium),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "Options for ${routine.name}") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun CurrentRoutinesTab(
    plan: TrainingPlan,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onRename: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            PlanSectionLabel("ROUTINES")
            Spacer(Modifier.height(10.dp))
            Text("Guided routines stay in DraftingRoom5; linked routines open another app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (plan.routines.isEmpty()) {
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No routines yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create a guided routine to train here, or a linked-app routine to open another app.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(plan.routines, key = { it.id }) { routine ->
                ManagedRoutineRow(
                    routine = routine,
                    scheduleSummary = plan.routineScheduleSummary(routine.id),
                    onEdit = { onEdit(routine.id) },
                    onRename = { onRename(routine) },
                    onDelete = { onDelete(routine) },
                )
            }
        }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add routine")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManagedRoutineRow(
    routine: Routine,
    scheduleSummary: String,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val showArtwork = LocalDensity.current.fontScale <= 1.3f
    BrandedCard(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).semantics {
            stateDescription = "${routine.name}, ${routine.routineListMetadata()}, $scheduleSummary"
        },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 116.dp).padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(routine.routineListMetadata(), color = AppBlue, style = MaterialTheme.typography.labelLarge)
                Text(scheduleSummary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (showArtwork) {
                Image(
                    painter = painterResource(RoutineArtworkCatalog.resolve(routine.artworkId).cardAsset),
                    contentDescription = null,
                    modifier = Modifier.width(82.dp).height(92.dp).clip(MaterialTheme.shapes.medium),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, "Options for ${routine.name}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, warning: String? = null, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { warning?.let { Text(it) }; OutlinedTextField(name, { if (it.length <= 200) name = it }, label = { Text("Routine name") }, singleLine = true) } },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun DayOfWeek.displayName() = name.lowercase().replaceFirstChar { it.titlecase() }
private fun DayOfWeek.shortName() = displayName().take(3)
private fun DayOfWeek.fullName() = getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun Set<DayOfWeek>.weekdayList(): String = sortedBy(DayOfWeek::getValue)
    .joinToString(", ") { it.fullName() }

private fun Routine.scheduleEyebrow(): String = when (execution) {
    RoutineExecution.GUIDED -> "GUIDED ROUTINE"
    RoutineExecution.LINKED_APP -> "LINKED APP · ${linkedAppDisplayName(checkNotNull(appLink).packageName).uppercase()}"
}

private fun Routine.scheduleMetadata(): String = when (execution) {
    RoutineExecution.GUIDED -> if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises"
    RoutineExecution.LINKED_APP -> "Opens ${linkedAppDisplayName(checkNotNull(appLink).packageName)}"
}

internal fun Routine.routineListMetadata(): String = when (execution) {
    RoutineExecution.GUIDED -> "Guided Routine · ${if (exercises.size == 1) "1 exercise" else "${exercises.size} exercises"}"
    RoutineExecution.LINKED_APP -> "Linked App · ${linkedAppDisplayName(checkNotNull(appLink).packageName)}"
}

internal fun TrainingPlan.routineScheduleSummary(routineId: String): String {
    val days = schedule.asSequence().filter { it.routineId == routineId }.flatMap { it.days.asSequence() }.toSet()
    return if (days.isEmpty()) "Not scheduled" else "Scheduled ${days.sortedBy(DayOfWeek::getValue).joinToString(" · ") { it.shortName() }}"
}

internal fun routineDeletionMessage(plan: TrainingPlan, routine: Routine, partialSessionCount: Int): String {
    val scheduleCount = plan.schedule.count { it.routineId == routine.id }
    val consequences = buildList {
        if (scheduleCount > 0) add(if (scheduleCount == 1) "1 scheduled entry" else "$scheduleCount scheduled entries")
        if (partialSessionCount > 0) add(if (partialSessionCount == 1) "1 saved partial session" else "$partialSessionCount saved partial sessions")
    }
    return if (consequences.isEmpty()) {
        "This permanently deletes the routine. Workout history remains available."
    } else {
        "This permanently deletes the routine, ${consequences.joinToString(" and ")}, and their references. Workout history remains available."
    }
}

@Composable
private fun PlanSectionLabel(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = AppGold, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.width(38.dp).height(2.dp).background(AppGold))
    }
}
