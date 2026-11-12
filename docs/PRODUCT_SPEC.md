# Product Spec — « Second Brain » (nom de travail : **Calme**)

> Version 0.1 — 12 nov. 2026 — Auteur : utilisateur unique (étudiant ingénieur, dernière année, alternance école/entreprise).
> Implémentation prévue par un agent de codage ; le code sera relu par l'utilisateur (bases React + Spring Boot).
> Légende : **[Décidé]** = confirmé par l'utilisateur · **[Hypothèse]** = proposition à valider · **[Ouvert]** = à trancher.

---

## 1. Résumé exécutif

Calme est une application web personnelle, minimaliste et chaleureuse, construite autour d'un agent IA central. Elle transforme des phrases en langage naturel en événements, tâches et projets placés dans un calendrier interne.

Sa promesse : **savoir quoi faire maintenant, sans pression**. L'écran « Maintenant » n'affiche qu'une seule action. Quand l'énergie est basse, l'agent allège la journée sans culpabiliser.

Principe directeur : **planifier moins, avec plus de marge**. Le risque n°1 est un planning irréaliste qui finit abandonné, comme les calendriers précédents.

MVP = 3 piliers : calendrier intelligent, agent IA central, gestion de projets. Rien d'autre.

---

## 2. Contexte et problème

**Situation.** Dernière année d'ingénieur en alternance : 14 blocs école/entreprise alternés (1 à 4 semaines chacun), puis du 19 avril au 30 juin 2027 un long bloc entreprise avec projet de fin d'études, mémoire et réunions d'encadrement (~20 % du temps retiré).

**Douleur.**
- Le soir en rentrant : trop vidé pour avancer, tout est en vrac dans la tête, pas de point de départ clair, dérive vers des distractions puis culpabilité.
- Attention limitée : lire un long texte ou gérer une interface chargée est difficile.
- Les plannings passés (calendrier) ne sont jamais respectés : surcharge, perte d'énergie, abandon.

**Coût.** Projets qui n'avancent pas (mémoire, PFE), fatigue mentale, culpabilité, temps perdu à décider quoi faire.

**Alternatives et limites.**
| Alternative | Limite constatée |
|---|---|
| Calendrier classique | Planning rigide, jamais respecté, aucune notion d'énergie |
| Gestionnaires de tâches (Todoist, Notion…) | Saisie lourde, listes qui grossissent, pas de décision « maintenant » |
| Papier | Pas d'adaptation, pas de replanification |

---

## 3. Utilisateur cible et personas

**Persona unique : « L'étudiant surchargé »** (l'utilisateur lui-même).
- Comprend vite les concepts, bloque sur le passage à l'action.
- État mental typique le soir : fatigué, peu d'attention, sensible à la culpabilité.
- Contraintes : alternance école/entreprise, deadlines mémoire/PFE, ressources limitées (projet perso, budget minimal).
- Besoin de contrôle : l'agent agit, mais tout reste visible et annulable.

**Mode d'usage** : mono-utilisateur, desktop (travail) et mobile (soir, rapide). **[Décidé]**

---

## 4. Jobs-to-be-done

1. **Quand** je rentre le soir vidé, **je veux** une seule petite chose à faire, **pour** ne pas dériver ni culpabiliser.
2. **Quand** j'ai un gros projet (mémoire, PFE, projet de cours), **je veux** un plan réaliste avec des marges, **pour** qu'il avance un peu chaque jour.
3. **Quand** je suis fatigué, **je veux** que ma journée s'allège toute seule, **pour** protéger l'essentiel sans me sentir en échec.
4. **Quand** une idée ou un engagement surgit, **je veux** le dire en une phrase, **pour** ne pas le garder en tête.
5. **Quand** mon bloc change (école ↔ entreprise), **je veux** que l'app adapte ses propositions, **pour** ne pas replanifier à la main.

---

## 5. Principes de produit

