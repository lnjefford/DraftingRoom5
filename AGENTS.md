# Delivery workflow

After each completed set of changes, run the relevant checks, commit the changes,
push to GitHub, and create and push a new increasing version tag. Follow the
tag-triggered release build through completion and verify that its APK is
published. Keep the app's default version name and code consistent with the new
release. Do not release each intermediate edit separately.

This is the user's standing preference; no additional confirmation is needed
for these delivery steps. Never commit signing keys, credentials, or generated
build files.

On Windows, use `./tools/verify.ps1` for validation and invoke any additional
Gradle commands only through `./gradlew.ps1`; never call `gradlew.bat` directly.
The bootstrap rejects Java versions older than 17, discovers the repository JDK
or Android Studio JBR, and moves Gradle caches and build outputs to an isolated
path under `%LOCALAPPDATA%\DraftingRoom5` so OneDrive cannot lock generated
files. The canonical tiers and troubleshooting rules are in
`docs/VALIDATION.md` and must be followed by future sessions:

- `Fast` with explicit `-Tests` while implementing;
- `Commit` once for unit tests, lint, and debug APK assembly;
- `Release` once on stable source, adding the full screenshot matrix.

Do not routinely run `clean`, do not combine memory-heavy gates manually, and
do not update screenshot references unless the visual change is intentional and
the changed images are inspected. The verification script performs one bounded
automatic retry for recognized Windows locks or heap failures.
