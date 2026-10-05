# Compact watch redesign implementation audit

The implementation matches the approved Today, Fitness, Stocks and Next Games
mockups as closely as possible, including team logos, behavior, scenery and motion.
This audit covers v0.29.20. Publication status is authoritative in the
[tagged release](https://github.com/lnjefford/DraftingRoom5/releases/tag/v0.29.20)
and [release workflow](https://github.com/lnjefford/DraftingRoom5/actions/workflows/release.yml).

## Implemented and verified

- Today and Fitness are two horizontally paged screens. A rightward swipe from
  Today reaches Fitness. Detail lists replace the pager while open.
- Stock selection uses the greatest absolute percentage movement. Game selection
  uses the earliest future kickoff across followed teams. JVM tests cover both.
- The stock and game cards open all followed items. Both detail screens have the
  same light blue Back control and horizontal scroll lines, with touch and rotary
  scrolling. Longer-list screenshot data includes all eight stocks.
- Fitness offers today's native run plans and every unfinished guided workout.
  Selected workout occurrence IDs and cached exercises cross the watch protocol;
  a JVM test verifies selection, serialization, offline projection and rejecting
  an unknown selection. Timed Start Set actions begin the ready countdown.
- Existing active run/workout routing takes priority over Today. Permission denial
  and run-start errors route to the run picker with a visible explanation.
- Real team assets are bundled for the mockup teams. Other team/opponent logos use
  the phone's ESPN URLs and an on-watch cache. Iowa uses the gold single-color mark;
  Bears uses the exact C mark rather than a generated approximation.
- Twelve Madison lakeshore plates cover all four seasons in daylight, dusk and
  night. Dawn applies a rose tint to dusk; precipitation adds cloud/frost veils
  and particles while retaining the location and season. Night uses a moon glyph.
- Leaves, petals, rain, snow, winter glints and summer water glints animate behind
  the UI. Motion requires a resumed activity, interactive screen, non-ambient
  state and enabled system animations. Screenshot previews keep motion disabled.

## Verification evidence

- Focused Fast validation has passed for WatchProtocolTest and
  WatchTodaySelectionTest, including the selected-workout protocol extension.
- Wear lint passed after replacing an API 33-only stream reader with a bounded
  read compatible with the supported minimum API 30.
- Initial 41 mm and 45 mm renders were inspected. A pager child layout error was
  found and corrected; subsequent Today and Fitness renders were inspected.
- Updated 41 mm Today, Fitness, Stocks, Next Games, long stocks, winter, snow and
  night screenshots were inspected. Logos now match the intended variants.
  Final blue-arrow geometry and the eight-stock endpoint render were inspected.
  45 mm Today, Fitness, Next Games, spring, summer, winter, rain and snow
  renders were also inspected.
- A dedicated API 34 round Wear emulator verifies rightward Today-to-Fitness
  paging, summary taps, Back, touch scrolling and rotary scrolling. All eight
  seeded stocks and six seeded games are reachable. Native run Start, persisted
  active reopening, Finish and Done were exercised. A second guided workout's
  Start Set selects its own occurrence rather than the first workout.
- Focused Fast validation passed again after offline finish removes the completed
  guided occurrence. Large schedules round-trip above 100 KB; full snapshots use
  a bounded 16 MiB Data Layer Asset with a small legacy inline payload when possible.
- New 41 mm daylight, dawn, storm and moon renders were inspected, together with
  the core detail controls. The 45 mm Fitness and seasonal/precipitation renders
  were inspected. Daylight contrast and the long-list endpoint preview received
  final refinements; the resulting daylight and endpoint renders were inspected.
- Runtime guided completion removes only the selected occurrence; the other
  guided workout remains available. Two ambient Today captures three seconds
  apart have identical SHA-256 hashes, while interactive captures differ.
  Debug captures and seed data stay in ignored `tmp/`.
- The final time audit makes the next-game summary observe the minute clock
  directly, chooses seasonal scenery from the current calendar date, and filters
  Fitness against that date. A new overnight regression covers cached next-day
  run plans and excludes yesterday's guided occurrences. Uncached screenshot
  previews use a fixed date/hour; runtime still uses the current clock.
  Focused Fast checks passed after these refinements.
- The final Release tier passed on October 4, 2026: 540 unit tests across 79
  suites, both modules' lint and APK assembly, and the complete screenshot matrix
  including 80 watch references. The final incremental pass took 200.8 seconds
  after a complete 1507.2-second initial pass. No reference was updated merely to
  bypass comparison; intentional watch and version-footer changes were inspected.
- Version defaults are 0.29.20 (phone 290220, watch 290221), consistent with the
  tag workflow's calculation. The tagged release and workflow linked above record
  the published signed APKs and Play bundles.

## Requirement evidence

| Requirement | Evidence |
| --- | --- |
| One compact Today screen | Watch render case 0 and emulator `latest-today.png` |
| Largest absolute stock movement and earliest future game | `WatchTodaySelectionTest`; summary reads the minute clock |
| Rightward swipe to Fitness | Emulator `fitness-start.png`; reversed two-page horizontal pager |
| Every native activity scheduled today can be selected | Cached-plan/current-date selection; emulator second workout and run start; protocol selection tests |
| Active workout takes over through completion | Emulator timer reopening, `run-restored.png`, run/guided completion captures and Done navigation |
| All followed stocks/teams reachable with Back | Eight-stock and six-game emulator lists; touch and rotary checks; renders 12, 13 and 15 |
| Matching blue Back and scroll controls | One shared detail composable; paired blue-control mockup; inspected 41/45 mm renders |
| Real team marks | Bundled ESPN/Commons assets with provenance below; Bears C and gold Iowa treatment |
| Seasonal/daypart/weather scenery and subtle motion | Twelve Madison plates; renders 31–39; clock-driven resource selection; ambient capture hashes |
| Detailed run turn guidance retained | Existing run render cases 8, 9, 19, 20 and 30; actual route map enters below the face and fades into the distance |
| Phone seasonal motion retained | `TodayAmbientMotion.kt` covers spring petals, autumn leaves, summer shimmer, winter glints and precipitation |
| Release checks and published APKs | Final Release gate passed; publication is recorded by the linked v0.29.20 release and tag workflow |

## Team asset provenance

Packers, Bucks, Knicks, Wisconsin, Iowa, Indiana and Brewers source PNGs:
`https://a.espncdn.com/i/teamlogos/` with NFL `500/gb.png`, NBA `500/mil.png`
and `500/ny.png`, NCAA `500/275.png`, `500/2294.png`, `500/84.png`, and MLB
`500/mil.png`, respectively.

Bears C asset: [published vector rendering](https://commons.wikimedia.org/wiki/File:Chicago_Bears_logo.svg).
Shape and small-display variant checked against the
[Bears brand guidelines](https://www.chicagobears.com/about/brand-guidelines).
Iowa gold treatment checked against the
[University of Iowa brand manual](https://brand.uiowa.edu/logos/secondary-logos).
Team marks remain owned by their respective teams; they identify game opponents.
