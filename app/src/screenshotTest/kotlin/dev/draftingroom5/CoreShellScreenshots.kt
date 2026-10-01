package dev.draftingroom5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import dev.draftingroom5.retirement.ui.retirementAccountsPreviewState
import dev.draftingroom5.retirement.ui.retirementIntegratedPreviewState
import dev.draftingroom5.retirement.domain.ProviderEnvironment
import dev.draftingroom5.retirement.domain.HoldingAvailability
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.provider.ConnectionHomeContent
import dev.draftingroom5.retirement.provider.LinkedAccountData
import dev.draftingroom5.retirement.provider.PropertySearchContent
import dev.draftingroom5.retirement.provider.ReviewAccountsContent

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

class RetirementShellRoutes : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Overview",
        "Forecast",
        "Accounts",
        "Library",
        "Forecast settings",
    )
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RetirementShellScreenshots(@PreviewParameter(RetirementShellRoutes::class) screen: String) {
    val route = when (screen) {
        "Overview" -> AppRoute.RetirementOverview
        "Forecast" -> AppRoute.RetirementForecast
        "Accounts" -> AppRoute.RetirementAssets
        "Library" -> AppRoute.RetirementLibrary
        else -> AppRoute.RetirementForecastSettings
    }
    RetirementWorkspaceScreen(
        route, {}, {}, {}, {}, {},
        accountsPreviewState = if (route in setOf(AppRoute.RetirementOverview, AppRoute.RetirementForecast, AppRoute.RetirementAssets, AppRoute.RetirementLibrary)) {
            retirementIntegratedPreviewState()
        } else null,
    )
}

