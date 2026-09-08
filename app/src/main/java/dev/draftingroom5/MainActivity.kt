package dev.draftingroom5

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val weight: HealthMetric = HealthMetric(),
    val bodyFat: HealthMetric = HealthMetric(),
    val leanMass: HealthMetric = HealthMetric(),
    val workouts: HealthMetric = HealthMetric(),
    val distance: HealthMetric = HealthMetric(),
    val weightTrend: List<HealthTrendPoint> = emptyList(),
    val bodyFatTrend: List<HealthTrendPoint> = emptyList(),
    val leanMassTrend: List<HealthTrendPoint> = emptyList(),
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
    var launchBrandAnimationPending by rememberSaveable { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupManager = remember { AutomaticBackupManager(context) }
    remember { backupManager.restoreAfterAndroidTransferIfNeeded() }
    val planStore = remember { TrainingPlanStore(context) }
    val dashboardLayoutStore = remember { DashboardLayoutStore(context) }
    val healthDateRangeStore = remember { HealthDateRangeStore(context) }
    val workoutHistoryStore = remember { WorkoutHistoryStore(context) }
    var trainingPlan by remember { mutableStateOf(planStore.load()) }
    var dashboardLayout by remember { mutableStateOf(dashboardLayoutStore.load()) }
    var healthDateRange by remember { mutableStateOf(healthDateRangeStore.load()) }
    var workoutHistory by remember { mutableStateOf(workoutHistoryStore.load()) }
    var backupStatus by remember { mutableStateOf(backupManager.status()) }
    var backupActionMessage by remember { mutableStateOf<String?>(null) }
    val completedIds = completedScheduleIdsForDate(workoutHistory, LocalDate.now())
    val requestAutomaticBackup: () -> Unit = {
        backupManager.requestBackup()
        backupStatus = backupManager.status()
    }
    val updateTrainingPlan: (TrainingPlan) -> Unit = { updated ->
        trainingPlan = updated
        planStore.save(updated)
        requestAutomaticBackup()
    }
    val recordWorkoutCompletion: (String, String, Destination) -> Unit = { scheduleId, title, destination ->
        if (scheduleId !in completedIds) {
            workoutHistory = workoutHistory + WorkoutHistoryEntry(
                id = newId(),
                scheduleId = scheduleId,
                title = title,
                destination = destination,
                completedAtMillis = System.currentTimeMillis(),
            )
            workoutHistoryStore.save(workoutHistory)
            requestAutomaticBackup()
        }
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

    val refreshHealth: (HealthDateRange) -> Unit = refresh@ { range ->
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
                                stats = readHealthStats(context, client, granted, range),
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
        if (granted.intersect(healthPermissions).isNotEmpty()) refreshHealth(healthDateRange)
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
            HealthConnection.UNAVAILABLE -> refreshHealth(healthDateRange)
            else -> coroutineScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(context)
                    val requestedPermissions = healthPermissionsFor(client, healthPermissions)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.containsAll(requestedPermissions)) refreshHealth(healthDateRange)
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
            backupManager.schedule()
            backupStatus = backupManager.status()
            refreshHealth(healthDateRange)
        } else {
            healthRefreshJob?.cancel()
            healthUi = healthUi.copy(isLoading = false)
        }
    }

    LaunchedEffect(screen, windowFocused) {
        if (screen == Screen.Settings && windowFocused) {
            while (true) {
                backupStatus = backupManager.status()
                delay(1000)
            }
        }
    }

    DraftingRoom5Theme {
        when (screen) {
            Screen.Dashboard -> Dashboard(
                healthUi = healthUi,
                dashboardLayout = dashboardLayout,
                healthDateRange = healthDateRange,
                scheduledItems = trainingPlan.forDay(LocalDate.now().dayOfWeek),
                completedIds = completedIds,
                animateBrandOnEntry = launchBrandAnimationPending,
                onBrandAnimationFinished = { launchBrandAnimationPending = false },
                onOpenSettings = { screen = Screen.Settings },
                onHealthDateRangeChange = { updated ->
                    healthDateRange = updated
                    healthDateRangeStore.save(updated)
                    requestAutomaticBackup()
                    refreshHealth(updated)
                },
                onOpenCustom = { item -> item.routineId?.let { screen = Screen.CustomWorkout(it, item.id) } },
                onLaunchExternal = { item ->
                    launchWorkoutApp(context, item.destination)
                    recordWorkoutCompletion(item.id, item.title, item.destination)
                },
            )
            Screen.Settings -> SettingsScreen(
                healthUi = healthUi,
                onBack = { screen = Screen.Dashboard },
                onConnectHealth = connectHealth,
                onOpenHealthSettings = openHealthSettings,
                onManagePlan = { screen = Screen.PlanManagement },
                onCustomizeDashboard = { screen = Screen.DashboardCustomization },
                backupStatus = backupStatus,
                backupActionMessage = backupActionMessage,
                onAutomaticBackupChange = { enabled ->
                    backupManager.setEnabled(enabled)
                    backupStatus = backupManager.status()
                    backupActionMessage = if (enabled) "Automatic backups enabled." else "Automatic backups disabled."
                },
                onBackUpNow = {
                    val result = backupManager.createBackup()
                    if (result.isFailure) backupManager.requestBackup()
                    backupStatus = backupManager.status()
                    backupActionMessage = if (result.isSuccess) "Recovery snapshot saved." else "Backup failed and will retry automatically."
                },
                onRestoreLatest = {
                    backupManager.restoreLatest().onSuccess { restored ->
                        trainingPlan = restored.plan
                        dashboardLayout = restored.dashboardLayout
                        healthDateRange = restored.healthDateRange
                        workoutHistory = restored.workoutHistory
                        backupActionMessage = "Restored the latest recovery snapshot."
                    }.onFailure { error ->
                        backupActionMessage = error.message ?: "Could not restore the latest snapshot."
                    }
                    backupStatus = backupManager.status()
                },
                onOpenBackupSettings = {
                    runCatching { openAndroidBackupSettings(context) }
                },
            )
            Screen.PlanManagement -> PlanManagementScreen(
                plan = trainingPlan,
                onChange = updateTrainingPlan,
                onEditRoutine = { screen = Screen.RoutineEditor(it) },
                onBack = { screen = Screen.Settings },
            )
            Screen.DashboardCustomization -> DashboardCustomizationScreen(
                layout = dashboardLayout,
                onChange = { updated ->
                    dashboardLayout = updated
                    dashboardLayoutStore.save(updated)
                    requestAutomaticBackup()
                },
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
                    recordWorkoutCompletion(workout.scheduleId, routine.name, Destination.CUSTOM)
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
    data object DashboardCustomization : Screen
    data class RoutineEditor(val routineId: String) : Screen
    data class CustomWorkout(val routineId: String, val scheduleId: String) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    healthUi: HealthUiState,
    dashboardLayout: DashboardLayout,
    healthDateRange: HealthDateRange,
    scheduledItems: List<ScheduledItem>,
    completedIds: List<String>,
    animateBrandOnEntry: Boolean,
    onBrandAnimationFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    onHealthDateRangeChange: (HealthDateRange) -> Unit,
    onOpenCustom: (ScheduledItem) -> Unit,
    onLaunchExternal: (ScheduledItem) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = {
                    BrandTitle(
                        title = "DraftingRoom5",
                        animateOnEntry = animateBrandOnEntry,
                        onAnimationFinished = onBrandAnimationFinished,
                    )
                },
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
            item { HealthDateRangeSelector(healthDateRange, onHealthDateRangeChange) }
            dashboardLayout.visibleCards.forEach { card ->
                when (card) {
                    DashboardCard.WEIGHT -> item { MetricCard("Weight", healthUi.stats.weight, "lb", AppBlue, Modifier.fillMaxWidth(), healthUi.stats.weightTrend, healthDateRange) }
                    DashboardCard.BODY_FAT -> item { MetricCard("Body fat", healthUi.stats.bodyFat, "%", AppMint, Modifier.fillMaxWidth(), healthUi.stats.bodyFatTrend, healthDateRange) }
                    DashboardCard.LEAN_MASS -> item { MetricCard("Lean mass", healthUi.stats.leanMass, "lb", AppGold, Modifier.fillMaxWidth(), healthUi.stats.leanMassTrend, healthDateRange) }
                    DashboardCard.WORKOUTS -> item { MetricCard("Workouts", healthUi.stats.workouts, "last ${healthDateRange.displayLabel}", AppMint, Modifier.fillMaxWidth()) }
                    DashboardCard.DISTANCE -> item { MetricCard("Running", healthUi.stats.distance, "mi · ${healthDateRange.displayLabel}", AppBlue, Modifier.fillMaxWidth()) }
                    DashboardCard.TODAY -> {
                        item { SectionHeader("Today", "Your scheduled training, ready when you are.") }
                        if (scheduledItems.isEmpty()) {
                            item { EmptySchedule() }
                        } else {
                            items(scheduledItems, key = { "schedule-${it.id}" }) { item ->
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
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun HealthDateRangeSelector(
    selected: HealthDateRange,
    onSelected: (HealthDateRange) -> Unit,
) {
    BrandedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Health range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthDateRange.entries.forEach { range ->
                    if (range == selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(range.buttonLabel) }
                    } else {
                        OutlinedButton(onClick = { onSelected(range) }, modifier = Modifier.weight(1f)) { Text(range.buttonLabel) }
                    }
                }
            }
            Text(
                "Charts, workouts, and distance · last ${selected.displayLabel}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onCustomizeDashboard: () -> Unit,
    backupStatus: AutomaticBackupStatus,
    backupActionMessage: String?,
    onAutomaticBackupChange: (Boolean) -> Unit,
    onBackUpNow: () -> Unit,
    onRestoreLatest: () -> Unit,
    onOpenBackupSettings: () -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCustomizeDashboard, modifier = Modifier.fillMaxWidth()) {
                        Text("Customize dashboard")
                    }
                    OutlinedButton(onClick = onManagePlan, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage schedules & routines")
                    }
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
                SectionHeader("Automatic backups", "Recover your settings, training plan, and workout history.")
            }
            item {
                AutomaticBackupCard(
                    status = backupStatus,
                    actionMessage = backupActionMessage,
                    onEnabledChange = onAutomaticBackupChange,
                    onBackUpNow = onBackUpNow,
                    onRestoreLatest = onRestoreLatest,
                    onOpenBackupSettings = onOpenBackupSettings,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardCustomizationScreen(
    layout: DashboardLayout,
    onChange: (DashboardLayout) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = {
            TopAppBar(
                title = { Text("Customize dashboard", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Dashboard cards", "Choose what appears and arrange cards in the order you want.", "Make it yours")
            }
            items(layout.cards, key = { it.card.name }) { preference ->
                val index = layout.cards.indexOf(preference)
                BrandedCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preference.card.title, fontWeight = FontWeight.Bold)
                            Text(
                                preference.card.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = { onChange(layout.moveCard(index, -1)) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${preference.card.title} up")
                        }
                        IconButton(
                            onClick = { onChange(layout.moveCard(index, 1)) },
                            enabled = index < layout.cards.lastIndex,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${preference.card.title} down")
                        }
                        Switch(
                            checked = preference.visible,
                            onCheckedChange = { onChange(layout.setVisible(preference.card, it)) },
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { onChange(DashboardLayout()) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset dashboard")
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AutomaticBackupCard(
    status: AutomaticBackupStatus,
    actionMessage: String?,
    onEnabledChange: (Boolean) -> Unit,
    onBackUpNow: () -> Unit,
    onRestoreLatest: () -> Unit,
    onOpenBackupSettings: () -> Unit,
) {
    BrandedCard(
        containerColor = Color(0xFF142A45),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Offline recovery snapshots", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val needsAttention = backupIsStale(status, System.currentTimeMillis())
                    Text(
                        when {
                            !status.enabled -> "Automatic backup is off"
                            needsAttention -> "Backup needs attention"
                            else -> "Automatic backup is on"
                        },
                        color = if (status.enabled && !needsAttention) AppMint else AppGold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Switch(checked = status.enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(4.dp))
            Text(backupStatusDetail(status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            actionMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = AppMint, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Includes app settings, schedules, custom routines, and workout history. Keeps the latest two snapshots for offline recovery. Health Connect measurements and permissions are never copied.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBackUpNow, enabled = status.enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Back up now")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestoreLatest,
                enabled = status.hasRecoverySnapshot,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore latest snapshot")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBackupSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Google backup settings")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Android can encrypt and copy these snapshots to your selected Google backup account for device setup or reinstall recovery.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
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
        MetricCard("Workouts", stats.workouts, "selected range", AppMint, Modifier.weight(1f))
        MetricCard("Running", stats.distance, "mi selected range", AppBlue, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    label: String,
    metric: HealthMetric,
    unit: String,
    accent: Color,
    modifier: Modifier,
    trend: List<HealthTrendPoint> = emptyList(),
    dateRange: HealthDateRange? = null,
) {
    BrandedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
            Spacer(Modifier.height(10.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                metric.detail(),
                color = if (metric.state == HealthMetricState.STALE) AppGold else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            if (trend.isNotEmpty() && dateRange != null) HealthTrendChart(label, unit, trend, accent, dateRange)
        }
    }
}

@Composable
private fun HealthTrendChart(
    label: String,
    unit: String,
    trend: List<HealthTrendPoint>,
    accent: Color,
    dateRange: HealthDateRange,
) {
    val values = trend.mapNotNull { it.value }
    Spacer(Modifier.height(10.dp))
    Text("Last ${dateRange.displayLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (values.isEmpty()) {
        Text(
            "No trend data in the last ${dateRange.displayLabel}.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    val minimum = values.min()
    val maximum = values.max()
    val spread = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(top = 8.dp)
            .semantics {
                contentDescription = "$label trend, ${values.size} recorded days, from ${minimum.format(1)} to ${maximum.format(1)} $unit"
            },
    ) {
        val denominator = (trend.size - 1).coerceAtLeast(1).toFloat()
        fun x(index: Int) = size.width * index / denominator
        fun y(value: Double) = size.height - ((value - minimum) / spread).toFloat() * size.height

        for (index in 0 until trend.lastIndex) {
            val start = trend[index].value
            val end = trend[index + 1].value
            if (start != null && end != null) {
                drawLine(accent, start = androidx.compose.ui.geometry.Offset(x(index), y(start)), end = androidx.compose.ui.geometry.Offset(x(index + 1), y(end)), strokeWidth = 4f)
            }
        }
        trend.forEachIndexed { index, point ->
            point.value?.let { value -> drawCircle(accent, radius = 5f, center = androidx.compose.ui.geometry.Offset(x(index), y(value))) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(trend.first().date.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${minimum.format(1)}–${maximum.format(1)} $unit", style = MaterialTheme.typography.labelSmall, color = accent)
        Text(trend.last().date.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    context: Context,
    client: HealthConnectClient,
    granted: Set<String>,
    dateRange: HealthDateRange,
): HealthStats {
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()
    val trendEndDate = LocalDate.now(zoneId)
    val trendStartDate = dateRange.startDate(trendEndDate)
    val trendRange = TimeRangeFilter.between(trendStartDate.atStartOfDay(zoneId).toInstant(), now)

    val weight = readHealthValue(HealthPermission.getReadPermission(WeightRecord::class) in granted) {
        readLatestMeasurement(client, WeightRecord::class, now) { it.time }
    }
    val bodyFat = readHealthValue(HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
        readLatestMeasurement(client, BodyFatRecord::class, now) { it.time }
    }
    val leanMass = readHealthValue(HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted) {
        readLatestMeasurement(client, LeanBodyMassRecord::class, now) { it.time }
    }
    val weightHistory = readHealthValue(HealthPermission.getReadPermission(WeightRecord::class) in granted) {
        readMeasurementHistory(client, WeightRecord::class, trendRange).takeIf { it.isNotEmpty() }
    }
    val bodyFatHistory = readHealthValue(HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
        readMeasurementHistory(client, BodyFatRecord::class, trendRange).takeIf { it.isNotEmpty() }
    }
    val leanMassHistory = readHealthValue(HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted) {
        readMeasurementHistory(client, LeanBodyMassRecord::class, trendRange).takeIf { it.isNotEmpty() }
    }
    val sessions = readHealthValue(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
        readMeasurementHistory(client, ExerciseSessionRecord::class, trendRange).takeIf { it.isNotEmpty() }
    }
    val distance = readHealthValue(HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
        readMeasurementHistory(client, DistanceRecord::class, trendRange).takeIf { it.isNotEmpty() }
    }

    fun source(packageName: String?) = resolveHealthSource(context, packageName)
    val latestSession = sessions.value?.maxByOrNull { it.endTime }
    val latestDistance = distance.value?.maxByOrNull { it.endTime }

    return HealthStats(
        weight = healthMetric(
            value = weight.value?.weight?.inKilograms?.toPounds()?.format(1),
            outcome = weight.outcome,
            syncedAt = now,
            recordedAt = weight.value?.time,
            source = source(weight.value?.metadata?.dataOrigin?.packageName),
        ),
        bodyFat = healthMetric(
            value = bodyFat.value?.percentage?.value?.format(1),
            outcome = bodyFat.outcome,
            syncedAt = now,
            recordedAt = bodyFat.value?.time,
            source = source(bodyFat.value?.metadata?.dataOrigin?.packageName),
        ),
        leanMass = healthMetric(
            value = leanMass.value?.mass?.inKilograms?.toPounds()?.format(1),
            outcome = leanMass.outcome,
            syncedAt = now,
            recordedAt = leanMass.value?.time,
            source = source(leanMass.value?.metadata?.dataOrigin?.packageName),
        ),
        workouts = healthMetric(
            value = sessions.value?.size?.toString(),
            outcome = sessions.outcome,
            syncedAt = now,
            recordedAt = latestSession?.endTime,
            source = source(latestSession?.metadata?.dataOrigin?.packageName),
        ),
        distance = healthMetric(
            value = distance.value?.sumOf { it.distance.inMeters }?.let { (it / 1_609.344).format(1) },
            outcome = distance.outcome,
            syncedAt = now,
            recordedAt = latestDistance?.endTime,
            source = source(latestDistance?.metadata?.dataOrigin?.packageName),
        ),
        weightTrend = dailyHealthTrend(
            weightHistory.value.orEmpty().map { TimedHealthValue(it.time, it.weight.inKilograms.toPounds()) },
            trendStartDate,
            trendEndDate,
            zoneId,
        ),
        bodyFatTrend = dailyHealthTrend(
            bodyFatHistory.value.orEmpty().map { TimedHealthValue(it.time, it.percentage.value) },
            trendStartDate,
            trendEndDate,
            zoneId,
        ),
        leanMassTrend = dailyHealthTrend(
            leanMassHistory.value.orEmpty().map { TimedHealthValue(it.time, it.mass.inKilograms.toPounds()) },
            trendStartDate,
            trendEndDate,
            zoneId,
        ),
    )
}

private suspend fun <T : Record> readMeasurementHistory(
    client: HealthConnectClient,
    type: KClass<T>,
    range: TimeRangeFilter,
): List<T> {
    val records = mutableListOf<T>()
    val visitedTokens = mutableSetOf<String>()
    var token: String? = null
    do {
        val response = client.readRecords(
            ReadRecordsRequest(type, timeRangeFilter = range, ascendingOrder = true, pageSize = 1_000, pageToken = token),
        )
        records += response.records
        token = response.pageToken?.takeIf { it.isNotEmpty() }
        check(token == null || visitedTokens.add(token)) { "Health Connect repeated a page token." }
    } while (token != null)
    return records
}

private fun resolveHealthSource(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    val packageManager = context.packageManager
    return runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(
                packageName,
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    }.getOrNull() ?: packageName
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
