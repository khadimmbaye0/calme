# Critères d'acceptation du MVP

Les seuils sont issus de `PRODUCT_SPEC.md`. Les tests doivent vérifier le résultat fonctionnel, pas seulement la présence d'un composant.

## F2 — Calendrier

- Créer, modifier, déplacer et supprimer un événement depuis l'interface et l'agent.
- La barre « maintenant » correspond à l'heure courante et s'actualise chaque minute.
- Signaler tout conflit ; ne jamais créer silencieusement de chevauchement.

## F3 — Maintenant

- Afficher exactement une action, titre de 80 caractères maximum et durée estimée de 25 minutes maximum.
- « Je commence », « Plus tard » et « Trop lourd ce soir » mettent à jour l'état en moins d'une seconde.
- Sans tâche due, afficher un message de repos, sans suggestion forcée.

## F4 — Agent

- Les trois commandes de référence disposent de 20 variantes dans `fixtures/agent-commands.json` ; taux de réussite cible d'au moins 90 % sur ce jeu.
- Chaque action apparaît au journal et peut être annulée en un geste.
- Une sortie invalide, hors schéma ou hors règles backend n'entraîne aucune écriture et donne une erreur claire d'une ligne.
- Le LLM propose ; le backend valide conflits, disponibilité, marges et limites, puis exécute.

## F5 — Projets

- Un objectif avec échéance produit des étapes, estimations par étape, dépendances et buffer d'au moins 20 %.
- Aucune étape n'est planifiée avant ses dépendances.
- Si la capacité manque, l'agent explique le problème et propose de réduire le périmètre ou d'étendre l'échéance ; il ne surcharge pas.

## F6 — Énergie

- Changer le niveau (bas/moyen/bon) en deux gestes maximum.
- En énergie basse, déplacer les tâches non critiques et garder la charge planifiée sous 30 % du temps disponible.
- Ne jamais déplacer les événements fixes (cours, réunions).

## F7 — Alternance

- Importer et éditer les 14 blocs ; afficher le bloc courant.
- La disponibilité par défaut varie selon le bloc et reste paramétrable. Les heures restent à décider.

## F8 — Journal

- Annuler une action restaure exactement l'état antérieur des événements/tâches concernés.
- Conserver les 30 actions les plus récentes.

## F9 — Digest

- Envoyer au plus un digest quotidien à l'heure configurée, de 150 mots maximum, seulement s'il y a une information pertinente.
- Aucun email hors digest au MVP.
