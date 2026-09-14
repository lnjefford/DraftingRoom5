# DR5-033 — Launcher mask review

Completed at: 2026-09-12T13:41:38.9154110-05:00. Scope: launcher foreground, static/round/adaptive references, monochrome geometry, and focused validation only. Push: NO.

## Result

The measured-five launcher foreground now applies a centered `0.735` scale to the complete frame-and-five composition. Its 66-unit source width becomes 48.51 units, retaining Android's recommended minimum mark size. The frame's farthest meaningful corner is 32.78 units from center, inside the 33-unit radius of the guaranteed 66-unit circular safe zone. The original paths, three notch cutouts, colors, and internal optical adjustment are unchanged. The in-app brand mark and the standalone notification silhouette were not modified.

The production XML remains the shared source for static square, legacy round, adaptive, adaptive round, and themed monochrome launcher variants. No raster launcher fallback was added.

## Evidence

`DR5-033/icon-mask-review.png` was generated from the production transform and inspected at 1120 × 680. Its full-color and monochrome rows retain every frame corner and all three notch cutouts under circle, squircle, rounded-square, and tight masks using Android's 72-unit masked viewport. The renderer is retained beside the image for reproducibility.

Focused `MeasuredFiveAssetTest` validation passed all four tests. It checks color/monochrome path parity, the 48.51-unit size and 32.78-unit radial safe-zone bounds, every launcher variant's vector reference, absence of launcher PNG fallbacks, separation of the notification icon, and the review artifact dimensions/mask inventory. Android resource merging completed as part of that test run. `lintDebug` also passed with zero errors and 43 existing warnings. `git diff --check` passed; its output contained only the repository's existing LF-to-CRLF notices.

This is deterministic host evidence for the documented Android mask geometry. It does not claim physical inspection across OEM launchers or devices.
