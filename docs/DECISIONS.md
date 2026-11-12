# Décisions

Ce fichier ne contient que les choix confirmés. Les hypothèses de la spécification ne deviennent pas des décisions sans validation explicite.

## Produit et architecture confirmés

- Application personnelle mono-utilisateur, utilisable sur desktop et mobile.
- MVP limité au calendrier intelligent, à l'agent central et à la gestion de projets.
- Stack : React + Vite + TypeScript ; Spring Boot 3 + Java 21 ; PostgreSQL ; Flyway ; Docker Compose.
- DeepSeek API derrière une abstraction `LlmClient`. Le backend valide toutes les actions structurées avant exécution ; le modèle n'écrit jamais directement en base.
- Actions internes directement exécutables et annulables ; suppression complète d'un projet, opérations touchant plus de 10 éléments et sorties de l'application demandent confirmation.
- Design clair, éditorial et chaleureux ; pas de culpabilisation, score, streak, notifications push agressives ni rouge.
- Les données de contexte envoyées au LLM sont minimisées ; aucune donnée confidentielle d'école ou d'entreprise.

## Dépôt et collaboration

- Nom de travail du dépôt : `calme` (le nom du produit reste provisoire).
- Hébergement demandé : dépôt GitHub privé sous `khadimmbaye0`.
- `main` est la branche stable ; travail par branches courtes et pull requests.

## Non décidé

Voir `QUESTIONS.md` pour les choix produit/techniques qui restent ouverts. En particulier, les valeurs hypothétiques de capacité et d'horaires ne sont pas des paramètres validés.
