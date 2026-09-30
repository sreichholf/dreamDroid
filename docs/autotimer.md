# AutoTimer (plan)

**Status:** planned. API researched from source and on a real box; test data captured (`app/test/resources/web/autotimer/`). No app code yet.
**Target API:** the opendreambox **AutoTimer** plugin's web API (`/autotimer`, API version 1.6, plugin 4.3.2, config version 8). Not the OpenWebif AutoTimer fork. We do not patch the plugin.
**Reference (read-only):** [opendreambox/enigma2-plugins `autotimer`](https://github.com/opendreambox/enigma2-plugins/tree/master/autotimer), mainly `src/AutoTimerResource.py` (the web API), `src/AutoTimerConfiguration.py` (list XML), `src/AutoTimer.py` (matching), `src/web-data/autotimereditor.js` (the plugin's own web editor, a working client).
**Architecture:** new code follows [`modernize-dreamdroid.md`](modernize-dreamdroid.md#target-architecture) and [`AGENTS.md`](../AGENTS.md): one `AutoTimerRepository`, `@HiltViewModel`s with `StateFlow` UI state, type-safe routes, Snackbar messages, `TextFieldState` for text input.

An AutoTimer is a saved EPG search on the receiver. When the plugin runs, it adds a regular timer for every event that matches. dreamDroid can list, create, edit, enable/disable and delete AutoTimers, preview what they would record, and start a run.

### Decision brief (proposed, needs operator sign-off)

| | |
| --- | --- |
| **Entry** | Own drawer destination **AutoTimer**, after EPG. Not a Tools hub tab: the hub's bottom navigation is already full. |
| **Visibility** | Shown only when `/web/external` lists `autotimer` for the active profile. Checked once per profile; the last answer is kept while the box is unreachable. |
| **Surfaces** | List → edit form (create/edit) → preview. "Create AutoTimer" from the EPG detail sheet, prefilled from the event. "Run now" on the list. |
| **Transport** | Existing `EnigmaClient`/`EnigmaHttp` with query parameters. No new HTTP path. |
| **Offline** | Online only. No Room snapshot. Unreachable box → the usual offline state; writes blocked like timer writes. |
| **Out of v1** | TV, global plugin settings (`/autotimer/get`, `/set`), XML import/export, counters, VPS, SeriesPlugin, EPGRefresh. |

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| List | Name, match text, enabled switch, one summary line (services/bouquets, timespan, days). Tap → edit. Row menu: preview, delete. |
| Create | FAB on the list (defaults) or from the EPG detail sheet (match = event title, service = event service). |
| Edit | Form sections, most-used first: **Search** (match, name, search type, case), **Where** (services, bouquets), **When** (timespan, days, timeframe), **Filters** (include/exclude title, short description, description), **Recording** (offset, max duration, location, tags, after event, zap only, duplicate avoidance). |
| Preview | Per AutoTimer (`test?id=`): matching events with state *will record* / *skipped* and the reason. Enabled AutoTimers only (see §3). |
| Run now | List action: runs `/autotimer/parse`, shows the plugin's summary text in a Snackbar, and refreshes the timer list. |
| Delete | Confirm dialog, then reload the list (remove replies success even for unknown ids). |

### Phone layout sketch

```text
┌─ AutoTimer ─────────────────── [▶ Run] ┐
│ Wilsberg                          [■■] │
│ "Wilsberg" · ZDF HD, zdf_neo HD        │
│ 20:00–23:00 · Sat, Sun                 │
├────────────────────────────────────────┤
│ Tatort                            [□□] │
│ "Tatort" · Favourites (TV)             │
└──────────────────────────────── [ + ] ─┘
  tap row → edit form   ⋮ → Preview / Delete
```

## 2. Web API (verified)

All paths need the webif's HTTP Basic auth when it is enabled. They do **not** need `sessionid` (the webif only checks it under `/web/`); sending it is harmless. `GET` works everywhere except the two XML upload endpoints.

| Endpoint | Use | Response |
| --- | --- | --- |
| `/web/external` | Plugin present: an `e2path` of `autotimer`; `e2externalversion` is the API version | `e2webifexternals` |
| `/autotimer` | List (`webif=true` is the default and adds `id` and service names) | `<autotimer version="8">` config XML |
| `/autotimer/edit` | Create (no `id`) or change (`id=N`) | `e2simplexmlresult` |
| `/autotimer/remove?id=N` | Delete | `e2simplexmlresult` |
| `/autotimer/test?id=N` | Preview one AutoTimer, with per-event state and log | `e2autotimersimulate` |
| `/autotimer/simulate` | Preview all enabled AutoTimers (no state or log) | `e2autotimersimulate` |
| `/autotimer/parse` | Run now; adds real timers | `e2simplexmlresult`, streamed |

Not used in v1: `/autotimer/get`, `/autotimer/set` (plugin settings), `/autotimer/add_xmltimer`, `/autotimer/upload_xmlconfiguration` (POST only), `/autotimereditor` (the web UI).

### List format

One `<timer>` per AutoTimer. Settings are attributes; lists are child elements. The fixture `list_disabled_full.xml` shows most of them.

| Field | List XML | `edit` parameter | Notes |
| --- | --- | --- | --- |
| id | `id` | `id` | `-1` on `<defaults>` |
| name, match | `name`, `match` | `name`, `match` | `match` is required; empty `name` becomes `match` |
| enabled | `enabled="yes\|no"` | `enabled=1\|0` | |
| search | `searchType` (absent = `partial`), `searchCase` (absent = insensitive) | same | `partial`, `exact`, `description`; `sensitive`, `insensitive` |
| timespan | `from`, `to` (`HH:MM`) | `timespanFrom`, `timespanTo` | both empty clears |
| timeframe | `after`, `before` (epoch seconds) | `after`, `before` | both empty clears |
| offset | `offset="5"` or `"5,10"` (minutes) | `offset` | empty clears (use receiver default) |
| max duration | `maxduration` (minutes) | `maxduration` | empty clears |
| location | `location` | `location` | empty clears |
| services, bouquets | one `<e2service>` list for both | `services`, `bouquets` (comma-separated) | bouquet refs start with `1:7:` |
| tags | `<e2tags>` space-separated | `tag`, repeated | |
| includes, excludes | `<include where=…>`, `<exclude where=…>` | `title`, `shortdescription`, `description`, `dayofweek`; excludes prefixed `!`; repeated | one empty value clears that list |
| days | `where="dayofweek"`: `0`=Mon … `6`=Sun, `weekend`, `weekday` | `dayofweek` / `!dayofweek` | |
| after event | `<afterevent>`: `none`, `standby`, `shutdown`, `auto` | `afterevent`: `nothing`, `standby`, `deepstandby`, `auto`, `default` | **names differ**: list `none`/`shutdown` ↔ edit `nothing`/`deepstandby`; an unknown value becomes `auto` |
| zap only | `justplay="1"`, `setEndtime="0"` | `justplay`, `setEndtime` | |
| duplicates | `avoidDuplicateDescription` 0–3, `searchForDuplicateDescription` 0–2 (absent = 2) | same | 0 no, 1 same service, 2 any service, 3 any service or recording; 0 title, 1 + short, 2 + all descriptions |
| not in v1 | `counter`, `left`, `lastBegin`, `lastActivation`, `counterFormat`, `overrideAlternatives`, `vps_*`, `series_*` | same | kept by partial edits, see below |

**`edit` is a partial update.** A parameter that is not sent keeps its stored value; an empty one clears the field. The app sends every field its form owns (empty to clear) and never sends fields it does not model, so counters, VPS and SeriesPlugin settings survive an edit from dreamDroid. Proved on the box: a POST with only `offset`, `tag` and empty timespan left `enabled="no"`, services and filters untouched (`list_after_partial_edit.xml`).

### Quirks

- **Localized results.** `e2statetext` is in the box's language ("AutoTimer wurde erfolgreich hinzugefügt"). Decide on `e2state`; show the text as is.
- **Remove always succeeds.** `remove?id=99` replies `True`. Reload the list after a delete.
- **Empty defaults block.** An empty list still contains `<defaults id="-1"></defaults>`.
- **Preview skips disabled AutoTimers.** `test` and `simulate` iterate `getEnabledTimerList()` (`AutoTimer.py:785`); a disabled AutoTimer previews empty.
- **`e2message` is escaped twice.** After XML parsing it still contains the text `&#13;&#10;`; turn those into line breaks.
- **Preview failure is broken XML on purpose.** An exception ends the stream with `<exception>…</exception><|PURPOSEFULLYBROKENXML<`. A parse failure of a preview is "AutoTimer failed", not a connection error.
- **`parse` streams.** It opens `<e2simplexmlresult>`, writes `<ignore />` every 50 s while matching, then the result. The parser skips `ignore`; the request needs a long read timeout.
- **Plugin bug:** `/autotimer/get` swaps the values of `disabled_on_conflict` and `addsimilar_on_conflict` (`AutoTimerResource.py:700-701`). Irrelevant until plugin settings are in scope.
- **Dead endpoint:** the web editor calls `/autotimer/clone`, which the plugin never registers.

### Box evidence (2026-09-30)

dm900, enigma 4.3.3r3 (2021-10-29), webif 1.9.0, AutoTimer 4.3.2. A throwaway "Wilsberg" AutoTimer was created disabled, edited over GET and POST, enabled only for `test`/`simulate` (4 matches on ZDF HD / zdf_neo HD), then disabled and removed. The box's regular timer list was unchanged. `test` and `simulate` answered in about 0.7 s with one AutoTimer; `parse` was not called. Captured responses: `app/test/resources/web/autotimer/`. `/web/external` from the box equals `app/test/resources/web/bouqueteditor/web_external.xml`.

## 3. App architecture

| Piece | Responsibility |
| --- | --- |
| `enigma/AutoTimer.kt` | Domain model: typed fields for everything in the table above except the "not in v1" row. Services and bouquets split at parse time; after event, search type, days and duplicate modes are enums. |
| `enigma/AutoTimerParser.kt` | `<autotimer>` list → `List<AutoTimer>` (ignores `<defaults>` in v1); `<e2autotimersimulate>` → `List<AutoTimerPreviewRow>`, decoding the double-escaped message. `SimpleResultParser` for write replies, skipping `<ignore />`. |
| `enigma/AutoTimerParams.kt` | `AutoTimer` → `edit` parameters: every owned field, empty values to clear, repeated keys for lists, edit-side enum names. |
| `EnigmaClient` | `getAutoTimers`, `editAutoTimer`, `removeAutoTimer`, `testAutoTimer`, `runAutoTimer`; paths in `URIStore`. |
| `data/AutoTimerRepository.kt` | `available: StateFlow<Boolean>` per profile (from `getWebExternals`, cached like `TimerRepository.locationsAndTags`), `list()`, `save(autoTimer)`, `setEnabled(id, enabled)`, `delete(id)`, `preview(id)`, `run()`. Each method lands with its first caller. |
| `ui/autotimer/AutoTimerListViewModel` + `AutoTimerListScreen` | List, toggle, delete, run now. Shell title and messages as `UiText`. |
| `ui/autotimer/AutoTimerEditViewModel` + `AutoTimerEditScreen` | Form state in the ViewModel; text fields are `SavedTextField`. Services and bouquets are removable chips; "Add service" opens the timer service picker (`ui/pick/TimerServicePick*`), which returns one service per visit. "Add bouquet" needs the picker to return a bouquet from its bouquet level, which it cannot yet. Location and tags reuse `TimerRepository.locationsAndTags()`. |
| `ui/autotimer/AutoTimerPreviewViewModel` + screen | `test?id=` rows grouped by date; disabled AutoTimer → explanation instead of an empty list. |
| Drawer | New `DrawerMenuItem` in `DrawerDestinations.destinations`; visibility from `AutoTimerRepository.available` through the shell's state, not a `DreamDroid` static flag like the sleep timer's. |
| EPG detail sheet | "Create AutoTimer" action next to set/edit timer, navigating to the edit route with a prefilled AutoTimer. Hidden when the plugin is absent. |

Routes: `AutoTimerList`, `AutoTimerEdit(id: Int?, prefill…)`, `AutoTimerPreview(id: Int)` as `@Serializable` routes in `PhoneNavRoutes`.

Enabling an AutoTimer does not add timers by itself; the plugin does that on its next run (autopoll, if enabled on the box) or on "Run now". The list shows this once after enabling, as a Snackbar with a "Run now" action.

## 4. Phases

Each phase is one PR, ends green on the PR job (`spotlessCheck`, unit tests, `compileGoogleDebugAndroidTestKotlin`, `lintGoogleDebug`), and adds Compose UI tests for its screens.

| Phase | Scope | Proof |
| --- | --- | --- |
| 0 | API research and captured test data | Done: this doc, `app/test/resources/web/autotimer/` |
| 1 | Model, list parser, `available`, `list()`; drawer entry; read-only list screen | Parser tests on all `list_*.xml`; repository tests with `TestReceiver` for present/absent plugin; list screen UI test (rows, empty, offline) |
| 2 | `save`, `setEnabled`, `delete`; edit form; toggle and delete on the list | Parameter-encoding tests (clearing, repeated keys, after-event names, services vs bouquets); ViewModel tests with a fake repository; form UI test |
| 3 | Preview screen; "Run now"; "Create AutoTimer" from the EPG detail sheet | Preview parser tests on `test.xml`, `simulate*.xml`, the broken-XML case; run-now test with a streamed `<ignore />` reply; sheet action UI test |

Box check before merging phase 2 and 3 (operator): create, edit, toggle, delete and preview against a real box, and one "Run now" with an AutoTimer that matches something harmless.

## 5. Risks

| Risk | Mitigation |
| --- | --- |
| Long query strings for AutoTimers with many services and filters | Bouquet refs are about 90 characters; a realistic AutoTimer stays well below a few kilobytes. If a box rejects it, add a form-body POST to `EnigmaHttp` for `edit` only; the plugin accepts both (verified). |
| `parse` exceeds the normal read timeout | Separate longer timeout for that one request; the button shows progress and can be left. |
| Plugin forks with other fields (OpenWebif / OpenPLi AutoTimer) | Not a target. Unknown attributes are ignored and never sent back, so partial edits keep them. |
| Editing a field the form does not show wipes it | Only owned fields are sent (partial update); covered by an encoding test that asserts unmodelled fields are absent. |

## 6. Non-goals

TV surface; plugin settings; XML backup/restore; counters, VPS, SeriesPlugin; EPGRefresh; showing which timers an AutoTimer created; patching the plugin.
