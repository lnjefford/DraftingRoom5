# DR5-056 — independent Phase 7 audit

Reviewer: gpt-6-astra, task `01a0a230-1fbf-7442-b691-200447a13c6c`. Claimed 2026-09-14 at the user's explicit request ahead of the queue cooldown. Shared checkout; DR5-055 was complete. This is **Push: NO** work. DR5-057 owns the version bump, commit, tag and release.

## Review scope and method

Read `AGENTS.md`, `TODO.md`, DR5-048 and DR5-055 evidence, the provider, activity integration, manifest, layouts, widget metadata and tests, and the fifty-image catalog, batch QA/provenance, current contact sheets and runtime files. Prior evidence is identified as prior evidence, not presented as a newly repeated experiment.

The earlier four JVM widget tests cover selection and static contracts but largely inspect source/XML. This audit adds a native `WidgetAuditInstrumentation` runner using Android's real `AppWidgetManager`, `AppWidgetHost`, `RemoteViews` and WebP decoder. Its test widgets use a separate host ID and are removed in `finally`, preserving pre-existing launcher widgets. The test runner is in `androidTest`; its configuration does not add test code to the production APK.

## Requirements and findings

| Requirement | Independent assessment / evidence |
|---|---|
| Exactly 50 reachable images | `audit_assets.py` matches ordered provider IDs 01–50 to exactly 50 runtime files, catalog hashes and distinct file hashes. All 50 masters and selected sources exist. No fallback/prototype resources remain in the provider. |
| Art at icon size | [Per-image visual review](visual-review.md), original 56px collection sheet, and independent 48px light/dark sheet. No duplicates, accidental text, matte or clipping found. Deliberately hidden, fragmented, transformed and mirrored numerals are permitted. |
| 21–40 preserve free silhouettes | Raw alpha bounds and disc occupancy measured independently; nine images in 21–30 and all ten in 31–40 have clearly noncircular small-size silhouettes. Provider uses `setImageViewResource` with no bitmap mask; layout has no background and uses `fitCenter`. |
| 41–50 true-black discs | Every image contains over 8,000 fully opaque exact-black pixels at 256px, with over 99% opaque coverage of the inner disc and genuinely transparent corners. Actual pixel measurements are in `asset-audit.json`; visual review confirms circular outer geometry. |
| One-cell display | API 35 Pixel Launcher capture `api35-home.png` independently shows the tile at launcher-icon scale. Prior DR5-055 picker evidence advertises 1 × 1. API 31+ metadata requests 1 × 1; legacy minimums are 40dp. Launcher grid allocation remains host-controlled. |
| Direct normal launch | Independent API 35 tap in the lower empty part of the observed tile opened `dev.draftingroom5/.MainActivity` (`api35-tap.txt`). Code uses a direct immutable activity PendingIntent with MAIN/LAUNCHER; no receiver/service trampoline or special app route. |
| Inexact hourly rotation | UTC-hour modulo 50 is stateless and bounded, including negative times and skipped callbacks. Within-hour refreshes select the same image. Android owns a 3,600,000ms repeating update; no exact-time promise. `api35-alarm-before.txt` shows an actual system ELAPSED_WAKEUP alarm with a 45-minute delivery window. |
| Battery impact | No new widget service, WorkManager job, wake lock, network request, animation or polling loop. Each refresh sends small resource references to the host; the host decodes artwork. System hourly updates can wake the device and app/boot/update/time events can cause extra refreshes. This is bounded work, not zero battery cost. No physical mAh or standby-drain measurement is claimed. |
| Process death / missed updates | Selection has no app-process state or per-widget storage. Recreated callbacks derive the current look; missed callbacks skip images rather than creating catch-up work. Hosts retain the last RemoteViews while the process is absent. |
| Multiple instances / removal | All IDs in a callback use the same timestamp and selected resource. No app-owned scheduler or per-ID state needs cleanup. Android owns registration and cancellation after last removal. Native host tests supplement prior launcher-removal evidence. |
| Resize | Fixed-size widget (`resizeMode=0`) is intentional for the one-cell brief. Host option changes still refresh. `fitCenter` preserves image aspect ratio in rectangular bounds rather than cropping it. No resize gesture is promised on Pixel Launcher. |
| Reboot / app update / restore | Manifest receives boot, package-replaced and time-change events; provider refreshes installed IDs. Restore uses current manager IDs with no stale-ID data to migrate. Prior DR5-055 reinstall/reboot/time-change captures are retained; native callback exercises are labeled as simulated callbacks, not physical events. |
| Accessibility | Full-tile root exposes “Open DraftingRoom5”; its decorative ImageView is excluded from duplicate accessibility announcements. The label remains stable even when a scene hides the numeral. API 35 hierarchy and native applied-view checks cover semantics; spoken TalkBack and switch access on hardware remain unobserved. |
| Resource cost / launcher icon | 693,818 runtime bytes total; maximum individual file 23,842 bytes, below the 40KB / 2MB art budget. Each 256px ARGB decode is approximately 256KiB; the app sends only the selected resource, not fifty decoded bitmaps. Masters/provenance are outside Android resources. The Phase 7 diff does not modify launcher-icon files. |

