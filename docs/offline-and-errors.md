# Offline cache and unified errors

**Status:** Accepted (operator lock-in 2026-09-17; review pass on the same day).  
**Trunk:** `main`. **One PR per slice** unless asked otherwise.  
**Related:** MultiEPG cache/TTL already ships ([`docs/multiepg.md`](multiepg.md)). Chrome leftovers stay in [`docs/modernize-dreamdroid.md`](modernize-dreamdroid.md) — do not fold tablet rail, overlay shell, or Glance-only widget into these PRs.

Phone Enigma2 remote. New types are Kotlin. Proof is instrumented Compose tests (`bash .cursor/cloud/connected-test.sh …` on Cloud VMs).

This plan unifies **session connectivity** with a **use-driven Room cache** so a dead box is not an app-wide disaster. Writes stay on the receiver. Reads may come from what the user already opened.

## 1. Locked decisions

| | |
| --- | --- |
| Words | **Online** / **Offline** are **session** labels (drawer / shell). **Unavailable** is a **view** state for one screen that was never cached — not a third drawer chip. Do not say “degraded”. |
| Auth | Failure **kind**, not a session word. Same browse-from-cache as Offline; persistent **in-app** copy that credentials failed. Drawer stays Online/Offline/Checking. Store the kind on `ConnectionStatus` so slice 5 can grey writes for 401 vs Unreachable without an “Auth” chip. |
| Cold start with cache | Skip the **ProfileCheck UI**. Still run `CheckProfile` / first Enigma HTTP in the background (`MainActivity.onProfileChanged`). Success → Online without Recheck. **Do not skip the UI until slice 3** can paint the TV/Radio tab strip from Room (see §7). |
| Cold start with no cache | Keep today’s ProfileCheck gate (Recheck + Profiles). |
| Sync | **Use-driven only.** Cache the view the user opened. No idle full sync, no recursive crawl, no background bouquet/movie walk. |
| EPG fill | `/web/epgmulti` into existing Room chunks when the user opens a **cacheable container** (user-bouquet **tab**, or a **nested folder they opened**). That container’s ref is the `bRef` / `epg_chunk.bouquetRef`. |
| Never cache | **Provider** and **All Services** tabs **and anything drilled under them**. Never `epgmulti` the aggregate `bouquets.tv` / `bouquets.radio` **index** (`servicerefstv[0]` / `servicerefsradio[0]`). |
| Folders as rows | Name + ref + kind only. Never insert `epg_event` with `serviceRef` = the folder. Markers: name + ref if we keep separators; no EPG. `IS_GROUP` is unused; treat as a channel until proven otherwise. A nested directory under a cacheable tab is a valid `epgmulti` `bRef` even when it is `FROM SATELLITES` (not only `userbouquet.*` files). |
| Now / next | **Offline (and stale):** compute from Room with **phone `now()`**, not `epg_event.currentTime`. Event whose `[start, start+duration)` contains now is Now; next later start on that service is Next. Missing overlap → empty Now. **Online hub keeps `/web/epgnownext`** (see [`docs/multiepg.md`](multiepg.md)). |
| Online-only actions | Stay visible, **look disabled**, still tappable. Tap **explains** (“Needs the receiver”). Do not use M3 `enabled = false` (it swallows clicks). |
| Snackbar | Material 3, **low priority only**: Online mutation results (box `statetext` / `BoxRejected`). Never the session “cannot connect” surface. |
| Writes in v1 | Read-only cache. No optimistic zap, no queued remote keys, no offline timer edits. |
| Phone first | TV hub / widget follow after the phone shell works. Widget keeps Toast (no Scaffold). |

## 2. Why this exists

Today every leaf invents error chrome: ProfileCheck gate, drawer chip, list empty text, toast, silent swallow, or `Throwable` class names. HTTP failures collapse to `EnigmaHttpError` plus `errorText: String?`. Timeouts and SSL are often OkHttp `localizedMessage`. MultiEPG already peeks Room and keeps stale rows; the hub still waits up to 20s for in-memory `Profile.cachedDeviceInfo` (`@Ignore`, not Room) in both `HubDestination` and `HubNowPlaying` (`PROFILE_WAIT_MS`) before `loadBouquetList`.

