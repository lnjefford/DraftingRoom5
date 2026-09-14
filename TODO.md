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

## Active queue limits

- `minimum_completion_interval_minutes`: `60`
- `max_concurrency`: `1`
- `last_completion_at`: `2026-09-13T19:30:25-05:00`
- `next_eligible_dispatch_at`: `2026-09-13T20:30:25-05:00`
- `processor_lease`: empty
- For active Phase 6 and later queued work, dispatch only a task whose `Status` is `ready`, and never dispatch while another task is `running` or before `next_eligible_dispatch_at`.
- When claiming an active task, set its `Status` to `running` and fill `Executor thread ID` and `Started at`; the checkbox claim marker remains `[~]` for compatibility with the worker protocol above.
- When an active task completes, record its actual `completed_at`, copy that timestamp to `last_completion_at`, set `next_eligible_dispatch_at` to 60 minutes later, and promote only the next dependency-satisfied task from `blocked` to `ready`.
- A completed task must use checkbox `[x]` and `Status: complete`; a failed or input-blocked task must record evidence and use `Status: failed` or `Status: needs_input` without unblocking descendants.

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

## Phase 6 — Interaction and visual polish

Approved direction: **Quiet hierarchy (Option A)**. Preserve the current navy, ivory, blue, mint, and gold identity while reducing repetitive or overly prominent controls. Visual reference: `docs/design/phase-6-polish/quiet-hierarchy-approved.png`.

- [x] **DR5-032 — Refine Dashboard actions, date markers, and trends**
  - Outcome: Dashboard routine cards use a quieter right-aligned `Start →` action, today and selected-date indicators are visually distinct without crowding, the inert View trends link is gone, and every Dashboard metric sparkline represents exactly the latest 30 days regardless of drilldown range history.
  - Scope: Dashboard Compose UI/state, Health Connect trend queries and presentation, dashboard-focused tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: focused UI/state work with clear expected behavior.
  - Depends on: none
  - Status: `complete`
  - Acceptance: Start remains a 48 dp accessible action with subdued visual weight at the card's right edge; the today dot has clear separation from the selected circle at all supported font sizes; no View trends semantics or dead callback remains; Dashboard trends always query/render a rolling 30-day window and do not read or mutate metric-detail range preferences; focused unit and compact/tall/2x-font screenshot tests pass.
  - Executor thread ID: `01a09630-78ff-7492-8425-1d7e2a4f402c`
  - Started at: `2026-09-12T10:16:33.8865674-05:00`
  - Completed at: `2026-09-12T11:16:16.3409906-05:00`
  - Push: `NO`
  - Notes: implement the approved Option A Dashboard shown in the Phase 6 visual reference. Preserve tapping individual metrics to open their drilldowns.
  - Completed: Replaced the filled session pill with a subdued right-aligned 48 dp `Start →`/`Resume →` action, separated the today dot from the selected-date circle, removed the inert View trends affordance, and decoupled Dashboard Health Connect reads/rendering into an exact today-anchored 30-day window while preserving metric-detail range persistence. Focused Dashboard/health/detail tests passed 23/23; native compact, tall, and 2x-font reference updates were visually reviewed; all 136 screenshot comparisons passed.

- [x] **DR5-033 — Make the launcher artwork safe under Android icon masks**
  - Outcome: the main app mark retains comfortable padding and no meaningful corner detail is clipped by circular, squircle, rounded-square, or tight adaptive-icon masks.
  - Scope: adaptive/static/round/monochrome launcher icon foreground geometry and mask-review tests/artifacts only; do not change the in-app brand mark.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android resource and visual-validation change.
  - Depends on: DR5-032
  - Status: `complete`
  - Acceptance: foreground content remains inside the Android adaptive-icon safe zone; circle, squircle, rounded-square, and tight-mask review images show no cut-off corners; icon resource and asset tests pass; no raster launcher fallback or signing/build artifact is committed.
  - Executor thread ID: `01a096d4-8489-73a2-80b2-d2e5ded488bd`
  - Started at: `2026-09-12T13:15:14.8920754-05:00`
  - Completed at: `2026-09-12T13:41:38.9154110-05:00`
  - Push: `NO`
  - Notes: preserve the measured-five identity and notification icon behavior; fix scale/inset rather than redesigning the mark.
  - Completed: Applied a centered launcher-only scale to keep the complete measured-five frame inside Android's guaranteed circular safe zone while retaining a 48.51-unit mark. Added a reproducible full-color/monochrome circle, squircle, rounded-square, and tight-mask review sheet plus focused geometry/artifact regressions. All four `MeasuredFiveAssetTest` tests and `lintDebug` passed; no launcher PNG fallback was added. Push: NO; changes remain local for the Phase 6 delivery task.

- [x] **DR5-034 — Collapse voice rate and show update-check progress**
  - Outcome: voice rate is hidden beneath an expandable Voice announcements row that mirrors Automatic backups, and tapping the Dashboard update badge immediately exposes a working state until update checking or launching resolves.
  - Scope: Settings and Dashboard update UI/state, TTS preference controls, update flow, accessibility semantics, tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: related Compose disclosure and feedback-state improvements with established support classes.
  - Depends on: DR5-033
  - Status: `complete`
  - Acceptance: Voice announcements has a clear expand/collapse affordance; voice rate is absent while collapsed and remains editable/persistent while expanded; the structure and state restoration mirror Automatic backups; the Dashboard update affordance shows immediate progress, prevents ambiguous duplicate activation, and ends in success, failure, or actionable recovery feedback; screen-reader announcements and recreation tests cover the working state.
  - Executor thread ID: `01a0973f-7acb-7d01-870f-da924b43833e`
  - Started at: `2026-09-12T15:12:06.9958294-05:00`
  - Completed at: `2026-09-12T15:54:14-05:00`
  - Push: `NO`
  - Notes: preserve the existing yellow update indicator and TTS availability handling.
  - Completed: Voice announcements now uses a saveable inline disclosure matching Automatic backups, with its switch and persistent rate control inside. Dashboard update activation immediately shows gold progress and live working text, blocks duplicate activation through installer launch, and restores interrupted work as actionable retry feedback. Focused SettingsSupportTest and VoiceAnnouncementsTest, Kotlin compile, screenshot update, and screenshot validation passed. Push: NO; work remains in the shared checkout for Phase 6 delivery.