## Platform guidance

Android's [widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced) supports periodic provider updates, with additional refreshes from activity interactions. The [provider metadata reference](https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo) defines the inexact periodic update and host sizing contract. The observed 45-minute alarm window reinforces why exact-hour delivery must not be promised.

## Reproducing the native audit

Build `assembleDebug assembleDebugAndroidTest`, then run `run-native-audit.ps1` with the emulator serial, the directory containing the `app` build directory, and an evidence output directory. The script installs the two debug APKs, temporarily grants widget-host binding to the target package, runs the dependency-free instrumentation, exports native renders and revokes the grant. Use an emulator/test device; it does not delete existing launcher widgets.

`audit_assets.py` requires Python and Pillow and independently rebuilds the resource measurements and 48px sheet. Test scripts and review images are evidence; generated Gradle builds, SDKs, signing files and caches must remain uncommitted.

## Verification result

**PASS — DR5-056 audit complete.** The receiver mitigation and [controlled retest](retest/README.md) close the observed startup/input ANR findings. Verification includes real reboot recovery, ten successful widget launch/Settings interactions, native checks on APIs 28 and 35, a healthy natural hourly callback, and the full regression gate. Historical failures remain in [findings and evidence](findings.md).

The existing physical-device/API coverage waiver remains explicit. One slow emulator launch and one failed ten-second native wait during boot activity are retained as timing limitations; the unchanged native test passed on repeat. This audit makes no ten-second boot-delivery or production-device startup-speed guarantee. No ANR, accessibility setting or product behavior was disabled to obtain a pass.

| Final post-fix check | Result |
|---|---|
| Unit tests | 229 passed across 37 suites |
| Screenshot validation | 271 passed |
| Lint | 0 errors, 47 warnings, 2 informational findings |
| Debug app and native-test builds | Passed; final regression rerun completed in 10m 46s |
| Local non-debug release-variant build | Passed in 12m 23s, signed only with emulator test key; original run failed, controlled retest passed |
| Native host audit, API 28 | Passed all 50 renders, semantics, host lifecycle, multiple instances/removal and cold private-process callback (final repeat: 220 ms) |
| Native host audit, API 35 | Passed same checks; cold private-process callback 774 ms on final passing repeat; boot-load timeout retained |
| Natural hourly callback | Healthy post-mitigation callback observed at 20:39:55: 7 ms dispatch, 71 ms execution; no new ANR. |
| Actual API 35 reboot / later widget tap | Original run failed; controlled retest passed two dedicated reboot checks and ten launch/Settings interactions with no new ANRs. One cold launch remained slow (8.993s) on the constrained emulator. |
| Physical devices, API 30/31/36, spoken TalkBack, standby battery measurement | Unavailable / not repeated; earlier user waiver retained, no fabricated coverage |

`checks.log`, `unit-summary.json`, `screenshot-summary.json`, `lint-summary.json` and `apk-audit.json` describe the final debug build. The APK contains exactly 50 matching WebPs totaling 693,818 bytes. `pre-fix/` retains the earlier build/check summaries. `installed-payload-comparison.json` is historical evidence for the pre-fix installed debug payload, not a claim about the final APK.

Final native repeats are in `retest/api28-native/` and `retest/api35-native-settled/`; `api28-fixed/` and `api35-fixed/` preserve earlier successful native runs. Earlier `api28/` and `api35/` runs preceded the private-process mitigation. Simulated callback exercises are explicitly labeled; actual cold host-options broadcasts and real reboot evidence are separate. Temporary audit hosts and bind grants were removed; the original API 35 launcher widget was preserved. The extra API 28 emulator was stopped.

Changes within this audit: private receiver process to avoid eager main-process provider initialization, a native regression runner, a static process-contract assertion, corrected art handoff wording, and this independent evidence. No artwork, app version, commit, push, tag or release was changed by this audit. DR5-057 is ready for its release task after the queue cooldown.

The original non-debug follow-up is documented in `findings.md`, `release-variant-build.log` and `api35-release-variant/`. It also failed before the emulator environment correction. The later retest used the same non-debug APK, hardware graphics and sequential build/device checks; it closed the observed startup/interaction findings without disabling app behavior or accessibility.

The regression rerun (`retest/checks.log`) completed successfully in **10m 46s**. Unit and screenshot validation were explicitly rerun: 229 and 271 passed, respectively, with zero failures, errors or skips. Lint and app/native-test builds passed. The asset hash/alpha/provenance recheck passed again. Current JSON summaries describe this final verification; the earlier `checks.log` remains the historical 26m 4s gate.


Final state: the API 35 AVD retains the same locally test-signed non-debug APK (0.24.2 / 24004), app data and original launcher widget ID 2. `retest/final-appwidget.txt` confirms one launcher widget, no audit host and no binding grants. Both test emulators and the build daemon were stopped to free host memory. Local AVD graphics settings retain the verified hardware backend. No commit, push, version bump, tag or release was performed; DR5-057 owns delivery.
