# Modernize dreamDroid

Phone users get a **Compose Material 3** Enigma2 remote. TV gets a **Compose** hub (`androidx.tv`), not Leanback browse. New types are **Kotlin** + coroutines. Proof is **instrumented Compose tests**, not tap loops. Rewrite trunk is **`main`** (`master` is last 1.15 — do not merge them).

No permanent keepers. Old “keep DialogFragments / RemoteViews / HttpURLConnection / Leanback / Java overlay” calls were sequencing, not freezes. When a leftover surface is touched, move it to the current Android/Compose default. Do not preserve a legacy chassis only because an older plan said keep.

Floor: minSdk 26, JDK 25, debug package `net.reichholf.dreamdroid.debug`. Agent rules and how to run tests: [`AGENTS.md`](../AGENTS.md). MultiEPG product design: [`docs/multiepg.md`](multiepg.md). Offline cache + unified errors: phone shipped — [`docs/offline-and-errors.md`](offline-and-errors.md) (TV hub session/cache shipped; widget still follows).

## Done

Phone screens are Kotlin Compose **NavHost destinations** (drawer, hub, EPG, forms, remote). Dialogs and detail sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s — no DialogFragment chassis. HTTP is coroutines + typed `EnigmaClient` over OkHttp; AsyncTask/Loaders are gone. Profiles live in Room (`dreambox`); legacy SQLite is migrate/restore only. Widgets are Glance with `AndroidRemoteViews` only for the dense RCU grid. Player stays **libVLC** with Compose overlay chrome via `VideoOverlayController` (no Fragment). TV hub is Compose; Leanback browse and the overlay zap `HorizontalGridView` are gone (`uses-feature android.software.leanback` stays for the Android TV launcher). Material 3 P0–P2 UX is [#430](https://github.com/sreichholf/dreamDroid/pull/430). Phone **offline cache + unified errors** (plan slices 1–7) and **phone operator usertest** are verified (2026-09-19). **Tablet** and **TV / box** operator usertests are verified (2026-09-21) except newly added TV timer surfaces. TV hub session, Room cache, and Online-only streaming have shipped; widget offline still follows the phone shell. Mutation progress is an in-content `LinearProgressIndicator` (`IndeterminateProgressHost`), not a blocking dialog. Tablet hub destinations use a start-side `NavigationRail` inside the Compose `PhoneShell`; phone keeps the bottom `NavigationBar` and FAB dodge. The player channel list is Compose on phone and TV.

## Still to do

One PR per item unless asked otherwise. Do not fold these into unrelated chrome work.

| Item | Notes |
| --- | --- |
| ViewModels | Phone rows 1–19 have landed (#491, #492, #493, #496, #498). Row 22 (dialog routes) closed as a no-op. Remaining: service EPG and hub now-playing (rows 19a/19b), TV hosts (row 20), share and setup (21), and player (23). Order, rules, and the per-screen PR list: [ViewModels](#viewmodels) below. Do not fold a row into unrelated work. |
| Operator usertests | **Phone verified** (2026-09-19). **Tablet verified** (2026-09-21). **TV / box verified** (2026-09-21) except newly added timer surfaces. Phone drawer EPG and the bouquet service list remember list vs MultiEPG. Remaining box pass: hub **Timers** list add/edit/delete (`TvTimerHost`); bouquet service INFO/MENU overlay (stream / set / edit); MultiEPG detail set/edit (`TvTimerEditorHost`). File bugs; no drive-by refactors. Then a bugfix pass, one PR per fix. In-tree timer gate: `TvTimerHostTest` / `TvTimerListScreenTest` / `TvServiceTimerOverlayTest`. |
| 2.0 bugs | GitHub label `2.0-bug`. One PR per fix. Do not treat `feature` issues as ship blockers. Do not close more tickets unless asked. |
| Pre-release | Not GitHub-issue work. 1.15→2.0 Room/profile migration tests; minified `googleRelease` smoke; targetSdk 37 `ACCESS_LOCAL_NETWORK`; dual HTTP stacks (OkHttp `EnigmaClient` and the request-handler helpers). |

**Keep:** service-row / now-playing progress is a transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)). Do not “restore” a Material track. Widget stays Glance + `AndroidRemoteViews` for the dense RCU grid — a Glance-only rewrite does not pay for that layout. `DatabaseHelper` stays a read-only leftover-file importer for pre-Room installs and cloud snapshots — Room has not shipped on Play yet; do not drop the path. It never creates `dreamdroid`.

