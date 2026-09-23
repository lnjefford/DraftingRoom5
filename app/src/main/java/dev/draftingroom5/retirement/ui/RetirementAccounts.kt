package dev.draftingroom5.retirement.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.draftingroom5.retirement.ui.rememberPrivateState as rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.draftingroom5.AppRoute
import dev.draftingroom5.RetirementBorder
import dev.draftingroom5.RetirementHighlight
import dev.draftingroom5.RetirementPrimary
import dev.draftingroom5.RetirementSurface
import dev.draftingroom5.RetirementSurfaceRaised
import dev.draftingroom5.RetirementTextSecondary
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.Account
import dev.draftingroom5.retirement.domain.AccountOrigin
import dev.draftingroom5.retirement.domain.AccountRevision
import dev.draftingroom5.retirement.domain.AccountType
import dev.draftingroom5.retirement.domain.BalanceSnapshot
import dev.draftingroom5.retirement.domain.BalanceSource
import dev.draftingroom5.retirement.domain.HoldingAvailability
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.domain.Owner
import dev.draftingroom5.retirement.domain.ProviderStatus
import dev.draftingroom5.retirement.domain.ProviderError
import dev.draftingroom5.retirement.domain.ProviderEnvironment
import dev.draftingroom5.retirement.domain.ProviderIdentity
import dev.draftingroom5.retirement.domain.ProviderItemState
import dev.draftingroom5.retirement.domain.Property
import dev.draftingroom5.retirement.domain.Holding
import dev.draftingroom5.retirement.domain.HoldingSnapshot
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.domain.TaxTreatment
import dev.draftingroom5.retirement.provider.AccountClassification
import dev.draftingroom5.retirement.provider.LinkedAccountsActivity
import dev.draftingroom5.retirement.provider.PropertyActivity
import dev.draftingroom5.retirement.provider.RetirementAccountSyncWorker
import dev.draftingroom5.retirement.provider.RetirementPropertySyncWorker
import dev.draftingroom5.retirement.provider.RetirementProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
internal fun RetirementAccountsHost(
    route: AppRoute,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    previewState: RetirementState? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(previewState ?: RetirementState()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { reload++ }
    DisposableEffect(lifecycle, previewState) {
        if (previewState != null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reload, previewState) {
        if (previewState != null) state = previewState else runCatching {
            withContext(Dispatchers.IO) { RetirementProviders.get(context).repository.load() }
        }.onSuccess { state = it }.onFailure { message = "Account data is unavailable. Retry after unlocking the phone." }
    }
    val mutate: (((dev.draftingroom5.retirement.data.RetirementRepository) -> RetirementResult<RetirementState>), String) -> Unit = { operation, success ->
        if (previewState != null) message = "Preview action: $success" else scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { operation(RetirementProviders.get(context).repository) } }.getOrNull()
            if (result is RetirementResult.Success) {
                state = result.value
                message = success
                onBack()
            } else message = "The account changed or could not be saved. Review the current values and try again."
        }
    }
    val accountId = when (route) {
        is AppRoute.RetirementAccountDetail -> route.accountId
        is AppRoute.RetirementAccountUpdate -> route.accountId
        is AppRoute.RetirementAccountHistory -> route.accountId
        is AppRoute.RetirementAccountEdit -> route.accountId
        else -> null
    }
    val propertyId = when (route) {
        is AppRoute.RetirementPropertyDetail -> route.propertyId
        is AppRoute.RetirementPropertyHistory -> route.propertyId
        is AppRoute.RetirementPropertyEdit -> route.propertyId
        else -> null
    }
    val account = accountId?.let { id -> state.accounts.singleOrNull { it.id == id && it.archivedAt == null } }
    if (accountId != null && account == null) {
        MessagePage("Account unavailable", "This account was archived or is no longer available.", message)
        return
    }
    val property = propertyId?.let { id -> state.properties.singleOrNull { it.id == id && it.archivedAt == null } }
    if (propertyId != null && property == null) {
        MessagePage("Property unavailable", "This property was archived or is no longer available.", message)
        return
    }
    when (route) {
        AppRoute.RetirementAccounts -> AccountsList(state, message, { onNavigate(AppRoute.RetirementAddAsset) },
            { onNavigate(AppRoute.RetirementAccountDetail(it)) }, { onNavigate(AppRoute.RetirementPropertyDetail(it)) })
        AppRoute.RetirementAddAsset -> AddAssetPage(
            onConnect = { context.startActivity(Intent(context, LinkedAccountsActivity::class.java)) },
            onManual = { context.startActivity(Intent(context, LinkedAccountsActivity::class.java).putExtra("manual", true)) },
            onProperty = { context.startActivity(Intent(context, PropertyActivity::class.java)) },
            onEpic = { onNavigate(AppRoute.RetirementEpicUpload) },
        )
        is AppRoute.RetirementAccountDetail -> AccountDetailPage(
            state, account!!, message,
            onHistory = { onNavigate(AppRoute.RetirementAccountHistory(account.id)) },
            onEdit = { onNavigate(AppRoute.RetirementAccountEdit(account.id)) },
            onUpdate = { onNavigate(AppRoute.RetirementAccountUpdate(account.id)) },
            onReconnect = { context.startActivity(Intent(context, LinkedAccountsActivity::class.java).putExtra("item", account.providerIdentity!!.itemId)) },
            onRefresh = {
                if (previewState != null) message = "Preview action: refresh requested" else scope.launch {
                    val failure = withContext(Dispatchers.IO) { RetirementProviders.get(context).plaid.refresh(account.providerIdentity!!.itemId) }
                    message = if (failure == null) "Linked values refreshed." else "Refresh needs attention. Last accepted values remain available."
                    refresh()
                }
            },
        )
        is AppRoute.RetirementAccountUpdate -> {
            Box(Modifier.fillMaxSize()) {
                MessagePage(account!!.currentRevision.displayName, "Current value ${account.currentBalance?.amount?.format() ?: "unavailable"}", null)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)).clickable(onClick = onBack)
                    .semantics { role = Role.Button; contentDescription = "Dismiss update balance" })
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(containerColor = RetirementSurfaceRaised),
                ) {
                    UpdateBalanceSheet(
                        account, message, if (previewState == null) LocalDate.now() else LocalDate.parse("2026-09-19"),
                    ) { amount, date ->
                        val acceptedAt = Instant.now()
                        val snapshot = BalanceSnapshot(UUID.randomUUID().toString(), date, acceptedAt,
                            (account.balances.maxOfOrNull { it.sequence } ?: 0) + 1, amount, null, BalanceSource.MANUAL, UUID.randomUUID().toString())
                        mutate({ repository -> repository.appendManualBalance(repository.load().generation, account.id, snapshot) }, "Balance snapshot added.")
                    }
                }
            }
        }
        is AppRoute.RetirementAccountHistory -> BalanceHistoryPage(account!!)
        is AppRoute.RetirementAccountEdit -> EditAccountPage(account!!, message,
            onSave = { name, owner, type, tax, included ->
                val current = account.currentRevision
                val revision = AccountRevision(UUID.randomUUID().toString(), current.revision + 1, name, owner, type, tax,
                    included, Instant.now(), current.id)
                mutate({ repository -> repository.reviseAccount(repository.load().generation, account.id, revision) }, "Account details updated.")
            },
            onArchive = {
                mutate({ repository -> repository.archiveAccount(repository.load().generation, account.id, Instant.now()) }, "Account archived. Its history remains stored.")
            },
        )
        is AppRoute.RetirementPropertyDetail -> PropertyDetailPage(
            state, property!!, message, if (previewState == null) Instant.now() else Instant.parse("2026-09-20T18:00:00Z"),
            onHistory = { onNavigate(AppRoute.RetirementPropertyHistory(property.id)) },
            onEdit = { onNavigate(AppRoute.RetirementPropertyEdit(property.id)) },
            onRefresh = {
                if (previewState != null) message = "Preview action: property refresh requested" else scope.launch {
                    val failure = withContext(Dispatchers.IO) { RetirementProviders.get(context).rentCast.refresh(property.id) }
                    message = if (failure == null) "Property estimate refreshed." else "Refresh failed. The last accepted value remains available."
                    refresh()
                }
            },
            onManualValue = { amount, date ->
                val valuation = dev.draftingroom5.retirement.domain.PropertyValuationSnapshot(UUID.randomUUID().toString(), amount, null, null, null,
                    dev.draftingroom5.retirement.domain.ValuationSource.MANUAL, date, Instant.now(),
                    (property.valuations.maxOfOrNull { it.sequence } ?: 0) + 1, UUID.randomUUID().toString())
                mutate({ repository -> repository.appendManualValuation(repository.load().generation, property.id, valuation) }, "Property value added.")
            },
        )
        is AppRoute.RetirementPropertyHistory -> PropertyHistoryPage(property!!)
        is AppRoute.RetirementPropertyEdit -> EditPropertyPage(property!!, message,
            onReplaceCredential = { context.startActivity(Intent(context, PropertyActivity::class.java).putExtra(PropertyActivity.PROPERTY_ID, property.id)) },
            onSave = { revision ->
                mutate({ repository ->
                    repository.reviseProperty(state.generation, property.id, revision).also { result ->
                        if (result is RetirementResult.Success) {
                            if (revision.automaticValueEnabled) RetirementPropertySyncWorker.schedule(context, property.id)
                            else RetirementPropertySyncWorker.cancel(context, property.id)
                        }
                    }
                }, "Property details updated.")
            })
        else -> MessagePage("Accounts", "Open Accounts from the tax-treatment table.", message)
    }
}

