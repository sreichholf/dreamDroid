---
name: architect
description: Settle data shapes, ownership, boundaries, and interfaces before code crosses a function/module boundary. Use for non-trivial design work.
---
# Architect

Read the caller first. Architecture starts from use, not from an isolated abstraction.

1. Name the domain data shape and invariants.
2. Map ownership and lifecycle. For concurrency, identify what is truly shared before proposing synchronization.
3. Draft 2-3 boundary shapes when the decision is contested. If `delegate` is available, assign independent designs; otherwise keep alternatives explicit and sequential.
4. Compare on reader load, invalid states, migration cost, idempotence, testability, and fit with existing architecture.
5. Select the smallest coherent shape and write the caller-facing signature/contract before implementation.
6. Use `interrogate` for high-risk or contested decisions.
