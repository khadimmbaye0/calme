# API Calme

Toutes les routes `/api/**` sont protégées par la session, sauf `POST /api/auth/login` et `GET /api/health`.

- Avec la pile Docker, l'API passe par le proxy Nginx : `http://localhost:5173`.
- Avec le backend lancé depuis l'hôte : `http://localhost:8080`.
- Les erreurs métier utilisent `application/problem+json` (RFC 9457).

## Authentification

```bash
set -a
source .env
set +a

# Connexion : stocke le cookie de session
curl -c /tmp/calme-cookies.txt -X POST http://localhost:5173/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$CALME_AUTH_PASSWORD\"}"

# Session courante
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/auth/me

# Déconnexion
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/auth/logout
```

## Calendrier

```bash
# Événements d'une journée (bornes ISO-8601, excluantes)
curl -b /tmp/calme-cookies.txt \
  "http://localhost:5173/api/events?from=2026-11-12T00:00:00Z&to=2026-11-13T00:00:00Z"

# Création
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/events \
  -H 'Content-Type: application/json' \
  -d '{"title":"Relire le plan","startAt":"2026-11-12T19:00:00Z","endAt":"2026-11-12T19:15:00Z","kind":"PLANNED"}'
```

Un chevauchement entre événements `FIXED` ou `PLANNED` renvoie `409` sans écriture. Les `AVAILABILITY` peuvent se chevaucher.

## Maintenant, tâches et énergie

```bash
# Action proposée par le planner
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/now

# Ajouter une tâche
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Relire le plan","estimateMin":15,"estimateMax":15,"priority":"NORMAL","dueDate":"2026-11-12"}'

# Déclarer l'énergie
curl -b /tmp/calme-cookies.txt -X PUT http://localhost:5173/api/energy/today \
  -H 'Content-Type: application/json' -d '{"level":"LOW"}'

# Actions de l'écran Maintenant
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/now/start \
  -H 'Content-Type: application/json' -d '{"taskId":1}'
```

## Projets

```bash
# Liste
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/projects

# Planification
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/projects \
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

Les dépendances forment un DAG validé côté backend. Chaque étape est planifiée après ses dépendances, hors événements fixes ou déjà planifiés, avec un buffer de 20 % minimum. Si les créneaux manquent, l'API répond `409` sans écrire.

## Journal

```bash
# Les 30 dernières actions
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/journal

# Annuler la dernière action applicable
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/journal/1/undo
```

## Graphe de connaissance

```bash
# Nœuds et relations de toutes les entités de l'utilisateur
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/graph
```

Lecture seule. Le graphe est calculé à la demande, avec des volumes bornés ; il n'écrit rien et n'est jamais envoyé au LLM. Les types de nœuds et d'arêtes sont documentés dans [`GRAPH.md`](GRAPH.md).

## Agent

```bash
# Envoyer une phrase
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Ajoute un cours demain de 9h à 10h."}'

# Envoyer une image avec une phrase (multipart, 8 Mo max)
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/agent/chat \
  -F 'message=Ajoute ce planning à mon agenda.' \
  -F 'image=@planning.png;type=image/png'

# Récupérer une image de la conversation (session requise)
curl -b /tmp/calme-cookies.txt \
  http://localhost:5173/api/agent/messages/1/image -o planning.png

# Historique de la conversation
curl -b /tmp/calme-cookies.txt http://localhost:5173/api/agent/messages

# Confirmer ou annuler une action proposée
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/agent/confirm/<id>
curl -b /tmp/calme-cookies.txt -X POST http://localhost:5173/api/agent/cancel/<id>
```

L'agent propose des appels d'outils ; le backend valide et exécute. Une suppression ou un plan de plus de 10 éléments attend une confirmation. Chaque action réussie est journalisée et annulable.

Les images acceptées sont **JPEG, PNG, GIF et WebP**, jusqu'à **8 Mo**. Le type est détecté sur les octets, pas sur l'extension. L'image est stockée avec le message, servie uniquement à la session authentifiée, et transmise à DeepSeek Vision pour être lue.

## Santé

```bash
curl http://localhost:5173/api/health
# {"status":"UP"}
```
