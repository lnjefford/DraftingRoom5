# DraftingRoom5 clean-app technical design

Status: DR5-001 design complete; implementation is queued. This document specifies the target, not capabilities already delivered. Reviewed against the working tree on 2026-09-10 UTC, including its uncommitted visual work. No production code is changed by this task.

## Authority and implementation boundaries

The requirements are [DesignReview.md](DesignReview.md), [Dashboard.md](Dashboard.md), [MetricDetails.md](MetricDetails.md), [Settings.md](Settings.md), [DashboardCustomization.md](DashboardCustomization.md), [SchedulesAndRoutines.md](SchedulesAndRoutines.md), [RoutineEditor.md](RoutineEditor.md), and [GuidedSession.md](GuidedSession.md). Their referenced PNGs remain visual references under `docs/design`; none becomes a screen or a cropped production asset.

Use the existing single Android app module, Kotlin, native Compose/Material 3, coroutines, Health Connect, and WorkManager. Keep application ID `dev.draftingroom5`, minimum SDK 28, and the existing SDK/build configuration until a separate justified change. Add lifecycle ViewModel/SavedStateHandle and Compose lifecycle/test dependencies at the already selected lifecycle/Compose versions when needed. Do not introduce Hilt, Room, a backend, accounts, or another navigation framework for this app.

This is a clean replacement. No old-store reads, converters, aliases, missing-old-field defaults, or schema-version dispatch are permitted. Current-data validation and intentional artwork/layout normalization are defined below. The earlier handoffs' references to existing stores mean preserving their behavior and capabilities; the later clean-slate decision governs persistence. In particular, customization preserves the user's current-format order and visibility, without reading the retired layout key.

The queue's `Push: NO` tasks accumulate locally. Full tests, lint, debug assembly, version bump, commit/push, increasing tag, successful tag workflow, and published signed `DraftingRoom5.apk` belong to `Push: YES` milestones. Never release an intermediate edit. Preserve unrelated work and never stage build output, `.tooling`, `.gradle-user-home`, signing material, or credentials.

## Package and dependency map

All package paths below are relative to `app/src/main/java/dev/draftingroom5/`. Keep internal visibility except Android entry points. Pure domain code must not import Android, Compose, or JSON classes.

| Package / files | Responsibility and dependencies |
| --- | --- |
| `MainActivity.kt`, `DraftingRoom5Application.kt`, `app/AppContainer.kt` | Activity sets edge-to-edge content and registers activity-result launchers. Application creates one lazy process-scoped container shared with WorkManager; no Activity references in it. |
| `app/AppRoot.kt`, `AppStateHolder.kt`, `AppRoute.kt` | Saved route stack, app initialization, foreground/focus signals and navigation effects; observe repositories, never perform JSON or network work in composition. |
| `domain/TrainingPlan.kt`, `Defaults.kt`, `PlanMutations.kt`, `Recurrence.kt`, `Validation.kt` | Routine/exercise/schedule identity, pure validation, defaults and ordered-list operations. |
| `domain/GuidedSession.kt`, `SessionReducer.kt`, `Occurrence.kt` | Durable session/snapshot/history types, clock-independent transitions, occurrence keys and derived progress. |
| `data/AppDocument.kt`, `AppDocumentCodec.kt`, `AppDocumentStore.kt`, `AppRepository.kt` | One current-format document, one serialized writer, validation, atomic disk persistence and StateFlow. All plan/session/preference changes go through this repository. |
| `data/DashboardLayout.kt`, `HealthDateRange.kt` | Preserve the six card identities, range identities and pure transformations. Store facades delegate to AppRepository rather than writing independent preferences. |
| `health/HealthModels.kt`, `HealthRepository.kt`, `HealthReadSupport.kt`, `HealthMetricSupport.kt`, `HealthTrendSupport.kt` | Health Connect availability/permission/read states, paging, numeric samples, aggregation/formatting. Depends on Android provider adapter; never on training history for external measurements. |
| `platform/LinkedAppLauncher.kt`, `InstalledAppsRepository.kt`, `SessionClock.kt` | Package visibility, safe intents, launch results, app labels/icons and injectable boot/elapsed/wall clock. |
| `feedback/VoiceAnnouncements.kt`, `HapticFeedback.kt`, `SessionFeedbackController.kt` | TTS lifecycle, system-aware haptics, durable event identifiers and foreground-only cue dispatch. Settings come from AppRepository. |
| `backup/BackupRepository.kt`, `BackupCodec.kt`, `AutomaticBackupWorker.kt` | Current-format offline snapshots, rotation, restore, status and work scheduling; use the same AppRepository instance as UI. |
| `updates/AppUpdateRepository.kt`, `AppUpdateCheckWorker.kt`, `UpdateInstaller.kt` | Keep verified GitHub update functionality and local status, separately from app-owned backup data. |
| `ui/theme/*`, `ui/components/*`, `ui/artwork/*` | Tokens, measured-five vectors, reusable editorial controls, catalog resolution and accessible reorder mechanics. No business persistence. |
| `ui/dashboard/*`, `ui/metrics/*`, `ui/settings/*`, `ui/customization/*` | Stateless screen composables plus focused state holders / preview fixtures. |
| `ui/planning/*`, `ui/routines/*`, `ui/session/*` | Plan tabs, dedicated editors/pickers, focused session/timer/completion screens and their state holders. |
| `PermissionsRationaleActivity.kt` | Keep both Android rationale entry points; show scrollable privacy content using shared theme. |

Use manual constructor injection with interfaces only at real test boundaries (document storage, clock, provider queries, launcher, feedback). A `ViewModel` owns each destination state holder; factories receive the container and SavedStateHandle. Composables receive immutable UI state and event callbacks. Derive UI state by combining repository flows with saved UI selection, using `collectAsStateWithLifecycle`. Do not duplicate mutable plans across ViewModels.

## Canonical entities and invariants

Names here are concrete implementation names. IDs are opaque nonblank strings of at most 128 characters, generated once with UUID for new entities and never derived from a mutable name, position, package, or date. Default IDs use the explicit constants below. Duplicating an entity, if later supported, must create new IDs. IDs are unique in each entity namespace; exercise IDs are unique within a routine and are addressed by `(routineId, exerciseId)`.

| Type | Exact stored fields / rules |
| --- | --- |
| `TrainingPlan` | Ordered `routines: List<Routine>`, ordered `schedule: List<ScheduleEntry>`. Array positions are authoritative ordering; do not also store an integer rank. |
| `RoutineExecution` | Exactly `GUIDED`, `LINKED_APP`. Type chosen at creation; existing type cannot be changed by the approved editor. |
| `Routine` | `id`, `revision: Long >= 1`, trimmed nonblank `name`, required `artworkId`, `execution`, `exercises: List<Exercise>`, `appLink: AppLink?`. Every successful substantive edit increments revision once; no-op edits do not. |
| `Exercise` | `id`, trimmed nonblank `name`, `notes` (may be empty), `setCount: Int > 0`, trimmed nonblank `target`, `timerSeconds: Int?` (null or positive), required `artworkId`. Names/targets are display content, never parsed into tracking fields. |
| `AppLink` | Nonblank `packageName`, nullable `deepLink`. No persisted app label/icon, activity component, authorization state, or store URL. New app selection uses a package and null deep link. |
| `ScheduleEntry` | `id`, nonblank `routineId` referencing a live routine, nonempty `days: Set<DayOfWeek>`. No title, subtitle, artwork, type, app, single-day anchor, enabled switch, dates or repeat enum. |
| `OccurrenceKey` | Immutable `scheduleEntryId`, `scheduledDate: LocalDate`. It identifies an instance of a recurring rule, not its completion timestamp. Its schedule ID is historical origin, not a foreign key that must survive deletion. |
| `RoutineSnapshot` | A complete guided `Routine` copied on start, including revision and ordered exercises. Immutable throughout that session. Snapshot duplication is intentional history/session evidence; schedule records never duplicate identity. |
| `GuidedSession` | `id`, `occurrence`, `routineId` (live routine required), `snapshot`, `focusedExerciseId`, `completedSets` for every snapshot exercise, `timer`, `startedAtMillis`, `updatedAtMillis`, `eventRevision`. See session contract below. |
| `WorkoutHistoryEntry` | `id` (completed session ID), `occurrence`, `snapshot`, `startedAtMillis`, `completedAtMillis`. All required snapshot sets were completed. Immutable, no live routine/schedule foreign key; never used to fabricate Health Connect records. |

