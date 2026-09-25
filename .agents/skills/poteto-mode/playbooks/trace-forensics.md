# Captured trace forensics

1. Validate the artifact type, capture environment, clock/timebase, and whether the trace is complete enough to answer the question.
2. Find the dominant intervals, stacks, allocations, waits, or event clusters relevant to the symptom.
3. Connect hot/cold evidence to source and runtime behavior without equating correlation with cause.
4. Compare against another trace or source-level prediction when available.
5. Deliver the diagnosis, supporting trace locations, uncertainty, and the next discriminating measurement if needed.
