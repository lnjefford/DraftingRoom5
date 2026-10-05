package dev.draftingroom5.wear

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import dev.draftingroom5.watch.WatchExercise
import dev.draftingroom5.watch.WatchSnapshot
import dev.draftingroom5.watch.WatchTodayBriefing
import dev.draftingroom5.watch.WatchTodayStock
import dev.draftingroom5.watch.WatchWorkoutStatus
import dev.draftingroom5.watch.WatchRunCapture
import dev.draftingroom5.watch.WatchRunCatalog
import dev.draftingroom5.watch.WatchRunPlan
import dev.draftingroom5.watch.WatchRunInterval
import dev.draftingroom5.watch.WatchRunRoute
import dev.draftingroom5.watch.WatchRunCue
import dev.draftingroom5.watch.WatchRunPoint
import dev.draftingroom5.watch.WatchRunSample
import dev.draftingroom5.watch.WATCH_TREADMILL_ROUTE_ID
import kotlinx.coroutines.delay
import kotlin.math.ceil
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WatchActivity : ComponentActivity() {
    private var resumed by mutableStateOf(false)
    private var ambient by mutableStateOf(false)
    private val repository by lazy { WatchSyncRepository.get(this) }
    private val runRepository by lazy { WatchRunRepository.get(this) }
    private val runRecorder by lazy { WatchRunRecorder.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(androidx.wear.ambient.AmbientLifecycleObserver(this,
            object : androidx.wear.ambient.AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: androidx.wear.ambient.AmbientLifecycleObserver.AmbientDetails) { ambient = true }
                override fun onExitAmbient() { ambient = false }
                override fun onUpdateAmbient() = Unit
            }))
        setContent {
            CompositionLocalProvider(LocalWatchMotionAllowed provides (resumed && !ambient)) {
                DraftingRoom5Watch(repository, runRepository, runRecorder, onClose = ::finish)
            }
        }
    }

    override fun onResume() { super.onResume(); resumed = true }
    override fun onPause() { resumed = false; super.onPause() }

    override fun onStart() {
        super.onStart()
        repository.start()
        runRepository.start()
        runRecorder.resendPending()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runRecorder.capture.value?.takeIf { it.isRunning }?.let { WatchRunService.start(this, it.id) }
        }
    }

    override fun onStop() {
        repository.stop()
        runRepository.stop()
        super.onStop()
    }
}

private val Ink = Color(0xFF061424)
private val Raised = Color(0xFF0D2137)
private val Ivory = Color(0xFFF4F0E8)
private val Blue = Color(0xFF2F82FF)
private val Mint = Color(0xFF64E6B5)
private val Gold = Color(0xFFD8B56B)
private val Secondary = Color(0xFFA9B8CE)
private val Amber = Color(0xFFFFCE77)

private enum class TimerPhase { READY, RUNNING, FINISHED }

private data class LocalTimer(
    val exerciseId: String,
    val readyDeadlineMillis: Long,
    val activeDeadlineMillis: Long,
)

private class LocalTimerStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_timer", Context.MODE_PRIVATE)

    fun read(): LocalTimer? {
        val exerciseId = preferences.getString("exercise", null) ?: return null
        val ready = preferences.getLong("ready", -1)
        val active = preferences.getLong("active", -1)
        return LocalTimer(exerciseId, ready, active).takeIf { ready > 0 && active >= ready }
    }

    fun write(value: LocalTimer?) {
        preferences.edit().apply {
            if (value == null) clear() else {
                putString("exercise", value.exerciseId)
                putLong("ready", value.readyDeadlineMillis)
                putLong("active", value.activeDeadlineMillis)
            }
        }.apply()
    }
}

@Composable
private fun DraftingRoom5Watch(
    repository: WatchSyncRepository,
    runRepository: WatchRunRepository,
    runRecorder: WatchRunRecorder,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot by repository.state.collectAsState()
    val runCatalog by runRepository.catalog.collectAsState()
    val runCapture by runRecorder.capture.collectAsState()
    val pendingRuns by runRecorder.pendingCount.collectAsState()
    var dismissedRunId by remember { mutableStateOf<String?>(null) }
    var runPicker by remember { mutableStateOf(false) }
    var selectedRun by remember { mutableStateOf<WatchRunPlan?>(null) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    var runError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(runPicker, runCatalog?.generation) {
        if (runPicker && selectedRun !in runCatalog?.plans.orEmpty()) {
            selectedRun = runCatalog?.plans?.firstOrNull()
            selectedRouteId = selectedRun?.preferredRouteId ?: runCatalog?.lastRouteId
        }
    }
    BackHandler(runPicker) { runPicker = false }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            selectedRun?.let { plan ->
                val route = runCatalog?.routes?.firstOrNull { it.id == selectedRouteId }
                val started = runRecorder.start(plan.copy(preferredRouteId = selectedRouteId), route, System.currentTimeMillis())
                if (started == null) runError = "Could not start run."
                else {
                    WatchRunService.start(context, started.id)
                    runPicker = false
                }
            }
        } else {
            runError = if (selectedRouteId == WATCH_TREADMILL_ROUTE_ID)
                "Location permission keeps the watch timer active in the background. GPS stays off."
                else "Location permission is needed to record a run."
            runPicker = true
        }
    }
    val timerStore = remember { LocalTimerStore(context) }
    val startNativeRun: (WatchRunPlan) -> Unit = { plan ->
        selectedRun = plan
        selectedRouteId = plan.preferredRouteId ?: runCatalog?.lastRouteId
        runError = null
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val started = runRecorder.start(plan.copy(preferredRouteId = selectedRouteId),
                runCatalog?.routes?.firstOrNull { it.id == selectedRouteId }, System.currentTimeMillis())
            if (started == null) { runError = "Could not start run."; runPicker = true }
            else WatchRunService.start(context, started.id)
        } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var timer by remember { mutableStateOf(timerStore.read()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var celebration by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showTodayAfterComplete by remember { mutableStateOf(false) }
    val exercise = snapshot?.focusedExercise
    val startNativeWorkout: (String?) -> Unit = { id ->
        if (id == null) repository.startToday() else repository.startWorkout(id)
        val first = repository.state.value?.takeIf { it.status == WatchWorkoutStatus.ACTIVE }
            ?.focusedExercise?.takeIf { it.completedSets < it.setCount }
        first?.durationSeconds?.let { seconds ->
            val started = System.currentTimeMillis()
            timer = LocalTimer(first.id, started + 10_000L, started + 10_000L + seconds * 1_000L)
            timerStore.write(timer)
            if (snapshot?.hapticsEnabled != false) vibrate(context, long = false)
        }
    }

    LaunchedEffect(timer, runCapture?.id, runCapture?.isRunning) {
        while (timer != null || runCapture?.isRunning == true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    LaunchedEffect(exercise?.id) {
        if (timer?.exerciseId != null && timer?.exerciseId != exercise?.id) {
            timer = null
            timerStore.write(null)
        }
    }
    LaunchedEffect(snapshot?.status) {
        if (snapshot?.status in setOf(WatchWorkoutStatus.ACTIVE, WatchWorkoutStatus.READY_TO_FINISH)) runPicker = false
        if (snapshot?.status == WatchWorkoutStatus.READY_TO_FINISH) repository.finishSession()
    }
    LaunchedEffect(celebration) {
        if (celebration != null) {
            delay(900)
            celebration = null
        }
    }

    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.watch_dusk), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .72f), Ink.copy(alpha = .37f), Ink.copy(alpha = .86f)))))
        when {
            runCapture != null && runCapture?.completedAtMillis == null -> WatchActiveRunScreen(
                checkNotNull(runCapture), now,
                onPause = { runRecorder.pause(System.currentTimeMillis()); WatchRunService.stop(context) },
                onResume = { runRecorder.resume(System.currentTimeMillis())?.let { WatchRunService.start(context, it.id) } },
                onFinish = { runRecorder.finish(System.currentTimeMillis()); WatchRunService.stop(context) },
            )
            runCapture?.completedAtMillis != null && runCapture?.id != dismissedRunId -> WatchRunCompleteScreen(
                checkNotNull(runCapture), pendingRuns, onDone = { dismissedRunId = runCapture?.id })
            runPicker && snapshot?.status !in setOf(WatchWorkoutStatus.ACTIVE, WatchWorkoutStatus.READY_TO_FINISH) -> WatchRunPickerScreen(
                runCatalog,
                selectedRun,
                selectedRouteId,
                runError,
                onSelectPlan = { plan -> selectedRun = plan; selectedRouteId = plan.preferredRouteId },
                onSelectRoute = { selectedRouteId = it },
                onStart = {
                    if (selectedRun != null) {
                        runError = null
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            val started = runRecorder.start(checkNotNull(selectedRun).copy(preferredRouteId = selectedRouteId),
                                runCatalog?.routes?.firstOrNull { it.id == selectedRouteId }, System.currentTimeMillis())
                            if (started == null) runError = "Could not start run."
                            else { WatchRunService.start(context, started.id); runPicker = false }
                        } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onBack = { runPicker = false },
                onSync = repository::sync,
            )
            celebration != null -> SetCompleteScreen(checkNotNull(celebration)) { celebration = null }
            timer != null && exercise != null -> {
                val currentTimer = checkNotNull(timer)
                TimerScreen(
                    exercise = exercise,
                    timer = currentTimer,
                    now = now,
                    hapticsEnabled = snapshot?.hapticsEnabled != false,
                    onCancel = {
                        timer = null
                        timerStore.write(null)
                    },
                    onComplete = {
                        val setNumber = exercise.completedSets + 1
                        repository.completeSet(exercise.id, setNumber)
                        if (snapshot?.hapticsEnabled != false) vibrate(context, long = false)
                        timer = null
                        timerStore.write(null)
                        if (setNumber < exercise.setCount) celebration = setNumber to exercise.setCount
                    },
                )
            }
            snapshot == null -> WatchHomeScreen("CONNECTING", "Open DraftingRoom5 on your phone", repository::sync,
                runCatalog != null, { runPicker = true })
            snapshot?.status == WatchWorkoutStatus.NONE -> TodayScreen(checkNotNull(snapshot), repository::refreshToday,
                { startNativeWorkout(null) }, { runPicker = true }, runCatalog?.plans?.size ?: 0,
                catalog = runCatalog, onStartRun = startNativeRun, onSelectWorkout = startNativeWorkout)
            snapshot?.status == WatchWorkoutStatus.ERROR -> MessageScreen(
                "PHONE NEEDED", snapshot?.message ?: "Workout data is unavailable.", "Choose run", { runPicker = true },
            )
            snapshot?.status == WatchWorkoutStatus.AVAILABLE -> TodayScreen(checkNotNull(snapshot), repository::refreshToday,
                { startNativeWorkout(null) }, { runPicker = true }, runCatalog?.plans?.size ?: 0,
                catalog = runCatalog, onStartRun = startNativeRun, onSelectWorkout = startNativeWorkout)
            snapshot?.status == WatchWorkoutStatus.COMPLETE -> if (showTodayAfterComplete)
                TodayScreen(checkNotNull(snapshot), repository::refreshToday, { startNativeWorkout(null) },
                    { runPicker = true }, runCatalog?.plans?.size ?: 0,
                    catalog = runCatalog, onStartRun = startNativeRun, onSelectWorkout = startNativeWorkout)
                else CompletionScreen(checkNotNull(snapshot)) { showTodayAfterComplete = true }
            exercise != null -> ExerciseScreen(
                snapshot = checkNotNull(snapshot),
                exercise = exercise,
                onStartTimer = exercise.durationSeconds?.let { seconds ->
                    {
                        val started = System.currentTimeMillis()
                        timer = LocalTimer(exercise.id, started + 10_000L, started + 10_000L + seconds * 1_000L)
                        timerStore.write(timer)
                        if (snapshot?.hapticsEnabled != false) vibrate(context, long = false)
                    }
                },
                onComplete = {
                    val setNumber = exercise.completedSets + 1
                    repository.completeSet(exercise.id, setNumber)
                    if (snapshot?.hapticsEnabled != false) vibrate(context, long = false)
                    if (setNumber < exercise.setCount) celebration = setNumber to exercise.setCount
                },
            )
        }
    }
}

