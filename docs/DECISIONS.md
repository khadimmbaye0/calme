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

## Authentification locale (12 nov. 2026)

- Session serveur, cookie `HttpOnly` et `SameSite=Strict`, mot de passe haché en BCrypt, mono-utilisateur, sans 2FA.
- Compte local fixe `local@calme.invalid`. Le mot de passe est fourni par `CALME_AUTH_PASSWORD` au démarrage et n'est jamais commité ; seul son hash BCrypt est stocké en base.
- CSRF désactivé : choix assumé pour cette application locale non exposée, avec un cookie `SameSite=Strict`. À revoir avant toute exposition réseau.
- `/api/health` et `/api/auth/login` sont publics ; le reste de `/api` exige une session.

## Choix d'implémentation du calendrier (12 nov. 2026, délégués)

Ces choix techniques ont été faits pour avancer sans trancher les questions produit ouvertes.

- Un utilisateur local `local@calme.invalid` (UTC) est seedé par la migration `V2` ; son hash BCrypt est dérivé de `CALME_AUTH_PASSWORD` au démarrage.
- L'API calendrier est protégée par la session locale (voir Authentification locale).
- Les instants sont stockés en UTC (`timestamptz`) et échangés en ISO-8601 ; l'affichage utilise le fuseau du navigateur.
- Les événements `FIXED` et `PLANNED` ne peuvent pas se chevaucher : le backend répond `409` avec un Problem Detail, sans écriture. Les `AVAILABILITY` peuvent se chevaucher.
- Le CRUD manuel est en place ; la partie agent de F2 et les récurrences restent à faire.

## Planner et écran Maintenant (12 nov. 2026, délégués)

- Capacité planifiable : 60 % en énergie bonne, 40 % en énergie moyenne, 30 % en énergie basse.
- Disponibilités par défaut du MVP : en semaine, école 17:30–22:00 et entreprise 18:30–22:00 ; le week-end 09:00–22:00. La journée planifiée s'arrête avant les heures de repos (22:30). Ces valeurs sont codées, réversibles et seront éditables dans les réglages.
- L'écran « Maintenant » propose une seule tâche due aujourd'hui : 25 minutes maximum, titre de 80 caractères maximum, priorité la plus haute, créneau libre après l'heure courante et hors événements `FIXED` et `PLANNED`.
- En énergie basse, les tâches `TODO` non critiques dues aujourd'hui sont reportées à demain ; seules les tâches critiques restent proposées.
- Les 14 blocs d'alternance sont seedés et le bloc courant est affiché. L'import/édition depuis les réglages reste à faire.
- Les dépendances et le planner de projets (F5) sont implémentés ; l'édition/replanification avancée reste à faire.

## Projets (F5, 12 nov. 2026, délégués)

- Un projet a un objectif, une échéance, des étapes avec estimation et un buffer de 20 % minimum (20 % par défaut).
- Les dépendances d'étapes sont validées comme un DAG : pas de cycle, pas d'auto-dépendance, pas d'index hors bornes.
- La planification utilise 60 % du temps libre, place chaque étape après ses dépendances et hors événements `FIXED`/`PLANNED`, et crée un événement `PLANNED` par étape.
- Si les créneaux sont insuffisants, le backend répond `409` avec une explication et n'écrit rien ; il invite à réduire le périmètre ou à allonger l'échéance.
- La progression est calculée à partir des étapes terminées ; l'édition et la replanification avancée des projets restent à faire.

## Journal et annulation (F8, 12 nov. 2026, délégués)

- Le journal enregistre les mutations d'événements, de tâches, d'énergie et de projets, avec une explication d'une phrase.
- Les 30 actions les plus récentes sont listées. Seule la dernière action `APPLIED` peut être annulée ; elle passe ensuite en `UNDONE`.
- L'annulation restaure l'état antérieur via un inverse JSON : suppression/recréation d'événement, restauration de champs, état de tâche, suppression en cascade d'un projet planifié, ou restauration des tâches reportées et du niveau d'énergie précédent.
- Une suppression d'événement annulée restaure le même identifiant. L'annulation n'ajoute pas une nouvelle action au journal.
- Le journal est prêt à recevoir les actions de l'agent LLM, sans lui donner d'écriture directe en base.

## Agent conversationnel (F4, 12 nov. 2026, demandé par l'utilisateur)

