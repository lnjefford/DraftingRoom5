package dev.draftingroom5.watch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val WATCH_RUN_CATALOG_PATH = "/draftingroom5/phone/run-catalog"
const val WATCH_RUN_RESULT_PATH_PREFIX = "/draftingroom5/watch/run-result/"
const val WATCH_RUN_ACK_PATH_PREFIX = "/draftingroom5/phone/run-ack/"
const val WATCH_RUN_ASSET_KEY = "payload"
private const val RUN_PROTOCOL = 1
private const val MAX_RUN_PAYLOAD = 16 * 1024 * 1024

data class WatchRunInterval(val kind: String, val durationSeconds: Int)
data class WatchRunPoint(val latitudeE7: Int, val longitudeE7: Int)
data class WatchRunCue(val pointIndex: Int, val kind: String, val instruction: String)
data class WatchRunRoute(val id: String, val name: String, val points: List<WatchRunPoint>, val cues: List<WatchRunCue>)
data class WatchRunPlan(
    val routineId: String,
    val routineName: String,
    val scheduleEntryId: String,
    val scheduledDate: String,
    val effectiveDate: String,
    val intervals: List<WatchRunInterval>,
    val preferredRouteId: String?,
)
data class WatchRunCatalog(
    val generation: Long,
    val plans: List<WatchRunPlan>,
    val routes: List<WatchRunRoute>,
    val lastRouteId: String?,
    val hapticsEnabled: Boolean = true,
)
data class WatchRunSample(val point: WatchRunPoint, val recordedAtMillis: Long, val accuracyMeters: Int)
data class WatchRunResult(
    val id: String,
    val plan: WatchRunPlan,
    val route: WatchRunRoute?,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val elapsedMillis: Long,
    val distanceMeters: Int,
    val samples: List<WatchRunSample>,
)

data class WatchRunCapture(
    val id: String,
    val plan: WatchRunPlan,
    val route: WatchRunRoute?,
    val startedAtMillis: Long,
    val elapsedBeforeResumeMillis: Long,
    val runningSinceMillis: Long?,
    val distanceMeters: Int,
    val samples: List<WatchRunSample>,
    val completedAtMillis: Long? = null,
    val announcedIntervalIndex: Int = 0,
) {
    val isRunning: Boolean get() = runningSinceMillis != null && completedAtMillis == null
    fun elapsedMillis(now: Long) = elapsedBeforeResumeMillis + (runningSinceMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun intervalAt(now: Long): Pair<Int, WatchRunInterval>? {
        val elapsed = elapsedMillis(now) / 1_000
        var end = 0L
        plan.intervals.forEachIndexed { index, interval ->
            end += interval.durationSeconds
            if (elapsed < end) return index to interval
        }
        return null
    }
    fun pause(now: Long) = if (!isRunning) this else copy(elapsedBeforeResumeMillis = elapsedMillis(now), runningSinceMillis = null)
    fun resume(now: Long) = if (completedAtMillis != null || isRunning) this else copy(runningSinceMillis = now.coerceAtLeast(startedAtMillis))
    fun finish(now: Long) = if (completedAtMillis != null) this else pause(now).copy(completedAtMillis = now.coerceAtLeast(startedAtMillis))
    fun record(sample: WatchRunSample): WatchRunCapture {
        if (!isRunning || samples.size >= 10_000 || sample.accuracyMeters !in 0..50 ||
            !sample.point.isValid() ||
            sample.recordedAtMillis < startedAtMillis || sample.recordedAtMillis <= (samples.lastOrNull()?.recordedAtMillis ?: 0)) return this
        val previous = samples.lastOrNull()
        val distance = previous?.let { watchDistanceMeters(it.point, sample.point) } ?: 0.0
        val seconds = previous?.let { (sample.recordedAtMillis - it.recordedAtMillis) / 1_000.0 } ?: 0.0
        if (previous != null && (distance > 12 * seconds + 20 || (distance < 2 && seconds < 10))) return this
        return copy(distanceMeters = (distanceMeters + distance.toInt()).coerceAtMost(1_000_000), samples = samples + sample)
    }
    fun result(): WatchRunResult? = completedAtMillis?.let {
        WatchRunResult(id, plan, route, startedAtMillis, it, elapsedBeforeResumeMillis, distanceMeters, samples)
    }
    fun routePosition(): Pair<Int, Int>? {
        val route = route ?: return null
        val current = samples.lastOrNull()?.point ?: return null
        val stride = (route.points.size / 2_000).coerceAtLeast(1)
        val coarse = route.points.indices.step(stride).minByOrNull { watchDistanceMeters(current, route.points[it]) } ?: return null
        val nearest = ((coarse - stride).coerceAtLeast(0)..(coarse + stride).coerceAtMost(route.points.lastIndex))
            .minByOrNull { watchDistanceMeters(current, route.points[it]) } ?: coarse
        return nearest to watchDistanceMeters(current, route.points[nearest]).toInt()
    }
}

fun encodeWatchRunCapture(capture: WatchRunCapture): ByteArray = JSONObject().apply {
    put("protocol", RUN_PROTOCOL)
    put("id", capture.id)
    put("plan", capture.plan.toJson())
    put("route", capture.route?.toJson() ?: JSONObject.NULL)
    put("startedAtMillis", capture.startedAtMillis)
    put("elapsedBeforeResumeMillis", capture.elapsedBeforeResumeMillis)
    put("runningSinceMillis", capture.runningSinceMillis ?: JSONObject.NULL)
    put("distanceMeters", capture.distanceMeters)
    put("samples", JSONArray().apply { capture.samples.forEach { sample ->
        put(JSONArray().put(sample.point.latitudeE7).put(sample.point.longitudeE7)
            .put(sample.recordedAtMillis).put(sample.accuracyMeters))
    } })
    put("completedAtMillis", capture.completedAtMillis ?: JSONObject.NULL)
    put("announcedIntervalIndex", capture.announcedIntervalIndex)
}.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_RUN_PAYLOAD) }

