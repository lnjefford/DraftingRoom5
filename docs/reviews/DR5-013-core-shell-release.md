# DR5-013 — Core-shell release

Release review date: 2026-09-11. Target: v0.20.0, version code 20002.

## Milestone scope

The release combines the current document/repository foundation, responsive Dashboard, five Health Connect metric details, Settings, dashboard customization, stable routine/exercise artwork catalogs, linked-app launching and recovery, backup controls, feedback settings, and verified update flow. The README now describes this delivered core shell instead of the retired dashboard terminology.

Dead `BodyMetricRow`, `WeeklyStatRow`, and `MetricCard` composables were removed after reference scans showed no consumers. The unused legacy `session_dumbbell.png` was deleted; session cards resolve artwork from the routine catalog. The retained Dashboard hero has an explicit source-status and review entry in `docs/artwork/AssetLedger.md`.

## Verification

- `testDebugUnitTest`: 103 tests in 23 suites, zero failures or errors.
- `validateDebugScreenshotTest`: all 12 Dashboard, Weight, Settings, and customization references matched.
- `lintDebug`: 0 errors and 42 warnings; warnings are toolchain/version, style, and nonblocking platform/resource advice.
- `assembleDebug`: passed; debug APK size 66,322,501 bytes.
- `git diff --check`: no whitespace errors; only line-ending conversion notices.

The compact, tall, and 2x-font native references were visually inspected for wrapping, clipping, safe text/image overlap, reachable scroll content, and clear action hierarchy. The production vector identity was also inspected in the recorded circle, rounded-square, squircle-like, and tight-corner masks from 32 through 192 pixels. The measured five and gold frame remain separated and legible.

## Device-only follow-up

ADB is unavailable on this host, so this milestone does not claim physical-device verification of TalkBack/touch dispatch, OEM launcher and notification rendering, real Health Connect/Withings behavior, linked-app uninstall races, TTS/haptics system settings, Android backup transport, or signed installer/update activity-result flows. Those checks remain in the final device checklist and are not represented as passed.
