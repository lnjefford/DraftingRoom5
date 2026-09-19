# DraftingRoom5 implementation queue

This is the shared queue for the Astra and Sol scheduled tasks. The approved requirements are in `DesignReview.md`, `Dashboard.md`, `MetricDetails.md`, `Settings.md`, `DashboardCustomization.md`, `SchedulesAndRoutines.md`, `RoutineEditor.md`, and `GuidedSession.md`.

## Worker protocol

- Astra takes only tasks labeled `Model: gpt-6-astra`; Sol takes only tasks labeled `Model: gpt-5.6-sol`.
- Take the lowest-numbered unchecked task for that model whose dependencies are checked. Complete at most one task per scheduled run. If none is eligible, change nothing.
- Claim a task by changing `[ ]` to `[~]` and adding `Claimed: <ISO timestamp> by <model>`. Re-read this file immediately before claiming.
- Read every referenced handoff before editing. Mockup PNGs are references only; build native Jetpack Compose UI.
- This is a clean-slate app. Delete replaced types, storage, resources, and UI. Do not add migrations, compatibility readers, aliases, or version branches for development data.
- Preserve unrelated user edits. Never commit credentials, signing material, generated builds, `.gradle-user-home`, or `.tooling`.
- Add focused tests to every implementation task and run the relevant subset. A `Push: YES` task must run `testDebugUnitTest lintDebug assembleDebug`.
- On completion, change `[~]` to `[x]` and append a concise `Completed:` note naming checks and important files. If blocked, return it to `[ ]` and append `Blocked:`.
- `Push: NO`: do not commit, push, tag, or release; leave the coherent work for the next milestone.
- `Push: YES`: this is a usable product milestone. Commit accumulated coherent work, push it, update the app version consistently, create and push the next increasing version tag, follow the release workflow, and verify the APK is published.

## Active queue limits

- `minimum_completion_interval_minutes`: `60`
- `max_concurrency`: `1`
- `last_completion_at`: `2026-09-18T20:20:12-05:00`
- `next_eligible_dispatch_at`: `2026-09-18T21:20:12-05:00`
- `processor_lease`: empty
- For active queued work, dispatch only a task whose `Status` is `ready`, and never dispatch while another task is `running` or before `next_eligible_dispatch_at`.
- When claiming an active task, set its `Status` to `running` and fill `Executor thread ID` and `Started at`; the checkbox claim marker remains `[~]` for compatibility with the worker protocol above.
- When an active task completes, record its actual `completed_at`, copy that timestamp to `last_completion_at`, set `next_eligible_dispatch_at` to 60 minutes later, and promote only the next dependency-satisfied task from `blocked` to `ready`.
- A completed task must use checkbox `[x]` and `Status: complete`; a failed or input-blocked task must record evidence and use `Status: failed` or `Status: needs_input` without unblocking descendants.

## Phase 7 — Living icon-sized widget

Approved direction: a one-cell home-screen widget that opens the app normally on tap and rotates through **50** distinct still images at a battery-conscious, inexact cadence. The **5** and black/ivory/gold identity recur across the collection, but neither a readable 5 nor an intact border is required in every image: a strong concept may mirror, obscure, fragment, transform, or even hide the 5. The gold border and the 5 can move, bend, break, rotate, or become part of imaginative scenes. Any dark outer silhouette/backing must be black rather than navy, but a design may have no dark backing when the scene does not call for one. Images 21–40 use scene-shaped transparent outer silhouettes; the user requested black circular backgrounds again for the final images 41–50. The four samples and full brief are in `docs/design/widget-icon-variants/README.md`. Keep the actual launcher icon unchanged.

- [x] **DR5-048 — Prototype the icon-sized tap-to-open widget**
  - Outcome: a 1 × 1 home-screen widget displays the DraftingRoom5 identity, launches the normal app route on tap, and can rotate a small set of images without an always-on service.
  - Scope: new app-widget provider/layout/manifest wiring, prototype artwork derivatives of the four approved samples, scheduling integration, focused tests, and widget preview only; do not alter the launcher icon or Phase 6 product behavior.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android widget feasibility and lifecycle implementation.
  - Depends on: none
  - Status: `complete`
  - Acceptance: widget advertises a one-cell target size with sensible minimums; its full tile has a direct activity `PendingIntent` that opens the app through its normal entry point; a launcher can render the 5 and frame at icon-like size without clipping; rotation among the four samples works on supported host/emulator tests using a platform-supported, approximately hourly and inexact cadence plus safe refresh on relevant app interactions; no 30-second promise, foreground service, wake lock, or per-minute background polling; widget removal cancels unnecessary work; API 28–36 behavior and no-widget case are tested. If a real 1 × 1 tile or practical rotation fails on the available launcher, mark `needs_input` with evidence before commissioning 50 assets rather than silently enlarging the widget or changing the brief.
  - Executor thread ID: `01a09db0-586e-7110-80dc-7429367d15b9`
  - Started at: `2026-09-13T21:13:04-05:00`
  - Completed at: `2026-09-14T09:14:00-05:00`
  - Push: `NO`
  - Claimed: 2026-09-13T21:13:04-05:00 by gpt-5.6-sol.
  - Resumed: 2026-09-14T07:33:13-05:00 by gpt-5.6-sol after user authorized emulator installation or waiver of unavailable device checks.
  - Completed: Widget provider, one-cell layout/manifest wiring, circular transparent four-look assets, preview and API 35 Pixel Launcher evidence in `docs/reviews/DR5-048/`; direct full-tile tap, app-open rotation, and original prototype removal observed. `LivingIconWidgetTest` (4/4), `lintDebug`, and `assembleDebug` passed. User-authorized waiver covers untested API 28/30/31/36 launchers and real elapsed-hour callback; retain these checks before release.
  - Notes: use `docs/design/widget-icon-variants/` as approved direction only, not as unoptimized runtime PNGs. An approximately hourly rotation is a default to validate against Android battery guidance, not an exact-time guarantee. Keep the widget's click target and accessible label clear.

