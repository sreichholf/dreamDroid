# Hilt migration plan (B1, with C2 and B4)

**Status:** plan, not started. Open questions are at the end; PR 1 is blocked on questions 1–4.
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

The alternative, where statics delegate to Hilt through an `EntryPoint`, is what B1 currently describes. It fails for callers that run before `super.onCreate()` of the Application finishes injection, or in a process where the Application is not `DreamDroid`. Question 2 asks about switching.

## PR sequence

Screen groups are ordered so that each PR's repository exists before a later group depends on it. Easy, self-contained groups come first, so the pattern settles before the big ones. Split any PR that grows past ~20 production files, along the sub-bullets.

| # | PR | ViewModels moved | Introduces | Deletes |
| --- | --- | --- | --- | --- |
| 1 | Hilt + Device info | `DeviceInfoViewModel` | Hilt, `EnigmaClientFactory`, `ReceiverRepository` (device info), `UiText`, screen message/title pattern | `loadDeviceInfo` |
| 2 | Signal + Screenshot | `SignalViewModel`, `ScreenshotViewModel` | `SessionConnectionHolder` binding | `loadSignal`, `loadScreenshot` |
| 3 | Profiles + setup | `ProfilesViewModel`, `ProfileEditViewModel`, `SetupAssistantViewModel`, `TvProfilesHostViewModel` | `AppDatabase` binding, `@Inject` `ProfileRepository`/`RoomProfileStore`, `ProfileCheckRepository` | — (TV `MainActivity` gets `@AndroidEntryPoint`) |
| 4 | Settings + backup | `SettingsViewModel`, `BackupViewModel` | `SettingsRepository` over `SharedPreferences` (see Q5) | — |
| 5 | Timers | `HubTimerListViewModel`, `TimerEditViewModel`, `TvTimerHostViewModel`, `TvTimerEditViewModel` | `TimerRepository` | `TimerSnapshotStore`, `loadTimerList` |
| 6 | Movies | `HubMovieListViewModel` | `MovieRepository` (incl. file download) | `MovieSnapshotStore` |
| 7 | List EPG | `EpgBouquetViewModel`, `ServiceEpgViewModel`, `EpgSearchViewModel`, EPG detail dialog session | `EpgRepository` (list) | `ListEpgCache`, `loadEventList` |
| 8 | MultiEPG | `MultiEpgViewModel`, `TvMultiEpgViewModel` | `EpgRepository` owns `MultiEpgSync` | — (`MultiEpgSyncHolder` wraps the binding until 12) |
| 9 | Service lists + pickers | `HubServiceListViewModel`, `PickServiceViewModel`, `TimerServicePickViewModel` | `ServiceRepository` | `UserBouquetCache`, `UseDrivenCache` |
| 10 | Hub, now playing, zap | `HubViewModel`, `HubNowPlayingViewModel`, `CurrentServiceViewModel`, `ZapViewModel` | `ReceiverRepository` zap/current | `loadCurrentService` |
| 11 | Phone shell | `ShellViewModel`, `PhoneNavHostState`, virtual remote | `ReceiverRepository` power/sleep/message/keys/volume | `ShellViewModel.Factory`, `VolumePowerSleepLoad`, `launchDetectDevicesLoad`, phone `ShellMessages` use |
| 12 | TV hub | `TvHubViewModel`, TV hub browse | — | `TvHubViewModel.Factory`, `MultiEpgSyncHolder`, `ProfileDetectLoad`, `ShellMessages` |
| 13 | Player + share | `VideoPlaybackViewModel`, `ShareViewModel` | `@AndroidEntryPoint` on `VideoActivity`, `ShareActivity` | remaining `*Load.kt` helpers |
| 14 | Non-UI entry points + locator removal | — | `@AndroidEntryPoint` widget receiver/config, worker injection, injected `EnigmaOkHttp` | `ProfileRepository.get/install`, `SessionConnectionHolder.shared`, `AppDatabase` statics, `getAppContext()`, no-profile `EnigmaClient`/`EnigmaHttp` defaults |
| 15 | *(optional)* Hilt instrumented tests | — | `HiltTestRunner`, first `@TestInstallIn` fake | — |

