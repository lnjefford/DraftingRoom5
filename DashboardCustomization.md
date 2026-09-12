# Dashboard customization redesign specification

Status: implemented and audited through DR5-030 with pointer, accessibility, and keyboard reorder paths.

## Selected reference

- [Approved Customize dashboard mockup](docs/design/dashboard-customization/dashboard-customization-selected.png)
- Parent settings system: [Settings.md](Settings.md)
- Dashboard system: [Dashboard.md](Dashboard.md)

Build this screen with native Jetpack Compose components. The mockup is a visual reference, not an app asset.

## Approved direction

- Use a conventional secondary app bar with back arrow and `Customize dashboard`; no brand icon.
- Present all six real `DashboardCard` entries in one unified raised list.
- Replace the visible up/down arrow cluster with drag handles.
- Retain a visibility switch for every entry.
- Show a live visible-count summary and make automatic persistence explicit.
- Keep reset as a quiet gold text action, followed by a confirmation dialog; do not use an oversized outlined button.
- Do not use motivational copy.

## Content and default example

The design reference shows this order:

1. Today's training — visible
2. Weight — visible
3. Body fat — visible
4. Distance — visible
5. Lean mass — visible
6. Workouts — hidden

The running app must always render the actual saved `DashboardLayout`; do not hard-code that sample order or visibility.

## Layout

- Edge-to-edge transparent `Scaffold` over the shared navy canvas.
- Scrollable content with approximately 20 dp horizontal margins.
- Tracked uppercase section label `DASHBOARD SECTIONS` and short gold rule.
- Supporting instruction: `Choose what appears and drag to change the order.`
- Trailing summary: `{visible} of {total} visible`.
- One rounded group containing every row with internal dividers.
- Below the group, show a small mint confirmation such as `Changes save automatically`.
- End with a gold `Reset dashboard` text action and reset icon.

## Reorderable rows

Each row contains:

- A meaningful decorative icon for the dashboard section.
- Title from `DashboardCard.title`.
- Description from `DashboardCard.description`, adjusted only where the redesigned dashboard has a clearer user-facing phrase.
- Visibility switch bound to `DashboardCardPreference.visible`.
- Six-dot drag handle at the trailing edge.

Suggested row dimensions:

- Minimum height: 86–96 dp; allow growth at large font scales.
- Horizontal padding: 16–18 dp.
- Leading icon region: 44–48 dp.
- Switch and drag handle must each retain a 48 dp interaction region without making the row feel crowded.
- Hidden rows remain readable at accessible contrast. Reduce emphasis modestly; do not fade them into illegibility.

## Reordering behavior

- Long-press or drag from the handle to reorder.
- Lift the active row with a tonal/elevation change and provide haptic feedback only if system/app haptics allow it.
- Auto-scroll when dragging near list edges.
- Persist the new order through the existing `DashboardLayoutStore` after a completed move.
- Maintain stable keys using `DashboardCard.name`.
- Cancelled drags must leave the saved order unchanged.
- Do not require users to toggle a section on before moving it.

Drag gestures alone are not accessible. Provide equivalent custom accessibility actions for `Move up` and `Move down`, and support keyboard/D-pad reordering where practical. The actions may be available through semantics or a contextual menu without appearing as permanent arrow buttons.

## Visibility behavior

- Switch changes should persist immediately through the existing layout update path.
- The visible count updates immediately.
- Preserve at least one visible dashboard section. If the product intentionally allows an empty dashboard, replace this rule with a designed empty state before implementation.
- Tapping the switch must not initiate a drag.
- Avoid duplicate switch semantics if the whole row is also toggleable.

## Reset behavior

- `Reset dashboard` opens a confirmation dialog.
- Explain that reset restores the default order and visibility.
- Confirming uses `DashboardLayout()` and persists the result.
- Cancelling makes no changes.
- After reset, update the list and visible count immediately and provide a brief accessible confirmation.

## Visual tokens

Use the established settings tokens:

| Role | Value |
| --- | --- |
| Canvas | `#07111F` |
| Raised group | `#101D2D` to `#17283C` |
| Primary text | `#F4F0E7` |
| Secondary text | approximately `#B9C8DA` |
| Active switch | `#76A9FF` |
| Saved confirmation | `#4FE0B0` |
| Reset accent | `#FFC66D` |
| Border/divider | `#263B53` |

Use sans-serif typography throughout the list. Reserve the editorial serif for major display content elsewhere; this task-oriented screen benefits from compact clarity.

## Accessibility

- Back, switches, drag handles, reset, and alternative reorder actions require 48 dp touch targets.
- Announce position, visibility, and available move actions, for example `Weight, visible, position 2 of 6`.
- Do not communicate the hidden state only through opacity or switch color.
- Preserve focus on the moved row after a reorder.
- Ensure TalkBack reorder actions update their position announcements immediately.
- Allow descriptions and controls to reflow at large font scales without overlap.

## Suggested Compose structure

- `DashboardCustomizationScreen`
- `DashboardCustomizationHeader`
- `ReorderableDashboardList`
- `DashboardPreferenceRow`
- `AutoSaveStatus`
- `ResetDashboardAction`
- `ResetDashboardDialog`

Keep reorder mechanics separate from persistence and `DashboardLayout` transformations so `moveCard`, normalization, visibility, and storage remain unit-testable.

## Verification

- Test every card can move to the first, middle, and last positions.
- Test hidden cards can be reordered.
- Test visibility persistence and visible-count updates.
- Test normalization when stored data omits a newly introduced card.
- Test reset confirmation, cancellation, and persistence.
- Test TalkBack custom move actions and focus retention.
- Verify compact devices, large fonts, keyboard/D-pad input, and process recreation.

## Acceptance criteria

- All six real dashboard sections are represented exactly once.
- Order and visibility remain compatible with the existing stored layout.
- Pointer users can drag; accessibility users have equivalent move actions.
- No permanent up/down arrow cluster appears in the row UI.
- Changes persist automatically and reset remains recoverable through confirmation.
- The screen is visually consistent with the approved Settings design.
