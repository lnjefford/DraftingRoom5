package dev.draftingroom5.retirement.provider

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

internal class AndroidVaultKey(private val context: Context, private val alias: String = "retirement-provider-wrapping-v1") : VaultKey {
    override fun available() = context.getSystemService(UserManager::class.java).isUserUnlocked &&
        context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override fun key(create: Boolean): SecretKey {
        if (!available()) throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw ProviderException(ProviderFailure.NEEDS_CREDENTIALS)
        fun generate(strong: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).setIsStrongBoxBacked(strong).build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
        }
        return try { generate(true) } catch (_: StrongBoxUnavailableException) { generate(false) }
    }

    fun protection(): String {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key(false), KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= 31) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Trusted hardware"
            else -> "Software Keystore"
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "Secure hardware" else "Software Keystore"
        }
    }
    fun delete() { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
}

internal class AndroidVaultStorage(context: Context) : VaultStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "retirement-credentials/vault.json"))
    override fun read(): ByteArray? = if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null else file.openRead().use {
        readBounded(it, 2 * 1024 * 1024)
    }
    override fun write(bytes: ByteArray) {
        require(bytes.size <= 2 * 1024 * 1024)
        check(file.baseFile.parentFile!!.let { it.exists() || it.mkdirs() })
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (_: Exception) { file.failWrite(stream); throw ProviderException(ProviderFailure.UNAVAILABLE) }
    }
}
