# Retirement parity contract

These fixtures are authored from scratch with fictional inputs. No real financial record, provider response, credential, address, workbook or screenshot is included. Reference files were read only under `../finance/app` and `../finance/tests`; source hashes pin that inspection because the sibling directory is not a Git checkout.

## Run

Use Python 3.12+ with numpy, openpyxl and pydantic 2. In this workspace the bundled Python has numpy 2.3.5, openpyxl 3.1.5 and pydantic 2.13.5. The finance `.venv` launcher points to a missing interpreter on another machine, so it is not used.

From the DraftingRoom5 root in PowerShell:

```powershell
$parityPython = 'C:/Users/lnjef/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
& $parityPython -B docs/design/retirement-workspace/parity/verify_reference.py --finance ../finance
& $parityPython -B docs/design/retirement-workspace/parity/verify_forecast.py --finance ../finance
& $parityPython -B docs/design/retirement-workspace/parity/test_contract.py
git diff --check
```

The first runner calls the actual reference functions, asserts frozen expectations, stubs RentCast transport in memory, constructs a synthetic OOXML workbook in memory, and replaces Monte Carlo draws with a fixed return tape. An audit hook denies sockets, SQLite connections and personal file access in the finance directory; config/database entry points are also disabled. It does not invoke application startup, seed a real database, open `.env.local`, enumerate personal workbook directories or copy private contents. `-B` prevents bytecode writes in the sibling checkout. Golden values never auto-update.

`source-manifest.json` is SHA-256 by relative Python/SQL source path (including inspected source tests). Source drift fails the run. After a reviewed source change, `--write-source-manifest` explicitly updates **only hashes**, still checks all frozen financial results, and must accompany an explanation of any intentional expectation changes. Do not bless source drift by blindly regenerating goldens.

`test_contract.py` is an executable architectural specification for native corrections, transaction constraints, route parent closure and the current Android backup allowlist. Its SQLite database is in memory, its SQL is a **subset**, and its cell validator runs after a hypothetical OOXML decode. These tests are not evidence of implemented Room, Compose, provider, ZIP parser, lifecycle, encryption or Android backup behavior. The owning Kotlin tasks must port the fixtures and exercise real implementations.

`manual-foundations.json` is the sanitized DR5-069 transfer fixture for strict integer-cent parsing, account/holding classification, property ownership/mortgage equity, normalized Epic import metadata, safe aggregation, and the no-backup decision. It contains no provider identity, credential, private address, workbook bytes, URI, path, or source filename.

## Fixture lanes and transfer obligations

| Fixture section | Source behavior exercised | Kotlin owner / task | Comparison |
| --- | --- | --- | --- |
| money | `app/money.py`; `tests/test_money.py` | Money boundary, DR5-069 | Exact cents; strict native rejection cases are intentional deviations. |
| classification | `integrations/plaid.py::_account_classification`; `tests/test_plaid.py` | AccountClassification, DR5-070 | Exact type/tax strings; unknown subtype result is reference only, native must require review. |
| property | `integrations/rentcast.py`, `models/properties.py`; `tests/test_rentcast.py` | Property provider/repository, DR5-072 | Exact cents/range/count; missing and zero distinct; reject negative price. HALF_UP ownership differs from reference ties-to-even. |
| amortization | `sim/amortization.py`; `tests/test_amortization.py` | MortgageCalculator, DR5-074 | Exact rounded payment/balance/interest fixtures; closed-form agrees with schedule at published cents. Internal values remain unrounded until comparison. |
| federal/Wisconsin | `tax/federal.py`, `wisconsin.py`, `vectorized.py`; `tests/test_tax.py`, `test_tax_vectorized.py` | Versioned tax policies, DR5-074 | Scalar and vector outputs both match frozen cents. These are historical model semantics, not certified current tax advice. |
| ACA | `tax/aca.py`; `tests/test_tax.py` | AcaPolicy, DR5-074 | Exact rounded annual credit; below/exact 400% reference cliff and alternate regime. Model uses 2024 FPL. |
| withdrawal | `sim/withdrawal_strategy.py`; `tests/test_withdrawal_strategy.py` | WithdrawalPolicy, DR5-074 | Exact per-bucket remaining amounts, ordinary income, LTCG, tax-free and unmet spending; annual age boundary and HSA cap. |
| forecast | `sim/engine.py`, `sim/reference.py`; `tests/test_engine.py` | RetirementEngine, DR5-074 | Full deterministic path, fixed-tape paths/percentiles/success and failure despite locked positive assets. |
| timing | `engine::_income_by_tax_kind`, property/Epic projection and annual loop; `tests/test_engine.py` | RetirementEngine, DR5-074 | Closed-range pension/SS timing; owned home liquidation before growth; workbook after-tax liquidation after growth; real-dollar deflation. |
| workbook | `integrations/epic_workbook.py`; `tests/test_epic_workbook.py` | ShareworksImporter, DR5-073 | Actual generated OOXML read by reference; exact normalized cents/counts/date/year; historical volatility within 1e-12 percentage points. Native rejects six invalid cell/template mutations. |
| aggregate | `models/epic_stock.py`, `repositories/properties_repo.py` and approved brief | AssetAggregator, DR5-069/076 | One ordinary asset + vested Epic less loans + owned property equity; never add holdings/wrappers twice. |
| SQL contract | account/snapshot/property repositories, base schema and migrations 002/008/011/012 | RetirementDatabase, DR5-069 | Append-only history, rollback, duplicate operations, stale source revision, dated latest value, integer-cent storage and generation invalidation. |
| routes.json | Approved handoff, current `AppRoute.kt` saver/stack | WorkspaceNavigation, DR5-068 | 24 routes with acyclic default parents and ordinary Back; existing caller stack overrides Library/settings default restoration parents. |

