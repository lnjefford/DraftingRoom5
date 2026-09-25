package dev.draftingroom5.retirement.data

import dev.draftingroom5.retirement.domain.*
import dev.draftingroom5.inspectJsonStructure
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate

object RetirementCodec {
    const val FORMAT = "draftingroom5.retirement.v1"

    fun encode(value: RetirementState): String {
        validateRetirementState(value)
        return JSONObject().put("format", FORMAT).put("generation", value.generation)
            .put("accounts", array(value.accounts, ::accountJson))
            .put("properties", array(value.properties, ::propertyJson))
            .put("epicImports", array(value.epicImports, ::epicJson))
            .putNullable("activeEpicImportId", value.activeEpicImportId)
            .put("importMetadata", array(value.importMetadata, ::importMetadataJson))
            .put("planSettings", array(value.planSettings, ::planJson))
            .put("checklist", array(value.checklist, ::checklistJson))
            .put("acceptedProviderOperations", JSONArray(value.acceptedProviderOperations))
            .put("providerItems", array(value.providerItems, ::providerItemJson)).toString().also {
                require(it.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) { "Retirement payload is too large." }
            }
    }

    fun decode(encoded: String): RetirementState {
        require(encoded.toByteArray().size <= 4 * 1024 * 1024) { "Retirement payload is too large." }
        inspectJsonStructure(encoded)
        val parsed = JSONTokener(encoded).nextValue()
        require(parsed is JSONObject) { "Retirement payload must be an object." }
        val root = parsed.exact("format", "generation", "accounts", "properties", "epicImports", "activeEpicImportId", "importMetadata", "planSettings", "checklist", "providerItems", "acceptedProviderOperations")
        require(root.string("format") == FORMAT) { "Unsupported Retirement format." }
        return RetirementState(
            root.long("generation"), root.objects("accounts", ::decodeAccount), root.objects("properties", ::decodeProperty),
            root.objects("epicImports", ::decodeEpic), root.nullableString("activeEpicImportId"),
            root.objects("importMetadata", ::decodeImportMetadata), root.objects("planSettings", ::decodePlan),
            root.objects("checklist", ::decodeChecklist), root.objects("providerItems", ::decodeProviderItem),
            root.getJSONArray("acceptedProviderOperations").let { a -> List(a.length()) { index -> a.get(index).also { require(it is String) } as String } },
        ).also(::validateRetirementState)
    }

    private fun accountJson(value: Account) = JSONObject().put("id", value.id).put("origin", value.origin.name)
        .putNullable("archivedAt", value.archivedAt?.toString()).putNullable("providerIdentity", value.providerIdentity?.let(::providerIdentityJson))
        .put("revisions", array(value.revisions, ::accountRevisionJson)).put("balances", array(value.balances, ::balanceJson))
        .put("holdingSnapshots", array(value.holdingSnapshots, ::holdingSnapshotJson))

    private fun decodeAccount(value: JSONObject): Account {
        value.exact("id", "origin", "archivedAt", "providerIdentity", "revisions", "balances", "holdingSnapshots")
        return Account(value.string("id"), value.enum("origin"), value.instantOrNull("archivedAt"),
            value.objectOrNull("providerIdentity")?.let(::decodeProviderIdentity), value.objects("revisions", ::decodeAccountRevision),
            value.objects("balances", ::decodeBalance), value.objects("holdingSnapshots", ::decodeHoldingSnapshot))
    }

    private fun providerIdentityJson(value: ProviderIdentity) = JSONObject().put("credentialProfileId", value.credentialProfileId)
        .put("environment", value.environment.name).put("itemId", value.itemId).put("providerAccountId", value.providerAccountId)
    private fun decodeProviderIdentity(value: JSONObject): ProviderIdentity {
        value.exact("credentialProfileId", "environment", "itemId", "providerAccountId")
        return ProviderIdentity(value.string("credentialProfileId"), value.enum("environment"), value.string("itemId"), value.string("providerAccountId"))
    }

