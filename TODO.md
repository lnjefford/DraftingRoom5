# DraftingRoom5 implementation queue

This is the shared queue for the Astra and Sol scheduled tasks. The approved requirements are in `DesignReview.md`, `Dashboard.md`, `MetricDetails.md`, `Settings.md`, `DashboardCustomization.md`, `SchedulesAndRoutines.md`, `RoutineEditor.md`, `GuidedSession.md`, and `docs/design/retirement-workspace/README.md`.

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
- `last_completion_at`: `2026-09-25T13:41:01.322801-05:00`
- `next_eligible_dispatch_at`: `2026-09-25T14:41:01.322801-05:00`
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

## Phase 9 — Retirement workspace

Approved direction: incorporate the validated retirement-planning behavior from `../finance` as a first-class green Retirement workspace without changing the Fitness workspace. The durable screen, navigation, data-source, visual, security, and removal decisions are in `docs/design/retirement-workspace/README.md`. Intermediate tasks accumulate locally; only the terminal Phase 9 release task commits, pushes, tags, and publishes.

- [x] **DR5-067 — Lock the Retirement architecture and parity contract**
  - Outcome: implementation can proceed against one secure Android architecture with explicit parity fixtures from the existing finance site.
  - Scope: inspect `../finance` models, repositories, integrations, simulation/tax code, schemas, and tests; document native-versus-provider boundaries; define Kotlin domain/persistence contracts, secret handling, backup exclusions, provider interfaces, background-work constraints, navigation route model, and a fixture-based parity plan; no production Retirement UI.
  - Model: `gpt-6-astra`
  - Model reason: financial correctness and mobile secret boundaries require difficult cross-system architecture reasoning.
  - Depends on: DR5-066
  - Status: `complete`
  - Acceptance: an architecture record maps every approved data source and screen to a Kotlin owner; money uses integer cents and snapshots are append-only; coherent forecast input generations and atomic provider/import updates are specified; Plaid and RentCast secrets are proven absent from the APK, repository, logs, and backup; Shareworks retention and redaction rules are explicit; representative sanitized fixtures and expected outputs cover money, account classification, workbook parsing, property estimates, amortization, tax/ACA, withdrawal order, and forecast results; the route graph supports restoration and back navigation; any backend or companion requirement that materially changes deployment is raised as `needs_input` with concrete options before implementation.
  - Executor thread ID: `01a0b767-4a6e-74e3-9018-ed5c787d8ebd`
  - Started at: `2026-09-18T22:04:50.5144557-05:00`
  - Completed at: `2026-09-19T12:23:54-05:00`
  - Push: `NO`
  - Notes: read `docs/design/retirement-workspace/README.md` first. The existing Python app is reference behavior, not code to ship inside Android. Do not embed provider credentials or real financial data in fixtures.

  - Claimed: 2026-09-18T21:03:53.9263414-05:00 by gpt-6-astra; immediate dispatch explicitly authorized; DR5-066 dependency complete.
  - Interrupted: production v0.26.0 data-load hotfix took priority before implementation began; return this task to ready after the hotfix release.

  - Resumed: 2026-09-18T22:04:50.5144557-05:00 by gpt-6-astra after verified v0.26.1 hotfix; user requested restart. No other running queue claim; processor lease empty.

  - Input blocked at: `2026-09-18T22:19:35.2915939-05:00`
  - Prepared: `docs/design/retirement-workspace/ARCHITECTURE.md`, `parity/` and `VALIDATION.md`; 103 offline reference assertions and 14 contract tests pass. No production UI or hotfix files changed; no commit/push/tag/release.
  - Prior blocker (resolved 2026-09-19): Decision D1 requires user selection of an authenticated user-run companion (A), hosted private backend (B), or explicit local-only scope revision (C); for A/B identify host/machine and operator. The reference requires server-side Plaid/RentCast secrets; deployment was not approved. Independent architecture/fixture work is prepared. DR5-068 and descendants remain blocked; completion/cooldown timestamps unchanged.

  - Resumed: 2026-09-19T12:22:41.732952-05:00 by gpt-6-astra; user explicitly approved phone-only providers with user-entered, Keystore-protected credentials, superseding companion options.

  - Completed: 2026-09-19T12:23:54-05:00 — User approved phone-only native provider calls with personal credentials entered on-device and protected by Android Keystore. Updated architecture, product handoff and validation evidence; 103 reference assertions, 14 contract tests and whitespace checks pass. Provider runtime/Keystore verification remains in DR5-070/072/077/078. No production UI, commit, push, tag or release. DR5-068 promoted ready.

- [x] **DR5-068 — Add the extensible workspace shell and Retirement theme**
  - Outcome: users can move between the unchanged Fitness workspace and a native green Retirement shell with stable top-level navigation.
  - Scope: shared workspace switcher, workspace-aware routes/state restoration, Retirement color/typography tokens, Retirement top app bar, Overview/Forecast/Assets tab shell, book and forecast-settings actions, placeholder destinations, and shell screenshot tests only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Compose navigation and theming work using established app patterns.
  - Depends on: DR5-067
  - Status: `complete`
  - Acceptance: the workspace switcher can grow beyond two workspaces without a top-tab redesign; Fitness UI and behavior remain visually unchanged; Retirement uses the approved evergreen palette and DraftingRoom5 visual language; its book icon routes to Library and gear routes directly to Forecast settings; Overview, Forecast, and Assets restore across rotation/process recreation; no general Retirement settings route exists; compact, tall, landscape, and large-text shell screenshots plus focused navigation tests pass.
  - Executor thread ID: `01a0bab6-fdff-7940-a69b-2005e0bad7d6`
  - Started at: `2026-09-19T12:30:03.0654401-05:00`
  - Completed at: `2026-09-19T13:22:33-05:00`
  - Push: `NO`
  - Claimed: 2026-09-19T12:30:03.0654401-05:00 by gpt-5.6-sol; immediate execution explicitly requested, overriding the queue cooldown. No other queue worker was running and the processor lease was empty.
  - Completed: Extensible icon-menu workspace switcher, independent saved Fitness/Retirement stacks, scoped evergreen theme, native Overview/Forecast/Assets shell, Library and direct Forecast-settings routes, focused route tests, and 20 new compact/tall/landscape/large-text references. Visual inspection caught and corrected compact/large-text app-bar overlap. All 282 unit tests, 311 screenshot comparisons, lint (0 errors), debug assembly, and whitespace checks pass. Fitness retains its prior theme and behavior with only the shared switcher affordance added. No commit, push, tag, or release.
  - Notes: do not modify the workout workspace theme. Use native Compose, not the exploratory HTML.

