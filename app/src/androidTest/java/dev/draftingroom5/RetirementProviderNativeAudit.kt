package dev.draftingroom5

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.provider.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Synthetic-only, isolated storage and key alias; never reads production financial records. */
internal class RetirementProviderNativeAudit(private val instrumentation: Instrumentation) {
    fun run() {
        val report = StringBuilder()
        val id = UUID.randomUUID().toString()
        val parent = instrumentation.targetContext.noBackupFilesDir
        val directory = File(parent, "provider-audit-$id")
        val alias = "provider-audit-$id"
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getNoBackupFilesDir() = directory.apply { mkdirs() }
        }
        var stage = "device-unlock"
        try {
            val keys = AndroidVaultKey(context, alias)
            check(keys.available()) { "Synthetic emulator must be unlocked with a configured screen lock" }
            stage = "key-generation"
            keys.key(true)
            stage = "vault-create"
            val storage = AndroidVaultStorage(context)
            val vault = ProviderCredentialVault(storage, keys)
            val credential = vault.put(CredentialKind.RENTCAST, ProviderEnvironment.SANDBOX, JSONObject().put("api_key", "synthetic-native-canary"))
            stage = "vault-decrypt"
            check(vault.use(credential) { it.getString("api_key") } == "synthetic-native-canary")
            val initial = storage.read()!!
            check(!String(initial).contains("synthetic-native-canary"))
            stage = "vault-replace"
            val replaced = vault.put(credential.kind, credential.environment, JSONObject().put("api_key", "synthetic-native-canary"), credential)
            val oldIv = JSONObject(String(initial)).getJSONObject(credential.id).getString("iv")
            val newIv = JSONObject(String(storage.read()!!)).getJSONObject(credential.id).getString("iv")
            check(oldIv != newIv)
            stage = "vault-recreate"
            check(ProviderCredentialVault(AndroidVaultStorage(context), AndroidVaultKey(context, alias)).use(replaced) { it.getString("api_key") } == "synthetic-native-canary")
            stage = "vault-tamper"
            val tampered = JSONObject(String(storage.read()!!)); tampered.getJSONObject(replaced.id).put("revision", replaced.revision + 1)
            storage.write(tampered.toString().toByteArray())
            check(runCatching { vault.use(vault.current(replaced.id)) { it.getString("api_key") } }.exceptionOrNull() is ProviderException)
            report.appendLine("PASS: real Android Keystore AES-GCM, unique IVs, ciphertext exclusion, recreation, authenticated metadata tamper rejection (${keys.protection()})")

            stage = "sqlite-transaction"
            var database = RetirementDatabase.open(context)
            var repository = RetirementRepository(database.dao)
            val time = Instant.parse("2026-09-20T00:00:00Z")
            fun manual(name: String) = Account(name, AccountOrigin.MANUAL, null, null,
                listOf(AccountRevision("$name-r", 1, "Synthetic $name", Owner.SELF, AccountType.BROKERAGE, TaxTreatment.TAXABLE, true, time)),
                listOf(BalanceSnapshot("$name-b", LocalDate.parse("2026-09-19"), time, 1, Money(100), null, BalanceSource.MANUAL, "$name-original")))
            check(repository.addManualAccount(0, manual("a")) is RetirementResult.Success)
            check(repository.addManualAccount(1, manual("b")) is RetirementResult.Success)
            val item = CredentialHandle(UUID.randomUUID().toString(), CredentialKind.PLAID_ITEM, ProviderEnvironment.SANDBOX, 1)
            val profile = CredentialHandle(UUID.randomUUID().toString(), CredentialKind.PLAID, ProviderEnvironment.SANDBOX, 1)
            val batch = AccountBatch(item, profile, "${item.id}:1", listOf("a", "b").map {
                LinkedAccountData(it, "Synthetic", "1234", "brokerage", Money(200), LocalDate.parse("2026-09-20"), emptyList(), HoldingAvailability.COMPLETE)
            }, time)
            val selections = listOf("a", "b").map { AccountSelection(it, "Synthetic $it", Owner.SELF, AccountType.BROKERAGE, TaxTreatment.TAXABLE, true, it) }
            val sql = SQLiteDatabase.openDatabase(File(directory, "retirement/retirement.db").path, null, SQLiteDatabase.OPEN_READWRITE)
            sql.execSQL("CREATE TRIGGER audit_failure BEFORE INSERT ON balance_snapshots WHEN NEW.accountId='b' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END")
            val before = repository.load()
            check(runCatching { repository.acceptProviderBatch(2, batch, selections) }.isFailure)
            check(repository.load() == before && database.dao.balanceCount() == 2)
            sql.execSQL("DROP TRIGGER audit_failure")
            val accepted = (repository.acceptProviderBatch(2, batch, selections) as RetirementResult.Success).value
            check(database.dao.balanceCount() == 4)
            check(runCatching { sql.execSQL("UPDATE balance_snapshots SET amountCents=0") }.isFailure)
            check(runCatching { sql.execSQL("DELETE FROM balance_snapshots") }.isFailure)
            sql.close(); database.close()
            database = RetirementDatabase.open(context); repository = RetirementRepository(database.dao)
            check(repository.load() == accepted && database.dao.balanceCount() == 4)
            database.close()
            report.appendLine("PASS: real SQLite rollback after first linked snapshot, atomic classification/value/freshness publication, append-only triggers and database recreation")
            instrumentation.finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (failure: Throwable) {
            report.appendLine("FAIL: native provider audit at $stage (${failure.javaClass.simpleName}); no private exception details emitted")
            instrumentation.finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            check(directory.canonicalFile.parentFile == parent.canonicalFile && directory.name == "provider-audit-$id")
            directory.deleteRecursively()
        }
    }
}