1. **Une chose à la fois.** Un écran = une décision.
2. **Peu de texte.** Une phrase, un bouton. Pas de longues listes à lire.
3. **Marges d'abord.** On ne planifie jamais 100 % du temps disponible.
4. **Zéro culpabilité.** Pas de rouge, pas de « en retard », pas de streaks, pas de score.
5. **Contrôle.** Chaque action de l'agent est visible, expliquée en une phrase et annulable en un tap.
6. **Énergie avant tâches.** L'énergie déclarée prime sur le planning.
7. **Calme visuel.** Pas de notifications push, pas d'animations gratuites, pas de gadgets.
8. **Saisie minimale.** Une phrase suffit.

**Non-négociables (« ne jamais ») :** notifications agressives, gamification culpabilisante, messages de reproche, plus de 3 priorités affichées à la fois, actions externes (email) sans cadre clair.

---

## 6. Parcours utilisateur clés

### 6.1 Capture
1. L'utilisateur tape : « Demain j'ai cours, je rentre vers 18h, je suis disponible après. »
2. L'agent crée l'événement « Cours » et définit une disponibilité après 18h.
3. L'app affiche une confirmation d'une ligne + bouton **Annuler**.

### 6.2 Planification d'un projet
1. « Je veux finir mon projet CS231N en 3 semaines. »
2. L'agent estime la charge, découpe en étapes avec dépendances, applique des marges.
3. Il place directement les créneaux dans le calendrier (annulable), et résume : « 12 étapes, 6 h/semaine, marge de 20 % ».
4. L'utilisateur corrige par phrase ou par glisser-déposer.

### 6.3 Soir (parcours critique)
1. Ouverture de l'app → écran **Maintenant** : une action de 10 à 15 min.
2. Boutons : **Je commence** · **Plus tard** · **Trop lourd ce soir**.
3. « Trop lourd » → l'agent propose une version plus petite ou reporte, sans commentaire négatif.

### 6.4 Revue quotidienne
Digest email le matin : 3 éléments maximum (événements du jour, 1 à 3 priorités, 1 phrase sur la charge).

### 6.5 Imprévu
« Réunion décalée, je perds mon après-midi. » → l'agent déplace les tâches non critiques, protège les engagements, explique en une phrase.

### 6.6 Fatigue
« Je suis fatigué, allège ma journée. » → énergie = basse, tâches non critiques déplacées, journée réduite à l'essentiel.

---

## 7. Périmètre

### MVP (2 à 4 semaines)
- Login simple, mono-utilisateur.
- Calendrier interne (jour/semaine) avec indicateur « maintenant ».
- Écran « Maintenant ».
- Agent IA (DeepSeek) : commandes en langage naturel (texte).
- Gestion de projets : découpage, estimation, dépendances, marges, suivi, replanification.
- Énergie déclarée (3 niveaux, par tap ou phrase).
- Planning alternance école/entreprise saisi une fois (blocs).
- Journal des actions de l'agent + annulation.
- Digest email quotidien.

### V1
- Apprentissage de l'énergie (patterns heure/jour/bloc).
- Revue hebdomadaire légère.
- Mode focus (écran minimal, timer).
- Récurrences d'événements avancées.

