# Motion design

## Intention

Une boucle silencieuse de **16 secondes** pour présenter Calme en mouvement, sans trahir la direction artistique du produit : papier chaud, typographie éditoriale, aucune animation gratuite.

Le mouvement suit une idée simple : une phrase devient une action, l'action est vérifiée, puis il ne reste qu'une chose à faire.

## Storyboard

| Temps | Scène | Contenu |
|---|---|---|
| 0–3 s | Intro | « Une journée à la fois. » et la promesse de l'agent |
| 3–7,8 s | L'agent | « Parle. Il s'occupe. » ; curseur, clic d'envoi, réponse et événement vérifié |
| 7,8–11,8 s | Le contrôle | L'écran Agent ; l'agent propose, le serveur valide, tout reste annulable |
| 11,8–14,8 s | Le calme | « Une seule action. », carte Maintenant et entrée de journal annulable |
| 14,8–16 s | Fin | « Calme. » et la signature |

## Direction

- Palette du produit : crème `#FAF6EE`, encre `#2B2620`, terracotta `#C4704B`, sauge `#8AA07C`.
- Fraunces pour les titres ; sans-serif système pour le texte.
- Grille filaire très légère, traits fins, aucun dégradé, aucune ombre lourde.
- Transitions par masques typographiques, opacité et translations courtes.
- Le GIF est la version intégrée au README ; le MP4 est la version haute définition liée.

## Micro-mouvements

- Un fil terracotta traverse les scènes et se dessine progressivement.
- Le curseur se déplace vers le bouton d'envoi, clique, puis laisse place aux points de saisie.
- La carte événement apparaît avec une coche dessinée.
- Le titre varie légèrement de graisse grâce à la police variable.
- La grille dérive très lentement pour donner de la profondeur.
- Les sorties de scènes conservent un follow-through court.
- Le journal apparaît en fin de boucle pour rappeler que tout reste annulable.

## Fabrication

La séquence est pilotée par une seule timeline : une page HTML de 1600 × 900 px expose `window.seek(t)`, qui positionne chaque élément à l'instant demandé. Il n'y a ni librairie d'animation ni vidéo générative.

1. Le HTML [calme-motion.html](motion/calme-motion.html) contient la scène, la typographie et la timeline.
2. Chromium headless rend 480 images à 30 fps (16 s).
3. FFmpeg encode le MP4 H.264, puis le GIF 1000 × 563 à 15 fps avec une palette de 96 couleurs.

```bash
# Schéma de rendu
chromium --headless --screenshot=frame.png --window-size=1600,900 page.html
ffmpeg -framerate 30 -i frames/frame-%04d.png -c:v libx264 -pix_fmt yuv420p calme-motion.mp4
```

Les images de l'application utilisent le jeu de données de démonstration du README. Aucune donnée personnelle réelle n'est incluse.

## Fichiers

- [calme-motion.html](motion/calme-motion.html) — source et timeline.
- [calme-motion.mp4](videos/calme-motion.mp4) — 1600 × 900, 30 fps, silencieux.
- [calme-motion.gif](screenshots/calme-motion.gif) — boucle intégrée au README.
- [fraunces.woff2](motion/fraunces.woff2) et [OFL.txt](motion/OFL.txt) — police et licence.


## Références

- [Material Design 3 — Easing and duration](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs) : durées et courbes pour les entrées, sorties et changements d'état.
- [Kinetic type craft — beat-locked timing](https://github.com/varghq/skills/blob/6a46d4dc1c2ea043cf726514673d00edcb7c8fa3/kinetic-type-art/references/shared-type-craft.md) : rythme, temps de lecture, holds et espacement.
- [DEmotion — Apple-style card expansion](https://trydemotion.com/blog/apple-style-card-expansion-after-effects) : séparer le contenant du contenu, vérifier le point médian, ne pas étirer l'interface.
- [Apple — Designing Fluid Interfaces (WWDC18)](https://developer.apple.com/videos/play/wwdc2018/803/) : continuité spatiale, réponse et mouvement naturel.
- [The 12 Animation Principles](https://www.taylorfrancis.com/chapters/mono/10.1201/9781003147893-3/12-animation-principles-jonathan-cooper) : staging, anticipation, follow-through, timing.
- [Framer launch video breakdown](https://animatiq.ai/launch-directory/framer-7404) : accroche, rythme et lecture des plans.
