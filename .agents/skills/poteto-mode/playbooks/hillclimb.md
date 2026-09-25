# Metric hillclimb

1. Define one primary metric, guardrail metrics, target, and reproducible benchmark command.
2. Capture and save the baseline.
3. Create a ranked hypothesis queue. Change one meaningful variable per iteration.
4. For each iteration, benchmark before accepting it. Keep only wins that survive the guardrails.
5. Checkpoint each accepted win independently so the sequence is bisectable and reversible.
6. Stop at the target, a clearly exhausted hypothesis space, or a real blocker. Summarize accepted and rejected hypotheses with evidence.