The **Deletes** column names what is certain. Several `enigma/*Load.kt` helpers span groups and go with their last caller: `loadServiceList` (pickers 9, zap 10, TV hub 12), `loadBouquetList` (services 9, player 13), `loadMovieList` (movies 6, TV hub 12), `loadEpgNowNext` (player 13), and `launchLocationsAndTagsLoad` (hub 10, TV timer editor 5, so it goes in 10).

### PR 1 — Hilt and Device info

The smallest screen that exercises every part of the pattern: one read-only Enigma call, `SavedStateHandle` restore, a title, an error message, and existing tests (`DeviceInfoScreenTest`, `DeviceInfoSavedTest`, `DeviceInfoUiStateRestoreTest`).

- Build: Dagger/Hilt through KSP (`com.google.dagger:hilt-android` + `hilt-compiler`, plugin `com.google.dagger.hilt.android`) and `androidx.hilt:hilt-lifecycle-viewmodel-compose` for `hiltViewModel()`. The plugin must work with AGP 9.4 (built-in Kotlin) and KSP 2.3; check that first (Q4).
- `@HiltAndroidApp` on `DreamDroid`. `@AndroidEntryPoint` on phone `MainActivity` only; the other activities get it when their first Hilt ViewModel or injection lands.
- `EnigmaClientFactory` (`@Singleton`, `@Inject`): `current()` builds an `EnigmaClient` for `ProfileRepository.requireCurrent()`, and `forProfile(profile)` builds one for a given profile. It takes `ProfileRepository` through the transitional `@Provides`. See Q1 for why this is a factory and not a singleton `EnigmaClient`.
- `ReceiverRepository` (`@Singleton`) with `deviceInfo(): EnigmaResponse<DeviceInfo>`. It grows in PRs 2, 10, and 11.
- `DeviceInfoViewModel`: `@HiltViewModel`, `(SavedStateHandle, ReceiverRepository)`, `StateFlow<DeviceInfoUiState>` with `title: UiText`, `refreshing`, `userMessage: UiText?`, and the device info. `DeviceInfoScreen` stays stateless. `DeviceInfoDestination` collects with `collectAsStateWithLifecycle()` and shows `userMessage` through the shell snackbar, then calls `onMessageShown()` (Q3).
- `UiText` (`Resource(@StringRes id, args)` / `Raw(String)`). `Raw` is needed because box error texts arrive as server strings. `EnigmaFailure` gets a `UiText` mapping next to the existing `userMessage(context)`; the old mapping is deleted when its last caller moves.
- Delete `loadDeviceInfo`.
- Tests: JVM `DeviceInfoViewModelTest` (load, failure message, restore from `SavedStateHandle`) against a fake at the boundary chosen in Q6. Update `DeviceInfoScreenTest` for the new state type.
- Docs: set this doc's status to in progress, update B1 in `modernize-dreamdroid.md`, and add the Hilt rules in short form to `AGENTS.md`.

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

`SettingsViewModel` reads and writes many preferences and clears caches. That needs testable settings, which is the case B2 allows for: a `SettingsRepository` over `SharedPreferences`, shaped so a later DataStore swap does not change callers (Q5). Cache clearing goes through a small `CacheRepository` method or through `ServiceRepository` if PR 9 has already landed. Decide by order at the time; do not add both.

### PRs 5–9 — data groups (B4)

Each repository owns its offline rules. It absorbs the matching `*SnapshotStore` / `*Cache` object, which gets deleted, and takes `AppDatabase` DAOs plus `EnigmaClientFactory`. Proof per repository: JVM test with the Q6 HTTP fake and `AppDatabase.inMemory`.

