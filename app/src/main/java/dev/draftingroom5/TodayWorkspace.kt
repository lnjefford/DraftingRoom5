package dev.draftingroom5

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.draftingroom5.retirement.domain.AssetAggregator
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val TodayBackground = Color(0xFF130F27)
internal val TodaySurface = Color(0xFF201A38)
internal val TodayBorder = Color(0xFF41365F)
internal val TodayCopper = Color(0xFFFFB778)
internal val TodayLavender = Color(0xFFCAB5FF)

@Composable
internal fun TodayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = TodayCopper,
            onPrimary = TodayBackground,
            secondary = TodayLavender,
            background = TodayBackground,
            onBackground = Color(0xFFF7F2FF),
            surface = TodaySurface,
            onSurface = Color(0xFFF7F2FF),
            surfaceVariant = TodaySurface,
            outlineVariant = TodayBorder,
        ),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TodayWorkspaceScreen(
    document: AppDocument,
    onSwitchWorkspace: (AppWorkspace) -> Unit,
    backupStatus: AutomaticBackupStatus = AutomaticBackupStatus(),
    backupActionMessage: String? = null,
    onAutomaticBackupChange: (Boolean) -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onRestoreLatest: () -> Unit = {},
    onOpenBackupSettings: () -> Unit = {},
    updatePresentation: UpdateSettingsPresentation = updateSettingsPresentation(AppUpdateStatus(), false, null, BuildConfig.VERSION_NAME),
    onUpdate: () -> Unit = {},
    previewWeather: TodayWeather? = null,
    previewRetirementBalance: String? = null,
    previewDate: LocalDate? = null,
    previewSettings: Boolean = false,
) {
    val context = LocalContext.current
    val locationPreferences = remember { TodayLocationPreferences(context) }
    val todayPreferences = remember { TodayPreferences(context) }
    var symbols by remember { mutableStateOf(todayPreferences.symbols()) }
    var teams by remember { mutableStateOf(todayPreferences.teams()) }
    var selectedCity by remember { mutableStateOf(locationPreferences.selectedCity()) }
    var weather by remember { mutableStateOf(previewWeather) }
    var weatherMessage by remember { mutableStateOf<String?>(null) }
    var refreshSerial by remember { mutableIntStateOf(0) }
    var locationEditorOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(previewSettings) }
    var symbolsDraft by rememberSaveable { mutableStateOf(if (previewSettings) symbols.joinToString(", ") else "") }
    var symbolsError by remember { mutableStateOf<String?>(null) }
    var teamsDraft by remember { mutableStateOf(TodayTeam.entries.toList()) }
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var cityError by remember { mutableStateOf<String?>(null) }
    var cityBusy by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSerial++
    }
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    LaunchedEffect(selectedCity, refreshSerial, hasLocationPermission) {
        if (previewDate != null) return@LaunchedEffect
        while (true) {
            val location = if (selectedCity == null) TodayWeatherSource.currentLocation(context) else null
            val target = selectedCity ?: location?.let { TodayCity("Current location", it.latitude, it.longitude) }
            if (target == null) {
                weatherMessage = if (hasLocationPermission) "Location unavailable. Choose a city." else "Allow location or choose a city."
            } else {
                runCatching { TodayWeatherSource.fetch(target.latitude, target.longitude, target.name) }
                    .onSuccess { weather = it; weatherMessage = null }
                    .onFailure { weatherMessage = "Weather could not refresh. Showing the last scene." }
            }
            delay(30 * 60 * 1000L)
        }
    }
    LaunchedEffect(Unit) {
        if (previewDate != null) return@LaunchedEffect
        if (selectedCity == null && !hasLocationPermission && locationPreferences.shouldPromptForLocation()) {
            locationPreferences.markLocationPrompted()
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    var retirementBalance by remember { mutableStateOf(previewRetirementBalance) }
    var retirementMessage by remember { mutableStateOf(if (previewDate != null) "Tracked retirement value" else "Loading your plan") }
    LaunchedEffect(Unit) {
        if (previewDate != null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val repository = RetirementProviders.get(context).repository
                AssetAggregator.totals(repository.load()).tracked.format()
            }
        }.onSuccess { retirementBalance = it; retirementMessage = "Tracked retirement value" }
            .onFailure { retirementMessage = "Retirement data unavailable" }
    }
    var today by remember { mutableStateOf(previewDate ?: LocalDate.now()) }
    LaunchedEffect(previewDate) {
        if (previewDate == null) while (true) { today = LocalDate.now(); delay(60_000) }
    }
    val workouts = remember(document, today) { dashboardSessions(document, today) }
    val localHour = weather?.localHour ?: LocalDateTime.now().hour
    val daypart = daypartForHour(localHour)
    val kind = weather?.kind ?: TodayWeatherKind.UNKNOWN
    val scene = todaySceneResource(kind, daypart)

    BackHandler(settingsOpen || locationEditorOpen) {
        if (locationEditorOpen) { locationEditorOpen = false; cityError = null }
        else settingsOpen = false
    }
    LaunchedEffect(cityBusy) {
        if (cityBusy) {
            runCatching { TodayWeatherSource.findCity(cityQuery) }
                .onSuccess { city ->
                    if (city == null) cityError = "City not found. Try a more specific name."
                    else {
                        locationPreferences.setSelectedCity(city)
                        selectedCity = city
                        locationEditorOpen = false
                        cityQuery = ""
                    }
                }
                .onFailure { cityError = "City search unavailable. Try again later." }
            cityBusy = false
        }
    }

    WorkspaceDrawer(
        active = AppWorkspace.TODAY,
        onSelect = onSwitchWorkspace,
        updatePresentation = updatePresentation,
        onUpdate = onUpdate,
        backupStatus = backupStatus,
        backupActionMessage = backupActionMessage,
        onAutomaticBackupChange = onAutomaticBackupChange,
        onBackUpNow = onBackUpNow,
        onRestoreLatest = onRestoreLatest,
        onOpenBackupSettings = onOpenBackupSettings,
    ) { openDrawer ->
        Scaffold(
            modifier = Modifier.fillMaxSize().background(TodayBackground),
            containerColor = TodayBackground,
            topBar = {
                TopAppBar(
                    title = {
                        if (settingsOpen || locationEditorOpen) Text(if (locationEditorOpen) "Weather location" else "Today settings")
                        else BrandTitle("DraftingRoom5", onLogoClick = openDrawer, activeWorkspace = AppWorkspace.TODAY)
                    },
                    navigationIcon = {
                        if (settingsOpen || locationEditorOpen) IconButton(onClick = {
                            if (locationEditorOpen) { locationEditorOpen = false; cityError = null }
                            else settingsOpen = false
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TodayLavender) }
                    },
                    actions = {
                        if (!settingsOpen && !locationEditorOpen) IconButton(onClick = {
                            symbolsDraft = symbols.joinToString(", ")
                            teamsDraft = teams
                            symbolsError = null
                            settingsOpen = true
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Today settings", tint = TodayLavender)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TodayBackground),
                )
            },
        ) { padding ->
            if (locationEditorOpen) Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Choose the place shown in Today's weather scene.", color = TodayLavender)
                TodaySettingLabel("CURRENT SOURCE")
                Text(selectedCity?.name ?: "Current location", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = {
                    locationPreferences.setSelectedCity(null)
                    selectedCity = null
                    locationEditorOpen = false
                    if (!hasLocationPermission) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    else refreshSerial++
                }) { Text("Use current location") }
                HorizontalDivider(color = TodayBorder)
                TodaySettingLabel("CHOOSE A CITY")
                TextField(value = cityQuery, onValueChange = { cityQuery = it }, label = { Text("City") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                cityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = cityQuery.isNotBlank() && !cityBusy, onClick = {
                    cityBusy = true
                    cityError = null
                }) { Text(if (cityBusy) "Searching…" else "Save city") }
            } else if (settingsOpen) Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Text("Make this briefing yours", style = MaterialTheme.typography.headlineMedium)
                Text("Choose what appears when you open DraftingRoom5.", color = TodayLavender)
                TodaySettingLabel("WEATHER")
                Row(Modifier.fillMaxWidth().clickable { locationEditorOpen = true },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Location", style = MaterialTheme.typography.titleMedium)
                        Text(selectedCity?.name ?: "Current location", color = TodayLavender)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Change weather location", tint = TodayCopper)
                }
                HorizontalDivider(color = TodayBorder)
                TodaySettingLabel("STOCKS TO FOLLOW")
                TextField(value = symbolsDraft, onValueChange = { symbolsDraft = it },
                    label = { Text("Symbols, separated by commas") }, modifier = Modifier.fillMaxWidth())
                symbolsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(color = TodayBorder)
                TodaySettingLabel("TEAMS TO FOLLOW")
                TodayTeam.entries.forEach { team ->
                    Row(Modifier.fillMaxWidth().clickable {
                        teamsDraft = if (team in teamsDraft) teamsDraft - team else teamsDraft + team
                    }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(team.displayName, modifier = Modifier.weight(1f).padding(end = 16.dp))
                        Switch(checked = team in teamsDraft, onCheckedChange = { checked ->
                            teamsDraft = if (checked) teamsDraft + team else teamsDraft - team
                        })
                    }
                }
                Button(onClick = {
                    val parsed = runCatching { parseSymbols(symbolsDraft) }
                    if (parsed.isFailure) symbolsError = parsed.exceptionOrNull()?.message
                    else {
                        symbols = parsed.getOrThrow()
                        teams = TodayTeam.entries.filter { it in teamsDraft }
                        todayPreferences.setSymbols(symbols)
                        todayPreferences.setTeams(teams)
                        settingsOpen = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save Today settings") }
                Spacer(Modifier.height(24.dp))
            } else Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TodayHero(scene, kind, today, weather, weatherMessage, onEditLocation = { locationEditorOpen = true },
                    onRefresh = { refreshSerial++ })
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    TodaySection("TODAY'S WORKOUT", onOpen = { onSwitchWorkspace(AppWorkspace.FITNESS) }) {
                        if (workouts.isEmpty()) Text("A clear day to move your way.", color = TodayLavender,
                            style = MaterialTheme.typography.titleLarge)
                        else workouts.take(3).forEach { session ->
                            Row(Modifier.fillMaxWidth().clickable { onSwitchWorkspace(AppWorkspace.FITNESS) }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(session.routine.name, style = MaterialTheme.typography.headlineMedium)
                                    Text(session.actionLabel, color = TodayLavender)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TodayCopper)
                            }
                        }
                    }
                    TodaySection("YOUR STOCKS") {
                        symbols.forEach { symbol ->
                            Row(Modifier.fillMaxWidth().clickable { context.openTodayLink(marketUrl(symbol)) }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(when (symbol) { "AAPL" -> "Apple"; "^IXIC" -> "Nasdaq Composite"; else -> symbol },
                                        style = MaterialTheme.typography.titleLarge)
                                    Text(symbol, color = TodayLavender, style = MaterialTheme.typography.labelMedium)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open quote for $symbol", tint = TodayCopper)
                            }
                            HorizontalDivider(color = TodayBorder)
                        }
                        Text("Open a symbol for the latest quote", color = TodayLavender, style = MaterialTheme.typography.bodySmall)
                    }
                    TodaySection("YOUR TEAMS") {
                        if (teams.isEmpty()) Text("No teams selected", color = TodayLavender)
                        teams.forEach { team ->
                            Row(Modifier.fillMaxWidth().clickable { context.openTodayLink(team.scheduleUrl) }.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(team.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, contentDescription = "Open schedule for ${team.displayName}", tint = TodayCopper)
                            }
                            HorizontalDivider(color = TodayBorder)
                        }
                        Text("Open a team for schedules and scores", color = TodayLavender, style = MaterialTheme.typography.bodySmall)
                    }
                    TodaySection("RETIREMENT GLANCE", onOpen = { onSwitchWorkspace(AppWorkspace.RETIREMENT) }) {
                        Text(retirementMessage, color = TodayLavender)
                        retirementBalance?.let { Text(it, style = MaterialTheme.typography.displaySmall, color = Color(0xFFB7EC82)) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun android.content.Context.openTodayLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun TodaySection(title: String, onOpen: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = TodayCopper, thickness = 2.dp, modifier = Modifier.width(52.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
            if (onOpen != null) IconButton(onClick = onOpen) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Open $title", tint = TodayCopper)
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun TodaySettingLabel(text: String) {
    Text(text, color = TodayCopper, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
}

@Composable
private fun TodayHero(
    scene: Int,
    kind: TodayWeatherKind,
    date: LocalDate,
    weather: TodayWeather?,
    message: String?,
    onEditLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val transition = rememberInfiniteTransition(label = "Today ambient weather")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "Weather drift")
    val heroHeight = if (LocalDensity.current.fontScale >= 1.5f) 460.dp else 300.dp
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        if (motion) Crossfade(scene, animationSpec = tween(1200), label = "Today scene") { selected ->
            Image(painterResource(selected), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Image(painterResource(scene), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TodayBackground.copy(alpha = 0.5f),
            Color.Transparent, TodayBackground.copy(alpha = 0.7f)))))
        if (motion && kind in setOf(TodayWeatherKind.RAIN, TodayWeatherKind.SNOW, TodayWeatherKind.STORM)) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(42) { index ->
                    val x = (index * 67.3f % size.width)
                    val y = ((index * 113.7f + phase * size.height) % size.height)
                    if (kind == TodayWeatherKind.SNOW) drawCircle(Color.White.copy(alpha = 0.45f), 2.2f, androidx.compose.ui.geometry.Offset(x, y))
                    else drawLine(TodayLavender.copy(alpha = 0.28f), androidx.compose.ui.geometry.Offset(x, y),
                        androidx.compose.ui.geometry.Offset(x - 5f, y + 18f), 1.2f, cap = StrokeCap.Round)
                }
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)).uppercase(locale),
                style = MaterialTheme.typography.labelLarge, color = Color.White, letterSpacing = 2.sp)
            Text("Your day, at a glance", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = TodayCopper, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(weather?.location ?: message ?: "Weather loading", color = Color.White)
            }
            weather?.let { Text("${it.temperatureF}°F  ·  ${kind.name.lowercase().replaceFirstChar(Char::uppercase)}",
                color = Color.White, modifier = Modifier.padding(start = 24.dp)) }
            TextButton(onClick = onEditLocation) { Text("Change location", color = TodayCopper) }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh weather", tint = Color.White)
        }
        Text("Weather by Open-Meteo", color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))
            })
    }
}
