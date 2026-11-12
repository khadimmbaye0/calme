# Contrat de l'agent

**Statut : proposition de cadrage**, à compléter avec les types métier avant l'implémentation. Le schéma machine-readable est [`agent-contract.schema.json`](agent-contract.schema.json).

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
