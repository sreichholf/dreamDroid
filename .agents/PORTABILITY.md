# Portability contract

The canonical `skills/` tree is runtime-neutral. A skill asks for a capability, not a product API.

## Capabilities

| Capability | Meaning | Required fallback |
|---|---|---|
| `files.read` | Read repository/workspace content | Ask for supplied context only when nothing readable exists |
| `files.write` | Make workspace edits | Return an exact patch/draft when writes are unavailable |
| `run` | Execute commands, tests, profilers, or scripts | State that runtime proof is unavailable; do not claim success |
| `vcs` | Inspect diff/history/branches/PR state | Use available repository files; label missing history explicitly |
| `delegate` | Spawn isolated workers/subagents | Run sequential passes; label them non-independent |
| `search` | Query web, docs, issues, chat, observability, analytics | Use only available evidence and report the gap |
| `observe` | Drive/inspect the real UI, service, device, CLI, or artifact | Use the closest artifact only as a proxy and label it |
| `track` | Maintain todos/decision logs/checkpoints | Write a small repo-local Markdown/TSV log when writes exist |

## Rules

1. Do not name a vendor-specific tool in canonical workflow instructions unless the vendor itself is the subject of the task.
2. Feature-detect. Never assume subagents, a browser, a PR API, MCP, memory, or a particular model exists.
3. A fallback must preserve epistemic honesty. Sequential role-play is not independent review. A compile is not runtime proof. A screenshot description is not pixel comparison.
4. Prefer the open Agent Skills package shape: `SKILL.md` plus optional `scripts/`, `references/`, and `assets/`.
5. Keep model routing in `.potetos/config.json`, outside canonical skill logic.
6. Irreversible or externally consequential actions remain safety-gated by the host agent's own policy and permissions.

## Host adapters

Adapters do only two things: expose skill discovery and tell a host how to invoke the canonical files. They must not fork skill behavior. If an adapter needs different behavior, change the capability contract or the canonical skill instead.
