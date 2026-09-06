# Delivery workflow

After each completed set of changes, run the relevant checks, commit the changes,
push to GitHub, and create and push a new increasing version tag. Follow the
tag-triggered release build through completion and verify that its APK is
published. Keep the app's default version name and code consistent with the new
release. Do not release each intermediate edit separately.

This is the user's standing preference; no additional confirmation is needed
for these delivery steps. Never commit signing keys, credentials, or generated
build files.
