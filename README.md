# Calme

Calme est une application personnelle d'organisation pour rendre la prochaine action claire, réaliste et sans culpabilisation. La spécification produit complète est dans [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md).

> **État du dépôt :** cadre de travail initial. Aucun code applicatif n'a encore été implémenté.

## Démarrer avec DeepSeek Harness

Ouvrir ce dépôt comme espace de travail dans DeepSeek Harness, puis demander à l'agent de lire `AGENTS.md`, `docs/PLAN.md` et `docs/QUESTIONS.md` avant de commencer. Les compétences spécifiques au projet sont dans `.agents/skills/`.

DeepSeek Harness est encore en préversion et peut changer ; vérifier ses instructions et sa documentation à chaque mise à jour importante.

## Repères

- `docs/PRODUCT_SPEC.md` — copie de référence de la spécification fournie.
- `docs/PLAN.md` — jalons et tâches du MVP.
- `docs/DECISIONS.md` / `docs/QUESTIONS.md` — décisions confirmées et questions ouvertes.
- `docs/AGENT_CONTRACT.md` et `docs/agent-contract.schema.json` — contrat provisoire de sortie structurée.
- `docs/ACCEPTANCE.md` — critères d'acceptation F2 à F9.
- `fixtures/` — données de départ vérifiables.

## Versionnement

`main` est la branche stable. Créer une branche courte `feat/<sujet>`, `fix/<sujet>` ou `docs/<sujet>` par changement, puis proposer une pull request vers `main`. Garder les PR petites, relisibles et accompagnées des tests pertinents. Ne jamais pousser de secrets.

Les choix techniques déjà spécifiés sont React/Vite/TypeScript, Spring Boot 3/Java 21, PostgreSQL, DeepSeek API et Docker Compose. Les détails explicitement ouverts restent à décider avant leur implémentation.
