package dev.draftingroom5.retirement.provider

import dev.draftingroom5.inspectJsonStructure
import dev.draftingroom5.retirement.domain.ProviderEnvironment
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal enum class PlaidEndpoint(val path: String) {
    LINK("/link/token/create"), EXCHANGE("/item/public_token/exchange"), ACCOUNTS("/accounts/get"),
    HOLDINGS("/investments/holdings/get"), REMOVE("/item/remove")
}
internal fun interface ProviderTransport {
    fun post(environment: ProviderEnvironment, endpoint: PlaidEndpoint, body: JSONObject): JSONObject
}

/** Only fixed HTTPS origins and endpoint enums. No redirects, caching, cookies, traces or logging. */
internal class SecureProviderTransport(private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : ProviderTransport {
    override fun post(environment: ProviderEnvironment, endpoint: PlaidEndpoint, body: JSONObject): JSONObject {
        val host = when (environment) {
            ProviderEnvironment.SANDBOX -> "sandbox.plaid.com"
            ProviderEnvironment.DEVELOPMENT -> "development.plaid.com"
            ProviderEnvironment.PRODUCTION -> "production.plaid.com"
        }
        val connection = connectionFactory(URL("https://$host${endpoint.path}"))
        val request = body.toString().toByteArray(Charsets.UTF_8)
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Plaid-Version", "2020-09-14")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            val status = connection.responseCode
            if (status in 300..399) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
            if (status == 429) throw ProviderException(ProviderFailure.RATE_LIMITED, retryAfter(connection.getHeaderField("Retry-After")))
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readBounded(it, 4 * 1024 * 1024) } ?: byteArrayOf()
            try {
                if (bytes.size > 4 * 1024 * 1024) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
                val response = parseProviderJson(String(bytes, Charsets.UTF_8))
                if (status !in 200..299) throw ProviderException(when {
                    status == 429 -> ProviderFailure.RATE_LIMITED
                    response.optString("error_code") == "INVALID_API_KEYS" -> ProviderFailure.API_CREDENTIALS
                    response.optString("error_code") in setOf("ITEM_LOGIN_REQUIRED", "INVALID_ACCESS_TOKEN", "ITEM_NOT_FOUND", "USER_PERMISSION_REVOKED", "PASSWORD_RESET_REQUIRED") -> ProviderFailure.RECONNECT_REQUIRED
                    response.optString("error_code") in setOf("INVALID_CONFIGURATION", "INVALID_FIELD", "MISSING_FIELDS", "UNKNOWN_FIELDS", "UNAUTHORIZED_ENVIRONMENT") -> ProviderFailure.CONFIGURATION
                    response.optString("error_code") in setOf("PRODUCT_NOT_READY") -> ProviderFailure.UNAVAILABLE
                    response.optString("error_code") in setOf("PRODUCT_NOT_ENABLED", "SANDBOX_PRODUCT_NOT_ENABLED", "INVALID_PRODUCT", "PRODUCTS_NOT_SUPPORTED", "NO_INVESTMENT_ACCOUNTS", "ADDITIONAL_CONSENT_REQUIRED") -> ProviderFailure.UNSUPPORTED
                    status >= 500 -> ProviderFailure.UNAVAILABLE
                    else -> ProviderFailure.INVALID_RESPONSE
                })
                return response
            } finally { bytes.fill(0) }
        } catch (e: ProviderException) { throw e }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.INVALID_RESPONSE) }
        finally { request.fill(0); connection.disconnect() }
    }
}

internal fun retryAfter(header: String?, now: java.time.Instant = java.time.Instant.now()): java.time.Instant {
    val parsed = header?.let { value ->
        runCatching {
            value.toLongOrNull()?.let { now.plusSeconds(it.coerceAtLeast(0)) }
                ?: java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()
    }
    return parsed?.takeIf { it.isAfter(now) } ?: now.plusSeconds(86_400)
}

internal fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(8192)
    val output = java.io.ByteArrayOutputStream()
    try {
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            if (output.size() > limit) throw ProviderException(ProviderFailure.INVALID_RESPONSE)
        }
        return output.toByteArray()
    } finally { buffer.fill(0) }
}

/** Preserve decimal lexemes before Android JSONObject can coerce money through Double. */
internal fun parseProviderJson(text: String): JSONObject {
    return JSONObject(protectProviderNumbers(text))
}

internal fun protectProviderNumbers(text: String): String {
    require(text.length <= 4 * 1024 * 1024)
    inspectJsonStructure(text)
    val output = StringBuilder(text.length)
    var index = 0; var quoted = false; var escaped = false
    while (index < text.length) {
        val c = text[index]
        if (quoted) {
            output.append(c)
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            index++
        } else if (c == '"') { quoted = true; output.append(c); index++ }
        else if (c == '-' || c.isDigit()) {
            val start = index++
            while (index < text.length && text[index] in "0123456789.eE+-") index++
            val number = text.substring(start, index)
            require(number.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")))
            output.append('"').append(number).append('"')
        } else { output.append(c); index++ }
    }
    return output.toString()
}
