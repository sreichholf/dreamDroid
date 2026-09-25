---
name: principle-separate-before-serializing-shared-state
description: Eliminate unnecessary sharing or partition ownership before adding locks, queues, or coordination. Use when concurrency, locks, queues, shared state, or complex coordination are being considered.
---
# Separate Before Serializing Shared State

## Trigger

When concurrency, locks, queues, shared state, or complex coordination are being considered.

## Rule

Eliminate unnecessary sharing or partition ownership before adding locks, queues, or coordination.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
