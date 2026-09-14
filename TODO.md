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
- `last_completion_at`: `2026-09-13T19:30:25-05:00`
- `next_eligible_dispatch_at`: `2026-09-13T20:30:25-05:00`
- `processor_lease`: empty
- For active queued work, dispatch only a task whose `Status` is `ready`, and never dispatch while another task is `running` or before `next_eligible_dispatch_at`.
- When claiming an active task, set its `Status` to `running` and fill `Executor thread ID` and `Started at`; the checkbox claim marker remains `[~]` for compatibility with the worker protocol above.
- When an active task completes, record its actual `completed_at`, copy that timestamp to `last_completion_at`, set `next_eligible_dispatch_at` to 60 minutes later, and promote only the next dependency-satisfied task from `blocked` to `ready`.
- A completed task must use checkbox `[x]` and `Status: complete`; a failed or input-blocked task must record evidence and use `Status: failed` or `Status: needs_input` without unblocking descendants.

## Phase 7 — Living icon-sized widget

Approved direction: a one-cell home-screen widget that opens the app normally on tap and rotates through **50** distinct still images at a battery-conscious, inexact cadence. The **5** and navy/ivory/gold identity recur across the collection, but neither a readable 5 nor an intact border is required in every image: a strong concept may mirror, obscure, fragment, transform, or even hide the 5. The gold border and the 5 can move, bend, break, rotate, or become part of imaginative scenes. The four samples and full brief are in `docs/design/widget-icon-variants/README.md`. Keep the actual launcher icon unchanged.

- [ ] **DR5-048 — Prototype the icon-sized tap-to-open widget**
  - Outcome: a 1 × 1 home-screen widget displays the DraftingRoom5 identity, launches the normal app route on tap, and can rotate a small set of images without an always-on service.
  - Scope: new app-widget provider/layout/manifest wiring, prototype artwork derivatives of the four approved samples, scheduling integration, focused tests, and widget preview only; do not alter the launcher icon or Phase 6 product behavior.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android widget feasibility and lifecycle implementation.
  - Depends on: none
  - Status: `ready`
  - Acceptance: widget advertises a one-cell target size with sensible minimums; its full tile has a direct activity `PendingIntent` that opens the app through its normal entry point; a launcher can render the 5 and frame at icon-like size without clipping; rotation among the four samples works on supported host/emulator tests using a platform-supported, approximately hourly and inexact cadence plus safe refresh on relevant app interactions; no 30-second promise, foreground service, wake lock, or per-minute background polling; widget removal cancels unnecessary work; API 28–36 behavior and no-widget case are tested. If a real 1 × 1 tile or practical rotation fails on the available launcher, mark `needs_input` with evidence before commissioning 50 assets rather than silently enlarging the widget or changing the brief.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: use `docs/design/widget-icon-variants/` as approved direction only, not as unoptimized runtime PNGs. An approximately hourly rotation is a default to validate against Android battery guidance, not an exact-time guarantee. Keep the widget's click target and accessible label clear.

- [ ] **DR5-049 — Specify fifty distinctive widget-image concepts**
  - Outcome: a production-ready catalog names 50 visually different still transformations with a common icon-sized art contract.
  - Scope: `docs/design/widget-icon-variants/` concept catalog, art prompts, small-size QA rubric, safe-zone and optimization specification only.
  - Model: `gpt-5.6-sol`
  - Model reason: creative but bounded art-direction inventory following the proven widget footprint.
  - Depends on: DR5-048
  - Status: `blocked`
  - Acceptance: the catalog has 50 unique numbered IDs and one distinct scene/effect each; concepts are visually clear at one-cell size, while the 5 and navy/ivory/gold cues recur across the set rather than being compulsory in every image; intentional mirrored, obscured, fragmented, or hidden-5 concepts are explicitly allowed; concepts include the approved dumbbell, flex, fire, and melt seeds plus a 5 lying down and being abducted by aliens; several concepts break, overlay, rotate, fold, or repurpose the gold frame; training, surreal, elemental, kinetic, and playful ideas are balanced without near-duplicate recolors; exact prompts, negative constraints, target geometry, naming, and acceptance-at-rendered-size checks are recorded for five ten-image production batches.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: the first four images are approved examples, not a creativity ceiling; they may count toward 50 only if they pass the same final asset QA. No videos or animated frame sequences are required.

- [ ] **DR5-050 — Produce widget images 01–10**
  - Outcome: the first ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 01–10, image-generation prompts/provenance, source masters, optimized widget derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-049
  - Status: `blocked`
  - Acceptance: ten distinct named image files match catalog IDs 01–10; use one built-in image-generation call per distinct asset and record final prompts/provenance; each has a distinct visual idea that reads at one-cell size, with intentional exceptions allowed for numeral legibility and individual palette/frame treatment; no stray text or watermark, safe 1 × 1 crop, and an optimized runtime derivative; a contact sheet proves the concept and collection-level identity work at actual widget size; four approved samples are reused only if they meet the same standards.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: preserve masters separately from optimized runtime resources. Do not generate ten minor color variations.

- [ ] **DR5-051 — Produce widget images 11–20**
  - Outcome: the next ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 11–20, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-050
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 11–20 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–10.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: include ambitious border manipulation and a distinct silhouette for every concept.

