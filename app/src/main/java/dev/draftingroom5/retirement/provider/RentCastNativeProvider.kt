package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.data.RetirementResult
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.domain.MAX_ASSET_CENTS
import dev.draftingroom5.retirement.domain.ProviderEnvironment
import dev.draftingroom5.retirement.domain.ProviderError
import dev.draftingroom5.retirement.domain.PropertyValuationSnapshot
import dev.draftingroom5.retirement.domain.ValuationSource
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal enum class RentCastEndpoint(val path: String) { PROPERTIES("/v1/properties"), VALUE("/v1/avm/value") }
internal fun interface RentCastTransport {
    fun get(endpoint: RentCastEndpoint, query: Map<String, String>, apiKey: String): JSONObject
}

/** Fixed-origin RentCast transport. Addresses and credentials never enter logs, redirects, caches or work data. */
internal class SecureRentCastTransport(
    private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) : RentCastTransport {
    override fun get(endpoint: RentCastEndpoint, query: Map<String, String>, apiKey: String): JSONObject {
        require(apiKey.isNotBlank() && apiKey.length <= 512)
        val encoded = query.entries.sortedBy { it.key }.joinToString("&") {
            URLEncoder.encode(it.key, Charsets.UTF_8.name()) + "=" + URLEncoder.encode(it.value, Charsets.UTF_8.name())
        }
        val connection = connectionFactory(URL("https://api.rentcast.io${endpoint.path}?$encoded"))
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("X-Skip-Logging", "true")
            connection.setRequestProperty("X-Api-Key", apiKey)
            val status = connection.responseCode
            if (status in 300..399) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
            if (status == 429) throw ProviderException(ProviderFailure.RATE_LIMITED, retryAfter(connection.getHeaderField("Retry-After")))
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readBounded(it, 4 * 1024 * 1024) } ?: byteArrayOf()
            try {
                val text = String(bytes, Charsets.UTF_8)
                if (status !in 200..299) throw ProviderException(when (status) {
                    401, 403 -> ProviderFailure.NEEDS_CREDENTIALS
                    in 500..599 -> ProviderFailure.UNAVAILABLE
                    else -> ProviderFailure.INVALID_RESPONSE
                })
                return if (endpoint == RentCastEndpoint.PROPERTIES) JSONObject().put("items", parseProviderArray(text)) else parseProviderJson(text)
            } finally { bytes.fill(0) }
        } catch (e: ProviderException) { throw e }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.INVALID_RESPONSE) }
        finally { connection.disconnect() }
    }
}

internal data class PropertyMatch(
    val providerPropertyId: String,
    val formattedAddress: String,
    val facts: String?,
    val estimate: Money,
    val rangeLow: Money?,
    val rangeHigh: Money?,
    val comparableCount: Int,
    val providerAsOf: LocalDate,
    val credential: CredentialHandle? = null,
)

