# DraftingRoom5

DraftingRoom5 is an Android workspace designed to grow by modules. Its first module, Fitness Tracker, shows the day’s workout schedule, reads personal fitness data through Health Connect, opens Fitbod and Just Run, and hosts a custom Saturday forearm and grip routine.

## Current scope

- Native Android app built with Kotlin and Jetpack Compose.
- Health Connect permission flow for body mass, body-fat percentage, lean body mass, exercise sessions, and distance.
- Persistent schedule and custom-routine management with create, edit, reorder, enable/disable, delete, and reset-to-default controls.
- Persistent dashboard customization: show, hide, reorder, and reset metric and training cards.
- Per-metric Health Connect sync time, source app, stale-data warning, and distinct missing/unavailable states.
- Persistent 7-day, 30-day, 3-month, and 1-year controls for body-measurement charts and workout/distance summaries, with daily points and visible gaps when no measurement was recorded.
- Automatic offline recovery snapshots of app settings, schedules, routines, and workout history, with encrypted Android/Google device backup eligibility.
- Custom exercises support sets, targets, notes, optional timers, and a 10-second pre-timer countdown.
- Configurable workout haptics mark timer starts, countdown and timer completion, each completed set, and workout completion while respecting the phone's system haptic setting.
- Optional text-to-speech announces the final countdown, timer start and completion, set transitions, and workout completion with an adjustable voice rate.
- Custom DraftingRoom5 launcher artwork with adaptive, round, themed, and notification-safe icon assets.
- Branded dark-only interface with layered navy depth, blue/mint accents, elevated cards, strong visual hierarchy, and consistent system-bar styling.
- A short first-launch brand-title animation that never blocks navigation and follows Android's reduced-motion setting.
- Opens Fitbod (`com.fitbod.fitbod`) and Just Run (`com.jupli.run`) when installed.
- MIT licensed.

## Local build

Open the project in Android Studio with Android SDK 36 installed, then run `assembleDebug`.

Open **Settings → Customize dashboard** to choose which metric and training cards appear, move them into your preferred order, or restore the default layout. Open **Settings → Manage schedules & routines** to customize the weekly dashboard plan. Schedule entries can launch Fitbod, JustRun, or a selected custom routine. Changes are stored on-device across restarts; **Reset built-in plan** restores the original weekly schedule and Forearm & Grip routine.

Open **Settings → Workout feedback** to turn workout haptics on or off. Custom workouts include a per-exercise set counter; completing a set produces one short cue, while timer and workout milestones use distinct cues. Duplicate taps of the same event are throttled, and the app stays silent when Android system haptics are disabled or the phone has no vibrator.

The same **Workout feedback** section includes a voice-announcement mute switch and rate control. Announcements use the phone's default Android text-to-speech voice. If no compatible voice service or language is installed, DraftingRoom5 shows that status and keeps timers and haptics working without interruption.

Open **Settings → Automatic backups** to see the last successful snapshot, back up immediately, restore the latest snapshot, or turn automatic backups off. DraftingRoom5 saves a recovery snapshot after changes and every day, retries temporary failures with exponential backoff, and keeps the latest two copies on the device. Snapshots include dashboard/date-range settings, schedules, custom routines, and persistent workout completion history, so local recovery works without a network connection. Android can also encrypt and copy the snapshot to the Google account selected in system backup settings, then restore it during device setup or a fresh install. Changing the system backup account affects future cloud copies; Android owns account sign-in and transport timing. Health Connect measurements, permissions, downloads, and update files are never included.

DraftingRoom5 checks the latest public GitHub release twice daily when a network is available. When an update is ready, a small gold update indicator appears beside Settings on the dashboard; tapping it downloads the APK and opens Android's installer. The same action remains available under **Settings → App updates**. On first use, allow DraftingRoom5 to install apps when Android asks. Updates verify the package, version code, and signing certificate before installation. Network and installation errors appear in the update card.

## Publishing updates

Configure GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` with a backed-up, persistent Android signing key. Never commit the key or credentials. Publish increasing `vMAJOR.MINOR.PATCH` tags (minor and patch below 1000); the workflow assigns increasing version codes and attaches `DraftingRoom5.apk` to each release.

Every push to `main` and every pull request targeting `main` runs unit tests, Android lint, and a debug build. The successful workflow retains its installable debug APK artifact for 14 days. Dependabot checks Gradle and GitHub Actions dependencies weekly; patch-only updates are grouped and set to squash auto-merge only after this complete verification workflow succeeds. Minor and major updates remain open for manual review. Versioned releases and signed APK publication remain tag-driven.

Earlier releases used temporary CI debug keys, so their signing key may no longer exist. If the original key cannot be recovered, a one-time uninstall and installation of the first consistently signed release is necessary. Uninstalling removes app-local data. Subsequent releases must use that same persistent key to update in place.

Health Connect requires its provider on Android 13 and earlier; on Android 14 and later it is part of the system. Open **Settings**, tap **Connect Health Connect**, grant the read permissions for the measurements you want, and return to the dashboard. If permissions were denied repeatedly, use **Permissions** in the Health Connect card. The card keeps this action beside Refresh data on wide screens and stacks the actions on narrow screens. The dashboard refreshes when the app resumes. A privacy rationale screen is registered for both Android permission flows.

Each metric loads independently: a denied permission or failed workout read does not hide weight or body fat. Every metric card shows its latest successful read time and contributing source app when Android can resolve it. Records older than seven days keep their value with a stale warning; an empty successful read says no data was found, while permission/provider failures remain explicitly not synced. Body measurements query the last 30 days first, then fall back to older accessible history if empty. On providers that support it, the app requests Health Connect's separate past-data permission so Withings measurements outside the standard history window can be returned. All pages are scanned in ascending order and the latest measurement is selected locally; both null and empty continuation tokens end pagination. Reads start when the app window has focus and are cancelled on focus loss or a new refresh, so permission-dialog transitions do not leave stale reads running. The Health Connect client uses stable version 1.1.0. Open the dashboard's Settings button to manage Health Connect status and permissions or check for an app update; these connection and maintenance controls stay off the measurement dashboard.

Run `testDebugUnitTest` for permission, failure isolation, cancellation, and measurement pagination regression tests. Phone validation is still required for actual Withings records: check their dates in Health Connect, refresh the dashboard, and confirm the latest values appear. Also verify that denying exercise access does not hide permitted body measurements.
