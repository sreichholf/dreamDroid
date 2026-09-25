---
name: show-me-your-work
description: Maintain a compact auditable decision trail during autonomous or high-stakes work. Use when the user will review decisions later.
---
# Show Me Your Work

Keep `.potetos/decisions.tsv` with columns:

`timestamp\tunit\tdecision\tevidence\talternatives\tverification\trevision`

Rules:

1. Log decisions that materially change scope, architecture, risk, or the next unit. Do not log every command.
2. Use stable file/commit/test/URL references in evidence fields when available.
3. Record rejected alternatives briefly enough to explain why the chosen path won.
4. Update verification after the evidence exists; never pre-fill success.
5. Keep secrets and private transcript content out of committed logs.
