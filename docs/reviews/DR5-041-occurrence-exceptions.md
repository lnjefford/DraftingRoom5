# DR5-041 occurrence exception model

Implemented locally for the Phase 6 terminal milestone. No commit, push, tag, version bump, release, production menu, or production UI wiring was performed.

## Model and persistence

- `OccurrenceExceptions.kt` defines scheduled (no stored exception), deferred, and skipped dispositions. A source `OccurrenceKey` never changes; deferred effective dates are exactly the next civil day. Duplicate sources, explicit scheduled records, invalid dates, dangling exceptions, skipped progress/history, and inconsistent durable dates reject.
- `AppDocument.kt` and `AppDocumentCodec.kt` require `occurrenceExceptions` on the current document and `effectiveDate` on partial/history records. There is one strict current reader, with no missing-field fallbacks or compatibility path.
- `AppRepository.kt` implements atomic defer, skip, and exact-exception Undo; identical repeated commands do not write. Started/completed sources reject disposition changes. Guided open/restart/finish and linked completion preserve source identity and effective date. Failed writes retain both published and stored state.
- Schedule/routine deletion prunes unstarted exceptions and retains those supporting durable progress/history. Removed-schedule partials remain saved sessions. Plan/full reset clears exceptions. Existing current-schema backups carry all fields and restore clears imported timers while retaining dates and progress.
- `DashboardSupport.kt` produces collision-safe source keys and effective dates. A daily source deferred onto tomorrow coexists with the ordinary tomorrow occurrence. Deferred completion appears on the effective date, including after deletion using its history snapshot. `WorkoutHistory.kt` now returns full completed occurrence keys per effective date rather than collapsing schedule IDs.

## Verification

`OccurrenceExceptionsTest` has 16 deterministic tests covering daily collisions, neighboring weeks/dates, both completions on one effective date, skip without history, rejected stale launch/open, idempotency, exact Undo, failed defer/skip/Undo/finish/delete/reset/restore, guided restart/recreation/completion, deletion cascades, recurrence edits, DST spring/fall dates, timezone selection, midnight, leap day, year rollover, overflow, current-schema corruption, and backup restore of a deferred running partial.

The complete `testDebugUnitTest` suite passed: **216 tests in 35 suites, zero failures or errors**. This includes codec, repository, Dashboard, history, linked completion, backup, and durable-session regressions. Test XML: `%TEMP%/draftingroom5-sol-release/app/test-results/testDebugUnitTest/` (generated verification output, not a deliverable).

`lintDebug` also passed with **0 errors and 43 warnings**. The combined final gate completed successfully in 3m 42s. The updated technical-design JSON example parses with its required exception array, changed-file whitespace checks pass, and the retired schedule-ID-only history helper has no remaining references.

Build setup: bundled Java 17, populated `C:/Users/lnjef/.gradle` cache, `.tooling/astra-core-android`, and `.tooling/sol-release.init.gradle`; offline, one Gradle worker, in-process Kotlin compiler, and `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`. The first attempt found the workspace cache unpopulated; the successful checks used the existing user cache with approved access. No dependency or build configuration changed.

No screenshot updates or device checks were needed for this domain-only item. The only additional `MainActivity.kt` edit supplies required identity/date fields in the existing private `previewSession` fixture. Existing accumulated Phase 6 source/artwork/reference edits remain in place.

## DR5-042 integration handoff

Read the per-occurrence section of `TechnicalDesign.md`. Use `dashboardSessions(document, selectedDate)` and pass `document.occurrenceExceptions` to `savedDashboardSessions`. Every list key, launch, completion lookup, Undo-completion request, and guided route must use the projected `DashboardSession.occurrence`; never reconstruct it from the selected/effective date or identify a card only by schedule ID. Display placement/context uses `effectiveDate`.

Only current-day `SCHEDULED` Start cards expose the menu. Supply the current local date at execution to defer/skip. Save before hiding cards or announcing success; retain the exact committed exception for `undoOccurrenceException`. Reject stale or started/completed actions with local recovery. A moved card cannot be moved again; changing its disposition requires Undo first. These UI changes belong exclusively to DR5-042 and are not implemented here.
