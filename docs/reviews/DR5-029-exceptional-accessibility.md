# DR5-029 — Exceptional states and accessibility

Review date: 2026-09-12 UTC.

## Covered state matrix

The shared hardening matrix now gives stable, named fixtures to Health Connect loading/no-data/permission/provider-unavailable/provider-update states; installed-app picker loading/empty/failure states; corrupt current data; an uninstalled linked app; unavailable text-to-speech; backup and update failures; missing artwork fallback; no routines; a recovery day; destructive confirmation; and failed persistence with draft retention.

Each of the 18 states is rendered from the real production screen or shared state component at compact, tall, 2x-font, and landscape sizes. The 72 new references are summarized in the checked contact sheets:

- `DR5-029-exceptional-compact.png`
- `DR5-029-exceptional-tall.png`
- `DR5-029-exceptional-large-text.png`
- `DR5-029-exceptional-landscape.png`

Visual inspection found no clipped essential action or ambiguous color-only state. Long content remains scrollable; decorative artwork yields at large text; landscape retains access through each screen's existing vertical scroll container.

## Corrections

- Loading rows now expose polite live-region state text.
- Shared error recovery actions fill a reliable 48 dp-or-taller target, and shared confirmation actions explicitly retain 48 dp minimum height.
- The haptic preference is disabled when Android or the device reports that tactile feedback is unavailable.
- Empty installed-app results now offer `Check again`; empty filtered results offer `Clear search`; load failures retain `Try again`.
- Settings hardening fixtures isolate the affected functional group so TTS, backup, and update failure copy remains reviewable at large text and in landscape.

Existing accessible reorder alternatives, Ctrl+arrow keyboard movement, focus restoration, textual selected/progress/error states, reduced-motion guards, safe drawing insets, generic artwork resolution, local Health Connect errors, linked-app recovery, current-data reset/retry, and draft-retaining save failures remain covered by their focused tests and fixtures.

## Verification

- `testDebugUnitTest`: 180 tests in 33 suites, zero failures or errors.
- `validateDebugScreenshotTest`: all 132 references matched, including all 72 exceptional-state references.
- `lintDebug`: zero errors and 43 warnings.
- `assembleDebug`: passed; debug APK 67,305,529 bytes.
- `git diff --check`: no whitespace errors; only the repository's existing CRLF conversion notices.

## Device-only follow-up

No ADB device is attached. Physical verification remains required for TalkBack traversal/focus after live mutation, hardware keyboard and D-pad dispatch, actual Android font/display scaling, landscape IME and cutout insets, system animation-disable behavior, Health Connect permission/provider intents, TTS/haptic availability and timing, package uninstall/store recovery, storage exhaustion, Android backup settings/restore, and verified update installation. These are environment checks rather than unresolved application defects.
