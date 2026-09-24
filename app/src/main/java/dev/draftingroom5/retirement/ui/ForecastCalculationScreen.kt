package dev.draftingroom5.retirement.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.draftingroom5.R
import dev.draftingroom5.RetirementBackground
import dev.draftingroom5.RetirementBackgroundDeep
import dev.draftingroom5.RetirementBlue
import dev.draftingroom5.RetirementGold
import dev.draftingroom5.RetirementHighlight
import dev.draftingroom5.RetirementSurfaceRaised
import dev.draftingroom5.RetirementTextSecondary

@Composable
internal fun ForecastCalculationScreen(animated: Boolean = true) {
    val phase = if (!animated || LocalInspectionMode.current) .42f else {
        val transition = rememberInfiniteTransition(label = "forecast calculation")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "forecast path progress",
        )
        animatedPhase
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(RetirementSurfaceRaised.copy(alpha = .48f), RetirementBackground, RetirementBackgroundDeep),
                radius = 980f,
            ),
        ).semantics { contentDescription = "Calculating forecast. Results will appear when complete." },
    ) {
        Image(
            painter = painterResource(R.drawable.finance_planning_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = .30f,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(.78f).height(520.dp),
        )
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val start = Offset(0f, size.height * .58f)
                val endOffsets = listOf(-.25f, -.12f, 0f, .13f, .27f)
                val colors = listOf(RetirementBlue, RetirementHighlight, RetirementGold, RetirementHighlight, RetirementBlue)
                endOffsets.forEachIndexed { index, offset ->
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            size.width * .26f, size.height * (.60f - offset * .12f),
                            size.width * .62f, size.height * (.43f + offset * .72f),
                            size.width, size.height * (.43f + offset),
                        )
                    }
                    drawPath(
                        path,
                        colors[index].copy(alpha = if (index == 2) .88f else .42f),
                        style = Stroke(
                            width = if (index == 2) 2.5.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 11.dp.toPx()), -phase * 36.dp.toPx()),
                        ),
                    )
                }
                val t = phase
                val inverse = 1f - t
                val p0 = start
                val p1 = Offset(size.width * .26f, size.height * .60f)
                val p2 = Offset(size.width * .62f, size.height * .43f)
                val p3 = Offset(size.width, size.height * .43f)
                val marker = Offset(
                    inverse * inverse * inverse * p0.x + 3f * inverse * inverse * t * p1.x + 3f * inverse * t * t * p2.x + t * t * t * p3.x,
                    inverse * inverse * inverse * p0.y + 3f * inverse * inverse * t * p1.y + 3f * inverse * t * t * p2.y + t * t * t * p3.y,
                )
                drawCircle(RetirementGold.copy(alpha = .18f), 13.dp.toPx(), marker)
                drawCircle(RetirementGold, 4.5.dp.toPx(), marker)
                drawCircle(Color.White.copy(alpha = .86f), 1.5.dp.toPx(), marker)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "CALCULATING FORECAST",
                color = RetirementHighlight,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Modeling possible paths through retirement.",
                color = RetirementTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
