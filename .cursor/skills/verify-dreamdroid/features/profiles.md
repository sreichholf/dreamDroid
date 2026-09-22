# Profiles

Profiles lets a user see connection profiles, add a profile, and jump to the list from the navigation drawer header. A fresh install shows the setup wizard instead of a seeded profile.

## Sub-features

- `profiles-first-start` shows the setup wizard (`Welcome!`) after a clear-data launch.
- `profiles-demo` is retired. The old `Demo` / `dreamdroid.org` row is not created.
- `profiles-add-open` opens the add-profile form from the FAB.
- `profiles-drawer` reaches Profiles from the drawer profile header.

## How to get to it (user POV)

- Cold-start the app after clearing debug data (first start).
- Open the navigation drawer and choose the profile header.
- Choose `Add Profile` on the Profiles screen.

## Driving it with verify-dreamdroid

Preconditions:

- `doctor` passes on `net.reichholf.dreamdroid.debug`.
- Launch used `--clear-data`.
- No Enigma2 box is required for these steps.

- **First start.** After a clear-data launch, wait for the wizard: `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py wait-text "Welcome!"`. The shell, changelog, and Profiles list stay closed until a profile is saved.
- **Add Profile.** After a profile exists, choose the add FAB. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --resource-id "net.reichholf.dreamdroid.debug:id/fab_main"`. The form shows `Profile name` and `Hostname or IP`.
- **Drawer entry.** This needs a saved profile. Open the drawer and choose the profile header. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --desc "Open navigation drawer"` then `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --resource-id "net.reichholf.dreamdroid.debug:id/drawer_profile"`. Profiles is visible again.
- **Proof.** Capture the list. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py dump --path .cursor/skills/verify-dreamdroid/artifacts/profiles/ui.xml` and `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py screenshot --path .cursor/skills/verify-dreamdroid/artifacts/profiles/screen.png`.

## Gotchas

- A fresh `--clear-data` launch shows `Welcome!`, not Profiles and not the changelog. Do not `wait-text "Profiles"` or `wait-text "Demo"`.
- Autodiscovery needs LAN multicast and is not part of this feature's pass criteria.
- Saving a new profile against a dead host still creates the row; proving connectivity is a different feature.
- Do not treat a connection-error snackbar as a failed Profiles list. The list can be valid while the box is down.
