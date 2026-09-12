# Schedules and routines redesign specification

Status: implemented and audited through DR5-030. Both recurring Schedule and unified Routines tabs follow the selected references.

## Selected reference

- [Approved recurring Schedule mockup](docs/design/schedules-routines/schedule-selected.png)
- [Approved unified Routines mockup](docs/design/schedules-routines/routines-selected.png)
- [Approved recurring schedule editor](docs/design/schedules-routines/schedule-editor-selected.png)
- Parent settings system: [Settings.md](Settings.md)
- Session-card language: [Dashboard.md](Dashboard.md)

Build the screen from native Jetpack Compose components. The screenshot is a design reference only.

## Core product model

- This is a recurring weekly schedule, not a calendar for a specific dated week.
- The selector shows weekdays and scheduled-item counts only. Do not show dates, months, or week navigation.
- `ScheduleEntry.days` is the single source of weekday membership.
- A count represents the recurring items assigned to that weekday.
- A dash represents no scheduled items/recovery.
- Each schedule entry stores a routine reference and recurring weekdays. The routine owns its name, artwork, execution type, exercise list or app link, so those values cannot drift between screens.

Every routine is user-managed: it can be renamed, assigned curated artwork, scheduled, and opened for editing. `Custom routine` is not a user-facing type. Execution behavior is represented by two explicit types:

- `Guided routine`: DraftingRoom5 owns an ordered list of exercises or activities and presents the completion experience in-app.
- `Linked-app routine`: DraftingRoom5 owns the routine's identity, artwork, and app link, then opens the configured external app to perform it. Separate schedule entries assign recurring weekdays.

## Approved decisions

- Use a standard secondary app bar with back arrow, title, and overflow menu; no brand icon.
- Split the screen into `Schedule` and `Routines` tabs.
- Use a compact seven-day weekday selector instead of seven expanded timelines.
- Selecting a weekday shows only that weekday's editable recurring items.
- Do not include a redundant Week at a glance section.
- Do not show blue/gray calendar status dots; counts already communicate schedule density.
- Do not show an enable/disable switch on every scheduled item.
- Use a drag handle for ordering and an overflow menu for Edit and Delete.
- Use only metadata DraftingRoom5 actually owns. Do not invent Just Run program week, run number, duration, pace, or distance.
- Keep the full-width `Add scheduled item` action and automatic-save confirmation.

## Screen shell

- Edge-to-edge transparent `Scaffold` on the shared navy canvas.
- Top app bar: Back, `Schedules & routines`, overflow.
- Overflow contains the infrequent `Reset built-in plan` action. Reset must retain the current confirmation and clearly state that custom changes will be replaced.
- Two equal tabs: `Schedule` and `Routines`.
- Preserve selected tab and weekday during ordinary recomposition and configuration changes.

## Schedule tab

### Weekday selector

- Section label: `WEEKLY SCHEDULE` with the short gold rule.
- Supporting text: `Choose a day to review and edit its sessions.`
- One raised tonal group containing seven equal targets.
- Each target contains:
  - One-letter weekday label.
  - Recurring scheduled-item count, or a dash if empty.
- Selected weekday uses the blue filled circle behind its letter and blue count text.
- Each target must be at least 48 dp and expose a full accessibility label such as `Wednesday, 1 scheduled item, selected`.
- Counts represent the entries that appear for that weekday. The clean model has no enabled/paused field.

### Selected weekday

- Header uses the full weekday name and a localized count such as `1 scheduled`.
- Stack all items active on that weekday with 12–16 dp spacing.
- Multiple items can be reordered using their leading six-dot handles.
- If no items exist, show a compact `Recovery day` state and retain the Add action.

### Scheduled-item management card

- Use a simplified management variant of the dashboard's routine card.
- Resolve name, type metadata, and artwork from the referenced routine. Do not store or render a second schedule-owned title, subtitle, destination, or image.
- Eyebrow identifies the routine type and app where applicable, for example `LINKED APP · JUST RUN`, `LINKED APP · FITBOD`, or `GUIDED ROUTINE`.
- Routine artwork occupies the right third and remains decorative for accessibility.
- Leading drag handle reorders items.
- Trailing overflow menu contains:
  - Edit
  - Delete
- Delete requires confirmation when loss would be surprising; the dialog should name the item and recurring days affected.
- There is no Start/Open action here because this is a management screen.

The clean model has no schedule-level enable/disable or paused state. A user who does not want an entry removes it; deletion is confirmed when loss would be surprising.

