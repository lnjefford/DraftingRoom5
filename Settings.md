# Settings redesign specification

Status: approved visual direction for future implementation.

## Selected reference

- [Approved Settings mockup](docs/design/settings/settings-selected.png)
- Shared visual system: [Dashboard.md](Dashboard.md)

The mockup defines hierarchy and styling. Build the screen with native Jetpack Compose components and preserve all existing settings behavior.

## Approved direction

- Use a standard secondary app bar with back arrow and `Settings`; do not show the measured-five icon here.
- Organize controls into four functional groups: Training, Workout feedback, Connections & data, and App.
- Use one raised tonal surface per group with internal dividers. Do not wrap every row in a separate card.
- Use tracked uppercase section labels with a short gold rule.
- Keep row titles and controls in sans-serif. The screen title may use the shared editorial serif if it remains visually aligned with the secondary app bar.
- Use functional descriptions only. Do not add motivational headings or slogans.
- Preserve a minimum 48 dp touch target and clear pressed/selected states for every row.

## Screen structure

Implement as an edge-to-edge `Scaffold` with transparent top bar and a scrollable content column using approximately 20 dp horizontal margins.

### Training

One grouped surface with two navigation rows:

1. **Customize dashboard**
   - Description: `Choose and reorder dashboard sections`.
   - Trailing summary: number of visible dashboard sections, for example `5 visible`.
   - Opens the existing `DashboardCustomization` destination.
2. **Schedules & routines**
   - Description: `Plan the week and edit routines`.
   - Trailing summary: number of active recurring schedule entries, for example `7 sessions`.
   - Opens the existing `PlanManagement` destination.

Use lightweight leading icons, trailing chevrons, and a divider between rows.

### Workout feedback

One grouped surface containing:

1. **Voice announcements**
   - Description: `Countdowns, timers, sets, and completion`.
   - Trailing switch bound to `VoiceAnnouncementSettings.enabled`.
   - Preserve initializing, ready, and unavailable TTS states. When unavailable, disable the switch and show a concise status without hiding haptic controls.
2. **Voice rate**
   - Show semantic label and current value, for example `Normal · 1.00×`.
   - Slider remains bound to the existing minimum/maximum range and steps.
   - Disable it when voice announcements are off or TTS is unavailable.
   - Preserve accessible increment/decrement semantics and announce the rate value.
3. **Haptic feedback**
   - Description: `Tactile cues during guided sessions`.
   - Trailing switch bound to the existing haptic setting.
   - Continue respecting Android's system haptic setting and device vibrator availability.

### Connections & data

One grouped surface with two summary/navigation rows:

1. **Health Connect**
   - Show status dot and concise status such as `Connected · Withings`.
   - Trailing action reads `Manage` when connected.
   - Preserve every current state: checking, permission required, connected, provider update required, unavailable, and error.
   - The row or trailing action must expose the existing connect/permission/settings behavior appropriate to the current state.
2. **Automatic backups**
   - Summary such as `On · Last backup today at 8:42 AM`.
   - Status color is mint when current, gold when stale or needing attention, and neutral when off.
   - Tapping opens an inline expanded section, bottom sheet, or dedicated detail surface containing all existing actions: enable/disable, Back up now, Restore latest, and Android backup settings.
   - Preserve explanatory privacy text stating what snapshots include and that Health Connect data/permissions are excluded.

The compact summary row must not remove existing backup recovery capabilities.

### App

One grouped surface:

1. **App updates**
   - Summary uses the runtime version name and actual update state; never hard-code the mockup's version.
   - Trailing action may read `Check`, `Install`, `Retry`, or a busy state depending on `AppUpdateStatus`.
   - Preserve package, version-code, and signing-certificate verification before installation.
   - Surface network/download/install errors without replacing the entire Settings screen.
2. **About DraftingRoom5**
   - The mockup proposes a future row for privacy, licenses, and app information.
   - Do not add an inert row. Implement it only with a real destination or bottom sheet; otherwise omit it until that content exists.

## Group and row styling

- Group radius: approximately 18–22 dp.
- Group background: `#101D2D` to `#17283C` with a subtle `#263B53` border.
- Row horizontal padding: 16–18 dp.
- Standard row minimum height: 72 dp; allow growth for font scaling and state messages.
- Leading icon area: approximately 40–48 dp, visually consistent but not independently tappable unless it has a distinct action.
- Internal dividers begin after the icon region where practical.
- Row title: 16–18 sp medium/semibold.
- Description: 13–15 sp secondary text.
- Trailing summaries/actions: blue for actionable text, mint/gold only for status.
- Switches use the project's blue active treatment and retain Material semantics.
- Do not use decorative gradients or glassmorphism.

## State and interaction rules

- Make navigation rows clickable across their full width.
- Avoid nested click targets with duplicate screen-reader actions. If a trailing label performs the same action as the row, merge semantics.
- Switch rows may allow tapping the row to toggle only when that behavior is clear and does not conflict with a separate details action.
- Show transient action feedback near the affected group rather than as unrelated page-level copy.
- Keep loading indicators local to the row performing work.
- Preserve state while navigating to child screens and returning.
- Back navigation returns to the dashboard.

## Accessibility

- Every navigation row, switch, slider, and action must provide at least a 48 × 48 dp target.
- Status dots must have accompanying status text; never communicate state by color alone.
- Announce grouped controls with complete labels, values, and enabled/disabled state.
- Support large fonts by allowing trailing summaries to move below titles rather than truncate essential status.
- Ensure the long settings page scrolls correctly with TalkBack focus and keyboard/D-pad navigation.
- Keep switches and slider compatible with platform accessibility actions.

## Suggested Compose structure

- `SettingsScreen`
- `SettingsTopBar`
- `SettingsSection`
- `SettingsGroup`
- `SettingsNavigationRow`
- `SettingsToggleRow`
- `VoiceRateRow`
- `HealthConnectSettingsRow`
- `AutomaticBackupSettingsRow`
- `AppUpdateSettingsRow`

Prefer data-driven row models only where they simplify truly repeated navigation rows. Keep stateful Health Connect, backup, update, switch, and slider content as explicit composables.

## Verification

- Test navigation to dashboard customization and plan management.
- Test voice enable/disable, unavailable TTS behavior, rate persistence, and slider disabled state.
- Test haptic preference persistence and system-setting behavior.
- Test every Health Connect connection/permission/provider state.
- Test backup off/current/stale/failure states and all backup actions.
- Test update current/available/downloading/error/install states.
- Verify compact and tall phones, large font scales, TalkBack, and screen rotation/recreation.

## Acceptance criteria

- All current Settings capabilities remain reachable.
- The screen uses four coherent functional groups rather than a stack of unrelated cards.
- No row or trailing action is inert.
- Status, error, and loading states remain local and understandable.
- The result is visually consistent with the approved dashboard and metric-detail screens.