@Composable
private fun Page(title: String, subtitle: String? = null, message: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, color = RetirementTextSecondary) }
        message?.let { Text(it, color = RetirementHighlight) }
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun MessagePage(title: String, body: String, message: String?) = Page(title, body, message) {}

@Composable
private fun AccountsList(state: RetirementState, message: String?, onAdd: () -> Unit, onOpen: (String) -> Unit, onOpenProperty: (String) -> Unit) {
    val grouped = state.activeAccountsByGroup()
    Page("Accounts", "Linked and manual sources stay distinct. Account values are shown without a combined total.", message) {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add to plan", Modifier.padding(start = 8.dp))
        }
        AccountGroup.entries.filter { it != AccountGroup.HEALTH || grouped[it].orEmpty().isNotEmpty() }.forEach { group ->
            Text(group.label, style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            val accounts = grouped[group].orEmpty()
            if (accounts.isEmpty()) {
                val placeholder = if (group == AccountGroup.PROPERTY) "No properties yet" else "No accounts yet"
                Text(placeholder, color = RetirementTextSecondary)
            } else accounts.forEach { account ->
                val property = state.properties.singleOrNull { it.accountId == account.id && it.archivedAt == null }
                if (property != null) PropertyRow(state, property) { onOpenProperty(property.id) }
                else AccountRow(state, account) { onOpen(account.id) }
            }
        }
    }
}

