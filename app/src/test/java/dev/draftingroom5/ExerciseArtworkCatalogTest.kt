package dev.draftingroom5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseArtworkCatalogTest {
    private val resources = listOf(File("src/main/res/drawable-nodpi"), File("app/src/main/res/drawable-nodpi"))
        .firstOrNull(File::isDirectory)
        ?: error("Android drawable resources were not found from ${File(".").absolutePath}")

    @Test
    fun catalogContainsEveryStableExerciseArtworkId() {
        assertEquals(
            setOf("generic", "dead_hang", "farmers_walk", "grip_hold", "wrist_curl", "reverse_wrist_curl", "finger_extension", "wrist_rotation"),
            ExerciseArtworkCatalog.entries.mapTo(linkedSetOf()) { it.storageId },
        )
        assertEquals(ExerciseArtworkCatalog.entries.size, ExerciseArtworkCatalog.entries.map { it.storageId }.distinct().size)
    }

    @Test
    fun everyEntryHasSeparateOptimizedListAndHeaderAssets() {
        ExerciseArtworkCatalog.entries.forEach { entry ->
            ExerciseArtworkCrop.entries.forEach { crop ->
                val file = resources.resolve("exercise_${entry.storageId}_${crop.name.lowercase()}.webp")
                assertTrue(file.path, file.isFile)
                assertTrue("${file.path} should contain an optimized image", file.length() in 1_000..250_000)
                val signature = file.inputStream().use { it.readNBytes(12) }
                assertEquals("RIFF", signature.copyOfRange(0, 4).toString(Charsets.US_ASCII))
                assertEquals("WEBP", signature.copyOfRange(8, 12).toString(Charsets.US_ASCII))
                assertTrue(entry.resource(crop) != 0)
            }
        }
    }

    @Test
    fun missingBlankAndNullIdsResolveToTheGenericPair() {
        val fallback = ExerciseArtworkCatalog.resolve(ExerciseArtworkCatalog.FALLBACK_ID)
        assertSame(fallback, ExerciseArtworkCatalog.resolve("not_in_catalog"))
        assertSame(fallback, ExerciseArtworkCatalog.resolve(""))
        assertSame(fallback, ExerciseArtworkCatalog.resolve(null))
    }
}
