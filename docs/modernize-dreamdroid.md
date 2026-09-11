# Modernize dreamDroid phone app

Phone users get a Compose Material 3 remote. TV Phase 3 hybrid (Leanback shell + Compose cards/details/prefs) is on `main`; **full Compose TV hub (option C / Phase 3.1c-iv) is next after phone Phase 2.7i**. minSdk is 26. Dead deps went first, then the typed Enigma2 client, then phone screens.

Rewrite trunk is **`main`**. `master` is last 1.15 stable. Do not merge `master` or branches cut from `master` into `main`.

Default UI proof is instrumented Compose tests, not `verify-dreamdroid.py` tap loops. See [`AGENTS.md`](../AGENTS.md). AVD `dreamdroid-verify`. JDK 25. Debug package `net.reichholf.dreamdroid.debug`.

**Language:** new types are **Kotlin** (not Java). Prefer coroutines. Existing Java may stay until edited; heavy edits / extracted helpers go Kotlin. See [`AGENTS.md`](../AGENTS.md).

**Modernize to state of the art:** earlier “keep for now” calls (DialogFragments, XML `RemoteViews`, Leanback shell, `HttpURLConnection` Enigma2, leftover Java overlay) were sequencing choices, **not** permanent freezes. When those surfaces come up again, move them to the current Android/Compose default — do not preserve a legacy chassis only because a prior decision deferred it. Dialogs are the first reopen (Phase **2.1g-ii** below).

GitHub Actions: [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml) runs on PRs/`main` — unit tests + assemble + androidTest compile (JDK 25), plus instrumented Compose tests on an API 30 emulator (`-Pci` disables ABI splits for a single installable APK).

