# DR5-083 — Independent Phase 10 audit

Audit date: 2026-09-25. Scope: accumulated DR5-080–082 changes and corrective work below. Push **NO**: no commit, push, tag, release, or version change. Defaults remain **0.27.30 / 27032**.

## Findings and corrections

1. **Completion-sheet navigation and dismissal.** Manual adjustment Back returned to completion instead of adjustment review. Material sheet dismissal can hide the sheet before its callback; a failed keep-current save could leave the pending decision invisible. Back now follows manual → review → completion, dismissal restores the pending sheet until committed state removes it, and busy writes prevent dismissal. Mode changes reset scroll position without discarding saved drafts.
2. **Incomplete replacement review.** The adjustment review omitted replacement notes and artwork, although planned steps replace both. Before/after summaries now expose both visually and through semantics.
3. **New future-step defaults.** A newly appended step started from the original exercise instead of its immediate predecessor. It now starts from the preceding full prescription, matching the ordered-step comparison.
4. **Editor hierarchy and numeric bounds.** Artwork now precedes targets, followed by optional planned progression. Long valid integer values reserve space for both stepper buttons. Added screenshots cover maximum supported integers and the bottom of the editor at all four layouts.
5. **Focus after list changes.** Ordered steps and inserted-exercise rows retain stable identities and explicitly restore input focus after reorder/delete, with Add as the empty-list fallback. Runtime TalkBack focus is not certified by this source review.
6. **Documentation and audit coverage.** Updated README, RoutineEditor, GuidedSession, and TechnicalDesign to describe direct targets, optional full prescriptions, and Next/Adjust. Added malformed-value tests across nested stored prescriptions, receipts, backups, restore, and draft/request boundaries. Expanded native audit scrolling for short screens.

## Invariants reviewed

| Boundary | Evidence and result |
| --- | --- |
| Current exercise model | Required positive sets; independently optional positive reps/duration/weight. Weight is an integer number of pounds divisible by 5. No automatic progression, free-form target, measurements wrapper, or separate timer field remains in production. |
| Storage and backup | Strict JSON syntax, integer lexical validation, unknown/legacy fields rejected; validation traverses live routines, partial/history snapshots, replacement/inserted exercises, and receipts. Bad restore cannot publish or alter existing bytes. |
| Planned steps | Ordered full replacements; all name/notes/artwork/targets replace together. Added exercises remain independent, ordered immediately after the source. Consumption removes only the first step; exhaustion becomes absent progression. |
| Transactions | Repository serialization and revision/session/receipt checks protect one atomic routine-and-receipt publication. Manual adjustment preserves the planned queue. Duplicate application, stale routine/event, failed persistence, and undo conflict paths remain covered. |
| Sessions | Keep-current leaves live targets and queue intact. Existing session/history snapshots remain unchanged by progression. Explicit restart adopts current live targets. Draft savers, reconstructed repository state, timer foreground/background, and undo coverage remain intact. |
| Optional flow | Next exercise is primary at completion; Adjust is secondary. Manual adjustment can change multiple fields. Planned progression is offered only inside review. No automatic application or default future step is introduced. |
| Accessibility | Source review covers labels, merged summaries, explicit reorder actions, 48 dp controls, scrollable forms, and input-focus recovery. Screenshots cover compact/tall/landscape/2× text. Real TalkBack announcements and keyboard traversal require a device. |
| Retirement/privacy | No Retirement production code, manifest, backup-rule XML, or version configuration changed. Fitness backup allowlist remains separate from Retirement no-backup storage. Offline contract/reference/forecast checks pass. |

The approved written Phase 10 direction and DR5-080–082 handoffs were available. The parent task's original image attachments were not retrievable through task history; this audit compares against the written hierarchy and repository references, not a claimed pixel comparison with those attachments.

## Visual evidence

The initial complete comparison ran **471 cases**, with **111 expected changed/missing references**. Its original failure log is retained in `screenshots-before.log`. `visual/manifest.json` identifies all 111 candidates; `visual/differences-01.png` through `differences-24.png` retain before/after crops or new renders. All 24 pages were inspected before reference acceptance.

