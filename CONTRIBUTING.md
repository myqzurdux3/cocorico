# Contribuer à Cocorico

## La règle qui porte tout le reste

**Toute logique décidable vit dans une classe sans import `android.*`, couverte
par des tests unitaires.** Les composants Android — activités, services,
récepteurs, composables — ne font que du câblage.

Ce n'est pas une préférence d'architecture, c'est ce qui rend le projet
testable : un réveil ne se vérifie pas commodément à la main, et personne ne va
se lever à 6 h 30 pour valider un correctif. `NextOccurrenceCalculator`,
`CompteurPompes`, `NiveauxVolume`, `StatsCalculator` et `RequeteVision` sont
tous des classes pures pour cette raison.

Quand un correctif touche du code Android inséparable de la plateforme, dis-le
explicitement dans le message de commit plutôt que d'inventer un test qui ne
prouve rien.

## Ce qui doit passer avant une relecture

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Aucun test rouge, aucune erreur de lint.

## Écrire un test

Le test s'écrit **avant** la correction, et on vérifie qu'il échoue réellement —
une sortie d'échec prédite ne prouve rien. Pour prouver l'échec sur du code
existant, déplace le fichier de production hors de l'arbre avec `mv` et remets-le
après ; jamais `git stash`, `checkout` ou `reset`, qui ont déjà fait perdre du
travail ici.

Un test qui recopie une constante n'est pas un test, sauf quand cette constante
est une décision produit — le plancher de volume à 50 %, par exemple, mérite
d'échouer bruyamment si quelqu'un l'abaisse.

## Conventions

- **Commentaires et KDoc en français**, et ils expliquent le *pourquoi*. Le
  *quoi* est déjà dans le code. Une KDoc qui décrit une contrainte externe, un
  arbitrage ou un bug déjà rencontré vaut dix qui paraphrasent une signature.
- **Messages de commit en anglais**, à l'impératif, et ils disent ce que le
  changement empêche d'arriver.
- **Rien sous 15 sp** sur les écrans d'alarme. Chiffres en monospace.
- Indentation à 4 espaces, pas de tabulation, fin de ligne `\n` — voir
  `.editorconfig`.

## Ce qu'on ne fait pas

- Remplacer `setAlarmClock` par autre chose. C'est la seule API exemptée du
  Doze mode ; tout le reste est throttlé et l'alarme rate.
- Ajouter un repli silencieux. Un repli qui masque une panne a déjà coûté trois
  bugs à ce projet : la permission caméra jamais demandée, les alarmes annulées
  à la mise à jour, la sélection par pièce inerte au réveil. Un repli doit être
  **atteignable et visible**.
- Introduire du Device Admin ou un blocage de désinstallation. C'est une
  friction, pas une garantie, et un motif classique de refus sur le Play Store.

## Avant de proposer un changement

Lis [`docs/cocorico.md`](docs/cocorico.md), section « Décisions qui lient
encore ». Chacune de ces lignes a été payée par un bug réel.
