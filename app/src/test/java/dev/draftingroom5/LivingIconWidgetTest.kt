package dev.draftingroom5

import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingIconWidgetTest {
    private val project = if (File("app/src/main").isDirectory) File("app") else File(".")
    private val main = project.resolve("src/main")
    private val hour = 60L * 60L * 1000L

    @Test fun rotationIsStableWithinAnHourAndCyclesThroughAllFiftyLooks() {
        assertEquals(0, LivingIconWidgetProvider.imageIndexAt(0))
        assertEquals(0, LivingIconWidgetProvider.imageIndexAt(hour - 1))
        (0L..150L).forEach { hourNumber ->
            assertEquals((hourNumber % 50).toInt(), LivingIconWidgetProvider.imageIndexAt(hourNumber * hour))
        }
        assertEquals(49, LivingIconWidgetProvider.imageIndexAt(-1))
        assertEquals(0, LivingIconWidgetProvider.imageIndexAt(50 * hour))
    }

    @Test fun providerAdvertisesOneCellAndSystemManagedHourlyUpdates() {
        val document = xml("res/xml/living_icon_widget_info.xml")
        val provider = document.documentElement
        assertEquals("1", provider.android("targetCellWidth"))
        assertEquals("1", provider.android("targetCellHeight"))
        assertEquals("40dp", provider.android("minWidth"))
        assertEquals("40dp", provider.android("minHeight"))
        assertEquals("3600000", provider.android("updatePeriodMillis"))
        assertEquals("home_screen", provider.android("widgetCategory"))
        assertEquals("@layout/living_icon_widget", provider.android("previewLayout"))
        assertEquals("@drawable/widget_01_dumbbell", provider.android("previewImage"))
    }

    @Test fun wholeTileOpensTheNormalActivityAndHasAnAccessibleLabel() {
        val layout = xml("res/layout/living_icon_widget.xml")
        assertEquals("@+id/living_icon_tile", layout.documentElement.android("id"))
        val image = layout.getElementsByTagName("ImageView").item(0)
        assertEquals("match_parent", image.android("layout_width"))
        assertEquals("match_parent", image.android("layout_height"))
        assertEquals("fitCenter", image.android("scaleType"))
        assertEquals("@string/living_icon_widget_description", layout.documentElement.android("contentDescription"))
        assertEquals("no", image.android("importantForAccessibility"))
        val manifest = xml("AndroidManifest.xml")
        val receiver = manifest.getElementsByTagName("receiver").item(0)
        assertEquals(".LivingIconWidgetProvider", receiver.android("name"))
        assertEquals(":living_icon", receiver.android("process"))
        val source = main.resolve("java/dev/draftingroom5/LivingIconWidgetProvider.kt").readText()
        assertTrue(source.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(source.contains("PendingIntent.getActivity("))
        assertTrue(source.contains("setOnClickPendingIntent(R.id.living_icon_tile, launch)"))
        assertTrue(source.contains("setImageViewResource(R.id.living_icon_image, image)"))
        assertTrue(!source.contains("circularArtwork"))
        assertTrue(source.contains("if (ids.isNotEmpty()) update("))
        assertTrue(source.contains("onAppWidgetOptionsChanged("))
        assertTrue(source.contains("onRestored("))
        assertTrue(source.contains("Intent.ACTION_BOOT_COMPLETED"))
        assertTrue(source.contains("Intent.ACTION_TIME_CHANGED"))
        assertTrue(source.contains("Intent.ACTION_MY_PACKAGE_REPLACED"))
    }

    @Test fun exactlyFiftyApprovedRuntimeImagesAreSmallAndDistinct() {
        val resourceSource = main.resolve("java/dev/draftingroom5/LivingIconWidgetProvider.kt").readText()
        val names = Regex("R\\.drawable\\.(widget_\\d{2}_[a-z0-9_]+)")
            .findAll(resourceSource).map { it.groupValues[1] }.toList()
        val approved = (1..50).map { number ->
            val prefix = "widget_%02d_".format(number)
            names.single { it.startsWith(prefix) }
        }
        assertEquals(50, names.size)
        assertEquals(50, names.distinct().size)
        assertEquals(approved, names)
        val runtimeFiles = main.resolve("res/drawable-nodpi").listFiles()!!
            .filter { it.name.matches(Regex("widget_\\d{2}_.+\\.webp")) }
        assertEquals(approved.map { "$it.webp" }.toSet(), runtimeFiles.map { it.name }.toSet())
        val images = approved.map { main.resolve("res/drawable-nodpi/$it.webp").readBytes() }
        val sha256 = MessageDigest.getInstance("SHA-256")
        assertEquals(50, images.map { sha256.digest(it).toList() }.distinct().size)
        images.forEach { bytes ->
            assertTrue(bytes.size in 1..25_000)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            assertEquals("WEBP", String(bytes.copyOfRange(8, 12)))
        }
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(main.resolve(path))

    private fun org.w3c.dom.Node.android(name: String): String =
        attributes.getNamedItem("android:$name").nodeValue
}
