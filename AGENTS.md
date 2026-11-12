# Instructions de l'agent

Avant tout changement, lire `docs/PRODUCT_SPEC.md`, `docs/PLAN.md`, `docs/DECISIONS.md` et les questions pertinentes dans `docs/QUESTIONS.md`.

- Le périmètre est celui du MVP de la spécification. N'ajouter aucune fonctionnalité V1/V2 ni aucun non-goal sans décision explicite.
- Ne pas transformer une hypothèse ou une question ouverte en décision. Si un choix bloque le travail, ajouter une question précise à `docs/QUESTIONS.md` et s'arrêter sur ce point.
- Faire des changements petits et révisables, sur une branche dédiée, avec tests adaptés et une pull request vers `main`.
- Respecter les critères de `docs/ACCEPTANCE.md`. Les règles de capacité, conflits, dépendances et confirmations sont appliquées côté backend ; le LLM ne modifie jamais directement la base.
- Ne jamais envoyer de vrais secrets, données d'entreprise ou données sensibles au modèle. Ne jamais committer de secret ni de données personnelles réelles.
- Pour toute action d'agent, conserver une explication courte, un historique annulable et le contrôle de l'utilisateur conformément à la spécification.
- Appliquer le style visuel de `docs/DESIGN_TOKENS.md` : calme, lisible, sans rouge, reproche, score ou surcharge.
- Exécuter `python3 scripts/validate_fixtures.py` après toute modification des fixtures ou de leur schéma ; ajouter les tests liés à tout comportement applicatif introduit.

Les compétences projet se trouvent dans `.agents/skills/`. Le projet suit le format utilisé par le dépôt officiel DeepSeek Harness ; ne pas ajouter de fichiers `CLAUDE.md` ou `.claude/` spécifiques à un autre agent sans besoin établi.