- [x] **DR5-069 — Implement Retirement persistence and manual asset foundations**
  - Outcome: the app can safely persist and aggregate manually managed retirement accounts, snapshots, properties, mortgages, plan settings, checklist state, and provider/import metadata.
  - Scope: Kotlin domain types, repository/storage wiring, strict codec/schema, integer-cent money helpers, append-only balance/property snapshots, archive/inclusion behavior, manual account/property mutations, validation, backup inclusion/exclusion decisions, fixtures, and focused tests; no live Plaid, RentCast, workbook parsing, forecast engine, or final UI.
  - Model: `gpt-5.6-sol`
  - Model reason: deterministic domain and persistence work with clear contracts from DR5-067.
  - Depends on: DR5-067
  - Status: `complete`
  - Acceptance: account types, tax treatments, owner, source, freshness, holdings, property/mortgage/equity, import metadata, plan settings, and checklist state round-trip exactly; money is persisted as integer cents; snapshot histories are append-only and corrections create new records; aggregate totals cannot double-count property or Epic accounts; writes are atomic and survive recreation; secrets/raw Shareworks files are excluded from ordinary document backup; invalid values and duplicate provider identities are rejected; focused repository/codec tests and parity fixtures pass.
  - Executor thread ID: `01a0bb30-bf44-7a71-afb2-594893a45cf2`
  - Started at: `2026-09-19T14:42:50.0890221-05:00`
  - Completed at: `2026-09-19T15:52:17-05:00`
  - Push: `NO`
  - Notes: preserve the project's current clean-slate schema conventions. Do not add rental-priority domain or UI concepts.
  - Completed: Clean-slate no-backup SQLite storage, strict v1 codec, integer-cent money, account/holding/property/mortgage/Epic/plan/checklist/provider domains, atomic manual repositories, append-only correction ledgers/triggers, safe origin-aware aggregation, and sanitized `manual-foundations.json` parity coverage. All 295 unit tests, 14 executable parity contract tests, lint, and whitespace checks pass; focused DR5-069 tests cover exact exhaustive enum/state round trips, invalid/duplicate rejection, atomic failure/recreation, correction history, archive/inclusion, no double counting, and backup exclusions. No live provider, workbook parsing, forecast, rental concept, UI, commit, push, tag, or release was added.
  - Claimed: 2026-09-19T14:42:50.0890221-05:00 by gpt-5.6-sol; explicitly dispatched in the current saved checkout with preservation of accumulated Phase 9 work.

- [x] **DR5-070 — Integrate linked financial accounts securely**
  - Outcome: users can connect, review, refresh, reconnect, and safely classify read-only financial accounts without exposing credentials or provider secrets.
  - Scope: the approved provider topology, Plaid link/relink handoff, token handling, account/holding sync, classification review, duplicate matching, refresh metadata/errors, manual fallback, background refresh, redacted diagnostics, and integration tests; no final account-list/detail polish.
  - Model: `gpt-6-astra`
  - Model reason: OAuth-style handoff, token security, reconciliation, idempotency, and provider failures are high-consequence cross-system work.
  - Depends on: DR5-067, DR5-069
  - Status: `complete`
  - Acceptance: the app can request a connection, return safely, show provider accounts for explicit inclusion, and persist user-confirmed account type/tax treatment; credentials and service secrets never enter the APK or logs; access tokens follow the architecture's protected boundary; repeated syncs do not duplicate accounts, holdings, or same-day snapshots; reconnect repairs only the affected item; stale/error state stays attached to affected accounts; background work obeys Android constraints; manual accounts remain usable without Plaid; sanitized provider-contract tests cover success, cancellation, partial data, duplicate masks, stale tokens, reconnect, offline behavior, and atomic failure.
  - Executor thread ID: `01a0bc7b-6121-73b2-84b9-6a5b5318a5f9`
  - Started at: 2026-09-19T20:44:51.6124237-05:00
  - Completed at: 2026-09-19T22:28:31-05:00
  - Push: `NO`
  - Notes: implement approved phone-only PlaidNativeProvider plus the shared ProviderCredentialVault/Keystore and secure transport described in ARCHITECTURE.md D1. Credentials are entered privately on-device, never bundled or supplied through chat. Include setup/replacement within Connect/reconnect/attention flows. Preserve read-only semantics; no separate Data sources screen or companion/backend.

  - Claimed: 2026-09-19T20:44:51.6124237-05:00 by gpt-6-astra; immediate execution authorized, overriding cooldown; preserving accumulated DR5-067 through DR5-069 work.

  - Validation: 332 unit tests, 311 screenshot comparisons, zero lint errors, both local APK builds, API 35 Keystore/SQLite synthetic audit, 103 parity assertions and 14 contract tests pass. See docs/design/retirement-workspace/DR5-070.md for boundaries and remaining live/device gates. Preserved accumulated work and v0.26.1; no commit, push, tag or release.

- [x] **DR5-071 — Build Accounts and account-management flows**
  - Outcome: every financial account is usable through the approved grouped Accounts experience and its supporting detail flows.
  - Scope: Accounts grouped list, add-asset entry, connected/manual account paths, post-link review UI, account detail, update-balance sheet, balance history, edit/archive behavior, holdings display, needs-attention state, reconnect entry, accessibility, screenshots, and focused UI/state tests; properties and Epic use placeholders until their tasks complete.
  - Model: `gpt-5.6-sol`
  - Model reason: substantial but bounded Compose screen family on established repositories and provider state.
  - Depends on: DR5-068, DR5-069, DR5-070
  - Status: `complete`
  - Acceptance: Accounts has no total header and groups employer plans, IRAs, health savings, brokerage, cash, and properties; linked and manual accounts are clearly sourced; account details expose current value, classification, owner, inclusion, freshness, holdings, history, edit, and appropriate update/reconnect actions; manual updates append snapshots; provider values are not overwritten manually; attention appears on the affected account; add/review flows match the approved hierarchy; back/restoration behavior, compact/tall/landscape/large-text screenshots, TalkBack labels/order, and focused tests pass.
  - Executor thread ID: `01a0bd05-8efc-7682-894d-b0789c05c151`
  - Started at: `2026-09-19T23:14:42.7371922-05:00`
  - Completed at: `2026-09-20T00:50:24.8238097-05:00`
  - Push: `NO`
  - Claimed: 2026-09-19T23:14:42.7371922-05:00 by gpt-5.6-sol; immediate execution authorized, overriding cooldown; preserving accumulated DR5-067 through DR5-070 work and released v0.26.1 state.
  - Notes: the Assets tax-treatment table links here. Do not add account-type shortcut buttons or a Data sources destination.
  - Completed: Native grouped Accounts, add-asset entry, established connect/manual/review paths, full account detail, append-only manual balance updates, history, local edits/archive, holdings, per-item attention/reconnect, Assets link, accessible semantics, typed restoration/back routes, and 28 new four-form-factor screenshots. All 337 unit tests and 339 screenshot comparisons passed; lint has zero errors; debug and instrumentation APKs, 103 finance-reference assertions, 14 contract tests, and whitespace validation passed. Visual inspection covered compact, tall, landscape, and 2× text states. See `docs/design/retirement-workspace/DR5-071.md`. Preserved accumulated Phase 9 work and v0.26.1; no commit, push, tag, or release.