- [x] **DR5-049 — Specify fifty distinctive widget-image concepts**
  - Outcome: a production-ready catalog names 50 visually different still transformations with a common icon-sized art contract.
  - Scope: `docs/design/widget-icon-variants/` concept catalog, art prompts, small-size QA rubric, safe-zone and optimization specification only.
  - Model: `gpt-5.6-sol`
  - Model reason: creative but bounded art-direction inventory following the proven widget footprint.
  - Depends on: DR5-048
  - Status: `complete`
  - Acceptance: the catalog has 50 unique numbered IDs and one distinct scene/effect each; concepts are visually clear at one-cell size, while the 5 and black/ivory/gold cues recur across the set rather than being compulsory in every image; intentional mirrored, obscured, fragmented, or hidden-5 concepts are explicitly allowed; concepts include the approved dumbbell, flex, fire, and melt seeds plus a 5 lying down and being abducted by aliens; several concepts break, overlay, rotate, fold, or repurpose the gold frame; training, surreal, elemental, kinetic, and playful ideas are balanced without near-duplicate recolors; exact prompts, negative constraints, target geometry, naming, and acceptance-at-rendered-size checks are recorded for five ten-image production batches.
  - Executor thread ID: `01a0a083-9111-7933-ac5b-d74b92808a3a`
  - Started at: `2026-09-14T10:24:07-05:00`
  - Completed at: `2026-09-14T10:27:26-05:00`
  - Push: `NO`
  - Claimed: 2026-09-14T10:24:07-05:00 by gpt-5.6-sol.
  - Completed: `docs/design/widget-icon-variants/CATALOG.md` defines 50 ordered scene prompts with row-specific exclusions, exact shared prompt assembly, five balanced ten-image batches, 56 px QA, geometry, alpha, naming, and optimization handoff; `README.md` links the catalog. Catalog checks confirmed 50 unique ordered IDs, two concepts per lane in every batch, populated prompts/exclusions, and `git diff --check`.
  - Notes: the first four images are approved examples, not a creativity ceiling; they may count toward 50 only if they pass the same final asset QA. No videos or animated frame sequences are required.

- [x] **DR5-050 — Produce widget images 01–10**
  - Outcome: the first ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 01–10, image-generation prompts/provenance, source masters, optimized widget derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-049
  - Status: `complete`
  - Acceptance: ten distinct named image files match catalog IDs 01–10; use one built-in image-generation call per distinct asset and record final prompts/provenance; each has a distinct visual idea that reads at one-cell size, with intentional exceptions allowed for numeral legibility and individual palette/frame treatment; no stray text or watermark, safe 1 × 1 crop, and an optimized runtime derivative; a contact sheet proves the concept and collection-level identity work at actual widget size; four approved samples are reused only if they meet the same standards.
  - Executor thread ID: `01a0a0d0-e0bb-72d0-9ce9-bdf0e49b2a67`
  - Started at: `2026-09-14T11:48:04-05:00`
  - Completed at: `2026-09-14T12:10:28-05:00`
  - Claimed: 2026-09-14T11:48:04-05:00 by gpt-5.6-sol.
  - Completed: Ten distinct built-in-generated concepts, original sources, normalized RGBA masters, and 256 px WebP derivatives are in `docs/design/widget-icon-variants/` and `app/src/main/res/drawable-nodpi/`. `batches/batch-01-10.md` records exact prompts, repair provenance, and 56/48 px visual QA; all ten passed six rubric columns. Focused asset validation confirmed ten unique hashes, transparent corners, and 145,888 total derivative bytes; `git diff --check` passed.
  - Push: `NO`
  - Notes: preserve masters separately from optimized runtime resources. Do not generate ten minor color variations.

- [x] **DR5-051 — Produce widget images 11–20**
  - Outcome: the next ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 11–20, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-050
  - Status: `complete`
  - Acceptance: ten distinct named files match IDs 11–20 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–10.
  - Executor thread ID: `01a0a10f-13a9-7e80-a5c5-8bb31e704cd4`
  - Started at: `2026-09-14T12:56:23-05:00`
  - Completed at: `2026-09-14T13:12:49-05:00`
  - Claimed: 2026-09-14T12:56:23-05:00 by gpt-5.6-sol.
  - Completed: Ten built-in-generated images 11–20, two targeted repairs, exact prompt/provenance JSON, normalized masters, 256 px WebP derivatives, and 56/48 px contact-sheet QA in `docs/design/widget-icon-variants/`. All six rubric columns passed for all ten; asset validation confirmed transparent corners, ten unique hashes, 156,814 total derivative bytes, and `git diff --check`.
  - Push: `NO`
  - Notes: include ambitious border manipulation and a distinct silhouette for every concept.

