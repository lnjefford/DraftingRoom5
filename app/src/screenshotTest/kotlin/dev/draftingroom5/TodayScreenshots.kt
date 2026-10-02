package dev.draftingroom5

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import java.time.ZoneOffset

private val previewMarketTime = LocalDate.of(2026, 10, 1).atTime(15, 30).toEpochSecond(ZoneOffset.UTC)
private val previewMarketQuotes = mapOf(
    "AAPL" to TodayMarketQuote("AAPL", 265.13, 262.44, "USD", previewMarketTime,
        listOf(258.0, 259.2, 258.7, 261.1, 260.5, 262.8, 261.9, 264.0, 263.4, 265.13)),
    "^IXIC" to TodayMarketQuote("^IXIC", 22_384.47, 22_310.25, "USD", previewMarketTime,
        listOf(22_120.0, 22_180.0, 22_160.0, 22_260.0, 22_230.0, 22_290.0, 22_270.0, 22_384.47)),
)

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun TodayWorkspaceScreenshots() {
    TodayWorkspacePreview()
}

@PreviewTest
@Preview(name = "Scrolled", widthDp = 412, heightDp = 1100)
@Composable
fun TodayScrolledScreenshot() {
    TodayWorkspacePreview(scrollOffset = 600)
}

@Composable
private fun TodayWorkspacePreview(scrollOffset: Int = 0) {
    DraftingRoom5Theme { TodayTheme {
        TodayWorkspaceScreen(
            document = defaultAppDocument(),
            onSwitchWorkspace = {},
            previewWeather = TodayWeather("Madison, Wisconsin", 64, 2, 7, 0),
            previewRetirementBalance = "\$815,582.00",
            previewRetirementTrend = listOf(
                TodayRetirementPoint(LocalDate.of(2026, 9, 1), 78_200_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 8), 79_450_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 16), 79_100_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 23), 80_620_000),
                TodayRetirementPoint(LocalDate.of(2026, 10, 1), 81_558_200),
            ),
            previewDate = LocalDate.of(2026, 10, 1),
            previewMarketQuotes = previewMarketQuotes,
            previewScrollOffset = scrollOffset,
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

@PreviewTest
@Preview(name = "Details", widthDp = 412, heightDp = 1100)
@Preview(name = "Details large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun TodayDetailsScreenshots() {
    DraftingRoom5Theme { TodayTheme {
        Column(Modifier.fillMaxSize().background(TodayBackground).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp)) {
            TodayMarketsEditorial(listOf("AAPL", "^IXIC"), previewMarketQuotes, onOpen = {})
            TodayTeamsEditorial(TodayTeam.entries.toList()) {}
            TodayRetirementEditorial("\$815,582.00", "Tracked retirement value", listOf(
                TodayRetirementPoint(LocalDate.of(2026, 9, 1), 78_200_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 8), 79_450_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 16), 79_100_000),
                TodayRetirementPoint(LocalDate.of(2026, 9, 23), 80_620_000),
                TodayRetirementPoint(LocalDate.of(2026, 10, 1), 81_558_200),
            )) {}
        }
    } }
}
