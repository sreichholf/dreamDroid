# Graphical MultiEPG (plan)

**Status:** **Accepted** (operator lock-in 2026-09-12 — “Defaults look good”).  
**Implementation gate:** no MultiEPG feature code until Phase 0 spike notes land (real Dreambox `/web/epgmulti` sizes + `endTime` units).  
**Product reference:** on-box **GraphMultiEPG** (`enigma2-plugin-extensions-graphmultiepg` on DreamOS; same family as [Vu+ GraphMultiEPG](https://wiki.vuplus-support.org/index.php?title=GraphMultiEPG)) — channel rows × time columns, prime time, zoom, timer clocks.  
**Target API:** genuine Dreambox WebInterface only (not OpenWebif extensions). On-box GraphMultiEPG reads `eEPGCache` locally; dreamDroid must use `/web/epgmulti` over the network.  
**Reference (read-only):** [opendreambox/enigma2-plugins `webinterface`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) — we will **not** patch or extend the box webif. GraphMultiEPG plugin source (behaviour reference): Enigma2 `Plugins/Extensions/GraphMultiEPG/` (e.g. OpenPLi tree; DreamOS ships the same plugin package).

Related history in dreamDroid: 2014 EPG-sync sketches (`aa657268`), unfinished timeline UI removed in [#177](https://github.com/sreichholf/dreamDroid/pull/177), commented `EpgDatabase` dropped in [#293](https://github.com/sreichholf/dreamDroid/pull/293). Unused constant already exists: `URIStore.EPG_MULTI` (`/web/epgmulti?`).

### Decision brief (locked)

| | |
| --- | --- |
| **UI** | Phone Compose grid mirroring on-box GraphMultiEPG (rows = channels, bars = programmes); keep list EPG; new drawer **MultiEPG** |
| **Fetch** | Dreambox `/web/epgmulti?bRef=&time=&endTime=` with **unix** window (default 24 h cache chunk); never unbounded |
| **Visible** | Default **~2 h** (GraphMultiEPG `prev_time_period` default 120, range 60–300); zoom 1 / 2 / 4 / 5 h |
| **Sync** | Room cache + ~20–30 min TTL; **one** in-flight request; no idle background sync in v1 |
| **Fallback** | Throttled `/web/epgservice` only if spike shows `epgmulti` missing |
| **Out** | No webif patches; no OpenWebif-only APIs; no TV v1; timer overlays = v1.1 (GraphMultiEPG `show_record_clocks`) |
| **Next** | Phase 0 spike on a real Dreambox → then Phase 1+ implementation |

Full detail in §§1–8 below.

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| Layout | Channels as rows, programs as timed bars, sticky channel column + time header, “now” line |
| Scope | One bouquet (reuse bouquet picker) |
| Visible span | Default **~2 h** (GraphMultiEPG `prev_time_period` = 120; limits 60–300), pan horizontally / vertically |
| Prefetch window | Bounded **+24 h** per fetch (see sync) |
| Density | Time-scale zoom **1 / 2 / 4 / 5 h** (within GraphMultiEPG 60–300 min range) |
| Jump | Now, ±1 day; prime time (GraphMultiEPG `prime_time`) in polish |
| Tap | Existing EPG detail sheet (timer / zap / search); OK semantics later: info vs zap |
| Timer bars | **Not** in v1 (v1.1 — GraphMultiEPG `show_record_clocks`) |
| TV / Leanback | Out of scope for v1 |
| List EPG | **Keep** drawer list EPG; add separate **MultiEPG** entry |

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
| Picons | Optional in channel column if already available via existing picon helpers; text name always shown (GraphMultiEPG `servicetitle_mode` can be name-only) |
| Offline / stale | If Room chunk exists past TTL, still paint it with a subtle stale/refresh affordance; if no chunk and box unreachable, show existing connection-error pattern (do not spin forever) |
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
| Record clocks on events | **v1.1** |
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
| `/web/epgnow`, `epgnext`, `epgnownext` | bouquet / service | Hub now/next — unchanged |
| `/web/epgsearch` | search | Existing search — unchanged |

`EPG.getBouquetEPGMulti` is `getEPGofBouquet(param, multi=True)`. With `multi=True`, Dreambox passes **both** `time` and `endTime` into the cache lookup; `epgbouquet` does not. Response XML shape matches existing dreamDroid `Event` / `EventParser` fields (`e2eventid`, `e2eventstart`, `e2eventduration`, …).

### Parameter semantics (plan assumption — confirm in Phase 0)

- Treat `time` / `endTime` as **unix timestamps** (same parsing path as `epgservice` in `EPG.getEPGofService`), **not** OpenWebif’s documented “minutes ahead” for its `epgmulti`.
- Omitting them (`-1`) is what the stock web UI MultiEPG does (`bRef` only) and can dump a large unbounded schedule — **dreamDroid must always send a bounded window**.

OpenWebif’s wiki note that `epgmulti` is “not in Enigma2 WebInterface API” is **incorrect** for this Dreambox tree. dreamDroid will not rely on OpenWebif-only endpoints (`epgmultigz`, `/api/…`, etc.).

---

## 3. Sync / box-load model

Goal: GraphMultiEPG without hammering the box (historical reason for EPG-sync).

```text
Open MultiEPG(bouquet B)
  → Room hit for (profileId, bRef, day-chunk)?
       yes + fresh TTL → paint from cache
       no  → GET /web/epgmulti?bRef=B&time=T0&endTime=T1
            → upsert Room → paint
  → pan past chunk edge → fetch adjacent chunk (dedupe in-flight)
  → pull-to-refresh → invalidate chunk + refetch
  → leave screen → cancel HTTP; keep Room until TTL/evict
```

| Rule | Default |
| --- | --- |
| Primary API | Windowed `/web/epgmulti` |
| Chunk size | Rolling **24 h** windows (`endTime = time + 86400`), aligned to the viewport’s day/hour floor — not “full EPG dump” |
| Visible span | Default 2 h UI (data chunk still 24 h) |
| Concurrency | **One** in-flight MultiEPG request (no parallel bouquet dumps) |
| TTL | ~20–30 minutes |
| Idle background sync | **No** in v1 |
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

## 4. App architecture (when implementing)

```text
Drawer MultiEPG
  → PhoneNavRoutes.MULTI_EPG
  → MultiEpgDestination (Compose)
       ├── MultiEpgSync / EnigmaClient.getEvents(…, URIStore.EPG_MULTI)
       ├── Room EpgDao
       └── MultiEpgScreen (grid)
            └── tap → existing EpgDetail sheet / timer session
```

### Existing hooks (no code yet — for implementers)

| Concern | Current beachhead |
| --- | --- |
| Route table | `ui/nav/PhoneNavRoutes.kt` (`EPG`, `SERVICE_EPG`, `EPG_SEARCH`) — add `MULTI_EPG` |
| NavHost | `ui/nav/PhoneNavHost.kt` — register composable |
| Drawer | `ui/drawer/DrawerScreen.kt` + `res/menu/navigation.xml` — new item beside list EPG |
| Drawer → EPG | `fragment/helper/NavigationHelper.kt` (`menu_navigation_epg`) — parallel MultiEPG case |
| HTTP | `enigma/EnigmaClient.getEvents(params, uri)` already takes a URI; pass `URIStore.EPG_MULTI` |
| Params | Same style as `EpgBouquetDestination`: `NameValuePair("bRef", …)` plus `time` / `endTime` |
| Parse | Reuse `EventParser` / typed `enigma.Event` (XML tags match `epgservice`) |
| Detail / timer | Reuse `EpgEventDialogSession` (`ui/epg/EpgEventDialogSession.kt`) from bouquet/service EPG |
| Room today | `room/AppDatabase.kt` is **Profile-only** (v1) — MultiEPG needs a schema bump + entities |
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

`DatabaseHelper` (`DatabaseHelper.kt`) still creates a legacy SQLite `events` table, but MultiEPG sync writers were removed with the old `epgsync` package. **Do not** revive those writers. New cache goes through **Room** (`AppDatabase`) with a schema version bump; optional later cleanup can drop the unused `events` table from `DatabaseHelper`.

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
| **4 — Timers (optional)** | Overlay from `timerlist` | Optional follow-on |

**Phase 0 gate (2026-09-12):** units + XML shape confirmed from [opendreambox `EPG.py` / `epgmulti.xml`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface); live byte/event counts deferred (no Cloud-agent box). Operator script: [`scripts/epgmulti-spike.sh`](../scripts/epgmulti-spike.sh).


**TEMP debug hook (Phase 1 only):** Settings → enable Developer settings → **Run MultiEPG sync test** (debug builds). Remove when Phase 2 grid ships.

### Phase exit criteria

| Phase | Done when |
| --- | --- |
| **0** | Documented: `endTime` units (unix vs minutes); XML shape vs `epgservice`; live sizes optional until an operator runs the spike script |
| **1** | `EnigmaClient.getEvents(…, URIStore.EPG_MULTI)` returns typed `Event`s; Room chunk upsert + TTL hit/miss; single-flight covered by androidTest |
| **2** | Drawer → MultiEPG opens; bouquet context works; grid shows now-line; pan loads adjacent chunk from cache/network; tap opens existing detail sheet; `MultiEpgScreenTest` green via `.cursor/cloud/connected-test.sh` |
| **3** | Zoom 1/2/4/5 h; ±day + now jump; empty/error/pull-refresh UX; no unbounded requests in code paths |
| **4** | Timer clocks (or equivalent) on bars from `timerlist` join; optional |

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
| Box API | Genuine **Dreambox WebInterface** only; OpenWebif-only APIs out of scope |
| Webif source | [opendreambox webinterface](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) is **reference-only** — no patches |
| Primary EPG fetch | `/web/epgmulti` (confirmed in official webif `EPG.py` / `epgmulti.xml`) |
| UX metaphor | On-box **GraphMultiEPG** (horizontal grid), not stock web column MultiEPG |
| Sync | Required: windowed fetches + local cache so the box is not overloaded |
| Process | Plan + shared understanding **before** any feature implementation |

### Operator-confirmed defaults

| # | Decision | Locked default |
| --- | --- | --- |
| 1 | Navigation | Keep list EPG; add drawer **MultiEPG** |
| 2 | Visible window | **2 h** default (GraphMultiEPG); zoom 1 / 2 / 4 / 5 h |
| 3 | Prefetch | +24 h Room chunks |
| 4 | Cache TTL | ~20–30 min; no idle sync |
| 5 | Fallback | Defer `epgservice` fallback until spike proves need |
| 6 | TV | Phone-only v1 |
| 7 | Timer bars | v1.1 (`show_record_clocks`) |

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
4. No MultiEPG feature implementation has started before that acceptance — **still holds**; Phase 0 is the next step (separate work).

---

## 7. Risks (planning)

| Risk | Mitigation |
| --- | --- |
| Large bouquets + 24 h still heavy on weak boxes | Always bound window; single-flight; TTL; optional later “visible channels first” if spike shows pain |
| `endTime` units differ from OpenWebif docs | Phase 0 confirms unix vs minutes on genuine Dreambox WebIf |
| Very old WebIf without `epgmulti` | Spike records it; only then enable throttled `epgservice` fallback |
| Orphan `DatabaseHelper.events` confusion | New cache is Room-only; do not revive old writers |
| Grid jank with hundreds of bars | Virtualize rows; recycle bar composables; paint from Room off main thread |

---

## 8. Explicit non-goals

- Patching / forking Dreambox `webinterface` on the box.
- Depending on OpenWebif-only APIs.
- Reintroducing the 2014 N× unbounded `epgservice` full-bouquet sync as the happy path.
- Replacing list EPG in v1 (unless operator chooses otherwise).
