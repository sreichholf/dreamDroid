# dreamDroid agent notes

Phone Enigma2 remote. Rewrite trunk is `main`. Sources live in `app/src` and `app/res`, not `src/main`. Debug package is `net.reichholf.dreamdroid.debug`. CI builds with **JDK 25**; use it locally too (Gradle does not enforce it, app bytecode is Java 17).

**New code is Kotlin.** `app/src` has no Java sources. Do not add `.java` types under `app/src`. Prefer coroutines over executors/`AsyncTask`/`JobIntentService`. Do not add `@JvmStatic`/`@JvmOverloads`/`@JvmField` for Java callers.

**Style:** New Kotlin follows [Google’s Android Kotlin style guide](https://developer.android.com/kotlin/style-guide). Spotless + ktlint `android_studio` is the checker (`.editorconfig`). Run `./gradlew spotlessApply` on Kotlin you touch; `./gradlew spotlessCheck` is in CI (every Kotlin file under `app/src`, `app/test`, `app/androidTest`). Do not hand-retab or invent a house indent.

`.editorconfig` and ktlint are the source of truth for indent (4 spaces), the 100-column limit, braces, wrapping, import order (no wildcards), and semicolons. If `spotlessApply` changes your code, keep its output.

**Architecture:** new and touched code follows the target architecture in [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md#target-architecture): repositories + Hilt, ViewModels without `Application`/`Context` exposing `StateFlow` UI state, Material 3 `TopAppBar` (no `MenuProvider` / options menu), Snackbar instead of `Toast`, type-safe routes, DataStore. Do not copy a legacy pattern from neighboring code because it is still there; it is listed under remediation.

**Hilt** (migration in progress, see [`docs/hilt-migration.md`](docs/hilt-migration.md)):

- A binding, repository method, or module lands in the PR with its first consumer. No bindings "for later".
- Migrated screens use a `@HiltViewModel` taking repositories plus `SavedStateHandle`, obtained with `hiltViewModel()`. Their title and user message are UI state (`UiText`), reported with `ShellTitle` and `ShowShellUserMessage`, not `Activity.title` or `ShellMessages`.
- While a static holder (`SessionConnectionHolder.shared` and friends) still has callers, a Hilt `@Provides` returns that static instance. Delete the provider and the static with the last caller. `ProfileRepository` is already flipped: it has an `@Inject` constructor, and `ProfileRepository.get()` returns the Hilt instance.
- Hilt injects during `super.onCreate()` of `DreamDroid`, before the Room import runs. Constructors of bound classes must not touch the database.

Modernization plan: [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). UI look helper: [`.cursor/skills/verify-dreamdroid/SKILL.md`](.cursor/skills/verify-dreamdroid/SKILL.md).

## Subagents

Hand off broad exploration, investigation, and independent slices of work to subagents. Do small, focused tasks in the parent thread; a subagent starts cold and has to rebuild context.

**Never run subagents on a fast model variant.** In Cursor, do not pass a model slug that ends in `-fast`; `inherit` is allowed only when the parent model is not a fast variant. In Claude Code, do not spawn subagents while fast mode (`/fast`) is on.

## Change the tests when the design changes

A proper implementation is the goal. Do not keep a production type, or leave state on `remember` / `rememberSaveable`, so an existing test still compiles.

This is not allowed:

> Instrumented tests still construct `EpgBouquetSession` directly, so I'll keep that API and move only the saved list state onto the ViewModel.

If the list, the saved fields, and the load job belong on the `ViewModel`, put them there and update the tests. A `*Screen` composable that still takes state, so a UI test does not need a `ViewModelStore`, is fine. Keeping the old session as the owner of that state is not.

## Verify UI with instrumented tests

Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That path is slow and brittle (`About` matches `Settings & About`, Changelog contains `Profiles`, dumps miss color).

Default proof for Compose and in-app UI:

```bash
./gradlew :app:connectedGoogleDebugAndroidTest      # Linux / macOS
./gradlew.bat :app:connectedGoogleDebugAndroidTest  # Windows
```

Use `JAVA_HOME` pointing at JDK 25. Instrumented tests live in `app/androidTest/java`. ViewModels, repositories, and parsers also get JVM tests in `app/test` (`:app:testGoogleDebugUnitTest`) with fake repositories. Add Compose UI tests next to each new screen (`createComposeRule` / `createAndroidComposeRule`). Dialogs are Compose Material 3 / Navigation `dialog` destinations; host tests in composition or a NavHost `dialog` route. A `ComposeView` inside a View dialog must be tested in that host — a naked `setContent { }` will not catch `LocalContentColor` leaks from the View theme.

To run one test class, filter with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner` (see **Other traps** for what not to pass to Gradle).

CI is [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml); read it for what runs on which event. Before pushing, run what the PR job runs:

```bash
./gradlew -Pci spotlessCheck :app:testGoogleDebugUnitTest :app:compileGoogleDebugAndroidTestKotlin :app:lintGoogleDebug
```

The emulator job does not run on PRs; trigger it with `workflow_dispatch` when a change needs it before merge.

`verify-dreamdroid.py` exists for a shell-only dump when there is no instrumented test yet. It is not the verification loop.

## Cursor Cloud Agents

This section applies to Cursor Cloud Agent VMs only; Claude Code sessions are covered in the next section. Setup lives in [`.cursor/environment.json`](.cursor/environment.json) with scripts under `.cursor/cloud/`. `install.sh` installs JDK 25 + the Android SDK (build-tools 36, platform 34, `google_apis;x86_64` image), creates the `dreamdroid-verify` AVD, warms the Gradle build, and bakes a booted quickboot snapshot. `start.sh` boots that emulator each session.

**Do not visually drive the emulator in Cloud Agent sessions.** Do not use `computerUse`, GUI tapping, screenshot/recording walkthroughs of the phone UI, or `verify-dreamdroid.py launch` / adb tap loops to “look at” the app. Soft-accelerated TCG plus the agent display path is too slow and unreliable here; those attempts waste the session. Prove UI with instrumented tests (`bash .cursor/cloud/connected-test.sh …`) and log/output artifacts only.

Nested KVM guest execution hangs on Cursor Cloud VMs: `/dev/kvm` exists and `kvm-ok` passes, but under `-enable-kvm` the guest vCPU never runs (0% CPU, no kernel output). The emulator therefore runs under software (`-accel off`, TCG). It works but is slow. Set `DREAMDROID_EMU_ACCEL=auto` to try KVM on a host that supports nested virt.

Because of the slow emulator, the stock `:app:connectedGoogleDebugAndroidTest` task fails: UTP pushes the ~196 MB universal debug APK over ddmlib's sync protocol and the per-read socket timeout fires (it ignores `adbOptions.timeOutInMs`). On the Cloud VM, verify with the helper instead, which streams the standalone x86_64 APK and runs `am instrument` (see the `-Pci` trap below).

```bash
bash .cursor/cloud/connected-test.sh            # whole suite
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.ui.about.AboutScreenTest
```

## Claude Code

Claude Code reads this file through the one-line `CLAUDE.md` (`@AGENTS.md`). It finds skills in `.claude/skills/`, which is a symlink to `.agents/skills/`, so `poteto-mode` and the other pstack skills load by name. On Windows, enable symlinks (`git config core.symlinks true` with Developer Mode) or read `.agents/skills/<name>/SKILL.md` directly.

Claude Code on the web (cloud sessions) does **not** run `.cursor/environment.json` or `.cursor/cloud/*.sh`. Instead, the SessionStart hook [`.claude/hooks/session-start.sh`](.claude/hooks/session-start.sh) (registered in `.claude/settings.json`, remote sessions only) installs JDK 25 and the Android SDK (the platforms and build-tools CI uses) into `~/Android/Sdk`, writes `local.properties`, and exports `JAVA_HOME` / `ANDROID_HOME`. It does not install an emulator or system image. So:

- Run the PR job's Gradle checks (above) as usual. The hook also installs [`scripts/gradle/maven-central-mirror.init.gradle.kts`](scripts/gradle/maven-central-mirror.init.gradle.kts) into `~/.gradle/init.d`, which resolves Maven Central through Google's mirror (Sonatype answers `429 Too Many Requests` to the shared proxy IP). CI installs the same script. If a `429` still shows up, re-run; Gradle keeps what it already downloaded. Do not claim a check passed that did not run.
- `~/.gradle` lives as long as the container. For a warm cache across sessions, set the environment's setup script to [`scripts/cloud-env-setup.sh`](scripts/cloud-env-setup.sh) (the command is in its header); the environment snapshot then carries the JDK, SDK, and Gradle cache for about a week.
- Do not run `.cursor/cloud/connected-test.sh` or try to boot an emulator there.
- Proof of phone UI in a cloud session is: write or update the instrumented test and make it compile, then rely on CI. Run `workflow_dispatch` on `android-ci.yml` for the emulator job if the change needs it before merge.
- Keep the hook's SDK package list in step with `android-ci.yml` when CI changes platforms or build-tools.

Claude Code running locally on a machine with JDK 25, the SDK, and a device or emulator follows the normal rules above (`./gradlew :app:connectedGoogleDebugAndroidTest`).

## Other traps

- `main` is the rewrite. Do not merge rewrite work into `master`.
- Gradle 9.6 / AGP 9.4; run the build on JDK 25 (app bytecode stays Java 17).
- Two googleDebug processes cannot share one device.
- Lint fails on `UnusedResources` and `UnusedIds`, and spotless fails on unused imports. Delete what they flag. A resource only reached by name at runtime (like `resValue` in `app/build.gradle.kts`) goes in the `UnusedResources` ignore list in `app/lint.xml`.
- Do not pass `-Pandroid.testInstrumentationRunnerArguments...` to Gradle. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter with `adb shell am instrument -e class ...` instead.
- `-Pci` builds one fat APK so UTP can install it on GitHub Actions; `.cursor/cloud/connected-test.sh` deliberately uses the ABI-split x86_64 APK. The split is **intentional**; do not force the helper onto `-Pci` or drop `-Pci` from CI.
- Remaining modernization work (the remediation steps, anything still listed under **Still to do**, and the **Deliberate exceptions** that must not be "fixed") lives in [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). Do not quietly fold those into unrelated PRs.

<!-- potetos-for-everyone:begin -->
## Engineering workflow (poteto-mode)

For non-trivial engineering work, use the Agent Skill at `.agents/skills/poteto-mode/SKILL.md`.
It routes the task to a playbook, loads supporting skills progressively, prefers simple changes,
and requires evidence against the real artifact. Canonical skills are adapted from Lauren Tan's pstack.
If native skill discovery is unavailable, read that SKILL.md and its selected playbook manually.
dreamDroid rules above still govern build, style, and UI proof. On Cloud Agents, prove phone UI with instrumented tests, not emulator tapping.
<!-- potetos-for-everyone:end -->
