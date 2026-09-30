package dev.draftingroom5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
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

/** Stops are user-authored; walking geometry is fetched on demand and saved for offline runs. */
@Composable
internal fun RunMapEditor(onSave: (RunRoute) -> Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val keyStore = remember(context) { RunRoutingKeyStore(context) }
    var name by remember { mutableStateOf("") }
    var anchors by remember { mutableStateOf<List<RunRoutePoint>>(emptyList()) }
    var route by remember { mutableStateOf<WalkingRoute?>(null) }
    var routing by remember { mutableStateOf(false) }
    var routingMessage by remember { mutableStateOf<String?>(null) }
    var keyReady by remember { mutableStateOf(keyStore.load() != null) }
    var keyVersion by remember { mutableIntStateOf(0) }
    var keyDialog by remember { mutableStateOf(!keyReady) }
    var message by remember { mutableStateOf<String?>(null) }
    val map = remember(context) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_map", 0))
        Configuration.getInstance().userAgentValue = "DraftingRoom5/${BuildConfig.VERSION_NAME} (${context.packageName})"
        Configuration.getInstance().osmdroidTileCache = java.io.File(context.cacheDir, "osm-tiles")
        MapView(context).apply {
            setTileSource(XYTileSource("OpenStreetMap", 0, 19, 256, ".png",
                arrayOf("https://tile.openstreetmap.org/")))
            setMultiTouchControls(true)
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(0.0, 0.0))
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
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Draw route", onBack) }, containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tap stops to trace a walking route along paths and streets.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val routeControls: @Composable () -> Unit = {
                OutlinedButton(onClick = centerOnMe) { Text("Center on me") }
                OutlinedButton(onClick = { keyDialog = true }) { Text(if (keyReady) "Routing key" else "Set up routing") }
            }
            if (LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { routeControls() }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { routeControls() }
            val mapShape = RoundedCornerShape(20.dp)
            Box(Modifier.fillMaxWidth().weight(1f).clip(mapShape).clipToBounds()
                .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, mapShape)) {
                AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().graphicsLayer { clip = true }, update = { view ->
                    view.overlays.clear()
                    view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                            if (anchors.size < 50) {
                                anchors = anchors + RunRoutePoint((point.latitude * 10_000_000).toInt(),
                                    (point.longitude * 10_000_000).toInt())
                                route = null
                            } else message = "The routing service allows up to 50 stops."
                            return true
                        }
                        override fun longPressHelper(point: GeoPoint): Boolean = false
                    }))
                    route?.let { planned -> view.overlays.add(Polyline().apply {
                        setPoints(planned.points.map { GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0) })
                        outlinePaint.color = android.graphics.Color.rgb(47, 130, 255)
                        outlinePaint.strokeWidth = 8f
                    }) }
                    anchors.forEachIndexed { index, anchor -> view.overlays.add(Marker(view).apply {
                        position = GeoPoint(anchor.latitudeE7 / 10_000_000.0, anchor.longitudeE7 / 10_000_000.0)
                        title = "Stop ${index + 1}"
                    }) }
                    view.invalidate()
                })
            }
            Text("Routing: openrouteservice / HeiGIT · © OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppSurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${anchors.size} stops", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { anchors = anchors.dropLast(1); route = null }, enabled = anchors.isNotEmpty()) { Text("Undo") }
                            OutlinedButton(onClick = { anchors = emptyList(); route = null }, enabled = anchors.isNotEmpty()) { Text("Clear") }
                        }
                    }
                    if (routing) Text("Finding walking path…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!keyReady) Text("Add a routing key to follow walking paths.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    routingMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(name, { name = it.take(200) }, label = { Text("Route name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val clean = name.trim()
                        val planned = route
                        if (clean.isBlank() || planned == null) message = "Name the route and wait for a walking path."
                        else {
                            val route = RunRoute(newId(), 1, clean,
                                planned.points, planned.waypointIndices, emptyList())
                            if (!onSave(route)) message = "Could not save route." else onBack()
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = route != null && !routing && name.isNotBlank()) { Text("Save route") }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
