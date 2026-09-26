package dev.draftingroom5.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest

class WatchScreenCases : PreviewParameterProvider<String> {
    override val values = sequenceOf("Today", "Exercise", "Ready", "Running", "Set complete", "Complete")
}

@PreviewTest
@Preview(name = "41 mm", widthDp = 192, heightDp = 192)
@Preview(name = "45 mm", widthDp = 228, heightDp = 228)
@Composable
fun WatchScreenshots(@PreviewParameter(WatchScreenCases::class) screen: String) {
    WatchReviewPreview(screen)
}
