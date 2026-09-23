package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color = RetirementPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.displaySmall)
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
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val start = Offset(8.dp.toPx(), size.height * .80f)
            val end = Offset(size.width - 8.dp.toPx(), size.height * .36f)
            fun curve(yOffset: Float) = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(size.width * .28f, size.height * (.86f + yOffset), size.width * .47f, size.height * (.22f + yOffset), end.x, end.y + size.height * yOffset)
            }
            drawPath(curve(.18f), RetirementPrimary.copy(alpha = .08f), style = Stroke(42.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(.10f), RetirementPrimary.copy(alpha = .12f), style = Stroke(31.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(.04f), RetirementBlue.copy(alpha = .16f), style = Stroke(20.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curve(0f), RetirementHighlight, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val markerX = start.x + (end.x - start.x) * marker
            drawLine(RetirementGold, Offset(markerX, 12.dp.toPx()), Offset(markerX, size.height - 8.dp.toPx()), 1.5.dp.toPx())
            drawCircle(RetirementGold.copy(alpha = .24f), 11.dp.toPx(), Offset(markerX, size.height * .48f))
            drawCircle(RetirementGold, 5.dp.toPx(), Offset(markerX, size.height * .48f))
            drawCircle(RetirementHighlight, 5.dp.toPx(), start)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Today\n$currentAge", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("Retire\n$retirementAge", color = RetirementGold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Plan\n$safeEnd", color = RetirementTextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AssetStreams(rows: List<Pair<String, Money>>, modifier: Modifier = Modifier) {
    val visible = rows.filter { it.second.cents > 0L }
    val total = visible.sumOf { it.second.cents }.coerceAtLeast(1L)
    val description = visible.joinToString(prefix = "Tracked asset streams. ") { "${it.first}: ${it.second.format()}" }
    Canvas(modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = description }) {
        if (visible.isEmpty()) return@Canvas
        val centerY = size.height * .62f
        visible.forEachIndexed { index, (_, amount) ->
            val color = FinanceStreamColors[index % FinanceStreamColors.size]
            val lane = (index + 1f) / (visible.size + 1f)
            val startY = size.height * lane
            val width = (12.dp.toPx() + 38.dp.toPx() * (amount.cents.toFloat() / total)).coerceAtMost(46.dp.toPx())
            val path = Path().apply {
                moveTo(-width, startY)
                cubicTo(size.width * .30f, startY, size.width * .43f, centerY, size.width * .70f, centerY)
                cubicTo(size.width * .84f, centerY, size.width * .91f, centerY + (index - visible.lastIndex / 2f) * 8.dp.toPx(), size.width + width, centerY + (index - visible.lastIndex / 2f) * 13.dp.toPx())
            }
            drawPath(path, color.copy(alpha = .62f), style = Stroke(width, cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = .92f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(RetirementHighlight.copy(alpha = .22f), 10.dp.toPx(), Offset(size.width * .70f, centerY))
        drawCircle(RetirementHighlight, 4.dp.toPx(), Offset(size.width * .70f, centerY))
    }
}
