# Calme

Calme est une application personnelle d'organisation pour rendre la prochaine action claire, réaliste et sans culpabilisation. La spécification produit complète est dans [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md).

> **État du dépôt :** jalon « socle exécutable + calendrier manuel ». Le backend, le frontend, PostgreSQL, Docker Compose, la migration Flyway initiale et le CRUD calendrier manuel sont en place. L'authentification, le planner, l'agent, le digest et les projets ne sont pas encore implémentés.

## Ce qui fonctionne

- Backend Spring Boot 3.5 / Java 21 avec un endpoint de santé `GET /api/health`.
- CRUD calendrier `GET/POST/PUT/DELETE /api/events` avec validation des conflits et réponses `application/problem+json`.
- Vue jour React 19 / Vite / TypeScript : navigation par jour, barre « maintenant » actualisée chaque minute, création, modification et suppression d'événements.
- PostgreSQL 16, migrations Flyway `V1__initial_schema.sql` et `V2__seed_local_user.sql`.
- Docker Compose pour lancer la base, le backend et le frontend.
- Tests backend avec Testcontainers et tests frontend avec Vitest.

## Prérequis

- Java 21 et Maven 3.9+
- Node.js 22.12+ et npm
- Docker et Docker Compose

## Démarrage rapide avec Docker Compose

1. Copier la configuration locale :

   ```bash
   cp .env.example .env
   ```

2. Remplacer `POSTGRES_PASSWORD=replace-locally` par un mot de passe local dans `.env`. Ne jamais committer ce fichier.

3. Lancer la pile :

   ```bash
   docker compose up --build
   ```

4. Ouvrir <http://localhost:5173>. Le backend répond sur <http://localhost:8080/api/health>.

Les ports par défaut sont `5432`, `8080` et `5173`. En cas de conflit, les surcharger avec `DB_PORT`, `BACKEND_PORT` et `FRONTEND_PORT` dans `.env`.

Pour arrêter et supprimer les données locales :

```bash
docker compose down -v
```

## Développement local

### Base de données

```bash
docker compose up -d db
```

### Backend

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
```

Le backend applique les migrations Flyway au démarrage. `GET /api/health` renvoie `{"status":"UP"}` lorsque la base est joignable.

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

Vite sert l'application sur <http://localhost:5173> et proxyfie `/api` vers `http://localhost:8080`.

## API calendrier

```bash
# Événements d'une journée (bornes ISO-8601, excluantes)
curl "http://localhost:8080/api/events?from=2026-11-12T00:00:00Z&to=2026-11-13T00:00:00Z"

# Création
curl -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"title":"Relire le plan","startAt":"2026-11-12T19:00:00Z","endAt":"2026-11-12T19:15:00Z","kind":"PLANNED"}'
```

Un chevauchement entre événements `FIXED` ou `PLANNED` renvoie `409` avec un Problem Detail et n'écrit rien. Les `AVAILABILITY` peuvent se chevaucher. Les instants sont stockés en UTC et affichés dans le fuseau du navigateur.

## Tests et vérifications

```bash
# Backend : tests d'intégration avec PostgreSQL via Testcontainers (Docker requis)
cd backend && mvn test

# Frontend : tests puis build TypeScript
cd frontend && npm ci && npm test && npm run build

# Fixtures et contrat d'agent
python3 scripts/validate_fixtures.py
```

La CI GitHub exécute les tests backend et frontend sur chaque pull request.

## Structure

```text
backend/                 Spring Boot 3.5, Java 21, Flyway
  src/main/java/com/calme/calendar/   API et règles du calendrier
  src/main/java/com/calme/user/       utilisateur local provisoire
  src/main/resources/db/migration/    V1 schéma initial, V2 seed local
frontend/                React 19, Vite, TypeScript, Tailwind CSS
compose.yaml             PostgreSQL + backend + frontend
docs/                    spécification, plan, décisions, questions, acceptation
fixtures/                données de départ vérifiables
```

## Décisions encore ouvertes

Les choix non confirmés restent dans [`docs/QUESTIONS.md`](docs/QUESTIONS.md) et ne sont pas encodés dans le code. En particulier, l'authentification (question 6) reste à trancher : **l'API calendrier n'est pas protégée et doit rester limitée à un usage local** tant que ce choix n'est pas fait. Les horaires par défaut, l'heure et le fournisseur du digest, la politique de données DeepSeek et la calibration de la capacité restent également ouverts.

## Travailler avec DeepSeek Harness

Ouvrir ce dépôt comme espace de travail dans DeepSeek Harness, puis demander à l'agent de lire `AGENTS.md`, `docs/PLAN.md` et `docs/QUESTIONS.md` avant de commencer. Les compétences spécifiques au projet sont dans `.agents/skills/`. DeepSeek Harness est encore en préversion et peut changer ; vérifier ses instructions à chaque mise à jour importante.

## Versionnement

`main` est la branche stable. Créer une branche courte `feat/<sujet>`, `fix/<sujet>` ou `docs/<sujet>` par changement, puis proposer une pull request vers `main`. Garder les PR petites, relisibles et accompagnées des tests pertinents. Ne jamais pousser de secrets.

Les choix techniques déjà spécifiés sont React/Vite/TypeScript, Spring Boot 3/Java 21, PostgreSQL, DeepSeek API et Docker Compose. Les détails explicitement ouverts restent à décider avant leur implémentation.
