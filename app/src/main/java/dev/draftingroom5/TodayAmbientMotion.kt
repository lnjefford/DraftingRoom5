package dev.draftingroom5

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin

/** Quiet, decorative motion behind the Today hero copy. The scene image remains the source of truth. */
@Composable
internal fun TodayAmbientMotion(kind: TodayWeatherKind, season: TodaySeason) {
    val transition = rememberInfiniteTransition(label = "Today atmosphere")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "Atmosphere drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        when (kind) {
            TodayWeatherKind.RAIN, TodayWeatherKind.STORM, TodayWeatherKind.SNOW -> {
                repeat(36) { index ->
                    val x = index * 67.3f % size.width
                    val y = (index * 113.7f + phase * size.height * 1.7f) % size.height
                    if (kind == TodayWeatherKind.SNOW) {
                        val sway = sin((phase * 6.28f + index).toDouble()).toFloat() * 7f
                        drawCircle(Color.White.copy(alpha = .42f), 2.2f, Offset(x + sway, y))
                    } else {
                        drawLine(TodayLavender.copy(alpha = .25f), Offset(x, y),
                            Offset(x - 5f, y + 18f), 1.2f, cap = StrokeCap.Round)
                    }
                }
            }
            else -> when (season) {
                TodaySeason.SPRING, TodaySeason.AUTUMN -> repeat(11) { index ->
                    val progress = (phase + index * .137f) % 1f
                    val sway = sin((progress * 9f + index).toDouble()).toFloat() * 14f
                    val x = (index * 97.3f + progress * size.width * .22f + sway) % size.width
                    val y = progress * (size.height + 36f) - 18f
                    val center = Offset(x, y)
                    val color = if (season == TodaySeason.SPRING)
                        listOf(Color(0xFFFFD6E5), Color(0xFFFFEAF2), Color(0xFFF5B9D0))[index % 3]
                    else listOf(Color(0xFFFFB35C), Color(0xFFD88442), Color(0xFFF2C477))[index % 3]
                    rotate(progress * 280f + index * 29f, center) {
                        drawOval(color.copy(alpha = .36f),
                            topLeft = Offset(x - 4f, y - 2f), size = Size(8f, 4f))
                    }
                }
                TodaySeason.SUMMER -> repeat(14) { index ->
                    val shimmer = (phase + index * .173f) % 1f
                    val x = (index * 83.7f) % size.width
                    val y = size.height * (.54f + index % 5 * .085f)
                    val width = 5f + shimmer * 11f
                    drawLine(Color(0xFFFFD9A3).copy(alpha = .08f + .16f * (1f - shimmer)),
                        Offset(x, y), Offset(x + width, y), 1.5f, cap = StrokeCap.Round)
                }
                TodaySeason.WINTER -> repeat(9) { index ->
                    val glow = (phase + index * .211f) % 1f
                    val alpha = .06f + .2f * (1f - glow)
                    val x = (index * 101.3f) % size.width
                    val y = size.height * (.34f + index % 4 * .15f)
                    val color = Color(0xFFDDEEFF).copy(alpha = alpha)
                    drawLine(color, Offset(x - 3f, y), Offset(x + 3f, y), 1f)
                    drawLine(color, Offset(x, y - 3f), Offset(x, y + 3f), 1f)
                }
            }
        }
    }
}
