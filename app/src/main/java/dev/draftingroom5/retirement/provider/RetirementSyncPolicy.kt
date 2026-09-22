package dev.draftingroom5.retirement.provider

import java.util.concurrent.TimeUnit

/** Non-secret scheduling contract shared by workers and host tests. Android may run later. */
internal object RetirementSyncPolicy {
    const val POLICY_VERSION = 1
    const val MAX_TRANSIENT_RETRIES = 3
    val backoffMillis: Long = TimeUnit.MINUTES.toMillis(30)
    val accountIntervalMillis: Long = TimeUnit.DAYS.toMillis(1)
    val propertyIntervalMillis: Long = TimeUnit.DAYS.toMillis(7)

    fun shouldRetry(failure: ProviderFailure, runAttemptCount: Int): Boolean =
        failure in setOf(ProviderFailure.OFFLINE, ProviderFailure.UNAVAILABLE, ProviderFailure.CONFLICT) &&
            runAttemptCount < MAX_TRANSIENT_RETRIES
}

/** Prevents overlapping foreground/worker calls for one remote source in this process. */
internal class ProviderOperationGate {
    private val lock = Any()
    private val active = mutableSetOf<String>()

    fun <T> tryRun(key: String, busy: T, operation: () -> T): T {
        require(key.isNotBlank())
        synchronized(lock) { if (!active.add(key)) return busy }
        return try { operation() } finally { synchronized(lock) { active.remove(key) } }
    }

    companion object {
        val process = ProviderOperationGate()
    }
}
