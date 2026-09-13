1. Read-only. Do not edit files, commit, push, rebase, force-push, or open PRs.
2. Review git HEAD on the cloned rewrite trunk `main`. Do not restack.
3. Stay inside SCOPE paths. Out-of-scope findings are one-line follow-ups only.
4. Cite `path:line` for every issue. No issue without a file pointer.
5. Verdict is exactly one of PASS, ISSUES, BLOCKED.
6. Severity per issue is blocker, high, medium, or low.
7. Do not re-litigate completed modernization-plan checkboxes unless the code contradicts them.
8. `AGENTS.md` claims `app/src` has no Java. Production `.java` under `app/src` (not tests) is a high finding. Dual `.java` and `.kt` for the same type is a leftover-port bug.
9. Prefer domain bugs (wrong state, race, lost navigation, silent HTTP failure, untyped `ExtendedHashMap` leftovers, Compose theme leaks) over style nits.
10. Style nits only if they violate `AGENTS.md` Kotlin rules (tabs, wildcards, Java-only APIs in new Kotlin, `@JvmStatic`/`@JvmOverloads`/`@JvmField`).
11. Do not drive the emulator. Do not run `verify-dreamdroid.py`. Do not run `connectedGoogleDebugAndroidTest` unless SCOPE is tests-and-ci and it is cheap. File reads plus targeted grep are enough for other slices.
12. TIMEBOX 25 minutes. Return partial findings rather than continuing.
13. Report shape: first line `VERDICT: PASS|ISSUES|BLOCKED`. Then a short slice summary. Then numbered issues with severity, `file:line`, and why it matters to a phone user or the next maintainer. Then named test gaps. Then out-of-scope follow-ups, one line each.
14. No em dashes. No colon used as a mid-sentence crutch. Short declarative sentences.
15. New code is Kotlin. Prefer coroutines. Do not treat remaining Java as a plan to keep it.
