# Calme

Calme est une application personnelle d'organisation pour rendre la prochaine action claire, réaliste et sans culpabilisation. La spécification produit complète est dans [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md).

> **État du dépôt :** jalon « socle exécutable + calendrier manuel + authentification locale + écran Maintenant + projets + journal ». Le backend, le frontend, PostgreSQL, Docker Compose, les migrations Flyway, le CRUD calendrier, la session mono-utilisateur, le planner de base, le planner de projets (F5) et le journal/undo (F8) sont en place. L'agent LLM, le digest et la replanification/édition avancée des projets restent à faire.

## Ce qui fonctionne

- Backend Spring Boot 3.5 / Java 21 avec un endpoint de santé `GET /api/health`.
- Authentification locale par session, mot de passe BCrypt, un seul compte.
- CRUD calendrier `GET/POST/PUT/DELETE /api/events` protégé par la session, avec validation des conflits et réponses `application/problem+json`.
- Vue jour React 19 / Vite / TypeScript : navigation par jour, barre « maintenant » actualisée chaque minute, création, modification et suppression d'événements.
- Écran « Maintenant » : une seule action, énergie déclarée en un tap, boutons « Je commence », « Plus tard » et « Trop lourd ce soir ».
- Planner de base : capacité 60/40/30 % selon l'énergie, tâches de 25 min maximum, titres de 80 caractères maximum, créneaux libres hors événements fixes, report des tâches non critiques en énergie basse.
- Ajout manuel de tâches et seed des 14 blocs d'alternance ; le bloc courant est affiché.
- Projets (F5) : objectif et échéance, étapes avec durées, dépendances validées en DAG, buffer de 20 % minimum, planification dans les créneaux libres et suivi de progression.
- Interface Projets : liste, détail, création d'étapes avec dépendances et marquage terminé.
- Journal (F8) : 30 dernières actions, explication courte, annulation de la plus récente, restauration des événements/tâches/projets/énergie.
- PostgreSQL 16, migrations Flyway `V1` à `V4`.
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

2. Remplacer `POSTGRES_PASSWORD=replace-locally` par une valeur locale dans `.env`, puis y ajouter la variable `CALME_AUTH_PASSWORD` avec un mot de passe local. Ne jamais committer ce fichier.

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

## Connexion locale

- Compte fixe : `local@calme.invalid`.
- Mot de passe : la valeur de `CALME_AUTH_PASSWORD` dans `.env`.
- Session serveur avec cookie `HttpOnly` et `SameSite=Strict`, mot de passe haché en BCrypt, pas de 2FA.
- Le CSRF est désactivé : c'est un choix assumé pour cette application locale non exposée, avec un cookie `SameSite=Strict`. À revoir avant toute exposition réseau.
- L'API `/api/events` exige une session. Seuls `/api/health` et `/api/auth/login` sont publics.

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

Le backend applique les migrations Flyway au démarrage et dérive le hash du compte local depuis `CALME_AUTH_PASSWORD`. `GET /api/health` renvoie `{"status":"UP"}` lorsque la base est joignable.

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

Vite sert l'application sur <http://localhost:5173> et proxyfie `/api` vers `http://localhost:8080`.

## API calendrier et authentification

```bash
# Charger les valeurs locales (dont CALME_AUTH_PASSWORD)
set -a
source .env
set +a

# Connexion : stocke le cookie de session
curl -c /tmp/calme-cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$CALME_AUTH_PASSWORD\"}"

# Session courante
curl -b /tmp/calme-cookies.txt http://localhost:8080/api/auth/me

# Événements d'une journée (bornes ISO-8601, excluantes)
curl -b /tmp/calme-cookies.txt \
  "http://localhost:8080/api/events?from=2026-11-12T00:00:00Z&to=2026-11-13T00:00:00Z"

# Création
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"title":"Relire le plan","startAt":"2026-11-12T19:00:00Z","endAt":"2026-11-12T19:15:00Z","kind":"PLANNED"}'

# Déconnexion
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/auth/logout
```

Un chevauchement entre événements `FIXED` ou `PLANNED` renvoie `409` avec un Problem Detail et n'écrit rien. Les `AVAILABILITY` peuvent se chevaucher. Les instants sont stockés en UTC et affichés dans le fuseau du navigateur.

### Maintenant, tâches et énergie

```bash
# Action proposée par le planner
curl -b /tmp/calme-cookies.txt http://localhost:8080/api/now

# Ajouter une tâche manuellement (durée 10 à 25 min)
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Relire le plan","estimateMin":15,"estimateMax":15,"priority":"NORMAL","dueDate":"2026-11-12"}'

# Déclarer l'énergie (LOW, MID ou GOOD)
curl -b /tmp/calme-cookies.txt -X PUT http://localhost:8080/api/energy/today \
  -H 'Content-Type: application/json' -d '{"level":"LOW"}'

# Actions de l'écran Maintenant
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/now/start \
  -H 'Content-Type: application/json' -d '{"taskId":1}'
```

