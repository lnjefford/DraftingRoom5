package dev.draftingroom5.retirement.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.draftingroom5.retirement.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun propertyEditRevision(base: PropertyRevision, address: String, mortgage: String, payment: String,
    rate: String, months: String, ownership: String, owner: Owner, included: Boolean, automatic: Boolean,
    at: Instant = Instant.now(), today: LocalDate = LocalDate.now()): PropertyRevision {
    fun bps(text: String) = BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
    val debt = Money.parse(mortgage); val monthly = Money.parse(payment)
    val rateBps = bps(rate); val share = bps(ownership); val remaining = months.toInt()
    require(address.trim().length in 1..240 && debt.cents >= 0 && monthly.cents >= 0)
    require(rateBps in 0..2500 && share in 0..10000 && remaining in 0..600)
    require(!automatic || (base.providerPropertyId != null && base.credentialProfileId != null))
    return base.copy(id = UUID.randomUUID().toString(), revision = Math.addExact(base.revision, 1), previousRevisionId = base.id,
        address = address.trim(), owner = owner, ownershipBps = share, includedInForecast = included,
        automaticValueEnabled = automatic, effectiveAt = at,
        mortgage = base.mortgage.copy(outstanding = debt, payment = monthly, annualRateBps = rateBps, remainingMonths = remaining, asOfDate = today))
}

@Composable
internal fun PropertyEditFields(current: PropertyRevision, onSave: (PropertyRevision) -> Unit) {
    var address by rememberPrivateState(current.id) { mutableStateOf(current.address) }
    var mortgage by rememberPrivateState(current.id) { mutableStateOf(current.mortgage.outstanding.format()) }
    var payment by rememberPrivateState(current.id) { mutableStateOf(current.mortgage.payment.format()) }
    var rate by rememberPrivateState(current.id) { mutableStateOf(formatBps(current.mortgage.annualRateBps).removeSuffix("%")) }
    var months by rememberPrivateState(current.id) { mutableStateOf(current.mortgage.remainingMonths.toString()) }
    var ownership by rememberPrivateState(current.id) { mutableStateOf(formatBps(current.ownershipBps).removeSuffix("%")) }
    var owner by rememberPrivateState(current.id) { mutableStateOf(current.owner) }
    var included by rememberPrivateState(current.id) { mutableStateOf(current.includedInForecast) }
    var automatic by rememberPrivateState(current.id) { mutableStateOf(current.automaticValueEnabled) }
    var error by remember { mutableStateOf<String?>(null) }
    @Composable fun field(label: String, value: String, changed: (String) -> Unit) {
        OutlinedTextField(value, { changed(it.take(240)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
    }
    field("Property address", address) { address = it }
    field("Mortgage balance (USD)", mortgage) { mortgage = it }
    field("Monthly payment (USD)", payment) { payment = it }
    field("Annual rate (%)", rate) { rate = it }
    field("Months remaining", months) { months = it }
    field("Ownership (%)", ownership) { ownership = it }
    PropertyOwnerPicker(owner) { owner = it }
    Row(Modifier.fillMaxWidth()) {
        Checkbox(included, { included = it }, Modifier.semantics { contentDescription = "Include equity in forecast" }); Text("Include equity in forecast", Modifier.weight(1f).padding(top = 12.dp))
    }
    if (current.providerPropertyId != null) Row(Modifier.fillMaxWidth()) {
        Checkbox(automatic, { automatic = it }, Modifier.semantics { contentDescription = "Refresh RentCast values automatically" }); Text("Refresh RentCast values automatically", Modifier.weight(1f).padding(top = 12.dp))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        runCatching { propertyEditRevision(current, address, mortgage, payment, rate, months, ownership, owner, included, automatic) }
            .onSuccess(onSave).onFailure { error = "Review the address, nonnegative amounts, 0–25% rate, 0–600 months, and 0–100% ownership." }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
}

@Composable
internal fun PropertyOwnerPicker(owner: Owner, onChange: (Owner) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text("Owner: ${enumLabel(owner)}") }
        DropdownMenu(menu, { menu = false }) { Owner.entries.forEach { option ->
            DropdownMenuItem(text = { Text(enumLabel(option)) }, onClick = { onChange(option); menu = false })
        } }
    }
}
