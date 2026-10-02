package dev.draftingroom5

import dev.draftingroom5.retirement.domain.Account
import dev.draftingroom5.retirement.domain.AccountOrigin
import dev.draftingroom5.retirement.domain.AccountRevision
import dev.draftingroom5.retirement.domain.AccountType
import dev.draftingroom5.retirement.domain.BalanceSnapshot
import dev.draftingroom5.retirement.domain.BalanceSource
import dev.draftingroom5.retirement.domain.Money
import dev.draftingroom5.retirement.domain.Owner
import dev.draftingroom5.retirement.domain.RetirementState
import dev.draftingroom5.retirement.domain.TaxTreatment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayVisualDataTest {
    @Test fun weekStartsSundayAndUsesActualScheduledSessions() {
        val week = todayWeek(defaultAppDocument(), LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 9, 27), week.first().date)
        assertEquals(LocalDate.of(2026, 10, 3), week.last().date)
        assertEquals(listOf(0, 2, 1, 1, 1, 1, 1), week.map { it.planned })
        assertEquals(0, week.sumOf { it.completed })
    }

    @Test fun retirementChartUsesRecordedBalancesWithoutInventingEarlierValues() {
        val first = LocalDate.of(2026, 9, 1)
        val second = LocalDate.of(2026, 10, 1)
        val revision = AccountRevision("revision", 1, "Account", Owner.SELF, AccountType.BROKERAGE,
            TaxTreatment.TAXABLE, true, Instant.EPOCH)
        fun balance(id: String, date: LocalDate, cents: Long, sequence: Long) =
            BalanceSnapshot(id, date, Instant.EPOCH, sequence, Money(cents), null, BalanceSource.MANUAL, id)
        val account = Account("account", AccountOrigin.MANUAL, null, null, listOf(revision), listOf(
            balance("early", first, 10_000, 1), balance("recent", second, 15_000, 2),
        ))
        val laterAccount = Account("later", AccountOrigin.MANUAL, null, null, listOf(revision.copy(id = "later-revision")),
            listOf(balance("later-balance", second, 30_000, 1)))

        assertEquals(listOf(TodayRetirementPoint(first, 10_000), TodayRetirementPoint(second, 45_000)),
            todayRetirementTrend(RetirementState(accounts = listOf(account, laterAccount))))
    }
}
