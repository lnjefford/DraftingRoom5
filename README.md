# DraftingRoom5

DraftingRoom5 is an Android workspace designed to grow by modules. Its first module, Fitness Tracker, shows the day’s workout schedule, reads personal fitness data through Health Connect, opens Fitbod and Just Run, and hosts a custom Saturday forearm and grip routine.

## Current scope

- Native Android app built with Kotlin and Jetpack Compose.
- Health Connect permission flow for body mass, body-fat percentage, lean body mass, exercise sessions, and distance.
- Weekly schedule and Saturday custom routine with a 10-second pre-timer countdown.
- Opens Fitbod (`com.fitbod.fitbod`) and Just Run (`com.jupli.run`) when installed.
- MIT licensed.

## Local build

Open the project in Android Studio with Android SDK 36 installed, then run `assembleDebug`.

The dashboard's **Check & install update** button downloads the latest public GitHub release and opens Android's installer. On first use, allow DraftingRoom5 to install apps when Android asks. Updates verify the package, version code, and signing certificate before installation. Network and installation errors appear in the update card.

## Publishing updates

Configure GitHub Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` with a backed-up, persistent Android signing key. Never commit the key or credentials. Publish increasing `vMAJOR.MINOR.PATCH` tags (minor and patch below 1000); the workflow assigns increasing version codes and attaches `DraftingRoom5.apk` to each release.

Earlier releases used temporary CI debug keys, so their signing key may no longer exist. If the original key cannot be recovered, a one-time uninstall and installation of the first consistently signed release is necessary. Uninstalling removes app-local data. Subsequent releases must use that same persistent key to update in place.

Health Connect requires its provider on Android 13 and earlier; on Android 14 and later it is part of the system. Tap **Connect Health Connect**, grant the five read permissions, and return to the dashboard. If permissions were denied repeatedly, use **Health Connect permissions & settings**. The dashboard refreshes when the app resumes. A privacy rationale screen is registered for both Android permission flows.

Firebase backup remains a placeholder.
