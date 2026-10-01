package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    currentRouteId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var editingMap by remember { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
        if (selected != null) RunRouteMap(selected, Modifier.fillMaxWidth().height(220.dp))
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = if (selected == null) 20.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected == null) {
                item {
                    EditorialHeading("RUN ROUTES", if (onSelectRoute == null) "Designed routes" else "Choose a route",
                        if (onSelectRoute == null) "Choose, edit, or create a route for your run." else
                            "Select a saved route or design a new one.")
                    Spacer(Modifier.height(8.dp))
                }
                if (onSelectRoute != null) {
                    item {
                        RunSelectionCard(null, null, narrow, currentRouteId == null,
                            onClick = { onSelectRoute(null) })
                    }
                    item {
                        RunSelectionCard(null, TREADMILL_ROUTE_ID, narrow, currentRouteId == TREADMILL_ROUTE_ID,
                            onClick = { onSelectRoute(TREADMILL_ROUTE_ID) })
                    }
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
                    RunSelectionCard(route, route.id, narrow, onSelectRoute != null && route.id == currentRouteId,
                        lastUsed = route.id == lastUsedRouteId,
                        onClick = {
                            if (onSelectRoute == null) { selectedId = route.id; message = null }
                            else onSelectRoute(route.id)
                        },
                        onEdit = if (onSelectRoute == null) null else ({ selectedId = route.id }))
                }
                item {
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
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(AppBackground).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                            .clip(RoundedCornerShape(5.dp)).background(AppBorder))
                        EditorialHeading("SAVED ROUTE", selected.name, selected.distanceLabel())
                        val turnCount = selected.turnCues.count { it.kind.isTurn() }
                        Text("$turnCount ${if (turnCount == 1) "turn" else "turns"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onSelectRoute != null) Button(onClick = { onSelectRoute(selected.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use this route") }
                        Text("MANAGE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        RunRouteAction("Edit route", "Edit map and turn directions", Icons.Default.Edit) { editingMap = true }
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
                    }
                }
            }
            message?.let { notice -> item { Text(notice, Modifier.padding(horizontal = if (selected == null) 0.dp else 20.dp), color = AppMint) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
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
internal fun RunSelectionCard(
    route: RunRoute?,
    routeId: String?,
    narrow: Boolean,
    selected: Boolean,
    lastUsed: Boolean = false,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val treadmill = routeId == TREADMILL_ROUTE_ID
    val title = route?.name ?: if (treadmill) "Treadmill" else "Free run"
    val turnCount = route?.turnCues?.count { it.kind.isTurn() } ?: 0
    val subtitle = route?.distanceLabel() ?: if (treadmill) "Indoor timing · no GPS" else "Outdoor GPS · no route guidance"
    val detail = when {
        selected -> "SELECTED"
        route != null && lastUsed -> "LAST USED"
        route != null -> "$turnCount ${if (turnCount == 1) "turn" else "turns"}"
        treadmill -> "INDOOR RUN"
        else -> "OPEN ROUTE"
    }
    var menuExpanded by remember(routeId) { mutableStateOf(false) }
    AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClickLabel = "Choose $title", onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 12.dp)) {
            val thumbnail = Modifier.size(if (narrow) 68.dp else 104.dp)
            when {
                route != null -> RunRouteShape(route, thumbnail)
                treadmill -> Image(painterResource(R.drawable.run_treadmill_card), null,
                    thumbnail.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else -> FreeRunShape(thumbnail)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (narrow) 18.sp else 20.sp,
                    lineHeight = if (narrow) 22.sp else 24.sp,
                ),
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (narrow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                Text(detail, color = if (selected || lastUsed) AppMint else AppBlue,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (onEdit == null) Text("›", color = AppBlue, style = MaterialTheme.typography.headlineMedium)
            else Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Options for $title") }
                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Manage route") }, onClick = { menuExpanded = false; onEdit() })
                }
            }
        }
    }
}

@Composable
private fun FreeRunShape(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(AppBackgroundDeep)
        .border(1.dp, AppBorder, RoundedCornerShape(14.dp))) {
        drawRect(Color(0xFF0A1C2C))
        val road = Color(0xFF29445D)
        repeat(5) { index ->
            val y = size.height * (index + .6f) / 5f
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(size.width * .35f, y - size.height * .15f,
                    size.width * .65f, y + size.height * .1f, size.width, y - size.height * .06f)
            }
            drawPath(path, road, style = Stroke(width = 1.5.dp.toPx()))
        }
        drawCircle(AppBlue.copy(alpha = .2f), radius = size.minDimension * .24f,
            center = Offset(size.width * .5f, size.height * .5f))
        drawCircle(AppMint, radius = size.minDimension * .1f,
            center = Offset(size.width * .5f, size.height * .5f))
        drawCircle(Color.White, radius = size.minDimension * .035f,
            center = Offset(size.width * .5f, size.height * .5f))
    }
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
        routePreviewPoints(route.points).forEachIndexed { index, point ->
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
        // A cue dialog changes Compose state, not the route. Building thousands of native map
        // points again on that recomposition can stall the UI, so draw only for a new revision.
        key(route.id, route.revision) {
            AndroidView(factory = {
                map.apply {
                    overlays.add(Polyline().apply {
                        setPoints(routePreviewPoints(route.points).map { GeoPoint(it.latitudeE7 / 10_000_000.0,
                            it.longitudeE7 / 10_000_000.0) })
                        outlinePaint.color = android.graphics.Color.rgb(96, 163, 255)
                        outlinePaint.strokeWidth = 6f * context.resources.displayMetrics.density
                    })
                    route.waypointIndices.forEachIndexed { position, index ->
                        overlays.add(Marker(this).apply {
                            val point = route.points[index]
                            this.position = GeoPoint(point.latitudeE7 / 10_000_000.0,
                                point.longitudeE7 / 10_000_000.0)
                            icon = routeMarker(context, position)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        })
                    }
                }
            }, modifier = Modifier.fillMaxSize())
        }
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

internal fun routePreviewPoints(points: List<RunRoutePoint>, maxPoints: Int = 3000): List<RunRoutePoint> {
    require(maxPoints >= 2)
    if (points.size <= maxPoints) return points
    val step = (points.lastIndex + maxPoints - 2) / (maxPoints - 1)
    return points.filterIndexed { index, _ -> index == points.lastIndex || index % step == 0 }
}

@Composable
internal fun RunTurnCueDialog(pointIndex: Int, original: RunTurnCue?, onDismiss: () -> Unit, onSave: (RunTurnCue) -> Unit) {
    var kind by rememberSaveable(pointIndex) { mutableStateOf(original?.kind?.takeIf(RunTurnKind::isTurn) ?: RunTurnKind.LEFT) }
    var instruction by rememberSaveable(pointIndex) { mutableStateOf(original?.instruction.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit turn") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(kind.label()) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        listOf(RunTurnKind.LEFT, RunTurnKind.RIGHT, RunTurnKind.U_TURN).forEach { option ->
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

internal fun RunTurnKind.label(): String = when (this) {
    RunTurnKind.CONTINUE -> "Continue"
    RunTurnKind.LEFT -> "Turn left"
    RunTurnKind.RIGHT -> "Turn right"
    RunTurnKind.U_TURN -> "U-turn"
    RunTurnKind.ARRIVE -> "Arrive"
}
