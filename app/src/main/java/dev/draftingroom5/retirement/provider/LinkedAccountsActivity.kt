package dev.draftingroom5.retirement.provider

import android.app.KeyguardManager
import android.os.Bundle
import android.content.Intent
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.plaid.link.FastOpenPlaidLink
import com.plaid.link.OnLoadCallback
import com.plaid.link.Plaid
import com.plaid.link.PlaidHandler
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.configuration.LinkLogLevel
import com.plaid.link.result.LinkSuccess
import dev.draftingroom5.RetirementTheme
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Private connect/reconnect task. Drafts and SDK results are deliberately not saved to Bundle. */
class LinkedAccountsActivity : ComponentActivity() {
    private val runtime by lazy { RetirementProviders.get(this) }
    private var profiles by mutableStateOf<List<CredentialHandle>>(emptyList())
    private var profile by mutableStateOf<CredentialHandle?>(null)
    private var environmentChangeAllowed by mutableStateOf(true)
    private var setup by mutableStateOf(false)
    private var manual by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var plaidOpening by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var review by mutableStateOf<AccountBatch?>(null)
    private var reviewGeneration = 0L
    private var manualAccounts by mutableStateOf<List<Account>>(emptyList())
    private var attempt: LinkAttempt? = null
    private var authenticatedUntil = 0L
    private var reconnectItem: String? = null
    private var clientField: EditText? = null
    private var secretField: EditText? = null

    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) { authenticatedUntil = android.os.SystemClock.elapsedRealtime() + 120_000; setup = true }
        else message = "Device authentication cancelled. You can still add an account manually."
    }
    private val link = registerForActivityResult(FastOpenPlaidLink()) { result ->
        Plaid.destroy()
        val active = attempt; attempt = null
        if (active == null) { message = "Link was interrupted. Resume a pending review or start again."; return@registerForActivityResult }
        if (result !is LinkSuccess) {
            runtime.plaid.cancelLink(active.id); message = "Connection cancelled or unavailable. Try again or add an account manually."
        } else {
            val token = result.publicToken?.toCharArray()
            perform {
                val item = withContext(Dispatchers.IO) { runtime.plaid.completeLink(active.id, token) }
                if (active.item == null) loadReview(item) else {
                    val failure = withContext(Dispatchers.IO) { runtime.plaid.refresh(item) }
                    if (failure != null) throw ProviderException(failure)
                    RetirementAccountSyncWorker.schedule(this@LinkedAccountsActivity, item)
                    message = "The affected connection is repaired. Your classifications and other connections are unchanged."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        reconnectItem = intent.getStringExtra("item")?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        manual = intent.getBooleanExtra("manual", false)
        setContent { RetirementTheme { Content() } }
        perform {
            profiles = withContext(Dispatchers.IO) { runtime.plaid.profiles() }
            profile = if (reconnectItem == null) profiles.firstOrNull() else withContext(Dispatchers.IO) {
                val account = runtime.repository.load().accounts.firstOrNull { it.providerIdentity?.itemId == reconnectItem }
                profiles.firstOrNull { it.id == account?.providerIdentity?.credentialProfileId }
            }
            environmentChangeAllowed = profile?.let { withContext(Dispatchers.IO) { runtime.plaid.canChangeEnvironment(it) } } ?: true
            manualAccounts = withContext(Dispatchers.IO) { matchableAccounts() }
            if (reconnectItem != null && withContext(Dispatchers.IO) { runtime.repository.load().providerItems.any { it.id == reconnectItem && it.revokedAt != null } }) {
                reconnectItem = null
                message = "This connection was removed. Link again and explicitly match its disconnected accounts during review."
            }
        }
    }

    override fun onDestroy() {
        clientField?.text?.clear(); secretField?.text?.clear()
        attempt?.let { runtime.plaid.cancelLink(it.id) }
        super.onDestroy()
    }
    private fun authenticate() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) { message = "Set a device screen lock before entering provider credentials. Manual accounts remain available."; return }
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent("Protect provider credentials", "Authenticate to set up or replace your private credentials.")
        if (intent != null) authentication.launch(intent) else message = "Device authentication is unavailable."
    }
    private fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        lifecycleScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: ProviderException) { message = safeMessage(e.failure) }
            catch (_: Exception) { message = "The operation could not be completed. Accepted data is unchanged; retry or use a manual account." }
            finally { busy = false }
        }
    }
    private fun openPlaid() {
        val selectedProfile = profile ?: return
        if (busy || plaidOpening) return
        plaidOpening = true
        message = null
        lifecycleScope.launch {
            var pending: LinkAttempt? = null
            try {
                val active = withContext(Dispatchers.IO) { runtime.plaid.beginLink(selectedProfile.id, reconnectItem) }
                pending = active
                attempt = active
                val configuration = LinkTokenConfiguration.Builder().token(active.takeToken()).logLevel(LinkLogLevel.ASSERT).build()
                val opened = CompletableDeferred<Boolean>()
                var handler: PlaidHandler? = null
                handler = Plaid.create(application, configuration, object : OnLoadCallback {
                    override fun onLoad() {
                        window.decorView.post {
                            val readyHandler = handler
                            if (attempt?.id != active.id || readyHandler == null || opened.isCompleted) return@post
                            opened.complete(runCatching { link.launch(readyHandler) }.isSuccess)
                        }
                    }
                })
                if (!withTimeout(30_000) { opened.await() }) throw ProviderException(ProviderFailure.UNAVAILABLE)
            } catch (_: TimeoutCancellationException) {
                Plaid.destroy()
                message = "Plaid took too long to open. Check your connection and try again."
                pending?.let { runtime.plaid.cancelLink(it.id) }
                if (attempt?.id == pending?.id) attempt = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                Plaid.destroy()
                message = safeMessage(e.failure)
                pending?.let { runtime.plaid.cancelLink(it.id) }
                if (attempt?.id == pending?.id) attempt = null
            } catch (_: Exception) {
                Plaid.destroy()
                message = "Plaid couldn't open. Try again."
                pending?.let { runtime.plaid.cancelLink(it.id) }
                if (attempt?.id == pending?.id) attempt = null
            } finally {
                plaidOpening = false
            }
        }
    }
    private suspend fun loadReview(itemId: String) {
        val result = withContext(Dispatchers.IO) {
            runtime.repository.load().generation to runtime.plaid.fetchSnapshot(itemId)
        }
        reviewGeneration = result.first; review = result.second
        manualAccounts = withContext(Dispatchers.IO) { matchableAccounts() }
    }
    private fun matchableAccounts(): List<Account> {
        val state = runtime.repository.load()
        return state.accounts.filter { account -> account.archivedAt == null && (account.origin == AccountOrigin.MANUAL ||
            (account.origin == AccountOrigin.PLAID && state.providerItems.any { it.id == account.providerIdentity?.itemId && it.revokedAt != null })) }
    }

    @Composable @OptIn(ExperimentalMaterial3Api::class) private fun Content() {
        BackHandler(enabled = !busy && !plaidOpening) {
            if (setup) { setup = false; clientField?.text?.clear(); secretField?.text?.clear() }
            else finish()
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when {
                        manual -> "Add account"
                        setup -> "Plaid credentials"
                        review != null -> "Review accounts"
                        reconnectItem != null -> "Reconnect account"
                        else -> "Connect accounts"
                    }) },
                    actions = { IconButton(onClick = { finish() }, enabled = !busy && !plaidOpening) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    } },
                )
            },
        ) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { NoticeCard(it) }
                when {
                    manual -> ManualForm()
                    setup -> CredentialsForm()
                    review != null -> ReviewForm(review!!)
                    else -> ConnectionHome()
                }
            }
        }
    }

    @Composable private fun ConnectionHome() {
        ConnectionHomeContent(
            environment = profile?.environment,
            reconnecting = reconnectItem != null,
            busy = busy,
            plaidOpening = plaidOpening,
            onAuthenticate = ::authenticate,
            onOpenPlaid = ::openPlaid,
            onManual = { manual = true },
            onResume = { perform {
                val pending = withContext(Dispatchers.IO) { runtime.plaid.pendingItems() }
                if (pending.isEmpty()) message = "There isn't an unfinished connection to review."
                else loadReview(pending.first())
            } },
        )
    }

    @Composable private fun NoticeCard(text: String) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text, modifier = Modifier.weight(1f))
            }
        }
    }

    @Composable private fun CredentialsForm() {
        var environment by remember { mutableStateOf(profile?.environment ?: ProviderEnvironment.PRODUCTION) }
        Text("Encrypted on this phone. Your credentials can't be viewed after saving.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (environmentChangeAllowed) Choice(
            "Environment",
            environment,
            listOf(ProviderEnvironment.PRODUCTION, ProviderEnvironment.SANDBOX),
        ) { environment = it }
        Text(
            when (environment) {
                ProviderEnvironment.PRODUCTION -> "Production connects real institutions."
                ProviderEnvironment.SANDBOX -> "Sandbox accepts Plaid test credentials only."
                ProviderEnvironment.DEVELOPMENT -> "Development uses Plaid's legacy development environment."
            } + if (!environmentChangeAllowed) " Disconnect linked accounts before changing environments." else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecretField("Plaid client ID") { clientField = it }
        SecretField("Plaid secret") { secretField = it }
        Button(enabled = !busy, onClick = {
            if (android.os.SystemClock.elapsedRealtime() >= authenticatedUntil) { setup = false; authenticate(); return@Button }
            authenticatedUntil = 0
            val client = clientField?.text?.toString().orEmpty().toCharArray()
            val secret = secretField?.text?.toString().orEmpty().toCharArray()
            perform {
                try {
                    profile = withContext(Dispatchers.IO) { runtime.plaid.saveCredentials(client, secret, environment, profile) }
                    environmentChangeAllowed = true
                    profiles = withContext(Dispatchers.IO) { runtime.plaid.profiles() }
                    clientField?.text?.clear(); secretField?.text?.clear()
                    setup = false
                    message = "Plaid credentials saved securely on this phone."
                }
                finally { client.fill('\u0000'); secret.fill('\u0000') }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save credentials") }
        if (profile != null) {
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = !busy, onClick = {
                if (android.os.SystemClock.elapsedRealtime() >= authenticatedUntil) { setup = false; authenticate(); return@TextButton }
                authenticatedUntil = 0
                clientField?.text?.clear(); secretField?.text?.clear()
                val previous = profile!!
                perform {
                    val revoked = withContext(Dispatchers.IO) {
                        runtime.repository.load().accounts.mapNotNull { it.providerIdentity }.filter { it.credentialProfileId == previous.id }
                            .map { it.itemId }.distinct().forEach { RetirementAccountSyncWorker.cancel(this@LinkedAccountsActivity, it) }
                        runtime.plaid.removeCredentials(previous)
                    }
                    profile = null; environmentChangeAllowed = true; reconnectItem = null; setup = false
                    message = if (revoked) "Credentials and remote connections removed. Accepted history remains." else "Credentials removed locally. Revoke remote access from your Plaid or institution dashboard."
                }
            }) { Text("Remove credentials and connections") }
        }
        TextButton(enabled = !busy, onClick = {
            if (android.os.SystemClock.elapsedRealtime() >= authenticatedUntil) { setup = false; authenticate(); return@TextButton }
            authenticatedUntil = 0
            clientField?.text?.clear(); secretField?.text?.clear()
            perform {
                withContext(Dispatchers.IO) {
                    runtime.vault.resetAll(beforeErase = {
                        val state = runtime.repository.load()
                        state.providerItems.forEach {
                            if (state.properties.any { property -> property.id == it.id }) RetirementPropertySyncWorker.cancel(this@LinkedAccountsActivity, it.id)
                            else RetirementAccountSyncWorker.cancel(this@LinkedAccountsActivity, it.id)
                        }
                        if (runtime.repository.revokeProviderCredentials(state.generation, Instant.now()) !is RetirementResult.Success)
                            throw ProviderException(ProviderFailure.CONFLICT)
                    }, deleteKey = { runtime.key.delete() })
                }
                profile = null; reconnectItem = null; setup = false
                message = "Provider credentials reset locally. Enter credentials and link again; explicitly match disconnected accounts during review to preserve their history. Revoke old remote access from your provider dashboard."
            }
        }) { Text("Reset all unavailable provider credentials") }
    }

    @Composable private fun SecretField(label: String, attach: (EditText) -> Unit) {
        Text(label)
        AndroidView(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), factory = { context ->
            EditText(context).apply {
                contentDescription = label; hint = label
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_NEXT
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false; setSaveFromParentEnabled(false); setSingleLine(true)
                if (android.os.Build.VERSION.SDK_INT >= 30) importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
                setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.LTGRAY)
                attach(this)
            }
        })
    }

    @Composable private fun ReviewForm(batch: AccountBatch) {
        ReviewAccountsContent(batch.accounts, manualAccounts, busy, onSave = { selections ->
            perform {
                val result = withContext(Dispatchers.IO) { runtime.plaid.accept(batch, reviewGeneration, selections.values.toList()) }
                if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
                RetirementAccountSyncWorker.schedule(this@LinkedAccountsActivity, batch.item.id)
                review = null; message = "Accounts saved. Scheduled refresh is approximately daily and subject to Android constraints."
            }
        }, onDiscard = { perform {
            val revoked = withContext(Dispatchers.IO) { runtime.plaid.disconnect(batch.item.id) }
            RetirementAccountSyncWorker.cancel(this@LinkedAccountsActivity, batch.item.id)
            review = null
            message = if (revoked) "Connection removed." else "Removed from this phone. Remote revocation could not be confirmed; revoke access in your Plaid or institution dashboard."
        } })
    }

    @Composable private fun ManualForm() {
        var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(AccountType.BROKERAGE) }; var tax by remember { mutableStateOf(TaxTreatment.TAXABLE) }
        var owner by remember { mutableStateOf(Owner.SELF) }; var included by remember { mutableStateOf(false) }
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Account name") })
        OutlinedTextField(amount, { amount = it.take(40) }, label = { Text("Current balance (USD)") })
        Choice("Type", type, AccountType.entries.filter { it !in setOf(AccountType.PROPERTY, AccountType.EPIC) }) { type = it }
        Choice("Tax treatment", tax, TaxTreatment.entries.filter { it !in setOf(TaxTreatment.PROPERTY, TaxTreatment.EPIC) }) { tax = it }
        Choice("Owner", owner, Owner.entries) { owner = it }
        Row { Checkbox(included, { included = it }); Text("Include in forecast", Modifier.padding(top = 12.dp)) }
        Button(enabled = !busy && name.isNotBlank() && AccountClassification.valid(type, tax), onClick = { perform {
            val money = Money.parse(amount); val instant = Instant.now(); val id = UUID.randomUUID().toString()
            val account = Account(id, AccountOrigin.MANUAL, null, null,
                listOf(AccountRevision(UUID.randomUUID().toString(), 1, name, owner, type, tax, included, instant)),
                listOf(BalanceSnapshot(UUID.randomUUID().toString(), LocalDate.now(), instant, 1, money, null, BalanceSource.MANUAL, id)))
            val result = withContext(Dispatchers.IO) { runtime.repository.addManualAccount(runtime.repository.load().generation, account) }
            if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
            finish()
        } }) { Text("Save manual account") }
    }
}

