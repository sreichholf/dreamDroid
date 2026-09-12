# Graphical MultiEPG (plan)

**Status:** design only — **no implementation until operator lock-in.**  
**Inspiration:** [Vu+ GraphMultiEPG](https://wiki.vuplus-support.org/index.php?title=GraphMultiEPG) (channel rows × time columns).  
**Target API:** genuine Dreambox WebInterface only (not OpenWebif extensions).  
**Reference (read-only):** [opendreambox/enigma2-plugins `webinterface`](https://github.com/opendreambox/enigma2-plugins/tree/master/webinterface) — we will **not** patch or extend the box webif.

Related history in dreamDroid: 2014 EPG-sync sketches (`aa657268`), unfinished timeline UI removed in [#177](https://github.com/sreichholf/dreamDroid/pull/177), commented `EpgDatabase` dropped in [#293](https://github.com/sreichholf/dreamDroid/pull/293). Unused constant already exists: `URIStore.EPG_MULTI` (`/web/epgmulti?`).

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| Layout | Channels as rows, programs as timed bars, sticky channel column + time header, “now” line |
| Scope | One bouquet (reuse bouquet picker) |
| Visible span | ~3–4 hours, pan horizontally / vertically |
| Prefetch window | Bounded **+24 h** per fetch (see sync) |
| Density | Time-scale zoom (e.g. 2 / 4 / 6 h visible) |
| Jump | Now, ±1 day; prime-time optional later |
| Tap | Existing EPG detail sheet (timer / zap / search) |
| Timer bars | **Not** in v1 (v1.1) |
| TV / Leanback | Out of scope for v1 |
| List EPG | **Keep** drawer list EPG; add separate **MultiEPG** entry |

Not in v1: STB-style colour-key chrome, AutoTimer, TMDb, clock-vs-bar timer modes from the Vu+ skin.

---

## 2. Dreambox WebIf — verified EPG surface

Source of truth: `webinterface/src/WebComponents/Sources/EPG.py`, `WebScreens.py`, and `web/epg*.xml` in opendreambox.

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
| Chunk size | 24 h (`endTime = time + 86400`) |
| Visible span | 3–4 h (UI only; data is the chunk) |
| Concurrency | **One** in-flight MultiEPG request (no parallel bouquet dumps) |
| TTL | ~20–30 minutes |
| Idle background sync | **No** in v1 |
| Cache store | **Room** EPG entities (do **not** revive orphan `DatabaseHelper.events`) |
| Fallback | Throttled serial `/web/epgservice` per channel only if `epgmulti` unavailable |
| Unbounded `epgmulti` | Forbidden in app code |

One windowed `epgmulti` is still one heavy cache lookup on the box; bounds + TTL + single-flight are the load controls. Progressive row paint is optional polish if responses are large.

---

## 4. App architecture (when implementing)

```text
Drawer MultiEPG
  → PhoneNavRoutes.MULTI_EPG
  → MultiEpgDestination (Compose)
       ├── MultiEpgSync / EnigmaClient.getEpgMulti
       ├── Room EpgDao
       └── MultiEpgScreen (grid)
            └── tap → existing EpgDetail sheet / timer session
```

- Kotlin + Compose + coroutines only (see `AGENTS.md`).
- Reuse: bouquet pick, typed `enigma.Event`, detail/timer session.
- Grid: custom Compose layout (synced H-scroll time header + V-scroll channels); do not revive deleted `EpgTimelineFragment` / `multiepg*.xml`.
- Proof: instrumented Compose tests + `bash .cursor/cloud/connected-test.sh …` (no emulator tap loops).

---

## 5. Phases (implementation gates)

| Phase | Deliverable | Gate |
| --- | --- | --- |
| **0 — Spike** | Call windowed `epgmulti` on a real Dreambox WebIf; confirm `time`/`endTime` units; note payload size for 24 h × typical bouquet; fixture XML if needed | Operator or lab box result recorded in this doc / PR |
| **1 — Client + cache** | `EnigmaClient.getEpgMulti`, Room schema, TTL, single-flight | Unit tests + androidTest parse |
| **2 — Grid beachhead** | Nav + bouquet + now line + pan + tap → detail | `MultiEpgScreenTest` via connected-test helper |
| **3 — Polish** | Zoom / day jump / empty+error / pull-refresh | Same |
| **4 — Timers (optional)** | Overlay from `timerlist` | Optional follow-on |

**No Phase 1+ code until Phase 0 spike notes are accepted and this plan is lock-in.**

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

---

## 7. Explicit non-goals

- Patching / forking Dreambox `webinterface` on the box.
- Depending on OpenWebif-only APIs.
- Reintroducing the 2014 N× unbounded `epgservice` full-bouquet sync as the happy path.
- Replacing list EPG in v1 (unless operator chooses otherwise).
