# Hilt migration plan (B1, with C2 and B4)

**Status:** decisions accepted 2026-09-28 (see **Decisions**). PRs 1–13 and 14a merged; 14b in review. Progress is tracked in **Progress** below.
**Scope:** remediation items B1 (Hilt), C2 (ViewModel shape), and B4 (repositories) in [`modernize-dreamdroid.md`](modernize-dreamdroid.md). The modernization doc already says Hilt lands with the first C2 ViewModel, not alone. This plan orders the whole wave into PRs.

## End state

The wave is done when all of these hold:

- `DreamDroid` is `@HiltAndroidApp`. Every activity that hosts a ViewModel or needs a dependency is `@AndroidEntryPoint`.
- Every screen ViewModel is `@HiltViewModel`, gets its instance from `hiltViewModel()`, and takes repositories plus `SavedStateHandle`. No `AndroidViewModel`, no `Application`/`Context`, no custom `ViewModelProvider.Factory`.
- Each ViewModel exposes one `StateFlow<*UiState>`. User messages and the destination title are part of that state. Strings are `@StringRes` / `UiText` and get resolved in the UI.
- These are gone: `ProfileRepository.get()`/`install()`, `SessionConnectionHolder.shared`, the `AppDatabase` static accessors (except the builder used by tests), `MultiEpgSyncHolder`, `EnigmaOkHttp` as an `object`, `DreamDroid.getAppContext()`, the `EnigmaClient()` / `EnigmaHttp()` no-profile defaults, the `enigma/*Load.kt` helpers that take a `Context`, and `ShellMessages`.
- The compiler enforces all of this: once the statics are deleted, code that reaches for them does not build. No extra lint rule needed.

## Where we start (2026-09-28, `main` @ `c82af8c`)

| Global access | Files using it (`app/src`) |
| --- | --- |
| `AndroidViewModel` subclasses | 31 (phone 26, TV 5) |
| `ProfileRepository.get()` | 44 |
| `SessionConnectionHolder.shared` | 32 |
| `AppDatabase.<accessor>(context)` | 30 |
| `EnigmaClient()` / `EnigmaHttp()` / `loadX(context)` | ~28 |
| `DreamDroid.getAppContext()` | 6 calls in 5 files |
| `MultiEpgSyncHolder.shared` | 4 |
| Custom VM factories | `ShellViewModel.Factory`, `TvHubViewModel.Factory` |
| Instrumented tests touching those statics | 59 references |

Android components outside activities: `PiconSyncWorker` (WorkManager), `VirtualRemoteWidgetProvider` + `VirtualRemoteWidget` (Glance), `VirtualRemoteWidgetConfiguration` (activity), `DreamDroidBackupAgent`, `VLCInstance`.

## Rules for every PR in this wave

1. **Vertical slices.** Each PR moves one screen group: its ViewModels, the repository methods they need, the screen tests, and a JVM ViewModel test. It does not sweep one singleton across the codebase. A singleton cannot be swept early anyway: its callers include composables and `*Load.kt` helpers, which only lose it once their ViewModel owns the call.
2. **No unused additions.** A binding, repository method, qualifier, or artifact lands in the PR that has the first consumer for it. PR 1 does not add an `AppDatabase` binding, because Device info does not use the database.
3. **One instance per process at all times.** While a static accessor still has callers, the Hilt binding and the static return the same object. Pattern below.
4. **Delete with the last caller.** The PR that moves the last caller of a static, `*Load.kt` helper, or `*Snapshot`/`*Cache` object deletes it. There is no separate cleanup PR unless something is left over at the end (PR 14).
5. **Unmigrated screens keep working.** An `@AndroidEntryPoint` activity's default factory is `HiltViewModelFactory`. It hands non-`@HiltViewModel` classes to the normal delegate factory, and `viewModel()` inside a `NavHost` still uses the back-stack entry's own factory. So `AndroidViewModel` screens that have not moved yet keep working next to migrated ones.
6. **Proof per PR:** the PR job (`./gradlew -Pci spotlessCheck :app:testGoogleDebugUnitTest :app:compileGoogleDebugAndroidTestKotlin :app:lintGoogleDebug`). Also a new JVM ViewModel test, updated `*Screen` tests, and a `workflow_dispatch` run of the emulator job before merge. PRs 1, 3, 11, 12, 13, and 14 change activities or the Application class, so they also get a `googleRelease` (R8) smoke on a device.
7. Each PR moves its item into **Done** in `modernize-dreamdroid.md` and ticks it here.

