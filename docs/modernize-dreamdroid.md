# Modernize dreamDroid

Phone users get a **Compose Material 3** Enigma2 remote. TV gets a **Compose** hub (`androidx.tv`), not Leanback browse. New types are **Kotlin** + coroutines. Proof is **instrumented Compose tests**, not tap loops. Rewrite trunk is **`main`** (`master` is last 1.15 — do not merge them).

No permanent keepers. Old “keep DialogFragments / RemoteViews / HttpURLConnection / Leanback / Java overlay” calls were sequencing, not freezes. When a leftover surface is touched, move it to the current Android/Compose default. Do not preserve a legacy chassis only because an older plan said keep.

Floor: minSdk 26, JDK 25, debug package `net.reichholf.dreamdroid.debug`. Agent rules and how to run tests: [`AGENTS.md`](../AGENTS.md). MultiEPG product design: [`docs/multiepg.md`](multiepg.md). Offline cache + unified errors: [`docs/offline-and-errors.md`](offline-and-errors.md) (one PR per slice).

## Done

Phone screens are Kotlin Compose **NavHost destinations** (drawer, hub, EPG, forms, remote). Dialogs and detail sheets are Compose `AlertDialog` / `ModalBottomSheet` / Navigation `dialog`s — no DialogFragment chassis. HTTP is coroutines + typed `EnigmaClient` over OkHttp; AsyncTask/Loaders are gone. Profiles live in Room (`dreambox`); legacy SQLite is migrate/restore only. Widgets are Glance with `AndroidRemoteViews` only for the dense RCU grid. Player stays **libVLC** with Compose overlay chrome (not Media3). TV hub is Compose; Leanback browse is gone (`leanback` remains for overlay `HorizontalGridView`). Material 3 P0–P2 UX is [#430](https://github.com/sreichholf/dreamDroid/pull/430). Tablet hub destinations use a start-side `NavigationRail` (`layout-sw720dp` `shell_destination_rail`); phone keeps bottom `NavigationBar` / FAB dodge.

## Still to do

One PR per item unless asked otherwise. Do not fold these into unrelated chrome work.

| Item | Notes |
| --- | --- |
| Modal mutation progress | `IndeterminateProgressHost` is a blocking `BasicAlertDialog` spinner (profile detect, timer/movie save/delete, share import). Prefer in-content progress or a snackbar, one surface at a time. |
| Video overlay shell | `VideoOverlayFragment` + `VideoActivity` are not a phone NavHost leaf. Zap list still uses Leanback `HorizontalGridView`. Keep libVLC unless asked for Media3. |
| Glance-only widget | Dense RCU keys still go through `AndroidRemoteViews`. Full Glance only if it can express that grid. |
| Drop `DatabaseHelper` | Migrate-only leftover for pre-Room backups. Delete when the operator accepts migrate-from-backup-only (or no install still needs the file). |
| Operator usertests | Phone + real Android TV / box. File bugs; no drive-by refactors. Then a bugfix pass, one PR per fix. In-tree TV gate: `ComposeTvHubStubTest` / `Chrome` / `ServiceRow` / `MovieRow`. Box: cold-start Compose hub (not Leanback); D-pad headers↔rows; Settings Reload/Preferences/Profile; bouquet → stream; movie location lazy load → file stream; profile switch reloads. |
| Offline cache + unified errors | Use-driven Room + session Online/Offline/Unavailable. Plan: [`docs/offline-and-errors.md`](offline-and-errors.md). One PR per slice; do not implement as a single mega-PR. |

**Keep:** service-row / now-playing progress is a transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)). Do not “restore” a Material track.

## Out of scope until asked

Enigma2 **server**, VLC **codec / stream protocol**, Media3/ExoPlayer swap, deleting the home-screen widget, TV v1 MultiEPG, merging `master` into `main`.
