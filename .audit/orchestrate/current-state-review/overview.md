# Current-state review of dreamDroid `main`

Read-only program. Coordinator owns briefs and the rollup. Cloud workers own slice reviews. No PRs, no code edits.

Done predicate: all 20 units have a terminal report (`PASS`, `ISSUES`, or `BLOCKED`) and the coordinator has published one in-chat rollup.

Tracks:

- feature. MultiEPG and the nav/menu/tools chassis that was just adjusted.
- phone-ui. Compose destinations after the Kotlin rewrite.
- platform. Enigma2 client, Room, video, TV hub, widgets.
- hygiene. Leftover Java, tests/CI, SSL, dead XML.

Wall-clock budget: this session. Stop spawning new slices at 70% only if drain is stalled. Landing is skipped. Verifiers per slice are skipped. The coordinator synthesizes. A different-family trail review runs at close.