@Composable
private fun WatchRunCompleteScreen(capture: WatchRunCapture, pendingRuns: Int, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            WatchText("RUN COMPLETE", 9, Gold, FontWeight.Bold, letterSpacing = 1.4f)
            SuccessMark(42)
            capture.route?.let { route ->
                RunOverviewMap(route.points, capture.samples.lastOrNull()?.point,
                    Modifier.fillMaxWidth(.57f).height(18.dp))
            }
            WatchText("%02d:%02d".format(capture.elapsedBeforeResumeMillis / 60_000,
                (capture.elapsedBeforeResumeMillis / 1_000) % 60), 22, Ivory,
                FontWeight.Bold, serifFamily())
            if (capture.plan.preferredRouteId != WATCH_TREADMILL_ROUTE_ID)
                WatchText("${"%.2f".format(capture.distanceMeters / 1000.0)} km", 11, Ivory)
            WatchText(if (pendingRuns > 0) "☁ Sync pending" else "Saved on phone", 8, Secondary)
        }
        CompactButton("Done", Blue, onDone, Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 35.dp, vertical = 9.dp).fillMaxWidth())
    }
}

@Composable
private fun WatchHomeScreen(eyebrow: String, message: String, onSync: () -> Unit,
    hasRuns: Boolean, onRuns: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            PhoneStatusIcon(false)
            Spacer(Modifier.height(5.dp))
            WatchText(eyebrow, 11, Ivory, FontWeight.Bold, letterSpacing = 2f)
            WatchText(message, 10, Secondary, maxLines = 2,
                modifier = Modifier.padding(horizontal = 27.dp))
        }
        CompactButton("Sync", Blue, onSync, Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 36.dp, vertical = if (hasRuns) 31.dp else 12.dp).fillMaxWidth())
        if (hasRuns) WatchText("Choose run", 9, Secondary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                .clickable(onClick = onRuns, role = Role.Button))
    }
}

@Composable
private fun WatchRunPickerScreen(catalog: WatchRunCatalog?, selected: WatchRunPlan?, routeId: String?,
    error: String?, onSelectPlan: (WatchRunPlan) -> Unit, onSelectRoute: (String?) -> Unit,
    onStart: () -> Unit, onBack: () -> Unit, onSync: () -> Unit) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink)) {
        Image(painterResource(if (catalog?.plans.isNullOrEmpty()) R.drawable.watch_route_twilight
            else R.drawable.watch_athlete_run), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .38f), Ink.copy(alpha = .35f), Ink.copy(alpha = .88f)))))
        val plans = catalog?.plans.orEmpty()
        val active = selected?.takeIf { it in plans } ?: plans.firstOrNull()
        if (active == null) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneStatusIcon(false)
                WatchText("No runs cached", 17, Ivory, FontWeight.Bold)
                WatchText("Sync with phone", 11, Secondary)
            }
            CompactButton("Sync", Blue, onSync,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 35.dp, vertical = 24.dp)
                    .fillMaxWidth())
        } else {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                WatchText("CHOOSE RUN", 10, Gold, FontWeight.Bold, letterSpacing = 1.8f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (plans.size > 1) CompactButton("‹", Raised, {
                        onSelectPlan(plans[(plans.indexOf(active) - 1 + plans.size) % plans.size])
                    }, Modifier.width(25.dp))
                    WatchText(active.routineName, 17, Ivory, FontWeight.Bold, maxLines = 2,
                        modifier = Modifier.weight(1f))
                    if (plans.size > 1) CompactButton("›", Raised, {
                        onSelectPlan(plans[(plans.indexOf(active) + 1) % plans.size])
                    }, Modifier.width(25.dp))
                }
                val routeName = when (routeId) {
                    null -> "Free run"
                    WATCH_TREADMILL_ROUTE_ID -> "Treadmill"
                    else -> catalog?.routes?.firstOrNull { it.id == routeId }?.name ?: "Saved route"
                }
                val savedRoutes = catalog?.routes.orEmpty()
                WatchText(if (savedRoutes.isEmpty()) routeName else "$routeName ›", 10, Ivory,
                    letterSpacing = .5f,
                    modifier = Modifier.clickable(enabled = savedRoutes.isNotEmpty()) {
                        val current = savedRoutes.indexOfFirst { it.id == routeId }
                        onSelectRoute(savedRoutes[(current + 1) % savedRoutes.size].id)
                    }.semantics { contentDescription = "Choose saved route" })
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactButton("Free run", if (routeId == null) Blue else Raised,
                        { onSelectRoute(null) }, Modifier.weight(1f))
                    CompactButton("Treadmill", if (routeId == WATCH_TREADMILL_ROUTE_ID) Blue else Raised,
                        { onSelectRoute(WATCH_TREADMILL_ROUTE_ID) }, Modifier.weight(1f))
                }
                if (error != null) WatchText(error, 10, Amber, maxLines = 2)
            }
            CompactButton("Start run", Blue, onStart,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 31.dp, vertical = 15.dp)
                    .fillMaxWidth())
        }
        CompactButton("‹", Raised, onBack,
            Modifier.align(Alignment.TopStart).padding(start = 19.dp, top = 20.dp).width(25.dp), "Back")
    }
}

