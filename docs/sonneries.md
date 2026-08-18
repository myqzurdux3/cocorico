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

## `coq.wav` — licence non établie

Ce fichier n'est **pas** une création de ce dépôt : c'est un enregistrement
fourni par le propriétaire, dont la source et la licence d'origine n'ont pas
été documentées.

**`LICENSE` ne le couvre pas.** La licence MIT porte sur le code de ce dépôt. Un
fichier audio tiers n'en relève pas automatiquement, et personne n'a vérifié
que celui-ci autorise la redistribution.

Le dépôt a été rendu public le 18 août 2026 en connaissance de ce point : le
propriétaire a choisi de publier en l'état plutôt que de remplacer le fichier.
La question n'est donc pas *ouverte*, elle est *assumée* — mais elle reste
vraie, et ce paragraphe existe pour qu'elle ne soit jamais découverte par
surprise.

### Si tu réutilises ce dépôt

**Ne considère pas `coq.wav` comme du contenu sous licence MIT.** Avant toute
redistribution, tout paquet publié ou tout usage commercial, remplace-le :

- par un enregistrement dont la licence est connue (Freesound en CC0,
  Wikimedia Commons en domaine public) ;
- ou par une version synthétisée, sur le modèle des trois autres sonneries.

Le nom de fichier et le format attendus sont ceux du tableau ci-dessus : mono,
WAV PCM. Rien d'autre dans le code n'a besoin de changer.

### Si tu es l'ayant droit

Ouvre une issue, ou écris au mainteneur via un
[avis privé](https://github.com/myqzurdux3/cocorico/security/advisories/new) :
le fichier sera retiré sans discussion.

## Pourquoi le coq n'est plus synthétisé

Une version de synthèse a été écrite : pile d'harmoniques, tremblement de
hauteur, souffle et modulation d'amplitude rapide pour le grain éraillé. Elle
avait la bonne structure — deux appels brefs, une montée, une tenue
descendante — mais restait reconnaissable comme une imitation. Un vrai
enregistrement l'a remplacée. Le détail de la conversion appliquée est en tête
de `tools/generer_sonneries.py`.
