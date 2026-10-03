# AutoTimer (plan)

**Status:** phases 1–7 implemented on phone (§5); the operator's box checks are open. API researched from source and on a real box; test data captured (`app/test/resources/web/autotimer/`). The design was compared against three independent alternatives (§8).
**Target API:** the opendreambox **AutoTimer** plugin's web API (`/autotimer`, API version 1.6, plugin 4.3.2, config version 8). This plan was written for the Dreambox WebInterface only. Since [`openwebif.md`](openwebif.md) the same client (`AutoTimerPluginApi`) also serves OpenWebif boxes and the oe-alliance fork (API version 1.7); requests depend on the plugin's `api_version`, not on the web interface. We do not patch the plugin.
**Reference (read-only):** [opendreambox/enigma2-plugins `autotimer`](https://github.com/opendreambox/enigma2-plugins/tree/master/autotimer), mainly `src/AutoTimerResource.py` (the web API), `src/AutoTimerConfiguration.py` (list XML), `src/AutoTimer.py` (matching), `src/AutoTimerEditor.py` (on-box editor and "AutoTimer from event"), `src/web-data/autotimereditor.js` (the plugin's own web editor).
**Architecture:** new code follows [`modernize-dreamdroid.md`](modernize-dreamdroid.md#target-architecture) and [`AGENTS.md`](../AGENTS.md): one `AutoTimerRepository`, `@HiltViewModel`s with `SavedStateHandle` and one `StateFlow` UI state, `UiText` titles and messages, type-safe routes, `SavedTextField` for text input.

An AutoTimer is a saved EPG search on the receiver. When the plugin runs, it adds a regular timer for every event that matches. dreamDroid lists, creates, edits, enables/disables and deletes AutoTimers and previews what they would record.

### Decision brief (accepted 2026-09-30)

| | |
| --- | --- |
| **Entry** | Own drawer destination **AutoTimer**, after EPG. Not a Tools hub tab: the hub's bottom navigation is already full. |
| **Visibility** | Shown only when the plugin is present for the active profile: `/web/external` lists `autotimer` (Dreambox WebInterface) or `/autotimer/get` answers (OpenWebif). Hidden while unknown; the last answer is kept while the box is unreachable. |
| **Fast path** | **Record series** in the EPG detail sheet opens the editor prefilled from the event. After every save the user lands on the preview: "what will this record". |
| **Writes** | Typed model parsed at the boundary. `edit` gets only the field groups the user changed, plus escaped `match` and `name`. Fields the app does not model are never sent, so they survive. |
| **Identity** | Plugin ids are list positions that the box renumbers. Every write re-lists first and refuses when the id no longer names the AutoTimer the user loaded. |
| **Offline** | Online only. No Room snapshot. Writes blocked like timer writes. |
| **Out of v1** | TV, plugin settings (`get`/`set`), XML import/export, counters, VPS, SeriesPlugin, alternatives, editing `<defaults>`. |

---

## 1. Product summary (phone v1)

| Capability | v1 |
| --- | --- |
| List | Name, match, targets summary (first two, then "+N"), time window, days, enabled switch. Tap → preview; row menu: Edit, Delete. FAB: new. |
| Enable/disable | Row switch. A one-group write; flips optimistically, reverts with the box's message on failure. |
| Preview | `test?id=N` for one AutoTimer: upcoming matches and skipped events with the plugin's reason. A disabled AutoTimer shows "The box only previews enabled AutoTimers" and an **Enable** button, without a request. A tap on an upcoming match opens the EPG detail sheet without its actions; a tap on a skipped one shows the plugin's reason. |
| Create / edit | One scrolling screen of sections: the search (match, name, search type and case, enabled, zap only), **Channels** (removable chips, Add channels), **When** (time window, days, date window), **Filters** (include/exclude title, short description, description, added through one field and a choice of list) and **Recording** (margins, max duration, location, tags, after event, set end time, duplicates). The sections stay open; collapsing them was dropped as not worth its state. |
| Record series | EPG detail sheet action, only when the plugin is present: match = name = event title, contains, channel = event service, other settings as the plugin's defaults (no duplicate check). The on-box importer's "event time ±1 h" window is a one-tap suggestion chip, not applied silently. |
| Delete | Confirm dialog ("Timers it already added stay"), then reload; `remove` always replies success. |
| Run now | `parse`: runs the plugin's EPG search for all enabled AutoTimers now and adds timers for new matches, as a run on the box does. A top-bar action on the list, behind a confirm dialog, since it adds real timers and can take minutes. The plugin's multi-line summary is shown in a dialog. |

### Phone layout sketch

Illustrative; the upcoming rows are from the box capture (`test.xml`), the skipped row is made up.

```text
┌ ☰ AutoTimer ──────────────────────────────┐
│ Wilsberg                            [●━]  │
│ "Wilsberg" · ZDF HD, zdf_neo HD +1         │
│ 20:00–23:00 · Sat, Sun                     │
│ Tatort                              [━○]  │
│ Paused · "Tatort" · Favourites (TV)        │
│                                       (+)  │
└────────────────────────────────────────────┘
  tap → preview    ⋮ → Edit / Delete

┌ ← Wilsberg                      [✎] [🗑] ┐
│ Title contains "Wilsberg" on ZDF HD,       │
│ zdf_neo HD · Sat, Sun · 20:00–23:00        │
├── Upcoming (4) ────────────────────────────┤
│ Wed 30 Sep 20:10  zdf_neo HD               │
│   Wilsberg – In Treu und Glauben           │
│ Sat 3 Oct 20:10   ZDF HD                   │
│   Wilsberg – Einfach weg                   │
├── Skipped (1) ─────────────────────────────┤
│ Thu 1 Oct  Wilsberg – …  outside window ▸  │
└────────────────────────────────────────────┘
```

### States

| State | List | Preview / editor |
| --- | --- | --- |
| Loading | Progress | Progress; preview is cancellable |
| Empty | "No AutoTimers yet. Open a programme in the EPG and tap Record series, or tap +." `list_empty.xml` counts as empty. | Preview: "Nothing in the EPG matches right now." |
| Plugin missing | Reached only by a stale back stack or a profile switch: "AutoTimer is not installed on this receiver", no FAB | same |
| Offline | Error with Retry; switches, FAB and delete use `onlineOnlyLook` (`SessionConnectionHolder.status.blocksMutations`) | Save blocked the same way |
| Box says no | `e2state` False: `e2statetext` in the Snackbar as is (localized) | same |
| Changed on the box | Reload | Editor: says nothing was saved and that Reload discards the draft |
| Saved but not listed | — | Editor: the box's reply and "go back to the list"; the form is done, so a second Save cannot create the AutoTimer twice |
| Other receiver | List and preview start over for the new profile | Editor: "belongs to the previous receiver", nothing is sent; switching back resumes the draft. Preview: back on its profile it loads again |
| Unreadable entry | Row "dreamDroid cannot read this AutoTimer": delete only | — |
| Plugin failed | — | Preview shows the `<exception>` text, not a connection error |

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

**`edit` is a partial update.** A parameter that is not sent keeps its stored value; an empty one clears the field. Proved on the box: a POST with only `offset`, `tag` and empty timespan left `enabled="no"`, services and filters untouched (`list_after_partial_edit.xml`). Two exceptions, from source: `match` and `name` are decoded again even when not sent (next section), and within the include or exclude group, sending one of the four keys (`title`, `shortdescription`, `description`, `dayofweek`) keeps the other three only because they are absent, so a changed group sends all four.

### Quirks

- **Localized results.** `e2statetext` is in the box's language ("AutoTimer wurde erfolgreich hinzugefügt"). Decide on `e2state`; show the text as is.
- **Remove always succeeds.** `remove?id=99` replies `True`. Reload the list after a delete.
- **Empty defaults block.** An empty list still contains `<defaults id="-1"></defaults>`.
- **A match carries no EPG text, and its times hold the margins.** `test` rows have service, title, begin and end, but no event id or description, and begin and end are the timer's: the programme plus the recording margins (on the dm900, `test.xml`'s first row starts 5 min before and ends 5 min after the programme). The sheet looks the programme up in that window (`EpgRepository.recordedProgramme`): the one with the match's title, else the one that fills most of the window. The MultiEPG Room cache answers when it holds the title; otherwise `/web/epgservice?sRef=…&time=<begin>&endTime=<window minutes>`, which answers with the programmes running in the window (`epgservice_match_window.xml`). Without one the sheet shows the match itself and says the EPG has no details. An Offline session only looks in the cache.
- **Preview skips disabled AutoTimers.** `test` and `simulate` iterate `getEnabledTimerList()` (`AutoTimer.py:785`); a disabled AutoTimer previews empty.
- **`e2message` is escaped twice.** After XML parsing it still contains the text `&#13;&#10;`; turn those into line breaks.
- **Preview failure is broken XML on purpose.** An exception ends the stream with `<exception>…</exception><|PURPOSEFULLYBROKENXML<`. A parse failure of a preview is "AutoTimer failed", not a connection error.
- **`parse` streams.** It opens `<e2simplexmlresult>`, writes `<ignore />` every 50 s while matching, then the result. The parser skips `ignore`; the request needs a long read timeout.
- **Plugin bug:** `/autotimer/get` swaps the values of `disabled_on_conflict` and `addsimilar_on_conflict` (`AutoTimerResource.py:700-701`). Irrelevant until plugin settings are in scope.
- **Ids are positions, not identities.** `readXml()` re-parses the config whenever the file's mtime changed and renumbers every AutoTimer `1…n` in file order (`AutoTimer.py:139-162`). The list endpoint calls it on every request. After the file changes on the box (on-box editor, another client with `always_write_config`), an id the app holds can name a different AutoTimer, and `remove` by that id reports success either way.
- **Web edits may not reach disk.** `always_write_config` defaults to off (`__init__.py:38`). Then edits live in memory and are written only at a clean enigma2 shutdown (`plugin.py:58-71`); a crash or power cut loses them. `/autotimer/set` has no key for this setting.
- **Values are decoded twice.** `edit` runs `unquote()` on `match`, `name`, `services`, `bouquets`, filters and tags after Twisted already decoded the query (`AutoTimerResource.py:311-467`). For `match` and `name` it does so on the stored value when the parameter is missing. A client must escape `%` as `%25` in those values and always send `match` and `name`. A service ref containing `,` cannot be sent, because the list is split after decoding. From source only; not yet tried on a box.
- **A broken preview loses the plugin's message.** `parseEnigmaXml` runs the parser in strict mode and rejects the document (`enigma/XmlPull.kt`), so the rows before `<exception>` are not salvaged, but the exception text is lost with them. The preview parser reads `<exception>` from the raw text first.
- **Dead endpoint:** the web editor calls `/autotimer/clone`, which the plugin never registers.
- **Plugin bug: service refs are not escaped in the list.** The webif branch of `buildConfig` writes `<e2servicereference>` without `stringToXML` (the name is escaped). An IPTV ref with a raw `&` in its URL makes the whole list unparsable, so every AutoTimer screen shows the parse failure. From source only; not worked around in the app.
- **Date window ends.** "First day" and "Ends before" are local midnights; events must begin in between (`checkTimeframe`). Equal ends mean "from that day on" to the plugin, so the editor refuses a window that ends on or before its first day (unless the box already had it).
- **Run now rewrites the config.** `parse` changes counters and may renumber, so it takes the same write lock as edits; the list offers no write during a run.

### Box evidence (2026-09-30)

dm900, enigma 4.3.3r3 (2021-10-29), webif 1.9.0, AutoTimer 4.3.2. A throwaway "Wilsberg" AutoTimer was created disabled, edited over GET and POST, enabled only for `test`/`simulate` (4 matches on ZDF HD / zdf_neo HD), then disabled and removed. The box's regular timer list was unchanged. `test` and `simulate` answered in about 0.7 s with one AutoTimer; `parse` was not called. Captured responses: `app/test/resources/web/autotimer/`. `/web/external` from the box equals `app/test/resources/web/bouqueteditor/web_external.xml`.

## 3. Domain model and boundary

Types live in `enigma/autotimer/`. Box strings become these types only in the parser; nothing downstream sees `"yes"`, `"1:7:"`, `"5,10"` or epoch strings. Sketch, not final code:

```kotlin
@JvmInline value class AutoTimerId(val value: Int)   // a list position, valid for one config read

data class AutoTimer(val id: AutoTimerId, val settings: AutoTimerSettings, val extras: Extras)

data class AutoTimerSettings(
    val name: String, val match: String, val enabled: Boolean,
    val searchType: SearchType, val caseSensitive: Boolean,
    val targets: List<Target>,
    val timeWindow: ClockWindow?, val dateWindow: DateWindow?,
    val offset: Offset?, val maxDuration: Minutes?, val location: String?, val tags: List<String>,
    val include: Filters, val exclude: Filters,
    val afterEvent: AfterEvent, val recordMode: RecordMode, val duplicates: DuplicateCheck,
)

sealed interface Target { val ref: String
    data class Channel(override val ref: String, val name: String) : Target
    data class Bouquet(override val ref: String, val name: String) : Target   // ref starts "1:7:"
}
data class ClockWindow(val from: LocalTime, val to: LocalTime)          // may wrap midnight
data class DateWindow(val after: Instant, val before: Instant)
data class Offset(val before: Minutes, val after: Minutes)               // "5" or "5,10"
data class Filters(val title: List<String>, val shortDescription: List<String>,
                   val description: List<String>, val days: Set<DayFilter>)
sealed interface DayFilter { data class On(val day: DayOfWeek) : DayFilter; data object Weekdays : DayFilter; data object Weekend : DayFilter }
enum class AfterEventAction(val listToken: String, val editToken: String) {
    Nothing("none", "nothing"), Standby("standby", "standby"), DeepStandby("shutdown", "deepstandby"), Auto("auto", "auto")
}
sealed interface AfterEvent {
    data object ReceiverDefault : AfterEvent                                   // no <afterevent>
    data class Fixed(val action: AfterEventAction, val window: ClockWindow?) : AfterEvent
    data class Several(val entries: List<Fixed>) : AfterEvent                  // shown, never written
}
sealed interface RecordMode { data object Record : RecordMode; data class Zap(val setEndTime: Boolean) : RecordMode }
sealed interface DuplicateCheck { data object Off : DuplicateCheck
    data class On(val scope: DuplicateScope, val compare: DescriptionCompare) : DuplicateCheck }
data class Extras(val counter: Boolean, val vps: Boolean, val seriesPlugin: Boolean, val overrideAlternatives: Boolean)
```

Why these shapes:

- `DuplicateCheck.Off` has no compare mode and `RecordMode.Record` no `setEndTime`: the list XML omits those attributes in exactly these cases (`AutoTimerConfiguration.py`, the `timer` loop of `buildConfig`), so the app does not know them and must not claim a value.
- One enum carries both after-event tokens, so no code maps list names to edit names. The parser also accepts `deepstandby`, as the config reader does. An unknown token makes that AutoTimer `Unreadable` instead of letting the plugin's edit fallback turn it into `auto`.
- `Extras` is display-only ("Also uses: counter, VPS") and never encoded.

**Parsers** (on `parseEnigmaXml`, `enigma/XmlPull.kt`):

- `AutoTimerListParser`: `<autotimer>` → entries of `Readable(AutoTimer)` or `Unreadable(id, name, reason)`; `<defaults>` skipped; an `e2simplexmlresult` root ("Couldn't load config file", `AutoTimerResource.py:236-238`) is a box rejection.
- `AutoTimerPreviewParser`: checks the raw text for `<exception>` first, then `<e2simulatedtimer>` rows with `Verdict.Ok | Skip` (absent for `simulate`) and `e2message` unescaped a second time.
- Write replies reuse `SimpleResultParser`. Presence is `ReceiverApi.autoTimerPlugin()`, which also returns the plugin's API version: `DreamboxWebIfApi` reads `/web/external` and matches exactly `autotimer`, not `autotimereditor`; `OpenWebifApi` asks `/autotimer/get`. (Written as `getWebExternals()` before the `ReceiverApi` split.)

**Encoder.** `AutoTimerWrite` is the only way to build `edit` parameters:

```kotlin
sealed interface AutoTimerWrite {
    data class Create(val settings: AutoTimerSettings) : AutoTimerWrite                 // every group
    data class Change(val loaded: AutoTimer, val edited: AutoTimerSettings) : AutoTimerWrite  // changed groups only
}
fun autoTimerEditParams(write: AutoTimerWrite): List<NameValuePair>  // was AutoTimerWrite.toParams()
```

A field group is what the plugin updates together: Match, Name, Enabled, Search, TimeWindow, DateWindow, Offset, MaxDuration, Location, Targets (both `services` and `bouquets`), Tags, Include (all four keys), Exclude (all four `!` keys), AfterEvent, RecordMode, Duplicates. Rules:

- A `Change` sends `id`, the changed groups, and always `match` and `name`. `%` is escaped as `%25` in `match`, `name`, targets, filters and tags before normal URL encoding (double decoding, §2). `location` is not decoded twice and is not escaped.
- Clearing sends the empty value: `timespanFrom=&timespanTo=`, `before=&after=`, `offset=`, `maxduration=`, `location=`, `tag=`, `afterevent=default`. An empty filter list is one empty value.
- A target ref containing `,` makes the Targets group unsendable: the editor shows targets read-only until that entry is removed. `Several` after-events are never sent.
- An empty `Change` closes the editor without a request.

Sending only changed groups keeps values the app cannot round-trip exactly, and edits made on the box to other fields, untouched.

## 4. App wiring

**Repository** (`data/AutoTimerRepository.kt`, `@Singleton`, injects `ReceiverApiFactory` (was `EnigmaClientFactory`) and `ProfileRepository`):

| Member | Behaviour |
| --- | --- |
| `presence: StateFlow<PluginPresence>` | `Unknown`, `Present`, `Absent` for the active profile; reset to `Unknown` on profile change; a failed check keeps the last answer |
| `refreshPresence()` | `ReceiverApi.autoTimerPlugin()`; called from `ShellViewModel` after a successful profile check |
| `list()` | `Ready(entries)`, `PluginMissing` or `Failed` |
| `save(write)`, `setEnabled(timer, on)`, `remove(timer)` | Stale guard: re-list and compare the entry at `timer.id` with the loaded `AutoTimer`; different or missing → `Conflict`, no write. One write at a time (`Mutex`, as in `BouquetEditorRepository`). Reload after `remove`. |
| `preview(timer)` | `test?id=N`; a disabled AutoTimer never reaches the box |
| `runNow()` | Last phase: a client with a long timeout (`clients.current(RUN_TIMEOUT_MS)`). API 1.7 sends nothing until the run ends, so there the run starts in the background and the list refreshes when the box answers. |

Each call takes a fresh client from `clients.current()` (`ReceiverApiFactory`): one `EnigmaHttp` cancels its in-flight call when it starts another. `ReceiverApi` has `autoTimers`, `saveAutoTimer`, `removeAutoTimer`, `testAutoTimer` and `runAutoTimers`; both clients delegate them to `AutoTimerPluginApi`, and paths stay in `URIStore`. Parameters stay in the query string, as for every other request. (This paragraph first named `EnigmaClient` and `getAutoTimers`/`editAutoTimer`/`parseAutoTimers`.)

**Drawer.** `ShellUiState` gains `autoTimerInDrawer` (presence is `Present`); `PhoneShell` passes it to `DrawerScreen`, which filters `DrawerDestinations.destinations`. New id `R.id.menu_navigation_autotimer` maps to the `AutoTimers` route in `NavigationHelper.navRootRoutes` and in `DrawerHighlight.itemIdForRoute`. Do not add a `DreamDroid` static flag like `featureSleepTimer()` (`NavigationHelper.kt:116`).

**Routes** (`ui/nav/PhoneNavRoutes.kt`): `AutoTimers`, `AutoTimerPreview(id: Int)`, `AutoTimerEdit(id: Int? = null, prefill fields for Record series: title, serviceRef, serviceName, beginSec, durationSec)`, `AutoTimerTargetPick`. Only ids and prefill values travel; screens load the AutoTimer from the repository.

**Target picker.** A new multi-select destination over `ServiceRepository`: bouquet rows have a checkbox (whole bouquet → `Target.Bouquet`) and open on tap for single channels. The result goes to the editor's back-stack `SavedStateHandle`, not through the `Intent` bridge the timer picker uses (`PhoneNavigator.deliverPickResult`). `TimerServicePick` stays as it is.

**ViewModels** (`ui/autotimer/`): `AutoTimerListViewModel`, `AutoTimerPreviewViewModel`, `AutoTimerEditViewModel`, `AutoTimerTargetPickViewModel`; each takes the repository, `SessionConnectionHolder` and `SavedStateHandle`. The editor keeps the loaded `AutoTimer` and the draft in `SavedStateHandle` so the change set survives process death; name, match, filter and tag inputs are `SavedTextField`s. Form → `AutoTimerSettings` is a pure function returning the settings or per-field errors (blank match, invalid minutes, a date window that ends on or before its first day). Text left in the filter field is added on Save, as if Add had been tapped. The editor belongs to the profile it opened on. The EPG sheet reads `presence` through `EpgEventDetailViewModel`.

## 5. Phases

One PR each. Each runs the PR job (`./gradlew -Pci spotlessCheck :app:testGoogleDebugUnitTest :app:compileGoogleDebugAndroidTestKotlin :app:lintGoogleDebug`) and adds Compose UI tests next to its screens. Repository tests use `TestReceiver` and `loadWebFixture` like `BouquetEditorRepositoryTest`; ViewModel tests use a fake repository.

| Phase | Scope | Proof |
| --- | --- | --- |
| 0 | API research, captured test data | Done: this doc, `app/test/resources/web/autotimer/` |
| 1 | Model, list parser, repository `presence`/`list`, drawer row, list screen (read-only) | Parser tests on `list_empty`, `list_enabled`, `list_disabled_full`, `list_after_partial_edit`, plus inline cases for `shutdown`/`none`, windowed and several after-events, an unknown token → `Unreadable`. Presence Present on `bouqueteditor/web_external.xml`, Absent without `autotimer`. List screen states; drawer row shown/hidden. |
| 2 | Encoder, stale guard, enable switch, delete | `diff(a, a.settings)` is empty; `list_disabled_full` → `list_after_partial_edit` encodes to exactly `id`, `match`, `name`, `offset=3`, `tag=dreamdroid-post`, `timespanFrom=`, `timespanTo=`; a table test per field group incl. clears, the four-key filter rule, after-event tokens, `%` escaping, a comma target. Recorded queries: the switch sends `id`, `match`, `name`, `enabled` only. A renumbered list yields `Conflict` and no `edit` request. |
| 3 | Preview screen (row tap), Enable button for a disabled AutoTimer | Preview parser on `test.xml` (4 rows, line breaks), `simulate.xml`, `simulate_empty.xml`, and a synthetic `<exception>` body built from `AutoTimerResource.py:178`, labelled synthetic. Disabled AutoTimer: no `test` request. |
| 4 | Editor (always-open block and When), target picker, create; after save the user lands on the preview | ViewModel: load → edit → save emits the expected write; empty change makes no request; `Conflict` → Reload; process-death restore. New-id resolution after create (reply has no id): reload and take the highest id whose `match` equals ours. Editor and picker UI tests, picked bouquet lands in targets. |
| 5 | Editor sections Filters and Recording | Encoder table extended; section UI tests; `Several` shown read-only. |
| 6 | Record series in the EPG sheet | Action only when `Present`; prefill mapping; suggestion chip applies ±1 h (`AutoTimerEditor.py:1489-1491`). |
| 7 | Run now | Streamed reply with `<ignore />` before the result (synthetic, labelled); long timeout; another screen's request does not cancel it; confirm dialog UI test. |

All seven phases are on `claude/gracious-goodall-glejeu`, one commit each; the PR job (spotless, unit tests, instrumented test compile, lint) passes on each. The instrumented tests compile but have not run on an emulator in this environment.

Box checks by the operator before merging: toggle, delete, create and edit on a real box; one match containing `%` (proves or disproves the double decoding); one "Run now" with an AutoTimer that matches something harmless.

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| A stale id deletes or edits another AutoTimer | Stale guard before every write; the remaining window is one round trip. |
| Edits lost on a box crash (`always_write_config` off) | Not fixable from the app; known behaviour on these boxes, not surfaced in the UI. |
| Double decoding changes values with `%` | Escape and always send `match`/`name`; verify on a box in phase 2. |
| Query too long for many targets and filters | Bouquet refs are about 90 characters, so realistic AutoTimers stay small. If a box or proxy rejects one, add a form-body POST for `edit` only (the plugin accepts it, verified). |
| `parse` exceeds normal timeouts | Own client and a long timeout (phase 7). |
| Configs the model cannot read | `Unreadable` entries can be deleted, not edited; the model grows when real configs need it. |
| The drawer entry appears late | Presence is asked after each successful profile check and kept in memory per profile. After installing the plugin, or on a cold start with the receiver offline, AutoTimer (and Record series) show only after the next successful check. Accepted: the entry must not offer a plugin the box may not have. |

## 7. Decisions (operator, 2026-09-30)

1. Drawer entry after EPG.
2. Record series uses the plugin's defaults; no duplicate check preset.
3. No warning about unsaved config: losing edits on a crash without "Always write config" is known behaviour on these boxes.
4. Run now ships (phase 7).
5. Online only; no Room snapshot. The config changes on the box too often for a copy to be useful.

## 8. Alternatives considered

Three alternative plans were written independently from the API facts in §2, without seeing this doc, and scored with its first draft on API correctness, fit with the repo rules, scope and phasing, phone UX, testability, and clarity. This doc merges them; the domain-model plan is the base.

| Plan | Kept | Not kept |
| --- | --- | --- |
| Domain model first (base) | Typed model, change-only writes, escaped `match`/`name`, stale guard, `<exception>` check, `Unreadable` entries | A second `/web/external` parser for the version; copying visibility through `MainActivity` into `DrawerListState` |
| Experience first | Record series with a suggestion chip, preview after save, Enable button on a disabled preview, `ShellUiState` visibility, multi-select picker, the persistence risk | Preview all (`simulate`) and a separate detail screen in v1 |
| Minimal | Leaner first editor, exact-parameter tests | Drawer entry always shown; reuse of the single-pick timer picker through the `Intent` bridge |
| First draft of this doc | API section, fixtures, drawer entry | "Send every owned field" writes: they re-send values that do not round-trip and miss the stale-id and double-decoding hazards |

## 9. Non-goals

TV surface; plugin settings; XML backup and restore; counters, VPS, SeriesPlugin; editing `<defaults>`; Preview all; showing which timers an AutoTimer created; patching the plugin.
