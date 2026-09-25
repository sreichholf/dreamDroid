---
name: principle-minimize-reader-load
description: Reduce indirection, mutable scope, and one-caller abstractions until the causal path is easy to hold in one mind. Use when when code requires too many hops, wrappers, hidden states, or mental joins.
---
# Minimize Reader Load

## Trigger

When code requires too many hops, wrappers, hidden states, or mental joins.

## Rule

Reduce indirection, mutable scope, and one-caller abstractions until the causal path is easy to hold in one mind.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
