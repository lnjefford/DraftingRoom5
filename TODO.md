# DraftingRoom5 priorities

Work through these when advanced model access is available again.

1. **Fix weight, body-fat, and lean-mass reads — verified on device in v0.2.2**

   Determine why Withings records visible in Health Connect return no readable records in DraftingRoom5. Use the app’s Health data details output, verify Android 17 behavior and permissions, inspect data origins and timestamps, and test the exact record types Withings writes.

2. **Move connection and update controls into Settings — completed in v0.3.0**

   Add a Settings screen and move the Health Connect status card, app update card, and Health Connect permissions/settings link off the dashboard. Keep the dashboard focused on measurements and today’s schedule while preserving those actions in Settings.

12. **Next feature idea**

    Leave this item open for the next feature to define.

16. **Rework the fitness tracker workspace UI**

    Show small metric cards on the dashboard, each with an up, down, or neutral trend arrow based on the last month's trend. Give each card a drilldown with a line graph that draws actual lines between the data points. Place the day, week, month, and other duration selections in this detail view. Make the duration selector visually distinct from the graph card, without putting the selector in another card.

    Visually separate the dashboard's high-level stats from the schedule, taking inspiration from Epic's MyChart: a distinct header containing the stats, followed by a separate section containing schedule cards. Fix the activity header text color so it is readable against the dark background instead of black.
