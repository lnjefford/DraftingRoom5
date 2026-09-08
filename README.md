# DraftingRoom5

DraftingRoom5 is an Android workspace designed to grow by modules. Its first module, Fitness Tracker, shows the day’s workout schedule, reads personal fitness data through Health Connect, opens Fitbod and Just Run, and hosts a custom Saturday forearm and grip routine.

## Current scope

- Native Android app built with Kotlin and Jetpack Compose.
- Health Connect permission flow for body mass, body-fat percentage, lean body mass, exercise sessions, and distance.
- Persistent schedule and custom-routine management with create, edit, reorder, enable/disable, delete, and reset-to-default controls.
- Persistent dashboard customization: show, hide, reorder, and reset metric and training cards.
- Encrypted Android backup of schedules and custom routines through the Google account selected in system backup settings.
- Custom exercises support sets, targets, notes, optional timers, and a 10-second pre-timer countdown.
- Custom DraftingRoom5 launcher artwork with adaptive, round, themed, and notification-safe icon assets.
- Branded dark-only interface with layered navy depth, blue/mint accents, elevated cards, strong visual hierarchy, and consistent system-bar styling.
- Opens Fitbod (`com.fitbod.fitbod`) and Just Run (`com.jupli.run`) when installed.
- MIT licensed.

## Local build

Open the project in Android Studio with Android SDK 36 installed, then run `assembleDebug`.

Open **Settings → Customize dashboard** to choose which metric and training cards appear, move them into your preferred order, or restore the default layout. Open **Settings → Manage schedules & routines** to customize the weekly dashboard plan. Schedule entries can launch Fitbod, JustRun, or a selected custom routine. Changes are stored on-device across restarts; **Reset built-in plan** restores the original weekly schedule and Forearm & Grip routine.

Open **Settings → Google backup** to review the backup scope or open Android's backup settings. Android schedules encrypted cloud backups of the training plan and dashboard layout while the phone is online and idle, then restores the latest cloud snapshot during device setup or a fresh app install. A restored snapshot replaces the built-in defaults; subsequent local edits become the next snapshot, so the most recently edited installed copy wins after Android runs backup. Offline edits remain local and become eligible when the device reconnects. Changing the system backup account affects future snapshots; Android, rather than DraftingRoom5, owns account sign-in and restore selection. Health Connect measurements and permissions are never included.

The dashboard's **Check & install update** button downloads the latest public GitHub release and opens Android's installer. On first use, allow DraftingRoom5 to install apps when Android asks. Updates verify the package, version code, and signing certificate before installation. Network and installation errors appear in the update card.

## Publishing updates

Configure GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` with a backed-up, persistent Android signing key. Never commit the key or credentials. Publish increasing `vMAJOR.MINOR.PATCH` tags (minor and patch below 1000); the workflow assigns increasing version codes and attaches `DraftingRoom5.apk` to each release.

Every push to `main` and every pull request targeting `main` runs unit tests, Android lint, and a debug build. The successful workflow retains its installable debug APK artifact for 14 days. Dependabot checks Gradle and GitHub Actions dependencies weekly; patch-only updates are grouped and set to squash auto-merge only after this complete verification workflow succeeds. Minor and major updates remain open for manual review. Versioned releases and signed APK publication remain tag-driven.

Earlier releases used temporary CI debug keys, so their signing key may no longer exist. If the original key cannot be recovered, a one-time uninstall and installation of the first consistently signed release is necessary. Uninstalling removes app-local data. Subsequent releases must use that same persistent key to update in place.

Health Connect requires its provider on Android 13 and earlier; on Android 14 and later it is part of the system. Open **Settings**, tap **Connect Health Connect**, grant the read permissions for the measurements you want, and return to the dashboard. If permissions were denied repeatedly, use **Permissions** in the Health Connect card. The card keeps this action beside Refresh data on wide screens and stacks the actions on narrow screens. The dashboard refreshes when the app resumes. A privacy rationale screen is registered for both Android permission flows.

Each metric loads independently: a denied permission or failed workout read does not hide weight or body fat. Body measurements query the last 30 days first, then fall back to older accessible history if empty. On providers that support it, the app requests Health Connect's separate past-data permission so Withings measurements outside the standard history window can be returned. All pages are scanned in ascending order and the latest measurement is selected locally; both null and empty continuation tokens end pagination. Reads start when the app window has focus and are cancelled on focus loss or a new refresh, so permission-dialog transitions do not leave stale reads running. The Health Connect client uses stable version 1.1.0. Missing or unavailable measurements retain their normal `--` placeholder without exposing low-level Health Connect query diagnostics. Open the dashboard's Settings button to manage Health Connect status and permissions or check for an app update; these connection and maintenance controls stay off the measurement dashboard.

Run `testDebugUnitTest` for permission, failure isolation, cancellation, and measurement pagination regression tests. Phone validation is still required for actual Withings records: check their dates in Health Connect, refresh the dashboard, and confirm the latest values appear. Also verify that denying exercise access does not hide permitted body measurements.
