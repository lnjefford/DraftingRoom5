package dev.draftingroom5

import java.io.File
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

    private fun pathData(relativePath: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resources.resolve(relativePath))
        return (0 until document.getElementsByTagName("path").length).map { index ->
            document.getElementsByTagName("path").item(index).attributes.getNamedItem("android:pathData").nodeValue
        }
    }
}
