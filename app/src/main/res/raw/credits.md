# Sonneries — crédits

Les quatre fichiers de ce dossier (`coq.wav`, `reveil_matin.wav`, `klaxon.wav`,
`sirene.wav`) ne sont **pas** des enregistrements. Ce sont des sonneries de
remplacement synthétisées localement, générées par le script
[`tools/generer_sonneries.py`](../../../../../tools/generer_sonneries.py)
(WAV PCM 16 bits, mono, 22 050 Hz, 8 secondes chacune).

Raison : la machine de développement n'a ni accès réseau ni encodeur audio
(pas de ffmpeg, pas de sox), ce qui rendait impossible la consigne initiale du
brief de tâche 8 (sourcer quatre fichiers `.ogg` sous licence CC0 sur
freesound.org). Voir la ruling du contrôleur dans
`.superpowers/sdd/2026-08-16-cocorico-v1/task-8-brief.md`.

**Aucun droit n'est attaché à ces fichiers** — ils sont générés
algorithmiquement, sans source tierce. Ils doivent être remplacés par de
vrais enregistrements sous licence libre (ou compatible avec une publication
sur un store) avant toute publication de l'application.

Pour régénérer les quatre fichiers :

```bash
python3 tools/generer_sonneries.py
```