### Transitional pattern for existing singletons

While a static accessor still has callers, Hilt wraps it. The static does not wrap Hilt:

```kotlin
// temporary, deleted when the last ProfileRepository.get() caller moves
@Provides @Singleton
fun profileRepository(@ApplicationContext context: Context): ProfileRepository =
    ProfileRepository.install(context)
```

This changes no behavior: the existing lazy init, the early `getAppContext()` path, and any caller that runs before Hilt injects all still work. There is one instance. When a class gets an `@Inject constructor` (at the latest when its static is deleted), the `@Provides` goes away with the static.

The alternative, where statics delegate to Hilt through an `EntryPoint`, is what B1 currently describes. It fails for callers that run before `super.onCreate()` of the Application finishes injection, or in a process where the Application is not `DreamDroid`. Decision 2 settles the direction.

Since 14b no transitional provider is left: every binding is an `@Inject` constructor or a `@Provides` that builds its own instance.

## PR sequence

Screen groups are ordered so that each PR's repository exists before a later group depends on it. Easy, self-contained groups come first, so the pattern settles before the big ones. Split any PR that grows past ~20 production files, along the sub-bullets.

| # | PR | ViewModels moved | Introduces | Deletes |
| --- | --- | --- | --- | --- |
| 1 | Hilt + Device info | `DeviceInfoViewModel` | Hilt, `EnigmaClientFactory`, `ReceiverRepository` (device info), `UiText`, screen message/title pattern | `loadDeviceInfo` |
| 2 | Signal + Screenshot | `SignalViewModel`, `ScreenshotViewModel` | `SessionConnectionHolder` binding | `loadSignal`, `loadScreenshot` |
| 3 | Profiles + setup | `ProfilesViewModel`, `ProfileEditViewModel`, `SetupAssistantViewModel`, `TvProfilesHostViewModel` | `AppDatabase` binding, `@Inject` `ProfileRepository`/`RoomProfileStore`, `ProfileCheckRepository` | — (TV `MainActivity` gets `@AndroidEntryPoint`) |
| 4 | Settings + backup | `SettingsViewModel`, `BackupViewModel` | `SettingsRepository` over `SharedPreferences` (decision 5) | — |
| 5 | Timers | `HubTimerListViewModel`, `TimerEditViewModel`, `TvTimerHostViewModel`, `TvTimerEditViewModel` | `TimerRepository` | `TimerSnapshotStore`, `loadTimerList` |
| 6 | Movies | `HubMovieListViewModel` | `MovieRepository` (incl. file download) | `MovieSnapshotStore` |
| 7 | List EPG | `EpgBouquetViewModel`, `ServiceEpgViewModel`, `EpgSearchViewModel`, EPG detail dialog session | `EpgRepository` (list) | `ListEpgCache`, `loadEventList` |
| 8 | MultiEPG | `MultiEpgViewModel`, `TvMultiEpgViewModel` | `EpgRepository` owns `MultiEpgSync` | — (`MultiEpgSyncHolder` wraps the binding until 12) |
| 9 | Service lists + pickers | `HubServiceListViewModel`, `PickServiceViewModel`, `TimerServicePickViewModel` | `ServiceRepository` | `UserBouquetCache`, `UseDrivenCache` |
| 10 | Hub, now playing, zap | `HubViewModel`, `HubNowPlayingViewModel`, `CurrentServiceViewModel`, `ZapViewModel` | `ReceiverRepository` zap/current | `loadCurrentService` |
| 11a | Phone shell actions | `ShellViewModel` (power/sleep/message), `PhoneNavHostState`, virtual remote | `ReceiverRepository` power/sleep/message/keys/volume | `ShellViewModel.Factory`, phone `ShellMessages` use (`ProfileEditDestination`, virtual remote) |
| 11b | Phone shell gate | Startup profile check, reachability probe, volume keys into `ShellViewModel` | `SettingsRepository.firstStart` | `VolumePowerSleepLoad`, `ProfileDetectLoad` (incl. both `launchDetectDevicesLoad`), `hasUseDrivenCache(profile, context)` |
| 12 | TV hub | `TvHubViewModel`, TV hub browse | — | `TvHubViewModel.Factory`, `MultiEpgSyncHolder`, `ShellMessages` |
| 13 | Player + share | `VideoPlaybackViewModel`, `ShareViewModel` | `@AndroidEntryPoint` on `VideoActivity`, `ShareActivity` | remaining `*Load.kt` helpers |
| 14 | Non-UI entry points + locator removal | — | `@AndroidEntryPoint` widget receiver/config, worker injection, injected `EnigmaOkHttp` | `ProfileRepository.get/install`, `SessionConnectionHolder.shared`, `AppDatabase` statics, `getAppContext()`, no-profile `EnigmaClient`/`EnigmaHttp` defaults |
| 15 | *(optional)* Hilt instrumented tests | — | `HiltTestRunner`, first `@TestInstallIn` fake | — |

