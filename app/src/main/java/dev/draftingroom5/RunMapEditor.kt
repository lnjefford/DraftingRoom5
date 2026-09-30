package dev.draftingroom5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.XYTileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Stops are user-authored; walking geometry is fetched on demand and saved for offline runs. */
@Composable
internal fun RunMapEditor(onSave: (RunRoute) -> Boolean, onBack: () -> Unit, initial: RunRoute? = null) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val keyStore = remember(context) { RunRoutingKeyStore(context) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var anchors by remember(initial?.id) { mutableStateOf(initial?.let { source -> source.waypointIndices.map(source.points::get) } ?: emptyList()) }
    var route by remember(initial?.id) { mutableStateOf(initial?.let { WalkingRoute(it.points, it.waypointIndices) }) }
    var routing by remember { mutableStateOf(false) }
    var routingMessage by remember { mutableStateOf<String?>(null) }
    var keyReady by remember { mutableStateOf(keyStore.load() != null) }
    var keyVersion by remember { mutableIntStateOf(0) }
    var keyDialog by remember { mutableStateOf(!keyReady && !inspectionMode) }
    var message by remember { mutableStateOf<String?>(null) }
    var drawMode by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val map = remember(context) {
        MapView(context).apply {
            configureRunTiles(context, this)
            setMultiTouchControls(true)
            controller.setZoom(if (initial == null) 3.0 else 15.0)
            val first = initial?.points?.firstOrNull()
            controller.setCenter(first?.let { GeoPoint(it.latitudeE7 / 10_000_000.0,
                it.longitudeE7 / 10_000_000.0) } ?: GeoPoint(0.0, 0.0))
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Location permission is needed to center the map."
        else if (!centerOnRecentLocation(context, map)) message = "No recent location is available. Pan the map to your route."
    }
    val centerOnMe = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!centerOnRecentLocation(context, map)) message = "No recent location is available. Pan the map to your route."
        } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(anchors, keyReady, keyVersion) {
        if (initial != null && keyVersion == 0 && anchors == initial.waypointIndices.map(initial.points::get)) return@LaunchedEffect
        route = null
        routingMessage = null
        if (anchors.size < 2 || !keyReady) return@LaunchedEffect
        routing = true
        delay(300)
        try {
            val planned = withContext(Dispatchers.IO) {
                val key = keyStore.load() ?: throw WalkingRouteException("Add your routing key to plan a route.")
                requestWalkingRoute(anchors, key)
            }
            route = planned
        } catch (error: WalkingRouteException) { routingMessage = error.explanation }
        finally { routing = false }
    }
    BackHandler(onBack = onBack)
    val distancePoints = route?.points ?: anchors
    val distanceMeters = distancePoints.zipWithNext().sumOf { (a, b) -> geoDistanceMeters(a, b) }
    val distanceLabel = String.format(Locale.US, "%.1f mi", distanceMeters / 1609.344)
    val saveRoute = {
        val clean = name.trim()
        val planned = route
        if (clean.isBlank() || planned == null) message = "Name the route and wait for a walking path."
        else {
            val saved = RunRoute(initial?.id ?: newId(), (initial?.revision ?: 0) + 1, clean,
                planned.points, planned.waypointIndices,
                if (initial != null && planned.points == initial.points) initial.turnCues else emptyList())
            if (!onSave(saved)) message = "Could not save route." else onBack()
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(if (initial == null) "Design route" else "Edit route", onBack) {
            TextButton(onClick = saveRoute, enabled = route != null && !routing && name.isNotBlank()) { Text("Save") }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "More route options") }
                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(if (keyReady) "Routing key" else "Set up routing") },
                        onClick = { menuExpanded = false; keyDialog = true })
                }
            }
        } }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppBackgroundDeep)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().graphicsLayer { clip = true }, update = { view ->
                view.overlays.clear()
                view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                        if (!drawMode) {
                            if (anchors.size < 50) {
                                anchors = anchors + RunRoutePoint((point.latitude * 10_000_000).toInt(),
                                    (point.longitude * 10_000_000).toInt())
                                route = null
                            } else message = "The routing service allows up to 50 stops."
                        }
                        return true
                    }
                    override fun longPressHelper(point: GeoPoint): Boolean = false
                }))
                route?.let { planned ->
                    val points = planned.points.map { GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0) }
                    view.overlays.add(Polyline().apply {
                        setPoints(points)
                        outlinePaint.color = android.graphics.Color.argb(90, 32, 103, 237)
                        outlinePaint.strokeWidth = 16f * context.resources.displayMetrics.density
                    })
                    view.overlays.add(Polyline().apply {
                        setPoints(points)
                        outlinePaint.color = android.graphics.Color.rgb(87, 156, 255)
                        outlinePaint.strokeWidth = 5f * context.resources.displayMetrics.density
                    })
                }
                anchors.forEachIndexed { index, anchor -> view.overlays.add(Marker(view).apply {
                    position = GeoPoint(anchor.latitudeE7 / 10_000_000.0, anchor.longitudeE7 / 10_000_000.0)
                    title = if (index == 0) "Start" else "Waypoint $index"
                    icon = routeMarker(context, index)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }) }
                view.invalidate()
            })
            if (drawMode) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().pointerInput(drawMode, anchors) {
                    detectDragGestures(
                        onDragStart = { stroke = listOf(it) },
                        onDragEnd = {
                            val remaining = 50 - anchors.size
                            if (stroke.size >= 2 && remaining >= 2) {
                                val sampled = stroke.filterIndexed { index, _ ->
                                    index == 0 || index == stroke.lastIndex || index % (stroke.size / remaining).coerceAtLeast(1) == 0
                                }.take(remaining).map { point ->
                                    val geo = map.projection.fromPixels(point.x.toInt(), point.y.toInt())
                                    RunRoutePoint((geo.latitude * 10_000_000).toInt(), (geo.longitude * 10_000_000).toInt())
                                }
                                anchors = anchors + sampled
                                route = null
                            } else if (remaining < 2) message = "The routing service allows up to 50 stops."
                            stroke = emptyList()
                        },
                        onDragCancel = { stroke = emptyList() },
                    ) { change, _ -> stroke = stroke + change.position; change.consume() }
                }) {
                    if (stroke.size >= 2) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(stroke.first().x, stroke.first().y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, AppGold, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RunMapMode("Waypoints", !drawMode, Modifier.weight(1f)) { drawMode = false; stroke = emptyList() }
                RunMapMode("Draw", drawMode, Modifier.weight(1f)) { drawMode = true }
                RunMapControl("Center on me", Icons.Default.MyLocation, centerOnMe)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RunMapControl("Undo last point", Icons.Default.Undo, { anchors = anchors.dropLast(1); route = null }, anchors.isNotEmpty())
                RunMapControl("Clear route", Icons.Default.Delete, { anchors = emptyList(); route = null }, anchors.isNotEmpty())
            }
            Column(Modifier.align(Alignment.TopStart).padding(top = 88.dp, start = 14.dp)
                .clip(RoundedCornerShape(16.dp)).background(AppBackground.copy(alpha = 0.94f))
                .border(1.dp, AppBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("LIVE DISTANCE", color = AppGold, style = MaterialTheme.typography.labelSmall)
                Text(distanceLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (drawMode) "Trace the path you want to follow" else "Tap the map to add the next point",
                    Modifier.padding(8.dp).clip(RoundedCornerShape(24.dp))
                        .background(AppBackground.copy(alpha = 0.94f)).border(1.dp, AppBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White, textAlign = TextAlign.Center)
                Text("© OpenStreetMap contributors · Routing: HeiGIT",
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(AppSurface).border(1.dp, AppBorder, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(8.dp)).background(AppBorder))
                    if (drawMode) {
                        Text("DRAW MODE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text("Trace the path you want", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Drag across the map to add stops. The walking route follows streets and paths once routing completes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$distanceLabel · ${anchors.size} ${if (anchors.size == 1) "stop" else "stops"}",
                            color = AppMint, style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { drawMode = false; stroke = emptyList() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep)) {
                            Text("Finish drawing")
                        }
                    } else {
                        Text("ROUTE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(name, { name = it.take(200) }, placeholder = { Text("Name this route") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineMedium)
                        Text("$distanceLabel · ${anchors.size} ${if (anchors.size == 1) "stop" else "stops"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (routing) Text("Finding walking path…", color = AppMint)
                        if (!keyReady && !inspectionMode) Text("Set up routing to follow walking paths.", color = AppGold)
                        routingMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = saveRoute, enabled = route != null && !routing && name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep)) {
                            Text("Save route")
                        }
                    }
                }
            }
        }
    }
    if (keyDialog) RunRoutingKeyDialog(
        onDismiss = { keyDialog = false },
        onSave = { key ->
            if (keyStore.save(key)) {
                keyReady = true
                keyVersion++
                route = null
                keyDialog = false
                message = null
                true
            } else false
        },
    )
}

