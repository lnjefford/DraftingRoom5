package dev.draftingroom5

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal data class SessionTimerPresentation(
    val phaseLabel: String,
    val display: String,
    val supportingText: String,
    val primaryLabel: String?,
    val cancelLabel: String?,
    val faded: Boolean,
    val completeSetEnabled: Boolean,
)

internal data class GuidedSessionPresentation(
    val focused: Exercise,
    val focusedIndex: Int,
    val completedSets: Int,
    val completedExercises: Int,
    val totalExercises: Int,
    val timer: SessionTimerPresentation?,
    val upcoming: List<Exercise>,
    val readyToFinish: Boolean,
    val setProgress: Float,
)

internal fun guidedSessionPresentation(session: GuidedSession, elapsedMillis: Long): GuidedSessionPresentation {
    val focused = session.snapshot.exercises.first { it.id == session.focusedExerciseId }
    val completedSets = session.completedSets.getValue(focused.id)
    val completedExercises = session.snapshot.exercises.count {
        session.completedSets.getValue(it.id) == it.setCount
    }
    val timer = focused.timerSeconds?.let { configured ->
        val seconds = timerDisplaySeconds(session.timer, elapsedMillis, configured)
        when (session.timer.phase) {
            TimerPhase.IDLE -> SessionTimerPresentation(
                phaseLabel = "Timer ready",
                display = formatSessionTimer(seconds),
                supportingText = "10-second Get ready countdown",
                primaryLabel = "Start timer".takeIf { completedSets < focused.setCount },
                cancelLabel = null,
                faded = false,
                completeSetEnabled = completedSets < focused.setCount,
            )
            TimerPhase.READY -> SessionTimerPresentation(
                phaseLabel = "Get ready",
                display = seconds.toString(),
                supportingText = "Timer starts automatically",
                primaryLabel = null,
                cancelLabel = "Cancel countdown",
                faded = true,
                completeSetEnabled = false,
            )
            TimerPhase.RUNNING -> SessionTimerPresentation(
                phaseLabel = "Timer running",
                display = formatSessionTimer(seconds),
                supportingText = "Complete the set early when your movement is finished",
                primaryLabel = null,
                cancelLabel = "Cancel timer",
                faded = false,
                completeSetEnabled = completedSets < focused.setCount,
            )
            TimerPhase.FINISHED -> SessionTimerPresentation(
                phaseLabel = "Timer complete",
                display = "00:00",
                supportingText = "The timer does not complete the set",
                primaryLabel = "Restart timer",
                cancelLabel = null,
                faded = false,
                completeSetEnabled = completedSets < focused.setCount,
            )
        }
    }
    return GuidedSessionPresentation(
        focused = focused,
        focusedIndex = session.snapshot.exercises.indexOfFirst { it.id == focused.id },
        completedSets = completedSets,
        completedExercises = completedExercises,
        totalExercises = session.snapshot.exercises.size,
        timer = timer,
        upcoming = session.snapshot.exercises.filterNot { it.id == focused.id },
        readyToFinish = session.durableState() == DurableSessionState.READY_TO_FINISH,
        setProgress = (session.completedSets.values.sumOf { it.toLong() }.toDouble() /
            session.snapshot.exercises.sumOf { it.setCount.toLong() }).toFloat(),
    )
}

