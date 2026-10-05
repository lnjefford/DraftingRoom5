package dev.draftingroom5.wear

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import dev.draftingroom5.watch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.URL
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sin

private val HubInk = Color(0xFF061424)
private val HubIvory = Color(0xFFF4F0E8)
private val HubBlue = Color(0xFF2F82FF)
private val HubSecondary = Color(0xFFA9B8CE)
private val HubLink = Color(0xFFA5CAE9)
private val HubGain = Color(0xFF6EE89F)
private val HubLoss = Color(0xFFFF8484)
internal val LocalWatchMotionAllowed = staticCompositionLocalOf { false }

internal fun readWatchLogo(input: java.io.InputStream, limit: Int = 300_000): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "Team logo is too large." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun topWatchStock(stocks: List<WatchTodayStock>) =
    stocks.filter { it.changePercent != null }.maxByOrNull { abs(it.changePercent!!) }
        ?: stocks.firstOrNull()

internal fun nextWatchGame(games: List<WatchTodayGame>, now: Long) =
    games.filter { it.startsAtMillis != null && it.startsAtMillis >= now }
        .minByOrNull { it.startsAtMillis!! }

internal data class WatchFitnessActivities(val plans: List<WatchRunPlan>, val workouts: List<WatchTodayWorkout>,
    val legacyWorkout: WatchExercise?, val completed: Boolean)

internal fun watchFitnessActivities(snapshot: WatchSnapshot, catalog: WatchRunCatalog?, date: String): WatchFitnessActivities {
    val briefing = snapshot.today?.takeIf { it.date == date }
    val legacy = snapshot.exercises.firstOrNull().takeIf { snapshot.status == WatchWorkoutStatus.AVAILABLE &&
        briefing?.workouts.isNullOrEmpty() &&
        (briefing != null || (snapshot.today == null && snapshot.scheduledDate == date)) }
    return WatchFitnessActivities(catalog?.plans.orEmpty().filter { it.effectiveDate == date },
        briefing?.workouts.orEmpty(), legacy, snapshot.status == WatchWorkoutStatus.COMPLETE && briefing != null)
}

internal enum class WatchTodayDetail { STOCKS, GAMES }

@Composable
internal fun WatchTodayHub(snapshot: WatchSnapshot, catalog: WatchRunCatalog?, onSync: () -> Unit,
    onStartWorkout: (String?) -> Unit, onStartRun: (WatchRunPlan) -> Unit,
    previewPage: Int = 0, previewDetail: WatchTodayDetail? = null, previewScroll: Boolean = false) {
    var detail by remember { mutableStateOf(previewDetail) }
    val pager = rememberPagerState(initialPage = previewPage.coerceIn(0, 1)) { 2 }
    BackHandler(detail != null) { detail = null }
    BoxWithConstraints(Modifier.fillMaxSize().clip(CircleShape).background(HubInk)) {
        val scale = maxWidth.value / 192f
        val briefing = snapshot.today
        val preview = LocalInspectionMode.current
        var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val date = if (preview) briefing?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(2026, 10, 1) else Instant.ofEpochMilli(clock).atZone(ZoneId.systemDefault()).toLocalDate()
        val motionAllowed = LocalWatchMotionAllowed.current
        LaunchedEffect(motionAllowed, preview) {
            if (!preview && motionAllowed) while (true) {
                clock = System.currentTimeMillis()
                delay(60_000)
            }
        }
        val hour = briefing?.localHour?.let { local ->
            if (preview) local else ((local + ((clock - briefing.updatedAtMillis).coerceAtLeast(0) / 3_600_000)) % 24).toInt()
        } ?: if (preview) 18 else LocalTime.now().hour
        HubScene(date, hour, briefing?.weatherKind, detail != null)
        if (detail != null) {
            HubDetail(checkNotNull(detail), briefing, scale, previewScroll) { detail = null }
        } else {
            HorizontalPager(pager, reverseLayout = true, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize()) {
                    if (page == 0) HubSummary(briefing, scale, hour, clock,
                        { detail = WatchTodayDetail.STOCKS }, { detail = WatchTodayDetail.GAMES }, onSync)
                    else HubFitness(snapshot, catalog, date.toString(), scale, onStartWorkout, onStartRun, onSync)
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = (13 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                repeat(2) { index -> Box(Modifier.size((3.5f * scale).dp).background(
                    if (index == pager.currentPage) { if (index == 1) HubBlue else HubIvory }
                    else HubSecondary.copy(alpha = .5f), CircleShape)) }
            }
        }
    }
}

@Composable
private fun HubScene(date: LocalDate, hour: Int, weather: String?, detail: Boolean) {
    val scene = watchSceneResource(date.monthValue, hour)
    Image(painterResource(scene), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    if(hour in 5..8) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF9F7391).copy(alpha=.18f), Color.Transparent, HubInk.copy(alpha=.16f)))))
    HubWeatherVeil(weather)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        HubInk.copy(alpha = if (detail) .55f else if(hour in 9..16) .35f else .14f),
        HubInk.copy(alpha = if (detail) .62f else if(hour in 9..16) .26f else .12f), HubInk.copy(alpha = if (detail) .83f else .35f)))))
    val context = LocalContext.current
    var interactive by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                interactive = intent?.action == android.content.Intent.ACTION_SCREEN_ON
            }
        }
        interactive = (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive ?: false
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON); addAction(android.content.Intent.ACTION_SCREEN_OFF)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    if (!LocalInspectionMode.current && LocalWatchMotionAllowed.current && interactive && ValueAnimator.areAnimatorsEnabled() && !detail)
        HubWeatherMotion(weather, date.monthValue)
}