class RetirementAccountScreens : PreviewParameterProvider<String> {
    override val values = sequenceOf("Accounts", "Add account", "Manual detail", "Linked attention", "Balance history", "Edit account", "Update balance",
        "Property detail", "Property history", "Edit property")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RetirementAccountScreenshots(@PreviewParameter(RetirementAccountScreens::class) screen: String) {
    val route = when (screen) {
        "Accounts" -> AppRoute.RetirementAccounts
        "Add account" -> AppRoute.RetirementAddAsset
        "Manual detail" -> AppRoute.RetirementAccountDetail("preview-manual")
        "Linked attention" -> AppRoute.RetirementAccountDetail("preview-linked")
        "Balance history" -> AppRoute.RetirementAccountHistory("preview-manual")
        "Edit account" -> AppRoute.RetirementAccountEdit("preview-linked")
        "Update balance" -> AppRoute.RetirementAccountUpdate("preview-manual")
        "Property detail" -> AppRoute.RetirementPropertyDetail("preview-property")
        "Property history" -> AppRoute.RetirementPropertyHistory("preview-property")
        else -> AppRoute.RetirementPropertyEdit("preview-property")
    }
    RetirementWorkspaceScreen(route, {}, {}, {}, {}, {}, {}, retirementAccountsPreviewState())
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun LinkedAccountsHomeScreenshots() {
    RetirementTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                ConnectionHomeContent(ProviderEnvironment.SANDBOX, false, false, false, {}, {}, {}, {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun LinkedAccountsReviewScreenshots() {
    val account = LinkedAccountData(
        "preview-account", "Employer retirement plan", "1234", "401k", Money(125_034_56),
        java.time.LocalDate.of(2026, 9, 22), emptyList(), HoldingAvailability.COMPLETE,
    )
    RetirementTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                ReviewAccountsContent(listOf(account), emptyList(), false, {}, {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "Ready", widthDp = 412, heightDp = 900)
@Preview(name = "Large text", widthDp = 360, heightDp = 1000, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun PropertySearchScreenshots() {
    RetirementTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                PropertySearchContent(
                    credentialReady = true,
                    address = "500 Fixture Way, Madison, WI 53703",
                    busy = false,
                    onAddressChange = {},
                    onSetup = {},
                    onFind = {},
                    onManual = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Setup needed", widthDp = 320, heightDp = 800)
@Composable
fun PropertySearchSetupScreenshots() {
    RetirementTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                PropertySearchContent(false, "", false, {}, {}, {}, {})
            }
        }
    }
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
            "Routines" -> PlanManagementScreen(plan, emptyMap(), { true }, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, { _, _ -> }, {}, initialTab = 1)
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
            "Routines" -> PlanManagementScreen(plan, emptyMap(), { true }, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, { _, _ -> }, {}, initialTab = 1)
            "Editor" -> GuidedRoutineEditorScreen(routine, "Not scheduled", false, onPersist = { true }, onDelete = {}, onBack = {})
            else -> ExerciseEditorScreen(riceExercise, {}, {})
        }
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun CustomProgressionEditorScreenshots() {
    val added = Exercise(
        id = "future-three-finger", name = "Three-finger drag", notes = "Open hand", setCount = 3,
        reps = 10, durationSeconds = 15, artworkId = "hangboard", weightPounds = 45,
    )
    val source = Exercise(
        id = "fingerboard-source", name = "25 mm edge", notes = "Half crimp · shoulders engaged", setCount = 3,
        reps = 8, durationSeconds = 20, artworkId = "hangboard", weightPounds = 35,
        progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(
                ExercisePrescription("20 mm edge", "Half crimp", 3, 9, 20, "hangboard", 40),
                listOf(added),
            ),
            CustomProgressionStep(
                ExercisePrescription("15 mm edge", "Controlled grip", 4, 10, 15, "hangboard", 45),
            ),
        )),
    )
    DraftingRoom5Theme {
        CustomProgressionEditorScreen(source, source.progression as CustomExerciseProgression, {}, {})
    }
}

private fun previewRunRoute() = RunRoute(
    "preview-route", 1, "Neighborhood loop",
    listOf(
        RunRoutePoint(410_000_000, -870_000_000),
        RunRoutePoint(410_027_000, -870_017_000),
        RunRoutePoint(410_065_000, -870_009_000),
        RunRoutePoint(410_098_000, -869_971_000),
        RunRoutePoint(410_111_000, -869_931_000),
        RunRoutePoint(410_092_000, -869_897_000),
        RunRoutePoint(410_115_000, -869_858_000),
        RunRoutePoint(410_093_000, -869_821_000),
        RunRoutePoint(410_053_000, -869_811_000),
        RunRoutePoint(410_030_000, -869_840_000),
        RunRoutePoint(410_046_000, -869_887_000),
        RunRoutePoint(410_018_000, -869_933_000),
        RunRoutePoint(410_000_000, -870_000_000),
    ),
    listOf(0, 3, 6, 9, 12),
    listOf(RunTurnCue(1, RunTurnKind.LEFT, "Turn left at the path")),
)

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun StructuredExerciseEditorScreenshots() {
    val exercise = Exercise(
        id = "structured-carry", name = "Dumbbell farmer's walk", notes = "Tall posture · controlled pace",
        setCount = 3, reps = 10, durationSeconds = 30, artworkId = "farmers_walk", weightPounds = 35,
        progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(ExercisePrescription("Dumbbell farmer's walk", "Tall posture · controlled pace", 3, 10, 30, "farmers_walk", 40)),
            CustomProgressionStep(ExercisePrescription("Dumbbell farmer's walk", "Tall posture · controlled pace", 4, 10, 35, "farmers_walk", 45)),
        )),
    )
    DraftingRoom5Theme { ExerciseEditorScreen(exercise, {}, {}) }
}

class ProgressionDecisionCases : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(false, true)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun ProgressionDecisionScreenshots(@PreviewParameter(ProgressionDecisionCases::class) choosing: Boolean) {
    ProgressionDecisionReviewPreview(choosing)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun CustomProgressionDecisionScreenshots() {
    val source = Exercise("source", "25 mm edge", "Half crimp", 3, null, 20, "hangboard",
        35)
    val next = source.copy(name = "20 mm edge", weightPounds = 40, durationSeconds = 15)
    val option = ProgressionOption(ProgressionChoice.CUSTOM, next,
        listOf(source.copy(id = "addition", name = "Three-finger drag")))
    DraftingRoom5Theme {
        androidx.compose.material3.Surface {
            ProgressionDecisionSheetContent(
                exercise = source,
                options = listOf(option),
                mode = "manual",
                busy = false,
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

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun CustomStepEditorScreenshots(@PreviewParameter(ProgressionDecisionCases::class) bottom: Boolean) {
    val source = Exercise("step-source", "Loaded carry", "Tall posture", 3, 8, 30, "farmers_walk", 35)
    val before = source.prescription().copy(weightPounds = 40, reps = 10)
    val step = CustomProgressionStep(before.copy(weightPounds = 45, setCount = 4),
        listOf(source.copy(id = "added-carry", name = "Suitcase carry")))
    DraftingRoom5Theme {
        CustomStepEditorScreen(source, before, step, {}, {}, initialScrollPx = if (bottom) 10000 else 0)
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun StructuredEditorBottomScreenshots() {
    val source = Exercise("bottom-source", "Loaded carry", "Tall posture", 3, 8, 30, "farmers_walk", 35)
    DraftingRoom5Theme { ExerciseEditorScreen(source, {}, {}, initialScrollPx = 10000) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun StructuredTargetBoundaryScreenshots() {
    DraftingRoom5Theme {
        androidx.compose.material3.Surface {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(20.dp)
                .verticalScroll(rememberScrollState())) {
                StructuredTargetControls("2147483645", {}, true, {}, "2147483647", {}, "2147483647", {}, "2147483647", {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun StructuredTargetPlacementScreenshots() {
    DraftingRoom5Theme {
        androidx.compose.material3.Surface {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(20.dp)
                .verticalScroll(rememberScrollState())) {
                StructuredTargetControls("", {}, false, {}, "", {}, "4", {}, "12", {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RunRoutineEditorScreenshots() {
    val routine = Routine(
        id = "run-preview",
        revision = 1,
        name = "Neighborhood intervals",
        artworkId = "running_shoe",
        execution = RoutineExecution.RUN,
        exercises = emptyList(),
        appLink = null,
        run = RunRoutine(listOf(
            RunInterval("warmup", RunIntervalKind.WALK, 300),
            RunInterval("run-one", RunIntervalKind.RUN, 120),
            RunInterval("recovery", RunIntervalKind.WALK, 60),
            RunInterval("run-two", RunIntervalKind.RUN, 120),
        ), routeId = "preview-route"),
    )
    DraftingRoom5Theme {
        RunRoutineEditorScreen(
            routine = routine,
            routes = listOf(previewRunRoute()),
            scheduleSummary = "Scheduled Mon · Wed · Fri", isNew = false,
            onPersist = { true }, onDelete = {}, onBack = {}, onSaveRoute = { true },
        )
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunNewRoutineScreenshots() {
    val routine = Routine("new-run-preview", 1, "Neighborhood intervals", "running_shoe",
        RoutineExecution.RUN, emptyList(), null,
        RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), previewRunRoute().id))
    DraftingRoom5Theme {
        RunRoutineEditorScreen(routine, listOf(previewRunRoute()), "Not scheduled", true,
            { true }, {}, {}, onSaveRoute = { true })
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunDashboardScreenshots() {
    RunDashboardPreviewContent(false)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunDashboardResumeScreenshots() {
    RunDashboardPreviewContent(true)
}

@Composable
private fun RunDashboardPreviewContent(resume: Boolean) {
    val route = previewRunRoute()
    val routine = Routine("dashboard-run", 1, "Neighborhood intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), route.id))
    val date = java.time.LocalDate.parse("2026-09-29")
    val occurrence = OccurrenceKey("dashboard-entry", date)
    val card = DashboardSession(ScheduleEntry("dashboard-entry", routine.id, setOf(date.dayOfWeek)),
        routine, if (resume) SessionAction.RESUME else SessionAction.START,
        occurrence = occurrence, effectiveDate = date)
    val live = if (resume) RunSession("dashboard-live", occurrence, routine, route,
        1_000, 380_000, null, 850, emptyList()) else null
    DashboardSessionPreviewContent(listOf(card), route, live, showHero = true)
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunScheduleScreenshots() {
    val routine = Routine("schedule-run", 1, "Neighborhood intervals", "running_shoe",
        RoutineExecution.RUN, emptyList(), null,
        RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), previewRunRoute().id))
    val day = java.time.DayOfWeek.TUESDAY
    val entry = ScheduleEntry("schedule-entry", routine.id, setOf(day))
    val plan = defaultTrainingPlan().copy(routines = listOf(routine), schedule = listOf(entry))
    DraftingRoom5Theme {
        ScheduleEditorScreen(plan, entry, "schedule-draft", day, onSave = { true }, onBack = {})
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RunRoutesScreenshots() {
    val route = previewRunRoute()
    DraftingRoom5Theme { RunRouteScreen(listOf(route), { true }, { true }, {}) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunRoutePickerScreenshots() {
    DraftingRoom5Theme {
        RunRouteScreen(listOf(previewRunRoute()), { true }, { true }, {}, onSelectRoute = {})
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun RunRouteDetailScreenshots() {
    val route = previewRunRoute()
    DraftingRoom5Theme { RunRouteScreen(listOf(route), { true }, { true }, {}, initialSelectedId = route.id) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunRouteMissingDirectionsScreenshots() {
    val route = previewRunRoute().copy(turnCues = emptyList())
    DraftingRoom5Theme { RunRouteScreen(listOf(route), { true }, { true }, {}, initialSelectedId = route.id) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunRouteGuidanceEditorScreenshots() {
    val route = previewRunRoute()
    DraftingRoom5Theme {
        RunRouteGuidanceEditor(WalkingRoute(route.points, route.waypointIndices, route.turnCues),
            true, false, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Composable
fun RunRouteNoTurnsEditorScreenshots() {
    val route = previewRunRoute()
    DraftingRoom5Theme {
        RunRouteGuidanceEditor(WalkingRoute(route.points, route.waypointIndices, emptyList()),
            true, false, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun RunStartScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 120)), route.id))
    DraftingRoom5Theme { RunStartScreen(routine, listOf(route), route.id, { true }, {}) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun RunActiveScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 120)), route.id))
    val session = RunSession("preview-session", OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29")),
        routine, route, 1_000, 0, 1_000, 820,
        listOf(RunLocationSample(RunRoutePoint(410_000_000, -870_000_000), 2_000, 5)))
    DraftingRoom5Theme { RunSessionScreen(session, { true }, { true }, { true }, {},
        music = { SpotifyPreviewRow() }, reviewNow = 61_000) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunPausedScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), route.id))
    val session = RunSession("preview-paused", OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29")),
        routine, route, 1_000, 380_000, null, 850, emptyList())
    DraftingRoom5Theme { RunSessionScreen(session, { true }, { true }, { true }, {}, reviewNow = 381_000) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunEndEarlyScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), route.id))
    val session = RunSession("preview-end", OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29")),
        routine, route, 1_000, 380_000, null, 850, emptyList())
    DraftingRoom5Theme { RunSessionScreen(session, { true }, { true }, { true }, {},
        reviewNow = 381_000, reviewConfirmFinish = true) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunTimingCompleteScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), route.id))
    val session = RunSession("preview-complete", OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29")),
        routine, route, 1_000, 0, 1_000, 2100, emptyList())
    DraftingRoom5Theme { RunSessionScreen(session, { true }, { true }, { true }, {}, reviewNow = 950_000) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunHistoryScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("walk", RunIntervalKind.WALK, 300),
            RunInterval("run", RunIntervalKind.RUN, 600)), route.id))
    val occurrence = OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29"))
    val session = RunSession("preview-session", occurrence, routine, route, 1_000, 900_000, null, 2100,
        emptyList(), 901_000)
    val history = WorkoutHistoryEntry(session.id, occurrence, routine, 1_000, 901_000)
    DraftingRoom5Theme { RunHistoryScreen(routine, listOf(history), listOf(session), {}, {}) }
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Composable
fun RunCompletionScreenshots() {
    val route = previewRunRoute()
    val routine = Routine("preview-run", 1, "Morning intervals", "running_shoe", RoutineExecution.RUN,
        emptyList(), null, RunRoutine(listOf(RunInterval("run", RunIntervalKind.RUN, 120)), route.id))
    val occurrence = OccurrenceKey("preview-schedule", java.time.LocalDate.parse("2026-09-29"))
    val session = RunSession("preview-session", occurrence, routine, route, 1_000, 120_000, null, 840,
        listOf(RunLocationSample(RunRoutePoint(410_000_000, -870_000_000), 2_000, 5),
            RunLocationSample(RunRoutePoint(410_001_000, -870_001_000), 60_000, 5)), 121_000)
    val history = WorkoutHistoryEntry(session.id, occurrence, routine, 1_000, 121_000)
    DraftingRoom5Theme { RunCompletionScreen(history, session, {}) }
}
