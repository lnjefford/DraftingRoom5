package dev.draftingroom5

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineArtworkCatalogTest {
    private val resources = listOf(File("src/main/res/drawable-nodpi"), File("app/src/main/res/drawable-nodpi"))
        .firstOrNull(File::isDirectory)
        ?: error("Android drawable resources were not found from ${File(".").absolutePath}")

    @Test
    fun catalogContainsEveryStableRoutineArtworkId() {
        assertEquals(
            setOf("generic", "dumbbell", "grip_trainer", "running_shoe", "kettlebell", "leg_day", "full_body", "push_day", "pull_day", "jump_rope", "stopwatch"),
            RoutineArtworkCatalog.entries.mapTo(linkedSetOf()) { it.storageId },
        )
        assertEquals(RoutineArtworkCatalog.entries.size, RoutineArtworkCatalog.entries.map { it.storageId }.distinct().size)
    }

    @Test
    fun everyEntryHasOptimizedCardHeaderAndPickerAssets() {
        RoutineArtworkCatalog.entries.forEach { entry ->
            RoutineArtworkCrop.entries.forEach { crop ->
                val file = resources.resolve("routine_${entry.storageId}_${crop.name.lowercase()}.webp")
                assertTrue(file.path, file.isFile)
                assertTrue("${file.path} should contain an optimized image", file.length() in 1_000..100_000)
                val signature = file.inputStream().use { it.readNBytes(12) }
                assertEquals("RIFF", signature.copyOfRange(0, 4).toString(Charsets.US_ASCII))
                assertEquals("WEBP", signature.copyOfRange(8, 12).toString(Charsets.US_ASCII))
                assertTrue(entry.resource(crop) != 0)
            }
        }
    }

    @Test
    fun missingAndBlankIdsResolveToTheGenericFallback() {
        val fallback = RoutineArtworkCatalog.resolve(RoutineArtworkCatalog.FALLBACK_ID)
        assertSame(fallback, RoutineArtworkCatalog.resolve("not_in_catalog"))
        assertSame(fallback, RoutineArtworkCatalog.resolve(""))
        assertSame(fallback, RoutineArtworkCatalog.resolve(null))
    }
}
