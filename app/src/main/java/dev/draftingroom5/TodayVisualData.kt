package dev.draftingroom5

import dev.draftingroom5.retirement.domain.AssetAggregator
import dev.draftingroom5.retirement.domain.RetirementState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

internal data class TodayWeekDay(val date: LocalDate, val planned: Int, val completed: Int)

internal fun todayWeek(document: AppDocument, today: LocalDate): List<TodayWeekDay> {
    val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    return (0L..6L).map { offset ->
        val date = sunday.plusDays(offset)
        val sessions = dashboardSessions(document, date)
        TodayWeekDay(date, sessions.size, sessions.count { it.action == SessionAction.DONE })
    }
}

internal data class TodayRetirementPoint(val date: LocalDate, val cents: Long)

/** A history of recorded tracked values, not an investment-return series. */
internal fun todayRetirementTrend(state: RetirementState): List<TodayRetirementPoint> {
    val dates = (state.accounts.flatMap { account -> account.balances.map { it.asOfDate } } +
        state.properties.flatMap { property -> property.valuations.map {
            it.providerAsOf ?: it.acceptedAt.atZone(ZoneOffset.UTC).toLocalDate()
        } } + state.epicImports.map { it.acceptedAt.atZone(ZoneOffset.UTC).toLocalDate() })
        .distinct().sorted().takeLast(24)
    return dates.mapNotNull { date ->
        val snapshot = state.copy(
            accounts = state.accounts.map { account ->
                account.copy(balances = account.balances.filter { it.asOfDate <= date })
            },
            properties = state.properties.map { property ->
                property.copy(valuations = property.valuations.filter {
                    (it.providerAsOf ?: it.acceptedAt.atZone(ZoneOffset.UTC).toLocalDate()) <= date
                })
            },
            activeEpicImportId = state.activeEpicImportId?.takeIf { id ->
                state.epicImports.any { it.id == id && it.acceptedAt.atZone(ZoneOffset.UTC).toLocalDate() <= date }
            },
        )
        AssetAggregator.totals(snapshot).tracked.cents.takeIf { it > 0 }?.let { TodayRetirementPoint(date, it) }
    }
}