fun decodeWatchRunCapture(bytes: ByteArray): WatchRunCapture {
    require(bytes.size <= MAX_RUN_PAYLOAD)
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getInt("protocol") == RUN_PROTOCOL)
    val samples = root.getJSONArray("samples")
    require(samples.length() <= 10_000)
    val capture = WatchRunCapture(
        root.getString("id").also(::validateRunId), decodePlan(root.getJSONObject("plan")),
        if (root.isNull("route")) null else decodeRoute(root.getJSONObject("route")),
        root.getLong("startedAtMillis"), root.getLong("elapsedBeforeResumeMillis"),
        if (root.isNull("runningSinceMillis")) null else root.getLong("runningSinceMillis"),
        root.getInt("distanceMeters"),
        List(samples.length()) { index ->
            val sample = samples.getJSONArray(index)
            require(sample.length() == 4)
            WatchRunSample(WatchRunPoint(sample.getInt(0), sample.getInt(1)), sample.getLong(2), sample.getInt(3))
        },
        if (root.isNull("completedAtMillis")) null else root.getLong("completedAtMillis"),
        root.getInt("announcedIntervalIndex"),
    )
    require(capture.startedAtMillis >= 0 && capture.elapsedBeforeResumeMillis >= 0)
    require(capture.runningSinceMillis == null || capture.runningSinceMillis >= capture.startedAtMillis)
    require(capture.completedAtMillis == null || (capture.completedAtMillis >= capture.startedAtMillis && capture.runningSinceMillis == null))
    require(capture.distanceMeters in 0..1_000_000)
    require(capture.announcedIntervalIndex in 0..capture.plan.intervals.size)
    require(capture.samples.all { it.point.isValid() && it.accuracyMeters in 0..50 &&
        it.recordedAtMillis >= capture.startedAtMillis &&
        (capture.completedAtMillis == null || it.recordedAtMillis <= capture.completedAtMillis) })
    return capture
}

