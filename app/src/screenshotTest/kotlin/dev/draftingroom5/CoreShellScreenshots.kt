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
fun AutomaticProgressionEditorScreenshots() {
    val exercise = Exercise(
        id = "progression-preview",
        name = "Loaded farmer hold",
        notes = "Keep shoulders down and walk smoothly",
        setCount = 3,
        target = "Heavy timed hold",
        timerSeconds = 30,
        artworkId = "farmers_walk",
        measurements = ExerciseMeasurements(weightPounds = 35.0, durationSeconds = 30),
        progression = AutomaticExerciseProgression(
            weightPounds = AutomaticPoundsProgression(increment = 2.5, minimum = 20.0, maximum = 50.0),
            durationSeconds = AutomaticSecondsProgression(increment = 5, minimum = 20, maximum = 45),
        ),
    )
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val initialScrollPx = with(density) {
        when {
            density.fontScale > 1.3f -> 720.dp.roundToPx()
            configuration.screenHeightDp <= 400 -> 430.dp.roundToPx()
            else -> 400.dp.roundToPx()
        }
    }
    DraftingRoom5Theme { ExerciseEditorScreen(exercise, {}, {}, initialScrollPx) }
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
        target = "15 sec", timerSeconds = 15, artworkId = "hangboard",
        measurements = ExerciseMeasurements(durationSeconds = 15),
        progression = AutomaticExerciseProgression(durationSeconds = AutomaticSecondsProgression(5, maximum = 30)),
    )
    val source = Exercise(
        id = "fingerboard-source", name = "25 mm edge", notes = "Half crimp · shoulders engaged", setCount = 3,
        target = "20 sec", timerSeconds = 20, artworkId = "hangboard", measurements = ExerciseMeasurements(durationSeconds = 20),
        progression = CustomExerciseProgression(listOf(
            CustomProgressionStep(
                ExercisePrescription("20 mm edge", "Half crimp", 3, "20 sec", 20, "hangboard", ExerciseMeasurements(durationSeconds = 20)),
                listOf(added),
            ),
            CustomProgressionStep(
                ExercisePrescription("15 mm edge", "Controlled grip", 4, "15 sec", 15, "hangboard", ExerciseMeasurements(weightPounds = 5.0, durationSeconds = 15)),
            ),
        )),
    )
    DraftingRoom5Theme {
        CustomProgressionEditorScreen(source, source.progression as CustomExerciseProgression, {}, {})
    }
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
    val source = Exercise("source", "25 mm edge", "Half crimp", 3, "Controlled hold", 20, "hangboard",
        ExerciseMeasurements(5.0, 20))
    val next = source.copy(name = "20 mm edge", measurements = ExerciseMeasurements(7.5, 15), timerSeconds = 15)
    val option = ProgressionOption(ProgressionChoice.CUSTOM, next,
        listOf(source.copy(id = "addition", name = "Three-finger drag")))
    DraftingRoom5Theme {
        androidx.compose.material3.Surface {
            ProgressionDecisionSheetContent(source, listOf(option), false, ProgressionChoice.CUSTOM,
                false, {}, {}, {}, {}, {})
        }
    }
}
