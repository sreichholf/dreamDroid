---
name: recall
description: Reconstruct current task context from available durable history and records. Use when resuming work or rebuilding context on a topic.
---
# Recall

Rebuild context from durable evidence, not memory theater.

1. Search the sources the host actually exposes: prior chats, decision logs, issues/PRs, branches, commits, docs, or task files.
2. Prefer recent primary artifacts over summaries.
3. Reconcile contradictions against repository/current external state.
4. Return a compact brief: goal, current state, decisions, completed work, open risks, next verified step, and source pointers.
5. If history access is unavailable, say which source is missing rather than fabricating a recollection.
