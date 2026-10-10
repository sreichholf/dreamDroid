# VPS in the timer editor

**Status:** implemented on phone and TV, on both web interfaces, for [issue 134](https://github.com/sreichholf/dreamDroid/issues/134). Researched from source; not yet checked on a box (§8).
**Target APIs:** the opendreambox **VPS** system plugin's web API (`/vpsplugin/web/*`) on the Dreambox web interface, and OpenWebif's own VPS params on its `/api` timer calls. We patch neither.
**References (read-only):**
- [opendreambox/enigma2-plugin-vps](https://github.com/opendreambox/enigma2-plugin-vps) at `7126152`: `src_py/web/*.xml` (endpoints), `src_py/WebComponents/Sources/Vps.py` (what they do), `src_py/Modifications.py` (the on-box editor), `src_py/Vps.py` (runtime), `src_py/plugin.py` (registration). It lived in `enigma2-plugins/vps` until 4.2; the web API is unchanged since. oe-alliance's `enigma2-plugins/vps` is the same plugin.
- E2OpenPlugins/e2openplugin-OpenWebif at `e46534f`, under `plugin/controllers/` (as in [`openwebif.md`](openwebif.md)).

**Architecture:** [`AGENTS.md`](../AGENTS.md), and the `ReceiverApi` seam of [`openwebif.md`](openwebif.md#22-shape): no request is built above the clients.

VPS lets the box follow a broadcast's real start and end, so a recording follows a show that starts late or runs over. That works for EPG timers, including single episodes of a series, and for manual timers.

### Decision brief

| | |
| --- | --- |
| **Where** | One extra field in the existing timer editor, phone and TV. No new screen. |
| **Visibility** | Only when the receiver has the VPS plugin (§3), the timer's VPS is known, and the timer records and does not repeat (the box's own rule). The rule keys off the plugin, not the web interface. |
| **Choice** | One three-way choice, as on the box: **No / Yes (safe mode) / Yes**. Manual timers (no event id or no name) also get **VPS time**. |
| **Box switch** | Not read. `/web/settings` would send the whole saved config, credentials included, to read one key. A one-line note under the field says VPS must be on in the receiver's VPS settings. If it is off, the box keeps the flag and records at the planned time. |
| **Default for new timers** | A per-profile setting, default **No**, for new timers from the editor and EPG quick adds. The box's own default is not read, for the reason above; it also applies only after a per-channel PDC check that is not reachable over HTTP. |
| **Writes** | Dreambox: through `/vpsplugin/web/*` with all three VPS params when the plugin is there and the timer's VPS is known. OpenWebif: the VPS params on `timeradd`, `timerchange` and `timeraddbyeventid`. Without the plugin, Dreambox requests are what they were before. |
| **Offline** | The timer snapshot in Room keeps VPS, so the offline list and editor have it. |
| **Out of scope** | Plugin settings, the per-channel PDC check, instant-record VPS. |

---

## 1. The two APIs

### 1.1 Dreambox: the VPS plugin

`plugin.py` registers `addExternalChild(("vpsplugin", …))` at session start, whether `config.plugins.vps.enabled` is on or not. Each endpoint wraps the stock WebInterface one: it runs the stock code, then finds the timer it touched and sets three attributes.

| Endpoint | Wraps | Extra |
| --- | --- | --- |
| `/vpsplugin/web/timerlist` | `/web/timerlist` | per `e2timer`: `e2vpsplugin_enabled`, `e2vpsplugin_overwrite` (`True`/`False`), `e2vpsplugin_time` (unix seconds, `-1` = none) |
| `/vpsplugin/web/timerchange` | `/web/timerchange` | params `vpsplugin_enabled`, `vpsplugin_overwrite` (`0`/`1`), `vpsplugin_time` (seconds, `-1` = none) |
| `/vpsplugin/web/timeraddbyeventid` | `/web/timeraddbyeventid` | same params |
| `/vpsplugin/web/timeradd` | `/web/timeradd` | same params; not used (new timers go through `timerchange` with `deleteOldOnSave=0`) |

- **Send all three or lose them.** A missing param becomes `None` on the timer (`Vps.py` `editTimer` / `addTimerByEventID`), which turns VPS off.
- `timerchange` finds the timer again by the *new* `sRef`, `begin`, `end`, `name` and `description`. A save that also renames still works: the stock edit runs first.
- The stock `/web/timerchange` edits in place and keeps VPS attributes.

### 1.2 OpenWebif

- OpenWebif keeps `vpsplugin` out of `/web/external` (`pluginshook.src:10`), so the plugin pages are not mounted there. OpenWebif handles VPS itself.
- `timeradd`, `timerchange` and `timeraddbyeventid` all read `vpsplugin_enabled`, `vpsplugin_overwrite` (`== "1"`) and `vpsplugin_time` (`-1` = none) through `vpsparams` (`web.py:981-1011`, used at `:1113,1154,1175`), and set them on the timer (`models/timers.py:305-309,416-419`).
- `vpsparams` always yields all three, `False` for a param it is not sent. So `timerchange` without them **turns VPS off**.
- `/api/timerlist` reports `vpsplugin_enabled`, `vpsplugin_overwrite` and `vpsplugin_time` for every timer, `False`/`-1` where the timer has none, with or without the plugin (`models/timers.py:145-155,238-240`).
- The plugin's presence is in no `/api` answer. `HASVPS` (`defaults.py:207-213,266`, an import of `Plugins.SystemPlugins.vps`) reaches only the AutoTimer form: `/ajax/at` renders the checkbox `id="vps"` exactly when it is set (`ajax.py:349-351`, `views/ajax/at.tmpl:206-213`). `/api/vpschannels` is no signal: it reads `/etc/enigma2/vps.xml`, which the plugin writes only after a channel's PDC check (`models/timers.py:1114-1145`, `Vps_check.py`).

### 1.3 Meaning

| Box choice | `enabled` | `overwrite` | `VpsMode` | Effect |
| --- | --- | --- | --- | --- |
| No | 0 | 0 | `Off` | normal timer |
| Yes (safe mode) | 1 | 0 | `Safe` | records the planned window and extends it by what VPS reports |
| Yes | 1 | 1 | `Overwrite` | VPS controls start and stop |

Sources: the plugin's editor (`Modifications.py`: `yes_safe` sets only `enabled`, `yes` both) and OpenWebif's own mapping (`web.py:1002-1006`, `vps_pbox` `yes_safe`/`yes`; its editor sends `overwrite` 0 for safe mode, `views/ajax/edittimer.tmpl:250-251`). `enabled` false reads as `Off` whatever `overwrite` says.

- The box shows the choice only for `justplay != zap` and `type == once`.
- **VPS time** is used only when the timer has no event id or no name (`Vps.py:362`). It is the announced start the box looks for in the PDC data. Once the box finds the event, it sets the timer's `eit` and clears the time (`Vps.py:285-289`).
- EPG timers use their event id (`Vps.py:373`). This covers series recorded as one timer per episode, from the EPG or from AutoTimer.

## 2. Data shape

```kotlin
// enigma/Timer.kt
enum class VpsMode { Off, Safe, Overwrite }
data class TimerVps(val mode: VpsMode, val time: Long? = null) : Serializable

data class Timer(
    …,
    val allowDuplicate: String? = null, // OpenWebif round trip
    val autoAdjust: String? = null,     // OpenWebif round trip
    /** Null where the receiver did not report VPS. */
    val vps: TimerVps? = null
)
```

- One model for both web interfaces; it replaces OpenWebif's former `vpsEnabled`/`vpsOverwrite`/`vpsTime` strings.
- `vps == null` means **unknown**: a Dreambox list without the plugin, or a snapshot row stored before this change. Unknown VPS is never sent, so it never overwrites what the box has (§4).
- Parsing: Dreambox `TimerParser` reads the `e2vpsplugin_*` tags (absent tags: null); OpenWebif `OwifMapping` reads the list fields. A time `<= 0` is none.
- Writing: `TimerVps.params()` (`enigma/TimerVpsParams.kt`) yields all three params for both clients. `Off` sends `0`, `0`, `-1`.
- Profile: `vpsDefault: VpsMode = Off`, column `vps_default`.
- Form: `TimerEditForm.vps: VpsForm?`, null when the field is hidden. `VpsForm(mode, time, manual)`; `manual` decides whether the time pickers show.
- Room: version 11. `MIGRATION_10_11` adds `timer_list.vpsMode TEXT`, `timer_list.vpsTime INTEGER` and `profile.vps_default TEXT NOT NULL DEFAULT 'Off'`; schema `11.json`. (Version 10 is OpenWebif's `stream_mode`.)
- Backups carry `vpsDefault`; a missing or unknown value imports as `Off`, next to the `streamMode` upgrade.

## 3. Plugin presence

`ReceiverApi.plugins()` answers for the AutoTimer plugin (with its `api_version`) and the VPS plugin in one `ReceiverPlugins`:

- **Dreambox:** one `/web/external`; `autotimer` and `vpsplugin` entries.
- **OpenWebif:** `/autotimer/get` (AutoTimer, as before), then `/ajax/at` (VPS, §1.2). An HTTP error from `/ajax/at` counts as no VPS, so a broken page cannot hide AutoTimer.

`ReceiverPluginsRepository` keeps the one per-profile answer. It replaced `AutoTimerRepository`'s own presence cache; `ShellViewModel` and `EpgEventDetailViewModel` read AutoTimer presence from it.

- **Each resume.** A successful profile check (`ShellViewModel`, on return to the foreground and every profile switch) refreshes the answer. A plugin installed or removed while the app was away is seen the next time it is opened.
- **Lazily.** `AutoTimerRepository` and `TimerRepository` refresh while the profile has no answer yet. A failed request keeps the last answer.
- **While the app is open (Dreambox).** A removed plugin answers 404 on its first `/vpsplugin/...` request. The client reports it (`VpsPlugin.onMissing`, which marks it absent), and repeats the request once on the stock page. Later requests go without VPS. No request 404s twice.

## 4. Requests

`TimerRepository` builds each request's client with what it knows of the plugin (`ReceiverApiFactory.current(vpsPlugin = …)`). How VPS goes out is up to the client:

| | Dreambox, plugin there | Dreambox, no plugin | OpenWebif |
| --- | --- | --- | --- |
| Timer list | `/vpsplugin/web/timerlist` | `/web/timerlist` | `/api/timerlist` (always has VPS) |
| Add, edit (VPS known) | `/vpsplugin/web/timerchange` + 3 params | `/web/timerchange` | `timeradd` / `timerchange` + 3 params |
| Edit, VPS unknown | `/web/timerchange`: edits in place, keeps the box's VPS | same | reads `/api/timerlist`, sends the VPS listed for the old timer; a failed read fails the edit |
| Add by event | `/vpsplugin/web/timeraddbyeventid` + the profile default | `/web/timeraddbyeventid` | `timeraddbyeventid` + the profile default when the plugin is there |
| Enable switch | `timerchange` with the timer's VPS | `/web/timerchange` | `timertogglestatus` (VPS untouched) |

Without the plugin, every Dreambox request is byte for byte what it was before VPS.

## 5. Editor

`TimerFormActions` has `onVpsModeChange(mode)`, `onVpsDatePicked(...)` and `onVpsTimePicked(...)`; `TimerFormViewModel` implements them as edits of the working copy, which survives process death through `SavedStateHandle`. `TimerEditUiState.vpsPlugin` comes from `TimerRepository.hasVpsPlugin()` when the editor loads its choices.

- Eligibility, in one place (`TimerEditForm.from`): plugin there **and** VPS known **and** `!zap` **and** `repeated == 0`.
- On save (`normalized`): a field hidden for a zap or repeating timer saves as `Off`, as the box does. Without the plugin the timer keeps what the receiver listed.
- New timers (editor and EPG quick add): `TimerVps(profile.vpsDefault)` when the plugin is there, else null. Manual timers start with `time = begin`, as on the box.

Phone (`TimerEditScreen`), after **After event**:

```text
VPS                       [ Yes (safe mode) ▾ ]
  Requires VPS to be enabled in the receiver's VPS settings.
VPS time                  [ 02.10.2026 ] [ 20:15 ]     ← manual timers only
```

- `EditDropdownField` with the three choices; the note is its supporting text, shown when the mode is not `Off`.
- TV (`TvTimerEditor`) shows the same field and note.

Profile editor: **VPS default for new timers** (No / Yes (safe mode) / Yes), with "Used when the receiver has the VPS plugin." Shown for every profile; presence is known only for the active one.

Strings: `vps_modes` (in `VpsMode` order), `vps`, `vps_note`, `vps_date`, `vps_time`, `vps_default`, `vps_default_hint`, English and German.

## 6. Tests

- JVM: `TimerParserTest` on `app/test/resources/web/vps/timerlist.xml` (built from `src_py/web/timerlist.xml`); `DreamboxWebIfApiTest` (plugin pages, unknown VPS on the stock page, 404 fallback, no VPS without the plugin); `OpenWebifApiTest` (list mapping, params, `/ajax/at` presence on `owif/ajax_at*.html`, the re-read for unknown VPS); `ReceiverPluginsRepositoryTest`; `TimerRepositoryTest`; `TimerEditFormTest`; `TimerEditViewModelTest`; profile form, view model and backup tests; `AppDatabaseMigrationTest` (`profile` against schema 10 after 9→10; `profile` and `timer_list` against schema 11 after 10→11).
- Compose: `TimerEditScreenTest`, `TvTimerEditorHostTest`, `ProfileEditScreenTest`.

Run `workflow_dispatch` on `android-ci.yml` for the emulator job before merge, since this changes the editor UI.

## 7. Why not

- **The Dreambox plugin pages on OpenWebif.** Not mounted there (§1.2).
- **VPS presence from the OpenWebif timer list.** It reports `False` for every timer without the plugin too.
- **Leaving the VPS params out on OpenWebif when unknown.** `timerchange` would turn VPS off.
- **A separate VPS presence cache.** Presence belongs with the AutoTimer answer: one per-profile answer, one refresh.

## 8. Open

- **Box check.** Create, edit and quick-add with each mode on a Dreambox with the plugin and one without, and on an OpenWebif box with and without it. Confirm the list tags and the `-1` time, that a rename keeps VPS, that a removed plugin answers 404 on `/vpsplugin/web/*` after the GUI restart, and that `/ajax/at` shows `id="vps"` only with the plugin.
- **OpenWebif images without the classic views.** `/ajax/at` renders `views/ajax/at.tmpl`; an image that ships only the responsive views would answer with an error, and VPS would stay hidden there.
