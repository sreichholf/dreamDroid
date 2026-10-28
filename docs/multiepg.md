# Graphical MultiEPG (plan)

**Status:** **Shipped** on phone (phases 0–4, including timer clocks) and on TV (§9). Defaults accepted by the operator 2026-09-12 (“Defaults look good”). Still open: prime-time jump (polish), live `epgmulti` size measurements (§5), and the `epgservice` fallback (deferred until a box without `epgmulti` shows up).  
**Architecture:** `MultiEpgSync` and Room access sit behind `EpgRepository` with injected dependencies, and the MultiEPG ViewModels follow **Architecture** in [`AGENTS.md`](../AGENTS.md). The fetch, cache, and TTL rules in this doc do not change.  
**Product reference:** on-box **GraphMultiEPG** (`enigma2-plugin-extensions-graphmultiepg` on DreamOS; same family as [Vu+ GraphMultiEPG](https://wiki.vuplus-support.org/index.php?title=GraphMultiEPG)) — channel rows × time columns, prime time, zoom, timer clocks.  
**Target API:** `epgmulti` over the network: `/web/epgmulti` on the Dreambox WebInterface (`DreamboxWebIfApi`), `/api/epgmulti` on OpenWebif (`OpenWebifApi`, see [`openwebif.md`](openwebif.md)); `endTime` is minutes on both. This doc was written when only the Dreambox WebInterface was in scope. On-box GraphMultiEPG reads `eEPGCache` locally.  
**Reference (read-only):** [opendreambox/enigma2-plugins `webinterface`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) — we will **not** patch or extend the box webif. GraphMultiEPG plugin source (behaviour reference): Enigma2 `Plugins/Extensions/GraphMultiEPG/` (e.g. OpenPLi tree; DreamOS ships the same plugin package).

Related history in dreamDroid: 2014 EPG-sync sketches (`aa657268`), unfinished timeline UI removed in [#177](https://github.com/sreichholf/dreamDroid/pull/177), commented `EpgDatabase` dropped in [#293](https://github.com/sreichholf/dreamDroid/pull/293). Unused constant already exists: `URIStore.EPG_MULTI` (`/web/epgmulti?`).

### Decision brief (locked)

| | |
| --- | --- |
| **UI** | Phone Compose grid mirroring on-box GraphMultiEPG (rows = channels, bars = programmes); keep list EPG as the drawer entry; MultiEPG is nested from the hub toolbar/overflow and list EPG Timeline |
| **Fetch** | Dreambox `/web/epgmulti?bRef=&time=&endTime=` — `time` unix seconds, `endTime` **minutes of duration** (default 24 h cache chunk); never unbounded |
| **Visible** | Default **~2 h** (GraphMultiEPG `prev_time_period` default 120, range 60–300); zoom 1 / 2 / 4 / 5 h |
| **Sync** | Room cache; 25 min TTL for today and tomorrow, 6 h after that; 2-day retention prune; **one** in-flight request; no idle background sync in v1 |
| **Fallback** | Throttled `/web/epgservice` only if spike shows `epgmulti` missing |
| **Out** | No webif patches; no OpenWebif-only APIs. Timer overlays from `/web/timerlist` shipped. TV was out of the phone v1 lock-in and shipped later as its own surface (§9). |
| **Next** | Prime-time jump; operator live-size run of `scripts/epgmulti-spike.sh` (§5) |

Full detail in §§1–9 below.

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| Layout | Channels as rows (a service the bouquet lists twice keeps two rows, never deduped), programs as timed bars, sticky channel column + time header, “now” line |
| Scope | One bouquet (reuse bouquet picker) |
| Visible span | Default **~2 h** (GraphMultiEPG `prev_time_period` = 120; limits 60–300), pan horizontally / vertically |
| Prefetch window | Bounded **+24 h** per fetch (see sync) |
| Density | Time-scale zoom **1 / 2 / 4 / 5 h** (within GraphMultiEPG 60–300 min range); **text size Compact / Comfortable** (Settings → Appearance, default Comfortable) |
| Jump | Now, ±1 day; prime time (GraphMultiEPG `prime_time`) in polish |
| Tap | Existing EPG detail sheet (timer / zap / search); OK semantics later: info vs zap |
| Timer bars | Shipped as record clocks on bars (GraphMultiEPG `show_record_clocks`), phase 4 |
| TV | Not part of phone v1; shipped separately as a Compose TV surface (§9) |
| List EPG | **Keep** drawer list EPG (“what’s on at time x”); MultiEPG is nested from the hub toolbar/overflow and list EPG Timeline |

Not in v1: STB colour-key remapping, AutoTimer, TMDb/IMDB from skin mods.

### Phone layout sketch (v1)

```text
┌─ MultiEPG · Favourites ────────── 14:32 ┐
│ [Bouquet ▾]  [Now] [−day] [+day]  [2h▾] │
├────────┬─────15:00─────16:00─────17:00──┤
│ Das 1  │████ News ███│░░░░ Magazin ░░░░│
│ ZDF    │░░ Sport ░░░░│████ Serie █████│
│ RTL    │████ Film ────────────────────│
│ …      │                              │
├────────┴──────────────────────────────┤
│ │ ← now                               │
└───────────────────────────────────────┘
  tap bar → existing EPG detail sheet
```

Sticky channel column + time header; vertical channel scroll; horizontal time pan; density via `[2h▾]` (1/2/4/5 h).

### v1 product edges

| Topic | v1 behaviour |
| --- | --- |
| Picons | When Settings → Use Picons is on and a picon loads for the service, the channel column shows the picon (scaled to the current row height) instead of the service name; otherwise the name is shown |
| Offline / stale | If a Room chunk exists past TTL, still paint it and refresh it behind the toolbar spinner; a failed refresh keeps the rows and the session goes Offline, with no error text in the grid. With no Room chunk and the box unreachable, the load or pan is skipped silently: the grid keeps what it painted and otherwise shows its empty state, the Offline chip is the connection feedback, and nothing keeps spinning. A reachable box whose request fails still shows the error text above the grid. Hub TV/Radio rows while Offline/stale use Room overlap vs phone `now()`; **Online hub now/next stays `/web/epgnownext`**, overlaid on the `/web/getservices` roster so services without events stay visible. Opening a cacheable user-bouquet tab or nested folder is an additional `/web/epgmulti` writer into the same chunks. |
| Orientation | Phone portrait primary; landscape uses same grid with more horizontal hours visible |
| Profile switch | Invalidate MultiEPG UI state; Room rows are `profileId`-keyed so another profile’s cache is not mixed |

### On-box GraphMultiEPG → phone mapping

| GraphMultiEPG (DreamOS plugin) | dreamDroid v1 |
| --- | --- |
| Channel rows × time columns | Same metaphor (Compose grid) |
| Bouquet switch | Bouquet picker (reuse existing) |
| `prev_time_period` 60–300 (default 120) | Zoom 1 / 2 / 4 / 5 h (default 2 h) |
| Now / ±day / prime time | Now + ±day; prime time in polish |
| OK → info / zap / zap+exit | Tap → detail sheet (info); zap from sheet |
| Record clocks on events | Shipped (phase 4) |
| `items_per_page` (default 6) | Vertically scrollable channel list (no hard page size) |
| Reads `eEPGCache` on box | `/web/epgmulti` + Room cache on phone |

### Stock Dreambox web MultiEPG vs our target

Official webif MultiEPG (`tplMultiEpg.htm` + `/web/epgmulti?bRef=…` only):

- Layout: **one column per channel**, events stacked **vertically** by duration height (`item.size = remaining * scale` in `helpers.js` `MultiEPGList`)
- Fetch: **unbounded** `bRef` alone (heavy on the box)
- Interaction: popup ~900×570; tap → detail with add/zap/edit timer, IMDB, RSS search
- Default visible window in JS helpers: **240 minutes** when grouping (`visibleMinutes`)

dreamDroid v1 mirrors **on-box GraphMultiEPG** (horizontal timeline), not the stock web column UI, while still calling bounded `/web/epgmulti`.

---

## 2. Dreambox WebIf — verified EPG surface

Source of truth (opendreambox tree): `webinterface/src/WebComponents/Sources/EPG.py`, `WebScreens.py` (`EpgWebScreen` registers `EpgMulti`), and `web/epgmulti.xml` (`bRef,time,endTime`).

| Endpoint | XML params | Role |
| --- | --- | --- |
| `/web/epgmulti` | `bRef`, `time`, `endTime` | **MultiEPG primary.** All services in bouquet; each queried with `(service, 0, time, endTime)` via `eEPGCache.lookupEvent` |
| `/web/epgbouquet` | `bRef`, `time` | List EPG / “at this instant” (no `endTime`) — keep for existing bouquet list UI |
| `/web/epgservice` | `sRef`, `time`, `endTime` | Single-channel schedule; **fallback** if `epgmulti` fails on very old webif |
| `/web/epgnow`, `epgnext`, `epgnownext` | bouquet / service | **Online** hub now/next overlay on the `getservices` roster. Offline/stale hub now/next reads shared `epgmulti` Room chunks. |
| `/web/epgsearch` | search | **Online** title search, sent only after a pause in typing and one at a time. Room (`epg_event.titleKey` over the shared `epgmulti` chunks) answers first, Offline, and when the receiver fails; it only covers bouquets opened on this device. See [`offline-and-errors.md`](offline-and-errors.md). |

`EPG.getBouquetEPGMulti` is `getEPGofBouquet(param, multi=True)`. With `multi=True`, Dreambox passes **both** `time` and `endTime` into the cache lookup; `epgbouquet` does not. Response XML shape matches existing dreamDroid `Event` / `EventParser` fields (`e2eventid`, `e2eventstart`, `e2eventduration`, …).

### Parameter semantics (Phase 0 confirmed)

- `time` is a **unix timestamp** (start of window). `endTime` is **minutes of duration**, not a unix end (same 4th eEPGCache tuple arg GraphMultiEPG uses). Sending a unix end (~1.7e9) overflows `startTimeQuery` → **0 events**.
- Omitting them (`-1`) is what the stock web UI MultiEPG does (`bRef` only) and can dump a large unbounded schedule — **dreamDroid must always send a bounded window**. `EpgRepository.fetchEpgMulti` converts the unix window to minutes.

### Alternatives groups (`1:134:`)

Both webifs build the `epgmulti` lookup the same way: `eServiceCenter.list(bRef).getContent('S')`, one tuple per entry, one `eEPGCache.lookupEvent`, with each event's service reference taken from `'R'`, the ref that was asked for (Dreambox `EPG.getEPGofBouquet`, OpenWebif `models/services.py` `getBouquetEpg`). The EPG cache resolves a group to its member (OpenPLi `handleGroup`; Dreambox OS is closed source, but its `epgbouquet` and `epgnownext` answer for groups through the same lookup), so a group's programmes come back under the group's own ref.

A group ref names its members' `FROM BOUQUET`, so `Service.isDirectory` is true for it. MultiEPG classifies rows with `isMultiEpgChannel`, which keeps groups (`Service.isAlternatives`, flag 128); filtering on `isDirectory` alone hid them (#609).

OpenWebif’s wiki note that `epgmulti` is “not in Enigma2 WebInterface API” is **incorrect** for this Dreambox tree. When this was written, dreamDroid did not rely on OpenWebif-only endpoints. OpenWebif boxes now go through their own client, which uses `/api/epgmulti` ([`openwebif.md`](openwebif.md)); `epgmultigz` is still unused.

---

## 3. Sync / box-load model

Goal: GraphMultiEPG without hammering the box (historical reason for EPG-sync).

```text
Open MultiEPG(bouquet B)
  → Room hit for (profileId, bRef, day-chunk)?
       yes + fresh TTL → paint from cache
       no  → GET /web/epgmulti?bRef=B&time=T0&endTime=T1
            → upsert Room → paint
  → pan the sliding window → fetch chunks that enter the viewport (dedupe in-flight); only the chunks in view stay in memory, the next one is filled into Room without painting
  → pull-to-refresh → invalidate the chunks in view + refetch
  → leave screen → cancel HTTP; keep Room until TTL expires or the 2-day retention prune
```

| Rule | Default |
| --- | --- |
| Primary API | Windowed `/web/epgmulti` |
| Chunk size | Rolling **24 h** windows (`time` unix + `endTime` minutes for that span), aligned to the viewport’s day/hour floor — not “full EPG dump” |
| Painted windows | Only the chunks overlapping the viewport. The next chunk is warmed into Room, not held for the UI; panning into it reads Room. A wanted chunk that cannot be attached (Offline, or a fetch that fails unreachable) leaves the painted chunks in place, so a pan never blanks the grid |
| Visible span | Default 2 h UI (data chunk still 24 h) |
| Concurrency | **One** in-flight MultiEPG request (no parallel bouquet dumps) |
| TTL | 25 minutes for the chunks of today and tomorrow (UTC days, like the chunks), 6 hours from the day after tomorrow on (`MultiEpgWindows.ttlMs`) |
| Retention | Drop Room events/chunks that ended **≥ 2 days** ago; prune on process start and after each chunk write |
| Idle background sync | **No** in v1 |
| Hub week sync | While a cacheable hub list is on screen, `EpgRepository.syncContainer` fetches its chunks one after another, from now’s chunk up to **7**; it stops at the first chunk without programmes (a programme running past midnight can cost one more request), when the session is Offline, when the page is hidden, or when another list opens. The TV hub syncs the selected bouquet row the same way once its header stayed selected for 1 s |
| Cache store | **Room** EPG entities (do **not** revive orphan `DatabaseHelper.events`) |
| Fallback | Throttled serial `/web/epgservice` per channel only if `epgmulti` unavailable |
| Unbounded `epgmulti` | Forbidden in app code |

```mermaid
sequenceDiagram
  participant UI as MultiEpgScreen
  participant Sync as MultiEpgSync
  participant Room as Room cache
  participant Box as Dreambox /web/epgmulti

  UI->>Sync: open(bouquet, visibleWindow)
  Sync->>Room: lookup chunk(profile, bRef, day)
  alt fresh TTL hit
    Room-->>Sync: events
    Sync-->>UI: paint
  else miss / stale
    Sync->>Box: bRef + time + endTime (bounded)
    Box-->>Sync: e2eventlist XML
    Sync->>Room: upsert events + chunk meta
    Sync-->>UI: paint
  end
  UI->>Sync: pan past chunk edge
  Sync->>Box: adjacent window (single-flight)
  Note over Sync,Box: never omit endTime; cancel on leave
```

One windowed `epgmulti` is still one heavy cache lookup on the box; bounds + TTL + single-flight are the load controls. Progressive row paint is optional polish if responses are large.

---

## 4. App architecture

Target (repositories, ViewModel shape and Compose navigation per [`AGENTS.md`](../AGENTS.md)):

```text
Hub top bar action or list EPG Timeline
  → nested MultiEpg route (type-safe, bouquet ref in the route)
  → MultiEpgDestination → MultiEpgViewModel (hiltViewModel, StateFlow<MultiEpgUiState>)
       └── EpgRepository (single @Singleton MultiEpgSync, ReceiverApi.epgMulti, Room EpgDao)
  → MultiEpgScreen (stateless grid)
       └── tap → EPG detail sheet / timer flow
       └── At this time → list EPG (pop if nested on EPG)
```

`MultiEpgViewModel` and `TvMultiEpgViewModel` are `@HiltViewModel`s over `EpgRepository` and `TimerRepository`. Each owns a `MultiEpgGrid` (the sliding-window loader, `StateFlow<MultiEpgGridState>`) and exposes it inside its `StateFlow` UI state. `EpgRepository` builds the one `MultiEpgSync` per process; the hub service list syncs the list on screen through `EpgRepository.syncContainer`, the TV hub browse fills now’s chunk of each row through `EpgRepository.fillNowChunk`, and the TV hub syncs the selected bouquet through `TvHubBrowse.syncEpg`.

### Code map

| Concern | Current beachhead |
| --- | --- |
| Route table | `ui/nav/PhoneNavRoutes.kt` (`EPG`, `SERVICE_EPG`, `EPG_SEARCH`, `MULTI_EPG`) |
| NavHost | `ui/nav/PhoneNavHost.kt` — nested `composable(MULTI_EPG)` (back returns to hub or list EPG) |
| Drawer | `ui/drawer/DrawerScreen.kt` — list EPG only; MultiEPG is not a drawer peer |
| Drawer → EPG | `ui/nav/NavigationHelper.kt` (`menu_navigation_epg`) — list EPG. MultiEPG is `navigateToMultiEpg` nested from hub/list EPG |
| HTTP | `ReceiverApi.epgMulti(…)`: `DreamboxWebIfApi` sends `/web/epgmulti`, `OpenWebifApi` `/api/epgmulti`. Written as `EnigmaClient.getEvents(params, URIStore.EPG_MULTI)` before the `ReceiverApi` split. |
| Params | `bRef`, `time` and `endTime` (minutes), built inside each client |
| Parse | Reuse `EventParser` / typed `enigma.Event` (XML tags match `epgservice`) |
| Detail / timer | Reuse `EpgEventDetailViewModel` + `EpgEventDetailHost` (`ui/epg/EpgEventDetailSheet.kt`) from bouquet/service EPG |
| Room | `room/AppDatabase.kt` holds profiles plus the EPG event / chunk entities and the offline roster / snapshot tables |
| Sync | `multiepg/MultiEpgSync.kt` (one instance, owned by `data/EpgRepository.kt`), `MultiEpgGrid.kt` (grid loader), `MultiEpgTimerClocks.kt` for record clocks |
| Proof | `bash .cursor/cloud/connected-test.sh …` (not emulator tap loops); see `AGENTS.md` |

### Room sketch (Phase 1 — illustrative)

```text
EpgEventEntity
  profileId, serviceRef, eventId, start, duration, title, description, …
  PK / unique: (profileId, serviceRef, eventId) or (profileId, serviceRef, start)

EpgChunkMeta
  profileId, bouquetRef, windowStart, windowEnd, fetchedAtMs
  → TTL freshness for that chunk
```

`DatabaseHelper` is a read-only importer of leftover `dreamdroid` profile rows. It does not create or write `events` / `services`. **Do not** revive those writers. New cache goes through **Room** (`AppDatabase`).

- Kotlin + Compose + coroutines only (see `AGENTS.md`).
- Grid: custom Compose layout (synced H-scroll time header + V-scroll channels); do not revive deleted `EpgTimelineFragment` / `multiepg*.xml`.

---

## 5. Phases (implementation gates)

| Phase | Deliverable | Gate |
| --- | --- | --- |
| **0 — Spike** | Units from official webif source; live sizes via operator script when a box is available | Notes in this section |
| **1 — Client + cache** | `getEvents(…, EPG_MULTI)`, Room schema, TTL, single-flight | androidTest (`MultiEpgSyncTest`) |
| **2 — Grid beachhead** | Nav + bouquet + now line + pan + tap → detail | `MultiEpgScreenTest` via connected-test helper |
| **3 — Polish** | Zoom / day jump / empty+error / pull-refresh | Same |
| **4 — Timers** | Overlay from `timerlist` | Shipped (record clocks on bars) |

**Phase 0 gate (2026-09-12):** units + XML shape confirmed from [opendreambox `EPG.py` / `epgmulti.xml`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface); live byte/event counts deferred (no Cloud-agent box). Operator script: [`scripts/epgmulti-spike.sh`](../scripts/epgmulti-spike.sh).


Nested **MultiEPG** (hub toolbar/overflow or list EPG Timeline) opens the grid (LazyColumn rows + shared H-scroll; sync on `Dispatchers.IO`/`Default`). Grid chrome matches list EPG (`surfaceVariant` bars, hairline dividers); **now** marker uses `colorScheme.primary`. Rows default to Comfortable (~48.dp); Compact is the original dense 36.dp. Off-screen programme bars are viewport-culled.

**Sync UX (locked):** stale-while-revalidate — paint Room immediately when present; refresh the visible chunk in the background with a small toolbar spinner; the next chunk is filled into Room only, never painted, and a failure there stays silent; pull-to-refresh always forces a refetch; keep stale rows on refresh failure (soft error); bouquet/profile remount replaces the grid immediately. Cache chunks stay 24 h UTC. The painted grid is a **sliding window**: left edge is the **earliest start among programmes overlapping now**; panning right attaches the chunks that enter the viewport from Room (fetching only on a miss), drops the chunks that left it only once every wanted chunk is attached, and warms the following chunk into Room; a wanted chunk that cannot be attached (Offline, or a fetch that fails unreachable) leaves the last painted chunks in place and the pan stays silent, so panning never blanks the grid; any other fetch failure keeps those chunks too and shows its text above the grid; panning back toward now reattaches them from Room. The timeline keeps one chunk of scroll range beyond the painted window so panning can reach the warmed chunk. The toolbar shows the local calendar day under the viewport.

### Phase exit criteria

| Phase | Done when |
| --- | --- |
| **0** | Documented: `endTime` units (unix vs minutes); XML shape vs `epgservice`; live sizes optional until an operator runs the spike script |
| **1** | `EnigmaClient.getEvents(…, URIStore.EPG_MULTI)` returns typed `Event`s; Room chunk upsert + TTL hit/miss; single-flight covered by androidTest |
| **2** | Nested MultiEPG opens from hub/list EPG; bouquet context works; grid shows now-line; pan loads adjacent chunk from cache/network; tap opens existing detail sheet; `MultiEpgScreenTest` green via `.cursor/cloud/connected-test.sh` |
| **3** | Zoom 1/2/4/5 h; ±day + now jump; empty/error/pull-refresh UX; no unbounded requests in code paths |
| **4** | Timer clocks on bars from `timerlist` join |

### Phase 0 notes (source-confirmed; live sizes deferred)

| Question | Finding | Confidence |
| --- | --- | --- |
| `time` units | **Unix seconds** (start of window) | High (source) |
| `endTime` units | **Minutes of duration**, not unix end — webif passes the param as eEPGCache’s 4th tuple arg; GraphMultiEPG uses `time_epoch` minutes the same way. Sending unix end (~1.7e9) overflows `startTimeQuery` → **0 events** (seen on device, ~125 ms empty) | High (eEPGCache + device) |
| Omit `endTime` | Non-multi bouquet path ignores end; **multi** path always passes 4th arg (−1 if omitted) → treat omitted end as unbounded; **app must always send `endTime` as minutes** | High (source) |
| XML shape | `web/epgmulti.xml` event tags match `epgservice` (`e2eventid`…`e2eventservicename`); reuse `EventParser` | High (template) |
| Unbounded vs 2 h vs 24 h sizes | **Not measured here** (no Dreambox on Cloud Agent) | Deferred |

**Operator live checklist** (paste results under this heading):

```bash
BASE=http://dreambox BREF='…' bash scripts/epgmulti-spike.sh
# optional: SREF=… USER=… PASS=…
```

| Probe | status | ms | bytes | events |
| --- | --- | --- | --- | --- |
| unbounded | | | | |
| 2h | | | | |
| 24h | | | | |
| time_only | | | | |
| epgservice_24h | | | | |

Fixture: `app/androidTest/resources/web/epgmulti.xml` (multi-service, same tags as `epgservice.xml`).

---

## 6. Locked defaults (Accepted 2026-09-12)

### Already settled (not re-opened)

| Topic | Settlement |
| --- | --- |
| Box API | Settled as **Dreambox WebInterface** only; superseded by [`openwebif.md`](openwebif.md), which adds an OpenWebif client behind the same `ReceiverApi` |
| Webif source | [opendreambox webinterface](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) is **reference-only** — no patches |
| Primary EPG fetch | `/web/epgmulti` (confirmed in official webif `EPG.py` / `epgmulti.xml`) |
| UX metaphor | On-box **GraphMultiEPG** (horizontal grid), not stock web column MultiEPG |
| Sync | Required: windowed fetches + local cache so the box is not overloaded |
| Process | Plan + shared understanding **before** any feature implementation |

### Operator-confirmed defaults

| # | Decision | Locked default |
| --- | --- | --- |
| 1 | Navigation | Keep list EPG as the drawer entry; MultiEPG is nested from hub toolbar/overflow and list EPG Timeline |
| 2 | Visible window | **2 h** default (GraphMultiEPG); zoom 1 / 2 / 4 / 5 h |
| 3 | Prefetch | +24 h chunk warmed into Room, not painted; the hub syncs 7 days of the list on screen (operator, 2026-10-11) |
| 4 | Cache TTL | 25 min for today and tomorrow, 6 h after that (operator, 2026-10-11); 2-day retention prune; no idle sync |
| 5 | Fallback | Defer `epgservice` fallback until spike proves need |
| 6 | TV | Phone-only v1 (TV shipped later, §9) |
| 7 | Timer bars | v1.1 (`show_record_clocks`) — shipped as phase 4 |

Operator confirmed 2026-09-12 (“Defaults look good”). No overrides.

### Shared-understanding checklist

- [x] Operator agrees §6 defaults (or lists overrides)
- [x] Doc status line set to **Accepted**
- [x] Phase 0: source units confirmed; live sizes deferred to `scripts/epgmulti-spike.sh` (no Cloud-agent box)
- [x] Explicit: no feature code before Phase 0 notes land

### Planning Definition of Done

This **planning** goal is complete when all of the following are true:

1. `docs/multiepg.md` describes product, Dreambox `/web/epgmulti` sync model, phases 0–4, and non-goals — **done**.
2. Operator has explicitly accepted the Decision brief / §6 (or recorded overrides in this doc) — **done** (2026-09-12).
3. Status line is **Accepted** — **done**.
4. No MultiEPG feature implementation started before that acceptance — **held**; phases 0–4 then shipped.

---

## 7. Risks (planning)

| Risk | Mitigation |
| --- | --- |
| Large bouquets + 24 h still heavy on weak boxes | Always bound window; single-flight; TTL; optional later “visible channels first” if spike shows pain |
| `endTime` units differ from OpenWebif docs | Confirmed: Dreambox webif `endTime` = **minutes** (eEPGCache); app converts unix window → minutes in `EpgRepository.fetchEpgMulti` |
| Very old WebIf without `epgmulti` | Spike records it; only then enable throttled `epgservice` fallback |
| Orphan `DatabaseHelper.events` confusion | New cache is Room-only; do not revive old writers |
| Grid jank with hundreds of bars | Virtualize rows; recycle bar composables; paint from Room off main thread |

---

## 8. Explicit non-goals

- Patching / forking Dreambox `webinterface` on the box.
- Depending on OpenWebif-only APIs in the Dreambox path. (OpenWebif boxes have their own client since [`openwebif.md`](openwebif.md).)
- Reintroducing the 2014 N× unbounded `epgservice` full-bouquet sync as the happy path.
- Replacing list EPG in v1 (unless operator chooses otherwise).

---

## 9. TV GraphMultiEPG (requested surface)

Phone v1 (§1, §6 #6) is unchanged: that lock-in was **phone-only**. Phone GraphMultiEPG is nested from the hub toolbar/overflow and list EPG Timeline (not a phone drawer peer). TV is a new requested surface, not a reopen of zoom / TTL / `/web/epgmulti` defaults.

| | |
| --- | --- |
| **Entry** | The hub's **Guide** tab (`HubSection.Guide`) embeds `TvMultiEpgHost` on Live TV's bouquet, full width, without the hub drawer. The toolbar's bouquet chip drops a menu of the TV bouquets (`TvMultiEpgBouquetMenu`): it opens with focus on the current bouquet, which carries a check mark; OK picks, Back closes and focus returns to the chip. A bouquet picked there becomes Live TV's bouquet too. |
| **Grid** | GraphMultiEPG channel rows × time bars; a service listed twice keeps two rows; one D-pad cursor (`selectedKey` = `(serviceRef, occurrence)` + `selectedStartSec`); chrome (Now / ±day / zoom / bouquet) is a separate TV Surface row |
| **Layout** | TV only; the phone grid is unchanged. The Guide draws no panel of its own: rows sit on the hub gradient. Bars and rows are 4 dp apart, bars use the hub cards' radius (`shapes.medium`), no row dividers. All rows share the grid's one time origin; a hole of ≥ 5 min in a row's data (before the first programme, between two, after the last, or a row with none) is a quiet "No information" bar, not selectable. The title (and, on bars ≥ 120 dp when the row height fits it, start – end) sticks to the viewport's left edge for a programme that began earlier; less than 48 dp of bar shows no text (`MultiEpgBarLayout.stickyTextInsetDp` / `textWidthDp` / `gaps`). The cursor bar uses the hub cards' focused colours (primary, with a content-colour border) while the grid has focus, the "selected" container colour otherwise. The now-line runs over the bars but under their text, with a marker on the ruler; the ruler has hour (or half-hour) ticks with labels centred on them. The right and bottom edges fade while there is more to scroll. Toolbar: one rail in the hub cards' container colour holding the bouquet, ‹ day › (no year: the EPG reaches about a week ahead), Now, zoom (one OK cycles 1/2/4/5 h) and the Reload icon (TV has no pull-to-refresh, so it is the only forced refetch); its controls are flat at rest and take the hub top bar's inverse pill when focused, and the loading note sits outside the rail at the right. Channel column: one line with an ellipsis, or the picon when Settings → Use Picons is on. |
| **Session** | Same `MultiEpgGrid` + `EpgRepository.multiEpgSync` Room `/web/epgmulti` cache as phone. No second sync. TV reads `SessionConnectionHolder` for `shouldSkipReceiverHttp` / Offline (same as phone `MultiEpgDestination`); peek Room first (stale-while-revalidate). Do not invent a second MultiEPG store. |
| **Persist** | `MultiEpgPersistGate` (shared with phone). TV `knownTabRefs` comes from `ServiceRepository.userBouquetTabs(tvBouquets())` — never `{ true }`, never the phone tab strip. Fail-closed for empty known tabs, excluded refs, and FROM PROVIDERS. |
| **Detail** | Full-screen `Box` + `EpgDetailBody` / `EpgDetailScreen(showActions=false)`. Stream is hidden unless `ConnectionStatus.allowsStreaming()` (Online). Set timer is a write (`blocksMutations` explains / skips) — still add-by-event-id. Edit timer opens shared `TvTimerEditorHost` prefilled via `Timer.createByEvent`. IMDb stays (not box HTTP). No Similar; no `EpgEventDetailViewModel`. Overlay takes D-pad focus; grid/chrome keys are disabled while it is open. |
| **Proof** | `TvMultiEpgScreenTest` + hub JVM/chrome tests; Cloud Agent uses `.cursor/cloud/connected-test.sh` |
