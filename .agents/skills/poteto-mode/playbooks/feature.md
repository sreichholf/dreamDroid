# Feature

1. Name the user-visible outcome and the core data shape/state transition before writing logic.
2. Inspect the existing caller, boundary, and test/verification path. Reuse the native shape when it already fits.
3. Write a throughput checkpoint: what can be implemented and verified as one coherent unit, and what is explicitly out of scope.
4. Settle cross-boundary types/contracts before implementation. Prototype only genuine empirical forks.
5. Implement the smallest end-to-end slice that produces the outcome.
6. Verify through the consumer-facing path, plus targeted automated checks. Report the outcome before implementation detail.