- [x] **DR5-072 — Add automatic property values and property flows**
  - Outcome: a home can be found, confirmed, tracked, refreshed, edited, and included in retirement calculations without a separate property workspace.
  - Scope: RentCast provider implementation through the approved secure boundary, address search/manual fallback, property match confirmation, weekly and explicit refresh, estimate range/comparable metadata, mortgage/equity calculations, property detail/edit/history UI, account-list integration, background work, screenshots, and tests.
  - Model: `gpt-5.6-sol`
  - Model reason: cohesive provider, domain, and Compose flow with well-defined calculations and states.
  - Depends on: DR5-068, DR5-069, DR5-070
  - Status: `complete`
  - Acceptance: selecting an address shows the matched property and latest estimate/range before creation; mortgage balance yields exact equity without floating-point dollars; automatic estimates refresh approximately weekly and manual refresh is available; every accepted provider or manual valuation appends history; failures preserve the last good value and show its age/error; manual property entry remains available without claiming automatic updates; properties appear as an Accounts section and open directly to detail; no separate Properties overview or rental-priorities screen exists; provider fixtures, background-work tests, UI tests, and compact/tall/landscape/large-text screenshots pass.
  - Executor thread ID: `01a0c099-ebda-7c43-9387-84907ef5f909`
  - Started at: `2026-09-20T15:55:31.0082682-05:00`
  - Completed at: `2026-09-20T17:53:17.1025907-05:00`
  - Push: `NO`
  - Claimed: 2026-09-20T15:55:31.0082682-05:00 by gpt-5.6-sol; immediate execution explicitly requested, overriding the queue cooldown. Preserving accumulated DR5-067 through DR5-071 work and released v0.26.1 state.
  - Notes: use phone-only RentCastNativeProvider with user-entered credentials and the shared vault/transport supplied by DR5-070; this adds the DR5-070 dependency. Credential setup belongs inside Find/Edit property; no companion/backend. Follow ARCHITECTURE.md D1 and retain manual fallback.

  - Completed: Phone-only secure RentCast search/confirmation and credential replacement, manual fallback, exact-cent mortgage/equity, append-only provider/manual history, scoped failure preservation, weekly/explicit refresh, direct Accounts property detail/edit/history flows, accessible semantics, fixtures and 12 new four-form-factor property references. All 342 unit tests and 351 screenshot comparisons passed; lint reported zero issues; debug and instrumentation APKs, 103 finance-reference assertions, 14 contract tests, visual inspection and whitespace checks passed. Live RentCast and emulator callbacks remain DR5-077/078 gates. Preserved accumulated Phase 9 work and v0.26.1; no commit, push, tag or release.

- [x] **DR5-073 — Port the Shareworks Epic stock import**
  - Outcome: Epic stock is derived exclusively from a validated user-selected Shareworks workbook and remains fully usable without manual Epic fields.
  - Scope: Android-safe `.xlsm` selection/parsing or approved secure processing boundary, required-sheet/cell validation, derived position/breakdown/projection models, atomic replacement, import metadata, Epic detail/upload UI, redaction, fixtures, screenshots, and tests.
  - Model: `gpt-6-astra`
  - Model reason: sensitive workbook parsing, formula-cache semantics, atomic replacement, and parity with a specialized existing parser require careful reasoning.
  - Depends on: DR5-067, DR5-069, DR5-068
  - Status: `complete`
  - Acceptance: the implementation reads the same required Shareworks sheets and cached calculated values as the reference parser; share price, vested/unvested shares, loans, breakdown, projection assumptions, annual projections, tax-at-sale, and after-tax values come only from the latest accepted workbook; invalid, stale-formula, truncated, oversized, or wrong workbook uploads cannot replace good data; the raw workbook is not retained unless the architecture explicitly requires protected storage; logs/screenshots contain no private workbook values; Epic detail offers upload, provenance, and workbook-derived sections but no manual edits; sanitized golden workbook fixtures and parity tests pass.
  - Executor thread ID: `01a0c142-0b38-7303-b9b3-546439a8aa1a`
  - Started at: `2026-09-20T18:59:33.9253287-05:00`
  - Completed at: `2026-09-20T20:42:08.8340267-05:00`
  - Push: `NO`
  - Notes: remove any editable Epic growth field from Forecast settings. The workbook is authoritative for all Epic stock information.

  - Claimed: 2026-09-20T18:59:33.9253287-05:00 by gpt-6-astra; immediate execution authorized, overriding cooldown. Preserve accumulated Phase 9 work and v0.26.1; Push: NO.
  - Completed: Bounded phone-only Shareworks .xlsm import using saved formula results, full typed workbook model, explicit review/atomic replacement, append-only history, last-good preservation, direct read-only Epic detail/upload flows, private error handling and secure Retirement window. Raw workbooks/URIs/filenames are not persisted. All 355 JVM tests, 383 screenshot comparisons, 105 reference assertions and 14 contract tests passed; lint has zero errors and 51 existing warnings; app/test APKs, whitespace and APK fixture-exclusion checks passed. API 35 native audit passed SAX/ZIP parity, SQLite rollback/recreation/history, accessible Choose/Cancel/Replace and FLAG_SECURE. See `docs/design/retirement-workspace/DR5-073.md` for cache-freshness limits and remaining DR5-077/078 device gates. Preserved prior work and version 0.26.1 / 26003; no commit, push, tag or release. DR5-074 promoted ready; cooldown remains in force.