@Composable
private fun WatchActiveRunScreen(capture: WatchRunCapture, now: Long, onPause: () -> Unit,
    onResume: () -> Unit, onFinish: () -> Unit, music: @Composable () -> Unit = { WatchSpotifyRow() }) {
    val elapsed = capture.elapsedMillis(now) / 1_000
    val position = capture.routePosition()
    val cue = position?.let { (point, _) -> capture.route?.cues?.firstOrNull { it.pointIndex >= point } }
    val offRoute = position?.second?.takeIf { it > 50 }
    val atTurn = cue != null && position.first == cue.pointIndex
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink)) {
        val artwork = when {
            capture.plan.preferredRouteId == WATCH_TREADMILL_ROUTE_ID -> R.drawable.watch_treadmill
            capture.route == null -> R.drawable.watch_free_run
            else -> R.drawable.watch_route_twilight
        }
        Image(painterResource(artwork), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .34f), Ink.copy(alpha = .08f), Ink.copy(alpha = .42f)))))
        if (offRoute == null && cue == null) {
            RunOverviewContent(capture, elapsed, onPause, onResume, onFinish)
        } else Box(Modifier.fillMaxSize()) {
            capture.route?.let { route ->
                RunRouteMap(route, position.first, capture.samples.lastOrNull()?.point, offRoute != null)
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .padding(horizontal = 27.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WatchText("%02d:%02d".format(elapsed / 60, elapsed % 60), 11, Ivory,
                    FontWeight.Bold, letterSpacing = 1.2f)
                Box(Modifier.border(1.dp, Blue.copy(alpha = .9f), RoundedCornerShape(15.dp))
                    .background(Ink.copy(alpha = .64f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp)) {
                    BasicText(if (capture.isRunning) "RUN" else "PAUSED",
                        style = TextStyle(color = Ivory, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp))
                }
                Spacer(Modifier.height(4.dp))
                if (offRoute != null) {
                    WatchText("⚠", 30, Amber)
                    WatchText("OFF ROUTE", 15, Ivory, FontWeight.Bold, letterSpacing = 2f)
                    WatchText("$offRoute m", 12, Ivory)
                } else if (cue != null) {
                    TurnArrow(cue.kind, atTurn)
                    WatchText(if (atTurn) "TURN NOW" else "NEXT TURN", 7, Ivory,
                        FontWeight.Bold, letterSpacing = 1.7f)
                    WatchText(when (cue.kind) {
                        "LEFT" -> if (atTurn) "TURN LEFT" else "Turn left"
                        "RIGHT" -> if (atTurn) "TURN RIGHT" else "Turn right"
                        "U_TURN" -> "U-turn"
                        else -> cue.instruction
                    }, 16, Ivory, FontWeight.Bold, maxLines = 2)
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RunControlButton(if (capture.isRunning) "PAUSE" else "RESUME",
                    capture.isRunning, if (capture.isRunning) onPause else onResume)
                if (!capture.isRunning) RunControlButton("FINISH", false, onFinish)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Blue.copy(alpha = .52f), radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(.75.dp.toPx()))
        }
    }
}

@Composable
private fun BoxScope.RunOverviewContent(capture: WatchRunCapture, elapsed: Long,
    onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit) {
    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 17.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.border(1.dp, if (capture.isRunning) Blue else Amber, RoundedCornerShape(15.dp))
            .background(Ink.copy(alpha = .72f), RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp)) {
            BasicText(if (capture.isRunning) "RUN" else "Ⅱ PAUSED",
                style = TextStyle(color = if (capture.isRunning) Blue else Amber,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
        }
        WatchText("%02d:%02d".format(elapsed / 60, elapsed % 60), 34, Ivory,
            FontWeight.Bold, serifFamily())
    }
    val route = capture.route
    if (route != null || capture.samples.size > 1) {
        RunOverviewMap(route?.points ?: capture.samples.map { it.point },
            capture.samples.lastOrNull()?.point,
            Modifier.align(Alignment.Center).padding(top = 24.dp).fillMaxWidth(.62f).height(46.dp))
    } else {
        WatchText(if (capture.plan.preferredRouteId == WATCH_TREADMILL_ROUTE_ID) "TREADMILL"
            else if (capture.samples.isEmpty()) "Waiting for GPS" else "RUNNING",
            11, Ivory, FontWeight.Bold, modifier = Modifier.align(Alignment.Center)
                .padding(horizontal = 42.dp, vertical = 12.dp))
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .padding(horizontal = 35.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (capture.plan.preferredRouteId != WATCH_TREADMILL_ROUTE_ID && capture.samples.isNotEmpty())
            WatchText("${"%.2f".format(capture.distanceMeters / 1000.0)} km", 15, Ivory)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CompactButton(if (capture.isRunning) "Ⅱ PAUSE" else "▶ RESUME", Blue,
                if (capture.isRunning) onPause else onResume, Modifier.weight(1.8f))
            CompactButton("FINISH", Raised, onFinish, Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun RunOverviewMap(points: List<WatchRunPoint>, current: WatchRunPoint?, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minLon = points.minOf { it.longitudeE7 }
        val maxLon = points.maxOf { it.longitudeE7 }
        val minLat = points.minOf { it.latitudeE7 }
        val maxLat = points.maxOf { it.latitudeE7 }
        val lonSpan = (maxLon - minLon).coerceAtLeast(1).toFloat()
        val latSpan = (maxLat - minLat).coerceAtLeast(1).toFloat()
        fun map(point: WatchRunPoint) = androidx.compose.ui.geometry.Offset(
            size.width * (.1f + .8f * (point.longitudeE7 - minLon) / lonSpan),
            size.height * (.9f - .8f * (point.latitudeE7 - minLat) / latSpan))
        val mapped = points.map(::map)
        val path = Path().apply {
            moveTo(mapped.first().x, mapped.first().y)
            for (index in 1 until mapped.lastIndex) {
                val corner = mapped[index]
                val next = mapped[index + 1]
                quadraticTo(corner.x, corner.y, (corner.x + next.x) / 2f, (corner.y + next.y) / 2f)
            }
            quadraticTo(mapped.last().x, mapped.last().y, mapped.last().x, mapped.last().y)
        }
        drawPath(path, Blue.copy(alpha = .2f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, Blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        current?.let {
            val marker = map(it)
            drawCircle(Blue.copy(alpha = .35f), 7.dp.toPx(), marker)
            drawCircle(Ivory, 3.dp.toPx(), marker)
        }
    }
}

@Composable
private fun RunControlButton(label: String, pause: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(25.dp)
        .border(BorderStroke(1.dp, Blue.copy(alpha = .48f)), RoundedCornerShape(18.dp))
        .background(Ink.copy(alpha = .78f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick, role = Role.Button)
        .semantics { contentDescription = label; role = Role.Button }
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(9.dp)) {
            val stroke = 2.dp.toPx()
            if (pause) {
                drawLine(Ivory, androidx.compose.ui.geometry.Offset(size.width * .3f, size.height * .1f),
                    androidx.compose.ui.geometry.Offset(size.width * .3f, size.height * .9f), stroke, StrokeCap.Round)
                drawLine(Ivory, androidx.compose.ui.geometry.Offset(size.width * .7f, size.height * .1f),
                    androidx.compose.ui.geometry.Offset(size.width * .7f, size.height * .9f), stroke, StrokeCap.Round)
            } else {
                val icon = Path().apply {
                    moveTo(size.width * .2f, size.height * .1f)
                    lineTo(size.width * .87f, size.height * .5f)
                    lineTo(size.width * .2f, size.height * .9f)
                    close()
                }
                drawPath(icon, Ivory)
            }
        }
        BasicText(label, style = TextStyle(color = Ivory, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp))
    }
}

@Composable
private fun TurnArrow(kind: String, atTurn: Boolean = false) {
    Canvas(Modifier.size(51.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        when (kind) {
            "LEFT", "RIGHT" -> {
                val mirror = kind == "RIGHT"
                fun x(value: Float) = w * (if (mirror) 1f - value else value)
                path.moveTo(x(.10f), h * .40f)
                path.lineTo(x(.43f), h * .12f)
                path.lineTo(x(.43f), h * .30f)
                path.lineTo(x(.64f), h * .30f)
                path.quadraticTo(x(.88f), h * .30f, x(.88f), h * .55f)
                path.lineTo(x(.88f), h * .91f)
                path.lineTo(x(.64f), h * .91f)
                path.lineTo(x(.64f), h * .57f)
                path.quadraticTo(x(.64f), h * .53f, x(.59f), h * .53f)
                path.lineTo(x(.43f), h * .53f)
                path.lineTo(x(.43f), h * .71f)
                path.close()
                drawPath(path, if (atTurn) Amber else Ivory)
            }
            "U_TURN" -> {
                path.moveTo(w * .72f, h * .88f)
                path.lineTo(w * .72f, h * .37f)
                path.cubicTo(w * .72f, h * .05f, w * .28f, h * .05f, w * .28f, h * .37f)
                path.lineTo(w * .28f, h * .7f)
                drawPath(path, Amber, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                drawLine(Amber, androidx.compose.ui.geometry.Offset(w * .28f, h * .7f),
                    androidx.compose.ui.geometry.Offset(w * .13f, h * .55f), 6.dp.toPx(), StrokeCap.Round)
                drawLine(Amber, androidx.compose.ui.geometry.Offset(w * .28f, h * .7f),
                    androidx.compose.ui.geometry.Offset(w * .43f, h * .55f), 6.dp.toPx(), StrokeCap.Round)
            }
            else -> drawLine(Amber, androidx.compose.ui.geometry.Offset(w * .5f, h * .85f),
                androidx.compose.ui.geometry.Offset(w * .5f, h * .2f), 6.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun RunRouteMap(route: WatchRunRoute, pointIndex: Int?, current: WatchRunPoint?, offRoute: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        if (route.points.size < 2) return@Canvas
        val center = (pointIndex ?: 0).coerceIn(route.points.indices)
        val slice = route.points.subList(center, (center + 16).coerceAtMost(route.points.size))
        val anchor = route.points[center]
        val previousRoutePoint = route.points.getOrNull(center - 1)
        val headingX = if (previousRoutePoint != null)
            (anchor.longitudeE7 - previousRoutePoint.longitudeE7).toFloat()
            else (route.points[center + 1].longitudeE7 - anchor.longitudeE7).toFloat()
        val headingY = if (previousRoutePoint != null)
            (anchor.latitudeE7 - previousRoutePoint.latitudeE7).toFloat()
            else (route.points[center + 1].latitudeE7 - anchor.latitudeE7).toFloat()
        val headingLength = kotlin.math.sqrt(headingX * headingX + headingY * headingY).coerceAtLeast(1f)
        val east = headingX / headingLength
        val north = headingY / headingLength
        fun forward(point: WatchRunPoint): Float =
            (point.longitudeE7 - anchor.longitudeE7) * east +
                (point.latitudeE7 - anchor.latitudeE7) * north
        fun side(point: WatchRunPoint): Float =
            (point.longitudeE7 - anchor.longitudeE7) * north -
                (point.latitudeE7 - anchor.latitudeE7) * east
        val forwardRange = maxOf(slice.maxOf { kotlin.math.abs(forward(it)) }, headingLength, 1f)
        val sideRange = maxOf(slice.maxOf { kotlin.math.abs(side(it)) }, headingLength, 1f)
        val currentY = if (offRoute) .81f else .77f
        fun map(point: WatchRunPoint) = androidx.compose.ui.geometry.Offset(
            size.width * (.52f + .32f * side(point) / sideRange),
            size.height * (currentY - .21f * forward(point) / forwardRange -
                .04f * kotlin.math.abs(side(point)) / sideRange).coerceIn(.16f, .84f))
        val path = Path()
        val canvasWidth = size.width
        val canvasHeight = size.height
        val mapped = buildList<androidx.compose.ui.geometry.Offset> {
            slice.map(::map).forEachIndexed { index, point ->
                if (index > 1) {
                    val previous = last()
                    val deltaX = point.x - previous.x
                    val deltaY = point.y - previous.y
                    if (kotlin.math.abs(deltaX) > canvasWidth * .16f &&
                        kotlin.math.abs(deltaY) > canvasHeight * .06f) {
                        add(androidx.compose.ui.geometry.Offset(previous.x + deltaX * .55f,
                            previous.y + deltaY * .08f))
                    }
                }
                add(point)
            }
        }
        path.moveTo(size.width * .50f, size.height * 1.18f)
        path.cubicTo(size.width * .50f, size.height * .96f,
            mapped.first().x, size.height * (currentY + .04f),
            mapped.first().x, mapped.first().y)
        for (index in 1 until mapped.lastIndex) {
            val previous = mapped[index - 1]
            val corner = mapped[index]
            val next = mapped[index + 1]
            path.lineTo(corner.x + (previous.x - corner.x) * .18f,
                corner.y + (previous.y - corner.y) * .18f)
            path.quadraticTo(corner.x, corner.y,
                corner.x + (next.x - corner.x) * .18f,
                corner.y + (next.y - corner.y) * .18f)
        }
        path.lineTo(mapped.last().x, mapped.last().y)
        val farPoint = mapped.last()
        val beforeFar = mapped.getOrNull(mapped.lastIndex - 1)
        val outward = if (beforeFar == null) 0f else farPoint.x - beforeFar.x
        val outwardY = if (beforeFar == null) 0f else farPoint.y - beforeFar.y
        // Carry the road past the circular edge. The bend follows the real route,
        // then the distant section climbs toward the horizon before disappearing.
        val horizon = androidx.compose.ui.geometry.Offset(
            (farPoint.x + outward * 1.8f).coerceIn(-size.width * .18f, size.width * 1.18f),
            if (outwardY > size.height * .02f) size.height * 1.12f else -size.height * .12f)
        val directionX = horizon.x - farPoint.x
        val directionY = horizon.y - farPoint.y
        val originX = farPoint.x - size.width / 2f
        val originY = farPoint.y - size.height / 2f
        val radius = size.minDimension / 2f
        val a = directionX * directionX + directionY * directionY
        val b = 2f * (originX * directionX + originY * directionY)
        val c = originX * originX + originY * originY - radius * radius
        val edge = ((-b + kotlin.math.sqrt(b * b - 4f * a * c)) / (2f * a))
            .coerceAtLeast(0f)
        val beyond = androidx.compose.ui.geometry.Offset(
            farPoint.x + directionX * edge * 1.12f,
            farPoint.y + directionY * edge * 1.12f)
        val fade = Path().apply {
            moveTo(farPoint.x, farPoint.y)
            val curve = if (outwardY > size.height * .02f) -.11f else .11f
            cubicTo(farPoint.x + outward * .55f, farPoint.y - size.height * curve,
                beyond.x, beyond.y + size.height * curve, beyond.x, beyond.y)
        }
        val fadeStart = beforeFar ?: farPoint
        val glow = Brush.linearGradient(
            listOf(Blue.copy(alpha = .28f), Blue.copy(alpha = 0f)), fadeStart, beyond)
        val line = Brush.linearGradient(listOf(Blue, Blue.copy(alpha = 0f)), fadeStart, beyond)
        drawPath(path, glow, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(fade, glow, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(fade, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        if (pointIndex != null) {
            val nearest = map(anchor)
            if (offRoute && current != null) {
                val projected = map(current)
                val actual = androidx.compose.ui.geometry.Offset(
                    projected.x.coerceIn(size.width * .22f, size.width * .78f),
                    projected.y.coerceIn(size.height * .68f, size.height * .86f))
                drawLine(Ivory.copy(alpha = .75f), nearest, actual, 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
                drawCircle(Amber.copy(alpha = .26f), 8.dp.toPx(), actual)
                drawCircle(Ivory, 3.5.dp.toPx(), actual)
            } else {
                drawCircle(Blue.copy(alpha = .30f), 8.dp.toPx(), nearest)
                drawCircle(Ivory, 3.5.dp.toPx(), nearest)
            }
        }
    }
}

@Composable
private fun CompactButton(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier,
    description: String = label) {
    Box(modifier.height(34.dp).clip(RoundedCornerShape(18.dp)).background(color)
        .clickable(onClick = onClick, role = Role.Button).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        WatchText(label, 12, Ivory, FontWeight.Bold)
    }
}

@Composable
private fun TodayScreen(snapshot: WatchSnapshot, onSync: () -> Unit, onStart: () -> Unit,
    onChooseRun: () -> Unit, savedRuns: Int, previewPage: Int = 0,
    catalog: WatchRunCatalog? = null, onStartRun: (WatchRunPlan) -> Unit = { onChooseRun() },
    onSelectWorkout: (String?) -> Unit = { onStart() }) {
    WatchTodayHub(snapshot, catalog, onSync, onSelectWorkout, onStartRun,
        previewPage = if (previewPage in 1..2) 1 else 0,
        previewDetail = when (previewPage) {
            3, 6 -> WatchTodayDetail.STOCKS
            7 -> WatchTodayDetail.GAMES
            else -> null
        }, previewScroll = previewPage == 6)
}
@Composable
private fun BoxScope.TodayOfflinePage(hasWorkout: Boolean, onStart: () -> Unit,
    onSync: () -> Unit) {
    Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 23.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
            WatchText("☁", 32, Ivory)
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Ivory, androidx.compose.ui.geometry.Offset(size.width * .16f, size.height * .15f),
                    androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .86f),
                    2.dp.toPx(), StrokeCap.Round)
            }
        }
        WatchText("TODAY", 10, Ivory, FontWeight.Bold, letterSpacing = 2f)
        WatchText("Phone needed", 17, Ivory, family = serifFamily())
        WatchText("Update phone app, then sync", 9, Secondary, maxLines = 2,
            modifier = Modifier.padding(horizontal = 30.dp))
    }
    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .padding(horizontal = 27.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        CompactButton("Sync", Blue, onSync, Modifier.weight(1f))
        if (hasWorkout) CompactButton("Workout", Raised, onStart, Modifier.weight(1f),
            "Start workout")
    }
}

@Composable
private fun BoxScope.TodayWorkoutPage(briefing: WatchTodayBriefing, status: WatchWorkoutStatus,
    onStart: () -> Unit) {
    if (briefing.workoutCount == 0) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 27.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            WatchText(briefing.temperatureF?.let { "$it°" } ?: "—", 40, Ivory, family = serifFamily())
            WatchText("Your day is open", 18, Ivory, family = serifFamily())
            WatchText("No workout scheduled", 11, Secondary)
        }
        return
    }
    if (status == WatchWorkoutStatus.COMPLETE) {
        Box(Modifier.align(Alignment.TopEnd).padding(end = 35.dp, top = 34.dp)) {
            SuccessMark(58)
        }
        WatchText("Workout complete", 15, Ivory, FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).padding(start = 20.dp, end = 20.dp, top = 52.dp))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.69f).padding(bottom = 33.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            briefing.weekDays.forEach { (_, completed) ->
                Box(Modifier.size(7.dp).background(if (completed > 0) Mint else Raised, CircleShape))
            }
        }
        return
    }
    Column(Modifier.align(Alignment.TopStart).fillMaxWidth(.71f)
        .padding(start = 35.dp, top = 31.dp),
        horizontalAlignment = Alignment.Start) {
        WatchText("WORKOUT", 10, Gold, FontWeight.Bold, letterSpacing = 1.8f,
            textAlign = TextAlign.Start)
        Spacer(Modifier.height(10.dp))
        WatchText(briefing.workoutName ?: "Workout", 20, Ivory, family = serifFamily(),
            maxLines = 2, textAlign = TextAlign.Start)
        WatchText("${briefing.weekCompleted}/${briefing.weekPlanned} this week", 11, Ivory,
            textAlign = TextAlign.Start)
    }
    if (status == WatchWorkoutStatus.AVAILABLE) {
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.64f).padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            briefing.weekDays.forEach { (planned, completed) ->
                Box(Modifier.size(6.dp).background(when {
                    completed > 0 -> Mint
                    planned > 0 -> Color(0xFF635779)
                    else -> Raised
                }, CircleShape))
            }
        }
        CompactButton("Start workout", Blue, onStart,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 29.dp)
                .fillMaxWidth())
    } else {
        WatchText("Open on phone for this workout", 10, Secondary, maxLines = 2,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 34.dp))
    }
}

@Composable
private fun BoxScope.TodayRunPage(savedRuns: Int, onSync: () -> Unit, onChooseRun: () -> Unit) {
    Column(Modifier.align(Alignment.TopStart).fillMaxWidth(.72f)
        .padding(start = 35.dp, top = 34.dp),
        horizontalAlignment = Alignment.Start) {
        WatchText("RUN", 10, Gold, FontWeight.Bold, letterSpacing = 2f,
            textAlign = TextAlign.Start)
        Spacer(Modifier.height(10.dp))
        WatchText("Choose\na run", 21, Ivory, family = serifFamily(), maxLines = 2,
            textAlign = TextAlign.Start)
        WatchText(if (savedRuns == 0) "Sync for saved runs" else
            "$savedRuns saved ${if (savedRuns == 1) "run" else "runs"}",
            11, Ivory, maxLines = 2, textAlign = TextAlign.Start)
    }
    CompactButton(if (savedRuns == 0) "Sync" else "Choose run", Blue,
        if (savedRuns == 0) onSync else onChooseRun,
        Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 29.dp)
            .fillMaxWidth())
}