Guided routines require at least one valid exercise and `appLink = null`. Linked routines require a valid app link and an empty exercise list. New guided drafts may be empty in UI, but are not saved or schedulable until an exercise is saved into the draft. Deleting the last exercise from a saved guided routine is rejected with an explanation; offer Delete routine for removing the routine. Routine/exercise names may coincide; identity must not.

Validation bounds for user content: name at most 200 characters, target at most 500, notes at most 4,000, package at most 255, deep link at most 2,048. Set count and timer input must fit a positive Kotlin Int; reject overflow rather than truncate. Compute total sets and milliseconds with Long. Reject nonfinite numeric preference/health values. Empty text is allowed only where explicitly stated. URI security validation is in the launch section. Unknown nonblank artwork IDs remain valid and resolve to generic artwork; missing required artwork fields are invalid current data.

`AppPreferences` owns `DashboardLayout`, `HealthDateRange`, `hapticsEnabled`, `VoiceAnnouncementSettings`, and `automaticBackupsEnabled`. Preserve `DashboardCard` identifiers `TODAY`, `WEIGHT`, `BODY_FAT`, `DISTANCE`, `LEAN_MASS`, `WORKOUTS`, and range identifiers `DAY`, `WEEK`, `MONTH`, `THREE_MONTHS`, `YEAR`. A layout contains each known card once, with at least one visible.

### Deterministic clean defaults

Default routine/schedule IDs are new stable constants, not imports of development records. No built-in/custom distinction is persisted or displayed. This keeps the existing weekly workload without inventing external program details:

| Routine ID | Name / execution / artwork | Configuration |
| --- | --- | --- |
| `routine-strength` | Fitbod workout / LINKED_APP / `dumbbell` | `com.fitbod.fitbod`, no deep link |
| `routine-running` | JustRun run / LINKED_APP / `running_shoe` | `com.jupli.run`, no deep link |
| `routine-forearm` | Forearm & Grip Conditioning / GUIDED / `grip_trainer` | Seven exercises below |

All default revisions are 1. Ordered schedule: `schedule-strength` references strength on MONDAY/TUESDAY/THURSDAY; `schedule-running` references running on MONDAY/WEDNESDAY/FRIDAY; `schedule-forearm` references forearm on SATURDAY. Thus there are three recurring entries and seven weekly occurrences; Monday has two, Sunday zero. Settings reports **3 recurring entries**, not seven entities. Do not invent named Leg/Push/Pull days from artwork examples.

| Exercise ID | Name | Sets | Target | Timer seconds | Artwork ID / notes |
| --- | --- | --- | --- | --- | --- |
| `exercise-dead-hang` | Thick-Bar Dead Hangs | 3 | 20 sec | 20 | `dead_hang`; Pull-up bar + thick adapter |
| `exercise-farmers-walk` | Dumbbell Farmer's Walks | 3 | 30 sec | 30 | `farmers_walk`; Start 15-20 lb/hand |
| `exercise-grip-hold` | Grip Holds | 4 | 20 sec | 20 | `grip_hold`; Pinch & crush |
| `exercise-wrist-curl` | Seated Dumbbell Wrist Curls | 3 | 12-15 reps | null | `wrist_curl`; Palms up, start 5-10 lb |
| `exercise-reverse-wrist-curl` | Seated Dumbbell Reverse Wrist Curls | 3 | 12-15 reps | null | `reverse_wrist_curl`; Palms down, start 5-10 lb |
| `exercise-finger-extension` | Finger Extensor Band Extensions | 3 | 15-20 reps | null | `finger_extension`; empty notes |
| `exercise-wrist-rotation` | Wrist Rotations | 2 | 10-12 / side | null | `wrist_rotation`; Pronation / supination |

The former `2-3 sets` default becomes explicitly 3; no parser survives. The generic grip name avoids implying branded production artwork. Preserve owned instructional targets/notes, without claiming exercise prescriptions were newly validated by this design.

Default dashboard: TODAY, WEIGHT, BODY_FAT, DISTANCE, LEAN_MASS visible; WORKOUTS hidden. This is the default constructor's order; saved current-format layouts always win. Range MONTH; haptics on; voice on at 1.0 (finite rate clamped to existing 0.75–1.5); automatic backups on. Plan reset affects routines/schedules/partials/history, while dashboard reset affects only layout. Neither resets health permissions or other preferences.

### Recurrence, ordering and occurrence resolution

`forDay(day)` filters schedule array order by membership in `days`. Counts use that identical function. Repeat shortcuts are editor-only: Weekly selects the parent selected weekday (or earliest currently selected day on edit); Weekdays selects Monday–Friday; Every day selects all seven; Custom retains the current selection and permits individual toggles. Manual toggles may temporarily produce no days, disabling Save. Infer the displayed preset from the set; never silently repopulate empty membership on save.

On a selected weekday, reorder only the filtered IDs and write them back into the same occupied slots of the global schedule array. Unshown entries retain their slots. Example: global `[A(mon), X(tue), B(mon,tue)]`, moving B above A on Monday gives `[B, X, A]`. B also precedes X on Tuesday because entry order is global. Explain in reorder help that order applies wherever an entry repeats. No per-day duplicate ordering map. Cancelled drag makes no write; accessibility moves use the same transformation. Routine/exercise ordering likewise uses stable IDs, with expected revision checks rather than stale indices.

Dashboard dates are the Monday–Sunday week containing the actual local today. Initial selection is today, persisted through recreation. On a new day, follow today if the previous selection was today; otherwise preserve the explicit selection while it remains in that week. Observe foreground resume, midnight, and timezone changes through an injected clock/date source. Plan management shows only weekday names/counts.

Resolve each selected-date occurrence by joining schedule -> routine, then `(scheduleEntryId, selectedDate)` -> partial/history. History yields Done; a partial yields Resume; otherwise Start. Start on a selected past/future date explicitly works that selected occurrence; actual timestamps still record when work happens. Crossing midnight or changing timezone never reassigns a started occurrence. Two schedule entries for the same routine on one date are distinct occurrences; next week's same entry is distinct too.

There is at most one partial per occurrence, but multiple independent occurrences may have partials. Also show a compact Saved sessions group inside TODAY for incomplete sessions not already represented by the selected date's cards (including older dates or a removed recurrence). Label their originating date and use Resume. This prevents losing access when time passes or a recurrence is removed. No unscheduled Start action is added to routine management.

Deleting a schedule removes only its recurrence. Partials remain resumable through Saved sessions, linked to their still-live routine and historical occurrence origin. Changing a schedule's routine while it has partials requires a confirmation that saved sessions retain the prior routine snapshot; the associated card shows that saved session until completion. Its current management row always resolves the new routine. Routine deletion removes that routine, referencing recurrences and all partials for that routine in one transaction. Completed history survives routine/schedule deletion as self-contained evidence. Reset plan clears history too so reused default IDs cannot make a reset plan appear completed; its confirmation explicitly names this loss.

