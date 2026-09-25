---
name: swarm
description: Fan out independent slices or coverage partitions and aggregate them with owner verification. Use for large audits, races, and parallel work.
---
# Swarm

1. Define a partition with minimal shared mutable state and one owner per slice.
2. Give every worker exact scope, output schema, evidence requirements, and stop condition.
3. Use isolated `delegate` workers when available. If not, execute slices sequentially and label the fallback.
4. Collect artifacts and evidence, not only prose summaries.
5. The parent reconciles overlap, conflicts, and missing coverage, then independently checks high-impact findings or integrations.
6. Return one deduplicated result and a coverage map.
