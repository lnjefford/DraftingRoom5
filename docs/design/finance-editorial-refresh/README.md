# Finance editorial refresh — concept set 01

Status: approved and implemented in native Jetpack Compose.

## Direction

Bring the Finance workspace up to the visual confidence of Fitness without
copying its subject matter. The shared language is oversized ivory serif type,
compact uppercase labels, deliberate negative space, deep tonal surfaces, and
one dominant story per screen.

Finance gets its own motion language: flowing bands describe time, probability,
and composition. The curves are data-bearing rather than ornamental.

## Screens

- `overview.png` — a present-to-retirement trajectory opens into a probability
  range, with age 60 marked as the decision horizon.
- `overview-v2.png` — preferred wording pass; replaces the original hero line
  with the quieter, more direct **Your financial outlook**.
- `forecast.png` — the predictive ribbon becomes the hero: nested percentile
  bands, a median path, and a clear retirement marker.
- `assets.png` — separate asset streams converge into the tracked total, then
  resolve into a compact editorial ledger.

## Proposed visual rules

1. Keep the existing deep evergreen foundation, but reintroduce Fitness blue
   for navigation and selected actions. Reserve gold for meaningful time or
   decision markers.
2. Use large serif type for conclusions and important values; use sans serif
   for labels, controls, and explanatory detail.
3. Prefer fewer, larger compositions over stacks of equally weighted cards.
4. Use a swoop only when it represents a range, trajectory, threshold, or
   convergence of values.
5. Keep every chart legible without color alone and feasible in native Compose.

## Review questions

- Is the evergreen + periwinkle + gold combination the right bridge back to
  Fitness?
- Should the product label remain **Retirement**, or should this workspace use
  the broader **Finance** label shown on the Overview concept?
- Is the serif scale appropriately bold, or should the data visualizations take
  more vertical space?

These images remain concept references. Production UI recreates the approved
system with native Compose components, responsive text layouts, accessible
descriptions, and real forecast data.
