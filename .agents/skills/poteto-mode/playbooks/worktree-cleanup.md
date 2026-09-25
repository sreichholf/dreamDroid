# Worktree and local-state cleanup

1. Inventory worktrees, branches, dirty state, unpushed commits, active processes, and related simulator/cache usage before deletion.
2. Classify each item as active, merged, abandoned-with-proof, or unknown. Unknown is preserved.
3. Preview the exact deletion/prune set and verify no active process or branch depends on it.
4. Delete only proven-safe items using the narrowest command available.
5. Re-inventory disk/worktree state and report reclaimed space plus everything intentionally preserved.
