# Guided routine session specification

Status: implemented and resilience-audited through DR5-030, including durable progress, elapsed-clock timers, feedback ownership, recovery, and atomic completion.

## Selected reference

- [Approved guided-session mockup](docs/design/guided-session/guided-session-selected.png)
- [Approved timer-state board](docs/design/guided-session/timer-states-selected.png)
- [Approved routine-completion screen](docs/design/guided-session/completion-selected.png)
- [Approved save-and-resume flow](docs/design/guided-session/save-resume-selected.png)
- Routine-building system: [RoutineEditor.md](RoutineEditor.md)
- Shared visual system: [Dashboard.md](Dashboard.md)

Build this screen from native Jetpack Compose components. The screenshot is a design reference, not a production UI asset.

## Approved experience

- Focus on one current exercise instead of presenting every exercise as an equally prominent card.
- Keep overall progress visible with exercise position and a thin progress indicator.
- Show the current exercise name, notes, set target, set progress, optional timer, and primary `Complete set` action.
- Show upcoming exercises in a compact unified list. Tapping an upcoming row changes focus without marking earlier work complete.
- Preserve the existing 10-second timer-readiness countdown, voice cues, haptic cues, per-set completion, and absence of a rest timer.
- Do not invent weight, reps performed, heart rate, calories, or other tracking fields the routine does not own.
- Save progress automatically and restore an interrupted session when practical.

## Exercise artwork requirement

Every selectable exercise-artwork option is a coordinated pair:

1. `list` artwork: a compact, immediately recognizable composition for exercise-editor rows, picker tiles, and the `Up next` list.
2. `header` artwork: a larger editorial composition with enough negative space and a safe crop for the current-exercise header.

These are associated assets under one stable artwork ID. They may share a common high-resolution source render, but production exports should be composed and reviewed separately rather than relying on one automatic center crop. The list image must remain legible at thumbnail size; the header image may reveal more equipment and environmental context.

The exercise builder includes a curated built-in artwork picker. Users select one option and DraftingRoom5 automatically uses its paired list and header assets wherever appropriate. Do not ask users to choose the two sizes independently, and do not allow arbitrary photos that could break the theme.

### Asset direction

- Match the routine-artwork family: dark gunmetal or black equipment, deep navy shadows, restrained electric-blue or mint rim light, and tactile material detail.
- Keep the same object identity, color treatment, and lighting across both members of a pair.
- Use a near-square or modest landscape composition for `list`, with the subject centered enough to survive small crops.
- Use a wide/right-weighted composition for `header`, leaving the left side quiet for the eyebrow, exercise name, and notes.
- No embedded exercise names, logos, motivational copy, UI controls, or bright multicolor backgrounds.
- Avoid brand marks unless the exact branded equipment is intentionally required and its use is legally appropriate. Prefer generic equipment representations.
- Export optimized WebP or AVIF where supported, retain source-quality masters outside Android runtime resources, and verify every asset on a compact phone.
- Record the generation prompt, tool/model, date, and any source/licensing details alongside each approved asset set.

### Suggested data model

Persist a stable ID on each exercise rather than resource integers:

```kotlin
data class Exercise(
    val id: String,
    val name: String,
    val notes: String,
    val setCount: Int,
    val target: String,
    val timerSeconds: Int?,
    val artworkId: String? = null,
)

data class ExerciseArtwork(
    val storageId: String,
    val displayName: String,
    val listAsset: Int,
    val headerAsset: Int,
)
```

Serialization stores only `artworkId`. Resolve it through a central catalog at render time. Missing or unknown IDs fall back to a coordinated generic exercise pair without preventing the exercise or routine from loading.

The catalog should be extensible because the complete exercise-artwork library will be generated in sets later. Keep artwork IDs independent of exercise names so renamed exercises retain their selected imagery and one pair can be reused when appropriate.

## Screen structure

1. Standard secondary app bar: Back, routine name, overflow. No brand icon.
2. Thin progress bar and localized position such as `1 of 7 exercises`.
3. Current-exercise header with gold-rule eyebrow, large serif name, supporting notes, and the selected `header` asset cropped on the right.
4. One raised set/timer panel:
   - Current set and total set count.
   - Stored target.
   - Explicit set indicators with completed/current/upcoming semantics beyond color.
   - Large timer value only for timed exercises.
   - `Start timer`; while active, expose Cancel through a clear state transition rather than adding permanent clutter. Do not imply Pause unless pause behavior is implemented.
   - Full-width `Complete set` action.
5. `UP NEXT` unified list using each exercise's paired `list` asset, name, target metadata, and chevron.
6. Small `Progress saves automatically` status.

The complete remaining exercise list must remain reachable by scrolling; the two rows in the screenshot are not a hard limit.

## State and completion

