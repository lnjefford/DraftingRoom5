# Wear OS companion specification

Status: compact Today hub, guided companion and standalone run capture.

## Product boundary

The watch opens to a compact Today screen with weather, the followed stock with
the greatest absolute percentage movement, and the earliest future game across
followed teams. Stock and game taps open all followed items with matching light
blue Back controls and two horizontal scroll lines. Both touch and rotary
scrolling are supported. A rightward swipe opens Fitness with today's native run
plans and unfinished guided workouts. Active activities take over until finished.
The guided workout loop is:

1. Show today's watch-startable routines, or the active routine when resuming.
2. Start the selected occurrence or resume the active routine.
3. Show one focused exercise, its existing artwork, set position, and structured target.
4. Run the existing 10-second readiness countdown and exercise timer.
5. Complete sets explicitly and advance focus.
6. Finish after every required set is complete.

Routine editing, progression decisions, correction of earlier sets, linked-app routines, history, Health Connect, dashboard customization, backup, updates, and Finance remain on the phone.

For runs, the watch caches the next seven days of scheduled walk/run plans and bounded route geometry. It can choose a route, request GPS permission, record a run in a foreground service, display elapsed time, distance, intervals and the next turn cue, and provide interval haptics without the phone. Its journal and pending result survive process death and a disconnected network. The phone imports the result idempotently into run history and acknowledges it; the watch keeps the pending result until that acknowledgement arrives. Spotify status uses local media sessions after the user enables notification-listener access, and the unstyled logo/status row opens Spotify.

## Visual system

- Deep ink navy canvas and raised navy controls.
- Ivory primary type, cool blue-gray secondary type, electric-blue actions, mint completion, and restrained drafting gold labels.
- DM Serif Display is reserved for routine, exercise, and completion titles; compact sans serif carries controls and time-critical information.
- Today uses Madison lakeshore scenery selected by season and local daypart,
  with weather veils, leaves/petals, rain/snow and restrained summer/winter glints.
  Motion stops in ambient mode, while inactive, or with system animations disabled.
- Fitness uses compact activity cards with native Start actions. Exercise artwork
  appears on active-exercise screens; timers use a calm scenic background.
- A thin perimeter arc communicates overall set progress or the active countdown/timer.
- The 41 mm layout exposes the primary action without requiring a scroll. A second action may remain immediately below it and reachable with normal rotary/touch scrolling. The 45 mm layout exposes both exercise actions.

## Timer and feedback

The watch timer is persisted locally as wall-clock readiness and active deadlines. Reopening the watch reconciles the visible phase from those deadlines. Countdown, start, timer completion, and set completion use watch haptics. Timer completion never completes a set.

The local timer deliberately does not rewrite the phone's elapsed-realtime timer state. Set completion is the durable shared event, and it remains valid whether or not the optional exercise timer was used.

## Synchronization contract

Both APKs use the same application ID and release signature. Communication uses Google Play services' private Wear Data Layer:

- The phone publishes a snapshot at `/draftingroom5/phone/snapshot`, including
  cached exercises and exact occurrence IDs for all today's guided workouts.
  Payloads above 90 KB use a Data Layer Asset, bounded at 16 MiB on receipt;
  a smaller inline current-routine snapshot preserves older-watch compatibility.
- The watch persists commands under `/draftingroom5/watch/command/<UUID>` as urgent data items.
- Data items buffer while devices are disconnected and synchronize after reconnection.
- The phone processes commands serially, publishes its new authoritative snapshot, and deletes a command only after that publication succeeds.
- The phone retains a bounded acknowledgement window. The watch removes acknowledged commands from its local queue and projects any remaining commands over the latest phone snapshot.
- Set commands carry the stable exercise ID and exact next set number. Already-completed set numbers are successful no-ops, making retries idempotent.
- The phone uses the existing session repository, lease, atomic document write, and revision checks. The watch never receives the complete app document, credentials, Health Connect records, Retirement data, or backup contents.

The phone is the durable authority. Offline watch projection provides immediate continuity, while the next phone snapshot resolves authoritative routine/session changes.

## Availability and recovery

- Requires Wear OS 3 (API 30) or newer and Google Play services. Phone setup is needed to cache a run plan, but the watch can record that run without the phone nearby.
- The watch can continue an already-cached guided routine through a temporary disconnect.
- Starting without any cached snapshot requires the phone to publish one first.
- If today's plan changes on the phone, the next synchronized phone snapshot replaces stale base data while retaining unacknowledged watch commands for safe replay.
- No supported workout data is transferred through an external DraftingRoom5 service.

## Verification

- Shared protocol unit tests cover snapshot round-trips, ordered offline projection, duplicate completion, finish gating, malformed commands, and the no-data boundary.
- Compose screenshot references cover Today, exercise, readiness, running timer, set completion, and session completion at 41 mm and 45 mm.
- Repository Commit and Release validation include both phone and watch unit tests, lint, APK assembly, and screenshot comparison.
- The tag-triggered workflow signs and publishes `DraftingRoom5.apk` and `DraftingRoom5-Wear.apk` with the same version name, code, and signing key.
