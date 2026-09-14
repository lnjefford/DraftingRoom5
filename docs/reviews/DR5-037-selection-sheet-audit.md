# DR5-037 — Selection sheet audit

The current Compose modal/dialog/menu inventory was checked against the eight approved handoffs and the Phase 6 queue. Two modal sheets combine a selectable catalog with an explicit commit action:

| Surface | Entry points | Selection size | Commit behavior |
| --- | --- | ---: | --- |
| Routine artwork | Guided editor, linked editor, new linked-routine draft | 11 choices | Pending artwork stays local until Save; failed guided persistence leaves the sheet and pending choice open. |
| Paired exercise artwork | Exercise builder/editor | 8 choices | Pending artwork stays local until Save; it then updates only the exercise draft, which still needs Save exercise. |

Both use `PersistentSelectionSheet`: only the catalog scrolls in a bounded weighted region; title, supporting copy, and a separate full-width Cancel/Save footer remain fixed. The sheet content is constrained to the available height; the shared footer applies the union of IME and navigation-bar insets, and both actions retain at least 48 dp height. Catalog cards expose their names and selected state. Cancel and system Back invoke the existing dismiss callback without committing the pending choice.

Other surfaces were excluded because they do not combine selection with an explicit Save: Add routine chooses a type immediately; Choose app is a dedicated screen that advances on row tap; schedule weekdays and exercise fields belong to dedicated editor screens; Rename dialogs edit text; confirmation dialogs have no selection; overflow menus and dashboard/metric/settings selectors apply their action immediately or autosave. No Save action was added to those flows.

Native screenshot fixtures cover the shorter paired-exercise list and longer routine-artwork list at compact 320 × 640, 2× font 360 × 800, and landscape 800 × 360. These are host-rendered layout checks; actual soft-keyboard animation, system-bar overlays, Back focus, and touch scrolling still need an attached device to verify directly.

Verification: `testDebugUnitTest` passed 196 tests in 33 suites; `lintDebug` passed with zero errors; `updateDebugScreenshotTest` and `validateDebugScreenshotTest` passed against 151 native references, including all six new selection-sheet renders. The new compact, landscape, and 2× font references were visually inspected for title contrast, selected-state text, scroll containment, and the persistent footer. `git diff --check` passed.
