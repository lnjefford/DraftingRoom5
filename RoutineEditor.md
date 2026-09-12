# Routine editor redesign specification

Status: implemented and audited through DR5-030 for guided routines, linked-app routines, installed-app selection, exercises, and curated artwork.

## Selected references

- [Approved Routine editor mockup](docs/design/routine-editor/routine-editor-selected.png)
- [Approved built-in artwork picker](docs/design/routine-editor/artwork-picker-selected.png)
- [Approved linked-app Routine editor](docs/design/routine-editor/linked-app-routine-editor-selected.png)
- [Approved strength artwork directions](docs/design/routine-editor/strength-artwork-options.png)
- [Approved Add routine type chooser](docs/design/routine-editor/add-routine-type-chooser-selected.png)
- [Approved linked-app picker](docs/design/routine-editor/choose-linked-app-selected.png)
- [Approved exercise builder and paired-artwork picker](docs/design/routine-editor/exercise-builder-artwork-selected.png)
- Parent schedule system: [SchedulesAndRoutines.md](SchedulesAndRoutines.md)
- Guided-session system and paired exercise artwork: [GuidedSession.md](GuidedSession.md)

Build these interfaces from native Jetpack Compose components. The screenshots define visual intent and are not production UI assets.

## Approved decisions

- Use a standard secondary app bar with Back, `Routine editor`, and overflow; no brand icon.
- Display the editable routine name as the large editorial heading in page content so long names can wrap safely.
- Show selected routine artwork in the header's right side.
- Provide compact `Rename` and `Change artwork` actions beneath the routine metadata.
- Present exercises in one unified reorderable list with drag handles and overflow menus.
- Do not show permanent up/down, edit, or delete button clusters.
- Place `Add exercise` after the complete exercise list, matching `Add scheduled item` on the schedule screen.
- Show the automatic-save confirmation after the Add action.
- Use a curated built-in artwork picker; do not offer arbitrary user photos.
- Treat every routine as editable. `Guided` and `Linked app` describe execution behavior, not whether the routine is customizable.

## Routine editor structure

1. **Top app bar**
   - Back arrow, `Routine editor`, and overflow.
   - Overflow is reserved for infrequent routine-level actions; do not duplicate Rename or Change artwork there unless compact layouts require consolidation.
2. **Routine header**
   - Eyebrow `GUIDED ROUTINE` or `LINKED APP` and short gold rule.
   - Large serif routine name with natural wrapping.
   - Guided routines show an exercise count derived from their list; linked-app routines show the configured app.
   - Selected artwork aligned right, decoratively cropped and faded into the navy canvas.
   - Compact text actions: Rename and Change artwork.
3. **Exercises**
   - Section label `EXERCISES` and trailing instruction `Drag to reorder`.
   - One unified raised list with internal dividers.
   - Every exercise in the saved routine appears in the scrollable list. The continuation dots in the concept image are only a visual shorthand; do not render them in the app.
4. **Add and save status**
   - Full-width compact `Add exercise` action after the actual final exercise.
   - Small mint `Changes save automatically` confirmation below it.

## Exercise rows

Each row contains:

- Leading six-dot drag handle.
- Exercise name.
- Sets and target, for example `3 sets · 12–15 reps`.
- A blue timer indicator only when `timerSeconds` is non-null.
- Trailing overflow menu with Edit and Delete.

Behavior:

- Dragging reorders exercises and persists the completed move.
- Provide equivalent accessible Move up and Move down custom actions.
- Tapping the row may open Edit if that behavior remains consistent and does not conflict with drag gestures; otherwise use overflow only.
- Delete should identify the exercise and require confirmation where accidental loss is likely.
- Notes remain available in the editor. Avoid expanding long notes into every list row; optionally show one truncated supporting line when useful.
- Stable keys use `Exercise.id`.

## Exercise editor

Use these fields and validation:

- Exercise name
- Sets as a required positive integer
- Target
- Notes
- Timed exercise switch
- Timer seconds when timed
- Curated paired artwork selection
- Save and Cancel

Prefer a modal bottom sheet or dedicated editor on compact phones over a crowded alert dialog. Keep numeric input appropriate for timer seconds and require a positive value when timed.