@Composable
private fun TodayStockPage(stock: WatchTodayStock) {
    WatchText(stock.symbol, 19, Ivory, FontWeight.Bold)
    WatchText(stock.price?.let { String.format(Locale.US, "%,.2f", it) } ?: "Quote unavailable",
        32, Ivory, family = serifFamily())
    stock.changePercent?.let { change ->
        WatchText("${if (change >= 0) "↗" else "↘"}  ${String.format(Locale.US, "%+.2f%%", change)}",
            18, if (change >= 0) Mint else Amber, FontWeight.Bold)
    }
    if (stock.points.size >= 2) StockSparkline(stock.points,
        if ((stock.changePercent ?: 0.0) >= 0) Mint else Amber)
}

@Composable
private fun EmptyTodayContent(stocks: Boolean) {
    Spacer(Modifier.height(7.dp))
    Canvas(Modifier.size(54.dp)) {
        drawCircle(Blue.copy(alpha = .48f), radius = size.minDimension * .46f,
            style = Stroke(1.dp.toPx()))
        if (stocks) {
            val bars = listOf(.30f, .43f, .35f, .64f)
            bars.forEachIndexed { index, height ->
                val x = size.width * (.28f + index * .14f)
                drawLine(Ivory, androidx.compose.ui.geometry.Offset(x, size.height * .73f),
                    androidx.compose.ui.geometry.Offset(x, size.height * (1f - height)),
                    3.dp.toPx(), StrokeCap.Round)
            }
            val chart = Path().apply {
                moveTo(size.width * .22f, size.height * .54f)
                lineTo(size.width * .40f, size.height * .43f)
                lineTo(size.width * .55f, size.height * .49f)
                lineTo(size.width * .76f, size.height * .25f)
            }
            drawPath(chart, Ivory, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        } else {
            val left = Path().apply {
                moveTo(size.width * .12f, size.height * .26f)
                lineTo(size.width * .47f, size.height * .50f)
                lineTo(size.width * .12f, size.height * .74f)
                close()
            }
            val right = Path().apply {
                moveTo(size.width * .88f, size.height * .26f)
                lineTo(size.width * .53f, size.height * .50f)
                lineTo(size.width * .88f, size.height * .74f)
                close()
            }
            drawPath(left, Ivory, style = Stroke(2.dp.toPx()))
            drawPath(right, Ivory, style = Stroke(2.dp.toPx()))
        }
    }
    WatchText(if (stocks) "No stocks followed" else "No teams followed",
        15, Ivory, FontWeight.Bold)
    WatchText(if (stocks) "Add stocks on phone" else "Add teams on phone", 10, Secondary)
}

@Composable
private fun TeamMatchVisual(team: String, opponent: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        TeamPennant(team.take(1), Color(0xFF123B2C), Amber, false)
        WatchText("VS", 12, Ivory, FontWeight.Bold, modifier = Modifier.width(24.dp))
        TeamPennant(if (opponent?.contains("Bears", ignoreCase = true) == true) "C"
            else opponent?.take(1) ?: "?", Color(0xFF17284D), Amber, true)
    }
}