@Composable
private fun AccountRow(state: RetirementState, account: Account, onOpen: () -> Unit) {
    val attention = state.needsAttention(account)
    val description = buildString {
        append(account.currentRevision.displayName); append(", "); append(account.currentBalance?.amount?.format() ?: "balance unavailable")
        append(", "); append(sourceLabel(account)); if (attention) append(", needs attention")
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).semantics(mergeDescendants = true) {
            role = Role.Button; contentDescription = description
        },
        colors = CardDefaults.cardColors(containerColor = RetirementSurface),
        border = BorderStroke(1.dp, if (attention) RetirementHighlight else RetirementBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.currentRevision.displayName, fontWeight = FontWeight.SemiBold)
                Text(account.currentBalance?.amount?.format() ?: "Balance unavailable")
                Text(sourceLabel(account), color = RetirementPrimary)
                if (attention) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RetirementHighlight)
                    Text(" Needs attention", color = RetirementHighlight)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun PropertyRow(state: RetirementState, property: Property, onOpen: () -> Unit) {
    val revision = property.currentRevision
    val value = property.currentValuation
    val item = state.providerItems.singleOrNull { it.id == property.id }
    val attention = item?.error != null
    val description = "${revision.address}, ${value?.estimate?.format() ?: "value unavailable"}, equity ${property.equity()?.format() ?: "unavailable"}, ${if (revision.automaticValueEnabled) "automatic estimate" else "manual value"}${if (attention) ", refresh needs attention" else ""}"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).semantics(mergeDescendants = true) { role = Role.Button; contentDescription = description },
        colors = CardDefaults.cardColors(containerColor = RetirementSurface),
        border = BorderStroke(1.dp, if (attention) RetirementHighlight else RetirementBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(revision.address, fontWeight = FontWeight.SemiBold)
                Text(value?.estimate?.format() ?: "Value unavailable")
                Text(if (revision.automaticValueEnabled) "Automatic estimate" else "Manual value", color = RetirementPrimary)
                Text("Equity ${property.equity()?.format() ?: "unavailable"}", color = RetirementTextSecondary)
                if (attention) Text("Last value kept · refresh needs attention", color = RetirementHighlight)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun AddAssetPage(onConnect: () -> Unit, onManual: () -> Unit, onProperty: () -> Unit, onEpic: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RetirementFlowHero(
            "Build your plan",
            "Add what you own",
            "Connect an institution, import Epic stock, track property, or add an account yourself.",
            Icons.Default.Savings,
        )
        RetirementFlowActionCard("Fastest", "Connect an institution", "Bring in balances and holdings securely through Plaid.", Icons.Default.AccountBalance, featured = true, onClick = onConnect)
        RetirementFlowActionCard("Shareworks", "Import Epic workbook", "Choose your .xlsm file and review its saved values.", Icons.Default.Savings, onClick = onEpic)
        RetirementFlowActionCard("Home", "Find or enter a property", "Use a RentCast estimate or keep the value fully manual.", Icons.Default.HomeWork, onClick = onProperty)
        RetirementFlowActionCard("Offline", "Add an account manually", "Enter a balance now and add dated updates later.", Icons.Default.Edit, onClick = onManual)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountDetailPage(
    state: RetirementState, account: Account, message: String?, onHistory: () -> Unit, onEdit: () -> Unit,
    onUpdate: () -> Unit, onReconnect: () -> Unit, onRefresh: () -> Unit,
) {
    val revision = account.currentRevision
    val item = state.providerItem(account)
    val attention = state.needsAttention(account)
    val freshness = when {
        account.origin == AccountOrigin.MANUAL -> account.currentBalance?.let { "Updated ${it.asOfDate}" } ?: "No balance snapshot"
        item?.lastAcceptedAt != null -> "Last linked update ${formatInstant(item.lastAcceptedAt)}"
        else -> "No linked update accepted"
    }
    val holdings = account.holdingSnapshots.lastOrNull()
    Page(revision.displayName, sourceLabel(account), message) {
        if (attention) Card(colors = CardDefaults.cardColors(containerColor = RetirementSurfaceRaised),
            border = BorderStroke(1.dp, RetirementHighlight), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This account needs attention", color = RetirementHighlight, fontWeight = FontWeight.Bold)
                Text("The last accepted value remains available. Repair only this connection.")
                Button(onClick = onReconnect) { Text("Reconnect account") }
            }
        }
        Detail("Current value", account.currentBalance?.amount?.format() ?: "Unavailable")
        Detail("Classification", "${enumLabel(revision.type)} · ${enumLabel(revision.taxTreatment)}")
        Detail("Owner", enumLabel(revision.owner))
        Detail("Forecast inclusion", if (revision.includedInForecast) "Included" else "Not included")
        Detail("Freshness", freshness)
        HorizontalDivider(color = RetirementBorder)
        Text("Holdings", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        when (holdings?.availability) {
            HoldingAvailability.COMPLETE -> if (holdings.holdings.isEmpty()) Text("No holdings reported", color = RetirementTextSecondary) else holdings.holdings.forEach { holding ->
                Detail(holding.assetClass, "${holding.quantity} units${holding.price?.let { " · ${it.format()}" }.orEmpty()}")
            }
            HoldingAvailability.UNSUPPORTED -> Text("Holdings are not supported for this linked account.", color = RetirementTextSecondary)
            HoldingAvailability.PENDING -> Text("Holdings are awaiting the next accepted refresh.", color = RetirementTextSecondary)
            null -> Text(if (account.origin == AccountOrigin.MANUAL) "No holdings entered for this manual account." else "Holdings unavailable.", color = RetirementTextSecondary)
        }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Balance history") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit account") }
        if (account.origin == AccountOrigin.MANUAL) Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text("Update balance") }
        if (account.origin == AccountOrigin.PLAID && !attention && item?.status == ProviderStatus.READY) {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh linked value") }
            Text("Linked values cannot be overwritten manually.", color = RetirementTextSecondary)
        }
    }
}