- [x] **DR5-035 — Repair Schedule reordering and remove its duplicate heading**
  - Outcome: pointer drag-and-drop reliably reorders scheduled items and persists the committed order, while the Schedule/Routines activity keeps the blue tab header and removes the redundant yellow section title.
  - Scope: Schedule tab Compose gestures/state, repository reorder integration, accessibility alternatives, planning tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: localized gesture-state diagnosis and UI cleanup with existing reorder patterns.
  - Depends on: DR5-034
  - Status: `complete`
  - Acceptance: long-press/drag starts from the visible handle, crosses item boundaries, previews movement, commits exactly once on drop, rolls back on cancellation or failed persistence, and survives recreation; accessible Move up/Move down actions remain; the blue Schedule/Routines tabs remain and the yellow `Weekly schedule`/equivalent duplicate heading is absent on both tabs; focused unit and screenshot tests pass.
  - Executor thread ID: `01a097ae-62ec-7191-9523-2ad421ab4263`
  - Started at: `2026-09-12T17:13:12.2591455-05:00`
  - Completed at: `2026-09-12T17:34:26-05:00`
  - Push: `NO`
  - Notes: retain the explanatory copy and subtle gold rule without repeating the selected tab's wording.
  - Completed: Visible-handle drag now previews movement across rows, commits once on drop, and rolls back on cancellation or failed persistence; repository recreation retains the committed order. Removed duplicate yellow headings while retaining blue tabs and the gold rule. All 11 focused ScheduleData tests and 142 screenshot comparisons passed; no physical device was attached for live touch-drag validation. Push: NO; changes remain local.

- [x] **DR5-036 — Diversify Routine editor action hierarchy**
  - Outcome: the Routine editor replaces its field of visually identical rounded text buttons with a clearer mix of compact icon-plus-text links, row drag/overflow actions, and one prominent filled Add exercise action.
  - Scope: guided and linked-app routine editors, shared editor actions, accessibility semantics, tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: focused design-system application across two closely related editors.
  - Depends on: DR5-035
  - Status: `complete`
  - Acceptance: Rename and Change artwork are compact icon-plus-text actions; exercise rows distinguish drag handles from overflow menus; Add exercise is the single visually prominent filled action; destructive and secondary actions have appropriate hierarchy; every target remains at least 48 dp with unambiguous TalkBack labels; compact/tall/2x-font references match the approved quiet-hierarchy direction.
  - Executor thread ID: `01a0981b-d8b5-7e82-b251-801388abb088`
  - Started at: `2026-09-12T19:12:46.7716876-05:00`
  - Completed at: `2026-09-12T19:45:27-05:00`
  - Push: `NO`
  - Notes: do not replace clear text indiscriminately with unlabeled icons and do not change automatic-save semantics.
  - Completed: Guided and linked editors now use compact icon-plus-text Rename/Change artwork links, distinct 48 dp row drag and overflow targets with exercise-specific TalkBack labels, and one filled Add exercise action. Guided autosave and linked Save behavior remain unchanged. Focused tests passed 12/12, all 145 screenshot comparisons passed, lint reported 0 errors, and `git diff --check` passed. Push: NO; changes remain local.

- [x] **DR5-037 — Keep Save visible in every selection popover**
  - Outcome: every dialog, sheet, or popover that combines a selection area with an explicit Save action keeps Save fixed at the bottom while only the selection content scrolls.
  - Scope: all current Compose selection dialogs/sheets/popovers, shared UI primitives, insets/IME handling, accessibility, tests, and screenshot references.
  - Model: `gpt-5.6-sol`
  - Model reason: systematic but mechanically verifiable cross-screen layout cleanup.
  - Depends on: DR5-036
  - Status: `complete`
  - Acceptance: an audited inventory names every affected surface; each uses a bounded scrollable selection region plus a persistent bottom Save action; Save remains visible with compact height, 2x font, landscape, IME, and navigation-bar insets; Cancel/Back and failed-save draft retention remain correct; shared primitives prevent recurrence and screenshots cover representative shortest and longest lists.
  - Executor thread ID: `01a09888-e871-7820-a12d-645ae7b6aebd`
  - Started at: `2026-09-12T21:11:55.9396303-05:00`
  - Completed at: `2026-09-12T22:05:30.0626243-05:00`
  - Push: `NO`
  - Notes: selection content may scroll; the action area must not scroll away. Do not add Save to auto-saving surfaces.
  - Completed: Audited every current Compose modal/dialog/menu surface; routine-artwork (11 choices) and paired exercise-artwork (8 choices) are the two explicit-commit sheets. Both now use a shared bounded catalog scroller with fixed title and 48 dp Cancel/Save footer above IME/navigation-bar insets; pending choices still cancel without mutation and failed guided persistence retains the open draft. The audit is in `docs/reviews/DR5-037-selection-sheet-audit.md`. All 196 unit tests, lint with 0 errors, and 151 screenshot comparisons passed; six new compact/2×-font/landscape picker references were visually reviewed. Push: NO; changes remain local.

