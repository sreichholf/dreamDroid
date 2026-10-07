# dreamDroid agent notes

Phone Enigma2 remote. Rewrite trunk is `main` (`master` is the old 1.x line; never merge rewrite work into it). Sources live in `app/src` and `app/res`, not `src/main`. Debug package is `net.reichholf.dreamdroid.debug`. Gradle 9.6.1 / AGP 9.4.1 / Kotlin 2.4.20; CI builds with **JDK 25** — use it locally too (Gradle does not enforce it; app bytecode stays Java 17).

Modernization plan and remaining work: [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). Hilt history and decisions: [`docs/hilt-migration.md`](docs/hilt-migration.md). CI: [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) — read it for what runs on which event.

## Before you open a PR

1. `./gradlew spotlessApply` on every Kotlin file you touched; keep its output.
2. `./gradlew -Pci --no-configuration-cache :app:prCheck` (`gradlew.bat` on Windows) — the exact checks the PR jobs run: spotless, JVM tests, androidTest compile, lint. CI runs the same tasks, so do not assemble the task list from memory.
3. Phone UI changed? Write or update the instrumented test (see **Tests**). Run it locally on a device/emulator, or trigger `workflow_dispatch` on `android-ci.yml` — the emulator job does not run on PRs.
4. Touched Room, DataStore, legacy SharedPreferences, or profile import/export? Run `workflow_dispatch` with `upgrade_from_115` (see **Data migrations**).
5. User-visible change? Add a line to both changelogs (see **Changelog and strings**).
6. Completed a remediation step from `docs/modernize-dreamdroid.md`? Tick it off in that doc in the same PR.
7. Keep commit messages and PR descriptions to the essence of the change: what changed and why, short and precise. No file-by-file tour or restated diff. Name the checks you ran and their result in a line or two. **Do not claim a check passed that did not run.** If a check could not run where you are (no emulator, network 429), say so and say what covers it instead.

## Where things are

- `app/src/net/reichholf/dreamdroid/` — app code. `ui/<feature>/` (phone Compose screens, ViewModels, destinations: `services`, `epg`, `movies`, `timers`, `zap`, `pick`, `settings`, …), `ui/nav/` (shell, routes), `tv/` (Android TV hub), `data/` (repositories, `CacheFirstLoad.kt`), `di/` (Hilt modules), `room/` (`AppDatabase`, entities, migrations), `enigma/` (receiver models), `helpers/` (HTTP, errors), `appwidget/`, `video/`, `multiepg/`.
- Shell/UI-state helpers used below: `UiText` and `SavedTextField` in `ui/text/`; `ShellTitle` in `ui/nav/ShellTopBar.kt`; `ShowShellUserMessage` in `ui/nav/ShellUserMessage.kt`.
- `app/test/java/` — JVM tests (`:app:testGoogleDebugUnitTest`); fakes and `TestProfiles` under `testutil/`.
- `app/androidTest/java/` — instrumented tests; runner `net.reichholf.dreamdroid.testutil.HiltTestRunner`. Both test roots are named `java` for historical reasons and contain only Kotlin; the Kotlin-only rule applies to them.
- `app/schemas/net.reichholf.dreamdroid.room.AppDatabase/<version>.json` — exported Room schemas, committed.
- `app/res/raw/changelog.md`, `app/res/raw-de/changelog.md` — in-app changelogs (EN/DE). `fastlane/metadata/android/` holds store descriptions and screenshots only, no changelogs.
- `scripts/` — helper scripts shared by CI and the agent hooks. `.cursor/`, `.claude/`, `.agents/` — per-tool setup and skills.
- Design docs in `docs/`: `modernize-dreamdroid.md` (plan, target architecture, deliberate exceptions), `offline-and-errors.md` (what is cached and who decides), `hilt-migration.md`, `multiepg.md`, `openwebif.md`, `autotimer.md`, `vps.md`. Read the one for the area you touch before changing behavior there.

## Code rules

**New code is Kotlin.** No Java sources under `app/src`, `app/test`, or `app/androidTest`; do not add `.java` types. Prefer coroutines over executors/`AsyncTask`/`JobIntentService`. Do not add `@JvmStatic`/`@JvmOverloads`/`@JvmField` for Java callers.

