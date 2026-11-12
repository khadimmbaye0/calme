---
name: small-pr
description: Prepare a narrowly scoped, reviewable change and pull request that follows this repository's branch and validation rules.
---

# Small pull request

1. Confirm the change belongs to the MVP and identify its acceptance criterion.
2. Work on one short-lived `feat/`, `fix/` or `docs/` branch; do not commit directly to `main`.
3. Keep the diff focused. Include tests and update directly related docs.
4. Run targeted checks, including `python3 scripts/validate_fixtures.py` when fixtures or their schema change.
5. Summarize behavior, tests run, unresolved questions and any migration/deployment impact in the PR.
6. Never merge with failing checks or hide an unresolved product decision as an assumption.
