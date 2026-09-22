# Sanitized Shareworks archives

`values.xlsm` and `cached.xlsm` contain only hand-authored fictional data from `../cases.json`. No private workbook was copied or modified. The first stores literal results; the second wraps numeric cells in constant formulas with those same saved results. Both identify as macro-enabled OOXML but contain no VBA payload. They are valid parser/reference fixtures, not executable macros or a financial projection engine.

Recreate byte-for-byte with `python ../generate_shareworks_fixtures.py` from this directory (or invoke that script from the repository root). ZIP timestamps and part ordering are fixed. `verify_reference.py` compares both files, field for field, against the pinned Python parser's normalized synthetic workbook output. Kotlin tests independently assert every mapped total, breakdown, assumption and annual field plus volatility within 1e-12 percentage points.

Negative tests mutate these archives only in memory. Android instrumentation includes this directory as **test-only** assets. Production APK assets never contain these fixtures. Screenshot previews use equivalent handwritten fictional models and never open user-selected workbooks.
