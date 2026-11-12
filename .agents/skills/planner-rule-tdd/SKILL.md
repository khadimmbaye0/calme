---
name: planner-rule-tdd
description: Implement calendar and planning rules with focused tests first, including capacity, energy, conflicts, dependencies, and immutable fixed events.
---

# Planner rule TDD

1. Find the relevant acceptance criterion in `docs/ACCEPTANCE.md` and the confirmed rules in `docs/DECISIONS.md`.
2. Write focused tests for the rule and boundary cases before implementation.
3. Keep planning logic deterministic and independent of the LLM, persistence and UI.
4. Test that fixed events never move, conflicts are explicit, dependencies are respected, and capacity/buffer limits cannot be exceeded.
5. For low energy, verify that non-critical tasks move and fixed commitments remain unchanged.
6. Do not select defaults for open capacity or availability questions; request a decision if needed.
