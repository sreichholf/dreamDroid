# OpenWebif fixtures

Synthetic answers, not captures from a real box. Each is built from the handler and model that
produce it in E2OpenPlugins/e2openplugin-OpenWebif at commit `e46534f` (paths under
`plugin/controllers/`). JSON is written as `json.dumps(data, indent=1)` writes it (`base.py:221-224`),
with `ensure_ascii`. A real capture replaces its fixture (docs/openwebif.md §3).

| Fixture | Endpoint | Source | Quirk shown |
|---------|----------|--------|-------------|
| `deviceinfo.xml` | `/web/deviceinfo` | `views/web/deviceinfo.tmpl`, `models/info.py` `getInfo` | `e2webifversion` is `OWIF 1.5.2` (`defaults.py:30`) |
| `deviceinfo.json` | `/api/deviceinfo` | `web.py:1408-1421`, `models/info.py:200-582` | `fp_version` null; `dhcp` a JSON bool |
| `getservices.json` | `/api/getservices` | `web.py:502-524`, `models/services.py:550-631` | `servicename` not HTML-escaped (`:615`), a literal `&amp;` stays; `\u0086`/`\u0087` control characters; a marker; no hidden services without `hidden=1` (`:606`) |
| `epgnownext.json` | `/api/epgnownext` | `web.py:1547-1556`, `models/services.py:955-993` | escaped title and texts; ZDF lacks next and Sky lacks now: rows with null `begin_timestamp` from enigma2's `X` flag (`:964`), see `web.py:1547` "TODO: fix missing now or next"; `info` added by the handler |
| `epgbouquet.json` | `/api/epgbouquet` | `web.py:1440-1460`, `models/services.py:879-916` | an image that answers a range: ZDF has an event ending at `time` before the one running; Sky's only event starts after `time` |
| `epgmulti.json` | `/api/epgmulti` | `web.py:1465-1496`, `models/services.py:879-916` | |
| `epgservice.json` | `/api/epgservice` | `web.py:1636-1654`, `models/services.py:799-872` | escaped texts; `\x8a` in `longdesc` turned into `\n` by `convertDesc` (`:101`) |
| `epgservice_empty.json` | `/api/epgservice` | `models/services.py:852-870` | the filler row for an event without id: `begin_timestamp` 0, title `N/A` |
| `epgsearch.json` | `/api/epgsearch` | `web.py:1570-1600`, `models/services.py:1085-1139` | `now_timestamp` null; escaped `&` in a title |
| `getcurrent.json` | `/api/getcurrent` | `web.py:1706-1801`, `models/services.py:133-180,996-1035` | `info.name` always escaped (`:162`) |
| `getcurrent_iptv.json` | `/api/getcurrent` | same; `models/services.py:176,1020-1031`; `web.py:1749-1763` | IPTV ref quoted to `%253a`; `now` is an `X` row and `next` the handler's filler, both `begin_timestamp` 0; `id` null |
| `getcurrent_recording.json` | `/api/getcurrent` | `web.py:1765-1777` | while a recording plays, `now` carries its own title and descriptions, not escaped: a literal `&amp;` stays |
| `signal.json` | `/api/signal` | `base.py:187-190`, `web.py:202-218`, `models/info.py:661-702` | `snr_db` a dB string (`:694`) |
| `signal_nodb.json` | `/api/signal` | `models/info.py:690-691` | `snr_db` repeats the percent as a number: no dB |
| `timerlist.json` | `/api/timerlist` | `web.py:1028-1045`, `models/timers.py:78-253` | numbers and Python bools as JSON literals; `eit` null; `dirname` `"None"` without a folder (`:123-126`); `descriptionextended` `N/A` without EPG; `logentries` a list of lists; Tatort with VPS on, `allow_duplicate` 0 and `autoadjust` 1, heute journal on an image without auto-adjust (`autoadjust` -1, `:117-121`) |
| `timeradd.json` | `/api/timeradd` | `web.py:1184-1202`, `models/timers.py:256-350` | |
| `timer_name_empty.json` | `/api/timeradd`, `/api/timerchange` | `web.py:81-96,1198,1264` | an empty `name` is refused |
| `timer_conflict.json` | `/api/timeradd` | `models/timers.py:284-304` | `result: false` with `conflicts` |
| `timerchange.json` | `/api/timerchange` | `web.py:1233-1268`, `models/timers.py:395-492` | edits in place; no `deleteOldOnSave` |
| `timertogglestatus.json`, `timertogglestatus_enabled.json` | `/api/timertogglestatus` | `web.py:1270-1302`, `models/timers.py:519-561` | flips `disabled`; the answer carries the new state |
| `timertogglestatus_conflict.json` | `/api/timertogglestatus` | `models/timers.py:526-535` | enabling a timer that conflicts is refused |
| `timeraddbyeventid.json` | `/api/timeraddbyeventid` | `web.py:1204-1231`, `models/timers.py:353-392` | |
| `timerdelete.json` | `/api/timerdelete` | `web.py:1304-1343`, `models/timers.py:495-516` | an `eit` would match before begin and end (`:500`) |
| `timercleanup.json` | `/api/timercleanup` | `web.py:1345-1358`, `models/timers.py:564-569` | |
| `movielist.json` | `/api/movielist` | `web.py:760-774`, `models/movies.py:107-321` | no `result` key; one `tag` (`:121,213-214`); `dirname` read as Latin-1 with `%uXXXX` (`:115-118`); `\u0086`/`\u0087` left in names on Python 3 (`:245-246`); `length` `?:??` when unknown |
| `moviedelete.json` | `/api/moviedelete` | `web.py:832-850`, `models/movies.py:440-525` | |
| `getlocations.json` | `/api/getlocations` | `web.py:433-447`, `models/locations.py:15-20` | |
| `gettags.json` | `/api/gettags` | `web.py:965-978`, `models/movies.py:753-773` | |
| `mediaplayerplay_missing.json` | `/api/mediaplayerplay` | `web.py:1994-2000`, `models/mediaplayer.py:106-112` | the MediaPlayer plugin is missing |
| `file_not_found.txt` | `/file?file=` | `file.py:58-59` | HTTP 200 text without `Content-Disposition`, which a real file has (`:80-83`) |
| `vol.json` | `/api/vol` | `web.py:220-256`, `models/volume.py:15-46` | `current` a number, `ismute` a JSON bool |
| `powerstate_before.json`, `powerstate_after.json` | `/api/powerstate` | `web.py:360-379`, `models/control.py:207-243` | the answer to `newstate` is the state from before the action (`:208,232-235`); asking again gives the new one |
| `zap.json` | `/api/zap` | `web.py:296-321`, `models/control.py:111-166` | |
| `remotecontrol.json` | `/api/remotecontrol` | `web.py:323-358`, `models/control.py:169-205` | `rcu` and `type=long` as on the Dreambox |
| `message.json` | `/api/message` | `web.py:709-743`, `models/message.py:29-42` | |
| `sleeptimer.json` | `/api/sleeptimer` | `web.py:2080-2138`, `models/timers.py:874-904` | an InfoBar sleep timer: `minutes` a number, the configured time; `remaining` in seconds |
| `sleeptimer_powertimer.json` | `/api/sleeptimer` | `models/timers.py:912-931` | a power timer: `minutes` a string (`:922`) |
| `sleeptimer_set.json` | `/api/sleeptimer?cmd=set` | `models/timers.py:966-1020` | a change on the InfoBar path. A change on the power timer path always fails with `result: false`, "SleepTimer error": the `time` argument shadows `time()` (`:1027,1069`) |
| `sleeptimer_standby.json` | `/api/sleeptimer?cmd=set` | `web.py:2124-2136`, `models/timers.py:940-946` | a refusal is the current timer with a `message` starting `ERROR`, and no `result` |
| `getservices_hidden.json` | `/api/getservices?hidden=1` | `web.py:502-524`, `models/services.py:550-631` | the hidden service (flag 512) is listed only with `hidden=1` (`:606`) and does not count for `pos` (`:604-605`) |
| `getsatellites.json` | `/api/getsatellites` | `web.py:2203-2205`, `models/services.py:370-434` | named "<satellite> - Services" and "<satellite> - New"; provider folders left out (`:388-390`) |
| `bouqueteditor_addbouquet.json` | `/bouqueteditor/api/addbouquet` | `BQE.py:46-48,76-84,419-424`, `BouquetEditor.py:89-134` | `{"Result": [ok, text]}`; the box appends " (TV)" |
| `bouqueteditor_addservice_duplicate.json` | `/bouqueteditor/api/addservicetobouquet` | `BQE.py:116-124`, `BouquetEditor.py:279-344` | a refusal; `<` and `&` stay as they are in JSON (the XML answer would not escape them, `BQE.py:50-60`) |
| `bouqueteditor_backup.json` | `/bouqueteditor/api/backup` | `BQE.py:196-204`, `BouquetEditor.py:586-626` | answers with the tar's name in `/tmp` |
| `error403.html` | any page | `plugin/httpserver.py:386-388` | with authentication off, a client outside the box's networks gets 403 |
| `missing_parameter.json` | any handler with mandatory arguments | `web.py:81-96` | `result: false` with a `message` |
| `error404.html` | a handler that returns nothing, or an unknown page | `base.py:106-117,211-213` | HTML body with HTTP 404 |