internal fun watchSceneResource(month: Int, hour: Int): Int {
    val day = hour in 9..16
    val night = hour !in 5..20
    return when(month) {
        in 3..5 -> if(day) R.drawable.watch_today_spring_day else if(night) R.drawable.watch_today_spring_night else R.drawable.watch_today_spring_dusk
        in 6..8 -> if(day) R.drawable.watch_today_summer_day else if(night) R.drawable.watch_today_summer_night else R.drawable.watch_today_summer_dusk
        in 9..11 -> if(day) R.drawable.watch_today_autumn_day else if(night) R.drawable.watch_today_autumn_night else R.drawable.watch_today_autumn_dusk
        else -> if(day) R.drawable.watch_today_winter_day else if(night) R.drawable.watch_today_winter_night else R.drawable.watch_today_winter_dusk
    }
}

@Composable
private fun HubWeatherVeil(weather: String?) {
    val opacity = when(weather) { "CLOUDY" -> .10f; "SNOW" -> .24f; "RAIN" -> .32f; "STORM" -> .52f; else -> 0f }
    if(opacity == 0f) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF738394).copy(alpha=opacity),
            HubInk.copy(alpha=opacity*.6f), Color.Transparent)))
        repeat(3) { i ->
            val center=Offset(size.width*(i*.48f-.1f),size.height*(.09f+i*.10f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8194A6).copy(alpha=opacity*.7f),Color.Transparent),
                center,size.width*.48f),size.width*.48f,center)
        }
        if(weather=="SNOW") drawRect(Brush.verticalGradient(listOf(Color.Transparent,
            Color(0xFFC8D8E8).copy(alpha=.10f)),startY=size.height*.75f))
    }
}

@Composable
private fun HubWeatherMotion(weather: String?, month: Int) {
    val transition = rememberInfiniteTransition(label = "Watch weather")
    val phase by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "Drift")
    Canvas(Modifier.fillMaxSize()) {
        val wet = weather in listOf("RAIN", "SNOW", "STORM")
        repeat(if (wet) 20 else 7) { index ->
            val p = (phase + index * .137f) % 1f
            val x = (index * 73.7f + sin((p * 6.28 + index)).toFloat() * 8f) % size.width
            val y = p * size.height
            when {
                weather == "SNOW" -> drawCircle(Color.White.copy(alpha = .35f), 1.5.dp.toPx(), Offset(x,y))
                wet -> drawLine(HubLink.copy(alpha = .24f), Offset(x,y), Offset(x-2.dp.toPx(),y+7.dp.toPx()), 1.dp.toPx())
                month in 3..5 || month in 9..11 -> rotate(p*240, Offset(x,y)) {
                    drawOval((if (month in 3..5) Color(0xFFFFD0DF) else Color(0xFFE5AE64)).copy(alpha=.4f),
                        Offset(x,y), Size(3.dp.toPx(),1.5.dp.toPx()))
                }
                month in listOf(12,1,2) -> drawCircle(HubIvory.copy(alpha=.18f*(1-p)),
                    (1+p).dp.toPx(), Offset(x,size.height*.82f+index*2.dp.toPx()))
                else -> drawLine(HubIvory.copy(alpha = .15f * (1-p)), Offset(x,size.height*.75f+index*3.dp.toPx()),
                    Offset(x+5.dp.toPx(),size.height*.75f+index*3.dp.toPx()),1.dp.toPx())
            }
        }
    }
}

