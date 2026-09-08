# dreamDroid agent notes

Phone Enigma2 remote. Rewrite trunk is `main`. Sources live in `app/src` and `app/res`, not `src/main`. Debug package is `net.reichholf.dreamdroid.debug`. Build with **JDK 17**.

Modernization plan: [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). UI look helper: [`.cursor/skills/verify-dreamdroid/SKILL.md`](.cursor/skills/verify-dreamdroid/SKILL.md).

## Verify UI with instrumented tests

Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That path is slow and brittle (`About` matches `Settings & About`, Changelog contains `Profiles`, dumps miss color).

Default proof for Compose and in-app UI:

```bash
./gradlew.bat :app:connectedGoogleDebugAndroidTest
```

Use `JAVA_HOME` pointing at JDK 17. Tests live in `app/androidTest/java`. Add Compose UI tests next to each new screen (`createComposeRule` / `createAndroidComposeRule`). If the UI is Compose inside an XML dialog or `ComposeView`, the test must host it that way — a naked `setContent { }` will not catch `LocalContentColor` leaks from the View theme.

Do not pass `-Pandroid.testInstrumentationRunnerArguments...`. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter a class with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.

`verify-dreamdroid.py` is for a single look when you need a screenshot or a shell-only path that has no test yet. It is not the verification loop.

## Cloud Agent environment

Setup lives in [`.cursor/environment.json`](.cursor/environment.json) with scripts under `.cursor/cloud/`. `install.sh` installs JDK 17 + the Android SDK (build-tools 34, platform 34, `google_apis;x86_64` image), creates the `dreamdroid-verify` AVD, warms the Gradle build, and bakes a booted quickboot snapshot. `start.sh` boots that emulator each session.

Nested KVM guest execution hangs on Cursor Cloud VMs: `/dev/kvm` exists and `kvm-ok` passes, but under `-enable-kvm` the guest vCPU never runs (0% CPU, no kernel output). The emulator therefore runs under software (`-accel off`, TCG). It works but is slow, so APK installs need a raised adb timeout (`~/.gradle/init.gradle` sets `adbOptions.timeOutInMs`). Set `DREAMDROID_EMU_ACCEL=auto` to try KVM on a host that supports nested virt.

## Other traps

- `main` is the rewrite. Do not merge rewrite work into `master`.
- AGP 8.2 `jlink` fails on JDK 21.
- Two googleDebug processes cannot share one device.
