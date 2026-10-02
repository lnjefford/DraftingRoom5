package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun TodayWorkspaceScreenshots() {
    DraftingRoom5Theme { TodayTheme {
        TodayWorkspaceScreen(
            document = defaultAppDocument(),
            onSwitchWorkspace = {},
            previewWeather = TodayWeather("Madison, Wisconsin", 64, 2, 7, 0),
            previewRetirementBalance = "\$815,582.00",
            previewDate = LocalDate.of(2026, 10, 1),
        )
    } }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 412, heightDp = 1100)
@Preview(name = "Settings large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun TodaySettingsScreenshots() {
    DraftingRoom5Theme { TodayTheme {
        TodayWorkspaceScreen(
            document = defaultAppDocument(),
            onSwitchWorkspace = {},
            previewDate = LocalDate.of(2026, 10, 1),
            previewSettings = true,
        )
    } }
}
