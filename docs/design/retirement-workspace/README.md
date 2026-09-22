# Retirement workspace handoff

Status: implemented, independently audited, and released in v0.27.0. This document is the durable product and navigation handoff for Phase 9. The exploratory HTML mockups under `tmp/` are review aids only; production UI is native Jetpack Compose and preserves the established DraftingRoom5 visual language. Final audit and release evidence is in [DR5-078](../../reviews/DR5-078/README.md) and [DR5-079](../../reviews/DR5-079/README.md).

DR5-067 handoff: [native architecture and deployment decision](ARCHITECTURE.md), [sanitized executable parity contract](parity/README.md), and [validation/status evidence](VALIDATION.md). The user approved phone-only provider calls with user-entered, Keystore-protected credentials on 2026-09-19; no companion or hosted backend is required.

DR5-070 handoff: [linked-account implementation, security boundaries and verification](DR5-070.md). Its historical Push NO/version notes describe the intermediate state before the coherent v0.27.0 release.

DR5-071 handoff: [Accounts UI, navigation, accessibility and validation](DR5-071.md).

DR5-072 handoff: [automatic/manual property values, secure RentCast boundary, UI and validation](DR5-072.md).

DR5-073 handoff: [local Shareworks parsing, atomic replacement, private Epic UI and validation](DR5-073.md).

DR5-074 handoff: [forecast engine, numerical parity, labeled policy assumptions, background publication and validation](DR5-074.md).

DR5-075 handoff: [Forecast, settings, risk, scenarios, accessibility and validation](DR5-075.md).

DR5-076 handoff: [integrated Overview, Assets, Library, reconciliation and validation](DR5-076.md).

DR5-077 handoff: [cross-feature sync, lifecycle, privacy, recovery and validation hardening](DR5-077.md).

## Product direction

- Add Retirement as a first-class workspace beside Fitness.
- Use the existing extensible workspace switcher rather than a growing top navigation bar.
- Leave the Fitness workspace visually and behaviorally unchanged.
- Give Retirement a distinct evergreen theme while retaining DraftingRoom5 typography, density, card geometry, interaction patterns, and accessibility standards.
- Keep the primary Retirement navigation to three tabs: **Overview**, **Forecast**, and **Assets**.
- Put a book icon and settings icon in the Retirement top app bar. The book opens the Retirement library; settings opens Forecast settings directly. There is no general Retirement settings screen.
- Treat the existing `../finance` project as behavioral reference material, not code to embed blindly. Preserve its validated financial rules and tests where they remain applicable, while implementing Android-safe persistence, secrets, background work, and UI.

## Explicit removals and constraints

- No Strategy screen.
- No separate financial-runway section; the retirement-target card links to Forecast.
- No separate Properties overview; properties are a section in Accounts.
- No rental-priorities experience.
- No separate Data sources screen; connection problems appear on the affected account.
- No tax-treatment summary on Accounts. The tax-treatment table remains on Assets and links to Accounts.
- No manually editable Epic stock position, growth, tax, sale, or projection assumptions. Every Epic value comes from the uploaded Shareworks workbook.
- No workspace appearance, refresh, or notification settings page.

## Visual contract

Retirement uses the following approved palette:

| Token | Value |
| --- | --- |
| Background | `#061511` |
| Deep background | `#020b09` |
| Surface | `#0f241d` |
| Raised surface | `#17352b` |
| Border | `#295142` |
| Primary text | `#eff8f3` |
| Secondary text | `#b8cec4` |
| Primary action | `#72d9a8` |
| Highlight | `#b7ec82` |

Use Georgia-style display headings and the app's normal sans-serif body typography. Retirement-specific colors must be scoped to the Retirement workspace so the Fitness theme remains untouched.

## Navigation map

```text
Workspace switcher
└── Retirement
    ├── Overview
    │   ├── Financial map -> Assets
    │   ├── Retirement target -> Forecast
    │   └── Data attention -> affected account
    ├── Forecast
    │   ├── Forecast risk
    │   │   └── Scenario detail
    │   └── Forecast settings
    ├── Assets
    │   ├── Tax-treatment table -> Accounts
    │   └── Accounts
    │       ├── Account detail
    │       │   ├── Update balance
    │       │   ├── Balance history
    │       │   ├── Edit account
    │       │   └── Reconnect account when attention is required
    │       ├── Add asset
    │       │   ├── Connect institution -> Plaid -> Review linked accounts
    │       │   ├── Add account manually
    │       │   └── Find property -> Confirm property
    │       ├── Property detail
    │       │   ├── Edit property
    │       │   └── Property value history
    │       └── Epic stock detail -> Upload Shareworks workbook
    ├── Book icon -> Retirement library
    └── Settings icon -> Forecast settings
```

All detail and task routes must support ordinary back navigation, process recreation, and direct restoration from saved navigation state.

## Screen catalog