- [x] **DR5-040 — Complete linked-app routines when their app opens**
  - Outcome: a linked-app Dashboard occurrence has an explicit `Open & complete` action that marks only that occurrence Done after its external app launches successfully, with a reliable way to undo an accidental completion.
  - Scope: linked-app Dashboard action and semantics, safe external-launch result handling, atomic occurrence-history persistence/removal, backup coverage, focused tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded UI and repository behavior with explicit success, failure, and idempotency rules.
  - Depends on: DR5-037
  - Status: `complete`
  - Acceptance: linked-app cards label the action `Open & complete` so completion is disclosed before the tap; a verified successful intent launch atomically records history for exactly the selected schedule-entry/date occurrence and the card returns as Done; a missing or failed launch records no completion and retains recovery actions; double taps/recreation are idempotent and cannot create duplicate history; a completed linked-app card exposes `Undo completion`, which removes only that occurrence after successful persistence and restores `Open & complete`; guided-routine completion behavior is unchanged; completion and undo survive process recreation and current-schema backup/restore; unit, semantics, and compact/tall/2x-font screenshot tests pass.
  - Executor thread ID: `01a098f6-5d3f-73d0-babe-a19b5336a1b4`
  - Started at: `2026-09-12T23:11:20-05:00`
  - Completed at: `2026-09-12T23:43:00.2469570-05:00`
  - Push: `NO`
  - Notes: do not attempt to infer activity inside the external app or wait for an unreliable return callback. Persist completion only after Android accepts the launch, and retain the existing unavailable-app recovery flow.
  - Completed: Dashboard linked cards disclose `Open & complete`; an accepted launch atomically writes one exact occurrence to current history, and a completed card exposes persisted `Undo completion`. Missing/failed launches retain the existing recovery dialog; duplicate completion/undo, failed writes, guided history isolation, recreation, and current-schema backup/restore are covered by `LinkedOccurrenceCompletionTest` and existing launcher tests. `AppRepository.kt`, `DashboardSupport.kt`, `MainActivity.kt`, `AppDocument.kt`, and six native linked-card references changed. All 200 unit tests, 157 screenshot comparisons, and lintDebug (0 errors) passed; compact, tall, and 2×-font linked states were visually reviewed. Push: NO; changes remain local.

- [x] **DR5-041 — Model per-occurrence defer and skip exceptions**
  - Outcome: one scheduled occurrence can be moved to tomorrow or skipped without editing its recurring schedule, colliding with another occurrence, or affecting any past/future repetition.
  - Scope: current domain/document schema, strict codec validation, Dashboard occurrence projection, repository mutations, completion/history interaction, reset/backup behavior, and focused deterministic tests; no production UI changes.
  - Model: `gpt-6-astra`
  - Model reason: recurrence exceptions require difficult identity, persistence, collision, and temporal reasoning across schedule, Dashboard, and history state.
  - Depends on: DR5-040
  - Status: `complete`
  - Acceptance: the current schema represents a source occurrence's `scheduled`, `deferred`, or `skipped` disposition without compatibility readers; deferring removes only the source-date card and creates one uniquely identifiable one-off card tomorrow while preserving any ordinary tomorrow occurrence, including the same daily schedule entry; skipping removes only the selected occurrence and never creates completion history; repeated commands are idempotent; completed occurrences cannot be deferred/skipped; completion of a deferred card resolves exactly its source instance and displays on the effective date without marking another recurrence Done; DST, timezone changes, midnight rollover, schedule/routine deletion, reset, process recreation, and current-schema backup/restore retain valid deterministic state; write failures publish no partial exception; focused codec/repository/projection tests pass.
  - Executor thread ID: `01a09967-7f11-7d71-bc93-de4d833ed48d`
  - Started at: `2026-09-13T01:14:56-05:00`
  - Completed at: `2026-09-13T01:33:42.6178177-05:00`
  - Push: `NO`
  - Notes: this is an exception to one occurrence, never a weekday/recurrence edit. Keep original occurrence identity plus an explicit effective date or equivalent collision-safe representation; do not overload `OccurrenceKey(scheduleEntryId, date)` in a way that merges two tomorrow cards.
  - Completed: Added strict current-schema occurrence exceptions with immutable source keys and effective dates, atomic defer/skip/Undo, collision-safe Dashboard and completion projections, deletion/reset cleanup, and current-schema backup/restore. AppDocument.kt, AppDocumentCodec.kt, AppRepository.kt, DashboardSupport.kt, WorkoutHistory.kt, OccurrenceExceptions.kt, and TechnicalDesign.md carry the model; MainActivity.kt only supplies new fields in its private preview fixture. All 216 unit tests in 35 suites (including 16 new OccurrenceExceptionsTest cases) and lintDebug (0 errors, 43 warnings) passed; schema-example and whitespace checks passed. Evidence and DR5-042 integration handoff: docs/reviews/DR5-041-occurrence-exceptions.md. Push: NO; no production UI work or release; accumulated Phase 6 changes remain local.