    private fun accountRevisionJson(value: AccountRevision) = JSONObject().put("id", value.id).put("revision", value.revision)
        .put("displayName", value.displayName).put("owner", value.owner.name).put("type", value.type.name)
        .put("taxTreatment", value.taxTreatment.name).put("includedInForecast", value.includedInForecast)
        .put("effectiveAt", value.effectiveAt.toString()).putNullable("previousRevisionId", value.previousRevisionId)
    private fun decodeAccountRevision(value: JSONObject): AccountRevision {
        value.exact("id", "revision", "displayName", "owner", "type", "taxTreatment", "includedInForecast", "effectiveAt", "previousRevisionId")
        return AccountRevision(value.string("id"), value.long("revision"), value.string("displayName"), value.enum("owner"), value.enum("type"),
            value.enum("taxTreatment"), value.boolean("includedInForecast"), value.instant("effectiveAt"), value.nullableString("previousRevisionId"))
    }

    private fun balanceJson(value: BalanceSnapshot) = JSONObject().put("id", value.id).put("asOfDate", value.asOfDate.toString())
        .put("acceptedAt", value.acceptedAt.toString()).put("sequence", value.sequence).put("amountCents", value.amount.cents)
        .putNullable("basisCents", value.basis?.cents).put("source", value.source.name).put("batchId", value.batchId).putNullable("supersedesId", value.supersedesId)
    private fun decodeBalance(value: JSONObject): BalanceSnapshot {
        value.exact("id", "asOfDate", "acceptedAt", "sequence", "amountCents", "basisCents", "source", "batchId", "supersedesId")
        return BalanceSnapshot(value.string("id"), value.date("asOfDate"), value.instant("acceptedAt"), value.long("sequence"), Money(value.long("amountCents")),
            value.nullableLong("basisCents")?.let(::Money), value.enum("source"), value.string("batchId"), value.nullableString("supersedesId"))
    }

    private fun holdingSnapshotJson(value: HoldingSnapshot) = JSONObject().put("batchId", value.batchId).put("acceptedAt", value.acceptedAt.toString())
        .put("availability", value.availability.name).put("holdings", array(value.holdings, ::holdingJson))
    private fun decodeHoldingSnapshot(value: JSONObject): HoldingSnapshot {
        value.exact("batchId", "acceptedAt", "availability", "holdings")
        return HoldingSnapshot(value.string("batchId"), value.instant("acceptedAt"), value.enum("availability"), value.objects("holdings", ::decodeHolding))
    }
    private fun holdingJson(value: Holding) = JSONObject().put("securityId", value.securityId).put("quantity", value.quantity)
        .putNullable("priceCents", value.price?.cents).putNullable("basisCents", value.basis?.cents).put("assetClass", value.assetClass)
    private fun decodeHolding(value: JSONObject): Holding {
        value.exact("securityId", "quantity", "priceCents", "basisCents", "assetClass")
        return Holding(value.string("securityId"), value.string("quantity"), value.nullableLong("priceCents")?.let(::Money),
            value.nullableLong("basisCents")?.let(::Money), value.string("assetClass"))
    }

