package dev.draftingroom5

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KClass

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

private enum class HealthConnection { CHECKING, NEEDS_PERMISSION, CONNECTED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

private data class HealthStats(
    val weight: String = "--",
    val bodyFat: String = "--",
    val leanMass: String = "--",
    val workoutsThisWeek: String = "--",
    val milesThisWeek: String = "--",
    val details: List<String> = emptyList(),
)

private data class HealthUiState(
    val connection: HealthConnection = HealthConnection.CHECKING,
    val stats: HealthStats = HealthStats(),
    val message: String? = null,
    val isLoading: Boolean = false,
    val needsAdditionalAccess: Boolean = false,
)

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var healthRefreshJob by remember { mutableStateOf<Job?>(null) }
    var healthUi by remember { mutableStateOf(HealthUiState()) }
    val healthPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )
    }

    val refreshHealth: () -> Unit = refresh@ {
        if (!windowFocused) return@refresh
        healthRefreshJob?.cancel()
        healthRefreshJob = coroutineScope.launch {
            val sdkStatus = try {
                HealthConnectClient.getSdkStatus(context)
            } catch (error: Exception) {
                healthUi = HealthUiState(connection = HealthConnection.ERROR, message = error.message ?: "Could not check Health Connect availability.")
                return@launch
            }
            when (sdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    healthUi = HealthUiState(
                        connection = HealthConnection.UNAVAILABLE,
                        message = "Health Connect is not available on this device.",
                    )
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    healthUi = HealthUiState(
                        connection = HealthConnection.UPDATE_REQUIRED,
                        message = "Health Connect needs to be installed or updated.",
                    )
                }
                else -> {
                    try {
                        val client = HealthConnectClient.getOrCreate(context)
                        val requestedPermissions = healthPermissionsFor(client, healthPermissions)
                        val granted = client.permissionController.getGrantedPermissions()
                        if (granted.intersect(healthPermissions).isEmpty()) {
                            healthUi = HealthUiState(connection = HealthConnection.NEEDS_PERMISSION)
                        } else {
                            healthUi = healthUi.copy(connection = HealthConnection.CONNECTED, isLoading = true, message = null)
                            healthUi = HealthUiState(
                                connection = HealthConnection.CONNECTED,
                                stats = readHealthStats(client, granted, requestedPermissions),
                                message = when {
                                    !granted.containsAll(healthPermissions) -> "Some measurement permissions are off. Available measurements are shown below."
                                    !granted.containsAll(requestedPermissions) -> "Past-data access is off. Allow it to read Withings measurements from before the standard history window."
                                    else -> null
                                },
                                needsAdditionalAccess = !granted.containsAll(requestedPermissions),
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        healthUi = HealthUiState(
                            connection = HealthConnection.ERROR,
                            message = error.message ?: "Could not read Health Connect data.",
                        )
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        healthUi = if (granted.intersect(healthPermissions).isNotEmpty()) {
            HealthUiState(connection = HealthConnection.CONNECTED, isLoading = true)
        } else {
            HealthUiState(
                connection = HealthConnection.NEEDS_PERMISSION,
                message = "Allow the data types you want to display. If Android no longer shows the prompt, use Health Connect permissions & settings.",
            )
        }
        if (granted.intersect(healthPermissions).isNotEmpty()) refreshHealth()
    }

    val connectHealth: () -> Unit = {
        when (healthUi.connection) {
            HealthConnection.UPDATE_REQUIRED -> {
                try {
                    openHealthConnectInstaller(context)
                } catch (error: Exception) {
                    healthUi = HealthUiState(connection = HealthConnection.ERROR, message = error.message ?: "Could not open the Health Connect installer.")
                }
            }
            HealthConnection.UNAVAILABLE -> refreshHealth()
            else -> coroutineScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(context)
                    val requestedPermissions = healthPermissionsFor(client, healthPermissions)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.containsAll(requestedPermissions)) refreshHealth()
                    else permissionLauncher.launch(requestedPermissions)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    healthUi = HealthUiState(connection = HealthConnection.ERROR, message = error.message ?: "Could not open Health Connect permissions.")
                }
            }
        }
    }

    val openHealthSettings: () -> Unit = {
        try {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (error: Exception) {
            healthUi = HealthUiState(connection = HealthConnection.ERROR, message = "Could not open Health Connect settings: ${error.message}")
        }
    }

    // Permission dialogs can resume the Activity before the app has foreground focus.
    // Read once our own window is focused; cancel reads when a dialog/app takes over.
    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            refreshHealth()
        } else {
            healthRefreshJob?.cancel()
            healthUi = healthUi.copy(isLoading = false)
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Blue, secondary = Mint, background = Mist, surface = Color.White,
            onBackground = Ink, onSurface = Ink,
        ),
    ) {
        when (screen) {
            Screen.Dashboard -> Dashboard(
                healthUi = healthUi,
                scheduledItems = todayItems,
                completedIds = completedIds,
                onOpenSettings = { screen = Screen.Settings },
                onOpenCustom = { screen = Screen.CustomWorkout },
                onLaunchExternal = { item ->
                    launchWorkoutApp(context, item.destination)
                    if (item.id !in completedIds) completedIds += item.id
                },
            )
            Screen.Settings -> SettingsScreen(
                healthUi = healthUi,
                onBack = { screen = Screen.Dashboard },
                onConnectHealth = connectHealth,
                onOpenHealthSettings = openHealthSettings,
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
    data object Settings : Screen
    data object CustomWorkout : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    healthUi: HealthUiState,
    scheduledItems: List<ScheduledItem>,
    completedIds: List<String>,
    onOpenSettings: () -> Unit,
    onOpenCustom: () -> Unit,
    onLaunchExternal: (ScheduledItem) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DraftingRoom5", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mist),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            item { BodyMetricRow(healthUi.stats) }
            if (healthUi.stats.details.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Health data details", fontWeight = FontWeight.Bold)
                            healthUi.stats.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("Withings exports weight as Weight, body-fat percentage as Body Fat, and lean body mass as Lean Body Mass. Withings muscle mass is not the same Health Connect record as lean mass.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { WeeklyStatRow(healthUi.stats) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    healthUi: HealthUiState,
    onBack: () -> Unit,
    onConnectHealth: () -> Unit,
    onOpenHealthSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Mist),
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
                Text("Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Manage data access and app maintenance.", color = Color(0xFF64748B))
            }
            item { HealthConnectBanner(healthUi = healthUi, onConnect = onConnectHealth) }
            item {
                OutlinedButton(onClick = onOpenHealthSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Health Connect permissions & settings")
                }
            }
            item {
                Text("App updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item { AppUpdateCard() }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HealthConnectBanner(healthUi: HealthUiState, onConnect: () -> Unit) {
    val (title, detail, button) = when (healthUi.connection) {
        HealthConnection.CONNECTED -> Triple(
            "Health Connect is connected",
            if (healthUi.isLoading) "Loading your latest health data…" else healthUi.message ?: "Access is granted and dashboard measurements can sync.",
            if (healthUi.needsAdditionalAccess) "Review access" else "Refresh data",
        )
        HealthConnection.UPDATE_REQUIRED -> Triple("Update Health Connect", healthUi.message.orEmpty(), "Open Play Store")
        HealthConnection.UNAVAILABLE -> Triple("Health Connect unavailable", healthUi.message.orEmpty(), "Check again")
        HealthConnection.ERROR -> Triple("Couldn't read Health Connect", healthUi.message.orEmpty(), "Try again")
        HealthConnection.CHECKING -> Triple("Checking Health Connect", "Checking whether Health Connect is ready on this phone…", "Check again")
        HealthConnection.NEEDS_PERMISSION -> Triple(
            "Connect your health data",
            healthUi.message ?: "Grant Health Connect access to load your latest body metrics, workout count, and running miles.",
            "Connect Health Connect",
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Color(0xFFCAD5E4))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onConnect, enabled = !healthUi.isLoading) { Text(button) }
        }
    }
}

@Composable
private fun BodyMetricRow(stats: HealthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Weight", stats.weight, "lb", Modifier.weight(1f))
        MetricCard("Body fat", stats.bodyFat, "%", Modifier.weight(1f))
        MetricCard("Lean mass", stats.leanMass, "lb", Modifier.weight(1f))
    }
}

@Composable
private fun WeeklyStatRow(stats: HealthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Workouts", stats.workoutsThisWeek, "this week", Modifier.weight(1f))
        MetricCard("Running", stats.milesThisWeek, "mi this week", Modifier.weight(1f))
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
    BackHandler(onBack = onBack)
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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

private suspend fun readHealthStats(
    client: HealthConnectClient,
    granted: Set<String>,
    requestedPermissions: Set<String>,
): HealthStats {
    val now = Instant.now()
    val startOfWeek = LocalDate.now()
        .with(DayOfWeek.MONDAY)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
    val thisWeek = TimeRangeFilter.between(startOfWeek, now)

    val weight = readHealthValue(HealthPermission.getReadPermission(WeightRecord::class) in granted) {
        readLatestMeasurement(client, WeightRecord::class, now) { it.time }
    }
    val bodyFat = readHealthValue(HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
        readLatestMeasurement(client, BodyFatRecord::class, now) { it.time }
    }
    val leanMass = readHealthValue(HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted) {
        readLatestMeasurement(client, LeanBodyMassRecord::class, now) { it.time }
    }
    val sessions = readHealthValue(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
        client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = thisWeek, pageSize = 100),
        ).records
    }
    val distance = readHealthValue(HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
        client.readRecords(
            ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = thisWeek, pageSize = 1_000),
        ).records.sumOf { it.distance.inMeters }
    }

    return HealthStats(
        weight = weight.value?.weight?.inKilograms?.toPounds().orPlaceholder(),
        bodyFat = bodyFat.value?.percentage?.value.orPlaceholder(),
        leanMass = leanMass.value?.mass?.inKilograms?.toPounds().orPlaceholder(),
        workoutsThisWeek = sessions.value?.size?.toString() ?: "--",
        milesThisWeek = distance.value?.let { (it / 1_609.344).format(1) } ?: "--",
        details = buildList {
            val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
            add("Checked through ${dateFormat.format(now)} · Android ${android.os.Build.VERSION.RELEASE} · App ${BuildConfig.VERSION_NAME}")
            if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in requestedPermissions) {
                add(
                    if (HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted) "Past data access: allowed"
                    else "Past data access: off · only the standard history window is readable",
                )
            } else {
                add("Past data access: unavailable on this Health Connect version")
            }
            add("Exact types queried: WeightRecord · BodyFatRecord · LeanBodyMassRecord")
            add(measurementDetail("Weight", weight) { it.time })
            add(measurementDetail("Body fat", bodyFat) { it.time })
            add(measurementDetail("Lean mass", leanMass) { it.time })
            sessions.issue?.let { add("Workouts: $it") }
            distance.issue?.let { add("Distance: $it") }
        },
    )
}