- [x] **DR5-042 — Add the approved defer and skip card menu**
  - Outcome: today's actionable routine cards expose a quiet overflow menu with `Move to tomorrow` and `Skip today`, with clear saved feedback and recovery.
  - Scope: Dashboard card/menu Compose UI, DR5-041 repository integration, state restoration, accessibility, confirmation/undo feedback, tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: focused Compose interaction built on the completed occurrence-exception model.
  - Depends on: DR5-041
  - Status: `complete`
  - Acceptance: the approved three-dot card menu matches `docs/design/phase-6-polish/defer-skip-card-menu-approved.png`; only actionable current-day scheduled cards expose it; the menu has 48 dp targets and unambiguous TalkBack actions named for the routine/date; `Move to tomorrow` and `Skip today` persist before changing the visible list, announce the result, and expose a reliable Undo action; cancellation changes nothing; write failure retains the card/menu context with Retry; deferred cards appear tomorrow with concise moved-from-today context and remain startable/completable; skipped cards do not count as Done or toward completion totals; compact/tall/2x-font/landscape screenshots and focused semantics/recreation tests pass.
  - Executor thread ID: `01a099d3-0ed1-70f0-bac5-7454467e64c9`
  - Started at: `2026-09-13T03:12:24-05:00`
  - Completed at: `2026-09-13T03:40:05-05:00`
  - Push: `NO`
  - Notes: preserve the quiet-hierarchy Start/Open action and keep defer/skip out of the primary card action row. Do not expose these occurrence actions on saved partials, completed cards, or management screens.
  - Completed: Wired Dashboard cards, saved sessions, guided routes, and linked completion/Undo to exact source occurrences; added the 48 dp three-dot menu with dated TalkBack labels, save-before-hide confirmation, live saved feedback with exact Undo, and local Retry after failed writes. Deferred cards retain their source-date context and remain startable. MainActivity.kt, DashboardOccurrenceMenuTest.kt, CoreShellScreenshots.kt, and eight compact/tall/2x-font/landscape menu/deferred references carry the change. All 219 unit tests in 36 suites, 165 screenshot comparisons, lintDebug (0 errors), and git diff --check passed. Push: NO; coherent changes remain local for the terminal Phase 6 milestone.

- [x] **DR5-045 — Show complete exercise artwork in guided-session headers**
  - Outcome: every guided-session exercise image is fully visible at a modest, balanced size instead of being cropped to half an object.
  - Scope: `GuidedSessionScreen.kt` current-exercise header layout/image scale, shared artwork sizing only if necessary, focused UI tests, and compact/tall/2x-font/landscape screenshot references; no exercise data or timer behavior changes.
  - Model: `gpt-5.6-sol`
  - Model reason: localized Compose image-layout fix with clear visual acceptance.
  - Depends on: DR5-042
  - Status: `complete`
  - Acceptance: the complete silhouette of every current exercise-art header asset fits inside the current-exercise card with margin, without `ContentScale.Crop` cutting off either side or allowing overlap with labels; the dead-hang bar shows both uprights and its entire crossbar; the contained art is approximately the quiet scale in `docs/design/phase-6-polish/guided-session-option-b2-approved.png`, not the oversized art in the first Option B; the exercise title, notes, and target remain legible; compact/tall/2x-font and landscape screens handle long names and all catalog asset aspect ratios without clipping; focused layout and native screenshot tests pass.
  - Executor thread ID: `01a09a3f-f80b-7c92-abe5-9a73033ba6c6`
  - Started at: `2026-09-13T05:11:23-05:00`
  - Completed at: `2026-09-13T06:25:50-05:00`
  - Push: `NO`
  - Notes: the current shared header renders a 960x480 asset into a roughly half-width 190dp slot with `ContentScale.Crop`, which is the observed clipping cause. Use containment and a bounded modest-height art region; preserve the existing navy card and typography. The approved comparison image is a design reference, not a production asset.
  - Completed: Replaced the cropped half-width overlay with contained artwork above full-width text, using a 220×108 dp region normally and a visible 168×84 dp region at 2× font. `GuidedSessionScreen.kt`, `CoreShellScreenshots.kt`, and 36 new catalog/long-name references cover compact, tall, 2×-font, and landscape layouts; visual review confirmed all eight catalog silhouettes and the complete dead-hang bar at 2× with title, notes, and target legible. All 219 unit tests, 201 native screenshot comparisons, lintDebug (0 errors), and git diff --check passed. Push: NO; changes remain local for the terminal milestone.

- [x] **DR5-046 — Compact and clarify the guided-session timer controls**
  - Outcome: the timer digits are large enough to read at a distance, while a shorter set/timer card separates frequent actions from rare corrections.
  - Scope: `GuidedSessionScreen.kt` set/timer presentation and controls, accessibility semantics/state announcements, focused UI/presentation tests, and timer-state screenshot references only; preserve the durable timer/session reducer and persistence behavior.
  - Model: `gpt-5.6-sol`
  - Model reason: focused Compose hierarchy and responsive timer-state presentation work.
  - Depends on: DR5-045
  - Status: `complete`
  - Acceptance: implement the split-timer layout in `docs/design/phase-6-polish/guided-session-option-b2-approved.png`: a compact top row for `Set 2 of 3` and progress indicators; prominently enlarged high-contrast timer digits beside a separate, at-least-48dp outlined Start/Restart control when applicable; one distinct filled Complete set action below; remove visible `Timer ready`/other phase-label rows, stopwatch icon, duplicate target-time row, and long fixed-height explanatory copy; move Undo last set into an accessible overflow menu, while active Cancel remains quickly reachable as a compact, clearly labeled action; READY, RUNNING, FINISHED, no-timer, and all-sets-complete states retain accurate controls, non-color feedback and TalkBack/live-region phase announcements; no timer phase or completion semantics change; the card is materially shorter on a compact phone and controls remain usable at 2x font, landscape, and with large set counts; focused unit, semantics, recreation, and native screenshot tests pass.
  - Executor thread ID: `01a09ae4-dd86-7573-a01f-d2e1c8353a63`
  - Started at: `2026-09-13T08:11:29-05:00`
  - Completed at: `2026-09-13T09:08:35-05:00`
  - Push: `NO`
  - Notes: preserve the approved B2's side-by-side clock/Start arrangement and separate Complete set button. The preview shows an idle timer only; verify all other phases explicitly. Rare Undo may move to the card overflow; do not bury active Cancel where a user cannot find it. Keep visual phase text removed but phase meaning accessible and distinguishable without color alone.
  - Completed: Rebuilt the set/timer card with a compact progress row, large serif clock beside an outlined Start/Restart control, visible active Cancel, one filled Complete set action, and accessible Undo overflow. Removed the visible phase/icon/target/helper rows; phase changes announce once and the clock has a single TalkBack description. `GuidedSessionScreen.kt`, `GuidedSessionUiTest.kt`, `CoreShellScreenshots.kt`, and timer-state references cover timed, untimed, completed, large-set, compact, 2×-font, and landscape presentation. The final temp-backed gate passed 220 unit tests (including timer-phase and durable-reopen coverage), lintDebug with 0 errors, 214 native screenshot comparisons with 0 failures, and git diff --check. Push: NO; no commit, tag, or release.

