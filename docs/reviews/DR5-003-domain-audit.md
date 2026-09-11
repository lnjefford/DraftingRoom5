# DR5-003 clean-domain audit

Reviewed 2026-09-10 by gpt-6-astra against TechnicalDesign.md and DesignReview.md.
Scope: DR5-002's current document, domain invariants, recurrence, repository,
backup codec, and existing consumers of those APIs. Existing unrelated visual
and handoff changes were preserved. This is a Push: NO task.

## Repairs

- Replaced the permissive JSON structure scan with a bounded strict grammar
  check. It rejects trailing documents/content, non-JSON parser extensions,
  duplicate keys (including escaped spellings), malformed escapes and excessive
  nesting before Android's JSON parser can discard evidence of corruption.
  Both documents and backup envelopes use it.
- JSON parser and invalid-date failures now become recoverable corruption,
  retaining the original file. Current document and snapshot reads enforce the
  16 MiB bound while reading, and reject malformed UTF-8. AtomicFile backup-only
  recovery is recognized instead of being mistaken for a clean install.
- Fixed validation of partial occurrence IDs, timers on already completed
  exercises, deadline overflow, complete dashboard membership and unknown-card
  visibility types. Duplicate weekdays are rejected. Finite voice rates clamp
  before narrowing to Float.
- Encoding-size failures now return repository validation errors without
  publishing a generation or replacing committed bytes. Restores reject the
  wrong format and clear imported elapsed-clock timers while preserving sets.
- Activity and backup managers/workers now obtain the same process repository,
  preventing independent cached writers from overwriting one another's edits.
  Generation checks serialize conflicting writes. Plan edits enforce routine
  revisions, immutable execution type and acknowledgement for affected saved
  sessions. Routine deletion removes referencing schedules and partials while
  preserving history; plan reset clears partials/history and preserves settings.
- Connected the transitional planner to these commands. Failed writes restore
  the committed UI values and display an error. Completion feedback/navigation
  happens after successful persistence. Corrupt data exposes confirmed reset;
  plan reset copy explicitly identifies history/progress loss.
- Backups cannot export fallback defaults over unreadable live data. Manual
  backup works when automatic scheduling is disabled. Nested duplicate keys and
  negative backup timestamps are rejected; encoded snapshot size is bounded.

## Verification

Focused regression tests cover strict codec inputs, malformed dates and UTF-8,
bounded streams, oversized writes, I/O preservation, concurrent generation
conflicts, revision/acknowledgement gates, cascade/reset, restore timer clearing,
recurrence ordering, historical occurrence identity and snapshot independence.
Final testDebugUnitTest passed: 66 tests in 15 suites, zero failures/errors. The completed-dependency ledger in TODO.md records this result.

Build outputs and project caches were isolated under `.tooling/astra-audit-*`.
The normal output directory was not usable because of an AccessDeniedException
in existing merged resources. Java 17 and downloaded Android build dependencies
were used; the final verification ran offline from that cache. No generated
output or toolchain was staged. No commit, tag or release was performed.

No retired plan/history stores, free-form set parser or compatibility model
readers were found by the source scan. Git whitespace validation passed.

## Integration handoff

DR5-004 should replace the transitional synchronous UI facade with lifecycle
state holders, shared StateFlow collection and IO-dispatched commands as already
specified. Use AppRepository.get(context), and preserve the generation/error
and commit-before-feedback behavior when removing duplicated Compose state.

DR5-015/018 should bind deletion and routine-edit acknowledgements to their
specified confirmation flows; the repository now protects saved sessions.
DR5-023–026 still own the durable session reducer and session UI. The model
fixtures here verify storage invariants, not a completed session experience.
DR5-026 owns the full backup-operation mutex, atomic snapshot rotation and
restore-confirmation generation gate specified in the technical design.

Physical Android checks remain: AtomicFile interruption/recovery, process death,
corruption/reset dialogs, lifecycle state propagation, and backup transport.
No native rendering, instrumentation, lint or release APK was claimed for this
foundation audit; the release milestone retains its full gate.