    private fun propertyJson(value: Property) = JSONObject().put("id", value.id).put("accountId", value.accountId)
        .putNullable("archivedAt", value.archivedAt?.toString()).put("revisions", array(value.revisions, ::propertyRevisionJson))
        .put("valuations", array(value.valuations, ::valuationJson))
    private fun decodeProperty(value: JSONObject): Property {
        value.exact("id", "accountId", "archivedAt", "revisions", "valuations")
        return Property(value.string("id"), value.string("accountId"), value.instantOrNull("archivedAt"),
            value.objects("revisions", ::decodePropertyRevision), value.objects("valuations", ::decodeValuation))
    }
    private fun propertyRevisionJson(value: PropertyRevision) = JSONObject().put("id", value.id).put("revision", value.revision)
        .put("address", value.address).putNullable("facts", value.facts).put("owner", value.owner.name).put("ownershipBps", value.ownershipBps)
        .put("mortgage", mortgageJson(value.mortgage)).put("automaticValueEnabled", value.automaticValueEnabled)
        .put("includedInForecast", value.includedInForecast).put("effectiveAt", value.effectiveAt.toString()).putNullable("previousRevisionId", value.previousRevisionId)
        .putNullable("providerPropertyId", value.providerPropertyId).putNullable("credentialProfileId", value.credentialProfileId)
    private fun decodePropertyRevision(value: JSONObject): PropertyRevision {
        value.exact("id", "revision", "address", "facts", "owner", "ownershipBps", "mortgage", "automaticValueEnabled", "includedInForecast", "effectiveAt", "previousRevisionId", "providerPropertyId", "credentialProfileId")
        return PropertyRevision(value.string("id"), value.long("revision"), value.string("address"), value.nullableString("facts"), value.enum("owner"),
            value.int("ownershipBps"), decodeMortgage(value.obj("mortgage")), value.boolean("automaticValueEnabled"), value.boolean("includedInForecast"),
            value.instant("effectiveAt"), value.nullableString("previousRevisionId"), value.nullableString("providerPropertyId"), value.nullableString("credentialProfileId"))
    }
    private fun mortgageJson(value: MortgageTerms) = JSONObject().put("outstandingCents", value.outstanding.cents).put("asOfDate", value.asOfDate.toString())
        .putNullable("originalPrincipalCents", value.originalPrincipal?.cents).put("annualRateBps", value.annualRateBps).put("paymentCents", value.payment.cents)
        .put("remainingMonths", value.remainingMonths).put("contractualTermMonths", value.contractualTermMonths)
    private fun decodeMortgage(value: JSONObject): MortgageTerms {
        value.exact("outstandingCents", "asOfDate", "originalPrincipalCents", "annualRateBps", "paymentCents", "remainingMonths", "contractualTermMonths")
        return MortgageTerms(Money(value.long("outstandingCents")), value.date("asOfDate"), value.nullableLong("originalPrincipalCents")?.let(::Money),
            value.int("annualRateBps"), Money(value.long("paymentCents")), value.int("remainingMonths"), value.int("contractualTermMonths"))
    }
    private fun valuationJson(value: PropertyValuationSnapshot) = JSONObject().put("id", value.id).put("estimateCents", value.estimate.cents)
        .putNullable("rangeLowCents", value.rangeLow?.cents).putNullable("rangeHighCents", value.rangeHigh?.cents).putNullable("comparableCount", value.comparableCount)
        .put("source", value.source.name).putNullable("providerAsOf", value.providerAsOf?.toString()).put("acceptedAt", value.acceptedAt.toString())
        .put("sequence", value.sequence).put("batchId", value.batchId).putNullable("supersedesId", value.supersedesId)
    private fun decodeValuation(value: JSONObject): PropertyValuationSnapshot {
        value.exact("id", "estimateCents", "rangeLowCents", "rangeHighCents", "comparableCount", "source", "providerAsOf", "acceptedAt", "sequence", "batchId", "supersedesId")
        return PropertyValuationSnapshot(value.string("id"), Money(value.long("estimateCents")), value.nullableLong("rangeLowCents")?.let(::Money),
            value.nullableLong("rangeHighCents")?.let(::Money), value.nullableInt("comparableCount"), value.enum("source"), value.dateOrNull("providerAsOf"),
            value.instant("acceptedAt"), value.long("sequence"), value.string("batchId"), value.nullableString("supersedesId"))
    }

