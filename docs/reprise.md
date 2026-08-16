# Reprise de session

Note de passation, à lire en premier après une compaction de contexte ou au
début d'une nouvelle session. Décrit où en est le travail et comment le
reprendre. Le produit lui-même est décrit dans `cocorico.md`.

---

## Où en est le travail

**Branche :** `cocorico-v1`, poussée sur `origin`.
**Pull request :** https://github.com/myqzurdux3/wake-up/pull/1 (ouverte, non fusionnée).
**Base :** `main`.

La V1 est terminée, revue et testée sur appareil. Deux correctifs issus du test
sur téléphone sont livrés : marges système (bord-à-bord) et détection de prise
en main réécrite.

**Suite immédiate : exécuter le plan des pompes**, par sous-agents, selon
`superpowers:subagent-driven-development`. L'utilisateur a explicitement choisi
ce mode.

- Plan : `superpowers/plans/2026-08-17-pompes.md` (7 tâches)
- Spec : `superpowers/specs/2026-08-17-pompes-design.md`

---

## Comment exécuter

Scripts de la compétence, chemin complet :

```
/home/user/.claude/plugins/cache/claude-plugins-official/superpowers/6.3.0/skills/subagent-driven-development/scripts/
├── sdd-workspace PLAN_FILE          # imprime le répertoire de travail du plan
├── task-brief PLAN_FILE N           # extrait le texte d'une tâche
└── review-package PLAN_FILE BASE HEAD  # prépare le diff pour un relecteur
```

Le répertoire de travail est sous `.superpowers/sdd/`, **non versionné** — il a
déjà été effacé une fois par un agent. Tout ce qui doit survivre va dans `docs/`.

### Conventions de dispatch qui ont fait leurs preuves

Coûteuses à redécouvrir, elles ont chacune corrigé un problème réel :

- **Modèle selon la tâche.** Transcription de code fourni : modèle rapide.
  Intégration Android, jugement, câblage : modèle standard. Revue finale de
  branche et corrections subtiles : modèle le plus capable. Toujours préciser le
  modèle explicitement.
- **Preuve d'échec TDD réellement capturée.** Trois tâches de suite ont rendu une
  sortie prédite. Exiger la procédure : déplacer le fichier de production hors de
  l'arbre avec `mv` vers `/tmp`, lancer les tests, capturer la console, remettre
  le fichier. Jamais `git stash`, `checkout` ou `reset`.
- **Les implémenteurs ne touchent pas au plan ni à la spec.** Quand une tâche
  révèle un défaut du plan, c'est le contrôleur qui le corrige, et le correctif
  est transmis dans le message de reprise.
- **Un relecteur par tâche, jamais d'agent qui engendre son propre relecteur.**
- **Vérifier soi-même les affirmations de test** plutôt que croire les rapports :
  `grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml`
  puis `grep -l '<failure' ...`.

---

## État vérifié

56 tests unitaires verts. `./gradlew :app:assembleDebug` et
`:app:testDebugUnitTest` passent.

Téléphone de test : **Pixel 9a, Android 17 (API 37)**, connecté en USB.
`export PATH="$PATH:/home/user/Android/Sdk/platform-tools"` avant tout `adb`.

Vérifié sur appareil le 16 août 2026 : planification exacte avec sortie du Doze
mode confirmée par `dumpsys alarm`, déclenchement à la seconde, écran allumé
seul, activité par-dessus le verrouillage, volume forcé de 5 à 7, alarme qui
repart après extinction du téléphone en pleine sonnerie, marges système
corrigées.

### Précautions sur le téléphone

C'est le téléphone personnel de l'utilisateur.

- **Ne jamais déclencher l'alarme sans son accord explicite.** Elle l'a déjà fait
  paniquer au point d'éteindre l'appareil.
- Toujours faire une capture d'écran avant un appui : des appuis à l'aveugle ont
  déjà atterri dans ses réglages système.
- Ne pas naviguer hors de Cocorico.

---

## Reste à faire après les pompes

- **Calibrer les seuils de prise en main sur appareil.** `PriseEnMainDetector`
  expose `BUDGET_ENERGIE` (0,25), `PLANCHER_ENERGIE` (0,30) et
  `DUREE_INCLINAISON_MS` (400) — simulés, jamais mesurés. `SEUIL_ANGLE_DEG` (27°)
  est le plus sûr. L'utilisateur testera la prise en main une fois les pompes
  finies.
- **Calibrer les seuils du compteur de pompes**, prévu en tâche 7 du plan.
- Les écarts ouverts de la V1 sont listés dans `cocorico.md`.

---

## Décisions produit prises avec l'utilisateur

- **Pas de blocage de désinstallation.** Le Device Admin n'ajoute qu'une
  friction, se désactive en quelques manipulations, et fait refuser l'application
  sur le Play Store. À reconsidérer seulement s'il se surprend à désinstaller.
- **Bouton de renoncement immédiat aux pompes**, contre mon conseil d'un délai.
  Compensé par l'enregistrement du renoncement dans l'historique.
- **Le contrôle de régularité de cadence n'est pas implémenté** : il rejetterait
  un athlète régulier pour un gain faible.
- **La barrière d'onboarding sur la permission d'écran plein reste bloquante** sur
  Android 14+. Arbitrage produit resté ouvert : la revue finale suggérait de
  laisser passer après refus explicite, avec un bandeau. À soumettre à
  l'utilisateur, pas à trancher seul.
