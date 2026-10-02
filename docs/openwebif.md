# OpenWebif support: plan

**Status:** plan only. Nothing here is implemented.
**Goal:** make dreamDroid work properly on receivers running OpenWebif (OWIF: OpenATV, OpenPLi, OpenViX and others) without bending the Dreambox code path. Each web interface gets its own client. Repositories and screens do not branch on the flavor.

This changes the scope rule in `multiepg.md`, `autotimer.md` and `offline-and-errors.md` ("genuine Dreambox WebInterface only"). Those docs get updated in the phase that ships OWIF support (phase 6).

## 1. Evidence

The comparison is against these sources:
- E2OpenPlugins/e2openplugin-OpenWebif (`OWIF 1.5.2`, `plugin/controllers/`; version history from a full clone)
- opendreambox `enigma2-plugins` (`webinterface`, `autotimer` API 1.6, WebBouquetEditor)
- oe-alliance `enigma2-plugins` (`autotimer` API 1.7, and the old webinterface)
- OpenPLi `enigma2` (`lib/dvb/epgcache.cpp`, for EPG query semantics)
- An adversarial review of an earlier draft of this plan, whose findings are folded in below.

Everything was read in source. No real box was tested. Lines marked *inference* depend on enigma2 core code that is not in those trees.

### 1.1 What breaks on OWIF today

| # | Area | dreamDroid behavior on OWIF | Cause (source) |
|---|------|-----------------------------|----------------|
| 1 | Profile check | Flags "Version < 1.6.5". Now/next, the sleep timer and POST are turned off for the whole process. The hub shows `epgnow` only. | `e2webifversion` is `"OWIF 1.5.2"` (`defaults.py:30`). `CheckProfile.checkVersion` reads `"OWIF 1"` as 0 (`CheckProfile.kt:101-129`). |
| 2 | AutoTimer, bouquet editor | Both are treated as "not installed": the drawer entry is hidden, there is no "Record series", and the bouquet editor is disabled. | OWIF's hook drops `autotimer` and `bouqueteditor` from `/web/external` (`pluginshook.src:10`) because OWIF mounts both itself (`root.py:74-76`). The app looks for them only in `/web/external`. |
| 3 | New timer | Fails with "Missing mandatory parameter 'channelOld'". | OWIF `timerchange` requires `channelOld`/`beginOld`/`endOld` (`web.py:1264`). The app sends them only when editing (`Timer.kt:94-101`). OWIF has `timeradd` (`web.py:1184`). |
| 4 | "What's on at" (`epgbouquet`) | Every event from the given time to the end of the cache, for every service, where the app expects the one event running at that time. | The app sends `time` without `endTime`. OWIF then defaults `endTime` to -1 (`web.py:1440-1463`) and asks for `(ref, 0, time, -1)`. In enigma2's `lookupEvent` that fourth value is minutes: 0 means the single event at `time`, any other value starts a range query, and -1 means "to the end" (OpenPLi `lib/dvb/epgcache.cpp:1696-1735,1789-1796,1395-1398`). The Dreambox's `epgbouquet` asks for `(ref, 0, time)` and ignores `endTime` (`EPG.py:189-191`). On OWIF, sending `endTime=0` gives the Dreambox result. The mapper still picks the event covering `time`, in case an image differs. |
| 5 | Bouquet editor "Add from satellites" | 404. | OWIF's `BQE.py` has no `satelliteslist`. The equivalent is `/api/getsatellites` (`web.py:2203`). |
| 6 | Power toggle | The snackbar reports the opposite state. | OWIF returns the standby state from before the action (`models/control.py:207-235`). DM returns the state after it. |
| 7 | AutoTimer edit (oe-alliance 1.7) | Every partial edit, including the enable switch, turns off "always zap". Zap-only AutoTimers show as "Record". | oe-alliance `edit` resets `always_zap` when the parameter is missing (`AutoTimerResource.py:491-495`). |
| 8 | AutoTimer run / test (oe-alliance) | *Inference:* a run over 120 s times out in the app, and an error during test shows up as a timeout. | There is no `<ignore/>` keep-alive and no `<exception>` reply path. |
| 9 | Bouquet editor result | A name containing `&` or `<` breaks parsing. The app reports failure although the box applied the edit. | OWIF does not XML-escape `e2statetext` (`BQE.py:46-60`). |
| 10 | Signal | dB can show the percent value; BER is rescaled. | `info.py:691,700`. |
| 11 | Movie list | A filter with several tags returns nothing. *Inference:* non-ASCII folders come back empty. | OWIF takes one `tag` and decodes `dirname` as latin-1 (`movies.py:115-118,214`). |
| 12 | Current service | The name is shown double-escaped (`A&amp;E`), IPTV refs come back as `%253a`, and with no current event the start reads as 1970. | `services.py:162,176`. |
| 13 | Encoder stream | A profile set to the Dreambox RTSP encoder will not play. | OWIF boxes transcode over HTTP on port 8002. |

