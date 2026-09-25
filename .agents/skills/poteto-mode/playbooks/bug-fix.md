# Bug fix

1. Reproduce the defect on the closest real surface available. Record exact observed and expected behavior.
2. Trace the failing behavior to a root cause before editing. If the symptom cannot be reproduced, gather evidence rather than guessing.
3. When a cheap automated path exists, add or identify a behavior-level check that fails for the same reason.
4. Make the smallest change that fixes the root cause. Avoid nearby cleanup unless it reduces the fix itself.
5. Re-run the reproduction and targeted checks, then the smallest broader suite that can catch collateral damage.
6. Report root cause, change, proof, and any verification surface that was unavailable.
