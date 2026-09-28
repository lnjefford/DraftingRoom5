package dev.draftingroom5

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
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
    val completed: List<Exercise>,
    val readyToFinish: Boolean,
    val setProgress: Float,
)

internal fun guidedSessionPresentation(session: GuidedSession, elapsedMillis: Long): GuidedSessionPresentation {
    val focused = session.snapshot.exercises.first { it.id == session.focusedExerciseId }
    val completedSets = session.completedSets.getValue(focused.id)
    val completedExercises = session.snapshot.exercises.count {
        session.completedSets.getValue(it.id) == it.setCount
    }
    val timer = focused.durationSeconds?.takeIf { completedSets < focused.setCount }?.let { configured ->
        val seconds = timerDisplaySeconds(session.timer, elapsedMillis, configured)
        when (session.timer.phase) {
            TimerPhase.IDLE -> SessionTimerPresentation(
                phaseLabel = "Timer ready",
                display = formatSessionTimer(seconds),
                primaryLabel = "Start timer".takeIf { completedSets < focused.setCount },
                cancelLabel = null,
                faded = false,
                completeSetEnabled = completedSets < focused.setCount,
            )
            TimerPhase.READY -> SessionTimerPresentation(
                phaseLabel = "Get ready",
                display = seconds.toString(),
                primaryLabel = null,
                cancelLabel = "Cancel countdown",
                faded = true,
                completeSetEnabled = false,
            )
            TimerPhase.RUNNING -> SessionTimerPresentation(
                phaseLabel = "Timer running",
                display = formatSessionTimer(seconds),
                primaryLabel = null,
                cancelLabel = "Cancel timer",
                faded = false,
                completeSetEnabled = completedSets < focused.setCount,
            )
            TimerPhase.FINISHED -> SessionTimerPresentation(
                phaseLabel = "Timer complete",
                display = "00:00",
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
        upcoming = session.snapshot.exercises.filter { it.id != focused.id && session.completedSets.getValue(it.id) < it.setCount },
        completed = session.snapshot.exercises.filter { it.id != focused.id && session.completedSets.getValue(it.id) == it.setCount },
        readyToFinish = session.durableState() == DurableSessionState.READY_TO_FINISH,
        setProgress = (session.completedSets.values.sumOf { it.toLong() }.toDouble() /
            session.snapshot.exercises.sumOf { it.setCount.toLong() }).toFloat(),
    )
}

internal fun formatSessionTimer(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

internal fun progressionPrescription(exercise: Exercise): String = buildList {
    add(exercise.name)
    add("${exercise.setCount} ${if (exercise.setCount == 1) "set" else "sets"}")
    exercise.reps?.let { add("$it reps") }
    exercise.weightPounds?.let { add("${formatProgressionPounds(it)} lb") }
    exercise.durationSeconds?.let { add("$it seconds") }
}.joinToString(", ")

private fun formatProgressionPounds(value: Int): String = value.toString()

internal fun progressionResult(option: ProgressionOption): String = buildList {
    add(progressionPrescription(option.source))
    if (option.source.notes.isNotBlank()) add(option.source.notes)
    option.source.durationSeconds?.let { add("Timer: $it seconds") }
    option.additions.forEach { add("Add: ${progressionPrescription(it)}") }
}.joinToString("\n")

internal fun progressionOfferForSession(document: AppDocument, session: GuidedSession): ProgressionOffer? =
    session.snapshot.exercises.firstNotNullOfOrNull { exercise ->
        if (exercise.id in session.handledProgressionExerciseIds ||
            session.completedSets.getValue(exercise.id) != exercise.setCount) null
        else document.progressionOffer(session.id, exercise.id)
    }

internal fun completePrescriptionSummary(prescription: ExercisePrescription): String = buildList {
    add("Weight ${prescription.weightPounds?.let { "$it lb" } ?: "not set"}")
    add("Duration ${prescription.durationSeconds?.let { "$it seconds" } ?: "not timed"}")
    add("Sets ${prescription.setCount}")
    add("Reps ${prescription.reps?.toString() ?: "not set"}")
}.joinToString(" · ")

internal fun completionSheetParent(mode: String): String = if (mode == "manual") "adjust" else "complete"

internal fun manualAdjustmentPrescription(
    source: Exercise,
    weight: String,
    durationEnabled: Boolean,
    duration: String,
    sets: String,
    reps: String,
): ExercisePrescription? {
    val candidate = source.prescription().copy(
        weightPounds = if (weight.isBlank()) null else weight.toIntOrNull() ?: return null,
        durationSeconds = if (durationEnabled) duration.toIntOrNull() ?: return null else null,
        setCount = sets.toIntOrNull() ?: return null,
        reps = if (reps.isBlank()) null else reps.toIntOrNull() ?: return null,
    )
    return try {
        validatePrescription(candidate)
        candidate
    } catch (_: IllegalArgumentException) {
        null
    }
}

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
                            voice(VoiceCue.TimerStarted(exercise.name, requireNotNull(exercise.durationSeconds)))
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
    val repositoryState by repository.state.collectAsState()
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    fun applyProgression(offer: ProgressionOffer, request: ProgressionRequest) {
        val currentLease = lease ?: return
        if (saving) return
        val committed = (repository.state.value as? LoadState.Ready)?.value ?: return
        if (committed.progressionReceipts.any { it.sessionId == offer.sessionId && it.before.id == offer.exerciseId }) return
        error = null
        saving = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { repository.applyProgression(currentLease, request) }
            } finally {
                saving = false
            }
            when (result) {
                is RepositoryResult.Success -> {
                    syncDocument()
                    onBackupRequested()
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        var undoMessage = "Future prescription updated"
                        while (snackbarHostState.showSnackbar(
                                message = undoMessage,
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long,
                            ) == SnackbarResult.ActionPerformed) {
                            val undo = withContext(Dispatchers.IO) {
                                repository.undoProgression(currentLease, result.value.sessionId, result.value.before.id)
                            }
                            when (undo) {
                                is RepositoryResult.Success -> {
                                    syncDocument()
                                    onBackupRequested()
                                    break
                                }
                                is RepositoryResult.Conflict -> { error = "Undo expired because the routine changed."; break }
                                is RepositoryResult.Invalid -> { error = undo.error.message ?: "Undo is no longer available."; break }
                                is RepositoryResult.Failed -> undoMessage = "Could not save Undo. Try again."
                            }
                        }
                    }
                }
                is RepositoryResult.Conflict -> error = "The routine changed. Review the latest progression choice."
                is RepositoryResult.Invalid -> error = result.error.message ?: "This progression choice is no longer available."
                is RepositoryResult.Failed -> error = "Could not update the future prescription. Try again."
            }
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

    val currentSession = session
    val currentDocument = (repositoryState as? LoadState.Ready)?.value
    val progressionOffer = if (currentSession != null && currentDocument != null) {
        progressionOfferForSession(currentDocument, currentSession)
    } else null
    val feedbackAllowed = isForeground && !exitRequested && !restartRequested && progressionOffer == null
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
            snackbarHostState = snackbarHostState,
        )
    }
    progressionOffer?.let { offer ->
        ProgressionDecisionSheet(
            exercise = currentSession!!.snapshot.exercises.first { it.id == offer.exerciseId },
            options = offer.options,
            busy = saving,
            error = error,
            finishSession = currentSession.durableState() == DurableSessionState.READY_TO_FINISH,
            onContinue = { apply(SessionEvent.ContinueWorkout(offer.exerciseId), requestBackup = true) },
            onFinish = {
                val currentLease = lease
                if (currentLease != null) {
                    execute({ repository.finishGuidedSession(currentLease, currentSession.id, currentSession.eventRevision) }) { result ->
                        if (result is SessionRepositoryResult.Complete && result.newlyCompleted) onBackupRequested()
                        accept(result)
                    }
                }
            },
            onApplyPlanned = { applyProgression(offer, offer.request(ProgressionChoice.CUSTOM)) },
            onApplyManual = { applyProgression(offer, offer.manualRequest(it)) },
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "progress") {
                SessionProgress(presentation, Modifier.padding(horizontal = 20.dp))
            }
            if (error != null) item {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    InlineErrorState("Session needs attention", error, "Dismiss", onDismissError)
                }
            }
            item(key = "exercise-header") {
                CurrentExerciseHeader(presentation.focused, motionEnabled)
            }
            item(key = "set-panel") {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    SetAndTimerPanel(
                        presentation = presentation,
                        session = session,
                        onCompleteSet = onCompleteSet,
                        onUndo = onUndo,
                        onStartTimer = onStartTimer,
                        onCancelTimer = onCancelTimer,
                    )
                }
            }
            sessionExerciseSection("UP NEXT", "up-next", presentation.upcoming, session.completedSets, onFocus)
            sessionExerciseSection("COMPLETED", "completed", presentation.completed, session.completedSets, onFocus)
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressionDecisionSheet(
    exercise: Exercise,
    options: List<ProgressionOption>,
    busy: Boolean,
    error: String?,
    finishSession: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    onApplyPlanned: () -> Unit,
    onApplyManual: (ExercisePrescription) -> Unit,
) {
    var mode by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf("complete") }
    var weight by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf(exercise.weightPounds?.toString().orEmpty()) }
    var durationEnabled by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf(exercise.durationSeconds != null) }
    var duration by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf(exercise.durationSeconds?.toString() ?: "20") }
    var sets by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf(exercise.setCount.toString()) }
    var reps by rememberSaveable(exercise.id, options.hashCode()) { mutableStateOf(exercise.reps?.toString().orEmpty()) }
    val manual = manualAdjustmentPrescription(exercise, weight, durationEnabled, duration, sets, reps)
    val scope = rememberCoroutineScope()
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || !currentBusy },
    )
    ModalBottomSheet(
        onDismissRequest = {
            if (!busy) {
                if (mode == "complete" && !finishSession) onContinue()
                else if (mode != "complete") mode = completionSheetParent(mode)
            }
            // Material hides before this callback. Keep the pending decision visible,
            // including when a keep-current write fails; only committed state removes it.
            scope.launch { sheetState.show() }
        },
        sheetState = sheetState,
        containerColor = AppSurface,
    ) {
        ProgressionDecisionSheetContent(
            exercise = exercise,
            options = options,
            mode = mode,
            busy = busy,
            error = error,
            finishSession = finishSession,
            onContinue = onContinue,
            onFinish = onFinish,
            onAdjust = { mode = "adjust" },
            onManual = { mode = "manual" },
            onApplyPlanned = onApplyPlanned,
            onApplyManual = { manual?.let(onApplyManual) },
            onBack = { mode = completionSheetParent(mode) },
            weight = weight,
            onWeightChange = { weight = it },
            durationEnabled = durationEnabled,
            onDurationEnabledChange = { durationEnabled = it },
            duration = duration,
            onDurationChange = { duration = it },
            sets = sets,
            onSetsChange = { sets = it },
            reps = reps,
            onRepsChange = { reps = it },
            manualPrescription = manual,
        )
    }
}