- [x] **DR5-074 — Port and verify the retirement forecast engine**
  - Outcome: DraftingRoom5 produces deterministic and Monte Carlo retirement results that match the validated finance behavior for equivalent inputs.
  - Scope: real-dollar forecast core, seeded Monte Carlo paths, correlated returns, contributions, Epic workbook projection input, property/mortgage treatment, withdrawal ordering, federal/Wisconsin tax, LTCG, ACA, Social Security, pension, scenario deltas, risk diagnostics, performance work, and parity tests; no final Forecast UI.
  - Model: `gpt-6-astra`
  - Model reason: numerical financial logic, taxes, withdrawal sequencing, and cross-language parity are unusually difficult and consequence-sensitive.
  - Depends on: DR5-069, DR5-070, DR5-072, DR5-073
  - Status: `complete`
  - Acceptance: equivalent sanitized fixtures match the reference implementation within documented cent/percentage tolerances for deterministic results, seeded distributions, taxes, ACA, amortization, Social Security/pension timing, Epic liquidation, withdrawal ordering, depletion timing, and scenario changes; simulations use coherent immutable input snapshots and real dollars; seedable tests are deterministic while production runs use documented randomness; calculations cannot overflow practical ranges or block the UI thread; cancellation/restart never publishes a partial generation; risk diagnostics identify failure timing/causes without inventing qualitative scores; focused parity and performance tests pass.
  - Executor thread ID: `01a0c1a4-4301-7751-a8e5-937726b12094`
  - Started at: 2026-09-20T20:47:03.4120180-05:00
  - Completed at: 2026-09-20T21:57:41.6137306-05:00
  - Push: `NO`
  - Notes: internal withdrawal-strategy logic may remain, but there is no Strategy screen. Document assumptions and tolerances clearly; this is planning software, not advice.

  - Claimed: 2026-09-20T20:47:03.4120180-05:00 by gpt-6-astra; immediate execution explicitly authorized, overriding cooldown; preserving accumulated Phase 9 changes and v0.26.1. Push: NO.

  - Completed: Real-dollar deterministic/seeded correlated Monte Carlo core, immutable repository snapshots, workbook-only Epic and property/mortgage projections, contributions, ordered withdrawals, labeled federal/WI/LTCG/ACA policy, SS/pension/RMD timing, typed scenario CAS and observed failure/tax-residual diagnostics. All 379 JVM tests passed; 35,412 full-path comparisons have zero cent difference (1 cent permitted), with exact scalar cents and 1e-12 success-fraction tolerance. Passed 105 reference assertions, 14 expanded full-path replays, 14 contract tests, lint (0 errors; 51 existing warnings), both APK builds, fixture exclusion and whitespace checks. 10,000 paths x 50 years took 1.364s host / 9.706s API 35, with main-thread responsiveness, seeded repeatability and cancellation under 2s verified. See `docs/design/retirement-workspace/DR5-074.md` for the deliberate reference tax/ACA approximation, measured residuals, heap-guard correction and physical-device/multi-API limits. No final Forecast/Strategy UI, version change, commit, push, tag or release. Preserved accumulated Phase 9 work and v0.26.1; only DR5-075 promoted ready.

- [x] **DR5-075 — Build Forecast, settings, risk, and scenario screens**
  - Outcome: users can understand the current forecast, edit legitimate assumptions inline, inspect risk, and compare or apply focused scenarios.
  - Scope: Forecast tab, fan/range chart, retirement marker, available-at-retirement breakdown, lifestyle spending, Forecast settings form, forecast risk, scenario detail/apply behavior, loading/error/stale states, accessibility, screenshots, and focused tests.
  - Model: `gpt-5.6-sol`
  - Model reason: focused visualization and Compose form work after the engine contract is stable.
  - Depends on: DR5-068, DR5-074
  - Status: `complete`
  - Acceptance: Forecast shows modeled success, range, retirement age, available assets, spending, and a clear risk entry; Forecast settings are editable in place and cover approved local settings without navigation into one-field screens; Epic position/growth/tax/sale fields are absent because the workbook owns them; settings validate and trigger one coherent recomputation; scenario details compare against the base plan and apply only the displayed delta; charts remain legible and described for accessibility; stale/error/calculating states are honest; compact/tall/landscape/large-text screenshots, chart semantics, restoration, and focused tests pass.
  - Executor thread ID: `01a0c378-4262-7883-bf6d-6464512e9da4`
  - Started at: 2026-09-21T05:17:33.9316097-05:00
  - Completed at: 2026-09-21T07:08:11.5788060-05:00
  - Push: `NO`
  - Notes: the target card on Overview opens Forecast; do not add a separate financial-runway section. Read DR5-074.md for the engine/coordinator contract, mandatory reference-policy and tax-funding-residual disclosures, device path budgets, and scenario generation checks.

  - Claimed: 2026-09-21T05:17:33.9316097-05:00 by gpt-5.6-sol; immediate execution explicitly requested, overriding the queue cooldown. Preserving accumulated DR5-067 through DR5-074 work and released v0.26.1 state. Push: NO.

  - Completed: Native Forecast with modeled success, accessible percentile fan chart/retirement marker, available-at-retirement buckets, lifestyle spending, disclosed reference-policy/tax-residual limits, honest calculating/stale/empty/error/cancelled states, one restorable whole-plan settings form, factual risk diagnostics, and same-seed single-delta scenario comparison/CAS apply. Overview target opens Forecast; no Strategy/runway or editable Epic assumption was added. All 385 JVM tests, 415 screenshot comparisons, lint (0 errors), both APKs, 105 reference assertions, 14 full-path replays plus 112 tax/SS rows, 14 contract tests, fixture exclusion and whitespace checks passed. Visual inspection corrected compact value wrapping and 2×-text field-label overlap. See `docs/design/retirement-workspace/DR5-075.md`. Preserved accumulated work and v0.26.1 / 26003; no commit, push, tag or release. DR5-076 promoted ready.

