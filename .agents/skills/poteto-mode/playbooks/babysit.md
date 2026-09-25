# Drive a PR to merge-ready

1. Inspect current branch/PR state, review threads, conflicts, and CI before changing anything.
2. Classify blockers into code defect, flaky/infrastructure failure, conflict, review request, or non-actionable noise.
3. Resolve one concrete blocker at a time. Reproduce code failures when possible and keep fixes scoped.
4. Re-run only the checks needed to establish a new state, then refresh PR status.
5. Stop at merge-ready or a real external blocker. Report outstanding items exactly; do not equate green CI with safe-to-merge.