## AutoTimer plugin (`autotimer/`)

OpenWebif serves the AutoTimer plugin's own XML under `/autotimer` (`AT.py:140-195`). These
answers are built from the plugin's source, not from OpenWebif: oe-alliance `enigma2-plugins`
at `25c0dd8` (api_version 1.7) and opendreambox `enigma2-plugins` at `496657b` (1.6), under
`autotimer/src/`.

| Fixture | Endpoint | Source | Quirk shown |
|---------|----------|--------|-------------|
| `autotimer/get_17.xml` | `/autotimer/get` | oe-alliance `AutoTimerResource.py:623-677`, `AutoTimerSettings.py` | `api_version` 1.7; texts not XML-escaped; trimmed to two settings |
| `autotimer/get_16.xml` | `/autotimer/get` | opendreambox `AutoTimerResource.py:576-719` | `api_version` 1.6; trimmed to two settings |
| `autotimer/list_17.xml` | `/autotimer` | oe-alliance `AutoTimerConfiguration.py:538-860` (`webif=True`, `AutoTimer.py:199`) | `always_zap="1"` with no `justplay` (`:174,768-769`); `searchType="start"`; `encoding` on every timer |
| `autotimer/edit_17.xml` | `/autotimer/edit` | oe-alliance `AutoTimerResource.py:31-41,506-508` | `e2id` names the AutoTimer written |
| `autotimer/change_17.xml` | `/autotimer/change` | oe-alliance `AutoTimerResource.py:511-549` | empty `e2id` |

Inference, not read in OpenWebif:
- The shape of the `X` rows (all event fields null but the service) comes from enigma2's
  `eEPGCache::lookupEvent`, which is not in the OpenWebif tree.
- `error403.html` is Twisted's `resource.ErrorPage` markup; Twisted is not in the tree.
- A missing sleep timer on the power timer path makes the handler return nothing, which
  `base.py:210-213` turns into the HTML 404 of `error404.html`.
- The satellite folders' references and names in `getsatellites.json` come from enigma2's
  `FROM SATELLITES` listing and `nimmanager.getSatDescription`, which are not in the tree.
- `/autotimer/get` without the plugin is a 404 from Twisted's child lookup (`AT.py:150-152`
  returns before `putChild`); Twisted is not in the tree.