- [x] **DR5-058 — Correct the first twenty widget silhouettes to black**
  - Outcome: images 01–20 retain their approved scenes but use black rather than navy for the dark outer silhouette/backing.
  - Scope: existing 01–20 source/master art edits, optimized derivatives, exact edit prompts/provenance, both batch contact sheets and measurements, and visual/resource QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded image correction across the two completed art batches.
  - Depends on: DR5-051
  - Status: `complete`
  - Acceptance: each of the twenty assets has a black dark silhouette/backing rather than a navy one while retaining its distinct 5, gold frame/prop, scene-specific accents, safe crop, and genuine alpha; edited masters and WebPs match; both refreshed 56/48 px contact sheets show the correction on light and dark backgrounds; exact edit prompts/provenance and new measurements/hashes are recorded; no first-batch image is accidentally replaced or made less legible; focused asset checks and `git diff --check` pass.
  - Executor thread ID: `01a0a10f-13a9-7e80-a5c5-8bb31e704cd4`
  - Started at: `2026-09-14T13:46:10-05:00`
  - Completed at: `2026-09-14T14:07:44-05:00`
  - Claimed: 2026-09-14T13:46:10-05:00 by gpt-5.6-sol.
  - Completed: Twenty constrained built-in image edits changed the dark outer backings to black while retaining original generation sources/prompts and distinct scenes. Rebuilt current masters, 256 px WebPs, both 56/48 px contact-sheet pairs, measurements, and correction provenance in `docs/design/widget-icon-variants/`. All twenty passed visual QA and focused true-black, alpha, hash, uniqueness, size, and `git diff --check` checks; current derivatives total 288,214 bytes.
  - Push: `NO`
  - Notes: user correction after reviewing IDs 01–20. Use the imagegen editing path for raster changes; preserve the original-generation sources and prompts for provenance. Keep this correction sequential and complete before DR5-052.

- [x] **DR5-052 — Produce widget images 21–30**
  - Outcome: the third ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 21–30, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-058
  - Status: `complete`
  - Acceptance: ten distinct named files match IDs 21–30 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–20.
  - Executor thread ID: `01a0a153-9c88-7aa2-acf8-7fa9d48bf870`
  - Started at: `2026-09-14T14:11:15-05:00`
  - Completed at: `2026-09-14T14:32:21-05:00`
  - Claimed: 2026-09-14T14:11:15-05:00 by gpt-5.6-sol.
  - Completed: Ten built-in-generated free-silhouette concepts 21–30, with targeted regeneration of weak portal and shadow-puppet outputs, preserved source lineage, exact prompts, 1254 px RGBA masters, 256 px WebPs, and 56/48 px contact-sheet QA in `docs/design/widget-icon-variants/`. All six rubric columns passed; nine silhouettes are unmistakably noncircular at 56 px. Focused alpha, safe-bound, hash, uniqueness, size, black-field, and `git diff --check` checks passed; derivatives total 159,606 bytes.
  - Push: `NO`
  - Notes: the user observed that all of IDs 01–20 still have the same circular outer tile despite varied scenes. For IDs 21–30 use the revised black free-silhouette prompt/geometry in `CATALOG.md`; preserve scene-shaped transparency with no final circular mask and make at least six outer silhouettes clearly noncircular at 56 px. Include surreal concepts such as the 5 lying down and being abducted by aliens if not already in the earlier batches.

- [x] **DR5-053 — Produce widget images 31–40**
  - Outcome: the fourth ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 31–40, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-052
  - Status: `complete`
  - Acceptance: ten distinct named files match IDs 31–40 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–30.
  - Executor thread ID: `01a0a17c-23a7-7e71-be3e-13798d522110`
  - Started at: `2026-09-14T14:55:12-05:00`
  - Claimed: 2026-09-14T14:55:12-05:00 by gpt-5.6-sol.
  - Completed at: `2026-09-14T15:38:09-05:00`
  - Completed: Ten distinct built-in-generated concepts 31–40, with targeted regeneration of weak 33, 35, 37, and 38 and an explicit catalog scene revision for a readable domino chain. Preserved exact prompts/source lineage and rejected attempts, 1254 px RGBA masters, 256 px WebPs, measured hashes, and 56/48 px light/dark sheets in `docs/design/widget-icon-variants/`. All six rubric columns pass; all ten silhouettes are clearly noncircular at 56 px. Focused alpha, safe-bound, size, hash, uniqueness, provenance-path, visual, and `git diff --check` checks passed; derivatives total 145,758 bytes. Immediate claim at 14:55:12 CDT was user-authorized ahead of the normal 15:32:21 CDT gate; completion resets the next gate to 16:38:09 CDT.
  - Push: `NO`
  - Notes: continue the revised free-silhouette contract: at least six of IDs 31–40 must have a clearly noncircular outer silhouette at 56 px. The border and 5 may break, fold, move, or become the scene; do not normalize finished art to a disc. User clarified that black is required only where the scene uses a dark silhouette/backing; ivory/gold artwork on transparency is valid when the design does not call for black.

