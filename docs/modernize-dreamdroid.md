# Modernize dreamDroid

dreamDroid 2.0 is a Compose Material 3 Enigma2 remote for phone and a Compose (`androidx.tv`) hub for TV, written in Kotlin with coroutines. UI is proven by instrumented Compose tests, ViewModels and data code by JVM tests. Rewrite trunk is `main` (`master` is 1.15; do not merge them). Floor: minSdk 26, compileSdk / targetSdk 37, JDK 25 (bytecode Java 17). Agent rules: [`AGENTS.md`](../AGENTS.md). Related: [`multiepg.md`](multiepg.md), [`offline-and-errors.md`](offline-and-errors.md).

## Done

- **UI:** every phone screen is a Compose NavHost destination with type-safe `@Serializable` routes (D1). Dialogs and sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s. `PhoneShell` draws a Material 3 `TopAppBar` with no options menu or `MenuProvider` (C3), and picks rail vs bar from `currentWindowAdaptiveInfoV2()` (A3). TV is one Compose `NavHost` (hub, MultiEPG, settings, profiles); Leanback browse is gone (D2). Phone and TV activities own their launcher categories; the trampoline activity is deleted. Navigation 3 was evaluated and rejected (D3).
- **Messages:** in-app results show in a snackbar via `ShellMessages` (shell, TV hub, player) (A2, C1, C2 message part). Power, sleep timer, and send message run on `ShellViewModel`.
- **Data:** one HTTP stack, typed `EnigmaClient` over OkHttp with sealed `EnigmaFailure`; the request-handler hierarchy is gone (C4). Profiles in Room with `ProfileRepository` exposing the current profile as `StateFlow` (B3). Offline cache and unified errors shipped.
- **ViewModels:** every screen and host has a `ViewModel` scoped to its back-stack entry or activity; state and load jobs no longer live in `remember`. They do not yet match the target shape (see B1 + C2).
- **Platform:** edge-to-edge and predictive back on every activity. Online-only controls have accessibility semantics (A1). Glance widget. libVLC player with Compose overlay chrome.
- **Cleanups:** list-row action menus are a Compose `DropdownMenu` anchored to the row (`RowMenu`); `AnchorPopup` and the popup menu XML are gone. The player overlay forces dark through the Compose theme instead of `AppCompatDelegate.localNightMode`. Widget configuration without profiles shows an explanation and an "Open dreamDroid" button instead of a `Toast`.
- **Build:** Kotlin DSL, version catalog (A4). Migration tests for Room v1→8 and the 1.15 SQLite import; a real 1.15 → 2.0 in-place upgrade passes on the emulator (`upgrade_from_115`, 2026-09-27).
- **Usertests:** phone (2026-09-19), tablet and TV / box (2026-09-21) verified, except the newly added TV timer surfaces.

## Still to do

One PR per item. Do not fold these into unrelated work.

### 2.0 blockers

- [ ] **TV timer box pass** (operator): hub Timers list add/edit/delete (`TvTimerHost`), bouquet service INFO/MENU overlay (stream / set / edit), MultiEPG detail set/edit (`TvTimerEditorHost`). File bugs; no drive-by refactors.
- [ ] **`2.0-bug` fixes**, one PR per fix. #77 closed (won't fix: plugins grabbing the Linux input devices). #120 (TV live streaming) did not reproduce on Google TV; close or get details. Do not treat `feature` issues as blockers.
- [ ] **E2 release checks** (operator, at first 2.0 release): minified `googleRelease` smoke including the release-signed Play upgrade; `ACCESS_LOCAL_NETWORK` grant and deny pass on an API 37 device.
- [ ] Re-run the phone usertest pass (shell messages and top bar changed since it was verified).

### Opportunistic (when a feature or bug touches the screen)

- [ ] **B1 — Hilt** (KSP): `@HiltAndroidApp` on `DreamDroid`, `@AndroidEntryPoint` activities, `@Singleton` modules for `AppDatabase`, OkHttp, `EnigmaClient`, `SessionConnectionHolder`, `MultiEpgSync`. The `object` holders delegate to injected instances until callers move. Land together with the first C2 ViewModel, not alone. Proof: one Hilt test replaces a binding with a fake.
- [ ] **C2 — ViewModel shape**, per screen group: take repositories + `SavedStateHandle` via `hiltViewModel()`, drop `AndroidViewModel`, expose `StateFlow<*UiState>` (including user messages and the destination title, which today follows `Activity.title`), fold or delete the `*Session` class, resolve strings in the UI (`@StringRes` / `UiText`). Proof: JVM ViewModel test with fake repositories; `*Screen` test updated. Fixes: ~29 `AndroidViewModel`s and ~81 files of `mutableStateOf` session state.
- [ ] **B4 — repositories**, only when a C2 group needs one: `ServiceRepository` (`UseDrivenCache`, `UserBouquetCache`), `EpgRepository` (`MultiEpgSync`, `ListEpgCache`), `TimerRepository` (`TimerSnapshotStore`), `MovieRepository` (`MovieSnapshotStore`), `ReceiverRepository` (zap, power, remote keys, volume, …). Each owns its offline rules. Proof: JVM tests with a fake `EnigmaClient` and `AppDatabase.inMemory`.

