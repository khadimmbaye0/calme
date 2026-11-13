# Motion design

## Intention

Un film de **24 secondes** pour présenter Calme en mouvement. Une phrase devient un plan, le plan devient une action, puis il ne reste que le calme.

Le film assume une énergie plus directe que l'interface : typographie cinétique, coupes franches, réactions au son, mais toujours la palette papier, Fraunces et le fil terracotta du produit.

## Storyboard

| Temps | Scène | Contenu |
|---|---|---|
| 0–4 s | L'image | « Une image. Mille mots. » ; planning photographié, scan et rendez-vous extraits |
| 4–8 s | Manifeste | « Une phrase. », « Un plan. », « Une action. », « Le calme. » sur le beat |
| 8–10,4 s | Intro | Coupe franche sur « Une journée à la fois. » |
| 10,4–15,1 s | L'agent | Curseur, clic d'envoi, réponse, événement et coche |
| 15,1–19 s | Le contrôle | L'écran Agent ; l'agent propose, le serveur valide, tout reste annulable |
| 19–22,1 s | Le calme | Carte Maintenant et entrée de journal annulable |
| 22,1–24 s | Fin | « Calme. » et la signature |

## Direction

- Palette du produit : crème `#FAF6EE`, encre `#2B2620`, terracotta `#C4704B`, sauge `#8AA07C`.
- Fraunces pour les titres ; sans-serif système pour le texte.
- Grille filaire très légère, traits fins, aucun dégradé, aucune ombre lourde.
- Masques typographiques, échelles avec léger overshoot, coupes sur le beat, sorties avec follow-through.
- Le GIF est un aperçu muet ; le MP4 contient la bande sonore originale.

## Micro-mouvements

- Une image entre, un scan la traverse et trois rendez-vous sortent du papier.
- Quatre mots manifeste entrent sur le beat, avec une légère rotation et un overshoot.
- Un fil terracotta traverse les scènes et se dessine progressivement.
- Le curseur se déplace vers le bouton d'envoi, clique, puis laisse place aux points de saisie.
- La coche de l'événement se dessine.
- Le titre varie légèrement de graisse grâce à la police variable.
- La grille dérive pour donner de la profondeur.
- Le journal apparaît en fin de boucle avec « Annuler ».

## Son

La bande originale est **synthétisée de zéro** (aucun sample, aucun morceau sous licence) :

- 120 BPM, 24 secondes, quatre accords.
- Les quatre premières secondes accompagnent l'image : shutter, scan, ticks et validation.
- Nappe chaude, basse, kick doux, arpège pluck et charley discret.
- Peu d'effets, mais placés : trois whooshes, un clic d'envoi, deux pops, un chime de validation et une signature finale.
- Le niveau est normalisé pour rester confortable sur enceintes et casque.

Les scripts [soundtrack.py](motion/soundtrack.py) et [soundtrack_ultra.py](motion/soundtrack_ultra.py) génèrent le WAV ; FFmpeg l'encode en AAC et le muxe avec le MP4.

## Fabrication

La séquence est pilotée par une seule timeline : une page HTML de 1600 × 900 px expose `window.seek(t)`, qui positionne chaque élément à l'instant demandé. L'image ouvre les quatre premières secondes, le manifeste prend le relais, puis la timeline de l'application démarre sur une coupe.

1. [calme-motion.html](motion/calme-motion.html) contient l'image, la typographie, le manifeste et la timeline.
2. Chromium headless rend **720 images** à 30 fps (24 s).
3. FFmpeg encode le MP4 H.264, muxe l'AAC, puis génère le GIF 900 × 506 à 15 fps avec 96 couleurs.

```bash
# Schéma de rendu
chromium --headless --screenshot=frame.png --window-size=1600,900 page.html
ffmpeg -framerate 30 -i frames/frame-%04d.png -c:v libx264 -pix_fmt yuv420p video.mp4
ffmpeg -i video.mp4 -i soundtrack.wav -c:v copy -c:a aac -shortest film-sonore.mp4
```

Les images de l'application utilisent le jeu de données de démonstration du README. Aucune donnée personnelle réelle n'est incluse.

## Fichiers

- [calme-motion.html](motion/calme-motion.html) — source, manifeste et timeline.
- [soundtrack.py](motion/soundtrack.py) — synthèse de la base musicale.
- [soundtrack_ultra.py](motion/soundtrack_ultra.py) — intro image et mixage 24 s.
- [calme-motion.mp4](videos/calme-motion.mp4) — 1600 × 900, 30 fps, AAC, **film sonore**.
- [calme-motion.gif](screenshots/calme-motion.gif) — aperçu muet intégré au README.
- [image-input.png](screenshots/image-input.png) — image extraite du film pour la lecture d'un planning.
- [fraunces.woff2](motion/fraunces.woff2) et [OFL.txt](motion/OFL.txt) — police et licence.

## Références

- [Claude Motion — Anthropic Help Center](https://support.claude.com/fr/articles/17454997-commencer-avec-claude-motion) : animations construites en code à partir d'un prompt, export vidéo.
- [Pro Motion — audio-first motion graphics](https://github.com/jack-reson8/pro-motion) : montage cut to the music, analyse du beat et signature sonore.
- [Sound effects: few, exact, on the right frame](https://github.com/varghq/skills/blob/72e8109188e3366ba8d365902a84d6b237dec1d7/video-sound/references/sfx.md) : peu d'effets, chacun avec une cause à l'écran.
- [Material Design 3 — Easing and duration](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs) : durées et courbes des entrées, sorties et transitions.
- [Kinetic type craft — beat-locked timing](https://github.com/varghq/skills/blob/6a46d4dc1c2ea043cf726514673d00edcb7c8fa3/kinetic-type-art/references/shared-type-craft.md) : rythme, temps de lecture, holds et espacement.
- [DEmotion — Apple-style card expansion](https://trydemotion.com/blog/apple-style-card-expansion-after-effects) : séparer le contenant du contenu, vérifier le point médian.
- [Apple — Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) : continuité spatiale et mouvement naturel.
- [The 12 Animation Principles](https://www.taylorfrancis.com/chapters/mono/10.1201/9781003147893-3/12-animation-principles-jonathan-cooper) : staging, anticipation, follow-through, timing.
