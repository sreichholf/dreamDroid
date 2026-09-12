# Graphical MultiEPG (plan)

**Status:** design only — **no implementation until operator lock-in.**  
**Inspiration:** [Vu+ GraphMultiEPG](https://wiki.vuplus-support.org/index.php?title=GraphMultiEPG) (channel rows × time columns).  
**Target API:** genuine Dreambox WebInterface only (not OpenWebif extensions).  
**Reference (read-only):** [opendreambox/enigma2-plugins `webinterface`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) — we will **not** patch or extend the box webif.

Related history in dreamDroid: 2014 EPG-sync sketches (`aa657268`), unfinished timeline UI removed in [#177](https://github.com/sreichholf/dreamDroid/pull/177), commented `EpgDatabase` dropped in [#293](https://github.com/sreichholf/dreamDroid/pull/293). Unused constant already exists: `URIStore.EPG_MULTI` (`/web/epgmulti?`).

### Decision brief (lock-in target)

| | |
| --- | --- |
| **UI** | Phone Compose grid (rows = channels, bars = programmes); keep list EPG; new drawer **MultiEPG** |
| **Fetch** | Dreambox `/web/epgmulti?bRef=&time=&endTime=` with **unix** window (default 24 h); never unbounded |
| **Sync** | Room cache + ~20–30 min TTL; **one** in-flight request; no idle background sync in v1 |
| **Fallback** | Throttled `/web/epgservice` only if spike shows `epgmulti` missing |
| **Out** | No webif patches; no OpenWebif-only APIs; no TV v1; timer overlays = v1.1 |
| **Next after OK** | Mark **Accepted** → Phase 0 spike on a real Dreambox → then Phase 1+ code |

Reply **defaults OK** (or overrides). Full detail in §§1–8 below.

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| Layout | Channels as rows, programs as timed bars, sticky channel column + time header, “now” line |
| Scope | One bouquet (reuse bouquet picker) |
| Visible span | ~3–4 hours (matches stock web MultiEPG JS default `visibleMinutes = 240`), pan horizontally / vertically |
| Prefetch window | Bounded **+24 h** per fetch (see sync) |
| Density | Time-scale zoom (e.g. 2 / 4 / 6 h visible) |
| Jump | Now, ±1 day; prime-time optional later |
| Tap | Existing EPG detail sheet (timer / zap / search) |
| Timer bars | **Not** in v1 (v1.1) |
| TV / Leanback | Out of scope for v1 |
| List EPG | **Keep** drawer list EPG; add separate **MultiEPG** entry |

Not in v1: STB-style colour-key chrome, AutoTimer, TMDb, clock-vs-bar timer modes from the Vu+ skin.

### Vu+ GraphMultiEPG → phone mapping

| Vu+ / skin behaviour | dreamDroid v1 |
| --- | --- |
| Channel rows × time columns | Same metaphor (Compose grid) |
| Bouquet switch (CH±) | Bouquet picker (reuse existing) |
| Zoom density (1/2/3) | Time-scale zoom (2 / 4 / 6 h visible) |
| Jump now / ±day / prime time | Now + ±day; prime time later |
| OK → channel list EPG | Tap bar → existing detail sheet |
| Green timer create | Via detail sheet actions (already present) |
| Timer bars on grid | **v1.1** |
| Colour remote keys / AutoTimer / TMDb | Out of scope |

### Stock Dreambox web MultiEPG vs our target

Official webif MultiEPG (`tplMultiEpg.htm` + `/web/epgmulti?bRef=…` only):

- Layout: **one column per channel**, events stacked **vertically** by duration height (`item.size = remaining * scale` in `helpers.js` `MultiEPGList`)
- Fetch: **unbounded** `bRef` alone (heavy on the box)
- Interaction: popup ~900×570; tap → detail with add/zap/edit timer, IMDB, RSS search
- Default visible window in JS helpers: **240 minutes** when grouping (`visibleMinutes`)

dreamDroid v1 targets **Vu+ GraphMultiEPG** (channels as **rows**, time as **horizontal** axis) and uses the **same** `/web/epgmulti` API with **bounded** `time`/`endTime`. We are not cloning the stock web column layout.

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
| Visible span | 3–4 h (UI only; data is the chunk) |
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
| **0 — Spike** | Lab/operator: windowed `epgmulti` on real Dreambox WebIf; confirm units + size | Notes pasted into this doc / PR |
| **1 — Client + cache** | `getEvents(…, EPG_MULTI)`, Room schema, TTL, single-flight | Unit + androidTest parse |
| **2 — Grid beachhead** | Nav + bouquet + now line + pan + tap → detail | `MultiEpgScreenTest` via connected-test helper |
| **3 — Polish** | Zoom / day jump / empty+error / pull-refresh | Same |
| **4 — Timers (optional)** | Overlay from `timerlist` | Optional follow-on |

**No Phase 1+ code until Phase 0 spike notes are accepted and this plan is lock-in.**

### Phase 0 spike checklist

On a genuine Dreambox WebIf (no OpenWebif), with bouquet ref `BREF` URL-encoded:

1. Unbounded (stock UI behaviour — expect large):  
   `GET /web/epgmulti?bRef=BREF`
2. Windowed 24 h (intended app behaviour):  
   `GET /web/epgmulti?bRef=BREF&time=T0&endTime=T1`  
   where `T0` = now (unix), `T1 = T0 + 86400`
3. Compare to single channel:  
   `GET /web/epgservice?sRef=SREF&time=T0&endTime=T1`
4. Record: HTTP status, wall time, uncompressed byte size, event count, whether `endTime` is treated as unix end (not minutes).
5. Optional: omit `endTime` but set `time` — note whether result is “from time onward unbounded”.
6. Capture a trimmed XML fixture for androidTest if shape differs from `epgservice.xml`.

---

## 6. Defaults pending operator confirmation

| # | Decision | Proposed default |
| --- | --- | --- |
| 1 | Navigation | Keep list EPG; add drawer **MultiEPG** |
| 2 | Prefetch | +24 h chunks |
| 3 | Cache TTL | ~20–30 min; no idle sync |
| 4 | Fallback | Defer `epgservice` fallback until spike proves need |
| 5 | TV | Phone-only v1 |
| 6 | Timer bars | v1.1 |

Reply with **defaults OK** or a short override list. After lock-in, mark this doc **Accepted** and only then start Phase 0.

### Shared-understanding checklist

- [ ] Operator agrees §6 defaults (or lists overrides)
- [ ] Doc status line set to **Accepted**
- [ ] Phase 0 spike owner / box availability noted
- [ ] Explicit: no feature code before Phase 0 notes land

### Planning Definition of Done

This **planning** goal is complete when all of the following are true:

1. `docs/multiepg.md` describes product, Dreambox `/web/epgmulti` sync model, phases 0–4, and non-goals (present).
2. Operator has explicitly accepted the Decision brief / §6 (or recorded overrides in this doc).
3. Status line is **Accepted** (not merely “design only”).
4. No MultiEPG feature implementation has started before that acceptance.

Until (2)–(3), keep status as design-only and do not open implementation PRs.

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
