# DraftingRoom5 implementation queue

This is the shared queue for the Astra and Sol scheduled tasks. The approved requirements are in `DesignReview.md`, `Dashboard.md`, `MetricDetails.md`, `Settings.md`, `DashboardCustomization.md`, `SchedulesAndRoutines.md`, `RoutineEditor.md`, and `GuidedSession.md`.

## Worker protocol

- Astra takes only tasks labeled `Model: gpt-6-astra`; Sol takes only tasks labeled `Model: gpt-5.6-sol`.
- Take the lowest-numbered unchecked task for that model whose dependencies are checked. Complete at most one task per scheduled run. If none is eligible, change nothing.
- Claim a task by changing `[ ]` to `[~]` and adding `Claimed: <ISO timestamp> by <model>`. Re-read this file immediately before claiming.
- Read every referenced handoff before editing. Mockup PNGs are references only; build native Jetpack Compose UI.
- This is a clean-slate app. Delete replaced types, storage, resources, and UI. Do not add migrations, compatibility readers, aliases, or version branches for development data.
- Preserve unrelated user edits. Never commit credentials, signing material, generated builds, `.gradle-user-home`, or `.tooling`.
- Add focused tests to every implementation task and run the relevant subset. A `Push: YES` task must run `testDebugUnitTest lintDebug assembleDebug`.
- On completion, change `[~]` to `[x]` and append a concise `Completed:` note naming checks and important files. If blocked, return it to `[ ]` and append `Blocked:`.
- `Push: NO`: do not commit, push, tag, or release; leave the coherent work for the next milestone.
- `Push: YES`: this is a usable product milestone. Commit accumulated coherent work, push it, update the app version consistently, create and push the next increasing version tag, follow the release workflow, and verify the APK is published.

## Phase 1 — Clean architecture and foundation

## Phase 2 — Artwork and core product shell

- [x] **DR5-010 — Implement the redesigned Settings screen**
  - Claimed: 2026-09-11T06:32:15.0964401-05:00 by gpt-5.6-sol
  - Model: `gpt-5.6-sol`
  - Push: `NO`
  - Depends on: DR5-009
  - Work: Implement `Settings.md` groups for Training, Workout feedback, Connections & data, and App. Preserve Health Connect actions/states, TTS availability/rate, system-aware haptics, backups/privacy copy, and verified updates. Keep errors local and omit About unless it has a real destination.
  - Done when: every existing setting works, state tests pass, and no row is inert.
  - Completed: 2026-09-11 by gpt-5.6-sol. Rebuilt Settings as four native grouped surfaces with working Training navigation, TTS availability/rate semantics, system-aware haptic status, complete Health Connect state actions, expandable offline backup/recovery/privacy controls, and verified runtime update states; removed the superseded Settings cards and added compact/tall/2x-font previews plus `SettingsSupportTest`. Full `testDebugUnitTest lintDebug assembleDebug` passed and produced a 69,701,486-byte APK. Push: NO; changes remain local for the core-shell milestone.

## Phase 3 — Unified routines and recurring schedules

- [x] **DR5-022 — Release the planning milestone**
  - Claimed: 2026-09-11T17:58:22.2523948-05:00 by gpt-5.6-sol
  - Model: `gpt-5.6-sol`
  - Push: `YES`
  - Depends on: DR5-021
  - Work: Resolve findings, update README, run the full gate, and inspect all Schedule/Routines/editor flows on compact and tall layouts.
  - Done when: users can create, rename, illustrate, edit, schedule, reorder, launch, and delete both routine types, and the tagged release APK is published.
  - Completed: 2026-09-11 by gpt-5.6-sol. Released the accumulated Phase 3 planning system with recurring Schedule and unified Routines management, guided/linked creation and editors, paired exercise artwork, snapshot-safe saved-session handling, accessible reorder controls, and linked-app recovery; removed the retired workout label and refreshed README/design status. All 147 unit tests in 29 suites, 30 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; debug APK 66,977,861 bytes. Inspected all 18 planning/editor compact, tall, and 2x-font references. ADB/device interaction checks remain unavailable on this host and are recorded in `docs/reviews/DR5-021-planning-audit.md`. Release delivery: v0.21.0.

## Phase 4 — Durable guided sessions

