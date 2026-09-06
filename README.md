# DraftingRoom5

DraftingRoom5 is an Android workspace designed to grow by modules. Its first module, Fitness Tracker, shows the day’s workout schedule, reads personal fitness data through Health Connect, opens Fitbod and Just Run, and hosts a custom Saturday forearm and grip routine.

## Current scope

- Native Android app built with Kotlin and Jetpack Compose.
- Health Connect permission flow for body mass, body-fat percentage, lean body mass, exercise sessions, and distance.
- Weekly schedule and Saturday custom routine with a 10-second pre-timer countdown.
- Opens Fitbod (`com.fitbod.fitbod`) and Just Run (`com.jupli.run`) when installed.
- MIT licensed.

## Local build

Open the project in Android Studio with Android SDK 35 installed, then run `assembleDebug`.

The Firebase backup and GitHub self-update flows are intentionally configuration-free placeholders until a Firebase project and public GitHub repository exist. No credentials belong in this repository.
