---
name: principle-migrate-callers-then-delete-legacy-apis
description: Move callers to the new shape and delete the old path in the same migration wave instead of carrying a compatibility layer. Use when when replacing an internal api.
---
# Migrate Callers Then Delete Legacy APIs

## Trigger

When replacing an internal API.

## Rule

Move callers to the new shape and delete the old path in the same migration wave instead of carrying a compatibility layer.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
