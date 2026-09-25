---
name: principle-make-operations-idempotent
description: Design repeated execution to converge on the same correct end state rather than multiplying side effects. Use when for commands, lifecycle steps, retries, loops, or crash recovery.
---
# Make Operations Idempotent

## Trigger

For commands, lifecycle steps, retries, loops, or crash recovery.

## Rule

Design repeated execution to converge on the same correct end state rather than multiplying side effects.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