- [x] **DR5-028 — Release the guided-session milestone**
  - Claimed: 2026-09-11T22:18:00-05:00 by gpt-5.6-sol
  - Model: `gpt-5.6-sol`
  - Push: `YES`
  - Depends on: DR5-027
  - Work: Resolve findings, update README, run the full gate, and inspect new, partial, timer, resumed, and completed sessions at compact/tall/large-font sizes.
  - Done when: the guided workout is durable from Dashboard start through completion and the tagged release APK is published.
  - Completed: 2026-09-12 UTC by gpt-5.6-sol. Released the audited durable guided-session flow with exact occurrence start/resume, automatic checkpoints, partial-set correction, elapsed-clock timer recovery, foreground-only feedback, save-and-exit/restart confirmations, completion history, and current-schema backup/restore. Updated README/design status and release defaults to 0.22.0/22002. All 177 unit tests in 32 suites, 60 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; debug APK 67,239,993 bytes. Inspected idle/readiness/running/finished/completion and large-set controls at compact, tall, and 2x font. ADB/device interaction checks remain unavailable and are recorded in `docs/reviews/DR5-028-guided-session-release.md`. Release delivery: v0.22.0.

## Phase 5 — Whole-product hardening

- [x] **DR5-029 — Complete exceptional states and accessibility**
  - Claimed: 2026-09-12T00:10:56.5246738-05:00 by gpt-5.6-sol
  - Model: `gpt-5.6-sol`
  - Push: `NO`
  - Depends on: DR5-028
  - Work: Cover every loading, empty, corrupt-current-data, permission, Health Connect unavailable/update, uninstalled app, TTS unavailable, backup/update failure, missing artwork, no-routine, recovery-day, and destructive state. Verify 48 dp targets, TalkBack order/actions, non-color cues, keyboard/D-pad, reorder alternatives, focus restoration, reduced motion, contrast, insets, compact/tall/large-font layouts, and landscape where supported.
  - Done when: every handoff state has a fixture/test, every error has safe recovery or explanation, and no essential control clips or has duplicate semantics.
  - Completed: 2026-09-12 UTC by gpt-5.6-sol. Added an 18-state hardening matrix and 72 compact/tall/2x-font/landscape native references covering Health Connect, installed-app discovery, corrupt data, linked-app uninstall, TTS, backup/update, artwork fallback, no-routine/recovery, destructive, and failed-save states. Added explicit empty-picker recovery, unavailable-haptic disabling, live loading semantics, and 48 dp shared error/confirmation actions. All 180 unit tests in 33 suites and 132 screenshot comparisons passed; `lintDebug` reported 0 errors and `assembleDebug` produced a 67,305,529-byte APK. Visual/device checklist: `docs/reviews/DR5-029-exceptional-accessibility.md`. Push: NO; changes remain local for the final Astra review and release milestone.

- [x] **DR5-031 — Final cleanup and production release**
  - Claimed: 2026-09-12T02:11:50.8907492-05:00 by gpt-5.6-sol
  - Model: `gpt-5.6-sol`
  - Push: `YES`
  - Depends on: DR5-030
  - Work: Resolve the review; delete retired models/screens/helpers/resources/tests; update README and design status; confirm no migration/compatibility code remains; run the full gate; and perform available device checks for Withings/Health Connect, app launching, process-death restore, TalkBack, timers/voice/haptics, backup, icon masks, update install, and compact/large-font layouts. Record unavailable device checks honestly.
  - Done when: the clean app implements the approved design, checks pass, the tagged release succeeds, and its signed APK is published.
  - Completed: 2026-09-12 UTC by gpt-5.6-sol. Integrated the DR5-029/030 hardening and review fixes, removed the audited obsolete route/screen paths, confirmed no migration or compatibility identifiers remain, reconciled README and every handoff/design status, and aligned defaults to 0.23.0/23002. The full Java 17 gate passed 187 unit tests in 33 suites, 136 native screenshot comparisons, `lintDebug` with 0 errors, and `assembleDebug`; the 67,305,529-byte debug APK contains all 49 catalog WebPs and no mockups. ADB found no attached device, so the physical checklist remains explicitly unavailable in `docs/reviews/DR5-031-production-release.md`. Release delivery: v0.23.0.

## Completed implementation dependencies

- [x] **DR5-030 — Perform the final Astra product review**
  - Completed: 2026-09-12 UTC by gpt-6-astra. Reviewed all eight handoffs, current architecture/integrations, accumulated hardening diff, native views, artwork/masks, and release readiness. Fixed atomic backup rotation/corrupt-fallback preservation, manual-backup retry copy, Dashboard midnight/week rollover, scrollable Health Connect privacy, obsolete placeholder routing, and the Dependabot verified-commit merge race. Added seven unit regressions and four privacy references. Final offline gate passed 187 tests in 33 suites, lintDebug (0 errors), assembleDebug, and 136 screenshot comparisons; APK 67,305,529 bytes with all 49 catalog WebPs and no mockups. Review and prioritized device-only checklist: docs/reviews/DR5-030-final-product-review.md. ADB has no attached device. Push: NO; accumulated changes remain local for DR5-031.


