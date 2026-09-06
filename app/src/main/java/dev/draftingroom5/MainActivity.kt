package dev.draftingroom5

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DraftingRoom5App() }
    }
}

private val Ink = Color(0xFF122032)
private val Blue = Color(0xFF3E7BFA)
private val Mint = Color(0xFF39D6A3)
private val Mist = Color(0xFFF3F6FA)

private data class ScheduledItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val destination: Destination,
)

private enum class Destination { FITBOD, JUSTRUN, CUSTOM }

private data class Exercise(
    val name: String,
    val detail: String,
    val sets: String,
    val target: String,
    val isTimed: Boolean,
)

private val saturdayRoutine = listOf(
    Exercise("Thick-Bar Dead Hangs", "Pull-up bar + thick adapter", "3 sets", "20 sec", true),
    Exercise("Dumbbell Farmer's Walks", "Start 15-20 lb/hand", "3 sets", "30 sec", true),
    Exercise("Great Ape Grips Pro Holds", "Pinch & crush", "4 sets", "20 sec", true),
    Exercise("Seated Dumbbell Wrist Curls", "Palms up, start 5-10 lb", "3 sets", "12-15 reps", false),
    Exercise("Seated Dumbbell Reverse Wrist Curls", "Palms down, start 5-10 lb", "3 sets", "12-15 reps", false),
    Exercise("Finger Extensor Band Extensions", "", "2-3 sets", "15-20 reps", false),
    Exercise("Wrist Rotations", "Pronation / supination", "2 sets", "10-12 / side", false),
)

private fun todaySchedule(today: DayOfWeek = LocalDate.now().dayOfWeek): List<ScheduledItem> = when (today) {
    DayOfWeek.MONDAY -> listOf(
        ScheduledItem("fitbod", "Fitbod workout", "Strength session", Destination.FITBOD),
        ScheduledItem("justrun", "JustRun run", "Running session", Destination.JUSTRUN),
    )
    DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> listOf(
        ScheduledItem("fitbod", "Fitbod workout", "Strength session", Destination.FITBOD),
    )
    DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY -> listOf(
        ScheduledItem("justrun", "JustRun run", "Running session", Destination.JUSTRUN),
    )
    DayOfWeek.SATURDAY -> listOf(
        ScheduledItem("forearm", "Forearm & Grip Conditioning", "Custom workout", Destination.CUSTOM),
    )
    DayOfWeek.SUNDAY -> emptyList()
}

@Composable
private fun DraftingRoom5App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    val completedIds = remember { mutableStateListOf<String>() }
    val todayItems = remember { todaySchedule() }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Blue, secondary = Mint, background = Mist, surface = Color.White,
            onBackground = Ink, onSurface = Ink,
        ),
    ) {
        when (screen) {
            Screen.Dashboard -> Dashboard(
                scheduledItems = todayItems,
                completedIds = completedIds,
                onRefresh = { /* Health Connect refresh is wired during device setup. */ },
                onOpenCustom = { screen = Screen.CustomWorkout },
                onLaunchExternal = { item ->
                    launchWorkoutApp(LocalContextHolder.current, item.destination)
                    if (item.id !in completedIds) completedIds += item.id
                },
            )
            Screen.CustomWorkout -> CustomWorkout(
                onBack = { screen = Screen.Dashboard },
                onComplete = {
                    if ("forearm" !in completedIds) completedIds += "forearm"
                    screen = Screen.Dashboard
                },
            )
        }
    }
}

private sealed interface Screen {
    data object Dashboard : Screen
    data object CustomWorkout : Screen
}

/** Lightweight holder keeps the app-launch helper independent of composable screen details. */
private object LocalContextHolder { lateinit var current: Context }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    scheduledItems: List<ScheduledItem>,
    completedIds: List<String>,
    onRefresh: () -> Unit,
    onOpenCustom: () -> Unit,
    onLaunchExternal: (ScheduledItem) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LocalContextHolder.current = context
    var refreshing by remember { mutableStateOf(false) }
    val healthPermissions = setOf(
        HealthPermission.getReadPermission(androidx.health.connect.client.records.WeightRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.BodyFatRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.DistanceRecord::class),
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ -> onRefresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DraftingRoom5", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mist),
                actions = {
                    IconButton(onClick = {
                        refreshing = true
                        onRefresh()
                    }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh Health Connect data")
                    }
                },
            )
        },
        containerColor = Mist,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Fitness Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}, ${LocalDate.now()}", color = Color(0xFF64748B))
            }
            item { HealthConnectBanner(onConnect = { permissionLauncher.launch(healthPermissions) }) }
            item { BodyMetricRow() }
            item { WeeklyStatRow() }
            item {
                Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (scheduledItems.isEmpty()) {
                item { EmptySchedule() }
            } else {
                items(scheduledItems, key = { it.id }) { item ->
                    ScheduleCard(
                        item = item,
                        completed = item.id in completedIds,
                        onClick = {
                            when (item.destination) {
                                Destination.CUSTOM -> onOpenCustom()
                                else -> onLaunchExternal(item)
                            }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HealthConnectBanner(onConnect: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Connect your health data", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Grant Health Connect access to load your latest body metrics, workout count, and running miles.", color = Color(0xFFCAD5E4))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onConnect) { Text("Connect Health Connect") }
        }
    }
}

@Composable
private fun BodyMetricRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Weight", "--", "lb", Modifier.weight(1f))
        MetricCard("Body fat", "--", "%", Modifier.weight(1f))
        MetricCard("Muscle", "--", "lb", Modifier.weight(1f))
    }
}

