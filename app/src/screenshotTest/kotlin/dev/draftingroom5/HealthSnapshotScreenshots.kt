package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 1400)
@Preview(name = "Tall", widthDp = 412, heightDp = 1400)
@Preview(name = "Large text", widthDp = 360, heightDp = 1800, fontScale = 2f)
@Composable
fun HealthSnapshotScreenshots() {
    HealthSnapshotReviewPreview()
}
