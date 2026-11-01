# Analog watch face

The face is a separate Watch Face Format package, `dev.draftingroom5.face`.
Install `DraftingRoom5-Wear.apk` and `DraftingRoom5-Face.apk` on the watch,
then select **DraftingRoom5 Analog** from the watch face picker. The face
requires Wear OS 4 or newer. The Wear app supplies the weather and stand
complications; the face can also use other short-text complication providers.

The weather complication reads the latest phone-synced temperature. Tapping
it opens Pixel Weather when available, otherwise the watch app. The stand
complication counts down 30 minutes after a reset during scheduled hours.
Tapping it resets the timer and plays a short stretch confirmation.

Open **Stand hours** in the watch app launcher to choose active weekdays and
start/end hours. The default is Monday-Friday, 09:00-17:00 in the watch's
local timezone. End is exclusive. Outside that schedule the face shows
`OFF`; each active day starts a fresh interval. All seven days can be turned
off to pause the reminder.

The face offers mint, sky blue, and sun gold accents in the watch face editor.
The weather art makes one brief cloud-and-sun motion when the face becomes
visible. Ambient mode hides the animation and second hand and dims the face.
The separate face APK
is attached to GitHub releases; it is not yet a Google Play listing.
