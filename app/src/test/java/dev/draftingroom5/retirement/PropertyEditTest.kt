package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.ui.propertyEditRevision
import org.junit.Assert.*
import org.junit.Test

class PropertyEditTest {
    @Test fun ownershipMortgageAndRefreshEditsAreValidatedTogetherAndKeepIdentity() {
        val current = retirementFixture().properties.single().currentRevision.copy(automaticValueEnabled = true,
            providerPropertyId = "synthetic-property", credentialProfileId = "synthetic-profile")
        val revision = propertyEditRevision(current, current.address, "100000", "1000", "5.25", "120", "25.50", Owner.SELF, true, false, NOW)
        assertEquals(2550, revision.ownershipBps)
        assertEquals(525, revision.mortgage.annualRateBps)
        assertEquals(120, revision.mortgage.remainingMonths)
        assertEquals(Money(100000), revision.mortgage.payment)
        assertFalse(revision.automaticValueEnabled)
        assertEquals(current.providerPropertyId, revision.providerPropertyId)
        assertEquals(current.id, revision.previousRevisionId)
        assertThrows(IllegalArgumentException::class.java) {
            propertyEditRevision(current, current.address, "100000", "1000", "5", "120", "101", Owner.SELF, true, true)
        }
    }
}
