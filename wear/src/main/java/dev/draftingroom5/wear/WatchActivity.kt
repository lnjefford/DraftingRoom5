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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
    private val repository by lazy { WatchSyncRepository.get(this) }
    private val runRepository by lazy { WatchRunRepository.get(this) }
    private val runRecorder by lazy { WatchRunRecorder.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftingRoom5Watch(repository, runRepository, runRecorder, onClose = ::finish) }
    }

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
        } else runError = if (selectedRouteId == WATCH_TREADMILL_ROUTE_ID)
            "Location permission keeps the watch timer active in the background. GPS stays off."
            else "Location permission is needed to record a run."
    }
    val timerStore = remember { LocalTimerStore(context) }
    var timer by remember { mutableStateOf(timerStore.read()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var celebration by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showTodayAfterComplete by remember { mutableStateOf(false) }
    val exercise = snapshot?.focusedExercise

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
            runPicker -> WatchRunPickerScreen(
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
            )
            celebration != null -> SetCompleteScreen(checkNotNull(celebration))
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
                repository::startToday, { runPicker = true }, runCatalog?.plans?.size ?: 0)
            snapshot?.status == WatchWorkoutStatus.ERROR -> MessageScreen(
                "PHONE NEEDED", snapshot?.message ?: "Workout data is unavailable.", "Choose run", { runPicker = true },
            )
            snapshot?.status == WatchWorkoutStatus.AVAILABLE -> TodayScreen(checkNotNull(snapshot), repository::refreshToday,
                repository::startToday, { runPicker = true }, runCatalog?.plans?.size ?: 0)
            snapshot?.status == WatchWorkoutStatus.COMPLETE -> if (showTodayAfterComplete)
                TodayScreen(checkNotNull(snapshot), repository::refreshToday, repository::startToday,
                    { runPicker = true }, runCatalog?.plans?.size ?: 0)
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
    Column(Modifier.fillMaxSize().padding(horizontal = 29.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WatchText("RUN COMPLETE", 10, Gold, FontWeight.Bold)
        WatchText("✓", 30, Mint, FontWeight.Bold)
        WatchText("%02d:%02d".format(capture.elapsedBeforeResumeMillis / 60_000,
            (capture.elapsedBeforeResumeMillis / 1_000) % 60), 25, Ivory, FontWeight.Bold, serifFamily())
        if (capture.plan.preferredRouteId != WATCH_TREADMILL_ROUTE_ID)
            WatchText("${"%.2f".format(capture.distanceMeters / 1000.0)} km", 14, Mint)
        WatchText(if (pendingRuns > 0) "Sync pending" else "Saved on phone", 10, Secondary)
        CompactButton("Done", Blue, onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun WatchHomeScreen(eyebrow: String, message: String, onSync: () -> Unit,
    hasRuns: Boolean, onRuns: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(eyebrow)
        WatchText("◎", 42, Blue, FontWeight.Bold)
        WatchText(message, 17, Ivory, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        if (hasRuns) { PrimaryButton("Choose run", onRuns); Spacer(Modifier.height(5.dp)) }
        SecondaryButton("Sync", onSync)
    }
}

@Composable
private fun WatchRunPickerScreen(catalog: WatchRunCatalog?, selected: WatchRunPlan?, routeId: String?,
    error: String?, onSelectPlan: (WatchRunPlan) -> Unit, onSelectRoute: (String?) -> Unit,
    onStart: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink)) {
        Image(painterResource(R.drawable.watch_athlete_run), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = .45f), Ink.copy(alpha = .84f)))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Eyebrow("RUNS")
            val plans = catalog?.plans.orEmpty()
            if (plans.isEmpty()) WatchText("No runs cached · Sync with phone", 12, Ivory, maxLines = 2)
            else {
                plans.take(8).forEach { plan ->
                    CompactButton((if (plan == selected) "✓ " else "") + plan.routineName,
                        Raised, { onSelectPlan(plan) }, Modifier.fillMaxWidth())
                }
                if (selected != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactButton(if (routeId == null) "✓ Free" else "Free", Raised,
                            { onSelectRoute(null) }, Modifier.weight(1f))
                        CompactButton(if (routeId == WATCH_TREADMILL_ROUTE_ID) "✓ Treadmill" else "Treadmill", Raised,
                            { onSelectRoute(WATCH_TREADMILL_ROUTE_ID) }, Modifier.weight(1f))
                    }
                    catalog?.routes?.take(12)?.forEach { route ->
                        CompactButton((if (route.id == routeId) "✓ " else "") + route.name,
                            Raised, { onSelectRoute(route.id) }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (error != null) WatchText(error, 11, Amber, maxLines = 3)
        }
        CompactButton("‹", Raised, onBack,
            Modifier.align(Alignment.TopStart).padding(start = 26.dp, top = 19.dp).width(30.dp))
        if (selected != null) CompactButton("Start run", Blue, onStart,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 15.dp).fillMaxWidth())
    }
}

@Composable
private fun WatchActiveRunScreen(capture: WatchRunCapture, now: Long, onPause: () -> Unit,
    onResume: () -> Unit, onFinish: () -> Unit, music: @Composable () -> Unit = { WatchSpotifyRow() }) {
    val elapsed = capture.elapsedMillis(now) / 1_000
    val position = capture.routePosition()
    val cue = position?.let { (point, _) -> capture.route?.cues?.firstOrNull { it.pointIndex >= point } }
    val offRoute = position?.second?.takeIf { it > 50 }
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink)) {
        Image(painterResource(R.drawable.watch_route_twilight), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Ink.copy(alpha = .34f), Ink.copy(alpha = .08f), Ink.copy(alpha = .42f)))))
        Box(Modifier.fillMaxSize()) {
            capture.route?.let { route ->
                RunRouteMap(route, position?.first, capture.samples.lastOrNull()?.point, offRoute != null)
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
                    TurnArrow(cue.kind)
                    WatchText("NEXT TURN", 7, Ivory, FontWeight.Bold, letterSpacing = 1.7f)
                    WatchText(when (cue.kind) {
                        "LEFT" -> "Turn left"
                        "RIGHT" -> "Turn right"
                        "U_TURN" -> "U-turn"
                        else -> cue.instruction
                    }, 16, Ivory, FontWeight.Bold, maxLines = 2)
                } else {
                    Spacer(Modifier.height(17.dp))
                    WatchText(if (capture.plan.preferredRouteId == WATCH_TREADMILL_ROUTE_ID)
                        "TREADMILL" else "RUNNING", 14, Ivory, FontWeight.Bold, letterSpacing = 1.3f)
                    WatchText(if (capture.samples.isEmpty() && capture.plan.preferredRouteId != WATCH_TREADMILL_ROUTE_ID)
                        "Waiting for GPS" else "${"%.2f".format(capture.distanceMeters / 1000.0)} km",
                        12, Ivory)
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
private fun TurnArrow(kind: String) {
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
                drawPath(path, Ivory)
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
                .04f * kotlin.math.abs(side(point)) / sideRange))
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
        // Carry the road past the circular edge. The bend follows the real route,
        // then the distant section climbs toward the horizon before disappearing.
        val horizon = androidx.compose.ui.geometry.Offset(
            (farPoint.x + outward * 1.8f).coerceIn(-size.width * .18f, size.width * 1.18f),
            -size.height * .12f)
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
            cubicTo(farPoint.x + outward * .55f, farPoint.y - size.height * .11f,
                beyond.x, beyond.y + size.height * .11f, beyond.x, beyond.y)
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

private enum class TodayPageKind { OFFLINE, WEATHER, WORKOUT, RUN, STOCK, TEAM }
private data class TodayPage(val kind: TodayPageKind, val itemIndex: Int = 0)

@Composable
private fun TodayScreen(snapshot: WatchSnapshot, onSync: () -> Unit, onStart: () -> Unit,
    onChooseRun: () -> Unit, savedRuns: Int, previewPage: Int = 0) {
    val today = snapshot.today
    val locale = LocalLocale.current.platformLocale
    val pages = if (today == null) listOf(TodayPage(TodayPageKind.OFFLINE)) else buildList {
        add(TodayPage(TodayPageKind.WEATHER))
        add(TodayPage(TodayPageKind.WORKOUT))
        add(TodayPage(TodayPageKind.RUN))
        if (today.stocks.isEmpty()) add(TodayPage(TodayPageKind.STOCK))
        else today.stocks.indices.forEach { add(TodayPage(TodayPageKind.STOCK, it)) }
        if (today.games.isEmpty()) add(TodayPage(TodayPageKind.TEAM))
        else today.games.indices.forEach { add(TodayPage(TodayPageKind.TEAM, it)) }
    }
    val pager = rememberPagerState(initialPage = previewPage.coerceIn(0, pages.lastIndex)) { pages.size }
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val page = pages[pageIndex]
            val artwork = when (page.kind) {
                TodayPageKind.WORKOUT, TodayPageKind.RUN -> R.drawable.watch_athlete_run
                TodayPageKind.TEAM -> R.drawable.watch_stadium
                else -> R.drawable.watch_dusk
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(painterResource(artwork), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Ink.copy(alpha = .68f), Ink.copy(alpha = .31f), Ink.copy(alpha = .88f)))))
                Column(Modifier.fillMaxWidth().padding(horizontal = 29.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    when (page.kind) {
                        TodayPageKind.OFFLINE -> {
                            Eyebrow("TODAY")
                            WatchText("Phone needed", 19, Ivory, family = serifFamily())
                            WatchText("Update phone app, then sync", 10, Secondary, maxLines = 2)
                            if (snapshot.status == WatchWorkoutStatus.AVAILABLE)
                                CompactButton("Start workout", Blue, onStart, Modifier.fillMaxWidth())
                            CompactButton("Sync", Raised, onSync, Modifier.fillMaxWidth())
                        }
                        TodayPageKind.WEATHER -> {
                            val briefing = checkNotNull(today)
                            Eyebrow("TODAY")
                            val icon = when (briefing.weatherKind) {
                                "CLEAR" -> "☀"; "CLOUDY" -> "☁"; "RAIN" -> "☂"; "SNOW" -> "❄"
                                "STORM" -> "ϟ"; else -> "◌"
                            }
                            WatchText(icon, 22, Ivory)
                            WatchText(briefing.temperatureF?.let { "$it°" } ?: "—", 39, Ivory, family = serifFamily())
                            WatchText("${briefing.location ?: "Current location"} · ${briefing.weatherKind.orEmpty().lowercase()
                                .replaceFirstChar { it.titlecase() }}", 10, Ivory, maxLines = 2)
                            ScenicDivider()
                        }
                        TodayPageKind.WORKOUT -> {
                            val briefing = checkNotNull(today)
                            Eyebrow("WORKOUT")
                            Spacer(Modifier.height(10.dp))
                            WatchText(briefing.workoutName ?: "Your day is open", 18, Ivory,
                                family = serifFamily(), maxLines = 2)
                            WatchText("${briefing.weekCompleted}/${briefing.weekPlanned} this week", 11, Ivory)
                            TodayWeekMarks(briefing.weekDays)
                            when (snapshot.status) {
                                WatchWorkoutStatus.AVAILABLE -> CompactButton("Start workout", Blue, onStart, Modifier.fillMaxWidth())
                                WatchWorkoutStatus.COMPLETE -> WatchText("✓ Workout complete", 13, Mint)
                                else -> WatchText(if (briefing.workoutCount == 0) "No workout scheduled" else
                                    "Open on phone for this workout", 11, Secondary, maxLines = 2)
                            }
                        }
                        TodayPageKind.RUN -> {
                            Eyebrow("RUN")
                            Spacer(Modifier.height(12.dp))
                            WatchText("Choose a run", 20, Ivory, family = serifFamily())
                            WatchText(if (savedRuns == 0) "Sync your phone for saved runs" else
                                "$savedRuns saved ${if (savedRuns == 1) "run" else "runs"}",
                                11, Secondary, maxLines = 2)
                            CompactButton(if (savedRuns == 0) "Sync" else "Choose run", Blue,
                                if (savedRuns == 0) onSync else onChooseRun, Modifier.fillMaxWidth())
                        }
                        TodayPageKind.STOCK -> {
                            val briefing = checkNotNull(today)
                            val stock = briefing.stocks.getOrNull(page.itemIndex)
                            Eyebrow(if (stock == null) "YOUR STOCKS" else
                                "STOCK ${page.itemIndex + 1} OF ${briefing.stocks.size}")
                            if (stock == null) WatchText("No stocks followed", 14, Ivory)
                            else TodayStockPage(stock)
                        }
                        TodayPageKind.TEAM -> {
                            val briefing = checkNotNull(today)
                            val game = briefing.games.getOrNull(page.itemIndex)
                            Eyebrow(if (game == null) "YOUR TEAMS" else
                                "TEAM ${page.itemIndex + 1} OF ${briefing.games.size}")
                            if (game == null) WatchText("No teams followed", 13, Ivory)
                            else {
                                TeamMatchVisual(game.team, game.opponent)
                                WatchText(game.team, 17, Ivory, FontWeight.Bold, maxLines = 2)
                                WatchText(if (game.opponent == null) "No upcoming game" else {
                                    val time = game.startsAtMillis?.let { millis ->
                                        DateTimeFormatter.ofPattern("EEE h:mm a", locale)
                                            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
                                    }
                                    "vs ${game.opponent}" + (time?.let { " · $it" } ?: "")
                                }, 12, Secondary, maxLines = 2)
                            }
                        }
                    }
                }
                if (page.kind == TodayPageKind.WEATHER) CompactButton("↻", Raised, onSync,
                    Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 32.dp).width(34.dp),
                    "Refresh Today")
            }
        }
        if (pages.size > 1) {
            val pageLabel = when (pager.currentPage) {
                0 -> "1 / ${pages.size} · SWIPE ›"
                pages.lastIndex -> "‹ ${pages.size} / ${pages.size}"
                else -> "‹ ${pager.currentPage + 1} / ${pages.size} ›"
            }
            WatchText(pageLabel, 9, Secondary, modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 62.dp, vertical = 8.dp))
        }
    }
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
private fun TeamMatchVisual(team: String, opponent: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(33.dp).clip(CircleShape).background(Color(0xFF123B2C)), contentAlignment = Alignment.Center) {
            WatchText(team.take(1), 19, Ivory, FontWeight.Bold)
        }
        WatchText("VS", 16, Amber, FontWeight.Bold, modifier = Modifier.width(43.dp))
        Box(Modifier.size(33.dp).clip(CircleShape).background(Color(0xFF2C3155)), contentAlignment = Alignment.Center) {
            WatchText(opponent?.take(1) ?: "?", 19, Ivory, FontWeight.Bold)
        }
    }
}

