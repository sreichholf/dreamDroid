# High-finding fixes

Done predicate: each queued high cluster is revalidated, has a regression test that failed then passed, and is fixed on the working tree. No commit unless the user asks.

Order:
1. f01 SimpleHttpClient transport (blocking)
2. f02 EnigmaClient HTTP-fail vs empty-success
3. f03 generation tokens on reload
4. f04 timer-edit typed session
5. f05 profile delete + backup import/export
6. f06 widget PendingIntent + deleted profile
7. f07 JVM tests on PRs
Then remaining polish-blocker highs (VLC, MultiEPG, TLS, stuck UI).