### 1.2 What already works the same

- These endpoints take the same parameters and return the same XML: `vol`, `zap`, `remotecontrol` (key codes and long press), `message`, `mediaplayerplay`, `getservices`, `epgservice`, `epgmulti` (`endTime` is a duration in minutes on both), `timerlist`, `timeraddbyeventid`, `timerdelete`, `timercleanup`, `getlocations`, `gettags`, `deviceinfo`, `/grab`, `/file`, and live streams on port 8001.
- Session handling is harmless. OWIF has no `/web/session`, never answers 412, accepts both GET and POST, and ignores `sessionid`.
- Small differences that need no work: OWIF caps search results at 128 instead of 256, writes `''` where DM writes `"None"`, and translates bouquet editor messages.

### 1.3 Detection

- Detection goes by what the box answers, not by image name. A VTi box can ship OWIF next to the old webinterface; our own `deviceinfo.xml` fixture is a VTi box answering `1.7.4`.
- `/web/deviceinfo` is the cheapest test, and the app already fetches it:
  - `e2webifversion` starts with `"OWIF "` on OWIF.
  - OWIF also emits `e2oeversion`, `e2distroversion` and `e2driverdate`.
- `/api/about` returns JSON on OWIF and 404 on the Dreambox WebInterface. But it runs the same full device scan as `/web/deviceinfo` (`web.py:181,1421`), so `/api/statusinfo` is the cheaper confirmation.
- The `Server` header does not help: both run Twisted, and neither sets it.
- AutoTimer:
  - `/api/about` does not report plugins.
  - OWIF always mounts `/autotimer` and `/bouqueteditor`. Its `ATController` adds the AutoTimer sub-resources only when the plugin imports (`AT.py:140-160`), so `/autotimer/get` answers 404 when the plugin is missing.
  - When the plugin is present, `/autotimer/get` returns its settings, including `api_version` (`AutoTimerResource.py:668` in oe-alliance; 1.6 in opendreambox, 1.7 in oe-alliance).
  - One request therefore gives both whether AutoTimer is installed and which API it speaks.

## 2. Design

### 2.1 What we will not do

- Sprinkle `if (isOwif)` through repositories, parsers or `Timer.kt`.
- Teach the Dreambox parsers OWIF quirks, or make the version check "tolerant" of `"OWIF x"`.
- Block OWIF users the app already serves. Remote control, zap, services and EPG work over `/web` on OWIF today. Until the OWIF client is switched on, OWIF boxes keep today's path; that is the status quo, not a new mixed mode.

### 2.2 Shape

```
ViewModel ──► Repository ──► ReceiverApi (interface, domain types only)
                                ├── DreamboxWebIfApi  (/web XML + WebBouquetEditor, today's EnigmaClient)
                                └── OpenWebifApi      (OWIF /api JSON + OWIF bouquet editor /bouqueteditor/api)
                              AutoTimerApi (one client; requests depend on the plugin's api_version 1.6 / 1.7)
            shared: EnigmaHttp transport (OkHttp, auth, TLS, EnigmaFailure mapping, XML dump)
            shared: domain models (Service, Event, Timer, Movie, CurrentService, …)
```

