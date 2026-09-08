package dev.draftingroom5

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val AppBackground = Color(0xFF07111F)
internal val AppBackgroundDeep = Color(0xFF030912)
internal val AppSurface = Color(0xFF101D2D)
internal val AppSurfaceRaised = Color(0xFF17283C)
internal val AppBlue = Color(0xFF76A9FF)
internal val AppBlueStrong = Color(0xFF3F7FE8)
internal val AppMint = Color(0xFF4FE0B0)
internal val AppGold = Color(0xFFFFC66D)
internal val AppCompleted = Color(0xFF10372E)
internal val AppBorder = Color(0xFF263B53)

private val DarkColors = darkColorScheme(
    primary = AppBlue,
    onPrimary = Color(0xFF051A35),
    primaryContainer = Color(0xFF173A65),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = AppMint,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = AppCompleted,
    onSecondaryContainer = Color(0xFFB9F6DE),
    tertiary = AppGold,
    onTertiary = Color(0xFF402D00),
    background = AppBackground,
    onBackground = Color(0xFFF1F5FC),
    surface = AppSurface,
    onSurface = Color(0xFFF1F5FC),
    surfaceVariant = AppSurfaceRaised,
    onSurfaceVariant = Color(0xFFB9C8DA),
    outline = Color(0xFF8FA2B9),
    outlineVariant = AppBorder,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val AppTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

internal fun Modifier.appScreenBackground(): Modifier = background(
    Brush.verticalGradient(
        0f to Color(0xFF0C1B2E),
        0.42f to AppBackground,
        1f to AppBackgroundDeep,
    ),
)

@Composable
internal fun BrandedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = AppSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.9f)),
        content = content,
    )
}

@Composable
internal fun BrandTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(AppBlueStrong, AppMint))),
            contentAlignment = Alignment.Center,
        ) {
            Text("5", color = Color(0xFF041424), fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null, eyebrow: String? = null) {
    androidx.compose.foundation.layout.Column {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                color = AppMint,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.4.sp,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DraftingRoom5Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
