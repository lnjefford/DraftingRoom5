package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.domain.ProviderEnvironment
import org.json.JSONObject
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

enum class ProviderFailure { NEEDS_CREDENTIALS, API_CREDENTIALS, RECONNECT_REQUIRED, CONFIGURATION, OFFLINE, CANCELLED, RATE_LIMITED, UNSUPPORTED, INVALID_RESPONSE, CONFLICT, UNAVAILABLE, EXCHANGE_UNCERTAIN }
class ProviderException(val failure: ProviderFailure, val retryAfter: java.time.Instant? = null) : RuntimeException(failure.name) {
    override fun fillInStackTrace(): Throwable = this
}

/** No upstream messages, request IDs, identifiers or values are diagnostic input. */
data class ProviderDiagnostic(val operation: ProviderOperation, val failure: ProviderFailure?, val durationBucket: Int) {
    val diagnosticId: String = UUID.randomUUID().toString()
}
enum class ProviderOperation { LINK, EXCHANGE, SYNC, RECONNECT, DISCONNECT, CREDENTIALS }
enum class CredentialKind { PLAID, PLAID_ITEM, RENTCAST }
data class CredentialHandle(val id: String, val kind: CredentialKind, val environment: ProviderEnvironment, val revision: Long)

internal interface VaultStorage { fun read(): ByteArray?; fun write(bytes: ByteArray) }
internal interface VaultKey { fun key(create: Boolean): SecretKey; fun available(): Boolean }

/** A single atomic encrypted document. Metadata is authenticated per record; no key is stored here.
 * Callbacks with plaintext are internal to provider adapters. UI receives handles only.
 */
class ProviderCredentialVault internal constructor(private val storage: VaultStorage, private val keys: VaultKey) {
    private val lock = Any()
    internal fun handles(kind: CredentialKind): List<CredentialHandle> = synchronized(lock) {
        val root = readRoot()
        root.keys().asSequence().map { handle(root.getJSONObject(it)) }.filter { it.kind == kind }.toList()
    }

    internal fun current(id: String): CredentialHandle = synchronized(lock) {
        handle(readRoot().optJSONObject(id) ?: throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS))
    }

    /** Caller must complete device authentication before invoking provisioning/replacement. */
    internal fun put(kind: CredentialKind, environment: ProviderEnvironment, secret: JSONObject, previous: CredentialHandle? = null,
        allowEnvironmentChange: Boolean = false): CredentialHandle = safe {
        synchronized(lock) {
            if (!keys.available()) throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
            val root = readRoot()
            previous?.let { requireCurrent(root, it); require(it.kind == kind && (it.environment == environment || allowEnvironmentChange)) }
            val next = CredentialHandle(previous?.id ?: UUID.randomUUID().toString(), kind, environment, Math.addExact(previous?.revision ?: 0, 1))
            val plaintext = secret.toString().toByteArray(Charsets.UTF_8)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, keys.key(create = root.length() == 0))
                check(cipher.iv.size == 12)
                cipher.updateAAD(aad(next))
                val envelope = metadata(next).put("iv", Base64.getEncoder().encodeToString(cipher.iv))
                    .put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(plaintext)))
                root.put(next.id, envelope)
                storage.write(root.toString().toByteArray(Charsets.UTF_8))
                next
            } finally { plaintext.fill(0) }
        }
    }

    internal fun <T> use(handle: CredentialHandle, block: (JSONObject) -> T): T {
        val plaintext = safe { synchronized(lock) {
            if (!keys.available()) throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
            val root = readRoot(); requireCurrent(root, handle)
            val record = root.getJSONObject(handle.id)
            val iv = Base64.getDecoder().decode(record.getString("iv")); require(iv.size == 12)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, keys.key(false), GCMParameterSpec(128, iv)); updateAAD(aad(handle))
                doFinal(Base64.getDecoder().decode(record.getString("ciphertext")))
            }
        } }
        return try { block(JSONObject(String(plaintext, Charsets.UTF_8))) } finally { plaintext.fill(0) }
    }

    /** Commit while revisions cannot change. Lock order is vault -> repository, never inverse. */
    internal fun <T> guarded(handles: List<CredentialHandle>, block: () -> T): T = synchronized(lock) {
        val root = readRoot(); handles.forEach { requireCurrent(root, it) }; block()
    }

    internal fun remove(handle: CredentialHandle) = synchronized(lock) {
        val root = readRoot(); requireCurrent(root, handle); root.remove(handle.id)
        storage.write(root.toString().toByteArray(Charsets.UTF_8))
    }

    /** Explicit authenticated recovery only; never called automatically after a decryption error. */
    internal fun resetAll(beforeErase: () -> Unit, deleteKey: () -> Unit) = synchronized(lock) {
        beforeErase()
        deleteKey()
        storage.write(JSONObject().toString().toByteArray(Charsets.UTF_8))
    }

    private fun readRoot(): JSONObject = safe {
        storage.read()?.let { bytes ->
            require(bytes.size <= 2 * 1024 * 1024)
            JSONObject(String(bytes, Charsets.UTF_8)).also { root ->
                require(root.length() <= 500)
                root.keys().forEach { id -> require(id == handle(root.getJSONObject(id)).id) }
            }
        } ?: JSONObject()
    }
    private fun requireCurrent(root: JSONObject, expected: CredentialHandle) {
        val actual = root.optJSONObject(expected.id)?.let(::handle)
        if (actual != expected) throw ProviderException(ProviderFailure.CONFLICT)
    }
    private fun metadata(h: CredentialHandle) = JSONObject().put("schema", 1).put("id", h.id).put("provider", h.kind.name)
        .put("environment", h.environment.name).put("revision", h.revision)
    private fun aad(h: CredentialHandle) = "1|${h.kind}|${h.environment}|${h.id}|${h.revision}".toByteArray(Charsets.UTF_8)
    private fun handle(o: JSONObject): CredentialHandle {
        require(o.get("schema") == 1)
        val revision = o.get("revision"); require((revision is Int || revision is Long) && (revision as Number).toLong() > 0)
        val id = o.getString("id"); UUID.fromString(id)
        return CredentialHandle(id, CredentialKind.valueOf(o.getString("provider")), ProviderEnvironment.valueOf(o.getString("environment")), o.getLong("revision"))
    }
    private inline fun <T> safe(block: () -> T): T = try { block() }
    catch (e: ProviderException) { throw e }
    catch (_: Exception) { throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS) }
}