- **`ReceiverApi`** takes and returns domain types, for example `epgNowNext(bouquet)`, `addTimer(TimerDraft)`, `editTimer(old, new)`, `movies(location, tags)`.
  - `URIStore`, `NameValuePair` and the parameter builders in `helpers/enigma2/{Timer,Movie,PowerState,Message}.kt` stay below this line. Today they leak into repositories and into the widget (`WidgetRemoteRequest.kt:53-56`).
  - Stream and file URLs also come from the API (`ReceiverRepository.kt:81,107`, `MovieRepository.kt:90,101,110`).
- **The bouquet editor folds into each flavor client.** OWIF's bouquet editor takes the Dreambox parameter names. Only result parsing (`{"Result":[bool,text]}`) and the satellite list (`/api/getsatellites`) differ.
- **AutoTimer is one client.** Its requests depend on the plugin's `api_version`; the web interface does not matter. For 1.7 (oe-alliance) it:
  - sends back `always_zap` and the full state on every edit;
  - uses `/autotimer/change` for the enable switch;
  - reads `<e2id>`;
  - accepts `searchType="start"`;
  - treats "run now" as fire-and-forget, followed by a timer list refresh.
- **Plugin presence keeps one source of truth.** `AutoTimerRepository` already keeps per-profile `PluginPresence` with an `Unknown` state (`AutoTimerRepository.kt:93-97,248-254`), and `BouquetEditorRepository` checks on its own (`:68-70`). Both keep that state, but ask `ReceiverApi.pluginPresence(…)` instead of reading `/web/external`.
  - The Dreambox client answers from `/web/external`.
  - The OWIF client answers from `/autotimer/get`, which also returns `api_version`. The bouquet editor is always present on OWIF.
  - Presence stays lazy, as today, so the profile check does not gain a request.
- **`WebIfCapabilities` per profile** (`nowNext`, `sleepTimer`, `postRequest`) replace the process-global `DreamDroid.feature*` flags, which are wrong whenever two profiles differ.
  - Each flavor fills them its own way: the Dreambox client from its version table, the OWIF client with fixed values (OWIF has now/next and the sleep timer, and accepts GET and POST).
  - `EnigmaHttp` reads and updates `postRequest` per profile instead of the global flag (`EnigmaHttp.kt:102-103,153-158`); a 405 still flips it.
  - Before the first check the defaults stay as today: all true (`DreamDroid.kt:300-303`).
- **`ReceiverApiFactory`** chooses the implementation from the detected flavor. It keeps today's rule from `EnigmaClientFactory`: one client per operation, never a singleton (`hilt-migration.md:211`). An unknown flavor gets the Dreambox client, which is what every profile gets today.
- **The OWIF client uses `/api` JSON, not `/web` XML.** The same `P_<x>` handler serves both (`web.py:2490-2495`); all 26 endpoints the client needs return JSON. JSON avoids the XML template's double escaping and `None` handling, and offers calls `/web` lacks: `statusinfo`, `getallservices`, `timeradd`, `getsatellites`.
  - It needs `kotlinx-serialization-json`; only `-core` is on the classpath today.
  - There is no API version field, so the DTOs give every field a default and parse leniently.