@Composable
private fun PropertyDetailPage(
    state: RetirementState,
    property: Property,
    message: String?,
    currentTime: Instant,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onManualValue: (Money, LocalDate) -> Unit,
) {
    val revision = property.currentRevision
    val valuation = property.currentValuation
    val item = state.providerItems.singleOrNull { it.id == property.id }
    var addingValue by rememberSaveable(property.id) { mutableStateOf(false) }
    var amount by rememberSaveable(property.id) { mutableStateOf("") }
    var date by rememberSaveable(property.id) { mutableStateOf(LocalDate.now().toString()) }
    var localError by rememberSaveable(property.id) { mutableStateOf<String?>(null) }
    Page(revision.address, if (revision.automaticValueEnabled) "Automatic RentCast estimate" else "Manual property", message) {
        if (item?.error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = RetirementSurfaceRaised), border = BorderStroke(1.dp, RetirementHighlight), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Refresh needs attention", color = RetirementHighlight, fontWeight = FontWeight.Bold)
                    Text("The last accepted estimate remains in use. ${propertyAge(valuation?.acceptedAt, currentTime)}")
                    Text("${enumLabel(item.error)} · attempted ${item.attemptedAt?.let(::formatInstant) ?: "recently"}", color = RetirementTextSecondary)
                }
            }
        }
        Detail("Current value", valuation?.estimate?.format() ?: "Unavailable")
        if (valuation?.rangeLow != null) Detail("Estimate range", "Low ${valuation.rangeLow.format()}\nHigh ${valuation.rangeHigh!!.format()}")
        Detail("Equity", property.equity()?.format() ?: "Unavailable")
        Detail("Mortgage balance", revision.mortgage.outstanding.format())
        Detail("Ownership", "${revision.ownershipBps / 100.0}% · ${enumLabel(revision.owner)}")
        Detail("Forecast inclusion", if (revision.includedInForecast) "Included" else "Not included")
        Detail("Value source", if (revision.automaticValueEnabled) "RentCast · approximately weekly" else "Manual · no automatic updates")
        Detail("Freshness", valuation?.let { "${it.providerAsOf ?: it.acceptedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()} · ${propertyAge(it.acceptedAt, currentTime)}" } ?: "No accepted value")
        valuation?.comparableCount?.let { Detail("Comparable sales", "$it used by the latest estimate") }
        revision.facts?.let { Detail("Property details", it) }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Value history") }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit property") }
        if (revision.automaticValueEnabled) {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh estimate") }
            Text("Provider failures never erase the last accepted estimate.", color = RetirementTextSecondary)
        } else if (!addingValue) {
            Button(onClick = { addingValue = true }, modifier = Modifier.fillMaxWidth()) { Text("Add manual value") }
        } else {
            OutlinedTextField(amount, { amount = it.take(40) }, label = { Text("Property value (USD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it.take(10) }, label = { Text("As-of date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            localError?.let { Text(it, color = RetirementHighlight) }
            Button(onClick = {
                val parsed = runCatching { Money.parse(amount) to LocalDate.parse(date) }.getOrNull()
                if (parsed == null || parsed.first.cents < 0 || parsed.second.isAfter(LocalDate.now())) localError = "Enter a nonnegative value and a date that is not in the future."
                else onManualValue(parsed.first, parsed.second)
            }, modifier = Modifier.fillMaxWidth()) { Text("Append value") }
        }
    }
}

@Composable
private fun PropertyHistoryPage(property: Property) = Page("Value history", "Accepted estimates and manual valuations are append-only.") {
    property.valuations.sortedWith(compareByDescending<dev.draftingroom5.retirement.domain.PropertyValuationSnapshot> { it.providerAsOf ?: it.acceptedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate() }
        .thenByDescending { it.sequence }).forEach { value ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RetirementSurface), border = BorderStroke(1.dp, RetirementBorder)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value.estimate.format(), fontWeight = FontWeight.SemiBold)
                Text("${value.providerAsOf ?: value.acceptedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()} · ${enumLabel(value.source)}", color = RetirementTextSecondary)
                if (value.rangeLow != null) Text("Low ${value.rangeLow.format()}\nHigh ${value.rangeHigh!!.format()}")
                value.comparableCount?.let { Text("$it comparable sales", color = RetirementTextSecondary) }
                value.supersedesId?.let { Text("Correction snapshot", color = RetirementHighlight) }
            }
        }
    }
}

