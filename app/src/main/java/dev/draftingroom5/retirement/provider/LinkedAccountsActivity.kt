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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.plaid.link.FastOpenPlaidLink
import com.plaid.link.Plaid
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
    private var setup by mutableStateOf(false)
    private var manual by mutableStateOf(false)
    private var busy by mutableStateOf(false)
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

    @Composable private fun Content() {
        BackHandler(enabled = !busy) {
            if (setup) { setup = false; clientField?.text?.clear(); secretField?.text?.clear() }
            else finish()
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (manual) "Add account manually" else if (reconnectItem != null) "Reconnect account" else "Connect institution", style = MaterialTheme.typography.headlineMedium)
                message?.let { Text(it) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    manual -> ManualForm()
                    setup -> CredentialsForm()
                    review != null -> ReviewForm(review!!)
                    else -> {
                        Text("Read-only balances and investment holdings through Plaid. You choose which accounts to track and confirm their classification. Some institutions and cash accounts are unsupported.")
                        Text("Your own Plaid subscription needs Investments access and dev.draftingroom5 registered as an allowed Android package. Credentials stay encrypted on this phone and are sent directly to Plaid. They are not included in backups.")
                        Text(if (profile == null) "Provider credentials: not configured" else "Provider credentials: configured (${profile!!.environment.name.lowercase()})")
                        Button(onClick = ::authenticate, enabled = !busy) { Text(if (profile == null) "Set up credentials privately" else "Replace credentials") }
                        Button(enabled = profile != null && !busy, onClick = {
                            perform {
                                val active = withContext(Dispatchers.IO) { runtime.plaid.beginLink(profile!!.id, reconnectItem) }
                                attempt = active
                                val configuration = LinkTokenConfiguration.Builder().token(active.takeToken()).logLevel(LinkLogLevel.ASSERT).build()
                                link.launch(Plaid.create(application, configuration))
                            }
                        }) { Text(if (reconnectItem == null) "Continue to Plaid" else "Reconnect with Plaid") }
                        Button(enabled = !busy, onClick = { perform {
                            val pending = withContext(Dispatchers.IO) { runtime.plaid.pendingItems() }
                            if (pending.isEmpty()) message = "No pending review. Start a new connection."
                            else loadReview(pending.first())
                        } }) { Text("Resume pending review") }
                        TextButton(onClick = { manual = true }, enabled = !busy) { Text("Add an account manually") }
                    }
                }
                TextButton(onClick = { finish() }, enabled = !busy) { Text("Close") }
            }
        }
    }

    @Composable private fun CredentialsForm() {
        var environment by remember { mutableStateOf(profile?.environment ?: ProviderEnvironment.SANDBOX) }
        Text("Enter your credentials here only. They cannot be viewed later. Replacement keeps the existing profile and invalidates in-flight requests. You may need to reconnect after provider-side rotation.")
        if (profile == null) Choice("Environment", environment, ProviderEnvironment.entries) { environment = it }
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
                    profiles = withContext(Dispatchers.IO) { runtime.plaid.profiles() }
                    clientField?.text?.clear(); secretField?.text?.clear()
                    setup = false
                    message = "Plaid credentials saved securely on this phone."
                }
                finally { client.fill('\u0000'); secret.fill('\u0000') }
            }
        }) { Text("Save encrypted credentials") }
        if (profile != null) {
            Text("Removing credentials stops this profile's connections on this phone. Accepted account history remains readable. Remote revocation may require your provider dashboard.")
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
                    profile = null; reconnectItem = null; setup = false
                    message = if (revoked) "Credentials and remote connections removed. Accepted history remains." else "Credentials removed locally. Revoke remote access from your Plaid or institution dashboard."
                }
            }) { Text("Remove credentials and connections") }
        }
        Text("If the Keystore key is unavailable, reset all provider credentials on this phone, then enter them again and link again. This also removes any property-provider key. Accepted financial history stays readable. Remote access must be revoked in the provider dashboard.")
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
        var selections by remember(batch.operationId) { mutableStateOf<Map<String, AccountSelection>>(emptyMap()) }
        var confirmedTypes by remember(batch.operationId) { mutableStateOf<Set<String>>(emptySet()) }
        Text("Select accounts, then confirm type, tax treatment, ownership and forecast inclusion. Similar names or masks do not prove two accounts are the same.")
        batch.accounts.forEach { account ->
            val selected = selections[account.providerAccountId]
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.name + (account.mask?.let { " ••$it" } ?: ""))
                Text(account.amount?.format() ?: "Balance unavailable")
                Text("Holdings: ${account.availability.name.lowercase()}")
                Row {
                    Checkbox(selected != null, onCheckedChange = { checked ->
                        confirmedTypes = confirmedTypes - account.providerAccountId
                        selections = if (!checked) selections - account.providerAccountId else selections + (account.providerAccountId to
                            AccountSelection(account.providerAccountId, account.name, Owner.SELF, AccountType.BROKERAGE, TaxTreatment.TAXABLE, false))
                    })
                    Text("Track this account", Modifier.padding(top = 12.dp))
                }
                if (selected != null) {
                    val suggestion = AccountClassification.suggest(account.subtype)
                    Text(suggestion?.let { "Suggested: ${it.first.name.lowercase().replace('_', ' ')}. Confirm below." } ?: "Unfamiliar account subtype: choose classification explicitly.")
                    fun update(next: AccountSelection) { selections = selections + (account.providerAccountId to next) }
                    if (account.providerAccountId !in confirmedTypes) Text("Choose an account type to confirm it.")
                    Choice("Type", selected.type, AccountType.entries.filter { it !in setOf(AccountType.PROPERTY, AccountType.EPIC) }) {
                        update(selected.copy(type = it)); confirmedTypes = confirmedTypes + account.providerAccountId
                    }
                    Choice("Tax treatment", selected.tax, TaxTreatment.entries.filter { it !in setOf(TaxTreatment.PROPERTY, TaxTreatment.EPIC) }) { update(selected.copy(tax = it)) }
                    Choice("Owner", selected.owner, Owner.entries) { update(selected.copy(owner = it)) }
                    Row { Checkbox(selected.included, { update(selected.copy(included = it)) }); Text("Include in forecast", Modifier.padding(top = 12.dp)) }
                    if (manualAccounts.isNotEmpty()) {
                        var mergeMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { mergeMenu = true }) { Text(selected.replaceManualId?.let { "Replace existing tracking: ${manualAccounts.first { a -> a.id == it }.currentRevision.displayName}" } ?: "Add as new (or match an existing account)") }
                        DropdownMenu(mergeMenu, { mergeMenu = false }) {
                            DropdownMenuItem(text = { Text("Add as new") }, onClick = { update(selected.copy(replaceManualId = null)); mergeMenu = false })
                            manualAccounts.forEach { manual -> DropdownMenuItem(text = { Text("Replace ${manual.currentRevision.displayName}") }, onClick = { update(selected.copy(replaceManualId = manual.id)); mergeMenu = false }) }
                        }
                        Text("Matching converts only the chosen manual or disconnected account to this connection and preserves its history.")
                    }
                }
            } }
        }
        var confirmed by remember(batch.operationId, selections) { mutableStateOf(false) }
        Row { Checkbox(confirmed, { confirmed = it }); Text("I confirm the selected classifications and matches", Modifier.padding(top = 12.dp)) }
        Button(enabled = !busy && confirmed && selections.isNotEmpty() && confirmedTypes.containsAll(selections.keys) && selections.values.all { AccountClassification.valid(it.type, it.tax) }, onClick = {
            perform {
                val result = withContext(Dispatchers.IO) { runtime.plaid.accept(batch, reviewGeneration, selections.values.toList()) }
                if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
                RetirementAccountSyncWorker.schedule(this@LinkedAccountsActivity, batch.item.id)
                review = null; message = "Accounts saved. Scheduled refresh is approximately daily and subject to Android constraints."
            }
        }) { Text("Save reviewed accounts") }
        TextButton(enabled = !busy, onClick = { perform {
            val revoked = withContext(Dispatchers.IO) { runtime.plaid.disconnect(batch.item.id) }
            RetirementAccountSyncWorker.cancel(this@LinkedAccountsActivity, batch.item.id)
            review = null
            message = if (revoked) "Connection removed." else "Removed from this phone. Remote revocation could not be confirmed; revoke access in your Plaid or institution dashboard."
        } }) { Text("Discard pending connection") }
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