### V2
- Voix, partage depuis le téléphone, capture par email.
- Synchronisation Google Calendar (lecture seule d'abord).
- PWA hors-ligne.

### Non-goals
- Multi-utilisateurs, collaboration, partage.
- Gamification, scores, streaks, statistiques de « productivité ».
- Notifications push agressives.
- Application mobile native.
- Gestion de notes/documents longue (le « second brain » est ici un cerveau d'**organisation**, pas une base de connaissances).

---

## 8. Fonctionnalités détaillées (MoSCoW)

| ID | Fonctionnalité | Priorité |
|---|---|---|
| F1 | Login mono-utilisateur | Must |
| F2 | Calendrier interne (CRUD événements, vue jour/semaine, barre « maintenant ») | Must |
| F3 | Écran « Maintenant » (1 action) | Must |
| F4 | Agent : commandes en langage naturel → actions | Must |
| F5 | Projets : plan, estimations, dépendances, marges | Must |
| F6 | Énergie déclarée + allègement de journée | Must |
| F7 | Blocs école/entreprise (planning d'alternance) | Must |
| F8 | Journal des actions + annulation | Must |
| F9 | Digest email quotidien | Must |
| F10 | Explication des décisions (« Pourquoi ? ») | Should |
| F11 | Suivi d'avancement + replanification auto des retards | Should |
| F12 | Apprentissage de l'énergie | Could (V1) |
| F13 | Revue hebdomadaire | Could (V1) |
| F14 | Mode focus | Could (V1) |
| F15 | Voix / Google Calendar / PWA | Won't (MVP) |

### Critères d'acceptation (testables)

**F2 Calendrier**
- Créer, modifier, déplacer, supprimer un événement fonctionne par UI et par agent.
- La barre « maintenant » se positionne à l'heure courante et se met à jour toutes les minutes.
- Aucun chevauchement n'est créé silencieusement : un conflit est signalé.

**F3 Maintenant**
- L'écran affiche exactement 1 action (titre ≤ 80 caractères, durée estimée ≤ 25 min).
- Les boutons « Je commence », « Plus tard », « Trop lourd ce soir » mettent à jour l'état en < 1 s.
- Si aucune tâche n'est due, l'écran affiche un message de repos (pas de suggestion forcée).

**F4 Agent**
- Pour chacune des 3 commandes d'exemple (cf. §20), l'action attendue est produite avec succès dans ≥ 90 % des essais sur un jeu de 20 variantes de phrases.
- Toute action produit une entrée dans le journal avec un bouton **Annuler**.
- Une réponse du LLM invalide (JSON mal formé, hors contraintes) n'écrit rien et affiche une erreur claire d'une ligne.

**F5 Projets**
- À partir d'un objectif + échéance, le plan contient étapes, estimation par étape, dépendances, et un buffer ≥ 20 %.
- Aucune étape n'est placée avant la fin de ses dépendances.
- Si les créneaux disponibles sont insuffisants, l'agent le dit et propose de réduire le périmètre ou d'allonger l'échéance (il ne surcharge pas).

**F6 Énergie**
- Changer l'énergie (bas/moyen/bon) en ≤ 2 taps.
- Énergie « bas » : les tâches non critiques du jour sont déplacées, et la charge planifiée du jour passe sous 30 % du temps disponible.
- Les événements fixes (cours, réunions) ne sont jamais déplacés.

**F7 Alternance**
- Les 14 blocs sont importables/éditables ; le bloc courant est affiché.
- La disponibilité par défaut varie selon le bloc (paramétrable).

**F8 Journal**
- Annuler une action restaure l'état précédent exact (événements/tâches concernés).
- Le journal conserve les 30 dernières actions.

**F9 Digest**
- Un email par jour à l'heure configurée, ≤ 150 mots, jamais envoyé s'il n'y a rien de pertinent à dire.
- Aucun email en dehors du digest au MVP.

---

## 9. Agent IA

### Rôle
Mélange de **coach discret**, **assistant adaptatif** et **gestionnaire**. Il agit sur l'organisation (pas un simple chatbot de conseils). Ton : calme, court, sans reproche.

### Intents (MVP)
`create_event` · `update_event` · `delete_event` · `create_task` · `plan_project` · `reschedule` · `lighten_day` · `set_energy` · `set_availability` · `query_today` · `explain_decision` · `set_block_schedule`

### Outils (function calling)
`calendar.create/update/delete/list`, `task.create/update/complete`, `project.create_plan/replan`, `energy.set`, `availability.set`, `journal.record`.

**Règle d'architecture :** le LLM **propose** des actions structurées (JSON) ; le **backend valide** (chevauchements, disponibilité, marges, limites) puis exécute. Le LLM n'écrit jamais directement en base.

### Autonomie et confirmation **[Décidé]**
| Type d'action | Comportement |
|---|---|
| Calendrier/tâches internes | **Direct**, puis annulable en 1 tap |
| Suppression d'un projet entier | Confirmation |
| Action touchant > 10 éléments à la fois | Confirmation |
| Tout ce qui sort de l'app (emails, futurs connecteurs) | Confirmation ou réglage explicite |

### Mémoire (MVP) **[Hypothèse]**
Persistée en base : projets en cours, préférences de planning (heures de travail, marge), historique d'énergie déclarée, blocs d'alternance. Pas de mémoire de conversations longues : seul le contexte utile est injecté à chaque appel.

### Explication des décisions
Chaque action = **1 phrase** : « J'ai déplacé X à demain car tu as déclaré une énergie basse. » Un bouton « Pourquoi ? » donne le détail (≤ 5 lignes).

### Gestion des erreurs
- Sortie invalide → réessai unique, puis message d'erreur d'une ligne, aucune écriture.
- Timeout/API indisponible → l'UI reste utilisable manuellement, message calme.
- Ambiguïté (heure/date manquante) → une seule question de clarification, avec 2 options max.

### Guardrails
- Limites de charge (cf. §10) imposées par le backend, pas par le prompt.
- Ne jamais formuler de reproche ou de jugement.
- Ne jamais inventer d'événement non demandé.
- Données envoyées au LLM minimisées (cf. §15).

### Abstraction du modèle
`LlmClient` (interface) avec une implémentation DeepSeek. Changer de modèle ne doit toucher qu'une classe. **[Décidé : DeepSeek via API]**

---

## 10. Planification et calendrier

### Règles de capacité (valeurs par défaut, paramétrables)
- **Capacité planifiable** = 60 % du temps disponible (le reste = buffer et imprévus). **[Hypothèse]**
- **Max 3 priorités** par jour ; **1 seule** affichée dans « Maintenant ».
- **Tâche ≤ 25 min** pour une suggestion du soir ; les gros blocs sont découpés.
- **Buffer ≥ 20 %** sur chaque projet.
- Pas de planification pendant les heures de repos (par défaut après 22h30). **[Hypothèse]**

### Estimations
L'agent estime en minutes par étape (fourchette min/max), applique un coefficient d'incertitude (×1,3 par défaut), et affiche une fourchette, pas un chiffre faussement précis.

### Dépendances
Graphe orienté acyclique entre étapes. Une étape n'est planifiable que si ses prérequis sont terminés ou planifiés avant.

### Conflits
- Événement fixe vs tâche : l'événement gagne, la tâche est déplacée.
- Surcharge détectée → l'agent le dit, propose de réduire/étendre, ne comprime pas silencieusement.
- Replanification : tâche non faite → reportée au prochain créneau libre, sans marquage « en retard ».

### Énergie
| Niveau | Effet |
|---|---|
| Bon | Charge normale (≤ 60 % de la disponibilité) |
| Moyen | Charge réduite (≤ 40 %), priorité aux tâches courtes |
| Bas | ≤ 30 %, uniquement l'essentiel, tâches non critiques déplacées |

### Alternance école/entreprise
Chaque bloc porte un type (école/entreprise), des dates et des disponibilités par défaut. L'agent adapte la charge et les créneaux au bloc courant. Le bloc final (19 avril – 30 juin 2027) intègre ~20 % de temps retiré pour mémoire/projets/encadrants.

---

## 11. Rappels et emails

| Type | Fréquence | Contenu |
|---|---|---|
| Digest quotidien | 1×/jour, heure fixe (défaut 07h30) **[Ouvert]** | Événements du jour, 1 à 3 priorités, 1 phrase sur la charge |
| Aucun autre email au MVP | — | — |

- **Quiet hours** : aucun envoi entre 21h et 07h (le digest part à 07h30).
- Pas d'email si rien de pertinent.
- Ton : calme, sans reproche, ≤ 150 mots.
- Lien dans l'email vers l'écran « Maintenant ».
- Pas de notifications push au MVP.

---

## 12. UX/UI

### Principes
Une chose à la fois · texte court · beaucoup d'espace · aucun rouge d'alerte · interactions en 1 à 2 taps.

### Écrans
1. **Maintenant** (écran d'accueil)
   - 1 action, sa durée, 3 boutons : *Je commence · Plus tard · Trop lourd ce soir*.
   - Un champ de saisie unique en bas : « Dis-moi quelque chose… ».
   - Indicateur d'énergie discret (3 points).
2. **Calendrier** (desktop : à droite de « Maintenant » ; mobile : écran secondaire)
   - Vue jour (défaut) et semaine, barre « maintenant ».
3. **Projets** : liste courte, détail avec étapes et progression.
4. **Journal de l'agent** : actions récentes + Annuler.
5. **Réglages** : blocs d'alternance, heures de repos, heure du digest, marge.

### Layout **[Décidé]**
- **Desktop :** « Maintenant » et calendrier côte à côte.
- **Mobile :** « Maintenant » seul ; calendrier via un onglet.

### Design system **[Décidé : clair, éditorial, chaleureux]**
- Fond crème (`#FAF6EE`), texte brun foncé (`#2B2620`), accent doux (terracotta `#C4704B`), vert sauge (`#8AA07C`) pour « fait ».
- Titres en serif (ex. *Fraunces* ou *Lora*), corps en sans-serif lisible (ex. *Inter*).
- Rayons arrondis, ombres très légères, pas d'animation superflue.
- Aucun rouge ; les reports sont affichés en teinte neutre.

### Mode focus **[V1]**
Plein écran : une seule tâche, un minuteur discret, un bouton « Terminé ».

---

## 13. Modèle de données

```
User(id, email, password_hash, timezone, created_at)

Block(id, user_id, type[SCHOOL|COMPANY], start_date, end_date,
      default_availability_json, note)

Project(id, user_id, title, goal, deadline, status, buffer_ratio, created_at)

Task(id, user_id, project_id?, title, estimate_min, estimate_max,
     priority[CRITICAL|NORMAL|OPTIONAL], status[TODO|DOING|DONE|DEFERRED],
     due_date?, created_at)

TaskDependency(task_id, depends_on_task_id)

Event(id, user_id, task_id?, title, start_at, end_at,
      kind[FIXED|PLANNED|AVAILABILITY], recurrence_rule?, created_by[USER|AGENT])

EnergyLog(id, user_id, date, level[LOW|MID|GOOD], source[TAP|TEXT])

AgentAction(id, user_id, intent, payload_json, inverse_payload_json,
            explanation, status[APPLIED|UNDONE|FAILED], created_at)

Message(id, user_id, role[USER|AGENT], content, created_at)

Settings(user_id, rest_start, rest_end, digest_time, capacity_ratio, tone)

DigestLog(id, user_id, sent_at, status)
```

**Relations clés :** Project 1—N Task · Task N—N Task (dépendances) · Task 1—N Event · User 1—N tout.
**Journal d'annulation :** chaque `AgentAction` stocke son inverse pour restaurer l'état.

---

## 14. Architecture technique et intégrations

### Stack **[Décidé]**
- **Front :** React (Vite + TypeScript), TanStack Query, Tailwind CSS, composant calendrier maison (jour/semaine) ou lib légère.
- **Back :** Spring Boot 3 (Java 21), Spring Web, Spring Security, Spring Data JPA, Flyway (migrations).
- **Base :** PostgreSQL.
- **LLM :** DeepSeek API via `LlmClient` (WebClient), sortie JSON structurée, validation côté serveur.
- **Email :** SMTP ou service type Resend, déclenché par `@Scheduled`.
- **Hébergement :** Docker Compose (front + back + Postgres) sur serveur perso ou Oracle Cloud Free Tier. **[Ouvert]**

### Modules back
`auth` · `calendar` · `tasks` · `projects` · `planner` (règles de capacité/dépendances/énergie) · `agent` (orchestration LLM + outils) · `journal` · `digest` · `settings`.

### Flux agent
```
Phrase → /api/agent/command
  → contexte minimal (jour, bloc courant, énergie, créneaux libres, projets actifs)
  → LlmClient (DeepSeek) → actions JSON
  → Validation planner (conflits, capacité, dépendances)
  → Exécution + AgentAction (avec inverse)
  → Réponse UI : résumé d'une ligne + Annuler
```

### Recommandations pour l'agent de codage
- Écrire d'abord le **planner** (règles pures, testables) avant l'intégration LLM.
- Tests unitaires sur : chevauchements, dépendances, capacité, allègement de journée, annulation.
- Prévoir un **mode « agent simulé »** (réponses mockées) pour développer sans appeler l'API.
- Découper en petites PR relisables (l'utilisateur doit pouvoir lire chaque diff).

### Intégrations
MVP : DeepSeek + email. Hors MVP : Google Calendar, voix, mobile natif.

---

## 15. Sécurité, vie privée, confidentialité

- **Authentification :** login simple (email + mot de passe haché Argon2/BCrypt), session ou JWT court avec refresh, HTTPS obligatoire.
- **Secrets :** clés API et SMTP en variables d'environnement, jamais dans le dépôt.
- **Envoi au LLM :** seulement le contexte nécessaire (titres, créneaux, énergie). Pas de données sensibles (mots de passe, identifiants). Les données transitent par l'API DeepSeek : le noter explicitement et éviter d'y mettre des informations confidentielles liées à l'entreprise ou à l'école. **[Ouvert : fournisseur alternatif si besoin]**
- **Sauvegardes :** dump Postgres quotidien.
- **Rate limiting** sur le login et l'endpoint agent.
- **Logs :** pas de contenu de messages en clair dans les logs applicatifs.
- **Offline :** non requis au MVP.

---

## 16. Métriques de succès

Application personnelle : métriques simples, **non culpabilisantes**, consultées par l'utilisateur seulement.

| Métrique | Cible MVP |
|---|---|
| Activation | Planning d'alternance saisi + 1 projet planifié dans les 24 h |
| Usage | Ouverture de « Maintenant » ≥ 5 soirs sur 7 |
| Complétion | ≥ 60 % des actions « Maintenant » démarrées |
| Journée allégée | ≥ 1 journée de fatigue gérée via « allège ma journée » en 2 semaines, sans culpabilité déclarée |
| Stress perçu | Auto-évaluation hebdomadaire 1–5, tendance à la baisse |
| Fiabilité agent | ≥ 90 % de commandes d'exemple correctement exécutées |

**Critère de succès à 2 semaines [Décidé] :** traverser une journée de fatigue sans culpabilité grâce à l'allègement.

---

## 17. Risques et mitigations

| # | Risque | Mitigation |
|---|---|---|
| 1 | **Planning irréaliste → abandon (risque n°1)** | Capacité plafonnée à 60 %, buffers, estimation en fourchette, journées légères par défaut, replanification sans « retard » |
| 2 | L'app demande trop de saisie | Une phrase suffit ; aucun champ obligatoire ; valeurs par défaut |
| 3 | L'agent se trompe ou déplace sans explication | Validation backend, explication 1 phrase, journal, annulation 1 tap |
| 4 | Dérive de périmètre (feature bloat) | Non-goals explicites, 3 piliers, revue de périmètre avant chaque ajout |
| 5 | Coût/latence/indisponibilité de l'API LLM | Abstraction `LlmClient`, mode dégradé manuel, mode simulé en dev |
| 6 | Code généré difficile à relire | Petites PR, tests, structure modulaire, conventions simples |
| 7 | Attention limitée → UI trop dense | Une action par écran, texte court, revue UX en usage réel |
| 8 | Fuite de données vers un tiers (LLM) | Minimisation du contexte, pas de données sensibles |

---

## 18. Roadmap de développement

### Semaine 1 — Fondations
- Projet Spring Boot + React, Docker Compose, Postgres, Flyway.
- Auth mono-utilisateur.
- Modèle de données de base (Event, Task, Project, Block, Settings).
- CRUD calendrier + vue jour avec barre « maintenant ».
- **Jalon :** je peux créer/éditer des événements à la main.

### Semaine 2 — Planner
- Règles de capacité, énergie, dépendances, conflits (tests unitaires).
- Blocs d'alternance (import des 14 blocs).
- Écran « Maintenant » (sélection de l'action, 3 boutons).
- **Jalon :** « Maintenant » propose une action cohérente à partir de données saisies à la main.

### Semaine 3 — Agent
- `LlmClient` DeepSeek + mode simulé.
- Intents : `create_event`, `create_task`, `plan_project`, `lighten_day`, `set_energy`.
- Journal d'actions + annulation + explication.
- **Jalon :** les 3 commandes d'exemple fonctionnent de bout en bout.

### Semaine 4 — Finition
- Digest email planifié.
- Projets : suivi d'avancement et replanification.
- Design system (crème/serif), layout desktop/mobile.
- Déploiement (serveur perso ou Oracle Cloud), sauvegardes.
- **Jalon :** usage réel quotidien pendant 1 semaine.

**Règle :** si une semaine dérape, on coupe du périmètre (Should → hors MVP), on ne rallonge pas les journées.

---

## 19. Questions ouvertes et décisions à prendre

1. **Heure et contenu exacts du digest** (défaut proposé : 07h30, 3 éléments max).
2. **Mode focus** : MVP ou V1 ? (proposé : V1).
3. **Apprentissage de l'énergie** : V1 (proposé) ou MVP ?
4. **Hébergement final** : serveur perso ou Oracle Cloud Free Tier.
5. **Fuseau horaire et jours/horaires de travail par défaut** par type de bloc (école vs entreprise).
6. **Authentification** : session ou JWT ; besoin de 2FA ?
7. **Fournisseur d'email** : SMTP perso ou service dédié.
8. **Politique de données** : acceptable d'envoyer titres de tâches/projets à DeepSeek ? Faut-il un mode « titres anonymisés » ?
9. **Persistance de la mémoire de l'agent** : quels éléments exactement (au-delà des projets/préférences/énergie) ?
10. **Nom du produit** (« Calme » est un nom de travail).
11. **Capacité planifiable** : 60 % est-il le bon défaut ? À calibrer après 2 semaines d'usage.

---

## 20. Annexes

### 20.1 Exemples de commandes
| Commande | Action attendue |
|---|---|
| « Demain j'ai cours, je rentre vers 18h, je suis disponible après. » | Événement « Cours » demain + disponibilité après 18h |
| « Je veux finir mon projet CS231N en 3 semaines. » | Projet + plan (étapes, dépendances, marges) + créneaux placés |
| « Je suis fatigué aujourd'hui, allège ma journée. » | Énergie = basse, tâches non critiques déplacées, journée réduite |
| « Décale ma réunion de jeudi à 15h. » | Événement déplacé, conflits signalés |
| « Qu'est-ce que je fais maintenant ? » | Affiche l'action de l'écran « Maintenant » |
| « Je suis en entreprise jusqu'au 6 décembre. » | Bloc courant mis à jour |
| « Pourquoi tu as déplacé ça ? » | Explication en ≤ 5 lignes |

### 20.2 User stories
- En tant qu'utilisateur, je veux ouvrir l'app le soir et voir **une seule action** pour ne pas avoir à décider.
- En tant qu'utilisateur, je veux dire « je suis fatigué » pour que ma journée soit allégée sans commentaire négatif.
- En tant qu'utilisateur, je veux décrire un projet en une phrase pour obtenir un plan réaliste avec des marges.
- En tant qu'utilisateur, je veux **annuler** n'importe quelle action de l'agent en un tap pour garder le contrôle.
- En tant qu'utilisateur, je veux saisir mon planning d'alternance une fois pour que l'app s'adapte à chaque bloc.
- En tant qu'utilisateur, je veux un digest email court le matin pour savoir où j'en suis sans ouvrir l'app.

### 20.3 Wireframes textuels

**Maintenant (mobile)**
```
┌─────────────────────────────┐
│  Jeudi 12 nov.      ● ○ ○   │   ← énergie (3 points)
│                             │
│                             │
│   Relire le plan du         │
│   chapitre 2                │
│                             │
│   ⏱ 15 min                  │
│                             │
│  ┌───────────────────────┐  │
│  │     Je commence       │  │
│  └───────────────────────┘  │
│   Plus tard   Trop lourd    │
│                             │
│ ┌─────────────────────────┐ │
│ │ Dis-moi quelque chose…  │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

**Desktop (côte à côte)**
```
┌──────────────────────┬───────────────────────────┐
│  MAINTENANT          │  CALENDRIER — Jeudi 12    │
│                      │  08h ───────────────      │
│  Relire le plan du   │  09h ▇ Cours              │
│  chapitre 2          │  12h                      │
│  ⏱ 15 min            │  ▶ ── 18h12 (maintenant) │
│  [ Je commence ]     │  19h ░ Relire plan (15')  │
│  Plus tard · Trop    │  20h                      │
│  lourd               │                           │
│                      │                           │
│  [ Dis-moi quelque   │                           │
│    chose… ]          │                           │
└──────────────────────┴───────────────────────────┘
```

### 20.4 Planning d'alternance (données de départ)

| N° | Du | Au | Où | Semaines |
|---|---|---|---|---|
| 1 | 2 nov. 2026 | 21 nov. 2026 | École | 3 |
| 2 | 23 nov. 2026 | 6 déc. 2026 | Entreprise | 2 |
| 3 | 7 déc. 2026 | 12 déc. 2026 | École | 1 |
| 4 | 14 déc. 2026 | 3 janv. 2027 | Entreprise | 3 |
| 5 | 4 janv. 2027 | 9 janv. 2027 | École | 1 |
| 6 | 11 janv. 2027 | 24 janv. 2027 | Entreprise | 2 |
| 7 | 25 janv. 2027 | 30 janv. 2027 | École | 1 |
| 8 | 1er févr. 2027 | 14 févr. 2027 | Entreprise | 2 |
| 9 | 15 févr. 2027 | 20 févr. 2027 | École | 1 |
| 10 | 22 févr. 2027 | 7 mars 2027 | Entreprise | 2 |
| 11 | 8 mars 2027 | 13 mars 2027 | École | 1 |
| 12 | 15 mars 2027 | 11 avr. 2027 | Entreprise | 4 |
| 13 | 12 avr. 2027 | 17 avr. 2027 | École | 1 |
| 14 | 19 avr. 2027 | 30 juin 2027 | Entreprise | 10,4 |

Remarque bloc 14 : projets de stage, mémoire et réunions avec les encadrants ≈ 20 % du temps retiré.

### 20.5 Prompt système de l'agent (esquisse)
```
Tu es l'agent d'organisation de l'utilisateur. Ton ton est calme, court, sans reproche.
Tu ne réponds QU'EN JSON conforme au schéma d'actions fourni.
Tu proposes des actions ; le serveur les valide.
Règles : ne jamais planifier plus de la capacité autorisée ; protéger les événements fixes ;
si l'énergie est basse, ne garder que l'essentiel ; une seule question de clarification si
nécessaire ; chaque action inclut une explication d'une phrase.
```