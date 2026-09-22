package dev.draftingroom5

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.retirement.importer.WorkbookFailure
import dev.draftingroom5.retirement.ui.*
import java.time.Instant
import java.time.LocalDate

class EpicCases : PreviewParameterProvider<Int> { override val values = (0..7).asSequence() }

/** Hand-authored fictional values only; never reads a selected workbook or repository. */
private fun syntheticBook(): EpicWorkbook {
    val t = EpicTotals("100", "60", Money(60000), "40", Money(40000), Money(5000), Money(55000), Money(60000), Money(100000))
    return EpicWorkbook(Money(1000), t, listOf(EpicBreakdown("Synthetic Class A", t)),
        EpicProjection(LocalDate.parse("2030-01-01"), "0.1", "0.2", "Yes", Money(120000), Money(10000), Money(110000), Money(22000), Money(88000)),
        listOf(EpicYear(2026, "10", Money(60000), Money(40000), Money(100000), Money(5000), Money(95000), Money(50000), Money(45000), Money(9000), Money(0), Money(86000))), "7.0710678118654755")
}

@PreviewTest
@Preview(name = "Compact", widthDp = 320, heightDp = 800)
@Preview(name = "Tall", widthDp = 412, heightDp = 1100)
@Preview(name = "Large text", widthDp = 360, heightDp = 1100, fontScale = 2f)
@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
fun EpicScreenshots(@PreviewParameter(EpicCases::class) case: Int) {
    val book = syntheticBook()
    RetirementTheme {
        Surface {
            when (case) {
                0 -> EpicDetailPage(RetirementState()) {}
                1 -> {
                    val accepted = AcceptedEpicImport("synthetic", "shareworks-2026", 1, Instant.parse("2026-09-20T18:00:00Z"), "a".repeat(64), book, null)
                    EpicDetailPage(RetirementState(epicImports = listOf(accepted), activeEpicImportId = accepted.id)) {}
                }
                2 -> EpicUploadPage(null, false, false, null, {}, {}, {})
                3 -> EpicUploadPage(book, true, false, null, {}, {}, {})
                4 -> EpicUploadPage(null, true, false, WorkbookFailure.RECALCULATE, {}, {}, {})
                5 -> EpicPage("Workbook projection") { EpicProjectionSection(book) }
                6 -> EpicPage("Annual projections") { EpicAnnualSection(book) }
                else -> EpicPage("Current position") { EpicTotalsContent(book.totals) }
            }
        }
    }
}
