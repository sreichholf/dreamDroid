# Skill or prompt evaluation

1. Define the behavior under test, score rubric, guardrails, and representative task set before seeing candidate outputs.
2. Freeze the baseline and candidate. Randomize or blind judging when practical.
3. Run both under comparable tools, models, context, and budgets.
4. Score outcomes and failure modes, not writing similarity. Inspect regressions individually.
5. Promote only when the candidate clears the primary metric without unacceptable guardrail regressions. Preserve the eval artifacts.