- [x] **DR5-076 — Build the integrated Overview, Assets, and Retirement library**
  - Outcome: the Retirement workspace has its approved daily-use landing experience, complete asset summary, and concise research destination.
  - Scope: Overview financial map, retirement-target card, data-health row, Assets total and tax-treatment table, links to Accounts/Forecast/affected items, book-icon Library destination, curated source metadata/checklist persistence, empty/loading/stale states, accessibility, screenshots, and focused integration tests.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Compose integration across already implemented repositories and forecast outputs.
  - Depends on: DR5-071, DR5-072, DR5-073, DR5-075
  - Status: `complete`
  - Acceptance: the financial map is first on Overview; the retirement-target card links to Forecast and duplicates no separate runway; data attention routes to the affected account/import/property; Assets shows the total and tax-treatment table with an Accounts link; totals reconcile exactly with included accounts, workbook-derived Epic value, and property equity without double counting; the Library uses government-first bundled links, displays a review date, stores only checklist state, and never stores private documents; removed Strategy, rental, Properties-overview, Data-sources, and general-settings destinations are absent; all states and compact/tall/landscape/large-text screenshots plus focused navigation/aggregation tests pass.
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-21T07:53:28.8033809-05:00`
  - Completed at: `2026-09-21T09:05:08.9727731-05:00`
  - Push: `NO`
  - Claimed: 2026-09-21T07:53:28.8033809-05:00 by gpt-5; immediate execution explicitly requested, overriding the queue cooldown. Preserving accumulated DR5-067 through DR5-075 work and released v0.26.1 state.
  - Completed: Integrated native Overview, Assets, and Library with financial-map-first order, Forecast-linked target, affected-item data health, exact integer-cent tax-treatment reconciliation, official government-first reviewed resources, and checklist-only persistence. Exact fixture totals reconcile to $78,550.00 tracked and $76,550.00 Forecast-eligible without wrapper double counting. All 391 JVM tests, 443 screenshot comparisons, lint (zero errors), both APKs, 105 reference assertions, 14 full-path forecast replays plus 112 tax/SS rows, 14 contract tests, APK privacy checks, visual inspection, and whitespace checks passed. See `docs/design/retirement-workspace/DR5-076.md`. Preserved accumulated Phase 9 work and v0.26.1 / 26003; no commit, push, tag, or release. DR5-077 promoted ready.
  - Notes: use the approved green theme throughout Retirement and retain the same overall visual feel as Fitness.

- [x] **DR5-077 — Harden Retirement sync, lifecycle, privacy, and recovery**
  - Outcome: Retirement remains correct and understandable through offline use, provider failures, imports, background work, backup, and Android lifecycle events.
  - Scope: cross-feature integration, WorkManager scheduling, retry/backoff, atomic generation/version handling, offline and stale-data behavior, process recreation, backup/restore boundaries, sensitive-data logging/screenshot review, performance, accessibility, documentation, and full regression tests.
  - Model: `gpt-5.6-sol`
  - Model reason: broad but concrete integration pass after every feature path exists.
  - Depends on: DR5-076
  - Status: `complete`
  - Acceptance: no overlapping worker can double-import, double-sync, or publish mixed-generation totals; last-good data remains readable offline with accurate timestamps; failed workbook/provider writes are atomic and recoverable; provider tokens, service secrets, raw workbook content, and private values are excluded from logs and inappropriate backups; navigation and in-progress forms restore safely; Fitness regressions are absent; all new controls meet touch, TalkBack, keyboard, large-text, compact, tall, and landscape requirements; README and TechnicalDesign document the feature, limits, provider topology, privacy boundary, and forecast disclaimer; focused tests, all unit/screenshot tests, lint, assemble, and `git diff --check` pass.
  - Executor thread ID: `01a0c459-2371-7410-b640-7e09909ed1ae`
  - Started at: `2026-09-21T09:23:16.2174441-05:00`
  - Completed at: `2026-09-21T10:51:30.6300129-05:00`
  - Push: `NO`
  - Claimed: 2026-09-21T09:23:16.2174441-05:00 by gpt-5; immediate execution explicitly requested, overriding the queue cooldown. Preserving accumulated DR5-067 through DR5-076 work and released v0.26.1 state.
  - Completed: Cross-feature per-source overlap gating; versioned unique WorkManager scheduling with Android constraints and bounded transient backoff; generation-safe provider/import/forecast publication; offline last-good recovery; privacy/backup/log/APK hardening; lifecycle/accessibility/performance review; corrected seven malformed Library baselines; and complete product/technical documentation. All 394 JVM tests, 443 screenshot comparisons, lint (zero errors), both APKs, 105 reference assertions, 14 full-path forecast cases plus 112 tax/SS rows, 14 contracts, APK/logcat privacy scans, API 35 Keystore/SQLite/Epic/WorkManager/Forecast native audits and whitespace checks passed. API 35 forecast: 7.048s, 268 main-thread pulses, 71,591,520 bytes used, cancellation under two seconds. See `docs/design/retirement-workspace/DR5-077.md` for honest live-provider, physical-device, multi-API, backup-transport, TalkBack and hardware-keyboard limits. Preserved v0.26.1 / 26003; no commit, push, tag or release.
  - Notes: do not claim exact refresh timing when Android or providers cannot guarantee it.

- [x] **DR5-078 — Audit the Retirement workspace independently**
  - Outcome: an independent review verifies financial parity, security, privacy, usability, accessibility, and regression quality before release.
  - Scope: complete Phase 9 diff, provider/security boundaries, workbook handling, numerical parity, aggregation invariants, lifecycle/background behavior, native UI inspection, full regression gate, and a review artifact under `docs/reviews/`; fixes remain within approved Phase 9 scope.
  - Model: `gpt-6-astra`
  - Model reason: independent review of financial calculations and sensitive cross-system integrations benefits from the strongest reasoning model.
  - Depends on: DR5-077
  - Status: `complete`
  - Acceptance: the audit traces each value from Plaid/manual input, Shareworks, RentCast, or settings through persistence, aggregation, forecast, and UI; reproduces representative Python/Kotlin parity fixtures; verifies no secrets/private files leak into APK, repository, logs, backups, screenshots, or test artifacts; exercises reconnect, offline, stale, invalid workbook, failed valuation, duplicate sync, mixed-generation prevention, recreation, and recovery; visually inspects real compact/tall/landscape/large-text screens against the approved handoff; confirms Fitness remains unchanged; all relevant unit, screenshot, integration, lint, build, privacy, and whitespace checks pass; unresolved correctness, security, or usability findings block release.
  - Executor thread ID: `01a0c4f9-438e-72f3-bf17-e0b9d7e24f84`
  - Started at: `2026-09-21T12:18:18.9488783-05:00`
  - Completed at: `2026-09-22T07:13:54.580126-05:00`
  - Push: `NO`
  - Notes: record limitations honestly. Do not weaken tests or redact evidence by omitting failures.
  - Claimed: 2026-09-21T12:18:18.9488783-05:00 by gpt-6-astra; immediate execution authorized, overriding cooldown; independent audit with no commit, push, tag, version bump, or release.
  - Blocked: 2026-09-21T14:14:35.989258-05:00: Independent audit corrected eleven findings; 406 JVM tests, 443 screenshot comparisons, reference parity/contracts, lint (zero errors), both APKs, whitespace and API 35 synthetic Keystore/SQLite/Shareworks accessibility/WorkManager/Forecast/native display and Bundle checks passed. Full evidence and retained failures: `docs/reviews/DR5-078/README.md`. Architecture D1 still requires eligible-account native Link/exchange evidence; remaining physical-device, lifecycle and accessibility gates are not certified. API 28 could not boot because its userdata image requires more free disk space. DR5-079 remains blocked; no descendant promoted. Preserved accumulated work and v0.26.1 / 26003; no commit, push, tag or release.
  - Resumed: 2026-09-21T20:51:08.208649-05:00: User reports additional disk space; retry minimum-API native validation. Preserve Push NO and all existing work.
  - Scope update: 2026-09-21T21:25:11.531604-05:00: User authorizes Android 17+ only and emulator validation. Set minimum/target/compile API 37; older Android compatibility is no longer required. Revalidate full host gates and native API 37 behavior; Push NO remains in force.
  - Blocked after continuation: 2026-09-21T22:43:22.164057-05:00: User-approved Android 17+ support is implemented (minimum/target/compile API 37), with AGP 9.1.1, Gradle 9.3.1 and Kotlin 2.2.10. Final host gate passes 406 JVM tests, 443 unchanged screenshot comparisons, lint (zero errors), both APKs, privacy and 16 KB alignment checks. API 37 native validation is blocked before app installation by a reproducible SurfaceFlinger/GoldfishMapper emulator assertion across two official Android 17 images and multiple graphics modes; crash evidence is retained in `docs/reviews/DR5-078/`. Earlier API 28 native functional checks passed but older Android support is no longer required. Eligible-account live Link/exchange evidence also remains unavailable. DR5-079 stays blocked. No commit, push, tag, version bump or release.
  - Completed with accepted exceptions: 2026-09-22T07:13:54.580126-05:00: User explicitly approved an exception for the missing Android 17 runtime coverage and live Plaid connection test ("An exception for those is fine"). Those checks remain unrun/unverified, not passing; retained emulator failures and the scoped acceptance are documented in `docs/reviews/DR5-078/README.md`. All eleven audit findings are corrected; existing final host evidence remains 406 JVM tests, 443 screenshot comparisons, zero lint errors, both APKs, privacy/alignment and reference parity checks passing. DR5-079 promoted to ready with these disclosed exceptions; preserve version 0.26.1 / 26003 and Push NO for this audit.

- [x] **DR5-079 — Release the Retirement workspace milestone**
  - Outcome: the audited Retirement workspace ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 9 findings, final documentation/version defaults, complete verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic delivery procedure after independent audit.
  - Depends on: DR5-078
  - Status: `complete`
  - Acceptance: DR5-067–078 are complete and the audit has no unresolved blockers; product and technical documentation match the shipped behavior and privacy/provider boundaries; version name/code advance consistently from the latest released version; full unit, screenshot, integration/provider-fixture, forecast parity/performance, privacy, lint, assemble, and whitespace checks pass; coherent Phase 9 work is committed and pushed once; the next increasing version tag is pushed; the release workflow completes; and the signed APK is published and verified.
  - Executor thread ID: `01a0c90c-6965-7232-9a55-df49cc438fb7`
  - Started at: `2026-09-22T07:18:03.8477262-05:00`
  - Completed at: `2026-09-22T08:18:51.7976983-05:00`
  - Push: `YES`
  - Notes: never commit credentials, signing material, generated build files, raw Shareworks workbooks, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 9 tasks separately.
  - Audit handoff: DR5-078 completed with user-approved exceptions for unrun Android 17 runtime validation and live Plaid Link/exchange. Carry these limitations into release documentation; neither is a passing test. See the scoped acceptance in `docs/reviews/DR5-078/README.md`. All other release checks and published-APK verification remain required.
  - Claimed: 2026-09-22T07:18:03.8477262-05:00 by gpt-5.6-sol; user explicitly requested immediate execution, overriding the queue cooldown. Preserving accumulated DR5-067 through DR5-078 work and the accepted Android 17 runtime and live Plaid exceptions.
  - Completed: Coherent DR5-067–078 work is released as v0.27.0 / 27002. The final gate passed 406 JVM tests, 443 screenshot comparisons, lint with zero errors, debug and instrumentation APK assembly, 105 reference assertions, 14 full-path forecast cases, 112 tax/SS rows, 14 architecture contracts, APK identity/signing/privacy/16 KB zip-alignment checks, candidate secret/generated-file scans, and `git diff --check`. The signed `DraftingRoom5.apk` and tag workflow are verified at [v0.27.0](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.27.0) and [GitHub Actions](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml). Android 17 runtime and live Plaid Link/token exchange remain accepted unrun exceptions, not passing tests; see `docs/reviews/DR5-078/` and `docs/reviews/DR5-079/`.

## Phase 10 — Structured exercise targets and optional progression

Approved direction: every exercise has first-class structured target fields for weight, duration, sets, and reps without requiring progression. Weight is optional but, when present, is whole pounds in **5 lb increments only**; every weight control in the exercise editor, manual post-exercise adjustment, and custom-progression step editor changes by exactly 5 lb. Immediately after each exercise is completed, the filled primary action is **Next exercise**, which keeps the current targets. The secondary **Adjust exercise** action reveals controls only on request and can change multiple fields together. Custom progression remains an optional ordered queue of full future prescriptions and is offered only inside that adjustment path. Remove automatic progression, the exercise editor's Rest between sets row, and the explanatory After each exercise card. Preserve custom-step ordering and inserted-exercise behavior. The approved visual direction is the dark Material 3 editor and bottom-sheet flow reviewed in the originating task; build native Compose UI rather than shipping mockup images.

- [x] **DR5-080 — Replace progression storage with structured exercise targets**
  - Outcome: exercise prescriptions and custom steps represent weight, duration, sets, and reps directly, with no dependency on automatic progression.
  - Scope: exercise/prescription/progression domain types, validation, document codec/current schema, repository application and undo behavior, fixtures, and focused unit tests only; no final Compose UI.
  - Model: `gpt-6-astra`
  - Model reason: the clean-slate persistence rewrite and progression invariants affect stored routines, active-session snapshots, custom-step consumption, and undo atomically.
  - Depends on: DR5-079
  - Status: `complete`
  - Acceptance: every exercise and custom replacement prescription has structured slots for weight, duration, sets, and reps; optional fields remain representable without free-form target parsing; sets are positive and every present target is valid; weight is stored as whole pounds and accepts only positive multiples of 5; the old automatic-progression types, options, codec fields, and tests are deleted rather than retained behind compatibility paths; applying a custom step atomically replaces the complete structured prescription, consumes exactly one ordered step, preserves remaining steps and inserted exercises, and remains undoable; keeping current or applying a manual adjustment does not consume a custom step; current-schema encode/decode, invalid-value rejection, repository concurrency/revision checks, undo, active-session snapshots, and focused tests pass.
  - Executor thread ID: `01a0d942-27c9-7d73-a10b-0afac8a096d7`
  - Started at: `2026-09-25T10:50:35.1646853-05:00`
  - Completed at: `2026-09-25T11:15:53.9166288-05:00`
  - Push: `NO`
  - Notes: this repository is clean-slate: do not add migrations, compatibility readers, legacy target parsing, aliases, or version branches. Use an exact integer representation for pounds so the 5 lb invariant is not floating-point-dependent.
  - Claimed: 2026-09-25T10:50:35.1646853-05:00 by gpt-6-astra; explicit task dispatch, Push NO.
  - Completed: Structured integer pounds/duration/sets/reps, strict current-only codec, complete ordered custom replacement, manual multi-target requests preserving queued steps, atomic receipts/undo, snapshot and revision safety, and current fixtures are implemented. Fast passed 121 tests in 9 suites; the single Commit gate passed all 444 tests in 61 suites, lint (0 errors, 46 warnings), and debug APK assembly. Instrumentation and screenshot fixtures compile; no screenshot comparison or device execution was claimed. See `docs/DR5-080.md` for schema/API handoff and exact validation. Preserved actual checkout baseline v0.27.30 / 27032 (newer than the dispatch reference); no commit, push, version bump, tag, or release.

- [x] **DR5-081 — Rebuild the exercise and custom-progression editors**
  - Outcome: users can edit the four base targets on every exercise and optionally maintain an ordered queue of complete future prescriptions.
  - Scope: native Compose exercise editor, target controls, timed-exercise linkage, custom-progression summary and editor, step editing/reordering, state restoration, validation, accessibility, previews/screenshots, and focused tests.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Compose form and ordered-list work on the domain contract established by DR5-080.
  - Depends on: DR5-080
  - Status: `complete`
  - Acceptance: the full editor retains exercise name, artwork, and notes, then presents Weight, Duration, Sets, and Reps as first-class base targets independent of progression; weight minus/plus controls move exactly 5 lb and cannot create a non-multiple-of-5 value; duration, sets, and reps have appropriate independent controls; Timed exercise uses the structured duration; Rest between sets and the After each exercise information card are absent; automatic progression is absent; Planned progression is visually subordinate, can be disabled, summarizes its next full prescription, and opens an ordered future-step editor; every custom step edits complete structured targets, exposes exact before/after changes, supports reorder/edit/add/delete, and retains supported name/notes/artwork and inserted-exercise behavior; unsaved changes, validation, keyboard/TalkBack behavior, state restoration, and compact/tall/landscape/large-text screenshots pass.
  - Executor thread ID: `01a0d96d-0134-74a0-834d-0f53b3a8ecfa`
  - Started at: `2026-09-25T11:37:04.2797058-05:00`
  - Completed at: `2026-09-25T12:10:01.4876298-05:00`
  - Push: `NO`
  - Notes: follow the approved mockups' quiet hierarchy. Weight examples must use 35, 40, 45, and similar 5 lb multiples; do not reproduce the obsolete 37.5 lb examples.
  - Claimed: 2026-09-25T11:37:04.2797058-05:00 by gpt-5.6-sol; user explicitly requested immediate execution, overriding the queue cooldown. Preserving accumulated DR5-080 changes and the actual 0.27.30 / 27032 checkout.
  - Completed: Rebuilt the exercise and future-step editors around independent Weight/Duration/Sets/Reps controls; weight moves only in exact 5 lb steps, duration directly owns the timer, and optional Planned progression summarizes and edits ordered complete prescriptions with exact predecessor deltas and retained inserted-exercise behavior. Added focused state/stepper/change-summary tests, native accessibility audit coverage, and inspected compact/tall/landscape/large-text references for both editors. Fast passed 20 focused tests; Commit passed 446 tests in 61 suites, lint with 0 errors/46 warnings, and debug APK assembly; targeted Release updated and validated exactly 8 intentional editor references. Android-test/screenshot sources compile, but no attached device was available to execute the native audit. See `docs/DR5-081.md`. No commit, push, version bump, tag, release, or publication.

- [x] **DR5-082 — Add optional multi-target adjustment after each exercise**
  - Outcome: completing an exercise normally advances immediately, while an explicit secondary path can update several targets or apply one planned custom step for next time.
  - Scope: guided-session completion state and bottom sheets, manual target adjustment, custom-step preview/application, persistence/undo, race and lifecycle handling, accessibility, screenshots, and focused tests.
  - Model: `gpt-5.6-sol`
  - Model reason: cohesive session-state and Compose interaction work after the target/editor contracts are stable.
  - Depends on: DR5-081
  - Status: `complete`
  - Acceptance: after each exercise reaches all sets complete, the sheet emphasizes a filled Next exercise action that keeps all targets and consumes no custom step; Adjust exercise is secondary and no adjustment controls are initially visible; selecting it shows the current prescription and, when present, the exact next planned step before manual controls; applying a planned step consumes exactly one step and advances; choosing Adjust manually exposes independent weight, duration, sets, and reps controls and can save multiple simultaneous changes; weight changes only in 5 lb increments and never persists an invalid value; manual adjustment leaves queued custom steps intact and previews the exact resulting prescription; back/cancel and process recreation never apply or consume changes; stale routine/session revisions cannot overwrite newer edits; completion feedback, next-exercise focus, undo, compact/tall/landscape/large-text screenshots, TalkBack order/labels, and focused tests pass.
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-25T12:19:41.8567641-05:00`
  - Completed at: `2026-09-25T12:42:35.0248804-05:00`
  - Push: `NO`
  - Notes: this flow is exercise-by-exercise, never deferred until routine completion. The common path must stay faster and visually stronger than either adjustment path.
  - Claimed: 2026-09-25T12:19:41.8567641-05:00 by gpt-5.6-sol; user explicitly requested immediate execution, overriding the queue cooldown. Preserving accumulated DR5-080/081 work and the actual 0.27.30 / 27032 checkout.
  - Completed: Added the per-exercise completion sheet with filled Next exercise and secondary Adjust exercise, exact current/planned review, atomic one-step planned application, independent four-target manual save, strict 5 lb controls, queue preservation, Undo, saveable cancel/back state, and stale-revision/race protection. Fast passed 42 focused tests; Commit passed 449 JVM tests in 61 suites plus lint and debug APK assembly; targeted Release passed 12 inspected compact/tall/landscape/large-text comparisons. Android-test sources compile, but no device was attached for native TalkBack execution. See `docs/DR5-082.md`. Preserved 0.27.30 / 27032; no commit, push, version bump, tag, release, or publication.

