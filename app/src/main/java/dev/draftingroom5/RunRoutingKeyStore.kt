package dev.draftingroom5

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the user's HeiGIT key out of APKs, backups, logs, and saved run documents. */
internal class RunRoutingKeyStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "run-routing/key.bin"))
    private val alias = "run-routing-heigit-v1"

    fun hasKey(): Boolean = file.baseFile.exists()

    fun load(): String? = try {
        if (!hasKey()) return null
        val bytes = file.openRead().use { it.readNBytes(2049) }
        require(bytes.size in 29..2048)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8).takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    fun save(value: String): Boolean = try {
        val clean = value.trim()
        require(clean.isNotEmpty() && clean.length <= 1024 && clean.none(Char::isWhitespace))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val bytes = cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        check(file.baseFile.parentFile!!.let { it.exists() || it.mkdirs() })
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        true
    } catch (_: Exception) { false }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        require(create)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec); generateKey()
        }
    }
}
