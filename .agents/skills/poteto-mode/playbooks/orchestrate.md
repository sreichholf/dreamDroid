# Project orchestration

1. Define program outcome, milestone graph, shared interfaces, acceptance gates, and integration branch/state.
2. Partition work so each slice has one owner and minimal shared mutable state. Separate before serializing.
3. Delegate independent slices when isolated workers exist. Otherwise sequence them and label the loss of independent verification.
4. Require each slice to return artifacts and evidence, not a done-summary. Integrate only after owner-independent review.
5. Continuously recompute dependencies and risk. Keep a durable decision/integration log for session pickup.
6. Close milestones only when the integrated artifact passes its gate, then verify the program-level outcome.