## Status as of 2026-09-11 (SOTA reopen)

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
| http-async-locations-tags | [#223](https://github.com/sreichholf/dreamDroid/pull/223) | merged | Phase 2.2j: Locations/tags prefetch via `lifecycleScope` + `DreamDroid.loadLocations/Tags`; drop `GetLocationsAndTagsTask`. |
| http-async-simpleresult | [#224](https://github.com/sreichholf/dreamDroid/pull/224) | merged | Phase 2.2k: Mutations via `lifecycleScope` + `SimpleResultLoad`; drop `SimpleResultTask` (HttpFragmentHelper, NavigationHelper, ShareActivity). |
| http-async-volume-power-sleep | [#225](https://github.com/sreichholf/dreamDroid/pull/225) | merged | Phase 2.2l: Volume/power/sleeptimer via `lifecycleScope` + `VolumePowerSleepLoad`; drop `SetVolumeTask`/`SetPowerStateTask`/`SleepTimerTask`. |
| http-async-profile-detect | [#226](https://github.com/sreichholf/dreamDroid/pull/226) | merged | Phase 2.2m: Profile check + device detect via `lifecycleScope`; drop `CheckProfileTask`/`DetectDevicesTask`. |
| http-async-drop-asynctask-base | [#227](https://github.com/sreichholf/dreamDroid/pull/227) | merged | Phase 2.2n: Delete unused `AsyncHttpTaskBase` / `AsyncTaskExecutorService` and empty `asynctask/` package. |
| http-loader-chassis-stubs | [#229](https://github.com/sreichholf/dreamDroid/pull/229) | merged | Phase 2.2o: Drop dead `LoaderCallbacks` from phone HTTP bases + stub fragments; delete unused `AsyncSimpleLoader`. |
| http-async-screenshot | [#230](https://github.com/sreichholf/dreamDroid/pull/230) | merged | Phase 2.2p: ScreenShot grab via `lifecycleScope` + `ScreenshotLoad`; drop `AsyncByteLoader`. |
| http-async-videooverlay-epg | [#231](https://github.com/sreichholf/dreamDroid/pull/231) | merged | Phase 2.2q: VideoOverlay bouquet now/next via `lifecycleScope` + reuse `EpgNowNextLoad`; map with `serviceNowNextToExtendedHashMap`; drop LoaderCallbacks on overlay only. Keep ButterKnife/VLC UI. Keep OkHttp 3.14.9. |
| http-async-tv-browse | [#232](https://github.com/sreichholf/dreamDroid/pull/232) | merged | Phase 2.2r: Leanback `RootBrowseFragment` → `lifecycleScope` + reuse `ServiceListLoad` / `EpgNowNextLoad` / `MovieListLoad` (+ locs/tags); drop TV `LoaderCallbacks` / `AsyncListLoader` / `LoaderResult`. Keep Leanback UI + `ExtendedHashMap` BrowseItem. Keep OkHttp 3.14.9. |
| tv-browse-typed-item | [#234](https://github.com/sreichholf/dreamDroid/pull/234) | merged | Phase 3.1a: sealed Kotlin `BrowseItem` (`Service`/`Movie`/`Settings`); hash only at stream Intent edge; keep Leanback UI. Keep OkHttp 3.14.9. |
| tv-detail-compose | [#235](https://github.com/sreichholf/dreamDroid/pull/235) | merged | Phase 3.1b: TV `EpgDetailDialog` / `MovieDetailDialog` → Compose via shared phone screens + `DreamDroidTheme`; actions hidden on TV EPG; drop ButterKnife on those two dialogs. Keep OkHttp 3.14.9. |
| tv-prefs-compose | [#236](https://github.com/sreichholf/dreamDroid/pull/236) | merged | Phase 3.1d: Leanback prefs → Compose (`TvSettingsScreen` + `ProfileEditScreen` in `PreferenceActivity`); same PreferenceManager keys. Keep OkHttp 3.14.9. |
| tv-textcard-drop-butterknife | [#237](https://github.com/sreichholf/dreamDroid/pull/237) | merged | Drop ButterKnife on TV `TextCardView` (last TV binds); keep dep for `VideoOverlayFragment` (VLC). Keep OkHttp 3.14.9. |
| tv-hub-focus-dive | [#238](https://github.com/sreichholf/dreamDroid/pull/238) | merged | Phase 3.1c dive (docs only): TV browse hub focus options + agreed hub Compose PR slices. No hub code. |
| tv-compose-textcard | [#239](https://github.com/sreichholf/dreamDroid/pull/239) | merged | Phase 3.1c-i: Leanback movie `TextCardView` → Compose body inside `BaseCardView`; keep rows/headers. Keep OkHttp 3.14.9. |
| tv-compose-imagecard | [#240](https://github.com/sreichholf/dreamDroid/pull/240) | merged | Phase 3.1c-ii: Leanback service/settings image cards → Compose text + ImageView picons inside `BaseCardView`; keep rows/headers. Keep OkHttp 3.14.9. |
| docs-tv-hub-shell-decision | [#241](https://github.com/sreichholf/dreamDroid/pull/241) | merged | Phase 3.1c-iii (docs only): keep Leanback shell + Compose cards (option B); defer full Compose hub (C / 3.1c-iv). No hub code. Keep OkHttp 3.14.9. |
| docs-tv-compose-hub-option-c | [#308](https://github.com/sreichholf/dreamDroid/pull/308) | merged | Phase 3.1c-iv scheduled (docs only): operator asks for full Compose TV hub polish after Phase 2.7; PR slices + stack (`androidx.tv`). No hub code. |
| tv-compose-hub-iv-b | [#336](https://github.com/sreichholf/dreamDroid/pull/336) | merged | Phase 3.1c-iv-b: add `androidx.tv` (`tv-foundation`/`tv-material`); empty Compose TV hub stub behind debug-only pref `compose_tv_hub_debug` (default Leanback). Wire Room via KSP so Kotlin `AppDatabase`/`Profile` generate `*_Impl` again (Java `annotationProcessor` stopped after the Kotlin port). No release hub behavior change. |
| tv-compose-hub-iv-c | [#337](https://github.com/sreichholf/dreamDroid/pull/337) | merged | Phase 3.1c-iv-c: Compose TV hub chrome — TV Material `NavigationDrawer` side headers + row list focus model; settings row Reload / Preferences / Profile. Leanback remains default behind debug flag. No service/movie rows yet (iv-d/e). |
| tv-compose-hub-iv-d | [#338](https://github.com/sreichholf/dreamDroid/pull/338) | merged | Phase 3.1c-iv-d: Compose TV hub service/now-next rows + picon cards behind debug flag; bouquet headers from typed `loadServiceList`/`loadEpgNowNext`. Stream Intent edge unchanged. Movies still Leanback / iv-e. |
| tv-compose-hub-iv-e | [#339](https://github.com/sreichholf/dreamDroid/pull/339) | merged | Phase 3.1c-iv-e: Compose TV hub movie location headers + lazy `loadMovieList` on select; text cards; stream file Intent edge unchanged. Leanback remains default behind debug flag. |
| tv-compose-hub-iv-f | [#340](https://github.com/sreichholf/dreamDroid/pull/340) | merged | Phase 3.1c-iv-f: Compose TV hub is the only browse host; delete Leanback `RootBrowseFragment` / `BaseHttpBrowseFragment` / `CardPresenter` / Leanback card wrappers / `tv_main`; drop `leanback-preference`; keep `leanback` for VideoOverlay `HorizontalGridView`. |
| tv-compose-hub-iv-g | | open | Phase 3.1c-iv-g: TV hub instrumented coverage (header select, loading/error, movie loading) + Phase 4 operator Android TV / box smoke notes. No phone drive-bys. |
| room-backup-finish | [#242](https://github.com/sreichholf/dreamDroid/pull/242) | merged | Phase 2.4: Android BackupAgent includes Room `dreambox` (and legacy `dreamdroid` for restore compat); drop legacy file after migrate; widget stops using `DatabaseHelper` keys. Keep migrate-only `DatabaseHelper`. Keep OkHttp 3.14.9. |
| docs-vlc-product-decision | [#243](https://github.com/sreichholf/dreamDroid/pull/243) | merged | Phase 2.5 (docs only): VLC/streaming inventory + product options; **decision A** keep libVLC + Compose overlay rewrite path. No player code. Keep OkHttp 3.14.9. |
| vlc-overlay-typed-state | [#244](https://github.com/sreichholf/dreamDroid/pull/244) | merged | Phase 2.5b typed VideoOverlay state (`ServiceNowNext` / `Movie`); hash only at stream Intent / legacy edges. Keep OkHttp 3.14.9. |
| vlc-overlay-compose | [#245](https://github.com/sreichholf/dreamDroid/pull/245) | merged | Phase 2.5c Compose overlay chrome (+ ButterKnife drop / 2.5d). Keep OkHttp 3.14.9. |
| docs-state-rotation-dive | [#246](https://github.com/sreichholf/dreamDroid/pull/246) | merged | Phase 2.3a (docs only): Evernote @State + Livefront Bridge inventory + agreed migration slices. Keep OkHttp 3.14.9. |
| state-deviceinfo-beachhead | [#247](https://github.com/sreichholf/dreamDroid/pull/247) | merged | Phase 2.3b: DeviceInfoFragment drops `@State`; save/restore typed `DeviceInfo` via fragment Bundle. Keep Bridge/Evernote for other consumers. Keep OkHttp 3.14.9. |
| state-simple-fragments | [#249](https://github.com/sreichholf/dreamDroid/pull/249) | merged | Phase 2.3c: ServiceListPage / ServiceListPager / EpgBouquet drop `@State`; Bundle string/int save. Keep Bridge. Keep OkHttp 3.14.9. |
| state-screenshot | [#250](https://github.com/sreichholf/dreamDroid/pull/250) | merged | Phase 2.3d: ScreenShotFragment drops `@State` on `mRawImage`; do not Bundle the bytes — reload on process death. Keep Bridge. Keep OkHttp 3.14.9. |
| state-hash-fragments | [#251](https://github.com/sreichholf/dreamDroid/pull/251) | merged | Phase 2.3e: timers/movies/current/BaseHttpRecyclerEvent drop `@State`; Bundle selection + filters (typed `CurrentService`). Keep Bridge for MultiChoiceDialog. Keep OkHttp 3.14.9. |
| state-drop-bridge | [#252](https://github.com/sreichholf/dreamDroid/pull/252) | merged | Phase 2.3f: MultiChoiceDialog Bundle `boolean[]`; remove Livefront Bridge + Evernote android-state deps. Keep OkHttp 3.14.9. |
| docs-navhost-dive | [#254](https://github.com/sreichholf/dreamDroid/pull/254) | merged | Phase 2.1b (docs only): phone NavHost / Compose Navigation inventory + agreed hybrid slices. Keep OkHttp 3.14.9. |
| navhost-deviceinfo-beachhead | [#255](https://github.com/sreichholf/dreamDroid/pull/255) | merged | Phase 2.1c: `navigation-compose` + `PhoneNavHostFragment` Device Info leaf; other drawer destinations still Fragment/`NavigationHelper`. Keep OkHttp 3.14.9. |
| navhost-drawer-navcontroller | [#256](https://github.com/sreichholf/dreamDroid/pull/256) | merged | Phase 2.1d: drawer Device Info uses `NavController` when `PhoneNavHostFragment` is already shown (no `clearBackStack`+`showDetails`). Keep OkHttp 3.14.9. |
| navhost-signal-leaf | [#257](https://github.com/sreichholf/dreamDroid/pull/257) | merged | Phase 2.1e: Signal drawer root on `PhoneNavHost` (sibling of Device Info); `ARG_START_ROUTE` + drawer-style navigate. Keep OkHttp 3.14.9. |
| navhost-screenshot-leaf | [#258](https://github.com/sreichholf/dreamDroid/pull/258) | merged | Phase 2.1e continued: Screenshot drawer root on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-current-leaf | [#260](https://github.com/sreichholf/dreamDroid/pull/260) | merged | Phase 2.1e continued: Current Service drawer root on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-zap-leaf | [#262](https://github.com/sreichholf/dreamDroid/pull/262) | merged | Phase 2.1e continued: Zap drawer root on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-backup-leaf | [#265](https://github.com/sreichholf/dreamDroid/pull/265) | merged | Phase 2.1e continued: Backup drawer root on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-profiles-leaf | [#266](https://github.com/sreichholf/dreamDroid/pull/266) | merged | Phase 2.1e continued: Profiles drawer root on `PhoneNavHost` (still clears drawer selection). Keep OkHttp 3.14.9. |
| navhost-epg-leaf | [#267](https://github.com/sreichholf/dreamDroid/pull/267) | merged | Phase 2.1e continued: EPG bouquet drawer root on `PhoneNavHost` with service ref/name extras. Keep OkHttp 3.14.9. |
| navhost-remote-leaf | [#269](https://github.com/sreichholf/dreamDroid/pull/269) | merged | Phase 2.1e continued: tablet Virtual Remote on `PhoneNavHost`; phone still side activity. Keep OkHttp 3.14.9. |
| navhost-hub-leaf | [#270](https://github.com/sreichholf/dreamDroid/pull/270) | merged | Phase 2.1e continued: hub `ServiceListPager` on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| widgets-2-6-dive | [#271](https://github.com/sreichholf/dreamDroid/pull/271) | merged | Phase 2.6: widgets product decision (keep XML RemoteViews *then*). **Superseded** — Glance scheduled as **2.6e**. |
| widgets-2-6b-cleanup | [#272](https://github.com/sreichholf/dreamDroid/pull/272) | merged | Phase 2.6b: Kotlin coroutine widget click path (drop JobIntentService); prefs delete `isFull`; remove dead SyncService/`HttpIntentService`. Keep OkHttp 3.14.9. |
| anchor-popup-kotlin | [#273](https://github.com/sreichholf/dreamDroid/pull/273) | merged | Convert `AnchorPopup` to Kotlin; retab `WidgetRemoteRequest.kt`. Keep OkHttp 3.14.9. |
| navhost-service-epg | [#274](https://github.com/sreichholf/dreamDroid/pull/274) | merged | Phase 2.1f beachhead: nested service EPG typed route on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-epg-search | [#275](https://github.com/sreichholf/dreamDroid/pull/275) | merged | Phase 2.1f continued: nested EPG search typed query route on `PhoneNavHost`. Keep OkHttp 3.14.9. |
| navhost-pick-service | [#276](https://github.com/sreichholf/dreamDroid/pull/276) | merged | Phase 2.1f continued: nested bouquet pick on `PhoneNavHost` (host delivers result; no setTargetFragment). Keep OkHttp 3.14.9. |
| navhost-phone-remote | [#277](https://github.com/sreichholf/dreamDroid/pull/277) | merged | Phase 2.1h beachhead: phone Virtual Remote on `PhoneNavHost` (same as tablet); drop `SimpleNoTitleFragmentActivity`. Keep OkHttp 3.14.9. |
| docs-dialog-policy | [#278](https://github.com/sreichholf/dreamDroid/pull/278) | merged | Phase 2.1g: dialog policy decision (keep fragment dialogs *then*). **Superseded** by **2.1g-ii** (Compose M3 / Navigation `dialog`). |
| navhost-settings-leaf | [#279](https://github.com/sreichholf/dreamDroid/pull/279) | merged | Phase 2.1h continued: Settings drawer root on `PhoneNavHost`; drop side-activity path. Keep OkHttp 3.14.9. |
| navhost-profile-edit | [#280](https://github.com/sreichholf/dreamDroid/pull/280) | merged | Phase 2.1h continued: nested profile create/edit on `PhoneNavHost` (host delivers result). Keep OkHttp 3.14.9. |
| navhost-timer-edit | [#281](https://github.com/sreichholf/dreamDroid/pull/281) | merged | Phase 2.1h continued: nested timer create/edit on `PhoneNavHost`; service pick stayed side activity. Keep OkHttp 3.14.9. |
| navhost-timer-service-pick | [#283](https://github.com/sreichholf/dreamDroid/pull/283) | merged | Phase 2.1h continued: nested timer service pick on `PhoneNavHost` (request-code stack). Keep OkHttp 3.14.9. |
| drop-side-edit-fallbacks | [#284](https://github.com/sreichholf/dreamDroid/pull/284) | merged | Phase 2.1h continued: drop `SimpleToolbarFragmentActivity` fallbacks for profile/timer edit + service pick. Keep OkHttp 3.14.9. |
| navhelper-map-routes | [#285](https://github.com/sreichholf/dreamDroid/pull/285) | merged | Phase 2.1h continued: map drawer menu ids → `PhoneNavHost` roots; keep dialog/action arms. Keep OkHttp 3.14.9. |
| fix-nested-fragment-resume | [#286](https://github.com/sreichholf/dreamDroid/pull/286) | merged | Fix crash: nested `PhoneNavHost` leaves must not call `showDetails`. Keep OkHttp 3.14.9. |
| drawer-ia-settings | [#282](https://github.com/sreichholf/dreamDroid/pull/282) | merged | Slim drawer; nest About/Changelog/Backup under Settings. Keep OkHttp 3.14.9. |
| fix-hub-nested-teardown | [#287](https://github.com/sreichholf/dreamDroid/pull/287) | merged | Fix hub TV/Movies bottom bar stuck after leaving hub (remove nested leaf on NavHost route dispose). Keep OkHttp 3.14.9. |
| widgets-2-6c-config | [#288](https://github.com/sreichholf/dreamDroid/pull/288) | merged | Phase 2.6c: Compose Virtual Remote widget config activity; keep RemoteViews grids. Keep OkHttp 3.14.9. |
| retire-simple-fragment-activity | [#289](https://github.com/sreichholf/dreamDroid/pull/289) | merged | Drop orphan `SimpleFragmentActivity`; route SEARCH to `MainActivity` / PhoneNavHost EPG search. Keep OkHttp 3.14.9. |
| assert-navhost-leaf-fallbacks | [#291](https://github.com/sreichholf/dreamDroid/pull/291) | merged | Drop dead bare leaf `showDetails` fallbacks; cold SEARCH mounts `PhoneNavHost` + `queueEpgSearch`. Keep OkHttp 3.14.9. |
| dead-weight-epg-database | [#293](https://github.com/sreichholf/dreamDroid/pull/293) | merged | Drop fully commented `EpgDatabase.java` + empty `epgsync/` package. Keep OkHttp 3.14.9. |
| vlc-kotlin-wrapper | [#296](https://github.com/sreichholf/dreamDroid/pull/296) | merged | Phase 2.5e: thin Kotlin `VLCInstance`/`VLCPlayer`; keep existing overlay Compose smoke test. Keep OkHttp 3.14.9. |
| okhttp4-picasso | [#299](https://github.com/sreichholf/dreamDroid/pull/299) | merged | Bump OkHttp 3.14.9 → 4.12.0 for Picasso/TLS; drop `okhttp3.internal` hostname verifier. Enigma2 stays HttpURLConnection. No libVLC / Media3 / Glance. |
| libvlc-375-stable | [#300](https://github.com/sreichholf/dreamDroid/pull/300) | merged | Bump `libvlc-all` 3.5.1 → 3.7.5 (stable); raise `compileSdk` 34→36 (AAR requires ≥36); keep `targetSdk` 34. No Media3 / Glance. |
| docs-fragment-rework | [#304](https://github.com/sreichholf/dreamDroid/pull/304) | merged | Phase 2.7a: rework phone Fragment shells into **Kotlin** Compose NavHost destinations (convert off Java; not “same Fragment in Kotlin”). Docs only. |
| device-info-compose-dest | [#306](https://github.com/sreichholf/dreamDroid/pull/306) | merged | Phase 2.7b: Device Info Kotlin Compose destination; delete `DeviceInfoFragment` + `device_info.xml`. |
| backup-signal-compose-dest | [#307](https://github.com/sreichholf/dreamDroid/pull/307) | merged | Phase 2.7c partial: Backup + Signal Kotlin Compose destinations; delete `BackupFragment` / `SignalFragment`. |
| current-screenshot-compose-dest | [#309](https://github.com/sreichholf/dreamDroid/pull/309) | merged | Phase 2.7c finish: Current + Screenshot Kotlin Compose destinations; delete `CurrentServiceFragment` / `ScreenShotFragment`. |
| zap-remote-compose-dest | [#310](https://github.com/sreichholf/dreamDroid/pull/310) | merged | Phase 2.7d: Zap + Virtual Remote Kotlin Compose destinations; delete `ZapFragment` / `VirtualRemotePagerFragment` / `VirtualRemoteFragment` / `ScreenShotFragment`. |
| settings-profiles-compose-dest | [#311](https://github.com/sreichholf/dreamDroid/pull/311) | merged | Phase 2.7e: Settings + Profiles + Profile edit Kotlin Compose destinations; delete `MyPreferenceFragment` / `ProfileListFragment` / `ProfileEditFragment`. |
| epg-cluster-compose-dest | [#312](https://github.com/sreichholf/dreamDroid/pull/312) | merged | Phase 2.7f: EPG bouquet + Service EPG + EPG search + Pick service Kotlin Compose destinations. |
| timer-edit-compose-dest | [#313](https://github.com/sreichholf/dreamDroid/pull/313) | merged | Phase 2.7g: Timer edit + Timer service pick Kotlin Compose destinations. |
| hub-compose-dest | [#314](https://github.com/sreichholf/dreamDroid/pull/314) | merged | Phase 2.7h: Hub Kotlin Compose destination (`HubDestination` + page composables); delete `ServiceListPager` / page Fragments / `service_list_pager.xml`. |
| chassis-cleanup-2-7i | [#315](https://github.com/sreichholf/dreamDroid/pull/315) | merged | Phase 2.7i: Drop unused `NestedFragmentDestination` / phone `BaseHttp*` / recycler bases / `HttpFragmentHelper` / `IHttpBase`; `NavExtras.DATA` for Bundle `"data"`; TV `LOADER_DEFAULT_ID = 0`. |
| near-zero-java-dead-weight | [#316](https://github.com/sreichholf/dreamDroid/pull/316) | merged | Near-zero Java beachhead: delete post-Compose orphan adapters/helpers/layouts + unused list `*RequestHandler` / `E2*List` SAX / `E2ListHandler`; keep dialogs, VideoOverlay, Leanback, widgets RemoteViews, HttpURLConnection Enigma2. |
| near-zero-java-helpers | [#317](https://github.com/sreichholf/dreamDroid/pull/317) | merged | Near-zero Java residue: drop orphan `Signal` + unused DeviceInfo handler/SAX/hash keys + dead menus; Base64 → `android.util.Base64`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-kotlin-helpers | [#318](https://github.com/sreichholf/dreamDroid/pull/318) | merged | Port tiny helpers to Kotlin (`Python`, `BundleHelper`, `ProfileChangedListener`, `SimpleResult`/`Message`/`Tag`/`Volume`/`PowerState`/`SleepTimer`/`Remote`, `DreamDroidAttributionPresenter`). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-more-helpers | [#319](https://github.com/sreichholf/dreamDroid/pull/319) | merged | Kotlin-port more tiny helpers (`CurrentService`/`URIStore`/`Service`/`NameValuePair`/`Statics`/`GenericSetting`/`BackupData`/`AppDatabase`). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-interfaces | [#320](https://github.com/sreichholf/dreamDroid/pull/320) | merged | Kotlin-port fragment interfaces + `IntentFactory` + `DeviceDetector` (`MultiPaneHandler`/`ActivityCallbackHandler`/`IBaseFragment`/`IMutliPaneContent`). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-datetime | [#321](https://github.com/sreichholf/dreamDroid/pull/321) | merged | Kotlin-port `DateTime` + `DataParser`/`AbstractDataProvider`/`SimpleRequestInterface`/`SpacesItemDecoration`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-event-movie | [#322](https://github.com/sreichholf/dreamDroid/pull/322) | merged | Kotlin-port `Event`/`Movie`, thin Enigma2 request handlers, tiny SAX helpers, `DreamDroidBackupAgent`, `TabbedNavigationActivity`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-handlers | [#323](https://github.com/sreichholf/dreamDroid/pull/323) | merged | Kotlin-port request bases (`Request`/`AbstractSimple*RequestHandler`/`SimpleResultRequestHandler`), remaining E2 SAX handlers, `SaxDataProvider`, `DreamDroidTrustManager`, `AutofitRecyclerView`/`DrawerLayout`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-helpers2 | [#324](https://github.com/sreichholf/dreamDroid/pull/324) | merged | Kotlin-port `ExtendedHashMap`/`Picon`/`FragmentHelper`/`GenericSaxParser`/`ShareActivity` + recycler click helpers. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-timer-check | [#325](https://github.com/sreichholf/dreamDroid/pull/325) | merged | Kotlin-port `Timer`/`CheckProfile`/`BackupService`/`PiconSyncService`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-navhelper | [#326](https://github.com/sreichholf/dreamDroid/pull/326) | merged | Kotlin-port `NavigationHelper`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-base-shell | [#327](https://github.com/sreichholf/dreamDroid/pull/327) | merged | Kotlin-port `BaseFragment` + `BaseActivity`. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-profile | [#328](https://github.com/sreichholf/dreamDroid/pull/328) | merged | Kotlin-port `Profile` Room entity + Dao. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-service-adapter | [#329](https://github.com/sreichholf/dreamDroid/pull/329) | merged | Kotlin-port `ServiceAdapter` (VideoOverlay list). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-video-activity | [#330](https://github.com/sreichholf/dreamDroid/pull/330) | merged | Kotlin-port `VideoActivity` (VLC shell; overlay fragment stays Java). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-database-helper | [#331](https://github.com/sreichholf/dreamDroid/pull/331) | merged | Kotlin-port `DatabaseHelper` (legacy SQLite profiles/events). Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-dreamdroid-app | [#332](https://github.com/sreichholf/dreamDroid/pull/332) | merged | Kotlin-port `DreamDroid` Application. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-main-activity | [#333](https://github.com/sreichholf/dreamDroid/pull/333) | merged | Kotlin-port phone `MainActivity` shell. Keep dialogs / VideoOverlay / Leanback / RemoteViews / HttpURLConnection. |
| near-zero-java-keepers-only | [#334](https://github.com/sreichholf/dreamDroid/pull/334) | merged | Near-zero production Java reached: **15** Java files left that were recorded as temporary keepers — `fragment/dialogs/*` (8), `VideoOverlayFragment`, Leanback `tv/{activities,fragment,presenter}` (4), `VirtualRemoteWidgetProvider`, `SimpleHttpClient`. Those keepers are **not** permanent; operator reopen (2026-09-11) retires them toward SOTA. |
| docs-sota-no-keepers | [#335](https://github.com/sreichholf/dreamDroid/pull/335) | merged | Operator override: no permanent keepers. Supersede Phase **2.1g** option A — dialogs → Compose Material 3 / Navigation `dialog` destinations. Reopen widgets Glance, Enigma2 OkHttp, VideoOverlay Kotlin, Leanback Compose hub as scheduled SOTA work. Docs only. |

Wave 1 of this plan is on `main`. It is **not** a finished modernization. See Appendix E / H.

Wave 2 (operator choice): (1) TV & Movies lists (#170), (4) dead-weight (#172/#175/#177), and (2) typed list paths (#173/#176/#179/#180/#181/#183/#203/#209/#210) are on `main`. Dead-weight deletes must not drop ButterKnife (still used by phone VLC overlay).

Wave 3 (operator choice): convert remaining **non-Compose phone UIs** to Compose + Kotlin, **one PR per screen**. Checklist in Appendix G. **Wave 3 phone screens complete on `main` through #206** (plus CI #204). Phone Phase 2.7 (Fragment shells → Kotlin Compose destinations) **2.7b–i merged** ([#306](https://github.com/sreichholf/dreamDroid/pull/306)–[#315](https://github.com/sreichholf/dreamDroid/pull/315)). **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** (TV hub instrumented tests + Phase 4 operator box smoke notes). **Next:** Phase **4** operator usertests / dialogs **2.1g-ii** parallel SOTA backlog.

### Operator overrides (this program)

- **No permanent keepers.** Prior “keep DialogFragments / RemoteViews / HttpURLConnection / Leanback shell / Java overlay” decisions were deferrals. Reopen and modernize to the current Android/Compose default when touching that surface; do not leave a legacy chassis because an older plan said keep.
- **Land** when the operator says land. That waives "owners do not merge" for that PR.
- **Proof** is `./gradlew.bat :app:connectedGoogleDebugAndroidTest`. Do not prove phone UI by tapping the emulator through `adb` / `verify-dreamdroid.py` in a loop. That script is a single look, or a shell path that has no test yet.
- Do not pass `-Pandroid.testInstrumentationRunnerArguments...`. Gradle then sets project property `android` to a String and `android.applicationVariants` breaks. Filter with `adb shell am instrument -w -e class ... net.reichholf.dreamdroid.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- Theme follows `DreamDroid.getThemeType()`. Default `"1"` is always night. Do not reopen `colorSchemeFromViewTheme`. Version/license `Text` uses `onSurface`. Dialog-hosted Compose must be tested in a dialog/`ComposeView`, not only `setContent { }`.
- `captureToImage` on this AVD returns black pixels even for light content. Do not use pixel-luminance tests.
- Java calling Kotlin `(T) -> Unit` must `return kotlin.Unit.INSTANCE`. `DisposeOnViewTreeLifecycleDestroyed` is set from Kotlin, not Java.
- Two googleDebug processes cannot share one device. Gradle 9.6 / AGP 9.4 on JDK 25 (bytecode Java 17).

### Not done, recorded so it is not pretended done

- Swarm live lanes, perf probes, and `media/pr-*-review.*` videos were **not** run. The operator accepted connectedAndroidTest and landed.
- Hub + rows are Compose on `main` (#169/#170). `ServiceAdapter` remains for video overlay.
- Leftover `app/res/service_list_pager.xml` stub and `android-retrostreams` were removed in #172. Hub layout + pager Fragments deleted in [#314](https://github.com/sreichholf/dreamDroid/pull/314) (2.7h).
- Wave 3 phone Compose screens complete on `main` through #206; CI #204.
- Appendix G phone UI checklist: all 19 items merged.
- Remaining typed API (not Wave 3 UI): none on phone list paths (hub now/next #209; movies list #210; detail edge #206). Phase 0 Leanback dive #211 on `main`.
- Phone NavHost Device Info through Hub are **Kotlin Compose destinations**; Phase **2.7b–i** **merged** ([#306](https://github.com/sreichholf/dreamDroid/pull/306)–[#315](https://github.com/sreichholf/dreamDroid/pull/315); decision [#304](https://github.com/sreichholf/dreamDroid/pull/304)). **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** (TV hub instrumented tests + Phase 4 operator box smoke notes). **Next:** Phase **4** operator usertests / dialogs **2.1g-ii** parallel SOTA backlog.
- Phase 2.1a drawer chrome **merged** [#212](https://github.com/sreichholf/dreamDroid/pull/212). Phase 2.1b–e (through hub) **merged** [#254](https://github.com/sreichholf/dreamDroid/pull/254)–[#270](https://github.com/sreichholf/dreamDroid/pull/270). Widgets **merged** [#271](https://github.com/sreichholf/dreamDroid/pull/271)/[#272](https://github.com/sreichholf/dreamDroid/pull/272).
- Phase 2.2a Device Info coroutines **merged** [#213](https://github.com/sreichholf/dreamDroid/pull/213).
- Phase 2.2b Signal coroutines **merged** [#214](https://github.com/sreichholf/dreamDroid/pull/214).
- Phase 2.2c Current Service coroutines **merged** [#215](https://github.com/sreichholf/dreamDroid/pull/215).
- Phase 2.2d Timer list coroutines **merged** [#216](https://github.com/sreichholf/dreamDroid/pull/216).
- Phase 2.2e Movies list coroutines **merged** [#217](https://github.com/sreichholf/dreamDroid/pull/217).
- Phase 2.2f Hub now/next coroutines **merged** [#218](https://github.com/sreichholf/dreamDroid/pull/218).
- Phase 2.2g Zap + TimerServicePick service-list coroutines **merged** [#219](https://github.com/sreichholf/dreamDroid/pull/219).
- Phase 2.2h Service/bouquet/search EPG event-list coroutines **merged** [#220](https://github.com/sreichholf/dreamDroid/pull/220).
- Phase 2.2i Hub/pick/timer bouquet-list coroutines **merged** [#222](https://github.com/sreichholf/dreamDroid/pull/222).
- Phase 2.2j Locations/tags prefetch coroutines **merged** [#223](https://github.com/sreichholf/dreamDroid/pull/223).
- Phase 2.2k SimpleResult mutations coroutines **merged** [#224](https://github.com/sreichholf/dreamDroid/pull/224).
- Phase 2.2l Volume/power/sleeptimer coroutines **merged** [#225](https://github.com/sreichholf/dreamDroid/pull/225).
- Phase 2.2m Profile check + device-detect coroutines **merged** [#226](https://github.com/sreichholf/dreamDroid/pull/226).
- Phase 2.2n AsyncTask base retirement **merged** [#227](https://github.com/sreichholf/dreamDroid/pull/227).
- Phase 2.2o Loader chassis stubs **merged** [#229](https://github.com/sreichholf/dreamDroid/pull/229).
- Phase 2.2p ScreenShot bytes **merged** [#230](https://github.com/sreichholf/dreamDroid/pull/230).
- Phase 2.2q VideoOverlay now/next **merged** [#231](https://github.com/sreichholf/dreamDroid/pull/231).
- Phase 2.2r Leanback RootBrowse coroutines **merged** [#232](https://github.com/sreichholf/dreamDroid/pull/232). HTTP Loader chassis retired (`loader/` package gone).
- Phase 3.1a typed Leanback `BrowseItem` **merged** [#234](https://github.com/sreichholf/dreamDroid/pull/234).
- Phase 3.1b TV detail dialogs → Compose **merged** [#235](https://github.com/sreichholf/dreamDroid/pull/235).
- Phase 3.1d Leanback prefs → Compose **merged** [#236](https://github.com/sreichholf/dreamDroid/pull/236).
- TV `TextCardView` ButterKnife dropped **merged** [#237](https://github.com/sreichholf/dreamDroid/pull/237).
- Phase 3.1c TV hub focus dive **merged** [#238](https://github.com/sreichholf/dreamDroid/pull/238).
- Phase 3.1c-i Compose `TextCardView` beachhead **merged** [#239](https://github.com/sreichholf/dreamDroid/pull/239).
- Phase 3.1c-ii Compose service/settings image cards **merged** [#240](https://github.com/sreichholf/dreamDroid/pull/240).
- Phase 3.1c-iii hub shell decision **merged** [#241](https://github.com/sreichholf/dreamDroid/pull/241) (kept Leanback + Compose cards at the time).
- Phase 3.1c-iv full Compose TV hub (**option C**) scheduled **merged** [#308](https://github.com/sreichholf/dreamDroid/pull/308); **3.1c-iv-b–f** **merged** [#336](https://github.com/sreichholf/dreamDroid/pull/336)–[#340](https://github.com/sreichholf/dreamDroid/pull/340). **This PR:** **3.1c-iv-g** hub instrumented tests + Phase 4 smoke notes.
- Phase 2.4 Room backup finish **merged** [#242](https://github.com/sreichholf/dreamDroid/pull/242).
- Phase 2.5 VLC product decision **merged** [#243](https://github.com/sreichholf/dreamDroid/pull/243) (keep libVLC; Compose overlay rewrite path).
- Phase 2.5b typed overlay state **merged** [#244](https://github.com/sreichholf/dreamDroid/pull/244) (`ServiceNowNext` / `Movie`; hash only at stream Intent / legacy edges).
- Phase 2.5c Compose overlay **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) (ComposeView host; ButterKnife library dropped / 2.5d folded).
- Phase 2.3a state/rotation dive **merged** [#246](https://github.com/sreichholf/dreamDroid/pull/246).
- Phase 2.3b DeviceInfoFragment `@State` beachhead **merged** [#247](https://github.com/sreichholf/dreamDroid/pull/247).
- Phase 2.3c hub/EPG string-int `@State` **merged** [#249](https://github.com/sreichholf/dreamDroid/pull/249).
- Phase 2.3d ScreenShot `@State` drop **merged** [#250](https://github.com/sreichholf/dreamDroid/pull/250).
- Phase 2.3e hash-heavy `@State` **merged** [#251](https://github.com/sreichholf/dreamDroid/pull/251).
- Phase 2.3f Bridge/Evernote drop **merged** [#252](https://github.com/sreichholf/dreamDroid/pull/252) — Phase 2.3 complete.
- Phase 2.1b NavHost dive **merged** [#254](https://github.com/sreichholf/dreamDroid/pull/254).
- Phase 2.1c–e through hub **merged** [#255](https://github.com/sreichholf/dreamDroid/pull/255)–[#270](https://github.com/sreichholf/dreamDroid/pull/270).
- Phase 2.6a widgets decision **merged** [#271](https://github.com/sreichholf/dreamDroid/pull/271); 2.6b **merged** [#272](https://github.com/sreichholf/dreamDroid/pull/272).
- Phase 2.1f nested typed routes **merged** [#274](https://github.com/sreichholf/dreamDroid/pull/274)–[#276](https://github.com/sreichholf/dreamDroid/pull/276).
- Phase 2.1h beachhead phone Virtual Remote **merged** [#277](https://github.com/sreichholf/dreamDroid/pull/277).
- Phase 2.1g dialog policy **merged** [#278](https://github.com/sreichholf/dreamDroid/pull/278) (option A keep fragment dialogs *then*). **Superseded** by Phase **2.1g-ii** (this PR): Compose Material 3 / Navigation `dialog` destinations; retire DialogFragment chassis.
- Phase 2.1h Settings leaf **merged** [#279](https://github.com/sreichholf/dreamDroid/pull/279).
- Phase 2.1h nested profile edit **merged** [#280](https://github.com/sreichholf/dreamDroid/pull/280).
- Phase 2.1h nested timer edit **merged** [#281](https://github.com/sreichholf/dreamDroid/pull/281).
- Phase 2.1h nested timer service pick **merged** [#283](https://github.com/sreichholf/dreamDroid/pull/283).
- Phase 2.1h drop side-edit fallbacks **merged** [#284](https://github.com/sreichholf/dreamDroid/pull/284).
- Phase 2.1h map drawer NavHost roots **merged** [#285](https://github.com/sreichholf/dreamDroid/pull/285).
- Nested leaf `showDetails` crash fix **merged** [#286](https://github.com/sreichholf/dreamDroid/pull/286).
- Drawer IA (About/Changelog/Backup under Settings) **merged** [#282](https://github.com/sreichholf/dreamDroid/pull/282).
- Nested NavHost leaf teardown (hub bottom bar) **merged** [#287](https://github.com/sreichholf/dreamDroid/pull/287).
- Phase 2.6c Compose widget config **merged** [#288](https://github.com/sreichholf/dreamDroid/pull/288).
- Retire `SimpleFragmentActivity` **merged** [#289](https://github.com/sreichholf/dreamDroid/pull/289).
- Assert NavHost leaf fallbacks **merged** [#291](https://github.com/sreichholf/dreamDroid/pull/291).
- Dead-weight: drop commented `EpgDatabase` **merged** [#293](https://github.com/sreichholf/dreamDroid/pull/293).
- Phase 2.5e thin Kotlin VLC wrapper **merged** [#296](https://github.com/sreichholf/dreamDroid/pull/296).
- OkHttp 4.12 for Picasso/TLS **merged** [#299](https://github.com/sreichholf/dreamDroid/pull/299) (Enigma2 HTTP unchanged).
- libVLC-all **3.7.5** stable **merged** [#300](https://github.com/sreichholf/dreamDroid/pull/300) (still not Media3).
- Phase 2.7 phone Fragment→**Kotlin** Compose destination rework **2.7b–i merged** ([#304](https://github.com/sreichholf/dreamDroid/pull/304) docs; [#306](https://github.com/sreichholf/dreamDroid/pull/306)–[#315](https://github.com/sreichholf/dreamDroid/pull/315)).
- **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** — TV hub instrumented tests + Phase 4 operator Android TV / box smoke notes. Former keepers remain **SOTA backlog** (dialogs **2.1g-ii**, Glance, OkHttp Enigma2, VideoOverlay Kotlin) in parallel.

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
- [x] JDK 25 assemble / connected tests before push where UI changed.
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

Keeping minSdk 17 was rejected. Shipping phone and TV together in Wave 1 was rejected (phone first). Leanback hub shell was kept through Phase 3.1c-iii (#241); **operator later scheduled option C** (full Compose hub / 3.1c-iv) after phone Phase 2.7.

Keeping `ExtendedHashMap` as the list row type was rejected for new screens. Unmigrated fragments still use it.

## Appendix C. Risks and traps (current)

Play users below API 26 lose install. Floor is on `main`.

No Enigma2 box in CI. Connection error is a valid pass for list content. Crash is not.

ButterKnife library removed (**merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) / 2.5c+2.5d). TV binds cleared earlier (#235/#237); phone `VideoOverlayFragment` chrome is Compose.

Two HTTP stacks. Box XML is `HttpURLConnection`. Picons are Picasso plus OkHttp 3.14.9.

Two database files historically. Room `dreambox` holds `profile` and is included in Android Backup (**merged** [#242](https://github.com/sreichholf/dreamDroid/pull/242)). Legacy SQLite `dreamdroid` stays on the BackupAgent file list so pre-cutover cloud snapshots still restore; after restore (or first-run migrate) copies profiles into Room, the legacy file is deleted. Do not drop `DatabaseHelper` until no install still needs that migrate path (or operator accepts migrate-from-backup-only).

First-start skip-Profiles race is fixed on `main` in #168. Still wait for `Demo` after Changelog, not the word Profiles inside changelog text.

Evernote `@State` + Livefront Bridge retired in Phase 2.3 ([#252](https://github.com/sreichholf/dreamDroid/pull/252)). Fragments use fragment-local `onSaveInstanceState` Bundles. Do not re-add those deps.

VLC `VideoActivity` is out of this program.

Kotlin plugin is 1.9.24 with Compose compiler 1.5.14. OkHttp **this PR** bumps Picasso stack to 4.12.0. Enigma2 HTTP remains HttpURLConnection.

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
| Shell | `MainActivity`, `NavigationHelper`, drawer XML, `BaseFragment` tree | Bridge/`@State` retired Phase 2.3 |

### Still the old data stack

- Typed `EnigmaClient` exists. Zap list rows load typed `Service`. Service EPG list rows load typed `Event` (#176). EPG bouquet rows load typed `Event` (#179). EPG search rows load typed `Event` (#180). PickService list typing is on `main` via #181. Hub TV/Radio now/next loads typed `ServiceNowNext` (#209). Movies list loads typed `enigma.Movie` (#210). Leanback movie browse still uses hash SAX.
- Enigma2 HTTP still uses `HttpURLConnection` via `SimpleHttpClient` (coroutines + typed `EnigmaClient`). **Scheduled SOTA:** move Enigma2 to OkHttp (Picasso already on OkHttp 4.12). `asynctask/*` retired.
- Room holds `profile` only (`dreambox`). Android Backup includes Room and legacy `dreamdroid` for restore compat (**merged** [#242](https://github.com/sreichholf/dreamDroid/pull/242)). After migrate/restore copy, legacy file is deleted; `DatabaseHelper` stays migrate-only.
- ButterKnife dependency removed (**merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245)). TV binds cleared earlier; phone VLC overlay chrome is Compose. `legacy-support-v4` and `legacy-preference-v14` removed. `multiDexEnabled` stays; the `androidx.multidex` install helper is gone (minSdk 26).

### Explicitly frozen (narrow)

Historical “keepers” (DialogFragments, RemoteViews widgets, Leanback shell, `HttpURLConnection` Enigma2, Java `VideoOverlayFragment`) are **not** frozen — see operator override and Phase **2.1g-ii**. Still out of scope as *product* rewrites until asked:

- Enigma2 **server** side and VLC **codec / stream-protocol** work (player chrome may still move to Compose/Kotlin).
- Rotation model already modernized: fragment/destination-local Bundle save (Evernote/Bridge removed [#252](https://github.com/sreichholf/dreamDroid/pull/252)).

### Former keepers → SOTA backlog (scheduled)

| Surface | Was “keep” | SOTA target | Phase / slice |
| --- | --- | --- | --- |
| Phone dialogs / sheets | DialogFragment + Compose body ([#278](https://github.com/sreichholf/dreamDroid/pull/278) A) | Compose M3 `AlertDialog` / `ModalBottomSheet`; Navigation `dialog` destinations; delete FM chassis | **2.1g-ii** (docs this PR; code slices b–f) |
| Home-screen widgets | XML `RemoteViews` ([#271](https://github.com/sreichholf/dreamDroid/pull/271) A) | Glance (or Glance+RemoteViews hybrid for dense RCU) | **2.6e** |
| Enigma2 HTTP | `HttpURLConnection` `SimpleHttpClient` | OkHttp (aligned with Picasso stack) | dedicated HTTP PR after dialogs/TV hub pick |
| VLC overlay | Java `VideoOverlayFragment` (Compose chrome already) | Kotlin Compose destination / fragment | follow-on after dialogs beachhead or with player work |
| TV hub shell | Leanback browse ([#241](https://github.com/sreichholf/dreamDroid/pull/241) B) | Full Compose TV hub | **3.1c-iv** (already scheduled) |

### Sensible wave-2 shapes (pick one, do not do all at once)

1. **Finish TV & Movies** — Compose channel/movie/timer rows, drop `ServiceAdapter` on the pager path, feed typed `Service`/`Movie`/`Timer`. Highest continuity with #169. **Done on `main` as #170.**
2. **Retire `ExtendedHashMap` on one more list path at a time** — EPG, zap, current event. UI can stay XML until the parser boundary is typed. Stops the dual model from rotting. **Done on `main`:** Zap #173, Service EPG #176, bouquet EPG #179, search EPG #180, PickService #181, CurrentService #183, timers #203, device info #199, signal #200, hub now/next #209, movies list #210.
3. **Replace the drawer shell** — `NavigationHelper` + `MainActivity` in Compose Navigation. Touches every screen. Do this only after a few more destinations are Compose, or it wraps XML forever.
4. **Kill dead weight without UI rewrite** — ButterKnife (cleared with overlay Compose). `android-retrostreams` and leftover `res/service_list_pager.xml` dropped in **#172**. MediaPlayer UI + MultiDex lib + orphan layouts in **#175** (merged). #177: dead `EpgTimelineFragment`, `legacy-preference-v14`, `legacy-support-v4`, unused menus, GONE bottom nav. ButterKnife cleared with phone overlay Compose (#245).
5. **TV program** — Leanback hybrid complete; **full Compose hub (3.1c-iv / option C) after phone Phase 2.7**. Separate program. Do not mix into phone PRs.

Recommended default (historical): finish remaining typed API paths (shape 2), then wave 3 Compose screens (Appendix G). Drawer shell (3) and TV (5) were later programs — TV hub is now **3.1c-iv**; dialogs/widgets/HTTP keepers reopened under SOTA backlog.

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

Operator intent: **migrate everything** (phone leftovers + Leanback → Compose TV), then operator usertests, then bugfix pass. Do **one PR at a time**. Phase 3 hybrid Leanback path is on `main`; **full Compose hub (option C / 3.1c-iv)** code path is on `main` through **iv-f**; **this PR is 3.1c-iv-g** (tests + Phase 4 notes).

Phone Wave 3 + Phase 2.1–2.6 left **Compose UI inside Java Fragment shells** nested under `PhoneNavHost`. Phase **2.7** converts those shells to **Kotlin** Compose destinations (operator ask 2026-09-11): leave Java Fragments, move behavior into Kotlin screens/ViewModels, delete the shell. Do **not** stop at a same-shape Java→Kotlin Fragment rewrite.

### Phase 0 — Leanback dive (accepted)

Inventory of `app/src/.../tv/` (12 Java files, ~1.3k LOC). **No TV Compose code in this PR.** Phase 3 may start after this dive is on `main`.

#### Surfaces

| Surface | Path | Role |
| --- | --- | --- |
| MainActivity | `tv/activities/MainActivity.java` | TV host; installs Compose hub (`TvComposeHubHost`); custom TLS + Picasso OkHttp singleton |
| PreferenceActivity | `tv/activities/PreferenceActivity.kt` | TV settings/profile Compose host (Phase 3.1d) |
| TvComposeHubHost | `tv/ui/TvComposeHubHost.kt` | Compose TV browse hub (NavigationDrawer + service/movie rows) |
| EpgDetailDialog / MovieDetailDialog | `tv/fragment/` | Fullscreen Compose detail dialogs (shared phone screens; Phase 3.1b) |
| BrowseItem / ImageCardContent | `tv/BrowseItem.kt`, `tv/view/ImageCardContent.kt` | Typed sealed browse payload + shared now/next card text |

Entry: `TabbedNavigationActivity` → TV `MainActivity` when `DreamDroid.isTV()`. Detail dialogs under `tv/` are opened from phone `VideoOverlayFragment` (not from hub clicks). Leanback browse path removed in **3.1c-iv-f**.

#### ButterKnife (TV)

| File | `@BindView` count |
| --- | --- |
| *(none on TV)* | 0 |
| **Total** | **0** |

TV ButterKnife cleared (#235 details; #237 `TextCardView`). Phone VLC overlay Compose + library drop **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) (2.5c / 2.5d folded).

#### Coupling to phone stack

- Browse hub holds typed `Service` / `ServiceNowNext` / `Movie`; `ExtendedHashMap` only at stream Intent edge (Phase 3.1a). TV detail dialogs host shared Compose screens (Phase 3.1b); overlay still passes hash Event/Movie args.
- SAX handlers still used by typed client (`ServiceListRequestHandler`, `EpgNowNextListRequestHandler` / `EventListRequestHandler`, `MovieListRequestHandler`); `AsyncListLoader` retired in #232
- `Picon`, `IntentFactory` → `VideoActivity` / integrated player
- `DreamDroidTrustManager` + Picasso OkHttp in TV `MainActivity` (app-wide side effects)
- Shared prefs XML (`R.xml.preferences` / `R.xml.profile_preferences`)

Phone Compose detail screens are reused by TV dialog hosts (Phase 3.1b); TV EPG hides action buttons.

#### Risks

- Leanback D-pad browse ≠ phone Material 3; hub needs a TV-first focus model
- Typed `BrowseItem` landed (Phase 3.1a); hub Compose still needs a TV-first focus model
- HTTP Loader paths retired through 2.2r; hub Compose before focus/typing details risks a double rewrite
- Streaming UX tied to Phase 2 VLC/`VideoOverlayFragment` decisions
- Detail dialogs mis-located under `tv/` but driven by phone overlay
- No in-tree TV instrumented coverage for browse/prefs

#### Agreed Phase 3 PR order

Prefer **typed browse data → details → hub → prefs** (not prefs-first; not hub-first without typing):

1. Typed TV browse data — **merged** [#234](https://github.com/sreichholf/dreamDroid/pull/234) (Phase 3.1a)
2. Detail dialogs → Compose — **merged** [#235](https://github.com/sreichholf/dreamDroid/pull/235) (Phase 3.1b)
3. Browse hub → TV Compose / foundational focus — gated on focus dive (**this PR**); typed data already on `main`
4. Leanback prefs → Compose — **merged** [#236](https://github.com/sreichholf/dreamDroid/pull/236) (Phase 3.1d)
5. TV ButterKnife cleared — **merged** [#237](https://github.com/sreichholf/dreamDroid/pull/237); phone library drop **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) (2.5c)

**Safe next after Phase 0 / 3.1c focus dive landed:** Phase 3 cards/prefs (done). Broader Appendix H phone NavHost/widgets/VLC/state **merged**. Phone Phase **2.7b–h** destinations **merged**; **2.7i merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315). **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** (hub tests + Phase 4 notes). **Next:** Phase **4** operator usertests (dialogs **2.1g-ii** parallel).


### Phase 3.1c — TV hub focus dive (this PR; docs only)

**No hub Compose code in this PR.** Hub implementation may start after this dive is on `main`.

#### Hub inventory (current)

| Piece | Path | LOC | Notes |
| --- | --- | --- | --- |
| TV `MainActivity` | `tv/activities/MainActivity.java` | ~100 | Installs Compose hub; TLS + Picasso OkHttp singleton (app-wide) |
| `TvComposeHubHost` | `tv/ui/TvComposeHubHost.kt` | ~680 | NavigationDrawer headers + service/movie rows; typed loads |
| `ImageCardContent` | `tv/view/ImageCardContent.kt` | ~100 | Shared now/next card text for hub service cards |
| `BrowseItem` | `tv/BrowseItem.kt` | ~25 | Sealed `Service` / `Movie` / `Settings` (#234); settings kinds still used by hub |

Behaviors preserved: settings Reload/Preferences/Profile; lazy movie load on row select; stream Intent edge via hash mappers; profile-changed reload. Leanback browse deleted in **3.1c-iv-f** (`leanback` kept for VideoOverlay `HorizontalGridView`).

#### Focus options (chosen)

| Option | Idea | Pros | Cons |
| --- | --- | --- | --- |
| **A. Keep Leanback shell** | Leave `BrowseSupportFragment` rows; Compose only card content / presenters | Keeps D-pad/headers for free; smallest first PR | Still Leanback-centric; hybrid presenters awkward |
| **B. Hybrid** | Leanback headers/rows + Compose item views via `ComposeView` presenters | Incremental; reuses typed `BrowseItem` | Focus handoff Leanback↔Compose is tricky |
| **C. Full Compose hub** | Replace browse with Compose TV rows (`androidx.tv` or foundation focus) | Clean end state; matches phone Compose direction | Greenfield focus model; largest rewrite; needs TV device proof — **scheduled as 3.1c-iv after Phase 2.7** |

**Implementation path taken:** **A → B** (#239 movie text cards, #240 service/settings image cards). Focus rule used: Leanback card keeps focus (`descendantFocusability = FOCUS_BLOCK_DESCENDANTS`; ComposeView not focusable).

#### Hub shell decision (Phase 3.1c-iii — merged [#241](https://github.com/sreichholf/dreamDroid/pull/241); superseded for scheduling by 3.1c-iv)

**Historical decision (#241):** keep option B (Leanback `BrowseSupportFragment` headers/rows + Compose card bodies). Do not start option C / 3.1c-iv *then*.

Why B was right at the time:

- Card Compose under Leanback was proven for both text and image/picon cards without ripping out row/header focus.
- Full Compose hub needed `androidx.tv` (or equivalent), a new focus model, and TV-device proof — higher risk than remaining phone chassis work.
- Phase 3 Leanback UI goals for the hybrid program were met: typed browse, Compose details/prefs/cards, TV ButterKnife cleared.

#### Operator ask — schedule option C (Phase 3.1c-iv — this PR; docs only)

**Decision (2026-09-11):** after phone **Phase 2.7** finishes, the **next** TV program is **option C** — replace the Leanback browse hub with a full Compose TV hub. Hybrid B remains the **shipped** hub until 3.1c-iv implementation lands. Do **not** start hub Compose code in this docs PR; do **not** interleave 3.1c-iv PRs with unfinished 2.7 phone leaves.

**Goal:** TV-first Compose browse (headers/rows/cards/focus) that matches the phone modernization direction, reusing typed `BrowseItem` + existing coroutine loads. Drop Leanback browse presenters/`BrowseSupportFragment` when the Compose hub is feature-complete.

#### Stack

| Piece | Choice | Notes |
| --- | --- | --- |
| TV Compose UI | `androidx.tv:tv-foundation` + `androidx.tv:tv-material` (versions pinned in the deps beachhead PR) | D-pad focus helpers; do not reuse phone Material 3 layouts as the 10-foot chrome |
| Theme | `DreamDroidTheme` + TV typography/spacing as needed | Keep brand color / badge feel from Leanback hub |
| Host | Kotlin Activity/`setContent` or thin Fragment host under existing TV `MainActivity` | Replace `tv_main` → `RootBrowseFragment` path |
| Data | Keep sealed `BrowseItem` + `ServiceListLoad` / `EpgNowNextLoad` / `MovieListLoad` | Hash only at stream Intent edge (unchanged) |
| Details / prefs | Keep existing Compose dialogs + `PreferenceActivity` / `TvSettingsScreen` | Out of hub rewrite except navigation entry points |
| Leanback dep | Drop `BrowseSupportFragment` / card presenters when unused; **keep** `leanback` only if `VideoOverlayFragment` still needs `HorizontalGridView` (retire that separately or replace with Compose later) | `leanback-preference` is already unused after 3.1d — drop when safe |

#### Behaviors to preserve

- Headers on; brand color + badge
- Bouquet/service now/next rows; lazy movie load on row select
- Settings row: Reload / Preferences / Profile
- Stream Intent edge via existing hash mappers; profile-changed reload
- D-pad: horizontal card move, vertical row move, header ↔ content focus handoff equivalent to Leanback browse

#### Proof

- Instrumented Compose tests for hub chrome + card content (phone AVD is fine for composition; D-pad focus assertions where the test harness allows)
- Operator Phase 4 smoke on a real Android TV / box before calling C done
- Cloud Agent: `bash .cursor/cloud/connected-test.sh` for TV Compose tests — no GUI tap loops

#### Hub implementation PR slices (updated)

| Slice | Scope | Status |
| --- | --- | --- |
| 3.1c-i | Compose movie `TextCardView` inside Leanback rows | **merged** [#239](https://github.com/sreichholf/dreamDroid/pull/239) |
| 3.1c-ii | Service image cards → Compose (picon + now/next text) | **merged** [#240](https://github.com/sreichholf/dreamDroid/pull/240) |
| 3.1c-iii | Decide keep Leanback shell vs full Compose hub | **merged** [#241](https://github.com/sreichholf/dreamDroid/pull/241) — kept B *then* |
| 3.1c-iv-a | Docs: schedule option C after Phase 2.7; stack + slices | **merged** [#308](https://github.com/sreichholf/dreamDroid/pull/308) |
| 3.1c-iv-b | Add `androidx.tv` deps; empty Compose TV hub host behind a debug-only switch or replace `tv_main` host with a stub that still shows Leanback until iv-c | **merged** [#336](https://github.com/sreichholf/dreamDroid/pull/336) |
| 3.1c-iv-c | Compose hub chrome: side headers + row list focus model; settings row actions (Reload/Preferences/Profile) | **merged** [#337](https://github.com/sreichholf/dreamDroid/pull/337) |
| 3.1c-iv-d | Service / now-next rows + picon cards (reuse typed loads + card Compose from 3.1c-i/ii where possible) | **merged** [#338](https://github.com/sreichholf/dreamDroid/pull/338) |
| 3.1c-iv-e | Movie location rows + lazy load on row select | **merged** [#339](https://github.com/sreichholf/dreamDroid/pull/339) |
| 3.1c-iv-f | Delete Leanback browse path: `RootBrowseFragment`, `BaseHttpBrowseFragment`, `CardPresenter`, Leanback-only card view wrappers; drop unused leanback-preference | **merged** [#340](https://github.com/sreichholf/dreamDroid/pull/340) |
| 3.1c-iv-g | TV hub instrumented tests + Phase 4 operator box smoke notes | **this PR** — header/loading/error coverage + box smoke checklist |

**Order gate:** Phase **2.7** is complete — **3.1c-iv-b+** is allowed. One PR per slice unless the operator says otherwise.

#### Explicit non-goals of this dive / schedule PR

- No hub Compose / Leanback code changes
- No VLC / `VideoOverlayFragment` rewrite; no Media3
- No separate `tv` Gradle module / product flavor unless a later PR needs it for APK size
- Do not merge `master` into `main`

#### Explicit non-goals of Phase 3.1c-iv implementation (until asked)

- No phone Material 3 drawer/hub reuse on TV
- No Glance / widget work
- No Enigma2 server changes
- No forcing `leanback` library deletion while overlay still imports Leanback widgets

### Phase 1 — Remaining phone typed API (done)

Appendix G phone Compose screens are on `main` through #206 (+ CI #204). Phone ButterKnife dropped with Compose VideoOverlay (**merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) / 2.5c). Phase 1 typed leftovers:

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
| 1b | Drawer Navigation | Through hub **merged** [#254](https://github.com/sreichholf/dreamDroid/pull/254)–[#270](https://github.com/sreichholf/dreamDroid/pull/270). |
| 2a | HTTP / async — Device Info | `DeviceInfoFragment` → `lifecycleScope` + suspend `EnigmaClient.getDeviceInfo()`; delete `GetDeviceInfoTask`. **merged** [#213](https://github.com/sreichholf/dreamDroid/pull/213). Keep `SimpleHttpClient`/`HttpURLConnection`. |
| 2b | HTTP / async — Signal | `SignalFragment` poll → `lifecycleScope` + suspend `EnigmaClient.getSignal()`; delete `GetSignalTask`; keep generation guards. **merged** [#214](https://github.com/sreichholf/dreamDroid/pull/214). |
| 2c | HTTP / async — Current Service | `CurrentServiceFragment` → `lifecycleScope` + suspend `EnigmaClient.getCurrent()`; delete `GetCurrentServiceTask`. **merged** [#215](https://github.com/sreichholf/dreamDroid/pull/215). |
| 2d | HTTP / async — Timers | `TimerListFragment` → `lifecycleScope` + suspend `EnigmaClient.getTimers()`; delete `GetTimerListTask`; keep generation guards. **merged** [#216](https://github.com/sreichholf/dreamDroid/pull/216). |
| 2e | HTTP / async — Movies | `MovieListFragment` → `lifecycleScope` + suspend `EnigmaClient.getMovies()`; delete `GetMovieListTask`. **merged** [#217](https://github.com/sreichholf/dreamDroid/pull/217). |
| 2f | HTTP / async — Hub now/next | `ServiceListPageFragment` → `lifecycleScope` + suspend `EnigmaClient.getEpgNowNext()`; delete `GetEpgNowNextTask`. **merged** [#218](https://github.com/sreichholf/dreamDroid/pull/218). |
| 2g | HTTP / async — Service list | `ZapFragment` + `TimerServicePickFragment` → `lifecycleScope` + suspend `EnigmaClient.getServices()`; delete `GetServiceListTask`. Keep `GetBouquetListTask` for a later slice. **merged** [#219](https://github.com/sreichholf/dreamDroid/pull/219). |
| 2h | HTTP / async — Event list | `ServiceEpgListFragment` + `EpgBouquetFragment` + `EpgSearchFragment` → `lifecycleScope` + suspend `EnigmaClient.getEvents(uri)`; delete `GetEventListTask`. **merged** [#220](https://github.com/sreichholf/dreamDroid/pull/220). |
| 2i | HTTP / async — Bouquet list | `ServiceListPager` + `PickServiceFragment` + `TimerServicePickFragment` → `lifecycleScope` + `EnigmaClient.getServices()` for TV/Radio roots; delete `GetBouquetListTask`. **merged** [#222](https://github.com/sreichholf/dreamDroid/pull/222). |
| 2j | HTTP / async — Locations/tags | `ServiceListPager` + `TimerEditFragment` → `lifecycleScope` + `DreamDroid.loadLocations/Tags`; delete `GetLocationsAndTagsTask`. **merged** [#223](https://github.com/sreichholf/dreamDroid/pull/223). |
| 2k | HTTP / async — SimpleResult mutations | `HttpFragmentHelper` + `NavigationHelper` + `ShareActivity` → `lifecycleScope` + `SimpleResultLoad`; delete `SimpleResultTask`. **merged** [#224](https://github.com/sreichholf/dreamDroid/pull/224). |
| 2l | HTTP / async — Volume/power/sleep | `HttpFragmentHelper` + `NavigationHelper` → `lifecycleScope` + `VolumePowerSleepLoad`; delete `SetVolumeTask`/`SetPowerStateTask`/`SleepTimerTask`. **merged** [#225](https://github.com/sreichholf/dreamDroid/pull/225). |
| 2m | HTTP / async — Profile/detect | `MainActivity` + `ProfileListFragment` → `lifecycleScope` profile check / device detect; delete `CheckProfileTask`/`DetectDevicesTask`. **merged** [#226](https://github.com/sreichholf/dreamDroid/pull/226). |
| 2n | HTTP / async — Retire AsyncTask base | Delete unused `AsyncHttpTaskBase` / `AsyncTaskExecutorService` and empty `asynctask/` package. **merged** [#227](https://github.com/sreichholf/dreamDroid/pull/227). |
| 2o | HTTP / async — Loader chassis stubs | Drop `LoaderCallbacks` from `BaseHttpFragment`/`BaseHttpRecyclerFragment` + stub phone screens; gut `HttpFragmentHelper.reload()`; delete unused `AsyncSimpleLoader`. **merged** [#229](https://github.com/sreichholf/dreamDroid/pull/229). |
| 2p | HTTP / async — ScreenShot bytes | `ScreenShotFragment` → `lifecycleScope` + `ScreenshotLoad` / `Request.getBytes`; delete `AsyncByteLoader`. **merged** [#230](https://github.com/sreichholf/dreamDroid/pull/230). |
| 2q | HTTP / async — VideoOverlay now/next | `VideoOverlayFragment` → `lifecycleScope` + reuse `EpgNowNextLoad` / `serviceNowNextToExtendedHashMap`; drop overlay LoaderCallbacks only. Keep ButterKnife/VLC UI. Keep OkHttp 3.14.9. **merged** [#231](https://github.com/sreichholf/dreamDroid/pull/231). |
| 2r | HTTP / async — Leanback RootBrowse | `RootBrowseFragment` → `lifecycleScope` + reuse `ServiceListLoad` / `EpgNowNextLoad` / `MovieListLoad` (+ locs/tags); drop TV LoaderCallbacks; delete `AsyncListLoader` / `LoaderResult`. Keep Leanback UI + ExtendedHashMap. Keep OkHttp 3.14.9. **merged** [#232](https://github.com/sreichholf/dreamDroid/pull/232). |
| 2 | HTTP / async stack (program) | Replace `HttpURLConnection` + executor/`Loader` with Kotlin coroutines + typed `EnigmaClient` everywhere; keep OkHttp 3.14.9 for Picasso until a dedicated bump. **Phone+TV Loader paths done through 2r.** Follow-ons: typed TV browse (3.1a) / VLC product decision / NavHost / state. |
| 3 | State / rotation | **Complete** [#246](https://github.com/sreichholf/dreamDroid/pull/246)–[#252](https://github.com/sreichholf/dreamDroid/pull/252): fragment Bundles; Evernote/Bridge removed. |
| 4 | Data | Finish Room migration; BackupAgent → Room; drop legacy file after migrate. **merged** [#242](https://github.com/sreichholf/dreamDroid/pull/242). Shrink/remove `DatabaseHelper` later when migrate path is retired. |
| 5 | VLC / streaming | Product decision **merged** [#243](https://github.com/sreichholf/dreamDroid/pull/243) (option A: keep libVLC + Compose overlay). Typed overlay **merged** [#244](https://github.com/sreichholf/dreamDroid/pull/244). Compose overlay + ButterKnife drop **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) (2.5c / 2.5d). |
| 6 | Widgets | Decision **merged** [#271](https://github.com/sreichholf/dreamDroid/pull/271); 2.6b **merged** [#272](https://github.com/sreichholf/dreamDroid/pull/272). Optional 2.6c Compose config. |
| 7 | Fragment shells → Kotlin Compose destinations | **Phase 2.7** ([#304](https://github.com/sreichholf/dreamDroid/pull/304)): convert nested phone Fragments off Java into Kotlin Compose NavHost destinations; not a same-shape Fragment.kt rewrite. |

### Phase 2.5 — VLC / streaming (decision merged [#243](https://github.com/sreichholf/dreamDroid/pull/243); 2.5c merged [#245](https://github.com/sreichholf/dreamDroid/pull/245))

**Decision A** (docs [#243](https://github.com/sreichholf/dreamDroid/pull/243)): keep libVLC; Compose overlay rewrite path. Typed overlay state **merged** [#244](https://github.com/sreichholf/dreamDroid/pull/244). **2.5c merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245): Compose chrome in `VideoOverlayFragment` via `ComposeView`; ButterKnife dependency dropped (2.5d folded). Keep `VideoActivity` + VLCPlayer + gestures + RecyclerView/HorizontalGridView zap list. No Media3.

#### Inventory (current)

| Piece | Path | LOC | Notes |
| --- | --- | --- | --- |
| `VideoActivity` | `activities/VideoActivity.java` | ~455 | Integrated player host: VLC surface + PiP + `ACTION_VIEW` URI; hosts overlay |
| `VideoOverlayFragment` | `fragment/VideoOverlayFragment.java` | ~900 | Controls, bouquet zap, EPG now/next, seek/gestures, detail dialogs; Compose chrome (**merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245)) |
| `VLCPlayer` | `video/VLCPlayer.java` | ~177 | Singleton `libvlc` `MediaPlayer` wrapper |
| `VLCInstance` | `video/VLCInstance.java` | ~61 | Singleton `LibVLC` (`--http-reconnect`) |
| Stream Intent edge | `intents/IntentFactory.java` + `helpers/SimpleHttpClient` stream URL builders | ~170 | Live TS / encoder / recording `/file` URLs; integrated vs external player |
| Dep | `app/build.gradle` | — | `org.videolan.android:libvlc-all:3.7.5` |

Behaviors to preserve if keeping integrated playback: live + recording streams; external-player pref fallback; PiP; bouquet zap + now/next; audio/subtitle tracks; HW-accel / gesture prefs; phone + TV share one `VideoActivity` (television overlay layout variant).

HTTP overlay loads already coroutine (#231). Stream **bytes** are not `EnigmaClient` — URL string via `SimpleHttpClient`. Detail sheets already Compose. No in-tree VLC instrumented tests.

#### Product options

| Option | Idea | Pros | Cons |
| --- | --- | --- | --- |
| **A. Keep VLC + Compose overlay** | Keep `libvlc-all`; rewrite overlay (then thin host) to Compose; drop ButterKnife | Smallest codec risk; unlocks ButterKnife library drop; matches phone Compose | Still ships native VLC; surface/PiP stay View-heavy |
| **B. Replace with Media3 / ExoPlayer** | New player backend + Compose UI | Modern AndroidX stack; possible APK size win | Needs Enigma2 TS/recording proof spike; largest rewrite |
| **C. External-player only** | Delete integrated player; always `ACTION_VIEW` | Deletes ~1.6k LOC + VLC AAR + ButterKnife in one stroke | Loses in-app zap/EPG/PiP; worse TV UX |
| **D. Defer indefinitely** | Leave as-is | Zero risk now; frees NavHost / state / widgets | ButterKnife stuck; VLC stays a modernization island |

#### Decision (Phase 2.5 — merged [#243](https://github.com/sreichholf/dreamDroid/pull/243))

**Decision: option A** — keep libVLC; rewrite `VideoOverlayFragment` to Compose (typed overlay state first), then drop ButterKnife. Do **not** start B/C without a new operator ask.

Why:

- Integrated playback (zap + now/next + PiP) is load-bearing for phone and shared TV player.
- Overlay is the only ButterKnife holdout; Compose rewrite unlocks full library drop without a codec gamble.
- Media3 (**B**) needs a device stream-compat spike before any swap; external-only (**C**) regresses TV/in-app UX.

**Revisit B/C later** only if operator asks, or after a Media3 spike proves Enigma2 live TS + recordings.

#### Proposed implementation PR slices (after this dive)

| Slice | Scope | Non-goals |
| --- | --- | --- |
| 2.5a | Docs decision on `main` | **merged** [#243](https://github.com/sreichholf/dreamDroid/pull/243). No player code |
| 2.5b | Typed overlay state (`ServiceNowNext` / movie) — stop hashing for overlay UI lists | **merged** [#244](https://github.com/sreichholf/dreamDroid/pull/244). No Media3 |
| 2.5c | Compose overlay controls inside existing `VideoActivity` | **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245). Keep libVLC + Intent edge; ButterKnife drop folded |
| 2.5d | Drop ButterKnife binds + dependency (last phone binds) | **done in #245** (folded into 2.5c) |
| 2.5e | Optional: thin Kotlin VLC wrapper + instrumented overlay smoke | **merged** [#296](https://github.com/sreichholf/dreamDroid/pull/296): Kotlin `VLCInstance`/`VLCPlayer`; existing `VideoOverlayScreenTest` remains the overlay smoke. No codec / server work |

#### Explicit non-goals of 2.5c ([#245](https://github.com/sreichholf/dreamDroid/pull/245))

- No `VideoActivity` rewrite; no Media3 / ExoPlayer
- No LazyRow zap list; keep RecyclerView / HorizontalGridView
- No OkHttp 4 bump; do not merge `master` into `main`
- No Enigma2 server / codec / stream-protocol work
- No NavHost / widgets / full Compose hub (3.1c-iv) drive-bys

### Phase 2.6 — Widgets / Virtual Remote home screen (decision [#271](https://github.com/sreichholf/dreamDroid/pull/271); 2.6b [#272](https://github.com/sreichholf/dreamDroid/pull/272))

**Historical Decision A** ([#271](https://github.com/sreichholf/dreamDroid/pull/271)): keep XML `RemoteViews` Virtual Remote widget; defer Compose Glance. **Superseded** by reopen below (Glance is scheduled SOTA). **2.6b merged** [#272](https://github.com/sreichholf/dreamDroid/pull/272): Kotlin coroutines + `goAsync` click path; delete empty zap stub / dead `SyncService` / `HttpIntentService`; prefs delete `isFull` on remove. No OkHttp 4.

#### Inventory (after 2.6b)

| Piece | Path | Notes |
| --- | --- | --- |
| Provider | `appwidget/VirtualRemoteWidgetProvider.java` | `AppWidgetProvider`; builds full / QuickZap `RemoteViews`; RCU via `goAsync` |
| Config | `appwidget/VirtualRemoteWidgetConfiguration.java` | Style + profile pick; Room `ProfileDao`; deletes profile + `isFull` prefs |
| Click path | `appwidget/WidgetRemoteRequest.kt` | Coroutines (`Dispatchers.IO`) + SSL/toast helpers (no Service) |
| Layouts | `virtual_remote_appwidget*.xml` + `merge_*_widget.xml` | Dense RCU grids (~780 LOC merges) |
| Key map | `VirtualRemoteButtons.getRemoteButtons` | Widget RemoteViews binder |

**Flow:** configure → prefs → `updateWidget` → `PendingIntent.getBroadcast(ACTION_RCU)` → provider `onReceive` → `WidgetRemoteRequest` → `SimpleHttpClient` + `RemoteCommandRequestHandler`.

**Action string** `net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_RCU` kept stable for existing PendingIntents.

#### Product options

| Option | Idea | Pros | Cons |
| --- | --- | --- | --- |
| **A. Keep XML RemoteViews** | Leave grids; optional JobIntentService / prefs / config Compose | Zero UX risk; matches dense RCU; small LOC | Deprecated `JobIntentService` remains until optional slice |
| **B. Rewrite to Glance** | New Glance appwidget + layouts | Modern Compose widget stack | Poor fit for dense button grids; new dep; large rewrite; config still separate |
| **C. Delete widgets** | Remove provider + layouts + service | Deletes ~300 LOC Java + XML | Regresses home-screen remote users |
| **D. Defer indefinitely** | No decision | — | Leaves Appendix H open |

#### Historical decision (Phase 2.6 — [#271](https://github.com/sreichholf/dreamDroid/pull/271))

**Then: option A** — keep XML `RemoteViews`; defer Glance. That matched dense-RCU risk and program bandwidth while phone NavHost / HTTP / state landed. **2.6b–c** cleaned the RemoteViews path and Compose config.

#### Decision (Phase 2.6 reopen — this PR; supersedes A’s “not this program”)

**Operator reopen:** widgets are not a permanent RemoteViews keeper. **Target:** Compose Glance (option **B**) as the SOTA widget stack, sized carefully for the dense Virtual Remote grid (possibly hybrid Glance chrome + RemoteViews only where Glance cannot express a control). Do **not** delete widgets (**C**) without a separate ask.

#### Proposed implementation PR slices

| Slice | Scope | Non-goals |
| --- | --- | --- |
| 2.6a | Docs decision on `main` | **merged** [#271](https://github.com/sreichholf/dreamDroid/pull/271). No widget code |
| 2.6b | Kotlin coroutine click path (`WidgetRemoteRequest.kt`); delete zap stub; prefs delete `isFull`; drop dead `SyncService`/`HttpIntentService` | **merged** [#272](https://github.com/sreichholf/dreamDroid/pull/272). No Glance; no OkHttp 4 |
| 2.6c | Optional: Compose config activity | **merged** [#288](https://github.com/sreichholf/dreamDroid/pull/288) (RemoteViews grids then) |
| 2.6d | Glance rewrite | **was deferred**; **reopened** as **2.6e** |
| 2.6e | Glance (or Glance+RemoteViews hybrid) Virtual Remote; retire Java `VirtualRemoteWidgetProvider` when parity holds | Docs reopen is this PR; implementation is a later PR. Prefer after or beside **2.1g-ii**, not blocking TV hub unless asked |

#### Explicit non-goals of this PR

- No Glance dependency; no RemoteViews rewrite
- No OkHttp 4; do not merge `master` into `main`
- No NavHost / Media3 / Leanback drive-bys

### Phase 2.7 — Phone Fragment shells → Kotlin Compose destinations ([#304](https://github.com/sreichholf/dreamDroid/pull/304); docs decision)

**Done for NavHost leaves (2.7b–i).** Phase 2.1 nested each drawer/leaf route as a **Java `Fragment` inside `FragmentContainerView`** (`NestedFragmentDestination` in `PhoneNavHost.kt`). Wave 3 put Compose *bodies* in those Fragments; HTTP/state work left load logic on the Fragment. **2.7b–h** reworked every phone NavHost leaf into a **Kotlin** Compose destination. Phase **2.7i** **merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315) (nested-Fragment helper + phone HTTP/recycler chassis gone). **This PR** deletes post-Compose orphan adapters/helpers/layouts and unused list request/SAX handlers.

**Operator decision (2026-09-11):** rework **all** phone NavHost leaf Fragments into **Kotlin** Compose destinations. Converting off Java **is** part of this phase (new owners are Kotlin per `AGENTS.md`). Do **not** stop at “`FooFragment.java` → `FooFragment.kt` with the same methods” — behavior moves into Compose screens (+ ViewModel / coroutine owners as needed); delete the Fragment type when the route no longer needs it.

#### Goal (both)

| Required | Not enough by itself |
| --- | --- |
| Leave Java: load/UI/state owners become **Kotlin** | Same-shape `Fragment.kt` that still uses `BaseHttp*` + `FragmentContainerView` |
| `PhoneNavHost` `composable` calls the screen directly | Language-only churn with no destination rework |

#### Chassis (current)

| Piece | Path | Notes |
| --- | --- | --- |
| Host | `PhoneNavHostFragment` | Kotlin; owns `NavHostController`, result stacks, drawer navigate |
| Graph | `ui/nav/PhoneNavHost.kt` | All leaves are Kotlin Compose destinations (no nested Fragment helper) |
| Leaves | `ui/**/*Destination.kt` | Compose + coroutine load; Bundle extras via `NavExtras.DATA` |
| Bases | `fragment/abs/BaseFragment.kt`, `activities/abs/BaseActivity.kt` | Titles / multi-pane hooks for host; SSL/Picasso/picon sync shell (**this PR**) |
| Helpers | `FragmentHelper`, `NavigationHelper` | FM / drawer leftovers; volume/snackbar helpers |
| Dialogs | `fragment/dialogs/*` | **Was** Fragment dialogs (**2.1g** A). **Now** Phase **2.1g-ii** SOTA Compose/Navigation dialogs (out of 2.7 code scope; scheduled) |
| Player | `VideoOverlayFragment` + `VideoActivity` | Separate surface; not a `PhoneNavHost` leaf |

#### Inventory — NavHost nested leaves (converted 2.7b–h)

| Route | Fragment shell today (Java) | Compose UI already? |
| --- | --- | --- |
| `DEVICE_INFO` | — (Kotlin `DeviceInfoDestination`) | Yes (`DeviceInfoScreen`) — **2.7b** |
| `SIGNAL` | — (Kotlin `SignalDestination`) | Yes (`SignalScreen`) — **2.7c** |
| `SCREENSHOT` | — (Kotlin `ScreenshotDestination`) | Yes — **2.7c** |
| `CURRENT` | — (Kotlin `CurrentServiceDestination`) | Yes — **2.7c** |
| `ZAP` | — (Kotlin `ZapDestination`) | Yes — **2.7d** |
| `BACKUP` | — (Kotlin `BackupDestination`) | Yes — **2.7c** |
| `PROFILES` | — (Kotlin `ProfilesDestination`) | Yes — **2.7e** |
| `EPG` | — (Kotlin `EpgBouquetDestination`) | Yes — **2.7f** |
| `REMOTE` | — (Kotlin `VirtualRemoteDestination`) | Yes — **2.7d** |
| `SETTINGS` | — (Kotlin `SettingsDestination`) | Yes — **2.7e** |
| `HUB` | — (Kotlin `HubDestination`) | Yes — **2.7h** |
| `SERVICE_EPG` | — (Kotlin `ServiceEpgDestination`) | Yes — **2.7f** |
| `EPG_SEARCH` | — (Kotlin `EpgSearchDestination`) | Yes — **2.7f** |
| `PICK_SERVICE` | — (Kotlin `PickServiceDestination`) | Yes — **2.7f** |
| `PROFILE_EDIT` | — (Kotlin `ProfileEditDestination`) | Yes — **2.7e** |
| `TIMER_EDIT` | — (Kotlin `TimerEditDestination`) | Yes — **2.7g** |
| `TIMER_SERVICE_PICK` | — (Kotlin `TimerServicePickDestination`) | Yes — **2.7g** |

Hub children converted with **2.7h** (`HubServiceListPage` / `HubMovieListPage` / `HubTimerListPage` under `HubDestination`). Java pager + page Fragments deleted in [#314](https://github.com/sreichholf/dreamDroid/pull/314).

#### Agreed approach

**A → Kotlin Compose destination beachhead, then one route (or small cluster) per PR:**

1. **Convert to Kotlin:** move load/refresh/state off the Java Fragment into Kotlin (ViewModel or screen-owned state + existing `*Load` suspend helpers). New types are Kotlin only.
2. Change the `PhoneNavHost` `composable` for that route to call the screen composable directly (no `NestedFragmentDestination` / no child FM commit).
3. Delete the Java Fragment class (and its XML Compose host layout) when nothing else references it.
4. Keep `PhoneNavHostFragment` as the single Fragment host for the detail pane until a later shell cleanup (optional); do not require Activity-only Compose in this phase.
5. Preserve existing instrumented Compose tests; add/adjust route-level tests when the host path changes.

#### Proposed PR slices

| Slice | Scope | Non-goals |
| --- | --- | --- |
| 2.7a | Docs decision on `main` | **merged** [#304](https://github.com/sreichholf/dreamDroid/pull/304). No app code |
| 2.7b | Beachhead: Device Info Kotlin Compose destination; delete `DeviceInfoFragment` | **merged** [#306](https://github.com/sreichholf/dreamDroid/pull/306) |
| 2.7c | Simple leaves: Signal, Screenshot, Current, Backup → Kotlin destinations | Backup + Signal **merged** [#307](https://github.com/sreichholf/dreamDroid/pull/307). Current + Screenshot **merged** [#309](https://github.com/sreichholf/dreamDroid/pull/309) |
| 2.7d | Zap + Virtual Remote → Kotlin destinations | **merged** [#310](https://github.com/sreichholf/dreamDroid/pull/310) |
| 2.7e | Settings + Profiles + Profile edit → Kotlin destinations | **merged** [#311](https://github.com/sreichholf/dreamDroid/pull/311). Dialogs deferred then; reopen under **2.1g-ii** |
| 2.7f | EPG bouquet + Service EPG + EPG search + Pick service → Kotlin destinations | **merged** [#312](https://github.com/sreichholf/dreamDroid/pull/312). No hub |
| 2.7g | Timer edit + Timer service pick → Kotlin destinations | **merged** [#313](https://github.com/sreichholf/dreamDroid/pull/313) |
| 2.7h | Hub (`ServiceListPager` + TV/Radio/Movies/Timers pages) → Kotlin Compose destination | **merged** [#314](https://github.com/sreichholf/dreamDroid/pull/314). No Leanback |
| 2.7i | Chassis cleanup: drop unused `BaseHttp*` / recycler bases / `HttpFragmentHelper` / `NestedFragmentDestination`; `NavExtras.DATA`; TV `LOADER_DEFAULT_ID = 0` | **merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315). Dialog reopen is **2.1g-ii** (separate); no VideoActivity fold |

Order may slip for dependency (e.g. edit forms before pick), but **one PR per slice** unless the operator says otherwise. Prefer Device Info first (already coroutine + Bundle state + simplest UI).

#### Explicit non-goals of Phase 2.7

- No same-shape `Fragment.java` → `Fragment.kt` without also making it a Compose destination
- Leaving leaf logic in Java (conversion to Kotlin is in scope)
- DialogFragment chassis retirement is **Phase 2.1g-ii** (supersedes **2.1g** A); not folded into 2.7i code PRs
- No `VideoActivity` / `VideoOverlayFragment` fold into `PhoneNavHost`
- No Leanback / Glance / Media3 / Enigma2 server work (TV Compose hub is **Phase 3.1c-iv after 2.7**, not interleaved)
- Do not merge `master` into `main`

**Phase 2.7i merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315). **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** (hub tests + Phase 4 notes). **Next:** Phase **4** operator usertests (dialogs **2.1g-ii** parallel).

#### Chassis (after 2.3f)

- Evernote `android-state` and Livefront Bridge **removed** ([#252](https://github.com/sreichholf/dreamDroid/pull/252))
- Fragment/activity bases no longer call `Bridge.save/restore/clear`
- `DreamDroid` no longer initializes Bridge/StateSaver

#### @State inventory

None remaining. Last consumer was `MultiChoiceDialog` (`boolean[]` via Bundle [#252](https://github.com/sreichholf/dreamDroid/pull/252)).

#### Agreed approach (done)

**A → fragment-local save:** migrated one fragment at a time off `@State` onto `onSaveInstanceState`. Bridge kept until last consumer, then deleted with Evernote.

#### Proposed PR slices

| Slice | Scope | Status |
| --- | --- | --- |
| 2.3a | Docs dive | **merged** [#246](https://github.com/sreichholf/dreamDroid/pull/246) |
| 2.3b | DeviceInfoFragment beachhead | **merged** [#247](https://github.com/sreichholf/dreamDroid/pull/247) |
| 2.3c | Simple string/int fragments | **merged** [#249](https://github.com/sreichholf/dreamDroid/pull/249) |
| 2.3d | ScreenShotFragment — no byte Bundle; reload | **merged** [#250](https://github.com/sreichholf/dreamDroid/pull/250) |
| 2.3e | Hash-heavy fragments | **merged** [#251](https://github.com/sreichholf/dreamDroid/pull/251) |
| 2.3f | MultiChoiceDialog + delete Evernote/Bridge | **merged** [#252](https://github.com/sreichholf/dreamDroid/pull/252) |

### Phase 2.1b — NavHost / Compose Navigation (through hub [#270](https://github.com/sreichholf/dreamDroid/pull/270))

#### Chassis (current)

| Piece | Path | Notes |
| --- | --- | --- |
| Launcher | `TabbedNavigationActivity` | Phone → `MainActivity`; TV → Leanback |
| Shell | `MainActivity` + `dualpane.xml` | `DrawerLayout` + `detail_view` + FABs; profile header XML; drawer `ComposeView` |
| Drawer chrome | `ui/drawer/DrawerScreen.kt` | Compose destinations; menu ids from `res/menu/navigation.xml` |
| Router | `NavigationHelper` | Map menu ids → `PhoneNavHost` roots; dialogs + power/sleep/message actions remain; EPG extras separate |
| NavHost beachhead | `PhoneNavHostFragment` + `ui/nav/PhoneNavHost.kt` | **merged** [#255](https://github.com/sreichholf/dreamDroid/pull/255): `navigation-compose` 2.7.7; Device Info leaf nests existing `DeviceInfoFragment` |
| Drawer → NavController | `NavigationHelper` + `PhoneNavHostFragment.navigateToRoute` | **merged** [#256](https://github.com/sreichholf/dreamDroid/pull/256): Device Info re-select uses in-graph navigate when host already shown |
| Signal leaf | `PhoneNavRoutes.SIGNAL` + nested `SignalFragment` | **merged** [#257](https://github.com/sreichholf/dreamDroid/pull/257) |
| Screenshot leaf | `PhoneNavRoutes.SCREENSHOT` + nested `ScreenShotFragment` | **merged** [#258](https://github.com/sreichholf/dreamDroid/pull/258) |
| Current leaf | `PhoneNavRoutes.CURRENT` + nested `CurrentServiceFragment` | **merged** [#260](https://github.com/sreichholf/dreamDroid/pull/260) |
| Zap leaf | `PhoneNavRoutes.ZAP` + nested `ZapFragment` | **merged** [#262](https://github.com/sreichholf/dreamDroid/pull/262) |
| Backup leaf | `PhoneNavRoutes.BACKUP` + nested `BackupFragment` | **merged** [#265](https://github.com/sreichholf/dreamDroid/pull/265) |
| Profiles leaf | `PhoneNavRoutes.PROFILES` + nested `ProfileListFragment` | **merged** [#266](https://github.com/sreichholf/dreamDroid/pull/266) (still clears drawer selection) |
| EPG leaf | `PhoneNavRoutes.EPG` + nested `EpgBouquetFragment` | **merged** [#267](https://github.com/sreichholf/dreamDroid/pull/267) (default bouquet ref/name extras) |
| Remote leaf | `PhoneNavRoutes.REMOTE` + nested `VirtualRemotePagerFragment` | Tablet **merged** [#269](https://github.com/sreichholf/dreamDroid/pull/269); phone **merged** [#277](https://github.com/sreichholf/dreamDroid/pull/277) (2.1h beachhead) |
| Settings leaf | `PhoneNavRoutes.SETTINGS` + nested `MyPreferenceFragment` | **merged** [#279](https://github.com/sreichholf/dreamDroid/pull/279) |
| Profile edit nested | `PhoneNavRoutes.PROFILE_EDIT` | **merged** [#280](https://github.com/sreichholf/dreamDroid/pull/280) |
| Timer edit nested | `PhoneNavRoutes.TIMER_EDIT` | **merged** [#281](https://github.com/sreichholf/dreamDroid/pull/281) |
| Timer service pick nested | `PhoneNavRoutes.TIMER_SERVICE_PICK` | **merged** [#283](https://github.com/sreichholf/dreamDroid/pull/283) (request-code stack) |
| Hub leaf | `PhoneNavRoutes.HUB` + nested `ServiceListPager` | **merged** [#270](https://github.com/sreichholf/dreamDroid/pull/270) |
| Service EPG nested | `PhoneNavRoutes.SERVICE_EPG` typed ref/name | **merged** [#274](https://github.com/sreichholf/dreamDroid/pull/274) (2.1f beachhead) |
| EPG search nested | `PhoneNavRoutes.EPG_SEARCH` typed query | **merged** [#275](https://github.com/sreichholf/dreamDroid/pull/275) |
| Bouquet pick nested | `PhoneNavRoutes.PICK_SERVICE` | **merged** [#276](https://github.com/sreichholf/dreamDroid/pull/276) (host `deliverPickResult`) |
| Host API | `MultiPaneHandler` | `isMultiPane()` always true on `MainActivity` (in-host detail, not master–detail columns) |
| Nested | `FragmentHelper` | FM back stack leftovers; pickers via host / `onActivityResult` |
| Side hosts | `VideoActivity`, `ShareActivity` | Stream / Share. Search → `MainActivity` ([#289](https://github.com/sreichholf/dreamDroid/pull/289) dropped `SimpleFragmentActivity`). Profile/timer edit in-host ([#284](https://github.com/sreichholf/dreamDroid/pull/284)). Settings [#279](https://github.com/sreichholf/dreamDroid/pull/279); remote [#277](https://github.com/sreichholf/dreamDroid/pull/277). |
| Profiles | Profile header XML + first-start | Not in `DrawerScreen` / `navigation.xml`; opens `ProfileListFragment`, clears drawer selection |

#### Agreed approach

**A → hybrid beachhead:** add `navigation-compose`; host `NavHost` inside existing `MainActivity` / `detail_view` via `PhoneNavHostFragment`; migrate leaves (Device Info through hub + remote on phone and tablet). Keep `DrawerLayout` + profile header. Kept DialogFragments initially (**2.1g** A, later superseded by **2.1g-ii**); remaining side activities initially. Do **not** fold `VideoActivity` into the phone graph.

#### Proposed PR slices

| Slice | Scope | Non-goals |
| --- | --- | --- |
| 2.1b | Docs dive | **merged** [#254](https://github.com/sreichholf/dreamDroid/pull/254) |
| 2.1c | Beachhead: `navigation-compose` + Device Info leaf | **merged** [#255](https://github.com/sreichholf/dreamDroid/pull/255) |
| 2.1d | Drawer → `NavController` for migrated roots; stop `clearBackStack`+`showDetails` for those | **merged** [#256](https://github.com/sreichholf/dreamDroid/pull/256) |
| 2.1e | More drawer roots | Through hub **merged** [#257](https://github.com/sreichholf/dreamDroid/pull/257)–[#270](https://github.com/sreichholf/dreamDroid/pull/270) |
| 2.1f | Nested stack (EPG search / service EPG / pick-service) typed routes | **merged** [#274](https://github.com/sreichholf/dreamDroid/pull/274)–[#276](https://github.com/sreichholf/dreamDroid/pull/276) |
| 2.1g | Dialog policy (keep fragment dialogs or promote a few) | **merged** [#278](https://github.com/sreichholf/dreamDroid/pull/278) — decision A *then*; **superseded** by **2.1g-ii** |
| 2.1g-ii | Dialog SOTA reopen (Compose M3 + Navigation `dialog`) | **this PR** (docs); implementation slices below |
| 2.1h | Optional side-activity convergence; retire `NavigationHelper` switch | Phone remote **merged** [#277](https://github.com/sreichholf/dreamDroid/pull/277); Settings **merged** [#279](https://github.com/sreichholf/dreamDroid/pull/279); profile/timer nest **merged** [#280](https://github.com/sreichholf/dreamDroid/pull/280)–[#283](https://github.com/sreichholf/dreamDroid/pull/283); fallbacks drop **merged** [#284](https://github.com/sreichholf/dreamDroid/pull/284). **This PR:** map drawer roots → `PhoneNavHost`; keep dialog/action arms. No OkHttp 4; no widgets; no Media3 |

#### Explicit non-goals after 2.1e ([#270](https://github.com/sreichholf/dreamDroid/pull/270))

- Phone remote side activity retired in 2.1h ([#277](https://github.com/sreichholf/dreamDroid/pull/277))
- Settings side activity retired in 2.1h ([#279](https://github.com/sreichholf/dreamDroid/pull/279))
- Profile edit nested in 2.1h ([#280](https://github.com/sreichholf/dreamDroid/pull/280))
- Timer edit nested in 2.1h ([#281](https://github.com/sreichholf/dreamDroid/pull/281))
- Timer service pick nested in 2.1h ([#283](https://github.com/sreichholf/dreamDroid/pull/283))
- `SimpleToolbarFragmentActivity` removed ([#284](https://github.com/sreichholf/dreamDroid/pull/284))
- Drawer NavHost roots mapped in `NavigationHelper` (**this PR**); dialogs/actions stay
- Dialogs **were** FragmentDialogs / bottom sheets ([#278](https://github.com/sreichholf/dreamDroid/pull/278)); **reopened** as Compose/Navigation under **2.1g-ii**
- No VideoActivity / Glance widgets / TV Leanback / Media3
- No OkHttp 4; do not merge master into main
- Nested typed routes done in 2.1f

### Phase 2.1g / 2.1g-ii — Dialog policy ([#278](https://github.com/sreichholf/dreamDroid/pull/278); reopen this PR)

**2.1g** kept DialogFragments. **2.1g-ii** (this PR) supersedes that: Compose Material 3 + Navigation `dialog` destinations. Docs only here; implementation slices below.

#### Chassis (current)

| Class | Path | Role |
| --- | --- | --- |
| `AbstractDialog` | `fragment/dialogs/AbstractDialog.java` | `DialogFragment`; retain-instance + destroy-view dismiss workaround |
| `ActionDialog` | `fragment/dialogs/ActionDialog.java` | Extends `AbstractDialog`; `finishDialog` → activity `DialogActionListener` |
| `AbstractBottomSheetDialog` / `BottomSheetActionDialog` | `fragment/dialogs/` | Bottom-sheet equivalents |
| `MultiChoiceDialog` | `fragment/dialogs/MultiChoiceDialog.java` | Standalone `DialogFragment`; Bundle `boolean[]` (Phase 2.3f) |

Host: `MainActivity` / `MultiPaneHandler.showDialogFragment`. Drawer tags in `MainActivity.NAVIGATION_DIALOG_TAGS` (`about_dialog`, `powerstate_dialog`, `sendmessage_dialog`, `sleeptimer_dialog`, `sleeptimer_progress_dialog`) route callbacks to `NavigationHelper`.

#### Inventory — drawer / shell

| Dialog | UI | Host |
| --- | --- | --- |
| `AboutComposeDialog` | Compose alert (`ui/about/AboutScreen.kt`) | Drawer About |
| `SendMessageDialog` / `PowerStateDialog` / `SleepTimerDialog` | Compose | Drawer message / power / sleep |
| `ChangelogDialog` | Compose + Markwon | Drawer changelog / `MainActivity.showChangeLog` |
| `ConnectionErrorDialog` | Compose | Profile-check fail |
| `PositiveNegativeDialog` | Material alert | Leave-confirm; also contextual deletes |

#### Inventory — contextual

| Dialog | UI | Typical hosts |
| --- | --- | --- |
| `EpgDetailBottomSheet` / `MovieDetailBottomSheet` | Compose sheets | Hub / EPG / movies / phone `VideoOverlayFragment` |
| `MultiChoiceDialog` | Material multi-choice | Movies tags; timer edit |
| `SimpleChoiceDialog` | Material list | Video overlay track pick |
| `IndeterminateProgress` | Material progress | Profile device search |
| Material date/time pickers | Material lib | EPG bouquet; timer edit |
| Settings in-composition alerts | Compose | Settings side activity |

#### Product options

| Option | Idea | Pros | Cons |
| --- | --- | --- | --- |
| **A. Keep fragment dialogs** | Leave FM dialogs + Compose bodies | Zero UX/back-stack risk; matches Wave 3 Compose dialogs; small surface | `retainInstance` / listener chassis remains |
| **B. Promote several to NavHost** | About / power / sleep / sheets as routes | Uniform navigation API | Fights modal semantics; rewires `ActionDialog` + overlay; large churn |
| **C. Hybrid** | Promote “big” sheets only | Partial consistency | Two systems forever; unclear win |
| **D. Defer indefinitely** | No decision | — | Leaves Appendix H open |

#### Historical decision (Phase 2.1g — [#278](https://github.com/sreichholf/dreamDroid/pull/278))

**Then: option A** — keep fragment dialogs / bottom sheets; do not promote to `PhoneNavHost`. That matched Phase 2.1 sequencing (real leaves + side-activity retirement first). Bodies were already Compose; remaining cost was the DialogFragment / `retainInstance` / `ActionDialog` listener chassis.

#### Decision (Phase 2.1g-ii — this PR; docs only; supersedes A)

**Operator reopen (2026-09-11):** do **not** keep DialogFragments because A previously made sense. Modernize dialogs to the current Compose default.

**Target (state of the art):**

| Kind | Pattern | Notes |
| --- | --- | --- |
| Ephemeral alerts / confirms | Material 3 Compose `AlertDialog` / `BasicAlertDialog` owned by screen or shell Compose state (ViewModel / `mutableState`) | Prefer over FM for leave-confirm, connection error, simple yes/no |
| Sheets | Material 3 `ModalBottomSheet` in composition | EPG / movie detail sheets; not `BottomSheetDialogFragment` |
| Shell / cross-destination modals | Navigation Compose `dialog` destinations on `PhoneNavHost` | About, power, sleep timer, send message, changelog when they need back-stack identity |
| Choice / progress | Compose M3 dialogs or in-UI progress | Replace `MultiChoiceDialog` / `SimpleChoiceDialog` / `IndeterminateProgress` Java FM |
| Results | Typed callbacks / `SavedStateHandle` / shared ViewModel | Retire `ActionDialog` → activity `DialogActionListener` + `retainInstance` |

**Not the target:** `DialogFragment` / `BottomSheetDialogFragment` wrappers around ComposeView, even when bodies are already Compose.

**Why this beats A and the old B/C framing:**

- A froze a View-system modal chassis under a Compose-first app.
- Old **B** (“promote everything to ordinary NavHost composable routes”) fought modal semantics. Navigation’s **`dialog` destination** type is the SOTA middle path: back stack + deep link without pretending a modal is a full leaf screen.
- Old **C** (hybrid forever) is what we have today; the reopen ends the dual system.

#### Implementation PR slices (2.1g-ii)

| Slice | Scope | Proof |
| --- | --- | --- |
| 2.1g-ii-a | Docs reopen (this PR) | Doc review |
| 2.1g-ii-b | Beachhead: one drawer modal (About or connection error) → Compose `AlertDialog` or Nav `dialog` route; delete its DialogFragment wrapper | Instrumented Compose test hosts the new path (composition or NavHost `dialog`), not only naked `setContent` while any View host remains |
| 2.1g-ii-c | Remaining drawer modals (power / sleep / send message / changelog) | Same |
| 2.1g-ii-d | Contextual sheets (EPG / movie detail) → `ModalBottomSheet` | Same |
| 2.1g-ii-e | Multi/simple choice + indeterminate progress → Compose; delete Java FM helpers | Same |
| 2.1g-ii-f | Delete `AbstractDialog` / `ActionDialog` / bottom-sheet FM bases; drop `showDialogFragment` / `NAVIGATION_DIALOG_TAGS` listener routing | Assemble + connected tests green; no FM dialog types left under `fragment/dialogs/` |

One PR per slice unless the operator says otherwise. Prefer **2.1g-ii-b** before large TV hub work if dialog debt blocks Compose testing; otherwise operator may interleave with **3.1c-iv**.

#### Explicit non-goals of this docs PR

- No dialog migration code in this PR
- No Glance / OkHttp Enigma2 / VideoOverlay / TV hub code here (those reopen as separate scheduled slices; see widgets **2.6e** and former keepers backlog)

### Phase 3 — Leanback TV (after Phase 0 dive)

Separate PRs; do not mix with phone shell PRs. Order fixed by Phase 0 dive:

| Order | Slice | Notes |
| --- | --- | --- |
| 3.1a | Typed `BrowseItem` | Sealed Kotlin `Service`/`Movie`/`Settings`; hash only at stream Intent edge; keep Leanback UI. Keep OkHttp 3.14.9. **merged** [#234](https://github.com/sreichholf/dreamDroid/pull/234). |
| 3.1b | TV detail dialogs → Compose | Shared phone detail + `DreamDroidTheme`; hide EPG actions on TV; drop ButterKnife on Epg/Movie detail. Keep OkHttp 3.14.9. **merged** [#235](https://github.com/sreichholf/dreamDroid/pull/235). |
| 3.1c | Browse hub → TV Compose | Focus dive **merged** [#238](https://github.com/sreichholf/dreamDroid/pull/238). Cards **merged** [#239](https://github.com/sreichholf/dreamDroid/pull/239)/[#240](https://github.com/sreichholf/dreamDroid/pull/240). Shell **B** **merged** [#241](https://github.com/sreichholf/dreamDroid/pull/241). Option C scheduled **merged** [#308](https://github.com/sreichholf/dreamDroid/pull/308). **3.1c-iv-b–f** **merged** [#336](https://github.com/sreichholf/dreamDroid/pull/336)–[#340](https://github.com/sreichholf/dreamDroid/pull/340). **This PR:** **3.1c-iv-g** hub tests + Phase 4 notes. |
| 3.1d | Leanback prefs → Compose | **merged** [#236](https://github.com/sreichholf/dreamDroid/pull/236). |
| 3.1e | Drop ButterKnife | TV binds cleared **merged** [#237](https://github.com/sreichholf/dreamDroid/pull/237). Phone library drop **merged** [#245](https://github.com/sreichholf/dreamDroid/pull/245) (2.5c / 2.5d). |

**Phase 3 hybrid Leanback path complete** (typed browse, details, prefs, Compose cards, TV ButterKnife cleared; hub still Leanback shell until **3.1c-iv**). Phase 2.4 Room backup **merged** [#242](https://github.com/sreichholf/dreamDroid/pull/242). Phase 2.5 VLC through Compose overlay **merged** [#243](https://github.com/sreichholf/dreamDroid/pull/243)/[#244](https://github.com/sreichholf/dreamDroid/pull/244)/[#245](https://github.com/sreichholf/dreamDroid/pull/245). Phase 2.3 **complete** [#246](https://github.com/sreichholf/dreamDroid/pull/246)–[#252](https://github.com/sreichholf/dreamDroid/pull/252). Phase 2.1b–e through hub **merged** [#254](https://github.com/sreichholf/dreamDroid/pull/254)–[#270](https://github.com/sreichholf/dreamDroid/pull/270). Phase 2.6a–b **merged** [#271](https://github.com/sreichholf/dreamDroid/pull/271)/[#272](https://github.com/sreichholf/dreamDroid/pull/272). Phase 2.1f nested typed routes **merged** [#274](https://github.com/sreichholf/dreamDroid/pull/274)–[#276](https://github.com/sreichholf/dreamDroid/pull/276). Phase 2.1h phone remote through drawer root map **merged** [#277](https://github.com/sreichholf/dreamDroid/pull/277)–[#285](https://github.com/sreichholf/dreamDroid/pull/285) (dialog policy [#278](https://github.com/sreichholf/dreamDroid/pull/278); nested resume crash [#286](https://github.com/sreichholf/dreamDroid/pull/286); drawer IA [#282](https://github.com/sreichholf/dreamDroid/pull/282)). Hub nested-leaf teardown **merged** [#287](https://github.com/sreichholf/dreamDroid/pull/287). Phase 2.6c Compose widget config **merged** [#288](https://github.com/sreichholf/dreamDroid/pull/288). `SimpleFragmentActivity` retired **merged** [#289](https://github.com/sreichholf/dreamDroid/pull/289). Assert NavHost leaf fallbacks **merged** [#291](https://github.com/sreichholf/dreamDroid/pull/291). Phase 2.5e thin Kotlin VLC wrapper **merged** [#296](https://github.com/sreichholf/dreamDroid/pull/296). OkHttp 4.12 **merged** [#299](https://github.com/sreichholf/dreamDroid/pull/299); libVLC 3.7.5 **merged** [#300](https://github.com/sreichholf/dreamDroid/pull/300). Phase **2.7b–h** phone Compose destinations **merged** [#306](https://github.com/sreichholf/dreamDroid/pull/306)–[#314](https://github.com/sreichholf/dreamDroid/pull/314) (decision [#304](https://github.com/sreichholf/dreamDroid/pull/304)). Phase **2.7i** chassis cleanup **merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315). **#316–#340 merged**. **This PR:** Phase **3.1c-iv-g** (hub tests + Phase 4 notes). **Next:** Phase **4** operator usertests (dialogs **2.1g-ii** parallel).

### Phase 4 — Operator usertests

Phone + TV smoke on real devices / boxes. File bugs; no drive-by refactors.

#### TV hub / Android TV box smoke (after 3.1c-iv)

In-tree gate (Cloud / phone AVD is fine for composition; not a substitute for box D-pad):

```bash
bash .cursor/cloud/connected-test.sh \
  net.reichholf.dreamdroid.tv.ui.ComposeTvHubStubTest,\
net.reichholf.dreamdroid.tv.ui.ComposeTvHubChromeTest,\
net.reichholf.dreamdroid.tv.ui.ComposeTvHubServiceRowTest,\
net.reichholf.dreamdroid.tv.ui.ComposeTvHubMovieRowTest
```

Coverage today: stub chrome, NavigationDrawer headers, settings Reload/Preferences/Profile click, placeholder row, service/movie rows + click, header selection callback, loading/error/movie-loading tags.

**Operator box checklist** (real Android TV / stick / box; file bugs, do not drive-by refactor):

1. Cold start opens **Compose** hub (NavigationDrawer side headers — not Leanback browse).
2. D-pad: move between headers and row content; horizontal move across service/movie cards.
3. Settings header: **Reload** refreshes bouquets; **Preferences** / **Profile** open TV prefs hosts and return to hub.
4. Select a bouquet header → service/now-next cards appear; activate a card → stream Intent / player edge still works.
5. Select a movie location header → list loads lazily; activate a recording → stream file Intent still works.
6. Switch profile (or edit host) → hub reloads against the new box without crashing.
7. Optional: rotate / background / resume once; hub still usable.

Phase **3.1c-iv** code slices **b–f** are on `main`; **iv-g** records the instrumented gate + this checklist. Phase 4 is **operator** proof on hardware before calling option C done.

### Phase 5 — Bugfix pass

One PR per fix or small related cluster. Prefer regressions covered by Compose tests.

### Explicit non-goals until Phases 0–3.1c-iv clear

- Do not merge `master` into `main`.
- Do not rewrite VLC codecs or Enigma2 server side.
- Phase **2.7i** chassis cleanup **merged** [#315](https://github.com/sreichholf/dreamDroid/pull/315). Keepers reopen **merged** [#335](https://github.com/sreichholf/dreamDroid/pull/335). **3.1c-iv-b–f** **merged** [#336](https://github.com/sreichholf/dreamDroid/pull/336)–[#340](https://github.com/sreichholf/dreamDroid/pull/340). **This PR:** **3.1c-iv-g**; next Phase **4** (dialogs **2.1g-ii** remains parallel SOTA backlog).

## Appendix F. Links

[`AGENTS.md`](../AGENTS.md), [`.cursor/skills/verify-dreamdroid/SKILL.md`](../.cursor/skills/verify-dreamdroid/SKILL.md), [`app/build.gradle`](../app/build.gradle), [`NavigationHelper.java`](../app/src/net/reichholf/dreamdroid/fragment/helper/NavigationHelper.java), [`MainActivity.java`](../app/src/net/reichholf/dreamdroid/activities/MainActivity.java), [`URIStore.java`](../app/src/net/reichholf/dreamdroid/helpers/enigma2/URIStore.java), [`themes.xml`](../app/res/values/themes.xml).