    private fun epicTotalsJson(value: EpicTotals) = JSONObject()
        .put("sharesGranted", value.sharesGranted)
        .put("vestedShares", value.vestedShares)
        .put("vestedValue", value.vestedValue.cents)
        .put("unvestedShares", value.unvestedShares)
        .put("unvestedValue", value.unvestedValue.cents)
        .put("loans", value.loans.cents)
        .put("pretaxMinusLoans", value.pretaxMinusLoans.cents)
        .put("pretaxToday", value.pretaxToday.cents)
        .put("pretaxAllVested", value.pretaxAllVested.cents)
    private fun decodeEpicTotals(value: JSONObject): EpicTotals {
        value.exact("sharesGranted", "vestedShares", "vestedValue", "unvestedShares", "unvestedValue", "loans", "pretaxMinusLoans", "pretaxToday", "pretaxAllVested")
        return EpicTotals(value.string("sharesGranted"), value.string("vestedShares"), Money(value.long("vestedValue")), value.string("unvestedShares"), Money(value.long("unvestedValue")), Money(value.long("loans")), Money(value.long("pretaxMinusLoans")), Money(value.long("pretaxToday")), Money(value.long("pretaxAllVested")))
    }
    private fun epicBreakdownJson(value: EpicBreakdown) = JSONObject()
        .put("name", value.name)
        .put("totals", epicTotalsJson(value.totals))
    private fun decodeEpicBreakdown(value: JSONObject): EpicBreakdown {
        value.exact("name", "totals")
        return EpicBreakdown(value.string("name"), decodeEpicTotals(value.obj("totals")))
    }
    private fun epicProjectionJson(value: EpicProjection) = JSONObject()
        .put("date", value.date.toString())
        .put("growth", value.growth)
        .put("incomeTaxRate", value.incomeTaxRate)
        .put("paydownWithShares", value.paydownWithShares)
        .put("pretax", value.pretax.cents)
        .put("loans", value.loans.cents)
        .put("netPretax", value.netPretax.cents)
        .put("taxAtSale", value.taxAtSale.cents)
        .put("afterTax", value.afterTax.cents)
    private fun decodeEpicProjection(value: JSONObject): EpicProjection {
        value.exact("date", "growth", "incomeTaxRate", "paydownWithShares", "pretax", "loans", "netPretax", "taxAtSale", "afterTax")
        return EpicProjection(value.date("date"), value.string("growth"), value.string("incomeTaxRate"), value.string("paydownWithShares"), Money(value.long("pretax")), Money(value.long("loans")), Money(value.long("netPretax")), Money(value.long("taxAtSale")), Money(value.long("afterTax")))
    }
    private fun epicYearJson(value: EpicYear) = JSONObject()
        .put("year", value.year)
        .put("sharesVesting", value.sharesVesting)
        .put("vested", value.vested.cents)
        .put("unvested", value.unvested.cents)
        .put("totalBeforeLoans", value.totalBeforeLoans.cents)
        .put("loans", value.loans.cents)
        .put("netPretax", value.netPretax.cents)
        .put("costBasis", value.costBasis.cents)
        .put("taxableGain", value.taxableGain.cents)
        .put("capitalGainsTax", value.capitalGainsTax.cents)
        .put("sarOrdinaryTax", value.sarOrdinaryTax.cents)
        .put("afterTax", value.afterTax.cents)
    private fun decodeEpicYear(value: JSONObject): EpicYear {
        value.exact("year", "sharesVesting", "vested", "unvested", "totalBeforeLoans", "loans", "netPretax", "costBasis", "taxableGain", "capitalGainsTax", "sarOrdinaryTax", "afterTax")
        return EpicYear(value.int("year"), value.string("sharesVesting"), Money(value.long("vested")), Money(value.long("unvested")), Money(value.long("totalBeforeLoans")), Money(value.long("loans")), Money(value.long("netPretax")), Money(value.long("costBasis")), Money(value.long("taxableGain")), Money(value.long("capitalGainsTax")), Money(value.long("sarOrdinaryTax")), Money(value.long("afterTax")))
    }
    private fun epicWorkbookJson(value: EpicWorkbook) = JSONObject()
        .put("sharePrice", value.sharePrice.cents)
        .put("totals", epicTotalsJson(value.totals))
        .put("breakdown", array(value.breakdown, ::epicBreakdownJson))
        .put("projection", epicProjectionJson(value.projection))
        .put("years", array(value.years, ::epicYearJson))
        .putNullable("historicalVolatilityPct", value.historicalVolatilityPct)
    private fun decodeEpicWorkbook(value: JSONObject): EpicWorkbook {
        value.exact("sharePrice", "totals", "breakdown", "projection", "years", "historicalVolatilityPct")
        return EpicWorkbook(Money(value.long("sharePrice")), decodeEpicTotals(value.obj("totals")), value.objects("breakdown", ::decodeEpicBreakdown), decodeEpicProjection(value.obj("projection")), value.objects("years", ::decodeEpicYear), value.nullableString("historicalVolatilityPct"))
    }
    private fun epicJson(value: AcceptedEpicImport) = JSONObject()
        .put("id", value.id)
        .put("formatId", value.formatId)
        .put("parserVersion", value.parserVersion)
        .put("acceptedAt", value.acceptedAt.toString())
        .put("contentDigest", value.contentDigest)
        .put("workbook", epicWorkbookJson(value.workbook))
        .putNullable("replacesImportId", value.replacesImportId)
    private fun decodeEpic(value: JSONObject): AcceptedEpicImport {
        value.exact("id", "formatId", "parserVersion", "acceptedAt", "contentDigest", "workbook", "replacesImportId")
        return AcceptedEpicImport(value.string("id"), value.string("formatId"), value.int("parserVersion"), value.instant("acceptedAt"), value.string("contentDigest"), decodeEpicWorkbook(value.obj("workbook")), value.nullableString("replacesImportId"))
    }

