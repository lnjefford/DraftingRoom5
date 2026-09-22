# DR5-067 — Native Retirement architecture and parity contract

Status: **phone-only architecture approved on 2026-09-19**; executable reference contract prepared. The Plaid/vault implementation and its verification boundaries are recorded in [DR5-070](DR5-070.md); property integration and remaining device/release gates belong to DR5-072/077/078. Read with [the approved handoff](README.md), [the parity contract](parity/README.md), and [validation evidence](VALIDATION.md).

## Decision D1: where provider credentials live

**Selected: direct native provider calls with personal credentials entered on the phone.** The user explicitly approved this after discussing Android Keystore, transient plaintext exposure and provider guidance. This supersedes the earlier companion/hosted/local-only options and the initial preference for option A. No companion, hosted proxy, host/operator selection or separate deployment is required. The user supplies their own provider credentials and subscriptions through private in-app entry, never through chat, repository files or build configuration.

The sibling finance application is a local Python web server. `app/config.py` reads/writes `.env.local`; `integrations/plaid.py` creates Link tokens, exchanges public tokens and calls provider APIs; `db/migrations/012_plaid.sql` stores access tokens as ordinary TEXT. The native port implements equivalent requests directly over HTTPS, replacing plaintext configuration/database token storage with the encrypted vault below. We inspected source, not `.env.local`, databases, personal workbooks or exports.