- [x] **DR5-054 — Produce widget images 41–50**
  - Outcome: the final ten catalog concepts complete a validated 50-image widget set.
  - Scope: concept IDs 41–50, prompts/provenance, source masters, optimized derivatives, final 50-image contact sheet and quality pass only.
  - Model: `gpt-5.6-sol`
  - Model reason: final independent art batch plus whole-set visual consistency.
  - Depends on: DR5-053
  - Status: `complete`
  - Acceptance: ten distinct named files match IDs 41–50 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, and provenance checks; all ten have a true-black circular outer background with genuinely transparent corners and no navy, while their scenes and optional gold-frame treatments remain distinct; a 50-image contact sheet shows no duplicates, unclear ideas, accidental clipping, stray text, or inconsistent dimensions, while deliberate obscured/mirrored 5s and broken/absent inner gold borders are judged on artistic merit rather than rejected by rule; all 50 optimized derivatives are cataloged and resource-size impact is measured.
  - Executor thread ID: `01a0a1a6-e420-76d1-af48-d85f0b09f919`
  - Started at: `2026-09-14T15:42:21-05:00`
  - Completed at: `2026-09-14T16:00:49-05:00`
  - Completed: Ten original built-in-generated black-disc concepts 41–50, with targeted moonwalk regeneration and preserved source lineage, exact prompts, 1254 px RGBA masters, 256 px WebPs, and 56/48 px contact sheets in `docs/design/widget-icon-variants/`. All six rubric columns pass; the full 50-image light/dark 56 px sheet shows distinct scenes and coherent collection identity. Focused provenance, dimension, true-black disc geometry, alpha, size, hash, uniqueness, visual, and `git diff --check` checks passed. Final batch is 100,240 bytes; all 50 unique derivatives total 693,818 bytes. Immediate claim at 15:42:21 CDT was user-authorized ahead of the normal 16:38:09 CDT gate; completion resets the next gate to 17:00:49 CDT.
  - Push: `NO`
  - Notes: the user requested a return to black circular backgrounds for the final batch. Use the `CATALOG.md` final-batch prompt and black-disc geometry for all IDs 41–50; keep the 5 and inner gold frame expressive, and repair or regenerate weak images before considering the batch complete.

- [x] **DR5-055 — Integrate the fifty-image widget rotation**
  - Outcome: the widget reliably presents one of the 50 cataloged looks, rotates at the validated battery-conscious cadence, and still opens the app normally.
  - Scope: widget provider/scheduler, runtime artwork catalog/resources, lifecycle and click behavior, accessibility, tests, and widget screenshots only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android runtime integration after artwork and prototype validation.
  - Depends on: DR5-054
  - Status: `complete`
  - Acceptance: exactly 50 approved IDs are reachable without duplicates or missing-resource fallbacks; rotation is stable across process death, reboot, app update, launcher recreation, and widget add/remove, never demands a precise wall-clock interval, and avoids an always-on background service; the rotating set retains a recognizable DraftingRoom5 identity without requiring a readable 5 in every individual image, and every tile remains one direct tap from normal app launch; resizing and multiple widget instances behave deterministically; API 28–36, battery/update behavior, semantics, and widget preview/screenshot tests pass.
  - Executor thread ID: `01a0a1f1-d98f-76f3-baf9-608d07d81ad1`
  - Started at: `2026-09-14T17:03:30-05:00`
  - Completed at: `2026-09-14T18:03:43-05:00`
  - Push: `NO`
  - Claimed: 2026-09-14T17:03:30-05:00 by gpt-5.6-sol.
  - Completed: Mapped all 50 approved WebPs in catalog order, removed the prototype circular mask and four prototype runtime files, preserved true alpha through host resources, and added resize/restore/boot/time-correction/app-update refresh paths while retaining inexact hourly updates and direct app launch. Focused 50-file SHA-256 audit passed (693,818 bytes); `LivingIconWidgetTest` 4/4, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `git diff --check` passed. API 35 Pixel Launcher picker, one-cell tile, accessible click target, images 21 and 41, direct launch, reinstall, reboot, and Android-managed time-change evidence are in `docs/reviews/DR5-055/`; unobserved API/device/hourly and multi-instance checks are recorded there for DR5-056.
  - Notes: favor approximately hourly inexact changes per Android widget guidance. An app-open refresh may advance the look if it does not make rotation unexpectedly rapid. Replace the prototype provider's `circularArtwork` mask with rendering that preserves each image's real alpha silhouette: noncircular IDs 21–40 and black-disc IDs 01–20 and 41–50. Verify the actual one-cell widget on light/dark home screens. Do not modify the launcher icon itself.

