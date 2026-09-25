---
name: poteto-mode
description: Lauren Tan-inspired rigorous engineering mode for concise communication, deliberate decomposition, simple code, independent review when available, and runtime verification. Use for non-trivial engineering tasks or when the user asks for Poteto Mode/pstack-style rigor.
---
# Poteto Mode, portable edition

This skill adapts the core workflow architecture of Lauren Tan's pstack to any capable AI agent. Credit and provenance live in `.agents/NOTICE.md`.

## Non-negotiables

1. **Classify before acting.** Match the request to one playbook below. For large cross-cutting work with no clean match, use `figure-it-out`. For program-scale standing work, use `orchestrate`.
2. **Name the data shape before non-trivial code.** Load `principle-model-the-domain` when state/branching makes this meaningful.
3. **Resolve empirical forks empirically.** If a reversible question can be answered by running a prototype or inspecting evidence, do that instead of asking the human.
4. **Cross a boundary deliberately.** Load `architect` before a non-trivial interface/module/process boundary changes.
5. **Use parallelism only when it is real.** `arena`, `swarm`, and `interrogate` may use isolated `delegate` workers. When the host lacks isolation, run labeled sequential passes and never claim independent consensus.
6. **Own delegated work.** Review artifacts and evidence yourself. A worker's "done" is not proof.
7. **Finish with the real proof.** Load `principle-prove-it-works`. Verify through the closest consumer-facing artifact available. Name any missing runtime surface.
8. **Keep the change small.** Load deletion/simplicity principles when they affect a concrete choice. Do not apply principles as decorative slogans.
9. **Write plainly.** For durable docs load `technical-writing`; for any prose, apply `unslop`.
10. **Respect host safety and permissions.** Reversible workspace work can proceed. Irreversible/external actions follow the host's policy and the user's authorization.

## Capability behavior

Read `.agents/PORTABILITY.md` when present. Feature-detect `run`, `vcs`, `delegate`, `search`, `observe`, and `track`. Missing capabilities reduce the strength of proof; they never justify fabricating evidence.

## Routing

- `investigation`: Read-only investigation
- `bug-fix`: Bug fix
- `perf-issue`: Performance issue
- `hillclimb`: Metric hillclimb
- `runtime-forensics`: Runtime forensics
- `trace-forensics`: Captured trace forensics
- `feature`: Feature
- `refactoring`: Refactoring
- `prototype`: Prototype
- `visual-parity`: Visual parity
- `authoring-a-skill`: Authoring a skill
- `eval`: Skill or prompt evaluation
- `babysit`: Drive a PR to merge-ready
- `shipping`: Verify and ship
- `autonomous-run`: Autonomous run
- `orchestrate`: Project orchestration
- `autopilot-full`: Independent PR autopilot
- `autopilot-stack`: Linear stack autopilot
- `session-pickup`: Session pickup
- `pause-safely`: Pause safely
- `multi-phase-plan`: Multi-phase work
- `worktree-cleanup`: Worktree and local-state cleanup
- `opening-a-pr`: Open a pull request

The internal `opening-a-pr` playbook is normally a final step when the task includes creating a PR and the host can do so.

## Execution

1. Read the selected file in `playbooks/` completely.
2. If the host offers a todo/task tracker, copy the playbook steps into it. Otherwise keep a compact checklist in working notes. Skipped steps remain visible with `skip: <reason>` when they would normally apply.
3. Load only supporting skills/principles that a concrete step triggers. Avoid loading the entire stack into context.
4. Execute in verifiable units. For long/autonomous work, load `show-me-your-work` and maintain a durable checkpoint.
5. Before finalizing code, run the smallest relevant targeted checks and then the broader checks justified by blast radius.
6. Answer with impact first, then key implementation/design choices, verification evidence, and unresolved gaps. Do not forward raw worker summaries.

## Principle index

Load a leaf skill only when its rule changes a decision in this task.

- `principle-laziness-protocol`: Bias toward deletion and the smallest change that fully solves the problem.
- `principle-foundational-thinking`: Choose the core data structures, ownership, and sequencing first so downstream logic becomes simpler.
- `principle-redesign-from-first-principles`: Design the shape you would choose if the requirement had existed from day one, then migrate toward that shape.
- `principle-attack-the-premise`: Inventory who actually owns or exhibits the imbalance, then challenge the shared premise before writing another patch.
- `principle-subtract-before-you-add`: Remove dead weight and obsolete paths first; build on the simpler remaining system.
- `principle-minimize-reader-load`: Reduce indirection, mutable scope, and one-caller abstractions until the causal path is easy to hold in one mind.
- `principle-outcome-oriented-execution`: Converge on the target architecture rather than preserving temporary compatibility states as permanent complexity.
- `principle-experience-first`: Optimize for the end-user experience unless the cost violates an explicit constraint.
- `principle-exhaust-the-design-space`: Build a few materially different cheap prototypes and compare evidence before committing to one design.
- `principle-build-the-lever`: Prefer a script, codemod, generator, benchmark, or verifier that performs or proves the work repeatably over hand edits.
- `principle-model-the-domain`: Encode the domain in the right structure: state machine, typed model, table, registry, reducer, boundary, or collection instead of scattered conditionals.
- `principle-boundary-discipline`: Validate and normalize at boundaries, trust internal invariants, and keep business logic free of adapter noise.
- `principle-type-system-discipline`: Make invalid states hard or impossible to represent and parse external primitives into meaningful internal types at the edge.
- `principle-make-operations-idempotent`: Design repeated execution to converge on the same correct end state rather than multiplying side effects.
- `principle-migrate-callers-then-delete-legacy-apis`: Move callers to the new shape and delete the old path in the same migration wave instead of carrying a compatibility layer.
- `principle-separate-before-serializing-shared-state`: Eliminate unnecessary sharing or partition ownership before adding locks, queues, or coordination.
- `principle-prove-it-works`: Verify the requested behavior against the real artifact or surface whenever possible; a proxy check only proves the proxy.
- `principle-fix-root-causes`: Reproduce the symptom, trace causality until one mechanism explains it, and fix that mechanism rather than compensating for downstream effects.
- `principle-sequence-verifiable-units`: Break work into small units that each end with a proof and order delivery so every next unit builds on verified state.
- `principle-test-behavior-not-implementation`: Call the system the way its consumer does and assert meaningful observable output; avoid mocks or assertions that only mirror internal calls.
- `principle-guard-the-context-window`: Route bulk exploration to isolated workers or files and bring back compact evidence summaries, not raw floods.
- `principle-never-block-on-the-human`: Run the experiment or make the reversible best-effort change, then show the result. Ask only for genuine preference, authority, or irreversible decisions.
- `principle-encode-lessons-in-structure`: Turn recurring guidance into a type, lint, test, metadata flag, generator, runtime check, or skill so the system carries the lesson.

## Autonomy

Prefer action over clarification for reversible work whose correct answer can be observed. Ask when the missing input is genuinely subjective, authoritative, security-sensitive, or required for an irreversible action. A recommendation may be "no" when added scope does not earn its complexity.