## ViewModels

Status (2026-09-23): rows 1–19 are done. Every phone NavHost destination in those rows gets its state from a `ViewModel`, `PhoneNavHostState` is activity-scoped on a `SavedStateHandle`, and the hub pages share the hub entry's scope. Row 22 closed as a no-op. Rows 19a, 19b, 20, 21, and 23 are open.

Screen state is already extracted (`*State`, `*UiState`, `*Session`). Before a row lands, it is created with `remember` inside the destination, so it dies when that composable leaves the tree. Rotation and process death are handled by hand (`rememberSaveable`, and `PhoneNavHostState.saveState` from `MainActivity.onSaveInstanceState`). Load work is a `Job` held beside the state.

The target is a `ViewModel` per screen, scoped to that screen's `NavBackStackEntry`. The composable renders state it is given. HTTP, Room, and the Compose layout stay.

### Rules for every row

- The existing state class stays the model. The `ViewModel` owns it. `*Screen` keeps taking that state, so current instrumented tests keep calling the screen directly.
- `viewModel()` is called from the NavHost route (or from `*Destination` with an optional state/ViewModel parameter). Tests pass the state in. They do not construct a `ViewModelStore` unless the test is specifically about retention.
- Jobs move to `viewModelScope`. Remove `remember { mutableStateOf<Job?> }`.
- Fields that are `rememberSaveable` today, or keys in `PhoneNavHostState.saveState`, move to `SavedStateHandle`. Do not add new saved fields in the same PR.
- A `ViewModel` takes `Application`, `SavedStateHandle`, or a small interface. It does not hold `MainActivity`, a `View`, or a `Menu`.
- `MenuProvider` stays registered by the composable. The `ViewModel` does not implement it.
- Dialog open/closed flags and other pure UI toggles stay in composition.
- `SessionConnectionHolder` stays the process-wide connection status. ViewModels collect it.
- `NavigationHelper` stays on the activity. It needs the activity for toasts and loads. The nav `ViewModel` does not absorb it.
- Phone and TV activities stay separate. A TV host that copies a phone screen gets its own PR after that phone screen.
- Hub children (`HubServiceListPage`, `HubTimerListPage`, `HubMovieListPage`) are not their own routes. Their ViewModels are scoped to the hub back-stack entry so a tab change keeps the loaded list.
- Retention is the behavior change: opening a detail and popping back must show the same loaded list, not a fresh load. Update the destination test that assumed a new session on every composition.
- Proof for each row: existing `*Screen` test still passes; one navigation test (or a `SavedStateHandle` unit test) shows the retained or restored field. `bash .cursor/cloud/connected-test.sh` for the touched instrumented class. `./gradlew spotlessCheck` and `:app:testGoogleDebugUnitTest` when a JVM test was added.
- First PR adds an explicit `androidx.lifecycle:lifecycle-viewmodel-compose` dependency, aligned with `activity-compose` 1.13. Do not rely on it arriving only transitively through `navigation-compose`.

### Order

One PR per row. Device info is the pattern the later rows copy.

