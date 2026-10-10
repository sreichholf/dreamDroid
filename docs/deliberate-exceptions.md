# Deliberate exceptions

Places where dreamDroid departs from the architecture in [`AGENTS.md`](../AGENTS.md) on purpose. Do not "fix" these. Each states when to revisit it; when that happens, change the code and remove the row in the same PR.

| Exception | Reason | Revisit when |
| --- | --- | --- |
| Service-row progress: transparent track, `StrokeCap.Butt`, no stop indicator ([#421](https://github.com/sreichholf/dreamDroid/pull/421)) | Design choice for dense rows. Keep a progress semantics description for TalkBack. | Design changes. |
| Widget uses `AndroidRemoteViews` for the RCU grid | A Glance-only grid at that density is awkward and does not pay for itself. | Glance gains an equivalent layout. |
| `DatabaseHelper` as read-only importer of old `dreamdroid` SQLite | Installs that skipped 1.15 and old backups still carry the file. | One release after 2.0 ships on Play. |
| libVLC, not Media3 | Receiver streams (MPEG-TS with MPEG-2, AC3, varied codecs) need decoding that does not depend on device hardware. Cost: `libvlc-all` dominates APK size and is a native dependency to keep current. | Media3 covers the receiver formats on target devices. |
| `usesCleartextTraffic` | Users enter arbitrary LAN hosts over plain HTTP; a network security config cannot scope cleartext to private networks. | Never for arbitrary LAN hosts. |
| `Toast` in `WidgetRemoteRequest` and `ShareActivity` | Broadcast with no UI, and a share flow that finishes its activity; no window survives to host a Snackbar. | — |
| `BaseActivity`, `ShareActivity`, the TV `MainActivity`, and the widget configuration on `AppCompatActivity` | `AppCompatDelegate.setDefaultNightMode` for the in-app theme setting; the platform `UiModeManager.setApplicationNightMode` needs API 31. `VideoActivity` is on AppCompat too but no longer needs it for night mode: move it to `ComponentActivity` when you work on it, if nothing else (Material Components View theme, dialogs) still needs AppCompat. | minSdk reaches 31. |