- [x] **DR5-047 — Separate upcoming and completed guided-session exercises**
  - Outcome: `Up next` contains only unfinished exercises, followed by a visible `Completed` section so finished exercises never obstruct the next action.
  - Scope: `GuidedSessionScreen.kt` exercise-list presentation and section UI, focused presentation/semantics tests, and compact/tall/2x-font screenshot references only; preserve session progress, timer, and focus persistence behavior.
  - Model: `gpt-5.6-sol`
  - Model reason: localized Compose list partitioning and layout with straightforward state-based checks.
  - Depends on: DR5-046
  - Status: `complete`
  - Acceptance: implement the approved layout in `docs/design/phase-6-polish/guided-session-completed-below-up-next-approved.png`; below the current exercise/timer area, show `Up next` first with only incomplete non-focused exercises in routine order, then `Completed` below with only fully completed non-focused exercises in routine order; hide either section when it has no items; completed rows retain their progress status and remain selectable for review/correction, without losing or duplicating set progress; transitioning an exercise to or from fully complete moves it between sections immediately and correctly after recreation; long lists remain scrollable, section headings and row actions are accessible at 2x font, and focused UI/presentation and native screenshot tests pass.
  - Executor thread ID: `01a09b54-5726-7602-a470-d3b899d74888`
  - Started at: `2026-09-13T10:13:14-05:00`
  - Completed at: `2026-09-13T12:02:48-05:00`
  - Push: `NO`
  - Notes: the observed source list filters out only the focused exercise, leaving completed exercises in `Up next`. Partition by actual per-exercise completion state instead of changing the durable session reducer. The comparison image is a design reference, not a production asset; preserve the eventual DR5-046 compact timer layout above these sections.
  - Completed: Partitioned non-focused exercises by committed set count into conditional Up next and Completed sections in routine order, with accessible headings and the existing selectable progress rows. `GuidedSessionScreen.kt`, `GuidedSessionUiTest.kt`, `CoreShellScreenshots.kt`, and nine new compact/tall/2x-font section references cover mixed, completed-only, and upcoming-only layouts, completion/undo/review, and repository recreation. All 221 unit tests passed; lintDebug reported 0 errors; targeted reference update and all 223 native screenshot comparisons passed; compact/tall/2x-font references were visually reviewed and git diff --check passed. An initial all-at-once screenshot update exhausted native renderer memory; the successful follow-up produced the references and full validation passed. Push: NO; changes remain local for the Phase 6 milestone.

- [x] **DR5-043 — Add catalog-matched hangboard artwork for exercises and routines**
  - Outcome: Forearm & Grip Conditioning includes an illustrated Hangboard Holds step, and the same generic hangboard identity is available in the routine-artwork picker so users can create and independently schedule a dedicated hangboarding routine.
  - Scope: transparent hangboard masters and their exercise list/header plus routine card/header/picker WebP derivatives, both artwork catalogs/provenance, the seeded Forearm & Grip Conditioning routine, artwork preparation/validation tooling as needed, focused tests, and screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded asset-generation, catalog integration, and visual-validation work with an approved reference.
  - Depends on: DR5-047
  - Status: `complete`
  - Acceptance: the approved equipment-only direction in `docs/design/phase-6-polish/hangboard-artwork-option-a-approved.png` is implemented as an original generic dark-charcoal composite hangboard with symmetrical finger pockets, warm-left/cool-blue-right studio rim lighting, transparent background, no hands, branding, wood grain, or manufacturer-specific design; the 1254x1254 exercise master and independently composed 320x320 list and 960x480 header WebPs retain clean alpha, remain legible at their rendered sizes, and visually belong beside the existing dead-hang and grip-hold artwork; matching routine artwork provides a 1254x1254 master plus independently composed 720x480 card, 960x480 header, and 320x320 picker WebPs consistent with the existing routine catalog; `ExerciseArtworkCatalog` and `RoutineArtworkCatalog` each resolve a stable `hangboard` artwork ID and preserve fallback behavior; the hangboard option is selectable when creating or changing a guided routine and flows through routine cards, editors, schedule selection, guided-session completion, and backup/restore like every other catalog entry, allowing a user-created hangboarding routine to have its own recurrence; Forearm & Grip Conditioning contains a `Hangboard Holds` exercise using the new exercise pair with 3 sets, 20-second target/timer, and `Controlled edge hold` guidance; catalog, default-data, resource, creation/editing/scheduling, and compact/tall/2x-font screenshot tests pass.
  - Executor thread ID: `01a09bf7-bdce-7b13-9d9c-60e82114de4c`
  - Started at: `2026-09-13T13:11:43-05:00`
  - Completed at: `2026-09-13T15:37:47-05:00`
  - Push: `NO`
  - Notes: use the built-in image-generation path and the existing `exercise_dead_hang`/`exercise_grip_hold` plus routine-equipment masters as style references, not the user's purchased board as a shape reference. Preserve the approved equipment-only composition across both catalogs. Record the final prompts and provenance, run the established exercise- and routine-art preparation pipelines, and keep generated mockup boards out of runtime resources. Do not hard-code a special hangboarding routine type or pre-create a second routine; the existing guided-routine creator and schedule editor should support it through the new selectable artwork.
  - Completed: Added original transparent 1254px exercise/routine hangboard masters, independently composed list/header/card/picker WebPs, stable `hangboard` entries in both catalogs, localized labels, and a seeded 3 × 20-second Hangboard Holds step. `AssetLedger.md` records exact built-in prompts and provenance; both artwork preparation and validation pipelines passed (9 exercise masters/18 WebPs, 12 routine masters/36 WebPs). Focused creation, recurrence, storage, backup, fallback, and resource tests passed; the complete offline gate passed 222 unit tests in 36 suites, lintDebug with 0 errors, assembleDebug (67,538,824-byte APK containing all five hangboard WebPs and no mockup board), and 246 native screenshot comparisons. Compact, tall, and 2x-font exercise, picker, card, editor, and schedule references were visually reviewed; `git diff --check` passed. Push: NO; coherent changes remain local for the Phase 6 milestone.