@Composable
private fun EditPropertyPage(property: Property, message: String?, onReplaceCredential: () -> Unit,
    onSave: (dev.draftingroom5.retirement.domain.PropertyRevision) -> Unit) {
    val current = property.currentRevision
    Page("Edit property", "Update ownership, mortgage terms and refresh behavior. Accepted valuation history is retained.", message) {
        PropertyEditFields(current, onSave)
        if (current.providerPropertyId != null) {
            OutlinedButton(onClick = onReplaceCredential, modifier = Modifier.fillMaxWidth()) { Text("Manage or replace RentCast key") }
        }
    }
}

private fun propertyAge(acceptedAt: Instant?, currentTime: Instant): String {
    if (acceptedAt == null) return "No accepted value"
    val days = java.time.Duration.between(acceptedAt, currentTime).toDays().coerceAtLeast(0)
    return when (days) { 0L -> "accepted today"; 1L -> "1 day old"; else -> "$days days old" }
}

@Composable private fun Detail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label, $value" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = RetirementTextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpdateBalanceSheet(account: Account, message: String?, initialDate: LocalDate, onSave: (Money, LocalDate) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Update balance", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text("Add a dated snapshot. Earlier values remain in balance history.", color = RetirementTextSecondary)
        (message ?: localError)?.let { Text(it, color = RetirementHighlight) }
        if (account.origin != AccountOrigin.MANUAL) {
            Text("Linked provider values are read-only and cannot be updated manually.", color = RetirementHighlight)
            return@Column
        }
        OutlinedTextField(amount, { amount = it.take(40) }, label = { Text("Balance (USD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(date, { date = it.take(10) }, label = { Text("As-of date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            val parsed = runCatching { Money.parse(amount) to LocalDate.parse(date) }.getOrNull()
            if (parsed == null || parsed.second.isAfter(initialDate)) localError = "Enter a valid amount and a date that is not in the future."
            else onSave(parsed.first, parsed.second)
        }, modifier = Modifier.fillMaxWidth()) { Text("Add snapshot") }
    }
}