@Composable
private fun WeeklyStatRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Workouts", "--", "this week", Modifier.weight(1f))
        MetricCard("Running", "--", "mi this week", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Color(0xFF64748B), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptySchedule() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Check, null, tint = Mint)
            Spacer(Modifier.height(8.dp))
            Text("Nothing Scheduled Today", fontWeight = FontWeight.Bold)
            Text("Recovery is part of the plan.", color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun ScheduleCard(item: ScheduledItem, completed: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (completed) Color(0xFFE8FBF4) else Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(if (completed) Mint else Blue, MaterialTheme.shapes.medium).padding(10.dp)) {
                Icon(if (completed) Icons.Default.Check else Icons.Default.FitnessCenter, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(if (completed) "Completed" else item.subtitle, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            if (completed) Text("Done", color = Color(0xFF12835D), fontWeight = FontWeight.Bold)
            else Button(onClick = onClick) { Text(if (item.destination == Destination.CUSTOM) "Open" else "Start") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomWorkout(onBack: () -> Unit, onComplete: () -> Unit) {
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var graceSeconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, graceSeconds, timerSeconds) {
        if (!isRunning) return@LaunchedEffect
        delay(1000)
        if (graceSeconds > 0) graceSeconds--
        else if (timerSeconds > 0) timerSeconds--
        else isRunning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forearm & Grip", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mist),
            )
        },
        containerColor = Mist,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Saturday custom workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("10-second readiness period before every timer. No rest timer.", color = Color(0xFF64748B))
            }
            if (activeExercise != null) {
                item {
                    TimerPanel(
                        exercise = activeExercise!!,
                        timerSeconds = timerSeconds,
                        graceSeconds = graceSeconds,
                        isRunning = isRunning,
                        onStart = {
                            graceSeconds = 10
                            timerSeconds = timedSeconds(activeExercise!!)
                            isRunning = true
                        },
                    )
                }
            }
            items(saturdayRoutine, key = { it.name }) { exercise ->
                ExerciseCard(exercise, onStartTimer = {
                    activeExercise = exercise
                    graceSeconds = 10
                    timerSeconds = timedSeconds(exercise)
                    isRunning = true
                })
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Complete workout")
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

private fun timedSeconds(exercise: Exercise) = when (exercise.name) {
    "Dumbbell Farmer's Walks" -> 30
    else -> 20
}

@Composable
private fun TimerPanel(exercise: Exercise, timerSeconds: Int, graceSeconds: Int, isRunning: Boolean, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ink)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(exercise.name, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val label = if (graceSeconds > 0) "Get ready: $graceSeconds" else "%02d:%02d".format(timerSeconds / 60, timerSeconds % 60)
            Text(label, color = Mint, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (isRunning) if (graceSeconds > 0) "Timer starts after the countdown" else "Timer running" else "Timer finished", color = Color(0xFFCAD5E4))
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onStart) { Text("Restart", color = Color.White) }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onStartTimer: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.Bold)
                if (exercise.detail.isNotBlank()) Text(exercise.detail, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("${exercise.sets}  •  ${exercise.target}", color = Blue, style = MaterialTheme.typography.labelLarge)
            }
            if (exercise.isTimed) {
                IconButton(onClick = onStartTimer) { Icon(Icons.Default.Timer, "Start timer", tint = Blue) }
            } else {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF94A3B8))
            }
        }
    }
}

private fun launchWorkoutApp(context: Context, destination: Destination) {
    // Package names can be overridden in Settings once verified on the user's installed apps.
    val packageName = when (destination) {
        Destination.FITBOD -> "com.fitbod.fitbod"
        Destination.JUSTRUN -> "com.jupli.run"
        Destination.CUSTOM -> return
    }
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    try {
        if (launchIntent != null) context.startActivity(launchIntent)
        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
    }
}
