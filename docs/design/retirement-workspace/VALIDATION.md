# DR5-067 validation and decision evidence

Inspected/resumed 2026-09-18 in executor task `01a0b767-4a6e-74e3-9018-ed5c787d8ebd`, on hotfix HEAD `81bcd56` (`Restore app data compatibility in v0.26.1`). Queue had no running claims and an empty processor lease before reclaim. The only other active listed task was the coordinating task that explicitly instructed this resume.

## Result

- 103 reference assertions pass against source pinned in `parity/source-manifest.json`.
- 14 executable contract tests pass, including six negative workbook mutations and all 24 route parent chains.
- `git diff --check` passes. New files are also checked separately for trailing whitespace/conflict markers because ordinary `git diff` omits untracked files.
- No production Kotlin, Android resource, version/build configuration, Fitness schema/codec or hotfix change was made. No commit, push, tag or release was performed.
- Existing uncommitted planning content is retained; the README only gains links to these records, and TODO only updates this task's claim/status/evidence.

During fixture development, the added property transfer test initially expected 51,000 cents by assuming a 2% cash return. It failed at 50,250 cents. Inspection of `sim/reference.py::DEFAULT_REAL_MEAN` and `engine::_bucket_allocation_matrix` confirmed the empty taxable bucket defaults to cash at 0.5% real. The frozen expected output and explanation were corrected to 50,250; all 103 assertions then passed. No calculation in the reference was changed.

The finance `.venv` could not run because its Python path points to another machine's missing installation. The bundled Python was used successfully (numpy 2.3.5, openpyxl 3.1.5, pydantic 2.13.5); no dependencies were installed into the sibling project.

## Security evidence and limits

The fixture runner disables live config/database entry points, denies network and database audit events, builds workbook bytes in memory and stubs only RentCast's response transport. Source manifest contains hashes of source files, never private values. No `.env.local`, finance database, actual Shareworks file or personal export was read/copied. Fixtures identify all people/properties/accounts as synthetic; the fake transport key is visibly `synthetic-not-a-credential`.

Existing Android backup rules are a narrow allowlist for Fitness recovery documents/status. The contract test checks both old backup rules and cloud/device-transfer extraction rules. The architecture requires all new sensitive data outside those allowlisted documents and in no-backup storage. This is a future implementation requirement, not proof of a completed financial backup boundary. No Retirement-enabled APK exists to inspect; a runtime APK/log/backup privacy claim would be premature. Gradle/UI tests were not run for this docs/fixture-only change; Kotlin/Android implementation tests remain with their owning queue tasks.

## Resolved decision D1 — 2026-09-19

The user explicitly chose phone-only operation with personally entered, Keystore-protected provider credentials after reviewing transient plaintext exposure and the departure from Plaid/RentCast server-side guidance. This supersedes the earlier A/companion discussion and resolves the deployment blocker. No host/operator selection or service deployment is required.

The architecture and product handoff now specify direct native Plaid/RentCast adapters, a separate encrypted credential vault, inline setup within approved flows, background access after first device unlock, pending Link exchange recovery, rotation/revocation, key-loss behavior, backup exclusions and explicit Android validation gates. No actual credentials were requested, read or stored, and no provider implementation was added. Python fixture checks are not proof of working Android Keystore or provider eligibility.

## DR5-069 persistence implementation — 2026-09-19

The native manual-foundation layer now uses a clean version-1 Android SQLite database at `noBackupFilesDir/retirement/retirement.db`, with WAL, foreign keys, compare-and-set generation publication, strict versioned JSON state validation, integer-cent money, and append-only balance/property/Epic ledgers protected against update/delete by triggers. Manual account/property writes, classifications, holdings, provider/import freshness, plan revisions and checklist state are repository-owned and committed atomically. Repository recreation reads the committed generation; failed and stale writes do not publish partial state.

The ordinary Fitness document and both Android backup allowlists remain unchanged. Retirement database/journals, raw Shareworks content, provider credentials, tokens, staging content and drafts are excluded. The codec persists only normalized derived Epic values and a private content digest—never workbook bytes, URI, path or filename. Reinstall/device transfer therefore restores eligible Fitness recovery files only; manual Retirement history is intentionally not restored and providers/workbooks require reconnect/re-import.

Focused Kotlin coverage is in `RetirementCodecTest`, `RetirementRepositoryTest` and `RetirementBackupBoundaryTest`; the sanitized transfer fixture is `parity/manual-foundations.json`. Live providers, Keystore, workbook parsing, forecasts and UI remain outside DR5-069.

Repeated checks passed on 2026-09-19: 103 reference assertions, 14 contract tests and whitespace validation. DR5-067 completed at 2026-09-19T12:23:54-05:00; DR5-068 is ready and next eligible dispatch is 2026-09-19T13:23:54-05:00. Only that next task was promoted. DR5-072 now also depends on DR5-070 because it reuses the credential vault/transport implemented there. Push remains NO; changes are uncommitted.

## DR5-070 provider implementation — 2026-09-19

