---
name: maintain-verification-skill
description: Repair a project verification skill whose feature map or commands drifted. Use when app behavior and verification docs no longer agree.
---
# Maintain Verification Skill

1. Diff the current product surfaces against the verification feature map.
2. Run one representative live pass before changing the skill so drift is observed, not guessed.
3. Classify differences as product regression, intentional product change, environment drift, or verifier bug.
4. Update only proven verifier drift. Do not rewrite expectations to make a product regression green.
5. Re-run affected features plus one untouched control feature.