Le planner ne place jamais une tâche sur un événement fixe ou déjà planifié. En énergie `LOW`, les tâches non critiques dues aujourd'hui sont reportées à demain et seules les tâches critiques restent proposées.

### Projets

```bash
# Liste des projets
curl -b /tmp/calme-cookies.txt http://localhost:8080/api/projects

# Création d'un projet avec étapes et dépendances
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "title":"Mémoire",
    "goal":"Finir le chapitre 2",
    "deadline":"2026-11-30",
    "bufferRatio":0.20,
    "steps":[
      {"title":"Cadrage","estimateMin":30,"estimateMax":30,"priority":"NORMAL","dependsOn":[]},
      {"title":"Rédaction","estimateMin":60,"estimateMax":60,"priority":"NORMAL","dependsOn":[0]}
    ]
  }'
```

Les dépendances forment un DAG validé côté backend. Chaque étape est planifiée après ses dépendances, hors événements fixes ou déjà planifiés, avec un buffer de 20 % minimum. Si les créneaux sont insuffisants, l'API répond `409` et n'écrit rien.

### Journal

```bash
# Les 30 dernières actions
curl -b /tmp/calme-cookies.txt http://localhost:8080/api/journal

# Annuler la dernière action applicable
curl -b /tmp/calme-cookies.txt -X POST http://localhost:8080/api/journal/1/undo
```

Le journal enregistre les créations/modifications/suppressions d’événements, les tâches, l’énergie et la planification de projets. Seule l’action appliquée la plus récente peut être annulée ; elle est ensuite marquée comme annulée.

## Tests et vérifications

```bash
# Backend : tests d'intégration avec PostgreSQL via Testcontainers (Docker requis)
cd backend && mvn test

# Frontend : tests puis build TypeScript
cd frontend && npm ci && npm test && npm run build

# Fixtures et contrat d'agent
python3 scripts/validate_fixtures.py
```

La CI GitHub exécute les tests backend et frontend sur chaque pull request. La suite compte 50 tests backend et 25 tests frontend.

## Structure

```text
backend/                 Spring Boot 3.5, Java 21, Flyway
  src/main/java/com/calme/auth/       session, login/logout, compte local
  src/main/java/com/calme/calendar/   API et règles du calendrier
  src/main/java/com/calme/tasks/      tâches manuelles
  src/main/java/com/calme/planner/    règles pures et écran Maintenant
  src/main/java/com/calme/energy/     énergie déclarée
  src/main/java/com/calme/projects/   projets, dépendances et planification
  src/main/java/com/calme/journal/    journal, inverses et annulation
  src/main/java/com/calme/blocks/     blocs d'alternance et bloc courant
  src/main/java/com/calme/user/       utilisateur local
  src/main/resources/db/migration/    V1 à V4
frontend/                React 19, Vite, TypeScript, Tailwind CSS
compose.yaml             PostgreSQL + backend + frontend
docs/                    spécification, plan, décisions, questions, acceptation
fixtures/                données de départ vérifiables
```

## Décisions encore ouvertes

L'authentification, les disponibilités par défaut et le planner de projets du MVP sont tranchés et documentés dans [`docs/DECISIONS.md`](docs/DECISIONS.md). Les autres choix restent dans [`docs/QUESTIONS.md`](docs/QUESTIONS.md) : heure et fournisseur du digest, politique de données DeepSeek, calibration de la capacité à 60 %, hébergement, nom définitif et mémoire de l'agent.

## Travailler avec DeepSeek Harness

Ouvrir ce dépôt comme espace de travail dans DeepSeek Harness, puis demander à l'agent de lire `AGENTS.md`, `docs/PLAN.md` et `docs/QUESTIONS.md` avant de commencer. Les compétences spécifiques au projet sont dans `.agents/skills/`. DeepSeek Harness est encore en préversion et peut changer ; vérifier ses instructions à chaque mise à jour importante.

## Versionnement

`main` est la branche stable. Créer une branche courte `feat/<sujet>`, `fix/<sujet>` ou `docs/<sujet>` par changement, puis proposer une pull request vers `main`. Garder les PR petites, relisibles et accompagnées des tests pertinents. Ne jamais pousser de secrets.

Les choix techniques déjà spécifiés sont React/Vite/TypeScript, Spring Boot 3/Java 21, PostgreSQL, DeepSeek API et Docker Compose. Les détails explicitement ouverts restent à décider avant leur implémentation.
