package dev.draftingroom5.retirement.provider

import dev.draftingroom5.retirement.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AccountReviewPresentationTest {
    @Test fun defaultsUseEveryAccountAndIncludeItInForecast() {
        val account = LinkedAccountData(
            "provider-account", "Work retirement", "1234", "401k", Money(100_00),
            LocalDate.of(2026, 9, 22), emptyList(), HoldingAvailability.COMPLETE,
        )
        val selected = defaultSelection(account)
        assertEquals(account.providerAccountId, selected.providerAccountId)
        assertEquals(AccountType.EMPLOYER_401K, selected.type)
        assertEquals(TaxTreatment.PRE_TAX, selected.tax)
        assertTrue(selected.included)
    }

    @Test fun dropdownLabelsAreUserFacingAndTypeChangesStayValid() {
        assertFalse(AccountType.HSA in reviewAccountTypes())
        assertEquals("Employer 401(k)", accountTypeLabel(AccountType.EMPLOYER_401K))
        assertEquals("Traditional IRA", accountTypeLabel(AccountType.IRA_TRADITIONAL))
        assertEquals("Pre-tax", taxTreatmentLabel(TaxTreatment.PRE_TAX))
        assertEquals("Tax-free", taxTreatmentLabel(TaxTreatment.TAX_FREE))
        assertEquals("You", ownerLabel(Owner.SELF))
        assertEquals(TaxTreatment.ROTH, defaultTaxTreatment(AccountType.IRA_ROTH, TaxTreatment.TAXABLE))
        assertTrue(taxTreatmentsFor(AccountType.EMPLOYER_403B).all { AccountClassification.valid(AccountType.EMPLOYER_403B, it) })
    }

    @Test fun hsaProviderSuggestionFallsBackToAVisibleAccountType() {
        val account = LinkedAccountData(
            "provider-hsa", "Health savings", "9876", "hsa", Money(50_00),
            LocalDate.of(2026, 9, 23), emptyList(), HoldingAvailability.COMPLETE,
        )
        val selected = defaultSelection(account)
        assertEquals(AccountType.BROKERAGE, selected.type)
        assertEquals(TaxTreatment.TAXABLE, selected.tax)
    }
}
