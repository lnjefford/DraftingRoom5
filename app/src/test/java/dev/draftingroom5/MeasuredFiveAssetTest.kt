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
    fun roundLauncherKeepsTheMeasuredFiveAndHatchedRingInBothModes() {
        val fullColor = pathData("drawable/launcher_five_foreground.xml")
        val monochrome = pathData("drawable/launcher_five_monochrome.xml")
        val inAppMark = pathData("drawable/measured_five_foreground.xml")

        assertEquals(fullColor.take(3), monochrome.take(3))
        assertEquals(inAppMark.last(), fullColor.last())
        assertEquals(fullColor.last(), monochrome.last())
        assertTrue(fullColor[3].contains("M44,29h4v6"))
        assertFalse(fullColor.any { it.contains("M21,24h66") })
        assertTrue(resources.resolve("values/colors.xml").readText().contains("<color name=\"launcher_background\">#000000</color>"))
    }

    @Test
    fun launcherArtworkFitsTheGuaranteedAdaptiveIconSafeZone() {
        val fullColorScale = groupScale("drawable/launcher_five_foreground.xml", "launcher_safe_zone")
        val monochromeScale = groupScale("drawable/launcher_five_monochrome.xml", "launcher_safe_zone")
        val numeralScale = groupScale("drawable/launcher_five_foreground.xml", "launcher_numeral")

        assertEquals(1.0, fullColorScale, 0.0)
        assertEquals(fullColorScale, monochromeScale, 0.0)
        assertEquals(numeralScale, groupScale("drawable/launcher_five_monochrome.xml", "launcher_numeral"), 0.0)
        assertTrue("the 5 must be wider than the previous framed version", 47.0 * numeralScale > 35.0)
        // The complete outer stroke ends at radius 33; the numeral and hatching sit inside it.
        val vector = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resources.resolve("drawable/launcher_five_foreground.xml"))
        val outerRing = vector.getElementsByTagName("path").item(0).attributes
        assertTrue(outerRing.getNamedItem("android:pathData").nodeValue.startsWith("M86,54a32,32"))
        val ringStroke = outerRing.getNamedItem("android:strokeWidth").nodeValue.toDouble()
        assertTrue(32.0 + ringStroke / 2.0 <= 33.0)
        val fiveTopLeftX = 54.0 + (36.0 - 54.0) * numeralScale - 1.0
        val fiveTopLeftY = 54.0 + (29.0 - 54.0) * numeralScale - 2.0
        assertTrue(hypot(fiveTopLeftX - 54.0, fiveTopLeftY - 54.0) < 29.0)
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
            assertTrue(relativePath, resources.resolve(relativePath).readText().contains("launcher_five_foreground"))
        }
        assertTrue(resources.resolve("mipmap/ic_launcher_round.xml").readText().contains("android:shape=\"oval\""))
        listOf("mipmap-anydpi-v33/ic_launcher.xml", "mipmap-anydpi-v33/ic_launcher_round.xml").forEach { relativePath ->
            assertTrue(resources.resolve(relativePath).readText().contains("launcher_five_monochrome"))
        }
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
        val artifact = repository.resolve("docs/reviews/launcher-h2/mask-review.png")
        val renderer = repository.resolve("docs/reviews/launcher-h2/render_mask_review.py").readText()
        val png = artifact.readBytes()

        assertEquals(1120, pngInt(png, 16))
        assertEquals(680, pngInt(png, 20))
        listOf("Circle", "Squircle", "Rounded square", "Tight mask").forEach { mask ->
            assertTrue(mask, renderer.contains("\"$mask\""))
        }
        assertTrue(renderer.contains("icon(\"H2\""))
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

    private fun groupScale(relativePath: String, name: String): Double {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources.resolve(relativePath))
        val groups = document.getElementsByTagName("group")
        val safeZone = (0 until groups.length)
            .map(groups::item)
            .single { it.attributes.getNamedItem("android:name")?.nodeValue == name }
        val scaleX = safeZone.attributes.getNamedItem("android:scaleX").nodeValue.toDouble()
        val scaleY = safeZone.attributes.getNamedItem("android:scaleY").nodeValue.toDouble()
        assertEquals(scaleX, scaleY, 0.0)
        assertEquals("54", safeZone.attributes.getNamedItem("android:pivotX").nodeValue)
        assertEquals("54", safeZone.attributes.getNamedItem("android:pivotY").nodeValue)
        return scaleX
    }
}