@Composable
internal fun ProgressionDecisionSheetContent(
    exercise: Exercise,
    options: List<ProgressionOption>,
    mode: String,
    busy: Boolean,
    finishSession: Boolean = false,
    onContinue: () -> Unit,
    onFinish: () -> Unit = {},
    onAdjust: () -> Unit,
    onManual: () -> Unit,
    onApplyPlanned: () -> Unit,
    onApplyManual: () -> Unit,
    onBack: () -> Unit,
    weight: String = exercise.weightPounds?.toString().orEmpty(),
    onWeightChange: (String) -> Unit = {},
    durationEnabled: Boolean = exercise.durationSeconds != null,
    onDurationEnabledChange: (Boolean) -> Unit = {},
    duration: String = exercise.durationSeconds?.toString() ?: "20",
    onDurationChange: (String) -> Unit = {},
    sets: String = exercise.setCount.toString(),
    onSetsChange: (String) -> Unit = {},
    reps: String = exercise.reps?.toString().orEmpty(),
    onRepsChange: (String) -> Unit = {},
    manualPrescription: ExercisePrescription? = exercise.prescription(),
    error: String? = null,
) {
    val scroll = androidx.compose.runtime.key(mode) { rememberScrollState() }
    Column(
        Modifier.fillMaxWidth().verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (mode == "complete") {
            Text(
                "${exercise.name} complete",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = if (finishSession) onFinish else onContinue,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (finishSession) AppMint else AppBlue,
                    contentColor = if (finishSession) AppBackgroundDeep else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (finishSession) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (finishSession) "Finish session" else "Next exercise")
            }
            OutlinedButton(
                onClick = onAdjust,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("Adjust exercise") }
        } else if (mode == "adjust") {
            Text(
                "Adjust ${exercise.name}",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PrescriptionSummaryCard("CURRENT PRESCRIPTION", exercise.prescription())
            options.firstOrNull()?.let { option ->
                PrescriptionSummaryCard("NEXT PLANNED STEP", option.source.prescription(), option.additions)
                Button(
                    onClick = onApplyPlanned,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics {
                        contentDescription = "Apply planned step and go to next exercise. ${completePrescriptionSummary(option.source.prescription())}"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                ) { Text("Apply planned step") }
            }
            OutlinedButton(
                onClick = onManual,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Adjust manually") }
            TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back") }
        } else {
            Text("Manual adjustment", modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text("Changes apply together for the next workout. Planned steps stay queued.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            StructuredTargetControls(weight, onWeightChange, durationEnabled, onDurationEnabledChange,
                duration, onDurationChange, sets, onSetsChange, reps, onRepsChange)
            manualPrescription?.let { PrescriptionSummaryCard("RESULTING PRESCRIPTION", it) }
            OutlinedButton(onClick = onBack, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back") }
            Button(onClick = onApplyManual, enabled = !busy && manualPrescription != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics {
                    manualPrescription?.let { contentDescription = "Save adjustment and go to next exercise. ${completePrescriptionSummary(it)}" }
                }, colors = ButtonDefaults.buttonColors(containerColor = AppBlue)) {
                Text("Save adjustment & next")
            }
        }
    }
}

@Composable
private fun PrescriptionSummaryCard(
    label: String,
    prescription: ExercisePrescription,
    additions: List<Exercise> = emptyList(),
) {
    val artworkName = stringResource(ExerciseArtworkCatalog.resolve(prescription.artworkId).displayNameRes)
    AppSurfaceCard(Modifier.fillMaxWidth().semantics {
        contentDescription = "$label. ${prescription.name}. ${completePrescriptionSummary(prescription)}. " +
            "Notes: ${prescription.notes.ifBlank { "none" }}. Artwork: $artworkName" +
            additions.joinToString(prefix = if (additions.isEmpty()) "" else ". Adds ") { progressionPrescription(it) }
    }) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = AppGold, style = MaterialTheme.typography.labelSmall)
            Text(prescription.name, fontWeight = FontWeight.Bold)
            Text(completePrescriptionSummary(prescription), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Text("Notes: ${prescription.notes.ifBlank { "none" }}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text("Artwork: $artworkName", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            additions.forEach { Text("Adds ${progressionPrescription(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun progressionChoiceLabel(choice: ProgressionChoice): String = when (choice) {
    ProgressionChoice.CUSTOM -> "Next custom step"
    ProgressionChoice.MANUAL -> "Adjust manually"
}

private fun androidx.compose.foundation.lazy.LazyListScope.sessionExerciseSection(
    title: String,
    keyPrefix: String,
    exercises: List<Exercise>,
    completedSets: Map<String, Int>,
    onFocus: (String) -> Unit,
) {
    if (exercises.isEmpty()) return
    item(key = "$keyPrefix-heading") {
        Text(title, color = AppGold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() })
    }
    items(exercises, key = Exercise::id) { exercise ->
        Box(Modifier.padding(horizontal = 20.dp)) {
            UpcomingExerciseRow(exercise, completedSets.getValue(exercise.id), onFocus)
        }
    }
}

@Composable
private fun SessionProgress(presentation: GuidedSessionPresentation, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val position: @Composable () -> Unit = {
            Text(
                "${presentation.focusedIndex + 1} of ${presentation.totalExercises} exercises",
                color = if (presentation.readyToFinish) AppMint else AppBlue,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        val status: @Composable () -> Unit = {
            Text(
                if (presentation.readyToFinish) "ALL COMPLETE" else "IN PROGRESS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.4.sp,
            )
        }
        if (fontScale >= 1.75f) {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                position()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { status() }
            }
        } else {
            Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                position()
                status()
            }
        }
        LinearProgressIndicator(
            progress = { presentation.setProgress },
            modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (presentation.readyToFinish) AppMint else AppBlue,
            trackColor = AppBorder,
        )
        Text(
            "Focused exercise: ${presentation.focused.name}",
            modifier = Modifier.clearAndSetSemantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Focused exercise: ${presentation.focused.name}" }.height(0.dp),
        )
    }
}

@Composable
private fun CurrentExerciseHeader(exercise: Exercise, motionEnabled: Boolean) {
    val fontScale = LocalDensity.current.fontScale
    val artwork = ExerciseArtworkCatalog.resolve(exercise.artworkId)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val heroHeight = when {
            fontScale >= 1.75f -> 430.dp
            maxWidth < 360.dp -> 360.dp
            else -> 420.dp
        }
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            Image(
                painter = painterResource(artwork.headerAsset),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (motionEnabled) 1f else 0.96f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.46f to AppBackgroundDeep.copy(alpha = 0.08f),
                        0.72f to AppBackgroundDeep.copy(alpha = 0.78f),
                        1f to AppBackground,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to AppBackgroundDeep.copy(alpha = 0.28f),
                        0.55f to Color.Transparent,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("CURRENT EXERCISE", color = AppGold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                Text(exercise.name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                if (exercise.notes.isNotBlank()) {
                    Text(
                        exercise.notes.uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text(exercise.targetSummary().uppercase(), color = AppBlue, style = MaterialTheme.typography.titleMedium)
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
    val fontScale = LocalDensity.current.fontScale
    val accessibilityView = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var optionsExpanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    LaunchedEffect(exercise.id, session.timer.phase) {
        presentation.timer?.let { accessibilityView.announceForAccessibility(it.phaseLabel) }
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            currentComplete -> "ALL SETS COMPLETE"
                            presentation.completedSets + 1 == exercise.setCount -> "FINAL SET"
                            else -> "SET ${presentation.completedSets + 1} OF ${exercise.setCount}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SetIndicators(exercise.setCount, presentation.completedSets)
                }
                if (presentation.completedSets > 0) {
                    Box {
                        IconButton(
                            onClick = { optionsExpanded = true },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Set options for ${exercise.name}" },
                        ) { Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Undo last set") },
                                onClick = {
                                    optionsExpanded = false
                                    onUndo(exercise.id, presentation.completedSets)
                                },
                            )
                        }
                    }
                }
            }
            presentation.timer?.let { timer ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackControls = fontScale >= 1.75f || maxWidth < 230.dp || timer.display.length > 5
                    val desiredClockSize = if (timer.faded) 42.sp else if (maxWidth >= 300.dp) 68.sp else 56.sp
                    val clockStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = desiredClockSize)
                    val availableClockWidth = with(density) { maxWidth.toPx() } - 2f
                    fun fits(size: Float) = textMeasurer.measure(timer.display, clockStyle.copy(fontSize = size.sp), softWrap = false).size.width <= availableClockWidth
                    val clockSize = if (fits(desiredClockSize.value)) desiredClockSize else {
                        // Measure each candidate: Android large-font scaling need not be linear.
                        var low = 1f
                        var high = desiredClockSize.value
                        repeat(12) {
                            val candidate = (low + high) / 2f
                            if (fits(candidate)) low = candidate else high = candidate
                        }
                        low.sp
                    }
                    val clock: @Composable () -> Unit = {
                        Text(
                            timer.display,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "${timer.phaseLabel}, ${timer.display}"
                            },
                            color = if (timer.faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Serif,
                            fontSize = clockSize,
                            lineHeight = (if (timer.faded) 50.sp else if (maxWidth >= 300.dp) 76.sp else 64.sp) * (clockSize.value / desiredClockSize.value),
                            maxLines = 1,
                        )
                    }
                    val start: @Composable () -> Unit = {
                        timer.primaryLabel?.let { label ->
                            OutlinedButton(
                                onClick = onStartTimer,
                                modifier = Modifier.heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, AppBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(label, maxLines = if (stackControls) 2 else 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    if (stackControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            clock()
                            start()
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            clock()
                            start()
                        }
                    }
                }
                timer.cancelLabel?.let { label ->
                    TextButton(
                        onClick = { session.timer.runId?.let(onCancelTimer) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(label) }
                }
            }
            if (!currentComplete) {
                Button(
                    onClick = { onCompleteSet(exercise.id, presentation.completedSets + 1) },
                    enabled = presentation.timer?.completeSetEnabled != false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = AppBackgroundDeep),
                ) { Text("Complete set") }
            }
    }
}

@Composable
private fun SetIndicators(total: Int, completed: Int) {
    if (total > 6) {
        Text("$completed of $total sets complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$completed of $total sets complete" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(total) { index ->
            val setNumber = index + 1
            val done = setNumber <= completed
            val current = setNumber == completed + 1
            Box(
                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                    when { done -> AppMint; current -> AppBlue; else -> AppBorder },
                ),
            )
        }
    }
}

@Composable
private fun UpcomingExerciseRow(exercise: Exercise, completedSets: Int, onFocus: (String) -> Unit) {
    val artwork = ExerciseArtworkCatalog.resolve(exercise.artworkId)
    val completed = completedSets == exercise.setCount
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onFocus(exercise.id) }
            .semantics { stateDescription = if (completed) "Complete" else "$completedSets of ${exercise.setCount} sets complete" },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(artwork.listAsset),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, AppBorder, RoundedCornerShape(14.dp)).aspectRatio(1f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (completed) "Completed · ${exercise.targetSummary()}" else "$completedSets of ${exercise.setCount} sets · ${exercise.targetSummary()}",
                    color = if (completed) AppMint else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppBlue)
        }
        HorizontalDivider(color = AppBorder.copy(alpha = 0.8f))
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
internal fun GuidedSessionReviewPreview(state: String, controlsOnly: Boolean = false, sectionsOnly: Boolean = false) {
    val original = defaultTrainingPlan().routines.first { it.execution == RoutineExecution.GUIDED }
    val routine = when (state) {
        "Session many sets" -> original.copy(exercises = original.exercises.map { it.copy(setCount = Int.MAX_VALUE) })
        "Session six sets" -> original.copy(exercises = original.exercises.map { it.copy(setCount = 6) })
        "Session longest timer" -> original.copy(exercises = original.exercises.map { it.copy(durationSeconds = Int.MAX_VALUE) })
        "Session no timer" -> original.copy(exercises = original.exercises.map { it.copy(durationSeconds = null) })
        else -> original
    }
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
        completedSets = routine.exercises.associate {
            it.id to when {
                state == "Session all sets complete" -> it.setCount
                state == "Session sections" && routine.exercises.indexOf(it) in 2..4 -> it.setCount
                state == "Session completed only" && it != routine.exercises.first() -> it.setCount
                it == routine.exercises.first() -> 1
                else -> 0
            }
        },
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
        } else if (sectionsOnly) {
            val presentation = guidedSessionPresentation(session, elapsed)
            LazyColumn(Modifier.fillMaxSize().appScreenBackground().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sessionExerciseSection("UP NEXT", "up-next", presentation.upcoming, session.completedSets, {})
                sessionExerciseSection("COMPLETED", "completed", presentation.completed, session.completedSets, {})
            }
        } else GuidedSessionScreen(session, elapsed, false, null, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
    }
}

@Composable
internal fun GuidedSessionArtworkReviewPreview(artworkId: String) {
    val asset = ExerciseArtworkCatalog.resolve(artworkId)
    val longName = artworkId == "long_name"
    val exercise = Exercise(
        id = "preview-artwork",
        name = when {
            longName -> "Extended Pronated Thick-Bar Dead Hangs with Alternating Grip"
            artworkId == "hangboard" -> "Hangboard Holds"
            else -> stringResource(asset.displayNameRes)
        },
        notes = when {
            longName -> "Pull-up bar + thick adapter; change hands after each hold"
            artworkId == "hangboard" -> "Controlled edge hold"
            artworkId == "rice_bag" -> "Practice at your own pace"
            else -> "Pull-up bar + thick adapter"
        },
        setCount = 3,
        reps = null,
        durationSeconds = 20,
        artworkId = if (longName) "dead_hang" else artworkId,
    )
    DraftingRoom5Theme {
        LazyColumn(Modifier.fillMaxSize().appScreenBackground().padding(20.dp)) {
            item { CurrentExerciseHeader(exercise, motionEnabled = false) }
        }
    }
}

@Composable
internal fun ProgressionDecisionReviewPreview(choosing: Boolean) {
    val source = Exercise(
        id = "progression-sheet",
        name = "Loaded farmer hold",
        notes = "Keep shoulders down",
        setCount = 3,
        reps = null,
        durationSeconds = 30,
        artworkId = "farmers_walk",
        weightPounds = 30,
    )
    val options = listOf(ProgressionOption(ProgressionChoice.CUSTOM, source.copy(weightPounds = 35, durationSeconds = 35)))
    DraftingRoom5Theme {
        Box(Modifier.fillMaxSize().appScreenBackground(), contentAlignment = Alignment.BottomCenter) {
            ProgressionDecisionSheetContent(
                exercise = source,
                options = options,
                mode = if (choosing) "adjust" else "complete",
                busy = false,
                finishSession = !choosing,
                onContinue = {},
                onAdjust = {},
                onManual = {},
                onApplyPlanned = {},
                onApplyManual = {},
                onBack = {},
            )
        }
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