@Composable
private fun TodayWeekMarks(days: List<Pair<Int, Int>>) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        days.forEach { (planned, completed) ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(when {
                completed > 0 -> Mint
                planned > 0 -> Color(0xFF4E436D)
                else -> Raised
            }))
        }
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
    Canvas(Modifier.fillMaxWidth().height(35.dp)) {
        val low = points.minOrNull() ?: return@Canvas
        val high = points.maxOrNull() ?: return@Canvas
        val span = (high - low).coerceAtLeast(.001)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1)
            val y = size.height * (.85f - .7f * ((value - low) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
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
        AtmospherePage(artworkResource(exercise.artworkId)) {
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(when (phase) { TimerPhase.READY -> "GET READY"; TimerPhase.RUNNING -> "HOLD"; TimerPhase.FINISHED -> "TIMER COMPLETE" })
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
            if (phase == TimerPhase.FINISHED) PrimaryButton("Complete set", onComplete)
            else if (phase == TimerPhase.RUNNING) PrimaryButton("Complete set", onComplete)
            else SecondaryButton("Cancel", onCancel)
        }
    }
}

@Composable
private fun SetCompleteScreen(progress: Pair<Int, Int>) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(48.dp).background(Mint.copy(alpha = .19f), CircleShape), contentAlignment = Alignment.Center) {
            WatchText("✓", 33, Mint, FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Eyebrow("SET COMPLETE")
        WatchText("${progress.first} of ${progress.second}", 18, Secondary)
        WatchText("Next set", 13, Ivory, FontWeight.Bold)
    }
}

