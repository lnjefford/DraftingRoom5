package dev.draftingroom5.retirement

import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.ui.accountDataLoadPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetirementAccountsPresentationTest {
    @Test
    fun successfulReloadReplacesCachedDataAndClearsAnEarlierWarning() {
        val cached = RetirementState(generation = 1)
        val refreshed = RetirementState(generation = 2)

        val presentation = accountDataLoadPresentation(cached, Result.success(refreshed), userUnlocked = true)

        assertEquals(refreshed, presentation.state)
        assertNull(presentation.message)
    }

    @Test
    fun failedReloadKeepsCachedDataWithoutBlamingAnUnlockedPhone() {
        val cached = retirementFixture()

        val presentation = accountDataLoadPresentation(
            cached,
            Result.failure(IllegalStateException("Database read failed")),
            userUnlocked = true,
        )

        assertEquals(cached, presentation.state)
        assertEquals("Account data couldn't be loaded. Try again.", presentation.message)
    }

    @Test
    fun lockedPhoneGetsTheSpecificUnlockGuidance() {
        val presentation = accountDataLoadPresentation(
            RetirementState(),
            Result.failure(IllegalStateException("Credential-protected storage is locked")),
            userUnlocked = false,
        )

        assertEquals("Account data is unavailable until you unlock the phone.", presentation.message)
    }
}