- [x] **DR5-056 — Audit the living widget and its fifty images**
  - Outcome: an independent review confirms the widget's tap, rotation, accessibility, battery behavior, and 50-image visual quality without weakening the app.
  - Scope: Phase 7 diff, native widget/device evidence, asset/contact-sheet inspection, focused and full regression checks, and a review artifact under `docs/reviews/`; fixes stay within Phase 7 scope.
  - Model: `gpt-6-astra`
  - Model reason: cross-version launcher lifecycle and battery review benefits from difficult independent reasoning.
  - Depends on: DR5-055
  - Status: `complete`
  - Acceptance: review verifies every approved requirement against code/evidence, counts 50 distinct runtime images, inspects collection-level identity and each concept at actual one-cell size without penalizing deliberate obscured/mirrored/absent 5s or broken frames, checks direct app launch, time/update inexactness, widget removal, reboot, app update, multiple instances, accessibility and resource-size effects, and records physical-device checks or their unavailability honestly; all relevant unit, screenshot, lint, and build checks pass; unresolved findings block release.
  - Executor thread ID: `01a0a230-1fbf-7442-b691-200447a13c6c`
  - Started at: `2026-09-14T18:11:37-05:00`
  - Completed at: `2026-09-14T21:33:28-05:00`
  - Push: `NO`
  - Notes: audit that the runtime provider and launcher display preserve the intended noncircular outer silhouettes of IDs 21–40 and the true-black circular backgrounds of IDs 41–50; a uniform forced circular crop across all 50 fails the user's art direction. If the platform cannot uphold the approved one-cell/tap-to-open premise, seek user direction rather than disguising a larger or inert widget.

  - Claimed: 2026-09-14T18:11:37-05:00 by gpt-6-astra; immediate dispatch explicitly authorized by user.

  - Blocked: 2026-09-14T19:54:36-05:00 — Independent audit found unresolved API 35 reboot startup ANRs and repeated input/renderer ANRs after widget taps. Private-process mitigation and native cold-callback tests pass on API 28/35; all 50 images, 229 unit tests, 271 screenshots, lint (0 errors), debug/test builds and whitespace checks pass. See docs/reviews/DR5-056/README.md and findings.md. Audit acceptance failed; DR5-057 remains blocked. No commit, push, tag, version bump or release.

  - Resumed: 2026-09-14T20:51:35.6681810-05:00 — User explicitly requested fixes and repeated verification until the audit passes; release stays blocked pending evidence.

  - Completed: 2026-09-14T21:33:28-05:00 — Audit passes after private-process mitigation, corrected emulator graphics/load, and bounded fresh-UI test handling. Ten real widget launch/Settings checks, two dedicated reboot checks, unchanged ANR histories, healthy natural hourly callback, final native API 28/35 repeats (cold callbacks 220/774 ms), all 50 asset checks, 229 unit tests, 271 screenshot tests, lint (0 errors), app/test builds and whitespace checks pass. Historical ANRs, one boot-load native timing failure, slow startup and the existing physical-device waiver are retained in docs/reviews/DR5-056/. No production behavior or ANR threshold was disabled. DR5-057 promoted to ready; no commit, push, tag or release.

- [x] **DR5-057 — Release the living widget milestone**
  - Outcome: the reviewed fifty-image widget ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 7 findings, documentation/version defaults, full verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic release procedure after independent audit.
  - Depends on: DR5-056
  - Status: `complete`
  - Acceptance: DR5-048–056 and audit findings are complete; README/design status describes the widget and its inexact rotation honestly; default version name/code advance consistently from the actually released Phase 6 version; `testDebugUnitTest lintDebug assembleDebug` and all screenshot/widget checks pass; coherent Phase 7 work is committed and pushed once; the next increasing version tag is pushed, release workflow completes, and the signed APK is published and verified.
  - Executor thread ID: `01a0a2f3-f654-7542-aeb4-5e462edd414f`
  - Started at: `2026-09-14T21:45:23-05:00`
  - Completed at: `2026-09-14T22:40:22-05:00`
  - Push: `YES`
  - Claimed: 2026-09-14T21:45:23-05:00 by gpt-5.6-sol; user explicitly overrode the queue cooldown for immediate release work.
  - Completed: Phase 7 documentation and defaults now describe and identify v0.25.0/25002 consistently. The forced clean gate passed all 84 executed tasks: 229 unit tests, 271 screenshot tests, lint with zero errors, debug app and native-test APK builds, plus whitespace checks. Independent asset/resource verification passed all 50 unique catalog hashes, masters, sources, alpha/silhouette rules, and the 693,818-byte runtime total; the built APK reports 0.25.0/25002 and contains exactly 50 ordered widget WebPs. The coherent milestone is delivered by tag `v0.25.0`; the successful tag workflow and signed `DraftingRoom5.apk` are verified at [GitHub Actions](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml) and the [v0.25.0 release](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.25.0).
  - Notes: never commit credentials, signing material, generated build files, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 7 tasks separately.

## Phase 8 — Exercise-by-exercise progression

Approved direction: progression belongs to each guided exercise rather than to the routine. Exercises may use repeatable automatic increments or an ordered custom sequence. Pounds and seconds are stored as structured values instead of being recoverable only from target text; pounds are the only weight unit. After the final set of a progression-enabled exercise, a compact prompt says `<Exercise name> complete` with only secondary **Ready for more** and primary **Continue workout** actions. Continuing changes nothing and asks again after the next completion. If no further progression is available, skip the prompt and continue normally. Applying progression is immediate, previews the exact future prescription, and is undoable. The detailed acceptance below is authoritative; earlier exploratory mockups are not implementation assets.

- [x] **DR5-059 — Add structured exercise prescriptions and progression state**
  - Outcome: the domain and current-document codec can represent independent exercise progression without parsing numbers from display text.
  - Scope: guided-exercise prescription types, pounds and seconds measurements, automatic/custom progression definitions, validation, JSON codec, fixtures, and focused domain/codec tests only; no editor or workout UI.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Kotlin domain and persistence work with deterministic invariants.
  - Depends on: none
  - Status: `complete`
  - Executor thread ID: `01a0b438-acba-7891-90d6-2c64f98476d3`
  - Started at: `2026-09-18T06:14:15-05:00`
  - Completed at: `2026-09-18T06:39:15-05:00`
  - Acceptance: a guided exercise may have no progression, automatic progression, or ordered custom steps; structured optional weight-in-pounds and duration-in-seconds values coexist with the readable target and existing timer behavior without extracting numbers from strings; automatic rules store independent positive increments and optional minimum/maximum bounds for weight and duration; custom steps store the complete replacement prescription for the source exercise and zero or more exercises to insert immediately after it; inserted exercises have stable unique identities and their own optional progression; validation rejects non-finite/non-positive values, invalid bounds, empty custom steps, duplicate IDs, and unsupported linked-app progression; round trips retain progression exactly in live routines and session/history snapshots; current clean-slate strict-schema conventions are preserved; focused tests pass.
  - Push: `NO`
  - Claimed: 2026-09-18T06:14:15-05:00 by gpt-5.6-sol; user explicitly requested immediate manual queue execution.
  - Completed: Added explicit exercise measurements, complete prescriptions, automatic weight/time rules, ordered custom replacement steps, and recursive independently progressing insertions. Strict validation covers positive finite values, bounds, required current measures, nonempty custom sequences, routine-wide future ID uniqueness, and linked-app exclusion. The strict current JSON codec round-trips live, partial-session, and history snapshots with no legacy reader. Focused progression/codec/domain tests and all 236 unit tests passed with zero failures; `git diff --check` passed.
  - Notes: use pounds only. Preserve arbitrary target text for exercises that do not opt into structured progression. Do not add compatibility readers or migrations for development data.