The approved builder uses a dedicated scrollable screen with one artwork row. `Change` opens the exercise-artwork picker described in [GuidedSession.md](GuidedSession.md). The picker shows a curated grid and a paired preview labeled `LIST` and `HEADER`; users make one selection and never manage the two exports separately. A new exercise is not persisted until `Save exercise` succeeds. Editing an existing exercise may retain the current autosave model only if Cancel still restores the original value predictably.

## Linked-app routine editor

Use the same app bar, header, Rename, Change artwork, schedule summary, autosave status, and visual tokens as the guided editor. Replace the exercise list with an `APP CONNECTION` group:

- Show the selected app name and icon.
- Show a plain-language connection state such as `Installed · Ready to open`; never imply account authorization unless the integration actually has it.
- `Change` opens the supported-app picker.
- `Test app link` attempts the same launch intent that the routine will use, without changing saved configuration.
- If the app is unavailable, show `App not installed` and offer an appropriate install or change-app action. Do not leave a dead launch control.
- The editor may show recurring weekdays and an `Edit schedule` action backed by schedule entries; the routine record itself does not own those days. Never show calendar dates.
- Retain a destructive `Delete routine` action at the bottom with confirmation that names affected schedule entries.

Opening a linked routine from the dashboard should attempt the stored deep link first and fall back to the app's normal launch intent when safe. A launch failure must keep the user in DraftingRoom5 and offer recovery rather than silently doing nothing.

## Add routine flow

`Add routine` first opens a large modal bottom sheet titled `Add routine` with the supporting line `Choose how this routine starts.` It contains exactly two full-row actions:

- `Guided routine` — `Build a list of exercises or activities to complete here.`
- `Linked-app routine` — `Open another app when it’s time to begin.`

Tapping a row advances immediately, so the sheet has no radio selection, Save, or Next button. `Cancel` dismisses without creating anything. The type chooser must use explicit text and icons, not color alone, and each row must be one accessible touch target.

After choosing Guided, create a draft and open the guided editor. After choosing Linked app, first select an app and then open the linked-app editor with that app prefilled. Do not persist an incomplete routine until required fields are valid; navigating back should either discard the transient draft or clearly confirm when the user has entered meaningful data.

### Linked-app picker

- Use a dedicated secondary screen titled `Choose app` with Back and no brand icon.
- Explain that the user is choosing an installed app to open, not connecting or authorizing an account.
- Show compatible launchable apps in one unified list with app icon, name, concise category, explicit `Installed` status, and a trailing chevron.
- Search filters locally by the visible app name. It is useful when many compatible apps exist and may be omitted when only a very short fixed set is supported.
- Tapping a row immediately continues to the linked-app editor; there is no radio state or redundant Continue button.
- Only show apps DraftingRoom5 can safely launch. Do not request broad installed-app visibility solely to populate this UI; use the narrowest Android package-visibility approach that supports the final implementation.
- Keep the app package identifier as the durable link while displaying the localized app label. Handle uninstall, rename, and unavailable-launch cases without deleting the routine.

## Built-in artwork model

Define the routine model directly around an explicit execution type:

```kotlin
enum class RoutineExecution { GUIDED, LINKED_APP }
```

The shared routine record owns `id`, `name`, `artworkId`, and execution type. A guided routine owns exercises; a linked-app routine owns a durable package/deep-link configuration. Schedule entries reference the routine ID.

The artwork catalog may use an enum such as:

```kotlin
enum class RoutineArtwork(val storageId: String) {
    DUMBBELL("dumbbell"),
    GRIP_TRAINER("grip_trainer"),
    RUNNING_SHOE("running_shoe"),
    KETTLEBELL("kettlebell"),
    LEG_DAY("leg_day"),
    FULL_BODY("full_body"),
    PUSH_DAY("push_day"),
    PULL_DAY("pull_day"),
    JUMP_ROPE("jump_rope"),
    STOPWATCH("stopwatch"),
}
```

Persist the stable string ID rather than an Android resource integer. `artworkId` is required and receives an intentional default when a routine is created; the built-in Forearm routine uses `grip_trainer`.

## Artwork asset family

The approved core categories are:

