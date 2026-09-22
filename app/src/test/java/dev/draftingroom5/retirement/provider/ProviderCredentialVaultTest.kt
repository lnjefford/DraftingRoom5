package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.domain.ProviderEnvironment
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderCredentialVaultTest {
    @Test fun ciphertextRoundTripUsesDifferentIvsAndNoPlaintext() {
        val storage = MemoryVaultStorage(); val key = MemoryVaultKey(); val vault = ProviderCredentialVault(storage, key)
        val h = vault.put(CredentialKind.RENTCAST, ProviderEnvironment.PRODUCTION, JSONObject().put("api_key", "synthetic-canary"))
        val first = JSONObject(String(storage.bytes!!)).getJSONObject(h.id)
        val next = vault.put(h.kind, h.environment, JSONObject().put("api_key", "synthetic-canary"), h)
        val second = JSONObject(String(storage.bytes!!)).getJSONObject(h.id)
        assertNotEquals(first.getString("iv"), second.getString("iv"))
        assertFalse(String(storage.bytes!!).contains("synthetic-canary"))
        assertEquals("synthetic-canary", ProviderCredentialVault(storage, key).use(next) { it.getString("api_key") })
    }
    @Test fun tamperingAuthenticatedMetadataAndCiphertextFailsClosed() {
        for (field in listOf("revision", "environment", "ciphertext")) {
            val storage = MemoryVaultStorage(); val vault = ProviderCredentialVault(storage, MemoryVaultKey())
            val h = vault.put(CredentialKind.PLAID, ProviderEnvironment.SANDBOX, JSONObject().put("secret", "synthetic-canary"))
            val root = JSONObject(String(storage.bytes!!)); val record = root.getJSONObject(h.id)
            when (field) { "revision" -> record.put(field, 2); "environment" -> record.put(field, "PRODUCTION"); else -> record.put(field, "AAAA") }
            storage.bytes = root.toString().toByteArray()
            val altered = vault.current(h.id)
            assertThrows(ProviderException::class.java) { vault.use(altered) { fail("Tampered data was decrypted") } }
        }
    }
    @Test fun failedReplacementRetainsOldCredentialAndRevision() {
        val storage = MemoryVaultStorage(); val vault = ProviderCredentialVault(storage, MemoryVaultKey())
        val h = vault.put(CredentialKind.PLAID, ProviderEnvironment.SANDBOX, JSONObject().put("secret", "synthetic-old"))
        storage.fail = true
        assertThrows(ProviderException::class.java) { vault.put(h.kind, h.environment, JSONObject().put("secret", "synthetic-new"), h) }
        assertEquals(h, vault.current(h.id)); assertEquals("synthetic-old", vault.use(h) { it.getString("secret") })
    }
    @Test fun replacementInvalidatesInflightHandlesAndRemovalCannotResurrectThem() {
        val vault = ProviderCredentialVault(MemoryVaultStorage(), MemoryVaultKey())
        val h = vault.put(CredentialKind.PLAID, ProviderEnvironment.SANDBOX, JSONObject().put("secret", "synthetic-one"))
        val next = vault.put(h.kind, h.environment, JSONObject().put("secret", "synthetic-two"), h)
        assertThrows(ProviderException::class.java) { vault.guarded(listOf(h)) { fail("Old request committed") } }
        vault.remove(next)
        assertThrows(ProviderException::class.java) { vault.use(next) { fail("Removed credential decrypted") } }
    }
    @Test fun beforeUnlockAndKeyLossNeverFallBackToPlaintext() {
        val key = MemoryVaultKey(); val vault = ProviderCredentialVault(MemoryVaultStorage(), key)
        val h = vault.put(CredentialKind.PLAID, ProviderEnvironment.SANDBOX, JSONObject().put("secret", "synthetic-only"))
        key.unlocked = false
        assertThrows(ProviderException::class.java) { vault.use(h) { fail("Read before unlock") } }
        key.unlocked = true; key.secret = MemoryVaultKey().secret
        assertThrows(ProviderException::class.java) { vault.use(h) { fail("Key loss fallback") } }
    }
    @Test fun metadataCannotCoerceFractionalSchemaOrStringRevision() {
        for ((field, value) in listOf("schema" to 1.5, "revision" to "1")) {
            val storage = MemoryVaultStorage(); val vault = ProviderCredentialVault(storage, MemoryVaultKey())
            val h = vault.put(CredentialKind.PLAID, ProviderEnvironment.SANDBOX, JSONObject().put("secret", "synthetic"))
            val root = JSONObject(String(storage.bytes!!)); root.getJSONObject(h.id).put(field, value); storage.bytes = root.toString().toByteArray()
            assertThrows(ProviderException::class.java) { vault.current(h.id) }
        }
    }
}
