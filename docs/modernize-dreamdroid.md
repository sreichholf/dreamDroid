# Modernize dreamDroid

Phone users get a **Compose Material 3** Enigma2 remote. TV gets a **Compose** hub (`androidx.tv`), not Leanback browse. New types are **Kotlin** + coroutines. Proof is **instrumented Compose tests**, not tap loops. Rewrite trunk is **`main`** (`master` is last 1.15 — do not merge them).

No permanent keepers. Old “keep DialogFragments / RemoteViews / HttpURLConnection / Leanback / Java overlay” calls were sequencing, not freezes. When a leftover surface is touched, move it to the current Android/Compose default. Do not preserve a legacy chassis only because an older plan said keep.

Floor: minSdk 26, JDK 25, debug package `net.reichholf.dreamdroid.debug`. Agent rules and how to run tests: [`AGENTS.md`](../AGENTS.md). MultiEPG product design: [`docs/multiepg.md`](multiepg.md). Offline cache + unified errors: phone shipped — [`docs/offline-and-errors.md`](offline-and-errors.md) (TV hub session/cache shipped; widget still follows).

## Done

Phone screens are Kotlin Compose **NavHost destinations** (drawer, hub, EPG, forms, remote). Dialogs and detail sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s — no DialogFragment chassis. HTTP is coroutines + typed `EnigmaClient` over OkHttp; AsyncTask/Loaders are gone. Profiles live in Room (`dreambox`); legacy SQLite is migrate/restore only. Widgets are Glance with `AndroidRemoteViews` only for the dense RCU grid. Player stays **libVLC** with Compose overlay chrome via `VideoOverlayController` (no Fragment). TV hub is Compose; Leanback browse and the overlay zap `HorizontalGridView` are gone (`uses-feature android.software.leanback` stays for the Android TV launcher). Material 3 P0–P2 UX is [#430](https://github.com/sreichholf/dreamDroid/pull/430). Phone **offline cache + unified errors** (plan slices 1–7) and **phone operator usertest** are verified (2026-09-19). **Tablet** and **TV / box** operator usertests are verified (2026-09-21) except newly added TV timer surfaces. TV hub session, Room cache, and Online-only streaming have shipped; widget offline still follows the phone shell. Mutation progress is an in-content `LinearProgressIndicator` (`IndeterminateProgressHost`), not a blocking dialog. Tablet hub destinations use a start-side `NavigationRail` (`layout-sw720dp` `shell_destination_rail`); phone keeps bottom `NavigationBar` / FAB dodge.

## Still to do

One PR per item unless asked otherwise. Do not fold these into unrelated chrome work.

| Item | Notes |
| --- | --- |
| Operator usertests | **Phone verified** (2026-09-19). **Tablet verified** (2026-09-21). **TV / box verified** (2026-09-21) except newly added timer surfaces. Phone drawer EPG and the bouquet service list remember list vs MultiEPG. Remaining box pass: hub **Timers** list add/edit/delete (`TvTimerHost`); bouquet service INFO/MENU overlay (stream / set / edit); MultiEPG detail set/edit (`TvTimerEditorHost`). File bugs; no drive-by refactors. Then a bugfix pass, one PR per fix. In-tree timer gate: `TvTimerHostTest` / `TvTimerListScreenTest` / `TvServiceTimerOverlayTest`. |
| 2.0 bugs | GitHub label `2.0-bug`. One PR per fix. Do not treat `feature` issues as ship blockers. Do not close more tickets unless asked. |
| Pre-release | Not GitHub-issue work. 1.15→2.0 Room/profile migration tests; minified `googleRelease` smoke; targetSdk 37 `ACCESS_LOCAL_NETWORK`; dual HTTP/shell leftovers (do not fold overlay-shell into unrelated PRs); `volume_control` swallows volume keys (`MainActivity.onKeyDown` TODO plus `onKeyUp` always consumes them); `TvTimerEditorHostTest` covers `TvTimerEditorHost`. |

**Keep:** service-row / now-playing progress is a transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)). Do not “restore” a Material track. Widget stays Glance + `AndroidRemoteViews` for the dense RCU grid — a Glance-only rewrite does not pay for that layout. `DatabaseHelper` stays a read-only leftover-file importer for pre-Room installs and cloud snapshots — Room has not shipped on Play yet; do not drop the path. It never creates `dreamdroid`.

## Out of scope until asked

Enigma2 **server**, VLC **codec / stream protocol**, Media3/ExoPlayer swap, deleting the home-screen widget, merging `master` into `main`.
