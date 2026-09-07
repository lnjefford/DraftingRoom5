package dev.draftingroom5

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val AppBackground = Color(0xFF0B121C)
internal val AppSurface = Color(0xFF111C2A)
internal val AppSurfaceRaised = Color(0xFF182638)
internal val AppBlue = Color(0xFF78A6FF)
internal val AppMint = Color(0xFF52E3B1)
internal val AppCompleted = Color(0xFF12382F)

private val DarkColors = darkColorScheme(
    primary = AppBlue,
    onPrimary = Color(0xFF071A34),
    primaryContainer = Color(0xFF18385F),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = AppMint,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = AppCompleted,
    onSecondaryContainer = Color(0xFFB9F6DE),
    background = AppBackground,
    onBackground = Color(0xFFE7EDF7),
    surface = AppSurface,
    onSurface = Color(0xFFE7EDF7),
    surfaceVariant = AppSurfaceRaised,
    onSurfaceVariant = Color(0xFFBAC7D9),
    outline = Color(0xFF8492A6),
    outlineVariant = Color(0xFF3A485B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
internal fun DraftingRoom5Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
