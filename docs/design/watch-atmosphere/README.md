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

Design decisions:

- Use a dark navy atmosphere, scenic imagery, amber horizon, restrained blue actions, and clear visual hierarchy instead of text-heavy watch pages.
- Use the same dark-haired female athlete with a high ponytail and black training clothes as the phone app wherever an exercise view contains a person.
- Give an active run's next turn a prominent direction symbol. Show off-route status distinctly.
- Keep primary controls visible inside the round safe area on both 41 mm and 45 mm watches.
- Populate market charts from real quote points and run cues from available route data. Sample numbers and names in these concept sheets are illustrative.

The implementation is validated against the watch screenshot references in `wear/src/screenshotTestDebug/reference/`; those renders show the actual app at supported sizes.
