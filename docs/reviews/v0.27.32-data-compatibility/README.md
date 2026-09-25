# v0.27.32 saved-data compatibility repair

v0.27.31 changed the Fitness exercise and progression JSON shape while retaining
the old unversioned document format identifier. Its strict decoder therefore
rejected valid data written by v0.27.30 and showed **App data could not be
loaded** before the repository could recover or rewrite it.

v0.27.32 introduces explicit app-document schema version 2 and release-specific,
strict upgrade readers for v0.25.1, v0.27.30, and unversioned v0.27.31 data. The
upgrade preserves plans, preferences, schedules, partial sessions, history,
occurrence exceptions, custom planned steps, inserted exercises, and recoverable
progression receipts. Old structured duration and timer values become the new
duration; recognizable rep targets become structured reps; noncanonical target
text is retained in notes. Legacy decimal weights are deterministically rounded
to the nearest valid 5 lb step. Removed automatic rules retain their current
targets, while existing automatic receipts are converted into equivalent manual
receipts so Undo remains available.

The repository decodes and fully validates the upgraded domain object before
atomically replacing the stored JSON. Recovery-backup envelopes use the same
upgrade path. Unknown future schema versions, malformed data, and mixed old/new
exercise shapes still fail closed.

Immutable serialized fixtures now live under
`app/src/test/resources/compatibility/`. The validation guide requires every
future schema-changing release to add its fixture and retain all earlier readers.
## Validation

The canonical `Fast` compatibility gate passes for the codec, repository,
backup, and progression suites. `./tools/verify.ps1 -Tier Commit` and
`./tools/verify.ps1 -Tier Release` both pass on the stable source:

- **460 JVM tests across 63 suites**, zero failures or errors;
- **471 screenshot comparisons**, zero failures or errors and no reference updates;
- lint with zero errors;
- debug APK assembly; and
- `git diff --check`.

Published APK and workflow evidence is appended during delivery.
