# Findings and closure evidence

## Retest disposition

The original failures below are preserved as historical evidence. After the user requested continued work, controlled retesting with hardware graphics and no concurrent build reproduced neither the startup ANRs nor the input ANRs. The same installed non-debug application and launcher widget passed two boots and eight actual tap/Settings-navigation checks; `retest/anr-comparison.json` confirms no new application ANR entry across them. A healthy natural hourly callback was also recovered from the retained system history. See [controlled retest details](retest/README.md), including the slow post-reboot launch and test-harness readiness corrections. Final audit status follows completion of the regression gate in the main README.

**F1 and F2 are closed for this audit by the mitigation and controlled retest.** This is an observed recovery under corrected test conditions, not proof of one universal root cause. Earlier statements requiring further evidence are historical; the retest supplies that evidence. F3 remains resolved.

The final API 35 native verification also retained one failed ten-second callback wait during boot activity. The same test and deadline passed on the later repeat (774 ms), with no new application ANR. See the retest's timing-failure section for the original failure, Android's instrumentation-cleanup behavior and the distinction between boot recovery and steady-state test latency. No ten-second boot-delivery guarantee is added to the product or claimed by this audit.

## F1 — Cold widget callbacks eagerly initialize the main application's providers

**Original failed-run assessment: partially mitigated; startup failures remained.**

API 35 recorded startup ANRs for DraftingRoom5 at 17:50:56 and 18:55:22. The second occurred during the naturally delivered hourly update for launcher widget ID 2. Its main-thread trace runs through `androidx.startup.AppInitializer`, `WorkManagerInitializer`, `Configuration`, coroutine scheduler initialization and reflection. The provider's own update had not yet run. These are observed application ANRs, not inferred failures and not covered by the physical-device waiver.

Evidence: `pre-fix/anr-traces.txt`, `api35-widget-broadcasts.txt`, and the `api35-alarm-*.txt` snapshots. The natural update was enqueued at 18:54:55.404, dispatched at 18:55:28.471, and completed at 18:55:28.965. The repeating alarm advanced from elapsed-time target 3,717,341 to 7,317,341 ms and recorded one wakeup. This establishes real inexact-hour delivery, but it does not make the startup ANR acceptable.

**Fix:** the manifest now runs `LivingIconWidgetProvider` in the app-private `:living_icon` process. The receiver is stateless and uses only the framework widget manager, packaged resources and an activity PendingIntent. No content provider is assigned to that process, so periodic redraws do not eagerly initialize the main application's WorkManager/Startup providers. Android creates the process when needed and may cache or remove it; no service or keep-alive was added. Main-activity refresh remains valid because state lives in Android's widget manager, and selection is derived from the current hour.

The [Android receiver manifest documentation](https://developer.android.com/guide/topics/manifest/receiver-element#proc) describes an app-private process created on demand. This is a scoped widget fix; the existing backup and app-update WorkManager configuration remains intact.

**Regression coverage:** the JVM test checks the receiver's private-process declaration. The native runner checks the compiled receiver process and absence of content providers there, waits for actual system bind/update broadcasts, kills the private receiver process, requests a real host-options change, and requires a new process plus a fresh host update within the ten-second test deadline. It also repeats multiple-instance, removal, restoration, alpha/render and cached-host checks.

Post-fix native tests passed on API 28 and API 35, including actual cold private-process callbacks in 5,218 ms and 1,900 ms respectively. However, API 35 reboot produced private-process startup ANRs at 19:30:32, 19:31:16 and 19:35:24. Traces stop in `DexFile.openDexFileNative` / `LoadedApk.createOrUpdateClassLoaderLocked`, before widget callbacks or WorkManager initialization. Main-process startup also failed at 19:35:59 and 19:36:46. Other system apps and Pixel Launcher failed during this boot, suggesting an emulator-wide startup problem, but that does not establish that the app is safe to release. Evidence: `api35-fixed/anr-traces.txt`, `api35-fixed/framework-broadcasts.txt`, and `api35-fixed/after-reboot.png` (the latter shows a launcher ANR, not a clean success capture).

The private-process change removes the observed eager-provider path; it does not fix Android DEX loading or establish reliable reboot behavior. Retain the change for review, with release blocked until a controlled repeat distinguishes app behavior from the environment and demonstrates a healthy boot/cold callback. A successful natural hourly callback after this change has not yet been observed.

The rejected shell-broadcast experiment in `pre-fix/cold-callback.json` is explicitly marked invalid: Android rejected the shell sender for a protected action, so its elapsed time is not callback performance evidence.

## F2 — Input timeouts (closed after controlled retest)

At 18:22:22 the foreground app recorded a five-second focus/input timeout. The main thread was in `HardwareRenderer.nSetStopped` during `ViewRootImpl.performDraw`, not in widget refresh or a widget-manager binder call. The trace records high guest CPU and memory pressure during concurrent emulator startup and building. This is a separate observation from F1, and cannot be claimed fixed by receiver isolation.

**Original failed-run assessment: unresolved; reproduced after the code mitigation.** A later tap reached MainActivity (`api35-fixed/idle-tap.txt`) but produced another focus/input ANR at 19:48:20, again in `HardwareRenderer.setStopped` / `RenderProxy::setStopped`. The visible failure is captured in `api35-fixed/idle-tap.png`; `api35-fixed/final-anr-traces.txt` contains the trace. The API 28 emulator had been stopped, though a release-variant build was still running on the host. This is not proof of an idle-host failure or of an application-code cause. It is also not a passing tap test. The activity-status line alone must not be used as evidence of successful interaction.

Physical-device renderer and battery measurements remain unavailable under the user's earlier waiver. That waiver does not close an observed failure. A controlled repeat on a healthy emulator or device must show responsive launch before release approval.

### Non-debug variant follow-up

The local release variant built successfully in 12m 23s and was signed with the existing emulator **test key**, through an ignored temporary Gradle init script. It was not signed for distribution or published. Package flags confirm `DEBUGGABLE` is absent. SHA-256: `f48e58bda66c96b7c7d32689db360205a8d831ac557f45b65dbbe0b63a07c04f`. The existing widget survived installation. The real package-replaced callback was enqueued at 19:56:36.341, dispatched at 19:56:41.925 and finished at 19:56:42.749.

A widget tap after the build completed again produced an input-focus ANR, recorded at **19:58:08** (event timestamp 19:57:47.155). The non-debug main-thread sample is in Compose's `AndroidComposeViewAccessibilityDelegateCompat.boundsUpdatesEventLoop`, rather than the hardware-renderer wait seen in the debug samples. Do not claim these distinct samples establish one root cause. `api35-release-variant/later.png` and `later.xml` show the app-not-responding dialog; `anr-traces.txt` preserves the stack. The initial `tap.png` is a blank app frame and its hierarchy export failed, so it is not a passing launch capture.

This experiment rules out assuming the symptom is confined to the debug build. The release variant was left installed on API 35 with the original widget and app data preserved; the failure dialog was closed and HOME requested. A healthy cold launch, reboot and inexact-hour run remain necessary follow-up evidence. Investigation of broader Compose/app startup behavior should be explicitly scoped before changing unrelated application code.

## F3 — Stale art handoff status

**Resolved.** The art README still said the prototype currently applies a circular mask and DR5-055 must remove it. Updated that sentence to describe the completed alpha-preserving integration. The original prompts and historical catalog/provenance remain intact.
