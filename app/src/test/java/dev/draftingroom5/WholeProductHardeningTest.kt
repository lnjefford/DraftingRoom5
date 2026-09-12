package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeProductHardeningTest {
    @Test fun everyRequiredExceptionalStateHasOneStableFixture() {
        val names = HardeningState.entries.map { it.fixtureName }
        assertEquals(names.size, names.toSet().size)
        assertEquals(18, names.size)
        names.forEach { assertNotNull(hardeningStateForFixture(it)) }
    }

    @Test fun everyFailureOrDestructiveStateHasSafeRecoveryCopy() {
        val informational = setOf(HardeningState.LOADING, HardeningState.EMPTY, HardeningState.APP_PICKER_LOADING)
        HardeningState.entries.filterNot(informational::contains).forEach { state ->
            assertTrue("${state.name} needs recovery guidance", !state.recovery.isNullOrBlank())
        }
    }

    @Test fun unknownFixtureCannotSilentlySelectAnotherState() {
        assertEquals(null, hardeningStateForFixture("Unknown"))
    }
}