- [x] **DR5-027 — Audit guided-session resilience**
  - Completed: 2026-09-12 UTC by gpt-6-astra. Fixed backup-triggered repository reload/lease revocation, silent timer recovery after write failures, elapsed-regression tracking, fresh timer IDs, exact saved-session identity, recoverable stale routes, IO dispatch and foreground/canonical feedback guards, completion speech, partial-set undo, overflow-safe progress, bounded indicators, dialog restoration/errors, serialized backups, completion contrast and stable timer spacing. Added eight unit regressions and 15 control-panel references. Final gate passed all 177 unit tests in 32 suites, 60 screenshot comparisons, lintDebug (0 errors), and assembleDebug; APK 69,422,578 bytes with 49 catalog WebPs and no mockups. Audit and device-only checklist: docs/reviews/DR5-027-session-resilience-audit.md. ADB has no attached device. Push: NO; accumulated work remains local for DR5-028.


- [x] **DR5-026 — Implement save/resume/completion and current-schema backup**
  - Completed: 2026-09-11 by gpt-5.6-sol. Added confirmed ordinary exit with durable checkpoint failure retention, resumable focus/progress, confirmed latest-routine restart, one-time completion feedback, a saved native completion destination whose Close/Back/action replace the session stack with Dashboard, and dated history-backed Done routing. Aligned the strict current-only backup envelope, files, Android allowlists, and round-trip coverage for routines, schedules, partials, history, dashboard/range preferences, voice, haptics, and backup preference while excluding Health Connect data/permissions. All 169 unit tests in 32 suites, 45 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; debug APK 69,315,678 bytes. Push: NO; accumulated guided-session changes remain local for DR5-027/DR5-028.

- [x] **DR5-025 — Implement the focused session and timer UI**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced the ephemeral all-exercise workout with a repository-backed focused session destination using paired header/list artwork, saved position/set progress, current metadata, accessible correction/focus controls, scrollable Up next rows, automatic checkpointed exit, and durable finish routing. Added fixed-state idle/Get ready/running/finished timer presentation, elapsed-clock reconciliation, motion-safe rendering, and a single foreground feedback controller that deduplicates timer/set voice and haptic cues by durable identity. Added `GuidedSessionUiTest` recreation, fake-clock presentation and at-most-once feedback coverage plus 12 compact/tall/2x-font references. All 167 unit tests in 31 suites, 42 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; debug APK 67,141,701 bytes. Push: NO; guided-session changes remain local for DR5-028.

- [x] **DR5-024 — Implement durable session state and persistence**
  - Completed: 2026-09-11 by gpt-5.6-sol. Added the pure durable guided-session reducer, injected elapsed/wall/boot clock, strict timer phase/cue validation, runtime document leases, atomic open/resume/focus/set/undo/timer/checkpoint/restart/finish repository operations, idempotent occurrence/history lookup, process-death reconciliation, and one-time unverifiable-timer recovery output. Added `GuidedSessionStateTest` fake-clock/storage coverage for duplicate events, focus/wrap/undo, readiness and active boundaries, skipped cues, cancellation, process restoration, monotone audit time, strict corruption rejection, atomic completion, write failures, restart confirmation, and lease revocation. All 161 unit tests in 30 suites, `lintDebug` (0 errors, 43 warnings), and `assembleDebug` passed; debug APK 67,043,397 bytes. Push: NO; changes remain local for the guided-session milestone.

- [x] **DR5-023 — Finalize the guided-session state-machine design**
  - Completed: 2026-09-12 UTC by gpt-6-astra. Expanded TechnicalDesign.md with exhaustive entry/progress/exit/restart/completion rules, strict timer invariants, command ordering and restore leases, elapsed-clock reconciliation, foreground-only at-most-once feedback, fixed occurrence/history semantics, and 32 deterministic acceptance vectors for DR5-024–026. Validated local handoff links, JSON example, vector IDs/table structure, queue dependencies and changed-document whitespace; no production code or Android runtime tests in this documentation-only task. Push: NO; changes remain local for the guided-session milestone.

