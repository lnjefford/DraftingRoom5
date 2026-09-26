# Wear OS companion specification

Status: implemented for the v0.28.0 release.

## Product boundary

The watch app is a wrist-first controller for today's guided workout, not a copy of the phone dashboard. It owns the glanceable physical-workout loop:

1. Show the first resumable or scheduled guided routine for today.
2. Start or resume that routine.
3. Show one focused exercise, its existing artwork, set position, and structured target.
4. Run the existing 10-second readiness countdown and exercise timer.
5. Complete sets explicitly and advance focus.
6. Finish after every required set is complete.

Routine editing, progression decisions, correction of earlier sets, linked-app routines, history, Health Connect, dashboard customization, backup, updates, and Finance remain on the phone.

## Visual system

- Deep ink navy canvas and raised navy controls.
- Ivory primary type, cool blue-gray secondary type, electric-blue actions, mint completion, and restrained drafting gold labels.
- DM Serif Display is reserved for routine, exercise, and completion titles; compact sans serif carries controls and time-critical information.
- Exercise artwork appears on Today and active-exercise screens. Countdown and running-timer screens remain image-free for glanceability.
- A thin perimeter arc communicates overall set progress or the active countdown/timer.
- The 41 mm layout exposes the primary action without requiring a scroll. A second action may remain immediately below it and reachable with normal rotary/touch scrolling. The 45 mm layout exposes both exercise actions.

## Timer and feedback

The watch timer is persisted locally as wall-clock readiness and active deadlines. Reopening the watch reconciles the visible phase from those deadlines. Countdown, start, timer completion, and set completion use watch haptics. Timer completion never completes a set.

The local timer deliberately does not rewrite the phone's elapsed-realtime timer state. Set completion is the durable shared event, and it remains valid whether or not the optional exercise timer was used.

## Synchronization contract

Both APKs use the same application ID and release signature. Communication uses Google Play services' private Wear Data Layer:

- The phone publishes a compact snapshot at `/draftingroom5/phone/snapshot`.
- The watch persists commands under `/draftingroom5/watch/command/<UUID>` as urgent data items.
- Data items buffer while devices are disconnected and synchronize after reconnection.
- The phone processes commands serially, publishes its new authoritative snapshot, and deletes a command only after that publication succeeds.
- The phone retains a bounded acknowledgement window. The watch removes acknowledged commands from its local queue and projects any remaining commands over the latest phone snapshot.
- Set commands carry the stable exercise ID and exact next set number. Already-completed set numbers are successful no-ops, making retries idempotent.
- The phone uses the existing session repository, lease, atomic document write, and revision checks. The watch never receives the complete app document, credentials, Health Connect records, Retirement data, or backup contents.

The phone is the durable authority. Offline watch projection provides immediate continuity, while the next phone snapshot resolves authoritative routine/session changes.

## Availability and recovery

- Requires Wear OS 3 (API 30) or newer, an Android-paired watch, Google Play services, and the matching phone app.
- The watch can continue an already-cached guided routine through a temporary disconnect.
- Starting without any cached snapshot requires the phone to publish one first.
- If today's plan changes on the phone, the next synchronized phone snapshot replaces stale base data while retaining unacknowledged watch commands for safe replay.
- No supported workout data is transferred through an external DraftingRoom5 service.

## Verification

- Shared protocol unit tests cover snapshot round-trips, ordered offline projection, duplicate completion, finish gating, malformed commands, and the no-data boundary.
- Compose screenshot references cover Today, exercise, readiness, running timer, set completion, and session completion at 41 mm and 45 mm.
- Repository Commit and Release validation include both phone and watch unit tests, lint, APK assembly, and screenshot comparison.
- The tag-triggered workflow signs and publishes `DraftingRoom5.apk` and `DraftingRoom5-Wear.apk` with the same version name, code, and signing key.
