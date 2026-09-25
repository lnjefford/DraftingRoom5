package dev.draftingroom5.retirement.provider

import android.content.Context
import android.os.UserManager
import androidx.work.*
import dev.draftingroom5.requestAutomaticBackupAfterChange
import dev.draftingroom5.retirement.data.RetirementDatabase
import dev.draftingroom5.retirement.data.RetirementRepository
import dev.draftingroom5.retirement.forecast.AndroidForecastCacheStorage
import dev.draftingroom5.retirement.forecast.DailyForecastService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class RetirementProviders private constructor(context: Context) {
    val database = RetirementDatabase.open(context)
    val repository = RetirementRepository(database.dao) { requestAutomaticBackupAfterChange(context) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val forecast = DailyForecastService(repository, applicationScope, AndroidForecastCacheStorage(context))
    val key = AndroidVaultKey(context)
    val vault = ProviderCredentialVault(AndroidVaultStorage(context), key)
    val plaid = PlaidNativeProvider(vault, SecureProviderTransport(), repository)
    val rentCast = RentCastNativeProvider(vault, SecureRentCastTransport(), repository)
    companion object {
        @Volatile private var instance: RetirementProviders? = null
        fun get(context: Context): RetirementProviders = instance ?: synchronized(this) {
            instance ?: RetirementProviders(context.applicationContext).also { instance = it }
        }
    }
}

class RetirementPropertySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(PROPERTY) ?: return@withContext Result.failure()
        if (runCatching { UUID.fromString(id) }.isFailure) return@withContext Result.failure()
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return@withContext Result.retry()
        val runtime = RetirementProviders.get(applicationContext)
        val item = runtime.repository.load().providerItems.singleOrNull { it.id == id }
        if (item?.retryAfter?.isAfter(Instant.now()) == true || isStopped) return@withContext Result.success()
        when (val failure = runtime.rentCast.refresh(id) { !isStopped }) {
            null -> Result.success()
            else -> if (RetirementSyncPolicy.shouldRetry(failure, runAttemptCount)) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PROPERTY = "property"
        fun schedule(context: Context, propertyId: String) {
            UUID.fromString(propertyId)
            val request = PeriodicWorkRequestBuilder<RetirementPropertySyncWorker>(RetirementSyncPolicy.propertyIntervalMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInputData(workDataOf(PROPERTY to propertyId)).setInitialDelay(RetirementSyncPolicy.propertyIntervalMillis, TimeUnit.MILLISECONDS)
                .addTag("retirement-sync-v${RetirementSyncPolicy.POLICY_VERSION}")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RetirementSyncPolicy.backoffMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("retirement-property-$propertyId", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        fun cancel(context: Context, propertyId: String) { WorkManager.getInstance(context).cancelUniqueWork("retirement-property-$propertyId") }
    }
}

class RetirementAccountSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(ITEM) ?: return@withContext Result.failure()
        if (runCatching { UUID.fromString(id) }.isFailure) return@withContext Result.failure()
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return@withContext Result.retry()
        val runtime = RetirementProviders.get(applicationContext)
        val item = runtime.repository.load().providerItems.singleOrNull { it.id == id } ?: return@withContext Result.success()
        if (item.revokedAt != null || item.retryAfter?.isAfter(Instant.now()) == true) return@withContext Result.success()
        if (isStopped) return@withContext Result.success()
        when (val failure = runtime.plaid.refresh(id) { !isStopped }) {
            null -> Result.success()
            else -> if (RetirementSyncPolicy.shouldRetry(failure, runAttemptCount)) Result.retry() else Result.success()
            // Auth/invalid data await foreground attention; periodic work remains.
        }
    }
    companion object {
        private const val ITEM = "item"
        fun schedule(context: Context, itemId: String) {
            UUID.fromString(itemId)
            val request = PeriodicWorkRequestBuilder<RetirementAccountSyncWorker>(RetirementSyncPolicy.accountIntervalMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInputData(workDataOf(ITEM to itemId)).setInitialDelay(RetirementSyncPolicy.accountIntervalMillis, TimeUnit.MILLISECONDS)
                .addTag("retirement-sync-v${RetirementSyncPolicy.POLICY_VERSION}")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RetirementSyncPolicy.backoffMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("retirement-account-$itemId", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        fun cancel(context: Context, itemId: String) { WorkManager.getInstance(context).cancelUniqueWork("retirement-account-$itemId") }
    }
}
