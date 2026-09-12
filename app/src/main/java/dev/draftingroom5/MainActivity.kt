package dev.draftingroom5

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
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
import java.time.format.DateTimeFormatter
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

internal enum class HealthConnection { CHECKING, NEEDS_PERMISSION, CONNECTED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

private val LocalReviewTime = staticCompositionLocalOf<Instant?> { null }

private data class HealthStats(
    val weight: HealthMetric = HealthMetric(),
    val bodyFat: HealthMetric = HealthMetric(),
    val leanMass: HealthMetric = HealthMetric(),
    val workouts: HealthMetric = HealthMetric(),
    val distance: HealthMetric = HealthMetric(),
    val weightTrend: List<HealthTrendPoint> = emptyList(),
    val bodyFatTrend: List<HealthTrendPoint> = emptyList(),
    val leanMassTrend: List<HealthTrendPoint> = emptyList(),
    val workoutTrend: List<HealthTrendPoint> = emptyList(),
    val distanceTrend: List<HealthTrendPoint> = emptyList(),
    val historyIssues: Map<DashboardCard, String> = emptyMap(),
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
    val navigation = rememberAppNavigationState()
    val screen = navigation.current
    val screenState = rememberSaveableStateHolder()
    var savedRouteKeys by rememberSaveable { mutableStateOf(navigation.backStack.map(::encodeAppRoute)) }
    LaunchedEffect(navigation.backStack) {
        val activeKeys = navigation.backStack.map(::encodeAppRoute)
        (savedRouteKeys - activeKeys.toSet()).forEach(screenState::removeState)
        savedRouteKeys = activeKeys
    }
    var launchBrandAnimationPending by rememberSaveable { mutableStateOf(true) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupManager = remember { AutomaticBackupManager(context) }
    remember { backupManager.restoreAfterAndroidTransferIfNeeded() }
    val appRepository = remember { AppRepository.get(context) }
    val workoutHaptics = remember { WorkoutHaptics(context) }
    var voiceAvailability by remember { mutableStateOf(VoiceAvailability.INITIALIZING) }
    var pendingCompletionCue by remember { mutableStateOf<String?>(null) }
    val workoutVoice = remember { WorkoutVoiceAnnouncements(context) { voiceAvailability = it } }
    var appDocument by remember { mutableStateOf(appRepository.currentOrDefaults()) }
    var documentError by remember { mutableStateOf<String?>(
        if (appRepository.state.value is LoadState.Ready) null else "App data could not be loaded. Changes cannot be saved. Restore a recovery snapshot from Settings or retry loading.") }
    var confirmDataReset by remember { mutableStateOf(false) }
    var unavailableRoutine by remember { mutableStateOf<Routine?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var appPickerRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var appPickerPurpose by rememberSaveable { mutableStateOf<String?>(null) }
    var editorReplacementPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var editorReplacementLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var trainingPlan by remember { mutableStateOf(appDocument.plan) }
    var dashboardLayout by remember { mutableStateOf(appDocument.preferences.dashboardLayout) }
    var healthDateRange by remember { mutableStateOf(appDocument.preferences.healthDateRange) }
    var workoutHistory by remember { mutableStateOf(appDocument.history) }
    var hapticsEnabled by remember { mutableStateOf(appDocument.preferences.hapticsEnabled) }
    var voiceSettings by remember { mutableStateOf(appDocument.preferences.voice) }
    var backupStatus by remember { mutableStateOf(backupManager.status()) }
    var backupActionMessage by remember { mutableStateOf<String?>(null) }
    val updateManager = remember { AppUpdateManager(context) }
    var updateStatus by remember { mutableStateOf(updateManager.status()) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateActionMessage by remember { mutableStateOf<String?>(null) }
    var pendingUpdateInstall by rememberSaveable { mutableStateOf(false) }
    var routineDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var routineDraftExecution by rememberSaveable { mutableStateOf<String?>(null) }
    var routineDraftPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var routineDraftAppLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val routineDraft = routineDraftId?.let { id ->
        val execution = routineDraftExecution?.let { runCatching { RoutineExecution.valueOf(it) }.getOrNull() }
        execution?.let { RoutineDraft(id, it, routineDraftPackage, routineDraftAppLabel) }
    }
    val clearRoutineDraft: () -> Unit = {
        routineDraftId = null
        routineDraftExecution = null
        routineDraftPackage = null
        routineDraftAppLabel = null
    }
    val clearAppPicker: () -> Unit = {
        appPickerRoutineId = null
        appPickerPurpose = null
    }
    DisposableEffect(workoutVoice) {
        onDispose { workoutVoice.shutdown() }
    }
    val requestAutomaticBackup: () -> Unit = {
        backupManager.requestBackup()
        backupStatus = backupManager.status()
    }
    val acceptDocumentResult: (RepositoryResult<AppDocument>) -> Boolean = { result ->
        when (result) {
            is RepositoryResult.Success -> appDocument = result.value
            else -> {
                (appRepository.state.value as? LoadState.Ready)?.value?.let { appDocument = it }
                documentError = when (result) {
                    is RepositoryResult.Invalid -> result.error.message
                    is RepositoryResult.Failed -> "Could not save app data. Please retry."
                    is RepositoryResult.Conflict -> "App data changed. Review the current values and retry."
                    else -> null
                }
            }
        }
        trainingPlan = appDocument.plan
        dashboardLayout = appDocument.preferences.dashboardLayout
        healthDateRange = appDocument.preferences.healthDateRange
        workoutHistory = appDocument.history
        hapticsEnabled = appDocument.preferences.hapticsEnabled
        voiceSettings = appDocument.preferences.voice
        result is RepositoryResult.Success
    }
    val persistDocument: ((AppDocument) -> AppDocument) -> Boolean = { transform ->
        acceptDocumentResult(appRepository.update(appDocument.generation, transform))
    }
    val updateTrainingPlan: (TrainingPlan) -> Boolean = { updated ->
        acceptDocumentResult(appRepository.replacePlan(appDocument.generation, updated)).also { if (it) requestAutomaticBackup() }
    }
    val coroutineScope = rememberCoroutineScope()
    val installDownloadedUpdate: () -> Unit = {
        coroutineScope.launch {
        runCatching { openDownloadedUpdateInstaller(context) }
            .onSuccess {
                updateActionMessage = "Confirm the update in Android's installer. If you cancel, tap the update indicator to try again."
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                updateActionMessage = "Could not open the installer: ${error.message ?: "Download the update again."}"
            }
        }
    }
    val updateInstallPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingUpdateInstall && context.packageManager.canRequestPackageInstalls()) installDownloadedUpdate()
        else updateActionMessage = "Installation permission was not granted. Tap the update indicator to try again."
        pendingUpdateInstall = false
    }
    val checkAndInstallUpdate: () -> Unit = {
        if (!updateBusy) coroutineScope.launch {
            updateBusy = true
            updateActionMessage = "Checking for updates…"
            try {
                val available = downloadUpdate(context) { message -> updateActionMessage = message }
                if (!available) {
                    updateManager.clearAvailable()
                    updateStatus = updateManager.status()
                    updateActionMessage = "You're running the latest version."
                } else if (context.packageManager.canRequestPackageInstalls()) {
                    installDownloadedUpdate()
                } else {
                    updateActionMessage = "Allow DraftingRoom5 to install updates, then return to the app."
                    pendingUpdateInstall = true
                    updateInstallPermission.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateActionMessage = "Update failed: ${error.message ?: "Check your connection and try again."}"
            } finally {
                updateBusy = false
            }
        }
    }
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
                                stats = readHealthStats(context, client, granted, range, healthUi.stats),
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
            updateManager.schedule()
            updateManager.requestCheckIfStale()
            refreshHealth(healthDateRange)
            while (true) {
                updateStatus = updateManager.status()
                delay(2000)
            }
        } else {
            workoutVoice.stop()
            healthRefreshJob?.cancel()
            healthUi = healthUi.copy(isLoading = false)
        }
    }

    LaunchedEffect(screen, windowFocused) {
        if (screen == AppRoute.Settings && windowFocused) {
            while (true) {
                backupStatus = backupManager.status()
                delay(1000)
            }
        }
    }

    DraftingRoom5Theme {
        unavailableRoutine?.let { routine ->
            AlertDialog(
                onDismissRequest = { unavailableRoutine = null },
                title = { Text("App unavailable") },
                text = { Text(launchError ?: "${routine.name} could not open its linked app.") },
                confirmButton = { TextButton(onClick = {
                    unavailableRoutine = null
                    appPickerRoutineId = routine.id
                    appPickerPurpose = "RECOVERY"
                    navigation.navigate(AppRoute.InstalledAppPicker(routine.id))
                }) { Text("Change app") } },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { unavailableRoutine = null }) { Text("Cancel") }
                        TextButton(onClick = {
                    val packageName = routine.appLink?.packageName.orEmpty()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${Uri.encode(packageName)}"))) }
                        .onSuccess { unavailableRoutine = null }
                        .onFailure { launchError = "No app can open the store. Install the linked app, then try again." }
                        }) { Text("Open store") }
                    }
                },
            )
        }
        documentError?.let { message ->
            AlertDialog(
                onDismissRequest = { documentError = null },
                title = { Text("App data needs attention") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { documentError = null }) { Text("OK") } },
                dismissButton = {
                    if (appRepository.state.value is LoadState.Corrupt) {
                        TextButton(onClick = { documentError = null; confirmDataReset = true }) { Text("Reset app data") }
                    } else if (appRepository.state.value !is LoadState.Ready) {
                        TextButton(onClick = {
                            (appRepository.load() as? LoadState.Ready)?.value?.let {
                                acceptDocumentResult(RepositoryResult.Success(it))
                                documentError = null
                            }
                        }) { Text("Retry") }
                    }
                },
            )
        }
        if (confirmDataReset) {
            AlertDialog(
                onDismissRequest = { confirmDataReset = false },
                title = { Text("Reset app data?") },
                text = { Text("This deletes your routines, schedule, saved sessions, workout history, and app settings, and restores defaults. Health Connect data is unaffected.") },
                confirmButton = { TextButton(onClick = {
                    confirmDataReset = false
                    if (acceptDocumentResult(appRepository.resetToDefaults())) navigation.dashboard()
                }) { Text("Reset") } },
                dismissButton = { TextButton(onClick = { confirmDataReset = false }) { Text("Cancel") } },
            )
        }
        screenState.SaveableStateProvider(encodeAppRoute(screen)) {
        when (screen) {
            AppRoute.Dashboard -> Dashboard(
                healthUi = healthUi,
                dashboardLayout = dashboardLayout,
                healthDateRange = healthDateRange,
                trainingPlan = trainingPlan,
                partialSessions = appDocument.partialSessions,
                workoutHistory = workoutHistory,
                animateBrandOnEntry = launchBrandAnimationPending,
                onBrandAnimationFinished = { launchBrandAnimationPending = false },
                onOpenSettings = { navigation.navigate(AppRoute.Settings) },
                updateAvailableVersion = updateStatus.availableVersion,
                onInstallUpdate = checkAndInstallUpdate,
                onOpenMetric = { card -> navigation.navigate(AppRoute.MetricDetail(card)) },
                onOpenCustom = { item, date -> navigation.navigate(AppRoute.GuidedSession(item.routineId, item.id, date)) },
                onLaunchExternal = { item ->
                    val routine = trainingPlan.routineFor(item)
                    val result = launchLinkedApp(context, routine)
                    if (result is LinkedAppLaunchResult.Failed) {
                        launchError = linkedAppRecoveryMessage(routine, result)
                        unavailableRoutine = routine
                    }
                },
            )
            is AppRoute.MetricDetail -> {
                val card = screen.card
                MetricDetailScreen(
                    card = card,
                    healthUi = healthUi,
                    dateRange = healthDateRange,
                    onDateRangeChange = { updated ->
                        if (persistDocument { it.copy(preferences = it.preferences.copy(healthDateRange = updated)) }) {
                            requestAutomaticBackup()
                            refreshHealth(updated)
                        }
                    },
                    onConnectHealth = connectHealth,
                    onRetry = { refreshHealth(healthDateRange) },
                    onBack = { navigation.back() },
                )
            }
            AppRoute.Settings -> SettingsScreen(
                healthUi = healthUi,
                visibleDashboardCount = dashboardLayout.visibleCards.size,
                enabledSessionCount = trainingPlan.schedule.size,
                onBack = { navigation.back() },
                onConnectHealth = connectHealth,
                onOpenHealthSettings = openHealthSettings,
                onManagePlan = { navigation.navigate(AppRoute.PlanManagement) },
                onCustomizeDashboard = { navigation.navigate(AppRoute.DashboardCustomization) },
                hapticsEnabled = hapticsEnabled,
                hapticPresentation = workoutHaptics.settingsPresentation(),
                onHapticsEnabledChange = { enabled ->
                    hapticsEnabled = enabled
                    persistDocument { it.copy(preferences = it.preferences.copy(hapticsEnabled = enabled)) }
                    requestAutomaticBackup()
                },
                voiceSettings = voiceSettings,
                voiceAvailability = voiceAvailability,
                onVoiceSettingsChange = { updated ->
                    voiceSettings = updated
                    persistDocument { it.copy(preferences = it.preferences.copy(voice = updated)) }
                    requestAutomaticBackup()
                },
                backupStatus = backupStatus,
                backupActionMessage = backupActionMessage,
                onAutomaticBackupChange = { enabled ->
                    runCatching { backupManager.setEnabled(enabled) }.onSuccess {
                        (appRepository.state.value as? LoadState.Ready)?.value?.let { acceptDocumentResult(RepositoryResult.Success(it)) }
                        backupStatus = backupManager.status()
                        backupActionMessage = if (enabled) "Automatic backups enabled." else "Automatic backups disabled."
                    }.onFailure { backupActionMessage = it.message ?: "Could not save backup settings." }
                },
                onBackUpNow = {
                    val result = backupManager.createBackup()
                    if (result.isFailure) backupManager.requestBackup()
                    backupStatus = backupManager.status()
                    backupActionMessage = if (result.isSuccess) "Recovery snapshot saved." else backupFailureMessage(backupStatus.enabled)
                },
                onRestoreLatest = {
                    backupManager.restoreLatest().onSuccess {
                        (appRepository.state.value as? LoadState.Ready)?.value?.let { acceptDocumentResult(RepositoryResult.Success(it)) }
                        backupActionMessage = "Restored the latest recovery snapshot."
                    }.onFailure { error ->
                        backupActionMessage = error.message ?: "Could not restore the latest snapshot."
                    }
                    backupStatus = backupManager.status()
                },
                onOpenBackupSettings = {
                    runCatching { openAndroidBackupSettings(context) }
                        .onFailure { backupActionMessage = "Could not open Android backup settings: ${it.message ?: "Try again."}" }
                },
                updateStatus = updateStatus,
                updateBusy = updateBusy,
                updateActionMessage = updateActionMessage,
                onCheckAndInstallUpdate = checkAndInstallUpdate,
            )
            AppRoute.PlanManagement -> PlanManagementScreen(
                plan = trainingPlan,
                hapticsEnabled = hapticsEnabled,
                partialSessionCounts = appDocument.partialSessions.groupingBy { it.routineId }.eachCount(),
                onChange = updateTrainingPlan,
                onReset = {
                    if (acceptDocumentResult(appRepository.resetPlan(appDocument.generation))) requestAutomaticBackup()
                },
                onEditRoutine = { navigation.navigate(AppRoute.RoutineEditor(it)) },
                onAddGuidedRoutine = {
                    val draftId = newId()
                    routineDraftId = draftId
                    routineDraftExecution = RoutineExecution.GUIDED.name
                    routineDraftPackage = null
                    routineDraftAppLabel = null
                    navigation.navigate(AppRoute.RoutineEditor(draftId))
                },
                onAddLinkedRoutine = {
                    val draftId = newId()
                    routineDraftId = draftId
                    routineDraftExecution = RoutineExecution.LINKED_APP.name
                    routineDraftPackage = null
                    routineDraftAppLabel = null
                    navigation.navigate(AppRoute.InstalledAppPicker(draftId))
                },
                onRenameRoutine = { routineId, name ->
                    val renamed = trainingPlan.copy(routines = trainingPlan.routines.map { routine ->
                        if (routine.id == routineId) routine.copy(revision = routine.revision + 1, name = name) else routine
                    })
                    if (acceptDocumentResult(appRepository.replacePlan(appDocument.generation, renamed, acknowledgeSavedSessions = true))) {
                        requestAutomaticBackup()
                    }
                },
                onDeleteRoutine = { routineId ->
                    if (acceptDocumentResult(appRepository.deleteRoutine(appDocument.generation, routineId))) requestAutomaticBackup()
                },
                onDeleteSchedule = { scheduleEntryId ->
                    if (acceptDocumentResult(appRepository.deleteScheduleEntry(appDocument.generation, scheduleEntryId))) requestAutomaticBackup()
                },
                onAddSchedule = { day -> navigation.navigate(AppRoute.ScheduleEditor(null, newId(), day)) },
                onEditSchedule = { entryId ->
                    val anchor = trainingPlan.schedule.firstOrNull { it.id == entryId }
                        ?.days?.minByOrNull(java.time.DayOfWeek::getValue) ?: java.time.DayOfWeek.MONDAY
                    navigation.navigate(AppRoute.ScheduleEditor(entryId, newId(), anchor))
                },
                onBack = { navigation.back() },
            )
            AppRoute.DashboardCustomization -> DashboardCustomizationScreen(
                layout = dashboardLayout,
                hapticsEnabled = hapticsEnabled,
                onChange = { updated ->
                    persistDocument { it.copy(preferences = it.preferences.copy(dashboardLayout = updated)) }
                        .also { saved -> if (saved) requestAutomaticBackup() }
                },
                onBack = { navigation.back() },
            )
            is AppRoute.RoutineEditor -> {
                val routineId = screen.routineId
                val routine = trainingPlan.routines.firstOrNull { it.id == routineId }
                val draft = routineDraft?.takeIf { it.id == routineId }
                var acknowledgedSessions by rememberSaveable(routineId) { mutableStateOf(false) }
                if (routine != null && !acknowledgedSessions && appDocument.partialSessions.any { it.routineId == routineId }) {
                    AppConfirmationDialog(
                        title = "Edit ${routine.name}?",
                        message = "Saved incomplete sessions keep their original name, artwork, exercises and progress. Your edits apply to future sessions. Deleting this routine also deletes its incomplete sessions.",
                        confirmLabel = "Continue editing",
                        onConfirm = { acknowledgedSessions = true },
                        onDismiss = { navigation.back() },
                    )
                } else if (routine?.execution == RoutineExecution.LINKED_APP) LinkedAppRoutineEditorScreen(
                    routine = routine,
                    replacement = editorReplacementPackage?.let { packageName ->
                        InstalledAppOption(packageName, editorReplacementLabel ?: linkedAppDisplayName(packageName), "App")
                    },
                    scheduleSummary = trainingPlan.routineScheduleSummary(routine.id),
                    onChooseApp = {
                        editorReplacementPackage = null
                        editorReplacementLabel = null
                        appPickerRoutineId = routine.id
                        appPickerPurpose = "EDITOR"
                        navigation.navigate(AppRoute.InstalledAppPicker(routine.id))
                    },
                    onSave = { updated ->
                        val saved = acceptDocumentResult(appRepository.replacePlan(
                            appDocument.generation,
                            trainingPlan.copy(routines = trainingPlan.routines.map { if (it.id == updated.id) updated else it }),
                        ))
                        if (saved) {
                            requestAutomaticBackup()
                            editorReplacementPackage = null
                            editorReplacementLabel = null
                            clearAppPicker()
                            navigation.back()
                        }
                        saved
                    },
                    onDelete = {
                        if (acceptDocumentResult(appRepository.deleteRoutine(appDocument.generation, routine.id))) {
                            requestAutomaticBackup()
                            editorReplacementPackage = null
                            editorReplacementLabel = null
                            clearAppPicker()
                            navigation.back()
                        }
                    },
                    onTestLink = { launchLinkedApp(context, it) },
                    onBack = {
                        editorReplacementPackage = null
                        editorReplacementLabel = null
                        clearAppPicker()
                        navigation.back()
                    },
                ) else if (routine != null) GuidedRoutineEditorScreen(
                    routine = routine,
                    scheduleSummary = trainingPlan.routineScheduleSummary(routine.id),
                    isNew = false,
                    hapticsEnabled = hapticsEnabled,
                    onPersist = { updated ->
                        val saved = acceptDocumentResult(appRepository.replacePlan(
                            appDocument.generation,
                            trainingPlan.copy(routines = trainingPlan.routines.map { if (it.id == updated.id) updated else it }),
                            acknowledgeSavedSessions = acknowledgedSessions,
                        ))
                        if (saved) requestAutomaticBackup()
                        saved
                    },
                    onDelete = {
                        if (acceptDocumentResult(appRepository.deleteRoutine(appDocument.generation, routine.id))) {
                            requestAutomaticBackup()
                            navigation.back()
                        }
                    },
                    onBack = { navigation.back() },
                ) else if (draft?.execution == RoutineExecution.GUIDED) GuidedRoutineEditorScreen(
                    routine = Routine(
                        id = draft.id,
                        revision = 1,
                        name = "",
                        artworkId = RoutineArtworkCatalog.FALLBACK_ID,
                        execution = RoutineExecution.GUIDED,
                        exercises = emptyList(),
                        appLink = null,
                    ),
                    scheduleSummary = "Not scheduled",
                    isNew = true,
                    hapticsEnabled = hapticsEnabled,
                    onPersist = { created ->
                        val saved = created.isSaveableGuidedRoutine() && acceptDocumentResult(appRepository.replacePlan(
                            appDocument.generation,
                            trainingPlan.copy(routines = trainingPlan.routines + created),
                        ))
                        if (saved) {
                            requestAutomaticBackup()
                            clearRoutineDraft()
                        }
                        saved
                    },
                    onDelete = {
                        clearRoutineDraft()
                        navigation.back()
                    },
                    onBack = {
                        clearRoutineDraft()
                        navigation.back()
                    },
                ) else if (draft != null) NewRoutineDraftScreen(
                    draft = draft,
                    onChooseApp = {
                        appPickerRoutineId = draft.id
                        appPickerPurpose = "EDITOR"
                        navigation.navigate(AppRoute.InstalledAppPicker(draft.id))
                    },
                    onTestLink = { launchLinkedApp(context, it) },
                    onSaveLinked = { name, artworkId ->
                        val savedRoutine = draft.savedLinkedRoutine(name, artworkId) ?: return@NewRoutineDraftScreen false
                        val saved = acceptDocumentResult(appRepository.replacePlan(
                            appDocument.generation,
                            trainingPlan.copy(routines = trainingPlan.routines + savedRoutine),
                        ))
                        if (saved) {
                            requestAutomaticBackup()
                            clearRoutineDraft()
                            navigation.back()
                        }
                        saved
                    },
                    onDiscard = {
                        clearRoutineDraft()
                        navigation.back()
                    },
                ) else navigation.back()
            }
            is AppRoute.InstalledAppPicker -> {
                val draft = routineDraft?.takeIf {
                    it.id == screen.ownerDraftId && it.execution == RoutineExecution.LINKED_APP
                }
                val existingRoutine = trainingPlan.routines.firstOrNull {
                    it.id == screen.ownerDraftId && it.id == appPickerRoutineId && it.execution == RoutineExecution.LINKED_APP
                }
                if (draft == null && existingRoutine == null) navigation.back() else InstalledAppPickerScreen(
                    onSelect = { app ->
                        if (draft != null) {
                            val selected = draft.withSelectedApp(app)
                            routineDraftPackage = selected.packageName
                            routineDraftAppLabel = selected.appLabel
                            if (appPickerPurpose == "EDITOR") {
                                clearAppPicker()
                                navigation.back()
                            } else {
                                navigation.back()
                                navigation.navigate(AppRoute.RoutineEditor(selected.id))
                            }
                        } else if (existingRoutine != null && appPickerPurpose == "EDITOR") {
                            editorReplacementPackage = app.packageName
                            editorReplacementLabel = app.label
                            clearAppPicker()
                            navigation.back()
                        } else if (existingRoutine != null) {
                            editorReplacementPackage = app.packageName
                            editorReplacementLabel = app.label
                            clearAppPicker()
                            navigation.back()
                            navigation.navigate(AppRoute.RoutineEditor(existingRoutine.id))
                        }
                    },
                    onBack = {
                        if (draft != null && appPickerPurpose != "EDITOR") clearRoutineDraft()
                        clearAppPicker()
                        navigation.back()
                    },
                )
            }
            is AppRoute.GuidedSession -> {
                val workout = screen
                val routine = trainingPlan.routines.firstOrNull { it.id == workout.routineId }
                if (routine == null) navigation.dashboard() else GuidedSessionDestination(
                    repository = appRepository,
                    routine = routine,
                    scheduleEntryId = workout.scheduleEntryId,
                    scheduledDate = workout.scheduledDate,
                    isForeground = windowFocused,
                    onDocumentChanged = { updated ->
                        appDocument = updated
                        trainingPlan = updated.plan
                        dashboardLayout = updated.preferences.dashboardLayout
                        healthDateRange = updated.preferences.healthDateRange
                        workoutHistory = updated.history
                        hapticsEnabled = updated.preferences.hapticsEnabled
                        voiceSettings = updated.preferences.voice
                    },
                    onHapticCue = { workoutHaptics.perform(it, hapticsEnabled) },
                    onVoiceCue = { workoutVoice.announce(it, voiceSettings) },
                    onStopVoice = workoutVoice::stop,
                    onBackupRequested = requestAutomaticBackup,
                    onExit = { navigation.back() },
                    onCompleted = { historyId, newlyCompleted ->
                        pendingCompletionCue = historyId.takeIf { newlyCompleted }
                        navigation.navigate(AppRoute.Completion(historyId))
                    },
                )
            }
            is AppRoute.ScheduleEditor -> {
                val original = screen.entryId?.let { id -> trainingPlan.schedule.firstOrNull { it.id == id } }
                if (screen.entryId != null && original == null) navigation.back() else ScheduleEditorScreen(
                    plan = trainingPlan,
                    original = original,
                    draftId = screen.draftId,
                    requestedAnchor = screen.anchor,
                    hasSavedSessions = appDocument.partialSessions.any { it.occurrence.scheduleEntryId == screen.entryId },
                    onSave = { updated ->
                        val saved = acceptDocumentResult(appRepository.replacePlan(appDocument.generation, updated))
                        if (saved) requestAutomaticBackup()
                        saved
                    },
                    onBack = { navigation.back() },
                )
            }
            is AppRoute.Completion -> {
                val history = workoutHistory.firstOrNull { it.id == screen.historyId }
                LaunchedEffect(screen.historyId) {
                    val emit = pendingCompletionCue == history?.id && pendingCompletionCue != null
                    pendingCompletionCue = null
                    if (emit && windowFocused) {
                        workoutHaptics.perform(HapticCue.WORKOUT_COMPLETE, hapticsEnabled)
                        workoutVoice.announce(VoiceCue.WorkoutCompleted, voiceSettings)
                    }
                }
                if (history == null) navigation.dashboard() else SessionCompletionScreen(
                    history = history,
                    onReturnToDashboard = navigation::dashboard,
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(
    healthUi: HealthUiState,
    dashboardLayout: DashboardLayout,
    healthDateRange: HealthDateRange,
    trainingPlan: TrainingPlan,
    partialSessions: List<GuidedSession>,
    workoutHistory: List<WorkoutHistoryEntry>,
    animateBrandOnEntry: Boolean,
    onBrandAnimationFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    updateAvailableVersion: String?,
    onInstallUpdate: () -> Unit,
    onOpenMetric: (DashboardCard) -> Unit,
    onOpenCustom: (ScheduleEntry, LocalDate) -> Unit,
    onLaunchExternal: (ScheduleEntry) -> Unit,
) {
    val reviewTime = LocalReviewTime.current
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var today by remember { mutableStateOf((reviewTime ?: Instant.now()).atZone(ZoneId.systemDefault()).toLocalDate()) }
    LaunchedEffect(reviewTime, focused) {
        if (reviewTime != null) {
            today = reviewTime.atZone(ZoneId.systemDefault()).toLocalDate()
        } else if (focused) {
            while (true) {
                today = LocalDate.now(ZoneId.systemDefault())
                delay(30_000)
            }
        }
    }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var previousTodayEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selectedDate = resolveDashboardDate(LocalDate.ofEpochDay(selectedEpochDay), LocalDate.ofEpochDay(previousTodayEpochDay), today)
    LaunchedEffect(today) {
        selectedEpochDay = selectedDate.toEpochDay()
        previousTodayEpochDay = today.toEpochDay()
    }
    val week = dashboardWeek(today)
    val sessions = dashboardSessions(trainingPlan, partialSessions, workoutHistory, selectedDate)
    val savedSessions = savedDashboardSessions(trainingPlan, partialSessions, selectedDate)
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
                    if (updateAvailableVersion != null) {
                        IconButton(onClick = onInstallUpdate) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = "Update $updateAvailableVersion available. Tap to install.",
                                tint = AppGold,
                            )
                        }
                    }
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
            dashboardLayout.dashboardSections().forEachIndexed { sectionIndex, section ->
                when (section) {
                    DashboardSection.Training -> {
                        item(key = "training-hero") { TrainingHero(selectedDate, sessions) }
                        item(key = "training-heading-$sectionIndex") { EditorialSectionLabel("TODAY'S SESSION") }
                        if (sessions.isEmpty()) {
                            item(key = "training-empty-$sectionIndex") { EmptySchedule(selectedDate == today) }
                        } else {
                            items(sessions, key = { "schedule-$sectionIndex-${selectedDate}-${it.scheduleEntry.id}" }) { session ->
                                SessionCard(
                                    session = session,
                                    onClick = {
                                        if (session.action != SessionAction.DONE) {
                                            if (session.routine.execution == RoutineExecution.GUIDED) onOpenCustom(session.scheduleEntry, selectedDate)
                                            else onLaunchExternal(session.scheduleEntry)
                                        }
                                    },
                                )
                            }
                        }
                        item(key = "training-week-$sectionIndex") {
                            WeekSelector(
                                dates = week,
                                selectedDate = selectedDate,
                                today = today,
                                onSelect = { selectedEpochDay = it.toEpochDay() },
                            )
                        }
                        if (savedSessions.isNotEmpty()) {
                            item(key = "saved-heading-$sectionIndex") { EditorialSectionLabel("SAVED SESSIONS") }
                            items(savedSessions, key = { "saved-$sectionIndex-${it.scheduleEntry.id}-${it.savedOriginDate}" }) { session ->
                                SessionCard(session = session) {
                                    onOpenCustom(session.scheduleEntry, checkNotNull(session.savedOriginDate))
                                }
                            }
                        }
                    }
                    is DashboardSection.Metrics -> item(key = "metrics-$sectionIndex-${section.cards.joinToString { it.name }}") {
                        StatsWorkspaceHeader(section.cards, healthUi.stats, healthDateRange, onOpenMetric)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun TrainingHero(date: LocalDate, sessions: List<DashboardSession>, showArtwork: Boolean = true) {
    val today = (LocalReviewTime.current ?: Instant.now()).atZone(ZoneId.systemDefault()).toLocalDate()
    val completedCount = sessions.count { it.action == SessionAction.DONE }
    val dayWord = if (date == today) "today" else date.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase)
    val status = when {
        sessions.isEmpty() -> "Nothing scheduled $dayWord · Recovery day"
        completedCount > 0 -> "$completedCount of ${sessions.size} complete $dayWord"
        sessions.size == 1 -> "1 session scheduled $dayWord"
        else -> "${sessions.size} sessions scheduled $dayWord"
    }
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 280.dp)) {
        if (showArtwork) {
            Image(
                painter = painterResource(R.drawable.dashboard_athlete_hero),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
            )
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(AppBackground, AppBackground.copy(alpha = .9f), Color.Transparent))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, AppBackground))))
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.8f)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).uppercase(), color = Color(0xFFF4F0E7), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.6.sp)
            Spacer(Modifier.height(22.dp))
            Text("Today’s training", style = MaterialTheme.typography.displayMedium, color = Color(0xFFF4F0E7))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(58.dp).height(3.dp).background(AppGold))
            Spacer(Modifier.height(16.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EditorialSectionLabel(label: String, trailing: String? = null, onTrailingClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
            Text(label, modifier = Modifier.weight(1f), color = Color(0xFFF4F0E7), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.4.sp)
            if (trailing != null) Text(
                trailing,
                modifier = if (onTrailingClick == null) Modifier else Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTrailingClick)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                color = AppBlue,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(46.dp).height(2.dp).background(AppGold))
    }
}

