package dev.draftingroom5

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

private const val MAX_GPX_BYTES = 8 * 1024 * 1024

/** Imports a GPX track or route without requesting a map or location permission. */
internal fun importGpxRoute(input: InputStream, id: String, fallbackName: String): RunRoute {
    val bytes = input.readNBytes(MAX_GPX_BYTES + 1)
    require(bytes.size <= MAX_GPX_BYTES) { "GPX file is too large." }
    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }
    val handler = GpxRouteHandler()
    factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    val routePoints = handler.routePoints
    val points = if (routePoints.size >= 2) routePoints else handler.trackPoints
    require(points.size in 2..100_000 && points.distinct().size >= 2) {
        "GPX needs a track or route with at least two distinct points."
    }
    val name = (if (routePoints.size >= 2) handler.routeName else handler.trackName)
        ?.trim()?.takeIf(String::isNotEmpty) ?: fallbackName.trim()
    require(name.isNotEmpty() && name.length <= 200) { "GPX route name is invalid." }
    val waypoints = if (routePoints.size in 2..10_000) points.indices.toList() else listOf(0, points.lastIndex)
    return RunRoute(id, 1, name, points, waypoints, emptyList()).also(::validateRunRoute)
}

private class GpxRouteHandler : DefaultHandler() {
    val trackPoints = ArrayList<RunRoutePoint>()
    val routePoints = ArrayList<RunRoutePoint>()
    var trackName: String? = null
    var routeName: String? = null
    private val path = ArrayList<String>()
    private val nameText = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        val element = localName?.ifEmpty { qName.orEmpty().substringAfter(':') }.orEmpty()
        if (path.isEmpty()) require(element == "gpx") { "File is not GPX." }
        path += element
        if (element == "name" && path.size == 3 && path[1] in listOf("trk", "rte")) nameText.clear()
        if (element == "trkpt" && path.takeLast(2) == listOf("trkseg", "trkpt")) {
            addPoint(trackPoints, attributes)
        } else if (element == "rtept" && path.takeLast(2) == listOf("rte", "rtept")) {
            addPoint(routePoints, attributes)
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (path.size == 3 && path.last() == "name" && path[1] in listOf("trk", "rte")) {
            require(nameText.length + length <= 200) { "GPX route name is too long." }
            nameText.append(ch, start, length)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (path.size == 3 && path.last() == "name") {
            if (path[1] == "trk" && trackName == null) trackName = nameText.toString()
            if (path[1] == "rte" && routeName == null) routeName = nameText.toString()
        }
        path.removeAt(path.lastIndex)
    }

    private fun addPoint(target: ArrayList<RunRoutePoint>, attributes: Attributes) {
        require(target.size < 100_000) { "GPX has too many points." }
        val latitude = attributes.getValue("lat")?.toCoordinate(-900_000_000, 900_000_000)
        val longitude = attributes.getValue("lon")?.toCoordinate(-1_800_000_000, 1_800_000_000)
        require(latitude != null && longitude != null) { "GPX point has invalid coordinates." }
        target += RunRoutePoint(latitude, longitude)
    }
}

private fun String.toCoordinate(min: Int, max: Int): Int? = runCatching {
    BigDecimal(this).movePointRight(7).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact()
        .takeIf { it in min..max }
}.getOrNull()