- [x] **DR5-060 — Build automatic progression configuration**
  - Outcome: a user can configure common weight- and time-based exercises once instead of authoring every future step.
  - Scope: native Compose exercise-editor progression entry, automatic-rule form, structured current values, increments, optional bounds, validation, persistence wiring, previews, and focused editor screenshots/tests.
  - Model: `gpt-5.6-sol`
  - Model reason: focused Compose form and state-management implementation using the existing editor patterns.
  - Depends on: DR5-059
  - Status: `complete`
  - Executor thread ID: `01a0b451-57db-7470-9e77-f06cf05cd98a`
  - Started at: `2026-09-18T06:41:03-05:00`
  - Completed at: `2026-09-18T07:26:44-05:00`
  - Acceptance: the existing exercise editor exposes progression without adding a routine-level progression control; the user can select automatic progression, enter current pounds and/or seconds, configure independent increments and optional minimum/maximum values, and disable progression; timer duration and a structured timed target remain intentionally synchronized when selected rather than accidentally diverging; the UI previews exact next prescriptions; pounds are the only weight unit; single- and dual-measure exercises save and restore correctly; invalid, incomplete, or contradictory rules cannot be saved; existing no-progression exercises retain their current editing workflow; compact, tall, landscape, and large-text screenshot coverage plus focused tests pass.
  - Push: `NO`
  - Claimed: 2026-09-18T06:41:03-05:00 by gpt-5.6-sol; user explicitly requested immediate manual queue execution.
  - Completed: The exercise editor now configures disabled, weight-only, duration-only, and dual-measure automatic progression with pounds/seconds current values, independent positive increments, optional bounds, strict save gating, exact bounded previews, and explicit timer-duration synchronization. Draft conversion preserves automatic rules across edits while disabling progression restores the ordinary target/timer workflow. Five focused editor tests, all 241 unit tests, all 275 screenshot validations (including four dedicated compact/tall/landscape/large-text references), lint, debug assembly, visual inspection, and `git diff --check` passed; large-text optional bounds stack without overlap.
  - Notes: progression is configured per exercise. Do not add rating scales or routine-wide levels.