@Composable
private fun BoxScope.HubSummary(today: WatchTodayBriefing?, s: Float, hour: Int, clock: Long, onStocks: () -> Unit,
    onGames: () -> Unit, onSync: () -> Unit) {
    HubText("TODAY", 8*s, HubIvory, letterSpacing = 2f,
        modifier = Modifier.align(Alignment.TopCenter).padding(top=(20*s).dp))
    Column(Modifier.align(Alignment.TopCenter).padding(top=(36*s).dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
            WeatherGlyph(today?.weatherKind, hour !in 5..20, Modifier.size((27*s).dp))
            HubText(today?.temperatureF?.let { "$it°" } ?: "—", 30*s, HubIvory, serif=true)
        }
        HubText(today?.location ?: "Sync weather", 10*s, HubIvory,
            modifier=Modifier.width((130*s).dp).clickable(onClick=onSync, role=Role.Button))
    }
    val stock = topWatchStock(today?.stocks.orEmpty())
    val now = if (LocalInspectionMode.current) Instant.parse("2026-10-01T12:00:00Z").toEpochMilli() else clock
    val game = nextWatchGame(today?.games.orEmpty(), now)
    Column(Modifier.align(Alignment.TopCenter).padding(top=(116*s).dp).width((142*s).dp),
        verticalArrangement=Arrangement.spacedBy((4*s).dp)) {
        HubGlass(Modifier.fillMaxWidth().height((23*s).dp).clickable(onClick=onStocks,role=Role.Button)) {
            Row(Modifier.fillMaxSize().padding(horizontal=(9*s).dp), verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
                HubText(stock?.symbol ?: "Your stocks", 10*s, HubIvory, bold=true)
                HubText(percent(stock?.changePercent),10*s, movementColor(stock?.changePercent),bold=true)
                Sparkline(stock?.points.orEmpty(), movementColor(stock?.changePercent),Modifier.weight(1f).height((10*s).dp))
            }
        }
        HubGlass(Modifier.fillMaxWidth().height((25*s).dp).clickable(onClick=onGames,role=Role.Button)) {
            Row(Modifier.fillMaxSize().padding(horizontal=(8*s).dp), verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
                if (game != null) {
                    TeamLogo(game.team,game.teamLogo,Modifier.size((20*s).dp))
                    TeamLogo(game.opponent.orEmpty(),game.opponentLogo,Modifier.size((20*s).dp))
                }
                Column(Modifier.weight(1f)) {
                    HubText(game?.let { "${shortTeam(it.team)} · ${shortTeam(it.opponent.orEmpty())}" } ?: "No upcoming game",
                        7.5f*s,HubIvory,bold=true,align=TextAlign.Start)
                    HubText(gameTime(game?.startsAtMillis).uppercase(),7*s,HubSecondary,align=TextAlign.Start)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HubFitness(snapshot: WatchSnapshot, catalog: WatchRunCatalog?, date: String, s: Float,
    onWorkout: (String?) -> Unit, onRun: (WatchRunPlan) -> Unit, onSync: () -> Unit) {
    val activities = watchFitnessActivities(snapshot, catalog, date)
    val plans = activities.plans
    val workout = activities.legacyWorkout
    HubText("TODAY’S WORKOUT",8*s,HubIvory,letterSpacing=1.4f,
        modifier=Modifier.align(Alignment.TopCenter).padding(top=(53*s).dp))
    val scroll = rememberScrollState()
    Column(Modifier.align(Alignment.TopCenter).padding(top=(72*s).dp).width((158*s).dp).height((86*s).dp)
        .watchListScroll(scroll),verticalArrangement=Arrangement.spacedBy((4*s).dp)) {
        plans.forEach { plan ->
            FitnessRow(plan.routineName,"Run · ${plan.intervals.sumOf { it.durationSeconds } / 60} min","START RUN",true,s) { onRun(plan) }
        }
        activities.workouts.forEach { scheduled ->
            val first = scheduled.exercises.firstOrNull()
            FitnessRow(if (scheduled.exercises.size == 1) first?.name ?: scheduled.name else scheduled.name,
                if (scheduled.exercises.size == 1 && first != null)
                    "${first.setCount} sets · ${first.durationSeconds?.let { "$it sec" } ?: "${first.reps ?: 0} reps"}"
                else "${scheduled.exercises.size} exercises",
                if (first?.durationSeconds != null) "START SET" else "START", false, s) { onWorkout(scheduled.id) }
        }
        if(workout != null) FitnessRow(workout.name,
            "${workout.setCount} sets · ${workout.durationSeconds?.let { "$it sec" } ?: "${workout.reps ?: 0} reps"}",
            if(workout.durationSeconds != null) "START SET" else "START",false,s) { onWorkout(null) }
        if(plans.isEmpty() && workout == null && activities.workouts.isEmpty()) {
            HubText(if(activities.completed) "Workout complete" else "Your day is open",14*s,HubIvory,
                modifier=Modifier.fillMaxWidth().padding(top=(17*s).dp))
            HubText("Sync with phone",9*s,HubLink,modifier=Modifier.fillMaxWidth().padding((5*s).dp)
                .clickable(onClick=onSync,role=Role.Button))
        }
    }
}

@Composable
private fun FitnessRow(title:String,subtitle:String,action:String,run:Boolean,s:Float,onClick:()->Unit) {
    HubGlass(Modifier.fillMaxWidth().height((41*s).dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal=(6*s).dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy((4*s).dp)) {
            ActivityGlyph(run,Modifier.size((22*s).dp))
            Column(Modifier.weight(1f)) {
                HubText(title,8.5f*s,HubIvory,bold=true,align=TextAlign.Start)
                HubText(subtitle,8*s,HubSecondary,align=TextAlign.Start)
            }
            Box(Modifier.width((46*s).dp).height((20*s).dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF009DFF),HubBlue)))
                .clickable(onClick=onClick,role=Role.Button),contentAlignment=Alignment.Center) {
                HubText(action,7*s,HubIvory,bold=true)
            }
        }
    }
}

@Composable
private fun BoxScope.HubDetail(detail:WatchTodayDetail,today:WatchTodayBriefing?,s:Float,
    scrolled:Boolean,onBack:()->Unit) {
    Row(Modifier.align(Alignment.TopCenter).padding(top=(19*s).dp).width((70*s).dp).height((23*s).dp)
        .clickable(onClick=onBack,role=Role.Button), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.Center) {
        Canvas(Modifier.size((10*s).dp)) {
            val w=size.width; val h=size.height
            drawLine(HubLink,Offset(w*.1f,h*.5f),Offset(w*.95f,h*.5f),1.dp.toPx(),StrokeCap.Round)
            drawLine(HubLink,Offset(w*.1f,h*.5f),Offset(w*.5f,h*.1f),1.dp.toPx(),StrokeCap.Round)
            drawLine(HubLink,Offset(w*.1f,h*.5f),Offset(w*.5f,h*.9f),1.dp.toPx(),StrokeCap.Round)
        }
        Spacer(Modifier.width((5*s).dp))
        HubText("BACK",9*s,HubLink,letterSpacing=1.2f)
    }
    HubText(if(detail==WatchTodayDetail.STOCKS) "STOCKS" else "NEXT GAMES",if(detail==WatchTodayDetail.STOCKS)14*s else 10*s,
        HubIvory,serif=detail==WatchTodayDetail.STOCKS,letterSpacing=1.3f,
        modifier=Modifier.align(Alignment.TopCenter).padding(top=(45*s).dp))
    val scroll=rememberScrollState(if(scrolled) Int.MAX_VALUE else 0)
    LaunchedEffect(scrolled, scroll.maxValue) { if(scrolled && scroll.maxValue != Int.MAX_VALUE) scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.align(Alignment.TopCenter).padding(top=(69*s).dp).width((147*s).dp).height((85*s).dp)
        .watchListScroll(scroll)) {
        if(detail==WatchTodayDetail.STOCKS) {
            today?.stocks.orEmpty().forEachIndexed { index,stock ->
                Row(Modifier.fillMaxWidth().height((21*s).dp).padding(horizontal=(5*s).dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    HubText(stock.symbol,11*s,HubIvory,serif=true,align=TextAlign.Start,modifier=Modifier.weight(1f))
                    HubText(stock.price?.let { "$${"%.2f".format(java.util.Locale.US,it)}" } ?: "—",9*s,HubIvory,
                        modifier=Modifier.weight(1.25f))
                    HubText(percent(stock.changePercent),10*s,movementColor(stock.changePercent),align=TextAlign.End,
                        modifier=Modifier.weight(1f))
                }
                if(index < today!!.stocks.lastIndex) Box(Modifier.fillMaxWidth().height(.35.dp).background(HubSecondary.copy(alpha=.25f)))
            }
            if(today?.stocks.isNullOrEmpty()) HubText("No stocks followed",11*s,HubSecondary,modifier=Modifier.fillMaxWidth().padding(top=20.dp))
        } else {
            today?.games.orEmpty().sortedBy { it.startsAtMillis ?: Long.MAX_VALUE }.forEachIndexed { index,game ->
                Row(Modifier.fillMaxWidth().height((28*s).dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy((5*s).dp)) {
                    TeamLogo(game.team,game.teamLogo,Modifier.size((21*s).dp))
                    Column(Modifier.weight(1f)) {
                        HubText(game.opponent?.let { "${shortTeam(game.team)} vs ${shortTeam(it)}" } ?: shortTeam(game.team),
                            9*s,HubIvory,align=TextAlign.Start,maxLines=2)
                        HubText(gameTime(game.startsAtMillis),8*s,HubSecondary,align=TextAlign.Start)
                    }
                    if(game.opponent != null) TeamLogo(game.opponent,game.opponentLogo,Modifier.size((21*s).dp))
                }
                if(index < today!!.games.lastIndex) Box(Modifier.fillMaxWidth().height(.35.dp).background(HubSecondary.copy(alpha=.25f)))
            }
            if(today?.games.isNullOrEmpty()) HubText("No teams followed",11*s,HubSecondary,modifier=Modifier.fillMaxWidth().padding(top=20.dp))
        }
    }
    Column(Modifier.align(Alignment.BottomCenter).padding(bottom=(13*s).dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.width((8*s).dp).height((1.7f*s).dp).background(HubLink.copy(alpha=if(scroll.value<scroll.maxValue).8f else .35f),CircleShape))
        Spacer(Modifier.height((2*s).dp))
        Box(Modifier.width((6*s).dp).height((1.5f*s).dp).background(HubLink.copy(alpha=.4f),CircleShape))
        HubText(if(scroll.maxValue>0 && scroll.value>=scroll.maxValue) "END OF LIST" else if(scroll.maxValue>0) "SCROLL FOR MORE" else "ALL ${if(detail==WatchTodayDetail.STOCKS) today?.stocks?.size ?: 0 else today?.games?.size ?: 0} SHOWN",
            6*s,HubSecondary,letterSpacing=.6f,modifier=Modifier.padding(top=(3*s).dp))
    }
}

@Composable
private fun Modifier.watchListScroll(scroll: ScrollState): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }
    return onRotaryScrollEvent { event ->
        scope.launch { scroll.scrollBy(event.verticalScrollPixels) }
        true
    }.focusRequester(focus).focusable().verticalScroll(scroll)
}

private fun shortTeam(name:String)=when {
    name.contains("Wisconsin",true) -> "Badgers"
    name.contains("Indiana",true) -> "Hoosiers"
    name.contains("Iowa",true) -> "Iowa"
    else -> name.substringAfterLast(' ')
}
private fun percent(value:Double?)=value?.let { "%+.2f%%".format(java.util.Locale.US,it) } ?: "—"
private fun movementColor(value:Double?)=if(value==null || value==0.0) HubSecondary else if(value<0) HubLoss else HubGain
private fun gameTime(value:Long?)=value?.let {
    DateTimeFormatter.ofPattern("EEE · h:mm a").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
} ?: "No upcoming game"

@Composable
private fun HubGlass(modifier:Modifier,content:@Composable BoxScope.()->Unit) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(
        Color(0xFF314259).copy(alpha=.77f),HubInk.copy(alpha=.82f))))
        .border(.5.dp,HubSecondary.copy(alpha=.35f),RoundedCornerShape(10.dp)),content=content)
}

