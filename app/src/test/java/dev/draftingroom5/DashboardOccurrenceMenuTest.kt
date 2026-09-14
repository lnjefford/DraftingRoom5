package dev.draftingroom5

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardOccurrenceMenuTest {
    private val date = LocalDate.of(2026, 9, 10)
    private val plan = defaultTrainingPlan()
    private val card = dashboardSessions(plan, emptyList(), emptyList(), date).first()

    @Test fun onlyUnstartedCurrentDayScheduledCardsOfferTheMenu() {
        assertTrue(canChangeDashboardOccurrence(card, date, date, emptyList()))
        assertFalse(canChangeDashboardOccurrence(card, date.plusDays(1), date, emptyList()))
        assertFalse(canChangeDashboardOccurrence(card, date, date.plusDays(1), emptyList()))
        assertFalse(canChangeDashboardOccurrence(card.copy(action = SessionAction.RESUME), date, date, emptyList()))
        assertFalse(canChangeDashboardOccurrence(card.copy(action = SessionAction.DONE), date, date, emptyList()))
        assertFalse(canChangeDashboardOccurrence(card.copy(savedOriginDate = date), date, date, emptyList()))
        val exception = OccurrenceException(card.occurrence, OccurrenceDisposition.DEFERRED, date.plusDays(1))
        assertFalse(canChangeDashboardOccurrence(card, date, date, listOf(exception)))
        val moved = card.copy(effectiveDate = date.plusDays(1))
        assertFalse(canChangeDashboardOccurrence(moved, date, date, emptyList()))
    }

    @Test fun menuActionsIdentifyTheExactRoutineAndSourceDate() {
        assertEquals("More options for ${card.routine.name} on $date", occurrenceMenuDescription(card))
        assertEquals("Move ${card.routine.name} on $date to tomorrow", occurrenceMenuActionDescription(card, OccurrenceDisposition.DEFERRED))
        assertEquals("Skip ${card.routine.name} on $date", occurrenceMenuActionDescription(card, OccurrenceDisposition.SKIPPED))
    }

    @Test fun movedDailyOccurrenceRemainsSeparateAndStartableTomorrow() {
        val dailyEntry = plan.schedule.first()
        val dailyPlan = plan.copy(schedule = listOf(dailyEntry.copy(days = java.time.DayOfWeek.entries.toSet())))
        val source = OccurrenceKey(dailyEntry.id, date)
        val exception = OccurrenceException(source, OccurrenceDisposition.DEFERRED, date.plusDays(1))
        val tomorrow = dashboardSessions(dailyPlan, emptyList(), emptyList(), date.plusDays(1), listOf(exception))
        assertEquals(2, tomorrow.size)
        assertEquals(2, tomorrow.map { it.occurrence }.distinct().size)
        assertTrue(tomorrow.all { it.action == SessionAction.START })
        assertFalse(canChangeDashboardOccurrence(tomorrow.first { it.occurrence == source }, date.plusDays(1), date.plusDays(1), listOf(exception)))
    }
}
