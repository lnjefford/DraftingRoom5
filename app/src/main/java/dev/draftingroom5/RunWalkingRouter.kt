package dev.draftingroom5

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HeiGIT's current openrouteservice endpoint; the user supplies their own account key. */
internal const val RUN_ROUTING_ACCOUNT_URL = "https://account.heigit.org/"
private const val WALKING_ROUTE_URL = "https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson"

internal data class WalkingRoute(val points: List<RunRoutePoint>, val waypointIndices: List<Int>)

internal class WalkingRouteException(val explanation: String) : RuntimeException(explanation) {
    override fun fillInStackTrace(): Throwable = this
}

internal fun parseWalkingRoute(body: String): WalkingRoute {
    val feature = JSONObject(body).getJSONArray("features").getJSONObject(0)
    val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
    require(coordinates.length() in 2..100_000)
    val points = (0 until coordinates.length()).map { index ->
        val pair = coordinates.getJSONArray(index)
        require(pair.length() >= 2)
        val longitude = pair.getDouble(0)
        val latitude = pair.getDouble(1)
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        RunRoutePoint((latitude * 10_000_000).toInt(), (longitude * 10_000_000).toInt())
    }
    val indices = feature.getJSONObject("properties").getJSONArray("way_points")
        .let { array -> (0 until array.length()).map(array::getInt) }
    require(indices.size in 2..50 && indices == indices.distinct().sorted())
    require(indices.first() == 0 && indices.last() == points.lastIndex)
    return WalkingRoute(points, indices)
}

internal fun requestWalkingRoute(anchors: List<RunRoutePoint>, apiKey: String): WalkingRoute {
    require(anchors.size in 2..50 && apiKey.isNotBlank())
    val request = JSONObject().put("coordinates", JSONArray().apply {
        anchors.forEach { point ->
            put(JSONArray().put(point.longitudeE7 / 10_000_000.0).put(point.latitudeE7 / 10_000_000.0))
        }
    }).put("instructions", false)
    val connection = (URL(WALKING_ROUTE_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Authorization", apiKey)
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/geo+json, application/json")
    }
    return try {
        connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
        when (connection.responseCode) {
            200 -> try {
                val body = connection.inputStream.use { stream ->
                    val bytes = stream.readNBytes(8 * 1024 * 1024 + 1)
                    require(bytes.size <= 8 * 1024 * 1024)
                    String(bytes, Charsets.UTF_8)
                }
                parseWalkingRoute(body).also { require(it.waypointIndices.size == anchors.size) }
            } catch (_: Exception) { throw WalkingRouteException("The routing service returned an unusable route. Try different points.") }
            401, 403 -> throw WalkingRouteException("Routing key rejected. Check your HeiGIT account key.")
            404, 400, 422 -> throw WalkingRouteException("No walking path found between these points. Try nearby paths.")
            429 -> throw WalkingRouteException("Routing limit reached. Try again after your account quota resets.")
            else -> throw WalkingRouteException("Routing is unavailable right now. Try again later.")
        }
    } catch (error: WalkingRouteException) { throw error }
    catch (_: Exception) { throw WalkingRouteException("Couldn’t reach the routing service. Check your connection.") }
    finally { connection.disconnect() }
}
