# Controlled audit retest

The user requested continued fixes and verification after the failed audit. Historical failures remain in the parent review; none were erased to obtain a pass.

## Test environment correction

The host has approximately 8 GB RAM. Initial inspection found about 1 GB free, an idle project Gradle daemon using roughly 740 MB, and the API 35 emulator using software graphics at 1080 × 2280. Earlier boot traces also recorded failures in system apps and Pixel Launcher.

Stopped the project's idle Gradle daemon using `gradle --stop`. Restarted **the same AVD and installed non-debug APK**, preserving application data and launcher widget ID 2, with:

```text
emulator -avd dr5-widget-api35 -port 5554 -no-window -no-audio -no-snapshot -gpu host -memory 2560
```

The emulator reports WHPX acceleration and the Intel UHD Graphics 620 OpenGL ES translator. No build or second emulator runs concurrently with these interactive checks. This changes the test environment, not application rendering, accessibility, ANR deadlines or Android behavior. No application feature is disabled. A cold startup completed in 62,530 ms; the widget update queued at 20:53:15.186, dispatched at 20:53:19.934 and finished at 20:53:20.067. ANR history contained no new application entry after the historical 19:58:08 failure.

The ignored local API 28 and API 35 AVD configurations now retain `hw.gpu.enabled=yes` and `hw.gpu.mode=host`, so future starts on this host keep the verified graphics setting. On another machine, select a supported graphics backend and verify runtime health; do not copy this host's GPU assumptions blindly. Keep build verification and emulator verification sequential on memory-constrained machines.

## Natural hourly update

Before changing the emulator environment, the retained system history showed a naturally delivered periodic callback on the final non-debug variant at **20:39:55.224**, dispatched at **20:39:55.231**, finished at **20:39:55.302**. The sender was the Android system PendingIntent, widget ID 2, private receiver process. No tool or forced clock change triggered that event. `initial-broadcasts.txt` and `initial-anr.txt` preserve the evidence. This supplies the previously missing healthy post-mitigation natural-hour callback.

## Test-harness correction

The first `retest-launch.py` attempt did not pass: `uiautomator dump` briefly returned no root node, and a stale remote XML file was read. Its log is retained in `hostgpu-launches.log`. The corrected script deletes its temporary remote XML before every dump, requires a fresh export, retries within a bounded launch deadline and requires the dashboard's **Settings content description** rather than any matching header text. This test correction does not weaken the requirement to detect new ANRs or verify actual Settings navigation. The tap-to-hierarchy measurements include adb and hierarchy-export overhead and are not pure application frame timings.

## Repeated real launcher checks

Six repetitions in `hostgpu-launches-verified/` passed: three after Android's `am kill` terminated the background package processes, and three ordinary background/foreground transitions. Each verified the actual full-tile PendingIntent, fresh dashboard hierarchy, Settings button navigation, a Settings screenshot and return navigation. ANR history was unchanged. Android's display logs recorded initial/cold launches in approximately 3.3–4.7 seconds. The native host lifecycle tests remain separate from these actual Pixel Launcher interactions.

A second real `adb reboot` preserved the widget. Its update queued at 21:03:22.083, dispatched at 21:03:44.177 and finished at 21:03:44.227. Boot-time dispatch delay was 22.094 seconds; receiver execution was 50 ms. The first UI probe ran before the launcher exposed a hierarchy and did not pass. The final harness waits up to 30 seconds for a fresh, clickable launcher tile before starting its distinct 15-second tap-to-hierarchy deadline. It does not treat readiness failures as application passes. `reboot2-ready-launches/` contains two passing post-reboot launch/input repetitions with unchanged ANR history.

**Performance limitation:** the second reboot's cold launch took 8.993 seconds to display according to Android, and 13.438 seconds including hierarchy export. This is slow on the tested 8 GB host with roughly 350 MB free at that point. It produced no ANR and accepted subsequent input, but these observations are not a production-device startup benchmark. The audit does not promise sub-five-second startup or hide the slow sample.

`anr-comparison.json` compares the full set of historical application ANR entries before the environment correction, after both boots, and after all eight interactions. They are identical. This supports closing the observed ANR blocker under the corrected test configuration; it does not assert that every Android device is immune to startup stalls.

Final native/build outcomes and audit disposition are recorded in the parent review after all checks finish.

The API 28 repeat in `api28-native/` passed all assertions, including a 220 ms cold private-process callback. The native runner removed its audit host and revoked its temporary binding grant; `api28-after-removal.txt` records the empty widget state. API 28 was stopped before the regression build began.

## Final API 35 native repeat and retained timing failure

The first final native repeat (`api35-native/`) began shortly after another cold boot and failed its strict ten-second cold-callback wait. It must not be counted as a pass. Android started the replacement receiver process at 21:24:28.160, but the options broadcast was still pending when the test deadline expired. Finishing instrumentation at 21:24:35.978 force-stopped the package, so Android then marked that pending broadcast failed with `onApplicationCleanupLocked`. There was no new application ANR. `api35-native-failure-logcat.txt`, `api35-native-failure-broadcasts.txt` and `api35-native-failure-anr.txt` preserve the evidence, including concurrent system boot activity.

The later repeat (`api35-native-settled/`) used the **same APK, same test and unchanged ten-second deadline**, and passed every assertion, including the cold private-process callback in **774 ms**. This establishes a passing repeat, not a ten-second guarantee under boot load. The product promises inexact periodic updates and recovery, not ten-second end-to-end delivery during boot; actual boot recovery was verified separately above, including the 22-second dispatch delay. Do not interpret the failed stress-timing sample as a success or delete it from future reviews. Future test runs should separate boot-recovery observation from steady-state latency assertions; `sys.boot_completed=1` alone does not mean all boot work has finished.

The final regression gate in `checks.log` passed in 10m 46s, with unit and screenshot validation explicitly rerun: 229 unit tests and 271 screenshot tests passed without failures, errors or skips. Lint reported zero errors, and debug app/native-test APK builds passed. No additional application code changed during this retest.

## Final disposition

**Audit passed.** Restoring the same non-debug test APK and performing two additional real widget/Settings interactions passed (`final-release-launches/`), bringing the verified interaction total to ten. The final ANR comparison also includes the native timeout attempt and these final interactions, with no new application ANR entry. The original launcher widget ID 2 remains, audit hosts and grants are absent, and both test emulators were stopped. DR5-056 is complete; DR5-057 is ready after the queue cooldown. The physical-device waiver and all recorded timing limitations remain explicit.
