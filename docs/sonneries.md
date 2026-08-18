# Provenance des sonneries

Quatre sonneries sont embarquées dans `app/src/main/res/raw/`. Elles n'ont pas
toutes la même origine, et la différence a des conséquences juridiques.

| Fichier | Origine | Format |
|---|---|---|
| `coq.wav` | **Enregistrement fourni par le propriétaire du dépôt** le 18 août 2026 (`coq.mp3`, converti) | mono, 44 100 Hz, 2,25 s |
| `reveil_matin.wav` | Synthétisé par `tools/generer_sonneries.py` | mono, 22 050 Hz, 8 s |
| `klaxon.wav` | Synthétisé par `tools/generer_sonneries.py` | mono, 22 050 Hz, 8 s |
| `sirene.wav` | Synthétisé par `tools/generer_sonneries.py` | mono, 22 050 Hz, 8 s |

## Les trois fichiers synthétisés

Produits par le script, donc reproductibles et sans question de licence : ils
n'existent que par le code de ce dépôt, et sont couverts par sa licence.

Régénération :

```bash
python3 tools/generer_sonneries.py
```

Le script **n'écrase pas `coq.wav`**, et ne doit jamais le faire.

## `coq.wav` — point d'attention avant toute publication

Ce fichier n'est **pas** une création de ce dépôt. Sa licence d'origine n'est
pas documentée : elle n'a pas été fournie avec l'enregistrement.

Tant que le dépôt reste privé, cela n'a pas de conséquence. **Avant de le
rendre public, ou de publier l'application, il faut trancher :**

- soit retrouver la source de l'enregistrement et vérifier qu'elle autorise la
  redistribution sous Apache 2.0 — auquel cas la mentionner ici et dans
  `README.md` ;
- soit la remplacer par un enregistrement dont la licence est connue
  (Freesound en CC0, par exemple) ;
- soit revenir à une version synthétisée.

`LICENSE` couvre le code du dépôt. Un fichier audio tiers n'en relève pas
automatiquement, et le laisser sans mention reviendrait à affirmer une licence
qui n'a pas été vérifiée.

## Pourquoi le coq n'est plus synthétisé

Une version de synthèse a été écrite : pile d'harmoniques, tremblement de
hauteur, souffle et modulation d'amplitude rapide pour le grain éraillé. Elle
avait la bonne structure — deux appels brefs, une montée, une tenue
descendante — mais restait reconnaissable comme une imitation. Un vrai
enregistrement l'a remplacée. Le détail de la conversion appliquée est en tête
de `tools/generer_sonneries.py`.
