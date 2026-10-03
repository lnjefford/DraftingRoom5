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
import java.time.Instant
import java.time.ZoneOffset

private val previewMarketTime = LocalDate.of(2026, 10, 1).atTime(15, 30).toEpochSecond(ZoneOffset.UTC)
private val previewMarketQuotes = mapOf(
    "AAPL" to TodayMarketQuote("AAPL", 265.13, 262.44, "USD", previewMarketTime,
        listOf(258.0, 259.2, 258.7, 261.1, 260.5, 262.8, 261.9, 264.0, 263.4, 265.13)),
    "^IXIC" to TodayMarketQuote("^IXIC", 22_384.47, 22_310.25, "USD", previewMarketTime,
        listOf(22_120.0, 22_180.0, 22_160.0, 22_260.0, 22_230.0, 22_290.0, 22_270.0, 22_384.47)),
)
private val previewTeamGames = mapOf(
    TodayTeam.BREWERS to TodayNextGame(TodayTeam.BREWERS, "Padres",
        Instant.parse("2026-10-04T00:30:00Z"), true, true, null, null),
    TodayTeam.PACKERS to TodayNextGame(TodayTeam.PACKERS, "Buccaneers",
        Instant.parse("2026-10-04T17:00:00Z"), false, true, null, null),
    TodayTeam.INDIANA_FOOTBALL to TodayNextGame(TodayTeam.INDIANA_FOOTBALL, "Rutgers",
        Instant.parse("2026-10-03T19:00:00Z"), false, true, null, null),
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
            previewDate = LocalDate.of(2026, 10, 1),
            previewMarketQuotes = previewMarketQuotes,
            previewTeamGames = previewTeamGames,
            previewScrollOffset = scrollOffset,
        )
    } }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 412, heightDp = 1100)
@Preview(name = "Settings large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Composable
fun TodaySettingsScreenshots() {
    TodaySettingsPreview()
}

@PreviewTest
@Preview(name = "Ordering", widthDp = 412, heightDp = 1100)
@Composable
fun TodaySettingsOrderingScreenshot() {
    TodaySettingsPreview(scrollOffset = 620)
}

@Composable
private fun TodaySettingsPreview(scrollOffset: Int = 0) {
    DraftingRoom5Theme { TodayTheme {
        TodayWorkspaceScreen(
            document = defaultAppDocument(),
            onSwitchWorkspace = {},
            previewDate = LocalDate.of(2026, 10, 1),
            previewSettings = true,
            previewSettingsScrollOffset = scrollOffset,
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
            TodayTeamsEditorial(listOf(TodayTeam.INDIANA_FOOTBALL, TodayTeam.INDIANA_BASKETBALL) +
                TodayTeam.entries.filter { it != TodayTeam.INDIANA_FOOTBALL && it != TodayTeam.INDIANA_BASKETBALL },
                previewTeamGames, onOpen = {})
        }
    } }
}