## Current JSON document and persistence

One UTF-8 file: `filesDir/training-current/document.json`, managed with Android `AtomicFile`. Do not use any old preference file as an initialization signal. The sole format discriminator is `format = "draftingroom5.current"`. There is one decoder and no version switch. All shown fields are required; nullable fields must be explicitly null. Empty arrays/maps are valid only where the domain permits them.

```json
{
  "format": "draftingroom5.current",
  "generation": 1,
  "plan": {
    "routines": [{
      "id": "routine-example", "revision": 1, "name": "Grip practice",
      "artworkId": "grip_trainer", "execution": "GUIDED", "appLink": null,
      "exercises": [{
        "id": "exercise-example", "name": "Grip hold", "notes": "",
        "setCount": 3, "target": "20 sec", "timerSeconds": 20,
        "artworkId": "grip_hold"
      }]
    }],
    "schedule": [{
      "id": "schedule-example", "routineId": "routine-example", "days": ["SATURDAY"]
    }]
  },
  "preferences": {
    "dashboardLayout": {"cards": [
      {"card": "TODAY", "visible": true},
      {"card": "WEIGHT", "visible": true},
      {"card": "BODY_FAT", "visible": true},
      {"card": "DISTANCE", "visible": true},
      {"card": "LEAN_MASS", "visible": true},
      {"card": "WORKOUTS", "visible": false}
    ]},
    "healthDateRange": "MONTH", "hapticsEnabled": true,
    "voice": {"enabled": true, "rate": 1.0}, "automaticBackupsEnabled": true
  },
  "partialSessions": [],
  "history": []
}
```

This is an executable-shaped example fixture, not the entire default plan. The linked branch uses `"execution":"LINKED_APP", "exercises":[], "appLink":{"packageName":"com.fitbod.fitbod","deepLink":null}` with the same routine identity fields. Weekdays encode as uppercase enum names in ISO weekday order. Dates encode `YYYY-MM-DD`; audit timestamps are epoch milliseconds. Resource integers, intents, Health Connect measurements/permissions and installed-app inventories never serialize.

Nonempty `partialSessions` entries encode all GuidedSession fields listed above. `snapshot` has the exact routine object shape, restricted to GUIDED; its ID must equal `routineId`, its revision cannot exceed the live routine revision, and the live routine must still be GUIDED. `occurrence` is `{"scheduleEntryId":"schedule-example","scheduledDate":"2026-09-12"}`. `completedSets` maps each snapshot exercise ID to an integer in `[0,setCount]`; no omitted, extra, duplicate or negative progress. `eventRevision` is a nonnegative Long. `timer` is either `{"phase":"IDLE"}` or the full active/finished timer object below. History entries have exactly the WorkoutHistoryEntry fields; completed-set maps are unnecessary because all sets are complete by construction. No partial and history entry may share an occurrence or session ID, and both arrays independently require unique occurrences and session IDs.

Active/finished timer object: `phase` is READY/RUNNING/FINISHED; `runId`, `exerciseId`, `setNumber` (1-based), `bootCount` (nullable when unavailable), `readyDeadlineElapsedMillis`, `activeDeadlineElapsedMillis`, `lastObservedElapsedMillis`, and `lastHandledCueOrdinal`. All clock fields are nonnegative Longs, active deadline equals readiness deadline plus the snapshot duration in milliseconds, and the timer belongs to the focused exercise's next uncompleted set. `setNumber = completedSets[exerciseId] + 1`. IDLE has no live run fields. FINISHED keeps run identity/deadlines for safe replay suppression until explicit restart, cancel, focus change or set completion.

Use `org.json` initially, with strict typed accessors that reject coercion (e.g. a string `"3"` or fractional number is not an integer set count). Reject duplicate object keys, duplicate entity IDs, unexpected fields, wrong discriminator, invalid enums/dates, impossible timer/progress state and dangling live references before publishing data. Cap input at 16 MiB and structural nesting at 32 to bound current snapshot reads. If encoding a mutation exceeds the limit, retain the previous document and show a storage-size error; never silently truncate history. A missing whole document installs defaults; an unreadable file due to I/O failure is **not** treated as malformed content.

Normalize only explicit current-format exceptions: unknown artwork resolves visually to generic without rewriting identity; dashboard layout keeps the first known occurrence of each card, appends missing known cards in default order, drops unknown card IDs, and restores default visibility if none are visible. Persist the normalized current document once. Missing required preference containers or malformed field types remain corruption. A finite voice rate outside the slider range is clamped; NaN/infinity is rejected. These are current-data rules, not support for an older format.

