# Autonomous run

1. Translate the request into an explicit completion predicate, constraints, and irreversible-action boundaries.
2. Create a decision trail and a small queue of verifiable units.
3. Loop: choose the highest-leverage unit, execute it, verify it, checkpoint the result, then recompute the queue from evidence.
4. Do not idle waiting for a human on reversible work. Do not fabricate progress when a capability is unavailable.
5. Stop only when the predicate is satisfied, an explicit safety boundary requires the human, or a blocker cannot be removed with available capabilities.
