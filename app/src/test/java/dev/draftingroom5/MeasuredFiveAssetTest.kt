package dev.draftingroom5

import java.io.File
import kotlin.math.hypot
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredFiveAssetTest {
    private val resources = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull(File::isDirectory)
        ?: error("Android resources were not found from ${File(".").absolutePath}")

    @Test
    fun fullColorAndMonochromeUseTheSameSafeMeasuredGeometry() {
        val fullColor = pathData("drawable/measured_five_foreground.xml")
        val monochrome = pathData("drawable/measured_five_monochrome.xml")

        assertEquals(fullColor, monochrome)
        assertTrue(fullColor.first().contains("M21,24h66"))
        assertTrue(fullColor.last().contains("M44,29h4v6"))
        assertTrue(fullColor.last().contains("M53,29h4v6"))
        assertTrue(fullColor.last().contains("M62,29h4v6"))
    }

    @Test
    fun launcherArtworkFitsTheGuaranteedAdaptiveIconSafeZone() {
        val fullColorScale = launcherSafeZoneScale("drawable/measured_five_foreground.xml")
        val monochromeScale = launcherSafeZoneScale("drawable/measured_five_monochrome.xml")

        assertEquals(fullColorScale, monochromeScale, 0.0)

        // The 66-unit source frame remains at least 48 units after scaling, while its
        // furthest real corner (an overshoot endpoint, not the empty 21,21 corner)
        // remains inside Android's guaranteed 66-unit-diameter safe circle.
        assertTrue("launcher mark must remain at least 48 units", 66.0 * fullColorScale >= 48.0)
        assertTrue(
            "launcher frame corners must fit the 33-unit safe radius",
            hypot(33.0, 30.0) * fullColorScale < 33.0,
        )
    }

    @Test
    fun everyLauncherVariantReferencesVectorGeometry() {
        val launcherFiles = listOf(
            "mipmap/ic_launcher.xml",
            "mipmap/ic_launcher_round.xml",
            "mipmap-anydpi-v26/ic_launcher.xml",
            "mipmap-anydpi-v26/ic_launcher_round.xml",
            "mipmap-anydpi-v33/ic_launcher.xml",
            "mipmap-anydpi-v33/ic_launcher_round.xml",
        )
        launcherFiles.forEach { relativePath ->
            assertTrue(relativePath, resources.resolve(relativePath).readText().contains("measured_five_foreground"))
        }
        assertTrue(resources.resolve("mipmap/ic_launcher_round.xml").readText().contains("android:shape=\"oval\""))
        assertFalse(resources.walkTopDown().any { it.isFile && it.name.startsWith("ic_launcher") && it.extension == "png" })
    }

    @Test
    fun notificationHasASeparateHeavierMonochromeSilhouette() {
        val notification = resources.resolve("drawable/ic_notification.xml").readText()
        assertTrue(notification.contains("android:width=\"24dp\""))
        assertTrue(notification.contains("M20,23h68v4"))
        assertFalse(notification.contains("#FFFFC66D"))
    }

    @Test
    fun maskReviewArtifactCoversEveryRequiredShape() {
        val repository = if (File("docs").isDirectory) File(".") else File("..")
        val artifact = repository.resolve("docs/reviews/DR5-033/icon-mask-review.png")
        val renderer = repository.resolve("docs/reviews/DR5-033/render_launcher_mask_review.py").readText()
        val png = artifact.readBytes()

        assertEquals(1120, pngInt(png, 16))
        assertEquals(680, pngInt(png, 20))
        listOf("Circle", "Squircle", "Rounded square", "Tight mask").forEach { mask ->
            assertTrue(mask, renderer.contains("\"$mask\""))
        }
    }

    private fun pathData(relativePath: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources.resolve(relativePath))
        return (0 until document.getElementsByTagName("path").length).map { index ->
            document.getElementsByTagName("path").item(index).attributes.getNamedItem("android:pathData").nodeValue
        }
    }

    private fun pngInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun launcherSafeZoneScale(relativePath: String): Double {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources.resolve(relativePath))
        val groups = document.getElementsByTagName("group")
        val safeZone = (0 until groups.length)
            .map(groups::item)
            .single { it.attributes.getNamedItem("android:name")?.nodeValue == "launcher_safe_zone" }
        val scaleX = safeZone.attributes.getNamedItem("android:scaleX").nodeValue.toDouble()
        val scaleY = safeZone.attributes.getNamedItem("android:scaleY").nodeValue.toDouble()
        assertEquals(scaleX, scaleY, 0.0)
        assertEquals("54", safeZone.attributes.getNamedItem("android:pivotX").nodeValue)
        assertEquals("54", safeZone.attributes.getNamedItem("android:pivotY").nodeValue)
        return scaleX
    }
}