The phone-only native provider, private credential setup, AES-GCM/Keystore vault, Link/relink review, atomic account/holdings reconciliation, safe failure metadata, manual fallback and constrained background refresh are implemented. See [DR5-070 evidence and boundaries](DR5-070.md) for the exact scope and reproduction commands.

Validation passed: 332 JVM tests; 311 screenshot comparisons; lint with zero errors and 51 warnings; application and instrumentation APK assembly; 103 reference assertions and 14 contract tests; whitespace validation; absence of four synthetic vault-test canaries from the application APK. The API 35 synthetic native audit passed actual Keystore encryption/recreation/tamper rejection (Software Keystore) and real SQLite mid-transaction rollback, atomic publication, append-only triggers and recreation. Its temporary key/database and synthetic screen lock were cleaned up. No real credentials or personal financial sources were used.

The earlier fixture-only statement that no Retirement APK exists is now historical. These checks still do not certify live Plaid account eligibility, institution OAuth/consent, hardware Keystore on a physical phone, SDK-internal logging/backup behavior, or the full multi-API lifecycle/accessibility matrix. Those explicit live/device gates remain with DR5-077/078. Version defaults remain 0.26.1 / 26003, and Push NO means no commit, push, tag or release.

## DR5-073 Shareworks import — 2026-09-20

Implemented bounded on-device `.xlsm` parsing, complete saved-result models, explicit review and atomic append-only replacement, private failure handling, read-only Epic detail/upload screens and `FLAG_SECURE` throughout Retirement. No raw workbook, filename or URI is persisted. See [DR5-073 evidence and limits](DR5-073.md).

Passed 355 JVM tests, 383 screenshot comparisons (32 new Epic references; all 351 earlier references unchanged), 105 reference assertions, 14 contract tests, app/test APK assembly, whitespace and application-APK fixture exclusion. Lint has zero errors, 51 existing warnings and four informational findings. The final API 35 native audit passed Android SAX/ZIP golden parity, real SQLite rollback/replacement/history/recreation, accessible Choose/Cancel/Replace actions and the secure-window assertion. Emulator ANRs and the corrected Compose synchronization in the test harness are documented in the task evidence.

Unflagged internally consistent stale workbook caches cannot be independently detected; the UI requires recalculation and saving in Excel. Physical-device, multi-API, TalkBack speech and full external-picker/process-kill checks remain DR5-077/078 gates. DR5-073 completed at 2026-09-20T20:42:08.8340267-05:00; only dependency-satisfied DR5-074 was promoted ready, eligible at 21:42:08.8340267-05:00. Version remains 0.26.1 / 26003; no commit, push, tag or release.

## DR5-074 forecast engine — 2026-09-20

The real-dollar core, immutable generation capture, correlated seeded Monte Carlo, workbook-only Epic input, property/mortgage projection, withdrawals, reference tax/ACA, Social Security/pension timing, typed scenarios and measured failure diagnostics are implemented. Background publication requires both the current request and committed repository generation. See [DR5-074 evidence and assumptions](DR5-074.md) and [native runtime evidence](DR5-074-native.txt).

Final-source checks passed: 379 JVM tests, 35,412 full-engine parity comparisons with zero observed cent difference (one-cent allowed tolerance; success fraction tolerance 1e-12), 112 exact-cent scalar tax/SS grid rows, 105 original reference assertions, 14 full-path replays, 14 contract tests, lint with zero errors/51 existing warnings, both APK builds, fixture exclusion and whitespace checks. The 10,000-path/50-year benchmark took 1.364 seconds on the host and 9.706 seconds on API 35; the native main thread delivered 359 heartbeat callbacks and cancellation completed within two seconds. Sampled completion heap use was 66.6 MiB within a 192 MiB maximum. The first quarter-heap guard rejection and final one-third correction are documented; no device heap or manifest largeHeap increase was used.

The policy deliberately retains labeled reference tax/ACA approximations and exposes measured tax-funding residuals rather than claiming convergence or contemporary-law certification. Physical-device/multi-API and cross-feature lifecycle/privacy gates remain open for DR5-077/078; final Forecast UI remains DR5-075. No screenshot reference, Fitness behavior, default version (0.26.1 / 26003), commit, push, tag or release was changed. Only DR5-075 is promoted ready.

## DR5-077 hardening evidence — 2026-09-21

Final source passed 394 JVM tests, 443 screenshot comparisons, lint with zero errors, debug and instrumentation APK assembly, 105 reference assertions, 14 full-path forecast replays plus 112 tax/SS rows, 14 architecture contracts, APK/logcat canary scans and whitespace validation. API 35 native audits passed Software Keystore AES-GCM/tamper checks, real SQLite provider/Epic rollback and recreation, secure-window/accessibility actions, unique versioned WorkManager scheduling, and a responsive 10,000-path/50-year forecast in 7.048 seconds. Exact evidence and remaining live/provider/device limits are in [DR5-077](DR5-077.md). Version remains 0.26.1 / 26003; no commit, push, tag or release occurred. DR5-078 is ready for independent audit.
