package dev.draftingroom5.retirement.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.draftingroom5.AppRoute
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.importer.*
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun RetirementEpicHost(route: AppRoute, onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<RetirementState?>(null) }
    var review by remember { mutableStateOf<EpicImportReview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<WorkbookFailure?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var awaitingSelection by remember { mutableStateOf(false) }
    var openPickerOnEntry by rememberSaveable(route) { mutableStateOf(route == AppRoute.RetirementEpicUpload) }
    var repository by remember { mutableStateOf<dev.draftingroom5.retirement.data.RetirementRepository?>(null) }
    LaunchedEffect(route) {
        review = null; failure = null
        val loaded = withContext(Dispatchers.IO) { runCatching { RetirementProviders.get(context).repository.let { it to it.load() } }.getOrNull() }
        repository = loaded?.first; snapshot = loaded?.second
    }
    DisposableEffect(route) { onDispose { job?.cancel(); review = null; awaitingSelection = false } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // A picker result restored after process death/rotation must not restart an old import.
        if (!awaitingSelection) return@rememberLauncherForActivityResult
        awaitingSelection = false
        val before = snapshot
        val store = repository
        if (uri == null) { failure = WorkbookFailure.CANCELLED }
        else if (before != null && store != null) {
            val session = EpicImportSession(store)
            busy = true; review = null; failure = null
            job = scope.launch {
                try {
                    val work = currentCoroutineContext()
                    val result = withContext(Dispatchers.IO) {
                        try {
                            require(uri.scheme == "content")
                            val input = context.contentResolver.openInputStream(uri) ?: throw WorkbookRejected(WorkbookFailure.INVALID)
                            session.review(input, before) { !work.isActive }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (work.isActive) runCatching { session.rejected(before, Instant.now()) }
                            throw WorkbookRejected((error as? WorkbookRejected)?.reason ?: WorkbookFailure.INVALID)
                        }
                    }
                    ensureActive(); review = result
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: WorkbookRejected) {
                    failure = error.reason
                    snapshot = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrNull() } ?: before
                }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(route, snapshot, openPickerOnEntry) {
        if (route == AppRoute.RetirementEpicUpload && snapshot != null && openPickerOnEntry && !awaitingSelection && !busy) {
            openPickerOnEntry = false
            awaitingSelection = true
            try { picker.launch(arrayOf("application/vnd.ms-excel.sheet.macroEnabled.12", "application/octet-stream")) }
            catch (_: Exception) { awaitingSelection = false; failure = WorkbookFailure.INVALID }
        }
    }
    val state = snapshot
    if (state == null) {
        EpicPage("Epic stock", "Loading your saved Shareworks position.") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    } else if (route == AppRoute.RetirementEpicUpload) {
        EpicUploadPage(review?.candidate?.workbook, state.activeEpicImportId != null, busy, failure,
            onChoose = {
                awaitingSelection = true
                try { picker.launch(arrayOf("application/vnd.ms-excel.sheet.macroEnabled.12", "application/octet-stream")) }
                catch (_: Exception) { awaitingSelection = false; failure = WorkbookFailure.INVALID }
            },
            onConfirm = {
                val candidate = review
                val store = repository
                if (candidate != null && store != null && !busy) {
                    busy = true
                    job = scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { EpicImportSession(store).confirm(candidate, Instant.now()) }.getOrNull() }
                        busy = false
                        when (result) {
                            is RetirementResult.Success -> { review = null; snapshot = result.value; onBack() }
                            is RetirementResult.Conflict -> { review = null; failure = WorkbookFailure.CONFLICT
                                snapshot = withContext(Dispatchers.IO) { runCatching { store.load() }.getOrNull() } }
                            else -> failure = WorkbookFailure.SAVE_FAILED
                        }
                    }
                }
            },
            onCancel = { job?.cancel(); review = null; onBack() },
        )
    } else {
        EpicDetailPage(state) { onNavigate(AppRoute.RetirementEpicUpload) }
    }
}

