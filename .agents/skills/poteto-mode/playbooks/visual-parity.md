# Visual parity

1. Lock viewport, fonts, data, theme, device scale, and animation state so comparisons are meaningful.
2. Capture the reference and current rendering from the same surface.
3. Compare geometry first, then typography, spacing, color, and effects. Use image/pixel/overlay metrics when available.
4. Change one causal styling/layout cluster at a time and recapture.
5. Finish only when the agreed tolerance is met across the required states. Report exact remaining deltas if the environment blocks pixel proof.
