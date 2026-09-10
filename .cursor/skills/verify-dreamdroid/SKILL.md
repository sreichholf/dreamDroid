---
name: verify-dreamdroid
description: Prove dreamDroid phone UI. Prefer instrumented Compose tests. Use adb dumps only for a single look or a shell path that has no test yet.
---

# Verify dreamDroid

dreamDroid is a phone/tablet Enigma2 remote (`net.reichholf.dreamdroid`). **Default proof is instrumented tests**, not tapping the emulator. See `AGENTS.md`.

```bash
./gradlew.bat :app:connectedGoogleDebugAndroidTest
```

Use **JDK 17** (`JAVA_HOME`). AGP 8.2's `jlink` transform fails on JDK 21. Do not pass `-Pandroid.testInstrumentationRunnerArguments...` — that sets Gradle property `android` to a String and breaks `android.applicationVariants`. Filter a class with:

```bash
adb shell am instrument -w -e class net.reichholf.dreamdroid.ui.about.AboutScreenTest net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Tests live in `app/androidTest/java`. Add Compose UI tests next to each new screen. If the UI is Compose inside an XML dialog or `ComposeView`, host it that way in the test.

`verify-dreamdroid.py` is for a **single look** (screenshot) or a shell-only path that has no instrumented test yet. It is not the verification loop.

Helper (from repo root):

```bash
python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py <command>
```

On Windows use the same command. The script finds `adb` on `PATH`, in `ADB`, or in `sdk.dir` from `local.properties`.

Default package: `net.reichholf.dreamdroid.debug` (googleDebug `applicationIdSuffix`). Never drive `net.reichholf.dreamdroid` (Play/F-Droid release) or the Amazon id.

Set `ANDROID_SERIAL` when more than one device is attached. Two googleDebug instances cannot run side by side on one device; debug vs release can.

Read [features/README.md](features/README.md) before driving. Exercise the mapped user path, not internal setters or HTTP clients.

## Launch (optional look)

1. `JAVA_HOME` at JDK 17. `gradle.properties` must not pass `-XX:MaxPermSize` or CMS flags.
2. Install if needed: `./gradlew.bat :app:installGoogleDebug`
3. Disposable session: `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py launch --clear-data`

Ready when `pidof net.reichholf.dreamdroid.debug` returns a pid and the helper prints `started ... pid=...`. First start opens the Profiles screen and the navigation drawer. Seeded profile name is `Demo` (host `dreamdroid.org`). That host is not an offline mock: bouquets, EPG, zap, and remote still need a reachable Enigma2 WebInterface unless the recipe says otherwise.

Teardown is `cleanup` below. Do not `am force-stop` or `pm uninstall` by the release package name.

## Doctor

```bash
python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py doctor
```

Pass only when an adb device is in `device` state, `net.reichholf.dreamdroid.debug` is installed and in the foreground, and it has a pid. If doctor fails, relaunch the debug package; do not tap a user-owned release install.

## Drive (optional look)

Prefer resource ids and visible text over coordinates. `tap --text About` matches **Settings & About** first; scroll the drawer and match text exactly `About`.

| User control | Handle |
| --- | --- |
| Open drawer | content-desc `Open navigation drawer` (English) |
| Drawer profile row | `net.reichholf.dreamdroid.debug:id/drawer_profile` |
| Profile name / status | `...:id/drawer_profile_name`, `...:id/drawer_profile_status` |
| TV & Movies | text `TV & Movies` |
| EPG / Virtual Remote / Zap / Current event | text `EPG`, `Virtual Remote`, `Zap`, `Current event` |
| About | text `About` (opens a dialog) |
| Add Profile FAB | content-desc from `R.string.profile_add` (Compose Scaffold FAB; not XML `fab_main`) |
| Autodiscovery | text `Dreambox Autodiscovery` |
| TV/Radio/Movies/Timer tabs | text `TV`, `Radio`, `Movies`, `Timer` |

- First-start after `--clear-data` shows Changelog. Dismiss with `back` before driving Profiles or the drawer. The changelog text contains the word `Profiles`, so `wait-text "Profiles"` is a false positive until the dialog is gone.

Receiver-backed screens (TV & Movies, Zap, EPG, remote keypresses) are unverified when the profile host does not answer. Report the unmet precondition; do not call a connection-error snackbar a successful zap.

## Evidence

Instrumented test output is the proof for Compose screens. Optional look artifacts live in `.cursor/skills/verify-dreamdroid/artifacts/` (gitignored). Cleanup must not delete them.

```bash
python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py dump --path .cursor/skills/verify-dreamdroid/artifacts/<feature>/ui.xml
python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py screenshot --path .cursor/skills/verify-dreamdroid/artifacts/<feature>/screen.png
```

## Cleanup

```bash
python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py cleanup
```

Stops the debug package started by `launch` (`kill` of the recorded pid, then `am force-stop` of `net.reichholf.dreamdroid.debug` only). Removes the device-side dump file. Leaves `artifacts/` on disk.

## Helpers

`scripts/verify-dreamdroid.py` subcommands: `doctor`, `launch [--clear-data]`, `dump [--path]`, `screenshot --path`, `tap (--text|--desc|--resource-id)`, `wait-text TEXT`, `contains TEXT`, `back`, `cleanup`.
