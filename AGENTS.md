# dreamDroid agent notes

Phone Enigma2 remote. Rewrite trunk is `main`. Sources live in `app/src` and `app/res`, not `src/main`. Debug package is `net.reichholf.dreamdroid.debug`. Build with **JDK 25**.

**New code is Kotlin.** Do not add new `.java` types for modernization work (helpers, UI, loaders, widgets). Edit existing Java surgically when needed; convert to Kotlin when touching a file heavily or extracting a new type. Prefer coroutines over executors/`AsyncTask`/`JobIntentService`.

**Indent:** preserve the file’s existing tabs-or-spaces. Most Java uses tabs; do not reindent whole files or expand tabs to spaces when editing. New Kotlin next to tabbed Java may use tabs. Avoid drive-by newline/brace restyles.

Modernization plan: [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). UI look helper: [`.cursor/skills/verify-dreamdroid/SKILL.md`](.cursor/skills/verify-dreamdroid/SKILL.md).

## Verify UI with instrumented tests

Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That path is slow and brittle (`About` matches `Settings & About`, Changelog contains `Profiles`, dumps miss color).

Default proof for Compose and in-app UI:

```bash
./gradlew.bat :app:connectedGoogleDebugAndroidTest
```

Use `JAVA_HOME` pointing at JDK 25. Tests live in `app/androidTest/java`. Add Compose UI tests next to each new screen (`createComposeRule` / `createAndroidComposeRule`). Dialogs are moving to Compose Material 3 / Navigation `dialog` destinations (see `docs/modernize-dreamdroid.md` Phase **2.1g-ii**); host tests in composition or a NavHost `dialog` route. While a DialogFragment/`ComposeView` host still exists, the test must use that host — a naked `setContent { }` will not catch `LocalContentColor` leaks from the View theme.

Do not pass `-Pandroid.testInstrumentationRunnerArguments...`. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter a class with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.

CI: `.github/workflows/android-ci.yml` — on every PR/`main` push: `:app:testGoogleDebugUnitTest`, androidTest compile (with `-Pci`); uploads arm64 `dreamdroid-google-debug-apk` (2-day retention). Emulator `connectedGoogleDebugAndroidTest` (API 30) runs on `main` pushes and manual `workflow_dispatch` only (slow/flaky on PR). Local cloud helper: `bash .cursor/cloud/connected-test.sh`.

`verify-dreamdroid.py` exists for a shell-only dump when there is no instrumented test yet. It is not the verification loop.

## Cloud Agent environment

Setup lives in [`.cursor/environment.json`](.cursor/environment.json) with scripts under `.cursor/cloud/`. `install.sh` installs JDK 25 + the Android SDK (build-tools 36, platform 34, `google_apis;x86_64` image), creates the `dreamdroid-verify` AVD, warms the Gradle build, and bakes a booted quickboot snapshot. `start.sh` boots that emulator each session.

**Do not visually drive the emulator in Cloud Agent sessions.** Do not use `computerUse`, GUI tapping, screenshot/recording walkthroughs of the phone UI, or `verify-dreamdroid.py launch` / adb tap loops to “look at” the app. Soft-accelerated TCG plus the agent display path is too slow and unreliable here; those attempts waste the session. Prove UI with instrumented tests (`bash .cursor/cloud/connected-test.sh …`) and log/output artifacts only.

Nested KVM guest execution hangs on Cursor Cloud VMs: `/dev/kvm` exists and `kvm-ok` passes, but under `-enable-kvm` the guest vCPU never runs (0% CPU, no kernel output). The emulator therefore runs under software (`-accel off`, TCG). It works but is slow. Set `DREAMDROID_EMU_ACCEL=auto` to try KVM on a host that supports nested virt.

Because of the slow emulator, the stock `:app:connectedGoogleDebugAndroidTest` task fails: UTP pushes the ~196 MB universal debug APK over ddmlib's sync protocol and the per-read socket timeout fires (it ignores `adbOptions.timeOutInMs`). On the Cloud VM, verify with the helper instead, which streams the standalone x86_64 APK and runs `am instrument`:

```bash
bash .cursor/cloud/connected-test.sh            # whole suite
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.ui.about.AboutScreenTest
```

## Other traps

- `main` is the rewrite. Do not merge rewrite work into `master`.
- Gradle 9.6 / AGP 9.4; run the build on JDK 25 (app bytecode stays Java 17).
- Two googleDebug processes cannot share one device.