- [x] **DR5-021 — Audit planning and app-link flows**
  - Completed: 2026-09-11 by gpt-6-astra. Fixed exercise draft identity/recreation, new-routine and drag revisions, no-op edits, failed-save input retention, app-picker cancellation and explicit recovery saves, saved-session editing/deletion/reassignment disclosures, snapshot routing, safe launcher/deep-link filtering, reorder callbacks/keyboard controls, weekday targets and responsive editor layouts. Added a dedicated exercise builder and eight PlanningAuditTest regressions. All 147 unit tests in 29 suites, 30 reviewed screenshot comparisons, lintDebug (0 errors), and assembleDebug passed; APK 68,916,050 bytes. Review and device-only checklist: docs/reviews/DR5-021-planning-audit.md. Push: NO; accumulated Phase 3 changes remain local for DR5-022.


- [x] **DR5-020 — Finish deletion, reset, and referential integrity**
  - Completed: 2026-09-11 by gpt-5.6-sol. Routed schedule deletion through a typed atomic repository mutation that preserves its routine, history, preferences, and partial progress; hardened routine cascade deletion against missing IDs and verified failed writes publish neither half of the cascade. Added Dashboard Saved sessions for off-date and removed-recurrence partials using immutable routine snapshots, plus exact current-default reset coverage with no legacy fields. All 139 unit tests in 28 suites, 27 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; APK size 61,068,461 bytes. Push: NO; accumulated planning changes remain local for DR5-022.

- [x] **DR5-019 — Implement the recurring schedule-entry editor**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced the planning dialog/placeholder route with a dedicated saved-state schedule editor using existing routine identity and artwork, routine selection, Weekly/Weekdays/Every day/Custom shortcuts, an authoritative weekday set, localized plain-language recurrence preview, explicit Add/Save actions, validation, and dirty-draft Back confirmation. Added route anchor persistence, `ScheduleEditorTest`, and compact/tall/2x-font screenshot coverage. All 134 unit tests in 28 suites, 27 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; APK size 66,912,325 bytes. Push: NO; accumulated planning work remains local for the milestone.

- [x] **DR5-018 — Implement guided-routine and exercise editors**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced the legacy action-cluster editor with a responsive guided-routine editor using a wrapping editorial header, curated routine artwork, one ordered artwork-backed exercise list, long-press drag plus accessible move actions, stable focus, row Edit/Delete menus, bottom Add exercise, explicit transient-draft save/discard, and cascade-aware routine deletion. Added a dedicated exercise builder for name, notes, positive sets, target, optional positive timer, and a single curated paired-artwork choice with generic fallback; Cancel never mutates canonical data. Added `GuidedRoutineEditorTest` and compact/tall/2x-font screenshot coverage. All 128 unit tests in 27 suites, 24 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; APK size 66,879,557 bytes. Push: NO; accumulated planning work remains local for the milestone.

- [x] **DR5-017 — Implement linked-app editing and launch recovery**
  - Completed: 2026-09-11 by gpt-5.6-sol. Added an explicit save/cancel linked-app editor for existing and new routines with editable names, curated artwork, installed-app replacement, connection state, test launch, deletion, schedule summary, and discard protection. Centralized deep-link-first launching with validated targets, safe launcher fallback, and in-app replacement/store recovery without broad package visibility. Added persistence and launch regressions plus compact/tall/2x-font screenshot coverage. All 121 unit tests in 26 suites, 21 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed; APK size 66,715,717 bytes. Push: NO; accumulated planning work remains local for the milestone.

- [x] **DR5-016 — Implement Add routine and installed-app selection**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced immediate placeholder persistence with the approved Guided/Linked-app chooser, transient saveable drafts, and a searchable installed-app screen with localized labels, real icons, explicit Installed state, empty/error/retry states, accessible rows, and API-aware launcher-only package queries backed by narrow `MAIN`/`LAUNCHER` visibility. Linked selections remain transient until an explicit valid Save routine action; cancel/discard writes nothing. Added `RoutineCreationTest` coverage for chooser, search/empty, normalization, cancellation semantics, API 28–36 paths, selection, and valid-save conversion. All 116 unit tests in 25 suites, `lintDebug` (0 errors), and `assembleDebug` passed; APK size 66,568,261 bytes. Push: NO; changes remain local for the planning milestone.