- [x] **DR5-044 — Add catalog-matched rice-bag exercise artwork**
  - Outcome: users can select a recognizable rice-bag grip-training image for an exercise in any guided routine, without adding routine-level rice-bag artwork or a preconfigured routine.
  - Scope: one original transparent exercise-art master, its independently composed list/header WebP derivatives, `ExerciseArtworkCatalog` and localized display name, artwork provenance/validation, focused catalog/resource tests, and exercise-picker/list/guided-session screenshot references only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded exercise-art generation and catalog integration with an approved visual reference.
  - Depends on: DR5-043
  - Status: `complete`
  - Acceptance: the approved bag-only direction in `docs/design/phase-6-polish/rice-bag-artwork-option-a-approved.png` becomes a generic, unbranded compact upright dark-charcoal neoprene grip-training pouch with a visible open cuff and restrained hint of rice fill, warm-left/cool-blue-right studio lighting, no hand/person, grocery-sack styling, logo, text, or manufacturer-specific design; the 1254x1254 master has clean transparent alpha and the independently composed 320x320 list and 960x480 header WebPs remain recognizable beside the existing grip-hold and finger-extension art at phone size; `ExerciseArtworkCatalog` resolves a stable `rice_bag` ID, picker label, resource pair, and normal fallback, and users can select it while creating or editing an exercise without changing that exercise's sets, target, timer, or notes; no rice-bag routine-art entry, seeded exercise, training prescription, or schedule is added; focused catalog/resource/selection tests and compact/tall/2x-font exercise-picker, routine-list, and guided-session screenshot tests pass.
  - Executor thread ID: `01a09c94-ff6b-7130-aefe-28b49ea38f5d`
  - Started at: `2026-09-13T16:04:08-05:00`
  - Claimed: 2026-09-13T16:04:08-05:00 by gpt-5.6-sol
  - Completed at: `2026-09-13T17:01:39-05:00`
  - Push: `NO`
  - Notes: built-in image-generation reference prompt: "generic unbranded portable rice-training grip bag, black/charcoal neoprene cylindrical pouch, rounded fabric body, visible open top/cuff with a small glimpse of rice fill; isolated photorealistic equipment cutout on genuine transparency; existing exercise-catalog warm-left and cyan-blue-right rim lighting; no hands, branding, product-logo treatment, or grocery sack." Use `exercise_grip_hold` and `exercise_finger_extension` masters as style references, record final prompt/provenance, run `prepare_exercise_artwork.py`, and keep the approved comparison board out of runtime resources. The visual mockup's sample title and 3-set timer values are illustrative only, not requirements.
  - Completed: Added original transparent 1254px rice-bag master, independently composed 320px list and 960x480 header WebPs, stable `rice_bag` exercise catalog entry and localized label, plus provenance in `AssetLedger.md` and the refreshed exercise contact sheet. `prepare_exercise_artwork.py` validated 10 masters/20 crops with clean alpha and no clipping. Focused catalog/editor selection tests passed 12/12, lintDebug had 0 errors, and 263 native screenshot renders and comparisons passed; compact/tall/2x-font picker, routine/editor, and session references were visually reviewed. No rice-bag routine art, seeded exercise, schedule, or prescription was added; `git diff --check` passed. Push: NO; changes remain local for the Phase 6 milestone.

- [x] **DR5-038 — Audit the Phase 6 polish milestone**
  - Outcome: an independent product review verifies all approved Phase 6 improvements are resolved without regressions and records any device-only checks honestly.
  - Scope: Phase 6 diff, affected architecture and tests, native screenshot references, accessibility, and a new review artifact under `docs/reviews/`; fixes may touch Phase 6 scope only.
  - Model: `gpt-6-astra`
  - Model reason: cross-feature review requires difficult interaction, accessibility, and state-consistency reasoning.
  - Depends on: DR5-044
  - Status: `complete`
  - Acceptance: review maps every approved Phase 6 user report to code and evidence; exercises Dashboard trends independently from drilldown ranges; validates icon masks, schedule drag cancellation/persistence, disclosure recreation, update progress, editor hierarchy, fixed Save layouts, linked-app completion/undo success, failure, and idempotency, defer/skip recurrence isolation, collisions, undo, and temporal boundaries, complete contained artwork across all guided-session exercise assets, compact timer hierarchy and all timer phases, `Up next` before `Completed` with correct state transitions and accessibility, plus hangboard and rice-bag art catalog consistency, alpha/crop quality, grip-routine integration, and exercise-art selection; all relevant unit and screenshot tests pass; unresolved findings block release.
  - Executor thread ID: `01a09cef-8d77-72e3-be01-59702ff93145`
  - Started at: `2026-09-13T17:43:27-05:00`
  - Completed at: `2026-09-13T18:16:37-05:00`
  - Push: `NO`
  - Notes: compare against `docs/design/phase-6-polish/quiet-hierarchy-approved.png`; use physical-device checks when available and explicitly record unavailable checks.
  - Claimed: 2026-09-13T17:43:27-05:00 by gpt-6-astra; immediate dispatch explicitly authorized by user.
  - Completed: Independent requirement-by-requirement audit in `docs/reviews/DR5-038-phase-6-product-audit.md`; fixed long-timer clipping, crowded four-to-six-set headings, and the Dashboard health-query window staying stale across midnight. Added two unit regressions and eight timer boundary references. Focused tests passed 81/81; final full gate passed 225 unit tests in 36 suites, 271 fresh native screenshot comparisons with zero failures, lintDebug (0 errors), assembleDebug (70,019,961-byte APK; all 56 catalog WebPs, no mockups), both artwork validators, and git diff --check. All three findings closed. ADB found no attached device; live interaction/accessibility/system checks remain explicitly unavailable. Push: NO; accumulated work remains uncommitted for DR5-039.

