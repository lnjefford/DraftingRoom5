package dev.draftingroom5.wear

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import dev.draftingroom5.watch.WatchWorkoutStatus
import kotlinx.coroutines.delay
import kotlin.math.ceil

class WatchActivity : ComponentActivity() {
    private val repository by lazy { WatchSyncRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DraftingRoom5Watch(repository, onClose = ::finish) }
    }

    override fun onStart() {
        super.onStart()
        repository.start()
    }

    override fun onStop() {
        repository.stop()
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
private fun DraftingRoom5Watch(repository: WatchSyncRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val snapshot by repository.state.collectAsState()
    val timerStore = remember { LocalTimerStore(context) }
    var timer by remember { mutableStateOf(timerStore.read()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var celebration by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val exercise = snapshot?.focusedExercise

    LaunchedEffect(timer) {
        while (timer != null) {
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
        when {
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
            snapshot == null -> MessageScreen("CONNECTING", "Open DraftingRoom5 on your phone", "Sync", repository::sync)
            snapshot?.status == WatchWorkoutStatus.NONE -> MessageScreen(
                "TODAY", snapshot?.message ?: "No guided workout today.", "Sync", repository::sync,
            )
            snapshot?.status == WatchWorkoutStatus.ERROR -> MessageScreen(
                "PHONE NEEDED", snapshot?.message ?: "Workout data is unavailable.", "Retry", repository::sync,
            )
            snapshot?.status == WatchWorkoutStatus.AVAILABLE -> TodayScreen(checkNotNull(snapshot), repository::startToday)
            snapshot?.status == WatchWorkoutStatus.COMPLETE -> CompletionScreen(checkNotNull(snapshot), onClose)
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
private fun TodayScreen(snapshot: WatchSnapshot, onStart: () -> Unit) {
    val art = snapshot.exercises.firstOrNull()?.artworkId
    RoundProgress(0f) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtworkHero(artworkResource(art), 48)
            Eyebrow("TODAY")
            WatchText(
                snapshot.routineName.orEmpty(), 20, Ivory, FontWeight.Normal, serifFamily(),
                maxLines = 2, modifier = Modifier.semantics { heading() },
            )
            WatchText("${snapshot.exercises.size} exercises", 13, Secondary)
            Spacer(Modifier.height(5.dp))
            PrimaryButton("▶  Start", onStart)
        }
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
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtworkHero(artworkResource(exercise.artworkId), 38)
            Eyebrow("${snapshot.exercises.indexOf(exercise) + 1} OF ${snapshot.exercises.size}")
            WatchText(
                exercise.name, 19, Ivory, FontWeight.Normal, serifFamily(), maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            WatchText("Set ${exercise.completedSets + 1} of ${exercise.setCount}", 13, Secondary)
            WatchText(exercise.targetLabel(), 18, Ivory, FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (onStartTimer != null) {
                SecondaryButton("▶  Start timer", onStartTimer)
                Spacer(Modifier.height(4.dp))
            }
            PrimaryButton("Complete set", onComplete)
            Spacer(Modifier.height(8.dp))
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
    RoundProgress(fraction) {
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
        Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(48.dp).background(Mint.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            WatchText("✓", 32, Mint, FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Eyebrow("SET COMPLETE")
        WatchText("${progress.first} of ${progress.second}", 22, Secondary)
        Spacer(Modifier.height(5.dp))
        WatchText("Next set", 17, Ivory, FontWeight.Bold)
    }
}

@Composable
private fun CompletionScreen(snapshot: WatchSnapshot, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(48.dp).background(Mint.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            WatchText("✓", 32, Mint, FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        WatchText("SESSION\nCOMPLETE", 18, Ivory, FontWeight.Normal, serifFamily(), maxLines = 2)
        WatchText("${snapshot.exercises.size} of ${snapshot.exercises.size} exercises", 12, Secondary)
        Spacer(Modifier.height(7.dp))
        PrimaryButton("Done", onDone)
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
        WatchText(message, 20, Ivory, FontWeight.Normal, serifFamily(), maxLines = 3)
        Spacer(Modifier.height(15.dp))
        PrimaryButton(action, onAction)
    }
}

@Composable
private fun RoundProgress(progress: Float, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            drawArc(
                color = Raised,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
            if (progress > 0f) drawArc(
                color = Blue,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun ArtworkHero(resource: Int, height: Int) {
    Box(
        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(40.dp))
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f))))
            },
    ) {
        Image(painterResource(resource), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
    "dead_hang" -> R.drawable.exercise_dead_hang_header
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
        status = if (screen == "Today") WatchWorkoutStatus.AVAILABLE else WatchWorkoutStatus.ACTIVE,
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
    )
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        when (screen) {
            "Today" -> TodayScreen(snapshot, {})
            "Exercise" -> ExerciseScreen(snapshot, exercises.first(), {}, {})
            "Ready" -> TimerScreen(exercises.first(), LocalTimer("hang", 4_000, 24_000), 1_000, true, {}, {})
            "Running" -> TimerScreen(exercises.first(), LocalTimer("hang", 1_000, 21_000), 7_000, true, {}, {})
            "Set complete" -> SetCompleteScreen(1 to 3)
            else -> CompletionScreen(snapshot.copy(status = WatchWorkoutStatus.COMPLETE), {})
        }
    }
}
