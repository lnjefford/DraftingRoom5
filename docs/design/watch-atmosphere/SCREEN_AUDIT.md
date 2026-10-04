# Watch screen and concept audit

The `WatchScreenshots` preview renders each case at 41 mm and 45 mm. Screenshot case
numbers correspond to the numeric suffix of the PNGs in
`wear/src/screenshotTestDebug/reference/dev/draftingroom5/wear/WatchScreenshotsKt/`.
The mockup sheets are visual direction; live prices, teams, route points, and
elapsed times come from the app's data.

| Case | Watch state | Approved visual reference |
| ---: | --- | --- |
| 0 | Today weather overview | `today.png` · Overview |
| 1 | Exercise / Dead Hangs | `consistent-athlete.png` · Dead Hangs; supersedes the earlier male athlete in `workout.png` |
| 2 | Get ready | `workout.png` · Get ready |
| 3 | Hold timer | `workout.png` · Hold timer |
| 4 | Set complete | `workout.png` · Set complete |
| 5 | Session complete | `states.png` · Session complete |
| 6 | Run picker | `consistent-athlete.png` · Choose run and `runs.png` · Choose run |
| 7 | Active run with route | `runs.png` · Running |
| 8 | Next left turn | `turn-directions.png` · Next turn |
| 9 | Off route | `turn-directions.png` · Off route |
| 10 | Run complete | `runs.png` · Run complete |
| 11 | Today workout | `consistent-athlete.png` · Today workout |
| 12 | Today stock | `today.png` · Stocks |
| 13 | Today team | `today.png` · Teams; the later navigation decision removes its Runs button |
| 14 | Today offline | `states.png` · Today offline |
| 15 | Fourth followed stock | `today.png` · Stocks with a different live quote |
| 16 | Today run page | `today-run.png` |
| 17 | Timer finished | `additional-states.png` · Timer finished |
| 18 | Paused run | `runs.png` · Paused |
| 19 | At the left turn | `turn-directions.png` · At the turn |
| 20 | U-turn | `turn-directions.png` · U-turn |
| 21 | Today workout done | `additional-states.png` · Workout done today and `consistent-athlete.png` · Workout complete |
| 22 | Today open day | `additional-states.png` · Open day |
| 23 | No cached runs | `additional-states.png` · No cached runs |
| 24 | Connecting | `states.png` · Connecting |
| 25 | Phone needed | `states.png` · Phone needed |
| 26 | No stocks followed | `empty-and-free-run.png` · No stocks |
| 27 | No teams followed | `empty-and-free-run.png` · No teams |
| 28 | Free run waiting for GPS | `empty-and-free-run.png` · Free run, waiting for GPS |
| 29 | Treadmill run | `empty-and-free-run.png` · Treadmill run |
| 30 | Next right turn | `right-turn.png` |

The 41 mm and 45 mm references must be inspected after intentional visual changes,
including text and action placement inside the circular safe area. The running
overview draws a path only from real route or recorded sample points. Turn guidance
uses the saved route and its cues; the route enters below the face and fades past
the distant edge. The dedicated Today Run page and the empty/free-run sheet were
generated to cover screens that lacked an approved mockup. The final bitmap
background plates for Dead Hangs, open day, workout done, free run, and treadmill are in
`wear/src/main/res/drawable-nodpi/`.