This is a personal-device deployment decision, not a claim of provider endorsement. Plaid documents [server-side exchange and token storage](https://plaid.com/docs/identity/add-to-app/), and RentCast's [security guidance](https://developers.rentcast.io/reference/security) recommends keeping keys out of mobile clients. User approval accepts that departure; it does not change provider account eligibility, API permissions, Android Link/OAuth registration or contractual requirements. DR5-070 must verify the actual native Link/exchange flow under an eligible account; if the provider rejects it, document the concrete rejection rather than silently adding a backend or disabling the security boundary.

[Android Keystore](https://developer.android.com/privacy-and-security/keystore) protects an encryption key, potentially with secure hardware. API credentials encrypted with that key must still be decrypted briefly for provider requests. A compromised app process can access those plaintext credentials; secure storage does not remove that risk. No shared developer secret is shipped in any APK. Each installation is provisioned by its user, and sharing the APK shares no credentials. Provider requests necessarily leave the phone for Plaid/RentCast; planning, workbook parsing and forecasting remain local.

### Personal credential vault and lifecycle

`provider.ProviderCredentialVault` is the only owner of the Plaid client ID/secret/environment, per-item Plaid access tokens and RentCast API key. Its external callers use opaque credential/item handles. `PlaidNativeProvider` and `RentCastNativeProvider` obtain short-lived use access for request serialization; repositories, UI state models, ordinary DTOs, navigation and forecast code never receive saved plaintext secrets.

Generate an AES-256-GCM wrapping key inside Android Keystore; prefer hardware-backed TEE/StrongBox when available, verify the actual security level, and never claim hardware protection when only software backing is available. Do not derive the key from an app constant/password or persist it alongside ciphertext. Each encryption uses a fresh random 96-bit IV, a 128-bit authentication tag and authenticated metadata binding schema version, provider, environment, local credential ID and revision. Store ciphertext and envelope metadata atomically in `noBackupFilesDir/retirement-credentials/`; never in Room account metadata, SharedPreferences backup, external/shared storage or Fitness documents. Tampering/decryption failure fails closed without logging ciphertext or exceptions containing request data.

Require a configured device screen lock for provisioning. Credential viewing is not a product feature: display only configured/not configured and masked nonsecret identifiers; replacement/removal requires device authentication. Typed/pasted credential fields are transient, masked, excluded from autofill/keyboard learning/recents/screenshots and saved-instance state; process death requires re-entry. No app clipboard monitoring or copying secrets back to the clipboard. Keep decrypted data scoped to each call, wipe mutable buffers where possible, and acknowledge that platform/HTTP string copies cannot be reliably erased from a managed heap.

For approved background refresh, the encryption key must not require a fresh biometric prompt on every use. Workers can use the vault in the normal credential-encrypted app context after the device has been unlocked since boot; this permits scheduled refresh while the screen is subsequently locked. Before first unlock, on unavailable/invalidated key, or after credential removal, return `NeedsCredentials`/retry-after-unlock without fallback to plaintext. This background tradeoff is explicit; a future per-use authentication mode would require foreground-only refresh.

Credential changes bump a vault revision and invalidate older in-flight requests. New ciphertext is durably stored before switching the active handle; old credentials are removed only after successful replacement. Link exchange is a one-time network operation: persist the returned access token into an encrypted pending-item record before account review. A restart resumes a durable pending item or restarts Link, never assumes re-exchange is safe after an ambiguous response. Bind a pending item to a Link attempt and local credential profile; only explicit review commits accounts. Expired/cancelled pending items are revoked when possible and their local token removed. Do not expose the pending token in the ordinary import/review draft.

Disconnect cancels item work and tries provider revocation; if offline, clearly distinguish local removal from remote revocation and provide provider-dashboard revocation instructions. Local removal deletes the vault entry even if remote revocation fails; it never falsely claims remote access was revoked. Key loss, uninstall or device transfer requires re-entering credentials/relinking; accepted financial snapshots remain readable when only credentials are lost. No credentials are restored from backups. Provider-side rotation/revocation remains available to the user.

Credential setup/replacement is an inline step/sheet within Connect/reconnect/account attention or Find/Edit property. It adds no general settings or Data sources destination. Missing RentCast credentials offer manual property entry; missing Plaid credentials offer manual accounts. Never ask the user to send API keys through a Codex conversation.

## Native ownership and persistence

Use `dev.draftingroom5.retirement` with `domain`, `data`, `provider`, `importer`, `forecast`, and `ui` packages. These are proposed Kotlin owners, not classes added by this task. Fitness `AppRepository`, current document format, compatibility behavior fixed in v0.26.1, and recovery snapshots remain independent.

| Approved source | Kotlin owner / immutable domain value | Write authority and readers |
| --- | --- | --- |
| Plaid balance/holdings | `AccountsRepository`, `Account`, `BalanceSnapshot`, `HoldingSnapshot`, `ProviderItemState` | `PlaidNativeProvider` uses `ProviderCredentialVault`; `AccountSyncCoordinator` commits complete normalized batches. Account/Assets/Overview/forecast read accepted generations. |
| Manual balance | `AccountsRepository.appendManualBalance`, `Money`, `BalanceSnapshot` | Explicit dated user save; connected balances cannot be overwritten manually. |
| Local classification/name/owner/inclusion | `AccountsRepository.updateClassification`, `AccountRevision` | Local revision, independent of provider metadata; sync cannot undo confirmed choices. |
| RentCast value/range/comparables | `PropertiesRepository`, `PropertyValuationSnapshot` | `RentCastNativeProvider` uses `ProviderCredentialVault`; `PropertySyncCoordinator`; address/facts must be confirmed before linking. |
| Local property/mortgage/ownership | `PropertiesRepository`, `PropertyRevision`, `MortgageTerms` | Atomic local edit; market estimate is read-only. Manual valuation is a separately labeled source. |
| Shareworks Epic workbook | `EpicImportRepository`, `AcceptedEpicImport`, `EpicAnnualValue`, `ShareClassPosition` | `ShareworksImporter` proposes validated import; explicit replacement confirmation commits it. No local Epic assumptions. |
| Local forecast settings | `PlanRepository`, `PlanRevision`, `IncomeStream`, `HomeDisposition` | Validated whole-form save; `ScenarioDelta` applies with expected base revision. |
| Forecast results | `ForecastCoordinator`, `ForecastInputGeneration`, `ForecastResult` | Pure `RetirementEngine`, `FederalTaxPolicy`, `WisconsinTaxPolicy`, `AcaPolicy`, `MortgageCalculator`, `WithdrawalPolicy`; coordinator alone publishes. |
| Bundled library links | `RetirementLibraryCatalog`, `LibraryEntry(id,url,title,reviewedOn)` | App release only; government-first content review before shipping. |
| Private checklist | `LibraryChecklistRepository`, `ChecklistState` | Local boolean state keyed by catalog ID, no attachments/private documents/free-text vault. |

Use a separate SQLite database (`RetirementDatabase`) in `noBackupFilesDir/retirement/`, not a growing financial subtree in the Fitness JSON document. DR5-069 implements the version-1 schema directly through Android's platform SQLite API, avoiding an additional generated persistence layer while retaining the same typed repository boundary, foreign keys, WAL, atomic transaction owner and monotonically increasing `dataGeneration`. One transaction owner controls all repositories and one connection pool. Do not copy the reference migration chain or its permissive JSON metadata. Define one clean Retirement schema. Preserve all existing production Fitness readers and hotfix history.

Contract tables (all IDs opaque UUIDs except sequence/generation counters):

| Table/value | Required fields and invariants |
| --- | --- |
| Account | id; origin MANUAL/PLAID/PROPERTY/EPIC; archivedAt; current local revision. Provider identity is unique `(localCredentialProfileId,environment,itemId,providerAccountId)`; masks/names are never identity. |
| AccountRevision | id/accountId/revision; displayName (1–120), owner SELF/SPOUSE/JOINT; type; taxTreatment; includedInForecast; effectiveAt; previousRevisionId. |
| BalanceSnapshot | id/accountId; asOfDate; acceptedAt UTC; sequence; amountCents; nullable basisCents; source; batchId; supersedesId optional. Ordering is `(asOfDate,sequence)`; backdated saves do not replace a newer balance. |
| HoldingSnapshot | batchId/accountId/securityId; decimal share quantity; priceCents/basisCents nullable; assetClass. Explicit complete/unsupported/pending state distinguishes no holdings from absent response. |
| PropertyRevision | propertyId; address/facts; owner; ownershipBps 0–10000; mortgage revision; automaticValueEnabled; forecast inclusion; valuationId. Unique property account wrapper; never a second asset. |
| PropertyValuationSnapshot | propertyId; estimateCents; optional paired low/high; comparableCount; provider/source; providerAsOf optional; acceptedAt; sequence; batchId. Low ≤ estimate ≤ high, nonnegative values, missing range remains unknown. |
| MortgageTerms | outstandingCents; asOfDate; originalPrincipal optional; annualRateBps 0–2500; paymentCents; remainingMonths 0–600; contractual term. User mortgage balance is not inferred from RentCast. |
| AcceptedEpicImport | importId; formatId/parserVersion; acceptedAt; content digest (private); normalized derived totals, breakdown, annual rows, workbook assumptions; replacesImportId. Active import pointer unique. No raw bytes/path/URI/filename. |
| PlanRevision | birthDate/referenceDate, retirementAge/endAge; spending/inflation/market assumptions; filing/state/taxPolicyId; ACA household/premium/regime; SS/pension timing and amounts; split contributions; home disposition. No Epic position/growth/tax/sale controls. |
| ProviderItemState | opaque item ID; status READY/ATTENTION/OFFLINE/RATE_LIMITED/UNSUPPORTED; attemptedAt; lastAcceptedAt; retryAfter; safe error enum; revision; revokedAt. No provider tokens or error body. |
| AcceptedBatch / generation | requestId/idempotencyKey; scope; source revision; accepted sequence; dataGeneration; canonical payload digest (private). A unique accepted operation key makes retries no-ops. |

Account type values preserve ordinary employer/IRA/HSA/brokerage/cash/CD/crypto/profit-sharing semantics. Property and Epic are origin-specific wrappers. Social Security and pension future benefits are `IncomeStream`s, not current liquid account balances; prevent adding them twice. Classification review is required for unfamiliar subtypes; do not silently accept the reference brokerage fallback. Keep tax treatment distinct from display group and drive forecast buckets from the confirmed classification, not merely the provider subtype.

### Money, totals and snapshots

`@JvmInline value class Money(val cents: Long)` uses checked addition/subtraction. Boundary parsing uses `BigDecimal` from decimal text, `HALF_UP` at cent conversion, then `longValueExact`; reject nonfinite values, exponent notation, malformed grouping, ambiguous double negatives and values outside supported range. Quantities/rates are decimal strings or basis points, not persisted Double dollars. Define supported nonnegative single-asset maximum 100,000,000,000,000 cents and use checked aggregate arithmetic; reject overflow rather than wrapping. Negative equity is valid. Currency is USD in v1; reject non-USD provider payloads, never silently sum currencies.

Money text supports plain digits, one leading minus or enclosing parentheses, one dollar sign, conventional thousands grouping and at most a decimal fraction; fractional cents round half away from zero. Formatting uses integer/decimal arithmetic, not `cents / 100.0`. `null` balance/basis is unknown, not zero. Provider zero balances are valid data.

History is append-only in normal operation: no UPDATE/DELETE on accepted balance, valuation or import records; corrections append with lineage. SQL constraints/triggers and repository tests enforce this. Archive excludes an entity from current totals without erasing history; explicit whole-Retirement deletion is a separate destructive user action, not routine history maintenance. On reinstall/device restore with no retirement database, show honest empty/setup state.

`AssetAggregator` counts one contribution per origin: ordinary included current balance + Epic vested current value minus workbook loans + owned property equity. Holdings are a breakdown, not extra money. Current tracked Assets total includes active tracked assets; forecast inclusion affects the separately labeled forecast eligible total. Epic unvested value is detail/projection information, not current net worth. Ownership applies to both home and associated mortgage: `HALF_UP((value - mortgage) * ownershipBps / 10000)`. Preserve underwater equity in totals, while spendable sale proceeds cannot be negative. Map property wrapper to the same equity result; never sum wrapper snapshot plus property again. Different-date sources remain visibly dated.

### Atomic commits and coherent computation

1. Fetch/parse outside the database transaction into a bounded immutable staging object; validate every field, pagination completeness, ownership, currency, and response revision. No partial holdings delete/replace.
2. On confirmation/sync, enter one transaction, verify expected entity/provider/local revisions and idempotency key, append snapshots and normalized holdings/import records, update active pointers and successful freshness together, increment generation exactly once. Commit then emit one observable state. Crash/failure rolls everything back.
3. Errors update attemptedAt/safe error state separately without advancing value freshness or changing accepted values. Empty/partial responses do not remove accounts or clear holdings. Omission only means removal if an explicitly complete authenticated response establishes it and reconciliation policy allows it.
4. Same provider operation/page retried is a no-op. Same-day changed value appends once per distinct accepted source revision; same-day identical repeated request adds nothing. Two items are separate batches but each committed generation is readable. Never splice an in-flight batch into totals.
5. `ForecastInputRepository.capture()` reads one database transaction: generation, ordered IDs/revisions of accounts/balances/holdings/properties/mortgages/import/plan, reference date, engine version, tax policy version, deterministic return-tape/seed identity. Missing mandatory inputs produces `NeedsData`, not hidden defaults.
6. Compute on `Dispatchers.Default` in bounded cancellable chunks. Check cancellation between years/path blocks. Publish only if both generation and computation request ID still match. A generation change cancels/restarts; a stale result may remain visible with its input timestamp and stale flag, but must not be labeled current beside new totals. Never publish partial paths.
7. A scenario is `(baseGeneration,scenarioId,typedDelta)`. Preview is isolated, apply CAS-checks base plan revision and writes only the displayed delta; stale preview requires regeneration. Settings draft edits do not trigger persisted generations until explicit save.

WorkManager uniqueness is scheduling assistance, not a data lock. Database operation keys and revision checks arbitrate simultaneous manual refresh, worker retry, reconnect, and foreground imports. Relink invalidates old request revisions; late results cannot resurrect revoked items or overwrite local classifications.

## Provider and file contracts

Proposed suspend interfaces, implemented by native adapters under approved D1:

```kotlin
interface AccountsProvider {
    suspend fun beginLink(request: LinkRequest): LinkSession
    suspend fun completeLink(sessionId: String, publicToken: EphemeralToken): LinkReview
    suspend fun fetchSnapshot(itemId: String, expectedRevision: Long): AccountBatch
    suspend fun beginRelink(itemId: String, expectedRevision: Long): LinkSession
    suspend fun disconnect(itemId: String, operationId: String): DisconnectReceipt
}
interface PropertyProvider {
    suspend fun lookup(query: AddressQuery): List<PropertyCandidate>
    suspend fun estimate(confirmedProperty: PropertyIdentity): PropertyEstimate
}
interface ShareworksImporter {
    suspend fun parse(input: BoundedWorkbookStream): ValidatedEpicCandidate
}
```

`Result` values use a sealed failure enum: offline, cancelled, authentication required, rate limited with retry time, unsupported, invalid response, conflict, unavailable. Error strings come from local resources, never arbitrary upstream bodies. DTOs carry schema version, request ID, source revision, source timestamp and completeness; financial fields are integer-cent JSON values parsed as Long (never via Double). Decimal quantities use strings. Reject unknown critical versions and invalid enum values. Native adapters normalize provider decimal amounts with BigDecimal into integer cents; do not decode upstream dollars through Double. Bind each request to the local credential profile, environment, vault revision and item ownership. Provider responses need not supply a monotonic revision: use local request sequence/CAS checks plus provider timestamps to reject superseded responses; do not invent provider freshness.

Phone-side native adapters create/relink Link tokens, exchange temporary public tokens, fetch read-only provider data, normalize complete bounded responses and perform disconnect. Link/public tokens remain in memory; persistent provider credentials and access tokens go only into `ProviderCredentialVault`. Bind attempts to local profile/nonce/expiry, validate registered Android/OAuth callbacks and recover pending exchange state as specified above. Explicitly allowlist provider HTTPS origins, prohibit credential-bearing redirects and disable request/response-body logging. Never put tokens in URL parameters, navigation state, WorkManager input, analytics or crash breadcrumbs. Provider HTTP transport must not retain request bodies in caches or disk traces. Payment/transaction initiation endpoints are absent.

All UI, classification, snapshots, manual/property/mortgage data, workbook parsing and forecasts are native. Shareworks, plan settings and checklist data are not uploaded. RentCast receives the confirmed address for lookup; disclose this in that flow and request `suppressLogging=true` for supported calls to suppress provider-side query logging. Endpoint-restricted RentCast keys reduce access scope; do not require fixed IP restrictions that would prevent a phone switching networks. No custom server is present to operate or back up.

Reference Plaid uses investments holdings only; do not promise checking/savings or all institutions are supported by that endpoint. DR5-070 must validate required products/permissions against selected provider subscription; unsupported cash accounts retain manual fallback. No name/mask heuristic may merge manual and provider records without explicit user confirmation. Unsupported holdings remain labeled unavailable.

### Shareworks retention and validation

Use Android document picker for one `.xlsm` stream; extension alone is not validation. No broad storage permission, recursive folder scan, persisted URI grant, macro execution, formula evaluation, external relationship fetch, or workbook upload to a service. Parse only allowlisted OOXML entries/cells with DTD/external entity processing disabled. Enforce initial limits: compressed 20 MiB, expanded 100 MiB, 2,000 entries, ratio 100:1, 500 annual columns; reject encrypted archives, path traversal, duplicates and oversized XML. Tune only with sanitized evidence in DR5-073.

Required template `2026 Consolidated Statement` and `Inputs and Summary`; annual detail/price history have explicit availability states. Map E6, rows 12–18 and totals row 19, summary C9–C13/C38/C41/C44/C47, detail year row 15 and rows 19/23/24/31/36/37/46/47/48/49/51. See cell-address fixture. Read saved cached values only. A required formula without a numeric cached value, NaN, malformed required money, duplicate year, invalid date, or inconsistent total fails the entire candidate. Optional missing history means uncertainty unavailable, not zero volatility. Missing forecast years means unavailable forecast until the workbook covers them; do not extrapolate manually editable Epic growth. Native code never evaluates or invents workbook tax/sale math.

Workbook projection date controls Epic outputs; if local retirement date differs or lacks a covered workbook year, show an input mismatch and require an updated workbook. Workbook-derived uncertainty may be used only with a documented deterministic model and fixture; no arbitrary Epic volatility control in settings.

Keep raw stream in memory where bounded; if seekable staging is necessary, create a random file under noBackupFilesDir/retirement-import-temp, delete in `finally` on success/failure/cancel and sweep abandoned files on startup. Never copy original filenames (which may contain names) into import metadata. Retain normalized accepted records and private content digest for replacement/idempotency; retain no worksheets, formulas, original paths or unneeded cells. History retains derived values only. Confirmation shows derived summary locally; production capture/recents must conceal sensitive screens using FLAG_SECURE for the Retirement activity/window scope, restoring Fitness behavior on exit. Automated screenshots use fabricated state only.

### Background and privacy boundaries

`RetirementAccountSyncWorker`: unique per local credential profile/item, connected network, ordinary approximately daily freshness attempt plus explicit refresh; honor provider limits. `RetirementPropertySyncWorker`: unique weekly eligibility check plus explicit refresh; no automatic work when disabled/no confirmed address. No exact alarm, wake lock, constant polling or always-on foreground service. Exponential backoff for transient failures; stop retry loops on authentication/invalid data; respect retryAfter. Cold start may enqueue overdue work but must never block UI or read a URI from a previous import. Platform delays, unavailable credentials and offline connectivity are visible as stale/attention state; workers never prompt for credentials or spin while locked. Android [work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) run subject to constraints, so cadence is not a deadline.

| Surface | Required exclusion and verification |
| --- | --- |
| APK/repository | No provider secret or real fixture. Only this task's hand-authored synthetic inputs and source hashes are added. Later release gate scans source/resources, merged manifest, APK strings/assets and generated BuildConfig with synthetic canary credentials; no live key needed for tests. |
| Local storage | Retirement database including WAL/SHM, encrypted credential vault, drafts, import staging and forecast cache live under no-backup storage. Keystore protects the vault encryption key; local device sandbox is not a claim of protection against a compromised/unlocked device. |
| Automatic and device-transfer backup | Existing `backup_rules.xml` and `data_extraction_rules.xml` include only current-backups/latest.json, previous.json and status prefs. Never add Retirement to those files or Fitness `AppDocumentCodec`; explicitly test both transport paths and all database/journal/draft paths in DR5-069/077. |
| User export/recovery | Current Fitness document backup contains no Retirement. No new Retirement export is approved here. Reinstall restores Fitness, requires credential re-entry/provider reconnect and workbook re-import; local manual financial history is not restored. Any later financial backup needs a separate explicit encrypted export design. |
| Logs/errors/analytics | Allow only operation kind, safe enum, duration bucket and unrelated random diagnostic ID. No values, addresses, institutions/account names, URIs, filenames, payloads, tokens, digests or exception messages. Apply the same policy to native adapters, vault, HTTP interceptor, WorkManager and crash reporting. |
| Navigation/Bundle | Only route enums and opaque IDs; sensitive form drafts stay in local no-backup draft store with revision/expiry, not SavedStateHandle text. Expired or missing draft asks for re-entry. Link/upload sessions never auto-replay after restoration. |
| Test artifacts | Synthetic deterministic fixtures only; no real-account device screenshots, raw file excerpts or network captures. Test data generation never accesses finance/data, finance/Epic, .env files or personal exports. |

The original DR5-067 fixture-only work did not prove APK/log/backup behavior. DR5-070 now implements the phone-only Plaid/vault boundary; its executable checks and remaining limitations are recorded separately. DR5-072/073/077/078 retain the other runtime and release gates. Do not replace device validation with a string search claiming security.

## Route graph and screen owners

Extend the current explicit `AppNavigationState`/primitive saver approach with `WorkspaceId` and separate remembered stacks for Fitness and each Retirement tab. Do not hardcode every stack root to `AppRoute.Dashboard` as current normalization does. Root tabs: `Overview`, `Forecast`, `Assets`; top-bar Library/settings open on the current tab's stack so Back returns to its caller. Switch workspace preserves tab and stack; system Back pops the active stack, then returns to Overview from another root tab, then exits normally. No loops or duplicate consecutive destinations.

| Route / approved screen | Kotlin state owner | Default parent / dependencies |
| --- | --- | --- |
| Overview | `OverviewViewModel` | Retirement root; `AssetAggregator`, forecast and item freshness; map → Assets, target → Forecast, attention → affected detail |
| Forecast | `ForecastViewModel` | Root; `ForecastCoordinator`, `PlanRepository` |
| Assets | `AssetsViewModel` | Root; `AssetAggregator`; tax table → Accounts |
| Accounts | `AccountsViewModel` | Assets; accounts/properties/Epic repositories; grouped list without total/tax summary |
| AccountDetail(accountId), account attention state | `AccountDetailViewModel` | Accounts; account revision, snapshots, holdings, provider item error; missing item → nearest valid parent |
| BalanceUpdate(accountId,draftId) | `ManualBalanceViewModel` | AccountDetail; append manual snapshot only |
| BalanceHistory(accountId) | `BalanceHistoryViewModel` | AccountDetail; dated source history |
| AccountEdit(accountId,draftId) | `AccountEditViewModel` | AccountDetail; local classification/owner/inclusion; provider identity read-only |
| Reconnect(accountId,attemptId) | `LinkViewModel` | AccountDetail; affected item only, ephemeral external Link activity |
| AddAsset | `AddAssetViewModel` | Accounts; Connect, ManualAccount, FindProperty |
| Connect(attemptId) | `LinkViewModel` | AddAsset; `AccountsProvider`, manual fallback |
| ReviewLinkedAccounts(reviewId,draftId) | `LinkReviewViewModel` | Connect; explicit selection/classification and one atomic commit |
| ManualAccount(draftId) | `ManualAccountViewModel` | AddAsset; local account + initial snapshot transaction |
| FindProperty(draftId) | `PropertySearchViewModel` | AddAsset; provider lookup or manual entry |
| ConfirmProperty(draftId) | `PropertyConfirmViewModel` | FindProperty; confirmed match/mortgage/ownership → transaction |
| PropertyDetail(propertyId) | `PropertyDetailViewModel` | Accounts; value/range/facts/freshness/mortgage/equity/history |
| PropertyEdit(propertyId,draftId) | `PropertyEditViewModel` | PropertyDetail; local revisions and automatic-value toggle |
| PropertyHistory(propertyId) | `PropertyHistoryViewModel` | PropertyDetail; append-only valuations |
| EpicDetail | `EpicDetailViewModel` | Accounts; accepted workbook position/loans/vesting/projections |
| EpicUpload(draftId) | `EpicImportViewModel` | EpicDetail; picker, validation, summary, replacement confirmation; no saved URI |
| ForecastSettings(draftId) | `ForecastSettingsViewModel` | Caller Forecast or current tab via gear; one inline form/explicit save |
| ForecastRisk(resultId) | `ForecastRiskViewModel` | Forecast; real failure timing/causes, no invented score |
| ScenarioDetail(resultId,scenarioId) | `ScenarioViewModel` | ForecastRisk; isolated comparison and CAS delta apply |
| Library | `LibraryViewModel` | Current tab via book; catalog and checklist |

Deep restoration stores workspace/tab + ordered typed route descriptors with opaque IDs, validates each parent/entity/draft, and trims from the first invalid node. A standalone restored detail synthesizes its canonical parent chain. A deleted account or stale result must not crash. Task completion replaces task routes with the resulting detail, Back must not re-submit a mutation. Restored external Link/upload resolves attempt state or restarts with user action; it never re-exchanges a token. Same Save twice and process death after commit must be idempotent. Test each route encode/decode, parent closure, unknown route rejection, stack isolation, missing entity and back traversal in DR5-068/071/075/077.

No Strategy, DataSources, general RetirementSettings, PropertiesOverview or rental destinations exist. Every named VM renders loading/empty/last-good-stale/error states and uses Retirement-scoped evergreen tokens. Compact/tall/landscape/large-text/TalkBack screenshot and semantics verification belong to the implementation tasks, not this architecture-only change.

## Reference differences that must remain visible

The parity runner pins actual source hashes because `../finance` has no Git metadata. It does not infer correctness from comments or declare the model current tax law.

| Finding | Port decision / implementation gate |
| --- | --- |
| Federal module uses 2026 tables but several tests describe 2024 arithmetic. WI uses 2024 tables and an unphased standard deduction; ACA uses 2024 FPL/step schedules. | Freeze as `finance-reference-v1` regression fixtures only. Version federal/state/ACA policies separately; DR5-074 must validate chosen contemporary law against primary sources and label assumptions before shipping. No silent year upgrade. |
| Federal unused standard deduction is not applied to LTCG; NIIT model covers LTCG only. WI approximation can understate tax. | Preserve reference outputs as known limitations, not financial promises; corrected policy needs separate expected fixtures and documented reason. |
| Engine ACA uses a cross-path median gain ratio to estimate MAGI; tax gross-up uses two iterations plus final withdrawal. | Golden legacy behavior is recorded; DR5-074 must either explicitly retain labeled approximation or adopt per-path converged tax/ACA with residual/convergence tests. Never conceal tax underfunding behind loose tolerance. |
| Default strategy locks Roth/traditional until whole-year age 60; HSA draw is capped to modeled medical spending; RMD table/SS taxation are approximations. | Internal policy only, no Strategy screen. Add exact age/date policy tests for selected implementation; do not promise penalty or eligibility advice. |
| Engine buckets primarily follow account type rather than editable tax treatment. | Confirmed local classification must consistently determine tax bucket; flag inconsistent combinations instead of showing one treatment and modeling another. |
| Money parser strips arbitrary grouping; RentCast/property/Epic use Python rounding; invalid workbook values become zero. | Strict decimal HALF_UP boundary and fail-closed required workbook fields are intentional native corrections. Fixture lanes identify reference vs target behavior. |
| Property model permits negative equity but `_append_equity_snapshot` floors it to zero. | Native aggregator/history retain negative equity, with zero floor only for spendable liquidation proceeds. |
| Plaid name/mask/type matching can merge an unrelated account; incomplete responses replace holdings; item status writes are a separate transaction. | Strong identity + explicit review; complete batch and successful timestamp commit atomically. |
| Shareworks reference keeps filename/path and defaults missing projections/growth; supports manual Epic fields. | No raw filename/path or local Epic override, no silently invented forecasts. |
| Engine property liquidation is before that year's growth; workbook Epic transfer is after growth. Initial index is current-age start. | Preserve explicitly tested timing; golden return-tape cases test ordering. Show available-at-retirement on the retirement boundary, avoiding double-counting the post-transfer year. |
| Runtime floating-point simulation and RNG differences across Python/Kotlin. | Persist exact cents; use bounded finite Double real-dollar simulation internally, round only at published cents. Compare exact integer outcomes where defined; use shared return tapes, not an assumption that equal seeds imply equal draws. |

DR5-074 must add full-engine tests for SS/pension onset, RMD transfer conservation, inflation conversion, contribution timing, home/Epic retirement transfer, failure with locked assets, tax convergence, seeded repeatability, cancellation and device performance. This task supplies representative executable outputs, not a finished engine certification.

## Phone-only implementation gates

DR5-070 owns vault/Android Keystore integration, inline credential setup, direct Plaid Link/exchange/relink, encrypted pending items and fake-provider contract tests. DR5-072 reuses that vault/transport for direct RentCast with scoped credentials and no companion. DR5-077/078 must test key invalidation, ciphertext tampering, IV uniqueness, credential replacement during sync, boot-before-unlock, locked-screen background access, device transfer, uninstall/reinstall, cancelled/ambiguous exchange and offline revocation. Use fake transport and synthetic canaries to assert absence from APK/resources, repository, logcat, HTTP traces, work input/output, Bundle, database records, backups and screenshots. Exercise actual Android Keystore on supported API/device configurations; ordinary Python tests cannot certify it. No live keys enter test artifacts.