@Composable
private fun HubText(text:String,size:Float,color:Color,bold:Boolean=false,serif:Boolean=false,
    letterSpacing:Float=0f,modifier:Modifier=Modifier,align:TextAlign=TextAlign.Center,maxLines:Int=1) {
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=size.sp,
        fontFamily=if(serif) FontFamily(Font(R.font.dm_serif_display_regular)) else FontFamily.SansSerif,
        fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,letterSpacing=letterSpacing.sp,textAlign=align),
        maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}

@Composable
private fun Sparkline(points:List<Double>,color:Color,modifier:Modifier) {
    Canvas(modifier) {
        if(points.size>1) {
            val min=points.min(); val range=(points.max()-min).coerceAtLeast(.001)
            val path=Path()
            points.forEachIndexed { i,v -> val x=i*size.width/(points.size-1); val y=size.height*(1-((v-min)/range).toFloat())
                if(i==0)path.moveTo(x,y) else path.lineTo(x,y) }
            drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(),cap=StrokeCap.Round))
            val endY=size.height*(1-((points.last()-min)/range).toFloat())
            val tip=Offset(size.width,endY)
            drawLine(color,tip,Offset(size.width-3.dp.toPx(),endY+1.dp.toPx()),1.dp.toPx(),StrokeCap.Round)
            drawLine(color,tip,Offset(size.width-1.dp.toPx(),endY+3.dp.toPx()),1.dp.toPx(),StrokeCap.Round)
        }
    }
}