@Composable
private fun BalanceHistoryPage(account: Account) = Page("Balance history", "Snapshots are append-only; newer entries never erase earlier accepted values.") {
    account.balances.sortedWith(compareByDescending<BalanceSnapshot> { it.asOfDate }.thenByDescending { it.sequence }).forEach { snapshot ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RetirementSurface), border = BorderStroke(1.dp, RetirementBorder)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(snapshot.amount.format(), fontWeight = FontWeight.SemiBold)
                Text("${snapshot.asOfDate} · ${enumLabel(snapshot.source)}", color = RetirementTextSecondary)
                snapshot.supersedesId?.let { Text("Correction snapshot", color = RetirementHighlight) }
            }
        }
    }
    if (account.balances.isEmpty()) Text("No accepted balance history.", color = RetirementTextSecondary)
}

@Composable
private fun EditAccountPage(
    account: Account, message: String?, onSave: (String, Owner, AccountType, TaxTreatment, Boolean) -> Unit, onArchive: () -> Unit,
) {
    val current = account.currentRevision
    var name by rememberSaveable(account.id, current.id) { mutableStateOf(current.displayName) }
    var owner by rememberSaveable(account.id, current.id) { mutableStateOf(current.owner) }
    var type by rememberSaveable(account.id, current.id) { mutableStateOf(current.type) }
    var tax by rememberSaveable(account.id, current.id) { mutableStateOf(current.taxTreatment) }
    var included by rememberSaveable(account.id, current.id) { mutableStateOf(current.includedInForecast) }
    var confirmArchive by rememberSaveable(account.id, current.id) { mutableStateOf(false) }
    val valid = name.isNotBlank() && AccountClassification.valid(type, tax)
    Page("Edit account", if (account.origin == AccountOrigin.PLAID) "Provider identity and values remain read-only." else "Manual balances are edited by adding snapshots.", message) {
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        MenuChoice("Account type", type, AccountType.entries.filter { it !in setOf(AccountType.PROPERTY, AccountType.EPIC, AccountType.HSA) }) { type = it }
        MenuChoice("Tax treatment", tax, TaxTreatment.entries.filter { it !in setOf(TaxTreatment.PROPERTY, TaxTreatment.EPIC, TaxTreatment.TAX_FREE) }) { tax = it }
        MenuChoice("Owner", owner, Owner.entries.toList()) { owner = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Include in forecast")
            Switch(included, { included = it }, modifier = Modifier.semantics { contentDescription = "Include this account in forecast" })
        }
        if (!valid) Text("Choose a tax treatment compatible with the account type.", color = RetirementHighlight)
        Button(enabled = valid, onClick = { onSave(name.trim(), owner, type, tax, included) }, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
        HorizontalDivider(color = RetirementBorder)
        if (!confirmArchive) TextButton(onClick = { confirmArchive = true }) { Text("Archive account") }
        else {
            Text("Archive this account? It leaves totals but its history remains stored.", color = RetirementHighlight)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmArchive = false }) { Text("Cancel") }
                Button(onClick = onArchive) { Text("Archive") }
            }
        }
    }
}