- **5 Timers:** phone hub timer list, timer editor, and TV timer host/editor share `TimerRepository`. `TimerEditDestination` currently builds `EnigmaHttp()` inline; that moves into the repository.
- **6 Movies:** `MovieFileDownload` moves into `MovieRepository`.
- **7 List EPG:** `EpgEventDialogSession` folds into its ViewModel (C2 "fold or delete the `*Session` class").
- **8 MultiEPG:** `EpgRepository` constructs `MultiEpgSync` itself. `MultiEpgSyncHolder.shared()` returns the Hilt instance for its two remaining callers (hub service list page, TV hub browse) until 9 and 12. The fetch, cache, and TTL rules in [`multiepg.md`](multiepg.md) do not change.
- **9 Service lists + pickers:** `ServiceRepository` absorbs `UseDrivenCache` and `UserBouquetCache`. `SessionConnectionHolder.onUseDrivenCacheCleared` gets called from the repository instead of from call sites. This is the largest group (`ui/services` + `ui/pick`, ~35 files); split by hub service list first and pickers second if needed.

### PRs 10–13 — hosts

- **10** Hub, now-playing strip, current service, zap: `ReceiverRepository` gains zap and current service.
- **11** Phone shell: `ShellViewModel` loses its factory. The startup profile check, volume, and device detection move out of `MainActivity` / `PhoneNavHandle` into `ShellViewModel`. Power, sleep timer, send message, and virtual-remote keys move to `ReceiverRepository`. Shell messages from migrated screens are already UI state (Q3); `ShellMessages.post` callers left in the phone shell move here.
- **12** TV hub: `TvHubViewModel` loses its factory, the TV startup profile check moves out of TV `MainActivity`, and TV hub browse stops using `MultiEpgSyncHolder`, so the holder is deleted. `ShellMessages` gets deleted here if nothing else posts to it by then; otherwise in 13.
- **13** Player and share: `VideoActivity` and `ShareActivity` become `@AndroidEntryPoint`. `ShareActivity` keeps its `Toast` (deliberate exception). `VLCInstance` stops using `getAppContext()` and takes the context from its caller.

### PR 14 — Non-UI entry points and locator removal

- Widget: `VirtualRemoteWidgetProvider` and `VirtualRemoteWidgetConfiguration` become `@AndroidEntryPoint`. `VirtualRemoteWidget` (Glance, not an Android component) and `WidgetRemoteRequest` get dependencies through an `@EntryPoint`. `WidgetRemoteRequest` keeps its `Toast` (deliberate exception).
- `PiconSyncWorker`: see Q7.
- `EnigmaOkHttp` becomes an injected `@Singleton` that takes `@ApplicationContext` for `DreamDroidTrustManager`. `EnigmaHttp` requires a profile, and `EnigmaClient` requires its `EnigmaHttp`.
- Delete everything in **End state** that is still there. At this point the compiler lists any caller that is left.
- `DreamDroidBackupAgent` needs nothing: it only names files. It must stay free of injection, because a restore can run the agent in a process whose Application is not `DreamDroid`.

### PR 15 (optional) — Hilt in instrumented tests

Only once a UI test needs a faked binding (Q8). A custom runner that swaps the Application for `HiltTestApplication` applies to **all** 142 instrumented test files. Before PR 14 it would break the 59 static references and everything `DreamDroid.onCreate` sets up. Also update `.cursor/cloud/connected-test.sh` and the `am instrument` line in `AGENTS.md` for the new runner class.

## Questions to discuss

Each question has a recommendation. Q1–Q4 decide what PR 1 looks like.

1. **`EnigmaClient` is not a singleton.** B1 lists `@Singleton` `EnigmaClient`, but that does not fit the code. `EnigmaClient` wraps one `EnigmaHttp`, which is bound to one profile, and `EnigmaHttp` cancels its in-flight call when a second `fetch` starts ("do not share this type across concurrent fetches"). A process-wide client would serialize every screen's requests and freeze the profile at first injection.
   **Recommendation:** inject a `@Singleton EnigmaClientFactory` (`current()`, `forProfile()`), and have repositories create a client per operation, as call sites do today. Making `EnigmaHttp` stateless is possible later but changes cancel semantics; not part of this wave.