@Composable
private fun RunRoutingKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up walking routes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create a free HeiGIT account, then paste your own openrouteservice API key here. The key stays encrypted on this phone and is not included in backups. Your chosen stops are sent to HeiGIT to calculate each walking path.")
                TextButton(onClick = { uriHandler.openUri(RUN_ROUTING_ACCOUNT_URL) }) { Text("Get a free key") }
                OutlinedTextField(value, { value = it.take(1024); error = false },
                    label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (error) Text("Couldn’t save the key. Check that it contains no spaces.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { if (!onSave(value)) error = true }) { Text("Save key") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RunMapMode(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(28.dp))
        .background(if (selected) AppBlueStrong else AppBackground.copy(alpha = 0.94f))
        .border(1.dp, if (selected) AppBlue else AppBorder, RoundedCornerShape(28.dp))
        .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(if (label == "Draw") Icons.Default.Edit else Icons.Default.Place, null, Modifier.size(18.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RunMapControl(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                          onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50))
            .background(AppBackground.copy(alpha = 0.94f))
            .border(1.dp, AppBorder, RoundedCornerShape(50))) {
        Icon(icon, label, tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun routeMarker(context: Context, index: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt().coerceAtLeast(34)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = if (index == 0) android.graphics.Color.rgb(79, 224, 176)
        else android.graphics.Color.rgb(255, 198, 109)
    canvas.drawCircle(center, center, size * 0.34f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    canvas.drawCircle(center, center, size * 0.34f, paint)
    if (index > 0) {
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.rgb(5, 24, 42)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = 14f * density
        canvas.drawText(index.toString(), center, center - (paint.ascent() + paint.descent()) / 2f, paint)
    }
    return BitmapDrawable(context.resources, bitmap)
}

internal fun configureRunTiles(context: Context, map: MapView) {
    Configuration.getInstance().load(context, context.getSharedPreferences("osm_map", 0))
    Configuration.getInstance().userAgentValue = "DraftingRoom5/${BuildConfig.VERSION_NAME} (${context.packageName})"
    Configuration.getInstance().osmdroidTileCache = java.io.File(context.cacheDir, "osm-tiles")
    map.setTileSource(XYTileSource("OpenStreetMap", 0, 19, 256, ".png",
        arrayOf("https://tile.openstreetmap.org/")))
    map.setBackgroundColor(android.graphics.Color.rgb(6, 20, 34))
    map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(floatArrayOf(
        -0.07f, -0.17f, -0.03f, 0f, 75f,
        -0.08f, -0.14f, -0.03f, 0f, 94f,
        -0.08f, -0.10f, -0.07f, 0f, 117f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

private fun centerOnRecentLocation(context: Context, map: MapView): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
    val manager = context.getSystemService(LocationManager::class.java)
    val location = try {
        listOfNotNull(manager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).maxByOrNull { it.time }
    } catch (_: SecurityException) { null }
    catch (_: IllegalArgumentException) { null }
    if (location == null) return false
    map.controller.setZoom(15.0)
    map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
    return true
}