@Composable
internal fun ReviewAccountsContent(
    accounts: List<LinkedAccountData>,
    manualAccounts: List<Account>,
    busy: Boolean,
    onSave: (Map<String, AccountSelection>) -> Unit,
    onDiscard: () -> Unit,
) {
    var selections by remember(accounts) { mutableStateOf(accounts.associate { it.providerAccountId to defaultSelection(it) }) }
    var selectionMessage by remember { mutableStateOf<String?>(null) }
    Text("Choose what to use in your plan. You can change these details later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("${selections.size} of ${accounts.size} accounts selected", style = MaterialTheme.typography.titleMedium)
    accounts.forEach { account ->
        val selected = selections[account.providerAccountId]
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    account.mask?.let { Text("Account ending in $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    Text(account.amount?.format() ?: "Balance unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    when (account.availability) {
                        HoldingAvailability.COMPLETE -> "Holdings available"
                        HoldingAvailability.PENDING -> "Holdings pending"
                        HoldingAvailability.UNSUPPORTED -> "Balance only"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(selected != null, onCheckedChange = { checked ->
                        selectionMessage = null
                        selections = if (checked) selections + (account.providerAccountId to defaultSelection(account))
                            else selections - account.providerAccountId
                    })
                    Text("Use account", fontWeight = FontWeight.Medium)
                }
                if (selected != null) {
                    fun update(next: AccountSelection) { selectionMessage = null; selections = selections + (account.providerAccountId to next) }
                    Choice("Account type", selected.type, reviewAccountTypes(), ::accountTypeLabel) { type ->
                        update(selected.copy(type = type, tax = defaultTaxTreatment(type, selected.tax)))
                    }
                    Choice("Tax treatment", selected.tax, taxTreatmentsFor(selected.type), ::taxTreatmentLabel) { update(selected.copy(tax = it)) }
                    Choice("Owner", selected.owner, Owner.entries, ::ownerLabel) { update(selected.copy(owner = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected.included, { update(selected.copy(included = it)) })
                        Text("Include in forecast", fontWeight = FontWeight.Medium)
                    }
                    if (manualAccounts.isNotEmpty()) {
                        var mergeMenu by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { mergeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected.replaceManualId?.let { id -> "Replace ${manualAccounts.first { it.id == id }.currentRevision.displayName}" } ?: "Add as a new account")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(mergeMenu, { mergeMenu = false }) {
                            DropdownMenuItem(text = { Text("Add as a new account") }, onClick = { update(selected.copy(replaceManualId = null)); mergeMenu = false })
                            manualAccounts.forEach { manual -> DropdownMenuItem(text = { Text("Replace ${manual.currentRevision.displayName}") }, onClick = { update(selected.copy(replaceManualId = manual.id)); mergeMenu = false }) }
                        }
                        Text("Matching preserves the existing account's history.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    selectionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        enabled = !busy,
        onClick = {
            if (selections.isEmpty()) selectionMessage = "Choose at least one account to save."
            else onSave(selections)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text("Save accounts") }
    TextButton(enabled = !busy, onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard connection") }
}

internal fun defaultSelection(account: LinkedAccountData): AccountSelection {
    val suggestion = AccountClassification.suggest(account.subtype) ?: (AccountType.BROKERAGE to TaxTreatment.TAXABLE)
    return AccountSelection(account.providerAccountId, account.name, Owner.SELF, suggestion.first, suggestion.second, true)
}

internal fun reviewAccountTypes() = AccountType.entries.filter { it !in setOf(AccountType.PROPERTY, AccountType.EPIC) }

internal fun taxTreatmentsFor(type: AccountType): List<TaxTreatment> = when (type) {
    AccountType.EMPLOYER_401K, AccountType.EMPLOYER_403B, AccountType.EMPLOYER_457, AccountType.PROFIT_SHARING -> listOf(TaxTreatment.PRE_TAX, TaxTreatment.ROTH)
    AccountType.IRA_TRADITIONAL -> listOf(TaxTreatment.PRE_TAX)
    AccountType.IRA_ROTH -> listOf(TaxTreatment.ROTH)
    AccountType.HSA -> listOf(TaxTreatment.TAX_FREE)
    AccountType.BROKERAGE, AccountType.CASH, AccountType.CD, AccountType.CRYPTO -> listOf(TaxTreatment.TAXABLE)
    AccountType.PROPERTY, AccountType.EPIC -> emptyList()
}

internal fun defaultTaxTreatment(type: AccountType, current: TaxTreatment): TaxTreatment =
    current.takeIf { it in taxTreatmentsFor(type) } ?: taxTreatmentsFor(type).first()

internal fun accountTypeLabel(type: AccountType) = when (type) {
    AccountType.EMPLOYER_401K -> "Employer 401(k)"
    AccountType.EMPLOYER_403B -> "Employer 403(b)"
    AccountType.EMPLOYER_457 -> "Employer 457"
    AccountType.PROFIT_SHARING -> "Profit sharing"
    AccountType.IRA_TRADITIONAL -> "Traditional IRA"
    AccountType.IRA_ROTH -> "Roth IRA"
    AccountType.HSA -> "HSA"
    AccountType.BROKERAGE -> "Brokerage"
    AccountType.CASH -> "Cash"
    AccountType.CD -> "CD"
    AccountType.CRYPTO -> "Crypto"
    AccountType.PROPERTY -> "Property"
    AccountType.EPIC -> "Epic stock"
}

internal fun taxTreatmentLabel(tax: TaxTreatment) = when (tax) {
    TaxTreatment.PRE_TAX -> "Pre-tax"
    TaxTreatment.ROTH -> "Roth"
    TaxTreatment.TAXABLE -> "Taxable"
    TaxTreatment.TAX_FREE -> "Tax-free"
    TaxTreatment.PROPERTY -> "Property"
    TaxTreatment.EPIC -> "Epic stock"
}

internal fun ownerLabel(owner: Owner) = when (owner) {
    Owner.SELF -> "You"
    Owner.SPOUSE -> "Spouse"
    Owner.JOINT -> "Joint"
}

@Composable
internal fun ConnectionHomeContent(
    environment: ProviderEnvironment?,
    reconnecting: Boolean,
    busy: Boolean,
    plaidOpening: Boolean,
    onAuthenticate: () -> Unit,
    onOpenPlaid: () -> Unit,
    onManual: () -> Unit,
    onResume: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text(if (reconnecting) "Reconnect your institution" else "Connect your institution", style = MaterialTheme.typography.headlineMedium)
        Text("Choose an institution in Plaid, then review the accounts you want to add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (environment == null) Icons.Default.Lock else Icons.Default.CheckCircle, contentDescription = null,
                tint = if (environment == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (environment == null) "Plaid setup needed" else "Plaid is ready", fontWeight = FontWeight.SemiBold)
                Text(environment?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Add your private API credentials first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (environment == null) {
        Button(onClick = onAuthenticate, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Set up Plaid") }
    } else {
        Button(onClick = onOpenPlaid, enabled = !busy && !plaidOpening, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (plaidOpening) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(if (plaidOpening) "Opening Plaid…" else if (reconnecting) "Reconnect with Plaid" else "Open Plaid")
        }
    }
    OutlinedButton(onClick = onManual, enabled = !busy && !plaidOpening, modifier = Modifier.fillMaxWidth()) { Text("Add account manually") }
    TextButton(onClick = { showSettings = !showSettings }, modifier = Modifier.fillMaxWidth()) {
        Text(if (showSettings) "Hide connection settings" else "Connection settings")
    }
    if (showSettings) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Credentials are encrypted on this phone and excluded from backups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAuthenticate, enabled = !busy && !plaidOpening) { Text(if (environment == null) "Set up Plaid credentials" else "Replace Plaid credentials") }
            TextButton(enabled = !busy && !plaidOpening, onClick = onResume) { Text("Resume unfinished connection") }
        }
    }
}

@Composable private fun <T> Choice(
    label: String,
    value: T,
    values: List<T>,
    display: (T) -> String = { it.toString().lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
    changed: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(display(value))
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { option ->
            DropdownMenuItem(text = { Text(display(option)) }, onClick = { changed(option); expanded = false })
        } }
    }
}
internal fun safeMessage(failure: ProviderFailure) = when (failure) {
    ProviderFailure.EXCHANGE_UNCERTAIN -> "Link exchange could not be confirmed or saved. Its one-time token will not be replayed. Review and revoke any unfinished connection in your Plaid dashboard, then start Link again."
    ProviderFailure.NEEDS_CREDENTIALS -> "Saved credentials couldn't be unlocked on this phone. Replace them privately, then try again."
    ProviderFailure.API_CREDENTIALS -> "Plaid rejected the API keys for the selected environment. Confirm the secret matches Sandbox or Production, then replace the saved credentials."
    ProviderFailure.RECONNECT_REQUIRED -> "This institution connection needs to be linked again. Your saved Plaid API credentials are unchanged."
    ProviderFailure.CONFIGURATION -> "Plaid couldn't start. In the Plaid dashboard, enable Investments and allow the Android package dev.draftingroom5."
    ProviderFailure.OFFLINE -> "You're offline. Last accepted values are unchanged. Retry when connected."
    ProviderFailure.CANCELLED -> "Connection interrupted or expired. Start again; the one-time token will not be replayed."
    ProviderFailure.RATE_LIMITED -> "The provider has limited requests. Refresh will wait until its retry window."
    ProviderFailure.UNSUPPORTED -> "Plaid Investments isn't enabled for this connection. Check your Plaid product access, or add the account manually."
    ProviderFailure.CONFLICT -> "Data or credentials changed during this operation. Refresh and review again."
    ProviderFailure.INVALID_RESPONSE -> "Provider data was incomplete or invalid. Last accepted values are unchanged."
    ProviderFailure.UNAVAILABLE -> "The provider is temporarily unavailable. Last accepted values are unchanged."
}