| Screen/state | Required content and behavior | Primary source |
| --- | --- | --- |
| Retirement overview | Financial map first; retirement target card with age and modeled success; compact data-health row | Aggregated accounts, Epic import, property equity, forecast result, sync state |
| Forecast | Success rate, Monte Carlo range, retirement-age marker, available-at-retirement breakdown, lifestyle spending, and risk link | Forecast engine plus current plan inputs |
| Assets | Tracked total and a tax-treatment table; link from the table to Accounts | Aggregated current asset values |
| Accounts | No total at top; group every linked/manual account by employer plans, IRAs, health savings, brokerage, cash, and properties | Account repository plus property repository |
| Account detail | Current balance, institution/type/tax/owner, inclusion state, source freshness, holdings when available, actions | Plaid or manual account record and append-only snapshots |
| Account attention | Explain the problem on the affected account and offer reconnect or retry | Provider error state on that account/item |
| Connect institution | Brief Plaid handoff, read-only permissions, manual fallback | Plaid link-token flow through a secure provider boundary |
| Review linked accounts | Select imported accounts and confirm account type and tax treatment before adding | Plaid account metadata and balances |
| Update balance | Add a dated manual snapshot without overwriting history | Manual balance input |
| Balance history | Chronological append-only values and source labels | Value snapshots |
| Edit account | Editable local classification/name/owner/inclusion fields; provider identity remains read-only | Account record |
| Find property | Address lookup with manual fallback | Property lookup/provider boundary |
| Confirm property | Confirm address/facts/value range, enter mortgage balance, show calculated equity | RentCast estimate plus manual mortgage balance |
| Property detail | Current estimate, range, comparables/freshness, mortgage, equity, history, refresh/edit actions | RentCast plus local property/mortgage data |
| Edit property | Editable local property/mortgage fields and automatic-value behavior | Property repository; provider estimate is not directly edited |
| Property value history | Provider and manual snapshots, dates, and changes | Append-only property snapshots |
| Epic stock detail | Current workbook-derived position, breakdown, loans, vesting, and at-retirement values; upload action only | Latest accepted Shareworks workbook import |
| Upload Epic stock | File picker, validation, import summary, replacement confirmation, and import metadata | User-selected `.xlsm` workbook |
| Forecast settings | Inline editable age/date, spending, inflation, market, tax/ACA, Social Security, pension, contribution, and home assumptions | Local plan settings; Epic assumptions are excluded |
| Forecast risk | Explain failure timing and causes; list focused stress scenarios | Forecast distribution and derived diagnostics |
| Scenario detail | Compare one scenario with the base plan and optionally apply the changed setting | Forecast engine with an isolated settings delta |
| Retirement library | Curated government-first links and a private checklist; no document storage | Bundled curated metadata plus local checklist state |

## Data ownership and refresh map

| Data | Source of truth | Refresh/write rule |
| --- | --- | --- |
| Account balances and holdings | Plaid for connected accounts; local snapshots for manual accounts | Read-only provider sync; append snapshots; never overwrite history |
| Account classification, owner, display name, forecast inclusion | DraftingRoom5 | User-editable locally after provider import |
| Epic stock | Uploaded Shareworks workbook | Replace only after a fully validated import; retain import metadata; no manual Epic fields |
| Home market value, range, and comparable count | RentCast | Automatic weekly refresh plus explicit refresh; preserve every accepted valuation snapshot |
| Mortgage balance and property ownership | DraftingRoom5 | User-editable local values |
| Forecast settings | DraftingRoom5 | Inline edits with validation and explicit save/apply behavior |
| Forecast output | Deterministic and Monte Carlo engine | Recompute from a coherent input snapshot; never mix generations |
| Research library | Bundled curated links | Updated only with an app release; record a review date |
| Library checklist | DraftingRoom5 | Local-only checklist state; do not store private documents |

## Behavioral parity to preserve from `../finance`

- Store money as integer cents; do not use floating-point dollars for persisted values.
- Keep balance and property valuation snapshots append-only.
- Run projections in real, inflation-adjusted dollars.
- Preserve federal ordinary-income and long-term-capital-gain treatment, Wisconsin rules, ACA treatment, Social Security/pension timing, mortgage amortization, sequence-of-returns behavior, and withdrawal ordering only where covered by tests and still selected by this product direction.
- Port the reference tests for money, tax, ACA, amortization, engine behavior, Plaid classification, RentCast parsing, and Shareworks workbook parsing into deterministic Kotlin tests or golden fixtures.
- Strategy UI is removed even if strategy logic remains an internal forecast dependency.

## Security and platform boundary

Plaid and RentCast credentials are entered privately on the phone and stored encrypted using an Android Keystore key, as approved by the user on 2026-09-19. Native adapters call the providers directly; no companion or custom backend is required. Credentials must never be bundled in the APK, committed, logged or backed up. This personal-device choice departs from provider server-side guidance and accepts transient plaintext exposure during requests; the complete vault, background access, recovery and validation contract is in ARCHITECTURE.md, Decision D1. A concrete provider restriction preventing this flow must be reported before changing the approved phone-only deployment.

Shareworks uploads contain sensitive financial data. Parse the selected file with least privilege, avoid retaining the original workbook unless explicitly required, persist only validated derived values and import metadata, and exclude sensitive content from logs and screenshots.

## Cross-cutting acceptance

- Fitness behavior, theme, screenshots, and navigation remain unchanged except for the shared extensible workspace switcher.
- Every Retirement screen supports compact, tall, landscape, and large-text layouts; TalkBack order and labels are meaningful; touch targets meet the app standard.
- Background refresh respects Android scheduling and battery constraints and reports stale/error state honestly.
- Offline mode shows the last accepted local snapshot and its age; it never fabricates freshness.
- Import/sync failures are atomic: previously accepted data remains usable and visible.
- No credentials, access tokens, API secrets, raw workbook contents, or private financial values appear in checked-in fixtures or screenshot references.
- Phase 9 is committed, pushed, tagged, and released only after the independent audit passes.
