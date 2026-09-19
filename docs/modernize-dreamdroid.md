# Modernize dreamDroid

Phone users get a **Compose Material 3** Enigma2 remote. TV gets a **Compose** hub (`androidx.tv`), not Leanback browse. New types are **Kotlin** + coroutines. Proof is **instrumented Compose tests**, not tap loops. Rewrite trunk is **`main`** (`master` is last 1.15 — do not merge them).

No permanent keepers. Old “keep DialogFragments / RemoteViews / HttpURLConnection / Leanback / Java overlay” calls were sequencing, not freezes. When a leftover surface is touched, move it to the current Android/Compose default. Do not preserve a legacy chassis only because an older plan said keep.

Floor: minSdk 26, JDK 25, debug package `net.reichholf.dreamdroid.debug`. Agent rules and how to run tests: [`AGENTS.md`](../AGENTS.md). MultiEPG product design: [`docs/multiepg.md`](multiepg.md). Offline cache + unified errors: phone shipped — [`docs/offline-and-errors.md`](offline-and-errors.md) (TV hub session/cache shipped; widget still follows).

## Done

Phone screens are Kotlin Compose **NavHost destinations** (drawer, hub, EPG, forms, remote). Dialogs and detail sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s — no DialogFragment chassis. HTTP is coroutines + typed `EnigmaClient` over OkHttp; AsyncTask/Loaders are gone. Profiles live in Room (`dreambox`); legacy SQLite is migrate/restore only. Widgets are Glance with `AndroidRemoteViews` only for the dense RCU grid. Player stays **libVLC** with Compose overlay chrome (not Media3). TV hub is Compose; Leanback browse and the overlay zap `HorizontalGridView` are gone (`uses-feature android.software.leanback` stays for the Android TV launcher). Material 3 P0–P2 UX is [#430](https://github.com/sreichholf/dreamDroid/pull/430). Phone **offline cache + unified errors** (plan slices 1–7) and **phone operator usertest** are verified (2026-09-19). TV hub session, Room cache, and Online-only streaming have shipped; widget offline still follows the phone shell. Mutation progress is an in-content `LinearProgressIndicator` (`IndeterminateProgressHost`), not a blocking dialog. Tablet hub destinations use a start-side `NavigationRail` (`layout-sw720dp` `shell_destination_rail`); phone keeps bottom `NavigationBar` / FAB dodge.

## Still to do

One PR per item unless asked otherwise. Do not fold these into unrelated chrome work.

| Item | Notes |
| --- | --- |
| Video overlay shell | `VideoOverlayFragment` + `VideoActivity` are not a phone NavHost leaf. TV zap list is Compose Live TV cards (same hub row). Keep libVLC unless asked for Media3. |
| Glance-only widget | Dense RCU keys still go through `AndroidRemoteViews`. Full Glance only if it can express that grid. |
| Drop `DatabaseHelper` | Migrate-only leftover for pre-Room backups. Delete when the operator accepts migrate-from-backup-only (or no install still needs the file). |
| Operator usertests | **Phone verified** (2026-09-19). Tablet + real Android TV / box still open. File bugs; no drive-by refactors. Then a bugfix pass, one PR per fix. In-tree TV gate: `ComposeTvHubStubTest` / `Chrome` / `ServiceRow` / `MovieRow` / `TvMultiEpgScreenTest`. Box: cold-start Compose hub (not Leanback); D-pad headers↔rows; Settings Reload/Preferences/Profile (Profile is a list: switch/add/edit/delete); drawer **MultiEPG** OK → D-pad grid → detail stream/timer; bouquet → stream; movie location lazy load → file stream; profile switch reloads. |

**Keep:** service-row / now-playing progress is a transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)). Do not “restore” a Material track.

## Out of scope until asked

Enigma2 **server**, VLC **codec / stream protocol**, Media3/ExoPlayer swap, deleting the home-screen widget, merging `master` into `main`.
