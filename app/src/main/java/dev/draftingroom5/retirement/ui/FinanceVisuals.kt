package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.draftingroom5.RetirementBlue
import dev.draftingroom5.RetirementBackground
import dev.draftingroom5.RetirementGold
import dev.draftingroom5.RetirementHighlight
import dev.draftingroom5.RetirementPrimary
import dev.draftingroom5.RetirementTextSecondary
import dev.draftingroom5.retirement.domain.Money
import java.util.Locale
import kotlin.math.abs

internal val FinanceStreamColors = listOf(
    RetirementPrimary,
    RetirementBlue,
    RetirementGold,
    Color(0xFF9480F5),
    RetirementHighlight,
    Color(0xFF7DB6A5),
)

internal fun Money.editorialFormat(): String {
    val dollars = cents / 100.0
    return when {
        abs(dollars) >= 1_000_000 -> String.format(Locale.US, "$%.2fM", dollars / 1_000_000)
        else -> String.format(Locale.US, "$%,.0f", dollars)
    }
}

internal fun Money.wholeDollarEditorialFormat(): String = String.format(Locale.US, "$%,d", cents / 100)

@Composable
internal fun EditorialHeading(eyebrow: String, title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow.uppercase(), color = RetirementPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            title,
            style = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.displayMedium
            },
        )
        body?.let { Text(it, color = RetirementTextSecondary, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
internal fun PlanHorizon(
    currentAge: Int,
    retirementAge: Int,
    endAge: Int,
    modifier: Modifier = Modifier,
) {
    val safeEnd = maxOf(endAge, currentAge + 1)
    val marker = ((retirementAge - currentAge).toFloat() / (safeEnd - currentAge)).coerceIn(0f, 1f)
    fun cubic(value0: Float, value1: Float, value2: Float, value3: Float, t: Float): Float {
        val inverse = 1f - t
        return inverse * inverse * inverse * value0 + 3f * inverse * inverse * t * value1 +
            3f * inverse * t * t * value2 + t * t * t * value3
    }
    val markerYFraction = cubic(.80f, .60f, .05f, .42f, marker)
    BoxWithConstraints(modifier.fillMaxWidth().height(290.dp).semantics {
        contentDescription = "Plan horizon from age $currentAge to $safeEnd. Retirement begins at age $retirementAge."
    }) {
        Canvas(Modifier.matchParentSize()) {
            val plotHeight = 225.dp.toPx()
            val axisY = 240.dp.toPx()
            val start = Offset(8.dp.toPx(), plotHeight * .80f)
            val end = Offset(size.width - 8.dp.toPx(), plotHeight * .42f)
            fun yAt(t: Float) = plotHeight * cubic(.80f, .60f, .05f, .42f, t)
            fun predictionBand(startSpread: Float, endSpread: Float) = Path().apply {
                val steps = 48
                for (step in 0..steps) {
                    val t = step / steps.toFloat()
                    val eased = t * t * (3f - 2f * t)
                    val spread = startSpread + (endSpread - startSpread) * eased
                    val x = start.x + (end.x - start.x) * t
                    val y = yAt(t) - spread
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                quadraticTo(end.x + endSpread, yAt(1f), end.x, yAt(1f) + endSpread)
                for (step in steps - 1 downTo 0) {
                    val t = step / steps.toFloat()
                    val eased = t * t * (3f - 2f * t)
                    val spread = startSpread + (endSpread - startSpread) * eased
                    lineTo(start.x + (end.x - start.x) * t, yAt(t) + spread)
                }
                close()
            }
            fun curve() = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    start.x + (end.x - start.x) / 3f,
                    plotHeight * .60f,
                    start.x + (end.x - start.x) * 2f / 3f,
                    plotHeight * .05f,
                    end.x,
                    end.y,
                )
            }
            drawPath(predictionBand(5.dp.toPx(), 45.dp.toPx()), RetirementPrimary.copy(alpha = .08f))
            drawPath(predictionBand(3.5.dp.toPx(), 31.dp.toPx()), RetirementPrimary.copy(alpha = .15f))
            drawPath(predictionBand(2.dp.toPx(), 18.dp.toPx()), RetirementBlue.copy(alpha = .24f))
            drawPath(curve(), RetirementHighlight, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val markerX = start.x + (end.x - start.x) * marker
            val markerY = plotHeight * markerYFraction
            val markerLine = Path().apply {
                moveTo(markerX, markerY + 9.dp.toPx())
                lineTo(markerX, axisY)
            }
            drawPath(
                markerLine,
                RetirementGold,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                ),
            )
            drawCircle(RetirementGold.copy(alpha = .24f), 14.dp.toPx(), Offset(markerX, markerY))
            drawCircle(RetirementGold, 5.dp.toPx(), Offset(markerX, markerY))
            drawCircle(RetirementHighlight, 5.dp.toPx(), start)
            drawPath(
                Path().apply {
                    moveTo(start.x, start.y + 8.dp.toPx())
                    lineTo(start.x, axisY)
                },
                RetirementHighlight,
                style = Stroke(
                    width = 1.dp.toPx(),
                    cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                ),
            )
            drawLine(
                RetirementTextSecondary.copy(alpha = .24f),
                Offset(start.x, axisY),
                Offset(end.x, axisY),
                1.dp.toPx(),
            )
            for (tick in 0..5) {
                val tickX = start.x + (end.x - start.x) * tick / 5f
                drawLine(
                    RetirementTextSecondary.copy(alpha = .36f),
                    Offset(tickX, axisY),
                    Offset(tickX, axisY + 7.dp.toPx()),
                    1.dp.toPx(),
                )
            }
        }
        val markerX = maxWidth * marker
        val annotationWidth = if (LocalDensity.current.fontScale >= 1.5f) 188.dp else 142.dp
        Column(
            Modifier.align(Alignment.TopStart).offset(
                x = (markerX + 14.dp).coerceAtMost(maxWidth - annotationWidth),
                y = (225.dp * markerYFraction + 28.dp).coerceAtMost(180.dp),
            ).width(annotationWidth),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Age $retirementAge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("RETIREMENT HORIZON", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
        Text("Today", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomStart))
        Text(retirementAge.toString(), color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomCenter).offset(x = markerX - maxWidth / 2f))
        Text(safeEnd.toString(), color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
internal fun ConfidenceSparkline(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(72.dp).semantics {
        contentDescription = "A compact modeled confidence range with its median path highlighted."
    }) {
        val start = Offset(2.dp.toPx(), size.height * .76f)
        val end = Offset(size.width - 2.dp.toPx(), size.height * .54f)
        fun yAt(t: Float): Float = if (t <= .58f) {
            val local = t / .58f
            val inverse = 1f - local
            inverse * inverse * inverse * start.y +
                3f * inverse * inverse * local * size.height * .72f +
                3f * inverse * local * local * size.height * .12f +
                local * local * local * size.height * .25f
        } else {
            val local = (t - .58f) / .42f
            val inverse = 1f - local
            inverse * inverse * inverse * size.height * .25f +
                3f * inverse * inverse * local * size.height * .34f +
                3f * inverse * local * local * size.height * .68f +
                local * local * local * end.y
        }
        fun confidenceBand(startSpread: Float, endSpread: Float) = Path().apply {
            val steps = 36
            for (step in 0..steps) {
                val t = step / steps.toFloat()
                val spread = startSpread + (endSpread - startSpread) * t
                val x = start.x + (end.x - start.x) * t
                if (step == 0) moveTo(x, yAt(t) - spread) else lineTo(x, yAt(t) - spread)
            }
            quadraticTo(end.x + endSpread, yAt(1f), end.x, yAt(1f) + endSpread)
            for (step in steps - 1 downTo 0) {
                val t = step / steps.toFloat()
                val spread = startSpread + (endSpread - startSpread) * t
                lineTo(start.x + (end.x - start.x) * t, yAt(t) + spread)
            }
            close()
        }
        fun path() = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(size.width * .24f, size.height * .72f, size.width * .42f, size.height * .12f, size.width * .58f, size.height * .25f)
            cubicTo(size.width * .76f, size.height * .34f, size.width * .88f, size.height * .68f, end.x, end.y)
        }
        drawPath(confidenceBand(3.dp.toPx(), 18.dp.toPx()), RetirementPrimary.copy(alpha = .12f))
        drawPath(confidenceBand(2.dp.toPx(), 10.dp.toPx()), RetirementBlue.copy(alpha = .18f))
        drawPath(path(), RetirementHighlight, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val marker = Offset(size.width * .58f, size.height * .25f)
        drawLine(RetirementPrimary, Offset(marker.x, 2.dp.toPx()), Offset(marker.x, size.height - 2.dp.toPx()), 1.25.dp.toPx())
        drawCircle(RetirementHighlight.copy(alpha = .24f), 8.dp.toPx(), marker)
        drawCircle(RetirementHighlight, 3.5.dp.toPx(), marker)
    }
}

@Composable
internal fun AssetStreams(rows: List<Pair<String, Money>>, trackedTotal: Money, modifier: Modifier = Modifier) {
    val visible = rows.filter { it.second.cents > 0L }
    val total = visible.sumOf { it.second.cents }.coerceAtLeast(1L)
    val description = visible.joinToString(prefix = "Tracked asset streams. ") { "${it.first}: ${it.second.format()}" }
    Box(modifier.fillMaxWidth().height(270.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize()) {
            if (visible.isEmpty()) return@Canvas
            val centerY = size.height * .62f
            visible.forEachIndexed { index, (_, amount) ->
                val color = FinanceStreamColors[index % FinanceStreamColors.size]
                val lane = (index + 1f) / (visible.size + 1f)
                val startY = size.height * (.11f + lane * .60f)
                val width = (15.dp.toPx() + 46.dp.toPx() * (amount.cents.toFloat() / total)).coerceAtMost(58.dp.toPx())
                val path = Path().apply {
                    moveTo(-width, startY)
                    cubicTo(size.width * .30f, startY, size.width * .45f, centerY, size.width * .73f, centerY)
                    cubicTo(size.width * .86f, centerY, size.width * .92f, centerY + (index - visible.lastIndex / 2f) * 8.dp.toPx(), size.width + width, centerY + (index - visible.lastIndex / 2f) * 14.dp.toPx())
                }
                drawPath(path, color.copy(alpha = .68f), style = Stroke(width, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(RetirementHighlight.copy(alpha = .24f), 13.dp.toPx(), Offset(size.width * .73f, centerY))
            drawCircle(RetirementHighlight, 4.5.dp.toPx(), Offset(size.width * .73f, centerY))
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visible.forEach { (label, amount) ->
                Column(
                    Modifier.widthIn(max = 126.dp)
                        .background(RetirementBackground.copy(alpha = .78f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(label, color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(amount.editorialFormat(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd)
                .background(RetirementBackground.copy(alpha = .82f), RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text("TRACKED TOTAL", color = RetirementTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(trackedTotal.editorialFormat(), style = MaterialTheme.typography.headlineLarge)
        }
    }
}