@Composable private fun <T : Enum<T>> MenuChoice(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${enumLabel(value)}") }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { option -> DropdownMenuItem(text = { Text(enumLabel(option)) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

private fun formatInstant(value: Instant): String = DateTimeFormatter.ofPattern("MMM d, yyyy")
    .withZone(ZoneId.systemDefault()).format(value)

internal fun retirementAccountsPreviewState(): RetirementState {
    val now = Instant.parse("2026-09-19T18:00:00Z")
    val today = LocalDate.parse("2026-09-19")
    fun revision(id: String, name: String, owner: Owner, type: AccountType, tax: TaxTreatment, included: Boolean) =
        AccountRevision("$id-r1", 1, name, owner, type, tax, included, now)
    fun balance(id: String, cents: Long, source: BalanceSource, daysAgo: Long = 0) =
        BalanceSnapshot("$id-b1", today.minusDays(daysAgo), now.minusSeconds(daysAgo * 86_400), 1, Money(cents), null, source, "$id-batch")
    val manual = Account("preview-manual", AccountOrigin.MANUAL, null, null,
        listOf(revision("preview-manual", "Roth IRA", Owner.SELF, AccountType.IRA_ROTH, TaxTreatment.ROTH, true)),
        listOf(balance("preview-manual", 184_250_00, BalanceSource.MANUAL), balance("preview-manual-2", 176_800_00, BalanceSource.MANUAL, 31)))
    val itemId = "00000000-0000-0000-0000-000000000071"
    val linked = Account("preview-linked", AccountOrigin.PLAID, null,
        ProviderIdentity("preview-profile", ProviderEnvironment.PRODUCTION, itemId, "preview-provider-account"),
        listOf(revision("preview-linked", "Employer 401(k)", Owner.SPOUSE, AccountType.EMPLOYER_401K, TaxTreatment.PRE_TAX, true)),
        listOf(balance("preview-linked", 312_640_00, BalanceSource.PLAID, 4)),
        listOf(HoldingSnapshot("preview-holdings", now.minusSeconds(345_600), HoldingAvailability.COMPLETE,
            listOf(Holding("fund-one", "128.42", Money(24_350), Money(19_700), "US stock fund"),
                Holding("fund-two", "84.10", Money(18_100), null, "Bond fund")))))
    val cash = Account("preview-cash", AccountOrigin.MANUAL, null, null,
        listOf(revision("preview-cash", "Emergency savings", Owner.JOINT, AccountType.CASH, TaxTreatment.TAXABLE, false)),
        listOf(balance("preview-cash", 32_000_00, BalanceSource.MANUAL)))
    val propertyAccount = Account("preview-property-account", AccountOrigin.PROPERTY, null, null,
        listOf(revision("preview-property-account", "500 Fixture Way", Owner.JOINT, AccountType.PROPERTY, TaxTreatment.PROPERTY, true)), emptyList())
    val mortgage = dev.draftingroom5.retirement.domain.MortgageTerms(Money(198_430_55), today, Money(260_000_00), 625, Money(1_745_20), 214, 360)
    val property = Property("preview-property", propertyAccount.id, null,
        listOf(dev.draftingroom5.retirement.domain.PropertyRevision("preview-property-r1", 1, "500 Fixture Way, Madison, WI 53703",
            "Single Family · 3 beds · 2 baths · 1,840 sq ft", Owner.JOINT, 10_000, mortgage, true, true, now,
            providerPropertyId = "synthetic-provider-property", credentialProfileId = "synthetic-profile")),
        listOf(
            dev.draftingroom5.retirement.domain.PropertyValuationSnapshot("preview-value-1", Money(468_000_00), Money(445_000_00), Money(492_000_00), 14,
                dev.draftingroom5.retirement.domain.ValuationSource.RENTCAST, today.minusDays(7), now.minusSeconds(7 * 86_400), 1, "preview-property-batch-1"),
            dev.draftingroom5.retirement.domain.PropertyValuationSnapshot("preview-value-2", Money(485_123_45), Money(460_000_00), Money(510_000_00), 18,
                dev.draftingroom5.retirement.domain.ValuationSource.RENTCAST, today, now, 2, "preview-property-batch-2"),
        ))
    return RetirementState(accounts = listOf(linked, manual, cash, propertyAccount), properties = listOf(property), providerItems = listOf(
        ProviderItemState(itemId, linked.id, ProviderStatus.ATTENTION, now, now.minusSeconds(345_600), null,
            ProviderError.AUTHENTICATION_REQUIRED, 2, null),
        ProviderItemState(property.id, propertyAccount.id, ProviderStatus.READY, now, now, null, null, 1, null),
    ))
}
