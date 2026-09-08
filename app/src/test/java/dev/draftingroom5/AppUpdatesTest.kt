package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test fun newerMinorReleaseIsDetected() {
        assertTrue(compareReleaseVersions("0.17.0", "0.16.1") > 0)
    }

    @Test fun numericSegmentsAreComparedInsteadOfLexicographicText() {
        assertTrue(compareReleaseVersions("0.16.10", "0.16.9") > 0)
    }

    @Test fun equivalentVersionShapesCompareEqual() {
        assertEquals(0, compareReleaseVersions("1.2", "1.2.0"))
    }
}
