package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest

class CoreShellScreens : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        "Dashboard", "Weight", "Settings", "Customization", "Schedule", "Routines", "Guided editor", "Linked editor",
        "Schedule editor", "Exercise builder", "Session idle", "Session ready", "Session running", "Session finished",
        "Session completion",
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

class SessionControlStates : PreviewParameterProvider<String> {
    override val values = sequenceOf("Session idle", "Session ready", "Session running", "Session finished", "Session many sets")
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
