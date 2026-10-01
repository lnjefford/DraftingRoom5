package dev.draftingroom5

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint

@Composable
internal fun RunStartScreen(
    routine: Routine,
    routes: List<RunRoute>,
    lastRouteId: String?,
    onStart: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val initialRoute = lastRouteId?.takeIf { id -> routes.any { it.id == id } }
        ?: routine.run?.routeId?.takeIf { id -> routes.any { it.id == id } }
    var selectedId by rememberSaveable(routine.id) { mutableStateOf(initialRoute) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(routine.name, onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                EditorialHeading("READY TO RUN", "Choose your route", "Your last route is selected when available.")
            }
            items(routes.sortedWith(compareByDescending<RunRoute> { it.id == initialRoute }.thenBy { it.name }), key = { it.id }) { route ->
                RunRouteChoice(route, selectedId == route.id, route.id == initialRoute) {
                    selectedId = route.id
                }
            }
            item { RunRouteChoice(null, selectedId == null, false) { selectedId = null } }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("TIMING", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text("${formatRunDuration(checkNotNull(routine.run).totalDurationSeconds)} · ${routine.run.intervalCountLabel()}",
                            style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(routine.run.intervals.joinToString(" · ") { "${it.kind.name.lowercase().replaceFirstChar(Char::uppercase)} ${formatRunClock(it.durationSeconds)}" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(onClick = { if (!onStart(selectedId)) message = "Couldn’t start this run. Check the schedule and try again." },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep)) { Text("Start run") }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun RunRouteChoice(route: RunRoute?, selected: Boolean, lastUsed: Boolean, onSelect: () -> Unit) {
    AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (route != null) RunRouteShape(route, Modifier.size(76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(route?.name ?: "No route", style = if (LocalConfiguration.current.screenWidthDp < 360)
                    MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(route?.distanceLabel() ?: "Track time and distance without navigation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lastUsed) Text("LAST USED", color = AppMint, style = MaterialTheme.typography.labelSmall)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
internal fun RunSessionScreen(
    session: RunSession,
    onPause: () -> Boolean,
    onResume: () -> Boolean,
    onFinish: () -> Boolean,
    onBack: () -> Unit,
    locationEnabled: Boolean = true,
    onRequestLocation: () -> Unit = {},
    music: @Composable () -> Unit = {},
    reviewNow: Long? = null,
    reviewConfirmFinish: Boolean = false,
) {
    var now by remember { mutableLongStateOf(reviewNow ?: System.currentTimeMillis()) }
    var confirmFinish by remember { mutableStateOf(reviewConfirmFinish) }
    var message by remember { mutableStateOf<String?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.id, reviewNow) {
        if (reviewNow == null) while (true) { now = System.currentTimeMillis(); delay(500) }
    }
    BackHandler { if (confirmFinish) confirmFinish = false else onBack() }
    val plan = checkNotNull(session.routine.run)
    val interval = session.intervalAt(now)
    val elapsed = session.elapsedMillis(now)
    val elapsedSeconds = elapsed / 1_000
    val complete = interval == null
    val remaining = interval?.let { (plan.intervals.take(it.first + 1).sumOf(RunInterval::durationSeconds) - elapsedSeconds)
        .coerceAtLeast(0).toInt() } ?: 0
    val next = interval?.let { plan.intervals.getOrNull(it.first + 1) }
    val progress = session.routeProgress()
    val cue = progress?.let { position -> session.route?.turnCues?.firstOrNull {
        it.kind.isTurn() && it.pointIndex >= position.first - 1 } }
    val cueDistance = cue?.let { turn -> session.samples.lastOrNull()?.point?.let { point ->
        session.route?.points?.getOrNull(turn.pointIndex)?.let { geoDistanceMeters(point, it).toInt() }
    } }
    val requestFinish = {
        if (complete) { if (!onFinish()) message = "Couldn’t finish the run." }
        else confirmFinish = true
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            Column {
                SecondaryTopBar(session.routine.name, onBack)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(interval?.first ?: plan.intervals.size - 1) + 1} of ${plan.intervals.size} intervals",
                        color = if (complete) AppMint else AppBlue, style = MaterialTheme.typography.labelMedium)
                    Text(if (complete) "TIMING COMPLETE" else if (session.isRunning) "RUNNING" else "PAUSED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.intervals.forEachIndexed { index, item ->
                        val start = plan.intervals.take(index).sumOf(RunInterval::durationSeconds)
                        val fraction = ((elapsedSeconds - start).toFloat() / item.durationSeconds).coerceIn(0f, 1f)
                        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(4.dp)).background(AppBorder)) {
                            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(
                                if (item.kind == RunIntervalKind.WALK) AppMint else AppBlue))
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppBackgroundDeep)) {
            RunLiveMap(session, Modifier.fillMaxSize(), recenterTick)
            if (cue != null && (session.isRunning || complete)) {
                Column(Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(18.dp))
                    .background(AppBackground.copy(alpha = 0.96f)).border(1.dp, AppBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)) {
                    Text("NEXT TURN", color = AppGold, style = MaterialTheme.typography.labelSmall)
                    Text(cue.instruction, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    cueDistance?.let { Text("${it} m ahead", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else if (!session.isRunning && !complete) {
                Column(Modifier.align(Alignment.TopCenter).padding(14.dp)
                    .clip(RoundedCornerShape(18.dp)).background(AppBackground.copy(alpha = .94f))
                    .border(1.dp, AppBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Run paused", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tracking and timers are stopped", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { recenterTick++ }, modifier = Modifier.size(52.dp)
                        .clip(CircleShape).background(AppBackground.copy(alpha = .95f))
                        .border(1.dp, AppBorder, CircleShape)) {
                        Icon(Icons.Default.MyLocation, "Recenter map", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (!locationEnabled) Text("Location is off · tap to enable GPS tracking",
                    Modifier.fillMaxWidth().clickable(onClick = onRequestLocation).background(AppBackground.copy(alpha = 0.95f))
                        .padding(10.dp), color = MaterialTheme.colorScheme.error)
                Text("© OpenStreetMap contributors", Modifier.padding(start = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(AppSurface).border(1.dp, AppBorder, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                        .clip(RoundedCornerShape(5.dp)).background(AppBorder))
                    Text(if (complete) "TIMING COMPLETE" else if (!session.isRunning) "PAUSED" else interval!!.second.kind.name,
                        color = if (complete || interval?.second?.kind == RunIntervalKind.WALK) AppMint else AppGold,
                        style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (complete) "✓" else if (interval?.second?.kind == RunIntervalKind.WALK) "●" else "➤",
                            color = if (complete || interval?.second?.kind == RunIntervalKind.WALK) AppMint else AppBlue,
                            style = MaterialTheme.typography.displaySmall)
                        Column {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (complete) formatRunClock(plan.totalDurationSeconds) else formatRunClock(remaining),
                                    style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
                                if (!complete) Text("of ${formatRunClock(interval!!.second.durationSeconds)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(if (complete) "All ${plan.intervals.size} intervals complete" else
                                next?.let { "Next: ${it.kind.name.lowercase().replaceFirstChar(Char::uppercase)} ${formatRunClock(it.durationSeconds)}" }
                                    ?: "Final interval", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = AppBorder)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RunMetric("Elapsed", formatRunClock(elapsedSeconds.toInt()), Modifier.weight(1f))
                        RunMetric("Distance", java.lang.String.format(java.util.Locale.US, "%.1f mi", session.distanceMeters / 1609.344),
                            Modifier.weight(1f))
                    }
                    if (session.samples.isEmpty()) Text("Waiting for a GPS fix", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    music()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (complete) {
                            OutlinedButton(onClick = { message = "Tracking continues until you finish and save." },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Keep navigating") }
                            Button(onClick = { if (!onFinish()) message = "Couldn’t finish the run." },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppBlue,
                                    contentColor = AppBackgroundDeep)) { Text("Finish & save") }
                        } else {
                            OutlinedButton(onClick = requestFinish,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("End") }
                            Button(onClick = {
                                if (!(if (session.isRunning) onPause() else onResume())) message = "Couldn’t update the run."
                            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppBlue,
                                    contentColor = AppBackgroundDeep)) {
                                Text(if (session.isRunning) "Pause" else "Resume")
                            }
                        }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmFinish) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .56f))
            .clickable { confirmFinish = false })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(AppSurface).border(1.dp, AppBorder,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                .clip(RoundedCornerShape(5.dp)).background(AppBorder))
            Text("END RUN", color = AppGold, style = MaterialTheme.typography.labelMedium)
            Text("Finish this run?", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge)
            Text("Your route, completed intervals, and timing will be saved.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val completedIntervals = plan.intervals.runningFold(0) { total, item -> total + item.durationSeconds }
                .drop(1).count { elapsedSeconds >= it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RunMetric("Intervals", "$completedIntervals of ${plan.intervals.size}", Modifier.weight(1f))
                RunMetric("Elapsed", formatRunClock(elapsedSeconds.toInt()), Modifier.weight(1f))
                RunMetric("Distance", java.lang.String.format(java.util.Locale.US, "%.1f mi", session.distanceMeters / 1609.344),
                    Modifier.weight(1f))
            }
            Button(onClick = { if (!onFinish()) { message = "Couldn’t finish the run."; confirmFinish = false } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep)) {
                Text("Finish & save")
            }
            OutlinedButton(onClick = { confirmFinish = false },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Keep running") }
        }
    }
    }
}

@Composable
private fun RunMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RunLiveMap(session: RunSession, modifier: Modifier = Modifier, recenterTick: Int = 0) {
    if (LocalInspectionMode.current) {
        if (session.route != null) RunRouteShape(session.route, modifier)
        else Box(modifier.background(AppBackgroundDeep)) {
            Text("Tracking your run", Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    val map = remember(context, session.id) {
        MapView(context).apply {
            configureRunTiles(context, this)
            setMultiTouchControls(true)
            val first = session.samples.lastOrNull()?.point ?: session.route?.points?.firstOrNull()
            controller.setZoom(14.5)
            controller.setCenter(first?.let { GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0) }
                ?: GeoPoint(0.0, 0.0))
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(map, session.samples.size, recenterTick) {
        (session.samples.lastOrNull()?.point ?: session.route?.points?.firstOrNull())?.let {
            map.controller.animateTo(GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0))
        }
    }
    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        view.overlays.clear()
        session.route?.let { route ->
            val points = route.points.map { GeoPoint(it.latitudeE7 / 10_000_000.0, it.longitudeE7 / 10_000_000.0) }
            view.overlays.add(Polyline().apply {
                setPoints(points)
                outlinePaint.color = android.graphics.Color.rgb(96, 163, 255)
                outlinePaint.strokeWidth = 6f * context.resources.displayMetrics.density
            })
        }
        if (session.samples.size >= 2) view.overlays.add(Polyline().apply {
            setPoints(session.samples.map { GeoPoint(it.point.latitudeE7 / 10_000_000.0,
                it.point.longitudeE7 / 10_000_000.0) })
            outlinePaint.color = android.graphics.Color.rgb(79, 224, 176)
            outlinePaint.strokeWidth = 7f * context.resources.displayMetrics.density
        })
        session.samples.lastOrNull()?.point?.let { point -> view.overlays.add(Marker(view).apply {
            position = GeoPoint(point.latitudeE7 / 10_000_000.0, point.longitudeE7 / 10_000_000.0)
            icon = runLocationIcon(context)
            title = "Current position"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }) }
        view.invalidate()
    })
}

private fun runLocationIcon(context: android.content.Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (42 * density).toInt().coerceAtLeast(42)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = android.graphics.Color.argb(70, 93, 159, 255)
    canvas.drawCircle(center, center, size * 0.48f, paint)
    paint.color = android.graphics.Color.rgb(74, 146, 255)
    canvas.drawCircle(center, center, size * 0.30f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    canvas.drawCircle(center, center, size * 0.30f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

@Composable
internal fun RunHistoryScreen(
    routine: Routine,
    history: List<WorkoutHistoryEntry>,
    sessions: List<RunSession>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val byMonth = history.sortedByDescending { it.completedAtMillis }
        .groupBy { it.effectiveDate.withDayOfMonth(1) }
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Run history", onBack) }, containerColor = Color.Transparent) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { EditorialHeading(routine.name, "Previous runs", "Your saved timing, route, and tracking.") }
            if (history.isEmpty()) item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Text("No completed runs yet.", Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            byMonth.forEach { (month, entries) ->
                item(key = "month-$month") {
                    Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
                        color = AppGold, style = MaterialTheme.typography.labelMedium)
                }
                items(entries, key = { it.id }) { entry ->
                val session = sessions.firstOrNull { it.id == entry.id }
                val elapsed = ((session?.elapsedBeforeResumeMillis ?: (entry.completedAtMillis - entry.startedAtMillis)) / 1_000).toInt()
                val complete = elapsed >= (entry.snapshot.run?.totalDurationSeconds ?: Int.MAX_VALUE)
                val narrow = LocalConfiguration.current.screenWidthDp < 360
                AppSurfaceCard(Modifier.fillMaxWidth().clickable { onSelect(entry.id) }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 12.dp)) {
                        session?.route?.let { RunRouteShape(it, Modifier.size(if (narrow) 78.dp else 96.dp)) } ?: Box(
                            Modifier.size(if (narrow) 78.dp else 96.dp).clip(RoundedCornerShape(14.dp)).background(AppBackgroundDeep),
                            contentAlignment = Alignment.Center,
                        ) { Text("RUN", color = AppBlue, style = MaterialTheme.typography.labelLarge) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.effectiveDate.format(java.time.format.DateTimeFormatter.ofPattern(
                                if (narrow) "EEE, MMM d" else "EEEE, MMMM d")),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Text(if (complete) "✓ Timing complete" else "Ended early",
                                color = if (complete) AppMint else AppGold,
                                style = if (narrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
                            Text(session?.route?.name ?: entry.snapshot.name,
                                style = if (narrow)
                                    MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${formatRunClock(elapsed)} · " + java.lang.String.format(java.util.Locale.US,
                                "%.1f mi", (session?.distanceMeters ?: 0) / 1609.344),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!narrow) Text("›", color = AppBlue, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
internal fun RunCompletionScreen(history: WorkoutHistoryEntry, session: RunSession?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var recenterTick by remember { mutableIntStateOf(0) }
    val elapsedSeconds = ((session?.elapsedBeforeResumeMillis ?: (history.completedAtMillis - history.startedAtMillis)) / 1_000).toInt()
    val intervals = history.snapshot.run?.intervals.orEmpty()
    val timingComplete = elapsedSeconds >= intervals.sumOf(RunInterval::durationSeconds)
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Run saved", onBack) }, containerColor = Color.Transparent) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (session != null) item {
                Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(18.dp))) {
                    RunLiveMap(session, Modifier.fillMaxSize(), recenterTick)
                    IconButton(onClick = { recenterTick++ }, modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(12.dp).size(48.dp).clip(CircleShape)
                        .background(AppBackground.copy(alpha = .95f)).border(1.dp, AppBorder, CircleShape)) {
                        Icon(Icons.Default.MyLocation, "Recenter map", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .clip(RoundedCornerShape(12.dp)).background(AppBackground.copy(alpha = 0.92f))
                        .padding(8.dp)) {
                        Text("━  Planned route", color = AppBlue, style = MaterialTheme.typography.labelSmall)
                        Text("━  Actual trace", color = AppMint, style = MaterialTheme.typography.labelSmall)
                        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(history.effectiveDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(),
                        color = AppGold, style = MaterialTheme.typography.labelMedium)
                    Text(history.snapshot.name, style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (timingComplete) "✓ Timing complete" else "Ended early", color = if (timingComplete) AppMint else AppGold,
                        style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RunMetric("Elapsed", formatRunClock(elapsedSeconds), Modifier.weight(1f))
                        RunMetric("Distance", java.lang.String.format(java.util.Locale.US, "%.1f mi", (session?.distanceMeters ?: 0) / 1609.344),
                            Modifier.weight(1f))
                    }
                    Text("INTERVALS", color = AppGold, style = MaterialTheme.typography.labelMedium)
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (intervals.size <= 4) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    intervals.forEach { interval ->
                                        Box(Modifier.weight(interval.durationSeconds.toFloat()).height(6.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(
                                                if (interval.kind == RunIntervalKind.WALK) AppMint else AppBlue))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    intervals.forEach { interval ->
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(interval.kind.name.lowercase().replaceFirstChar(Char::uppercase),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall)
                                            Text(formatRunClock(interval.durationSeconds), color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            } else {
                                var boundary = 0
                                intervals.forEach { interval ->
                                    val completed = elapsedSeconds >= boundary + interval.durationSeconds
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(
                                            if (interval.kind == RunIntervalKind.WALK) AppMint else AppBlue))
                                        Text("${interval.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${formatRunClock(interval.durationSeconds)}",
                                            Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (completed) "Completed" else if (elapsedSeconds > boundary) "Partial" else "Not started",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    boundary += interval.durationSeconds
                                }
                            }
                        }
                    }
                    session?.route?.let { route ->
                        AppSurfaceCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(route.name, style = MaterialTheme.typography.titleLarge)
                                Text("Planned route · actual trace saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AppSurfaceCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("♡", color = AppBlue, style = MaterialTheme.typography.headlineMedium)
                            Column(Modifier.weight(1f)) {
                                Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                                Text("Run saved in DraftingRoom5", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Not synced", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep)) {
                        Text("Done")
                    }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun RunTrackPreview(samples: List<RunLocationSample>) {
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Recorded track", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val latMin = samples.minOf { it.point.latitudeE7 }.toDouble()
                val latMax = samples.maxOf { it.point.latitudeE7 }.toDouble()
                val lonMin = samples.minOf { it.point.longitudeE7 }.toDouble()
                val lonMax = samples.maxOf { it.point.longitudeE7 }.toDouble()
                val latRange = (latMax - latMin).coerceAtLeast(1.0)
                val lonRange = (lonMax - lonMin).coerceAtLeast(1.0)
                val path = Path()
                samples.forEachIndexed { index, sample ->
                    val x = ((sample.point.longitudeE7 - lonMin) / lonRange * (size.width - 20f) + 10f).toFloat()
                    val y = (size.height - ((sample.point.latitudeE7 - latMin) / latRange * (size.height - 20f) + 10f)).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, AppBlue, style = Stroke(width = 4.dp.toPx()))
            }
        }
    }
}
