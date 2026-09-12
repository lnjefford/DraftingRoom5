# DR5-028 — Guided-session release

Release review date: 2026-09-12 UTC. Target: v0.22.0, version code 22002.

## Milestone scope

This release combines the DR5-023 state-machine contract with the durable session reducer and repository commands, exact occurrence-bound start/resume routing, elapsed-clock timer reconciliation, foreground-only voice and haptic feedback, ordinary save-and-exit, destructive restart confirmation, partial-set correction, completion history, and current-schema backup/restore. The DR5-027 resilience audit corrected repository reload, lease, write-failure, feedback ownership, completion navigation, large-count, dialog-restoration, and backup-serialization edge cases before release.

README and technical-design status now describe the delivered guided-session behavior. Default application metadata is aligned with the release tag at 0.22.0/22002.

## Verification

- `testDebugUnitTest`: 177 tests in 32 suites, zero failures or errors.
- `validateDebugScreenshotTest`: all 60 native references matched.
- `lintDebug`: 0 errors, 43 warnings, and 1 informational finding; findings are nonblocking toolchain, style, and platform/resource advice.
- `assembleDebug`: passed; debug APK size 67,239,993 bytes.
- `git diff --check`: no whitespace errors; only line-ending conversion notices.

The full-session idle, readiness, running, finished, and completion references were visually inspected at compact, tall, and 2x-font sizes. The dedicated control-panel fixtures were also inspected at 2x font for every timer phase and `Int.MAX_VALUE` set counts. Labels, actions, disabled state, textual progress, contrast, wrapping, and scroll-reachable content remain clear; header artwork yields at large text and no essential control overlaps.

## Device-only follow-up

ADB has no attached device, so this milestone does not claim physical verification of actual activity/process death, reboot and unknown-clock recovery, TalkBack focus, keyboard/touch dispatch, TTS/haptic timing, Android backup transport, failed-storage UI, landscape/IME insets, or signed installer/update behavior. The prioritized scenarios remain in `DR5-027-session-resilience-audit.md` for final production verification.
