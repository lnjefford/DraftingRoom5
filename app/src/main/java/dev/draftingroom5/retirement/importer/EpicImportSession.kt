package dev.draftingroom5.retirement.importer

import dev.draftingroom5.retirement.data.*
import dev.draftingroom5.retirement.domain.*
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** A review is bound to the snapshot shown before selection; never persisted or auto-confirmed. */
class EpicImportReview internal constructor(
    val candidate: ValidatedEpicCandidate, val generation: Long, val replaces: String?,
) {
    override fun toString() = "EpicImportReview([private])"
}
class EpicImportSession(private val repository: RetirementRepository) {
    fun review(input: InputStream, snapshot: RetirementState, cancelled: () -> Boolean = { false }): EpicImportReview =
        EpicImportReview(ShareworksImporter().parse(input, cancelled), snapshot.generation, snapshot.activeEpicImportId)

    fun confirm(review: EpicImportReview, at: Instant): RetirementResult<RetirementState> {
        val accepted = AcceptedEpicImport(UUID.randomUUID().toString(), "shareworks-2026", 1, at,
            review.candidate.digest, review.candidate.workbook, review.replaces)
        return repository.acceptEpicImport(review.generation, accepted,
            ImportMetadata("SHAREWORKS", ImportStatus.ACCEPTED, accepted.id, at, at, null))
    }
    fun rejected(snapshot: RetirementState, at: Instant) = repository.recordEpicFailure(snapshot.generation, at)
}

fun workbookMessage(failure: WorkbookFailure): String = when (failure) {
    WorkbookFailure.RECALCULATE -> "Recalculate and save the workbook in Excel, then choose it again. The previous import is unchanged."
    WorkbookFailure.TOO_LARGE -> "This workbook exceeds the safe import limits. The previous import is unchanged."
    WorkbookFailure.UNSUPPORTED -> "Choose a Shareworks 2026 .xlsm workbook. The previous import is unchanged."
    WorkbookFailure.CANCELLED -> "Import cancelled. The previous import is unchanged."
    WorkbookFailure.CONFLICT -> "Retirement data changed during review. Choose the workbook again before replacing data."
    WorkbookFailure.SAVE_FAILED -> "The import could not be saved. The previous import is unchanged. Try again."
    WorkbookFailure.INVALID -> "The workbook is incomplete or invalid. Recalculate and save the original Shareworks workbook, then choose it again. The previous import is unchanged."
}

fun epicDateNotice(book: EpicWorkbook, retirementDate: java.time.LocalDate?): String? = when {
    retirementDate == null -> "Workbook projection date: ${book.projection.date}. A retirement date has not been configured."
    retirementDate != book.projection.date -> "Workbook date ${book.projection.date} differs from retirement date $retirementDate. Upload an updated workbook for matching retirement values."
    book.years.none { it.year == retirementDate.year } -> "The workbook has no annual projection for ${retirementDate.year}. Upload an updated workbook; missing years are not extrapolated."
    else -> null
}
