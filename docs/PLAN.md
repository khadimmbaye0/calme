# Plan du projet

Le périmètre de référence est le MVP de `PRODUCT_SPEC.md`. Les jalons ci-dessous reprennent la roadmap produit ; les semaines sont indicatives, pas des engagements de calendrier. Cocher une tâche seulement après vérification.

## Cadre du dépôt

- [x] Rechercher les conventions officielles DeepSeek Harness.
- [x] Mettre en place les instructions, documents de référence, compétences et fixtures.
- [x] Créer le dépôt GitHub privé et publier le commit initial sur `main`.
- [ ] Vérifier la protection de `main` et la CI de validation des fixtures.

## Semaine 1 — Fondations

- [x] Initialiser le squelette Spring Boot + React, TypeScript et Vite.
- [x] Ajouter Docker Compose avec PostgreSQL et Flyway.
- [x] Mettre en place l'authentification mono-utilisateur (F1). *(session + BCrypt, sans 2FA)*
- [ ] Définir les entités de base : événements, tâches, projets, blocs et réglages.
  - [x] Entités `AppUser`, `Event`, `Task`, `EnergyLog` et `Block`.
  - [ ] Entités `Project`, `TaskDependency` et `Settings`.
- [ ] Réaliser le CRUD calendrier et la vue jour (F2).
  - [x] CRUD manuel (API + UI), conflits signalés et barre « maintenant ».
  - [ ] CRUD via l'agent.
- **Jalon :** créer et modifier des événements manuellement. *(partie manuelle atteinte ; partie agent à faire)*

## Semaine 2 — Planner

- [ ] Implémenter et tester les règles pures de capacité, énergie, dépendances et conflits.
  - [x] Capacité, énergie, conflits et sélection de l'action « Maintenant ».
  - [ ] Dépendances de tâches et planner de projets (F5).
- [ ] Importer et éditer les blocs d'alternance (F7).
  - [x] Seed des 14 blocs et affichage du bloc courant.
  - [ ] Import/édition depuis les réglages.
- [x] Construire l'écran « Maintenant » et ses trois actions (F3).
- **Jalon :** une action cohérente est proposée depuis des données saisies manuellement. *(atteint avec les tâches et l'énergie saisies à la main)*

## Semaine 3 — Agent

- [ ] Définir le contrat d'API et les validateurs backend pour chaque intent.
- [ ] Ajouter `LlmClient` DeepSeek et un mode simulé sans appel réseau.
- [ ] Implémenter progressivement les intents MVP, selon les décisions validées.
- [ ] Ajouter le journal, l'annulation et les explications (F8).
- **Jalon :** les commandes de référence fonctionnent sans écriture sur réponse invalide.

## Semaine 4 — Finition du MVP

- [ ] Ajouter le digest quotidien (F9), uniquement après décisions sur l'heure et le fournisseur email.
- [ ] Finaliser projets, suivi et replanification dans le périmètre MVP (F5).
- [ ] Appliquer le design system et vérifier desktop/mobile.
- [ ] Préparer déploiement, configuration de sauvegardes et procédure de restauration.
- **Jalon :** usage réel quotidien pendant une semaine.

## Règles de livraison

- Une branche par changement : `feat/<sujet>`, `fix/<sujet>` ou `docs/<sujet>`.
- Une pull request petite, expliquée, testée et relue avant fusion.
- Fusionner vers `main` uniquement après CI verte. Ne pas développer directement sur `main`.
- Si le délai dérive, réduire le périmètre Must/Should selon la spécification ; ne pas augmenter la charge quotidienne.