private fun healthPermissionsFor(client: HealthConnectClient, healthPermissions: Set<String>): Set<String> {
    val historyAvailable = client.features.getFeatureStatus(
        HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY,
    ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    return requestedHealthPermissions(healthPermissions, historyAvailable)
}

private suspend fun <T : Record> readLatestMeasurement(
    client: HealthConnectClient,
    type: KClass<T>,
    now: Instant,
    timestamp: (T) -> Instant,
): T? {
    suspend fun queryRange(range: TimeRangeFilter): T? = latestHealthRecord(timestamp) { token ->
        val response = client.readRecords(
            ReadRecordsRequest(type, timeRangeFilter = range, ascendingOrder = true, pageSize = 1_000, pageToken = token),
        )
        HealthRecordPage(response.records, response.pageToken)
    }
    // Start with a bounded recent query. If empty, retain access to older records
    // that are still within Health Connect's permission-dependent history window.
    return queryRange(TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now))
        ?: queryRange(TimeRangeFilter.before(now))
}

private fun <T : Record> measurementDetail(label: String, result: HealthReadResult<T>, timestamp: (T) -> Instant): String {
    val record = result.value ?: return "$label: ${result.issue}"
    val date = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(timestamp(record))
    val source = record.metadata.dataOrigin.packageName.ifBlank { "Unknown source" }
    return "$label: $date · $source"
}

private fun Double.toPounds() = this * 2.2046226218

private fun Double.format(decimals: Int) = String.format(Locale.US, "%.${decimals}f", this)

private fun Double?.orPlaceholder() = this?.format(1) ?: "--"

private fun openHealthConnectInstaller(context: Context) {
    // This is Health Connect's documented provider package. The client library's internal
    // default-provider constant is intentionally not exposed to app code.
    val provider = "com.google.android.apps.healthdata"
    val marketIntent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$provider&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$provider")))
    }
}
