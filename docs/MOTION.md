# Motion design

## Intention

Une boucle silencieuse de **12 secondes** pour présenter Calme en mouvement, sans trahir la direction artistique du produit : papier chaud, typographie éditoriale, aucune animation gratuite.

Le mouvement suit une idée simple : une phrase devient une action, l'action est vérifiée, puis il ne reste qu'une chose à faire.

## Storyboard

| Temps | Scène | Contenu |
|---|---|---|
| 0–2,5 s | Intro | « Une journée à la fois. » et la promesse de l'agent |
| 2,5–5,8 s | L'agent | « Parle. Il s'occupe. » ; message, réponse et événement ajouté |
| 5,8–8,7 s | Le contrôle | L'écran Agent ; l'agent propose, le serveur valide, tout reste annulable |
| 8,7–10,9 s | Le calme | « Une seule action. » et la carte Maintenant |
| 10,9–12 s | Fin | « Calme. » et la signature |

## Direction

- Palette du produit : crème `#FAF6EE`, encre `#2B2620`, terracotta `#C4704B`, sauge `#8AA07C`.
- Fraunces pour les titres ; sans-serif système pour le texte.
- Grille filaire très légère, traits fins, aucun dégradé, aucune ombre lourde.
- Transitions par masques typographiques, opacité et translations courtes.
- Le GIF est la version intégrée au README ; le MP4 est la version haute définition liée.

## Fabrication

La séquence est pilotée par une seule timeline : une page HTML de 1600 × 900 px expose `window.seek(t)`, qui positionne chaque élément à l'instant demandé. Il n'y a ni librairie d'animation ni vidéo générative.

1. Le HTML [calme-motion.html](motion/calme-motion.html) contient la scène, la typographie et la timeline.
2. Chromium headless rend 360 images à 30 fps (12 s).
3. FFmpeg encode le MP4 H.264, puis le GIF 1120 × 630 à 15 fps avec une palette de 128 couleurs.

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
