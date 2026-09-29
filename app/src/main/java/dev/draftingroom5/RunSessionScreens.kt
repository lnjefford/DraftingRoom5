package dev.draftingroom5

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
        topBar = { SecondaryTopBar("Start run", onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(routine.name, style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${checkNotNull(routine.run).intervalCountLabel()} · ${formatRunDuration(routine.run.totalDurationSeconds)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Text("ROUTE", color = AppGold, style = MaterialTheme.typography.labelMedium) }
            item { RunRouteChoice("No route", "Track time and distance without a planned route.", selectedId == null) { selectedId = null } }
            items(routes.sortedBy { it.name }, key = { it.id }) { route ->
                RunRouteChoice(route.name, "${route.points.size} points · ${route.turnCues.size} turn cues", selectedId == route.id) {
                    selectedId = route.id
                }
            }
            item {
                Button(onClick = { if (!onStart(selectedId)) message = "Couldn’t start this run. Check the schedule and try again." },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Start run") }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun RunRouteChoice(name: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
    var now by remember { mutableLongStateOf(reviewNow ?: System.currentTimeMillis()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.id, reviewNow) {
        if (reviewNow == null) while (true) { now = System.currentTimeMillis(); delay(500) }
    }
    BackHandler(onBack = onBack)
    val interval = session.intervalAt(now)
    val elapsed = session.elapsedMillis(now)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Run in progress", onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(session.routine.name, style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (session.isRunning) "RUNNING" else "PAUSED", color = AppGold,
                    style = MaterialTheme.typography.labelMedium)
            }
            if (!locationEnabled) item {
                AppSurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onRequestLocation)) {
                    Text("Location is off. Tap to enable GPS tracking.", Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ELAPSED", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(formatRunDuration((elapsed / 1_000).toInt()), style = MaterialTheme.typography.displayMedium)
                        Text("${session.distanceMeters} m recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (session.samples.isEmpty()) Text("Waiting for a GPS fix", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("INTERVAL", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(interval?.let { "${it.first + 1}. ${it.second.kind.name.lowercase().replaceFirstChar(Char::uppercase)}" }
                            ?: "Plan complete", style = MaterialTheme.typography.headlineMedium)
                        Text(interval?.let { formatRunDuration(it.second.durationSeconds) } ?: "You can finish when ready.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                val progress = session.routeProgress()
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ROUTE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                        Text(session.route?.name ?: "No route selected", fontWeight = FontWeight.Bold)
                        if (progress != null) Text("Nearest track point ${progress.first} of ${progress.second}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        progress?.let { position -> session.route?.turnCues?.firstOrNull { it.pointIndex >= position.first - 1 } }
                            ?.let { Text(it.instruction, color = AppMint) }
                    }
                }
            }
            item { music() }
            item {
                OutlinedButton(onClick = { if (!(if (session.isRunning) onPause() else onResume())) message = "Couldn’t update the run." },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (session.isRunning) "Pause run" else "Resume run")
                }
            }
            item {
                Button(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Finish run")
                }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
    if (confirmFinish) AppConfirmationDialog(
        title = "Finish this run?",
        message = "This saves your elapsed time and recorded route to run history.",
        confirmLabel = "Finish run",
        onConfirm = { if (!onFinish()) { message = "Couldn’t finish the run."; confirmFinish = false } },
        onDismiss = { confirmFinish = false },
    )
}

@Composable
internal fun RunCompletionScreen(history: WorkoutHistoryEntry, session: RunSession?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Run complete", onBack) }, containerColor = Color.Transparent) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("RUN COMPLETE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                Text(history.snapshot.name, style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            item {
                AppSurfaceCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Elapsed ${formatRunDuration(((session?.elapsedBeforeResumeMillis ?: (history.completedAtMillis - history.startedAtMillis)) / 1_000).toInt())}")
                        Text("Distance ${session?.distanceMeters ?: 0} m")
                        Text(session?.route?.name?.let { "Route: $it" } ?: "No planned route")
                    }
                }
            }
            if (session != null && session.samples.size >= 2) item { RunTrackPreview(session.samples) }
            item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Return to dashboard")
            } }
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
