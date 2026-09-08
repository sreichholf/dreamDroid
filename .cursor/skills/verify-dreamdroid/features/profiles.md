# Profiles

Profiles lets a user see connection profiles, open the seeded Demo profile, add a new profile, and jump to the list from the navigation drawer header.

## Sub-features

- `profiles-first-start` shows the Profiles screen after a clear-data launch.
- `profiles-demo` lists the seeded `Demo` profile.
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

- **First start.** After launch, a Changelog dialog appears on a fresh install. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py wait-text "Changelog"` then `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py back`. Then wait for the seeded profile, not the word Profiles: `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py wait-text "Demo"`. The Profiles list shows `Demo`.
- **Demo row.** Confirm the seeded profile. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py contains "Demo"`. The list shows `Demo`.
- **Add Profile.** Choose the add FAB. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --resource-id "net.reichholf.dreamdroid.debug:id/fab_main"`. The form shows `Profile name` and `Hostname or IP`.
- **Drawer entry.** Return to Profiles if needed, open the drawer, and choose the profile header. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --desc "Open navigation drawer"` then `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py tap --resource-id "net.reichholf.dreamdroid.debug:id/drawer_profile"`. Profiles is visible again with `Demo`.
- **Proof.** Capture the list with Demo visible. Run `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py dump --path .cursor/skills/verify-dreamdroid/artifacts/profiles/ui.xml` and `python .cursor/skills/verify-dreamdroid/scripts/verify-dreamdroid.py screenshot --path .cursor/skills/verify-dreamdroid/artifacts/profiles/screen.png`. Both artifacts show `Profiles` and `Demo`.

## Gotchas

- A fresh `--clear-data` launch shows Changelog before Profiles. Dismiss it with `back`. Do not `wait-text "Profiles"` while Changelog is open: the changelog body contains the word Profiles.
- Autodiscovery needs LAN multicast and is not part of this feature's pass criteria.
- Saving a new profile against a dead host still creates the row; proving connectivity is a different feature.
- Do not treat a connection-error snackbar as a failed Profiles list. The list can be valid while the box is down.
