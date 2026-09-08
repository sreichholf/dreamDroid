# Virtual Remote

Virtual Remote is the on-screen Dreambox remote: number keys, OK, power, and color keys.

## Sub-features

- `remote-open` opens Virtual Remote from the drawer.
- `remote-ok` shows the `OK` key (and typically `Power`).

## How to get to it (user POV)

- Open the navigation drawer and choose `Virtual Remote`.

## Driving it with verify-dreamdroid

Preconditions:

- `doctor` passes.
- Opening the remote does not require a receiver. A keypress that should control the box does.

- **Open drawer.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --desc "Open navigation drawer"`.
- **Open remote.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --text "Virtual Remote"`. Keys such as `OK` are visible.
- **OK visible.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py contains "OK"`.
- **Proof.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py dump --path .cursor/skills/verify-dreamdroid/artifacts/virtual-remote/ui.xml` and `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py screenshot --path .cursor/skills/verify-dreamdroid/artifacts/virtual-remote/screen.png`. Artifacts show the remote keypad.

## Gotchas

- On phone, Virtual Remote may open in a separate no-title activity. `doctor` must still see `net.reichholf.dreamdroid.debug`.
- Pressing `Power` or other keys against a dead host is not a pass for this feature; visibility of the keypad is.
- Do not kill `net.reichholf.dreamdroid` (release) if this activity is mistaken for a second app.
