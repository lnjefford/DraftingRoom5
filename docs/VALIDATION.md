# Validation workflow

Use `tools/verify.ps1` for routine and release validation on Windows. It provides
three deliberately different gates so the full screenshot matrix is not paid for
after every source edit.

## Tiers

```powershell
# During implementation: pass one or more directly affected JVM test patterns.
./tools/verify.ps1 -Tier Fast -Tests 'dev.draftingroom5.retirement.ForecastCoordinatorTest'

# Before committing a coherent change set.
./tools/verify.ps1 -Tier Commit

# Once, immediately before the version commit and tag.
./tools/verify.ps1 -Tier Release
```

`Fast` runs `git diff --check` and the requested unit-test filters. Without
`-Tests`, it intentionally runs the complete unit suite rather than guessing an
unsafe subset. `Commit` runs the unit suite, lint, and APK assembly as separate
Gradle invocations so D8, lint, and tests do not compete for heap. `Release` adds
the complete screenshot comparison after the commit gate.

For a known visual change, update only the relevant preview class, inspect the
changed PNGs, and then validate it:

```powershell
./tools/verify.ps1 -Tier Release -UpdateScreenshots `
  -ScreenshotTests 'dev.draftingroom5.ForecastScreenshotsKt*'
```

Omit `-ScreenshotTests` only when the complete visual baseline genuinely needs
refreshing. Never accept reference changes solely to make validation green.

## Saved-data compatibility gate

Every released app-document schema must have an immutable serialized fixture under
`app/src/test/resources/compatibility/`. `ReleasedDocumentCompatibilityTest` must
decode every fixture, validate the complete domain object, and re-encode it into
the current explicit schema version. Schema-changing work must add a new fixture
and upgrader before release; it must not rewrite or remove older fixtures merely
to make the current codec pass. Unknown future versions and mixed-schema payloads
must continue to fail closed. The same fixtures cover documents embedded in local
recovery backups, and repository tests must prove that an upgrade is written
atomically only after successful decoding and validation.

## Stable Windows build state

`gradlew.ps1` derives a key from the checkout path and places generated state at
`%LOCALAPPDATA%\DraftingRoom5`:

- `gradle-user-home` contains downloaded Gradle dependencies;
- `android-user-home` contains Android tooling state;
- `repositories/<key>/project-cache` replaces the checkout's `.gradle` cache;
- `repositories/<key>/build` replaces `build` and `app/build`.

This keeps high-churn generated files outside OneDrive while leaving source and
screenshot references in the repository. Linux/macOS and GitHub Actions retain
their normal in-repository build paths because they do not set `DR5_BUILD_ROOT`.

The checked-in Gradle defaults use a 3 GiB heap, at most two workers, in-process
Kotlin compilation, no VFS watcher, a 2 GiB screenshot renderer, and build/config
caching. The verification script additionally runs memory-heavy gates
sequentially. If it recognizes a transient Windows lock or heap failure, it stops
Gradle and retries that one gate once with a single worker. A second failure is
reported rather than hidden.

Do not run `clean` as routine validation. The isolated output is safe to reuse,
and incremental builds are materially faster. Use `./gradlew.ps1 --stop` only
for a confirmed stuck external process; the verification script handles known
recoverable cases itself.
