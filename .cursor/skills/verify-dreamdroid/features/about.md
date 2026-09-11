# About

About shows the installed version, the GPL notice, and the source link in a dialog, without talking to a receiver.

## Sub-features

- `about-open` opens the About dialog from Settings.
- `about-identity` shows a version string and `About` as the dialog title.

## How to get to it (user POV)

- Open the navigation drawer, choose `Settings`, then `About` at the bottom of the list.

## Driving it with verify-dreamdroid

Preconditions:

- A device or emulator in `device` state.
- No Enigma2 box is required.

- **Proof.** Run `./gradlew.bat :app:connectedGoogleDebugAndroidTest`. `AboutScreenTest` and `AboutDialogHostTest` must pass. Night `LocalContentColor` equals `onSurface` (light) inside `DreamDroidTheme`, including when About is hosted as a Navigation Compose `dialog`.
- **Look (optional).** `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py launch`, open the drawer, tap `Settings`, scroll to `About`, then `screenshot --path .cursor/skills/verify-dreamdroid/artifacts/about/screen.png`.

## Gotchas

- Instrumented tests are the pass criteria. A screenshot is not a substitute.
- About opens as a Navigation Compose dialog from Settings (and the drawer), not a drawer destination.
- Licenses is a separate button inside the dialog and is not this feature's pass criteria.