internal fun formatSessionTimer(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

internal data class SessionFeedbackOwner(
    val sessionId: String,
    val leaseValue: Long,
    val foregroundEpoch: Long,
)

internal class SessionFeedbackController(private val capacity: Int = 96) {
    private var owner: SessionFeedbackOwner? = null
    private val consumed = LinkedHashSet<String>()

    fun claim(value: SessionFeedbackOwner) {
        owner = value
    }

    fun revoke(value: SessionFeedbackOwner? = null) {
        if (value == null || owner == value) owner = null
    }

    fun dispatch(
        expectedOwner: SessionFeedbackOwner,
        session: GuidedSession,
        outputs: List<SessionOutput>,
        completedExerciseId: String? = null,
        haptic: (HapticCue) -> Unit,
        voice: (VoiceCue) -> Unit,
    ) {
        if (owner != expectedOwner || session.id != expectedOwner.sessionId) return
        outputs.forEach { output ->
            val identity = when (output) {
                is SessionOutput.TimerCue -> "${session.id}:${output.runId}:${output.ordinal}"
                SessionOutput.SetCompleted -> "${session.id}:${session.eventRevision}:set"
                SessionOutput.TimerCouldNotResume -> "${session.id}:${session.eventRevision}:recovery"
            }
            if (!consumed.add(identity)) return@forEach
            while (consumed.size > capacity) consumed.remove(consumed.first())
            when (output) {
                is SessionOutput.TimerCue -> {
                    if (session.timer.runId != output.runId) return@forEach
                    val exercise = session.snapshot.exercises.firstOrNull { it.id == session.timer.exerciseId }
                        ?: return@forEach
                    when (output.ordinal) {
                        0 -> {
                            haptic(HapticCue.TIMER_START)
                            voice(VoiceCue.CountdownStarted)
                        }
                        1, 2, 3 -> voice(VoiceCue.CountdownTick(4 - output.ordinal))
                        4 -> {
                            haptic(HapticCue.COUNTDOWN_COMPLETE)
                            voice(VoiceCue.TimerStarted(exercise.name, requireNotNull(exercise.timerSeconds)))
                        }
                        5 -> {
                            haptic(HapticCue.TIMER_COMPLETE)
                            voice(VoiceCue.TimerCompleted(exercise.name))
                        }
                    }
                }
                SessionOutput.SetCompleted -> {
                    val exercise = session.snapshot.exercises.firstOrNull { it.id == completedExerciseId }
                        ?: return@forEach
                    val count = session.completedSets.getValue(exercise.id)
                    val next = session.snapshot.exercises.firstOrNull { it.id == session.focusedExerciseId }
                        ?.takeIf { it.id != exercise.id }?.name
                    haptic(HapticCue.SET_COMPLETE)
                    voice(VoiceCue.SetCompleted(exercise.name, count, exercise.setCount, next))
                }
                SessionOutput.TimerCouldNotResume -> Unit
            }
        }
    }
}

@Composable
internal fun GuidedSessionDestination(
    repository: AppRepository,
    routine: Routine,
    scheduleEntryId: String,
    scheduledDate: LocalDate,
    isForeground: Boolean,
    onDocumentChanged: (AppDocument) -> Unit,
    onHapticCue: (HapticCue) -> Unit,
    onVoiceCue: (VoiceCue) -> Unit,
    onStopVoice: () -> Unit,
    onBackupRequested: () -> Unit,
    onExit: () -> Unit,
    onCompleted: (String, Boolean) -> Unit,
) {
    val occurrence = remember(scheduleEntryId, scheduledDate) { OccurrenceKey(scheduleEntryId, scheduledDate) }
    val lease = remember(occurrence) { repository.sessionLease() }
    val context = LocalContext.current
    val clock = remember(context) { AndroidSessionClock(context.applicationContext) }
    val feedback = remember { SessionFeedbackController() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var session by remember(occurrence) { mutableStateOf<GuidedSession?>(null) }
    var boundSessionId by rememberSaveable(scheduleEntryId, scheduledDate.toString()) { mutableStateOf<String?>(null) }
    var elapsedMillis by remember { mutableLongStateOf(clock.sample().elapsedMillis) }
    var loading by remember(occurrence) { mutableStateOf(true) }
    var error by remember(occurrence) { mutableStateOf<String?>(null) }
    var exitRequested by rememberSaveable(scheduleEntryId, scheduledDate.toString()) { mutableStateOf(false) }
    var restartRequested by rememberSaveable(scheduleEntryId, scheduledDate.toString()) { mutableStateOf(false) }
    var owner by remember { mutableStateOf<SessionFeedbackOwner?>(null) }
    var foregroundEpoch by remember { mutableLongStateOf(0L) }

    fun syncDocument() {
        (repository.state.value as? LoadState.Ready)?.value?.let(onDocumentChanged)
    }

    fun execute(operation: () -> SessionRepositoryResult, consume: (SessionRepositoryResult) -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { operation() }
                consume(result)
            } finally {
                saving = false
            }
        }
    }

    fun accept(result: SessionRepositoryResult, completedExerciseId: String? = null, allowFeedback: Boolean = true) {
        loading = false
        if (repository.sessionLease() != lease) {
            session = null
            exitRequested = false
            restartRequested = false
            feedback.revoke(owner)
            onStopVoice()
            error = "App data changed. Return to the dashboard and reopen this session."
            return
        }
        when (result) {
            is SessionRepositoryResult.Partial -> {
                session = result.session
                boundSessionId = result.session.id
                syncDocument()
                if (result.outputs.any { it == SessionOutput.TimerCouldNotResume }) {
                    error = "Timer could not be resumed. Your completed sets and exercise focus are saved."
                }
                val canonical = (repository.state.value as? LoadState.Ready)?.value
                    ?.partialSessions?.firstOrNull { it.id == result.session.id }
                if (allowFeedback && canonical == result.session) owner?.let { token ->
                    feedback.dispatch(token, result.session, result.outputs, completedExerciseId, onHapticCue, onVoiceCue)
                }
            }
            is SessionRepositoryResult.Complete -> {
                syncDocument()
                feedback.revoke(owner)
                onStopVoice()
                onCompleted(result.history.id, result.newlyCompleted && isForeground)
            }
            is SessionRepositoryResult.Conflict -> {
                session = result.current
                syncDocument()
                error = "Session progress changed. The latest saved progress is shown."
            }
            SessionRepositoryResult.RefreshRequired, SessionRepositoryResult.Missing -> {
                session = null
                exitRequested = false
                restartRequested = false
                feedback.revoke(owner)
                onStopVoice()
                error = "This saved session changed or is no longer available. Return to the dashboard to continue."
            }
            SessionRepositoryResult.AlreadyComplete -> onCompleted(session?.id.orEmpty(), false)
            is SessionRepositoryResult.Invalid -> error = result.reason
            is SessionRepositoryResult.Failed -> error = "Could not save session progress. Try the action again."
        }
    }

    fun apply(
        event: SessionEvent,
        completedExerciseId: String? = null,
        allowFeedback: Boolean = true,
        requestBackup: Boolean = false,
    ) {
        val current = session ?: return
        val currentLease = lease ?: return
        val commandOwner = owner
        if (event is SessionEvent.Focus || event is SessionEvent.CancelTimer || event is SessionEvent.UndoLastSet ||
            event is SessionEvent.CompleteSet) onStopVoice()
        execute({ repository.applySessionEvent(currentLease, current.id, current.eventRevision, event) }) { result ->
            accept(result, completedExerciseId, allowFeedback && owner == commandOwner && isForeground && !exitRequested && !restartRequested)
            if (requestBackup && result is SessionRepositoryResult.Partial) onBackupRequested()
        }
    }

    fun saveAndExit() {
        val current = session ?: return
        val currentLease = lease ?: return
        execute({ repository.applySessionEvent(
            currentLease,
            current.id,
            current.eventRevision,
            SessionEvent.SaveCheckpoint(sameProcess = true),
        ) }) { result ->
            accept(result, allowFeedback = false)
            if (result is SessionRepositoryResult.Partial) {
                onBackupRequested()
                feedback.revoke(owner)
                onStopVoice()
                onExit()
            }
        }
    }

    fun keepGoing() {
        exitRequested = false
        val current = session ?: return
        current.timer.runId?.let { apply(SessionEvent.Reconcile(it, sameProcess = true, visibleForFeedback = false), allowFeedback = false) }
    }

    fun restartSession() {
        val current = session ?: return
        val currentLease = lease ?: return
        execute({ repository.restartGuidedSession(
            currentLease,
            current.id,
            current.eventRevision,
            routine.revision,
            newId(),
        ) }) { result ->
            accept(result, allowFeedback = false)
            if (result is SessionRepositoryResult.Partial) {
                onBackupRequested()
                restartRequested = false
                feedback.revoke(owner)
                onStopVoice()
            }
        }
    }

    LaunchedEffect(occurrence, routine.id, routine.revision, lease) {
        if (lease == null) {
            loading = false
            error = "App data is not ready. Return to the dashboard and try again."
        } else {
            val result = withContext(Dispatchers.IO) { repository.openGuidedSession(
                lease = lease,
                occurrence = occurrence,
                displayedRoutineId = routine.id,
                displayedRoutineRevision = routine.revision,
                newSessionId = newId(),
                existingSessionId = boundSessionId,
            ) }
            accept(result, allowFeedback = false)
            if (result is SessionRepositoryResult.Partial && result.created) onBackupRequested()
        }
    }

    val feedbackAllowed = isForeground && !exitRequested && !restartRequested
    DisposableEffect(feedbackAllowed, session?.id, lease) {
        if (feedbackAllowed && session != null && lease != null) {
            foregroundEpoch += 1
            SessionFeedbackOwner(session!!.id, lease.value, foregroundEpoch).also {
                owner = it
                feedback.claim(it)
            }
        } else {
            feedback.revoke(owner)
            owner = null
            onStopVoice()
        }
        onDispose {
            feedback.revoke(owner)
            owner = null
            onStopVoice()
        }
    }

    LaunchedEffect(feedbackAllowed, session?.id, lease) {
        if (!feedbackAllowed || lease == null) return@LaunchedEffect
        while (saving) delay(10)
        session?.timer?.takeIf { it.phase != TimerPhase.IDLE }?.runId?.let { runId ->
            apply(SessionEvent.Reconcile(runId, sameProcess = true, visibleForFeedback = false), allowFeedback = false)
        }
        while (feedbackAllowed) {
            val current = session ?: break
            val sample = clock.sample()
            elapsedMillis = sample.elapsedMillis
            val runId = current.timer.runId
            if (runId != null) apply(SessionEvent.Reconcile(runId, sameProcess = true, visibleForFeedback = true))
            delay(200)
        }
    }

    BackHandler(enabled = session != null) { exitRequested = true }
    when {
        loading -> GuidedSessionLoading(routine.name, onExit)
        session == null -> GuidedSessionFailure(routine.name, error ?: "Session unavailable.", onExit)
        else -> GuidedSessionScreen(
            session = requireNotNull(session),
            elapsedMillis = elapsedMillis,
            motionEnabled = ValueAnimator.areAnimatorsEnabled(),
            error = error,
            onDismissError = { error = null },
            onFocus = { apply(SessionEvent.Focus(it), requestBackup = true) },
            onCompleteSet = { exerciseId, setNumber ->
                apply(SessionEvent.CompleteSet(exerciseId, setNumber), completedExerciseId = exerciseId, requestBackup = true)
            },
            onUndo = { exerciseId, count -> apply(SessionEvent.UndoLastSet(exerciseId, count), requestBackup = true) },
            onStartTimer = {
                val current = requireNotNull(session)
                apply(SessionEvent.StartTimer(newId(), current.timer.runId), requestBackup = true)
            },
            onCancelTimer = { runId -> apply(SessionEvent.CancelTimer(runId), requestBackup = true) },
            onFinish = {
                val current = requireNotNull(session)
                val currentLease = lease
                if (currentLease != null) {
                    execute({ repository.finishGuidedSession(currentLease, current.id, current.eventRevision) }) { result ->
                        if (result is SessionRepositoryResult.Complete && result.newlyCompleted) onBackupRequested()
                        accept(result)
                    }
                }
            },
            onExit = { exitRequested = true },
            onRestart = { restartRequested = true },
        )
    }
    if (exitRequested) AppConfirmationDialog(
        title = "Save this session for later?",
        message = "Your completed sets and current exercise will be preserved." + (error?.let { "\n\n$it" } ?: ""),
        confirmLabel = "Save & exit",
        onConfirm = ::saveAndExit,
        onDismiss = ::keepGoing,
        dismissLabel = "Keep going",
    )
    if (restartRequested) AppConfirmationDialog(
        title = "Restart session?",
        message = "This clears all saved progress and starts again with the latest version of ${routine.name}." + (error?.let { "\n\n$it" } ?: ""),
        confirmLabel = "Restart session",
        onConfirm = ::restartSession,
        onDismiss = { restartRequested = false },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuidedSessionScreen(
    session: GuidedSession,
    elapsedMillis: Long,
    motionEnabled: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onFocus: (String) -> Unit,
    onCompleteSet: (String, Int) -> Unit,
    onUndo: (String, Int) -> Unit,
    onStartTimer: () -> Unit,
    onCancelTimer: (String) -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit,
    onRestart: () -> Unit,
) {
    val presentation = guidedSessionPresentation(session, elapsedMillis)
    var overflow by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(session.snapshot.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Save and exit")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflow = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, "Session options")
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Save & exit") },
                                onClick = { overflow = false; onExit() },
                            )
                            DropdownMenuItem(
                                text = { Text("Restart session") },
                                onClick = { overflow = false; onRestart() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "progress") {
                SessionProgress(presentation)
            }
            if (error != null) item {
                InlineErrorState("Session needs attention", error, "Dismiss", onDismissError)
            }
            item(key = "exercise-header") {
                CurrentExerciseHeader(presentation.focused, motionEnabled)
            }
            item(key = "set-panel") {
                SetAndTimerPanel(
                    presentation = presentation,
                    session = session,
                    onCompleteSet = onCompleteSet,
                    onUndo = onUndo,
                    onStartTimer = onStartTimer,
                    onCancelTimer = onCancelTimer,
                )
            }
            if (presentation.upcoming.isNotEmpty()) item {
                Text("UP NEXT", color = AppGold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
            }
            items(presentation.upcoming, key = Exercise::id) { exercise ->
                UpcomingExerciseRow(exercise, session.completedSets.getValue(exercise.id), onFocus)
            }
            item {
                if (presentation.readyToFinish) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppMint, contentColor = AppBackgroundDeep),
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Finish session")
                    }
                }
                Text(
                    "Progress saves automatically",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SessionProgress(presentation: GuidedSessionPresentation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${presentation.focusedIndex + 1} of ${presentation.totalExercises} exercises",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            if (presentation.readyToFinish) "All complete" else "In progress",
            modifier = Modifier.fillMaxWidth(),
            color = if (presentation.readyToFinish) AppMint else AppBlue,
            textAlign = TextAlign.End,
        )
        LinearProgressIndicator(
            progress = { presentation.setProgress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = if (presentation.readyToFinish) AppMint else AppBlue,
            trackColor = AppBorder,
        )
        Text("Focused exercise: ${presentation.focused.name}", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CurrentExerciseHeader(exercise: Exercise, motionEnabled: Boolean) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val showArtwork = configuration.screenWidthDp >= 340 && fontScale < 1.75f
    val artwork = ExerciseArtworkCatalog.resolve(exercise.artworkId)
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 176.dp)) {
            if (showArtwork) {
                Image(
                    painter = painterResource(artwork.headerAsset),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.52f).height(190.dp)
                        .alpha(if (motionEnabled) 0.9f else 0.82f),
                )
            }
            Column(
                Modifier.fillMaxWidth(if (showArtwork) 0.62f else 1f).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("CURRENT EXERCISE", color = AppGold, style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(Modifier.width(36.dp), thickness = 2.dp, color = AppGold)
                Text(exercise.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                if (exercise.notes.isNotBlank()) Text(exercise.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${exercise.setCount} sets · ${exercise.target}", color = AppBlue, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SetAndTimerPanel(
    presentation: GuidedSessionPresentation,
    session: GuidedSession,
    onCompleteSet: (String, Int) -> Unit,
    onUndo: (String, Int) -> Unit,
    onStartTimer: () -> Unit,
    onCancelTimer: (String) -> Unit,
) {
    val exercise = presentation.focused
    val currentComplete = presentation.completedSets >= exercise.setCount
    AppSurfaceCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (currentComplete) "All ${exercise.setCount} sets complete" else "Set ${presentation.completedSets + 1} of ${exercise.setCount}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(exercise.target, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SetIndicators(exercise.setCount, presentation.completedSets)
            presentation.timer?.let { timer ->
                HorizontalDivider(color = AppBorder)
                Icon(Icons.Default.Timer, null, tint = if (session.timer.phase == TimerPhase.FINISHED) AppMint else AppBlue)
                Text(timer.phaseLabel, color = if (session.timer.phase == TimerPhase.FINISHED) AppMint else MaterialTheme.colorScheme.onSurface)
                Text(
                    timer.display,
                    modifier = Modifier.semantics { stateDescription = "${timer.phaseLabel}, ${timer.display}" },
                    color = if (timer.faded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(timer.supportingText, minLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                timer.primaryLabel?.let { label ->
                    TextButton(onClick = onStartTimer, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                }
                timer.cancelLabel?.let { label ->
                    TextButton(
                        onClick = { session.timer.runId?.let(onCancelTimer) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(label) }
                }
            }
            if (presentation.completedSets > 0) {
                TextButton(
                    onClick = { onUndo(exercise.id, presentation.completedSets) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Undo last set") }
            }
            if (!currentComplete) {
                Button(
                    onClick = { onCompleteSet(exercise.id, presentation.completedSets + 1) },
                    enabled = presentation.timer?.completeSetEnabled != false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) { Text("Complete set") }
            }
        }
    }
}

@Composable
private fun SetIndicators(total: Int, completed: Int) {
    if (total > 6) {
        Text("$completed of $total sets complete")
        return
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$completed of $total sets complete" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(total) { index ->
            val setNumber = index + 1
            val done = setNumber <= completed
            val current = setNumber == completed + 1
            Icon(
                imageVector = when { done -> Icons.Default.CheckCircle; current -> Icons.Default.RadioButtonChecked; else -> Icons.Default.Circle },
                contentDescription = when { done -> "Set $setNumber complete"; current -> "Set $setNumber current"; else -> "Set $setNumber upcoming" },
                tint = when { done -> AppMint; current -> AppBlue; else -> AppBorder },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun UpcomingExerciseRow(exercise: Exercise, completedSets: Int, onFocus: (String) -> Unit) {
    val artwork = ExerciseArtworkCatalog.resolve(exercise.artworkId)
    val completed = completedSets == exercise.setCount
    AppSurfaceCard(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(role = Role.Button) { onFocus(exercise.id) }
            .semantics { stateDescription = if (completed) "Complete" else "$completedSets of ${exercise.setCount} sets complete" },
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(artwork.listAsset),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).clip(MaterialTheme.shapes.medium).aspectRatio(1f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (completed) "Completed · ${exercise.target}" else "$completedSets of ${exercise.setCount} sets · ${exercise.target}",
                    color = if (completed) AppMint else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppBlue)
        }
    }
}

@Composable
private fun GuidedSessionLoading(title: String, onExit: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        containerColor = Color.Transparent,
        topBar = { SecondaryTopBar(title, onExit) },
    ) { padding ->
        InlineLoadingState("Opening saved session…", Modifier.padding(padding).padding(20.dp))
    }
}

@Composable
private fun GuidedSessionFailure(title: String, message: String, onExit: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().appScreenBackground(),
        containerColor = Color.Transparent,
        topBar = { SecondaryTopBar(title, onExit) },
    ) { padding ->
        InlineErrorState("Session unavailable", message, "Return to dashboard", onExit, Modifier.padding(padding).padding(20.dp))
    }
}

@Composable
internal fun GuidedSessionReviewPreview(state: String, controlsOnly: Boolean = false) {
    val original = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }
    val routine = if (state == "Session many sets") original.copy(
        exercises = original.exercises.map { it.copy(setCount = Int.MAX_VALUE) },
    ) else original
    val elapsed = 100_000L
    val phase = when (state) {
        "Session ready" -> TimerPhase.READY
        "Session running" -> TimerPhase.RUNNING
        "Session finished" -> TimerPhase.FINISHED
        else -> TimerPhase.IDLE
    }
    val timer = when (phase) {
        TimerPhase.IDLE -> SessionTimer()
        TimerPhase.READY -> SessionTimer(phase, "preview-run", routine.exercises.first().id, 2, 7, 105_000, 125_000, elapsed, 0)
        TimerPhase.RUNNING -> SessionTimer(phase, "preview-run", routine.exercises.first().id, 2, 7, 90_000, 112_000, elapsed, 4)
        TimerPhase.FINISHED -> SessionTimer(phase, "preview-run", routine.exercises.first().id, 2, 7, 80_000, 100_000, elapsed, 5)
    }
    val session = GuidedSession(
        id = "preview-session",
        occurrence = OccurrenceKey("schedule-forearm", LocalDate.of(2026, 9, 12)),
        routineId = routine.id,
        snapshot = routine,
        focusedExerciseId = routine.exercises.first().id,
        completedSets = routine.exercises.associate { it.id to if (it == routine.exercises.first()) 1 else 0 },
        timer = timer,
        startedAtMillis = 1,
        updatedAtMillis = 1,
        eventRevision = 1,
    )
    DraftingRoom5Theme {
        if (controlsOnly) {
            LazyColumn(Modifier.fillMaxSize().appScreenBackground().padding(20.dp)) {
                item {
                    SetAndTimerPanel(guidedSessionPresentation(session, elapsed), session, { _, _ -> }, { _, _ -> }, {}, {})
                }
            }
        } else GuidedSessionScreen(session, elapsed, false, null, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Guided session compact", widthDp = 320, heightDp = 800)
@Composable
private fun GuidedSessionCompactPreview() = GuidedSessionReviewPreview("Session idle")

@Preview(name = "Guided session tall", widthDp = 412, heightDp = 1100)
@Composable
private fun GuidedSessionTallPreview() = GuidedSessionReviewPreview("Session running")

@Preview(name = "Guided session large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
private fun GuidedSessionLargeTextPreview() = GuidedSessionReviewPreview("Session ready")