- [x] **DR5-039 — Release the Phase 6 polish milestone**
  - Outcome: review findings are resolved and one coherent tagged release publishes the signed APK with aligned app version defaults.
  - Scope: Phase 6 findings, README/design status, version defaults, full verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic release procedure after Astra approval.
  - Depends on: DR5-038
  - Status: `complete`
  - Acceptance: all approved Phase 6 improvements and DR5-038 findings are complete; README/design status is current; default version name/code advance consistently from 0.23.0/23002; `testDebugUnitTest lintDebug assembleDebug` and all screenshot comparisons pass; accumulated work is committed and pushed once; the next increasing version tag is pushed; its release workflow completes and the signed APK is published and verified.
  - Executor thread ID: `01a09d12-d91d-7053-b455-7f7a9c96bdb9`
  - Started at: `2026-09-13T18:22:29-05:00`
  - Completed at: `2026-09-13T19:30:25-05:00`
  - Push: `YES`
  - Claimed: 2026-09-13T18:22:29-05:00 by gpt-5.6-sol; immediate dispatch explicitly authorized by user.
  - Notes: never commit credentials, signing material, generated builds, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 6 tasks.
  - Completed: Released the audited Phase 6 polish as v0.24.0/24002 after reconciling README and TechnicalDesign status. All 225 unit tests in 36 suites, 271 native screenshot comparisons, lintDebug (0 errors), assembleDebug, both artwork validators (56 WebPs), version/asset inspection, and staged whitespace/credential/path checks passed. The two Settings references showing the new version were updated after visual inspection. ADB had no attached device; device-only limitations remain in `docs/reviews/DR5-038-phase-6-product-audit.md`. Commit `2ce0646` and tag `v0.24.0` were pushed; GitHub Actions run `34792338420` completed successfully, including persistent-key signing and release publication. The public release has an uploaded `DraftingRoom5.apk` (49,979,233 bytes, SHA-256 `e06553918513d10cc403b8e277793b14929670b942df2c0334cba7a2a4c6c8a9`).

## Phase 7 — Living icon-sized widget

Approved direction: a one-cell home-screen widget that opens the app normally on tap and rotates through **50** distinct still images at a battery-conscious, inexact cadence. The **5** and navy/ivory/gold identity recur across the collection, but neither a readable 5 nor an intact border is required in every image: a strong concept may mirror, obscure, fragment, transform, or even hide the 5. The gold border and the 5 can move, bend, break, rotate, or become part of imaginative scenes. The four samples and full brief are in `docs/design/widget-icon-variants/README.md`. Keep the actual launcher icon unchanged.

- [ ] **DR5-048 — Prototype the icon-sized tap-to-open widget**
  - Outcome: a 1 × 1 home-screen widget displays the DraftingRoom5 identity, launches the normal app route on tap, and can rotate a small set of images without an always-on service.
  - Scope: new app-widget provider/layout/manifest wiring, prototype artwork derivatives of the four approved samples, scheduling integration, focused tests, and widget preview only; do not alter the launcher icon or Phase 6 product behavior.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android widget feasibility and lifecycle implementation.
  - Depends on: DR5-039
  - Status: `ready`
  - Acceptance: widget advertises a one-cell target size with sensible minimums; its full tile has a direct activity `PendingIntent` that opens the app through its normal entry point; a launcher can render the 5 and frame at icon-like size without clipping; rotation among the four samples works on supported host/emulator tests using a platform-supported, approximately hourly and inexact cadence plus safe refresh on relevant app interactions; no 30-second promise, foreground service, wake lock, or per-minute background polling; widget removal cancels unnecessary work; API 28–36 behavior and no-widget case are tested. If a real 1 × 1 tile or practical rotation fails on the available launcher, mark `needs_input` with evidence before commissioning 50 assets rather than silently enlarging the widget or changing the brief.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: use `docs/design/widget-icon-variants/` as approved direction only, not as unoptimized runtime PNGs. An approximately hourly rotation is a default to validate against Android battery guidance, not an exact-time guarantee. Keep the widget's click target and accessible label clear.

- [ ] **DR5-049 — Specify fifty distinctive widget-image concepts**
  - Outcome: a production-ready catalog names 50 visually different still transformations with a common icon-sized art contract.
  - Scope: `docs/design/widget-icon-variants/` concept catalog, art prompts, small-size QA rubric, safe-zone and optimization specification only.
  - Model: `gpt-5.6-sol`
  - Model reason: creative but bounded art-direction inventory following the proven widget footprint.
  - Depends on: DR5-048
  - Status: `blocked`
  - Acceptance: the catalog has 50 unique numbered IDs and one distinct scene/effect each; concepts are visually clear at one-cell size, while the 5 and navy/ivory/gold cues recur across the set rather than being compulsory in every image; intentional mirrored, obscured, fragmented, or hidden-5 concepts are explicitly allowed; concepts include the approved dumbbell, flex, fire, and melt seeds plus a 5 lying down and being abducted by aliens; several concepts break, overlay, rotate, fold, or repurpose the gold frame; training, surreal, elemental, kinetic, and playful ideas are balanced without near-duplicate recolors; exact prompts, negative constraints, target geometry, naming, and acceptance-at-rendered-size checks are recorded for five ten-image production batches.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: the first four images are approved examples, not a creativity ceiling; they may count toward 50 only if they pass the same final asset QA. No videos or animated frame sequences are required.

