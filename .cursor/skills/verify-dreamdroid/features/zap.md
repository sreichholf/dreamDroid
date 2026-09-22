# Zap

Zap lists channels the user can switch the receiver to.

## Sub-features

- `zap-open` opens Zap from the drawer.
- `zap-channel` taps a channel and the receiver changes service.

## How to get to it (user POV)

- Open the navigation drawer and choose `Zap`.
- From a service row overflow, choose zap (popup `Zap`) when that menu is offered.

## Driving it with verify-dreamdroid

Preconditions:

- `doctor` passes.
- `zap-open` can be attempted without a box; an empty or error state is not a channel change.
- `zap-channel` requires a reachable receiver and at least one channel row.

- **Open drawer.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --desc "Open navigation drawer"`.
- **Open Zap.** Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --text "Zap"`. The destination is Zap (toolbar or heading includes `Zap`).
- **Change channel (receiver).** Tap a visible channel name with `tap --text "<channel>"`. Then open `TV & Movies` and read the `Now` strip; the headline is the channel just chosen.
- **Proof.** Dump and screenshot Zap before the tap and the hub `Now` strip after: `.cursor/skills/verify-dreamdroid/artifacts/zap/`.

## Gotchas

- Opening Zap is not proof of a zap. The hub `Now` strip must show the new service.
- Zap needs a saved profile that can reach the receiver. A clear-data launch stops on the setup wizard.
- Instant-zap settings can change whether a tap zaps immediately; still confirm the `Now` strip.
