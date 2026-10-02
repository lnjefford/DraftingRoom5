package dev.draftingroom5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (LocalDensity.current.fontScale >= 1.5f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("THIS WEEK · $done / $planned", color = TodayLavender, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("THIS WEEK", color = TodayLavender, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Text("$done / $planned complete", color = TodayLavender, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            week.forEach { day ->
                val barHeight = if (day.planned == 0) 3.dp else (7 + day.planned.coerceAtMost(3) * 6).dp
                Column(Modifier.weight(1f).semantics {
                    contentDescription = "${day.date.dayOfWeek}, ${day.completed} of ${day.planned} workouts complete"
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.height(26.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(7.dp).height(barHeight).clip(RoundedCornerShape(7.dp))
                            .background(if (day.planned == 0) TodayBorder else Color(0xFF4E436D)))
                        if (day.completed > 0) Box(Modifier.width(7.dp)
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
internal fun TodayMarketsEditorial(
    symbols: List<String>,
    quotes: Map<String, TodayMarketQuote>,
    loadLogos: Boolean = false,
    loading: Boolean = false,
    onOpen: (String) -> Unit,
    onAttribution: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayEditorialHeading("YOUR STOCKS")
        val largeText = LocalDensity.current.fontScale >= 1.5f
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            symbols.forEach { symbol ->
                TodayMarketCard(symbol, quotes[symbol], loadLogos, loading,
                    Modifier.width(if (largeText) 250.dp else 164.dp)) { onOpen(symbol) }
            }
        }
        Text("Market data: Yahoo Finance", color = TodayLavender.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall)
        Text("Logos by AllInvestView ↗", color = TodayLavender.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable(onClick = onAttribution))
    }
}

@Composable
private fun TodayMarketCard(symbol: String, quote: TodayMarketQuote?, loadLogos: Boolean, loading: Boolean,
                            modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val change = quote?.changePercent
    val trendColor = if (change == null || change >= 0) TodayMint else Color(0xFFFF8F90)
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(TodayEditorialCardSurface).border(1.dp, TodayBorder.copy(alpha = 0.7f), shape)
        .clickable(onClick = onOpen).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TodayStockLogo(symbol, loadLogos)
            Text(symbol, color = TodayText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ChevronRight, contentDescription = "Open $symbol quote", tint = TodayCopper,
                modifier = Modifier.size(18.dp))
        }
        Text(when (symbol) { "AAPL" -> "Apple"; "^IXIC" -> "Nasdaq Composite"; else -> symbol },
            color = TodayLavender, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Text(quote?.let { marketPriceText(it, locale) } ?: if (loading) "Loading quote…" else "Quote unavailable", color = TodayText,
            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(change?.let { String.format(locale, "%+.2f%% · 1D", it) } ?: "Latest available quote",
            color = if (change == null) TodayLavender else trendColor, style = MaterialTheme.typography.labelSmall,
            maxLines = 1)
        if (quote != null && quote.points.size >= 2) TodayMarketSparkline(quote.points, trendColor)
        else Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
            Text("Trend unavailable", color = TodayLavender.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
        Text(quote?.let { "5D · ${Instant.ofEpochSecond(it.updatedAtSeconds).atZone(ZoneId.systemDefault()).format(
            DateTimeFormatter.ofPattern("MMM d, h:mm a", locale))}" } ?: "Tap for market details",
            color = TodayLavender, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

private fun marketPriceText(quote: TodayMarketQuote, locale: Locale): String {
    val value = String.format(locale, "%,.2f", quote.price)
    return if (quote.symbol.startsWith("^")) value else when (quote.currency) {
        "USD" -> "\$$value"
        "EUR" -> "€$value"
        "GBP" -> "£$value"
        else -> "${quote.currency} $value".trim()
    }
}

@Composable
private fun TodayStockLogo(symbol: String, loadLogos: Boolean) {
    val image by produceState<android.graphics.Bitmap?>(initialValue = null, symbol, loadLogos) {
        value = if (loadLogos) runCatching { TodayMarketLogoSource.fetch(symbol) }.getOrNull() else null
    }
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        when {
            image != null -> Image(image!!.asImageBitmap(), contentDescription = "$symbol logo", modifier = Modifier.size(28.dp))
            symbol == "AAPL" -> Image(painterResource(R.drawable.today_logo_apple), contentDescription = "Apple logo",
                modifier = Modifier.size(28.dp))
            else -> Text(symbol.trimStart('^').take(2), color = TodayBackground, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TodayMarketSparkline(points: List<Double>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = "Five day price trend" }) {
        val low = points.minOrNull() ?: return@Canvas
        val high = points.maxOrNull() ?: return@Canvas
        val range = (high - low).takeIf { it > 0.0 } ?: 1.0
        val line = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1)
            val y = size.height * (0.85f - ((value - low) / range).toFloat() * 0.7f)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun TodayTeamsEditorial(teams: List<TodayTeam>, games: Map<TodayTeam, TodayNextGame>,
                                  loading: Boolean = false, onOpen: (TodayTeam) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayEditorialHeading("YOUR TEAMS")
        val largeText = LocalDensity.current.fontScale >= 1.5f
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            teams.forEach { team ->
                TodayTeamCard(team, games[team], loading,
                    Modifier.width(if (largeText) 290.dp else 216.dp).height(if (largeText) 340.dp else 244.dp)) {
                    onOpen(team)
                }
            }
        }
        if (teams.isEmpty()) Text("Choose teams in Today settings", color = TodayLavender)
        Text("Schedules: ESPN", color = TodayLavender.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TodayTeamCard(team: TodayTeam, game: TodayNextGame?, loading: Boolean,
                          modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(TodayEditorialCardSurface).border(1.dp, TodayBorder.copy(alpha = 0.7f), shape)
        .clickable(onClick = onOpen).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                contentAlignment = Alignment.Center) {
                Image(painterResource(team.logoResource), contentDescription = "${team.displayName} logo",
                    modifier = Modifier.size(34.dp))
            }
            Text(team.shortMark, color = TodayCopper, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = "Open ${team.displayName} schedule",
                tint = TodayCopper, modifier = Modifier.size(18.dp))
        }
        Text(team.displayName, color = TodayText, style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("NEXT GAME", color = TodayLavender, style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp)
        if (game == null) {
            Text(if (loading) "Loading schedule…" else "No upcoming game posted", color = TodayText,
                style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Text("View full schedule ↗", color = TodayLavender, style = MaterialTheme.typography.labelSmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayOpponentLogo(game.opponentLogo, game.opponent.take(2))
                Text("${if (game.home) "vs" else "@"} ${game.opponent}", color = TodayText,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text(game.startsAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)),
                color = TodayCopper, style = MaterialTheme.typography.labelLarge)
            Text(if (game.timeConfirmed) game.startsAt.atZone(ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("h:mm a", locale)) else "Time TBD",
                color = TodayLavender, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val TodayEditorialCardSurface = Color(0xA6251E41)

@Composable
private fun TodayOpponentLogo(url: String?, fallback: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = if (url == null) null else runCatching { TodayTeamSource.logo(url) }.getOrNull()
    }
    Box(Modifier.size(27.dp).clip(RoundedCornerShape(7.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(25.dp))
        else Text(fallback.uppercase(Locale.ROOT), color = TodayBackground, style = MaterialTheme.typography.labelSmall)
    }
}

private val TodayTeam.logoResource: Int get() = when (this) {
    TodayTeam.BREWERS -> R.drawable.today_team_brewers
    TodayTeam.PACKERS -> R.drawable.today_team_packers
    TodayTeam.INDIANA_FOOTBALL, TodayTeam.INDIANA_BASKETBALL -> R.drawable.today_team_indiana
    TodayTeam.WISCONSIN_FOOTBALL, TodayTeam.WISCONSIN_BASKETBALL -> R.drawable.today_team_wisconsin
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