A LAN remote is often used with a sleeping box. If the user has already opened Favourites (or a movie location, or timers), that data should still be readable. Zap, remote, stream, and live meters cannot.

## 3. Session vs view

### 3.1 Session (drawer / shell)

```text
Online     — last successful Enigma HTTP, or CheckProfile finished without a hard error
Offline    — Unreachable (DNS / connect / timeout / SSL) and this profile has use-driven cache
Checking   — progress only, not a stored session
```

**Auth** (HTTP 401): keep cache readable; block writes; persistent copy, not a snackbar. Illegal host / port: no useful cache → ProfileCheck gate.

Drawer shows **Online** / **Offline** (optional last-updated) / **Checking**. Persistent. Not a snackbar. Do not label the chip “OK” or “Auth”.

### 3.2 View: Unavailable vs empty

**Unavailable** = this **profile + container was never written** (user never opened it while we could sync). In-content error + Recheck. Other cached screens still work.

**Empty** = we **did** write this container and it has no playable rows (markers only, etc.). `no_list_item`, not an error, not Unavailable — Online or Offline.

### 3.3 Transitions

- Successful Enigma HTTP → **Online** (mutations enabled). Hub now-playing’s 30s `/web/getcurrent` poll may flip Offline → Online; that stays.
- Unreachable + `hasCache(profile)` → **Offline**.
- Unreachable + no cache → ProfileCheck UI (cold start) or view Unavailable.
- Recheck / background CheckProfile success → **Online** and refresh the visible view.

`hasCache(profile)` is a **stable function later slices extend**, not replace:

1. Until slice 3 ships, **do not skip ProfileCheck UI** even if MultiEPG chunks exist.
2. After slice 3: skip the UI when the TV/Radio **tab strip** exists (the start route can paint). MultiEPG `epg_chunk` **alone** does not skip the gate — that user would land on a tabless hub. They still keep Room EPG for MultiEPG after they get past a live check or a later tab-strip write.
3. Slices 6–7 extend `hasCache` with timer / movie snapshots the same way (can paint that start surface).

Skip **UI** ≠ skip **work**. Always still run `CheckProfile` when a profile is selected so webif feature flags (now/next, POST, sleep timer) and `cachedDeviceInfo` stay current when the box answers.

## 4. Use-driven cache

### 4.1 What “using it” means

Opening a screen (or drilling into a folder / movie location) may write Room for **that container**. Leaving the app, idling, or never opening a tab writes nothing.

MultiEPG’s +24 h prefetch of the bouquet **already on screen** is using it. Prefetching a sibling folder or another bouquet is not.

### 4.2 Cacheable containers (enforcement)

Hub TV/Radio tabs (`buildDedicatedBouquets` in `HubBouquetSelection.kt`):

- If `loadBouquetList` returned children of `servicerefstv[0]` / `servicerefsradio[0]`, those children **are** the user-bouquet tabs (Favourites, Sports, …). Then dedicated **Provider** (`[1]`) and **All Services** (`[2]`) are appended.
- If the HTTP list was **empty**, `buildDedicatedBouquets` falls back to showing the dedicated roots themselves (index 0 = aggregate Bouquets). That fallback tab is **not** a user bouquet. Never `epgmulti` it; do not treat `FROM BOUQUET "bouquets.tv"` as a cacheable EPG container.

`Service.isDirectory` is **too coarse** to be the cache guard: it is true for user-bouquet tabs, nested folders, Provider, satellites, and the aggregate index (`FROM BOUQUET` / `FROM PROVIDERS` / `FROM SATELLITES`).

**Predicate (implement next to `Service`, used by hub, MultiEPG, sync):**

`isCacheableUserBouquetContainer(ref, tabRootRef)` — `ref` is the **container being written** (cacheable user-bouquet **tab**, or a **nested folder the user opened** under that tab). `tabRootRef` is the hub tab (`HubServiceListPage.rootRef`), not a drilled `currentRef`. Profile default bouquet can be a nested ref; MultiEPG/Zap still key ancestry on the tab.

True iff:

- `tabRootRef` is a user-bouquet tab: once slice 3 exists, a row in the Room **tab strip**; before that, a child of the last HTTP `bouquets.tv` / `bouquets.radio` list. **Fail closed** if the strip/list is unknown (under-cache; do not leak Provider).
- `tabRootRef` is **not** equal to dedicated `[1]` / `[2]`, not `FROM PROVIDERS`, not the All Services root, and not the aggregate `[0]` EPG target.

Anything whose **root tab** is Provider or All Services: **no Room**, including nested `FROM SATELLITES` folders. Guard on **tab ancestry**, not only `currentRef`. Nested All Services directories will not equal the All Services root ref.

Also guard **writers**, not only the hub: Zap default bouquet, list EPG, MultiEPG, and the Settings MultiEPG sync test (`ensureChunk` on `defaultBouquetTv`). If default is Provider, **do not persist**. Prefer the guard inside `MultiEpgSync.ensureChunk` (and the roster writer) so every caller is covered.

### 4.3 Folders vs `epgmulti`

| Object | Roster | EPG |
| --- | --- | --- |
| User-bouquet **tab** (itself `isDirectory`, typically `FROM BOUQUET "userbouquet.…"`) | Ordered children | **Yes** — `epgmulti` with `bRef` = tab ref, playable children only |
| Nested **folder** the user opened | Ordered children of the folder | **Yes** — `epgmulti` with `bRef` = **that folder ref** (nested directory, including `FROM SATELLITES` under a cacheable tab). Playable children only |
| Folder **row** in a parent list (not opened) | Name + ref + kind on the parent roster | **No** — do not crawl it |
| Folder as a **channel** | — | **Never** `epg_event.serviceRef` = folder ref |

“Folders never have EPG” means the folder is not a programme. Opening the folder **is** using that container; its playable services get `epgmulti` under **that** `bRef` (same as MultiEPG already keys chunks per selected bouquet). Do not `epgmulti` the parent Favourites list in order to fill an unopened child folder (that would pull services the user did not open, or miss nested services entirely).

### 4.4 Datasets

| Dataset | When written | What is stored | While Offline |
| --- | --- | --- | --- |
| TV/Radio **tab strip** | User opened hub TV or Radio while we could HTTP `loadBouquetList` | Ordered name + ref of user-bouquet tabs (not Provider/All) | Restore those tabs. Still **append** dedicated Provider/All from `R.array` like `buildDedicatedBouquets` — do not persist them; Offline they are greyed + explain. |
| Bouquet / folder roster | User opens that cacheable tab or folder | Ordered name + ref + kind (channel / folder / marker) | Paint the list. Unopened nested folder → Unavailable for that folder. |
| EPG | Same trigger, playable rows only | Existing `epg_event` / `epg_chunk` via `/web/epgmulti` (TTL + 2-day prune already in MultiEPG) | Now/next from timestamps; MultiEPG grid from chunks. |
| Movie **location names** | User opened the Movies tab | Location list | Tabs for locations we have seen. |
| Movie rows | User opened a **dirname** | That location’s movies | Browse names. Play/delete greyed. Other dirs Unavailable. |
| Timer list | User opens the Timer tab | Snapshot of `/web/timerlist` | Read-only. Share this snapshot with MultiEPG timer clocks — do not invent a second store. Add/edit/delete greyed. |
| Profiles / settings / about | Already local | — | Always available. |
| Synced picons on disk | Existing picon sync | Files | Show if present; no FTP while Offline. |

Not cached as blobs: device info live page, signal, screenshot, `/web/getcurrent`. Now-playing **strip** while Offline: hide or empty, do not freeze last headline. Channel rows in a cached user bouquet use Room now/next **while Offline/stale**; Online hub stays on `epgnownext`.

**List EPG** (drawer `/web/epgbouquet`): v1 Offline reads shared `epgmulti` Room chunks for that user bouquet, or Unavailable if never filled. Do not persist `epgbouquet` separately.

**Phone hub Online path:** keep `loadEpgNowNext` / `epgnownext` (`HubServiceListPage`). Roster + `epgmulti` are **additional writers**, not a replacement for Online now/next. Zap/pickers/MultiEPG already use `getservices`; slice 3 must not assume the hub already has that roster.

### 4.5 Hard excludes

Never write Room for:

- Provider tab, `FROM PROVIDERS` paths, All Services tab, nested lists under those tabs.
- Aggregate `bouquets.tv` / `bouquets.radio` index as an EPG `bRef`.
- Folder **rows** the user did not open (no recursive Favourites walk).
- `epg_event.serviceRef` set to a directory/marker.

Online, Provider/All still load over HTTP as today.

### 4.6 Shared sync

Hub slice 4 and MultiEPG must share **one process-wide** `MultiEpgSync` owned by the application (`DreamDroid` / a small holder). Today `MultiEpgDestination` constructs its own; `inFlight` is per instance. Otherwise opening Favourites and MultiEPG doubles `epgmulti` for the same chunk.

## 5. Error and chrome (Material 3)

| Situation | Surface |
| --- | --- |
| Offline session (cache exists) | Persistent shell/drawer status. Cached screens paint. Online-only actions greyed + explain-on-tap. |
| Unavailable (this container never written) | In-content error + Recheck. Not a toast. |
| Empty (written, no playable rows) | `no_list_item`. Reload only if a refetch is meaningful. |
| Cache hit, refresh failed | Keep content. Session **Offline**. Same stale-while-revalidate as MultiEPG. |
| Auth | Persistent in-app copy; cache still readable; writes greyed. |
| User taps a greyed action | Short explanation (dialog or inline). Not a session snackbar. |
| Mutation while Online, box answers | Compose **Snackbar** (`SnackbarHost` on the phone shell): `statetext` or typed failure. Silent success for remote keys stays. |
| Widget RCU | Toast (no Scaffold) — already `WidgetRemoteRequest`. |

Replace the unused View `Snackbar` on `MainActivity` when the Compose host exists. Do **not** replace `IndeterminateProgressHost` here — that is the separate “modal mutation progress” leftover in `modernize-dreamdroid.md`.

`ListEmptyState` already has a `loading` flag; hub passes `refresh.isRefreshing`. **Fix callers** that still do `loading = emptyMessage == getString(R.string.loading)` (`PickServiceScreen`, `EpgBouquetScreen`, `ServiceEpgScreen`). Split **empty vs error** (error color; empty `onSurfaceVariant`). Do not show Reload as the only action on a true empty list unless we mean refetch.

Greyed Online-only (phone v1): zap, virtual remote keys, power, sleep timer **write**, send message, screenshot grab, signal meter, stream, timer save/delete, movie play/delete. Profiles, Settings, cached lists, MultiEPG (cached bouquet), EPG detail **read** stay enabled. Timer create-from-EPG is a write → greyed.

## 6. HTTP types

Replace switching on raw strings with a sealed failure the UI can switch on. Keep `EnigmaHttp` retries (HTTP 405 POST toggle, 412 session) inside the client.

Kinds (names flexible):

- `Unreachable` — DNS (`host_not_found`), connect (`host_unreach`), **timeout**, SSL
- `Auth` — 401
- `Http` — other status
- `Parse` — XML/image did not make sense
- `BoxRejected` — Enigma `state=False` / `statetext`. Produced in SimpleResult handlers / `launchSimpleResultLoad`, **not** inside `EnigmaHttp.fetch`
- `Cancelled` — never shown
- `Unknown`

One `userMessage(context)` for copy. Log the extra detail. MultiEPG `error("epgmulti request failed")` / `t.javaClass.simpleName` (`MultiEpgSync.httpFetch`, `applyBouquetRoster`) maps to this type.

**Timeout vs cancel:** on Android `SocketTimeoutException` extends `InterruptedIOException`. Map **timeout → `Unreachable` first**. Do not treat every `InterruptedIOException` as `Cancelled`.

Load wrappers may still format a string for display; session code must see the **kind**. Slice 1 should keep a temporary `errorText` adapter so it does not rewrite every screen in one PR.

## 7. Implementation slices

One PR per row. Each slice must leave the app shippable. Do not start slice *n+1* by rewriting slice *n*’s API without a migration. `hasCache` only **gains** sources.