@Composable
private fun TeamPennant(letter: String, fill: Color, outline: Color, reverse: Boolean) {
    Box(Modifier.width(53.dp).height(43.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val triangle = Path().apply {
                if (reverse) {
                    moveTo(size.width, size.height * .08f)
                    lineTo(size.width * .08f, size.height * .5f)
                    lineTo(size.width, size.height * .92f)
                } else {
                    moveTo(0f, size.height * .08f)
                    lineTo(size.width * .92f, size.height * .5f)
                    lineTo(0f, size.height * .92f)
                }
                close()
            }
            drawPath(triangle, fill)
            drawPath(triangle, outline, style = Stroke(1.5.dp.toPx()))
        }
        WatchText(letter, 22, Ivory, FontWeight.Bold)
    }
}

@Composable
private fun AtmospherePage(resource: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
        Image(painterResource(resource), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .63f), Ink.copy(alpha = .27f), Ink.copy(alpha = .69f), Ink.copy(alpha = .93f)))))
        Column(Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
    }
}

@Composable
private fun ScenicDivider() {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val left = Path().apply {
            moveTo(size.width * .08f, size.height * .9f)
            quadraticTo(size.width * .28f, size.height * .08f, size.width * .5f, size.height * .12f)
        }
        val right = Path().apply {
            moveTo(size.width * .5f, size.height * .12f)
            quadraticTo(size.width * .72f, size.height * .08f, size.width * .92f, size.height * .9f)
        }
        drawPath(left, Amber, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(right, Blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Ivory, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .12f))
    }
}

