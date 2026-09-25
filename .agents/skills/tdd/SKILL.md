---
name: tdd
description: Write a minimal failing test before implementation, make it pass with the simplest code, and refactor cleanly. Use when implementing bug fixes or features with clear verification targets.
---
# Test-Driven Development (TDD)

1. Understand the exact requirement or bug and identify the narrowest executable check.
2. Write the failing test first. Ensure it fails for the expected reason, not a setup or syntax error.
3. Implement the minimal production code needed to make the failing test pass. Avoid speculative features.
4. Re-run the test to confirm green.
5. Refactor: clean up names, remove duplication, and simplify structure while keeping tests green.
6. Verify adjacent tests and broader contracts to ensure no regressions.
