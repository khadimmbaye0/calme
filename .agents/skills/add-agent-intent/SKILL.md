---
name: add-agent-intent
description: Add or change a Calme agent intent while preserving strict parsing, backend validation, journaling, undo, and confirmation requirements.
---

# Add an agent intent

1. Check that the intent is in the confirmed MVP scope and document its request/response semantics.
2. Update `docs/agent-contract.schema.json` and the human-readable contract together.
3. Add a dedicated parser/validator and tests for valid, malformed, out-of-bounds and ambiguous model output.
4. Keep model output declarative: backend validation owns authorization, capacity, conflict and confirmation decisions.
5. Verify invalid output performs no writes; successful mutations have an explanation, journal record and exact inverse.
6. Update command fixtures and acceptance documentation where behavior changes. Never send real sensitive data to the model.