- Dumbbell
- Grip trainer
- Running shoe
- Kettlebell
- Leg day: loaded barbell on a squat rack
- Full body day: kettlebell
- Push day: dumbbells on a bench
- Pull day: cable and lat-pulldown bar
- Jump rope
- Stopwatch

The day-specific strength subjects are distinct artwork choices, not text baked into an image. Full body has its own stable `full_body` ID even when its selected visual subject is a kettlebell; generic Kettlebell remains a separate reusable option.

Production assets must be created as one coordinated family:

- Same three-quarter camera angle and visual scale.
- Dark gunmetal/black base materials.
- Restrained electric-blue or mint rim light.
- Soft navy shadow and transparent background.
- No bright multicolor branding, text, logos, or photographic scene backgrounds.
- Subject positioned to crop safely into the right 28–35% of a home-page session card.
- Enough transparent padding to survive card crops, editor-header crops, and picker tiles.
- Optimized WebP or AVIF resources where supported, with appropriate density handling and decode fallback.

Do not use the picker screenshot as an asset source. Generate or construct each object as an individual production file, review at phone size, and store the source/licensing information with the project.

## Artwork picker

- Open as a large Material modal bottom sheet with a drag handle.
- Title: `Choose artwork`.
- Supporting text: `Designed to stay consistent across session cards.`
- Two-column grid of the current curated options; allow the catalog to grow without hard-coding a six-item limit.
- Each tile includes preview and label.
- Selected tile uses blue border, mint check, and explicit `Selected` text/semantics.
- `Cancel` dismisses without changing the routine.
- `Done` commits the selected artwork and persists the plan.
- Preserve a pending selection locally while the sheet is open; do not save on every exploratory tap.

The crop guides shown in the mockup are explanatory. In production, use a very subtle overlay only if it helps users understand the card crop; otherwise omit them because the choices are curated and already crop-safe.

## Visual tokens

Use the established values from `Dashboard.md`:

| Role | Value |
| --- | --- |
| Canvas | `#07111F` |
| Raised list/sheet | `#101D2D` to `#17283C` |
| Ivory heading | `#F4F0E7` |
| Secondary text | approximately `#B9C8DA` |
| Blue action/timer | `#76A9FF` |
| Mint selection/save | `#4FE0B0` |
| Gold rule | `#FFC66D` |
| Border/divider | `#263B53` |

Use serif only for the routine name. Keep exercise names, metadata, controls, and picker labels in sans-serif.

## Accessibility

- Back, overflow, Rename, Change artwork, drag handles, exercise menus, Add, picker tiles, Cancel, and Done require 48 dp targets.
- Mark decorative header artwork as decorative.
- Picker tiles announce label and selected state.
- Exercise rows announce title, sets, target, timed state, position, and available reorder actions.
- Preserve focus after reorder, edit, delete, and returning from the artwork picker.
- Support large fonts by allowing header actions to wrap and hiding/reducing decorative artwork before text clips.

## Suggested Compose structure

- `RoutineEditorScreen`
- `RoutineEditorHeader`
- `ReorderableExerciseList`
- `ManagedExerciseRow`
- `AddExerciseAction`
- `ExerciseEditorSheet`
- `RoutineArtworkPickerSheet`
- `RoutineArtworkTile`
- `DeleteExerciseDialog`
- `RenameRoutineDialog`

Keep artwork-ID serialization, reorder transformations, field validation, and persistence outside visual composables.

## Verification

- Test add, edit, delete, and reorder for timed and untimed exercises.
- Test all seven default Forearm routine exercises remain visible through scrolling.
- Test Add exercise appears after the true final item.
- Test artwork selection Cancel versus Done behavior and persistence.
- Test required artwork IDs, catalog resolution, and corruption handling.
- Review all artwork in the home card, editor header, and picker tile crops.
- Test compact screens, large fonts, TalkBack, keyboard/D-pad navigation, and process recreation.

## Acceptance criteria

- Exercise controls are clear without permanent action clusters.
- All exercises remain reachable and reorderable.
- Add exercise follows the list and is never represented by continuation dots.
- Users can select only theme-safe built-in artwork.
- The selected artwork appears consistently in the routine editor and dashboard session card.
- New guided and linked-app routines persist using the clean shared routine model.
- The screen matches the approved DraftingRoom5 editorial system.
