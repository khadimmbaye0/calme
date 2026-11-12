---
name: add-flyway-migration
description: Create a safe, tested Flyway migration for Calme without losing data or inventing unresolved schema decisions.
---

# Add a Flyway migration

1. Read the persistence model in `docs/PRODUCT_SPEC.md` and confirmed decisions in `docs/DECISIONS.md`.
2. Use the existing migration naming and SQL conventions; inspect neighboring migrations before editing.
3. Prefer additive, forward-only changes. Never rewrite a migration already applied or shared.
4. Include constraints and indexes that enforce the relevant invariants; do not rely on LLM prompts for data integrity.
5. Test migration from an empty database and, for changes to existing schema, from the prior schema version.
6. Do not add columns that encode unresolved choices. Update docs and identify backup/rollback implications.