@Composable
private fun StockSparkline(points: List<Double>, color: Color) {
    if (points.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val low = points.minOrNull() ?: return@Canvas
        val high = points.maxOrNull() ?: return@Canvas
        val span = (high - low).coerceAtLeast(.001)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1)
            val y = size.height * (.85f - .7f * ((value - low) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .34f),
            color.copy(alpha = 0f))))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width,
            size.height * (.85f - .7f * ((points.last() - low) / span).toFloat())))
    }
}

@Composable
private fun ExerciseScreen(
    snapshot: WatchSnapshot,
    exercise: WatchExercise,
    onStartTimer: (() -> Unit)?,
    onComplete: () -> Unit,
) {
    val totalSets = snapshot.exercises.sumOf { it.setCount }
    val completedSets = snapshot.exercises.sumOf { it.completedSets }
    RoundProgress(if (totalSets == 0) 0f else completedSets.toFloat() / totalSets) {
        if (exercise.artworkId == "dead_hang") {
            Box(Modifier.fillMaxSize().clip(CircleShape)) {
                Image(painterResource(R.drawable.watch_exercise_dead_hang), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                    Ink.copy(alpha = .15f), Ink.copy(alpha = .18f), Ink.copy(alpha = .66f)))))
                Column(Modifier.align(Alignment.CenterEnd).fillMaxWidth(.51f)
                    .padding(end = 18.dp, bottom = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    WatchText(exercise.name, 17, Ivory, FontWeight.Bold, maxLines = 2,
                        modifier = Modifier.semantics { heading() })
                    WatchText("Set ${exercise.completedSets + 1} of ${exercise.setCount}", 10, Ivory)
                    WatchText((if (onStartTimer == null) "" else "◷ ") + exercise.targetLabel(),
                        17, Ivory, FontWeight.Bold,
                        modifier = if (onStartTimer == null) Modifier else Modifier
                            .clickable(onClick = onStartTimer, role = Role.Button)
                            .semantics { contentDescription = "Start timer" })
                }
                CompactButton("Complete set", Blue, onComplete,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 27.dp, vertical = 22.dp)
                        .fillMaxWidth())
            }
        } else AtmospherePage(artworkResource(exercise.artworkId)) {
            WatchText("${snapshot.exercises.indexOf(exercise) + 1} OF ${snapshot.exercises.size}", 10, Gold, FontWeight.Bold)
            WatchText(exercise.name, 18, Ivory, FontWeight.Normal, serifFamily(), maxLines = 2,
                modifier = Modifier.semantics { heading() })
            WatchText("Set ${exercise.completedSets + 1} of ${exercise.setCount}", 10, Secondary)
            WatchText(exercise.targetLabel(), 16, Ivory, FontWeight.Bold)
            if (onStartTimer != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CompactButton("◷", Raised, onStartTimer, Modifier.width(40.dp), "Start timer")
                    CompactButton("Complete set", Blue, onComplete, Modifier.weight(1f))
                }
            } else {
                CompactButton("Complete set", Blue, onComplete, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TimerScreen(
    exercise: WatchExercise,
    timer: LocalTimer,
    now: Long,
    hapticsEnabled: Boolean,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
) {
    val phase = when {
        now < timer.readyDeadlineMillis -> TimerPhase.READY
        now < timer.activeDeadlineMillis -> TimerPhase.RUNNING
        else -> TimerPhase.FINISHED
    }
    val remaining = when (phase) {
        TimerPhase.READY -> secondsRemaining(timer.readyDeadlineMillis, now)
        TimerPhase.RUNNING -> secondsRemaining(timer.activeDeadlineMillis, now)
        TimerPhase.FINISHED -> 0
    }
    val context = LocalContext.current
    LaunchedEffect(phase, remaining) {
        if (hapticsEnabled && phase == TimerPhase.READY && remaining in 1..3) vibrate(context, long = false)
        if (hapticsEnabled && phase == TimerPhase.RUNNING && remaining == exercise.durationSeconds) vibrate(context, long = true)
        if (hapticsEnabled && phase == TimerPhase.FINISHED) vibrate(context, long = true)
    }
    val configured = exercise.durationSeconds ?: 1
    val fraction = when (phase) {
        TimerPhase.READY -> (10 - remaining).coerceAtLeast(0) / 10f
        TimerPhase.RUNNING -> (configured - remaining).coerceAtLeast(0) / configured.toFloat()
        TimerPhase.FINISHED -> 1f
    }
    RoundProgress(fraction, if (phase == TimerPhase.READY) Amber else Blue) {
        if (phase == TimerPhase.FINISHED) {
            Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 29.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                WatchText("00:00", 43, Ivory, family = serifFamily(),
                    modifier = Modifier.semantics { contentDescription = "Timer complete" })
                WatchText("TIMER COMPLETE", 10, Ivory, FontWeight.Bold, letterSpacing = 1.6f)
                Spacer(Modifier.height(9.dp))
                CompactButton("Complete set", Blue, onComplete, Modifier.fillMaxWidth())
            }
            return@RoundProgress
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(if (phase == TimerPhase.READY) "GET READY" else "HOLD")
            WatchText(
                if (phase == TimerPhase.READY) remaining.toString() else formatTimer(remaining),
                if (phase == TimerPhase.READY) 52 else 48,
                Ivory,
                FontWeight.Bold,
                serifFamily(),
                modifier = Modifier.semantics { contentDescription = "$remaining seconds remaining" },
            )
            WatchText(
                if (phase == TimerPhase.READY) "Timer starts automatically" else "Set ${exercise.completedSets + 1} of ${exercise.setCount}",
                12, Secondary,
            )
            Spacer(Modifier.height(8.dp))
            if (phase == TimerPhase.RUNNING) PrimaryButton("Complete set", onComplete)
            else SecondaryButton("Cancel", onCancel)
        }
    }
}

@Composable
private fun SetCompleteScreen(progress: Pair<Int, Int>, onNext: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            SuccessMark(54)
            Spacer(Modifier.height(6.dp))
            WatchText("SET COMPLETE", 11, Gold, FontWeight.Bold, letterSpacing = 1.7f)
            WatchText("${progress.first} of ${progress.second}", 15, Ivory)
            WatchText("Next set", 11, Secondary)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            .size(30.dp).background(Blue, CircleShape)
            .clickable(onClick = onNext, role = Role.Button)
            .semantics { contentDescription = "Next set" }, contentAlignment = Alignment.Center) {
            WatchText("→", 18, Ivory, FontWeight.Bold)
        }
    }
}