`AppDocumentStore` does open/read/decode and atomic writes on Dispatchers.IO. `AppRepository` exposes `StateFlow<LoadState<AppDocument>>` and typed suspend commands returning success, validation error, conflict or I/O error. One process-wide Mutex covers read-modify-validate-write-publish for UI, backup restore and workers. AtomicFile alone does not supply locking; the repository owns serialization. Use `startWrite` / `finishWrite` and `failWrite` on failure. Publish the new generation only after successful durable write. [Android AtomicFile reference](https://developer.android.com/reference/android/util/AtomicFile)

Commands are ID-based, not `save(entirePossiblyStalePlan)`: create/edit routine, add/edit/remove/reorder schedule, edit/reorder exercise, reset plan, update preferences, start/resume/reduce/complete session, restore document. Each edit supplies expected entity revision or document generation as appropriate. Reject conflicts, reload state, and let the user retry; never overwrite unrelated changes. Check any destructive confirmation again against current counts/revisions. A routine edit with partials requires acknowledgement and creates the next revision; existing snapshots stay intact. Delete/reset and completion span plan/progress/history atomically. Completion cannot clear progress in a separate write.

Once a validated transaction begins its disk write, complete or roll it back in a bounded noncancellable IO section so navigating away cannot strand an acknowledged mutation. Do not make network/provider operations noncancellable. Serialize events, disable duplicate pending actions, and keep the old visible state on save failure with local Retry. Do not show Saved, Done, or completion feedback before persistence succeeds. App loading and disk failure keep Settings/recovery navigation usable; never silently continue editing an in-memory-only plan.

Malformed current data: report a recoverable corruption state; offer Restore current snapshot and Reset to defaults with loss confirmation. If the user selects reset, atomically replace with a fresh current document. Do not erase a corrupt file just because reading failed, and do not automatically fall back to retired stores. Absent file on clean start uses defaults; absent file after Android transfer may restore only a validated current backup before installing defaults. A valid restored document with automatic backups off is still eligible for explicit restore. Runtime read permission failures never reset app data.

## Navigation, ownership and edit semantics

Represent a small explicit back stack of `AppRoute` values in AppStateHolder's SavedStateHandle, using stable route names and primitive IDs. Routes are Dashboard, MetricDetail(card), Settings, DashboardCustomization, PlanManagement, RoutineEditor(draftId/routineId), ScheduleEditor(draftId/entryId), InstalledAppPicker(ownerDraftId), ExerciseEditor(ownerDraftId/exerciseId), GuidedSession(sessionId), Completion(historyId). Pickers/chooser/confirmations are saved overlay state owned by their parent, not separate business records.

Restoring a route validates its referenced entity. Missing editor target returns to its parent with an explanation; missing session checks history and opens Completion if committed, otherwise Dashboard. Completion Back/close/Return all replace the stack with Dashboard, so Back never reopens finished work. Dashboard Back exits the Activity. Secondary top Back and Android Back use the same handler. Health permission and package-install activity results remain in the Activity and deliver typed results to the relevant holder.

| Screen / overlay | State owner and inputs | Events, output and Back |
| --- | --- | --- |
| Dashboard | DashboardStateHolder combines plan, partials/history, preferences, current date/selected date, HealthRepository and update status. Saves date and list position. | One semantic Start/Resume action per card; Done is readable noninteractive status. Linked Start calls launcher; guided Start durably creates snapshot then navigates. Settings and metric routes preserve Dashboard selection. |
| Health snapshot / MetricDetail | MetricStateHolder selects one of five metrics, observes shared saved range and per-metric health state. | Range commit triggers cancellable read. Retry, permission, provider and source actions are real. Back -> Dashboard. VIEW TRENDS uses MetricDetail(WEIGHT), the shared existing detail family; no duplicate trends screen. |
| Settings | SettingsStateHolder combines preferences, voice availability, health state, backup and update states; retains expanded group. | Four groups per handoff. Child navigation -> customization/planning. Backup details expand inline; all existing actions remain. No inert About. Back -> Dashboard. |
| DashboardCustomization | CustomizationStateHolder plus transient drag ordering, active ID, list/focus state. | Visibility saves immediately; last visible switch cannot be turned off. Reorder writes on completed move only. Reset dialog uses defaults on confirm. Back -> Settings. |
| PlanManagement | PlanningStateHolder observes plan; saves tab and weekday. | Schedule/Routines lists, counts and empty state. Add, edit, rename/delete, accessible reorder, reset confirmation. Back -> Settings. No management Start action. |
| Add routine chooser | PlanningStateHolder overlay. | Exactly Guided or Linked app; Guided -> new editor draft, Linked -> app picker then draft editor. Cancel creates nothing. |
| Installed app picker | InstalledAppPickerStateHolder owns query, loading/error/empty/list and scroll; owner draft ID routes selection. | Tap chooses package immediately; picker Back preserves original link and returns to originating chooser/editor. Query persists through recreation; inventory does not persist to disk. |
| Guided/linked routine editor | RoutineEditorStateHolder, repository entity plus expected revision, transient new draft; save draft in SavedStateHandle. | New routine has explicit Save routine after validity; Back confirms discarding meaningful new input. Existing rename/artwork/exercise changes each commit explicitly at their child action, then list shows autosave status. Delete routine confirms cascading loss. |
| Rename / routine artwork picker | Parent holder owns original and pending selection. | Rename Save / picker Done commits to existing entity, or updates new parent draft. Cancel/Back preserves original. Existing artwork selection is not saved on every tap. |
| Linked connection group | RoutineEditorStateHolder plus installed-app resolution and launcher status. | Change stages replacement, Save connection commits it (or parent draft Save). Cancel restores prior app. Test app link uses staged valid configuration without saving or recording completion. Existing name/artwork retain their own explicit child commits. |
| Exercise editor / paired picker | ExerciseEditorStateHolder keeps text inputs and artwork draft; parent routine revision is captured. | Dedicated scrollable screen. Save exercise validates and updates parent draft or one repository transaction; Cancel restores original. Timed off saves null seconds. Picker Done updates only exercise draft. |
| Schedule editor | ScheduleEditorStateHolder saves draft routine ID, weekday set, anchor and revision/generation. | Add to schedule / Save changes only; Back discards draft after dirty confirmation. No routine available -> explanation plus Add routine route, returning saved routine selection. No date fields. |
| Guided session | SessionStateHolder observes repository session snapshot; display ticker uses SessionClock, durable events use reducer. | Focus, set completion/correction, timer actions, restart and exit. Top/system Back -> Keep going / Save & exit dialog for partial or ready-to-finish state. Ordinary exit never discards. |
| Completion | CompletionStateHolder reads durable history by ID. | Shows snapshot name/art, all exercises complete and Saved. Single Return plus close share Dashboard navigation. No invented duration, calories or records. |
| Privacy rationale | Separate Activity, shared content components. | Scrollable privacy copy and Done finishes this Activity. No database mutation or brand icon in secondary header. |

New editor drafts survive configuration/process recreation via SavedStateHandle; limit saved text to the validation bounds and avoid Bitmaps/whole app documents. For larger new-routine drafts, use a dedicated bounded current-format draft file under `noBackupFilesDir/editor-drafts` keyed by UUID, containing only editor state; prune on save/discard and next cold start when no restored route references it. Drafts are never canonical routines or backup content. Persist each field change to the draft holder; no reliance on composition disposal. If restored draft data is missing, return safely with an explanation rather than create a partial routine.

Existing routine editors show `Changes save automatically` only for committed list/rename/artwork operations; child forms and pending app replacement clearly show Save/Cancel. There is no global Cancel that pretends to undo previously committed edits. A cancelled reorder writes nothing. Routine edits with any partial session show a confirmation that saved progress keeps its original exercise revision; cancel means no edit. Restoring/resetting the document invalidates all open drafts by generation and returns to Dashboard after success.

Dashboard customization controls all six sections. TODAY owns hero/session list/week selector/Saved sessions. Render visible cards in saved order; adjacent metric sections form responsive health-snapshot rows, flushing a row around TODAY. If TODAY is hidden, those training parts are hidden as requested. Keep brand/settings always reachable. Metric order is never silently overridden by a fixed three-column implementation. At default settings, first metric row is Weight/Body fat/Distance, with Lean mass continuing below; large fonts reflow to fewer columns.

All screens expose stable loading structure, inline recoverable errors and local save status. Reorder focus uses stable row IDs: after move focus the moved row; after delete focus the next row or previous last row, then Add if empty. Confirmations restore the triggering control on cancel. Controls are >=48 dp, rows grow/wrap, decorative artwork shrinks/hides before text clips, and status has text/semantics in addition to color. Seven 48 dp weekday targets require 336 dp: on narrower available width use a horizontally scrollable seven-item selector rather than shrinking touch targets. Provide TalkBack Move up/down and a keyboard/D-pad accessible overflow alternative. Use safeDrawing/IME insets and scrollable content for landscape and large fonts.

## Health Connect boundary and metric rules

Keep `DashboardCard`, `HealthMetric`, `HealthTrendPoint`, `HealthDateRange`, `HealthReadOutcome`, `HealthReadResult`, `HealthRecordPage`, `HealthMetricState`, `HealthTrendDirection`, `HealthTrendSummary` and `TimedHealthValue` concepts. Move them to focused packages; do not replace the working provider path with fixtures. HealthMetric is a display projection: store numeric values/timestamps/source packages in in-memory read results so unit/delta/summary formatting never parses display strings.

Preserve read-only permissions for weight/body-fat/lean mass/exercise/distance, history permission feature check, provider install/settings actions, null/empty token termination, repeated-token protection, newest-record selection across every page, and cancellation propagation. Preserve the recent-30-day latest query followed by older accessible records when empty. Rationale activity/alias remains registered. Never merge internal occurrence history into the Workouts health metric or mark linked launches as Health Connect exercise sessions.

HealthRepository owns CHECKING, NEEDS_PERMISSION, CONNECTED, UPDATE_REQUIRED, UNAVAILABLE, ERROR plus independent per-metric loading/latest/history/error states. Start reads only when the app is resumed **and** its window is focused; cancel on loss or range change. A generation/request token prevents stale cancelled work from overwriting newer results. Query metrics independently under supervisorScope with bounded concurrency; a denied/failed metric cannot erase successful neighbors. Refresh on return from permission/settings/provider flow. Do not persist measurements or cached grants in app backups.

Keep actual `recordedAt` and successful `syncedAt` distinct; stale means older than seven days, retaining the value. On a failed refresh keep any previously readable value with a refresh-error label and its old sync time; revoke access -> clear that metric's cached value. Distinguish no-data, no-permission, provider failure and partial-history messages. Resolve source labels from packages with a package-name fallback; expose all contributing sources when there is more than one.

Ranges DAY/WEEK/MONTH/THREE_MONTHS/YEAR retain 1/7/30/90/365 inclusive-day behavior, anchored to the newest available point for each metric. First obtain the newest accessible reading/activity endpoint, then query the selected start through that anchor day. Do not query only today's recent interval and pretend old readings have a history series. Future-dated records are excluded from the current reading. No points means no anchor-dependent chart summary, but the selector remains usable.

Body measurements retain every actual reading sorted by timestamp, including multiple readings per day. Extend HealthTrendPoint with an exact sample timestamp (or equivalent stable point key) instead of collapsing these to a daily latest sample. Workouts count records by local end date and distance sums record meters by local end date, preserving the existing endpoint attribution rule for cross-midnight records. Deduplicate record IDs across pages before totals. A zero activity day may be shown only inside a successfully read interval; denied, failed, outside-access and entirely empty intervals must not become zero-valued fabricated series. Label partial spans honestly.

Use one range-filtered numeric series for delta (last minus first), direction, chart domain, date span and high/average/low. The old earlier-half/later-half direction algorithm must not contradict the displayed endpoint delta. Neutral wording is up/down/steady, never improved/worsened. Single points have no line or delta claim. No numeric values means no summary. Sort before computing endpoints; reject nonfinite values; pad equal-value domains as well as variable domains without a zero baseline for body measurements. Locale-aware output: lb/%/mi one decimal; workouts whole counts including its displayed average. Unit tests cover these rules separately from Canvas rendering.

## Installed apps and safe launch behavior

Declare a MAIN + LAUNCHER intent query in the manifest, retaining the Health Connect package and TTS service queries. Query enabled, exported, permission-accessible launchable activities for the current user only; deduplicate by package, exclude DraftingRoom5, sort localized labels, and filter search locally by label. No `QUERY_ALL_PACKAGES`, hidden-profile access, account authorization, remote inventory or broad `getInstalledApplications` scan. Use the API-33 flags overload when available and the supported older overload on API 28–32. Labels/icons are runtime projections with generic fallbacks; optional category uses platform metadata when available and neutral `App` otherwise. [Android visibility declarations](https://developer.android.com/training/package-visibility/declaring)

`LinkedAppLauncher.launch(AppLink)` returns Launched, MissingApp, InvalidLink, or LaunchFailed, with a recoverable reason. Check readiness freshly because an app may be uninstalled between picker and launch. A configured deep link uses an ACTION_VIEW intent constrained with `setPackage(packageName)`; accept only absolute HTTPS or a nonreserved custom scheme. Reject `intent:`, file/content/javascript/data, embedded credentials and control characters; never parse stored text as an arbitrary Intent or accept arbitrary extras/flags. The approved UI does not offer free-text deep-link editing; absent a deliberately supplied verified configuration, use null and the normal launcher.

Try a valid configured deep link first, then the package's normal launch intent. Catch ActivityNotFoundException/SecurityException at each start and return failure if neither succeeds. Revalidate when returning from the app picker. Package constraints prevent an unrelated app handling a broken deep link. Do not automatically open a store on launch failure: remain in DraftingRoom5 and offer Change app and Install app. Install uses the selected package to construct the store URI, then an HTTPS Play fallback; catch failure of the fallback too. An unavailable configured app remains an editable routine.

Successful external launch is **not** proof of workout completion. Linked-app cards remain Start; no internal Done or health workout is recorded for launch/test/install/return. The approved flow has no linked-app completion confirmation or trusted completion callback. Done is backed only by durable guided completion history. This deliberately removes the old `launchWorkoutApp` followed by unconditional `recordWorkoutCompletion` behavior.

## Guided-session contract

DR5-023 will expand these rules into exhaustive implementation vectors before the durable session UI milestone. The model/storage must support them now; no progress in `remember` maps and no mutable-session references to the live exercise list.

Start validates the selected occurrence and live guided routine, checks history first (Done -> Completion), then existing partial (Resume), otherwise atomically creates a new session/snapshot with every count zero, first exercise focused, IDLE timer, fresh session ID, and eventRevision zero. Start double taps serialize to the same partial. Resume retains the saved focused exercise even when it is completed; if focus is absent from the snapshot, the data is invalid rather than silently pointing at a new live exercise.

Session mode is derived: ACTIVE if any snapshot exercise is incomplete; READY_TO_FINISH if all are complete. Terminal COMPLETE exists in history, with no resumable partial. Exercise progress is a contiguous count, not arbitrary checked set IDs. Set indicators offer an explicit `Undo last completed set` on a completed exercise; decrement exactly one count, retain other exercises and focus the corrected exercise. Completing a set advances the count; completing its final set focuses the first subsequent incomplete exercise, wrapping to the earliest incomplete if needed. Focus-only changes never complete skipped work. Show all other exercises, including completed ones in a reachable completed section, so correction does not lose later progress.

Commands include session ID and expected eventRevision; CompleteSet also includes exercise ID and expected 1-based set number. Commit increments revision and validates expected focus/count. Replayed/stale commands are no-ops or conflicts, never a second set. Undo/focus/timer mutations use the same gate. Action buttons remain disabled while their mutation is pending. An all-complete partial remains resumable as READY_TO_FINISH until explicit Finish session; final set completion alone does not silently navigate or commit history.

| Event | Accepted source state | Durable result / output |
| --- | --- | --- |
| Focus exercise | ACTIVE or READY_TO_FINISH, snapshot ID exists | Save focused ID, cancel any live timer; counts unchanged. |
| Complete set | ACTIVE, focused exercise incomplete, timer not READY | Increment exactly one count; cancel timer, advance set/focus as above; emit committed set cue. Last required set -> READY_TO_FINISH. |
| Undo last completed set | Count > 0, before terminal completion | Decrement selected count, focus it, timer IDLE, mode ACTIVE; no completion cue. |
| Start timer / Restart timer | Timed incomplete focused exercise, phase IDLE or FINISHED | New runId, READY deadline nowElapsed + 10 seconds; active deadline + exercise duration; start cue once. |
| Tick / Reconcile | READY or RUNNING | Derive remaining from deadlines, save boundary/cue watermark when crossed; READY -> RUNNING -> FINISHED. Never touch counts. |
| Cancel countdown / Cancel timer | READY/RUNNING/FINISHED | IDLE, no set mutation and no completion cue. |
| Complete set early during timer | RUNNING or FINISHED | Same CompleteSet transaction; cancel run before any later timer cue. |
| Complete set during readiness | READY | Reject; UI disabled until readiness ends. |
| Back / End early overflow | ACTIVE or READY_TO_FINISH | Keep going dismisses. Save & exit durably reconciles/saves and returns to Dashboard, preserving timer deadlines/progress; no discard or Done. |
| App background/process exit | Any partial | Already committed state remains. No dependency on onStop/onDispose writes. Timer deadlines continue; feedback ceases. |
| Restart session confirmed | Any partial | Replace with fresh session ID and latest live routine snapshot, same occurrence, counts zero and IDLE; cancel old timer/cues. Cancel leaves old session intact. |
| Finish session | READY_TO_FINISH only | In one write add immutable history using session ID and remove partial. Show Completion and cue only after success. Retry detects existing history idempotently. |
| Finish while incomplete | ACTIVE | Reject; offer Save & exit, never infer missing sets. |
| Routine edit acknowledged | Any partial | Increment live revision; partial snapshot/focus/counts remain unchanged. |
| Routine delete / Plan reset confirmed | Any partial | Atomic cascade; stop feedback and navigate away from invalid session. |

Use `SystemClock.elapsedRealtime()` through SessionClock for countdown and exercise deadlines, including sleep, rather than decrementing a counter per rendered second. Wall time supplies audit dates only during normal same-boot execution. [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock)

SessionClock returns elapsedMillis, wallMillis and nullable bootCount. Use Android's boot counter through a platform adapter; if unavailable, regard persisted timers as unverifiable after a new process. Same-boot restore compares deadlines to current elapsed time: at readiness boundary RUNNING, at active boundary FINISHED. Remaining display uses ceiling seconds clamped to `[0,duration]`. Background spanning both deadlines resumes as FINISHED with unchanged completed sets. Render at a modest interval only while visible; boundary persistence need not write every display tick. Wall-clock changes and DST cannot extend/finish a same-boot timer.

Boot count changed, unavailable after process death, or elapsed time regressed: cancel to IDLE and explain `Timer stopped after device restart; your sets are saved`. Do not guess elapsed work from wall time. Explicit backup restore always resets live timers to IDLE because their boot-clock origin is not portable, while retaining focus/counts. Ordinary same-process background with unavailable bootCount still uses its live elapsed clock. No foreground service, alarm, wake lock or claim of exact audible background delivery is needed; reconciliation is authoritative and feedback is only foreground.

Feedback is best-effort, at most once, tied to `(sessionId, eventRevision)` for set/finish and `(sessionId, runId, cueOrdinal)` for timers. Timer ordinal sequence: readiness started, 3, 2, 1, timer started, timer complete. Persist a lastHandledCueOrdinal watermark before attempting delivery; after process death never replay a prior cue. If a boundary was crossed while unfocused, mark it handled silently on reconciliation. If a visible tick skipped seconds, play at most the latest applicable cue rather than a burst. A crash after commit but before output may omit a cue; it cannot lose set progress or replay a cue. On re-entry, render remaining/finished state without stale announcements.

Only SessionFeedbackController drives WorkoutVoiceAnnouncements/WorkoutHaptics; no second composable cue path. Use the live saved preferences, ready TTS status, device vibrator and system haptic setting at dispatch time. Voice rate disabled when muted/unavailable, with 0.75–1.5 range and existing 0.05 step behavior (14 intermediate slider steps). Stop speech on exit/focus loss and release the TTS engine with its owner. A time-based haptic throttle is secondary; durable event identity prevents duplicate completions.

## Current-schema backup, restore and updates

Backup envelope: `{"format":"draftingroom5.backup.current","createdAtMillis":<Long>,"document":<complete current document>}`. Decoder accepts only this exact shape and the current document rules. Store `filesDir/current-backups/latest.json` and `previous.json`; local status metadata in `current-backup-status` preferences contains last attempt/success/error only. Automatic-backup enabled is authoritative in AppPreferences, not duplicated in status. Never read `automatic-backups` or old schema-1 snapshots.

BackupRepository exports one immutable committed repository generation under the shared mutation coordination, then writes it on IO with AtomicFile. Rotate a valid latest to previous successfully before replacing latest; failed writes keep a known-good copy. Workers serialize with manual backup/restore via a backup-operation Mutex, always acquired before the document Mutex to avoid deadlock. A snapshot may reflect the last committed generation before a concurrent subsequent edit; it must never combine plan and history from different generations. Record success only after a durable write. Failed attempts show local retry state and WorkManager backoff, never success.

Preserve after-change debounce (~10 seconds) and daily offline-capable periodic backup with exponential backoff. Disabling cancels unique pending work but retains recovery files. Back up now remains explicitly usable even with automatic scheduling off. Restore latest remains usable when scheduling is off and falls back to previous only if latest is invalid; expose which snapshot date will replace local plan, sessions, history and preferences before confirmation. Decode/validate candidate fully, require acknowledgement of current generation, cancel active feedback, reset imported live timers and atomically replace the entire document. Recheck candidate/generation at confirmation; one StateFlow publication refreshes every screen. Failure leaves existing data intact.

Android backup XML allowlists only the new current snapshot files and appropriate status metadata with existing encrypted cloud/device transfer behavior. Exclude document scratch/drafts, Health Connect measurements/grants, installed app list, update preferences/APKs, temporary files and signing data. Android transfer initialization may restore only when current document is absent and a valid current-format snapshot exists; it never probes an old plan key. Local snapshots work offline; Android transport timing/account choice is system-controlled, not promised by the app.

Retain update source `lnjefford/DraftingRoom5`, expected asset name `DraftingRoom5.apk`, HTTPS download bound, complete byte count, package identity, increasing version code and signing-certificate checks before installer launch. Keep FileProvider limited to update cache and existing install-permission flow. Run blocking HTTP/file verification on IO; propagate cancellation rather than turning it into success/failure retry loops. State belongs to update repository; busy flags are derived from live work and must not restore as permanently busy after process death. Revalidate cached APK before later installation, retain Retry after cancelled Android installer, and display network/check/download/permission/install errors within Settings.

## Production artwork and shared visual contract

`RoutineArtworkCatalog` maps stable string IDs to localized names and resource assets. Required initial IDs: `generic`, `dumbbell`, `grip_trainer`, `running_shoe`, `kettlebell`, `leg_day`, `full_body`, `push_day`, `pull_day`, `jump_rope`, `stopwatch`. `full_body` remains separate from `kettlebell`. Defaults use catalog IDs immediately even before generated assets exist, with generic vector fallback available during implementation.

`ExerciseArtworkCatalog` maps `generic`, `dead_hang`, `farmers_walk`, `grip_hold`, `wrist_curl`, `reverse_wrist_curl`, `finger_extension`, `wrist_rotation` to `{storageId, displayName, listAsset, headerAsset}`. A single saved choice selects both compositions. On unknown ID, missing resource or decode failure use the generic pair; never crash or replace the exercise. Use resource names `routine_<id>` and `exercise_<id>_list` / `exercise_<id>_header`. No resource IDs in JSON, dynamic downloaded images, arbitrary photo picker or independent pair selections.

Generate/licence each subject independently from mockup pixels. Review transparent gunmetal/navy assets with restrained blue/mint light at card right-third, routine header and picker crops; exercise list is compact and centered, header wide/right-weighted with left text space. Use optimized WebP for minimum-SDK coverage, bounded decode sizes, and no decode work in list recomposition. Hero has a separate owned/licensed athlete asset and intentional text-only fallback. Sources/masters stay outside runtime resources. Record prompt, tool/model, date, source/license, output paths, stable ID, paired variants, crop checks and approval in `docs/artwork/AssetLedger.md`. Existing hero/dumbbell PNGs are provisional until provenance and phone crops are checked.

Implement Dashboard.md's navy/ivory/blue/mint/gold tokens, DM Serif Display with bundled OFL attribution, and sans-serif controls. Measured-five geometry is one deterministic source reused by in-app and launcher resources: ivory 5, exactly three shallow notch cutouts, complete thin gold frame/overshoots, 108-unit adaptive viewport, safe bounds and monochrome variant. Remove text-plus-overlay notches, superseded raster launcher resources and legacy mark usage after replacement. Keep notification silhouette separate and Android-compliant. No hero parallax/continuous motion; any existing entry animation must remain nonblocking and respect system motion settings.

## Existing file/type disposition

The following inventory covers every production Kotlin source present during DR5-001. Target packages are above; remove each old definition/import in the same implementation slice that replaces its last consumer. Do not retain a second legacy screen path to keep compilation green.

| Current file | Types/functions and required disposition |
| --- | --- |
| `MainActivity.kt` | Keep Activity only. Replace private `Screen` with AppRoute. Extract `HealthConnection`, `HealthStats`, `HealthUiState` to health models; `DraftingRoom5App` orchestration to AppRoot/holders. Extract/rebuild Dashboard, TrainingHero, session/metric/settings/customization components in corresponding UI packages. Delete `LegacySettingsScreen`, `LegacyDashboardCustomizationScreen`, old `BodyMetricRow`, `WeeklyStatRow`, old nested `MetricCard`, `StatsWorkspaceHeader`, old `AutomaticBackupCard`/duplicate action rows once new groups own their capabilities. Replace `HealthTrendChart` with tested EditorialTrendChart; move metric/trend/unit/range extensions into health projections. Replace EmptySchedule/ScheduleCard with recovery/SessionCard. Delete `CustomWorkout`, old TimerPanel/ExerciseCard, `timedSeconds`, `exerciseSetCount`, and `launchWorkoutApp`; replace with session reducer/UI and LinkedAppLauncher. Move all health query, source, permission and installer helpers into provider adapter. Replace US-only `format` / `orPlaceholder` with locale-aware formatters. |
| `ScheduleData.kt` | Delete `Destination`, `ScheduledItem`, `CustomRoutine`, string-sets Exercise, old TrainingPlan/TrainingPlanStore, encodePlan/decodePlan, activeDays and old repeat fallback. Replace with domain Routine/Exercise/ScheduleEntry/TrainingPlan and AppDocumentCodec/Repository. `ScheduleRepeat` becomes nonpersisted editor preset. Keep UUID and pure move concepts in domain utilities with safe ID-based commands. Rewrite defaultTrainingPlan, removeRoutine, repeatDays/repeatPattern and forDay to the new invariants. |
| `PlanManagement.kt` | Replace monolithic PlanManagementScreen, WeeklyTimelineDay, old RoutineEditorScreen, ScheduleEditorDialog and ExerciseEditorDialog with tabbed planning and dedicated editors. Replace NameDialog with shared rename dialog. Delete Destination formatting; move locale-aware weekday/recurrence formatting outside UI. Remove enabled controls, permanent arrow/edit/delete clusters and duplicated schedule identity fields. |
| `WorkoutHistory.kt` | Replace old WorkoutHistoryEntry, separate WorkoutHistoryStore, encode/decode and completedScheduleIdsForDate with snapshot-based history and OccurrenceKey lookup in AppRepository. No same-calendar-completion-day inference. |
| `DashboardLayout.kt` | Retain DashboardCard, DashboardCardPreference, DashboardLayout names and pure visible/move/normalize behavior with defined defaults and last-visible protection. Move under data. Replace SharedPreferences persistence/codec with current document field codec and delegating DashboardLayoutStore facade if useful; no old key reads. |
| `HealthDateRange.kt` | Retain range enum/labels/inclusive startDate behavior. Move pure enum under data; replace store writes with AppRepository facade. |
| `HealthReadSupport.kt` | Retain HealthReadOutcome/Result, HealthRecordPage, readHealthValue, requestedHealthPermissions and latestHealthRecord in health package; preserve cancellation and token protections. |
| `HealthMetricSupport.kt` | Retain HealthMetricState/HealthMetric and freshness distinction; extend numeric/source projections and locale-aware detail formatters without persisting provider data. |
| `HealthTrendSupport.kt` | Retain TimedHealthValue, HealthTrendPoint, HealthTrendDirection/Summary and pounds conversion concepts. Replace dailyHealthTrend use for body chart with actual readings; remove helper if no remaining legitimate consumer. Retain dailyHealthTotals only for successful activity intervals; replace half-average direction with consistent endpoint delta. |
| `AppTheme.kt` | Split tokens/theme/typography into ui/theme and BrandedCard/SectionHeader/BrandTitle into components. Retain public theme use; replace MeasuredFiveMark with shared vector geometry. Remove superseded colors/animation/imports only after consumers are moved. |
| `VoiceAnnouncements.kt` | Retain VoiceAnnouncementSettings, VoiceAvailability, VoiceCue/text helpers, constants and WorkoutVoiceAnnouncements behavior; move to feedback. Replace store with AppRepository facade, validate finite rate, and route delivery through SessionFeedbackController. |
| `HapticFeedback.kt` | Retain HapticCue, WorkoutHaptics, system/vibrator checks and secondary HapticEventGate; move to feedback. Replace HapticSettingsStore persistence with facade and wall-clock throttling with elapsed clock. Delete test dependency on exerciseSetCount. |
| `BackupSupport.kt` | Retain AutomaticBackupStatus presentation and backupIsStale 48-hour behavior. Replace BackupSnapshot shape, independent-store reads/writes, automatic-transfer old-key probe, encode/decode schema 1, rotation and worker construction with current BackupRepository/Codec/Worker. Keep openAndroidBackupSettings with handled failure. |
| `AppUpdates.kt` | Retain AppUpdateStatus, release-version comparison and update safety checks; split manager/worker/network/installer. Replace AppUpdateCard with Settings update row. Move network off Main, expose observable local status and make cached installation revalidation explicit. |
| `PermissionsRationaleActivity.kt` | Keep Activity and manifest endpoints; update secondary hierarchy/privacy text, scroll and large-font behavior using shared components. |

| Other current files | Disposition |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | Retain Activity/rationale/provider and narrow permissions; register Application and MAIN/LAUNCHER visibility query. No new broad package permission or foreground-service requirement. |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Replace old snapshot/status allowlists with current ones; retain encrypted cloud constraints. |
| `res/xml/update_paths.xml` | Retain narrowly scoped cache update path; verify it never broadens to entire storage. |
| `res/values/themes.xml`, `colors.xml` | Keep Android theme plumbing; consolidate navy background/token values and remove obsolete ones once unused. |
| `res/mipmap-anydpi-v26/*`, `mipmap-anydpi-v33/*`, `res/mipmap/*.xml`, `res/drawable/measured_five_*.xml` | Finish/verify shared deterministic vector identity and adaptive/static/round/themed variants. Existing uncommitted XML is a candidate to refine, not certified final output. |
| `res/drawable-nodpi/ic_launcher_{foreground,monochrome}.png`, density `mipmap-*/ic_launcher*.png`, `branding/draftingroom5-mark.png` | Remove superseded launcher/brand raster assets and stale README references. Some are already deleted in the working tree; do not restore them. |
| `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_notification.png` | Replace with verified standalone monochrome vector/silhouette in DR5-005, then remove superseded density variants. |
| `res/drawable-nodpi/dashboard_athlete_hero.png` | Retained as the reviewed Dashboard-only hero with text fallback and an honest source-status entry in `docs/artwork/AssetLedger.md`; the unused legacy `session_dumbbell.png` was removed at DR5-013. |
| `res/font/dm_serif_display_regular.ttf`, `third_party/dm_serif_display/OFL.txt` | Retain together, validate licensing/rendering and attribution; do not remove license when relocating font. |
| `docs/design/**/*.png`, eight approved handoffs | Keep as design references only; update implementation status at milestones honestly. Add TechnicalDesign.md and AssetLedger.md; do not copy mockup UI into runtime resources. |
| `README.md`, `LICENSE`, `AGENTS.md`, `TODO.md` | Keep license/delivery authority; update README for delivered clean capabilities and retire obsolete terminology. Remove completed queue work block, retaining compact checked dependency ledger. |
| `app/build.gradle.kts`, root Gradle scripts, version catalog, wrapper/properties | Keep toolchain; add narrowly needed lifecycle/test dependencies and consistent version defaults at release milestones. No arbitrary upgrades in model/UI replacement. |
| `.github/workflows/commit-build.yml`, `release.yml`, `dependabot-automerge.yml`, `.github/dependabot.yml` | Preserve verification, tag release and guarded patch auto-merge. Release defaults must equal tag name and code formula `major*1,000,000 + minor*1,000 + patch + 2`; current working defaults 0.20.0/20000 are inconsistent with that formula and must be corrected when selecting the next release, not released by DR5-001. |
| `.gitignore`, `.tooling/`, `.gradle-user-home/`, build/cache/local files | Preserve ignores; never commit toolchains/caches/generated APKs/signing files. Add missing ignore rules only as part of the relevant implementation cleanup. |

Retired storage is explicitly `training-plan/plan-v1`, `workout-history/history-v1`, `dashboard-layout/layout-v1`, `health-date-range/selected-range-v1`, `haptic-feedback`, `voice-announcements`, `automatic-backup`, and `automatic-backups/*`. Delete their source readers/writers; leave old device files unobserved until Android clears app data. App-update status/cache is independent transient maintenance data and may remain, never a plan-format bridge.

## Implementation sequencing and verification

DR5-002 introduces the full current entity/document shapes (including empty partial/history fields), invariants, defaults and atomic repository. Update all existing consumers to the new entities in that same slice; do not keep compatibility aliases. Basic native UI can remain visually transitional, but build must use only current types. Because backup/history reference the plan, replace their old codecs with the current envelope plumbing immediately; DR5-026 completes durable-session backup integration and end-to-end recovery. Session reducer detail/UI remains DR5-023–026, and no fake resumability should be advertised before it is real. DR5-004 moves state/navigation and shared components; later tasks deliver the approved surfaces. No new product choices are required to start DR5-002.

Focused unit tests are required for each implementation task. For this document-only task, validate the source inventory, relative links, JSON example and queue/dependency integrity; do not claim an Android runtime was tested. The existing tests are a regression starting point, not proof of target compliance.

| Existing test file | Required retention/replacement |
| --- | --- |
| `ScheduleDataTest.kt` | Replace legacy/default-single-day/disabled tests with current schema round-trip, strict validation, defaults, pure recurrence and filtered-slot reorder tests. Remove `olderSavedPlansDefaultToTheirSingleAssignedDay`. |
| `WorkoutHistoryTest.kt` | Replace with occurrence-date vs completion-date, duplicate session completion, next-week/same-day multiple entries, deletion survival and timezone tests. |
| `BackupSupportTest.kt` | Preserve status/staleness coverage, replace snapshot round-trip and remove olderSnapshot defaults tests. Add current-only strict decode, atomic restore, previous fallback, off/manual backup, interrupted write and timer invalidation. |
| `DashboardLayoutTest.kt` | Update deliberate defaults, retain independent reorder/visibility and normalization; test every destination position, hidden reorder, all-hidden repair and final-visible protection. |
| `HealthDateRangeTest.kt` | Retain inclusive ranges; add newest-point anchoring, persistence and leap/DST boundary projections. |
| `HealthReadSupportTest.kt` | Retain provider permission, missing/error isolation, cancellation and all paging regressions. Add request-generation race and source-label fallback coverage. |
| `HealthMetricSupportTest.kt` | Retain current/stale/missing/unavailable distinctions; add partial refresh, per-metric revoked permission, multiple sources and locale formatting. |
| `HealthTrendSupportTest.kt` | Replace daily-latest measurement/half-average direction expectations; test repeated same-day actual samples, empty/single/equal/nonfinite values, activity zeros only in read intervals, summaries and padded chart domain. |
| `HapticFeedbackTest.kt` | Keep capability/gate tests; delete free-form sets parser test. Add elapsed-clock and event identity tests at feedback/session boundary. |
| `VoiceAnnouncementsTest.kt` | Keep settings/rate/copy coverage; add unavailable/init/disposal, finite-rate validation and at-most-once restored cue handling. |
| `AppUpdatesTest.kt` | Keep numeric release comparisons; add fake HTTP/installer package, size, signer, cached-file and cancellation checks. |

Add `AppDocumentCodecTest`, `AppRepositoryTest`, `PlanMutationsTest`, `SessionReducerTest`, `LinkedAppLauncherTest`, catalog tests and focused UI/semantics tests. Use fake storage with failure at before-write/finish/publish boundaries; fake provider/launcher; injected clock; coroutine test scheduler. Real instrumented tests cover Android AtomicFile, lifecycle recreation, package visibility and activity-result behavior. Do not mirror composable internals in brittle tests.

Minimum session vectors: two sets at expected revision; double-tap stale CompleteSet does not complete two; final set -> ready-to-finish -> durable history; premature finish rejected; undo earlier work keeps later progress; readiness at 9,999/10,000 ms; active deadline minus 1/exact boundary; cancel then stale tick; complete early while running; same-boot process death in ready/running/finished; background crossing both deadlines; boot change/unknown boot; wall-clock jumps; midnight/timezone occurrence remains fixed; restart changes session/run identity; safe routine edit retains snapshot; routine deletion/reset clears partials; schedule deletion preserves reachable partial; persistence failure retains old state and emits no success cue; restore clears timers; duplicate Finish returns same history.

UI fixture matrix: Dashboard linked/guided/partial/done/multiple/recovery/old partial/hidden sections; every metric loading/no data/permission/provider update/unavailable/stale/read error/partial history/single point; Settings TTS initializing/unavailable, haptics unavailable/system off, backups off/current/stale/failure/restore, updates current/available/downloading/error/install permission; customization cancelled drag/last visible/reset; planning empty/one/many/repeating; pickers empty/search/missing app; editors new/dirty/cancel/invalid/conflict/deleted target; session untimed/ready/running/finished/partial/corrected/complete; corruption/read-only failure/reset.

Render native previews/screens at compact 320 dp width, typical 360–411 dp widths, tall portrait, landscape, and 1.0/1.5/2.0 font scales. Verify 48 dp bounds, scrolling, no duplicate card/switch semantics, chart summaries, focus after reorder/delete, keyboard/D-pad controls, motion-off behavior, every artwork crop and adaptive circle/squircle/rounded-square/OEM icon mask at mdpi–xxxhdpi. Use physical-device checks for actual Withings data/grants, installed app launch/uninstall race, TTS/vibration system settings, process kill/reboot, Android backup transport, TalkBack and signed update installation. Record unavailable device checks explicitly rather than marking them verified.

Every Push YES milestone runs `testDebugUnitTest lintDebug assembleDebug`, relevant instrumentation/render checks available in the environment, then reviews the accumulated diff. Select the next increasing tag from GitHub/local tags, update default version name/code using the workflow formula, commit coherent source/docs only, push main/tag, follow Android release to success, and verify nonempty signed APK publication attached to that exact tag. DR5-001 performs none of those release mutations because its work item is Push NO.

DR5-001 validation (2026-09-10 UTC): all eight local handoff links resolve; the JSON example parses with the expected format, six layout cards and integer set count; every one of the 15 existing production Kotlin files and 11 test files has a disposition above. Queue dependency integrity and changed-document whitespace were checked. No production changes, Android tests, visual renders, commit, tag or APK release were performed by this documentation task.