@Composable
internal fun TeamLogo(name:String,address:String?,modifier:Modifier) {
    val resource=when {
        name.contains("Packers",true)->R.drawable.team_gb
        name.contains("Bears",true)->R.drawable.team_bears_c
        name.contains("Bucks",true)->R.drawable.team_mil
        name.contains("Knicks",true)->R.drawable.team_ny
        name.contains("Wisconsin",true)||name.contains("Badgers",true)->R.drawable.team_wisconsin
        name.contains("Iowa",true)->R.drawable.team_iowa
        name.contains("Indiana",true)||name.contains("Hoosiers",true)->R.drawable.team_indiana
        name.contains("Brewers",true)->R.drawable.team_brewers
        else->null
    }
    val context=LocalContext.current
    val preview=LocalInspectionMode.current
    val bitmap by produceState<ImageBitmap?>(null,address) {
        if(resource==null && !preview && address!=null) value=withContext(Dispatchers.IO) {
            runCatching {
                val url=URL(address)
                require(url.protocol=="https" && url.host=="a.espncdn.com")
                val key=MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
                val file=java.io.File(context.filesDir,"team-logos/$key.png")
                if(!file.exists()) {
                    val connection=url.openConnection().apply { connectTimeout=8000;readTimeout=8000 }
                    val bytes=connection.getInputStream().use { readWatchLogo(it) }
                    require(BitmapFactory.decodeByteArray(bytes,0,bytes.size)!=null)
                    file.parentFile?.mkdirs();file.writeBytes(bytes)
                }
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    if(resource!=null) Image(painterResource(resource),name,modifier,contentScale=ContentScale.Fit,
        colorFilter=if(resource==R.drawable.team_iowa) ColorFilter.tint(Color(0xFFFFCD00)) else null)
    else if(bitmap!=null) Image(checkNotNull(bitmap),name,modifier,contentScale=ContentScale.Fit)
    else Box(modifier,contentAlignment=Alignment.Center) { HubText(shortTeam(name).take(3).uppercase(),8f,HubSecondary) }
}

@Composable
private fun WeatherGlyph(kind:String?,night:Boolean,modifier:Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        if(kind=="CLEAR" || kind=="CLOUDY") {
            val center=Offset(w*.62f,h*.34f)
            if(night) {
                val outer=Path().apply { addOval(androidx.compose.ui.geometry.Rect(center-Offset(w*.23f,w*.23f),center+Offset(w*.23f,w*.23f))) }
                val cutCenter=center+Offset(w*.13f,-w*.08f)
                val inner=Path().apply { addOval(androidx.compose.ui.geometry.Rect(cutCenter-Offset(w*.22f,w*.22f),cutCenter+Offset(w*.22f,w*.22f))) }
                drawPath(Path.combine(PathOperation.Difference,outer,inner),HubLink)
            } else {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDA8C),Color(0xFFE5A94F)),center,w*.23f),w*.23f,center)
                repeat(8) { i ->
                val a=i*Math.PI/4
                val dx=kotlin.math.cos(a).toFloat(); val dy=kotlin.math.sin(a).toFloat()
                drawLine(Color(0xFFFFCB6C),center+Offset(dx,dy)*w*.29f,center+Offset(dx,dy)*w*.35f,
                    1.dp.toPx(),StrokeCap.Round)
                }
            }
        }
        if(kind!="CLEAR") {
            drawCircle(Color(0xFFE4EAF5),w*.23f,Offset(w*.34f,h*.57f))
            drawCircle(Color(0xFFF4F5FA),w*.28f,Offset(w*.52f,h*.5f))
            drawRoundRect(Color(0xFFE4EAF5),Offset(w*.12f,h*.55f),Size(w*.79f,h*.3f),androidx.compose.ui.geometry.CornerRadius(h*.15f))
        }
        if(kind=="RAIN" || kind=="STORM") repeat(3) { i -> drawLine(HubLink,Offset(w*(.28f+i*.23f),h*.87f),Offset(w*(.24f+i*.23f),h),1.dp.toPx()) }
        if(kind=="SNOW") repeat(3) { i -> drawCircle(Color.White,1.dp.toPx(),Offset(w*(.28f+i*.23f),h*.96f)) }
    }
}

