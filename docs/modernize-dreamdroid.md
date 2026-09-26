# Modernize dreamDroid

**Premise:** dreamDroid 2.0 is a modern Android app that follows current platform best practices. Phone users get a **Compose Material 3** Enigma2 remote. TV gets a **Compose** hub (`androidx.tv`), not Leanback browse. New types are **Kotlin** + coroutines. Proof is **instrumented Compose tests** for UI and **JVM tests** for ViewModels and data code, not tap loops. Rewrite trunk is **`main`** (`master` is last 1.15 — do not merge them).

No permanent keepers. A rule in this doc or in [`AGENTS.md`](../AGENTS.md) that protects a legacy pattern is a sequencing note, not a freeze. When a leftover surface is touched, move it to the [target architecture](#target-architecture). The only long-lived deviations are listed under [Deliberate exceptions](#deliberate-exceptions), each with its reason. If a rule here contradicts the target architecture, the target architecture wins and the rule gets fixed.

Floor: minSdk 26, compileSdk / targetSdk 37, JDK 25 (bytecode Java 17), debug package `net.reichholf.dreamdroid.debug`. Agent rules and how to run tests: [`AGENTS.md`](../AGENTS.md). MultiEPG product design: [`docs/multiepg.md`](multiepg.md). Offline cache + unified errors: [`docs/offline-and-errors.md`](offline-and-errors.md).

## Target architecture

This is what "current best practice" means for this app. It follows Google's [guide to app architecture](https://developer.android.com/topic/architecture) and the Compose / Material 3 defaults. New code is written this way; the [remediation plan](#remediation-plan) moves existing code here.

| Area | Target |
| --- | --- |
| Layers | UI (Compose screens + `ViewModel`) → data (repositories that own Enigma HTTP, Room, and settings). Repositories are the single source of truth; screens never call `EnigmaClient`, DAOs, or `SharedPreferences` directly. An optional domain layer is added only when logic is shared by several ViewModels. |
| Dependency injection | Hilt (KSP). `@HiltAndroidApp` on `DreamDroid`, `@AndroidEntryPoint` activities, `hiltViewModel()` in NavHost routes. No service-locator `object`s holding mutable state, no static `getAppContext()`. |
| ViewModel | Scoped to its `NavBackStackEntry` (or the activity for shell / host state). Constructor takes repositories and `SavedStateHandle`. No `Application`, `Context`, `View`, `Menu`, or activity. Exposes one immutable `StateFlow<*UiState>`; the UI collects it with `collectAsStateWithLifecycle()`. Work runs in `viewModelScope`. |
| UI events | User messages (mutation results, errors) are part of UI state, shown by the screen through a `SnackbarHostState`, then cleared by the screen calling back into the ViewModel. No `Toast` in the app UI. |
| Screens | Stateless `*Screen(state, onAction…)` composables. Top bar, actions, overflow, and search are Material 3 `TopAppBar` / `SearchBar` driven by screen state. No View `Toolbar`, `setSupportActionBar`, options menu, or `MenuProvider`. |
| Navigation | Single activity per form factor (phone, TV). Navigation Compose with **type-safe routes** (`@Serializable` route classes; arguments live in the route, not in a shared holder). `dreamdroid://` is a widget broadcast and an activity VIEW, not a NavHost deep link; search opens from `MainActivity.handleSearchIntent`. Navigation 3 is evaluated only after type-safe routes land (route classes carry over as keys). |
| Adaptive layout | Layout decisions use `currentWindowAdaptiveInfo()` / window size classes, not `Configuration.smallestScreenWidthDp`, so multi-window, foldables, and desktop windowing work. Prefer `NavigationSuiteScaffold` for bar vs rail. |
| Settings | Preferences DataStore behind a settings repository, exposed as `Flow`. One `SharedPreferencesMigration` imports old values. |
| Networking | One HTTP stack: typed `EnigmaClient` over OkHttp with sealed `EnigmaFailure`. No parallel request-handler hierarchy. |
| Persistence | Room (`room3`) with exported schemas and migration tests. |
| Background work | WorkManager for deferrable work (picon sync already is). No idle sync (by design, see offline doc). |
| Accessibility | Every interactive element has a role, a label, and a correct enabled/state description. 48 dp touch targets. Tests assert semantics, not only text. |
| Edge-to-edge / back | `enableEdgeToEdge()` on every activity; predictive back enabled (`enableOnBackInvokedCallback`); Compose `BackHandler`/`PredictiveBackHandler` rather than overriding activity back. |
| Build | Gradle version catalog (`gradle/libs.versions.toml`), Kotlin DSL build scripts, Compose BOM, R8 on release, Baseline Profile for startup and the hub / MultiEPG scroll paths. |
| Testing | JVM tests (JUnit 5, `kotlinx-coroutines-test`, fakes for repositories) for ViewModels, repositories, parsers, and mappers. Instrumented Compose tests for screens and navigation. Room migration tests. |

## Done

Phone screens are Kotlin Compose **NavHost destinations** (drawer, hub, EPG, forms, remote). Dialogs and detail sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s — no DialogFragment chassis. HTTP is coroutines + typed `EnigmaClient` over OkHttp; AsyncTask/Loaders are gone. Profiles live in Room (`dreambox`); legacy SQLite is migrate/restore only. Widgets are Glance with `AndroidRemoteViews` only for the dense RCU grid. Player stays **libVLC** with Compose overlay chrome via `VideoOverlayController` (no Fragment). TV hub is Compose; Leanback browse and the overlay zap `HorizontalGridView` are gone (`uses-feature android.software.leanback` stays for the Android TV launcher). Material 3 P0–P2 UX is [#430](https://github.com/sreichholf/dreamDroid/pull/430). Phone **offline cache + unified errors** (slices 1–7 of the offline plan) and **phone operator usertest** are verified (2026-09-19). **Tablet** and **TV / box** operator usertests are verified (2026-09-21) except newly added TV timer surfaces. TV hub session, Room cache, and Online-only streaming have shipped; widget offline still follows the phone shell. Mutation progress is an in-content `LinearProgressIndicator` (`IndeterminateProgressHost`), not a blocking dialog. Tablet hub destinations use a start-side `NavigationRail` inside the Compose `PhoneShell`; phone keeps the bottom `NavigationBar` and FAB dodge. The player channel list is Compose on phone and TV. Every screen and host has a `ViewModel` (see [ViewModel history](#viewmodel-history)); those ViewModels do not yet match the target shape (remediation R2). Edge-to-edge and predictive back are on for every activity.

## Still to do

One PR per item unless asked otherwise. Do not fold these into unrelated chrome work.

| Item | Notes |
| --- | --- |
| Remediation | The [remediation plan](#remediation-plan) below. It replaces the old "leave as they are" list. |
| Operator usertests | **Phone verified** (2026-09-19). **Tablet verified** (2026-09-21). **TV / box verified** (2026-09-21) except newly added timer surfaces. Phone drawer EPG and the bouquet service list remember list vs MultiEPG. Remaining box pass: hub **Timers** list add/edit/delete (`TvTimerHost`); bouquet service INFO/MENU overlay (stream / set / edit); MultiEPG detail set/edit (`TvTimerEditorHost`). File bugs; no drive-by refactors. Then a bugfix pass, one PR per fix. In-tree timer gate: `TvTimerHostTest` / `TvTimerListScreenTest` / `TvServiceTimerOverlayTest`. Re-run the phone pass after R3, R4, and R5 land; they change shell behavior. |
| 2.0 bugs | GitHub label `2.0-bug`. One PR per fix. Do not treat `feature` issues as ship blockers. Do not close more tickets unless asked. |
| Pre-release | Not GitHub-issue work. 1.15→2.0 Room/profile migration tests; minified `googleRelease` smoke; `ACCESS_LOCAL_NETWORK` (the app already targets SDK 37, so this is required before release, not optional); the 2.0 blockers marked in the remediation plan. The dual HTTP stack moved to remediation R9. |

## Deliberate exceptions

These deviate from a platform default on purpose. Each needs its reason to stay true; revisit when it stops being true. Everything else that looks legacy is in the remediation plan.

| Exception | Reason | Revisit when |
| --- | --- | --- |
| Service-row / now-playing progress is a transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)) | Product design choice for dense rows. Do not "restore" a Material track. | Design changes. |
| Widget is Glance + `AndroidRemoteViews` for the dense RCU grid | Glance cannot lay out the grid at that density; a Glance-only rewrite does not pay for itself. | Glance gains an equivalent layout. |
| `DatabaseHelper` stays as a read-only importer of leftover `dreamdroid` SQLite rows | Room has not shipped on Play yet; 1.15 users and cloud backups still carry the old file. It never creates `dreamdroid`. | One release after Room ships on Play, with migration telemetry or a support window agreed. |
| libVLC, not Media3 | Enigma2 streams (MPEG-TS, varied codecs, some transcoded) need libVLC coverage. `VideoOverlayController` stays the `View` + libVLC binder; its chrome is Compose. | Media3 covers the receiver formats. |
| Phone and TV are separate activities | Different input model (touch vs D-pad), theme (`androidx.tv` Material), and launcher category. Within each form factor there is one activity (R7). | — |
| `uses-feature android.software.leanback` (required = false) | Needed for the Android TV launcher. Leanback **libraries** are gone. | — |
| `usesCleartextTraffic` | Enigma2 WebInterface on a LAN is plain HTTP by default; users enter arbitrary hosts. HTTPS profiles keep working. | Never for arbitrary LAN hosts. |
| `Toast` in the widget and in a share flow that finishes its activity | No Scaffold or window survives to host a Snackbar. | — |

## Remediation plan

Earlier rules in this doc protected legacy patterns ("`MenuProvider` stays", "`NavigationHelper` stays on the activity", "ViewModels take `Application`", "no DI framework or repository layer", "the existing state class stays the model"). Those rules are withdrawn. This section lists what they left behind and the order to fix it.

### Locked decisions (operator, 2026-09-25)

| Decision | Settlement |
| --- | --- |
| Dependency injection | **Hilt** with KSP. No hand-rolled container, no Koin. |
| Navigation | **Type-safe Navigation Compose first** (D1). Navigation 3 only afterwards, and only if it removes code (D3). |
| Launcher trampoline | **Done in D2.** Phone and TV activities own their launcher categories. A search found no shortcut XML, widget component, or `activity-alias` targeting `TabbedNavigationActivity`, so it was deleted with no alias. |
| Problem table | Counts and file lists in "What is wrong today" are a snapshot. Each step's PR deletes the rows it fixes; there is no separate recount. |

One PR per numbered step unless the step says otherwise. Each PR leaves the app shippable, adds or updates tests for what it moves, and runs `spotlessCheck`, `:app:testGoogleDebugUnitTest`, and the touched instrumented classes via `bash .cursor/cloud/connected-test.sh`. **2.0 blocker** marks what must land before release; the rest can land before or after 2.0 but in this order.

### What is wrong today

| # | Problem | Where | Caused by |
| --- | --- | --- | --- |
| P1 | Receiver writes (power, sleep timer, send message, and their results) run on jobs launched from `MainActivity` via `NavigationHelper`; a rotation or process death cancels them mid-request. Results go out as `Toast`. | `ui/nav/NavigationHelper.kt`, `enigma/launch*Load` helpers | "`NavigationHelper` stays on the activity"; row 22 closed as a no-op |
| P2 | The phone top bar is a View `Toolbar` passed to `setSupportActionBar`, with `onCreateOptionsMenu` and 9 destinations registering `MenuProvider`s. Icons are tinted by hand. Search uses `android.app.default_searchable`. | `MainActivity`, `ToolbarMenuIcons.kt`, `MultiEpgDestination`, `Hub*Page`, `Profiles*Destination`, `TimerEditDestination`, `EpgBouquetDestination`, `ZapDestination` | "`MenuProvider` stays registered by the composable" |
| P3 | 29 ViewModels extend `AndroidViewModel` and pull strings, preferences, and `DreamDroid` globals through `Application`. They cannot be JVM-tested without Android. | every `*ViewModel.kt` | "A `ViewModel` takes `Application`" |
| P4 | Screen state is mutable Compose-state `*Session` / `*State` classes owned by the ViewModel, not an immutable `StateFlow` UI state. Loads live in the session, not in a data layer. | 81 files with `mutableStateOf`, 4 with `StateFlow` | "The existing state class stays the model" |
| P5 | No data layer and no DI. Process-wide mutable singletons: `DreamDroid` companion (current profile, location list, tag list, profile-changed listener, `getAppContext()`), `SessionConnectionHolder.shared`, `MultiEpgSyncHolder`, `UseDrivenCache`, `UserBouquetCache`, `TimerSnapshotStore`, `MovieSnapshotStore`, `ListEpgCache`, `PiconSync`. | `DreamDroid.kt`, `room/*Store.kt`, `room/*Cache.kt`, `multiepg/`, `ui/session/ConnectionStatus.kt` | "A new DI framework, repository layer … leave as they are" |
| P8 | 25 files show `Toast` for in-app results and errors. The Compose `SnackbarHost` from the offline plan (slice 2) was never added; `MainActivity` still holds a View `Snackbar` field that is only ever dismissed. | `Hub*Page`, `*Destination`, `EpgEventDialogSession`, `VideoOverlayController`, TV ViewModels, `BaseActivity` | Offline slice 2 allowed deferring the Snackbar host; nobody picked it up |
| P9 | Two HTTP paths: typed `EnigmaClient` and 16 `helpers/enigma2/requesthandler/*RequestHandler` classes behind `launchSimpleResultLoad`. | `helpers/enigma2/requesthandler/`, `enigma/` | Listed as "pre-release" with no owner |
| P10 | Online-only actions are greyed with `alpha` only (`onlineOnlyLook`). TalkBack announces them as normal buttons with no hint that they need the receiver. | `ui/session/OnlineOnly.kt` and its callers | Offline plan banned `enabled = false` without requiring replacement semantics |
| P11 | Tablet vs phone is `LocalConfiguration.smallestScreenWidthDp >= 600`, which ignores multi-window and foldable postures. | `ui/nav/PhoneShell.kt` | Not covered by any rule |
| P12 | Settings are raw `SharedPreferences` in 34 files, read synchronously, with `MainActivity` implementing `OnSharedPreferenceChangeListener`. | `SettingsState`, `DreamDroid`, `MainActivity`, widget, backup agent, … | Not covered by any rule |
| P13 | Build: Groovy scripts with inline versions, no version catalog, no Baseline Profile. A developer MultiEPG sync test ships in Settings. | `app/build.gradle`, `SettingsViewModel.runMultiEpgSyncTest` | Not covered by any rule |

### Steps

**Phase A — independent fixes (no architecture dependency)**

| Step | Fixes | Change | Proof |
| --- | --- | --- | --- |
| A1 **2.0 blocker** | P10 | `onlineOnlyLook` becomes a modifier that also sets `semantics { stateDescription = "Needs the receiver"; onClick(label = …) }` (keeps the click, per offline plan). Apply to every caller. | Compose tests assert the state description on a greyed zap / power / timer action while Offline. |
| A2 **2.0 blocker** | P8 (shell part) | Add a `SnackbarHostState` to `PhoneShell`'s `Scaffold` and the TV hub. Delete the dead View `Snackbar` in `MainActivity`. Mutation results from the hub pages and `NavigationHelper` go to it. | Compose test: a `BoxRejected` result shows a Snackbar with `statetext`. |
| A3 | P11 | Replace `smallestScreenWidthDp` with `currentWindowAdaptiveInfo()` (`material3-adaptive`); move bar vs rail to `NavigationSuiteScaffold` if it keeps FAB dodge, otherwise keep the custom switch on window size class. | Compose test with a forced window size shows the rail at Expanded and the bar at Compact. |
| A4 | P13 | Move versions to `gradle/libs.versions.toml`; convert `build.gradle` files to `.kts`. Remove the Settings MultiEPG sync test once `MultiEpgSyncTest` covers it, or move it behind the debug build type. | CI green; no dependency version changes in the same PR. |

**Phase B — data layer foundation**

| Step | Fixes | Change | Proof |
| --- | --- | --- | --- |
| B1 | P5 | Add Hilt (KSP). `@HiltAndroidApp` on `DreamDroid`, `@AndroidEntryPoint` on activities, modules providing `AppDatabase`, the OkHttp client, `EnigmaClient`, `SessionConnectionHolder`, and `MultiEpgSync` as `@Singleton`s. No behavior change; the `object` holders delegate to the injected instances until their callers move. | App starts; existing tests pass; one Hilt test replaces a binding with a fake. |
| B2 | P5, P12 | `SettingsRepository` over Preferences DataStore with `SharedPreferencesMigration`. `SettingsState`, theme, picon, and video settings read from it. Backup: `DreamDroidBackupAgent` / `BackupService` back up the DataStore file (key/value prefs backup no longer sees the values). Widget reads through the repository. | JVM test: migration keeps every key in `DreamDroid.PREFS_KEY_*`. Backup restore test on device. |
| B3 | P5 | `ProfileRepository`: Room profiles + current profile as `StateFlow<Profile?>`. Replaces `DreamDroid.getCurrentProfile()`, `ProfileChangedListener`, `cachedDeviceInfo` on the profile object, and the location / tag lists. `MainActivity.onProfileChanged` becomes a collector in the shell ViewModel. | JVM test: switching profile emits once and clears per-profile caches. |
| B4 | P5 | Domain repositories, one PR each, absorbing the singletons: `ServiceRepository` (bouquets, service lists, `UseDrivenCache`, `UserBouquetCache`), `EpgRepository` (`MultiEpgSync`, `ListEpgCache`, now/next), `TimerRepository` (`TimerSnapshotStore`), `MovieRepository` (`MovieSnapshotStore`), `ReceiverRepository` (zap, power, sleep timer, message, remote keys, volume, screenshot, signal). Each repository owns the offline rules from the offline plan (`isCacheableUserBouquetContainer`, `hasCache`). | JVM tests with a fake `EnigmaClient` and `AppDatabase.inMemory`; the offline-plan guard tests move with the code. |

**Phase C — ViewModels and screens (after the repository they need)**

| Step | Fixes | Change | Proof |
| --- | --- | --- | --- |
| C1 **2.0 blocker** | P1 | Receiver actions move off the activity: power, sleep timer, and send message run in an activity-scoped `ShellViewModel` (or the dialog route's parent entry) through `ReceiverRepository` (or the existing client until B4 lands). Results go to the A2 Snackbar. Reverses the row 22 no-op. `NavigationHelper` keeps only navigation. | Instrumented test: start a power toggle, recreate the activity, the result still arrives. |
| C2 | P3, P4, P8 | Per screen group, in the ViewModel history order: the ViewModel takes repositories + `SavedStateHandle` (`hiltViewModel()`), drops `AndroidViewModel`, exposes `StateFlow<*UiState>`, and puts user messages in that state instead of `Toast`. The `*Session` class goes away or becomes a plain state holder inside the ViewModel. Strings resolve in the UI (`@StringRes` / `UiText`), not in the ViewModel. | Each PR adds a JVM ViewModel test with fake repositories; the `*Screen` Compose test updates to the new state type. |
| C3 | P2 | Replace the View toolbar with a Material 3 `TopAppBar` in `PhoneShell`; each destination supplies title, actions, and overflow from its UI state. Search becomes a Compose `SearchBar` route. Remove `setSupportActionBar`, `onCreateOptionsMenu`, all `MenuProvider`s, `ToolbarMenuIcons`, the `default_searchable` meta-data, and the `ACTION_SEARCH` intent filter on `MainActivity`. Once nothing needs AppCompat, `BaseActivity` moves to `ComponentActivity`. Can be split by destination; the shell PR goes first. | Compose tests click toolbar actions by content description on each migrated destination. |
| C4 | P9 | Move each `*RequestHandler` call to a typed `EnigmaClient` function behind `ReceiverRepository` / `TimerRepository`; delete `helpers/enigma2/requesthandler/` and `launchSimpleResultLoad`. | JVM tests on XML fixtures for each moved call; `BoxRejected` mapping preserved. |

**Phase D — navigation**

| Step | Fixes | Change | Proof |
| --- | --- | --- | --- |
| D1 **Done** | — | Type-safe routes: `@Serializable` route classes replace string patterns. Profile edit, timer edit, EPG, MultiEPG, service EPG, search, and sleep timer carry their arguments on the route. `PhoneNavHostState` keeps the start route and pick-request codes. Drawer items map to route objects. A saved back stack from the old string routes starts at the start destination. `dreamdroid://` is not a graph deep link; search stays `MainActivity.handleSearchIntent`. | `EpgSearchRouteTest`, `PhoneRouteRestoreTest`, `ServiceEpgRetentionTest`, `ProfileEditCreateRouteTest`, `SleepTimerDialogHostTest`. |
| D2 **Done** | — | One TV `NavHost` in `tv.activities.MainActivity` hosts `TvHub`, `TvMultiEpg`, `TvSettings`, and `TvProfiles`. Phone `MainActivity` has `MAIN` / `LAUNCHER` / `MULTIWINDOW_LAUNCHER` and the `dreamdroid` `VIEW` filter. TV `MainActivity` has `MAIN` / `LEANBACK_LAUNCHER`. No alias: nothing in the repo targeted `TabbedNavigationActivity`. | `TvHubNavHostTest`, `LauncherIntentTest`, `TvComposeHubHostTest`. |
| D3 | — | Evaluate Navigation 3 against the type-safe graph. Write the decision here; migrate only if it removes code (e.g. list-detail on tablet). | Decision recorded. |

**Phase E — performance and release**

| Step | Fixes | Change | Proof |
| --- | --- | --- | --- |
| E1 | P13 | Baseline Profile module (macrobenchmark) covering cold start → hub, hub scroll, MultiEPG pan. Ship `profileinstaller`. | Benchmark numbers in the PR. |
| E2 | — | Release checks: R8 `googleRelease` smoke, Room 1.15→2.0 migration tests, `ACCESS_LOCAL_NETWORK` flow on SDK 37. | Pre-release row above. |

When a step lands, its PR marks the step done here and deletes the rows it fixed from "What is wrong today".

## ViewModel history

Status (2026-09-23): every screen and host has a `ViewModel`; row 22 closed as a no-op (reopened by C1). Phone NavHost destinations get their state from an entry-scoped `ViewModel`, `PhoneNavHostState` is activity-scoped on a `SavedStateHandle`, the hub pages share the hub entry's scope, and the TV, share, setup, and player hosts use activity-scoped ViewModels.

That pass moved **ownership**: state and load jobs left `remember` / `rememberSaveable` and survive rotation and back-stack pops. It deliberately kept the old `*Session` state classes, `Application` constructors, `MenuProvider`s, and activity-held receiver actions so each PR stayed small. Those are no longer rules; they are remediation P1–P5 and C1–C3. Current ViewModel rules are the [target architecture](#target-architecture) row.

Rules from that pass that still hold:

- `viewModel()` / `hiltViewModel()` is called from the NavHost route (or `*Destination`). `*Screen` takes state and callbacks, so Compose tests call the screen directly. Tests construct a `ViewModelStore` only when the test is about retention.
- Saved fields live in `SavedStateHandle`.
- Dialog open/closed flags and pure UI toggles (key-repeat timing on the virtual remote, FAB / destination-bar controllers for the life of `PhoneShell`) stay in composition.
- Hub children (`HubServiceListPage`, `HubTimerListPage`, `HubMovieListPage`) are not their own routes. Their ViewModels are scoped to the hub back-stack entry so a tab change keeps the loaded list.
- Retention is tested: opening a detail and popping back shows the same loaded list.
- A TV host that copies a phone screen changes in its own PR after that phone screen.
- `VideoOverlayController` stays the `View` and libVLC binder; zap-list and playback session state live in `VideoPlaybackViewModel`, which the controller observes.

| # | Screen / host | Status |
| --- | --- | --- |
| 1 | Device info (template) | Done |
| 2 | Phone nav host (`PhoneNavHostState`) | Done |
| 3–19 | Screenshot, Signal, Zap, Backup, Current service, Settings, Profiles, Profile edit, Timer edit, Service pick, EPG bouquet, EPG search, MultiEPG, Hub shell, Hub service list, Hub timers, Hub movies | Done |
| 19a–19b | Service EPG, Hub now playing | Done |
| 20 | TV hosts (`TvComposeHubHost`, `TvTimerHost`, `TvTimerEditor`, `TvTimerServicePick`, `TvMultiEpgHost`) | Done |
| 21 | Share and setup | Done |
| 22 | Dialog routes (`SleepTimer`, `SendMessage`, `Power`) | Closed as no-op; reopened as remediation C1 |
| 23 | Player | Done |

## Out of scope until asked

Enigma2 **server** / webif patches, VLC **codec / stream protocol**, Media3/ExoPlayer swap (see exceptions), deleting the home-screen widget, merging `master` into `main`.
