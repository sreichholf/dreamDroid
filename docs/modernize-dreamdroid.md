# Modernize dreamDroid phone app

Phone users get a Compose Material 3 remote. TV stays Leanback until a later program. minSdk is 26. Dead deps went first, then the typed Enigma2 client, then phone screens.

Rewrite trunk is **`main`**. `master` is last 1.15 stable. Do not merge `master` or branches cut from `master` into `main`.

Default UI proof is instrumented Compose tests, not `verify-dreamdroid.py` tap loops. See [`AGENTS.md`](../AGENTS.md). AVD `dreamdroid-verify`. JDK 17. Debug package `net.reichholf.dreamdroid.debug`.

## Status as of 2026-09-08

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
| Event typed rows (ServiceEpgList) | this PR (`cursor/epg-typed-list-c88a`) | open | typed `enigma.Event` into `ServiceEpgListFragment`. XML list stays. Bouquet/search/timeline not migrated. |

Wave 1 of this plan is on `main`. It is **not** a finished modernization. See Appendix E.

Wave 2 (operator choice): (1) TV & Movies lists (#170), (4) dead-weight (#172/#175), and (2) Zap typing (#173) are on `main`. This PR continues shape (2) for the service EPG list path. Dead-weight deletes must not drop ButterKnife (still used by frozen Leanback TV).

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
- Hub + rows are Compose on `main` (#169/#170). `ServiceAdapter` remains for `ShareActivity` / video overlay; a hidden RecyclerView may remain for `BaseRecyclerFragment`.
- `ProfileAdapter` remains for `ShareActivity`.
- Leftover `app/res/service_list_pager.xml` stub and `android-retrostreams` were removed in #172. Inflater still uses `R.layout.service_list_pager`.
- Zap still XML. Rows are typed `enigma.Service` on `main` via #173. Bouquet picker still returns `ExtendedHashMap`; Zap converts at the fragment boundary.
- Service EPG list still XML. Rows are typed `enigma.Event` in this PR. Detail sheet / timer create still take `ExtendedHashMap` at the fragment edge. Bouquet/search/timeline EPG remain hash maps.

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
- [ ] `ProfileAdapter` **not** deleted. `ShareActivity` still uses it.

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
- [x] `ServiceListPager.java` hosts Compose header + destination bar. XML `TabLayout` / activity bottom nav gone (`GONE` leftover id ok).
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
- [x] `ZapListMapper` drops markers and converts the bouquet-picker `ExtendedHashMap` at the fragment boundary.
- [x] `Picon` accepts reference + name so Zap does not rebuild hashes for picons.
- [ ] Drawer shell, EPG, current event, Compose Navigation **not** in this PR.

**Verify, unit.**

- [x] `ZapListMapperTest` plus `ServiceParserTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug. Connected tests when a device exists.

## Type Service EPG list rows (pr-epg-event)

**Depends on.** pr-client, #173. **`cursor/epg-typed-list-c88a`, `--base main`.**

**Files.**

- [x] `EventParser` parses `/web/epgservice` into `enigma.Event` (readable fields included).
- [x] `EnigmaClient.getEvents` / `getEventsBlocking` (+ URI param for later bouquet/search).
- [x] `GetEventListTask` → `HttpFragmentHelper.fetchEvents` → `EnigmaClient`.
- [x] `ServiceEpgListFragment` / `ServiceEpgAdapter` hold `List<Event>`. XML `epg_list_item` stays.
- [x] `EpgListMapper.toExtendedHashMap` at detail/timer edge only. Do not rewrite `EpgDetailBottomSheet`.
- [ ] `EpgBouquetFragment` / `EpgSearchFragment` / `EpgTimelineFragment`, drawer shell, Compose Navigation **not** in this PR.

**Verify, unit.**

- [x] `EventParserTest` plus `EpgListMapperTest`. `:app:testGoogleDebugUnitTest`. Assemble googleDebug.

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

### Still Java/XML phone screens (drawer and related)

Compose on `main`: About dialog, Profiles list (not the edit form), TV & Movies **tabs/bar**. The pager pages behind those tabs landed as Compose in #170.

| Surface | Code | Notes |
| --- | --- | --- |
| Channel / bouquet rows | Compose on `main` via #170 | Long-press, picons, popup menu. |
| Movies list | Compose on `main` via #170 | Hub Movies destination. |
| Timer list / edit | Compose list on `main` via #170; `TimerEditFragment` | List Compose; edit stays XML. |
| Profile add/edit | `ProfileEditFragment` | List is Compose; form is XML. Autodiscovery stays Java. |
| Share / pick profile | `ShareActivity` + `ProfileAdapter` | |
| Zap | `ZapFragment`, `ZapAdapter` | XML grid. Rows are typed `enigma.Service` (#173). Picker still `ExtendedHashMap`. |
| Service EPG list | `ServiceEpgListFragment`, `ServiceEpgAdapter` | XML list. Rows are typed `enigma.Event` (this PR). Detail/timer edge still hash. |
| EPG bouquet / search / timeline | `EpgBouquetFragment`, `EpgSearchFragment`, `EpgTimelineFragment`, `EpgAdapter` | Still `ExtendedHashMap`. |
| EPG detail | `EpgDetailBottomSheet` | ButterKnife. |
| Current event | `CurrentServiceFragment` | |
| Virtual remote | `VirtualRemoteFragment`, `VirtualRemotePagerFragment` | |
| Device info | `DeviceInfoFragment` | |
| Signal | `SignalFragment` + vendored gauge lib | |
| Screenshot | `ScreenShotFragment` + PhotoView | |
| Settings | `MyPreferenceFragment`, `legacy-preference-v14` | |
| Backup | `BackupFragment`, `DreamDroidBackupAgent` | Still talks to legacy SQLite. |
| Sleep timer / send message / power / changelog | dialogs | |
| Mediaplayer | removed (#175) | Drawer entry was commented; UI stack deleted. `URIStore.MEDIA_PLAYER_PLAY` kept for `ShareActivity`. |
| Streaming | `VideoActivity`, `VideoOverlayFragment`, VLC | Explicitly out of wave 1. |
| Widget | `appwidget/` | |
| Shell | `MainActivity`, `NavigationHelper`, drawer XML, `BaseFragment` tree, Evernote `@State` + Bridge | |

### Still the old data stack

- Typed `EnigmaClient` exists. Zap list rows load typed `Service`. Service EPG list rows load typed `Event` (this PR). Other lists still use `ExtendedHashMap` through SAX handlers, `AsyncListLoader`, and `HttpFragmentHelper`.
- Enigma2 HTTP is still `HttpURLConnection` + `asynctask/*`. Picons still Picasso + OkHttp 3.14.9.
- Room holds `profile` only. `DatabaseHelper` / `dreamdroid` SQLite still exist for migration and backup.
- ButterKnife (5 files), `legacy-support-v4`, `legacy-preference-v14`. `multiDexEnabled` stays; the `androidx.multidex` install helper is gone (minSdk 26).

### Explicitly frozen

- `app/src/.../tv/` Leanback. Operator picked phone first.
- Rotation via Evernote State + Livefront Bridge.
- VLC playback.

### Sensible wave-2 shapes (pick one, do not do all at once)

1. **Finish TV & Movies** — Compose channel/movie/timer rows, drop `ServiceAdapter` on the pager path, feed typed `Service`/`Movie`/`Timer`. Highest continuity with #169. **Done on `main` as #170.**
2. **Retire `ExtendedHashMap` on one more list path at a time** — EPG, zap, current event. UI can stay XML until the parser boundary is typed. Stops the dual model from rotting. **Zap typed on `main` as #173.** Service EPG list typed in this PR. Bouquet/search/timeline EPG and current event remain.
3. **Replace the drawer shell** — `NavigationHelper` + `MainActivity` in Compose Navigation. Touches every screen. Do this only after a few more destinations are Compose, or it wraps XML forever.
4. **Kill dead weight without UI rewrite** — ButterKnife (not while TV is frozen), preference-v14, unused MediaPlayer entry. `android-retrostreams` and leftover `res/service_list_pager.xml` dropped in **#172**. MediaPlayer UI + MultiDex lib + orphan layouts in **#175** (merged). Still open later: preference-v14, legacy-support-v4, dead `EpgTimelineFragment`, unused menus, GONE bottom nav.
5. **TV program** — Leanback → Compose for TV. Separate program. Do not mix into phone PRs.

Recommended default if the operator just says go: (1) then (4), then (2) one list path at a time; keep (3) and (5) as later programs. Zap typing is on `main` as #173. Service EPG list typing is this PR. Next typed path: bouquet/search EPG or current event, or Profile edit Compose.

## Appendix F. Links

[`AGENTS.md`](../AGENTS.md), [`.cursor/skills/verify-dreamdroid/SKILL.md`](../.cursor/skills/verify-dreamdroid/SKILL.md), [`app/build.gradle`](../app/build.gradle), [`NavigationHelper.java`](../app/src/net/reichholf/dreamdroid/fragment/helper/NavigationHelper.java), [`MainActivity.java`](../app/src/net/reichholf/dreamdroid/activities/MainActivity.java), [`URIStore.java`](../app/src/net/reichholf/dreamdroid/helpers/enigma2/URIStore.java), [`themes.xml`](../app/res/values/themes.xml).
