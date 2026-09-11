# Dashboard redesign specification

Status: approved visual direction for future implementation. This document is a design handoff, not an indication that the current Compose dashboard has been changed.

## Selected references

- [Final dashboard mockup](docs/design/dashboard/dashboard-selected.png)
- [Selected app icon](docs/design/dashboard/app-icon-selected.png)

The reference images communicate visual intent. Rebuild the interface as native Jetpack Compose components; do not place the dashboard screenshot in the app. Recreate the launcher mark as vector geometry rather than shipping the concept PNG as the final adaptive foreground.

## Decisions captured

- Use the premium editorial direction: deep navy canvas, ivory editorial headings, restrained gold drafting details, electric-blue actions, and mint health trends.
- Do not use motivational quotes, slogans, manifestos, or decorative instructional copy.
- Use functional copy only: date, current training status, workout metadata, health measurements, and actions.
- Retain the photographic athlete hero as a quiet background element. It must not reduce text contrast or become the primary content.
- Use the tactile artwork selected on each routine. Linked-app and guided routines share the same card component and curated artwork system.
- Keep one compact blue action pill. It reads `Start` for a new session and `Resume` for saved guided progress. The entire session card and pill invoke the same action; the pill is the visible affordance, not the only touch target.
- Do not include the redundant full-width `Begin session` button.
- Replace the current in-app mark and launcher icon with the selected measured-five identity.

## Screen structure

Build the dashboard as a vertically scrollable edge-to-edge screen. The reference is a tall phone, but the implementation must fit narrower and shorter Android devices without clipping.

1. **System bars**
   - Use light status/navigation icons over the deep navy background.
   - Apply `WindowInsets.safeDrawing` and keep content clear of display cutouts.
2. **Brand row**
   - Selected measured-five mark at approximately 42–48 dp.
   - `DraftingRoom5` wordmark in the primary sans-serif title style.
   - Settings icon in a 48 dp touch target at the trailing edge.
3. **Editorial hero**
   - Tracked uppercase date, for example `WEDNESDAY, SEPTEMBER 9`.
   - Large serif heading: `Today’s training`.
   - Short gold rule below the heading.
   - Optional functional status line derived only from DraftingRoom5-owned data, for example `2 sessions scheduled today`. Omit the line when there is no useful owned status.
   - Athlete image aligned to the right and blended into the background with horizontal and vertical scrims.
4. **Today’s session**
   - Section label: `TODAY’S SESSION`.
   - One card for each active routine occurrence scheduled on the selected day. If there are multiple items, stack the cards with 12–16 dp spacing rather than horizontally scrolling them.
   - Resolve identity, artwork, and execution behavior through the referenced routine; the schedule entry must not duplicate those fields.
5. **Week selector**
   - Seven equal day targets, Monday through Sunday.
   - The selected day uses the blue filled circle; today also receives the small blue dot.
   - Selecting a day changes the session cards while retaining actual completion state for that date.
   - If day selection is deferred, render this as a non-interactive weekly summary and preserve the existing today-only behavior.
6. **Health snapshot**
   - Section label and trailing `VIEW TRENDS` action.
   - Three equal metric columns in the first implementation: Weight, Body fat, and Distance.
   - Each column shows the latest value, a small trend line, delta, and comparison period.
   - Tapping a metric opens its existing drilldown. `VIEW TRENDS` should open the existing health/trend destination rather than create a duplicate screen.
   - End the page after the metrics with calm bottom breathing room. Do not add another workout CTA.

## Session card system

Create one reusable `SessionCard` composable driven by a schedule occurrence, its referenced routine, completion state, and optional partial-session progress.

### Shared layout