@Composable
private fun CompletionScreen(snapshot: WatchSnapshot, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            SuccessMark(60)
            Spacer(Modifier.height(6.dp))
            WatchText("SESSION\nCOMPLETE", 13, Ivory, FontWeight.Bold, maxLines = 2,
                letterSpacing = 2f)
            WatchText("${snapshot.exercises.size} of ${snapshot.exercises.size} exercises", 10, Secondary)
        }
        CompactButton("Done", Blue, onDone, Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 36.dp, vertical = 10.dp).fillMaxWidth())
    }
}

@Composable
private fun SuccessMark(diameter: Int) {
    Box(Modifier.size(diameter.dp).border(2.dp, Mint, CircleShape)
        .background(Mint.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
        WatchText("✓", diameter * 2 / 3, Mint, FontWeight.Bold)
    }
}

@Composable
private fun MessageScreen(eyebrow: String, message: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(bottom = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            PhoneStatusIcon(true)
            Spacer(Modifier.height(6.dp))
            WatchText(eyebrow, 11, Ivory, FontWeight.Bold, letterSpacing = 1.8f)
            WatchText(message, 10, Secondary, maxLines = 2,
                modifier = Modifier.padding(horizontal = 28.dp))
        }
        CompactButton(action, Blue, onAction, Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 36.dp, vertical = 12.dp).fillMaxWidth())
    }
}

@Composable
private fun PhoneStatusIcon(warning: Boolean) {
    Canvas(Modifier.size(68.dp)) {
        val accent = if (warning) Amber else Blue
        if (!warning) {
            drawCircle(accent.copy(alpha = .15f), size.minDimension * .45f,
                style = Stroke(1.dp.toPx()))
            drawCircle(accent.copy(alpha = .65f), size.minDimension * .34f,
                style = Stroke(1.dp.toPx()))
            drawCircle(accent, 3.dp.toPx(),
                androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .38f))
        }
        drawRoundRect(Ivory, topLeft = androidx.compose.ui.geometry.Offset(size.width * .34f,
            size.height * .14f), size = androidx.compose.ui.geometry.Size(size.width * .32f,
            size.height * .72f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(2.dp.toPx()))
        if (warning) {
            drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .39f),
                androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .62f),
                4.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, 2.dp.toPx(),
                androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .72f))
        } else drawCircle(accent, 2.dp.toPx(),
            androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .76f))
    }
}

@Composable
private fun RoundProgress(progress: Float, progressColor: Color = Blue, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            drawArc(
                color = Raised,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
            if (progress > 0f) drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    WatchText(text, 12, Gold, FontWeight.Bold, letterSpacing = 2.3f)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) = WatchButton(label, Blue, Ivory, onClick)

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) = WatchButton(label, Raised, Ivory, onClick)

@Composable
private fun WatchButton(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(background)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        WatchText(label, 15, foreground, FontWeight.Bold)
    }
}

