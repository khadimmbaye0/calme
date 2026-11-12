# Questions ouvertes

Ne pas inventer de réponse à ces questions. Ajouter une décision à `DECISIONS.md` une fois que l'utilisateur l'a confirmée.

1. Heure exacte et contenu du digest (07h30 est une proposition).
2. Mode focus : V1 (proposition), pas MVP.
3. Apprentissage de l'énergie : V1 (proposition), pas MVP.
4. Hébergement final : serveur personnel ou Oracle Cloud Free Tier.
5. ~~Fuseau horaire et disponibilités par défaut pour les blocs école/entreprise.~~ **Décidé pour le MVP local : semaine école 17:30–22:00, entreprise 18:30–22:00, week-end 09:00–22:00 ; édition paramétrable à venir (voir DECISIONS.md).**
6. ~~Authentification : session ou JWT ; besoin éventuel de 2FA.~~ **Décidé : session locale + BCrypt, sans 2FA (voir DECISIONS.md).**
7. Fournisseur email : SMTP personnel ou service dédié.
8. ~~Politique de données DeepSeek : titres de projets/tâches autorisés ou anonymisation.~~ **Décidé pour l'usage local : l'utilisateur a demandé l'agent réel DeepSeek ; messages, titres et créneaux nécessaires autorisés, pas de secrets ni de données d'entreprise. Mode simulé disponible.**
9. Mémoire agent au-delà des projets, préférences, énergie et blocs d'alternance.
10. Nom définitif du produit (« Calme » est provisoire).
11. Capacité planifiable de 60 % : hypothèse à calibrer après deux semaines d'usage.

## Dépendances d'implémentation

- La question 5 est tranchée pour le MVP : valeurs codées, édition paramétrable encore à faire.
- Les questions 1 et 7 bloquent l'envoi réel du digest ; le développement du contenu et des tests simulés peut avancer.
- La question 8 doit être tranchée avant l'envoi de données réelles au LLM.
- La question 6 est tranchée : session locale, BCrypt, sans 2FA.
- Les autres choix ne doivent pas bloquer les fondations indépendantes.