The **Deletes** column names what is certain. Several `enigma/*Load.kt` helpers span groups and go with their last caller: `loadServiceList` (pickers 9, zap 10, TV hub 12), `loadBouquetList` (services 9, player 13), `loadMovieList` (movies 6, TV hub 12), `loadEpgNowNext` (player 13), and `launchLocationsAndTagsLoad` (hub 10, TV timer editor 5, so it goes in 10).

### PR 1 — Hilt and Device info

The smallest screen that exercises every part of the pattern: one read-only Enigma call, `SavedStateHandle` restore, a title, an error message, and existing tests (`DeviceInfoScreenTest`, `DeviceInfoSavedTest`, `DeviceInfoUiStateRestoreTest`).

- Build: Dagger/Hilt through KSP (`com.google.dagger:hilt-android` + `hilt-compiler`, plugin `com.google.dagger.hilt.android`) and `androidx.hilt:hilt-lifecycle-viewmodel-compose` for `hiltViewModel()`. The plugin must work with AGP 9.4 (built-in Kotlin) and KSP 2.3; check that first (decision 4).
- `@HiltAndroidApp` on `DreamDroid`. `@AndroidEntryPoint` on phone `MainActivity` only; the other activities get it when their first Hilt ViewModel or injection lands.
- `EnigmaClientFactory` (`@Singleton`, `@Inject`): `current()` builds an `EnigmaClient` for `ProfileRepository.requireCurrent()`, and `forProfile(profile)` builds one for a given profile. It takes `ProfileRepository` through the transitional `@Provides`. See decision 1 for why this is a factory and not a singleton `EnigmaClient`.
- `ReceiverRepository` (`@Singleton`) with `deviceInfo(): EnigmaResponse<DeviceInfo>`. It grows in PRs 2, 10, and 11.
- `DeviceInfoViewModel`: `@HiltViewModel`, `(SavedStateHandle, ReceiverRepository)`, `StateFlow<DeviceInfoUiState>` with `title: UiText`, `refreshing`, `userMessage: UiText?`, and the device info. `DeviceInfoScreen` stays stateless. `DeviceInfoDestination` collects with `collectAsStateWithLifecycle()` and shows `userMessage` through the shell snackbar, then calls `onMessageShown()` (decision 3).
- `UiText` (`Resource(@StringRes id, args)` / `Raw(String)`). `Raw` is needed because box error texts arrive as server strings. `EnigmaFailure` gets a `UiText` mapping next to the existing `userMessage(context)`; the old mapping is deleted when its last caller moves.
- Delete `loadDeviceInfo`.
- Tests: JVM `DeviceInfoViewModelTest` (load, failure message, restore from `SavedStateHandle`) against a fake at the boundary chosen in decision 6. Update `DeviceInfoScreenTest` for the new state type.
- Docs: add the Hilt rules in short form to `AGENTS.md` (bindings land with their first consumer, `hiltViewModel()` for migrated screens, Hilt wraps statics until their last caller moves). The coordinator keeps this doc's progress and `modernize-dreamdroid.md` current.

Proof beyond the PR job: emulator job (the Application class changed, so every instrumented test starts the new app). A `googleRelease` build that opens Device info on a device.

### PR 2 — Signal and Screenshot

Two more tool screens on the same pattern. They add the `SessionConnectionHolder` binding (transitional `@Provides` returning `.shared`), because both ViewModels gate on `blocksMutations`. `ReceiverRepository` gains `signal()` and `screenshot(...)`. `SignalViewModel` keeps `AudioTrack` playback. That is audio output, not a `Context` dependency, so it stays in the ViewModel. `SignalUiState` becomes an immutable snapshot instead of a mutable holder.

### PR 3 — Profiles and setup assistant

