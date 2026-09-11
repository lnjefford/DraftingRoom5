# Metric detail redesign specification

Status: approved visual direction for future implementation. The Weight screen is the reference implementation for the shared metric-detail family.

## Selected reference

- [Approved Weight detail mockup](docs/design/metric-details/weight-detail-selected.png)
- Related dashboard system: [Dashboard.md](Dashboard.md)

The image is a design reference. Build the screen from native Jetpack Compose components; do not ship the screenshot as UI.

## Scope

Use one shared metric-detail layout for:

- Weight
- Body fat
- Lean mass
- Workouts
- Distance

The screen must continue using the existing `DashboardCard`, `HealthMetric`, `HealthTrendPoint`, and `HealthDateRange` data paths. Preserve the existing 1D, 1W, 1M, 3M, and 1Y range choices and the stored range selection.

## Approved design decisions

- Use a conventional secondary app bar: back arrow, screen title, and an optional trailing action. Do not place the measured-five logo between the back arrow and title.
- Keep the measured-five brand mark on the launcher and main dashboard only.
- Make the current value the strongest element on the screen.
- Make the connected trend chart the primary analytical element.
- Use high, average, and low summaries below the chart when the selected range contains numeric points.
- Keep source and sync freshness visible in one quiet footer row.
- Do not use athlete photography, motivational copy, bottom navigation, or a redundant call to action on metric-detail screens.
- Avoid nested cards. The range selector may use a bounded tonal surface; the chart and summaries should read as one continuous editorial composition.

## Layout

Implement the screen as an edge-to-edge `Scaffold` with a transparent top app bar and a vertically scrollable content column.

1. **Top app bar**
   - Back arrow in a 48 dp touch target.
   - Metric title immediately after the navigation region.
   - The mockup shows an overflow icon. Do not ship an inert overflow menu. Either remove it or assign real actions such as opening the metric's Health Connect data/source settings.
2. **Current reading**
   - Tracked uppercase eyebrow such as `CURRENT WEIGHT`.
   - Large ivory editorial-serif value and smaller unit aligned to its baseline.
   - Trend direction, magnitude, and selected comparison period beneath the value.
   - Last-updated time and contributing source app on a quiet metadata line.
3. **Date range selector**
   - Equal-width targets for `1D`, `1W`, `1M`, `3M`, and `1Y`.
   - Minimum control height of 48 dp.
   - Selected range uses blue fill and dark navy text; unselected options remain high-contrast text on the tonal surface.
   - Preserve the current `HealthDateRangeStore` behavior.
4. **Trend chart**
   - Header reflects the selected range, for example `30-DAY TREND`, plus the actual start and end dates.
   - Connected line with visible recorded points and an emphasized final point/value label.
   - Restrained horizontal guides and readable axes.
   - Use a subtle area fade only inside the plot; do not add a decorative page gradient.
   - Calculate a padded y-axis domain from visible data. Do not force a zero baseline for body measurements.
5. **Range summary**
   - Three equal columns: high, average, and low.
   - Derive values from the visible, successfully loaded points only.
   - Use the metric's normal unit and formatting precision.
6. **Source footer**
   - Leading sync/source icon and `Health Connect · {source}`.
   - Trailing freshness text such as `Synced today`.
   - If multiple sources contributed, use `Health Connect · Multiple sources` and expose the full source list in semantics or a real details action.

## Metric-specific behavior

| Metric | Primary value | Chart aggregation | Preferred accent | Improvement direction |
| --- | --- | --- | --- | --- |
| Weight | pounds | individual readings | mint | contextual; do not label all loss as success |
| Body fat | percent | individual readings | blue or mint | contextual |
| Lean mass | pounds | individual readings | gold | contextual |
| Workouts | sessions | daily totals | mint | higher is usually positive, but use neutral wording |
| Distance | miles | daily totals | blue | higher is usually positive, but use neutral wording |