Changes comprise structured editor hierarchy; complete adjustment summaries; explicit seconds and numeric reps in routine/session displays; and 16 new editor-bottom, full future-step, and integer-boundary cases. Artwork itself is unchanged. Scroll continuation at viewport boundaries is intentional; the new bottom cases show the final controls and Save actions. Large-text artwork labels wrap vertically without overlap. Retirement references are outside the update scope.

| Preview group | Changed/new cases inspected |
| --- | ---: |
| Core shell routine/editor/session states | 21 |
| Manual adjustment | 4 |
| Adjustment review | 4 |
| Full future-step editor, top and bottom | 8 |
| Hangboard routine | 3 |
| Rice-bag routine/editor | 6 |
| Session artwork | 44 |
| Session sections | 9 |
| Structured editor | 4 |
| Structured editor bottom | 4 |
| Maximum target values | 4 |

The four unchanged completion-root renders were also inspected: Next exercise remains filled and primary, with Adjust exercise outlined below it. Existing ordered-step editor references were inspected separately. `accepted-reference-hashes.json` records the precise 111 rendered candidates reviewed before acceptance.

## Validation

- Initial focused Fast: **75 tests, 0 failures/errors/skips**, six suites; `fast-initial.log` and `fast-initial-results.json`.
- Final focused Fast after the last source fixes: passed in **244.6 seconds**, same six explicit test patterns; `fast-final.log`.
- Commit tier: passed in **244.3 seconds**; **453 tests, no failures/errors/skips**, lint **0 errors / 46 warnings**, debug APK assembled. `commit-gate.log` and `commit-unit-results.json`. Warnings match the prior API 37 baseline count.
- Final Release tier: passed in **672.3 seconds**, **453 tests / 62 suites**, **471 screenshot cases**, **0 failures/errors/skips**, **0 lint errors / 46 warnings**, debug APK assembled. `release-gate.log` and `final-check-summary.json`.
- Selective reference update passed; all **111** accepted PNG hashes match their reviewed candidates exactly. `reference-update.log` and `reference-hash-verification.json`. The four obsolete automatic-editor references remain deleted intentionally.
- Final APK: **84,328,528 bytes**, version **0.27.30 / 27032**, minimum/target/compile API **37**. Eight existing privacy/fixture canaries have **zero matches**. Identity, manifest, decoded Fitness-only backup/device-transfer rules, SHA-256, and passing 16 KiB ZIP alignment are retained in `apk-*.log` and `final-apk-scan.json`.
- Native instrumentation: `./gradlew.ps1 assembleDebugAndroidTest` **passed**, including Kotlin and Java compilation; `native-assemble.log`. Execution is unavailable: `native-devices.log` contains an empty connected-device list.
- Retirement contract: **14 tests passed**, `retirement-contract.log`.
- Retirement reference: **105 assertions passed**, `retirement-reference.log`.
- Retirement forecast: **14 full paths and 112 tax/Social Security rows passed**, `retirement-forecast.log`.

Commands use `./tools/verify.ps1 -Tier Fast -Tests` with StructuredTargetAuditTest, GuidedSessionUiTest, ProgressionApplicationTest, ExerciseProgressionTest, GuidedRoutineEditorTest, and ProgressionAuditTest; then `./tools/verify.ps1 -Tier Commit` and `./tools/verify.ps1 -Tier Release`. The initial diagnostic screenshot comparison and selective reference update use `./gradlew.ps1 validateDebugScreenshotTest` / `updateDebugScreenshotTest`; the update filters only the eleven reviewed Fitness methods above. No clean, disabled compilation, reduced screenshot threshold, or weakened test expectation is used. `collect_evidence.py` reads final reports and the actual APK; it does not mutate either.

## Environment limitations

`adb devices -l` returned an empty device list. Native instrumentation can be compiled here, but no device execution, actual process kill/relaunch, TalkBack speech, hardware-keyboard traversal, or foreground timer interaction is claimed. JVM reconstruction and saver tests plus screenshot renders are complementary evidence, not substitutes for those checks. Existing Phase 9 API 37/live-provider exceptions are not reclassified as successful checks by this audit.

## Disposition

All identified Phase 10 code findings are corrected, all available checks pass, and no known code blocker remains. DR5-083 is complete; DR5-084 may proceed with the documented native limitations and its own release gate. No commit, push, tag, release, or version change was performed. The accumulated work remains local for the release task. Final whitespace validation passes.