@Composable private fun <T> Choice(label: String, value: T, values: List<T>, changed: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${value.toString().lowercase().replace('_', ' ')}") }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { option ->
            DropdownMenuItem(text = { Text(option.toString().lowercase().replace('_', ' ')) }, onClick = { changed(option); expanded = false })
        } }
    }
}
internal fun safeMessage(failure: ProviderFailure) = when (failure) {
    ProviderFailure.EXCHANGE_UNCERTAIN -> "Link exchange could not be confirmed or saved. Its one-time token will not be replayed. Review and revoke any unfinished connection in your Plaid dashboard, then start Link again."
    ProviderFailure.NEEDS_CREDENTIALS -> "Credentials need attention. Set up or replace them privately, then reconnect. Accepted values remain available."
    ProviderFailure.OFFLINE -> "You're offline. Last accepted values are unchanged. Retry when connected."
    ProviderFailure.CANCELLED -> "Connection interrupted or expired. Start again; the one-time token will not be replayed."
    ProviderFailure.RATE_LIMITED -> "The provider has limited requests. Refresh will wait until its retry window."
    ProviderFailure.UNSUPPORTED -> "This institution or product is unsupported. Add an account manually."
    ProviderFailure.CONFLICT -> "Data or credentials changed during this operation. Refresh and review again."
    ProviderFailure.INVALID_RESPONSE -> "Provider data was incomplete or invalid. Last accepted values are unchanged."
    ProviderFailure.UNAVAILABLE -> "The provider is temporarily unavailable. Last accepted values are unchanged."
}
