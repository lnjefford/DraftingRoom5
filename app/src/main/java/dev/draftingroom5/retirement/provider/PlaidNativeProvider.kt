package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Ephemeral SDK handoff only. Never parcel, save, log, or use as a route. */
internal class LinkAttempt(val id: String, val profile: CredentialHandle, val item: CredentialHandle?,
    val expiresAt: Instant, private var token: String?) {
    fun takeToken(): String = token?.also { token = null } ?: throw ProviderException(ProviderFailure.CANCELLED)
    override fun toString() = "LinkAttempt(redacted)"
}

internal class PlaidNativeProvider(
    private val vault: ProviderCredentialVault, private val transport: ProviderTransport,
    private val repository: RetirementRepository,
    private val operationGate: ProviderOperationGate = ProviderOperationGate.process,
    private val now: () -> Instant = Instant::now,
) {
    private val attempts = mutableMapOf<String, LinkAttempt>()
    private val attemptLock = Any()
    fun profiles() = vault.handles(CredentialKind.PLAID)
    fun items() = vault.handles(CredentialKind.PLAID_ITEM)
    fun removeCredentials(profile: CredentialHandle): Boolean = vault.guarded(listOf(profile)) {
        val affected = items().filter { vault.use(it) { record -> record.getString("profile") == profile.id } }
        var revoked = true
        affected.forEach { if (!disconnect(it.id)) revoked = false }
        vault.remove(profile)
        revoked
    }
    fun saveCredentials(clientId: CharArray, secret: CharArray, environment: ProviderEnvironment, previous: CredentialHandle?): CredentialHandle {
        try {
            require(clientId.size in 1..256 && secret.size in 1..512)
            return vault.put(CredentialKind.PLAID, environment,
                JSONObject().put("client_id", String(clientId)).put("secret", String(secret)), previous)
        } finally { clientId.fill('\u0000'); secret.fill('\u0000') }
    }

    fun beginLink(profileId: String, itemId: String? = null): LinkAttempt {
        val profile = vault.current(profileId)
        require(profile.kind == CredentialKind.PLAID)
        val item = itemId?.let { reserve(it, profile) }
        val body = JSONObject().put("client_name", "DraftingRoom5").put("language", "en")
            .put("country_codes", JSONArray().put("US")).put("android_package_name", "dev.draftingroom5")
            .put("user", JSONObject().put("client_user_id", profile.id))
        if (item == null) body.put("products", JSONArray().put("investments"))
        else vault.use(item) { body.put("access_token", it.getString("access_token")) }
        val response = request(profile, PlaidEndpoint.LINK, body)
        val attempt = LinkAttempt(UUID.randomUUID().toString(), profile, item,
            minOf(Instant.parse(response.getString("expiration")), now().plusSeconds(1800)), response.getString("link_token"))
        vault.guarded(listOfNotNull(profile, item)) {
            synchronized(attemptLock) { attempts.entries.removeAll { !now().isBefore(it.value.expiresAt) }; attempts[attempt.id] = attempt }
        }
        return attempt
    }

    fun cancelLink(attemptId: String) { synchronized(attemptLock) { attempts.remove(attemptId) } }

    /** Consume attempt before one-time exchange. Ambiguous responses are never re-exchanged. */
    fun completeLink(attemptId: String, publicToken: CharArray?): String {
        try {
            val attempt = synchronized(attemptLock) { attempts.remove(attemptId) } ?: throw ProviderException(ProviderFailure.CANCELLED)
            if (!now().isBefore(attempt.expiresAt)) throw ProviderException(ProviderFailure.CANCELLED)
            vault.guarded(listOfNotNull(attempt.profile, attempt.item)) { }
            if (attempt.item != null) return attempt.item.id // Update mode retains the affected access token.
            require(publicToken != null && publicToken.isNotEmpty() && publicToken.size <= 2048)
            // Keep rotation/removal outside this one-time exchange -> durable-token critical section.
            return vault.guarded(listOf(attempt.profile)) {
                val response = try { request(attempt.profile, PlaidEndpoint.EXCHANGE, JSONObject().put("public_token", String(publicToken))) }
                catch (_: Exception) { throw ProviderException(ProviderFailure.EXCHANGE_UNCERTAIN) }
                val pending = try { JSONObject().put("profile", attempt.profile.id).put("access_token", response.getString("access_token"))
                    .put("remote_item", response.getString("item_id")).put("pending", true).put("attempt", attempt.id)
                    .put("expires", now().plusSeconds(86_400).toString()) }
                catch (_: Exception) { throw ProviderException(ProviderFailure.EXCHANGE_UNCERTAIN) }
                // A provider item may already be linked. Never create a second local identity for it.
                val duplicate = items().firstOrNull { h -> h.environment == attempt.profile.environment && vault.use(h) {
                    it.getString("profile") == attempt.profile.id && it.getString("remote_item") == pending.getString("remote_item")
                } }
                if (duplicate != null) throw ProviderException(ProviderFailure.CONFLICT)
                try { vault.put(CredentialKind.PLAID_ITEM, attempt.profile.environment, pending).id }
                catch (_: Exception) {
                    // Never leave a known unsaved token intentionally active. Remote failure stays explicit.
                    runCatching { request(attempt.profile, PlaidEndpoint.REMOVE, JSONObject().put("access_token", pending.getString("access_token"))) }
                    throw ProviderException(ProviderFailure.EXCHANGE_UNCERTAIN)
                }
            }
        } finally { publicToken?.fill('\u0000') }
    }

    fun pendingItems(): List<String> {
        val pending = mutableListOf<String>()
        items().forEach { handle ->
            vault.use(handle) { record ->
                if (record.getBoolean("pending")) {
                    val accepted = repository.load().providerItems.any { it.id == handle.id && it.lastAcceptedAt != null }
                    if (accepted) vault.put(handle.kind, handle.environment, record.put("pending", false), handle)
                    else if (!now().isBefore(Instant.parse(record.getString("expires")))) disconnect(handle.id)
                    else pending += handle.id
                }
            }
        }
        return pending
    }

    fun fetchSnapshot(itemId: String, reserved: (CredentialHandle) -> Unit = {}): AccountBatch {
        var original = vault.current(itemId)
        vault.use(original) { record ->
            if (record.getBoolean("pending") && repository.load().providerItems.any { it.id == itemId && it.lastAcceptedAt != null && it.revokedAt == null }) {
                original = vault.put(original.kind, original.environment, record.put("pending", false), original)
            }
        }
        val profile = vault.use(original) { vault.current(it.getString("profile")) }
        val item = reserve(itemId, profile)
        reserved(item)
        return vault.use(item) { record ->
            if (record.getBoolean("pending") && !now().isBefore(Instant.parse(record.getString("expires"))))
                throw ProviderException(ProviderFailure.CANCELLED)
            val body = JSONObject().put("access_token", record.getString("access_token"))
            val response = request(profile, PlaidEndpoint.ACCOUNTS, body)
            require(response.getJSONObject("item").getString("item_id") == record.getString("remote_item"))
            val accounts = response.getJSONArray("accounts")
            require(accounts.length() in 1..500)
            var holdingsResponse: JSONObject? = null
            var availability = HoldingAvailability.COMPLETE
            try {
                holdingsResponse = request(profile, PlaidEndpoint.HOLDINGS, JSONObject().put("access_token", record.getString("access_token")))
                require(holdingsResponse.getJSONObject("item").getString("item_id") == record.getString("remote_item"))
            } catch (e: ProviderException) {
                if (e.failure != ProviderFailure.UNSUPPORTED) throw e
                availability = HoldingAvailability.UNSUPPORTED
            }
            val holdings = holdingsResponse?.getJSONArray("holdings") ?: JSONArray()
            require(holdings.length() <= 10_000)
            val securityObjects = holdingsResponse?.getJSONArray("securities") ?: JSONArray()
            val securities = (0 until securityObjects.length()).map { securityObjects.getJSONObject(it) }.associateBy { it.getString("security_id") }
            require(securities.size == securityObjects.length())
            val accountIds = (0 until accounts.length()).map { accounts.getJSONObject(it).getString("account_id") }.toSet()
            require(accountIds.size == accounts.length())
            val holdingAccounts = holdingsResponse?.getJSONArray("accounts")
            val supportedIds = holdingAccounts?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("account_id") }.toSet() }.orEmpty()
            val grouped = (0 until holdings.length()).map { holdings.getJSONObject(it) }.groupBy { it.getString("account_id") }
            require(grouped.keys.all { it in accountIds && it in supportedIds })
            val time = now()
            val normalized = (0 until accounts.length()).map { index ->
                val account = accounts.getJSONObject(index); val id = account.getString("account_id")
                // Credit/loan balances are liabilities, never a positive asset selected as brokerage.
                if (account.optString("type") !in setOf("investment", "brokerage", "depository"))
                    throw ProviderException(ProviderFailure.UNSUPPORTED)
                val balances = account.getJSONObject("balances")
                usd(balances)
                val current = money(balances, "current")
                // Unknown balances cannot replace last-good data or certify a complete sync.
                require(current != null)
                val rows = grouped[id].orEmpty().map { h ->
                    usd(h)
                    val securityId = h.getString("security_id")
                    val security = requireNotNull(securities[securityId]); usd(security)
                    val quantity = decimal(h, "quantity").stripTrailingZeros().toPlainString()
                    Holding(securityId, quantity, money(h, "institution_price"), money(h, "cost_basis"), security.optString("type", "unknown").ifBlank { "unknown" })
                }.sortedBy { it.securityId }
                require(rows.map { it.securityId }.distinct().size == rows.size)
                val status = if (availability == HoldingAvailability.UNSUPPORTED || id !in supportedIds) HoldingAvailability.UNSUPPORTED else HoldingAvailability.COMPLETE
                require(availability != HoldingAvailability.COMPLETE || account.optString("type") != "investment" || id in supportedIds)
                val date = balances.optString("last_updated_datetime").takeIf { it.isNotBlank() && it != "null" }
                    ?.let { Instant.parse(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: time.atZone(ZoneOffset.UTC).toLocalDate()
                require(!date.isAfter(time.atZone(ZoneOffset.UTC).toLocalDate()))
                LinkedAccountData(id, account.getString("name").take(120), account.optString("mask").takeIf { it != "null" && it.isNotBlank() },
                    account.optString("subtype").takeIf { it != "null" }, current, date, rows, status)
            }
            vault.guarded(listOf(profile, item)) { AccountBatch(item, profile, "${item.id}:${item.revision}", normalized, time) }
        }
    }

    fun accept(batch: AccountBatch, generation: Long, selections: List<AccountSelection>?): RetirementResult<RetirementState> = vault.guarded(emptyList()) {
        val current = repository.load()
        if (batch.operationId in current.acceptedProviderOperations) return@guarded RetirementResult.Success(current)
        vault.guarded(listOf(batch.profile, batch.item)) {
            val pending = vault.use(batch.item) { it.getBoolean("pending") }
            if (pending && selections == null) throw ProviderException(ProviderFailure.CONFLICT)
            val result = repository.acceptProviderBatch(generation, batch, selections)
            if (result is RetirementResult.Success && pending) {
                // DB is durable first. A crash here leaves a recoverable pending record, not lost accounts.
                vault.use(batch.item) { record -> vault.put(CredentialKind.PLAID_ITEM, batch.item.environment, record.put("pending", false), batch.item) }
            }
            result
        }
    }

    fun refresh(itemId: String, shouldContinue: () -> Boolean = { true }): ProviderFailure? {
        return operationGate.tryRun("plaid:$itemId", ProviderFailure.CONFLICT) {
            refreshExclusive(itemId, shouldContinue)
        }
    }

    private fun refreshExclusive(itemId: String, shouldContinue: () -> Boolean): ProviderFailure? {
        val initial = repository.load()
        if (initial.providerItems.any { it.id == itemId && it.retryAfter?.isAfter(now()) == true }) return ProviderFailure.RATE_LIMITED
        val generation = initial.generation
        var handle: CredentialHandle? = null
        try {
            val batch = fetchSnapshot(itemId) { handle = it }
            if (!shouldContinue()) throw ProviderException(ProviderFailure.CANCELLED)
            when (accept(batch, generation, null)) {
                is RetirementResult.Success -> return null
                is RetirementResult.Conflict -> throw ProviderException(ProviderFailure.CONFLICT)
                is RetirementResult.Invalid -> throw ProviderException(ProviderFailure.INVALID_RESPONSE)
            }
        } catch (e: ProviderException) {
            if (!shouldContinue()) return ProviderFailure.CANCELLED
            recordFailure(itemId, handle, generation, e.failure, e.retryAfter); return e.failure
        } catch (_: Exception) {
            if (!shouldContinue()) return ProviderFailure.CANCELLED
            recordFailure(itemId, handle, generation, ProviderFailure.INVALID_RESPONSE); return ProviderFailure.INVALID_RESPONSE
        }
    }

    private fun recordFailure(itemId: String, handle: CredentialHandle?, generation: Long, failure: ProviderFailure, retryAfter: Instant? = null) {
        if (failure in setOf(ProviderFailure.CONFLICT, ProviderFailure.CANCELLED)) return
        val state = repository.load()
        if (state.generation != generation) return
        if (handle != null && runCatching { vault.current(itemId) }.getOrNull() != handle) return
        val old = state.providerItems.singleOrNull { it.id == itemId } ?: return
        if (old.revokedAt != null) return
        if (handle != null && old.revision > handle.revision) return
        val status = when (failure) { ProviderFailure.OFFLINE -> ProviderStatus.OFFLINE; ProviderFailure.RATE_LIMITED -> ProviderStatus.RATE_LIMITED
            ProviderFailure.UNSUPPORTED -> ProviderStatus.UNSUPPORTED; else -> ProviderStatus.ATTENTION }
        val error = when (failure) { ProviderFailure.NEEDS_CREDENTIALS -> ProviderError.AUTHENTICATION_REQUIRED
            ProviderFailure.RATE_LIMITED -> ProviderError.RATE_LIMITED; ProviderFailure.INVALID_RESPONSE -> ProviderError.INVALID_RESPONSE; else -> ProviderError.UNAVAILABLE }
        repository.setProviderItem(state.generation, old.copy(status = status, attemptedAt = now(), error = error,
            retryAfter = if (failure == ProviderFailure.RATE_LIMITED) retryAfter ?: now().plusSeconds(86_400) else null))
    }

    /** Receipt distinguishes local removal from remote revocation. Last-good account history remains. */
    fun disconnect(itemId: String): Boolean {
        val item = vault.current(itemId)
        return vault.guarded(listOf(item)) {
            // Invalidate accepted work before the remote call; all future fetches refuse a revoked item.
            val state = repository.load(); val old = state.providerItems.singleOrNull { it.id == itemId }
            if (old?.revokedAt == null) {
                val tombstone = old?.copy(status = ProviderStatus.ATTENTION, error = ProviderError.AUTHENTICATION_REQUIRED,
                    revokedAt = now(), revision = item.revision + 1) ?: ProviderItemState(item.id, null, ProviderStatus.ATTENTION,
                    now(), null, null, ProviderError.AUTHENTICATION_REQUIRED, item.revision + 1, now())
                val result = repository.setProviderItem(state.generation, tombstone)
                if (result !is RetirementResult.Success) throw ProviderException(ProviderFailure.CONFLICT)
            }
            var revoked = false
            try {
                vault.use(item) { record -> request(vault.current(record.getString("profile")), PlaidEndpoint.REMOVE,
                    JSONObject().put("access_token", record.getString("access_token"))) }
                revoked = true
            } catch (_: Exception) { /* No upstream details leave this boundary. */ }
            vault.remove(item)
            revoked
        }
    }

    private fun reserve(itemId: String, profile: CredentialHandle): CredentialHandle {
        val item = vault.current(itemId)
        return vault.guarded(listOf(profile, item)) {
            if (repository.load().providerItems.any { it.id == itemId && it.revokedAt != null }) throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
            vault.use(item) { record ->
                require(item.kind == CredentialKind.PLAID_ITEM && item.environment == profile.environment && record.getString("profile") == profile.id)
                vault.put(item.kind, item.environment, record, item)
            }
        }
    }
    private fun request(profile: CredentialHandle, endpoint: PlaidEndpoint, body: JSONObject): JSONObject = vault.use(profile) { credential ->
        require(profile.kind == CredentialKind.PLAID)
        body.put("client_id", credential.getString("client_id")).put("secret", credential.getString("secret"))
        try { transport.post(profile.environment, endpoint, body) }
        finally { body.remove("client_id"); body.remove("secret"); body.remove("access_token"); body.remove("public_token") }
    }
    private fun usd(o: JSONObject) { require(o.optString("iso_currency_code") == "USD" && (o.isNull("unofficial_currency_code") || !o.has("unofficial_currency_code"))) }
    private fun decimal(o: JSONObject, key: String): BigDecimal {
        val raw = o.get(key); require(raw is String && raw.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) && raw.length <= 80)
        return BigDecimal(raw)
    }
    private fun money(o: JSONObject, key: String): Money? = if (o.isNull(key)) null else {
        val cents = decimal(o, key).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(cents in -MAX_ASSET_CENTS..MAX_ASSET_CENTS); Money(cents)
    }
}