- `AppDatabase` binding (`@Provides @Singleton`, from the same builder `AppDatabase.database(context)` uses, so the statics and Hilt share the instance) and DAO providers as used.
- `ProfileRepository` gets `@Inject constructor(store: ProfileStore)`; `RoomProfileStore` takes the DAO. `ProfileRepository.install()` becomes a thin lookup of the Hilt instance for the remaining static callers (flip of the transitional pattern). This is safe here because `DreamDroid` now injects it in `onCreate`.
- `DreamDroid.onCreate` gets `ProfileRepository` injected instead of calling `install`/`get`. Trap: Hilt injects during `super.onCreate()`, **before** `DatabaseHelper.migrateIntoRoomIfNeeded`. Constructors of bound classes must not query the database. Keep reads in methods, or inject `dagger.Lazy<>`.
- `ProfileCheckRepository` wraps `CheckProfile` for the edit and setup flows. The startup profile check still runs in phone and TV `MainActivity` (`launchCheckProfileLoad`); it moves to the shell ViewModels in PRs 11 and 12 and reuses this repository.
- TV `MainActivity` becomes `@AndroidEntryPoint` for `TvProfilesHostViewModel`.
- May split: (a) profiles list + edit + TV host, (b) setup assistant.

### PR 4 — Settings and backup

`SettingsViewModel` reads and writes many preferences and clears caches. That needs testable settings, which is the case B2 allows for: a `SettingsRepository` over `SharedPreferences`, shaped so a later DataStore swap does not change callers (decision 5). Cache clearing goes through a small `CacheRepository` method or through `ServiceRepository` if PR 9 has already landed. Decide by order at the time; do not add both.

### PRs 5–9 — data groups (B4)

Each repository owns its offline rules. It absorbs the matching `*SnapshotStore` / `*Cache` object, which gets deleted, and takes `AppDatabase` DAOs plus `EnigmaClientFactory`. Proof per repository: JVM test with the decision 6 HTTP fixture and `AppDatabase.inMemory`.

- **5 Timers:** phone hub timer list, timer editor, and TV timer host/editor share `TimerRepository`. `TimerEditDestination` currently builds `EnigmaHttp()` inline; that moves into the repository.
- **6 Movies:** `MovieFileDownload` moves into `MovieRepository`.
- **7 List EPG:** `EpgEventDialogSession` folds into its ViewModel (C2 "fold or delete the `*Session` class").
- **8 MultiEPG:** `EpgRepository` constructs `MultiEpgSync` itself. `MultiEpgSyncHolder.shared()` returns the Hilt instance for its two remaining callers (hub service list page, TV hub browse) until 9 and 12. The fetch, cache, and TTL rules in [`multiepg.md`](multiepg.md) do not change.
- **9 Service lists + pickers:** `ServiceRepository` absorbs `UseDrivenCache` and `UserBouquetCache`. `SessionConnectionHolder.onUseDrivenCacheCleared` gets called from the repository instead of from call sites. This is the largest group (`ui/services` + `ui/pick`, ~35 files); split by hub service list first and pickers second if needed.

### PRs 10–13 — hosts

