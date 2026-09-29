package dev.draftingroom5

internal enum class RunIntervalKind { WALK, RUN }

internal data class RunInterval(
    val id: String,
    val kind: RunIntervalKind,
    val durationSeconds: Int,
)

/** A routine owns its timing plan. Route selection is deliberately separate. */
internal data class RunRoutine(
    val intervals: List<RunInterval>,
)

/** Integer coordinates keep saved documents and phone/watch comparisons deterministic. */
internal data class RunRoutePoint(
    val latitudeE7: Int,
    val longitudeE7: Int,
)

internal enum class RunTurnKind { CONTINUE, LEFT, RIGHT, U_TURN, ARRIVE }

internal data class RunTurnCue(
    val pointIndex: Int,
    val kind: RunTurnKind,
    val instruction: String,
)

/**
 * A reusable, offline-capable route. waypointIndices retain editor intent while
 * points and turn cues are the immutable guidance payload transferred to a watch.
 */
internal data class RunRoute(
    val id: String,
    val revision: Long,
    val name: String,
    val points: List<RunRoutePoint>,
    val waypointIndices: List<Int>,
    val turnCues: List<RunTurnCue>,
)

internal fun validateRunRoutine(run: RunRoutine) {
    require(run.intervals.isNotEmpty() && run.intervals.size <= 512) {
        "Run routines need between 1 and 512 intervals."
    }
    require(run.intervals.map { it.id }.distinct().size == run.intervals.size) {
        "Run interval IDs must be unique."
    }
    run.intervals.forEach { interval ->
        require(interval.id.isNotBlank() && interval.id.length <= 128) { "Run interval ID is invalid." }
        require(interval.durationSeconds in 1..86_400) { "Run interval duration is invalid." }
    }
}

internal fun validateRunRoute(route: RunRoute) {
    require(route.id.isNotBlank() && route.id.length <= 128) { "Run route ID is invalid." }
    require(route.revision >= 1) { "Run route revision must be positive." }
    require(route.name.isNotBlank() && route.name == route.name.trim() && route.name.length <= 200) {
        "Run route name is invalid."
    }
    require(route.points.size in 2..100_000) { "Run route point count is invalid." }
    route.points.forEach { point ->
        require(point.latitudeE7 in -900_000_000..900_000_000) { "Run route latitude is invalid." }
        require(point.longitudeE7 in -1_800_000_000..1_800_000_000) { "Run route longitude is invalid." }
    }
    require(route.waypointIndices.size in 2..10_000) { "Run route waypoint count is invalid." }
    require(route.waypointIndices == route.waypointIndices.distinct().sorted()) {
        "Run route waypoints must be unique and ordered."
    }
    require(route.waypointIndices.first() == 0 && route.waypointIndices.last() == route.points.lastIndex) {
        "Run route waypoints must include both endpoints."
    }
    require(route.waypointIndices.all { it in route.points.indices }) { "Run route waypoint is out of range." }
    require(route.turnCues.size <= route.points.size) { "Run route has too many turn cues." }
    require(route.turnCues.map { it.pointIndex } == route.turnCues.map { it.pointIndex }.distinct().sorted()) {
        "Run route turn cues must be unique and ordered."
    }
    route.turnCues.forEach { cue ->
        require(cue.pointIndex in route.points.indices) { "Run route turn cue is out of range." }
        require(cue.instruction.isNotBlank() && cue.instruction == cue.instruction.trim() && cue.instruction.length <= 500) {
            "Run route turn instruction is invalid."
        }
    }
}