@Composable
internal fun EpicPage(
    title: String,
    subtitle: String = "Private Shareworks values, processed and stored on this phone.",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RetirementFlowHero("Epic stock", title, subtitle, Icons.Default.Savings)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EpicSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable private fun EpicValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun EpicUploadPage(book: EpicWorkbook?, replacing: Boolean, busy: Boolean, failure: WorkbookFailure?, onChoose: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    EpicPage(
        if (book == null) "Import your workbook" else "Review imported values",
        if (book == null) "Choose your recalculated Shareworks .xlsm file. We'll extract the saved Epic values."
        else "Make sure these headline values look right, then save them to your plan.",
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Private by design", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text("Macros never run. The workbook and filename are not retained.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        failure?.let {
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(workbookMessage(it), modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Checking workbook…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (book != null) {
            EpicSection("Ready to save") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Workbook passed validation", color = MaterialTheme.colorScheme.primary)
                }
                EpicValue("Share price", book.sharePrice.format())
                EpicValue("Vested value", book.totals.vestedValue.format())
                EpicValue("Loans", book.totals.loans.format())
                EpicValue("Workbook projection date", book.projection.date.toString())
                EpicValue("After tax at workbook date", book.projection.afterTax.format())
                EpicValue("Annual projections", if (book.years.isEmpty()) "Unavailable in this workbook" else "${book.years.size} saved years • ${book.years.first().year}–${book.years.last().year}")
            }
            Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (replacing) "Update Epic data" else "Save Epic data")
            }
            OutlinedButton(onClick = onChoose, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose a different workbook") }
        } else {
            Button(onClick = onChoose, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Choose Epic workbook") }
        }
        TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
    }
}

@Composable
internal fun EpicDetailPage(state: RetirementState, onUpload: () -> Unit) {
    val accepted = state.epicImports.singleOrNull { it.id == state.activeEpicImportId }
    EpicPage("Your Epic position", "Saved Shareworks values, vesting, loans, and projections in one place.") {
        Button(onClick = onUpload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (accepted == null) "Import Epic workbook" else "Update from workbook") }
        if (state.importMetadata.any { it.source == "SHAREWORKS" && it.status == ImportStatus.NEEDS_ATTENTION }) {
            Text("The last upload failed validation. Saved Epic data is unchanged. Choose an updated workbook.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (accepted == null) {
            Text("No workbook accepted yet. Upload your Shareworks workbook to see shares, loans, vesting and saved projections.")
        } else {
            val book = accepted.workbook
            val plan = state.planSettings.maxByOrNull { it.revision }
            EpicSection("Import provenance") {
                EpicValue("Accepted on this phone", DateTimeFormatter.ofPattern("MMM d, uuuu • HH:mm").withZone(ZoneId.systemDefault()).format(accepted.acceptedAt))
                EpicValue("Source", "Shareworks 2026 • parser ${accepted.parserVersion}")
                Text("Saved workbook results. Import time is not a market-price timestamp. Update values by uploading a recalculated workbook.")
                epicDateNotice(book, plan?.birthDate?.plusYears(plan.retirementAge.toLong()))?.let { Text(it) }
            }
            EpicSection("Current position") {
                EpicValue("Share price", book.sharePrice.format())
                EpicTotalsContent(book.totals)
            }
            book.breakdown.forEach { row -> EpicSection(row.name) { EpicTotalsContent(row.totals) } }
            EpicProjectionSection(book)
            EpicAnnualSection(book)

        }
    }
}

@Composable internal fun EpicTotalsContent(t: EpicTotals) {
    EpicValue("Shares granted", t.sharesGranted); EpicValue("Vested shares", t.vestedShares); EpicValue("Vested value", t.vestedValue.format())
    EpicValue("Unvested shares", t.unvestedShares); EpicValue("Unvested value", t.unvestedValue.format()); EpicValue("Loans from Epic", t.loans.format())
    EpicValue("Pre-tax today, less loans", t.pretaxMinusLoans.format()); EpicValue("Pre-tax today", t.pretaxToday.format()); EpicValue("Pre-tax if all vested", t.pretaxAllVested.format())
}
private fun percent(value: String) = java.math.BigDecimal(value).movePointRight(2).stripTrailingZeros().toPlainString() + "%"

@Composable
internal fun EpicProjectionSection(book: EpicWorkbook) {
            EpicSection("Workbook projection") {
                val p = book.projection
                EpicValue("Projection date", p.date.toString())
                EpicValue("Annual growth assumption", percent(p.growth))
                EpicValue("Income tax assumption", percent(p.incomeTaxRate))
                EpicValue("Pay down loans with shares", p.paydownWithShares)
                EpicValue("Pre-tax value at date", p.pretax.format()); EpicValue("Loans due", p.loans.format())
                EpicValue("Net pre-tax", p.netPretax.format()); EpicValue("Tax at sale", p.taxAtSale.format()); EpicValue("After tax", p.afterTax.format())
                EpicValue("Historical growth volatility", book.historicalVolatilityPct?.let { "$it%" } ?: "Unavailable • at least two saved history observations required")
            }
}

@Composable
internal fun EpicAnnualSection(book: EpicWorkbook) {
            EpicSection("Annual projections") {
                if (book.years.isEmpty()) Text("Unavailable in this workbook. Missing years are never extrapolated.")
                var yearIndex by remember(book) { mutableIntStateOf(0) }
                if (book.years.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { yearIndex-- }, enabled = yearIndex > 0, modifier = Modifier.weight(1f)) { Text("Previous year") }
                        OutlinedButton(onClick = { yearIndex++ }, enabled = yearIndex < book.years.lastIndex, modifier = Modifier.weight(1f)) { Text("Next year") }
                    }
                }
                book.years.getOrNull(yearIndex)?.let { year ->
                    Text(year.year.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    EpicValue("Shares vesting", year.sharesVesting)
                    listOf("Vested value" to year.vested, "Unvested value" to year.unvested, "Total before loans" to year.totalBeforeLoans,
                        "Loans outstanding" to year.loans, "Net pre-tax" to year.netPretax, "Cost basis" to year.costBasis,
                        "Taxable gain" to year.taxableGain, "Capital gains tax" to year.capitalGainsTax,
                        "SAR ordinary tax" to year.sarOrdinaryTax, "After tax" to year.afterTax).forEach { (label, value) -> EpicValue(label, value.format()) }
                }
            }
}
