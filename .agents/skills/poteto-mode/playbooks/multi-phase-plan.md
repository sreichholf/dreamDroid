# Multi-phase work

1. Define the target end state and phase boundaries in terms of observable acceptance criteria.
2. Order phases so each one simplifies or unlocks the next and can be verified independently.
3. Name temporary states explicitly. Do not preserve a transition API as permanent architecture unless it earns a long-term role.
4. For each phase, list input state, changes, proof, rollback/checkpoint, and dependencies.
5. Execute and verify one phase at a time. Re-plan downstream phases when evidence invalidates the original sequence.
