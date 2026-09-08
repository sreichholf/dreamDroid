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

## Other traps

- `main` is the rewrite. Do not merge rewrite work into `master`.
- AGP 8.2 `jlink` fails on JDK 21.
- Two googleDebug processes cannot share one device.