internal class RentCastNativeProvider(
    private val vault: ProviderCredentialVault,
    private val transport: RentCastTransport,
    private val repository: RetirementRepository,
    private val operationGate: ProviderOperationGate = ProviderOperationGate.process,
    private val now: () -> Instant = Instant::now,
) {
    fun credential(): CredentialHandle? = vault.handles(CredentialKind.RENTCAST).singleOrNull()

    fun provision(apiKey: CharArray, previous: CredentialHandle? = credential()): CredentialHandle {
        return try {
            require(apiKey.size in 8..512 && apiKey.none(Char::isWhitespace))
            val secret = JSONObject().put("api_key", String(apiKey))
            vault.put(CredentialKind.RENTCAST, ProviderEnvironment.PRODUCTION, secret, previous)
        }
        finally { apiKey.fill('\u0000') }
    }

    fun find(address: String): PropertyMatch {
        val normalized = address.trim()
        require(normalized.length in 8..240)
        val handle = credential() ?: throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
        return vault.use(handle) { secret ->
            val key = secret.getString("api_key")
            val records = transport.get(RentCastEndpoint.PROPERTIES, mapOf("address" to normalized, "limit" to "5"), key).getJSONArray("items")
            if (records.length() == 0) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
            val record = records.getJSONObject(0)
            val matchedAddress = record.optString("formattedAddress").ifBlank { normalized }
            val value = transport.get(RentCastEndpoint.VALUE, mapOf("address" to matchedAddress, "compCount" to "20"), key)
            vault.guarded(listOf(handle)) { match(record, value, matchedAddress).copy(credential = handle) }
        }
    }

    fun refresh(propertyId: String, continueWork: () -> Boolean = { true }): ProviderFailure? {
        return operationGate.tryRun("rentcast:$propertyId", ProviderFailure.CONFLICT) {
            refreshExclusive(propertyId, continueWork)
        }
    }

    private fun refreshExclusive(propertyId: String, continueWork: () -> Boolean): ProviderFailure? {
        val attempted = now()
        val state = repository.load()
        if (!continueWork()) return ProviderFailure.CANCELLED
        if (state.providerItems.any { it.id == propertyId && it.retryAfter?.isAfter(attempted) == true }) return ProviderFailure.RATE_LIMITED
        val property = state.properties.singleOrNull { it.id == propertyId } ?: return ProviderFailure.INVALID_RESPONSE
        val revision = property.currentRevision
        if (!revision.automaticValueEnabled || property.archivedAt != null) return ProviderFailure.UNSUPPORTED
        val handle = runCatching { vault.current(revision.credentialProfileId!!) }.getOrElse {
            recordFailure(state.generation, propertyId, attempted, ProviderFailure.NEEDS_CREDENTIALS, null)
            return ProviderFailure.NEEDS_CREDENTIALS
        }
        return try {
            val match = vault.use(handle) { secret ->
                val value = transport.get(RentCastEndpoint.VALUE, mapOf("address" to revision.address, "compCount" to "20"), secret.getString("api_key"))
                match(JSONObject().put("id", revision.providerPropertyId).put("formattedAddress", revision.address), value, revision.address)
            }
            if (match.providerPropertyId != revision.providerPropertyId) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
            if (!continueWork()) return ProviderFailure.CANCELLED
            val snapshot = match.snapshot((property.valuations.maxOfOrNull { it.sequence } ?: 0) + 1, attempted)
            vault.guarded(listOf(handle)) {
                if (!continueWork()) return@guarded ProviderFailure.CANCELLED
                when (repository.acceptProviderValuation(state.generation, propertyId, revision.revision, snapshot, attempted)) {
                    is RetirementResult.Success -> null
                    is RetirementResult.Invalid -> ProviderFailure.INVALID_RESPONSE
                    is RetirementResult.Conflict -> ProviderFailure.CONFLICT
                }
            }
        } catch (error: ProviderException) {
            if (!continueWork()) return ProviderFailure.CANCELLED
            if (error.failure !in setOf(ProviderFailure.CONFLICT, ProviderFailure.CANCELLED)) {
                runCatching { vault.guarded(listOf(handle)) { recordFailure(state.generation, propertyId, attempted, error.failure, error.retryAfter) } }
            }
            error.failure
        } catch (_: Exception) {
            if (!continueWork()) return ProviderFailure.CANCELLED
            runCatching { vault.guarded(listOf(handle)) { recordFailure(state.generation, propertyId, attempted, ProviderFailure.INVALID_RESPONSE, null) } }
            ProviderFailure.INVALID_RESPONSE
        }
    }

    private fun recordFailure(generation: Long, propertyId: String, attempted: Instant, failure: ProviderFailure, retryAfter: Instant?) {
        repository.recordPropertyFailure(generation, propertyId, attempted, failure.toProviderError(), retryAfter)
    }

    private fun match(record: JSONObject, value: JSONObject, fallbackAddress: String): PropertyMatch = try {
        val subject = value.optJSONObject("subjectProperty")
        val id = subject?.optString("id")?.takeIf(String::isNotBlank)
            ?: record.optString("id").takeIf(String::isNotBlank) ?: throw ProviderException(ProviderFailure.INVALID_RESPONSE)
        val address = subject?.optString("formattedAddress")?.takeIf(String::isNotBlank)
            ?: record.optString("formattedAddress").takeIf(String::isNotBlank) ?: fallbackAddress
        val facts = listOfNotNull(
            (subject ?: record).optString("propertyType").takeIf(String::isNotBlank),
            (subject ?: record).optString("bedrooms").takeIf(String::isNotBlank)?.let { "$it beds" },
            (subject ?: record).optString("bathrooms").takeIf(String::isNotBlank)?.let { "$it baths" },
            (subject ?: record).optString("squareFootage").takeIf(String::isNotBlank)?.let { "$it sq ft" },
        ).joinToString(" · ").ifBlank { null }
        val estimate = dollars(value, "price")
        val low = if (value.isNull("priceRangeLow")) null else dollars(value, "priceRangeLow")
        val high = if (value.isNull("priceRangeHigh")) null else dollars(value, "priceRangeHigh")
        require((low == null) == (high == null))
        require(low == null || (low.cents <= estimate.cents && estimate.cents <= high!!.cents))
        require(id.length in 1..240 && address.length in 1..240)
        val comps = value.optJSONArray("comparables")?.length() ?: 0
        PropertyMatch(id, address, facts, estimate, low, high, comps, now().atZone(ZoneOffset.UTC).toLocalDate())
    } catch (error: ProviderException) { throw error }
    catch (_: Exception) { throw ProviderException(ProviderFailure.INVALID_RESPONSE) }

    private fun dollars(value: JSONObject, key: String) = dollars(value.getString(key))
    private fun dollars(value: String): Money {
        require(value.length <= 80 && value.matches(Regex("(0|[1-9][0-9]*)(\\.[0-9]+)?")))
        val cents = BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        if (cents !in 0..MAX_ASSET_CENTS) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
        return Money(cents)
    }
}

internal fun PropertyMatch.snapshot(sequence: Long, acceptedAt: Instant) = PropertyValuationSnapshot(
    UUID.randomUUID().toString(), estimate, rangeLow, rangeHigh, comparableCount, ValuationSource.RENTCAST,
    providerAsOf, acceptedAt, sequence, UUID.randomUUID().toString(),
)

private fun ProviderFailure.toProviderError() = when (this) {
    ProviderFailure.NEEDS_CREDENTIALS -> ProviderError.AUTHENTICATION_REQUIRED
    ProviderFailure.RATE_LIMITED -> ProviderError.RATE_LIMITED
    ProviderFailure.OFFLINE, ProviderFailure.UNAVAILABLE -> ProviderError.UNAVAILABLE
    ProviderFailure.CONFLICT -> ProviderError.CONFLICT
    else -> ProviderError.INVALID_RESPONSE
}

private fun parseProviderArray(text: String): JSONArray = JSONArray(protectProviderNumbers(text))
