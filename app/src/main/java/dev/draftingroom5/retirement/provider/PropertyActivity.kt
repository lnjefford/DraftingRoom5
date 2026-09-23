package dev.draftingroom5.retirement.provider

import android.app.KeyguardManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.draftingroom5.RetirementTheme
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Private property search/edit task. Address drafts, API keys and provider responses are never saved to Bundle. */
class PropertyActivity : ComponentActivity() {
    private val runtime by lazy { RetirementProviders.get(this) }
    private var credential by mutableStateOf<CredentialHandle?>(null)
    private var setupCredential by mutableStateOf(false)
    private var manual by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var match by mutableStateOf<PropertyMatch?>(null)
    private var editing by mutableStateOf<Property?>(null)
    private var authenticatedUntil = 0L
    private var keyField: EditText? = null

    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) { authenticatedUntil = android.os.SystemClock.elapsedRealtime() + 120_000; setupCredential = true }
        else message = "Device authentication cancelled. Manual property entry remains available."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { RetirementTheme { Content() } }
        perform {
            credential = withContext(Dispatchers.IO) { runtime.rentCast.credential() }
            intent.getStringExtra(PROPERTY_ID)?.let { id ->
                editing = withContext(Dispatchers.IO) { runtime.repository.load().properties.singleOrNull { it.id == id && it.archivedAt == null } }
                if (editing == null) message = "This property is unavailable."
            }
        }
    }

    override fun onDestroy() { keyField?.text?.clear(); super.onDestroy() }

    private fun authenticate() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) { message = "Set a device screen lock before entering provider credentials. Manual property entry remains available."; return }
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("Protect provider credentials", "Authenticate to set up or replace your private RentCast key.")
        if (intent != null) authentication.launch(intent) else message = "Device authentication is unavailable."
    }

    private fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        lifecycleScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: ProviderException) { message = safeMessage(e.failure) }
            catch (_: Exception) { message = "The operation could not be completed. Last accepted property values are unchanged." }
            finally { busy = false }
        }
    }

    @Composable @OptIn(ExperimentalMaterial3Api::class) private fun Content() {
        BackHandler(enabled = !busy) {
            when {
                setupCredential -> { setupCredential = false; keyField?.text?.clear() }
                match != null -> match = null
                manual -> manual = false
                else -> finish()
            }
        }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(when {
                    editing != null -> "Edit property"
                    setupCredential -> "RentCast key"
                    match != null -> "Review property"
                    manual -> "Add property"
                    else -> "Find a property"
                }) },
                actions = { IconButton(onClick = { finish() }, enabled = !busy) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                } },
            )
        }) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { PropertyNoticeCard(it) }
                when {
                    setupCredential -> CredentialForm()
                    editing != null -> EditForm(editing!!)
                    match != null -> PropertyForm(match!!, automatic = true)
                    manual -> PropertyForm(null, automatic = false)
                    else -> SearchForm()
                }
            }
        }
    }

    @Composable private fun SearchForm() {
        var address by remember { mutableStateOf("") }
        PropertySearchContent(
            credentialReady = credential != null,
            address = address,
            busy = busy,
            onAddressChange = { address = it.take(240) },
            onSetup = ::authenticate,
            onFind = { perform { match = withContext(Dispatchers.IO) { runtime.rentCast.find(address.trim()) } } },
            onManual = { manual = true },
        )
    }

    @Composable private fun CredentialForm() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text("Set up automatic estimates", style = MaterialTheme.typography.headlineMedium)
            Text("Your RentCast key is encrypted on this phone and can't be viewed after saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AndroidView(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), factory = { context ->
            EditText(context).apply {
                contentDescription = "RentCast API key"; hint = "RentCast API key"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_DONE
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false; setSaveFromParentEnabled(false); setSingleLine(true)
                if (android.os.Build.VERSION.SDK_INT >= 30) importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
                setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.LTGRAY); keyField = this
            }
        })
        Button(enabled = !busy, onClick = {
            if (android.os.SystemClock.elapsedRealtime() >= authenticatedUntil) { setupCredential = false; authenticate(); return@Button }
            authenticatedUntil = 0
            val key = keyField?.text?.toString().orEmpty().toCharArray(); keyField?.text?.clear()
            perform {
                try { credential = withContext(Dispatchers.IO) { runtime.rentCast.provision(key, credential) }; setupCredential = false }
                finally { key.fill('\u0000') }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save RentCast key") }
    }

    @Composable private fun PropertyForm(found: PropertyMatch?, automatic: Boolean) {
        var address by remember { mutableStateOf(found?.formattedAddress.orEmpty()) }
        var value by remember { mutableStateOf(found?.estimate?.format().orEmpty()) }
        var mortgage by remember { mutableStateOf("$0.00") }
        var payment by remember { mutableStateOf("$0.00") }
        var rate by remember { mutableStateOf("0") }
        var months by remember { mutableStateOf("0") }
        var ownership by remember { mutableStateOf("100") }
        var owner by remember { mutableStateOf(Owner.JOINT) }
        var error by remember { mutableStateOf<String?>(null) }
        if (found != null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Property found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(found.formattedAddress, style = MaterialTheme.typography.titleMedium)
                    found.facts?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(found.estimate.format(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (found.rangeLow != null) Text("Estimate range ${found.rangeLow.format()}–${found.rangeHigh!!.format()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${found.comparableCount} comparable sales · ${found.providerAsOf}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.HomeWork, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Text("Add property manually", style = MaterialTheme.typography.headlineMedium)
                Text("Enter a current value now. You can add updated values later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Property details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(address, { address = it.take(240) }, label = { Text("Property address") }, modifier = Modifier.fillMaxWidth(), enabled = !automatic)
        OutlinedTextField(value, { value = it.take(40) }, label = { Text("Current value (USD)") }, modifier = Modifier.fillMaxWidth(), enabled = !automatic)
        Text("Mortgage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Optional. Leave these at zero if the property is paid off.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(mortgage, { mortgage = it.take(40) }, label = { Text("Mortgage balance (USD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(payment, { payment = it.take(40) }, label = { Text("Monthly payment (USD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(rate, { rate = it.take(8) }, label = { Text("Annual rate (%)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(months, { months = it.filter(Char::isDigit).take(3) }, label = { Text("Months remaining") }, modifier = Modifier.fillMaxWidth())
        Text("Ownership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(ownership, { ownership = it.take(8) }, label = { Text("Ownership (%)") }, modifier = Modifier.fillMaxWidth())
        dev.draftingroom5.retirement.ui.PropertyOwnerPicker(owner) { owner = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, onClick = {
            val parsed = runCatching {
                val amount = Money.parse(value); val debt = Money.parse(mortgage); val monthly = Money.parse(payment)
                val bps = java.math.BigDecimal(rate).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact()
                val remaining = months.toInt(); require(address.isNotBlank() && amount.cents >= 0 && debt.cents >= 0 && monthly.cents >= 0 && bps in 0..2500 && remaining in 0..600)
                val share = java.math.BigDecimal(ownership).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact()
                require(share in 0..10000)
                PropertyInput(address.trim(), amount, debt, monthly, bps, remaining, owner, share)
            }.getOrNull()
            if (parsed == null) error = "Enter a valid address, nonnegative dollar values, rate from 0–25%, and 0–600 months."
            else perform {
                val state = withContext(Dispatchers.IO) { runtime.repository.load() }
                val records = buildPropertyRecords(parsed, found, credential, Instant.now(), LocalDate.now())
                val result = withContext(Dispatchers.IO) {
                    if (automatic) runtime.vault.guarded(listOf(requireNotNull(found?.credential))) {
                        runtime.repository.addProviderProperty(state.generation, records.first, records.second,
                            ProviderItemState(records.second.id, records.first.id, ProviderStatus.READY, Instant.now(), Instant.now(), null, null, 1, null))
                    }
                    else runtime.repository.addManualProperty(state.generation, records.first, records.second)
                }
                if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
                if (automatic) RetirementPropertySyncWorker.schedule(this@PropertyActivity, records.second.id)
                finish()
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (automatic) "Save property" else "Add property") }
    }

    @Composable private fun EditForm(property: Property) {
        val current = property.currentRevision
        if (current.providerPropertyId != null) Button(onClick = ::authenticate, enabled = !busy) { Text("Replace RentCast key") }
        dev.draftingroom5.retirement.ui.PropertyEditFields(current) { revision ->
            perform {
                val result = withContext(Dispatchers.IO) {
                    runtime.repository.reviseProperty(runtime.repository.load().generation, property.id,
                        if (revision.providerPropertyId != null && credential != null) revision.copy(credentialProfileId = credential!!.id) else revision)
                }
                if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
                if (revision.automaticValueEnabled) RetirementPropertySyncWorker.schedule(this@PropertyActivity, property.id)
                else RetirementPropertySyncWorker.cancel(this@PropertyActivity, property.id)
                finish()
            }
        }
    }

    companion object { const val PROPERTY_ID = "property" }
}

@Composable
internal fun PropertySearchContent(
    credentialReady: Boolean,
    address: String,
    busy: Boolean,
    onAddressChange: (String) -> Unit,
    onSetup: () -> Unit,
    onFind: () -> Unit,
    onManual: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.HomeWork, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text("Track your home's value", style = MaterialTheme.typography.headlineMedium)
        Text("Search by address, review the estimate, then add mortgage details.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (credentialReady) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (credentialReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(if (credentialReady) "Automatic estimates are ready" else "Automatic estimates need setup", fontWeight = FontWeight.SemiBold)
                Text(
                    if (credentialReady) "Powered by your private RentCast connection."
                    else "Add your RentCast key to search for a property.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (credentialReady) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text("Property address") },
            placeholder = { Text("123 Main St, City, ST 12345") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = address.trim().length >= 8 && !busy,
            onClick = onFind,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (busy) Spacer(Modifier.width(8.dp))
            Text(if (busy) "Finding property…" else "Find property")
        }
    } else {
        Button(onClick = onSetup, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Set up automatic estimates")
        }
    }
    OutlinedButton(onClick = onManual, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add property manually") }
    TextButton(onClick = { showSettings = !showSettings }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (showSettings) "Hide property settings" else "Property settings")
    }
    if (showSettings) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Property estimates are not appraisals. Your key is encrypted on this phone and excluded from backups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSetup, enabled = !busy) { Text(if (credentialReady) "Replace RentCast key" else "Set up RentCast key") }
        }
    }
}

@Composable
private fun PropertyNoticeCard(text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text, modifier = Modifier.weight(1f))
        }
    }
}

internal data class PropertyInput(val address: String, val value: Money, val mortgage: Money, val payment: Money, val rateBps: Int,
    val remainingMonths: Int, val owner: Owner = Owner.JOINT, val ownershipBps: Int = 10000)

internal fun buildPropertyRecords(input: PropertyInput, match: PropertyMatch?, credential: CredentialHandle?, now: Instant, today: LocalDate): Pair<Account, Property> {
    val automatic = match != null
    require(!automatic || credential != null)
    require(!automatic || match?.credential == credential)
    val accountId = UUID.randomUUID().toString(); val propertyId = UUID.randomUUID().toString()
    val mortgage = MortgageTerms(input.mortgage, today, null, input.rateBps, input.payment, input.remainingMonths, input.remainingMonths)
    val propertyRevision = PropertyRevision(UUID.randomUUID().toString(), 1, input.address, match?.facts, input.owner, input.ownershipBps, mortgage,
        automatic, true, now, providerPropertyId = match?.providerPropertyId, credentialProfileId = if (automatic) credential?.id else null)
    val valuation = match?.snapshot(1, now) ?: PropertyValuationSnapshot(UUID.randomUUID().toString(), input.value, null, null, null,
        ValuationSource.MANUAL, today, now, 1, UUID.randomUUID().toString())
    val accountRevision = AccountRevision(UUID.randomUUID().toString(), 1, input.address.take(120), input.owner, AccountType.PROPERTY,
        TaxTreatment.PROPERTY, true, now)
    return Account(accountId, AccountOrigin.PROPERTY, null, null, listOf(accountRevision), emptyList()) to
        Property(propertyId, accountId, null, listOf(propertyRevision), listOf(valuation))
}
