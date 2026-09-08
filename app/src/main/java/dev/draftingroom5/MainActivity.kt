package dev.draftingroom5

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.Locale
import kotlin.reflect.KClass

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { DraftingRoom5App() }
    }
}

private enum class HealthConnection { CHECKING, NEEDS_PERMISSION, CONNECTED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

private data class HealthStats(
    val weight: String = "--",
    val bodyFat: String = "--",
    val leanMass: String = "--",
    val workoutsThisWeek: String = "--",
    val milesThisWeek: String = "--",
)

private data class HealthUiState(
    val connection: HealthConnection = HealthConnection.CHECKING,
    val stats: HealthStats = HealthStats(),
    val message: String? = null,
    val isLoading: Boolean = false,
    val needsAdditionalAccess: Boolean = false,
)

@Composable
private fun DraftingRoom5App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    val completedIds = remember { mutableStateListOf<String>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val planStore = remember { TrainingPlanStore(context) }
    var trainingPlan by remember { mutableStateOf(planStore.load()) }
    val updateTrainingPlan: (TrainingPlan) -> Unit = { updated ->
        trainingPlan = updated
        planStore.save(updated)
    }
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
                                stats = readHealthStats(client, granted),
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

    DraftingRoom5Theme {
        when (screen) {
            Screen.Dashboard -> Dashboard(
                healthUi = healthUi,
                scheduledItems = trainingPlan.forDay(LocalDate.now().dayOfWeek),
                completedIds = completedIds,
                onOpenSettings = { screen = Screen.Settings },
                onOpenCustom = { item -> item.routineId?.let { screen = Screen.CustomWorkout(it, item.id) } },
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
                onManagePlan = { screen = Screen.PlanManagement },
            )
            Screen.PlanManagement -> PlanManagementScreen(
                plan = trainingPlan,
                onChange = updateTrainingPlan,
                onEditRoutine = { screen = Screen.RoutineEditor(it) },
                onBack = { screen = Screen.Settings },
            )
            is Screen.RoutineEditor -> {
                val routineId = (screen as Screen.RoutineEditor).routineId
                val routine = trainingPlan.routines.firstOrNull { it.id == routineId }
                if (routine == null) screen = Screen.PlanManagement else RoutineEditorScreen(
                    routine = routine,
                    onChange = { updated ->
                        updateTrainingPlan(trainingPlan.copy(routines = trainingPlan.routines.map { if (it.id == updated.id) updated else it }))
                    },
                    onBack = { screen = Screen.PlanManagement },
                )
            }
            is Screen.CustomWorkout -> {
                val workout = screen as Screen.CustomWorkout
                val routine = trainingPlan.routines.firstOrNull { it.id == workout.routineId }
                if (routine == null) screen = Screen.Dashboard else CustomWorkout(
                routine = routine,
                onBack = { screen = Screen.Dashboard },
                onComplete = {
                    if (workout.scheduleId !in completedIds) completedIds += workout.scheduleId
                    screen = Screen.Dashboard
                },
            )
            }
        }
    }
}

private sealed interface Screen {
    data object Dashboard : Screen
    data object Settings : Screen
    data object PlanManagement : Screen
    data class RoutineEditor(val routineId: String) : Screen
    data class CustomWorkout(val routineId: String, val scheduleId: String) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    healthUi: HealthUiState,
    scheduledItems: List<ScheduledItem>,
    completedIds: List<String>,
    onOpenSettings: () -> Unit,
    onOpenCustom: (ScheduledItem) -> Unit,
    onLaunchExternal: (ScheduledItem) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { BrandTitle("DraftingRoom5") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("YOUR TRAINING ROOM", color = AppMint, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                Text("Fitness tracker", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}, ${LocalDate.now()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { BodyMetricRow(healthUi.stats) }
            item { WeeklyStatRow(healthUi.stats) }
            item { SectionHeader("Today", "Your scheduled training, ready when you are.") }
            if (scheduledItems.isEmpty()) {
                item { EmptySchedule() }
            } else {
                items(scheduledItems, key = { it.id }) { item ->
                    ScheduleCard(
                        item = item,
                        completed = item.id in completedIds,
                        onClick = {
                            when (item.destination) {
                                Destination.CUSTOM -> onOpenCustom(item)
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
    onManagePlan: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Training", "Customize your weekly schedule and workout routines.", "Build your plan")
            }
            item {
                OutlinedButton(onClick = onManagePlan, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage schedules & routines")
                }
            }
            item {
                SectionHeader("Connections", "Manage data access and app maintenance.", "Keep data moving")
            }
            item {
                HealthConnectBanner(
                    healthUi = healthUi,
                    onConnect = onConnectHealth,
                    onOpenSettings = onOpenHealthSettings,
                )
            }
            item {
                SectionHeader("App updates", "Stay current with the latest DraftingRoom5 build.")
            }
            item { AppUpdateCard() }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HealthConnectBanner(
    healthUi: HealthUiState,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
    BrandedCard(
        containerColor = Color(0xFF142A45),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 360.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onConnect,
                            enabled = !healthUi.isLoading,
                            modifier = Modifier.weight(1f),
                        ) { Text(button) }
                        HealthSettingsButton(onClick = onOpenSettings, modifier = Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onConnect,
                            enabled = !healthUi.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(button) }
                        HealthSettingsButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Text("Permissions")
    }
}

@Composable
private fun BodyMetricRow(stats: HealthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Weight", stats.weight, "lb", AppBlue, Modifier.weight(1f))
        MetricCard("Body fat", stats.bodyFat, "%", AppMint, Modifier.weight(1f))
        MetricCard("Lean mass", stats.leanMass, "lb", AppGold, Modifier.weight(1f))
    }
}

@Composable
private fun WeeklyStatRow(stats: HealthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Workouts", stats.workoutsThisWeek, "this week", AppMint, Modifier.weight(1f))
        MetricCard("Running", stats.milesThisWeek, "mi this week", AppBlue, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, accent: Color, modifier: Modifier) {
    BrandedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
            Spacer(Modifier.height(10.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptySchedule() {
    BrandedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Check, null, tint = AppMint)
            Spacer(Modifier.height(8.dp))
            Text("Nothing Scheduled Today", fontWeight = FontWeight.Bold)
            Text("Recovery is part of the plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScheduleCard(item: ScheduledItem, completed: Boolean, onClick: () -> Unit) {
    BrandedCard(
        Modifier.fillMaxWidth(),
        containerColor = if (completed) AppCompleted else AppSurfaceRaised,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(if (completed) AppMint else AppBlue, MaterialTheme.shapes.medium).padding(10.dp)) {
                Icon(
                    if (completed) Icons.Default.Check else Icons.Default.FitnessCenter,
                    null,
                    tint = if (completed) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(if (completed) "Completed" else item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (completed) Text("Done", color = AppMint, fontWeight = FontWeight.Bold)
            else Button(onClick = onClick) { Text(if (item.destination == Destination.CUSTOM) "Open" else "Start") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomWorkout(routine: CustomRoutine, onBack: () -> Unit, onComplete: () -> Unit) {
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
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text(routine.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeader("Custom workout", "10-second readiness period before every timer. No rest timer.", "Focus mode")
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
            items(routine.exercises, key = { it.id }) { exercise ->
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

private fun timedSeconds(exercise: Exercise) = exercise.timerSeconds ?: 20

@Composable
private fun TimerPanel(exercise: Exercise, timerSeconds: Int, graceSeconds: Int, isRunning: Boolean, onStart: () -> Unit) {
    BrandedCard(
        Modifier.fillMaxWidth(),
        containerColor = Color(0xFF142F4D),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(exercise.name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val label = if (graceSeconds > 0) "Get ready: $graceSeconds" else "%02d:%02d".format(timerSeconds / 60, timerSeconds % 60)
            Text(label, color = AppMint, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isRunning) if (graceSeconds > 0) "Timer starts after the countdown" else "Timer running" else "Timer finished",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onStart) { Text("Restart") }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onStartTimer: () -> Unit) {
    BrandedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.Bold)
                if (exercise.notes.isNotBlank()) Text(exercise.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text("${exercise.sets}  •  ${exercise.target}", color = AppBlue, style = MaterialTheme.typography.labelLarge)
            }
            if (exercise.timerSeconds != null) {
                IconButton(onClick = onStartTimer) { Icon(Icons.Default.Timer, "Start timer", tint = AppBlue) }
            } else {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.outline)
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