@Composable
private fun CompletionScreen(snapshot: WatchSnapshot, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(50.dp).background(Mint.copy(alpha = .19f), CircleShape), contentAlignment = Alignment.Center) {
            WatchText("✓", 34, Mint, FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        WatchText("SESSION\nCOMPLETE", 16, Ivory, FontWeight.Normal, serifFamily(), maxLines = 2)
        WatchText("${snapshot.exercises.size} of ${snapshot.exercises.size} exercises", 10, Secondary)
        Spacer(Modifier.height(2.dp))
        CompactButton("Done", Blue, onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MessageScreen(eyebrow: String, message: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow(eyebrow)
        WatchText("⚠", 43, Amber, FontWeight.Bold)
        WatchText(message, 20, Ivory, FontWeight.Normal, serifFamily(), maxLines = 3)
        Spacer(Modifier.height(15.dp))
        PrimaryButton(action, onAction)
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
) {
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family,
            textAlign = TextAlign.Center,
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
                WatchTodayStock("^IXIC", 18412.25, -0.34,
                    listOf(18490.0, 18470.0, 18486.0, 18433.0, 18412.25)),
                WatchTodayStock("MSFT", 428.56, 0.71,
                    listOf(425.0, 425.7, 426.3, 427.8, 428.56)),
                WatchTodayStock("NVDA", 137.90, 2.10,
                    listOf(134.2, 134.9, 135.1, 136.8, 137.90)),
            ),
            listOf(dev.draftingroom5.watch.WatchTodayGame("Green Bay Packers", "Bears", 1_759_500_000_000)),
            1,
        ),
    )
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
                    {}, {}, {}, {})
            }
            "Run active" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("WALK", 300), WatchRunInterval("RUN", 120)), null)
                WatchActiveRunScreen(WatchRunCapture("run", plan, null, 1_000, 0, 1_000, 820,
                    listOf(WatchRunSample(WatchRunPoint(410_000_000, -870_000_000), 2_000, 5))),
                    61_000, {}, {}, {}, music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run left turn", "Run off route" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("WALK", 300), WatchRunInterval("RUN", 120)), "route")
                val route = WatchRunRoute("route", "Neighborhood loop", listOf(
                    WatchRunPoint(410_000_000, -870_000_000), WatchRunPoint(410_000_200, -870_000_000),
                    WatchRunPoint(410_000_200, -870_000_250)),
                    listOf(WatchRunCue(1, "LEFT", "Turn left at the path")))
                val point = if (screen == "Run off route") WatchRunPoint(410_000_100, -869_991_000)
                    else route.points.first()
                WatchActiveRunScreen(WatchRunCapture("run", plan, route, 1_000, 0, 1_000, 820,
                    listOf(WatchRunSample(point, 2_000, 5))), 61_000, {}, {}, {},
                    music = { WatchSpotifyStatus("Nothing playing", {}) })
            }
            "Run complete" -> {
                val plan = WatchRunPlan("run", "Morning intervals", "schedule", "2026-09-29", "2026-09-29",
                    listOf(WatchRunInterval("RUN", 120)), null)
                WatchRunCompleteScreen(WatchRunCapture("run", plan, null, 1_000, 60_000, null, 820,
                    emptyList(), 61_000), 1, {})
            }
            "Today" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 0)
            "Today workout" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 1)
            "Today run" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 2)
            "Today stocks" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 3)
            "Today more stocks" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 6)
            "Today teams" -> TodayScreen(snapshot, {}, {}, {}, 2, previewPage = 7)
            "Today offline" -> TodayScreen(snapshot.copy(today = null), {}, {}, {}, 0)
            "Exercise" -> ExerciseScreen(snapshot, exercises.first(), {}, {})
            "Ready" -> TimerScreen(exercises.first(), LocalTimer("hang", 4_000, 24_000), 1_000, true, {}, {})
            "Running" -> TimerScreen(exercises.first(), LocalTimer("hang", 1_000, 21_000), 7_000, true, {}, {})
            "Set complete" -> SetCompleteScreen(1 to 3)
            else -> CompletionScreen(snapshot.copy(status = WatchWorkoutStatus.COMPLETE), {})
        }
    }
}
