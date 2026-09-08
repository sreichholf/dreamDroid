# TV and Movies

TV and Movies is the bouquet/recordings/timers hub: bouquet tabs on TV or Radio, plus Movies and Timer lists.

## Sub-features

- `services-open` opens TV & Movies from the drawer.
- `services-tv-bar` shows the TV, Radio, Movies, and Timer destinations.
- `services-list` shows bouquet or service rows when the receiver answers.

## How to get to it (user POV)

- Open the navigation drawer and choose `TV & Movies`.
- After a successful profile check, the app may land here instead of Profiles.

## Driving it with verify-dreamdroid

Preconditions:

- `doctor` passes.
- For `services-list`, the active profile's host must accept Enigma2 WebInterface requests. If it does not, record the error dump and skip `services-list` only.

- **Open drawer.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --desc "Open navigation drawer"`.
- **Open TV & Movies.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --text "TV & Movies"`. The bottom bar includes `TV`, `Radio`, `Movies`, and `Timer`.
- **Bar destinations.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --text "Radio"` then `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --text "TV"`. The selected destination changes; the screen stays in TV & Movies.
- **List (receiver).** If no connection error is shown, the list or tabs contain bouquet or service names. If a connection error is shown, dump it and skip this sub-feature.
- **Proof.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py dump --path .cursor/skills/verify-dreamdroid/artifacts/tv-and-movies/ui.xml` and `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py screenshot --path .cursor/skills/verify-dreamdroid/artifacts/tv-and-movies/screen.png`. Artifacts show `TV`, `Radio`, `Movies`, and `Timer`.

## Gotchas

- A hard connection error blocks this screen from loading lists. That is a skip, not a pass.
- Bottom bar items are hidden until TV & Movies is the current destination.
- `TV & Movies` is the drawer label; do not search for `Services`.