private fun watchDistanceMeters(a: WatchRunPoint, b: WatchRunPoint): Double {
    val lat1 = Math.toRadians(a.latitudeE7 / 10_000_000.0)
    val lat2 = Math.toRadians(b.latitudeE7 / 10_000_000.0)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians((b.longitudeE7.toLong() - a.longitudeE7.toLong()) / 10_000_000.0)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 12_742_000.0 * asin(min(1.0, sqrt(h)))
}

fun encodeWatchRunCatalog(catalog: WatchRunCatalog): ByteArray = JSONObject().apply {
    put("protocol", RUN_PROTOCOL)
    put("generation", catalog.generation)
    put("plans", JSONArray().apply { catalog.plans.forEach { put(it.toJson()) } })
    put("routes", JSONArray().apply { catalog.routes.forEach { put(it.toJson()) } })
    put("lastRouteId", catalog.lastRouteId ?: JSONObject.NULL)
    put("hapticsEnabled", catalog.hapticsEnabled)
}.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_RUN_PAYLOAD) }

fun decodeWatchRunCatalog(bytes: ByteArray): WatchRunCatalog {
    require(bytes.size <= MAX_RUN_PAYLOAD)
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getInt("protocol") == RUN_PROTOCOL)
    val plans = root.getJSONArray("plans").runObjects(::decodePlan)
    val routes = root.getJSONArray("routes").runObjects(::decodeRoute)
    require(plans.size <= 512 && routes.size <= 256)
    require(routes.map { it.id }.distinct().size == routes.size)
    val last = root.runNullableString("lastRouteId")
    require(last == null || routes.any { it.id == last })
    return WatchRunCatalog(root.getLong("generation").also { require(it >= 0) }, plans, routes, last,
        root.getBoolean("hapticsEnabled"))
}

fun encodeWatchRunResult(result: WatchRunResult): ByteArray = JSONObject().apply {
    put("protocol", RUN_PROTOCOL)
    put("id", result.id)
    put("plan", result.plan.toJson())
    put("route", result.route?.toJson() ?: JSONObject.NULL)
    put("startedAtMillis", result.startedAtMillis)
    put("completedAtMillis", result.completedAtMillis)
    put("elapsedMillis", result.elapsedMillis)
    put("distanceMeters", result.distanceMeters)
    put("samples", JSONArray().apply { result.samples.forEach { sample ->
        put(JSONArray().put(sample.point.latitudeE7).put(sample.point.longitudeE7)
            .put(sample.recordedAtMillis).put(sample.accuracyMeters))
    } })
}.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_RUN_PAYLOAD) }

fun decodeWatchRunResult(bytes: ByteArray): WatchRunResult {
    require(bytes.size <= MAX_RUN_PAYLOAD)
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getInt("protocol") == RUN_PROTOCOL)
    val samples = root.getJSONArray("samples")
    require(samples.length() <= 10_000)
    val result = WatchRunResult(
        id = root.getString("id").also(::validateRunId),
        plan = decodePlan(root.getJSONObject("plan")),
        route = if (root.isNull("route")) null else decodeRoute(root.getJSONObject("route")),
        startedAtMillis = root.getLong("startedAtMillis"),
        completedAtMillis = root.getLong("completedAtMillis"),
        elapsedMillis = root.getLong("elapsedMillis"),
        distanceMeters = root.getInt("distanceMeters"),
        samples = List(samples.length()) { index ->
            val sample = samples.getJSONArray(index)
            require(sample.length() == 4)
            WatchRunSample(WatchRunPoint(sample.getInt(0), sample.getInt(1)), sample.getLong(2), sample.getInt(3))
        },
    )
    require(result.startedAtMillis >= 0 && result.completedAtMillis >= result.startedAtMillis)
    require(result.elapsedMillis in 0..(result.completedAtMillis - result.startedAtMillis))
    require(result.distanceMeters in 0..1_000_000)
    require(result.samples.all { it.point.isValid() && it.recordedAtMillis >= result.startedAtMillis &&
        it.recordedAtMillis <= result.completedAtMillis && it.accuracyMeters in 0..50 })
    require(result.samples.zipWithNext().all { (a, b) -> a.recordedAtMillis <= b.recordedAtMillis })
    return result
}

