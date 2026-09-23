# dreamDroid agent notes

Phone Enigma2 remote. Rewrite trunk is `main`. Sources live in `app/src` and `app/res`, not `src/main`. Debug package is `net.reichholf.dreamdroid.debug`. Build with **JDK 25**.

**New code is Kotlin.** `app/src` has no Java sources. Do not add `.java` types under `app/src`. Prefer coroutines over executors/`AsyncTask`/`JobIntentService`. Do not add `@JvmStatic`/`@JvmOverloads`/`@JvmField` for Java callers.

**Style:** New Kotlin follows [Google’s Android Kotlin style guide](https://developer.android.com/kotlin/style-guide). Spotless + ktlint `android_studio` is the checker (`.editorconfig`). Run `./gradlew spotlessApply` on Kotlin you touch; `./gradlew spotlessCheck` is in CI (full `app/**/*.kt` tree). Do not hand-retab or invent a house indent.

Hard rules (also in `.editorconfig`):
- 4 spaces, never tabs
- 100-character column limit (except `package` / `import` and unavoidable KDoc URLs)
- K&R braces (`{` on the same line); wrap long function signatures with one parameter per line and `)` on its own line at the same indent as `fun`
- ASCII-sorted imports; no wildcards
- One statement per line; no semicolons

Modernization plan: [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). UI look helper: [`.cursor/skills/verify-dreamdroid/SKILL.md`](.cursor/skills/verify-dreamdroid/SKILL.md).

## Subagents

Use subagents whenever they make sense. Hand off exploration, investigation, and independent slices of work instead of doing all of it in the parent thread.

## Change the tests when the design changes

A proper implementation is the goal. Do not keep a production type, or leave state on `remember` / `rememberSaveable`, so an existing test still compiles.

This is not allowed:

> Instrumented tests still construct `EpgBouquetSession` directly, so I'll keep that API and move only the saved list state onto the ViewModel.

If the list, the saved fields, and the load job belong on the `ViewModel`, put them there and update the tests. A `*Screen` composable that still takes state, so a UI test does not need a `ViewModelStore`, is fine. Keeping the old session as the owner of that state is not.

## Verify UI with instrumented tests

Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That path is slow and brittle (`About` matches `Settings & About`, Changelog contains `Profiles`, dumps miss color).

Default proof for Compose and in-app UI:

```bash
./gradlew.bat :app:connectedGoogleDebugAndroidTest
```

Use `JAVA_HOME` pointing at JDK 25. Tests live in `app/androidTest/java`. Add Compose UI tests next to each new screen (`createComposeRule` / `createAndroidComposeRule`). Dialogs are Compose Material 3 / Navigation `dialog` destinations; host tests in composition or a NavHost `dialog` route. A `ComposeView` inside a View dialog must be tested in that host — a naked `setContent { }` will not catch `LocalContentColor` leaks from the View theme.

Do not pass `-Pandroid.testInstrumentationRunnerArguments...`. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter a class with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.

CI: `.github/workflows/android-ci.yml` — on every PR/`main` push: `spotlessCheck`, `:app:testGoogleDebugUnitTest`, androidTest compile (with `-Pci`). Arm64 `dreamdroid-google-debug-apk` (2-day retention) uploads on `main` pushes, or `workflow_dispatch` with `upload_apk=true`. Emulator `connectedGoogleDebugAndroidTest` (API 30) on `main` pushes / `workflow_dispatch` (slow/flaky on PR). Local cloud helper: `bash .cursor/cloud/connected-test.sh`.

`verify-dreamdroid.py` exists for a shell-only dump when there is no instrumented test yet. It is not the verification loop.

## Cloud Agent environment

Setup lives in [`.cursor/environment.json`](.cursor/environment.json) with scripts under `.cursor/cloud/`. `install.sh` installs JDK 25 + the Android SDK (build-tools 36, platform 34, `google_apis;x86_64` image), creates the `dreamdroid-verify` AVD, warms the Gradle build, and bakes a booted quickboot snapshot. `start.sh` boots that emulator each session.

**Do not visually drive the emulator in Cloud Agent sessions.** Do not use `computerUse`, GUI tapping, screenshot/recording walkthroughs of the phone UI, or `verify-dreamdroid.py launch` / adb tap loops to “look at” the app. Soft-accelerated TCG plus the agent display path is too slow and unreliable here; those attempts waste the session. Prove UI with instrumented tests (`bash .cursor/cloud/connected-test.sh …`) and log/output artifacts only.

Nested KVM guest execution hangs on Cursor Cloud VMs: `/dev/kvm` exists and `kvm-ok` passes, but under `-enable-kvm` the guest vCPU never runs (0% CPU, no kernel output). The emulator therefore runs under software (`-accel off`, TCG). It works but is slow. Set `DREAMDROID_EMU_ACCEL=auto` to try KVM on a host that supports nested virt.

Because of the slow emulator, the stock `:app:connectedGoogleDebugAndroidTest` task fails: UTP pushes the ~196 MB universal debug APK over ddmlib's sync protocol and the per-read socket timeout fires (it ignores `adbOptions.timeOutInMs`). On the Cloud VM, verify with the helper instead, which streams the standalone x86_64 APK and runs `am instrument`. That ABI-split APK vs CI `-Pci` (one fat APK, needed so UTP can install on GHA) is **intentional** — do not force the helper onto `-Pci`.

```bash
bash .cursor/cloud/connected-test.sh            # whole suite
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.ui.about.AboutScreenTest
```

## Other traps

- `main` is the rewrite. Do not merge rewrite work into `master`.
- Gradle 9.6 / AGP 9.4; run the build on JDK 25 (app bytecode stays Java 17).
- Two googleDebug processes cannot share one device.
- Remaining modernization work (the intentional service-row track keep, and anything still listed under **Still to do**) lives in [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). Do not quietly fold those into unrelated PRs.
