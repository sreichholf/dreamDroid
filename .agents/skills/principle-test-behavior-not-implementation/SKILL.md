---
name: principle-test-behavior-not-implementation
description: Call the system the way its consumer does and assert meaningful observable output; avoid mocks or assertions that only mirror internal calls. Use when when writing or evaluating tests.
---
# Test Behavior, Not Implementation

## Trigger

When writing or evaluating tests.

## Rule

Call the system the way its consumer does and assert meaningful observable output; avoid mocks or assertions that only mirror internal calls.

## Application

1. Name the concrete decision this principle changes.
2. Apply the rule to that decision, not as a decorative citation.
3. Prefer evidence from the current repository/runtime over generic preference.
4. If the principle conflicts with an explicit user constraint, keep the constraint and state the tradeoff.
