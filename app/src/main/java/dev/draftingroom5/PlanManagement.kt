package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanManagementScreen(
    plan: TrainingPlan,
    onChange: (TrainingPlan) -> Unit,
    onEditRoutine: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var editingSchedule by remember { mutableStateOf<ScheduledItem?>(null) }
    var addingSchedule by remember { mutableStateOf(false) }
    var addingRoutine by remember { mutableStateOf(false) }
    var resetRequested by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Schedules & routines", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeader("Weekly schedule", "Items appear on the dashboard for their assigned day.", "Plan the week")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { addingSchedule = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add scheduled item")
                }
            }
            DayOfWeek.entries.forEach { day ->
                item(key = "timeline-${day.name}") {
                    WeeklyTimelineDay(
                        day = day,
                        items = plan.schedule.filter { day in it.activeDays() },
                        onEnabledChange = { item, enabled ->
                            onChange(plan.copy(schedule = plan.schedule.map { if (it.id == item.id) item.copy(enabled = enabled) else it }))
                        },
                        onMove = { item, offset ->
                            onChange(plan.copy(schedule = move(plan.schedule, plan.schedule.indexOfFirst { it.id == item.id }, offset)))
                        },
                        onEdit = { editingSchedule = it },
                        onDelete = { item -> onChange(plan.copy(schedule = plan.schedule.filterNot { it.id == item.id })) },
                        canMoveUp = { plan.schedule.indexOfFirst { scheduled -> scheduled.id == it.id } > 0 },
                        canMoveDown = { plan.schedule.indexOfFirst { scheduled -> scheduled.id == it.id } in 0..<plan.schedule.lastIndex },
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Custom routines", "Build routines, then assign them to scheduled items.", "Make it yours")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { addingRoutine = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add custom routine")
                }
            }
            itemsIndexed(plan.routines, key = { _, routine -> routine.id }) { _, routine ->
                BrandedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, fontWeight = FontWeight.Bold)
                            Text("${routine.exercises.size} exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEditRoutine(routine.id) }) { Icon(Icons.Default.Edit, "Edit routine") }
                        IconButton(onClick = { onChange(plan.removeRoutine(routine.id)) }) { Icon(Icons.Default.Delete, "Delete routine") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { resetRequested = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.RestartAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset built-in plan")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (addingSchedule || editingSchedule != null) {
        ScheduleEditorDialog(
            original = editingSchedule,
            routines = plan.routines,
            onDismiss = { addingSchedule = false; editingSchedule = null },
            onSave = { saved ->
                val schedule = if (editingSchedule == null) plan.schedule + saved else plan.schedule.map { if (it.id == saved.id) saved else it }
                onChange(plan.copy(schedule = schedule))
                addingSchedule = false
                editingSchedule = null
            },
        )
    }
    if (addingRoutine) {
        NameDialog(
            title = "New custom routine",
            initial = "",
            onDismiss = { addingRoutine = false },
            onSave = { name ->
                val routine = CustomRoutine(newId(), name, emptyList())
                onChange(plan.copy(routines = plan.routines + routine))
                addingRoutine = false
                onEditRoutine(routine.id)
            },
        )
    }
    if (resetRequested) {
        AlertDialog(
            onDismissRequest = { resetRequested = false },
            title = { Text("Reset schedules and routines?") },
            text = { Text("This replaces all custom changes with the built-in weekly plan and Forearm & Grip routine.") },
            confirmButton = { TextButton(onClick = { onChange(defaultTrainingPlan()); resetRequested = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetRequested = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WeeklyTimelineDay(
    day: DayOfWeek,
    items: List<ScheduledItem>,
    onEnabledChange: (ScheduledItem, Boolean) -> Unit,
    onMove: (ScheduledItem, Int) -> Unit,
    onEdit: (ScheduledItem) -> Unit,
    onDelete: (ScheduledItem) -> Unit,
    canMoveUp: (ScheduledItem) -> Boolean,
    canMoveDown: (ScheduledItem) -> Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (items.isEmpty()) AppSurfaceRaised else AppBlueStrong),
                contentAlignment = Alignment.Center,
            ) { Text(day.shortName(), fontWeight = FontWeight.Bold) }
            if (items.isNotEmpty()) Box(Modifier.width(2.dp).height(64.dp).background(AppBorder))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.displayName(), style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("Recovery day", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            items.forEach { item ->
                BrandedCard(Modifier.fillMaxWidth(), containerColor = if (item.enabled) AppSurface else AppSurface.copy(alpha = 0.62f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.destination.displayName()}${if (item.subtitle.isBlank()) "" else " · ${item.subtitle}"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(item.enabled, { onEnabledChange(item, it) })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onMove(item, -1) }, enabled = canMoveUp(item)) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { onMove(item, 1) }, enabled = canMoveDown(item)) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { onEdit(item) }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditorScreen(
    routine: CustomRoutine,
    onChange: (CustomRoutine) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var addingExercise by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text(routine.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename routine") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeader("Exercises", "Reorder exercises and configure sets, targets, timers, and notes.", "Routine builder")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { addingExercise = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add exercise")
                }
            }
            itemsIndexed(routine.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                BrandedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(exercise.name, fontWeight = FontWeight.Bold)
                        Text("${exercise.sets} · ${exercise.target}${exercise.timerSeconds?.let { " · ${it}s timer" } ?: ""}", color = AppBlue)
                        if (exercise.notes.isNotBlank()) Text(exercise.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { onChange(routine.copy(exercises = move(routine.exercises, index, -1))) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { onChange(routine.copy(exercises = move(routine.exercises, index, 1))) }, enabled = index < routine.exercises.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { editingExercise = exercise }) { Icon(Icons.Default.Edit, "Edit exercise") }
                            IconButton(onClick = { onChange(routine.copy(exercises = routine.exercises.filterNot { it.id == exercise.id })) }) {
                                Icon(Icons.Default.Delete, "Delete exercise")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (addingExercise || editingExercise != null) {
        ExerciseEditorDialog(
            original = editingExercise,
            onDismiss = { addingExercise = false; editingExercise = null },
            onSave = { saved ->
                val exercises = if (editingExercise == null) routine.exercises + saved else routine.exercises.map { if (it.id == saved.id) saved else it }
                onChange(routine.copy(exercises = exercises))
                addingExercise = false
                editingExercise = null
            },
        )
    }
    if (renaming) NameDialog("Rename routine", routine.name, { renaming = false }) { onChange(routine.copy(name = it)); renaming = false }
}

@Composable
private fun ScheduleEditorDialog(
    original: ScheduledItem?,
    routines: List<CustomRoutine>,
    onDismiss: () -> Unit,
    onSave: (ScheduledItem) -> Unit,
) {
    var title by remember(original) { mutableStateOf(original?.title.orEmpty()) }
    var subtitle by remember(original) { mutableStateOf(original?.subtitle.orEmpty()) }
    var selectedDays by remember(original) { mutableStateOf(original?.activeDays() ?: setOf(DayOfWeek.MONDAY)) }
    var repeat by remember(original) { mutableStateOf(repeatPattern(selectedDays)) }
    var destination by remember(original) { mutableStateOf(original?.destination ?: Destination.FITBOD) }
    var routineId by remember(original, routines) { mutableStateOf(original?.routineId ?: routines.firstOrNull()?.id) }
    val selectedRoutine = routines.firstOrNull { it.id == routineId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add scheduled item" else "Edit scheduled item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle") }, singleLine = true)
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScheduleRepeat.entries) { option ->
                        FilterChip(
                            selected = repeat == option,
                            onClick = {
                                repeat = option
                                selectedDays = repeatDays(option, selectedDays.minByOrNull(DayOfWeek::getValue) ?: DayOfWeek.MONDAY, selectedDays)
                            },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                Text("Days", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(DayOfWeek.entries) { option ->
                        FilterChip(
                            selected = option in selectedDays,
                            onClick = {
                                val updated = if (repeat == ScheduleRepeat.WEEKLY) {
                                    setOf(option)
                                } else if (option in selectedDays && selectedDays.size > 1) {
                                    selectedDays - option
                                } else {
                                    selectedDays + option
                                }
                                selectedDays = updated
                                repeat = repeatPattern(updated)
                            },
                            label = { Text(option.shortName()) },
                        )
                    }
                }
                Text("Opens", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Destination.entries) { option ->
                        FilterChip(
                            selected = destination == option,
                            onClick = { destination = option },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                if (destination == Destination.CUSTOM) {
                    OutlinedButton(
                        onClick = {
                            if (routines.isNotEmpty()) {
                                val index = routines.indexOfFirst { it.id == routineId }.coerceAtLeast(0)
                                routineId = routines[(index + 1) % routines.size].id
                            }
                        },
                        enabled = routines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(selectedRoutine?.let { "Routine: ${it.name}" } ?: "Create a routine first") }
                }
                BrandedCard(Modifier.fillMaxWidth(), containerColor = AppSurfaceRaised) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WEEKLY PREVIEW", color = AppMint, style = MaterialTheme.typography.labelMedium)
                        Text(title.ifBlank { "Your scheduled item" }, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { previewDay ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape).background(if (previewDay in selectedDays) AppBlueStrong else AppBackground),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(previewDay.shortName().take(1), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        Text(selectedDays.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduledItem(
                            id = original?.id ?: newId(),
                            title = title.trim(),
                            subtitle = subtitle.trim(),
                            day = selectedDays.minByOrNull(DayOfWeek::getValue) ?: DayOfWeek.MONDAY,
                            destination = destination,
                            routineId = if (destination == Destination.CUSTOM) routineId else null,
                            enabled = original?.enabled ?: true,
                            repeatDays = selectedDays,
                        ),
                    )
                },
                enabled = title.isNotBlank() && (destination != Destination.CUSTOM || selectedRoutine != null),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExerciseEditorDialog(original: Exercise?, onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by remember(original) { mutableStateOf(original?.name.orEmpty()) }
    var notes by remember(original) { mutableStateOf(original?.notes.orEmpty()) }
    var sets by remember(original) { mutableStateOf(original?.sets.orEmpty()) }
    var target by remember(original) { mutableStateOf(original?.target.orEmpty()) }
    var timed by remember(original) { mutableStateOf(original?.timerSeconds != null) }
    var seconds by remember(original) { mutableStateOf(original?.timerSeconds?.toString() ?: "20") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Exercise name") }, singleLine = true)
                OutlinedTextField(sets, { sets = it }, label = { Text("Sets") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Target") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timed exercise", modifier = Modifier.weight(1f))
                    Switch(timed, { timed = it })
                }
                if (timed) OutlinedTextField(
                    seconds,
                    { seconds = it.filter(Char::isDigit) },
                    label = { Text("Timer seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(Exercise(original?.id ?: newId(), name.trim(), notes.trim(), sets.trim(), target.trim(), if (timed) seconds.toIntOrNull() else null)) },
                enabled = name.isNotBlank() && sets.isNotBlank() && target.isNotBlank() && (!timed || (seconds.toIntOrNull() ?: 0) > 0),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Routine name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun DayOfWeek.displayName() = name.lowercase().replaceFirstChar { it.titlecase() }
private fun DayOfWeek.shortName() = displayName().take(3)
private fun ScheduleRepeat.displayName() = when (this) {
    ScheduleRepeat.WEEKLY -> "Weekly"
    ScheduleRepeat.WEEKDAYS -> "Weekdays"
    ScheduleRepeat.DAILY -> "Every day"
    ScheduleRepeat.CUSTOM -> "Custom"
}
private fun Set<DayOfWeek>.summary(): String = when (repeatPattern(this)) {
    ScheduleRepeat.WEEKLY -> "Every ${single().displayName()}"
    ScheduleRepeat.WEEKDAYS -> "Every weekday"
    ScheduleRepeat.DAILY -> "Every day"
    ScheduleRepeat.CUSTOM -> sortedBy(DayOfWeek::getValue).joinToString(" · ") { it.shortName() }
}
private fun Destination.displayName() = when (this) {
    Destination.FITBOD -> "Fitbod"
    Destination.JUSTRUN -> "JustRun"
    Destination.CUSTOM -> "Custom routine"
}
