package dev.draftingroom5.watch

import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dev.draftingroom5.AppDocument
import dev.draftingroom5.RoutineExecution
import dev.draftingroom5.RunRoute
import dev.draftingroom5.forRunSnapshot
import dev.draftingroom5.dashboardSessions
import java.time.LocalDate

internal fun AppDocument.toWatchRunCatalog(today: LocalDate): WatchRunCatalog {
    val plans = (0L..6L).flatMap { day -> dashboardSessions(this, today.plusDays(day)) }
        .filter { it.routine.execution == RoutineExecution.RUN && it.action == dev.draftingroom5.SessionAction.START }
        .take(512)
        .map { candidate ->
            WatchRunPlan(
                routineId = candidate.routine.id,
                routineName = candidate.routine.name,
                scheduleEntryId = candidate.occurrence.scheduleEntryId,
                scheduledDate = candidate.occurrence.scheduledDate.toString(),
                effectiveDate = candidate.effectiveDate.toString(),
                intervals = checkNotNull(candidate.routine.run).intervals.map { WatchRunInterval(it.kind.name, it.durationSeconds) },
                preferredRouteId = checkNotNull(candidate.routine.run).routeId,
            )
        }
    val preferredIds = (plans.mapNotNull { it.preferredRouteId } + listOfNotNull(lastRunRouteId)).toSet()
    val selectedRoutes = runRoutes.sortedWith(compareByDescending<RunRoute> { it.id in preferredIds }.thenBy { it.name }).take(64)
    return WatchRunCatalog(
        generation = generation,
        plans = plans,
        routes = selectedRoutes.map(RunRoute::toWatchRoute),
        lastRouteId = lastRunRouteId?.takeIf { id -> selectedRoutes.any { it.id == id } },
        hapticsEnabled = preferences.hapticsEnabled,
    )
}

internal fun RunRoute.toWatchRoute(): WatchRunRoute {
    val bounded = forRunSnapshot()
    return WatchRunRoute(id, name,
        bounded.points.map { WatchRunPoint(it.latitudeE7, it.longitudeE7) },
        bounded.turnCues.map { WatchRunCue(it.pointIndex, it.kind.name, it.instruction) })
}

internal fun publishWatchRunCatalog(service: android.content.Context, document: AppDocument) {
    val payload = encodeWatchRunCatalog(document.toWatchRunCatalog(LocalDate.now()))
    val request = PutDataRequest.create(WATCH_RUN_CATALOG_PATH)
        .setData(document.generation.toString().toByteArray(Charsets.UTF_8))
        .putAsset(WATCH_RUN_ASSET_KEY, Asset.createFromBytes(payload))
        .setUrgent()
    Wearable.getDataClient(service).putDataItem(request)
}