- Body measurements should connect actual recorded values.
- Workouts and distance should aggregate into daily totals before charting.
- Avoid semantic assumptions that a direction is universally good or bad. Announce `up`, `down`, or `steady`, not `improved` or `worsened`.
- For a single point, show the point and reading without drawing a misleading trend line.

## Data and formatting

- Keep the selected range anchored to the newest available point, matching current behavior unless product requirements later change.
- Use locale-aware dates and numbers in production. The English copy in the mockup is illustrative.
- Weight and lean mass: one decimal place and `lb` under the current US-unit behavior.
- Body fat: one decimal place and `%`.
- Distance: one decimal place and `mi`.
- Workouts: whole-number session counts.
- Average values should use the metric's displayed precision.
- The large delta label must use the same range as the selected control; do not hard-code `30 days` when another range is selected.

## Visual tokens

Reuse the dashboard tokens:

| Role | Value |
| --- | --- |
| Canvas | `#07111F` |
| Deep canvas | `#030912` |
| Ivory value/headline | `#F4F0E7` |
| Primary blue | `#76A9FF` |
| Strong blue | `#3F7FE8` |
| Mint | `#4FE0B0` |
| Warm gold | `#FFC66D` |
| Secondary text | approximately `#B9C8DA` |
| Divider/grid | `#263B53` |

- Use the same bundled editorial serif chosen for the dashboard's hero/session titles for major metric values and high/average/low values.
- Keep titles, labels, controls, metadata, and axes in the existing sans-serif family.
- Allow the value row and summary columns to reflow at large font scales rather than clipping.

## Loading and exceptional states

Preserve and visually redesign every existing Health Connect state:

- **Loading:** keep the structure stable; use a small progress treatment near the current reading or chart rather than replacing the whole screen.
- **No data:** show `No data found for this range` in the chart area and retain the range selector so another range can be chosen.
- **Permission missing:** explain which metric cannot be read and provide one clear route to Health Connect permissions.
- **Provider unavailable/update required:** show the existing actionable status without inventing chart data.
- **Stale data:** retain the value, use the gold stale warning, and show its actual last-updated date.
- **Read error:** keep other app navigation usable and offer the existing refresh/retry behavior where available.
- **Partial history:** chart available points and label the actual date span rather than implying a complete range.

Do not show high, average, or low summaries when no numeric data exists.

## Accessibility

- Back, range, optional overflow, and any retry/permission actions require at least 48 × 48 dp touch targets.
- Expose the current reading and trend as one coherent phrase, for example `Current weight, 184.2 pounds, down 2.1 pounds over 30 days`.
- Provide a useful chart summary for screen readers, including first value, last value, direction, high, and low. Individual points may be accessible through focused exploration if implemented without overwhelming navigation.
- Do not communicate selected range, stale state, or trend direction by color alone.
- Maintain readable contrast for chart grid lines without allowing them to compete with the data.
- Support font scaling and portrait screens with reduced height through scrolling.

## Suggested Compose structure

- `MetricDetailScreen`
- `MetricDetailTopBar`
- `CurrentMetricReading`
- `HealthDateRangeSelector`
- `EditorialTrendChart`
- `MetricRangeSummary`
- `MetricSourceFooter`
- `MetricDetailStateContent`

Keep chart-domain calculation, daily aggregation, summary statistics, and display formatting outside the drawing composable so they can be unit tested.

## Verification

- Add preview fixtures for each metric and every exceptional state.
- Test all five range selections and persistence.
- Test high/average/low calculations with empty, single-point, repeated-value, and irregular data.
- Test workout/distance daily aggregation independently from measurement points.
- Verify a compact phone, tall phone, and large font scale.
- Confirm back navigation returns to the dashboard without losing its state.
- Confirm Health Connect permission, stale, source, and error behavior remain intact.

## Acceptance criteria

- All five metric types use one coherent screen system.
- The app bar follows standard secondary-screen hierarchy and contains no brand icon.
- The selected range controls the value delta, chart, date span, and summary statistics consistently.
- No synthetic values are shown when data is absent or unavailable.
- Source and freshness information remain visible.
- The screen is recognizably part of the approved Dashboard design language.

