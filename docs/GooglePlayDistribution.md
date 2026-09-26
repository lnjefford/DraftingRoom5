# Google Play distribution

Each tagged release publishes four signed artifacts on GitHub:

- `DraftingRoom5.apk` and `DraftingRoom5-Wear.apk` retain the GitHub self-update path.
- `DraftingRoom5-Play.aab` is the phone bundle for Google Play.
- `DraftingRoom5-Wear-Play.aab` is the Wear OS bundle for Google Play.

The Play phone build removes `REQUEST_INSTALL_PACKAGES`, removes the updater file
provider, does not schedule GitHub update checks, and presents updates as managed
by Google Play. Phone version codes end in `0`; watch version codes end in `1`, so
the two independently uploaded form factors never collide in Play Console.

## First internal release

1. Download both Play `.aab` assets from the newest GitHub release.
2. In Play Console, open **Test and release > Testing > Internal testing** and
   create the phone release with `DraftingRoom5-Play.aab`.
3. Open **Test and release > Advanced settings > Form factors**, add **Wear OS**,
   and complete the Wear OS opt-in and store-listing requirements.
4. Open the Wear OS testing track and create its release with
   `DraftingRoom5-Wear-Play.aab`.
5. Add the intended Google account to the internal tester list, publish both
   releases, open the opt-in link with that account, and install from Google Play
   on the phone and watch.

Future tagged releases produce both replacement bundles. Upload the phone bundle
to the phone track and the watch bundle to the Wear OS track. Google Play delivers
updates automatically when automatic updates are enabled on each device.
