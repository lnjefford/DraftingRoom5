package dev.draftingroom5

internal enum class RunIntervalKind { WALK, RUN }

internal data class RunInterval(
    val id: String,
    val kind: RunIntervalKind,
    val durationSeconds: Int,
)

/** A routine owns its timing plan and an optional reference to a reusable route. */
internal data class RunRoutine(
    val intervals: List<RunInterval>,
    val routeId: String? = null,
)

internal fun Routine.withRunRoute(routeId: String?): Routine? {
    if (execution != RoutineExecution.RUN || (routeId != null && (routeId.isBlank() || routeId.length > 128))) return null
    val current = checkNotNull(run)
    return if (current.routeId == routeId) this else copy(revision = revision + 1, run = current.copy(routeId = routeId))
}

internal fun AppDocument.withoutRunRoute(routeId: String): AppDocument = copy(
    runRoutes = runRoutes.filterNot { it.id == routeId },
    lastRunRouteId = lastRunRouteId.takeUnless { it == routeId },
    plan = plan.copy(routines = plan.routines.map { routine ->
        if (routine.run?.routeId == routeId) checkNotNull(routine.withRunRoute(null)) else routine
    }),
)

internal val RunRoutine.totalDurationSeconds: Int
    get() = intervals.sumOf { it.durationSeconds }

internal fun RunRoutine.intervalCountLabel(): String =
    if (intervals.size == 1) "1 interval" else "${intervals.size} intervals"

internal fun Routine.withRunIdentity(name: String, artworkId: String): Routine? {
    val cleanName = name.trim()
    if (execution != RoutineExecution.RUN || cleanName.isEmpty() || cleanName.length > 200) return null
    val resolvedArtwork = RoutineArtworkCatalog.resolve(artworkId).storageId
    if (cleanName == this.name && resolvedArtwork == this.artworkId) return this
    return copy(revision = revision + 1, name = cleanName, artworkId = resolvedArtwork)
}

internal fun Routine.withRunArtwork(artworkId: String): Routine? {
    if (execution != RoutineExecution.RUN) return null
    val resolvedArtwork = RoutineArtworkCatalog.resolve(artworkId).storageId
    return if (resolvedArtwork == this.artworkId) this else copy(revision = revision + 1, artworkId = resolvedArtwork)
}

internal fun Routine.withRunInterval(interval: RunInterval): Routine? {
    if (execution != RoutineExecution.RUN || interval.id.isBlank() || interval.durationSeconds !in 1..86_400) return null
    val current = checkNotNull(run)
    val next = if (current.intervals.any { it.id == interval.id }) {
        current.intervals.map { if (it.id == interval.id) interval else it }
    } else current.intervals + interval
    if (next.map { it.id }.distinct().size != next.size || next.size > 512) return null
    return if (next == current.intervals) this else copy(revision = revision + 1, run = current.copy(intervals = next))
}

internal fun Routine.moveRunInterval(intervalId: String, offset: Int): Routine {
    if (execution != RoutineExecution.RUN) return this
    val current = checkNotNull(run)
    val moved = move(current.intervals, current.intervals.indexOfFirst { it.id == intervalId }, offset)
    return if (moved == current.intervals) this else copy(revision = revision + 1, run = current.copy(intervals = moved))
}

internal fun Routine.withoutRunInterval(intervalId: String, allowEmpty: Boolean = false): Routine? {
    if (execution != RoutineExecution.RUN) return null
    val current = checkNotNull(run)
    val next = current.intervals.filterNot { it.id == intervalId }
    if (next.size == current.intervals.size || (!allowEmpty && next.isEmpty())) return null
    return copy(revision = revision + 1, run = current.copy(intervals = next))
}

internal fun Routine.forRunEditorSave(original: Routine, isNew: Boolean): Routine =
    copy(revision = if (isNew) 1 else if (copy(revision = original.revision) == original) original.revision else original.revision + 1)

internal fun Routine.isSaveableRunRoutine(): Boolean = runCatching {
    execution == RoutineExecution.RUN && name.trim().isNotEmpty() && name.trim().length <= 200 &&
        appLink == null && exercises.isEmpty() && run != null && validateRunRoutine(run).let { true }
}.getOrDefault(false)

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

