# Linear stack autopilot

1. Define the ordered change stack and acceptance gate for every layer.
2. Build each layer on the verified previous layer. Keep one coherent linear base and avoid parallel edits to shared stack state.
3. Verify each layer before adding the next; repair at the lowest layer that explains a failure.
4. Deliver the full reviewed stack without landing it unless the operator explicitly requested shipping.
5. Report stack order, head revisions, per-layer proof, and any assumptions the operator must check before landing.
