package dev.draftingroom5

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
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

internal val EditorialSerif = FontFamily(Font(R.font.dm_serif_display_regular, FontWeight.Normal))

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = EditorialSerif, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
        displayMedium = displayMedium.copy(fontFamily = EditorialSerif, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp),
        headlineLarge = headlineLarge.copy(fontFamily = EditorialSerif, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = EditorialSerif, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

internal fun Modifier.appScreenBackground(): Modifier = background(AppBackground)

@Composable
internal fun MeasuredFiveMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.measured_five_foreground),
        contentDescription = null,
        modifier = modifier.size(46.dp),
    )
}

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
internal fun BrandTitle(
    title: String,
    animateOnEntry: Boolean = false,
    onAnimationFinished: () -> Unit = {},
) {
    val motionEnabled = animateOnEntry && ValueAnimator.areAnimatorsEnabled()
    val progress = remember(animateOnEntry) { Animatable(if (motionEnabled) 0f else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = BRAND_ENTRY_DURATION_MILLIS, easing = FastOutSlowInEasing),
            )
        } else {
            progress.snapTo(1f)
        }
        if (animateOnEntry) onAnimationFinished()
    }

    Row(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            scaleX = 0.94f + (0.06f * progress.value)
            scaleY = 0.94f + (0.06f * progress.value)
            translationX = (1f - progress.value) * 12f
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasuredFiveMark()
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

internal const val BRAND_ENTRY_DURATION_MILLIS = 320

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

@Composable
private fun VisualFoundationPreview() {
    DraftingRoom5Theme {
        androidx.compose.foundation.layout.Column(
            Modifier.appScreenBackground().padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            BrandTitle("DraftingRoom5")
            EditorialHeading("Visual foundation", "Measured, calm, and ready to train.", "Current system")
            AppSurfaceCard {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Text("Raised navy surface", style = MaterialTheme.typography.titleMedium)
                    Text("Ivory hierarchy, blue action, mint completion, and restrained gold detail.")
                    Button(onClick = {}) { Text("Primary action") }
                    Button(onClick = {}, enabled = false) { Text("Unavailable action") }
                }
            }
        }
    }
}

@Preview(name = "Visual foundation compact", widthDp = 320, heightDp = 568)
@Composable
private fun VisualFoundationCompactPreview() = VisualFoundationPreview()

@Preview(name = "Visual foundation tall", widthDp = 411, heightDp = 891)
@Composable
private fun VisualFoundationTallPreview() = VisualFoundationPreview()

@Preview(name = "Visual foundation large font", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun VisualFoundationLargeFontPreview() = VisualFoundationPreview()