@Composable
private fun StatsWorkspaceHeader(
    cards: List<DashboardCard>,
    stats: HealthStats,
    selectedRange: HealthDateRange,
    onOpenMetric: (DashboardCard) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditorialSectionLabel("HEALTH SNAPSHOT", "VIEW TRENDS  →") { onOpenMetric(cards.first()) }
        BoxWithConstraints {
        val columns = (maxWidth.value / (104f * LocalDensity.current.fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(columns).forEach { rowCards ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCards.forEach { card ->
                    CompactMetricCard(card, stats, selectedRange, Modifier.weight(1f)) { onOpenMetric(card) }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun HealthDateRangeSelector(
    selected: HealthDateRange,
    onSelected: (HealthDateRange) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Time range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().background(AppSurfaceRaised, RoundedCornerShape(18.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HealthDateRange.entries.forEach { range ->
                val isSelected = range == selected
                Button(
                    onClick = { if (!isSelected) onSelected(range) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        this.selected = isSelected
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) AppBlue else Color.Transparent,
                        contentColor = if (isSelected) AppBackgroundDeep else MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                ) { Text(range.buttonLabel) }
            }
        }
    }
}

private fun DashboardCard.metric(stats: HealthStats): HealthMetric = when (this) {
    DashboardCard.WEIGHT -> stats.weight
    DashboardCard.BODY_FAT -> stats.bodyFat
    DashboardCard.LEAN_MASS -> stats.leanMass
    DashboardCard.WORKOUTS -> stats.workouts
    DashboardCard.DISTANCE -> stats.distance
    DashboardCard.TODAY -> HealthMetric()
}

private fun DashboardCard.trend(stats: HealthStats): List<HealthTrendPoint> = when (this) {
    DashboardCard.WEIGHT -> stats.weightTrend
    DashboardCard.BODY_FAT -> stats.bodyFatTrend
    DashboardCard.LEAN_MASS -> stats.leanMassTrend
    DashboardCard.WORKOUTS -> stats.workoutTrend
    DashboardCard.DISTANCE -> stats.distanceTrend
    DashboardCard.TODAY -> emptyList()
}

private val DashboardCard.unit: String
    get() = metricUnit

private val DashboardCard.accent: Color
    get() = when (this) {
        DashboardCard.WEIGHT, DashboardCard.DISTANCE -> AppBlue
        DashboardCard.BODY_FAT, DashboardCard.WORKOUTS -> AppMint
        DashboardCard.LEAN_MASS -> AppGold
        DashboardCard.TODAY -> AppBlue
    }

private fun List<HealthTrendPoint>.forRange(range: HealthDateRange): List<HealthTrendPoint> =
    visibleMetricTrend(this, range)

@Composable
private fun CompactMetricCard(
    card: DashboardCard,
    stats: HealthStats,
    selectedRange: HealthDateRange,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val metric = card.metric(stats)
    val trend = card.trend(stats).forRange(selectedRange)
    val direction = healthTrendDirection(trend)
    val recordedValues = trend.mapNotNull { it.value }
    val delta = if (recordedValues.size >= 2) recordedValues.last() - recordedValues.first() else null
    val trendIcon = when (direction) {
        HealthTrendDirection.UP -> Icons.Default.KeyboardArrowUp
        HealthTrendDirection.DOWN -> Icons.Default.KeyboardArrowDown
        HealthTrendDirection.NEUTRAL -> Icons.Default.Remove
    }
    val trendText = when {
        delta == null -> "No comparison available"
        direction == HealthTrendDirection.UP -> "Up ${formatMetricNumber(card, delta)} ${card.unit} over ${selectedRange.displayLabel}"
        direction == HealthTrendDirection.DOWN -> "Down ${formatMetricNumber(card, kotlin.math.abs(delta))} ${card.unit} over ${selectedRange.displayLabel}"
        else -> "Steady over ${selectedRange.displayLabel}"
    }
    BrandedCard(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "View ${card.title} details", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${card.title}, ${metric.value} ${card.unit}, $trendText"
            },
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(card.title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Icon(trendIcon, contentDescription = null, tint = card.accent)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(metric.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (card == DashboardCard.WORKOUTS || card == DashboardCard.DISTANCE) "${card.unit} · ${selectedRange.buttonLabel}" else card.unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            CompactTrendLine(trend, card.accent)
            Text(trendText, color = card.accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CompactTrendLine(trend: List<HealthTrendPoint>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(24.dp).padding(vertical = 4.dp)) {
        val values = trend.mapNotNull { it.value }
        if (values.size < 2) {
            if (values.size == 1) drawCircle(accent, radius = 3.dp.toPx(), center = center)
            return@Canvas
        }
        val min = values.min()
        val spread = (values.max() - min).takeIf { it > 0.0001 } ?: 1.0
        val fractions = metricChartFractions(trend)
        fun point(index: Int, value: Double) = androidx.compose.ui.geometry.Offset(
            x = size.width * fractions[index],
            y = size.height - ((value - min) / spread).toFloat() * size.height,
        )
        trend.mapIndexedNotNull { index, item -> item.value?.let { index to it } }
            .zipWithNext()
            .filter { (first, second) -> second.first == first.first + 1 }
            .forEach { (first, second) -> drawLine(accent, point(first.first, first.second), point(second.first, second.second), strokeWidth = 3f) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MetricDetailScreen(
    card: DashboardCard,
    healthUi: HealthUiState,
    dateRange: HealthDateRange,
    onDateRangeChange: (HealthDateRange) -> Unit,
    onConnectHealth: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val metric = card.metric(healthUi.stats)
    val trend = card.trend(healthUi.stats).forRange(dateRange)
    val summary = healthTrendSummary(trend)
    val deltaDescription = if (trend.size < 2) "No comparison available" else metricDeltaDescription(card, summary, dateRange)
    val readingDescription = "Current ${card.title.lowercase()}, ${metric.value} ${card.unit}, $deltaDescription"
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar(card.title, onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) { contentDescription = readingDescription }) {
                    Text("CURRENT ${card.title.uppercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.4.sp)
                    FlowRow(verticalArrangement = Arrangement.Center) {
                        Text(metric.value, style = MaterialTheme.typography.displayLarge, color = Color(0xFFF4F0E7))
                        Spacer(Modifier.width(8.dp))
                        Text(card.unit, modifier = Modifier.padding(bottom = 12.dp), color = Color(0xFFF4F0E7), style = MaterialTheme.typography.headlineMedium)
                        if (healthUi.isLoading) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(22.dp).padding(bottom = 4.dp), strokeWidth = 2.dp, color = card.accent)
                        }
                    }
                    Text(deltaDescription, color = card.accent, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(metric.detail(), color = if (metric.state == HealthMetricState.STALE) AppGold else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HealthDateRangeSelector(dateRange, onDateRangeChange) }
            metricDetailIssue(healthUi, metric)?.let { issue ->
                item { MetricDetailIssue(issue, onConnectHealth, onRetry) }
            }
            healthUi.stats.historyIssues[card]?.let { issue ->
                item { MetricDetailIssue(MetricIssue(issue, MetricIssueAction.RETRY), onConnectHealth, onRetry) }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    val span = metricDateSpan(trend)
                    EditorialSectionLabel(
                        "${dateRange.displayLabel.uppercase()} TREND",
                        span?.let { "${it.first.monthValue}/${it.first.dayOfMonth} — ${it.last.monthValue}/${it.last.dayOfMonth}" },
                    )
                    HealthTrendChart(card.title, card.unit, trend, card.accent, dateRange)
                    if (summary != null) MetricRangeSummary(card, summary)
                }
            }
            if (metric.state == HealthMetricState.CURRENT || metric.state == HealthMetricState.STALE) {
                item { MetricSourceFooter(metric) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetricRangeSummary(card: DashboardCard, summary: HealthTrendSummary) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    FlowRow(Modifier.fillMaxWidth().padding(top = 22.dp), maxItemsInEachRow = if (stacked) 1 else 3, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("HIGH" to summary.high, "AVERAGE" to summary.average, "LOW" to summary.low).forEachIndexed { index, item ->
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(item.first, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp)
                Text(formatMetricNumber(card, item.second), color = Color(0xFFF4F0E7), style = MaterialTheme.typography.headlineSmall, fontFamily = EditorialSerif)
                Text(card.unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class MetricIssue(val message: String, val action: MetricIssueAction)
private enum class MetricIssueAction { CONNECT, RETRY }

private fun metricDetailIssue(healthUi: HealthUiState, metric: HealthMetric): MetricIssue? = when (healthUi.connection) {
    HealthConnection.CHECKING -> MetricIssue("Checking Health Connect availability.", MetricIssueAction.RETRY)
    HealthConnection.NEEDS_PERMISSION -> MetricIssue("Permission is required to read this metric from Health Connect.", MetricIssueAction.CONNECT)
    HealthConnection.UPDATE_REQUIRED -> MetricIssue("Health Connect must be installed or updated before this metric can sync.", MetricIssueAction.CONNECT)
    HealthConnection.UNAVAILABLE -> MetricIssue(healthUi.message ?: "Health Connect is unavailable on this device.", MetricIssueAction.RETRY)
    HealthConnection.ERROR -> MetricIssue(healthUi.message ?: "This metric could not be refreshed.", MetricIssueAction.RETRY)
    HealthConnection.CONNECTED -> if (metric.refreshError != null) {
        MetricIssue(metric.refreshError, MetricIssueAction.RETRY)
    } else if (metric.state == HealthMetricState.UNAVAILABLE) {
        MetricIssue("Permission for this metric is off. Choose it in Health Connect permissions.", MetricIssueAction.CONNECT)
    } else null
}

@Composable
private fun MetricDetailIssue(issue: MetricIssue, onConnectHealth: () -> Unit, onRetry: () -> Unit) {
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(issue.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppActionPill(
                label = if (issue.action == MetricIssueAction.CONNECT) "Health Connect permissions" else "Retry",
                onClick = if (issue.action == MetricIssueAction.CONNECT) onConnectHealth else onRetry,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetricSourceFooter(metric: HealthMetric) {
    val source = metric.source ?: "Unknown source"
    val freshness = metricFreshness(metric.syncedAt, LocalReviewTime.current ?: Instant.now())
    FlowRow(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Health Connect, ${metric.sources.joinToString().ifBlank { source }}, $freshness"
        },
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Cloud, contentDescription = null, tint = AppBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Health Connect · $source", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(freshness, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

private class MetricDetailPreviewProvider : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Weight", "Body fat", "Lean mass", "Workouts", "Distance",
        "Loading", "No data", "Permission", "Provider update", "Unavailable", "Stale", "Read error", "Partial history",
    )
}

@Preview(name = "Metric detail states", widthDp = 360, heightDp = 760)
@Composable
private fun MetricDetailPreview(@PreviewParameter(MetricDetailPreviewProvider::class) case: String) {
    val now = Instant.parse("2026-09-10T18:00:00Z")
    val end = LocalDate.of(2026, 9, 10)
    val selectedCard = DashboardCard.entries.firstOrNull { it.title == case } ?: DashboardCard.WEIGHT
    val sampleMetric = HealthMetric(
        value = when (selectedCard) {
            DashboardCard.WEIGHT -> "184.2"
            DashboardCard.BODY_FAT -> "18.6"
            DashboardCard.LEAN_MASS -> "149.9"
            DashboardCard.WORKOUTS -> "4"
            DashboardCard.DISTANCE -> "12.8"
            DashboardCard.TODAY -> "--"
        },
        state = if (case == "Stale") HealthMetricState.STALE else HealthMetricState.CURRENT,
        syncedAt = now,
        recordedAt = if (case == "Stale") now.minus(Duration.ofDays(10)) else now.minus(Duration.ofHours(2)),
        source = if (selectedCard == DashboardCard.DISTANCE) "Multiple sources" else "Withings",
        sources = if (selectedCard == DashboardCard.DISTANCE) listOf("Google Fit", "Withings") else listOf("Withings"),
    )
    val sampleTrend = (0L..29L).map { offset ->
        HealthTrendPoint(end.minusDays(29L - offset), if (offset % 6L == 0L) null else 180.0 + offset / 10.0)
    }
    fun stats(metric: HealthMetric = sampleMetric, trend: List<HealthTrendPoint> = sampleTrend) = when (selectedCard) {
        DashboardCard.WEIGHT -> HealthStats(weight = metric, weightTrend = trend)
        DashboardCard.BODY_FAT -> HealthStats(bodyFat = metric, bodyFatTrend = trend.map { it.copy(value = it.value?.div(10)) })
        DashboardCard.LEAN_MASS -> HealthStats(leanMass = metric, leanMassTrend = trend.map { it.copy(value = it.value?.minus(30)) })
        DashboardCard.WORKOUTS -> HealthStats(workouts = metric, workoutTrend = trend.map { it.copy(value = it.value?.rem(4)) })
        DashboardCard.DISTANCE -> HealthStats(distance = metric, distanceTrend = trend.map { it.copy(value = it.value?.rem(8)) })
        DashboardCard.TODAY -> HealthStats()
    }
    val ui = when (case) {
        "Loading" -> HealthUiState(HealthConnection.CONNECTED, stats(), isLoading = true)
        "No data" -> HealthUiState(HealthConnection.CONNECTED, stats(HealthMetric(state = HealthMetricState.MISSING, syncedAt = now), emptyList()))
        "Permission" -> HealthUiState(HealthConnection.NEEDS_PERMISSION)
        "Provider update" -> HealthUiState(HealthConnection.UPDATE_REQUIRED)
        "Unavailable" -> HealthUiState(HealthConnection.UNAVAILABLE, message = "Health Connect is unavailable on this device.")
        "Read error" -> HealthUiState(HealthConnection.ERROR, message = "Could not read Health Connect data.")
        "Partial history" -> HealthUiState(HealthConnection.CONNECTED, stats(trend = sampleTrend.takeLast(8)))
        else -> HealthUiState(HealthConnection.CONNECTED, stats())
    }
    DraftingRoom5Theme {
        MetricDetailScreen(
            card = selectedCard,
            healthUi = ui,
            dateRange = HealthDateRange.MONTH,
            onDateRangeChange = {},
            onConnectHealth = {},
            onRetry = {},
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    healthUi: HealthUiState,
    visibleDashboardCount: Int,
    enabledSessionCount: Int,
    onBack: () -> Unit,
    onConnectHealth: () -> Unit,
    onOpenHealthSettings: () -> Unit,
    onManagePlan: () -> Unit,
    onCustomizeDashboard: () -> Unit,
    hapticsEnabled: Boolean,
    hapticPresentation: HapticSettingsPresentation,
    onHapticsEnabledChange: (Boolean) -> Unit,
    voiceSettings: VoiceAnnouncementSettings,
    voiceAvailability: VoiceAvailability,
    onVoiceSettingsChange: (VoiceAnnouncementSettings) -> Unit,
    backupStatus: AutomaticBackupStatus,
    backupActionMessage: String?,
    onAutomaticBackupChange: (Boolean) -> Unit,
    onBackUpNow: () -> Unit,
    onRestoreLatest: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    updateStatus: AppUpdateStatus,
    updateBusy: Boolean,
    updateActionMessage: String?,
    onCheckAndInstallUpdate: () -> Unit,
    reviewSection: String? = null,
) {
    BackHandler(onBack = onBack)
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    if (confirmRestore) AppConfirmationDialog(
        title = "Restore recovery snapshot?",
        message = "This replaces your routines, schedule, saved sessions, history, and settings with the recovery copy. Health Connect data is unaffected.",
        confirmLabel = "Restore",
        onConfirm = { confirmRestore = false; onRestoreLatest() },
        onDismiss = { confirmRestore = false },
    )
    val healthSources = listOf(
        healthUi.stats.weight,
        healthUi.stats.bodyFat,
        healthUi.stats.leanMass,
        healthUi.stats.workouts,
        healthUi.stats.distance,
    ).flatMap { it.sources.ifEmpty { listOfNotNull(it.source) } }.distinct()
    val healthPresentation = healthSettingsPresentation(healthUi.connection, healthUi.isLoading, healthSources)
    val backupPresentation = backupSettingsPresentation(backupStatus, (LocalReviewTime.current ?: Instant.now()).toEpochMilli())
    val updatePresentation = updateSettingsPresentation(updateStatus, updateBusy, updateActionMessage, BuildConfig.VERSION_NAME)
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Settings", onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (reviewSection == null || reviewSection == "TRAINING") item {
                SettingsSection("TRAINING") {
                    SettingsNavigationRow(Icons.Default.Settings, "Customize dashboard", "Choose and reorder dashboard sections", "$visibleDashboardCount visible", onClick = onCustomizeDashboard)
                    SettingsDivider()
                    SettingsNavigationRow(Icons.Default.FitnessCenter, "Schedules & routines", "Plan the week and edit routines", "$enabledSessionCount sessions", onClick = onManagePlan)
                }
            }
            if (reviewSection == null || reviewSection == "WORKOUT FEEDBACK") item {
                SettingsSection("WORKOUT FEEDBACK") {
                    SettingsToggleRow(
                        Icons.Default.Timer,
                        "Voice announcements",
                        when (voiceAvailability) {
                            VoiceAvailability.INITIALIZING -> "Checking text-to-speech service"
                            VoiceAvailability.READY -> "Countdowns, timers, sets, and completion"
                            VoiceAvailability.UNAVAILABLE -> "Text-to-speech unavailable; timers still work"
                        },
                        voiceSettings.enabled,
                        voiceAvailability == VoiceAvailability.READY,
                    ) { onVoiceSettingsChange(voiceSettings.copy(enabled = it)) }
                    SettingsDivider()
                    Column(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
                            .padding(start = 66.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("Voice rate", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${voiceRateLabel(voiceSettings.rate)} · ${String.format(Locale.US, "%.2f", voiceSettings.rate)}×",
                                color = AppBlue,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Slider(
                            modifier = Modifier.semantics {
                                contentDescription = "Voice rate"
                                stateDescription = "${voiceRateLabel(voiceSettings.rate)}, ${String.format(Locale.US, "%.2f", voiceSettings.rate)} times"
                            },
                            value = voiceSettings.rate,
                            onValueChange = { onVoiceSettingsChange(voiceSettings.copy(rate = it)) },
                            valueRange = MIN_VOICE_RATE..MAX_VOICE_RATE,
                            steps = 14,
                            enabled = voiceSettings.enabled && voiceAvailability == VoiceAvailability.READY,
                        )
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        Icons.Default.FitnessCenter,
                        "Haptic feedback",
                        hapticPresentation.detail,
                        hapticsEnabled,
                        hapticPresentation.available,
                        onHapticsEnabledChange,
                    )
                }
            }
            if (reviewSection == null || reviewSection == "CONNECTIONS & DATA") item {
                SettingsSection("CONNECTIONS & DATA") {
                    SettingsActionRow(
                        icon = Icons.Default.FitnessCenter,
                        title = "Health Connect",
                        detail = healthPresentation.summary,
                        action = healthPresentation.actionLabel,
                        enabled = healthPresentation.action != HealthSettingsAction.NONE,
                        tone = healthPresentation.tone,
                    ) {
                        if (healthPresentation.action == HealthSettingsAction.MANAGE) onOpenHealthSettings() else onConnectHealth()
                    }
                    if (healthUi.connection == HealthConnection.CONNECTED) {
                        TextButton(
                            onClick = onConnectHealth,
                            enabled = !healthUi.isLoading,
                            modifier = Modifier.padding(start = 58.dp).defaultMinSize(minHeight = 48.dp),
                        ) { Text(if (healthUi.needsAdditionalAccess) "Review permissions" else "Refresh data") }
                    }
                    healthUi.message?.let {
                        Text(it, modifier = Modifier.padding(start = 66.dp, end = 18.dp, bottom = 12.dp), color = AppGold, style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsDivider()
                    SettingsNavigationRow(
                        Icons.Default.Cloud,
                        "Automatic backups",
                        "Offline recovery snapshots",
                        backupPresentation.summary,
                        summaryColor = settingsToneColor(backupPresentation.tone),
                        expanded = backupExpanded,
                    ) { backupExpanded = !backupExpanded }
                    if (backupExpanded) {
                        Column(Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsToggleRow(null, "Automatic snapshots", "Keep two offline recovery copies", backupStatus.enabled, true, onAutomaticBackupChange)
                            Text("Includes settings, schedules, routines, and workout history. Health Connect data and permissions are excluded.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            backupActionMessage?.let {
                                Text(it, color = if (it.contains("fail", true) || it.contains("could not", true)) AppGold else AppMint, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = onBackUpNow, modifier = Modifier.fillMaxWidth()) { Text("Back up now") }
                            OutlinedButton(onClick = { confirmRestore = true }, enabled = backupStatus.hasRecoverySnapshot, modifier = Modifier.fillMaxWidth()) { Text("Restore latest") }
                            TextButton(onClick = onOpenBackupSettings, modifier = Modifier.align(Alignment.End).defaultMinSize(minHeight = 48.dp)) { Text("Android backup settings") }
                        }
                    }
                }
            }
            if (reviewSection == null || reviewSection == "APP") item {
                SettingsSection("APP") {
                    SettingsActionRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "App updates",
                        detail = updatePresentation.summary,
                        action = updatePresentation.actionLabel,
                        enabled = updatePresentation.enabled,
                        tone = updatePresentation.tone,
                        onClick = onCheckAndInstallUpdate,
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorialSectionLabel(label)
        val shape = RoundedCornerShape(20.dp)
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, AppBorder, shape),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = AppSurface),
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    detail: String,
    summary: String,
    summaryColor: Color = AppBlue,
    expanded: Boolean? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).clickable(onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            .semantics { if (expanded != null) stateDescription = if (expanded) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(summary, color = summaryColor, style = MaterialTheme.typography.labelLarge)
        }
        Icon(
            if (expanded == true) Icons.Default.KeyboardArrowUp else if (expanded == false) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp).padding(4.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsToggleRow(icon: ImageVector?, title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp)
            .padding(start = if (icon == null) 0.dp else 10.dp, end = if (icon == null) 0.dp else 10.dp, top = 12.dp, bottom = 12.dp),
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { SettingsLeadingIcon(it) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = title })
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    action: String,
    enabled: Boolean = true,
    tone: SettingsStatusTone = SettingsStatusTone.NEUTRAL,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 7.dp).size(7.dp).clip(CircleShape).background(settingsToneColor(tone)))
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "$action $title" }) { Text(action) }
    }
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = AppBlue, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = AppBorder, modifier = Modifier.padding(start = 58.dp))
}

@Composable
private fun settingsToneColor(tone: SettingsStatusTone): Color = when (tone) {
    SettingsStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    SettingsStatusTone.POSITIVE -> AppMint
    SettingsStatusTone.ATTENTION -> AppGold
}

@Preview(name = "Settings compact", widthDp = 320, heightDp = 640)
@Preview(name = "Settings tall", widthDp = 412, heightDp = 900)
@Preview(name = "Settings large font", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun SettingsScreenPreview() {
    DraftingRoom5Theme {
        SettingsScreen(
            healthUi = HealthUiState(connection = HealthConnection.CONNECTED),
            visibleDashboardCount = 5,
            enabledSessionCount = 7,
            onBack = {},
            onConnectHealth = {},
            onOpenHealthSettings = {},
            onManagePlan = {},
            onCustomizeDashboard = {},
            hapticsEnabled = true,
            hapticPresentation = HapticSettingsPresentation("Tactile cues during guided sessions", true),
            onHapticsEnabledChange = {},
            voiceSettings = VoiceAnnouncementSettings(),
            voiceAvailability = VoiceAvailability.READY,
            onVoiceSettingsChange = {},
            backupStatus = AutomaticBackupStatus(lastSuccessfulMillis = (LocalReviewTime.current ?: Instant.now()).toEpochMilli(), hasRecoverySnapshot = true),
            backupActionMessage = null,
            onAutomaticBackupChange = {},
            onBackUpNow = {},
            onRestoreLatest = {},
            onOpenBackupSettings = {},
            updateStatus = AppUpdateStatus(lastCheckedMillis = System.currentTimeMillis()),
            updateBusy = false,
            updateActionMessage = null,
            onCheckAndInstallUpdate = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DashboardCustomizationScreen(
    layout: DashboardLayout,
    hapticsEnabled: Boolean,
    onChange: (DashboardLayout) -> Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val dragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val focusRequesters = remember { DashboardCard.entries.associateWith { FocusRequester() } }
    var workingLayout by remember { mutableStateOf(layout) }
    var draggedCard by remember { mutableStateOf<DashboardCard?>(null) }
    var dragOrigin by remember { mutableStateOf<DashboardLayout?>(null) }
    var pendingFocus by remember { mutableStateOf<DashboardCard?>(null) }
    var resetAnnouncement by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(layout, draggedCard) {
        if (draggedCard == null) workingLayout = layout
    }
    LaunchedEffect(workingLayout.cards, pendingFocus) {
        pendingFocus?.let { card ->
            focusRequesters.getValue(card).requestFocus()
            pendingFocus = null
        }
    }

    fun moveAndSave(card: DashboardCard, offset: Int): Boolean {
        val from = workingLayout.cards.indexOfFirst { it.card == card }
        val target = from + offset
        if (from !in workingLayout.cards.indices || target !in workingLayout.cards.indices) return false
        val updated = workingLayout.moveCard(card, target)
        if (!onChange(updated)) {
            workingLayout = layout
            return false
        }
        workingLayout = updated
        pendingFocus = card
        resetAnnouncement = null
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }

    if (confirmReset) {
        AppConfirmationDialog(
            title = "Reset dashboard?",
            message = "This restores the default section order and visibility.",
            confirmLabel = "Reset",
            onConfirm = {
                val defaults = DashboardLayout()
                if (onChange(defaults)) {
                    workingLayout = defaults
                    pendingFocus = DashboardCard.TODAY
                    resetAnnouncement = "Dashboard restored to defaults"
                }
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        topBar = { SecondaryTopBar("Customize dashboard", onBack) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                EditorialSectionLabel("DASHBOARD SECTIONS")
                Spacer(Modifier.height(18.dp))
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
                    Text("Choose what appears and drag to change the order.", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${workingLayout.visibleCards.size} of ${workingLayout.cards.size} visible", color = AppBlue)
                }
            }
            item {
                BrandedCard(
                    Modifier.fillMaxWidth(),
                    containerColor = if (draggedCard == null) AppSurface else AppSurfaceRaised,
                ) {
                    Column {
                        workingLayout.cards.forEachIndexed { index, preference ->
                            key(preference.card.name) {
                                val moveUp = { moveAndSave(preference.card, -1) }
                                val moveDown = { moveAndSave(preference.card, 1) }
                                FlowRow(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 96.dp)
                                        .focusRequester(focusRequesters.getValue(preference.card))
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) false
                                            else when (event.key) {
                                                Key.DirectionUp -> moveUp()
                                                Key.DirectionDown -> moveDown()
                                                else -> false
                                            }
                                        }
                                        .focusable()
                                        .semantics {
                                            stateDescription = "${preference.card.title}, ${if (preference.visible) "visible" else "hidden"}, position ${index + 1} of ${workingLayout.cards.size}"
                                            customActions = buildList {
                                                if (index > 0) add(CustomAccessibilityAction("Move up", moveUp))
                                                if (index < workingLayout.cards.lastIndex) add(CustomAccessibilityAction("Move down", moveDown))
                                            }
                                        }
                                        .background(if (draggedCard == preference.card) AppBlue.copy(alpha = .12f) else Color.Transparent)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                                ) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        dashboardPreferenceIcon(preference.card),
                                        null,
                                        tint = if (preference.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp).padding(10.dp),
                                    )
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(preference.card.title, fontWeight = FontWeight.SemiBold, color = if (preference.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(preference.card.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        modifier = Modifier.semantics { contentDescription = "Show ${preference.card.title}" },
                                        checked = preference.visible,
                                        enabled = !preference.visible || workingLayout.visibleCards.size > 1,
                                        onCheckedChange = { visible ->
                                            val updated = workingLayout.setVisible(preference.card, visible)
                                            if (updated != workingLayout && onChange(updated)) {
                                                workingLayout = updated
                                                resetAnnouncement = null
                                            }
                                        },
                                    )
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder ${preference.card.title}, position ${index + 1} of ${workingLayout.cards.size}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .pointerInput(preference.card, hapticsEnabled) {
                                                var dragDistance = 0f
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        dragDistance = 0f
                                                        dragOrigin = workingLayout
                                                        draggedCard = preference.card
                                                        pendingFocus = preference.card
                                                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragCancel = {
                                                        workingLayout = dragOrigin ?: layout
                                                        draggedCard = null
                                                        dragOrigin = null
                                                    },
                                                    onDragEnd = {
                                                        val completed = workingLayout
                                                        draggedCard = null
                                                        dragOrigin = null
                                                        pendingFocus = preference.card
                                                        if (completed != layout && !onChange(completed)) workingLayout = layout
                                                    },
                                                ) { change, amount ->
                                                    change.consume()
                                                    dragDistance += amount.y
                                                    if (kotlin.math.abs(dragDistance) >= dragThreshold) {
                                                        val current = workingLayout
                                                        val from = current.cards.indexOfFirst { it.card == preference.card }
                                                        val target = (from + if (dragDistance > 0) 1 else -1).coerceIn(current.cards.indices)
                                                        if (target != from) {
                                                            workingLayout = current.moveCard(preference.card, target)
                                                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            scope.launch { listState.scrollBy(if (dragDistance > 0) dragThreshold else -dragThreshold) }
                                                        }
                                                        dragDistance = 0f
                                                    }
                                                }
                                            },
                                    )
                                    }
                                }
                                if (index < workingLayout.cards.lastIndex) HorizontalDivider(color = AppBorder, modifier = Modifier.padding(start = 62.dp))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    resetAnnouncement ?: "✓  Changes save automatically",
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                    color = AppMint,
                )
            }
            item { TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("↶  Reset dashboard", color = AppGold) } }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun dashboardPreferenceIcon(card: DashboardCard): ImageVector = when (card) {
    DashboardCard.TODAY -> Icons.Default.CalendarMonth
    DashboardCard.WEIGHT -> Icons.Default.MonitorWeight
    DashboardCard.BODY_FAT -> Icons.Default.WaterDrop
    DashboardCard.LEAN_MASS -> Icons.Default.FitnessCenter
    DashboardCard.WORKOUTS -> Icons.Default.EventAvailable
    DashboardCard.DISTANCE -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Preview(name = "Customize compact", widthDp = 320, heightDp = 640)
@Preview(name = "Customize tall", widthDp = 412, heightDp = 900)
@Preview(name = "Customize large font", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun DashboardCustomizationPreview() {
    DraftingRoom5Theme {
        DashboardCustomizationScreen(DashboardLayout(), hapticsEnabled = true, onChange = { true }, onBack = {})
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
    val domain = metricChartDomain(trend)
    val recordedPoints = trend.mapIndexedNotNull { index, point -> point.value?.let { index to it } }
    Spacer(Modifier.height(14.dp))
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("No data found for this range", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(top = 8.dp)
            .clearAndSetSemantics { contentDescription = metricChartDescription(
                DashboardCard.entries.first { it.title == label },
                trend,
            ) },
    ) {
        checkNotNull(domain)
        val fractions = metricChartFractions(trend)
        val inset = 8.dp.toPx()
        fun x(index: Int) = inset + (size.width - 2 * inset) * fractions[index]
        fun y(value: Double) = size.height - ((value - domain.minimum) / (domain.maximum - domain.minimum)).toFloat() * size.height

        repeat(4) { guide ->
            val guideY = size.height * guide / 3f
            drawLine(AppBorder.copy(alpha = .8f), androidx.compose.ui.geometry.Offset(0f, guideY), androidx.compose.ui.geometry.Offset(size.width, guideY), strokeWidth = 1f)
        }
        if (recordedPoints.size > 1) {
            val area = Path().apply {
                moveTo(x(recordedPoints.first().first), size.height)
                lineTo(x(recordedPoints.first().first), y(recordedPoints.first().second))
                recordedPoints.drop(1).forEach { (index, value) -> lineTo(x(index), y(value)) }
                lineTo(x(recordedPoints.last().first), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .22f), Color.Transparent)))
            recordedPoints.zipWithNext().forEach { (start, end) ->
                drawLine(
                    accent,
                    start = androidx.compose.ui.geometry.Offset(x(start.first), y(start.second)),
                    end = androidx.compose.ui.geometry.Offset(x(end.first), y(end.second)),
                    strokeWidth = 4f,
                )
            }
        }
        recordedPoints.forEach { (index, value) ->
            val isLast = index == recordedPoints.last().first
            drawCircle(if (isLast) Color(0xFFF4F0E7) else accent, radius = if (isLast) 7f else 5f, center = androidx.compose.ui.geometry.Offset(x(index), y(value)))
        }
    }
    val span = checkNotNull(metricDateSpan(trend))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(span.first.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatMetricNumber(DashboardCard.entries.first { it.title == label }, values.last())} $unit", style = MaterialTheme.typography.labelSmall, color = accent)
        Text(span.last.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptySchedule(isToday: Boolean) {
    BrandedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Check, null, tint = AppMint)
            Spacer(Modifier.height(8.dp))
            Text(if (isToday) "Nothing scheduled today" else "Nothing scheduled this day", fontWeight = FontWeight.Bold)
            Text("Recovery day", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekSelector(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(date) }
                    .semantics {
                        selected = isSelected
                        stateDescription = buildString {
                            append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())))
                            if (date == today) append(", today")
                            if (isSelected) append(", selected")
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                        .background(if (isSelected) AppBlueStrong else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(date.dayOfMonth.toString(), color = if (isSelected) AppBackgroundDeep else MaterialTheme.colorScheme.onSurface)
                }
                Box(Modifier.size(4.dp).background(if (date == today) AppBlue else Color.Transparent, CircleShape))
            }
        }
    }
}

@Composable
private fun SessionCard(session: DashboardSession, onClick: () -> Unit) {
    val routine = session.routine
    val completed = session.action == SessionAction.DONE
    val actionModifier = if (completed) Modifier else Modifier.clickable(
        onClickLabel = session.accessibilityAction,
        onClick = onClick,
    )
    val progress = session.progressLabel
    val metadata = when {
        progress != null && session.savedOriginDate != null -> "$progress · ${session.savedOriginDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}"
        progress != null -> progress
        completed -> "Completed"
        routine.execution == RoutineExecution.GUIDED -> "${routine.exercises.size} exercises"
        else -> "Opens ${linkedAppDisplayName(checkNotNull(routine.appLink).packageName)}"
    }
    val eyebrow = when (routine.execution) {
        RoutineExecution.GUIDED -> "GUIDED ROUTINE"
        RoutineExecution.LINKED_APP -> "LINKED APP · ${linkedAppDisplayName(checkNotNull(routine.appLink).packageName)}"
    }
    BrandedCard(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(actionModifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "${routine.name}. $eyebrow. $metadata. ${session.actionLabel}."
                stateDescription = if (completed) "Completed" else session.actionLabel
            }
            .then(if (completed) Modifier.border(1.dp, AppMint.copy(alpha = .7f), MaterialTheme.shapes.large) else Modifier),
        containerColor = if (completed) AppCompleted else AppSurfaceRaised,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 168.dp)) {
            Box(Modifier.matchParentSize()) {
            Image(
                painter = painterResource(RoutineArtworkCatalog.resolve(routine.artworkId).cardAsset),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.7f).fillMaxHeight(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
            )
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(.56f).fillMaxHeight().background(
                    Brush.horizontalGradient(
                        listOf(if (completed) AppCompleted else AppSurfaceRaised, Color.Transparent),
                    ),
                ),
            )
            }
            Column(Modifier.fillMaxWidth(.72f).heightIn(min = 168.dp).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    eyebrow,
                    color = if (completed) AppMint else AppBlue,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.8.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(routine.name, color = Color(0xFFF4F0E7), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SessionActionPill(session.action)
            }
        }
    }
}

@Composable
private fun SessionActionPill(action: SessionAction) {
    val completed = action == SessionAction.DONE
    Row(
        modifier = Modifier
            .clearAndSetSemantics { }
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (completed) AppCompleted else AppBlue)
            .border(1.dp, if (completed) AppMint else AppBlue, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (completed) Icon(Icons.Default.Check, null, tint = AppMint, modifier = Modifier.size(18.dp))
        Text(action.name.lowercase().replaceFirstChar(Char::uppercase), color = if (completed) AppMint else AppBackgroundDeep, fontWeight = FontWeight.Bold)
        if (!completed) Icon(Icons.Default.ChevronRight, null, tint = AppBackgroundDeep, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DashboardSessionPreviewContent(sessions: List<DashboardSession>) {
    DraftingRoom5Theme {
        Column(
            Modifier.fillMaxSize().appScreenBackground().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EditorialSectionLabel("TODAY'S SESSION")
            if (sessions.isEmpty()) EmptySchedule(true) else sessions.forEach { SessionCard(it) {} }
            WeekSelector(
                dashboardWeek(LocalDate.of(2026, 9, 10)),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
            ) {}
        }
    }
}

/** Shared real-screen fixtures for host-rendered visual regression checks. */
@Composable
internal fun CoreShellReviewPreview(screen: String) {
    CompositionLocalProvider(LocalReviewTime provides Instant.parse("2026-09-10T18:00:00Z")) {
    when (screen) {
        in HardeningState.entries.map { it.fixtureName } -> HardeningStateReviewPreview(checkNotNull(hardeningStateForFixture(screen)))
        "Session idle", "Session ready", "Session running", "Session finished" -> GuidedSessionReviewPreview(screen)
        "Session completion" -> SessionCompletionReviewPreview()
        "Settings" -> SettingsScreenPreview()
        "Customization" -> DashboardCustomizationPreview()
        "Schedule", "Routines" -> DraftingRoom5Theme {
            PlanManagementScreen(
                plan = defaultTrainingPlan(),
                partialSessionCounts = emptyMap(),
                onChange = { true },
                onReset = {},
                onEditRoutine = {},
                onAddGuidedRoutine = {},
                onAddLinkedRoutine = {},
                onRenameRoutine = { _, _ -> },
                onDeleteRoutine = {},
                onDeleteSchedule = {},
                onAddSchedule = {},
                onEditSchedule = {},
                onBack = {},
                initialTab = if (screen == "Routines") 1 else 0,
            )
        }
        "Linked editor" -> DraftingRoom5Theme {
            LinkedAppRoutineEditorScreen(
                routine = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.LINKED_APP },
                replacement = null,
                scheduleSummary = "Scheduled Mon · Tue · Thu",
                onChooseApp = {},
                onSave = { true },
                onDelete = {},
                onTestLink = { LinkedAppLaunchResult.Failed(LinkedAppLaunchFailure.UNAVAILABLE) },
                onBack = {},
            )
        }
        "Guided editor" -> DraftingRoom5Theme {
            val plan = defaultTrainingPlan()
            val routine = plan.routines.first { it.execution == RoutineExecution.GUIDED }
            GuidedRoutineEditorScreen(
                routine = routine,
                scheduleSummary = plan.routineScheduleSummary(routine.id),
                isNew = false,
                onPersist = { true },
                onDelete = {},
                onBack = {},
            )
        }
        "Schedule editor" -> DraftingRoom5Theme {
            ScheduleEditorScreen(
                plan = defaultTrainingPlan(),
                original = null,
                draftId = "preview-schedule",
                requestedAnchor = java.time.DayOfWeek.SATURDAY,
                onSave = { true },
                onBack = {},
            )
        }
        "Exercise builder" -> DraftingRoom5Theme {
            ExerciseEditorScreen(defaultTrainingPlan().routines.last().exercises.first(), {}, {})
        }
        "Weight" -> MetricDetailPreview("Weight")
        else -> DraftingRoom5Theme {
            Dashboard(
                healthUi = HealthUiState(HealthConnection.CONNECTED),
                dashboardLayout = DashboardLayout(),
                healthDateRange = HealthDateRange.MONTH,
                trainingPlan = defaultTrainingPlan(),
                partialSessions = emptyList(),
                workoutHistory = emptyList(),
                animateBrandOnEntry = false,
                onBrandAnimationFinished = {},
                onOpenSettings = {},
                updateAvailableVersion = null,
                onInstallUpdate = {},
                onOpenMetric = {},
                onOpenCustom = { _, _ -> },
                onLaunchExternal = {},
            )
        }
    }
    }
}

@Composable
private fun HardeningStateReviewPreview(state: HardeningState) {
    when (state) {
        HardeningState.LOADING -> MetricDetailPreview("Loading")
        HardeningState.EMPTY -> MetricDetailPreview("No data")
        HardeningState.APP_PICKER_LOADING,
        HardeningState.APP_PICKER_EMPTY,
        HardeningState.APP_PICKER_FAILURE -> DraftingRoom5Theme {
            val pickerState = when (state) {
                HardeningState.APP_PICKER_LOADING -> InstalledAppLoadState.Loading
                HardeningState.APP_PICKER_EMPTY -> InstalledAppLoadState.Ready(emptyList())
                else -> InstalledAppLoadState.Failed("Android could not list launchable apps.")
            }
            InstalledAppPickerContent(pickerState, "", {}, {}, {}, {})
        }
        HardeningState.HEALTH_PERMISSION -> MetricDetailPreview("Permission")
        HardeningState.HEALTH_UNAVAILABLE -> MetricDetailPreview("Unavailable")
        HardeningState.HEALTH_UPDATE_REQUIRED -> MetricDetailPreview("Provider update")
        HardeningState.TTS_UNAVAILABLE,
        HardeningState.BACKUP_FAILURE,
        HardeningState.UPDATE_FAILURE -> SettingsHardeningPreview(state)
        HardeningState.LINKED_APP_UNINSTALLED -> DraftingRoom5Theme {
            val routine = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.LINKED_APP }
                .copy(appLink = AppLink("dev.draftingroom5.missing", null))
            LinkedAppRoutineEditorScreen(
                routine = routine,
                replacement = null,
                scheduleSummary = "Scheduled Mon · Thu",
                onChooseApp = {},
                onSave = { true },
                onDelete = {},
                onTestLink = { LinkedAppLaunchResult.Failed(LinkedAppLaunchFailure.UNAVAILABLE) },
                onBack = {},
            )
        }
        HardeningState.MISSING_ARTWORK -> DraftingRoom5Theme {
            val plan = defaultTrainingPlan().let { current ->
                current.copy(routines = current.routines.map { it.copy(artworkId = "missing-artwork") })
            }
            Dashboard(
                healthUi = HealthUiState(HealthConnection.CONNECTED),
                dashboardLayout = DashboardLayout(),
                healthDateRange = HealthDateRange.MONTH,
                trainingPlan = plan,
                partialSessions = emptyList(),
                workoutHistory = emptyList(),
                animateBrandOnEntry = false,
                onBrandAnimationFinished = {},
                onOpenSettings = {},
                updateAvailableVersion = null,
                onInstallUpdate = {},
                onOpenMetric = {},
                onOpenCustom = { _, _ -> },
                onLaunchExternal = {},
            )
        }
        HardeningState.NO_ROUTINES -> PlanHardeningPreview(emptyPlan = true, routinesTab = true)
        HardeningState.RECOVERY_DAY -> PlanHardeningPreview(emptyPlan = false, routinesTab = false)
        HardeningState.CORRUPT_CURRENT_DATA -> HardeningMessagePreview(
            title = "App data needs attention",
            message = "The current app data is damaged. Restore a recovery snapshot from Settings, or explicitly reset to current defaults.",
            action = "Open Settings",
        )
        HardeningState.PERSISTENCE_FAILURE -> HardeningMessagePreview(
            title = "Couldn’t save changes",
            message = "Your draft is still shown. Retry after storage becomes available.",
            action = "Retry",
        )
        HardeningState.DESTRUCTIVE_CONFIRMATION -> DraftingRoom5Theme {
            Box(Modifier.fillMaxSize().appScreenBackground()) {
                AppConfirmationDialog(
                    title = "Reset app data?",
                    message = "This removes routines, schedules, saved sessions, and history. Health Connect data is not changed.",
                    confirmLabel = "Reset",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}

@Composable
private fun SettingsHardeningPreview(state: HardeningState) {
    val now = (LocalReviewTime.current ?: Instant.now()).toEpochMilli()
    val voiceAvailability = if (state == HardeningState.TTS_UNAVAILABLE) VoiceAvailability.UNAVAILABLE else VoiceAvailability.READY
    val backupStatus = if (state == HardeningState.BACKUP_FAILURE) {
        AutomaticBackupStatus(lastSuccessfulMillis = now - 86_400_000L, lastFailureMillis = now, lastFailureMessage = "Storage unavailable", hasRecoverySnapshot = true)
    } else AutomaticBackupStatus(lastSuccessfulMillis = now, hasRecoverySnapshot = true)
    val updateStatus = if (state == HardeningState.UPDATE_FAILURE) AppUpdateStatus(lastError = "Network unavailable. Check your connection and try again.")
        else AppUpdateStatus(lastCheckedMillis = now)
    DraftingRoom5Theme {
        SettingsScreen(
            healthUi = HealthUiState(connection = HealthConnection.CONNECTED),
            visibleDashboardCount = 5,
            enabledSessionCount = 7,
            onBack = {}, onConnectHealth = {}, onOpenHealthSettings = {}, onManagePlan = {}, onCustomizeDashboard = {},
            hapticsEnabled = true,
            hapticPresentation = HapticSettingsPresentation("Tactile cues during guided sessions", true),
            onHapticsEnabledChange = {},
            voiceSettings = VoiceAnnouncementSettings(),
            voiceAvailability = voiceAvailability,
            onVoiceSettingsChange = {},
            backupStatus = backupStatus,
            backupActionMessage = if (state == HardeningState.BACKUP_FAILURE) "Backup failed: storage is unavailable. Existing snapshots were kept." else null,
            onAutomaticBackupChange = {}, onBackUpNow = {}, onRestoreLatest = {}, onOpenBackupSettings = {},
            updateStatus = updateStatus,
            updateBusy = false,
            updateActionMessage = null,
            onCheckAndInstallUpdate = {},
            reviewSection = when (state) {
                HardeningState.TTS_UNAVAILABLE -> "WORKOUT FEEDBACK"
                HardeningState.BACKUP_FAILURE -> "CONNECTIONS & DATA"
                HardeningState.UPDATE_FAILURE -> "APP"
                else -> null
            },
        )
    }
}

@Composable
private fun PlanHardeningPreview(emptyPlan: Boolean, routinesTab: Boolean) {
    val plan = if (emptyPlan) TrainingPlan(emptyList(), emptyList()) else defaultTrainingPlan().copy(schedule = emptyList())
    DraftingRoom5Theme {
        PlanManagementScreen(
            plan = plan,
            partialSessionCounts = emptyMap(),
            onChange = { true }, onReset = {}, onEditRoutine = {}, onAddGuidedRoutine = {}, onAddLinkedRoutine = {},
            onRenameRoutine = { _, _ -> }, onDeleteRoutine = {}, onDeleteSchedule = {}, onAddSchedule = {}, onEditSchedule = {}, onBack = {},
            initialTab = if (routinesTab) 1 else 0,
        )
    }
}

@Composable
private fun HardeningMessagePreview(title: String, message: String, action: String) {
    DraftingRoom5Theme {
        Scaffold(
            modifier = Modifier.fillMaxSize().appScreenBackground(),
            topBar = { SecondaryTopBar("Recovery", {}) },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                InlineErrorState(title, message, action)
            }
        }
    }
}

private fun previewSession(routineId: String, action: SessionAction, completedExercises: Int = 0): DashboardSession {
    val plan = defaultTrainingPlan()
    val routine = checkNotNull(plan.routines.firstOrNull { it.id == routineId })
    val entry = checkNotNull(plan.schedule.firstOrNull { it.routineId == routineId })
    return DashboardSession(entry, routine, action, completedExercises)
}

@Preview(name = "Dashboard linked session", widthDp = 360, heightDp = 360)
@Composable
private fun DashboardLinkedPreview() = DashboardSessionPreviewContent(listOf(previewSession("routine-strength", SessionAction.START)))

@Preview(name = "Dashboard guided session", widthDp = 360, heightDp = 360)
@Composable
private fun DashboardGuidedPreview() = DashboardSessionPreviewContent(listOf(previewSession("routine-forearm", SessionAction.START)))

@Preview(name = "Dashboard resumed session", widthDp = 360, heightDp = 380)
@Composable
private fun DashboardResumePreview() = DashboardSessionPreviewContent(listOf(previewSession("routine-forearm", SessionAction.RESUME, 3)))

@Preview(name = "Dashboard completed session", widthDp = 360, heightDp = 360)
@Composable
private fun DashboardCompletedPreview() = DashboardSessionPreviewContent(listOf(previewSession("routine-forearm", SessionAction.DONE)))

@Preview(name = "Dashboard multiple sessions", widthDp = 360, heightDp = 640)
@Composable
private fun DashboardMultiplePreview() = DashboardSessionPreviewContent(
    listOf(previewSession("routine-strength", SessionAction.START), previewSession("routine-running", SessionAction.START)),
)

@Preview(name = "Dashboard recovery", widthDp = 360, heightDp = 320)
@Composable
private fun DashboardRecoveryPreview() = DashboardSessionPreviewContent(emptyList())

@Preview(name = "Dashboard hero text fallback", widthDp = 360, heightDp = 320)
@Composable
private fun DashboardHeroFallbackPreview() {
    DraftingRoom5Theme {
        Box(Modifier.fillMaxSize().appScreenBackground().padding(horizontal = 20.dp)) {
            TrainingHero(LocalDate.of(2026, 9, 10), emptyList(), showArtwork = false)
        }
    }
}

private suspend fun readHealthStats(
    context: Context,
    client: HealthConnectClient,
    granted: Set<String>,
    dateRange: HealthDateRange,
    previous: HealthStats,
): HealthStats {
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()
    val trendEndDate = LocalDate.now(zoneId)
    fun historyRange(anchor: Instant?): TimeRangeFilter {
        val end = anchor?.atZone(zoneId)?.toLocalDate() ?: trendEndDate
        val start = minOf(dateRange.startDate(end), HealthDateRange.MONTH.startDate(end))
        return TimeRangeFilter.between(start.atStartOfDay(zoneId).toInstant(), minOf(end.plusDays(1).atStartOfDay(zoneId).toInstant(), now))
    }

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
        readMeasurementHistory(client, WeightRecord::class, historyRange(weight.value?.time)).takeIf { it.isNotEmpty() }
    }
    val bodyFatHistory = readHealthValue(HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
        readMeasurementHistory(client, BodyFatRecord::class, historyRange(bodyFat.value?.time)).takeIf { it.isNotEmpty() }
    }
    val leanMassHistory = readHealthValue(HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted) {
        readMeasurementHistory(client, LeanBodyMassRecord::class, historyRange(leanMass.value?.time)).takeIf { it.isNotEmpty() }
    }
    val sessions = readHealthValue(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
        readMeasurementHistory(client, ExerciseSessionRecord::class, historyRange(readLatestMeasurement(client, ExerciseSessionRecord::class, now) { it.endTime }?.endTime)).takeIf { it.isNotEmpty() }
    }
    val distance = readHealthValue(HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
        readMeasurementHistory(client, DistanceRecord::class, historyRange(readLatestMeasurement(client, DistanceRecord::class, now) { it.endTime }?.endTime)).takeIf { it.isNotEmpty() }
    }

    fun source(packageName: String?) = resolveHealthSource(context, packageName)
    fun sources(packageNames: Iterable<String>) = packageNames.mapNotNull(::source).distinct().sorted()
    fun selectedStart(anchor: Instant?): Instant = dateRange.startDate(anchor?.atZone(zoneId)?.toLocalDate() ?: trendEndDate).atStartOfDay(zoneId).toInstant()
    val sessionStart = selectedStart(sessions.value?.maxOfOrNull { it.endTime })
    val distanceStart = selectedStart(distance.value?.maxOfOrNull { it.endTime })
    val selectedSessions = sessions.value?.filter { !it.endTime.isBefore(sessionStart) }
    val selectedDistances = distance.value?.filter { !it.endTime.isBefore(distanceStart) }
    val latestSession = selectedSessions?.maxByOrNull { it.endTime }
    val latestDistance = selectedDistances?.maxByOrNull { it.endTime }

    val result = HealthStats(
        weight = healthMetric(
            value = weight.value?.weight?.inKilograms?.toPounds()?.format(1),
            outcome = weight.outcome,
            syncedAt = now,
            recordedAt = weight.value?.time,
            source = source(weight.value?.metadata?.dataOrigin?.packageName),
            sources = sources(weightHistory.value.orEmpty().map { it.metadata.dataOrigin.packageName }),
        ),
        bodyFat = healthMetric(
            value = bodyFat.value?.percentage?.value?.format(1),
            outcome = bodyFat.outcome,
            syncedAt = now,
            recordedAt = bodyFat.value?.time,
            source = source(bodyFat.value?.metadata?.dataOrigin?.packageName),
            sources = sources(bodyFatHistory.value.orEmpty().map { it.metadata.dataOrigin.packageName }),
        ),
        leanMass = healthMetric(
            value = leanMass.value?.mass?.inKilograms?.toPounds()?.format(1),
            outcome = leanMass.outcome,
            syncedAt = now,
            recordedAt = leanMass.value?.time,
            source = source(leanMass.value?.metadata?.dataOrigin?.packageName),
            sources = sources(leanMassHistory.value.orEmpty().map { it.metadata.dataOrigin.packageName }),
        ),
        workouts = healthMetric(
            value = selectedSessions?.size?.toString(),
            outcome = sessions.outcome,
            syncedAt = now,
            recordedAt = latestSession?.endTime,
            source = source(latestSession?.metadata?.dataOrigin?.packageName),
            sources = sources(selectedSessions.orEmpty().map { it.metadata.dataOrigin.packageName }),
        ),
        distance = healthMetric(
            value = selectedDistances?.sumOf { it.distance.inMeters }?.let { (it / 1_609.344).format(1) },
            outcome = distance.outcome,
            syncedAt = now,
            recordedAt = latestDistance?.endTime,
            source = source(latestDistance?.metadata?.dataOrigin?.packageName),
            sources = sources(selectedDistances.orEmpty().map { it.metadata.dataOrigin.packageName }),
        ),
        weightTrend = measurementHealthTrend(
            weightHistory.value.orEmpty().map { TimedHealthValue(it.time, it.weight.inKilograms.toPounds()) },
            LocalDate.MIN,
            trendEndDate,
            zoneId,
        ),
        bodyFatTrend = measurementHealthTrend(
            bodyFatHistory.value.orEmpty().map { TimedHealthValue(it.time, it.percentage.value) },
            LocalDate.MIN,
            trendEndDate,
            zoneId,
        ),
        leanMassTrend = measurementHealthTrend(
            leanMassHistory.value.orEmpty().map { TimedHealthValue(it.time, it.mass.inKilograms.toPounds()) },
            LocalDate.MIN,
            trendEndDate,
            zoneId,
        ),
        workoutTrend = sessions.value?.let { records ->
            dailyHealthTotals(
                records.map { TimedHealthValue(it.endTime, 1.0) },
                dateRange.startDate(records.maxOf { it.endTime }.atZone(zoneId).toLocalDate()),
                records.maxOf { it.endTime }.atZone(zoneId).toLocalDate(),
                zoneId,
            )
        }.orEmpty(),
        distanceTrend = distance.value?.let { records ->
            dailyHealthTotals(
                records.map { TimedHealthValue(it.endTime, it.distance.inMeters / 1_609.344) },
                dateRange.startDate(records.maxOf { it.endTime }.atZone(zoneId).toLocalDate()),
                records.maxOf { it.endTime }.atZone(zoneId).toLocalDate(),
                zoneId,
            )
        }.orEmpty(),
        historyIssues = listOf(
            DashboardCard.WEIGHT to weightHistory,
            DashboardCard.BODY_FAT to bodyFatHistory,
            DashboardCard.LEAN_MASS to leanMassHistory,
            DashboardCard.WORKOUTS to sessions,
            DashboardCard.DISTANCE to distance,
        ).mapNotNull { (card, read) ->
            read.issue?.takeIf { read.outcome == HealthReadOutcome.ERROR }?.let { card to it }
        }.toMap(),
    )
    return result.copy(
        weight = retainHealthMetricOnError(result.weight, previous.weight),
        bodyFat = retainHealthMetricOnError(result.bodyFat, previous.bodyFat),
        leanMass = retainHealthMetricOnError(result.leanMass, previous.leanMass),
        workouts = retainHealthMetricOnError(result.workouts, previous.workouts),
        distance = retainHealthMetricOnError(result.distance, previous.distance),
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
    return records.distinctBy { it.metadata.id }
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

private fun Double.format(decimals: Int) = String.format(Locale.getDefault(), "%.${decimals}f", this)

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