## Numerical rules

Money fields in JSON use integer cents; reference APIs taking dollars are adapted by division at the test boundary and results are rounded using Decimal HALF_UP. Tax schedules are frozen as reference policy IDs, not silently advanced with the calendar. No real-dollar output is reinflated for display.

The deterministic fixture is 1,000 dollars in US equities with a 6.5% real mean for two years, no spending/contributions: 100,000 → 106,500 → 113,423 cents after publication rounding. This checks source behavior against explicit arithmetic.

The sequence fixture has three paths and 100 dollars annual retired spending from a 1,000 dollar Roth balance. Return tapes are `(+10%,-20%)`, `(-20%,+10%)`, and `(-100%,0%)`. Terminals are 700, 670 and 0 dollars; two of three paths succeed. Linear percentiles at terminal are 134/670/694 dollars (p10/p50/p90). This tests drawdown ordering, a failed path, percentile convention and success definition; it is not a statistical Monte Carlo quality/performance claim. Kotlin must accept an injected return tape and use the same path/year/asset ordering before native RNG validation. An equal RNG seed alone is insufficient across languages.

Pure integer results must match exactly. General long-horizon Double calculations in DR5-074 must establish field-specific tolerances from numerical error, with cent rounding at publication, finite intermediate checks and checked Long conversion. Do not widen tolerance to hide a tax deficit or a wrong year. Statistical distribution tests should use sample-size-aware bounds and fixed assumptions; test seed repeatability within Kotlin independently. Failed paths include any unmet spending, not just zero terminal wealth.

## Known gaps are deliberate test boundaries

The reference accepts a required missing E6 as zero; the native contract rejects it. The reference silently falls back to taxable brokerage for unknown Plaid subtype; native requires review. The reference rounds an owned half cent to zero; native rounds to one cent. Native malformed money rejection is stricter. These outputs are separately named so a porter cannot mistake an observed reference defect for required product behavior.

The cell validator covers the demonstrated single-row synthetic template only; it is not a general Shareworks parser. DR5-073 must add real parser tests using generated sanitized archives for cached formulas, absent caches, XML entities, traversal, compression bombs, malformed dates, partial optional history, unknown format versions, cancel/process death and replacement rollback. No native parser should import this Python validator.

The SQL subset does not yet model import active pointers, every property/plan table, provider reconnect state or parallel SQLite writers. DR5-069/070/073 must port transaction invariants into Room integration tests, inject failures after each write, and test competing commits and process recovery. The generation assertion is a publication precondition demonstration; it is not a real background computation cancellation test.

Remaining full-engine validation in DR5-074 includes SS taxation/RMD details, tax/ACA convergence, inflation treatment of nominal mortgage liabilities, negative/overflow paths, cost-basis changes, contribution split priority, exact retirement-date policy, bounded Epic uncertainty, and device memory/runtime limits. DR5-077/078 must validate Android backup/recents/log privacy with synthetic canaries and real lifecycle/process recreation. No current test claims those gates have passed.

DR5-074 now implements these engine lanes in Kotlin, with 14 additional complete-path fixtures and shared seeded asset/Epic/property tapes in `forecast-cases.json` / `forecast-expected.json`. See [the engine handoff](../DR5-074.md) for exact tolerances, runtime evidence and deliberate retained approximations. Tax/ACA retain the explicitly labeled reference policy; the engine measures tax-funding residuals instead of claiming convergence. The final UI and cross-feature device/privacy gates remain with their owners.