- [ ] **`VideoActivity` off AppCompat**: it forces dark through the Compose theme and `SystemBarStyle.dark`; check whether anything else still needs `AppCompatActivity` (Material Components View theme, dialogs) and move it to `ComponentActivity` if not.

### Deferred (after 2.0)

- [ ] **B2 — DataStore**: `SettingsRepository` over Preferences DataStore with `SharedPreferencesMigration` (34 files use `SharedPreferences`). Risks backup and startup regressions; if a ViewModel needs testable settings first, put a `SettingsRepository` over `SharedPreferences`. Needs operator sign-off.
- [ ] **E1 — Baseline Profile** (cold start → hub, hub scroll, MultiEPG pan). Only if jank is reported.

When an item lands, its PR moves it into **Done** here.

## Target architecture

New and touched code follows this ([guide to app architecture](https://developer.android.com/topic/architecture)).

| Area | Target |
| --- | --- |
| Layers | Compose screens + `ViewModel` → repositories owning Enigma HTTP, Room, and settings. Screens never call `EnigmaClient`, DAOs, or `SharedPreferences` directly. |
| DI | Hilt (KSP). No mutable service-locator `object`s, no static `getAppContext()`. |
| ViewModel | Takes repositories and `SavedStateHandle`; no `Application`, `Context`, `View`, or activity. One immutable `StateFlow<*UiState>` collected with `collectAsStateWithLifecycle()`. Work in `viewModelScope`. |
| UI events | User messages are UI state, shown in a `SnackbarHostState` and cleared via the ViewModel. No `Toast`. |
| Screens | Stateless `*Screen(state, onAction…)`. Material 3 `TopAppBar` / `SearchBar`; no View toolbar or options menu. |
| Navigation | One activity per form factor, Navigation Compose with type-safe routes. |
| Layout | Window size classes via `currentWindowAdaptiveInfoV2()`, not `smallestScreenWidthDp`. |
| Settings | DataStore behind a settings repository (deferred, see B2). |
| Persistence / work | Room with exported schemas and migration tests; WorkManager for deferrable work. |
| Accessibility | Role, label, and state on every interactive element; 48 dp targets; tests assert semantics. |
| Testing | JVM tests with fake repositories for ViewModels, repositories, parsers; instrumented Compose tests for screens. |

## Deliberate exceptions

Do not "fix" these. Each states when to revisit it.

| Exception | Reason | Revisit when |
| --- | --- | --- |
| Service-row progress: transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)) | Design choice for dense rows. Keep a progress semantics description for TalkBack. | Design changes. |
| Widget uses `AndroidRemoteViews` for the RCU grid | A Glance-only grid at that density is awkward and does not pay for itself. | Glance gains an equivalent layout. |
| `DatabaseHelper` as read-only importer of old `dreamdroid` SQLite | Installs that skipped 1.15 and old backups still carry the file. | One release after 2.0 ships on Play. |
| libVLC, not Media3 | Receiver streams (MPEG-TS with MPEG-2, AC3, varied codecs) need decoding that does not depend on device hardware. Cost: `libvlc-all` dominates APK size (~196 MB universal debug APK) and is a native dependency to keep current. | Media3 covers the receiver formats on target devices. |
| `usesCleartextTraffic` | Users enter arbitrary LAN hosts over plain HTTP; a network security config cannot scope cleartext to private networks. | Never for arbitrary LAN hosts. |
| `Toast` in `WidgetRemoteRequest` and `ShareActivity` | Broadcast with no UI, and a share flow that finishes its activity; no window survives to host a Snackbar. | — |
| `BaseActivity`, `ShareActivity`, and the widget configuration on `AppCompatActivity` | `AppCompatDelegate.setDefaultNightMode` for the in-app theme setting; the platform `UiModeManager.setApplicationNightMode` needs API 31. `VideoActivity` no longer needs AppCompat for night mode (see todo). | minSdk reaches 31. |

## Out of scope until asked

Enigma2 server / webif patches, VLC codec or stream protocol, Media3 swap, deleting the widget, merging `master` into `main`.