| # | PR | What moves | Status |
| --- | --- | --- | --- |
| 1 | Device info | `DeviceInfoDestination` / `DeviceInfoUiState`. One load job and one `rememberSaveable` model. This is the template. | Done |
| 2 | Phone nav host | `PhoneNavHostState` becomes an activity-scoped `ViewModel`. `SavedStateHandle` replaces `saveState` / `restoreState`. `MainActivity` keeps `NavigationHelper`, the drawer, and profile-check UI. Profile-check loads move into `viewModelScope` only when they can take an application `Context`; otherwise they stay on the activity and the PR says so. | Done |
| 3 | Screenshot | `ScreenshotDestination` / `ScreenshotUiState`. | Done |
| 4 | Signal | `SignalDestination` / `SignalUiState` / `SignalPollGate`. Poll loop and `AudioTrack` stay behind the ViewModel; the `Handler` does not outlive `viewModelScope`. | Done |
| 5 | Zap | `ZapDestination` / `ZapSession` / `ZapListState`. | Done |
| 6 | Backup | `BackupDestination` / `BackupUiState`. `BackupService` is constructed from the application context. | Done |
| 7 | Current service | `CurrentServiceDestination` / `CurrentServiceSession` / `CurrentServiceUiState`. | Done |
| 8 | Settings | `SettingsDestination` / `SettingsState`. Preference reads leave `remember`. | Done |
| 9 | Profiles | `ProfilesDestination` / `ProfilesSession` / `ProfilesListState`, including discovery. | Done |
| 10 | Profile edit | `ProfileEditState` and the profile-edit route args that today sit on `PhoneNavHostState`. | Done |
| 11 | Timer edit | `TimerEditDestination` / `TimerEditSession` / `TimerEditState`. | Done |
| 12 | Service pick | `PickServiceDestination` and `TimerServicePickDestination` / `TimerServicePickSession`. | Done |
| 13 | EPG bouquet | `EpgBouquetDestination` / `EpgBouquetSession` / `EpgBouquetListState`. | Done |
| 14 | EPG search | `EpgSearchDestination`. | Done |
| 15 | MultiEPG | `MultiEpgDestination` / `MultiEpgSession` / `MultiEpgMenuSession`. Window position that is `rememberSaveable` goes to `SavedStateHandle`. | Done |
| 16 | Hub shell | `HubDestination` mode, selected row, bouquet refs (`rememberSaveable` today). | Done |
| 17 | Hub service list | `HubServiceListSession` / `ServiceListState`, scoped to the hub entry. | Done |
| 18 | Hub timers | `HubTimerListSession` / `TimerListState`, scoped to the hub entry. | Done |
| 19 | Hub movies | `HubMovieListSession` / `MovieListState`, scoped to the hub entry. | Done |
| 19a | Service EPG | `ServiceEpgDestination` / `ServiceEpgListState`, its `ComposeRefreshState`, empty message, and load job. Missed by the first plan. | Open |
| 19b | Hub now playing | `HubNowPlaying` load job, `CurrentServiceLoadGate`, and loaded `CurrentService`, scoped to the hub entry. The sheet flag stays in composition. Missed by the first plan. | Open |
| 20 | TV hosts | After the matching phone row: `TvComposeHubHost`, `TvTimerHost`, `TvTimerEditor`, `TvTimerServicePick`, `TvMultiEpgHost`. One PR per host. Do not merge the TV activity into the phone one. | Open |
| 21 | Share and setup | `ShareActivity` / `ShareProfilesListState` and `SetupAssistantScreen`. Activity-scoped ViewModels. These activities have no phone `NavHost`. | Open |
| 22 | Dialog routes | `SleepTimer`, `SendMessage`, and `Power` only when they own a load or a session. `About` stays composition. Visibility flags stay in the dialog composable. No-op: every submit dismisses its dialog, so an entry-scoped ViewModel would cancel its own request; the jobs and the sleep-timer fetch live on `NavigationHelper`, and the dialogs hold only form input. | Done |
| 23 | Player | `VideoOverlayController` stays the `View` and libVLC binder. A last PR may move zap-list and playback session state into a ViewModel the controller observes. Do not start it before row 20. | Open |

### Leave as they are

- `PhoneShell` FAB and destination-bar controllers (`ShellFabController`, `ShellDestinationBarController`). They are composition locals for the life of the shell.
- The virtual remote's key-repeat flags, when they are only UI timing and not a session.
- Glance widget state.
- A new DI framework, repository layer, or rewrite of `EnigmaClient`. These PRs move ownership, not the HTTP stack.

## Out of scope until asked

Enigma2 **server**, VLC **codec / stream protocol**, Media3/ExoPlayer swap, deleting the home-screen widget, merging `master` into `main`.