- [x] **DR5-083 — Audit structured targets and exercise completion independently**
  - Outcome: an independent review verifies the replacement model, 5 lb invariant, custom-step semantics, optional adjustment flow, accessibility, and Fitness regressions before release.
  - Scope: complete Phase 10 diff, domain/codec/repository invariants, editor and guided-session behavior, lifecycle/concurrency/undo checks, native visual inspection, documentation, and full regression gate; fixes remain within approved Phase 10 scope.
  - Model: `gpt-6-astra`
  - Model reason: independent review of a storage rewrite plus session-state transitions benefits from the strongest reasoning model.
  - Depends on: DR5-082
  - Status: `complete`
  - Acceptance: the audit proves no automatic-progression or free-form-target behavior remains; every entry point enforces whole 5 lb weight steps and rejects malformed stored/request values; base targets work without custom progression; keeping current, manual multi-field adjustment, planned-step application, inserted exercises, step exhaustion, undo, stale revisions, session restart, and process recreation behave exactly as specified; editor and completion screens match the approved hierarchy on compact/tall/landscape/large-text layouts with accessible touch targets and TalkBack order; Retirement behavior is unchanged; product and technical documentation are updated; all unit and screenshot tests, lint, assemble, native checks available in the environment, privacy/backup review, and `git diff --check` pass with evidence under `docs/reviews/`.
  - Executor thread ID: `01a0d9ad-fe65-7c81-aa5d-62b92222d312`
  - Started at: `2026-09-25T12:48:18.8200474-05:00`
  - Completed at: `2026-09-25T13:41:01.322801-05:00`
  - Push: `NO`
  - Notes: record limitations honestly and do not weaken existing progression or session-resilience coverage to make the new model pass.

  - Claimed: 2026-09-25T12:48:18.8200474-05:00 by gpt-6-astra; immediate execution authorized, overriding cooldown. Independent audit; Push NO; preserve 0.27.30 / 27032.

  - Completed: Independent review and fixes documented in `docs/reviews/DR5-083/README.md`; 453 unit tests, 471 screenshots, lint (0 errors / 46 existing warnings), both APK assemblies, offline Retirement checks, APK privacy/backup review, and whitespace checks passed. Back/dismissal recovery, full replacement review, predecessor defaults, numeric layout, and focus recovery corrected. No connected Android device: runtime TalkBack/keyboard/process-death checks remain disclosed limitations. Push NO; defaults remain 0.27.30 / 27032. DR5-084 ready.

