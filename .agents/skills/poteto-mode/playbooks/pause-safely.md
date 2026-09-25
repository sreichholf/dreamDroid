# Pause safely

1. Stop creating new work and finish or revert any half-applied local operation.
2. Record branch/worktree, dirty files, running jobs, external state, decisions, and the last verified checkpoint.
3. Run the cheapest sanity check that proves the paused state is coherent.
4. Write a pickup note with exact next action and any action that must not be repeated.
5. Do not leave irreversible external actions armed or ambiguous.
