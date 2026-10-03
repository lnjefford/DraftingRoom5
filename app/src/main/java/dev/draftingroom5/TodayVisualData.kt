package dev.draftingroom5

import java.time.DayOfWeek
import java.time.LocalDate
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
