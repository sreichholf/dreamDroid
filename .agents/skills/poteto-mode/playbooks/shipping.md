# Verify and ship

1. Enumerate the exact PR/commit stack in dependency order and freeze the heads being judged.
2. Independently verify each merge-ready head against its acceptance criteria; CI status alone is insufficient.
3. Identify the longest contiguous verified run from the root. Do not arm descendants above an unverified change.
4. Merge or land bottom-up only through the verified run, using the host repository mechanism available.
5. After each landing step, confirm the next head/base state still matches what was verified. Report every landed revision and any stop condition.
