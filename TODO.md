# DraftingRoom5 priorities

Work through these when advanced model access is available again.

1. **Fix weight, body-fat, and lean-mass reads — verified on device in v0.2.2**

   Determine why Withings records visible in Health Connect return no readable records in DraftingRoom5. Use the app’s Health data details output, verify Android 17 behavior and permissions, inspect data origins and timestamps, and test the exact record types Withings writes.

2. **Move connection and update controls into Settings — completed in v0.3.0**

   Add a Settings screen and move the Health Connect status card, app update card, and Health Connect permissions/settings link off the dashboard. Keep the dashboard focused on measurements and today’s schedule while preserving those actions in Settings.

3. **Create an app icon**

   Design an excellent DraftingRoom5 icon. Explore time-of-day variants or subtle motion while keeping a stable launcher icon and avoiding distracting battery or animation behavior. Add adaptive icon assets and verify the icon on Android launchers and notifications.

4. **Make the app dark-only**

   Create a polished dark theme and remove the light-theme option. Check contrast, cards, buttons, Health Connect states, timers, settings, dialogs, and system bars.

5. **Add schedule and custom-routine management**

   Let the user create, edit, reorder, enable, disable, and delete scheduled items. Let the user manage custom routines and exercises, including sets, targets, timed exercises, and notes. Persist changes across restarts and keep the built-in routine as a reset/default.

6. **Get tracking backup working with Google**

   Choose and implement the backup path for the user’s Google account, likely Google Drive or Firebase with Google sign-in. Define what is backed up, minimize sensitive data, support restore and conflict handling, show backup status, and test sign-in, backup, restore, offline behavior, and account changes.

7. **Automate dependency updates and commit builds**

   Add Dependabot configuration for Gradle dependencies and GitHub Actions. Add CI builds on pushes to `main` and pull requests so every commit produces a verifiable APK artifact. Let Dependabot patch-update pull requests auto-merge only after tests, lint, and the build pass. Keep versioned release publishing tag-driven, with the commit build serving as validation rather than an app-store release.

8. **Customize the dashboard**

   Let the user choose which cards appear and reorder them. Persist the layout and provide a reset-to-default option.

9. **Show last synced time per health metric**

   Display the most recent successful sync time for weight, body fat, lean mass, workouts, and distance. Show the source app when available and distinguish stale data from missing data.

10. **Add health trend charts**

    Plot weight, body fat, and lean mass over time. Handle missing days, multiple records on one day, unit conversion, and a clear empty state.

11. **Add date-range controls**

    Let the user switch charts and summaries between one week, one month, three months, and one year. Keep the selected range across app restarts.

12. **Add automatic backups**

    Back up app settings, schedules, routines, and workout history automatically. Show the last successful backup, retry failures, support offline recovery, and let the user disable automatic backups.

13. **Add animated in-app branding**

    Use restrained animation during launch or workout completion. Respect reduced-motion settings and keep animations short so they do not delay navigation.

14. **Add haptic feedback**

    Provide haptic feedback for timer start, countdown completion, set completion, and workout completion. Make it configurable and avoid repeating vibrations excessively.

15. **Add voice timer announcements**

    Announce countdowns, timer start, timer completion, and set transitions through Android text-to-speech. Provide a mute toggle, voice-rate control, and graceful behavior when text-to-speech is unavailable.

16. **Next feature idea**

    Leave this item open for the next feature to define.

17. **Rename the "running" card to "Distance"**
