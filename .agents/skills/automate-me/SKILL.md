---
name: automate-me
description: Create a personal mode skill from recurring workflow patterns in available history. Use when the user wants their own agent style encoded.
---
# Automate Me

1. Gather a representative sample of the user's actual completed workflows from available, authorized history. Do not infer private history you cannot access.
2. Extract repeated triggers, decisions, quality gates, preferred artifacts, and successful repair loops. Ignore one-off stylistic quirks unless explicitly requested.
3. Separate universal engineering principles from user-specific routing preferences.
4. Draft a `<name>-mode` Agent Skill that routes through existing skills rather than duplicating them.
5. Validate triggers against positive and negative examples, then present the generated skill and evidence behind each rule.
