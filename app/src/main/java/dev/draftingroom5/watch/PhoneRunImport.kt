package dev.draftingroom5.watch

import dev.draftingroom5.AppDocument
import dev.draftingroom5.OccurrenceKey
import dev.draftingroom5.Routine
import dev.draftingroom5.RoutineExecution
import dev.draftingroom5.RunInterval
import dev.draftingroom5.RunIntervalKind
import dev.draftingroom5.RunLocationSample
import dev.draftingroom5.RunRoute
import dev.draftingroom5.RunRoutePoint
import dev.draftingroom5.RunRoutine
import dev.draftingroom5.RunSession
import dev.draftingroom5.RunTurnCue
import dev.draftingroom5.RunTurnKind
import dev.draftingroom5.WorkoutHistoryEntry
import java.time.LocalDate

/** Pure, idempotent import so Data Layer retries never double-complete an occurrence. */
internal fun AppDocument.withWatchRunResult(result: WatchRunResult): AppDocument {
    if (runSessions.any { it.id == result.id } && history.any { it.id == result.id }) return this
    require(runSessions.none { it.id == result.id } && history.none { it.id == result.id }) { "Run ID conflicts with local data." }
    val occurrence = OccurrenceKey(result.plan.scheduleEntryId, LocalDate.parse(result.plan.scheduledDate))
    require(history.none { it.occurrence == occurrence } && runSessions.none { it.occurrence == occurrence } &&
        partialSessions.none { it.occurrence == occurrence }) { "The scheduled run has already been started or completed." }
    val intervals = result.plan.intervals.mapIndexed { index, interval ->
        RunInterval("watch-$index", RunIntervalKind.valueOf(interval.kind), interval.durationSeconds)
    }
    val routine = Routine(result.plan.routineId, 1, result.plan.routineName, "running_shoe",
        RoutineExecution.RUN, emptyList(), null, RunRoutine(intervals, result.route?.id))
    val route = result.route?.let { source ->
        RunRoute(source.id, 1, source.name,
            source.points.map { RunRoutePoint(it.latitudeE7, it.longitudeE7) },
            listOf(0, source.points.lastIndex),
            source.cues.map { RunTurnCue(it.pointIndex, RunTurnKind.valueOf(it.kind), it.instruction) })
    }
    val effectiveDate = LocalDate.parse(result.plan.effectiveDate)
    val session = RunSession(result.id, occurrence, routine, route, result.startedAtMillis,
        result.elapsedMillis, null, result.distanceMeters,
        result.samples.map { RunLocationSample(RunRoutePoint(it.point.latitudeE7, it.point.longitudeE7),
            it.recordedAtMillis, it.accuracyMeters) }, result.completedAtMillis, effectiveDate)
    return copy(
        runSessions = runSessions + session,
        history = history + WorkoutHistoryEntry(result.id, occurrence, routine, result.startedAtMillis,
            result.completedAtMillis, effectiveDate),
        lastRunRouteId = route?.id?.takeIf { id -> runRoutes.any { it.id == id } } ?: lastRunRouteId,
    )
}
