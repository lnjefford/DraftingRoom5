package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    Column(modifier.semantics {
        contentDescription = "Plan horizon from age $currentAge to $safeEnd. Retirement begins at age $retirementAge."
    }) {
        Canvas(Modifier.fillMaxWidth().height(225.dp)) {
            val start = Offset(8.dp.toPx(), size.height * .80f)
            val end = Offset(size.width - 8.dp.toPx(), size.height * .43f)
            fun curve(spread: Float) = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    size.width * .25f,
                    size.height * (.88f + spread * .18f),
                    size.width * .48f,
                    size.height * (.18f + spread * .48f),
                    end.x,
                    end.y + size.height * spread,
                )
            }
            drawPath(curve(.20f), RetirementPrimary.copy(alpha = .08f), style = Stroke(72.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(.11f), RetirementPrimary.copy(alpha = .14f), style = Stroke(52.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(.04f), RetirementBlue.copy(alpha = .22f), style = Stroke(30.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(0f), RetirementHighlight, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val markerX = start.x + (end.x - start.x) * marker
            val markerY = size.height * .46f
            drawLine(RetirementGold, Offset(markerX, 18.dp.toPx()), Offset(markerX, size.height - 4.dp.toPx()), 1.5.dp.toPx())
            drawCircle(RetirementGold.copy(alpha = .24f), 14.dp.toPx(), Offset(markerX, markerY))
            drawCircle(RetirementGold, 5.dp.toPx(), Offset(markerX, markerY))
            drawCircle(RetirementHighlight, 5.dp.toPx(), start)
            drawLine(
                RetirementTextSecondary.copy(alpha = .24f),
                Offset(start.x, size.height - 2.dp.toPx()),
                Offset(end.x, size.height - 2.dp.toPx()),
                1.dp.toPx(),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Today\n$currentAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("Retire\n$retirementAge", color = RetirementGold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Plan\n$safeEnd", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
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
