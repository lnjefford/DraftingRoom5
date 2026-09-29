package dev.draftingroom5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.tileprovider.tilesource.XYTileSource

/** Tap-to-draw geometry stays local; only visible tiles are requested from OSM. */
@Composable
internal fun RunMapEditor(onSave: (RunRoute) -> Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
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
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Draw route", onBack) }, containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pan and zoom, then tap to add route points.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = centerOnMe) { Text("Center on me") }
            AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().weight(1f), update = { view ->
                view.overlays.clear()
                view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                        if (points.size < 10_000) points = points + point else message = "Route point limit reached."
                        return true
                    }
                    override fun longPressHelper(point: GeoPoint): Boolean = false
                }))
                if (points.isNotEmpty()) view.overlays.add(Polyline().apply {
                    setPoints(points)
                    outlinePaint.color = android.graphics.Color.rgb(47, 130, 255)
                    outlinePaint.strokeWidth = 8f
                })
                view.invalidate()
            })
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${points.size} points", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { points = points.dropLast(1) }, enabled = points.isNotEmpty()) { Text("Undo") }
                OutlinedButton(onClick = { points = emptyList() }, enabled = points.isNotEmpty()) { Text("Clear") }
            }
            OutlinedTextField(name, { name = it.take(200) }, label = { Text("Route name") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val clean = name.trim()
                if (clean.isBlank() || points.size < 2) message = "Name the route and add at least two points."
                else {
                    val route = RunRoute(newId(), 1, clean,
                        points.map { RunRoutePoint((it.latitude * 10_000_000).toInt(),
                            (it.longitude * 10_000_000).toInt()) },
                        listOf(0, points.lastIndex), emptyList())
                    if (!onSave(route)) message = "Could not save route." else onBack()
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = points.size >= 2 && name.isNotBlank()) { Text("Save route") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
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