- [x] **DR5-061 — Build ordered custom progression for fingerboard exercises**
  - Outcome: complex fingerboard work can change grips over time and introduce additional grip exercises without forcing numeric auto-progression.
  - Scope: native custom-progression overview, ordered step management, step editor, add/edit/delete/reorder behavior, optional exercise insertion, persistence wiring, and focused screenshots/tests.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded but interaction-heavy Compose editor work following the routine/exercise editor conventions.
  - Depends on: DR5-059
  - Status: `complete`
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-18T09:54:14-05:00`
  - Completed at: `2026-09-18T11:04:32-05:00`
  - Acceptance: an exercise can select custom progression and show its current prescription plus ordered next steps; each step can change the source exercise's name/grip description, notes, sets, target, timer, artwork, and structured measurements; a step can add zero or more fully valid exercises immediately after the source; added exercises may receive their own progression during creation or later through the ordinary exercise editor; steps and additions use the existing cards, menus, add actions, drag semantics, automatic-save messaging, accessibility actions, and discard safeguards; the overview clearly distinguishes current and future prescriptions; compact, tall, landscape, and large-text screenshots plus focused tests pass.
  - Push: `NO`
  - Claimed: 2026-09-18T09:54:14-05:00 by gpt-5.6-sol; user explicitly overrode the queue cooldown for immediate manual execution.
  - Completed: Added mutually exclusive custom progression configuration, a clear current-versus-future overview, complete ordered replacement-step editing, stable independently progressing exercise insertions, card menus, pointer/keyboard/TalkBack reordering, confirmation-backed deletion/discard, and routine-editor save messaging. Three focused custom editor tests plus all 244 unit tests and all 279 screenshot validations passed; compact, tall, landscape, and corrected large-text references were visually inspected; lint, debug assembly with a 4 GB verification heap, and `git diff --check` passed. Main implementation is in `CustomProgressionEditor.kt` with exercise-editor wiring in `GuidedRoutineEditor.kt`.
  - Notes: once inserted, each added grip is an ordinary independent exercise. Advancing the original later must not silently modify or remove it.

- [x] **DR5-062 — Apply progression atomically and preserve exercise independence**
  - Outcome: accepting a progression produces exactly one safe future-routine update while completed and in-progress workout snapshots remain historically correct.
  - Scope: repository mutations, automatic next-target calculations, custom-step consumption, insertion lineage, idempotency, undo, partial-session isolation, history behavior, failure handling, and focused repository/state tests; no final workout-sheet polish.
  - Model: `gpt-6-astra`
  - Model reason: atomic cross-aggregate transitions, snapshots, retries, and undo require careful invariant reasoning.
  - Depends on: DR5-059, DR5-060, DR5-061
  - Status: `complete`
  - Acceptance: applying a single-measure automatic rule advances only that measure within its configured bound; dual-measure rules can calculate exact alternatives that increase weight, increase duration, or increase weight while reducing duration without changing an unselected value unexpectedly; custom advancement replaces only the source exercise and inserts each configured addition exactly once immediately after it; newly inserted exercises progress independently on future completions; routine revision advances once per accepted mutation; the active/completed workout keeps its original snapshot; retries, process recreation, duplicate taps, and stale revisions cannot double-advance or duplicate inserted exercises; immediate Undo restores the previous source prescription and removes only additions created by that transition; write failure leaves the live routine unchanged and recoverable; focused tests cover bounds, final steps, IDs, ordering, restart, stale events, undo, and history integrity.
  - Executor thread ID: `01a0b64a-0393-76b3-815c-e0821b620a38`
  - Started at: `2026-09-18T15:52:27-05:00`
  - Claimed: 2026-09-18T15:52:27-05:00 by gpt-6-astra; user authorized immediate manual execution overriding cooldown.
  - Completed at: `2026-09-18T16:16:31-05:00`
  - Completed: Atomic progression application, bounded weight/duration/heavier-shorter alternatives, ordered custom-step consumption and stable independent insertions, durable per-session/exercise receipts, stale-event protection, selective immediate Undo, and write-failure recovery are implemented in `ExerciseProgression.kt`, `AppRepository.kt`, and the strict current document/codec. All 265 unit tests (including 21 new application tests and 6 existing progression tests), `lintDebug`, `assembleDebug`, and `git diff --check` passed. Active/completed snapshots remain unchanged. Handoff and API semantics are in `docs/reviews/DR5-062.md`. Existing Phase 8 and unrelated edits were preserved; no commit, push, tag, release, or UI changes.
  - Push: `NO`
  - Notes: progression is intentional movement between prescriptions, not a scalar difficulty score; one measure may decrease while another increases.

- [x] **DR5-063 — Add the in-workout progression decision flow**
  - Outcome: each completed eligible exercise can be advanced with minimal interruption while continuing the workout remains the clear default.
  - Scope: guided-session completion hook, progression bottom sheets, exact-result selection for single/dual measurements and custom steps, application/undo feedback, resume behavior, haptics/voice boundaries, accessibility, screenshots, and focused UI/state tests.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded guided-session UI integration after transition semantics are proven.
  - Depends on: DR5-062
  - Status: `complete`
  - Acceptance: after the final set of an exercise with an available next progression, show the concise title `<Exercise name> complete` with no explanatory subtitle, a secondary outlined **Ready for more** action, and a primary filled-blue **Continue workout** action; no Too hard/About right scale appears; Continue changes nothing, proceeds to the next exercise, and allows the prompt again after the next workout's completion; Ready applies a single unambiguous next prescription directly or, when weight and duration both vary, opens an exact-result chooser that supports weight-only, duration-only, and heavier/shorter outcomes without offering an accidental increase-both default; the selected future pounds/seconds are shown before commitment; successful application proceeds normally and offers Undo without blocking the workout; exercises at an automatic bound, final custom step, or with progression disabled skip the sheet entirely; rotations, backgrounding, process recreation, repeated taps, timers, voice cues, and incomplete-session restoration do not repeat or lose the decision; semantics state the full resulting prescription; compact, tall, landscape, and large-text screenshots and focused tests pass.
  - Executor thread ID: `01a0b662-9005-7902-a552-d1c83f5bbb09`
  - Started at: `2026-09-18T16:18:54-05:00`
  - Completed at: `2026-09-18T17:30:29-05:00`
  - Push: `NO`
  - Claimed: 2026-09-18T16:18:54-05:00 by gpt-5.6-sol; user explicitly overrode the queue cooldown for immediate manual execution.
  - Completed: Added durable per-exercise decision handling, the concise completion sheet, exact weight/time/heavier-shorter chooser, atomic progression application, nonblocking Undo, full-result semantics, and eight compact/tall/landscape/large-text references in `GuidedSessionScreen.kt` and `GuidedSessionState.kt`. All 268 unit tests and 287 screenshot validations passed with zero failures; `lintDebug`, `assembleDebug`, visual inspection, and `git diff --check` passed. Rotation/recreation, resume, repeat-tap, timer, voice, bounds, final-step, disabled, and unavailable behavior follow the saved session and existing durable progression receipts.
  - Notes: do not move this decision to whole-routine completion. Continue workout is always the visually primary action.

- [x] **DR5-064 — Harden progression across editing and workout lifecycles**
  - Outcome: progression remains coherent under real editing, deletion, scheduling, backup, and session edge cases.
  - Scope: cross-feature integration, backup/current-document coverage, exercise deletion/reordering, routine edits with partial sessions, added-exercise management, accessibility and exceptional layouts, performance, documentation updates, and full regression tests.
  - Model: `gpt-5.6-sol`
  - Model reason: broad but concrete integration pass across established app workflows.
  - Depends on: DR5-063
  - Status: `complete`
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-18T17:38:27-05:00`
  - Completed at: `2026-09-18T18:09:27-05:00`
  - Acceptance: routine/exercise edits cannot leave dangling custom additions or duplicate progression identities; deleting or reordering a source/inserted exercise has explicit deterministic behavior; saved incomplete sessions remain immutable snapshots while future sessions use the progressed routine; backup/export/import retains every rule and current value; no prompt appears for linked-app routines, unavailable next steps, or already-handled completions; all new controls meet touch-target, TalkBack, keyboard, large-text, compact, tall, and landscape requirements; README, `RoutineEditor.md`, `GuidedSession.md`, and `TechnicalDesign.md` describe the shipped behavior and limits; focused tests, all unit tests, screenshot tests, lint, assemble, and `git diff --check` pass.
  - Push: `NO`
  - Claimed: 2026-09-18T17:38:27-05:00 by gpt-5.6-sol; user explicitly overrode the queue cooldown for immediate manual execution.
  - Completed: Hardened the live/future exercise identity namespace, editor collision rejection, deterministic source/future/inserted deletion and reordering, handled-completion suppression at the repository boundary, routine-deletion receipt pruning, immutable partial/history behavior, and full progression backup round trips. Updated README, `RoutineEditor.md`, `GuidedSession.md`, and `TechnicalDesign.md` with shipped behavior and limits. Focused tests, all 272 unit tests, all 287 screenshot validations, lint (0 errors), debug assembly, and `git diff --check` passed. Existing Phase 8 and unrelated work were preserved; no separate dashboard, commit, push, tag, or release was created.
  - Notes: keep the interaction inside existing DraftingRoom5 workflows and visual language; do not introduce a separate progression dashboard.