- [ ] **DR5-050 — Produce widget images 01–10**
  - Outcome: the first ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 01–10, image-generation prompts/provenance, source masters, optimized widget derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-049
  - Status: `blocked`
  - Acceptance: ten distinct named image files match catalog IDs 01–10; use one built-in image-generation call per distinct asset and record final prompts/provenance; each has a distinct visual idea that reads at one-cell size, with intentional exceptions allowed for numeral legibility and individual palette/frame treatment; no stray text or watermark, safe 1 × 1 crop, and an optimized runtime derivative; a contact sheet proves the concept and collection-level identity work at actual widget size; four approved samples are reused only if they meet the same standards.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: preserve masters separately from optimized runtime resources. Do not generate ten minor color variations.

- [ ] **DR5-051 — Produce widget images 11–20**
  - Outcome: the next ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 11–20, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-050
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 11–20 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–10.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: include ambitious border manipulation and a distinct silhouette for every concept.

- [ ] **DR5-052 — Produce widget images 21–30**
  - Outcome: the third ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 21–30, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-051
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 21–30 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–20.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: include surreal concepts such as the 5 lying down and being abducted by aliens if not already in the earlier batches.

- [ ] **DR5-053 — Produce widget images 31–40**
  - Outcome: the fourth ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 31–40, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-052
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 31–40 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–30.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: maintain the common composition while allowing the border and 5 to break or move for a stronger visual gag.

- [ ] **DR5-054 — Produce widget images 41–50**
  - Outcome: the final ten catalog concepts complete a validated 50-image widget set.
  - Scope: concept IDs 41–50, prompts/provenance, source masters, optimized derivatives, final 50-image contact sheet and quality pass only.
  - Model: `gpt-5.6-sol`
  - Model reason: final independent art batch plus whole-set visual consistency.
  - Depends on: DR5-053
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 41–50 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, and provenance checks; a 50-image contact sheet shows no duplicates, unclear ideas, accidental clipping, stray text, or inconsistent dimensions, while deliberate obscured/mirrored 5s and broken/absent borders are judged on artistic merit rather than rejected by rule; all 50 optimized derivatives are cataloged and resource-size impact is measured.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: repair or regenerate weak images before considering the batch complete.

- [ ] **DR5-055 — Integrate the fifty-image widget rotation**
  - Outcome: the widget reliably presents one of the 50 cataloged looks, rotates at the validated battery-conscious cadence, and still opens the app normally.
  - Scope: widget provider/scheduler, runtime artwork catalog/resources, lifecycle and click behavior, accessibility, tests, and widget screenshots only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android runtime integration after artwork and prototype validation.
  - Depends on: DR5-054
  - Status: `blocked`
  - Acceptance: exactly 50 approved IDs are reachable without duplicates or missing-resource fallbacks; rotation is stable across process death, reboot, app update, launcher recreation, and widget add/remove, never demands a precise wall-clock interval, and avoids an always-on background service; the rotating set retains a recognizable DraftingRoom5 identity without requiring a readable 5 in every individual image, and every tile remains one direct tap from normal app launch; resizing and multiple widget instances behave deterministically; API 28–36, battery/update behavior, semantics, and widget preview/screenshot tests pass.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: favor approximately hourly inexact changes per Android widget guidance. An app-open refresh may advance the look if it does not make rotation unexpectedly rapid. Do not modify the launcher icon itself.

- [ ] **DR5-056 — Audit the living widget and its fifty images**
  - Outcome: an independent review confirms the widget's tap, rotation, accessibility, battery behavior, and 50-image visual quality without weakening the app.
  - Scope: Phase 7 diff, native widget/device evidence, asset/contact-sheet inspection, focused and full regression checks, and a review artifact under `docs/reviews/`; fixes stay within Phase 7 scope.
  - Model: `gpt-6-astra`
  - Model reason: cross-version launcher lifecycle and battery review benefits from difficult independent reasoning.
  - Depends on: DR5-055
  - Status: `blocked`
  - Acceptance: review verifies every approved requirement against code/evidence, counts 50 distinct runtime images, inspects collection-level identity and each concept at actual one-cell size without penalizing deliberate obscured/mirrored/absent 5s or broken frames, checks direct app launch, time/update inexactness, widget removal, reboot, app update, multiple instances, accessibility and resource-size effects, and records physical-device checks or their unavailability honestly; all relevant unit, screenshot, lint, and build checks pass; unresolved findings block release.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: if the platform cannot uphold the approved one-cell/tap-to-open premise, seek user direction rather than disguising a larger or inert widget.

- [ ] **DR5-057 — Release the living widget milestone**
  - Outcome: the reviewed fifty-image widget ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 7 findings, documentation/version defaults, full verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic release procedure after independent audit.
  - Depends on: DR5-056
  - Status: `blocked`
  - Acceptance: DR5-048–056 and audit findings are complete; README/design status describes the widget and its inexact rotation honestly; default version name/code advance consistently from the actually released Phase 6 version; `testDebugUnitTest lintDebug assembleDebug` and all screenshot/widget checks pass; coherent Phase 7 work is committed and pushed once; the next increasing version tag is pushed, release workflow completes, and the signed APK is published and verified.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `YES`
  - Notes: never commit credentials, signing material, generated build files, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 7 tasks separately.

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