2. **Transitional direction.** B1 says "the `object` holders delegate to injected instances until callers move". This plan does the opposite at first: Hilt `@Provides` returns the existing static instance. It flips once the class gets an `@Inject` constructor (PR 3 for `ProfileRepository`, PR 8 for `MultiEpgSync`).
   **Recommendation:** accept, and update the B1 wording in PR 1. It keeps the early PRs behavior-neutral and avoids an `EntryPoint` lookup from code that can run before injection.

3. **Where user messages go.** C2 wants messages in `UiState`, but the `SnackbarHost` lives in the shell (`ShellSnackbarHost`), not in each screen.
   **Recommendation:** the shell provides its `SnackbarHostState` through a `CompositionLocal`. A destination shows `uiState.userMessage` with it and then calls `viewModel.onMessageShown()`. The title works the same way: destinations report `uiState.title`, and `PhoneShell` reads it instead of `Activity.title`. The pattern gets set in PR 1 and reused. Alternative: an injected `@Singleton` message bus, which keeps `ShellMessages` under a new name and is not what C2 asks for.

4. **Hilt with AGP 9.4.** The Hilt Gradle plugin rewrites bytecode for `@AndroidEntryPoint`/`@HiltAndroidApp`. Current Dagger is 2.60.1 and `androidx.hilt` is 1.4.0; this plan has not verified that they work with AGP 9.4 built-in Kotlin and KSP 2.3.12.
   **Recommendation:** check that first in PR 1. If the plugin does not work, use Hilt without it (`@HiltAndroidApp(Application::class) class DreamDroid : Hilt_DreamDroid()`, and likewise for activities). That works without the plugin, but it is less idiomatic and has to be undone later. Do not downgrade AGP.

5. **Settings before B2.** PR 4, and parts of 8, 10, and 11, read preferences in ViewModels. B2 (DataStore) is deferred and needs operator sign-off.
   **Recommendation:** add `SettingsRepository` over `SharedPreferences` in the first PR that needs it (PR 4). Give it typed properties and `Flow` reads, so B2 later swaps only its internals. Alternatively, pull B2 forward to PR 4. Not recommended: that adds backup and startup risk to a DI wave.

6. **What JVM ViewModel tests fake.** The target architecture says "fake repositories". That means an interface plus an implementation for each repository, where most interfaces have exactly one production class.
   **Recommendation:** keep repositories concrete. Test them and their ViewModels through the real boundary: a `MockWebServer`-backed profile (the test suite already uses `okhttp-mockwebserver`) and `AppDatabase.inMemory`. Add an interface only when a test fake is clearly simpler than the server fixture, for example to simulate slow or concurrent calls. This tests behavior, not call mirroring.

7. **WorkManager and Hilt.** `@HiltWorker` needs `androidx.hilt:hilt-work`, a custom `Configuration.Provider` on `DreamDroid`, and removal of the default WorkManager initializer from the manifest. That is more startup surface for one worker.
   **Recommendation:** `PiconSyncWorker` gets its dependencies through an `@EntryPoint` in PR 14. Switch to `@HiltWorker` if a second worker shows up.

8. **Proof for "one Hilt test replaces a binding with a fake".** B1 asks for this proof. Dagger already checks the graph at compile time. Launching `MainActivity` in the existing instrumented tests covers injection at runtime, and JVM tests cover behavior with fakes. A Hilt instrumented test needs the runner swap described in PR 15.
   **Recommendation:** drop that proof from B1 and PR 1. Use compile-time validation, the emulator job, and JVM tests. Do PR 15 only once a UI test actually needs a fake binding.

9. **Keep this a single wave?** C2 and B4 are listed as "opportunistic (when a feature or bug touches the screen)". This plan runs them as a planned series instead. Leaving the wave half done means two ways to get a dependency for as long as it stays half done.
   **Recommendation:** run PRs 1–14 as a series, but let 2.0 blocker fixes go first. Every PR leaves the app shippable, so the series can pause between any two PRs.

## Not in this plan

DataStore (B2), a dispatcher-injection layer (`@IoDispatcher`), Navigation changes, splitting into Gradle modules, making `EnigmaHttp` stateless, and changes to MultiEPG fetch/cache rules. Each would widen PRs whose purpose is DI and ViewModel shape.