private fun WatchRunPlan.toJson() = JSONObject().apply {
    put("routineId", routineId)
    put("routineName", routineName)
    put("scheduleEntryId", scheduleEntryId)
    put("scheduledDate", scheduledDate)
    put("effectiveDate", effectiveDate)
    put("intervals", JSONArray().apply { intervals.forEach {
        put(JSONObject().put("kind", it.kind).put("durationSeconds", it.durationSeconds))
    } })
    put("preferredRouteId", preferredRouteId ?: JSONObject.NULL)
}

private fun decodePlan(root: JSONObject): WatchRunPlan {
    val intervals = root.getJSONArray("intervals")
    require(intervals.length() in 1..512)
    return WatchRunPlan(
        root.getString("routineId").also(::validateRunId),
        root.getString("routineName").also { require(it.isNotBlank() && it.length <= 200) },
        root.getString("scheduleEntryId").also(::validateRunId),
        root.getString("scheduledDate").also { java.time.LocalDate.parse(it) },
        root.getString("effectiveDate").also { java.time.LocalDate.parse(it) },
        List(intervals.length()) { index ->
            val value = intervals.getJSONObject(index)
            WatchRunInterval(value.getString("kind").also { require(it == "WALK" || it == "RUN") },
                value.getInt("durationSeconds").also { require(it in 1..86_400) })
        },
        root.runNullableString("preferredRouteId")?.also(::validateRunId),
    )
}

private fun WatchRunRoute.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("points", JSONArray().apply { points.forEach { put(JSONArray().put(it.latitudeE7).put(it.longitudeE7)) } })
    put("cues", JSONArray().apply { cues.forEach {
        put(JSONObject().put("pointIndex", it.pointIndex).put("kind", it.kind).put("instruction", it.instruction))
    } })
}

private fun decodeRoute(root: JSONObject): WatchRunRoute {
    val points = root.getJSONArray("points")
    require(points.length() in 2..100_000)
    val decodedPoints = List(points.length()) { index ->
        val pair = points.getJSONArray(index)
        require(pair.length() == 2)
        WatchRunPoint(pair.getInt(0).also { require(it in -900_000_000..900_000_000) },
            pair.getInt(1).also { require(it in -1_800_000_000..1_800_000_000) })
    }
    val cues = root.getJSONArray("cues")
    require(cues.length() <= points.length())
    val decodedCues = List(cues.length()) { index ->
        val cue = cues.getJSONObject(index)
        WatchRunCue(cue.getInt("pointIndex").also { require(it in decodedPoints.indices) },
            cue.getString("kind").also { require(it in setOf("CONTINUE", "LEFT", "RIGHT", "U_TURN", "ARRIVE")) },
            cue.getString("instruction").also { require(it.isNotBlank() && it.length <= 500) })
    }
    require(decodedCues.map { it.pointIndex } == decodedCues.map { it.pointIndex }.distinct().sorted())
    return WatchRunRoute(root.getString("id").also(::validateRunId),
        root.getString("name").also { require(it.isNotBlank() && it.length <= 200) }, decodedPoints, decodedCues)
}

private fun validateRunId(value: String) { require(value.isNotBlank() && value.length <= 128) }
private fun WatchRunPoint.isValid() = latitudeE7 in -900_000_000..900_000_000 &&
    longitudeE7 in -1_800_000_000..1_800_000_000
private fun JSONObject.runNullableString(name: String): String? = if (isNull(name)) null else getString(name)
private fun <T> JSONArray.runObjects(decode: (JSONObject) -> T) = List(length()) { decode(getJSONObject(it)) }