**Style:** [Google's Android Kotlin style guide](https://developer.android.com/kotlin/style-guide), enforced by Spotless + ktlint `android_studio` with `.editorconfig` as the source of truth (4-space indent, 100 columns, braces, wrapping, import order without wildcards, semicolons). `spotlessCheck` runs in CI over `app/src`, `app/test`, `app/androidTest`. Do not hand-retab or invent a house indent; if `spotlessApply` changes your code, keep its output.

**Architecture:** new and touched code follows the target architecture in [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md#target-architecture): repositories + Hilt, ViewModels without `Application`/`Context` exposing `StateFlow` UI state, Material 3 `TopAppBar` (no `MenuProvider` / options menu), Snackbar instead of `Toast`, type-safe routes, DataStore. Do not copy a legacy pattern from neighboring code because it is still there; it is listed under remediation.

**Repositories decide between Room and the receiver; ViewModels and screens do not.** A list is read cache-first through `data/CacheFirstLoad.kt`: Room paints first, the receiver is skipped while the session is Offline and Room had the list, and Room is the fallback when the receiver fails. A ViewModel collects that flow and passes only `forceRefresh` (pull-to-refresh, Retry, reload after a write). It does not call `cached*()` itself, does not read `sessions.status` to choose between Room and HTTP, and does not hold in-memory copies of receiver data as the "known" value. Composables hold UI state only in `remember` / `rememberSaveable` (scroll, expansion, text field state), never backend data. Which datasets are cached, and the lists that are deliberately not cache-first, are in [`docs/offline-and-errors.md` §4.4](docs/offline-and-errors.md#44-datasets).

This is not allowed (it was the pattern in several ViewModels until PR #577 moved it into the repositories):

> `loadBouquets()` reads `services.cachedBouquets()`, checks `sessions.status.value.shouldSkipReceiverHttp(hasStrip)`, and only then calls the receiver — so the hub can paint before the HTTP timeout.

Put the read in the repository as a `cacheFirstLoad` flow and collect it.

**Material 3 first:** where Material 3 defines a design or behavior, use its default: the component, its `*Defaults`, its state holders and scroll behaviors (for example `TopAppBarDefaults.enterAlwaysScrollBehavior`, `BottomAppBarDefaults.exitAlwaysScrollBehavior`, `rememberTopAppBarState`). Do not hand-write what the library already covers. Write custom code only for what Material 3 does not define, and say in the PR why the library does not fit.

**Hilt** (the app and its instrumented tests run on Hilt):

- A binding, repository method, or module lands in the PR with its first consumer. No bindings "for later".
- Migrated screens use a `@HiltViewModel` taking repositories plus `SavedStateHandle`, obtained with `hiltViewModel()`. Their title and user message are UI state (`UiText`), reported with `ShellTitle` and `ShowShellUserMessage`, not `Activity.title`.
- No static service locators. Get dependencies by injection: `@Inject` constructors and fields, `hiltViewModel()`, or an `@EntryPoint` where Android or Glance creates the object (widget, picon loader, worker).
- Text input state is a `TextFieldState` owned by the ViewModel (`SavedTextField` keeps it in `SavedStateHandle`), rendered with state-based text fields, not a String in the UiState `StateFlow`.
- Hilt injects during `super.onCreate()` of `DreamDroid`, before the Room import runs. Constructors of bound classes must not touch the database.

## Tests

**Where a test goes:**

| What you changed | Test | Task |
| --- | --- | --- |
| ViewModel, repository, parser, mapper | JVM test with fake repositories in `app/test` | `:app:testGoogleDebugUnitTest` |
| A Compose screen or dialog | Compose UI test next to the screen in `app/androidTest` (`createComposeRule` / `createAndroidComposeRule`) | `:app:connectedGoogleDebugAndroidTest` |
| Navigation, Hilt wiring, anything needing a real `Activity` | Instrumented test in `app/androidTest` | `:app:connectedGoogleDebugAndroidTest` |

There is no Robolectric in the build; Compose tests are instrumented. If that changes, update the second row of this table and nothing else.

Instrumented tests are the default proof for Compose and in-app UI. Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop; that path is slow and brittle (`About` matches `Settings & About`, Changelog contains `Profiles`, dumps miss color). `verify-dreamdroid.py` exists for a shell-only dump when there is no instrumented test yet; it is not the verification loop. UI look helper: [`.cursor/skills/verify-dreamdroid/SKILL.md`](.cursor/skills/verify-dreamdroid/SKILL.md).

```
./gradlew :app:connectedGoogleDebugAndroidTest      # Linux / macOS
./gradlew.bat :app:connectedGoogleDebugAndroidTest  # Windows
```

Use `JAVA_HOME` pointing at JDK 25. Dialogs are Compose Material 3 / Navigation `dialog` destinations; host tests in composition or a NavHost `dialog` route. A `ComposeView` inside a View dialog must be tested in that host — a naked `setContent { }` will not catch `LocalContentColor` leaks from the View theme.

**CI runs instrumented tests on `aosp_atd`, API 30, x86_64, headless (no Play / Google APIs).** A test must not depend on Google Play services or a Google-APIs-only feature, and API 30 is the only level exercised in CI (`minSdk` 26, `targetSdk` 37; the Cursor Cloud VM boots `system-images;android-34;google_apis;x86_64` instead). The suite runs in 3 shards (`-PtestShardCount=3 -PtestShardIndex=i`; `app/build.gradle.kts` maps them to `numShards`/`shardIndex`).

**Tests wait for a signal, never for time.** Do not use `Thread.sleep`, `delay(n)`, `SystemClock.sleep` or a poll-with-delay loop when there is something to await:

- `flow.first { … }` on the `StateFlow` / UiState, wrapped in `withTimeout` as an upper bound;
- `advanceUntilIdle()` / `runCurrent()` under a test dispatcher;
- `composeRule.waitUntil { … }` / `waitForIdle()`;
- a `CompletableDeferred` or latch completed by the fake;
- `MockWebServer.takeRequest(timeout)`.

To show that something does *not* happen, await a later signal that must follow it, or drive the code with a test dispatcher. Do not sleep and assert.

To run one test class, filter with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/net.reichholf.dreamdroid.testutil.HiltTestRunner` (see **Other traps** for what not to pass to Gradle).

**Change the tests when the design changes.** A proper implementation is the goal. Do not keep a production type, or leave state on `remember` / `rememberSaveable`, so an existing test still compiles. This is not allowed:

> Instrumented tests still construct `EpgBouquetSession` directly, so I'll keep that API and move only the saved list state onto the ViewModel.

If the list, the saved fields, and the load job belong on the `ViewModel`, put them there and update the tests. A `*Screen` composable that still takes state, so a UI test does not need a `ViewModelStore`, is fine. Keeping the old session as the owner of that state is not.

## Build modes and CI

Three ways to build the APK; each exists for a reason, do not unify them:

| Flag | Output | Used by |
| --- | --- | --- |
| *(none)* | ABI splits (one APK per ABI) | release, `upgrade115` job, `.cursor/cloud/connected-test.sh` (streams the x86_64 split) |
| `-Pci` | one fat universal APK (~196 MB) | emulator job on GitHub Actions, so UTP can install one APK |
| `-Parm64Apk` | one arm64-v8a APK (~57 MB) | `unit` and `lint` jobs; `unit` uploads it as the `dreamdroid-google-debug-apk` artifact on PRs and `main` pushes (phone smoke test without a local build) and on `workflow_dispatch` with `upload_apk` |

**PR jobs (`unit`, `lint`)** — two parallel jobs on every PR. `unit` runs `./gradlew --no-configuration-cache -Parm64Apk :app:prTests :app:assembleGoogleDebug`; `lint` runs `:app:prLint` (spotless + Android lint) with the same flags. `prCheck` is `prTests` + `prLint`; all three are in `app/build.gradle.kts`, so change the check lists there, nowhere else. ABI splits only affect packaging, so `-Pci` and `-Parm64Apk` run the same checks. Lint errors fail the build (`abortOnError`); warnings are reported only. `main` pushes run `unit` only, since the merged PR ran lint, and save Gradle's build cache for the next PRs. Shared setup (JDK, SDK, Gradle, caches) is the composite action `.github/actions/setup-build`. Runs with `--no-configuration-cache` on purpose (restoring a config-cache across runners cost more than it saved).

**Emulator job (`androidTest`)** — on `main` pushes and `workflow_dispatch`, not on PRs. Three shards boot from a cached AVD snapshot that `.github/emulator/settle-snapshot.sh` takes only after the framework is up. Bump `AVD_SNAPSHOT_VERSION` in `android-ci.yml` (the cache key suffix) whenever the snapshot step or that script changes. If your change needs the emulator before merge, trigger the dispatch and link the run in the PR.

**Upgrade job (`build115` + `upgrade115`)** — `workflow_dispatch` with `upgrade_from_115`: builds the last 1.x release (`v1.15.460`, JDK 17) and does a real `adb install -r` upgrade to the current build on the emulator, then runs `.github/upgrade-from-115/run.sh`. Both builds share `debug.keystore` and the debug package, which is what makes the in-place upgrade real. ABI splits are **on** for this job (no `-Pci`) so the x86_64 split's `versionCode` beats 1.15's, as on Play.

Maven Central: Sonatype rate-limits per egress IP (`429 Too Many Requests` on shared runners and the Claude Code web proxy). CI and the agent hooks install [`scripts/gradle/maven-central-mirror.init.gradle.kts`](scripts/gradle/maven-central-mirror.init.gradle.kts) into `~/.gradle/init.d`, which resolves Maven Central through Google's mirror. If a `429` still shows up, re-run; Gradle keeps what it already downloaded.

## Data migrations

The 1.15 → 2.0 upgrade over live user data is a release gate. If you touch any of these, run the upgrade dispatch before merge and say so in the PR:

- `room/AppDatabase.kt` (currently `version = 12`, `exportSchema = true`): any entity, DAO or index change bumps `version`, adds an explicit `MIGRATION_<n>_<n+1>` to `addMigrations(...)`, and commits the new `app/schemas/.../<n+1>.json` that KSP writes. There is no `fallbackToDestructiveMigration` and none may be added; users' offline cache and profiles live in this file.
- `DatabaseHelper.kt` and the legacy-profile import that `DreamDroid.onCreate()` runs after Hilt injection (the pre-2.0 SQLite profile store); `DreamDroidBackupAgent.kt`.
- Settings keys (`PreferenceManager` defaults / DataStore) and the backup/restore screen under `ui/backup/`.

## Changelog and strings

- A user-visible change gets one line in `app/res/raw/changelog.md` **and** `app/res/raw-de/changelog.md`. The whole unreleased 2.0 is one entry, `## 2.0.465`, with `* NEW:` / `* UPD:` / `* FIX:` / `* DEL:` lines (`NEU:` / `UPD:` / `FIX:` / `DEL:` in German; `DEV:`/`TEC:` for developer-facing notes). Do not start a new version heading; the maintainer does that at release.
- New strings go in `app/res/values/strings.xml` in English and `app/res/values-de/strings.xml` in German, in the same PR. The other locales (`values-ar` … `values-zh`) are left to translators; lint's `MissingTranslation` is set to `ignore` in `app/lint.xml`, `ExtraTranslation` too.
- Removing or renaming a string: remove it from every `values-*` folder it exists in, or `UnusedResources` (severity `error`) fails lint.

## Subagents

Hand off broad exploration, investigation, and independent slices of work to subagents. Do small, focused tasks in the parent thread; a subagent starts cold and has to rebuild context — point it at **Where things are** above.

**Never run subagents on a fast model variant.** In Cursor, do not pass a model slug that ends in `-fast`; `inherit` is allowed only when the parent model is not a fast variant. In Claude Code, do not spawn subagents while fast mode (`/fast`) is on.

## Cursor Cloud Agents

This section applies to Cursor Cloud Agent VMs only; Claude Code sessions are covered in the next section. Setup lives in [`.cursor/environment.json`](.cursor/environment.json) with scripts under `.cursor/cloud/`. `install.sh` installs JDK 25 + the Android SDK (the platforms and build-tools from `scripts/android-sdk-packages.txt`, the same list CI uses, plus `system-images;android-34;google_apis;x86_64`), creates the `dreamdroid-verify` AVD, warms the Gradle build, and bakes a booted quickboot snapshot. `start.sh` boots that emulator each session.

**Do not visually drive the emulator in Cloud Agent sessions.** Do not use `computerUse`, GUI tapping, screenshot/recording walkthroughs of the phone UI, or `verify-dreamdroid.py launch` / adb tap loops to "look at" the app. Soft-accelerated TCG plus the agent display path is too slow and unreliable here; those attempts waste the session. Prove UI with instrumented tests (`bash .cursor/cloud/connected-test.sh …`) and log/output artifacts only.

Nested KVM guest execution hangs on Cursor Cloud VMs: `/dev/kvm` exists and `kvm-ok` passes, but under `-enable-kvm` the guest vCPU never runs (0% CPU, no kernel output). The emulator therefore runs under software (`-accel off`, TCG). It works but is slow. Set `DREAMDROID_EMU_ACCEL=auto` to try KVM on a host that supports nested virt.

Because of the slow emulator, the stock `:app:connectedGoogleDebugAndroidTest` task fails: UTP pushes the fat `-Pci` APK over ddmlib's sync protocol and the per-read socket timeout fires (it ignores `adbOptions.timeOutInMs`). On the Cloud VM, verify with the helper instead, which streams the ABI-split x86_64 APK and runs `am instrument`:

```
bash .cursor/cloud/connected-test.sh            # whole suite
bash .cursor/cloud/connected-test.sh net.reichholf.dreamdroid.ui.about.AboutScreenTest
```

## Claude Code

Claude Code reads this file through the one-line `CLAUDE.md` (`@AGENTS.md`). It finds skills in `.claude/skills/`, which is a symlink to `.agents/skills/`, so `poteto-mode` and the other pstack skills load by name. On Windows, enable symlinks (`git config core.symlinks true` with Developer Mode) or read `.agents/skills/<name>/SKILL.md` directly.

Claude Code on the web (cloud sessions) does **not** run `.cursor/environment.json` or `.cursor/cloud/*.sh`. Instead, the SessionStart hook [`.claude/hooks/session-start.sh`](.claude/hooks/session-start.sh) (registered in `.claude/settings.json`, remote sessions only) installs JDK 25 and the Android SDK into `~/Android/Sdk` (the packages in `scripts/android-sdk-packages.txt`), writes `local.properties`, exports `JAVA_HOME` / `ANDROID_HOME`, and installs the Maven Central mirror init script. It does not install an emulator or system image. So:

- Run `./gradlew -Pci --no-configuration-cache :app:prCheck` as usual.
- `~/.gradle` lives as long as the container. For a warm cache across sessions, set the environment's setup script to [`scripts/cloud-env-setup.sh`](scripts/cloud-env-setup.sh) (the command is in its header); the environment snapshot then carries the JDK, SDK, and Gradle cache for about a week.
- Do not run `.cursor/cloud/connected-test.sh` or try to boot an emulator there.
- Proof of phone UI in a cloud session is: write or update the instrumented test and make it compile (`:app:compileGoogleDebugAndroidTestKotlin` is part of `:app:prCheck`), then rely on CI. Run `workflow_dispatch` on `android-ci.yml` for the emulator job if the change needs it before merge, and say in your report that the test compiled but did not run here.

Claude Code running locally on a machine with JDK 25, the SDK, and a device or emulator follows the normal rules above (`./gradlew :app:connectedGoogleDebugAndroidTest`).

## Other traps

- Keep feature and implementation branches current by rebasing them onto `origin/main`, not by merging `main` into them; then force-push with `--force-with-lease`. A branch merged back into `main` should not carry merges of `main`.
- Two googleDebug processes cannot share one device.
- Lint fails on `UnusedResources` and `UnusedIds`, and spotless fails on unused imports. Delete what they flag. A resource only reached by name at runtime (like `resValue` in `app/build.gradle.kts`) goes in the `UnusedResources` ignore list in `app/lint.xml`.
- Do not pass `-Pandroid.testInstrumentationRunnerArguments...` to Gradle. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter with `adb shell am instrument -e class ...` instead; shard with `-PtestShardCount=N -PtestShardIndex=i`.
- `-Pci` and the ABI-split path in `.cursor/cloud/connected-test.sh` are both **intentional** (see **Build modes and CI**). Do not force the helper onto `-Pci` or drop `-Pci` from CI.
- The SDK package list (platforms and build-tools) lives in one place, `scripts/android-sdk-packages.txt`, read by the CI composite action `.github/actions/ensure-android-sdk`, the Claude Code hook, and the Cursor install script. Change it there only. The callers share nothing else: each finds or bootstraps `sdkmanager` itself and adds its own extras (`platform-tools` in the hook; `platform-tools`, `emulator` and the system image in the Cursor script).
- Remaining modernization work (the remediation steps, anything still listed under **Still to do**, and the **Deliberate exceptions** that must not be "fixed") lives in [`docs/modernize-dreamdroid.md`](docs/modernize-dreamdroid.md). Do not quietly fold those into unrelated PRs.

## Engineering workflow (poteto-mode)

For non-trivial engineering work, use the Agent Skill at `.agents/skills/poteto-mode/SKILL.md`. It routes the task to a playbook, loads supporting skills progressively, prefers simple changes, and requires evidence against the real artifact. Canonical skills are adapted from Lauren Tan's pstack. If native skill discovery is unavailable, read that SKILL.md and its selected playbook manually. dreamDroid rules above still govern build, style, and UI proof. On Cloud Agents, prove phone UI with instrumented tests, not emulator tapping.