    private fun importMetadataJson(value: ImportMetadata) = JSONObject().put("source", value.source).put("status", value.status.name)
        .putNullable("acceptedImportId", value.acceptedImportId).putNullable("attemptedAt", value.attemptedAt?.toString()).putNullable("lastAcceptedAt", value.lastAcceptedAt?.toString())
        .putNullable("safeError", value.safeError?.name)
    private fun decodeImportMetadata(value: JSONObject): ImportMetadata { value.exact("source", "status", "acceptedImportId", "attemptedAt", "lastAcceptedAt", "safeError"); return ImportMetadata(value.string("source"), value.enum("status"), value.nullableString("acceptedImportId"), value.instantOrNull("attemptedAt"), value.instantOrNull("lastAcceptedAt"), value.enumOrNull<ProviderError>("safeError")) }

    private fun planJson(value: PlanSettings) = JSONObject().put("id", value.id).put("revision", value.revision).put("birthDate", value.birthDate.toString())
        .put("referenceDate", value.referenceDate.toString()).put("retirementAge", value.retirementAge).put("endAge", value.endAge)
        .put("annualSpendingCents", value.annualSpending.cents).put("inflationBps", value.inflationBps).put("expectedReturnBps", value.expectedReturnBps)
        .put("filingStatus", value.filingStatus.name).put("stateCode", value.stateCode).put("taxPolicyId", value.taxPolicyId)
        .put("acaHouseholdSize", value.acaHouseholdSize).put("acaAnnualPremiumCents", value.acaAnnualPremium.cents).put("acaRegime", value.acaRegime)
        .put("annualPreTaxContributionCents", value.annualPreTaxContribution.cents).put("annualRothContributionCents", value.annualRothContribution.cents)
        .put("annualTaxableContributionCents", value.annualTaxableContribution.cents).put("incomeStreams", array(value.incomeStreams, ::incomeJson))
        .put("homeDisposition", value.homeDisposition.name).put("annualHsaContributionCents", value.annualHsaContribution.cents).put("annualMedicalSpendingCents", value.annualMedicalSpending.cents).put("homeRealAppreciationBps", value.homeRealAppreciationBps).put("volatilityScaleBps", value.volatilityScaleBps).putNullable("spouseBirthYear", value.spouseBirthYear)
    private fun decodePlan(value: JSONObject): PlanSettings {
        if (!value.has("spouseBirthYear")) value.put("spouseBirthYear", JSONObject.NULL)
        value.exact("id", "revision", "birthDate", "referenceDate", "retirementAge", "endAge", "annualSpendingCents", "inflationBps", "expectedReturnBps", "filingStatus", "stateCode", "taxPolicyId", "acaHouseholdSize", "acaAnnualPremiumCents", "acaRegime", "annualPreTaxContributionCents", "annualRothContributionCents", "annualTaxableContributionCents", "incomeStreams", "homeDisposition", "annualHsaContributionCents", "annualMedicalSpendingCents", "homeRealAppreciationBps", "volatilityScaleBps", "spouseBirthYear")
        return PlanSettings(value.string("id"), value.long("revision"), value.date("birthDate"), value.date("referenceDate"), value.int("retirementAge"), value.int("endAge"), Money(value.long("annualSpendingCents")), value.int("inflationBps"), value.int("expectedReturnBps"), value.enum("filingStatus"), value.string("stateCode"), value.string("taxPolicyId"), value.int("acaHouseholdSize"), Money(value.long("acaAnnualPremiumCents")), value.string("acaRegime"), Money(value.long("annualPreTaxContributionCents")), Money(value.long("annualRothContributionCents")), Money(value.long("annualTaxableContributionCents")), value.objects("incomeStreams", ::decodeIncome), value.enum("homeDisposition"), Money(value.long("annualHsaContributionCents")), Money(value.long("annualMedicalSpendingCents")), value.int("homeRealAppreciationBps"), value.int("volatilityScaleBps"), if (value.isNull("spouseBirthYear")) null else value.int("spouseBirthYear"))
    }
    private fun incomeJson(value: IncomeStream) = JSONObject().put("id", value.id).put("annualAmountCents", value.annualAmount.cents).put("startAge", value.startAge).put("endAge", value.endAge).put("taxKind", value.taxKind.name).put("owner", value.owner.name)
    private fun decodeIncome(value: JSONObject): IncomeStream {
        if (!value.has("owner")) value.put("owner", Owner.SELF.name)
        value.exact("id", "annualAmountCents", "startAge", "endAge", "taxKind", "owner")
        return IncomeStream(value.string("id"), Money(value.long("annualAmountCents")), value.int("startAge"), value.int("endAge"), value.enum("taxKind"), value.enum("owner"))
    }

