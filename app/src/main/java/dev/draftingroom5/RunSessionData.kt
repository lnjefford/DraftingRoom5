package dev.draftingroom5

import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Captured positions are bounded and filtered before they enter the durable document. */
internal data class RunLocationSample(
    val point: RunRoutePoint,
    val recordedAtMillis: Long,
    val accuracyMeters: Int,
)

internal data class RunSession(
    val id: String,
    val occurrence: OccurrenceKey,
    val routine: Routine,
    val route: RunRoute?,
    val startedAtMillis: Long,
    val elapsedBeforeResumeMillis: Long,
    val runningSinceMillis: Long?,
    val distanceMeters: Int,
    val samples: List<RunLocationSample>,
    val completedAtMillis: Long? = null,
    val effectiveDate: LocalDate = occurrence.scheduledDate,
) {
    val isRunning: Boolean get() = runningSinceMillis != null && completedAtMillis == null

    fun elapsedMillis(nowMillis: Long): Long = elapsedBeforeResumeMillis +
        (runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0) } ?: 0)

    fun intervalAt(nowMillis: Long): Pair<Int, RunInterval>? {
        val elapsedSeconds = elapsedMillis(nowMillis) / 1_000
        var boundary = 0L
        routine.run?.intervals?.forEachIndexed { index, interval ->
            boundary += interval.durationSeconds
            if (elapsedSeconds < boundary) return index to interval
        }
        return null
    }

    fun pause(atMillis: Long): RunSession = if (!isRunning) this else copy(
        elapsedBeforeResumeMillis = elapsedMillis(atMillis),
        runningSinceMillis = null,
    )

    fun resume(atMillis: Long): RunSession = if (completedAtMillis != null || isRunning) this else
        copy(runningSinceMillis = atMillis.coerceAtLeast(startedAtMillis))

    fun finish(atMillis: Long): RunSession = if (completedAtMillis != null) this else
        pause(atMillis).copy(completedAtMillis = atMillis.coerceAtLeast(startedAtMillis))

    fun record(sample: RunLocationSample): RunSession {
        if (!isRunning || samples.size >= 10_000 || sample.accuracyMeters !in 0..50 ||
            sample.recordedAtMillis < startedAtMillis || sample.recordedAtMillis < (samples.lastOrNull()?.recordedAtMillis ?: startedAtMillis)) return this
        val previous = samples.lastOrNull()
        val step = previous?.let { geoDistanceMeters(it.point, sample.point) } ?: 0.0
        val seconds = previous?.let { (sample.recordedAtMillis - it.recordedAtMillis) / 1_000.0 } ?: 0.0
        if (previous != null && (seconds <= 0.0 || step > 12.0 * seconds + 20.0)) return this
        if (previous != null && step < 2.0 && seconds < 10.0) return this
        return copy(
            distanceMeters = (distanceMeters + step.toInt()).coerceAtMost(1_000_000),
            samples = samples + sample,
        )
    }
}

internal fun startRunSession(
    id: String,
    occurrence: OccurrenceKey,
    routine: Routine,
    route: RunRoute?,
    atMillis: Long,
    effectiveDate: LocalDate = occurrence.scheduledDate,
): RunSession {
    require(routine.execution == RoutineExecution.RUN && routine.run != null && atMillis >= 0)
    route?.let(::validateRunRoute)
    return RunSession(id, occurrence, routine, route, atMillis, 0, atMillis, 0, emptyList(), effectiveDate = effectiveDate)
}

internal fun validateRunSession(session: RunSession) {
    require(session.id.isNotBlank() && session.id.length <= 128)
    require(session.routine.execution == RoutineExecution.RUN && session.routine.run != null)
    validateRunRoutine(checkNotNull(session.routine.run))
    session.route?.let(::validateRunRoute)
    require(session.startedAtMillis >= 0 && session.elapsedBeforeResumeMillis >= 0)
    require(session.runningSinceMillis == null || session.runningSinceMillis >= session.startedAtMillis)
    require(session.completedAtMillis == null || (session.completedAtMillis >= session.startedAtMillis && session.runningSinceMillis == null))
    require(session.distanceMeters in 0..1_000_000 && session.samples.size <= 10_000)
    require(session.samples.zipWithNext().all { (a, b) -> a.recordedAtMillis <= b.recordedAtMillis })
    session.samples.forEach {
        require(it.recordedAtMillis >= session.startedAtMillis && it.accuracyMeters in 0..50)
        require(it.point.latitudeE7 in -900_000_000..900_000_000 && it.point.longitudeE7 in -1_800_000_000..1_800_000_000)
    }
}

internal fun RunSession.routeProgress(): Pair<Int, Int>? {
    val route = route ?: return null
    val current = samples.lastOrNull()?.point ?: return null
    val stride = (route.points.size / 2_000).coerceAtLeast(1)
    val coarse = route.points.indices.step(stride).minByOrNull { geoDistanceMeters(current, route.points[it]) } ?: return null
    val nearest = ((coarse - stride).coerceAtLeast(0)..(coarse + stride).coerceAtMost(route.points.lastIndex))
        .minByOrNull { geoDistanceMeters(current, route.points[it]) } ?: coarse
    return nearest + 1 to route.points.size
}

internal fun geoDistanceMeters(a: RunRoutePoint, b: RunRoutePoint): Double {
    val lat1 = Math.toRadians(a.latitudeE7 / 10_000_000.0)
    val lat2 = Math.toRadians(b.latitudeE7 / 10_000_000.0)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians((b.longitudeE7.toLong() - a.longitudeE7.toLong()) / 10_000_000.0)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 12_742_000.0 * asin(min(1.0, sqrt(h)))
}