@Composable
private fun WatchText(
    text: String,
    size: Int,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = FontFamily.SansSerif,
    maxLines: Int = 1,
    letterSpacing: Float = 0f,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family,
            textAlign = textAlign,
            letterSpacing = letterSpacing.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun serifFamily(): FontFamily = FontFamily(Font(R.font.dm_serif_display_regular))

private fun WatchExercise.targetLabel(): String = buildList {
    weightPounds?.let { add("$it lb") }
    reps?.let { add("$it reps") }
    durationSeconds?.let { add("$it sec") }
}.joinToString(" · ").ifBlank { "$setCount sets" }

private fun artworkResource(artworkId: String?): Int = when (artworkId) {
    "dead_hang" -> R.drawable.watch_exercise_dead_hang
    "farmers_walk" -> R.drawable.exercise_farmers_walk_header
    "grip_hold" -> R.drawable.exercise_grip_hold_header
    "rice_bag" -> R.drawable.exercise_rice_bag_header
    "hangboard" -> R.drawable.exercise_hangboard_header
    "wrist_curl" -> R.drawable.exercise_wrist_curl_header
    "reverse_wrist_curl" -> R.drawable.exercise_reverse_wrist_curl_header
    "finger_extension" -> R.drawable.exercise_finger_extension_header
    "wrist_rotation" -> R.drawable.exercise_wrist_rotation_header
    else -> R.drawable.exercise_generic_header
}

private fun secondsRemaining(deadline: Long, now: Long): Int =
    ceil((deadline - now).coerceAtLeast(0L) / 1_000.0).toInt()

private fun formatTimer(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun vibrate(context: Context, long: Boolean) {
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    val duration = if (long) 120L else 45L
    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
internal fun WatchReviewPreview(screen: String) {
    val exercises = listOf(
        WatchExercise("hang", "Dead Hangs", "Thick adapter", 3, null, 20, null, "dead_hang", if (screen == "Exercise") 0 else 1),
        WatchExercise("walk", "Farmer's Walk", "", 1, null, 30, 20, "farmers_walk", 0),
    )
    val snapshot = WatchSnapshot(
        status = if (screen.startsWith("Today")) WatchWorkoutStatus.AVAILABLE else WatchWorkoutStatus.ACTIVE,
        generation = 1,
        eventRevision = 1,
        sessionId = "preview",
        routineId = "routine",
        routineName = "Forearm & Grip",
        scheduleEntryId = "schedule",
        scheduledDate = "2026-09-25",
        focusedExerciseId = "hang",
        exercises = exercises,
        updatedAtMillis = 1,
        today = WatchTodayBriefing(
            "2026-10-01", "Fitbod workout", 1, 7, 0,
            listOf(0 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0, 1 to 0),
            "Madison", 64, "CLOUDY",
            listOf(
                WatchTodayStock("AAPL", 265.13, 1.02,
                    listOf(262.45, 262.72, 262.61, 263.1, 262.88, 263.45, 263.32, 264.08,
                        263.95, 264.41, 264.17, 264.82, 265.13)),
                WatchTodayStock("NVDA", 188.46, 0.41, listOf(187.9, 188.2, 188.46)),
                WatchTodayStock("MSFT", 517.72, -0.38, listOf(519.0, 518.1, 517.72)),
                WatchTodayStock("TSLA", 436.18, 0.76, listOf(433.0, 434.2, 436.18)),
            ),
            listOf(
                dev.draftingroom5.watch.WatchTodayGame("Green Bay Packers", "Chicago Bears",
                    java.time.Instant.parse("2026-10-04T20:25:00Z").toEpochMilli()),
                dev.draftingroom5.watch.WatchTodayGame("Milwaukee Bucks", "New York Knicks",
                    java.time.Instant.parse("2026-10-05T23:30:00Z").toEpochMilli()),
                dev.draftingroom5.watch.WatchTodayGame("Wisconsin Badgers", "Iowa Hawkeyes",
                    java.time.Instant.parse("2026-10-10T19:30:00Z").toEpochMilli())),
            1, localHour = 18,
        ),
    )
    val todayRuns = WatchRunCatalog(1, listOf(WatchRunPlan("run", "Morning intervals", "run-schedule",
        "2026-10-01", "2026-10-01", listOf(WatchRunInterval("WALK", 300),
            WatchRunInterval("RUN", 1200)), null)), emptyList(), null)
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.watch_dusk), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .72f), Ink.copy(alpha = .37f), Ink.copy(alpha = .86f)))))
        when (screen) {
            "Run picker" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("WALK", 300), WatchRunInterval("RUN", 120)), "route")
                val route = WatchRunRoute("route", "Neighborhood loop", listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_100, -870_000_100)), emptyList())
                WatchRunPickerScreen(WatchRunCatalog(1, listOf(plan), listOf(route), "route"), plan, "route", null,
                    {}, {}, {}, {}, {})
            }
            "Run active" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("WALK", 300), WatchRunInterval("RUN", 120)), "route")
                val route = WatchRunRoute("route", "Neighborhood loop", listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_150, -870_000_140),
                    WatchRunPoint(410_000_260, -870_000_050), WatchRunPoint(410_000_300, -870_000_230),
                    WatchRunPoint(410_000_100, -870_000_330), WatchRunPoint(410_000_000, -870_000_000)),
                    emptyList())
                WatchActiveRunScreen(WatchRunCapture("run", plan, route, 1_000, 0, 1_000, 820,
                    listOf(WatchRunSample(route.points.first(), 2_000, 5))),
                    61_000, {}, {}, {}, music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run waiting GPS", "Run treadmill" -> {
                val treadmill = screen == "Run treadmill"
                val plan = WatchRunPlan("run", if (treadmill) "Treadmill" else "Free run", "schedule",
                    "2026-09-29", "2026-09-29", listOf(WatchRunInterval("RUN", 120)),
                    if (treadmill) WATCH_TREADMILL_ROUTE_ID else null)
                WatchActiveRunScreen(WatchRunCapture("run", plan, null, 1_000, 0, 1_000, 0,
                    emptyList()), 61_000, {}, {}, {}, music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run left turn", "Run right turn", "Run at turn", "Run U-turn", "Run off route" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("WALK", 300), WatchRunInterval("RUN", 120)), "route")
                val kind = when (screen) {
                    "Run U-turn" -> "U_TURN"
                    "Run right turn" -> "RIGHT"
                    else -> "LEFT"
                }
                val points = if (kind == "U_TURN") listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_200, -870_000_000),
                    WatchRunPoint(410_000_260, -870_000_140), WatchRunPoint(410_000_180, -870_000_260),
                    WatchRunPoint(410_000_030, -870_000_280)) else if (kind == "RIGHT") listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_200, -870_000_000),
                    WatchRunPoint(410_000_200, -869_999_750)) else listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_200, -870_000_000),
                    WatchRunPoint(410_000_200, -870_000_250))
                val route = WatchRunRoute("route", "Neighborhood loop", points,
                    listOf(WatchRunCue(1, kind, when (kind) {
                        "RIGHT" -> "Turn right at the path"
                        "U_TURN" -> "Make a U-turn"
                        else -> "Turn left at the path"
                    })))
                val point = when (screen) {
                    "Run off route" -> WatchRunPoint(410_000_100, -869_991_000)
                    "Run at turn" -> route.points[1]
                    else -> route.points.first()
                }
                WatchActiveRunScreen(WatchRunCapture("run", plan, route, 1_000, 0, 1_000, 820,
                    listOf(WatchRunSample(point, 2_000, 5))), 61_000, {}, {}, {},
                    music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run paused" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("RUN", 120)), "route")
                val route = WatchRunRoute("route", "Neighborhood loop", listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_220, -870_000_130),
                    WatchRunPoint(410_000_170, -870_000_340), WatchRunPoint(409_999_900, -870_000_250)),
                    emptyList())
                WatchActiveRunScreen(WatchRunCapture("run", plan, route, 1_000, 60_000, null, 820,
                    listOf(WatchRunSample(route.points.first(), 2_000, 5))), 61_000, {}, {}, {},
                    music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run complete" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("RUN", 120)), "route")
                val route = WatchRunRoute("route", "Neighborhood loop", listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_150, -870_000_140),
                    WatchRunPoint(410_000_260, -870_000_050), WatchRunPoint(410_000_300, -870_000_230),
                    WatchRunPoint(410_000_000, -870_000_000)), emptyList())
                WatchRunCompleteScreen(WatchRunCapture("run", plan, route, 1_000, 60_000, null, 820,
                    listOf(WatchRunSample(route.points.first(), 2_000, 5)), 61_000), 1, {})
            }
            "Today" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 0)
            "Today workout" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 1, catalog = todayRuns)
            "Today workout done" -> TodayScreen(snapshot.copy(status = WatchWorkoutStatus.COMPLETE,
                today = snapshot.today?.copy(weekCompleted = 7, weekDays = List(7) { 1 to 1 })),
                {}, {}, {}, 2, previewPage = 1)
            "Today open day" -> TodayScreen(snapshot.copy(status = WatchWorkoutStatus.NONE,
                today = snapshot.today?.copy(workoutName = null, workoutCount = 0)),
                {}, {}, {}, 2, previewPage = 1)
            "Today run" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 2, catalog = todayRuns)
            "Today stocks" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 3)
            "Today no stocks" -> TodayScreen(snapshot.copy(today = snapshot.today?.copy(stocks = emptyList())),
                {}, {}, {}, 2, previewPage = 3)
            "Today more stocks" -> TodayScreen(snapshot.copy(today = snapshot.today?.copy(stocks =
                snapshot.today.stocks + listOf(WatchTodayStock("GOOG", 243.90, 0.22),
                    WatchTodayStock("AMZN", 221.14, -0.52), WatchTodayStock("META", 713.08, 0.16),
                    WatchTodayStock("^IXIC", 18412.25, -0.34)))), {}, {}, {}, 2, previewPage = 6)
            "Today teams" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 7)
            "Today no teams" -> TodayScreen(snapshot.copy(today = snapshot.today?.copy(games = emptyList())),
                {}, {}, {}, 2, previewPage = 7)
            "Today offline" -> TodayScreen(snapshot.copy(today = null), {}, {}, {}, 0)
            "Today spring", "Today summer", "Today winter", "Today rain", "Today snow", "Today night",
            "Today day", "Today dawn", "Today storm" -> {
                val date = when(screen) {
                    "Today spring" -> "2026-04-01"
                    "Today summer" -> "2026-07-01"
                    "Today winter", "Today snow" -> "2026-01-01"
                    else -> "2026-10-01"
                }
                TodayScreen(snapshot.copy(today=snapshot.today?.copy(date=date,
                    weatherKind=when(screen) { "Today rain" -> "RAIN"; "Today snow" -> "SNOW"; "Today storm" -> "STORM"; else -> "CLEAR" },
                    localHour=when(screen) { "Today night" -> 23; "Today day" -> 12; "Today dawn" -> 6; else -> 18 })), {}, {}, {}, 2)
            }
            "Run no cached" -> WatchRunPickerScreen(WatchRunCatalog(1, emptyList(), emptyList(), null),
                null, null, null, {}, {}, {}, {}, {})
            "Connecting" -> WatchHomeScreen("CONNECTING", "Open DraftingRoom5 on your phone", {}, false, {})
            "Phone needed" -> MessageScreen("PHONE NEEDED", "Workout data unavailable", "Choose run", {})
            "Exercise" -> ExerciseScreen(snapshot, exercises.first(), {}, {})
            "Ready" -> TimerScreen(exercises.first(), LocalTimer("hang", 4_000, 24_000), 1_000, true, {}, {})
            "Running" -> TimerScreen(exercises.first(), LocalTimer("hang", 1_000, 21_000), 7_000, true, {}, {})
            "Timer finished" -> TimerScreen(exercises.first(), LocalTimer("hang", 1_000, 21_000), 22_000, true, {}, {})
            "Set complete" -> SetCompleteScreen(1 to 3, {})
            else -> CompletionScreen(snapshot.copy(status = WatchWorkoutStatus.COMPLETE), {})
        }
    }
}
