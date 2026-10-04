# Watch Atmosphere design reference

The user selected the Atmosphere direction for the watch app and approved its implementation across Today, workouts, timers, runs, and connection and empty states. These are the approved concept sheets recovered from the design conversation:

| Sheet | Coverage |
| --- | --- |
| [Today](today.png) | Weather overview, workout, stocks, teams |
| [Workout](workout.png) | Workout flow |
| [Runs](runs.png) | Run selection and active run |
| [States](states.png) | Completion and connection states |
| [Additional states](additional-states.png) | Timer finished, done today, open day, no cached runs |
| [Consistent athlete](consistent-athlete.png) | Corrected person-based views using the phone app's female athlete as reference |
| [Turn directions](turn-directions.png) | Large turn cues and off-route state during runs |
| [Today run](today-run.png) | Dedicated horizontally paged Today run card |
| [Empty and free run states](empty-and-free-run.png) | Empty stocks and teams, waiting for GPS, treadmill run |
| [Right turn](right-turn.png) | Mirrored right-turn route guidance |

[Screen audit](SCREEN_AUDIT.md) maps every concept panel to the 41 mm and 45 mm watch screenshot cases.

## Next Today navigation concept

These newer mockups are design proposals and have not yet replaced the shipped watch UI:

| Screen | Mockup |
| --- | --- |
| One-page Today summary: weather, largest absolute move among followed stocks, next game | [Compact Today](today-compact-concept.png) |
| Rightward swipe to today's watch-startable activities | [Fitness start](fitness-start-concept.png) |
| Tap the stock summary to see every followed stock in one scrollable detail list | [Stocks list](stocks-list-concept.png) |
| Tap the game summary to see each followed team's next game in one scrollable detail list | [Next games list](games-list-concept.png) |

Each detail list has a persistent Back action to Today. When a watch activity is running,
its active screen takes over until the activity finishes. The mockup values, teams, and
dates are illustrative. The Today's scenic background should respond to daypart,
season, and weather; ambient particles stay behind the data and stop when motion is
disabled or the screen is not interactive.
The Stocks and Next Games detail views share the same light-blue Back control
without a pill and the two-line "Scroll for more" cue.

Design decisions:

- Use a dark navy atmosphere, scenic imagery, amber horizon, restrained blue actions, and clear visual hierarchy instead of text-heavy watch pages.
- Use the same dark-haired female athlete with a high ponytail and black training clothes as the phone app wherever an exercise view contains a person.
- Give an active run's next turn a prominent direction symbol. Show off-route status distinctly.
- Keep primary controls visible inside the round safe area on both 41 mm and 45 mm watches.
- Populate market charts from real quote points and run cues from available route data. Sample numbers and names in these concept sheets are illustrative.
- Navigate Today by swiping left and right through full watch screens. Each followed stock and team has a reachable page with a position label. A dedicated Run page offers a clearly named "Choose run" action instead of a Runs shortcut on unrelated pages.
- Give the active run route a visible path and current-position marker behind the turn cue, with a distinct off-route marker when needed.
- Keep the route entering from below the visible face and fading beyond its distant edge.
- Later navigation decisions supersede early concept-sheet "Runs" buttons on unrelated Today pages: swiping reaches the dedicated Run page.

The implementation is validated against the watch screenshot references in `wear/src/screenshotTestDebug/reference/`; those renders show the actual app at supported sizes.