- [x] **DR5-015 — Implement the unified Routines tab**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced the placeholder routine controls with one artwork-backed list for guided and linked-app routines, routine-owned type/item/app metadata, deduplicated recurring-weekday summaries, row editing, Rename/Delete overflow, cascade-aware confirmation for schedules and partial sessions, a guided/linked empty state, and one Add routine action. Routed rename and delete through current-document repository operations, added `RoutineManagementSupportTest`, strengthened repository cascade coverage, and added compact/tall/2x-font Routines references. All 110 unit tests in 24 suites, 18 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed. Push: NO; changes remain local for the planning milestone.

- [x] **DR5-014 — Implement the recurring Schedule tab**
  - Completed: 2026-09-11 by gpt-5.6-sol. Replaced the dated seven-timeline management UI with saved Schedule/Routines tabs, a compact count-only weekday selector, selected-day recovery and artwork-card states, six-dot drag plus accessible move actions, Edit/Delete overflow, named delete confirmation, Add scheduled item, reset overflow, and automatic-save feedback. Added count, boundary reorder, delete, current-store persistence, and compact/tall/2x-font visual regressions. All 107 unit tests in 23 suites, 15 screenshot comparisons, `lintDebug` (0 errors), and `assembleDebug` passed. Push: NO; changes remain local for the planning milestone.

- [x] **DR5-013 — Release the core-shell milestone**
  - Completed: 2026-09-11 by gpt-5.6-sol. Resolved the core-shell audit, removed superseded metric-row UI and the unused legacy session image, completed the artwork ledger/status, refreshed README, and aligned release defaults to 0.20.0/20002. Inspected the 12 compact/tall/2x-font native references and circle/rounded-square/squircle/tight launcher-mask sheet; real-device checks remain explicitly unavailable because ADB is absent. All 103 unit tests in 23 suites, screenshot validation, `lintDebug` (0 errors), and `assembleDebug` passed; release delivery is v0.20.0.

- [x] **DR5-012 — Review the core shell**
  - Completed: 2026-09-11 by gpt-6-astra. Corrected measurement history/range/delta/error handling, state restoration, manual backup/restore confirmation, update verification/recreation, linked-app visibility/recovery, responsive typography/semantics, and icon/artwork crops. Added native Compose screenshot tests; all 103 unit tests in 23 suites, 12 reviewed screenshot comparisons, lintDebug (0 errors), and assembleDebug passed. APK contains all 49 catalog WebPs. Review and physical-device/release checklist: docs/reviews/DR5-012-core-shell-audit.md. Push: NO; accumulated changes remain local for DR5-013.
- [x] **DR5-011 — Implement Dashboard customization**
  - Completed: 2026-09-11 by gpt-5.6-sol. Rebuilt dashboard customization as one six-section raised list with immediate visibility persistence, commit-on-drop drag ordering, cancelled-drag rollback, accessible move actions, haptic/tonal drag feedback, stable keys, focus restoration, protected last-visible state, confirmed reset, live announcements, and compact/tall/2x-font previews. Dashboard now renders every visible card in exact saved order, including Lean mass and Workouts. Added move/group/persistence/reset coverage to `DashboardLayoutTest`; all 96 tests in 22 suites, `lintDebug` (0 errors), and `assembleDebug` passed with a 69,708,518-byte APK. Push: NO; changes remain local for the core-shell review.

- [x] **DR5-009 — Implement the shared metric-detail family**
  - Completed: 2026-09-10 by gpt-5.6-sol. Implemented one native Compose detail family for Weight, Body fat, Lean mass, Workouts, and Distance with persistent 1D/1W/1M/3M/1Y ranges, current readings, range-correct neutral deltas, padded connected charts, high/average/low summaries, real single/multiple Health Connect sources, freshness/stale metadata, and local loading/permission/provider/empty/read-error recovery states. Added all-metric and exceptional-state previews plus `MetricDetailSupportTest`; all 89 tests in 21 suites passed and `assembleDebug` produced a 69,740,346-byte APK. Lint source analysis was attempted, but restricted networking could not resolve the uncached Android-test collection metadata or `lint-gradle` artifact. Push: NO; changes remain local for the core-shell milestone.

- [x] **DR5-008 — Implement Dashboard and session cards**
  - Completed: 2026-09-10 by gpt-5.6-sol. Rebuilt the native Compose Dashboard with the measured-five brand row, project-owned athlete hero and text-only fallback, actual selectable dates, stacked routine-resolved session cards, recovery state, and three-column Health Connect snapshot. Added one-action Start/Resume semantics, mint Done state, guided progress, selected-date completion routing, seven-day selection, seven targeted previews, and `DashboardSupportTest`. All 83 tests passed; `lintDebug` completed with 0 errors; `assembleDebug` produced a 68,463,833-byte APK. Push: NO; changes remain local for the core-shell milestone.

