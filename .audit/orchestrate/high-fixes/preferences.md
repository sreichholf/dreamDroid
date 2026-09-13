1. Revalidate every cited finding by reading the current lines and writing a test that fails for that reason before changing production code.
2. Tests call the code the way its users do and assert a literal result. A test that would pass if collaborators returned empty is not allowed.
3. Matching surface is instrumented or JVM tests, not emulator tapping and not verify-dreamdroid.py loops.
4. Do not pass -Pandroid.testInstrumentationRunnerArguments. Filter with adb am instrument -e class.
5. JAVA_HOME is JDK 25. New code is Kotlin, 4 spaces, 100-column, no Java types under app/src.
6. Prefer coroutines. Do not add @JvmStatic / @JvmOverloads / @JvmField.
7. Smallest change the evidence justifies. No belt-and-suspenders.
8. Exclusive SCOPE paths only. Do not commit, push, rebase, or open a PR.
9. TIMEBOX 45 minutes. Return partial findings rather than expanding scope.
10. Report: revalidation (pass/fail per cited line), failing-test command and output, fix, passing-test command and output, leftover follow-ups.
