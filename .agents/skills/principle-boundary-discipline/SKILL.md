---
name: principle-boundary-discipline
description: Validate and normalize at boundaries, trust internal invariants, and keep business logic free of adapter noise. Use when at parsing, framework, process, network, storage, or user-input boundaries.
---
# Boundary Discipline

## Trigger

At parsing, framework, process, network, storage, or user-input boundaries.

## Rule

Validate and normalize at boundaries, trust internal invariants, and keep business logic free of adapter noise.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