- [x] **DR5-084 — Release the structured exercise-target milestone**
  - Outcome: the audited structured-target and post-exercise adjustment redesign ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 10 findings, final documentation/version defaults, complete verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic delivery procedure after independent audit.
  - Depends on: DR5-083
  - Status: `complete`
  - Acceptance: DR5-080–083 are complete and the audit has no unresolved blockers; documentation matches the shipped structured-target, 5 lb weight-step, custom-progression, and exercise-completion behavior; version name/code advance consistently from the Retirement release; full unit, screenshot, integration, lint, assemble, native checks available in the environment, and whitespace checks pass; the coherent Phase 10 work is committed and pushed once; the next increasing version tag is pushed; the tag-triggered release workflow completes; and the signed APK is published and verified.
  - Executor thread ID: `01a09848-5d39-7670-8013-394782400976`
  - Started at: `2026-09-25T13:42:59.0532782-05:00`
  - Completed at: `2026-09-25T14:07:54.1430306-05:00`
  - Push: `YES`
  - Notes: never commit credentials, signing material, generated build files, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 10 tasks separately.
  - Claimed: 2026-09-25T13:42:59.0532782-05:00 by gpt-6-sol; user explicitly requested immediate execution, overriding the queue cooldown. Preserving the complete accumulated DR5-080–083 work and the actual v0.27.30 / 27032 checkout.
  - Completed: Reconciled and documented the coherent structured Weight/Duration/Sets/Reps milestone, exact 5 lb invariant, optional ordered complete prescriptions, fast Next exercise path, optional multi-field adjustment, Undo/lifecycle behavior, and retained device limitations. Defaults advance to v0.27.31 / 27033, the next increasing local and remote version. The single canonical Release gate passed in 850.8 seconds: 453 JVM tests in 62 suites, 471 screenshots, lint with 0 errors / 46 existing warnings, debug assembly, and whitespace. Instrumentation APK assembly passed; no Android device was attached, so runtime TalkBack/keyboard/process-death/timer checks remain unclaimed. The 84,279,376-byte local APK has SHA-256 `1aea4d43108445765085ccbb0b25b17e848cfedf31781530f14d19b83876f3c8`, identity `dev.draftingroom5` 0.27.31 / 27033, one valid v2 debug signer, passing 16 KiB alignment, Fitness-only backup rules, zero prohibited archive types, and zero matches for eight privacy canaries. The coherent commit, pushed tag, successful workflow, and independently verified signed release attachment are linked in `docs/reviews/DR5-084/README.md` and the final task report.
