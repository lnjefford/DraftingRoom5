package dev.draftingroom5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val TodayText = Color(0xFFF7F2FF)
private val TodayMint = Color(0xFF55DFB7)

@Composable
internal fun TodayEditorialHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        if (largeText) {
            Text(title, color = TodayText, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.7.sp)
            if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(action, color = TodayCopper, style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TodayCopper, modifier = Modifier.size(18.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TodayText, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.7.sp)
                if (action != null && onAction != null) TextButton(onClick = onAction) {
                    Text(action, color = TodayCopper, style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TodayCopper, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = TodayCopper, thickness = 2.dp, modifier = Modifier.width(46.dp))
    }
}

@Composable
internal fun TodayWorkoutEditorial(sessions: List<DashboardSession>, week: List<TodayWeekDay>, today: LocalDate, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TodayEditorialHeading("TODAY'S WORKOUT")
        val primary = sessions.firstOrNull()
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(72.dp).background(Color(0xFF8FB9FF), RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (primary == null) "OPEN DAY" else when (primary.action) {
                    SessionAction.DONE -> "COMPLETED TODAY"
                    SessionAction.RESUME -> "IN PROGRESS"
                    SessionAction.START -> "ON YOUR SCHEDULE"
                }, color = Color(0xFF8FB9FF), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                Text(primary?.routine?.name ?: "Your day is open", color = TodayText,
                    style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(primary?.progressLabel ?: if (primary == null) "Make room for what moves you" else
                    if (sessions.size == 1) "1 session on your schedule" else "${sessions.size} sessions on your schedule",
                    color = Color(0xFFCAD9F2), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open Fitness", tint = Color(0xFF8FB9FF),
                modifier = Modifier.size(24.dp))
        }
        if (sessions.size > 1) Text("+ ${sessions.size - 1} more on today's schedule", color = TodayLavender,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = onOpen))
        TodayWeekChart(week, today)
    }
}

@Composable
private fun TodayWeekChart(week: List<TodayWeekDay>, today: LocalDate) {
    val done = week.sumOf { it.completed }
    val planned = week.sumOf { it.planned }
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("THIS WEEK", color = TodayLavender, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp)
            Text("$done of $planned complete", color = TodayLavender, style = MaterialTheme.typography.labelMedium)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("THIS WEEK", color = TodayLavender, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp)
            Text("$done of $planned complete", color = TodayLavender, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            week.forEach { day ->
                val barHeight = if (day.planned == 0) 5.dp else (13 + day.planned.coerceAtMost(3) * 11).dp
                Column(Modifier.weight(1f).semantics {
                    contentDescription = "${day.date.dayOfWeek}, ${day.completed} of ${day.planned} workouts complete"
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(12.dp).height(barHeight).clip(RoundedCornerShape(7.dp))
                            .background(if (day.planned == 0) TodayBorder else Color(0xFF4E436D)))
                        if (day.completed > 0) Box(Modifier.width(12.dp)
                            .height(barHeight * day.completed.toFloat() / day.planned.coerceAtLeast(1))
                            .clip(RoundedCornerShape(7.dp)).background(TodayMint))
                    }
                    Text(day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        color = if (day.date == today) TodayCopper else TodayLavender,
                        style = MaterialTheme.typography.labelSmall, fontWeight = if (day.date == today) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun TodayMarketsEditorial(symbols: List<String>, onOpen: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TodayEditorialHeading("YOUR STOCKS")
        val visible = if (expanded) symbols else symbols.take(2)
        val largeText = LocalDensity.current.fontScale >= 1.5f
        visible.chunked(if (largeText) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { symbol ->
                    Column(Modifier.weight(1f).clickable { onOpen(symbol) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(symbol, color = TodayCopper, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                        Text(when (symbol) { "AAPL" -> "Apple"; "^IXIC" -> "Nasdaq Composite"; else -> symbol },
                            color = TodayText, style = MaterialTheme.typography.titleLarge, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text("VIEW LIVE QUOTE  ↗", color = TodayLavender, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.size == 1 && !largeText) Spacer(Modifier.weight(1f))
            }
            HorizontalDivider(color = TodayBorder)
        }
        if (symbols.size > 2) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Show ${symbols.size - 2} more", color = TodayCopper)
        }
    }
}

@Composable
internal fun TodayTeamsEditorial(teams: List<TodayTeam>, onOpen: (TodayTeam) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TodayEditorialHeading("YOUR TEAMS")
        val visible = if (expanded) teams else teams.take(2)
        val largeText = LocalDensity.current.fontScale >= 1.5f
        visible.chunked(if (largeText) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { team ->
                    Column(Modifier.weight(1f).clickable { onOpen(team) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(if (largeText) 68.dp else 42.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C264A)),
                            contentAlignment = Alignment.Center) {
                            Text(team.shortMark, color = TodayCopper, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(team.displayName, color = TodayText, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("SCHEDULE & SCORES  ↗", color = TodayLavender, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.size == 1 && !largeText) Spacer(Modifier.weight(1f))
            }
            HorizontalDivider(color = TodayBorder)
        }
        if (teams.isEmpty()) Text("Choose teams in Today settings", color = TodayLavender)
        if (teams.size > 2) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show less" else "Show ${teams.size - 2} more teams", color = TodayCopper)
        }
    }
}

private val TodayTeam.shortMark: String get() = when (this) {
    TodayTeam.BREWERS -> "MKE"
    TodayTeam.PACKERS -> "GB"
    TodayTeam.INDIANA_FOOTBALL, TodayTeam.INDIANA_BASKETBALL -> "IU"
    TodayTeam.WISCONSIN_FOOTBALL, TodayTeam.WISCONSIN_BASKETBALL -> "WI"
}

@Composable
internal fun TodayRetirementEditorial(balance: String?, message: String, trend: List<TodayRetirementPoint>, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        TodayEditorialHeading("RETIREMENT GLANCE", "VIEW FINANCE", onOpen)
        Text(message.uppercase(Locale.ROOT), color = TodayLavender,
            style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
        Text(balance ?: "—", color = TodayText, style = MaterialTheme.typography.displaySmall)
        if (trend.size >= 2) {
            TodayTrendChart(trend.map { it.cents.toFloat() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trend.first().date.toString(), color = TodayLavender, style = MaterialTheme.typography.labelSmall)
                Text("RECORDED UPDATES", color = TodayMint, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                Text(trend.last().date.toString(), color = TodayLavender, style = MaterialTheme.typography.labelSmall)
            }
        } else Text("A trend appears after two recorded balance updates", color = TodayLavender,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TodayTrendChart(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(88.dp)) {
        val low = values.minOrNull() ?: return@Canvas
        val high = values.maxOrNull() ?: return@Canvas
        val range = (high - low).takeIf { it > 0f } ?: 1f
        val top = size.height * 0.12f
        val bottom = size.height * 0.82f
        val line = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = bottom - (value - low) / range * (bottom - top)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(TodayMint.copy(alpha = 0.24f), TodayMint.copy(alpha = 0.01f))))
        drawPath(line, TodayMint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        val finalY = bottom - (values.last() - low) / range * (bottom - top)
        drawCircle(TodayMint, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - 4.dp.toPx(), finalY))
    }
}