- [x] **DR5-007 — Produce the initial paired exercise-artwork catalog**
  - Completed: 2026-09-10 by gpt-5.6-sol. Generated eight original transparent exercise masters and 16 independently composed WebP list/header assets; added the stable localized `ExerciseArtworkCatalog`, generic pair fallback, reproducible crop/validation tooling, provenance ledger, and phone-size contact-sheet review. All 79 tests in 19 suites passed; `lintDebug` and `assembleDebug` passed, the 68,398,297-byte APK contains every paired WebP, and `git diff --check` reported only existing CRLF notices. Push: NO; changes remain local for the core-shell milestone.

- [x] **DR5-006 — Produce the routine-artwork catalog**
  - Completed: 2026-09-10 by gpt-5.6-sol. Generated 11 original unbranded transparent masters and 33 optimized WebP card/header/picker crops; added the stable localized `RoutineArtworkCatalog`, generic fallback behavior, reproducible crop tooling, provenance/license ledger, and phone-size contact-sheet review. All 76 tests in 18 suites passed, all 33 assets retained alpha and expected dimensions, `assembleDebug` produced a 68,758,996-byte APK containing all 33 WebPs. Lint source analysis was attempted but its detached lint artifact and an empty Android-test classpath dependency were unavailable under restricted networking; no lint source error was reported. Push: NO; changes remain local for the core-shell milestone.

- [x] **DR5-005 — Finish visual tokens and the measured-five identity**
  - Completed: 2026-09-10 by gpt-5.6-sol. Added the approved navy/ivory/blue/mint/gold Material theme, bundled licensed DM Serif typography, edge-to-edge light system-bar treatment, safe Scaffold insets, shared shapes and interaction colors, and compact/tall/2x-font previews. Rebuilt the measured-five as shared deterministic vector geometry for in-app, adaptive, static, round, themed-monochrome, and notification-safe use; removed superseded raster icon assets. All 73 tests in 17 suites passed; `lintDebug` completed with 0 errors and `assembleDebug` produced a 67,164,020-byte APK. Push: NO; changes remain local for the core-shell milestone.

- [x] **DR5-004 — Establish navigation, state holders, and shared UI**
  - Completed: 2026-09-10 by gpt-5.6-sol. Added a saveable typed `AppRoute` back stack covering the approved destination family, unified toolbar/system Back behavior, repository-backed screen state and failure recovery, shared secondary bars/headings/cards/action pills/dialogs/loading/error treatments, compact previews, and navigation regression tests. All 70 tests in 16 suites passed with `testDebugUnitTest` using the isolated Java 17 build setup. Push: NO; changes remain local for the milestone release.

- [x] **DR5-003 — Audit the clean domain implementation**
  - Completed: 2026-09-10 by gpt-6-astra. Hardened current JSON/UTF-8/size validation, timer/occurrence invariants, shared repository ownership, revision/conflict checks, cascade/reset, restore timer clearing, and failed-save UI behavior. Added domain, repository, codec, stream and backup regressions. All 66 tests in 15 suites passed with testDebugUnitTest in isolated Java 17 build output; whitespace and retired-model scans passed. See docs/reviews/DR5-003-domain-audit.md. Push: NO; changes remain local for the milestone release.

- [x] **DR5-002 — Replace the legacy plan model and persistence**
  - Completed: 2026-09-10 by gpt-5.6-sol. Added the current-format `AppDocument` codec/repository, shared routine identity, guided/linked execution, integer sets, artwork IDs, recurring weekday references, clean defaults, strict validation, and corruption reset. Removed production references to the retired schema and stores. Focused `AppDocumentCodecTest`, `AppRepositoryTest`, and `ScheduleDataTest` passed in an isolated Java 17 build. Push: NO; changes remain locally for the milestone release.

- [x] **DR5-001 — Write the clean-app technical design**
  - Completed: 2026-09-10 by gpt-6-astra. TechnicalDesign.md defines the clean model, storage, navigation, session transitions, artwork and replacement map. Handoff links, JSON example, all 15 source / 11 test mappings, whitespace and queue dependencies checked. Push: NO; no production changes or release. Work block removed from the active queue; this checked record satisfies DR5-002.

## Completed legacy work

- [x] Health Connect body-measurement reads were verified on device in v0.2.2.
- [x] Connection and update controls moved into Settings in v0.3.0.
