# DR5-021 planning and app-link audit

Reviewed 2026-09-11 against DesignReview.md, TechnicalDesign.md, SchedulesAndRoutines.md, RoutineEditor.md and the Dashboard/Settings/GuidedSession handoffs. Scope: the accumulated DR5-014–020 planning implementation. Push: NO; release delivery belongs to DR5-022.

## Corrected defects

- The exercise builder generated an ID on every recomposition, resetting input while typing. It now owns one saveable ID and retained input, uses the approved dedicated scrollable screen, and confirms dirty Back navigation.
- New guided routines accumulated child-edit revisions and could never pass the repository's new-routine revision check. Save now normalizes a new routine to revision one. A multi-position drag commits one revision; a drag back to its original order and unchanged child edits are no-ops.
- New guided drafts used ordinary remembered state and lost their name, artwork and exercises on recreation. A current-shape primitive-string saver preserves incomplete drafts without admitting them to canonical storage. Popped editor state is cleared while parent state remains available during child navigation.
- Cancelling Change app from a new linked routine discarded its parent draft. Picker Back now preserves the editor draft. Launch-recovery app selection stages the replacement in the editor for explicit Save instead of immediately rewriting the routine.
- Failed exercise, name or artwork writes closed their editor and lost the pending input. Those controls remain open until persistence succeeds. Failed schedule reorder rolls back its optimistic display.
- Routine edits with incomplete sessions were rejected without a way to acknowledge snapshot preservation. Entering that editor now explicitly confirms that future changes leave saved snapshots untouched; routine rename carries the same disclosure. Routine deletion names incomplete-session loss as well as recurring-entry loss. Schedule reassignment confirms snapshot preservation and rejects a stale editor after an external recurrence change.
- Reassigning a recurrence to a linked app incorrectly changed an existing partial's action to Start and its navigation target to the new routine. Dashboard projection now keeps Resume, the original routine ID and snapshot. Completed cards also use their historical snapshot.
- Launcher discovery now excludes unexported and permission-inaccessible activities, retains enabled/current-profile filtering and self exclusion, sorts localized labels, and preserves coroutine cancellation. Deep-link validation rejects reserved unsafe schemes, HTTP, credentials, control characters, malformed HTTPS and excessive length; package-constrained launch and recoverable launcher fallback remain in place. No broad visibility permission was added. Platform references: [visibility declarations](https://developer.android.com/training/package-visibility/declaring), [intent resolution](https://developer.android.com/guide/components/intents-filters).
- Reorder gestures use current callbacks and stable exercise composition keys. Keyboard Ctrl+Up/Down supplements accessibility move actions. Weekday targets are now genuinely 48 dp with horizontal scrolling on compact widths. Linked-editor artwork yields to compact/large text. Planning drag feedback respects the app haptic preference.

## Ownership and validation review

Schedule records still contain only ID, routine ID and weekday membership; order remains array order. Repeat shortcuts transform the authoritative nonempty weekday set. Rename/artwork/app edits update routine identity, not copied schedule fields. Repository generation/revision checks and atomic write-before-publish remain authoritative. Routine deletion cascades recurrences and partials while preserving completed history; deleting a recurrence preserves the routine and partials; reset explicitly clears plan, partials and history while retaining other preferences.

Eight PlanningAuditTest regressions cover multi-edit creation through recreation and storage reload, incomplete draft restoration, multi-position drag with saved snapshots, no-op edits, unsafe-link fallback, launcher eligibility, invalid app drafts/launch failures, and recurrence reassignment with partial/history identity. Existing planning, repository, codec and deletion tests remain part of the gate.

## Verification

Final `testDebugUnitTest lintDebug assembleDebug validateDebugScreenshotTest` gate passed: 147 unit tests in 29 suites, 30 screenshot comparisons with zero errors/failures/skips, lint with zero errors (31 warnings and one informational finding), and a 68,916,050-byte debug APK. Whitespace checks passed. APK inspection confirmed all 49 catalog WebPs and no selected mockup PNGs.

Native previews cover compact 320×800, tall 412×1100 and 360×1100 at 2× font scale. The exercise-builder fixture is included alongside the five planning screens and four core screens. All 18 planning/builder images were visually reviewed; the unchanged core images passed comparison. Reference images are under app/src/screenshotTestDebug/reference. The Java 17 offline gate used `.tooling/astra-render.init.gradle` with isolated temporary build output; logs are `.tooling/astra-planning-verified.log`. No build outputs, signing material, version changes, commits, pushes, tags or release were created for delivery by this task.

## Device-only checks and follow-through

ADB reported no attached device. Screenshot rendering and JVM tests do not prove actual typing, Android process recreation, drag touch streams, keyboard focus, TalkBack announcements, IME insets, or installed-app interactions. Before release, manually exercise create → rename/artwork → add/edit exercise → rotate/background → Save, app-picker Cancel from both new and existing editors, failed-save retry, multi-row drag/cancel, and saved-session acknowledgement/reassignment. Verify app uninstall/disable and deep-link/launcher/store failures on API 28–32 and 33+ without granting broad visibility.

The durable session execution/resume engine is explicitly Phase 4 work; this audit verifies planning's snapshot ownership/projections and must not be read as certification of that future engine. The existing synchronous repository facade and large MainActivity remain architectural follow-through from the core audit; avoid implying performance or full lifecycle validation from screenshots. No critical/high planning defect remains identified by this source, transaction and rendering review; physical interaction coverage remains the release checklist above.
