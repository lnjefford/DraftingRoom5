# DR5-031 — Production release

Release date: 2026-09-12 UTC. Release: v0.23.0, version code 23002.

## Final cleanup

- Integrated the DR5-029 exceptional-state work and every DR5-030 product-review correction.
- Removed the obsolete foundation destination and unused exercise-editor route through the audited cleanup, with no compatibility alias or migration reader retained.
- Reconciled README, technical-design status, and every approved handoff status with the production implementation.
- Confirmed the APK contains all 49 catalog WebPs and no design mockup/contact-sheet PNGs.
- Confirmed the guarded Dependabot merge binds the exact successfully verified head SHA.

## Verification

The isolated Java 17 release gate passed `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `updateDebugScreenshotTest`, and `validateDebugScreenshotTest`:

- 187 unit tests in 33 suites, zero failures.
- 136 native screenshot comparisons, zero errors or failures.
- Android lint: zero errors and 43 warnings.
- Debug APK: 67,305,529 bytes, SHA-256 `a07670fd724a78132a369ee95487ed3aff3c847680dae1ccc0b58d96d99b86a4`.
- AAPT: package `dev.draftingroom5`, version 0.23.0/code 23002, min SDK 28, target SDK 35.
- Retired-route/model and migration/compatibility identifier scan: clean.

The screenshot tool initially reported one false comparison failure for the tall maximum-set-count control even though rendered and reference PNGs were byte-identical. Regenerating the audited references and immediately validating them completed successfully. The only tracked existing reference update is the tall Settings fixture's runtime version changing from 0.22.0 to 0.23.0; it was visually inspected.

## Device checks

ADB is available but reports no attached device. Withings/Health Connect, linked-app launches and uninstall recovery, real process death/reboot, TalkBack and hardware input, timer voice/haptics, Android backup transport, installer permission/update flow, physical compact/large-font layouts, and OEM launcher masks therefore remain unavailable in this run. The prioritized physical-device checklist and acceptance boundaries remain in `docs/reviews/DR5-030-final-product-review.md`; no host-render evidence is presented as physical-device certification.