    private fun checklistJson(value: ChecklistState) = JSONObject().put("catalogId", value.catalogId).put("checked", value.checked).put("updatedAt", value.updatedAt.toString())
    private fun decodeChecklist(value: JSONObject): ChecklistState { value.exact("catalogId", "checked", "updatedAt"); return ChecklistState(value.string("catalogId"), value.boolean("checked"), value.instant("updatedAt")) }

    private fun providerItemJson(value: ProviderItemState) = JSONObject().put("id", value.id).putNullable("accountId", value.accountId).put("status", value.status.name)
        .putNullable("attemptedAt", value.attemptedAt?.toString()).putNullable("lastAcceptedAt", value.lastAcceptedAt?.toString()).putNullable("retryAfter", value.retryAfter?.toString())
        .putNullable("error", value.error?.name).put("revision", value.revision).putNullable("revokedAt", value.revokedAt?.toString())
    private fun decodeProviderItem(value: JSONObject): ProviderItemState { value.exact("id", "accountId", "status", "attemptedAt", "lastAcceptedAt", "retryAfter", "error", "revision", "revokedAt"); return ProviderItemState(value.string("id"), value.nullableString("accountId"), value.enum("status"), value.instantOrNull("attemptedAt"), value.instantOrNull("lastAcceptedAt"), value.instantOrNull("retryAfter"), value.enumOrNull<ProviderError>("error"), value.long("revision"), value.instantOrNull("revokedAt")) }

    private fun <T> array(values: List<T>, transform: (T) -> JSONObject) = JSONArray().also { array -> values.forEach { array.put(transform(it)) } }
}

private fun JSONObject.exact(vararg names: String): JSONObject { require(keys().asSequence().toSet() == names.toSet()) { "Unexpected or missing fields." }; return this }
private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
private fun JSONObject.string(name: String) = get(name).also { require(it is String) { "$name must be a string." } } as String
private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else string(name)
private fun JSONObject.long(name: String) = get(name).also { require(it is Int || it is Long) { "$name must be an integer." } }.let { (it as Number).toLong() }
private fun JSONObject.nullableLong(name: String) = if (isNull(name)) null else long(name)
private fun JSONObject.int(name: String) = long(name).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
private fun JSONObject.nullableInt(name: String) = if (isNull(name)) null else int(name)
private fun JSONObject.boolean(name: String) = get(name).also { require(it is Boolean) { "$name must be a boolean." } } as Boolean
private fun JSONObject.obj(name: String) = get(name).also { require(it is JSONObject) } as JSONObject
private fun JSONObject.objectOrNull(name: String) = if (isNull(name)) null else obj(name)
private fun JSONObject.date(name: String) = LocalDate.parse(string(name))
private fun JSONObject.dateOrNull(name: String) = nullableString(name)?.let(LocalDate::parse)
private fun JSONObject.instant(name: String) = Instant.parse(string(name))
private fun JSONObject.instantOrNull(name: String) = nullableString(name)?.let(Instant::parse)
private inline fun <reified T : Enum<T>> JSONObject.enum(name: String) = enumValueOf<T>(string(name))
private inline fun <reified T : Enum<T>> JSONObject.enumOrNull(name: String) = nullableString(name)?.let { enumValueOf<T>(it) }
private fun <T> JSONObject.objects(name: String, transform: (JSONObject) -> T): List<T> {
    val array = get(name).also { require(it is JSONArray) { "$name must be an array." } } as JSONArray
    return List(array.length()) { transform(array.get(it).also { item -> require(item is JSONObject) } as JSONObject) }
}
