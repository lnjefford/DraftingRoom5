package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
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
    lastUsedRouteId: String? = null,
    onSelectRoute: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var editingMap by remember { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var addWaypoint by rememberSaveable { mutableStateOf(false) }
    var cuePoint by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = routes.firstOrNull { it.id == selectedId }
    if (editingMap && selected != null) {
        RunMapEditor(onSave = onSave, onBack = { editingMap = false }, initial = selected)
        return
    }
    if (drawing) {
        RunMapEditor(onSave = { route ->
            val saved = onSave(route)
            if (saved) onSelectRoute?.invoke(route.id)
            saved
        }, onBack = { drawing = false })
        return
    }
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
                        if (onSelectRoute == null) {
                            selectedId = route.id
                            message = "Route imported."
                        } else onSelectRoute(route.id)
                    } else message = "Could not save route. Try again."
                }.onFailure { error -> message = error.message ?: "Could not read this GPX file." }
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(selected?.name ?: "Routes", goBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = if (selected == null) 20.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected == null) {
                item {
                    EditorialHeading("RUN ROUTES", if (onSelectRoute == null) "Designed routes" else "Choose a route",
                        if (onSelectRoute == null) "Choose, edit, or create a route for your run." else
                            "Select a saved route or design a new one.")
                    Spacer(Modifier.height(8.dp))
                }
                if (routes.isEmpty()) item {
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("No routes saved", fontWeight = FontWeight.Bold)
                            Text("Design a route or import a GPX file to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(routes, key = { it.id }) { route ->
                    var routeMenuExpanded by remember(route.id) { mutableStateOf(false) }
                    AppSurfaceCard(Modifier.fillMaxWidth().clickable(
                        onClickLabel = if (onSelectRoute == null) "Edit ${route.name}" else "Select ${route.name}") {
                        if (onSelectRoute == null) { selectedId = route.id; message = null }
                        else onSelectRoute(route.id)
                    }) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RunRouteShape(route, Modifier.size(if (narrow) 78.dp else 104.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(route.name, style = if (narrow) MaterialTheme.typography.titleMedium else
                                    MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(route.distanceLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (route.id == lastUsedRouteId) "LAST USED" else "${route.waypointIndices.size} waypoints",
                                    color = if (route.id == lastUsedRouteId) AppMint else AppBlue,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (onSelectRoute == null) Text("›", color = AppBlue,
                                style = MaterialTheme.typography.headlineMedium)
                            else Box {
                                IconButton(onClick = { routeMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, "Options for ${route.name}")
                                }
                                DropdownMenu(routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Edit route") },
                                        onClick = { routeMenuExpanded = false; selectedId = route.id })
                                }
                            }
                        }
                    }
                }
                item {
                    if (onSelectRoute != null) TextButton(onClick = { onSelectRoute(null) },
                        modifier = Modifier.fillMaxWidth()) { Text("No route") }
                    OutlinedButton(onClick = { drawing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.Add, null); Text(" Design a new route")
                    }
                    TextButton(onClick = { picker.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) },
                        enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (importing) "Importing…" else "Import GPX route")
                    }
                }
            } else {
                item {
                    RunRouteMap(selected, Modifier.fillMaxWidth().height(350.dp))
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(AppBackground).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                            .clip(RoundedCornerShape(5.dp)).background(AppBorder))
                        EditorialHeading("SAVED ROUTE", selected.name, selected.distanceLabel())
                        if (onSelectRoute != null) Button(onClick = { onSelectRoute(selected.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use this route") }
                        Text("MANAGE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        RunRouteAction("Edit route", "Change points or redraw", Icons.Default.Edit) { editingMap = true }
                        RunRouteAction("Reverse direction", "Travel the route the other way", Icons.Default.SwapHoriz) {
                            if (onSave(selected.reversedDirection())) message = "Direction reversed. Turn cues cleared for safety."
                            else message = "Could not reverse route."
                        }
                        RunRouteAction("Duplicate route", "Create an editable copy", Icons.Default.ContentCopy) {
                            val copy = selected.duplicate(newId())
                            if (onSave(copy)) { selectedId = copy.id; message = "Route duplicated." }
                            else message = "Could not duplicate route."
                        }
                        OutlinedButton(onClick = { delete = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Delete, null); Text(" Delete route")
                        }
                        TextButton(onClick = { rename = true }) { Text("Rename route") }
                    }
                }
                item {
                    if (largeText) Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("WAYPOINTS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { addWaypoint = true }) { Text("Add waypoint") }
                    } else Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("WAYPOINTS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { addWaypoint = true }) { Text("Add waypoint") }
                    }
                }
                items(selected.waypointIndices, key = { "waypoint-$it" }) { index ->
                    AppSurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
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
                item { Text("TURN CUES", Modifier.padding(horizontal = 20.dp), color = AppGold, style = MaterialTheme.typography.labelMedium) }
                if (selected.turnCues.isEmpty()) item {
                    Text("No turn cues yet. Add one at a waypoint.", Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(selected.turnCues, key = { "cue-${it.pointIndex}" }) { cue ->
                    AppSurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { cuePoint = cue.pointIndex }) {
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
            message?.let { notice -> item { Text(notice, Modifier.padding(horizontal = if (selected == null) 0.dp else 20.dp), color = AppMint) } }
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
        message = "This removes the saved route and its turn cues. Run routines using it will have no route selected.",
        confirmLabel = "Delete route",
        onConfirm = { if (onDelete(selected.id)) { selectedId = null; delete = false } else message = "Could not delete route." },
        onDismiss = { delete = false },
    )
}

@Composable
internal fun RunRouteShape(route: RunRoute, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(AppBackgroundDeep)
        .border(1.dp, AppBorder, RoundedCornerShape(14.dp))) {
        drawRect(Color(0xFF0A1C2C))
        drawCircle(Color(0xFF16372E).copy(alpha = .8f), radius = size.minDimension * .27f,
            center = Offset(size.width * .78f, size.height * .28f))
        drawCircle(Color(0xFF16372E).copy(alpha = .55f), radius = size.minDimension * .2f,
            center = Offset(size.width * .15f, size.height * .86f))
        val road = Color(0xFF29445D).copy(alpha = .8f)
        val localRoad = Color(0xFF20394F).copy(alpha = .85f)
        repeat(6) { index ->
            val start = size.height * (index + .4f) / 6f
            val roadPath = Path().apply {
                moveTo(0f, start)
                cubicTo(size.width * .3f, start - size.height * .11f,
                    size.width * .56f, start + size.height * .1f,
                    size.width, start - size.height * .04f)
            }
            drawPath(roadPath, if (index % 2 == 0) road else localRoad,
                style = Stroke(width = (if (index % 2 == 0) 1.7f else 1.2f).dp.toPx()))
        }
        repeat(5) { index ->
            val start = size.width * (index + .4f) / 5f
            val roadPath = Path().apply {
                moveTo(start, 0f)
                cubicTo(start + size.width * .1f, size.height * .3f,
                    start - size.width * .08f, size.height * .66f,
                    start + size.width * .06f, size.height)
            }
            drawPath(roadPath, localRoad, style = Stroke(width = 1.2.dp.toPx()))
        }
        val minX = route.points.minOf { it.longitudeE7 }.toDouble()
        val maxX = route.points.maxOf { it.longitudeE7 }.toDouble()
        val minY = route.points.minOf { it.latitudeE7 }.toDouble()
        val maxY = route.points.maxOf { it.latitudeE7 }.toDouble()
        val width = (maxX - minX).coerceAtLeast(1.0)
        val height = (maxY - minY).coerceAtLeast(1.0)
        val scale = minOf(size.width * .78f / width, size.height * .78f / height)
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
        drawPath(path, AppBlueStrong.copy(alpha = 0.25f), style = Stroke(width = 11.dp.toPx()))
        drawPath(path, AppBlue, style = Stroke(width = 3.dp.toPx()))
        drawCircle(AppMint, 6.dp.toPx(), position(route.points.first()))
        drawCircle(Color.White, 7.dp.toPx(), position(route.points.first()), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun RunRouteMap(route: RunRoute, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        RunRouteShape(route, modifier)
        return
    }
    val context = LocalContext.current
    val map = remember(context, route.id, route.revision) {
        MapView(context).apply {
            configureRunTiles(context, this)
            setMultiTouchControls(true)
            val latitudes = route.points.map { it.latitudeE7 / 10_000_000.0 }
            val longitudes = route.points.map { it.longitudeE7 / 10_000_000.0 }
            val bounds = BoundingBox(
                latitudes.max() + 0.001, longitudes.max() + 0.001,
                latitudes.min() - 0.001, longitudes.min() - 0.001)
            post { zoomToBoundingBox(bounds, false, 48) }
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    Box(modifier.clip(RoundedCornerShape(18.dp))) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { view ->
            view.overlays.clear()
            view.overlays.add(Polyline().apply {
                setPoints(route.points.map { GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0) })
                outlinePaint.color = android.graphics.Color.rgb(96, 163, 255)
                outlinePaint.strokeWidth = 6f * context.resources.displayMetrics.density
            })
            route.waypointIndices.forEachIndexed { position, index ->
                view.overlays.add(Marker(view).apply {
                    val point = route.points[index]
                    this.position = GeoPoint(point.latitudeE7 / 10_000_000.0, point.longitudeE7 / 10_000_000.0)
                    icon = routeMarker(context, position)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            view.invalidate()
        })
        Text("© OpenStreetMap contributors", Modifier.align(Alignment.BottomStart)
            .background(AppBackground.copy(alpha = 0.85f)).padding(5.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RunRouteAction(label: String, detail: String,
                           icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = AppBlue)
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = AppBlue, style = MaterialTheme.typography.headlineMedium)
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