@Composable
private fun ActivityGlyph(run:Boolean,modifier:Modifier) {
    Canvas(modifier.background(HubLink.copy(alpha=.1f),CircleShape)) {
        val c=HubLink; val w=size.width; val h=size.height; val stroke=1.8.dp.toPx()
        if(run) {
            drawCircle(c,w*.09f,Offset(w*.62f,h*.25f))
            listOf(.55f to .4f,.45f to .62f,.24f to .78f).zipWithNext().forEach { (a,b)->drawLine(c,Offset(w*a.first,h*a.second),Offset(w*b.first,h*b.second),stroke,StrokeCap.Round) }
            drawLine(c,Offset(w*.45f,h*.6f),Offset(w*.7f,h*.78f),stroke,StrokeCap.Round)
            drawLine(c,Offset(w*.5f,h*.45f),Offset(w*.7f,h*.5f),stroke,StrokeCap.Round)
        } else {
            drawLine(c,Offset(w*.15f,h*.2f),Offset(w*.85f,h*.2f),stroke)
            drawCircle(c,w*.09f,Offset(w*.5f,h*.36f))
            drawLine(c,Offset(w*.3f,h*.21f),Offset(w*.35f,h*.54f),stroke)
            drawLine(c,Offset(w*.7f,h*.21f),Offset(w*.65f,h*.54f),stroke)
            drawLine(c,Offset(w*.35f,h*.54f),Offset(w*.65f,h*.54f),stroke)
            drawLine(c,Offset(w*.43f,h*.55f),Offset(w*.43f,h*.84f),stroke)
            drawLine(c,Offset(w*.57f,h*.55f),Offset(w*.57f,h*.84f),stroke)
        }
    }
}
