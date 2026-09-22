package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.domain.ProviderEnvironment
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class SecureProviderTransportTest {
    @Test fun onlyFixedHttpsPostOriginsWithNoRedirectOrDiskCache() {
        ProviderEnvironment.entries.forEach { environment -> PlaidEndpoint.entries.forEach { endpoint ->
            var connection: FakeConnection? = null
            val transport = SecureProviderTransport { url -> FakeConnection(url).also { connection = it } }
            val result = transport.post(environment, endpoint, JSONObject().put("secret", "synthetic-canary"))
            val c = connection!!
            assertEquals("https", c.url.protocol); assertEquals(-1, c.url.port); assertNull(c.url.query); assertNull(c.url.userInfo)
            assertTrue(c.url.host in setOf("sandbox.plaid.com", "development.plaid.com", "production.plaid.com"))
            assertFalse(c.instanceFollowRedirects); assertFalse(c.useCaches); assertEquals("POST", c.requestMethod)
            assertEquals("no-store", c.getRequestProperty("Cache-Control")); assertTrue(c.disconnected)
            assertEquals("0.005", result.getString("amount"))
        } }
    }
    @Test fun redirectsFailWithoutFollowingAndUpstreamErrorsAreRedacted() {
        val redirect = FakeConnection(URL("https://sandbox.plaid.com"), 302)
        val exception = assertThrows(ProviderException::class.java) {
            SecureProviderTransport { redirect }.post(ProviderEnvironment.SANDBOX, PlaidEndpoint.LINK, JSONObject())
        }
        assertEquals(ProviderFailure.INVALID_RESPONSE, exception.failure); assertTrue(redirect.disconnected)
        val failed = FakeConnection(URL("https://sandbox.plaid.com"), 400,
            """{"error_code":"INVALID_ACCESS_TOKEN","error_message":"synthetic-secret-do-not-leak"}""")
        val auth = assertThrows(ProviderException::class.java) {
            SecureProviderTransport { failed }.post(ProviderEnvironment.SANDBOX, PlaidEndpoint.ACCOUNTS, JSONObject())
        }
        assertEquals(ProviderFailure.RECONNECT_REQUIRED, auth.failure)
        assertFalse(auth.toString().contains("synthetic-secret")); assertNull(auth.cause)
    }
    @Test fun apiKeysAndInstitutionReconnectionAreNotConflated() {
        fun failure(code: String) = assertThrows(ProviderException::class.java) {
            SecureProviderTransport { FakeConnection(URL("https://sandbox.plaid.com"), 400,
                """{"error_code":"$code","error_message":"synthetic-sensitive-detail"}""") }
                .post(ProviderEnvironment.SANDBOX, PlaidEndpoint.LINK, JSONObject())
        }.failure
        assertEquals(ProviderFailure.API_CREDENTIALS, failure("INVALID_API_KEYS"))
        assertEquals(ProviderFailure.RECONNECT_REQUIRED, failure("ITEM_LOGIN_REQUIRED"))
        assertEquals(ProviderFailure.RECONNECT_REQUIRED, failure("ITEM_NOT_FOUND"))
        assertEquals(ProviderFailure.RECONNECT_REQUIRED, failure("USER_PERMISSION_REVOKED"))
    }
    @Test fun linkSetupFailuresRemainActionableWithoutExposingUpstreamText() {
        fun failure(code: String) = assertThrows(ProviderException::class.java) {
            SecureProviderTransport { FakeConnection(URL("https://sandbox.plaid.com"), 400,
                """{"error_code":"$code","error_message":"synthetic-sensitive-detail"}""") }
                .post(ProviderEnvironment.SANDBOX, PlaidEndpoint.LINK, JSONObject())
        }
        assertEquals(ProviderFailure.CONFIGURATION, failure("INVALID_CONFIGURATION").failure)
        assertEquals(ProviderFailure.CONFIGURATION, failure("INVALID_FIELD").failure)
        assertEquals(ProviderFailure.UNSUPPORTED, failure("PRODUCT_NOT_ENABLED").failure)
        assertEquals(ProviderFailure.UNSUPPORTED, failure("SANDBOX_PRODUCT_NOT_ENABLED").failure)
    }
    @Test fun responseSizeAndMalformedJsonFailClosed() {
        assertThrows(ProviderException::class.java) { readBounded(ByteArrayInputStream(ByteArray(101)), 100) }
        assertEquals(100, readBounded(ByteArrayInputStream(ByteArray(100)), 100).size)
        assertThrows(IllegalArgumentException::class.java) { parseProviderJson("""{"amount":1,"amount":2}""") }
    }
    @Test fun retryAfterHonorsLongProviderDelays() {
        val now = java.time.Instant.parse("2026-09-20T00:00:00Z")
        assertEquals(now.plusSeconds(172800), retryAfter("172800", now))
        assertEquals(java.time.Instant.parse("2026-09-22T00:00:00Z"), retryAfter("Tue, 22 Sep 2026 00:00:00 GMT", now))
        assertEquals(now.plusSeconds(86400), retryAfter("malformed", now))
    }
    @Test fun rentCastUsesOnlyFixedHttpsGetAndPrivacyHeaders() {
        RentCastEndpoint.entries.forEach { endpoint ->
            var connection: FakeConnection? = null
            val response = if (endpoint == RentCastEndpoint.PROPERTIES) "[]" else """{"price":485000}"""
            val transport = SecureRentCastTransport { url -> FakeConnection(url, response = response).also { connection = it } }
            transport.get(endpoint, mapOf("address" to "500 Fixture Way, Madison, WI", "compCount" to "20"), "synthetic-key")
            val c = connection!!
            assertEquals("https", c.url.protocol); assertEquals("api.rentcast.io", c.url.host); assertEquals(-1, c.url.port)
            assertTrue(c.url.path in RentCastEndpoint.entries.map { it.path }); assertNull(c.url.userInfo)
            assertFalse(c.instanceFollowRedirects); assertFalse(c.useCaches); assertEquals("GET", c.requestMethod)
            assertEquals("no-store", c.getRequestProperty("Cache-Control")); assertEquals("true", c.getRequestProperty("X-Skip-Logging"))
            assertEquals("synthetic-key", c.getRequestProperty("X-Api-Key")); assertTrue(c.disconnected)
        }
    }
}

internal class FakeConnection(url: URL, private val status: Int = 200, response: String = """{"amount":0.005}""") : HttpsURLConnection(url) {
    private val bytes = response.toByteArray(); private val output = ByteArrayOutputStream(); var disconnected = false
    override fun connect() = Unit
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getOutputStream() = output
    override fun getInputStream() = ByteArrayInputStream(bytes)
    override fun getErrorStream() = ByteArrayInputStream(bytes)
    override fun getCipherSuite() = "synthetic"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}
