# DraftingRoom5 Analog — Google Play listing packet

## Paste into Play Console

**App name:** DraftingRoom5 Analog

**Default language:** English (United States)

**Category:** Personalization

**Short description:** Analog watch face with weather and a workday stand reminder

**Full description:**

DraftingRoom5 Analog brings a clear, classic dial to your wrist with quiet minute marks and a little movement from the weather art.

See the latest phone-synced temperature and a 30-minute stand countdown in two complications. Tap the weather complication to open Pixel Weather when it is available on your watch. Tap the stand complication to reset the countdown and see a short stretch animation.

Choose mint, sky blue, or sun gold accents. The always-on display keeps the time readable while reducing motion and brightness.

Set the stand reminder's days and hours in the separate DraftingRoom5 watch app. It defaults to Monday through Friday, 9:00 a.m. to 5:00 p.m. in the watch's local time, and shows OFF outside that schedule. The weather temperature also requires the DraftingRoom5 phone and watch apps to sync.

**Suggested release notes:** Initial internal test of DraftingRoom5 Analog with weather and scheduled stand complications.

## Upload map

| Play Console field | File | Details |
| --- | --- | --- |
| App icon | `app-icon-512.png` | 512 × 512 RGBA, circular face to the edges |
| Feature graphic | `feature-graphic-1024x500.png` | 1024 × 500 RGB |
| Wear OS screenshot | `wear-live-offline.png` | 454 × 454 RGB emulator capture showing the face with no paired phone, outside stand hours |
| Color reference images | `wear-preview-mint.png`, `wear-preview-sky.png`, `wear-preview-gold.png` | 450 × 450 RGB previews showing the three accent choices |

The preview images use the exact dial artwork bundled with the face APK and sample complication values supplied by its providers. They are visual references, not live device captures. Upload `wear-live-offline.png` as the screenshot. Google asks for captured images showing more than one option when a face is customizable, so add real sky and gold screenshots from a watch before completing the listing. The phone and watch apps must be paired for live weather data to appear.

## Alt text

- Icon: Analog black watch face with white numerals, mint hands, weather and stand indicators.
- Feature graphic: Dark teal graphic with the DraftingRoom5 analog watch face and a playful message.
- Mint preview: Analog face with mint hands, weather temperature, and stand countdown.
- Sky preview: Analog face with sky blue hands, weather temperature, and stand countdown.
- Gold preview: Analog face with sun gold hands, weather temperature, and stand countdown.
- Live offline: Analog face showing unavailable weather and stand reminders paused outside scheduled hours.

## Bundle

The initial Play upload needs the signed `DraftingRoom5-Face-Play.aab` from the next GitHub release. It has package ID `dev.draftingroom5.face`. The separate DraftingRoom5 watch app supplies the weather and stand complication data.

Google's [preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) specify a 512 × 512 icon, a 1024 × 500 feature graphic, and square Wear OS screenshots at least 384 × 384. Its [watch face quality rules](https://developer.android.com/docs/quality-guidelines/wear-app-quality) require an icon that shows the face itself and screenshots that accurately depict the face.
