package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RunRouteScreen(
    routes: List<RunRoute>,
    onSave: (RunRoute) -> Boolean,
    onDelete: (String) -> Boolean,
    onBack: () -> Unit,
    initialSelectedId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var addWaypoint by rememberSaveable { mutableStateOf(false) }
    var cuePoint by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = routes.firstOrNull { it.id == selectedId }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val narrow = LocalConfiguration.current.screenWidthDp < 360
    val goBack = { if (selectedId != null) selectedId = null else onBack() }
    BackHandler(onBack = goBack)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { importGpxRoute(it, newId(), "Imported route") }
                            ?: error("Could not open the GPX file.")
                    }
                }
                importing = false
                result.onSuccess { route ->
                    if (onSave(route)) {
                        selectedId = route.id
                        message = "Route imported."
                    } else message = "Could not save route. Try again."
                }.onFailure { error -> message = error.message ?: "Could not read this GPX file." }
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(if (selected == null) "Run routes" else "Route details", goBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected == null) {
                item {
                    Text("RUN ROUTES", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Text("Your saved routes", style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Import a GPX track or route to use as an offline route plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text(if (importing) " Importing…" else " Import GPX")
                    }
                }
                if (routes.isEmpty()) item {
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("No routes saved", fontWeight = FontWeight.Bold)
                            Text("Choose a GPX file from your device to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(routes, key = { it.id }) { route ->
                    AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Edit ${route.name}") { selectedId = route.id; message = null }) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(route.name, fontWeight = FontWeight.Bold)
                            Text("${route.points.size} track points · ${route.waypointIndices.size} waypoints · ${route.turnCues.size} turn cues",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    Text("SAVED ROUTE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Text(selected.name, style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("${selected.points.size} track points · ${selected.waypointIndices.size} waypoints · ${selected.turnCues.size} turn cues",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { RunRoutePreview(selected) }
                item {
                    if (largeText || narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { rename = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Edit, null); Text(" Rename")
                            }
                            OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Delete, null); Text(" Delete")
                            }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, null); Text(" Rename") }
                        OutlinedButton(onClick = { delete = true }) { Icon(Icons.Default.Delete, null); Text(" Delete") }
                    }
                }
                item {
                    if (largeText) Column {
                        Text("WAYPOINTS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { addWaypoint = true }) { Text("Add waypoint") }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WAYPOINTS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { addWaypoint = true }) { Text("Add waypoint") }
                    }
                }
                items(selected.waypointIndices, key = { "waypoint-$it" }) { index ->
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(when (index) { 0 -> "Start"; selected.points.lastIndex -> "Finish"; else -> "Point ${index + 1}" },
                                Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { cuePoint = index }) { Text("Turn cue") }
                            if (index != 0 && index != selected.points.lastIndex) IconButton(onClick = {
                                selected.withoutWaypoint(index)?.let { if (!onSave(it)) message = "Could not save waypoint." }
                            }) { Icon(Icons.Default.Delete, "Remove waypoint") }
                        }
                    }
                }
                item { Text("TURN CUES", color = AppGold, style = MaterialTheme.typography.labelMedium) }
                if (selected.turnCues.isEmpty()) item {
                    Text("No turn cues yet. Add one at a waypoint.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(selected.turnCues, key = { "cue-${it.pointIndex}" }) { cue ->
                    AppSurfaceCard(Modifier.fillMaxWidth().clickable { cuePoint = cue.pointIndex }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Point ${cue.pointIndex + 1} · ${cue.kind.label()}", fontWeight = FontWeight.Bold)
                                Text(cue.instruction, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                selected.withoutTurnCue(cue.pointIndex)?.let { if (!onSave(it)) message = "Could not delete turn cue." }
                            }) { Icon(Icons.Default.Delete, "Delete turn cue") }
                        }
                    }
                }
            }
            message?.let { notice -> item { Text(notice, color = AppMint) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (selected != null && rename) RouteTextDialog("Rename route", selected.name, "Route name", { rename = false }) { name ->
        selected.renamed(name)?.let { if (onSave(it)) rename = false else message = "Could not rename route." }
    }
    if (selected != null && addWaypoint) RouteTextDialog("Add waypoint", "", "Track point number", { addWaypoint = false }, numeric = true) { value ->
        val index = value.toIntOrNull()?.minus(1)
        val updated = index?.let(selected::withWaypoint)
        if (updated == null) message = "Enter a track point number from 1 to ${selected.points.size}."
        else if (onSave(updated)) addWaypoint = false else message = "Could not save waypoint."
    }
    if (selected != null && cuePoint != null) {
        val index = checkNotNull(cuePoint)
        val current = selected.turnCues.firstOrNull { it.pointIndex == index }
        RunTurnCueDialog(index, current, onDismiss = { cuePoint = null }) { cue ->
            selected.withTurnCue(cue)?.let { if (onSave(it)) cuePoint = null else message = "Could not save turn cue." }
        }
    }
    if (selected != null && delete) AppConfirmationDialog(
        title = "Delete ${selected.name}?",
        message = "This removes the saved route and its turn cues.",
        confirmLabel = "Delete route",
        onConfirm = { if (onDelete(selected.id)) { selectedId = null; delete = false } else message = "Could not delete route." },
        onDismiss = { delete = false },
    )
}

@Composable
private fun RunRoutePreview(route: RunRoute) {
    val track = AppBlue
    val marker = AppGold
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Track preview", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                val minX = route.points.minOf { it.longitudeE7 }.toDouble()
                val maxX = route.points.maxOf { it.longitudeE7 }.toDouble()
                val minY = route.points.minOf { it.latitudeE7 }.toDouble()
                val maxY = route.points.maxOf { it.latitudeE7 }.toDouble()
                val width = (maxX - minX).coerceAtLeast(1.0)
                val height = (maxY - minY).coerceAtLeast(1.0)
                val scale = minOf((size.width - 24f) / width, (size.height - 24f) / height)
                val offsetX = (size.width - width * scale) / 2
                val offsetY = (size.height - height * scale) / 2
                fun position(point: RunRoutePoint) = Offset(
                    (offsetX + (point.longitudeE7 - minX) * scale).toFloat(),
                    (size.height - offsetY - (point.latitudeE7 - minY) * scale).toFloat(),
                )
                val path = Path()
                route.points.forEachIndexed { index, point ->
                    val p = position(point)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, track, style = Stroke(width = 4.dp.toPx()))
                route.waypointIndices.forEach { index -> drawCircle(marker, 5.dp.toPx(), position(route.points[index])) }
            }
            Text("Route shape preview", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RouteTextDialog(title: String, initial: String, label: String, onDismiss: () -> Unit,
                            numeric: Boolean = false, onSave: (String) -> Unit) {
    var value by rememberSaveable(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(if (numeric) 8 else 200) }, label = { Text(label) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RunTurnCueDialog(pointIndex: Int, original: RunTurnCue?, onDismiss: () -> Unit, onSave: (RunTurnCue) -> Unit) {
    var kind by rememberSaveable(pointIndex) { mutableStateOf(original?.kind ?: RunTurnKind.CONTINUE) }
    var instruction by rememberSaveable(pointIndex) { mutableStateOf(original?.instruction.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn cue · point ${pointIndex + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(kind.label()) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        RunTurnKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label()) }, onClick = { kind = option; expanded = false })
                        }
                    }
                }
                OutlinedTextField(instruction, { instruction = it.take(500) }, label = { Text("Instruction") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = instruction.isNotBlank(),
            onClick = { onSave(RunTurnCue(pointIndex, kind, instruction)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun RunTurnKind.label(): String = when (this) {
    RunTurnKind.CONTINUE -> "Continue"
    RunTurnKind.LEFT -> "Turn left"
    RunTurnKind.RIGHT -> "Turn right"
    RunTurnKind.U_TURN -> "U-turn"
    RunTurnKind.ARRIVE -> "Arrive"
}