/** Keep turn locations and endpoints while bounding offline transfer and session snapshots. */
internal fun RunRoute.forRunSnapshot(maxPoints: Int = 2_000): RunRoute {
    if (points.size <= maxPoints) return this
    require(maxPoints >= 2)
    val required = (listOf(0, points.lastIndex) + turnCues.map { it.pointIndex }).distinct().sorted()
    val retainedRequired = if (required.size <= maxPoints) required else
        (listOf(0) + required.drop(1).dropLast(1).take(maxPoints - 2) + points.lastIndex).distinct().sorted()
    val room = maxPoints - retainedRequired.size
    val supplemental = if (room <= 0) emptyList() else {
        val step = (points.size / room).coerceAtLeast(1)
        val requiredSet = retainedRequired.toHashSet()
        points.indices.step(step).filterNot { it in requiredSet }.take(room)
    }
    val selected = (retainedRequired + supplemental).distinct().sorted()
    val mapped = selected.withIndex().associate { it.value to it.index }
    return copy(points = selected.map(points::get),
        waypointIndices = (listOf(0) + waypointIndices.mapNotNull(mapped::get) + selected.lastIndex).distinct().sorted(),
        turnCues = turnCues.mapNotNull { cue -> mapped[cue.pointIndex]?.let { cue.copy(pointIndex = it) } })
}

internal fun RunRoute.renamed(name: String): RunRoute? {
    val clean = name.trim()
    if (clean.isEmpty() || clean.length > 200) return null
    return if (clean == this.name) this else copy(name = clean, revision = revision + 1)
}

internal fun RunRoute.reversedDirection(): RunRoute = copy(
    revision = revision + 1,
    points = points.reversed(),
    waypointIndices = waypointIndices.map { points.lastIndex - it }.sorted(),
    // A saved instruction's direction does not remain valid when traveling the other way.
    turnCues = emptyList(),
)

internal fun RunRoute.duplicate(copyId: String): RunRoute = copy(
    id = copyId,
    revision = 1,
    name = "$name copy".take(200),
)

/** Existing geometry must not be silently replaced just to add provider directions. */
internal fun RunRoute.withGeneratedDirections(planned: WalkingRoute): RunRoute? {
    if (planned.points != points || planned.waypointIndices != waypointIndices || planned.turnCues.isEmpty()) return null
    return copy(turnCues = planned.turnCues, revision = revision + 1)
}

internal val RunRoute.distanceMeters: Double
    get() = points.zipWithNext().sumOf { (a, b) -> geoDistanceMeters(a, b) }

internal fun RunRoute.distanceLabel(): String =
    java.lang.String.format(java.util.Locale.US, "%.1f mi", distanceMeters / 1609.344)

internal fun RunRoute.withWaypoint(pointIndex: Int): RunRoute? {
    if (pointIndex !in points.indices || waypointIndices.size >= 10_000) return null
    if (pointIndex in waypointIndices) return this
    return copy(waypointIndices = (waypointIndices + pointIndex).sorted(), revision = revision + 1)
}

internal fun RunRoute.withoutWaypoint(pointIndex: Int): RunRoute? {
    if (pointIndex == 0 || pointIndex == points.lastIndex || pointIndex !in waypointIndices) return null
    return copy(waypointIndices = waypointIndices - pointIndex, revision = revision + 1)
}

internal fun RunRoute.withTurnCue(cue: RunTurnCue): RunRoute? {
    val instruction = cue.instruction.trim()
    if (cue.pointIndex !in points.indices || instruction.isEmpty() || instruction.length > 500) return null
    val normalized = cue.copy(instruction = instruction)
    val next = (turnCues.filterNot { it.pointIndex == cue.pointIndex } + normalized).sortedBy { it.pointIndex }
    return if (next == turnCues) this else copy(turnCues = next, revision = revision + 1)
}

internal fun RunRoute.withoutTurnCue(pointIndex: Int): RunRoute? {
    if (turnCues.none { it.pointIndex == pointIndex }) return null
    return copy(turnCues = turnCues.filterNot { it.pointIndex == pointIndex }, revision = revision + 1)
}

internal fun validateRunRoutine(run: RunRoutine) {
    require(run.routeId == null || (run.routeId.isNotBlank() && run.routeId.length <= 128)) {
        "Run route ID is invalid."
    }
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