- Target height: roughly 160–176 dp before font scaling; allow the card to grow rather than clip.
- Corner radius: 18–22 dp.
- Background: deep raised navy with a subtle one-pixel blue-gray border and low elevation.
- Content padding: 20 dp horizontally and 18 dp vertically.
- Left/content region: approximately 58–65% of the width.
- Artwork region: the right 28–35%, clipped to the card and faded toward the text with a horizontal scrim.
- Metadata eyebrow: tracked uppercase sans-serif, 11–12 sp.
- Session title: editorial serif, approximately 30–36 sp depending on available width.
- Supporting metadata: 14–16 sp with compact icons.
- Compact action pill: minimum 48 dp height, blue fill, dark navy label, state-appropriate verb plus right arrow.
- Make the whole card clickable and expose one clear accessibility action such as `Start Push day` or `Resume Forearm and Grip Conditioning`. Avoid nested click semantics that cause duplicate screen-reader actions.
- Use a visible pressed state across the whole card: small tonal lift or overlay, no scale animation that causes layout movement.

### Routine treatments

- **Linked-app routine**
  - Eyebrow names the type and configured app, for example `LINKED APP · FITBOD` or `LINKED APP · JUST RUN`.
  - Use the routine's selected artwork: squat rack for Leg day, kettlebell for Full body, bench/dumbbells for Push day, pulldown bar for Pull day, and running shoe for running.
  - `Start` launches the stored app/deep link. If the app is unavailable, keep the user in DraftingRoom5 and offer recovery.
  - Never display external workout details DraftingRoom5 does not own.
- **Guided routine**
  - Eyebrow: `GUIDED ROUTINE`.
  - Use the routine's selected artwork and show its exercise/item count.
  - `Start` opens a new focused guided session. Saved partial progress changes the action to `Resume` and adds concise progress such as `3 of 7 exercises complete`.
- **Completed**
  - Replace the action pill with a quiet mint `Done` state and check icon.
  - Apply a subtle mint border/tint while retaining the same card geometry.
  - Do not make the card look disabled or reduce text contrast excessively.
- **No session / recovery**
  - Do not fabricate an exercise card.
  - Use a compact editorial empty state with `Nothing scheduled today` and one short functional line such as `Recovery day`.

The object artwork is conceptual in the mockup. Before implementation, create or license separate production assets with transparent backgrounds, consistent camera angle and lighting, and enough resolution for xxhdpi/xxxhdpi. Generated mockups may contain incidental equipment branding and must never be cropped into production assets. Use generic/unbranded or intentionally licensed final artwork. Optimize it as WebP or AVIF where supported and provide a non-image fallback.

## Visual tokens

Use these target theme values consistently:

| Role | Target |
| --- | --- |
| Canvas | `#07111F` |
| Deep canvas | `#030912` |
| Raised surface | `#101D2D` to `#17283C` |
| Primary blue | `#76A9FF` |
| Strong blue | `#3F7FE8` |
| Mint | `#4FE0B0` |
| Warm gold | `#FFC66D` |
| Ivory headline | `#F4F0E7` |
| Secondary text | approximately `#B9C8DA` |
| Border | `#263B53` |

- Avoid a decorative full-screen gradient. Subtle tonal scrims used to blend photography are acceptable.
- Use gold sparingly: icon frame, short editorial rule, and exceptional status accents only.
- Use blue for primary actions and selection; mint for positive trends and completion.

## Typography

- Add a bundled, redistribution-safe editorial serif for the hero and session titles. A robust display family such as DM Serif Display is a suitable starting point; verify its license and rendering before committing font files.
- Keep body text, metadata, controls, and the wordmark in the existing Material sans-serif family.
- Do not rely on `FontFamily.Serif` for the final build because its appearance varies by device.
- Support Android font scaling. At large font scales, allow titles and cards to grow and stack metadata/actions as needed.
- Keep tracked uppercase labels short. Do not apply wide tracking to sentence-case body copy.

## Hero artwork

- The athlete shown in the mockup is visual direction, not a separately prepared production asset.
- Produce a licensed or project-owned portrait asset before implementation.
- Required composition: athlete on the right, face and dumbbell contained within the upper-right area, dark neutral clothing, monochrome navy grade, uncluttered silhouette.
- Apply a left-to-right scrim to guarantee readable date, heading, and status text. Add a bottom scrim so the image blends behind the session section.
- Supply a text-only fallback when the image is unavailable and ensure the layout remains intentional without it.
- Respect reduced-motion settings; the hero should not use parallax or continuous animation.