- **OWIF client rules**, each with a test:
  - **HTML unescaping per field, not globally.** The current service name is always escaped (`services.py:162`), EPG text only in JSON, and `getservices` names never (`:615`). A blanket unescape would corrupt a name that really contains `&amp;`.
  - **Errors.** Errors come back as HTTP 200 JSON with varying keys (`result`, `state`, the bouquet editor's `Result`). A handler that returns nothing or throws gives an HTML 404/500, so a non-JSON body maps to an `EnigmaFailure`, never a crash.
  - **Signal:** dB only when `snr_db` is a string (`info.py:685-700`).
  - **Current service:** the IPTV ref comes back as `%253a`; with no event, start is 0.
  - **EPG:**
    - `epgbouquet` sends `endTime=0`.
    - `epgnownext` takes `bRef` and is grouped by service reference, not paired by position. OWIF's own "TODO: fix missing now or next" (`web.py:1547`) means a service can lack one of the two.
  - **Power:** the reply gives the state from before the action, so read the state again afterwards.
  - **Timers:**
    - New timers go through `timeradd`; `timerchange` requires the old values.
    - Both reject an empty `name`.
  - **Movies:** one tag; check `dirname` encoding against a real capture.
  - **Sleep timer:** can answer 404 when it returns nothing (`timers.py:912-935`); `minutes` is sometimes a string, and on some images it is the configured value, not the time left.
  - **Services for the bouquet editor:** need `hidden=1`, or hidden services disappear.
  - **`/file`:** a missing file is HTTP 200 with a text body. Check `Content-Type` before saving a download.
  - **Auth:** with auth off, OWIF answers clients outside the LAN with 403 "IP address rejected" (`httpserver.py:388`). Map that to its own failure that tells the user to enable OWIF authentication.

### 2.3 Options considered

| Option | Verdict |
|--------|---------|
| A. Patch the DM path: strip the version prefix, probe for plugins, add `timeradd` on OWIF, and so on | Rejected. It is the compromise asked to avoid, and items 4, 6, 7, 10 and 12 still need flavor branches in parsers and repositories. |
| B. A second client that speaks `/web` XML and reuses the DM parsers | Rejected. The shared parsers would have to absorb OWIF's template quirks (the double escape in item 12, `''` for None), and the client would miss `statusinfo`, `getallservices` and `timeradd`. |
| **C. A second client on `/api` JSON, with its own DTOs and mapping into shared domain models** | **Chosen.** It keeps a clean split, the Dreambox parsers stay untouched, and it uses the API OWIF itself builds on. Cost: JSON fixtures and DTOs, and no schema version to rely on. |

### 2.4 Client selection

- **Where.** `ReceiverDetector` belongs to neither client; it is the only code that knows both formats. The profile check calls it, and the app already runs that check at start, on each profile switch, and in the setup assistant (`ShellViewModel.kt:136,143`, `SetupAssistantViewModel`).
- **Probe.** The detector reads `/web/deviceinfo`, which the check fetches today (`CheckProfile.kt:65`).
  - If `e2webifversion` starts with `"OWIF "`, the detector confirms with `/api/statusinfo`. Every OWIF release seen in its history uses that prefix (`OWIF 0.1.2` … `OWIF 1.5.2`).
  - If confirmed, the flavor is `OpenWebif`, and the "Version < 1.6.5" error no longer appears.
  - Otherwise the flavor is `DreamboxWebIf`, and the Dreambox version table applies as today.
  - A Dreambox pays no extra request. OWIF pays one cheap one.
- **Conflicting answers.** If `deviceinfo` says OWIF but `/api/statusinfo` does not answer with JSON, the flavor stays unknown, and the box keeps today's Dreambox path with a soft warning. Nothing is blocked.
- **Storage: in memory, per profile.** The flavor is cached next to the device-info cache, so it survives as long as that cache does and a cached check still yields a flavor.
  - It is not a `Profile` column. As a column it would go into Gson backups, be overwritten by a stale edit form, interact with `hasSameSettings`, and re-emit the current profile on every check.
  - After process death the flavor is unknown until the first check finishes, and the Dreambox client serves meanwhile, as it does today. If that window turns out to matter, persist it in a separate table keyed by profile id.
- **Reflashing.** Every check overwrites the flavor, so a DM900/920 reflashed to OpenATV switches client on the next check.
- **No manual override** to begin with.

## 3. Phases

All phases land as commits in one PR. CI runs on every push. Each commit ends with the PR job's checks green:

```
./gradlew -Pci spotlessCheck :app:testGoogleDebugUnitTest :app:compileGoogleDebugAndroidTestKotlin :app:lintGoogleDebug
```

Phases 1 and 2 change no behavior on Dreambox.

**Fixtures without a box.** OWIF fixtures land with the code that first uses them.
- Each fixture is built from the OWIF handler and model that produce it. A header comment cites the source file and the commit (`e46534f`). Fixtures go under `app/test/resources/owif/`.
- They prove the mapping against what the source says, not against a real box.
- Phase 3 adds `scripts/owif-capture.sh`, which uses `curl` to fetch every endpoint the OWIF client uses into a folder a user can send in. Each real capture replaces its synthetic fixture.

1. **Seam on the Dreambox path, by area (no behavior change).** Introduce `ReceiverApi`, turn `EnigmaClient` into `DreamboxWebIfApi`, and move each area's wire details into it.
   - **1a.** The interface and factory; services and EPG (`ServiceRepository`, `EpgRepository`, the TV hub).
   - **1b.** Timers, movies (including `/file` URLs and downloads), the receiver (stream URL, `playMedia`), and locations and tags in `ProfileRepository`.
   - **1c.** Control (volume, power, zap, remote, message, sleep timer, screenshot), the widget, `CheckProfile` device info, the AutoTimer and bouquet editor callers, and plugin presence via `ReceiverApi`. After 1c, nothing above the client uses `URIStore` or `NameValuePair`.

   Repository tests stay on `MockWebServer`. What they assert about the wire must not change.
2. **Capabilities and detection.**
   - Per-profile `WebIfCapabilities` replace the global flags, including the POST state in `EnigmaHttp`. Delete the flags.
   - Add `ReceiverDetector` and the in-memory flavor (§2.4).
   - First consumer: on OWIF, the check no longer reports "Version < 1.6.5" and no longer turns off now/next and the sleep timer.
   - `ReceiverApiFactory` still returns the Dreambox client for every flavor.
3. **`OpenWebifApi`.** Built area by area against synthetic fixtures; switched on last.
   - **3a.** Read paths: services, EPG, current service and status, device info, signal.
   - **3b.** Timers and movies.
   - **3c.** Control: volume, power, zap, remote, message, sleep timer, `/grab`; the auth failure.
   - **3d.** The factory returns `OpenWebifApi` for `OpenWebif`. Add the capture script.
4. **Plugins.**
   - The AutoTimer client, with requests by `api_version`.
   - The OWIF bouquet editor: JSON results, so item 9 does not apply; satellites from `/api/getsatellites`; services with `hidden=1`.
   - Presence on OWIF from `/autotimer/get`.
5. **Streams and picons.**
   - Transcoding on port 8002 becomes a profile option. Setup gives it a default from the detected flavor. There are no per-flavor URL builders; stream URLs keep coming from profile settings.
   - Picons on OWIF: decide between the `picon` field from `getservices?picon=1` and `/picon/` (mounted only when the box has a picon path).
6. **Docs and discovery.**
   - Update the scope statements in `multiepg.md`, `autotimer.md`, `offline-and-errors.md` and `modernize-dreamdroid.md`.
   - Fix the `SignalParser` comment: DM emits `e2acg` too.
   - Optionally widen `DeviceDetector` and mDNS beyond `dm*` host names.
   - Run the emulator job via `workflow_dispatch` for the profile-setup and timer screens.

## 4. Open questions

- **Which images matter?** OpenATV and OpenPLi differ in their AutoTimer fork and OWIF version. The first real captures should come from what users actually run.
- **Real captures before release.** The OWIF client is tested against synthetic fixtures until someone with a box runs the capture script. Ship it marked experimental, or wait?
- **Minimum OWIF version.** Check the `/api` fields used in phase 3 against `CHANGES.md` and document the floor. Below it the flavor stays unknown, which keeps today's path.
- **2.0 blockers.** This plan does not say whether it waits for the open 2.0 blockers in `modernize-dreamdroid.md`.
