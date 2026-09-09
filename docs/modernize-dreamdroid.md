# Modernize dreamDroid phone app

Phone users get a Compose Material 3 remote. TV stays Leanback until a later program. minSdk is 26. Dead deps went first, then the typed Enigma2 client, then phone screens.

Rewrite trunk is **`main`**. `master` is last 1.15 stable. Do not merge `master` or branches cut from `master` into `main`.

Default UI proof is instrumented Compose tests, not `verify-dreamdroid.py` tap loops. See [`AGENTS.md`](../AGENTS.md). AVD `dreamdroid-verify`. JDK 17. Debug package `net.reichholf.dreamdroid.debug`.

GitHub Actions: [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) runs on PRs/`main` — unit tests + assemble + androidTest compile (JDK 17), plus instrumented Compose tests on an API 30 emulator (`-Pci` disables ABI splits for a single installable APK).

## Status as of 2026-09-09

| Unit | GitHub | State | Head / merge |
| --- | --- | --- | --- |
| pr-floor | #163 closed without squash-merge | on `main` as direct commits | `60e59f72` minSdk 26 / drop jcenter. `0c769e8f` OkHttp 4.12.0. `e7b43a7c` back to OkHttp 3.14.9 so Kotlin 1.6.21 still compiled. |
| pr-about | [#164](https://github.com/sreichholf/dreamDroid/pull/164) | merged | `7372a21d` |
| About night | [#166](https://github.com/sreichholf/dreamDroid/pull/166) | merged | `17042aa0` `LocalContentColor` / `onSurface` |
| About tests | [#167](https://github.com/sreichholf/dreamDroid/pull/167) | merged | `d725ee9b` `AGENTS.md`, `AboutScreenTest`, `AboutDialogHostTest` |
| pr-client | [#165](https://github.com/sreichholf/dreamDroid/pull/165) | merged | `de010cb4` |
| pr-profiles | [#168](https://github.com/sreichholf/dreamDroid/pull/168) | merged | `e7563787` |
| pr-services | [#169](https://github.com/sreichholf/dreamDroid/pull/169) | merged | squash `f125c117` |
| TV/Movies/Timer rows | [#170](https://github.com/sreichholf/dreamDroid/pull/170) | merged | `dbd20628` |
| skill+plan | [#171](https://github.com/sreichholf/dreamDroid/pull/171) | merged | `b98ac193` |
| dead-weight (retrostreams + stub) | [#172](https://github.com/sreichholf/dreamDroid/pull/172) | merged | `970ad4bc` drop android-retrostreams + leftover `app/res/service_list_pager.xml` |
| Zap typed rows | [#173](https://github.com/sreichholf/dreamDroid/pull/173) | merged | `452f7c80` typed `enigma.Service` into Zap. XML grid stays. |
| Cloud Agent env | [#174](https://github.com/sreichholf/dreamDroid/pull/174) | merged | Cloud Agent `environment.json` / install helpers. |
| dead-weight (MediaPlayer + MultiDex lib + orphan layouts) | [#175](https://github.com/sreichholf/dreamDroid/pull/175) | merged | drop unused MediaPlayer UI, `androidx.multidex` install helper, orphan XML. Keep `multiDexEnabled`, `MEDIA_PLAYER_PLAY`, ButterKnife. |
| Event typed rows (ServiceEpgList) | [#176](https://github.com/sreichholf/dreamDroid/pull/176) | merged | typed `enigma.Event` into `ServiceEpgListFragment`. XML list stays. Bouquet/search EPG not migrated. |
| dead-weight (EPG timeline + prefs + menus + bottom nav) | [#177](https://github.com/sreichholf/dreamDroid/pull/177) | merged | `c89afc11` drop dead `EpgTimelineFragment`, `legacy-preference-v14`, `legacy-support-v4`, unused menus, GONE bottom nav. Keep ButterKnife. |
| EPG bouquet typed rows | [#179](https://github.com/sreichholf/dreamDroid/pull/179) | merged | `21b6db59` typed `enigma.Event` into `EpgBouquetFragment` / `EpgBouquetAdapter`. Search EPG not migrated. |
| EPG search typed rows | [#180](https://github.com/sreichholf/dreamDroid/pull/180) | merged | `4f3249b2` typed `enigma.Event` into `EpgSearchFragment`; reuses `EpgBouquetAdapter`. Drop unused `EpgAdapter`. |
| PickService typed list | [#181](https://github.com/sreichholf/dreamDroid/pull/181) | merged | `ce7cfb1d` typed `enigma.Service` into `PickServiceFragment`; load via `GetBouquetListTask`. Intent still maps one `ExtendedHashMap` at send. |
| CurrentService typed | [#183](https://github.com/sreichholf/dreamDroid/pull/183) | merged | typed `enigma.CurrentService` for `/web/getcurrent`; XML UI stays; hash only at EPG detail/timer edge. |
| profile-edit-compose | [#184](https://github.com/sreichholf/dreamDroid/pull/184) | merged | `ProfileEditFragment` Compose form + `ProfileEditScreenTest`. |
| zap-compose | [#185](https://github.com/sreichholf/dreamDroid/pull/185) | merged | `55ecc5df` `ZapFragment` Compose grid + `ZapScreenTest`. |
| virtual-remote-compose | [#186](https://github.com/sreichholf/dreamDroid/pull/186) | merged | `bd2ccfb9` `VirtualRemoteFragment` Compose pad + `VirtualRemoteScreenTest`. |
| screenshot-compose | [#187](https://github.com/sreichholf/dreamDroid/pull/187) | merged | `527e4c7f` `ScreenShotFragment` Compose + PhotoView + `ScreenshotScreenTest`. |
| settings-compose | [#188](https://github.com/sreichholf/dreamDroid/pull/188) | merged | `63ea96ba` `MyPreferenceFragment` Compose prefs + `SettingsScreenTest`. |
| backup-compose | [#189](https://github.com/sreichholf/dreamDroid/pull/189) | merged | `16e86fa2` `BackupFragment` Compose + `BackupScreenTest`. |
| current-event-compose | [#190](https://github.com/sreichholf/dreamDroid/pull/190) | merged | `CurrentServiceFragment` Compose + `CurrentServiceScreenTest`.
| pick-service-compose | [#191](https://github.com/sreichholf/dreamDroid/pull/191) | merged | `PickServiceFragment` Compose list + `PickServiceScreenTest`.
| epg-bouquet-compose | [#192](https://github.com/sreichholf/dreamDroid/pull/192) | merged | `EpgBouquetFragment` Compose list + `EpgBouquetScreenTest`.
| service-epg-compose | [#194](https://github.com/sreichholf/dreamDroid/pull/194) | merged | `ServiceEpgListFragment` Compose list + `ServiceEpgScreenTest`.
| epg-search-compose | [#193](https://github.com/sreichholf/dreamDroid/pull/193) | merged | `EpgSearchFragment` Compose list; dropped `EpgBouquetAdapter` + multi-service row XML. |
| share-profiles-compose | [#196](https://github.com/sreichholf/dreamDroid/pull/196) | merged | `ShareActivity` Compose list + `ShareProfilesScreenTest`; dropped `ProfileAdapter`.
| epg-detail-compose | [#197](https://github.com/sreichholf/dreamDroid/pull/197) | merged | `EpgDetailBottomSheet` Compose + typed `Event`; pinned actions outside scroll.
| drawer-dialogs-compose | [#198](https://github.com/sreichholf/dreamDroid/pull/198) | merged | Sleep timer / send message / power / changelog / connection error → Compose; drop sleeptimer/send_message XML. |
| device-info-typed | [#199](https://github.com/sreichholf/dreamDroid/pull/199) | merged | typed `enigma.DeviceInfo` for `/web/deviceinfo`; XML UI stays; CheckProfile uses typed parse. |
| signal-typed | [#200](https://github.com/sreichholf/dreamDroid/pull/200) | merged | typed `enigma.Signal` for `/web/signal`; poll via `GetSignalTask`; async cancel fixes. |
| device-info-compose | [#201](https://github.com/sreichholf/dreamDroid/pull/201) | merged | `DeviceInfoFragment` Compose UI + `DeviceInfoScreenTest`; keep last-good on failed refresh.
| signal-compose | [#202](https://github.com/sreichholf/dreamDroid/pull/202) | merged | `SignalFragment` Compose + HalfGauge `AndroidView` + `SignalScreenTest`.
| timers-typed | [#203](https://github.com/sreichholf/dreamDroid/pull/203) | merged | typed `enigma.Timer` for `/web/timerlist`; Compose list via typed model; edit/delete hash at edge. |
| ci-basic-tests | [#204](https://github.com/sreichholf/dreamDroid/pull/204) | merged | GitHub Actions: unit+assemble+androidTest compile on PRs; API 30 emulator on `main` / `workflow_dispatch` only. |
| timer-edit-compose | [#205](https://github.com/sreichholf/dreamDroid/pull/205) | merged | `TimerEditFragment` Compose form + `TimerEditScreenTest`; hash at save/pick edge; drop `timer_edit.xml`. |
| movie-detail-compose | [#206](https://github.com/sreichholf/dreamDroid/pull/206) | merged | `MovieDetailBottomSheet` Compose + typed `enigma.Movie` edge; drop phone ButterKnife on detail + `movie_epg_dialog` phone layout. |
| timer-service-pick-compose | [#207](https://github.com/sreichholf/dreamDroid/pull/207) | merged | `TimerServicePickFragment` Compose bouquet→service pick; drop unused `ServiceListFragment` + `dual_list_view`. |
| hub-nownext-typed | [#209](https://github.com/sreichholf/dreamDroid/pull/209) | merged | typed `ServiceNowNext` for `/web/epgnownext` into hub TV/Radio `ServiceListPageFragment`; hash only at detail/timer/stream edge. |
| movies-typed | [#210](https://github.com/sreichholf/dreamDroid/pull/210) | merged | typed `enigma.Movie` for `/web/movielist` into hub `MovieListFragment`; hash only at delete/stream edge; detail uses typed sheet. |
| leanback-dive | [#211](https://github.com/sreichholf/dreamDroid/pull/211) | merged | Phase 0 Leanback inventory + risks + agreed Phase 3 PR order (docs only; no TV code). |
| drawer-compose | [#212](https://github.com/sreichholf/dreamDroid/pull/212) | merged | Phase 2.1a: Compose drawer chrome (`DrawerScreen` in `ComposeView`); keep `DrawerLayout` + fragment host + `NavigationHelper.navigateTo`; no `NavHost` yet. |
| http-async-deviceinfo | [#213](https://github.com/sreichholf/dreamDroid/pull/213) | merged | Phase 2.2 beachhead: Device Info load via `lifecycleScope` + suspend `EnigmaClient.getDeviceInfo()`; drop `GetDeviceInfoTask` / `runBlocking` on that path. Keep `HttpURLConnection` / OkHttp 3. |
| http-async-signal | [#214](https://github.com/sreichholf/dreamDroid/pull/214) | merged | Phase 2.2b: Signal meter poll via `lifecycleScope` + suspend `EnigmaClient.getSignal()`; drop `GetSignalTask`; keep generation guards + dedicated client per fetch. |
| http-async-current | [#215](https://github.com/sreichholf/dreamDroid/pull/215) | merged | Phase 2.2c: Current Service load via `lifecycleScope` + suspend `EnigmaClient.getCurrent()`; drop `GetCurrentServiceTask`. |
| http-async-timers | [#216](https://github.com/sreichholf/dreamDroid/pull/216) | merged | Phase 2.2d: Timer list via `lifecycleScope` + suspend `EnigmaClient.getTimers()`; drop `GetTimerListTask`; keep generation guards. |
| http-async-movies | [#217](https://github.com/sreichholf/dreamDroid/pull/217) | merged | Phase 2.2e: Movies list via `lifecycleScope` + suspend `EnigmaClient.getMovies()`; drop `GetMovieListTask`; keep locs/tags prefetch + pending-when-not-resumed. |
| http-async-epgnownext | [#218](https://github.com/sreichholf/dreamDroid/pull/218) | merged | Phase 2.2f: Hub now/next via `lifecycleScope` + suspend `EnigmaClient.getEpgNowNext()`; drop `GetEpgNowNextTask`. |
| http-async-servicelist | [#219](https://github.com/sreichholf/dreamDroid/pull/219) | merged | Phase 2.2g: Zap + TimerServicePick service list via `lifecycleScope` + suspend `EnigmaClient.getServices()`; drop `GetServiceListTask`. Keep `GetBouquetListTask`. |
| http-async-eventlist | [#220](https://github.com/sreichholf/dreamDroid/pull/220) | merged | Phase 2.2h: Service/bouquet/search EPG via `lifecycleScope` + suspend `EnigmaClient.getEvents()`; drop `GetEventListTask`. |
| http-async-bouquetlist | [#222](https://github.com/sreichholf/dreamDroid/pull/222) | merged | Phase 2.2i: Hub/pick/timer bouquet roots via `lifecycleScope` + `EnigmaClient.getServices()`; drop `GetBouquetListTask`. |

Wave 1 of this plan is on `main`. It is **not** a finished modernization. See Appendix E / H.

Wave 2 (operator choice): (1) TV & Movies lists (#170), (4) dead-weight (#172/#175/#177), and (2) typed list paths (#173/#176/#179/#180/#181/#183/#203/#209/#210) are on `main`. Dead-weight deletes must not drop ButterKnife (still used by frozen Leanback TV).

Wave 3 (operator choice): convert remaining **non-Compose phone UIs** to Compose + Kotlin, **one PR per screen**. Checklist in Appendix G. **Wave 3 phone screens complete on `main` through #206** (plus CI #204). Drawer shell and Leanback TV are later programs (Appendix H).

### Operator overrides (this program)

- **Land** when the operator says land. That waives "owners do not merge" for that PR.
- **Proof** is `./gradlew.bat :app:connectedGoogleDebugAndroidTest`. Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That script is a single look, or a shell path that has no test yet.
- Do not pass `-Pandroid.testInstrumentationRunnerArguments...`. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- Theme follows `DreamDroid.getThemeType()`. Default `"1"` is always night. Do not reopen `colorSchemeFromViewTheme`. Version/license `Text` uses `onSurface`. Dialog-hosted Compose must be tested in a dialog/`ComposeView`, not only `setContent { }`.
- `captureToImage` on this AVD returns black pixels even for light content. Do not use pixel-luminance tests.
- Java calling Kotlin `(T) -> Unit` must `return kotlin.Unit.INSTANCE`. `DisposeOnViewTreeLifecycleDestroyed` is set from Kotlin, not Java.
- Two googleDebug processes cannot share one device. AGP 8.2 `jlink` fails on JDK 21.

### Not done, recorded so it is not pretended done

- Swarm live lanes, perf probes, and `media/pr-*-review.*` videos were **not** run. The operator accepted connectedAndroidTest and landed.
- Hub + rows are Compose on `main` (#169/#170). `ServiceAdapter` remains for video overlay; a hidden RecyclerView may remain for `BaseRecyclerFragment`.
- Leftover `app/res/service_list_pager.xml` stub and `android-retrostreams` were removed in #172. Inflater still uses `R.layout.service_list_pager`.
- Wave 3 phone Compose screens complete on `main` through #206; CI #204.
- Appendix G phone UI checklist: all 19 items merged.
- Remaining typed API (not Wave 3 UI): none on phone list paths (hub now/next #209; movies list #210; detail edge #206). Phase 0 Leanback dive #211 on `main`.
- Phase 2.1a drawer chrome **merged** [#212](https://github.com/sreichholf/dreamDroid/pull/212). Full Compose Navigation / `NavHost` still later (2.1b).
- Phase 2.2a Device Info coroutines **merged** [#213](https://github.com/sreichholf/dreamDroid/pull/213).
- Phase 2.2b Signal coroutines **merged** [#214](https://github.com/sreichholf/dreamDroid/pull/214).
- Phase 2.2c Current Service coroutines **merged** [#215](https://github.com/sreichholf/dreamDroid/pull/215).
- Phase 2.2d Timer list coroutines **merged** [#216](https://github.com/sreichholf/dreamDroid/pull/216).
- Phase 2.2e Movies list coroutines **merged** [#217](https://github.com/sreichholf/dreamDroid/pull/217).
- Phase 2.2f Hub now/next coroutines **merged** [#218](https://github.com/sreichholf/dreamDroid/pull/218).
- Phase 2.2g Zap + TimerServicePick service-list coroutines **merged** [#219](https://github.com/sreichholf/dreamDroid/pull/219).
- Phase 2.2h Service/bouquet/search EPG event-list coroutines **merged** [#220](https://github.com/sreichholf/dreamDroid/pull/220).
- Phase 2.2i Hub/pick/timer bouquet-list coroutines **merged** [#222](https://github.com/sreichholf/dreamDroid/pull/222).
- Out of wave still: Leanback `tv/` (Phase 3), VLC, widgets. See Appendix H.

## How to read this

One box is one unit of work. Check a box only when its evidence exists: a SHA, a PR URL, a test run, a file. Nested boxes are sub-steps.

The original playbook wanted ten live `verify-dreamdroid.py` lanes plus a perf ratio per PR. That protocol is superseded by the overrides above. Live/perf boxes below stay unchecked unless a later run actually produces those files.

## Program checklist

### Arm the program

- [x] Protocol and plan stated. Operator go given. Later holds (reboot) and "land" orders override the playbook in chat.
- [ ] Original `/goal` text still says `master` and `verify-dreamdroid`. Do not re-arm that string. Wave 1 done is #169 on `main` plus hub Compose tests green.
- [x] Forge resolved to `gh` as `sreichholf`. Origin/`gt` not used.

### Spawn owners

- [x] Work ran serially in one worktree, not one cloud owner per PR.
- [x] Order held: floor, then About and client in parallel in time, Profiles after About, services after Profiles + client.
- [x] No PR edited `app/src/net/reichholf/dreamdroid/tv/` except compile-safe leftovers.
- [x] Review gate waived per operator "land" for #164–#169.

### PR mechanics, for every PR

- [x] PRs opened ready, never draft, `--base main`.
- [x] JDK 17 assemble / connected tests before push where UI changed.
- [x] Cursor git wrapper injects `--trailer`; Git 2.23 rejects it. Commit via Python calling `git.exe` with `-F`.

## Raise the device floor (pr-floor)

**Depends on.** None. **On `main`.**

**Files.**

- [x] [`app/build.gradle`](../app/build.gradle) `minSdkVersion` 26, Java 17, no `jcenter()`, no unused Navigation.
- [x] [`gradle.properties`](../gradle.properties).
- [x] Wrapper left unless AGP required it.
- [x] OkHttp ended at **3.14.9** (`e7b43a7c`) after 4.12.0 failed Kotlin 1.6.21. Still Picasso-only. Enigma2 stays `HttpURLConnection`.

**Build.**

- [x] `./gradlew.bat :app:assembleGoogleDebug` `BUILD SUCCESSFUL`. Manifest minSdk 26.

**You see.**

- [x] Debug package still `net.reichholf.dreamdroid.debug`.

**Verify, unit.**

- [x] `app/src/test/java/net/reichholf/dreamdroid/MinSdkSmokeTest.java`.

**Verify, live / perf / swarm.** Not run. Operator landed the floor as commits; #163 was closed without merge.

## Show About in Compose (pr-about)

**Depends on.** pr-floor. **On `main` as #164, #166, #167.**

**Files.**

- [x] Compose BOM, `compose` buildFeatures, Kotlin plugin kept (later 1.9.24).
- [x] `app/src/net/reichholf/dreamdroid/ui/about/AboutScreen.kt`.
- [x] `NavigationHelper` opens Compose About.
- [x] `AboutDialog.java` deleted.
- [x] Unused `app/res/layout/navigation_layout.xml` gone.
- [x] Night: `DreamDroidTheme` provides `LocalContentColor` from scheme `onSurface` (#166).
- [x] `AGENTS.md` plus `AboutScreenTest` / `AboutDialogHostTest` (#167). Test APK strings `app_name_debug` / `app_name_tv_debug` so AAPT links.

**Build.**

- [x] About shows `DreamDroid.VERSION_STRING`, GPLv3, source URL.

**You see.**

- [x] Drawer About is a Compose dialog titled About.

**Verify, unit.**

- [x] `AboutScreenTest` version + licenses. `AboutDialogHostTest` night `LocalContentColor` inside an `AlertDialog` host. `connectedGoogleDebugAndroidTest`.

**Verify, live / perf / review media.** Not run. Operator landed on tests.

## Rebuild Profiles in Compose (pr-profiles)

**Depends on.** pr-about. **On `main` as #168 (`e7563787`).**

**Files.**

- [x] `app/src/net/reichholf/dreamdroid/ui/profiles/ProfilesScreen.kt`, `ProfileListItem.kt`, `ProfilesListState.kt`.
- [x] `ProfileListFragment` hosts `ComposeView`. XML `fab_main` hidden. Compose FAB uses `R.string.profile_add`.
- [x] `MainActivity.onProfileChecked`: after `navigateTo(Profiles)`, skip `navigateTo(services)` when `isFirstStart` even if `mDetailFragment` is still null after `commit()`.
- [x] Room `AppDatabase.profiles()` kept.
- [x] `ProfileAdapter` deleted with `share-profiles-compose` (was Share-only).

**Build.**

- [x] Seeded `Demo` row and Add Profile FAB. Autodiscovery, edit, delete kept.

**You see.**

- [x] Profiles list is Compose.

**Verify, unit.**

- [x] `ProfilesScreenTest` Demo row + Add Profile content description. `connectedGoogleDebugAndroidTest`.

**Verify, live / perf / review media.** Not run. Operator landed #168.

## Type the Enigma2 client (pr-client)

**Depends on.** pr-floor. **On `main` as #165 (`de010cb4`).**

**Files.**

- [x] `app/src/net/reichholf/dreamdroid/enigma/` Kotlin types and coroutine client wrapping `SimpleHttpClient` + `URIStore`.
- [x] Bouquet fetch for the hub goes through `EnigmaClient.getServicesBlocking`.
- [x] Zap list fetch goes through `EnigmaClient.getServicesBlocking` (this PR). `ExtendedHashMap` remains on unmigrated list rows (EPG, current event, `ServiceListPageFragment` hash maps into Compose items).

**Build.**

- [x] Models for Service, Event, Timer, Movie. Parse at the HTTP boundary. Not OkHttp.

**You see.**

- [x] JVM test parses fixture `/web/getservices` XML into `Service`.

**Verify, unit.**

- [x] `ServiceParserTest`. `./gradlew.bat :app:testGoogleDebugUnitTest`.

**Verify, live / perf / review media.** Not run. Operator landed #165.

## Rebuild TV and Movies hub (pr-services)

**Depends on.** pr-profiles, pr-client. **On `main` as [#169](https://github.com/sreichholf/dreamDroid/pull/169) (`f125c117`).**

**Files.**

- [x] `app/src/net/reichholf/dreamdroid/ui/services/TvMoviesScreen.kt`, `TvMoviesDestination.kt`, `TvMoviesHubState.kt`.
- [x] `ServiceListPager.java` hosts Compose header + destination bar. XML `TabLayout` / activity bottom nav gone (GONE leftover removed in dead-weight EPG/prefs PR).
- [x] Phone Recycler adapters on the pager path deleted on `main` via [#170](https://github.com/sreichholf/dreamDroid/pull/170). Adapters used outside the pager path remain.

**Build.**

- [x] Destinations TV, Radio, Movies, Timer. Bouquet/location names in Compose tabs. Lists still pager fragments on `main`. Errors from typed bouquet fetch in hub state.

**You see.**

- [x] Instrumented: fake bouquets `Favourites (TV)` and `All Services` plus four destinations.

**Verify, unit.**

- [x] `TvMoviesScreenTest`. `:app:connectedGoogleDebugAndroidTest` before push.

**Merge.**

- [x] Squash-merged. `origin/main` is `f125c117` after #169.

## Close wave 1

- [x] #169 on `main`.
- [x] Operator chose wave 2: (1) finish TV & Movies lists, then (4) dead weight. See Appendix E.
- [x] #170 and #172 on `main`. Next: shape (2) Zap typing (this PR).

## Type Zap list rows (pr-zap)

**Depends on.** pr-client. **[#173](https://github.com/sreichholf/dreamDroid/pull/173), `--base main`.**

**Files.**

- [x] `ZapFragment` / `ZapAdapter` hold `enigma.Service`. XML `zap_grid_item` stays.
- [x] Fetch via `GetServiceListTask` → `HttpFragmentHelper.fetchServices` → `EnigmaClient`.
- [x] `ZapListMapper` drops markers; `toBouquetMap` / `bouquetFrom` convert picker Intent `ExtendedHashMap` at the fragment boundary.
- [x] `Picon` accepts reference + name so Zap does not rebuild hashes for picons.
- [ ] Drawer shell, EPG, current event, Compose Navigation **not** in this PR.

**Verify, unit.**

- [x] `ZapListMapperTest` plus `ServiceParserTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug. Connected tests when a device exists.

## Type Service EPG list rows (pr-epg-event)

**Depends on.** pr-client, #173. **On `main` as [#176](https://github.com/sreichholf/dreamDroid/pull/176).**

**Files.**

- [x] `EventParser` parses `/web/epgservice` into `enigma.Event` (readable fields included).
- [x] `EnigmaClient.getEvents` / `getEventsBlocking` (+ URI param for later bouquet/search).
- [x] `GetEventListTask` → `HttpFragmentHelper.fetchEvents` → `EnigmaClient`.
- [x] `ServiceEpgListFragment` holds `List<Event>` (Compose list via `service-epg-compose`; was `ServiceEpgAdapter` / `epg_list_item`).
- [x] `EpgListMapper.toExtendedHashMap` at detail/timer edge only. Do not rewrite `EpgDetailBottomSheet`.
- [ ] `EpgBouquetFragment` / `EpgSearchFragment`, drawer shell, Compose Navigation **not** in this PR. Dead `EpgTimelineFragment` removed separately in dead-weight. Bouquet typing landed as #179; search typing is the next unit below.

**Verify, unit.**

- [x] `EventParserTest` plus `EpgListMapperTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug.

## Type EPG bouquet list rows (pr-epg-bouquet)

**Depends on.** #176. **On `main` as [#179](https://github.com/sreichholf/dreamDroid/pull/179).**

**Files.**

- [x] `HttpFragmentHelper.fetchEvents` / `GetEventListTask` accept optional URI (default `URIStore.EPG_SERVICE`; bouquet uses `URIStore.EPG_BOUQUET`).
- [x] `EpgBouquetFragment` / `EpgBouquetAdapter` hold `List<Event>`. XML `epg_multi_service_list_item` stays.
- [x] `EpgListMapper.toExtendedHashMap` at detail/timer edge only. Do not rewrite `EpgDetailBottomSheet`.
- [x] Bouquet picker list typing is a separate unit (`cursor/pick-service-typed-c88a`); Intent still maps one `ExtendedHashMap` at send for Zap/EpgBouquet.
- [ ] Drawer shell, Compose Navigation **not** in this PR. Search typing landed as #180; picker typing is the next unit below.

**Verify, unit.**

- [x] Existing `EventParserTest` / `EpgListMapperTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug.

## Type EPG search list rows (pr-epg-search)

**Depends on.** #179. **On `main` as [#180](https://github.com/sreichholf/dreamDroid/pull/180).**

**Files.**

- [x] `EpgSearchFragment` uses `GetEventListTask` with `URIStore.EPG_SEARCH`.
- [x] Rows are typed `List<Event>`; reuses `EpgBouquetAdapter` (same `epg_multi_service_list_item`).
- [x] `EpgListMapper.toExtendedHashMap` at detail/timer edge only. Do not rewrite `EpgDetailBottomSheet`.
- [x] Delete unused hash `EpgAdapter`.
- [ ] Drawer shell, Compose Navigation **not** in this PR.

**Verify, unit.**

- [x] Existing `EventParserTest` / `EpgListMapperTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug.

## Type PickService list (pr-pick-service)

**Depends on.** #173, #180. **Open on `cursor/pick-service-typed-c88a`.**

**Files.**

- [x] `PickServiceFragment` holds `List<Service>` (Compose list via `pick-service-compose`; was `ServiceNameAdapter`).
- [x] Load fav TV+Radio roots via `GetBouquetListTask` → `HttpFragmentHelper.fetchServices` → `EnigmaClient`.
- [x] Intent `KEY_BOUQUET` still one `ExtendedHashMap` mapped at send (`ZapListMapper.toBouquetMap`). Zap / EpgBouquet consumers unchanged.
- [x] Delete unused `AsyncFavListLoader`.
- [ ] Drawer shell, Compose Navigation **not** in this PR.

**Verify, unit.**

- [x] `ZapListMapperTest` (`toBouquetMap` / `bouquetFrom`). `:app:testGoogleDebugUnitTest`. Assemble googleDebug.

## Appendix A. Prototype evidence

Compose versus XML Views was not prototyped in-repo. Plan mode blocked a throwaway sketch. The choice is Compose because minSdk 26 allows the current BOM, and Material 3 in [`app/res/values/themes.xml`](../app/res/values/themes.xml) already parents `Theme.Material3`.

minSdk 17 versus 26 was a product call. The operator picked 26.

TV in the first wave was a product call. The operator picked phone only.

## Appendix B. Alternatives rejected

A greenfield second app was rejected. The Enigma2 XML API and Room profiles are the product.

Keeping minSdk 17 was rejected. Shipping phone and TV together was rejected. Leanback stays frozen.

Keeping `ExtendedHashMap` as the list row type was rejected for new screens. Unmigrated fragments still use it.

## Appendix C. Risks and traps (current)

Play users below API 26 lose install. Floor is on `main`.

No Enigma2 box in CI. Connection error is a valid pass for list content. Crash is not.

ButterKnife remains on a few files. Do not drop it as a drive-by. Leanback TV still uses it.

Two HTTP stacks. Box XML is `HttpURLConnection`. Picons are Picasso plus OkHttp 3.14.9.

Two database files. Room `dreambox` holds `profile`. Legacy SQLite `dreamdroid` still feeds first-run migration and backup. Do not delete the old file until backup points at Room.

First-start skip-Profiles race is fixed on `main` in #168. Still wait for `Demo` after Changelog, not the word Profiles inside changelog text.

Evernote `@State` plus Livefront Bridge is load-bearing on rotation. Do not migrate it in this program.

VLC `VideoActivity` is out of this program.

Kotlin plugin is 1.9.24 with Compose compiler 1.5.14. OkHttp stays 3.14.9. Do not bump OkHttp to 4 as a drive-by.

Test APK must define `app_name_debug` / `app_name_tv_debug` or AAPT fails.

Do not merge `cursor/verify-dreamdroid-skill` (or any other `master`-based branch) into `main`. Port files.

## Appendix E. What wave 1 did not cover

Wave 1 was a beachhead: minSdk 26, Compose BOM, About, Profiles list, a typed client used by bouquet fetch, and Compose hub chrome. Counts on `main` after #169: **14 Kotlin files, 219 Java files** under `app/src` (12 of those Java files are Leanback TV).

This was never "replace the phone app with Compose." The original boxes named five PRs. Everything else stayed on purpose or by omission.

### Phone drawer / related surfaces (Wave 3 Compose on `main`)

Compose on `main`: About dialog, Profiles list + edit form (#184), TV & Movies **tabs/bar**, Zap grid (#185), Virtual remote (#186), Screenshot (#187), Settings (#188), Backup (#189), Current event (#190), PickService (#191), EPG bouquet (#192), EPG search (#193), Service EPG (#194), timer edit (#205), timer service pick (#207), movie detail (#206), EPG detail (#197), drawer dialogs (#198), device info (#201), signal (#202), Share profiles (#196). The pager pages behind those tabs landed as Compose in #170.

| Surface | Code | Notes |
| --- | --- | --- |
| Channel / bouquet rows | Compose on `main` via #170; typed now/next #209 | Long-press, picons, popup menu; `ServiceNowNext` load path. |
| Movies list | Compose on `main` via #170; typed list #210 | Hub Movies destination; `enigma.Movie` load path. |
| Timer list / edit | Compose list on `main` via #170/#203; edit #205; service pick #207 | List typed+Compose; edit Compose form (hash save/pick). |
| Profile add/edit | Compose on `main` via #184 | Form Compose; list was #168. Autodiscovery stays Java. |
| Share / pick profile | Compose on `main` via #196 | Compose list; Room profiles; dropped `ProfileAdapter`. |
| Zap | Compose on `main` via #185 | Compose grid + Picasso picons. Rows typed `enigma.Service` (#173). Picker list typing on `main` via #181. |
| Service EPG list | Compose on `main` via #194 | Compose list; typed `enigma.Event` (#176). Detail/timer edge still hash. |
| EPG bouquet | Compose on `main` via #192 | Compose list; typed `enigma.Event` (#179). Shared `EpgBouquetScreen` with search. Detail/timer edge still hash. |
| EPG search | Compose on `main` via #193 | Compose list; typed `enigma.Event` (#180). Dropped `EpgBouquetAdapter`. Detail/timer edge still hash. |
| Bouquet picker | Compose on `main` via #191 | Compose list; typed `enigma.Service` (#181). Intent still one hash at send. |
| EPG detail | Compose on `main` via #197 | Compose sheet; typed `Event` + hash/`showNext` overload for hub/video. |
| Current event | Compose on `main` via #190 | Compose now/next + stream; typed `enigma.CurrentService` (#183). Detail/timer edge still hash. |
| Virtual remote | Compose on `main` via #186 | Compose pad + HTTP keys; tablet screenshot host kept. |
| Device info | Compose on `main` via #201 | Typed #199 + Compose UI. |
| Signal | Compose on `main` via #202 | Typed #200 + HalfGauge `AndroidView`. |
| Screenshot | Compose on `main` via #187 | Compose + PhotoView AndroidView; reload/share/save kept. |
| Settings | Compose on `main` via #188 | Compose preference list; same PreferenceManager keys as `R.xml.preferences`. TV Leanback prefs unchanged. |
| Backup | Compose on `main` via #189 | Compose import/export + toggles; legacy SQLite + Room via `BackupService`. |
| Sleep timer / send message / power / changelog | Compose on `main` via #198 | Drawer dialogs Compose. |
| Movie detail | Compose on `main` via #206 | Compose sheet; typed `enigma.Movie` + hash overload; dropped phone ButterKnife on detail. |
| Mediaplayer | removed (#175) | Drawer entry was commented; UI stack deleted. `URIStore.MEDIA_PLAYER_PLAY` kept for `ShareActivity`. |
| Streaming | `VideoActivity`, `VideoOverlayFragment`, VLC | Explicitly out of wave 1. |
| Widget | `appwidget/` | |
| Shell | `MainActivity`, `NavigationHelper`, drawer XML, `BaseFragment` tree, Evernote `@State` + Bridge | |

### Still the old data stack

- Typed `EnigmaClient` exists. Zap list rows load typed `Service`. Service EPG list rows load typed `Event` (#176). EPG bouquet rows load typed `Event` (#179). EPG search rows load typed `Event` (#180). PickService list typing is on `main` via #181. Hub TV/Radio now/next loads typed `ServiceNowNext` (#209). Movies list loads typed `enigma.Movie` (#210). Leanback movie browse still uses hash SAX.
- Enigma2 HTTP is still `HttpURLConnection` + `asynctask/*`. Picons still Picasso + OkHttp 3.14.9.
- Room holds `profile` only. `DatabaseHelper` / `dreamdroid` SQLite still exist for migration and backup.
- ButterKnife (4 files: phone `VideoOverlayFragment` + 3 Leanback TV). `legacy-support-v4` and `legacy-preference-v14` removed. `multiDexEnabled` stays; the `androidx.multidex` install helper is gone (minSdk 26).

### Explicitly frozen

- `app/src/.../tv/` Leanback. Operator picked phone first.
- Rotation via Evernote State + Livefront Bridge.
- VLC playback.

### Sensible wave-2 shapes (pick one, do not do all at once)

1. **Finish TV & Movies** — Compose channel/movie/timer rows, drop `ServiceAdapter` on the pager path, feed typed `Service`/`Movie`/`Timer`. Highest continuity with #169. **Done on `main` as #170.**
2. **Retire `ExtendedHashMap` on one more list path at a time** — EPG, zap, current event. UI can stay XML until the parser boundary is typed. Stops the dual model from rotting. **Done on `main`:** Zap #173, Service EPG #176, bouquet EPG #179, search EPG #180, PickService #181, CurrentService #183, timers #203, device info #199, signal #200, hub now/next #209, movies list #210.
3. **Replace the drawer shell** — `NavigationHelper` + `MainActivity` in Compose Navigation. Touches every screen. Do this only after a few more destinations are Compose, or it wraps XML forever.
4. **Kill dead weight without UI rewrite** — ButterKnife (not while TV is frozen). `android-retrostreams` and leftover `res/service_list_pager.xml` dropped in **#172**. MediaPlayer UI + MultiDex lib + orphan layouts in **#175** (merged). #177: dead `EpgTimelineFragment`, `legacy-preference-v14`, `legacy-support-v4`, unused menus, GONE bottom nav. ButterKnife still open while TV is frozen.
5. **TV program** — Leanback → Compose for TV. Separate program. Do not mix into phone PRs.

Recommended default: finish remaining typed API paths (shape 2), then wave 3 Compose screens (Appendix G). Keep drawer shell (3) and TV (5) as later programs.

## Appendix G. Wave 3 — remaining non-Compose phone UIs

One PR per screen. Pattern: Compose + Kotlin Material 3 like About (#164) / Profiles (#168). Prefer instrumented Compose tests. Do **not** start the drawer shell here. Do **not** mix Leanback TV.

**Order:** finish remaining typed API first where noted; screens marked “no API wait” may run as soon as the typed-API queue is clear (or in parallel only if they do not fight the same files).

| # | PR slug | Class | Opened how | Wait for typed API? |
| --- | --- | --- | --- | --- |
| 1 | `profile-edit-compose` | `ProfileEditFragment` | Profiles FAB/row → `SimpleToolbarFragmentActivity` | No (Room) — **merged** [#184](https://github.com/sreichholf/dreamDroid/pull/184) |
| 2 | `current-event-compose` | `CurrentServiceFragment` | Drawer current | Typed #183 — **merged** [#190](https://github.com/sreichholf/dreamDroid/pull/190) |
| 3 | `zap-compose` | `ZapFragment` | Drawer zap | No (rows already typed #173) — **merged** [#185](https://github.com/sreichholf/dreamDroid/pull/185) |
| 4 | `virtual-remote-compose` | `VirtualRemotePagerFragment` / `VirtualRemoteFragment` | Drawer remote | No — **merged** [#186](https://github.com/sreichholf/dreamDroid/pull/186) |
| 5 | `device-info-compose` | `DeviceInfoFragment` | Drawer device info | Prefer type device-info first — **merged** [#201](https://github.com/sreichholf/dreamDroid/pull/201) |
| 6 | `signal-compose` | `SignalFragment` | Drawer signal | Prefer type signal first — **merged** [#202](https://github.com/sreichholf/dreamDroid/pull/202) |
| 7 | `screenshot-compose` | `ScreenShotFragment` | Drawer screenshot | No — **merged** [#187](https://github.com/sreichholf/dreamDroid/pull/187) |
| 8 | `settings-compose` | `MyPreferenceFragment` | Drawer settings | No — **merged** [#188](https://github.com/sreichholf/dreamDroid/pull/188) |
| 9 | `backup-compose` | `BackupFragment` | Drawer backup | No — **merged** [#189](https://github.com/sreichholf/dreamDroid/pull/189) |
| 10 | `timer-edit-compose` | `TimerEditFragment` | Hub / EPG → timer edit | Yes — type timers list/edit path — **merged** [#205](https://github.com/sreichholf/dreamDroid/pull/205) |
| 11 | `movie-detail-compose` | `MovieDetailBottomSheet` | Movies row tap | Yes — typed `enigma.Movie` edge — **merged** [#206](https://github.com/sreichholf/dreamDroid/pull/206) |
| 12 | `epg-detail-compose` | `EpgDetailBottomSheet` | EPG / current / channel popup | Yes — accept typed `enigma.Event` — **merged** [#197](https://github.com/sreichholf/dreamDroid/pull/197) |
| 13 | `drawer-dialogs-compose` | Sleep timer / send message / power / changelog / connection error | Drawer dialogs | Partial (sleep-timer result optional) — **merged** [#198](https://github.com/sreichholf/dreamDroid/pull/198) |
| 14 | `epg-bouquet-compose` | `EpgBouquetFragment` | Drawer EPG | No for list (rows typed #179); detail with #12 — **merged** [#192](https://github.com/sreichholf/dreamDroid/pull/192) |
| 15 | `epg-search-compose` | `EpgSearchFragment` | Toolbar search | No for list (rows typed #180) — **merged** [#193](https://github.com/sreichholf/dreamDroid/pull/193) |
| 16 | `service-epg-compose` | `ServiceEpgListFragment` | Channel → EPG | No for list (rows typed #176) — **merged** [#194](https://github.com/sreichholf/dreamDroid/pull/194) |
| 17 | `pick-service-compose` | `PickServiceFragment` | Zap / EPG bouquet pick | No for list (rows typed #181) — **merged** [#191](https://github.com/sreichholf/dreamDroid/pull/191) |
| 18 | `timer-service-pick-compose` | `TimerServicePickFragment` (was `ServiceListFragment` pick) | Timer edit service pick | Typed bouquet+services — **merged** [#207](https://github.com/sreichholf/dreamDroid/pull/207) |
| 19 | `share-profiles-compose` | `ShareActivity` + `ProfileAdapter` | Share intent | No (Room) — **merged** [#196](https://github.com/sreichholf/dreamDroid/pull/196) |

**Out of this wave:** drawer shell (`MainActivity` / `NavigationHelper`), Leanback `tv/`, VLC `VideoActivity` / `VideoOverlayFragment`, home-screen widgets.

## Appendix H. After Wave 3 — one-by-one modernization plan

Operator intent: **migrate everything** (phone leftovers + Leanback), then operator usertests, then bugfix pass. Do **one PR at a time**. Do **not** start Leanback implementation until the dive below is written and accepted.

### Phase 0 — Leanback dive (accepted)

Inventory of `app/src/.../tv/` (12 Java files, ~1.3k LOC). **No TV Compose code in this PR.** Phase 3 may start after this dive is on `main`.

#### Surfaces

| Surface | Path | Role |
| --- | --- | --- |
| MainActivity | `tv/activities/MainActivity.java` | TV host (`tv_main`); custom TLS + Picasso OkHttp singleton |
| PreferenceActivity | `tv/activities/PreferenceActivity.java` | Host for Leanback prefs |
| RootBrowseFragment | `tv/fragment/RootBrowseFragment.java` | Hub: bouquet/service/movie rows, settings row, stream/prefs |
| BaseHttpBrowseFragment | `tv/fragment/abs/BaseHttpBrowseFragment.java` | Leanback browse + loader callbacks over `ExtendedHashMap` |
| SettingsFragment / PrefsFragment / ProfileFragment | `tv/fragment/` | Leanback settings router; `PrefsFragment` loads `R.xml.preferences`; `ProfileFragment` loads `R.xml.profile_preferences` |
| EpgDetailDialog / MovieDetailDialog | `tv/fragment/` | Fullscreen XML detail dialogs (`AbstractDialog` + ButterKnife) |
| CardPresenter / TextCardView / BrowseItem | `tv/presenter/`, `tv/view/`, `tv/BrowseItem.java` | Card presenters + hash payload wrapper |

Entry: `TabbedNavigationActivity` → TV `MainActivity` when `DreamDroid.isTV()`. Detail dialogs under `tv/` are opened from phone `VideoOverlayFragment` (not from `RootBrowseFragment` clicks).

#### ButterKnife (TV)

| File | `@BindView` count |
| --- | --- |
| `MovieDetailDialog` | 8 |
| `EpgDetailDialog` | 5 |
| `TextCardView` | 2 |
| **Total** | **15** (3 files) |

Phone leftover: `VideoOverlayFragment` (Phase 2 VLC). ButterKnife cannot be dropped until TV + that site are gone.

#### Coupling to phone stack

- `ExtendedHashMap` / string-key `Event`/`Movie`/`Service` helpers in browse + cards + dialogs
- `AsyncListLoader` + SAX handlers (`ServiceListRequestHandler`, `EpgNowNextListRequestHandler` / `EventListRequestHandler`, `MovieListRequestHandler`)
- `Picon`, `IntentFactory` → `VideoActivity` / integrated player
- `AbstractDialog.setTextOrHide` in TV detail dialogs
- `DreamDroidTrustManager` + Picasso OkHttp in TV `MainActivity` (app-wide side effects)
- Shared prefs XML (`R.xml.preferences` / `R.xml.profile_preferences`); EPG detail may share phone dialog XML, while `MovieDetailDialog` uses TV-only `layout-television/movie_epg_dialog` (phone layout removed in #206)

Phone Compose detail screens do **not** cover TV dialogs.

#### Risks

- Leanback D-pad browse ≠ phone Material 3; hub needs a TV-first focus model
- Hash-map data plane in every `BrowseItem` — Compose without typing rebinds string keys
- Loader/SAX stack will be replaced in Phase 2 HTTP; hub Compose before typing/HTTP direction risks a double rewrite
- Streaming UX tied to Phase 2 VLC/`VideoOverlayFragment` decisions
- Detail dialogs mis-located under `tv/` but driven by phone overlay
- No in-tree TV instrumented coverage for browse/prefs

#### Agreed Phase 3 PR order

Prefer **typed browse data → details → hub → prefs** (not prefs-first; not hub-first without typing):

1. Typed TV browse data — stop `ExtendedHashMap` in `BrowseItem` / loaders; reuse phone typed client
2. Detail dialogs → Compose (or shared phone detail + TV theme) — drops 13/15 TV binds
3. Browse hub → TV Compose / foundational focus — needs typed data; kills `TextCardView` ButterKnife
4. Leanback prefs → Compose preferences — isolated; same PreferenceManager keys
5. Drop ButterKnife when zero call sites remain (TV + phone VLC)

**Safe before full Phase 2:** typed TV browse data and detail Compose (careful with VLC hosts). **Defer hub Compose** until typed data lands and Phase 2 HTTP direction is at least sketched.

### Phase 1 — Remaining phone typed API (done)

Appendix G phone Compose screens are on `main` through #206 (+ CI #204). Phone ButterKnife left only on `VideoOverlayFragment` (VLC / Phase 2). Phase 1 typed leftovers:

| Order | Slug | Notes |
| --- | --- | --- |
| 1 | typed hub now/next | **merged** [#209](https://github.com/sreichholf/dreamDroid/pull/209) |
| 2 | typed movies list path | **merged** [#210](https://github.com/sreichholf/dreamDroid/pull/210) |

Next: Phase 2.2 HTTP/async (Device Info coroutine beachhead in this PR), then more Get*Task retirements, then state/rotation, Room, VLC (product decision), widgets. Phase 3 Leanback only after Phase 0 dive (done #211) and preferably after HTTP direction is clear for the hub.

### Phase 2 — Phone chassis (still not Leanback)

One program each, still one PR (or small PR series) at a time:

| Order | Program | What |
| --- | --- | --- |
| 1a | Drawer chrome (Compose) | Replace `NavigationView` menu with Compose `DrawerScreen` in `ComposeView`; keep XML profile header, `DrawerLayout`, fragment `detail_view`, and `NavigationHelper.navigateTo`. `res/menu/navigation.xml` kept for destination ids. **merged** [#212](https://github.com/sreichholf/dreamDroid/pull/212). |
| 1b | Drawer Navigation (later) | `MainActivity` + destinations → Compose Navigation / `NavHost`; retire dual-pane XML host gradually. Do **not** start until chrome is stable. |
| 2a | HTTP / async — Device Info | `DeviceInfoFragment` → `lifecycleScope` + suspend `EnigmaClient.getDeviceInfo()`; delete `GetDeviceInfoTask`. **merged** [#213](https://github.com/sreichholf/dreamDroid/pull/213). Keep `SimpleHttpClient`/`HttpURLConnection`. |
| 2b | HTTP / async — Signal | `SignalFragment` poll → `lifecycleScope` + suspend `EnigmaClient.getSignal()`; delete `GetSignalTask`; keep generation guards. **merged** [#214](https://github.com/sreichholf/dreamDroid/pull/214). |
| 2c | HTTP / async — Current Service | `CurrentServiceFragment` → `lifecycleScope` + suspend `EnigmaClient.getCurrent()`; delete `GetCurrentServiceTask`. **merged** [#215](https://github.com/sreichholf/dreamDroid/pull/215). |
| 2d | HTTP / async — Timers | `TimerListFragment` → `lifecycleScope` + suspend `EnigmaClient.getTimers()`; delete `GetTimerListTask`; keep generation guards. **merged** [#216](https://github.com/sreichholf/dreamDroid/pull/216). |
| 2e | HTTP / async — Movies | `MovieListFragment` → `lifecycleScope` + suspend `EnigmaClient.getMovies()`; delete `GetMovieListTask`. **merged** [#217](https://github.com/sreichholf/dreamDroid/pull/217). |
| 2f | HTTP / async — Hub now/next | `ServiceListPageFragment` → `lifecycleScope` + suspend `EnigmaClient.getEpgNowNext()`; delete `GetEpgNowNextTask`. **merged** [#218](https://github.com/sreichholf/dreamDroid/pull/218). |
| 2g | HTTP / async — Service list | `ZapFragment` + `TimerServicePickFragment` → `lifecycleScope` + suspend `EnigmaClient.getServices()`; delete `GetServiceListTask`. Keep `GetBouquetListTask` for a later slice. **merged** [#219](https://github.com/sreichholf/dreamDroid/pull/219). |
| 2h | HTTP / async — Event list | `ServiceEpgListFragment` + `EpgBouquetFragment` + `EpgSearchFragment` → `lifecycleScope` + suspend `EnigmaClient.getEvents(uri)`; delete `GetEventListTask`. **merged** [#220](https://github.com/sreichholf/dreamDroid/pull/220). |
| 2i | HTTP / async — Bouquet list | `ServiceListPager` + `PickServiceFragment` + `TimerServicePickFragment` → `lifecycleScope` + `EnigmaClient.getServices()` for TV/Radio roots; delete `GetBouquetListTask`. **merged** [#222](https://github.com/sreichholf/dreamDroid/pull/222). |
| 2 | HTTP / async stack (program) | Replace `HttpURLConnection` + executor/`Loader` with Kotlin coroutines + typed `EnigmaClient` everywhere; keep OkHttp 3.14.9 for Picasso until a dedicated bump. Follow-ons after 2i: locations/tags, mutations/`SimpleResultTask`, Loader chassis. |
| 3 | State / rotation | Replace Evernote `@State` + Livefront Bridge (frozen today) with SavedStateHandle / rememberSaveable |
| 4 | Data | Finish Room migration; shrink `DatabaseHelper` to backup-only then remove |
| 5 | VLC / streaming | `VideoActivity` + `VideoOverlayFragment` (ButterKnife) — product decision before rewrite |
| 6 | Widgets | `appwidget/` Virtual Remote — Compose Glance or keep XML |

### Phase 3 — Leanback TV (after Phase 0 dive)

Separate PRs; do not mix with phone shell PRs. Order fixed by Phase 0 dive:

1. Typed data path for TV browse (stop `ExtendedHashMap` in `BrowseItem`).
2. TV detail dialogs → Compose (or shared phone detail with TV theme).
3. Browse hub → TV Compose / foundational focus model.
4. Leanback prefs → Compose preferences.
5. Drop ButterKnife when zero call sites remain.

### Phase 4 — Operator usertests

Phone + TV smoke on real devices / boxes. File bugs; no drive-by refactors.

### Phase 5 — Bugfix pass

One PR per fix or small related cluster. Prefer regressions covered by Compose tests.

### Explicit non-goals until Phases 0–3 clear

- Do not merge `master` into `main`.
- Do not bump OkHttp to 4 as a drive-by.
- Do not rewrite VLC codecs or Enigma2 server side.

## Appendix F. Links

[`AGENTS.md`](../AGENTS.md), [`.cursor/skills/verify-dreamdroid/SKILL.md`](../.cursor/skills/verify-dreamdroid/SKILL.md), [`app/build.gradle`](../app/build.gradle), [`NavigationHelper.java`](../app/src/net/reichholf/dreamdroid/fragment/helper/NavigationHelper.java), [`MainActivity.java`](../app/src/net/reichholf/dreamdroid/activities/MainActivity.java), [`URIStore.java`](../app/src/net/reichholf/dreamdroid/helpers/enigma2/URIStore.java), [`themes.xml`](../app/res/values/themes.xml).