- [ ] **DR5-052 — Produce widget images 21–30**
  - Outcome: the third ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 21–30, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-051
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 21–30 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–20.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: include surreal concepts such as the 5 lying down and being abducted by aliens if not already in the earlier batches.

- [ ] **DR5-053 — Produce widget images 31–40**
  - Outcome: the fourth ten catalog concepts become validated, original widget-ready still assets.
  - Scope: concept IDs 31–40, prompts/provenance, source masters, optimized derivatives, and contact-sheet QA only.
  - Model: `gpt-5.6-sol`
  - Model reason: independently reviewable ten-image art batch.
  - Depends on: DR5-052
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 31–40 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, prompt/provenance, and actual-size contact-sheet checks; no concept is visually redundant with IDs 01–30.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: maintain the common composition while allowing the border and 5 to break or move for a stronger visual gag.

- [ ] **DR5-054 — Produce widget images 41–50**
  - Outcome: the final ten catalog concepts complete a validated 50-image widget set.
  - Scope: concept IDs 41–50, prompts/provenance, source masters, optimized derivatives, final 50-image contact sheet and quality pass only.
  - Model: `gpt-5.6-sol`
  - Model reason: final independent art batch plus whole-set visual consistency.
  - Depends on: DR5-053
  - Status: `blocked`
  - Acceptance: ten distinct named files match IDs 41–50 and each passes the DR5-050 one-call-per-asset, concept clarity, intentional identity exceptions, safe-crop, optimization, and provenance checks; a 50-image contact sheet shows no duplicates, unclear ideas, accidental clipping, stray text, or inconsistent dimensions, while deliberate obscured/mirrored 5s and broken/absent borders are judged on artistic merit rather than rejected by rule; all 50 optimized derivatives are cataloged and resource-size impact is measured.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: repair or regenerate weak images before considering the batch complete.

- [ ] **DR5-055 — Integrate the fifty-image widget rotation**
  - Outcome: the widget reliably presents one of the 50 cataloged looks, rotates at the validated battery-conscious cadence, and still opens the app normally.
  - Scope: widget provider/scheduler, runtime artwork catalog/resources, lifecycle and click behavior, accessibility, tests, and widget screenshots only.
  - Model: `gpt-5.6-sol`
  - Model reason: bounded Android runtime integration after artwork and prototype validation.
  - Depends on: DR5-054
  - Status: `blocked`
  - Acceptance: exactly 50 approved IDs are reachable without duplicates or missing-resource fallbacks; rotation is stable across process death, reboot, app update, launcher recreation, and widget add/remove, never demands a precise wall-clock interval, and avoids an always-on background service; the rotating set retains a recognizable DraftingRoom5 identity without requiring a readable 5 in every individual image, and every tile remains one direct tap from normal app launch; resizing and multiple widget instances behave deterministically; API 28–36, battery/update behavior, semantics, and widget preview/screenshot tests pass.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: favor approximately hourly inexact changes per Android widget guidance. An app-open refresh may advance the look if it does not make rotation unexpectedly rapid. Do not modify the launcher icon itself.

- [ ] **DR5-056 — Audit the living widget and its fifty images**
  - Outcome: an independent review confirms the widget's tap, rotation, accessibility, battery behavior, and 50-image visual quality without weakening the app.
  - Scope: Phase 7 diff, native widget/device evidence, asset/contact-sheet inspection, focused and full regression checks, and a review artifact under `docs/reviews/`; fixes stay within Phase 7 scope.
  - Model: `gpt-6-astra`
  - Model reason: cross-version launcher lifecycle and battery review benefits from difficult independent reasoning.
  - Depends on: DR5-055
  - Status: `blocked`
  - Acceptance: review verifies every approved requirement against code/evidence, counts 50 distinct runtime images, inspects collection-level identity and each concept at actual one-cell size without penalizing deliberate obscured/mirrored/absent 5s or broken frames, checks direct app launch, time/update inexactness, widget removal, reboot, app update, multiple instances, accessibility and resource-size effects, and records physical-device checks or their unavailability honestly; all relevant unit, screenshot, lint, and build checks pass; unresolved findings block release.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `NO`
  - Notes: if the platform cannot uphold the approved one-cell/tap-to-open premise, seek user direction rather than disguising a larger or inert widget.

- [ ] **DR5-057 — Release the living widget milestone**
  - Outcome: the reviewed fifty-image widget ships as one coherent tagged release with a verified published APK.
  - Scope: Phase 7 findings, documentation/version defaults, full verification gate, Git commit/push/tag, release workflow, and published APK verification.
  - Model: `gpt-5.6-sol`
  - Model reason: established deterministic release procedure after independent audit.
  - Depends on: DR5-056
  - Status: `blocked`
  - Acceptance: DR5-048–056 and audit findings are complete; README/design status describes the widget and its inexact rotation honestly; default version name/code advance consistently from the actually released Phase 6 version; `testDebugUnitTest lintDebug assembleDebug` and all screenshot/widget checks pass; coherent Phase 7 work is committed and pushed once; the next increasing version tag is pushed, release workflow completes, and the signed APK is published and verified.
  - Executor thread ID: empty
  - Started at: empty
  - Completed at: empty
  - Push: `YES`
  - Notes: never commit credentials, signing material, generated build files, `.gradle-user-home`, or `.tooling`; do not release intermediate Phase 7 tasks separately.