- Completing a set updates its exercise progress and advances to the next set.
- Completing the final set of an exercise marks that exercise complete and focuses the next incomplete exercise.
- Completed exercises remain reachable and can be corrected without losing later progress.
- Show the session-completion action only after all required sets are complete. Ending early belongs in the overflow and requires confirmation that distinguishes saving partial progress from marking the routine complete.
- Timer completion does not automatically complete a set; it provides the existing haptic/voice cue and leaves the explicit completion action available.
- Persist session identity, focused exercise, completed sets, and timer state outside ephemeral composable state. Define safe behavior for process death and elapsed timers.

### Timer states

Keep the timer panel's height and control positions stable across all states:

- `Get ready`: show the remaining readiness count as large muted cool blue-gray/soft ivory text at roughly 60–65% of the actual timer's visual emphasis. Pair it with explicit `Get ready` and `Timer starts automatically` labels. Offer `Cancel countdown`; disable `Complete set` during this brief state.
- `In progress`: show the real timer at full-bright ivory contrast with explicit `Timer running`. Offer `Cancel timer`; keep `Complete set` enabled so the user can record an early physical finish. Do not add Pause unless pause behavior is deliberately implemented.
- `Finished`: show `00:00` at full contrast with mint check and `Timer complete`. Offer `Restart timer` and emphasize `Complete set`.

Timer completion never completes a set automatically. The readiness countdown must remain visually distinguishable from elapsed exercise time beyond its label; the deliberately faded numerals are part of the approved design.

### Partial progress and resume

Leaving an incomplete guided session saves partial progress by default. The confirmation states that completed sets and the current exercise will be preserved and offers:

- `Keep going` — dismiss and remain in the session.
- `Save & exit` — persist the session and return to the previous destination.

Do not offer a destructive discard action in this ordinary exit dialog. `Restart session` may live in the session overflow or resumed-session entry flow and must separately confirm that saved progress will be cleared.

Persist at least the routine ID, a session ID, routine-content revision or snapshot, focused exercise ID, completed-set counts by stable exercise ID, and last-updated time. A running timer should be reconciled from an elapsed-time deadline; never silently count a timer as a completed set after process death.

An incomplete session changes the associated dashboard action from `Start` to `Resume` and exposes concise progress such as `3 of 7 exercises`. Resuming restores the focused exercise and set progress. Editing or deleting a routine with an incomplete session requires explicit confirmation; deletion clears that session, while safe edits create a new routine revision for the next session.

### Completion screen

- Show a terminal `Session complete` screen only after all required sets have been explicitly completed.
- Use the selected routine/exercise artwork family as the visual reward, with a mint completion medallion and restrained gold drafting accent.
- Show only owned facts: routine name, `7 of 7`-style exercise progress, `All complete`, and saved status.
- Do not invent duration, calories, weight, repetitions performed, streaks, scores, or records.
- Provide one primary `Return to dashboard` action. The top close action has identical navigation semantics and must not reopen the finished session.
- Committing completion clears the resumable partial session only after the completed state is durably saved.

## Exercise-artwork picker behavior

- Add `Choose artwork` or `Change artwork` to the exercise editor.
- Use a large modal sheet with a searchable or categorized grid once the library is large enough to require it.
- Picker tiles use the `list` asset and clearly announce label and selected state.
- A detail preview may show the paired `header` crop before committing.
- Cancel preserves the previous artwork; Done commits the stable ID.
- Every newly created exercise receives a required artwork ID; the builder supplies an intentional default until the user chooses another pair.

## Accessibility

- Back, overflow, set indicators when interactive, timer controls, Complete set, and upcoming rows require at least 48 dp targets.
- Treat artwork as decorative when adjacent text already identifies the exercise.
- Announce exercise position, completed sets, remaining sets, timer state, and available actions.
- Do not communicate progress solely by mint/blue color.
- Support large fonts by reducing or hiding header artwork before text clips.
- Preserve TalkBack focus when a set completes or focus advances.

## Verification

- Test timed and untimed exercises, readiness countdown, restart/cancel, and timer completion.
- Test one and many sets, and reject zero/negative set counts in the exercise builder.
- Test completing, undoing, skipping to an upcoming exercise, ending early, and restoring a session.
- Test required artwork selection, catalog resolution, and corrupted-record handling.
- Review every artwork pair in picker tile, editor row, upcoming row, and current header crops.
- Verify compact phones, large fonts, TalkBack, process recreation, and dark-theme contrast.

## Acceptance criteria

- The current exercise is immediately clear without hiding the remaining routine.
- Set completion and timer start are distinct, unambiguous actions.
- Each chosen artwork ID reliably resolves to a coordinated small list image and large header image.
- Users select artwork once; placement determines which paired asset is displayed.
- No unsupported workout metrics or motivational copy appears.
- New routines, exercises, and partial sessions use the clean model with required stable IDs.
