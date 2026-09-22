package dev.draftingroom5.retirement.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RetirementSyncPolicyTest {
    @Test fun onlyBoundedTransientFailuresRetry() {
        listOf(ProviderFailure.OFFLINE, ProviderFailure.UNAVAILABLE, ProviderFailure.CONFLICT).forEach {
            assertTrue(RetirementSyncPolicy.shouldRetry(it, 0))
            assertTrue(RetirementSyncPolicy.shouldRetry(it, 2))
            assertFalse(RetirementSyncPolicy.shouldRetry(it, 3))
        }
        ProviderFailure.entries.filterNot {
            it in setOf(ProviderFailure.OFFLINE, ProviderFailure.UNAVAILABLE, ProviderFailure.CONFLICT)
        }.forEach { assertFalse(it.name, RetirementSyncPolicy.shouldRetry(it, 0)) }
        assertEquals(TimeUnit.MINUTES.toMillis(30), RetirementSyncPolicy.backoffMillis)
        assertEquals(TimeUnit.DAYS.toMillis(1), RetirementSyncPolicy.accountIntervalMillis)
        assertEquals(TimeUnit.DAYS.toMillis(7), RetirementSyncPolicy.propertyIntervalMillis)
    }

    @Test fun sameSourceCannotOverlapButDifferentSourcesCan() {
        val gate = ProviderOperationGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val first = Thread {
            gate.tryRun("plaid:item", -1) {
                calls.incrementAndGet()
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                1
            }
        }
        first.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertEquals(-1, gate.tryRun("plaid:item", -1) { calls.incrementAndGet() })
        assertEquals(2, gate.tryRun("rentcast:item", -1) { calls.incrementAndGet() })
        release.countDown()
        first.join(2_000)
        assertEquals(2, calls.get())
        assertEquals(3, gate.tryRun("plaid:item", -1) { calls.incrementAndGet() })
    }
}
