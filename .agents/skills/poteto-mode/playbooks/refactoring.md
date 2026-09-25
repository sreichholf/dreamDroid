# Refactoring

1. State the invariant behavior and capture a baseline test, output, or runtime observation that proves it.
2. Map every caller and boundary touched by the structural change.
3. Prefer deletion, inlining, or one direct shape change over compatibility layers.
4. Make the transformation in verifiable units. Migrate callers before deleting old internal APIs.
5. Re-run the same behavior proof and relevant broader checks. Treat any behavior drift as a bug, not a refactor.
