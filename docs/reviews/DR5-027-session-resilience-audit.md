# DR5-027 guided-session resilience audit

Reviewed the durable reducer, repository, session/completion destinations, Dashboard routing, feedback, current-schema backup and Android transfer allowlists against GuidedSession.md, TechnicalDesign.md and the approved product handoffs. The accumulated DR5-024–026 implementation remains part of the next milestone.

## Findings corrected

- **Backup invalidated every active session.** Creating a backup manager or reading backup status called `load()` on the shared repository. Progress-triggered backup requests consequently revoked the session lease; repeated loads also discarded unknown-boot timer ownership. `ensureLoaded()` now initializes once and gives readers the committed state. Explicit recovery reload/restore still revokes leases, and failed/corrupt data cannot become an exported default document.
- **Failed timer writes could replay missed cues.** The repository now remembers runs needing silent recovery, consumes the catch-up watermark without feedback, and allows only subsequent fresh cues. Runtime observations detect elapsed regression even between persisted timer thresholds. Restarting a finished timer rejects reuse of its run ID.
- **Restored screens could adopt replacement sessions.** The destination saves its bound session ID and uses exact identity on reopening. A missing/restarted session returns a recoverable state rather than creating or retargeting work. Missing records and revoked leases have an exit that does not require another doomed checkpoint.
- **Storage ran on the UI thread.** Session open, mutation, checkpoint, restart and finish run on IO under the repository lock. In-flight commands retain their original session/revision payload; duplicate dispatch is gated. Feedback requires the same foreground owner and canonical committed session after the IO result returns.
- **Completion speech was cancelled by screen cleanup.** The session stops its old speech before navigation; the completion destination consumes a transient one-time completion cue after disposal. History reads and recreation do not replay it, and loss of window focus stops speech.
- **Corrections and progress were incomplete.** Undo is available after any completed set, including partially completed exercises. The progress bar counts sets with Long totals. Completed timed exercises have no inert Start timer action. Large set counts use a textual summary instead of allocating one icon per set. Stable list keys, a focus announcement and merged set-indicator semantics improve accessibility.
- **Dialog and backup edge cases.** Exit/restart intent survives recreation, and failed-save explanations appear inside the dialog. Backup rotation and restore are serialized across manager instances so workers and manual actions cannot race on the same temporary snapshot.
- **Visual verification.** Dedicated native control-panel fixtures expose actions below the initial full-screen viewport at compact/tall/2x-font sizes, including Int.MAX_VALUE set counts. Completion uses dark text on its blue action, and timer helper text reserves consistent space across phases.

## Verification

Focused regressions cover backup reads and unknown-boot ownership, failed-boundary silent catch-up followed by fresh cues, exact-ID restore after restart, both tick/completion lock orders, atomic write failures for progress/undo/restart/finish/restore, fresh timer identities, clock regression between thresholds, and set progress at Int.MAX_VALUE. Existing fake-clock, owner-revocation, process-load, snapshot/edit/cascade, occurrence, current-codec and backup-round-trip tests remain in the gate.

Final gate passed: 177 unit tests in 32 suites, 60 native screenshot comparisons, lintDebug (0 errors, 43 warnings, 1 information), and assembleDebug. Reviewed representative full-session/completion and control-panel renders at compact/tall/2x-font sizes, including every timer phase and extreme set counts. The debug APK is 69,422,578 bytes and contains all 49 catalog WebPs with no design mockups. Final log: `.tooling/astra-session-final-gate.log`; build output is isolated outside OneDrive. No signing material or generated APK/build directory is added to the repository.

## Physical-device release checks

ADB reports no attached device. JVM tests and native host renders cannot establish Android activity/process death, actual TalkBack focus, touch input or TTS timing. Before the DR5-028 release, verify:

1. Start a timed session with backups enabled; complete several sets, let the worker run, and continue without a refresh error.
2. Rotate/recreate while active and while either confirmation is open; kill the process in readiness/running/ready-to-finish states. Verify exact progress, silent catch-up and fixed occurrence dates.
3. Lock/unlock, change focus, background across both deadlines, and reboot. Check unknown-clock recovery, no stale speech/countdown bursts, and one audible completion cue.
4. Exercise failed storage/checkpoint/finish and retry. Confirm errors stay readable and no success navigation precedes persistence.
5. Restore a snapshot with partial/history state, including after Android transfer. Imported timers must be idle and old callbacks unable to alter restored progress.
6. Use TalkBack and keyboard with partial-set undo, focus changes, all remaining exercises and large set counts. Check landscape, IME/system insets and timer-control stability on compact and large-font devices.

No critical/high-severity issue remains identified in the audited code paths after these corrections. The device checks above remain explicit verification limits, not claimed passes. DR5-027 is `Push: NO`; version changes, commit/push, tag and APK publication belong to DR5-028.
