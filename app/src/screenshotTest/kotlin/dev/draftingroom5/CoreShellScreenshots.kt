package dev.draftingroom5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import java.time.DayOfWeek

class CoreShellScreens : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Dashboard", "Weight", "Settings", "Customization", "Schedule", "Routines", "Guided editor", "Linked editor",
        "Schedule editor", "Exercise builder", "Session idle", "Session ready", "Session running", "Session finished",
        "Session completion", "Dashboard update working", "Settings voice expanded", "Guided editor actions",
    )
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun CoreShellScreenshots(@PreviewParameter(CoreShellScreens::class) screen: String) {
    CoreShellReviewPreview(screen)
}

class LinkedCompletionStates : PreviewParameterProvider<String> {
    override val values = sequenceOf("Dashboard linked open", "Dashboard linked done")
}

class OccurrenceMenuStates : PreviewParameterProvider<String> {
    override val values = sequenceOf("Dashboard defer menu", "Dashboard deferred")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun OccurrenceMenuScreenshots(@PreviewParameter(OccurrenceMenuStates::class) screen: String) {
    CoreShellReviewPreview(screen)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun LinkedCompletionScreenshots(@PreviewParameter(LinkedCompletionStates::class) screen: String) {
    CoreShellReviewPreview(screen)
}

class SessionControlStates : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Session idle", "Session ready", "Session running", "Session finished",
        "Session no timer", "Session all sets complete", "Session many sets",
        "Session six sets", "Session longest timer",
    )
}

class SessionSectionStates : PreviewParameterProvider<String> {
    override val values = sequenceOf("Session sections", "Session completed only", "Session upcoming only")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun SessionSectionScreenshots(@PreviewParameter(SessionSectionStates::class) state: String) {
    GuidedSessionReviewPreview(state, sectionsOnly = true)
}

class SessionArtworkCases : PreviewParameterProvider<String> {
    override val values = (ExerciseArtworkCatalog.entries.map(ExerciseArtworkAsset::storageId) + "long_name").asSequence()
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun SessionArtworkScreenshots(@PreviewParameter(SessionArtworkCases::class) artworkId: String) {
    GuidedSessionArtworkReviewPreview(artworkId)
}

class ExceptionalStates : PreviewParameterProvider<String> {
    override val values = HardeningState.entries.asSequence().map { it.fixtureName }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun ExceptionalStateScreenshots(@PreviewParameter(ExceptionalStates::class) state: String) {
    CoreShellReviewPreview(state)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun SessionControlsScreenshots(@PreviewParameter(SessionControlStates::class) state: String) {
    GuidedSessionReviewPreview(state, controlsOnly = true)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 640)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun PrivacyScreenshots() {
    DraftingRoom5Theme { HealthPrivacyScreen({}) }
}

class SelectionSheetCases : PreviewParameterProvider<String> {
    override val values = sequenceOf("Exercise artwork", "Routine artwork", "Exercise hangboard", "Routine hangboard", "Exercise rice bag")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 640)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun SelectionSheetScreenshots(@PreviewParameter(SelectionSheetCases::class) sheet: String) {
    DraftingRoom5Theme {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.BottomCenter) {
            if (sheet.startsWith("Exercise")) {
                PersistentSelectionSheetContent(
                    "Choose exercise artwork",
                    "One choice supplies the paired list and session images.",
                    {}, {},
                ) { ExerciseArtworkSelection(when (sheet) {
                    "Exercise hangboard" -> "hangboard"
                    "Exercise rice bag" -> "rice_bag"
                    else -> ExerciseArtworkCatalog.FALLBACK_ID
                }) {} }
            } else {
                PersistentSelectionSheetContent(
                    "Choose artwork",
                    "Designed to stay consistent across session cards.",
                    {}, {},
                ) { RoutineArtworkSelection(if (sheet == "Routine hangboard") "hangboard" else RoutineArtworkCatalog.FALLBACK_ID) {} }
            }
        }
    }
}

class HangboardRoutineCases : PreviewParameterProvider<String> {
    override val values = sequenceOf("Routines", "Editor", "Schedule")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun HangboardRoutineScreenshots(@PreviewParameter(HangboardRoutineCases::class) screen: String) {
    val initial = defaultTrainingPlan()
    val routine = Routine(
        "review-hangboard", 1, "Hangboard session", "hangboard", RoutineExecution.GUIDED,
        listOf(initial.routines.last().exercises.first().copy(id = "review-hangboard-holds")), null,
    )
    val occurrence = ScheduleEntry("review-hangboard-entry", routine.id, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
    val plan = initial.copy(routines = listOf(routine) + initial.routines, schedule = initial.schedule + occurrence)
    DraftingRoom5Theme {
        when (screen) {
            "Routines" -> PlanManagementScreen(plan, emptyMap(), { true }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, initialTab = 1)
            "Editor" -> GuidedRoutineEditorScreen(routine, "Scheduled Tue · Fri", false, onPersist = { true }, onDelete = {}, onBack = {})
            else -> ScheduleEditorScreen(plan, occurrence, "review-hangboard-entry", DayOfWeek.TUESDAY, onSave = { true }, onBack = {})
        }
    }
}

class RiceBagArtworkCases : PreviewParameterProvider<String> {
    override val values = sequenceOf("Routines", "Editor", "Exercise editor")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun RiceBagArtworkScreenshots(@PreviewParameter(RiceBagArtworkCases::class) screen: String) {
    val initial = defaultTrainingPlan()
    val riceExercise = initial.routines.last().exercises.first().copy(
        id = "review-rice-bag", name = "Rice bag grip work", notes = "Practice at your own pace", artworkId = "rice_bag",
    )
    val routine = initial.routines.last().copy(
        id = "review-rice-bag-routine", name = "Grip work", exercises = listOf(riceExercise),
    )
    val plan = initial.copy(routines = listOf(routine) + initial.routines)
    DraftingRoom5Theme {
        when (screen) {
            "Routines" -> PlanManagementScreen(plan, emptyMap(), { true }, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, initialTab = 1)
            "Editor" -> GuidedRoutineEditorScreen(routine, "Not scheduled", false, onPersist = { true }, onDelete = {}, onBack = {})
            else -> ExerciseEditorScreen(riceExercise, {}, {})
        }
    }
}