## Selected measured-five icon

The mark consists of an ivory editorial serif `5`, exactly three shallow gold measurement notches cut into the top terminal, and a complete thin gold drafting frame with small corner overshoots on a deep navy ground.

### Production construction

- Redraw the mark as deterministic vector paths. Do not trace image-generation artifacts or embed the selected PNG as the launcher foreground.
- Use a 108 × 108 adaptive-icon viewport.
- Fill the background layer edge-to-edge with `#07111F`.
- Keep the complete foreground, including frame overshoots, within the adaptive safe region. Start with frame bounds near 22–86 in the 108-unit viewport and validate visually under every mask.
- Optically center the 5 inside the frame; do not center solely by path bounds.
- Keep the three notches evenly spaced, shallow, and open enough to survive mdpi rendering.
- Add adaptive foreground/background XML, static square and round launcher resources required by supported Android launchers, and a monochrome themed-icon resource.
- The monochrome resource should reduce the mark to a single-color 5 and frame while preserving the three notch cutouts.
- Reuse the same vector geometry for the in-app header mark. At small in-app size, increase apparent stroke weight if necessary rather than raster scaling the concept image.

### Mask and size verification

Check the icon at mdpi through xxxhdpi under circle, squircle, rounded-square, and OEM-style masks. Confirm:

- No frame corner or overshoot is clipped.
- The numeral reads unmistakably as `5` at 32–48 px.
- All three notches remain distinct.
- The gold frame does not collapse into the ivory numeral.
- The monochrome themed icon remains recognizable.
- The notification icon stays a separate Android-compliant monochrome silhouette; do not reuse the full-color launcher asset directly.

## Accessibility and behavior

- Maintain WCAG-aware contrast for text and essential controls.
- All touch targets must be at least 48 × 48 dp.
- Provide content descriptions for settings and meaningful artwork only. Mark decorative hero/session imagery as decorative so it is not announced.
- Expose metric values and trends as complete phrases, for example `Weight, 184.2 pounds, up 2.1 pounds since August 12`.
- Preserve existing loading, unavailable, stale, permission-denied, and missing-data states for Health Connect.
- Preserve haptic and voice-feedback settings. Do not introduce dashboard haptics that bypass the existing system-setting checks.
- Ensure the selected-day state and completion state are not communicated by color alone.

## Suggested implementation sequence

1. Add final vector icon geometry and update adaptive, static, round, monochrome, and in-app mark resources.
2. Add the licensed serif font and production hero/session artwork.
3. Extend theme tokens without removing current colors needed by other screens.
4. Extract dashboard sections into focused composables: `DashboardHeader`, `TrainingHero`, `SessionCard`, `WeekSelector`, and `HealthSnapshot`.
5. Bind the new components to existing `TrainingPlan`, `WorkoutHistory`, Health Connect, navigation, and launch behavior.
6. Add preview fixtures for linked-app, guided, resumable, completed, recovery, missing-health-data, narrow-screen, and large-font cases.
7. Add or update unit/UI tests for session actions, date selection, completion rendering, metric navigation, and semantics.
8. Verify on at least one compact phone and one tall phone in portrait, then run unit tests, lint, and debug assembly before release.

## Acceptance criteria

- The running app is recognizably faithful to the selected mockup without using the screenshot as UI.
- There is exactly one visible workout-start action per session card and no redundant page-level CTA.
- Every active routine occurrence scheduled for the selected day is reachable.
- Linked-app and guided actions follow their routine type, including safe launch recovery and resumable partial progress.
- Health data states and drilldowns remain functional.
- The screen scrolls safely on compact devices and with large fonts.
- The selected icon is consistent in the launcher, in-app header, round icon, adaptive masks, and themed monochrome mode.
- No motivational or decorative prose is introduced.
