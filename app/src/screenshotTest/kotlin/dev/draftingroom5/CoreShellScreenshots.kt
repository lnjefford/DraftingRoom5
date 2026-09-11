package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest

class CoreShellScreens : PreviewParameterProvider<String> {
    override val values = sequenceOf("Dashboard", "Weight", "Settings", "Customization", "Schedule", "Routines", "Guided editor", "Linked editor", "Schedule editor", "Exercise builder")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun CoreShellScreenshots(@PreviewParameter(CoreShellScreens::class) screen: String) {
    CoreShellReviewPreview(screen)
}
