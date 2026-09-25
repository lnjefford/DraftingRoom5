# DR5-084 — Structured exercise-target release

Release task: `01a09848-5d39-7670-8013-394782400976`. The coherent DR5-080–083 implementation and audit corrections ship as **v0.27.31 / 27033**. This record supplements the independent [DR5-083 audit](../DR5-083/README.md) and retains its unavailable-device limitations.

## Shipped behavior

Every guided exercise now has required positive Sets and independently optional Weight, Duration, and Reps. Pounds are positive whole integers divisible by five, and every weight control changes by exactly 5 lb. Duration owns the exercise timer. No automatic-progression rule, free-form target field, legacy compatibility reader, or obsolete 37.5 lb product example remains.

Planned progression is optional and exercise-specific. It is an ordered queue of complete future prescriptions, including name, notes, artwork, all four targets, and any exercises inserted immediately after the source. Applying the next step consumes exactly one item; inserted exercises become independent. The default post-exercise path is the filled **Next exercise** action, which changes nothing. **Adjust exercise** opens complete current/next review; **Adjust manually** can change several fields without consuming the queue, while **Apply planned step** consumes one step. Successful changes offer durable, conflict-safe Undo. Active and completed session snapshots remain unchanged; a new session or explicit restart adopts the live routine.

Back, dismissal, recreation, duplicate input, stale revisions, failed persistence, and receipt recovery retain the audited lifecycle and atomicity behavior. Backup/restore retains structured targets, queues, sessions, decisions, and receipts. Retirement data and credentials remain excluded from ordinary backup and production artifacts.

## Validation and retained limits

The final release gate ran once on stable v0.27.31 source using `./tools/verify.ps1 -Tier Release` and passed in **850.8 seconds**: **453 JVM tests in 62 suites**, **471 screenshot comparisons**, zero failures/errors/skips, lint with **0 errors / 46 existing warnings**, debug APK assembly, and `git diff --check`. The version-bearing Core Shell comparison passed without a reference update. DR5-083's inspected **111** changed/new Fitness references and **24** visual review pages remain the accepted visual evidence. `assembleDebugAndroidTest` also passed, including Kotlin/Java compilation and instrumentation APK assembly.

The final local debug APK is **84,279,376 bytes**, SHA-256 `1aea4d43108445765085ccbb0b25b17e848cfedf31781530f14d19b83876f3c8`. Its decoded identity is `dev.draftingroom5`, version **0.27.31 / 27033**, minimum/target/compile SDK **37**. APK Signature Scheme v2 verification passes with one debug signer (certificate SHA-256 `4b171b0d4d1e289469384ebcd5a2d75b0b1460949279266cfb2a49de44917c6b`), and official 16 KiB `zipalign` verification succeeds. The production archive contains no JKS/keystore/private-key, workbook, database, or SQLite extension and has zero matches for the audit's eight privacy/fixture canaries. Decoded backup and device-transfer rules still contain only the three encrypted Fitness recovery entries.

`adb devices -l` returned an empty device list. Native test sources and APK assembly are available, but runtime TalkBack speech/order, hardware-keyboard traversal, actual process kill/relaunch, and foreground timer interaction are not reported as passing. Existing Phase 9 Android 17/live-provider exceptions also remain limitations rather than successes.

## Delivery

The milestone is delivered by tag [`v0.27.31`](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.27.31). The tag-triggered [Android release workflow](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml) publishes `DraftingRoom5.apk`. The final task report records the exact workflow run, published attachment size and SHA-256, package/version identity, certificate/signature result, 16 KiB alignment, and privacy scan.
