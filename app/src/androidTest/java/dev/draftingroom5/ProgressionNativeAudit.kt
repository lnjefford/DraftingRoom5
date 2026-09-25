package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import java.io.File
import java.time.LocalDate

/** Exercises the production destination and Android accessibility tree with isolated audit data. */
internal class ProgressionNativeAudit(private val instrumentation: Instrumentation) {
    private val output = File(instrumentation.targetContext.filesDir, "progression-audit").apply { mkdirs() }
    private val report = StringBuilder()
    private lateinit var activity: MainActivity
    private lateinit var repository: AppRepository
    private lateinit var routine: Routine
    private val feedback = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val date = LocalDate.of(2026, 9, 19)

    fun run() {
        try {
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            mountEditor()
            reveal("Increase weight by 5 pounds")
            check(node("Decrease weight by 5 pounds") != null)
            click("Increase weight by 5 pounds")
            await { node("Weight value 40") != null }
            listOf("Increase duration by 5 seconds", "Increase sets by 1", "Increase reps by 1", "Planned progression").forEach { reveal(it) }
            check(node("Rest between sets") == null)
            check(node("Automatic progression") == null)
            check(node("After each exercise", prefix = true) == null)
            capture("structured-editor")
            report.appendLine("PASS: native editor exposes ordered target controls, exact 35 to 40 lb step, planned progression, and no removed progression/rest copy")

            mount()
            await { node("25 mm edge complete") != null }
            capture("custom-before")
            check(node("Next exercise") != null)
            check(node("Adjust exercise") != null)
            check(node("Increase weight by 5 pounds") == null)
            click("Adjust exercise")
            await { node("CURRENT PRESCRIPTION", prefix = true) != null }
            check(node("NEXT PLANNED STEP", prefix = true)?.contentDescription.toString().contains("Weight 40 lb"))
            check(node("NEXT PLANNED STEP", prefix = true)?.contentDescription.toString().contains("Three-finger drag"))
            check(node("Increase weight by 5 pounds") == null)
            click("Adjust manually")
            await { node("Increase weight by 5 pounds") != null }
            check(node("Save adjustment and go to next exercise", prefix = true) != null)
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            await { node("Apply planned step") != null }
            check(document().progressionReceipts.isEmpty())
            check(document().plan.routines.single() == routine)
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            await { node("Next exercise") != null }
            check(document().progressionReceipts.isEmpty())
            click("Next exercise")
            await { document().partialSessions.single().handledProgressionExerciseIds.contains("source") }
            check(document().plan.routines.single() == routine)
            report.appendLine("PASS: native sheet orders filled Next before secondary Adjust; controls remain hidden until manual choice; Back applies nothing; Next persists handled completion and leaves the queue unchanged")
            click("Start timer")
            await { document().partialSessions.single().timer.phase == TimerPhase.READY }
            val run = document().partialSessions.single().timer
            instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
            await { !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            instrumentation.runOnMainSync {
                activity.startActivity(Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            await { activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            await { node("Next hold") != null }
            check(document().partialSessions.single().timer.runId == run.runId)
            check(run.activeDeadlineElapsedMillis!! - run.readyDeadlineElapsedMillis!! == 30_000L)
            check(feedback.count { it == "haptic:TIMER_START" } == 1)
            check(feedback.count { it == "voice:CountdownStarted" } == 1)
            capture("timer-resumed")
            report.appendLine("PASS: native timer starts after Continue, retains run/deadline across background/foreground, and does not replay start voice/haptic callbacks")

            mount()
            await { node("25 mm edge complete") != null }
            click("Adjust exercise")
            await { node("Apply planned step") != null }
            click("Apply planned step")
            await { document().progressionReceipts.size == 1 }
            check(document().plan.routines.single().exercises.map { it.id } == listOf("source", "added", "next"))
            check(document().partialSessions.single().snapshot == routine)
            capture("custom-applied")
            click("Undo")
            await { document().progressionReceipts.single().undone }
            check(document().plan.routines.single().exercises == routine.exercises)
            report.appendLine("PASS: native custom apply inserts once; snackbar Undo restores source and removes addition")

            mount()
            await { node("25 mm edge complete") != null }
            click("Adjust exercise")
            click("Adjust manually")
            click("Increase weight by 5 pounds")
            click("Decrease duration by 5 seconds")
            click("Increase sets by 1")
            click("Increase reps by 1")
            await { node("Save adjustment and go to next exercise", prefix = true)?.contentDescription.toString()
                .contains("Weight 40 lb · Duration 25 seconds · Sets 2 · Reps 10") }
            click("Save adjustment and go to next exercise", prefix = true)
            await { document().progressionReceipts.size == 1 }
            val adjusted = document().plan.routines.single().exercises.first()
            check(adjusted.prescription() == routine.exercises.first().prescription().copy(
                weightPounds = 40, durationSeconds = 25, setCount = 2, reps = 10))
            check(adjusted.progression == routine.exercises.first().progression)
            capture("manual-applied")
            click("Undo")
            await { document().progressionReceipts.single().undone }
            check(document().plan.routines.single().exercises == routine.exercises)
            report.appendLine("PASS: native manual save applies four targets together, preserves the queued planned step, advances focus, and remains undoable")

            report.appendLine("PASS: Android API ${android.os.Build.VERSION.SDK_INT}; production Compose destination and accessibility actions")
            File(output, "results.txt").writeText(report.toString())
            instrumentation.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            report.appendLine("FAIL: ${failure.stackTraceToString()}")
            File(output, "results.txt").writeText(report.toString())
            instrumentation.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        }
    }

    private fun mountEditor() {
        val exercise = Exercise(
            "editor-source", "Dumbbell farmer's walk", "Tall posture", 3, 10, 30, "farmers_walk", 35,
            CustomExerciseProgression(listOf(CustomProgressionStep(
                ExercisePrescription("Dumbbell farmer's walk", "Tall posture", 3, 10, 30, "farmers_walk", 40),
            ))),
        )
        instrumentation.runOnMainSync {
            activity.setContent {
                DraftingRoom5Theme { ExerciseEditorScreen(exercise, {}, {}) }
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun mount() {
        feedback.clear()
        val source = Exercise("source", "25 mm edge", "Half crimp", 1, null, 30, "hangboard",
            35)
        val added = source.copy(id = "added", name = "Three-finger drag", progression = null)
        val progression = CustomExerciseProgression(listOf(CustomProgressionStep(
            source.copy(name = "20 mm edge", durationSeconds = 25, weightPounds = 40).prescription(), listOf(added))))
        routine = Routine("audit", 1, "Progression audit", "hangboard", RoutineExecution.GUIDED,
            listOf(source.copy(progression = progression), source.copy(id = "next", name = "Next hold")), null)
        val session = GuidedSession("audit-session", OccurrenceKey("audit-schedule", date), routine.id, routine,
            "next", mapOf("source" to 1, "next" to 0), SessionTimer(), 1, 1, 0)
        val initial = AppDocument(plan = TrainingPlan(listOf(routine), listOf(ScheduleEntry("audit-schedule", routine.id, setOf(date.dayOfWeek)))), partialSessions = listOf(session))
        val storage = AtomicJsonStorage(File(output, "isolated-document.json"))
        storage.write(encodeAppDocument(initial))
        repository = AppRepository(storage, AndroidSessionClock(instrumentation.targetContext)).also { it.load() }
        instrumentation.runOnMainSync {
            activity.setContent {
                DraftingRoom5Theme {
                    androidx.compose.runtime.key(repository) {
                        val foreground = androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                        }
                        androidx.compose.runtime.DisposableEffect(activity) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
                                foreground.value = activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                            }
                            activity.lifecycle.addObserver(observer)
                            onDispose { activity.lifecycle.removeObserver(observer) }
                        }
                        GuidedSessionDestination(repository, routine, "audit-schedule", date, foreground.value,
                            {}, { feedback.add("haptic:$it") }, { feedback.add("voice:$it") }, {}, {}, {}, { _, _ -> })
                    }
                }
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun document() = (repository.state.value as LoadState.Ready).value
    private fun node(label: String, prefix: Boolean = false): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val labels = listOf(node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty())
            if (labels.any { if (prefix) it.startsWith(label) else it == label }) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }
    private fun reveal(label: String, prefix: Boolean = false): AccessibilityNodeInfo {
        fun scroll(node: AccessibilityNodeInfo): Boolean {
            if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { if (scroll(it)) return true }
            return false
        }
        await {
            if (node(label, prefix) != null) true else {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root != null && !scroll(root)) {
                    // A partially expanded Material sheet can fit its content at full height;
                    // expand it by gesture before expecting its inner scroll actions.
                    val bounds = android.graphics.Rect().also(root::getBoundsInScreen)
                    val x = bounds.exactCenterX()
                    val startY = bounds.top + bounds.height() * .85f
                    val endY = bounds.top + bounds.height() * .35f
                    val down = SystemClock.uptimeMillis()
                    fun pointer(action: Int, y: Float) {
                        val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                    }
                    pointer(android.view.MotionEvent.ACTION_DOWN, startY)
                    for (step in 1..12) {
                        SystemClock.sleep(20)
                        pointer(android.view.MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 12)
                    }
                    pointer(android.view.MotionEvent.ACTION_UP, endY)
                }
                false
            }
        }
        return checkNotNull(node(label, prefix))
    }
    private fun click(label: String, prefix: Boolean = false) {
        var target = reveal(label, prefix)
        while (!target.isClickable) target = checkNotNull(target.parent) { "No clickable parent for $label" }
        target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!condition()) { check(SystemClock.uptimeMillis() < deadline) { "Native condition timed out" }; SystemClock.sleep(200) }
    }
    private fun capture(name: String) {
        val tree = StringBuilder()
        fun describe(node: AccessibilityNodeInfo, depth: Int) {
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            tree.appendLine("${"  ".repeat(depth)}text=${node.text}; description=${node.contentDescription}; clickable=${node.isClickable}; selected=${node.isSelected}; bounds=$bounds")
            for (i in 0 until node.childCount) node.getChild(i)?.let { describe(it, depth + 1) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { describe(it, 0) }
        File(output, "$name-tree.txt").writeText(tree.toString())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
