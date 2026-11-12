# Contrat de l'agent

**Statut : implémenté pour le cœur F4.** Le schéma d'actions JSON reste la référence fonctionnelle ; l'agent réel utilise le tool calling DeepSeek, décrit plus bas. Le schéma machine-readable des actions est [`agent-contract.schema.json`](agent-contract.schema.json), celui des outils est [`backend/src/main/resources/agent-tools.json`](../backend/src/main/resources/agent-tools.json).

## Frontière de confiance

1. Le modèle reçoit seulement la phrase et le contexte minimal utile.
2. Il retourne un objet JSON conforme au schéma : résumé court, actions proposées et éventuelle demande de clarification.
3. Le backend valide chaque action contre les règles métier, les droits, la disponibilité, les conflits, les dépendances, les limites et les confirmations requises.
4. En cas d'échec de parsing ou de validation, aucune écriture n'a lieu. Un seul nouvel essai peut être tenté pour une sortie mal formée ; ensuite l'application affiche une erreur claire.
5. L'exécution et son inverse sont enregistrés dans le journal. Le modèle n'accède jamais directement à la base de données.

## Intents MVP

`create_event`, `update_event`, `delete_event`, `create_task`, `plan_project`, `reschedule`, `lighten_day`, `set_energy`, `set_availability`, `query_today`, `explain_decision`, `set_block_schedule`.

Les noms et champs métier détaillés seront versionnés avec l'API backend. Chaque intent doit avoir un validateur dédié ; ne pas traiter le schéma de sortie du modèle comme une autorisation d'exécuter.

## Prompt système de départ

> Tu es l'agent d'organisation personnel de l'utilisateur. Ton ton est calme, court et sans reproche. Retourne uniquement un JSON conforme au schéma fourni. Tu proposes des actions structurées ; le backend les valide et les exécute. Ne modifie jamais directement des données. Ne planifie jamais au-delà de la capacité autorisée ; protège les événements fixes ; si l'énergie est basse, ne garde que l'essentiel. N'invente aucun événement. Si une information essentielle manque, pose une seule question avec au plus deux options. Chaque action porte une explication d'une phrase. Ne demande ni n'expose de données sensibles.


## Implémentation par tool calling (12 nov. 2026)

Le modèle reçoit la conversation, le contexte minimal (date, bloc, énergie) et la liste blanche d'outils. Il ne reçoit jamais d'accès direct à la base. Chaque appel est exécuté par un service métier qui applique les règles de conflit, de capacité, de dépendances et de journalisation.

| Outil | Rôle | Confirmation |
|---|---|---|
| `calendar_list` | Lire les événements d'une période | non |
| `calendar_create` | Créer un événement | non |
| `calendar_update` | Modifier un événement | non |
| `calendar_delete` | Supprimer un événement | **oui** |
| `task_list` | Lister les tâches ouvertes | non |
| `task_create` | Créer une tâche | non |
| `task_start` | Démarrer une tâche | non |
| `task_complete` | Terminer une tâche | non |
| `task_defer` | Reporter à demain | non |
| `project_list` | Lister les projets | non |
| `project_plan` | Planifier un projet | **oui si > 10 éléments** |
| `energy_set` | Déclarer l'énergie | non |
| `now_get` | Lire l'action « Maintenant » | non |
| `journal_list` | Lire le journal | non |
| `journal_undo` | Annuler la dernière action | non |

- Boucle bornée à 4 tours et 14 appels d'outils par message.
- Les arguments invalides produisent un résultat d'erreur, sans écriture.
- Une confirmation est stockée en mémoire pour 10 minutes ; aucune écriture avant le `POST /api/agent/confirm/{id}`.
- Les actions réussies passent par les services existants et créent une entrée de journal avec inverse.
- L'historique est limité aux 20 derniers messages utiles et ne contient pas les échanges d'outils bruts.
- `docs/QUESTIONS.md` #8 est cadrée pour l'usage local : titres et créneaux nécessaires autorisés, pas de secrets ni de données d'entreprise.