- **10** Hub, now-playing strip, current service, zap: `ReceiverRepository` gains zap and current service.
- **11** Phone shell, split past the ~20-file limit along the sub-bullets below.
  - **11a** Shell actions: `ShellViewModel` loses its factory and exposes `StateFlow<ShellUiState>` (message, sleep-timer and profile-switch effects). Power, sleep timer, send message, and virtual-remote keys move to `ReceiverRepository`. `PhoneNavHostState` becomes a `@HiltViewModel`. `ShellMessages.post` callers in `ProfileEditDestination` and the virtual remote move to shell/screen UI state. The gate (`MainActivity.onProfileChanged`), volume keys, `VolumePowerSleepLoad`, and `launchDetectDevicesLoad` stay for 11b.
  - **11b** Shell gate: the startup profile check, the reachability probe, and volume keys move out of `MainActivity` into `ShellViewModel`, which follows `profiles.switches` itself (replacing 11a's profile-switch effect) and owns what the gate shows (`ShellUiState.profileCheck`, moved off `PhoneNavHostState`). The activity only navigates on the check's started/outcome effects. First start goes through `SettingsRepository.firstStart`. Deletes `VolumePowerSleepLoad` and `ProfileDetectLoad`; neither `launchDetectDevicesLoad` had a caller (setup discovery already uses `ReceiverDiscovery`), so no device detection moves.
- **12** TV hub: `TvHubViewModel` loses its factory, the TV startup profile check moves out of TV `MainActivity`, and TV hub browse stops using `MultiEpgSyncHolder`, so the holder is deleted. `ShellMessages` gets deleted here if nothing else posts to it by then; otherwise in 13.
- **13** Player and share: `VideoActivity` and `ShareActivity` become `@AndroidEntryPoint`. `ShareActivity` keeps its `Toast` (deliberate exception). `VLCInstance` stops using `getAppContext()` and takes the context from its caller.

### PR 14 — Non-UI entry points and locator removal

Split in two. **14a** moves the widget, picons, the HTTP stack, live streams, and the remaining helpers onto Hilt. **14b** deletes the locators once the phone shell (11) stops using them: `ShellMessages`, `SessionConnectionHolder.shared`, `ProfileRepository.get()`/`install`, and `getAppContext()`. The transitional `EnigmaClient()` and two-argument `CheckProfile.checkProfile` lost their last callers with 11b and go in 14a.

- Widget: `VirtualRemoteWidgetProvider` and `VirtualRemoteWidgetConfiguration` become `@AndroidEntryPoint`. `VirtualRemoteWidget` (Glance, not an Android component) and `WidgetRemoteRequest` get dependencies through an `@EntryPoint`. `WidgetRemoteRequest` keeps its `Toast` (deliberate exception).
- `PiconSyncWorker`: see decision 7.
- `EnigmaOkHttp` becomes an injected `@Singleton` that takes `@ApplicationContext` for `DreamDroidTrustManager`. `EnigmaHttp` requires a profile, and `EnigmaClient` requires its `EnigmaHttp`.
- Delete everything in **End state** that is still there. At this point the compiler lists any caller that is left.
- `DreamDroidBackupAgent` needs nothing: it only names files. It must stay free of injection, because a restore can run the agent in a process whose Application is not `DreamDroid`.

14b as landed: `MainActivity` injects `ProfileRepository`. `SessionConnectionHolder` is an `@Singleton` with an `@Inject` constructor, and `PhoneShell` takes `boxActionsBlocked` from the status `MainActivity` already collects. The picon sync, the last `ShellMessages` poster, runs from `SettingsViewModel` through an injected `PiconSyncScheduler` (WorkManager; a fake in the JVM test, since WorkManager does not run there) and reports started / still running as `SettingsUiState.userMessage`. `DatabaseModule` builds the database with `AppDatabase.build`; `DreamDroid` gets it injected for the pre-Room import. Instrumented tests that drive real activities read the app's singletons from the fields Hilt injected into `DreamDroid` (`testutil/DreamDroidApp.kt`).

### PR 15 (optional) — Hilt in instrumented tests

Only once a UI test needs a faked binding (decision 8). A custom runner that swaps the Application for `HiltTestApplication` applies to **all** 142 instrumented test files. Before PR 14 it would break the 59 static references and everything `DreamDroid.onCreate` sets up. Also update `.cursor/cloud/connected-test.sh` and the `am instrument` line in `AGENTS.md` for the new runner class.

## Progress

One line per PR: state, then PR link once opened.

- [x] 1 Hilt + Device info — merged, [#526](https://github.com/sreichholf/dreamDroid/pull/526)
- [x] 2 Signal + Screenshot — merged, [#527](https://github.com/sreichholf/dreamDroid/pull/527)
- [x] 3 Profiles + setup — merged, [#528](https://github.com/sreichholf/dreamDroid/pull/528)
- [x] 4 Settings + backup — merged, [#532](https://github.com/sreichholf/dreamDroid/pull/532)
- [x] 5 Timers — merged, [#530](https://github.com/sreichholf/dreamDroid/pull/530)
- [x] 6 Movies — merged, [#531](https://github.com/sreichholf/dreamDroid/pull/531)
- [x] 7 List EPG — merged, [#529](https://github.com/sreichholf/dreamDroid/pull/529)
- [x] 8 MultiEPG — merged, [#533](https://github.com/sreichholf/dreamDroid/pull/533)
- [x] 9 Service lists + pickers — merged, [#534](https://github.com/sreichholf/dreamDroid/pull/534) (hub service list) and [#535](https://github.com/sreichholf/dreamDroid/pull/535) (pickers)
- [x] 10 Hub, now playing, zap — merged, [#537](https://github.com/sreichholf/dreamDroid/pull/537)
- [x] 11a Phone shell actions — merged, [#541](https://github.com/sreichholf/dreamDroid/pull/541)
- [x] 11b Phone shell gate — merged, [#543](https://github.com/sreichholf/dreamDroid/pull/543)
- [x] 12 TV hub — merged, [#536](https://github.com/sreichholf/dreamDroid/pull/536)
- [x] 13 Player + share — merged, [#538](https://github.com/sreichholf/dreamDroid/pull/538)
- [x] 14a Non-UI entry points — merged, [#545](https://github.com/sreichholf/dreamDroid/pull/545)
- [ ] 14b Locator removal — in review
- [ ] 15 *(optional)* Hilt instrumented tests

### Parallel waves

After PR 1, PRs in the same wave do not depend on each other and run in parallel. Each wave starts from `main` with the previous wave merged, or stacked on a reviewed predecessor. Same-wave PRs put their bindings in a feature module (`di/<Feature>Module.kt`) to keep conflicts small, and merge one at a time, each pulling in `main` first.

| Wave | PRs |
| --- | --- |
| A | 2, 3 |
| B | 4, 5, 6, 7 |
| C | 8, then 9 |
| D | 10, 12, 13 |
| E | 11a, then 11b |
| F | 14 |

## Decisions

Accepted by the operator 2026-09-28. Change one only with a note here saying why.

1. **`EnigmaClient` is not a singleton.** `EnigmaClient` wraps one `EnigmaHttp`, which is bound to one profile and cancels its in-flight call when a second `fetch` starts. A process-wide client would serialize every screen's requests and freeze the profile at first injection. Inject a `@Singleton EnigmaClientFactory` (`current()`, `forProfile(profile)`); repositories create a client per operation. Making `EnigmaHttp` stateless is out of scope.
2. **Transitional direction: Hilt wraps the static.** While a static accessor has callers, a Hilt `@Provides` returns the existing static instance. It flips once the class gets an `@Inject` constructor (PR 3 for `ProfileRepository`, PR 8 for `MultiEpgSync`); the static is deleted with its last caller. This avoids an `EntryPoint` lookup from code that can run before injection.
3. **User messages and titles are screen state.** The shell provides its `SnackbarHostState` through a `CompositionLocal`. A destination shows `uiState.userMessage` with it and then calls `viewModel.onMessageShown()`. Destinations report `uiState.title` to the shell, which stops reading `Activity.title` for migrated destinations. PR 1 sets the pattern. No injected message bus.
4. **Hilt Gradle plugin, with a fallback.** PR 1 first checks that the Hilt Gradle plugin (Dagger 2.60.1, `androidx.hilt` 1.4.0) builds with AGP 9.4 built-in Kotlin and KSP 2.3.12. If it does not, use Hilt without the plugin (`@HiltAndroidApp(Application::class) class DreamDroid : Hilt_DreamDroid()`, likewise for activities) and record that here. Do not downgrade AGP.
5. **`SettingsRepository` over `SharedPreferences`** in the first PR that needs it (PR 4), with typed properties and `Flow` reads so B2 later swaps only its internals. DataStore stays deferred.
6. **Repositories are concrete classes.** Tests go through the real boundary: a `MockWebServer`-backed profile and `AppDatabase.inMemory`. Add an interface only when a fake is clearly simpler than the server fixture.
7. **`PiconSyncWorker` uses an `@EntryPoint`** in PR 14. Switch to `@HiltWorker` if a second worker shows up.
8. **No Hilt instrumented test as B1 proof.** Proof is Dagger's compile-time graph validation, the emulator job, and JVM tests. PR 15 happens only once a UI test needs a faked binding.
9. **Planned series.** PRs 1–14 run in order; 2.0 blocker fixes go first when they come up. Every PR leaves the app shippable, so the series can pause between any two PRs.
10. **JVM tests and Android stubs.** `EnigmaHttp` error paths call `android.util.Log`, which throws on the JVM. The app sets `testOptions.unitTests.isReturnDefaultValues = true` (PR 1) so repository and ViewModel tests cover HTTP errors. Tests build their own `ProfileRepository` over an in-memory database; since 14b there is no static repository to install.
11. **Transitional lookups and assisted ViewModels** (PR 6). When a class moves behind Hilt and a non-Hilt caller that runs after Application injection still needs it (for example the phone hub before PR 10, the TV hub before PR 12), the caller uses a small `@EntryPoint` lookup such as `movieRepository(context)`, which returns the same singleton. It is deleted with its last caller, like `ProfileRepository.get()`. A ViewModel whose input is not a route argument (a pager page keyed by location) uses `@HiltViewModel(assistedFactory = …)` with `hiltViewModel(key = …) { it.create(…) }`.

## Not in this plan

DataStore (B2), a dispatcher-injection layer (`@IoDispatcher`), Navigation changes, splitting into Gradle modules, making `EnigmaHttp` stateless, and changes to MultiEPG fetch/cache rules. Each would widen PRs whose purpose is DI and ViewModel shape.