- L'agent réel utilise le **tool calling DeepSeek** via `RestClient`, avec `deepseek-flash` par défaut et `deepseek-v4-pro` disponible. `CALME_LLM_MODE=deepseek` active le réel ; `simulated` reste le défaut sans clé.
- Les outils sont une liste blanche : calendrier, tâches, projets, énergie, Maintenant et journal. Le modèle ne touche jamais la base ; le backend valide puis exécute via les services métier.
- Suppression d'événement : confirmation obligatoire. Plan de projet de plus de 10 éléments : confirmation. Une confirmation expire après 10 minutes et n'écrit rien avant validation.
- Les actions réussies sont journalisées par les services existants et annulables. La réponse du chat expose la dernière action annulable.
- L'historique persistant est limité à 20 messages utiles ; les appels d'outils bruts ne sont pas conservés.
- Données envoyées à DeepSeek : message, date, bloc, énergie et données minimales nécessaires aux outils (titres, créneaux, ids). Pas de mots de passe, secrets ou données d'entreprise. L'utilisateur a explicitement demandé ce mode réel ; le mode simulé reste disponible.
- L'agent est l'écran central par défaut ; le reste de l'application reste accessible par onglets.

## Déploiement local (12 nov. 2026, délégué)

- `./scripts/up.sh` (ou `make up`) prépare `.env`, génère les secrets locaux, construit et démarre la pile, puis attend `/api/health`.
- Seul le frontend est publié (`FRONTEND_PORT=5173` par défaut) ; PostgreSQL et Spring restent sur le réseau Compose. `compose.dev.yaml` publie PostgreSQL sur la boucle locale pour le développement.
- `POSTGRES_PASSWORD`, `CALME_AUTH_PASSWORD` et `DEEPSEEK_API_KEY` restent dans `.env`, ignoré par Git et en mode `600`. Aucun secret n'est commité ni intégré à l'image.
- La clé DeepSeek est transmise au backend via `DEEPSEEK_API_KEY`, avec `DEEPSEEK_MODEL=deepseek-flash` par défaut. Le mode LLM reste `simulated` tant que l'agent n'est pas branché et que la politique de données (question 8) n'est pas tranchée.
- Healthchecks backend/frontend et `restart: unless-stopped`. Ce n'est pas encore un déploiement de production : pas de TLS, de sauvegarde ni de supervision.

## Graphe de connaissance (13 nov. 2026, demandé par l'utilisateur)

- Une vue **Graphe** en lecture seule relie les entités déjà présentes en base : projets, tâches, dépendances, événements, actions du journal, énergie, blocs et messages de l'agent.
- Elle ne crée ni notes, ni documents, ni nouveau type de connaissance : elle visualise le cerveau d'**organisation** existant et ne rouvre pas le non-goal « base de connaissances longues ».
- L'API `GET /api/graph` renvoie des nœuds et des arêtes calculés à la demande, sans écriture. Les limites de volume (80 événements, 30 actions, 30 énergies, 20 messages) gardent la vue lisible.
- Le graphe est un onglet de l'application, pas un nouvel écran par défaut. L'agent reste l'écran central.
- La vue est volontairement sobre : palette du produit, pas de nouvelle couleur, pas d'animation décorative, filtres par type et panneau de détail.

## Images dans l'agent (13 nov. 2026, demandé par l'utilisateur)

- L'agent accepte une image par message en complément du texte : JPEG, PNG, GIF ou WebP, 8 Mo maximum.
- Le type réel est vérifié par signature binaire ; un fichier texte renommé en image est refusé sans écriture.
- L'image est stockée dans `message_image` (bytea), liée au message, servie par `/api/agent/messages/{id}/image` derrière la session, et jamais exposée dans une URL publique.
- DeepSeek `deepseek-flash` reçoit l'image en content block `image_url` avec `detail=auto`, en même temps que les outils. Le modèle ne reçoit pas d'accès base.
- Une seule image est envoyée par tour ; une question de suivi doit joindre à nouveau l'image.
- L'utilisateur doit cocher explicitement le consentement d'envoi à DeepSeek avant le téléversement.
- Toute mutation proposée depuis une image passe par une confirmation backend ; aucune écriture directe.
- Les images sont conservées 30 jours, puis supprimées par une purge lors d'un nouvel envoi.
- Le mode simulé répond calmement sans prétendre avoir lu l'image. Le mode réel nécessite `DEEPSEEK_API_KEY`.
- Les images sont envoyées à DeepSeek : l'UI le rappelle et demande de ne pas y mettre de données confidentielles.

## Non décidé

Voir `QUESTIONS.md` pour les choix produit/techniques qui restent ouverts. En particulier, la calibration de la capacité à 60 % après usage réel, l'heure et le fournisseur du digest, l'hébergement, le nom définitif, la mémoire de l'agent et l'évaluation des variantes de commandes restent à trancher.