- [x] **DR5-065 — Audit exercise progression independently**
  - Outcome: an independent review verifies progression correctness, workout safety, usability, accessibility, and regression quality before release.
  - Scope: Phase 8 diff, domain/repository invariant review, native UI and lifecycle evidence, screenshot inspection, full regression gate, and a review artifact under `docs/reviews/`; fixes remain within Phase 8 scope.
  - Model: `gpt-6-astra`
  - Model reason: independent review of stateful progression, atomic insertion, undo, and lifecycle behavior benefits from difficult cross-cutting reasoning.
  - Depends on: DR5-064
  - Status: `complete`
  - Acceptance: the audit traces no/automatic/custom progression through codec, editor, repository, active session, completed history, backup, undo, and recreation; proves exercise-by-exercise independence and exactly-once custom insertion; exercises weight-only, duration-only, dual-measure heavier/shorter, bounds, final-step prompt skipping, Continue behavior, and failure recovery; inspects real compact/tall/landscape/large-text screens for the approved concise hierarchy; verifies accessibility and timer/voice/haptic coexistence; records emulator/device evidence or limitations honestly; all relevant unit, screenshot, lint, build, and whitespace checks pass; unresolved correctness or usability findings block release.
  - Executor thread ID: `01a0b6ce-336b-7d01-a2ee-03d1f153f308`
  - Started at: `2026-09-18T18:16:56-05:00`
  - Claimed: 2026-09-18T18:16:56-05:00 by gpt-6-astra; immediate execution explicitly authorized by user.
  - Completed at: `2026-09-18T19:28:25-05:00`
  - Completed: Independent audit corrected eight findings covering visible measurements, retained terminal values, exact previews, custom result disclosure, draft restoration, failure recovery, JSON depth safety, and reorder identity/accessibility. All 278 unit tests, 291 screenshot comparisons, lint (0 errors), APK builds, native API 35 progression/lifecycle checks, and whitespace validation passed. Evidence and explicit physical-device limitations are in docs/reviews/DR5-065/README.md. Accumulated work preserved; no commit, push, tag, or release.
  - Push: `NO`

- [x] **DR5-066 — Release the exercise progression milestone**
  - Outcome: the audited exercise-by-exercise progression feature ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 8 findings, final documentation/version defaults, full verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic release procedure after independent audit.
  - Depends on: DR5-065
  - Status: `complete`
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-18T19:30:55-05:00`
  - Completed at: `2026-09-18T20:20:12-05:00`
  - Acceptance: DR5-059–065 and audit findings are complete; product and technical documentation match the final behavior; default version name/code advance consistently from the latest released version; `testDebugUnitTest lintDebug assembleDebug`, screenshot tests, focused progression checks, and whitespace checks pass; the coherent Phase 8 work is committed and pushed once; the next increasing version tag is pushed; the release workflow completes; and the signed APK is published and verified.
  - Push: `YES`
  - Claimed: 2026-09-18T19:30:55-05:00 by gpt-5.6-sol; user explicitly overrode the queue cooldown for immediate release work.
  - Completed: Phase 8 documentation and defaults identify v0.26.0/26002 consistently. The Java-17-aware Windows Gradle bootstrap prevents the host's older Java from reaching the build and was verified from an intentionally forced Java 8 environment. Focused progression checks passed; the corrected complete gate passed all 278 unit tests, all 291 screenshot comparisons, lint with 0 errors, debug and instrumentation APK builds, and whitespace validation. Native Android API 35 checks passed Continue persistence, timer lifecycle/deduplication, exact-once custom insertion and Undo, and the exact 32.5 lb/25 second heavier-shorter result while preserving the session snapshot. The coherent milestone is delivered by tag `v0.26.0`; the successful tag workflow and signed `DraftingRoom5.apk` are verified at [GitHub Actions](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml) and the [v0.26.0 release](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.26.0).
  - Notes: never commit credentials, signing material, generated build files, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 8 tasks separately.
