# DraftingRoom5 fitness UI final design review

Status: screen-flow design approved and internally reconciled. The Dashboard, metric details, Settings, customization, recurring Schedule, unified Routines, and routine/editor flows are implemented through the Phase 3 planning milestone. Durable guided-session behavior remains Phase 4 work.

## Outcome

No major user-facing screen-flow decisions remain. The approved system covers:

- Dashboard, measured-five identity, metric detail, Settings, and dashboard customization.
- Recurring Schedule and unified Routines tabs.
- Add-routine type chooser, installed-app picker, linked-app editor, and guided-routine editor.
- Exercise builder with one paired-artwork selection.
- Recurring schedule-item editor.
- Focused guided session, timer states, partial save/resume, and completion.

Individual production artwork choices remain a content-production review. Empty, loading, error, permission, unavailable-app, destructive-confirmation, compact-screen, and large-font treatments are implementation states governed by the approved specs rather than new flows.

## Clean-slate implementation decision

Implement the new model directly. There is no installed user base and no requirement to preserve development data.

- Do not write readers, converters, aliases, optional fields, version branches, or compatibility shims for the old destination/custom-routine schema.
- Use new storage keys/schema and ignore the prior development store. A clean install or cleared app data starts from the new defaults.
- Do not keep old model types or UI paths after their new equivalents are complete.
- Corrupted current-format data may fail safely and reset to current defaults; that is validation, not an old-schema migration path.
- Tests should cover only the current schema, its validation, persistence, and reset behavior.
- Store set count as a positive integer; do not retain the old free-form set-count parser.

## Canonical ownership model

| Data | Owner | Consumers |
| --- | --- | --- |
| Name, routine artwork, execution type | Routine | Dashboard, Schedule, Routines tab, editors |
| Exercise list and paired exercise artwork | Guided routine/exercise | Routine editor, guided session |
| Package/deep link | Linked-app routine | Dashboard launch, linked-app editor |
| Recurring weekdays and ordering | Schedule entry | Schedule tab, dashboard occurrence resolution |
| Completed sets, focused exercise, partial state | Guided session progress | Guided session, dashboard Resume card |
| Completion by actual occurrence/date | Workout history | Dashboard completed state |

The schedule editor never duplicates routine-owned identity or execution fields. Renaming a routine or changing its artwork/app updates every schedule occurrence automatically.

## Cross-screen consistency

- Canvas is deep navy; ivory serif is reserved for editorial hierarchy; sans-serif handles controls and metadata.
- Blue means action/selection, mint means saved/completed/restored, and gold is a restrained drafting accent.
- Secondary screens use Back + title and never show the brand icon.
- Whole cards/rows may be tappable, but accessibility exposes one action even when a visible pill or chevron repeats the affordance.
- New guided work uses `Start`; partial work uses `Resume`; completed work uses `Done`.
- `Custom routine` and `Custom workout` are retired user-facing terms. Use `Guided routine` and `Linked-app routine`.
- Schedules are recurring weekday rules, never a dated calendar week.
- The dashboard may use the actual current date and occurrence history; schedule management never shows calendar dates.
- Timers and set completion are separate. Readiness numerals are deliberately muted; timer completion never completes a set.
- Ordinary early exit saves partial progress. Destructive restart/reset actions require separate confirmation.
- Do not add motivational copy, fabricated external-program details, a page-level workout CTA, per-schedule enable switches, or Week at a glance.

## Artwork review

Two curated catalogs are required:

1. Routine artwork for dashboard cards, schedule rows, and routine headers.
2. Exercise artwork pairs containing a small `list` composition and large `header` composition under one stable ID.

All final art must be generated or licensed as project-owned production assets. Mockup screenshots are references only and may contain incidental commercial markings, inconsistent equipment, or baked backgrounds. Do not crop those pixels into the app. Prefer generic/unbranded equipment unless a branded item is intentional and cleared. Record prompts, generation tool/model/date, and licensing/source notes.

## Highest-risk implementation areas

1. **Clean model replacement:** remove the old destination/custom types rather than adapting them, then bind every screen to routine references.
2. **Referential integrity:** deleting a routine also removes its schedule entries and any partial session after explicit confirmation.
3. **Partial-session durability:** persist completed sets and focus, reconcile elapsed timers after process death, and never infer set completion.
4. **Android app visibility and launch recovery:** use the narrowest supported package-query approach and handle uninstall/deep-link failure without a dead action.
5. **Artwork production:** generate and review every routine asset and every list/header exercise pair at actual phone crops before shipping.
6. **Responsive accessibility:** verify 48 dp targets, TalkBack semantics, accessible reorder alternatives, focus restoration, large fonts, compact phones, and non-color state cues.

## Implementation gate

Before coding, define the clean current-schema Kotlin types and production-asset inventory. Build the native Compose UI from the specs rather than embedding mockup screenshots. Before release, run unit tests, UI/semantics checks, lint, and debug assembly; validate compact and tall phones and inspect every adaptive-icon mask and artwork crop.

## Source handoffs

- [Dashboard.md](Dashboard.md)
- [MetricDetails.md](MetricDetails.md)
- [Settings.md](Settings.md)
- [DashboardCustomization.md](DashboardCustomization.md)
- [SchedulesAndRoutines.md](SchedulesAndRoutines.md)
- [RoutineEditor.md](RoutineEditor.md)
- [GuidedSession.md](GuidedSession.md)
