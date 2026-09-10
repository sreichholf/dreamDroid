# dreamDroid verification map

This directory is the maintained source for verifying the user-facing behavior of dreamDroid. Read the index before driving the app, then use the matching feature file as the recipe.

## Baseline preconditions

- Prove Compose screens with `./gradlew.bat :app:connectedGoogleDebugAndroidTest` (see `AGENTS.md`). Do not tap the emulator in a loop.
- googleDebug is installed: `./gradlew.bat :app:installGoogleDebug`.
- For an optional look, launch with `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py launch --clear-data`. Dismiss the first-install Changelog with `back` before driving other screens.
- Package `net.reichholf.dreamdroid.debug` is in the foreground. `doctor` must pass.
- Device language English unless a recipe says to match a dump instead.
- Never drive a release install (`net.reichholf.dreamdroid`).
- Receiver-backed features need a reachable Enigma2 WebInterface on the active profile. First-start seeds profile `Demo` at `dreamdroid.org`; that is not an offline bouquet/EPG mock.

## Driving conventions

- Start every recipe from the baseline state unless its preconditions say otherwise.
- Prefer resource-id and visible text over bounds.
- Treat every command as literal.
- Restore debug app data with `--clear-data` on the next launch after a mutation. Do not remove proof artifacts during cleanup.

## Proof and skip reporting

- Capture the user action and the resulting state, not only the final screen.
- UI proof includes a UI dump and a screenshot with the debug app identity visible (`dreamDroid DBG`).
- Mutation proof includes a second user-visible view of the stored value.
- Record the feature ID and entry point used with every artifact.
- Report an unreachable path with the attempted command and the unmet precondition (including `Connection error` when the box is down).
- Do not report a skipped entry point as verified through a different path.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with verify-dreamdroid` starts with `Preconditions:` and uses labeled bullets that pair each user action with an exact command and observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

Keep implementation details out of the map. Name only user paths, stable handles, required state, commands, and observable proof.

## Features

- [Profiles](./profiles.md) covers first-start Profiles, the Demo row, Add Profile, and the drawer profile header.
- [About](./about.md) covers the About dialog from Settings (no receiver required).
- [TV and Movies](./tv-and-movies.md) covers drawer TV & Movies and the TV/Radio/Movies/Timer bar.
- [Zap](./zap.md) covers the Zap screen and changing channel.
- [Virtual Remote](./virtual-remote.md) covers opening the on-screen remote.
