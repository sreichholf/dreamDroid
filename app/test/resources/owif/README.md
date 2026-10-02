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
| `getcurrent_iptv.json` | `/api/getcurrent` | same; `models/services.py:176,1020-1031`; `web.py:1749-1763` | IPTV ref quoted to `%253a`; `now` and `next` are `X` rows (`epg.py:226-230` asks with `IBDCTSERNWX`): `begin_timestamp` 0, title `N/A`, `id` null; `provider` `N/A` for a service without one (`models/services.py:126-128`) |
| `getcurrent_recording.json` | `/api/getcurrent` | `web.py:1765-1777` | while a recording plays, `now` carries its own title and descriptions, not escaped: a literal `&amp;` stays |
| `signal.json` | `/api/signal` | `base.py:187-190`, `web.py:202-218`, `models/info.py:661-702` | `snr_db` a dB string (`:694`) |
| `signal_nodb.json` | `/api/signal` | `models/info.py:690-691` | `snr_db` repeats the percent as a number: no dB |
| `missing_parameter.json` | any handler with mandatory arguments | `web.py:81-96` | `result: false` with a `message` |
| `error404.html` | a handler that returns nothing, or an unknown page | `base.py:106-117,211-213` | HTML body with HTTP 404 |

Inference, not read in OpenWebif: the shape of the `X` rows (all event fields null but the
service) comes from enigma2's `eEPGCache::lookupEvent`, which is not in the OpenWebif tree.