### Add action and persistence

- `Add scheduled item` opens the schedule editor.
- Changes save through the existing plan store.
- Show `Changes save automatically` as a small mint confirmation below the action.
- Do not reserve permanent blank content below; the screen remains scrollable and naturally grows when a day contains multiple items.

## Routines tab

- Use the section label `ROUTINES`, concise supporting text, and an `Add routine` action.
- Show every linked-app and guided routine together; do not separate built-in, custom, or app-linked routines into different sections.
- Each row includes routine name, explicit type, type-specific metadata, selected artwork, and trailing chevron/overflow.
- Linked-app metadata names the configured app, for example `Linked App · Fitbod` or `Linked App · Just Run`.
- Guided metadata uses `Guided Routine` and its item count.
- Optional schedule metadata states recurring weekdays without calendar dates.
- Tapping the row opens the routine editor.
- Overflow contains Rename and Delete.
- Deleting a routine must explain that schedule entries referencing it will also be removed.
- An empty state should explain that routines can be guided in-app or open another app, and provide one Add action.

## Schedule editor

The approved editor is a dedicated compact-phone screen. It contains only:

- Existing routine selection, with selected artwork, name, type, and item count/app metadata resolved from that routine.
- Repeat shortcuts: Weekly, Weekdays, Every day, and Custom.
- Authoritative recurring weekday selection.
- Plain-language recurrence preview such as `Every Saturday`.
- `Add to schedule` for a new entry or `Save changes` when editing.
- Cancel/Back behavior that does not save an invalid or incomplete new entry.

Do not expose title, subtitle, destination/app, artwork, or exercise fields here; edit those on the routine. A routine and at least one weekday are required. Repeat shortcuts only transform the weekday selection; the stored weekday set remains the source of truth. Never show calendar dates.

Adding a new entry is not autosaved before `Add to schedule`. Existing list reorder/delete changes may continue saving automatically. Editing an existing entry should use an explicit `Save changes` action unless the implementation can make autosave and Back behavior completely unambiguous.

## Visual system

| Role | Value |
| --- | --- |
| Canvas | `#07111F` |
| Raised surface | `#101D2D` to `#17283C` |
| Ivory | `#F4F0E7` |
| Secondary text | approximately `#B9C8DA` |
| Blue selection/action | `#76A9FF` |
| Mint saved/completed | `#4FE0B0` |
| Gold rule/destructive-reset emphasis | `#FFC66D` |
| Border/divider | `#263B53` |

- Use serif only for focused session/routine titles; management controls remain sans-serif.
- Avoid decorative gradients, nested card stacks, and persistent destructive-action buttons.
- Keep routine artwork consistent with the dashboard card system.

## Accessibility

- Provide 48 dp targets for tabs, weekdays, drag handles, overflow actions, and Add.
- Provide accessible Move up/Move down custom actions in addition to drag gestures.
- Announce recurring day membership explicitly in the schedule editor.
- Do not communicate selected weekday, routine type, paused state, or item count by color alone.
- Preserve focus after reorder, edit, delete, and tab changes.
- Support large fonts by allowing card metadata to wrap and artwork to reduce or disappear.

## Suggested Compose structure

- `PlanManagementScreen`
- `PlanManagementTopBar`
- `PlanManagementTabs`
- `RecurringWeekSelector`
- `SelectedWeekdaySchedule`
- `ManagedScheduleCard`
- `RoutinesList`
- `ManagedRoutineRow`
- `ScheduleEditorSheet`
- `DeleteScheduleDialog`
- `ResetPlanDialog`

Keep schedule membership, counts, reorder operations, repeat-pattern conversion, deletion, and persistence outside visual composables so they remain unit-testable.

## Verification

- Test all repeat shortcuts and custom weekday combinations; verify that the stored weekday set is authoritative.
- Test a day with zero, one, and multiple items.
- Test items assigned to multiple weekdays and count updates.
- Test reorder persistence and accessible move actions.
- Test edit/delete and routine-reference deletion behavior.
- Test routine tab empty/populated states.
- Verify compact phones, large fonts, TalkBack, and process recreation.

## Acceptance criteria

- The screen never implies a specific dated week.
- Every recurring weekday and item is reachable.
- Counts match the visible active schedule.
- No redundant Week at a glance section, status dots, or per-card enable switch appears.
- External-program details are never fabricated.
- Schedule entries contain only routine ID, recurring weekdays, and ordering; routine-owned fields are never duplicated.
- The screen is visually consistent with the approved dashboard and Settings system.
