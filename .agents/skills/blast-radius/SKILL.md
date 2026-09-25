---
name: blast-radius
description: Prove what a small-looking change can affect before or after editing. Use for compatibility, caller, state, schema, or behavior impact analysis.
---
# Blast Radius

1. Name the changed contract: type, value, state transition, API, storage shape, timing, style token, or side effect.
2. Enumerate direct readers/writers/callers and any serialized/public boundaries.
3. Trace transitive assumptions by search, type references, tests, runtime wiring, and history as available.
4. Identify the strongest fact that would make the change safe and prove that fact by running or inspecting code when possible.
5. Return affected surfaces grouped as definite, plausible, and ruled out with evidence. Never write "safe" without the proof behind it.
