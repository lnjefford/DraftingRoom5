# DR5-079 — Retirement workspace release

Release task: `01a0c90c-6965-7232-9a55-df49cc438fb7`. The coherent DR5-067–078 implementation and audit corrections ship as **v0.27.0 / 27002**. This record supplements the independent [DR5-078 audit](../DR5-078/README.md); it does not relabel skipped runtime coverage as passing.

## Shipped product and boundaries

The release adds the native evergreen Retirement workspace without changing the Fitness workspace's model or visual identity. It includes manual and read-only linked accounts, automatic/manual property values, local non-retaining Shareworks import, coherent Overview and Assets aggregation, Forecast/settings/risk/scenarios, and a bundled government-first Library with a local private checklist. Strategy, rental priorities, separate Properties/Data sources/general Retirement settings, and editable Epic assumptions remain intentionally absent.

Plaid and RentCast use user-entered on-device credentials protected by an Android Keystore-backed vault and fixed-origin HTTPS adapters. This personal-device topology is user approved but departs from provider server-side guidance. Retirement data, credential envelopes, transient drafts, provider responses, raw workbook bytes/URIs/names, and secrets remain outside ordinary backup and must not enter routes, Bundles, WorkManager payloads, logs, screenshots, fixtures shipped in the APK, or diagnostics. Shareworks parsing is local and persists only validated derived values and safe metadata.

Forecasts use real, inflation-adjusted dollars, integer-cent publication, versioned reference tax/ACA approximations, coherent input generations, and displayed source/revision age. They are planning estimates—not financial, tax, legal, or investment advice, current-law certification, or a promise of future results. Android background intervals are inexact requests, not refresh deadlines; last-good data and scoped freshness/error state remain visible.

## Accepted limitations

- **Android 17 runtime coverage did not run.** Android 17+ (API 37) is the supported scope, but two official API 37 emulator images and multiple graphics modes crashed SurfaceFlinger before app installation. API 37 provider/import/forecast/WorkManager/privacy/UI, Fitness progression, and widget runtime behavior is therefore not certified. Earlier API 35 emulator evidence and host/static API 37 checks are narrower evidence. Emulator testing is accepted; Android 9 testing is outside the supported scope.
- **Live Plaid Link/token exchange did not run.** Synthetic fixtures and provider-contract tests do not certify eligible-account Link/OAuth, exchange, institution behavior, products, quotas, relink, or remote revocation. Live RentCast, a real customer workbook, physical-device hardware Keystore, real backup/device transfer, OEM recents, TalkBack speech, hardware keyboard/D-pad, and external picker/process-kill behavior also remain unverified as detailed in DR5-078.
- The API 37 D8 packaging warning is retained rather than suppressed. Static package, privacy, signature, and 16 KB alignment checks do not substitute for runtime qualification.

## Release verification

The final isolated Windows gate used Java 17, AGP 9.1.1, Gradle 9.3.1, Kotlin 2.2.10, SDK 37, in-process Kotlin compilation, and the exact tasks `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest validateDebugScreenshotTest`. It passed:

- **406 JVM tests in 58 suites**, with zero failures, errors, or skips.
- **443 screenshot comparisons**, with zero failures, errors, or skips. Advancing the release version initially produced exactly two expected tall Settings diffs; both were visually inspected as intact layouts with only the displayed `Version 0.27.0` label changed, regenerated, and independently revalidated.
- Lint with **zero errors and 35 warnings**; debug and instrumentation APK assembly and signing validation.
- **105** hash-pinned finance reference assertions; **14** complete forecast cases using shared tapes; **112** tax/Social Security rows; and **14** money/workbook/navigation/backup/transaction architecture contract tests. Network, personal-file, and database guards remained enabled, and no golden output/source manifest was rewritten.
- The audited forecast performance/cancellation/repeatability evidence remains the precompiled API 35 emulator result from DR5-078: 10,000 paths × 50 years in **19,653 ms**, 747 main-thread pulses, 73,074,144 bytes used against a 201,326,592-byte process maximum, cancellation within two seconds, and exact seeded path/year repeatability. This is not an Android 17 or physical-device timing claim.

The final local debug APK is **81,993,851 bytes**, SHA-256 `f36912aeb95d7fcfcc831add989aac11036954118dbcecce8109aae66f11b458`. Its decoded identity is `dev.draftingroom5`, version **0.27.0 / 27002**, minimum/target/compile SDK **37**. APK Signature Scheme v2 verification passes with one debug signer, and official `zipalign -c -P 16 -v 4` verification succeeds. The manifest disables cleartext traffic. Decoded backup/device-transfer resources retain only the three encrypted Fitness recovery allowlist entries. The production archive has zero matches for the audit's eight credential/private/fixture canaries and contains no `.xlsm`, `.xlsx`, database, SQLite, JKS, or keystore entry. These local debug-signature facts do not substitute for the distribution signature check below.

The 515-file candidate was reviewed against `v0.26.1`. No generated build output, APK/AAB, heap dump, emulator image, `.gradle-user-home`, `.tooling`, signing file, private database, or synthetic draft file is included. Candidate text contains no private-key/API-key/live-secret pattern. The two checked-in `.xlsm` archives are deterministic, macro-free, fictional parser fixtures generated from the documented script—not a raw customer Shareworks workbook—and remain test-only assets excluded from the production APK. `git diff --check` passes; line-ending conversion notices are informational.

## Delivery

The coherent milestone is committed once and delivered by tag [`v0.27.0`](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.27.0). The tag-triggered [Android release workflow](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml) completes successfully and publishes a nonempty signed `DraftingRoom5.apk`. The published attachment is downloaded independently and checked for package `dev.draftingroom5`, version **0.27.0 / 27002**, minimum/target SDK 37, signature verification, 16 KB ZIP alignment, exact size/SHA-256, and the same privacy exclusions. Exact distribution size, digest, signer digest, and workflow run URL are recorded in the GitHub release notes and final task report.