| # | Slice | Ships |
| --- | --- | --- |
| 1 | Typed failures | `EnigmaFailure` from `EnigmaHttp` / `EnigmaClient` + SimpleResult `BoxRejected`. Timeout/SSL mapping tests. Adapter so existing `errorText` callers keep compiling. MultiEPG throws → `userMessage`. No new Room. No session chrome. |
| 2 | Session status | Phone-shell `ConnectionStatus` (`Online` / `Offline` + lastUpdated). Drawer copy. **ProfileCheck UI still shows** (no skip yet). Do **not** promise hub Room paint. May drop the 20s wait *only* if CheckProfile is clearly in flight without blocking first paint on empty lists — prefer keeping the wait until slice 3 if dropping it shows a blank hub. Compose `SnackbarHost` **or** defer to a follow-up if the mutation migration explodes; greyed chrome is slice 5. |
| 3 | Tab strip + roster Room | Profile-keyed **tab strip** + ordered rows for opened cacheable containers. Never Provider/All (including nested). Folders = name+ref+kind. Hub/Zap/pickers read roster Offline. **This slice enables ProfileCheck UI skip** when the tab strip exists. Tests: directory vs channel vs marker; Provider/All not inserted; nested All Services folder not inserted; aggregate `bouquets.tv` root is never an `epgmulti` target. |
| 4 | EPG fill from the service list | Opening a cacheable container calls the **shared** `MultiEpgSync.ensureChunk` for **now’s** 24 h chunk. Offline list now/next from Room overlap. **Online hub stays on `epgnownext`.** MultiEPG keeps pan/prefetch. Tests: Favourites tab (directory!) writes chunks; nested folder **does** write child events with `bouquetRef` = folder; folder ref is not a `serviceRef`; Provider / All / index / default-bouquet-is-Provider do **not** write. Update [`docs/multiepg.md`](multiepg.md) Offline/shared-Room row only — do not delete Online `epgnownext`. |
| 5 | Greyed Online-only | Disabled look + explain-on-tap while Offline or Auth. Compose tests (click still works). |
| 6 | Timer snapshot | Write `/web/timerlist` when the Timer tab is shown. Read-only Offline. Same store MultiEPG clocks can read. |
| 7 | Movie snapshots | Location names when Movies tab opened; rows for the **opened** dirname only. Play/delete remain Online-only. |

### Suggested types / files (illustrative)

- `enigma/EnigmaFailure.kt` — sealed type; map in `helpers/EnigmaHttp.kt`; SimpleResult maps `BoxRejected`
- `ui/session/ConnectionStatus.kt` — `Online` / `Offline` plus `lastUpdatedMs?` (avoid clashing with OS “connectivity”)
- Room v5: roster + tab-strip entities (`profileId`, `containerRef`, `position`, `serviceRef`, `name`, `kind`); reuse `EpgDao` for events
- `Service.isCacheableUserBouquetContainer(...)` + tests — **extend** `ServiceDirectoryTest` (it already covers `FROM BOUQUET` on tabs; it does not yet encode cache guards)

Reuse `epg_event.bouquetRef` as the container `bRef` (tab or opened folder). Nested folders are distinct keys.

## 8. Testing

- Unit / androidTest: failure mapping (timeout ≠ cancel), roster guards, now/next overlap vs phone clock, “Provider never inserted”, nested All Services not inserted.
- Compose: ProfileCheck **shown** without cache; **skipped** when tab strip exists (slice 3+); Offline list paints; greyed zap explains; empty vs error.
- Fixtures: `AppDatabase.inMemory` (already used by MultiEPG tests).
- Cloud: `bash .cursor/cloud/connected-test.sh` for touched classes. No adb tap loops, no Cloud screenshot walkthroughs (`AGENTS.md`).

## 9. Out of scope

- Idle / full EPG sync, AutoTimer, OpenWebif-only APIs
- Caching Provider / All Services
- Optimistic offline writes or a command queue
- TV hub offline (follow-up; do not regress Compose TV)
- OS `ConnectivityManager` as a third copy — box unreachable is enough for v1
- Folding tablet `NavigationRail`, video overlay shell, Glance-only widget, or `IndeterminateProgressHost` replacement into these PRs
- Changing MultiEPG zoom/TTL/retention defaults
- Switching Online hub off `epgnownext` unless the operator re-locks that

## 10. Doc touch-ups in later slices

When slice 4 ships, update [`docs/multiepg.md`](multiepg.md): Offline/stale hub now/next may use Room; Online hub now/next stays `epgnownext` unless re-locked. Service-list Favourites fill is an additional `epgmulti` writer, not a second store.
