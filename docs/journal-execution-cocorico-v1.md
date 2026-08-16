# SDD ledger — plan: docs/superpowers/plans/2026-08-16-cocorico-v1.md

Spec: docs/superpowers/specs/2026-08-16-cocorico-design.md (read)
Branch: cocorico-v1 (branched from main @ 8ab4771)
Session works in place (no worktree) per harness configuration.

## Preflight scan

| # | Tâches | Produit vs consommé | Constat |
|---|---|---|---|
| 1 | T1 → T7, T8, T9 | Manifeste déclare `.ui.MainActivity`, `.ui.AlarmActivity`, `.alarm.AlarmService`, `.alarm.AlarmReceiver`, `.alarm.BootReceiver`, tous créés plus tard | OK — le manifest merger ne vérifie pas l'existence des classes ; seul `lint` (tâche `MissingClass`) le ferait, et il n'est pas dans `assembleDebug` |
| 2 | T2 → T3, T7, T11 | `AlarmConfig.challengeId`, `days`, `armed` | OK — nommage identique partout, jamais `challengeType` |
| 3 | T5 → T8, T9 | `VolumeState { PLEIN, BAISSE }` | OK |
| 4 | T6 → T9, T10 | `ChallengeProgress(done, total)`, `MathChallengeEngine.erreurs` | OK après correction du plan (`defi?.erreurs?.value`) |
| 5 | T7 → T8, T11, T12 | `AlarmScheduler.schedule/cancel/canScheduleExact` | OK |
| 6 | T7 → T8 | T7 référence `AlarmService`, absent | OK — le plan annonce explicitement l'échec de compilation en T7 step 5, résolu en T8 |
| 7 | T8 → T9 | `AlarmService.appliquerVolume(VolumeState)` exposé, mais T9 crée son propre `RingtonePlayer` | **Conflit** — voir Ruling 2 |
| 8 | T9 → T11 | `AlarmActivity` référence `MainActivity.EXTRA_VICTOIRE`, défini seulement en T11 | **Conflit bloquant** — voir Ruling 3 |
| 9 | T10 → T9 | T10 modifie `AlarmActivity.terminer()` créé en T9 | OK |
| 10 | T11 → T12 | T12 modifie le `setContent` de `MainActivity` écrit en T11 | OK |
| 11 | T11 interne | `HomeViewModel.prochaine` initialisé nulle part avant la première modification | **Défaut** — voir Ruling 4 |
| 12 | T13 → T8 | T13 modifie `AlarmService` | OK |
| 13 | Tous | Compte de tests annoncé par tâche (23 / 28 / 34 / 37) | **Incohérent** — voir Ruling 5 |
| 14 | T1 interne | Le step 1 exige Android Studio en interface graphique | **Bloquant pour un agent** — voir Ruling 1 |
| 15 | T4 interne | Tests réévaluent l'énoncé, séparateur et signe moins U+2212 | OK — cohérent entre test et implémentation |
| 16 | T12 interne | `ConstructeurTest` teste `Constructeurs`, défini dans `PermissionChecker.kt` | OK — fichier différent du nom de classe, valide en Kotlin |

Ruling 1: Task 1 step 1 — le wrapper Gradle est copié depuis `/home/user/Documents/dressly/dressly/android/` et épinglé sur `gradle-8.14.3-bin` (déjà en cache local) au lieu de passer par l'interface Android Studio. — Raison : aucun agent ne peut piloter une interface graphique, et AGP 8.7.2 est incompatible avec le Gradle 9.1 du wrapper source. — Coût si faux : le build échoue à la première commande, corrigé en une édition de `gradle-wrapper.properties`.

Ruling 2: `AlarmService.appliquerVolume` est supprimé en T8 ; `AlarmActivity` pilote le volume via son propre `RingtonePlayer`, qui n'agit que sur le flux système. — Raison : deux chemins pour la même action, dont un mort ; le volume du flux `STREAM_ALARM` est global, donc l'instance de l'activité suffit. — Coût si faux : la sonnerie ne baisserait pas ; détecté immédiatement à la recette T9 step 5.

Ruling 3: T9 ajoute `companion object { const val EXTRA_VICTOIRE = "com.cocorico.EXTRA_VICTOIRE" }` à la `MainActivity` de T1. — Raison : sans ça T9 ne compile pas, et T11 réécrit `MainActivity` en conservant la constante. — Coût si faux : aucun, la constante est reprise telle quelle en T11.

Ruling 4: `HomeViewModel` initialise `_prochaine` dans un bloc `init {}` en calculant la prochaine occurrence à partir de la configuration persistée. — Raison : sans ça l'accueil affiche « Aucun jour actif » au lancement même quand l'alarme est armée. — Coût si faux : affichage erroné au premier écran, sans effet sur le déclenchement.

Ruling 5: les nombres de tests annoncés dans le plan sont indicatifs ; les implémenteurs rapportent le compte réel. — Raison : le plan additionne mal (28 attendus après T6, le plan en annonce 23 en T8). — Coût si faux : aucun, les tests verts font foi.

## Progression

Task 1: implémenté (commit 83ebba8, DONE, assembleDebug + testDebugUnitTest verts) — revue en cours

Ruling 6: le manifeste utilise `android:showWhenLocked="true"` et non `android:showOnLockScreen="true"`. — Raison : `showWhenLocked` est l'attribut public depuis l'API 27, largement sous le minSdk 28 du projet ; `showOnLockScreen` est l'attribut hérité de l'API 17, accepté silencieusement par le manifest merger, donc invisible au build. Sans correction, `AlarmActivity` ne s'affiche pas par-dessus l'écran verrouillé — la fonction centrale du produit. Le plan source est corrigé en même temps pour que les tâches suivantes n'y reviennent pas. — Coût si faux : aucun, `setShowWhenLocked(true)` est appelé aussi côté code en T9, les deux voies sont complémentaires.

Task 1: minor (deferred): ordre des imports non alphabétique dans MainActivity.kt (hérité du plan, cosmétique, sera réécrit en T11)
Task 1: fix round 1/5 (1 traité, 0 ouvert — showOnLockScreen remplacé par showWhenLocked ; commits 83ebba8..334593f) — re-revue en cours
Task 1: complete (commits 8ab4771..334593f, review clean après 1 round de correction)

Ruling 7: le premier test de NextOccurrenceCalculatorTest passe `minute = 0` explicitement. — Raison : le plan écrivait `config(DayOfWeek.WEDNESDAY, hour = 22)` en laissant le défaut `minute = 30`, tout en attendant `22:00` — le test était faux, pas le code. Plan source corrigé. — Coût si faux : aucun, le test vérifie toujours « sonne le jour même si l'heure n'est pas passée ».

Task 2: implémenté (commit bc9baa0, DONE, 5/5 verts) — revue en cours
Task 2: minor (deferred): preuve GREEN limitée à « BUILD SUCCESSFUL », sans décompte par test
Task 2: minor (deferred): pas de test de bascule à 23:59 dans NextOccurrenceCalculatorTest
Task 2: complete (commits 334593f..bc9baa0, review clean)
Task 3: implémenté (commit 8b44ebc, DONE, 3/3 verts) — revue en cours

Ruling 8: `AlarmConfigRepository.update()` lit et transforme à l'intérieur de la transaction `edit`, via une fonction `lire(prefs)` partagée avec le flux. — Raison : le plan lisait la configuration hors transaction puis écrivait dedans ; `edit` ne sérialise que l'écriture, donc deux mises à jour concurrentes se perdent. L'écran d'accueil bascule un jour par appui et replanifie à chaque changement : deux appuis rapprochés suffisent à déclencher la course. Bénéfice secondaire, la lecture des sept champs cesse d'être dupliquée. Plan source corrigé. — Coût si faux : aucun, `edit` accepte une lambda suspendue et le comportement à un seul appelant est identique.

Task 3: minor traité dans le même round : KDoc précisant que le constructeur attend l'application context
Task 3: fix round 1/5 (2 traités, 0 ouvert — lecture dans la transaction edit, KDoc du contexte ; commits 8b44ebc..8cd7e3c) — re-revue en cours
Task 3: minor (deferred): KDoc du constructeur en anglais alors que le reste du fichier est en français — contrainte globale « commentaires en français » ajoutée au plan pour les tâches suivantes
Task 3: note: `current()` seul n'est pas linéarisable avec un `update()` concurrent ; tout cycle lire-modifier-écrire doit passer par `update(transform)`. Conforme au design, à surveiller en T11.
Task 3: complete (commits bc9baa0..8cd7e3c, review clean après 1 round de correction)
Task 4: implémenté (commit 60f4851, DONE, 5/5 verts, 14 au total) — revue en cours
Task 4: minor (deferred): KDoc de MathProblemGenerator dit « espace insécable normal » alors que le séparateur est l'espace ordinaire (plan source corrigé, code à aligner en revue finale)
Task 4: minor (deferred): branches MOYEN et DIFFICILE structurellement identiques hors plage de b
Task 4: complete (commits 8cd7e3c..60f4851, review clean)
Task 5: implémenté (commit dae4d6c, DONE, 9/9 verts) — revue en cours

Ruling 9: `InactivityTracker` garde son comportement « expiré avant tout amorçage ». — Raison : un appelant qui oublie d'appeler `onInteraction` obtient une alarme à plein volume ; l'inverse donnerait une alarme muette, soit exactement l'échec que le produit existe pour empêcher. Le défaut réel était l'absence de documentation et de test, pas le comportement. KDoc et test ajoutés au plan et au code. — Coût si faux : nul côté produit ; au pire une ligne de KDoc à réécrire.

Task 5: fix round 1/5 en cours (2 findings : KDoc + test de l'état initial, preuve RED réellement capturée)
Task 5: fix round 1/5 (2 traités, 0 ouvert — KDoc + test de l'état initial, transcription RED réellement capturée ; commits dae4d6c..956d302)
Task 5: minor (deferred): bloc « suite complète » du rapport recopié plutôt que capturé (typo « up-to-today »)
Task 5: vérification indépendante du contrôleur : ./gradlew :app:testDebugUnitTest vert, 24 tests dans les XML (1 SmokeTest + 3 + 5x4) — cohérent avec les rapports
Task 5: complete (commits 60f4851..956d302, review clean après 1 round de correction)
Task 6: implémenté (commit 032f318, DONE, 6/6 verts) — revue en cours

Ruling 10: le test `soumettre apres resolution ne change plus rien` gagne une assertion sur `current`, et un test dédié `le probleme resolu reste affiche apres la resolution` est ajouté. — Raison : le test du plan portait un nom qui promettait de verrouiller l'ordre « résoudre puis sortir avant de régénérer », mais n'assertait jamais sur `current` ; il serait resté vert avec la régression exacte qu'il prétend couvrir. Code de production inchangé, il était déjà correct. Plan source corrigé. — Coût si faux : nul, ce sont deux assertions supplémentaires sur un comportement déjà implémenté.

Ruling 11: à partir de maintenant, tout implémenteur capture la preuve RED en déplaçant temporairement le fichier de production hors de l'arbre (`mv` vers /tmp puis retour), jamais par prédiction ni via git stash/checkout/reset. — Raison : trois tâches de suite ont rendu une sortie RED prédite au lieu d'exécutée, ce qui vide le TDD de sa valeur de preuve. — Coût si faux : quelques secondes de build par tâche.
Task 6: fix round 1/5 (2 traités, 0 ouvert — assertions sur `current`, transcription RED capturée pour de vrai ; commits 032f318..3fd94e5)
Task 6: minor (deferred): les tests s'appuient sur Random(99) ne produisant pas deux problèmes identiques consécutifs — déterministe donc pas flaky, mais fragile si la graine change
Task 6: complete (commits d2f2556..3fd94e5, review clean après 1 round de correction)
Task 7: implémenté (commit cf38102, DONE_WITH_CONCERNS — échec de compilation attendu sur AlarmService, résolu en T8) — revue en cours
Task 7: concern de l'implémenteur : `canScheduleExact()` n'est pas appelé par `schedule()`. Attendu — c'est T12 (PermissionChecker) qui le consomme. Pas une anomalie.

Ruling 12: les quatre sonneries de la V1 sont des remplacements synthétisés en WAV PCM mono 22 050 Hz, générés par `tools/generer_sonneries.py` versionné, et non des enregistrements téléchargés en OGG. — Raison : la machine n'a ni ffmpeg ni sox ni accès réseau, donc aucun encodeur Vorbis et aucune source ; Python et numpy sont disponibles. Android lit le WAV PCM sans problème et le nom de ressource ignore l'extension (`klaxon.wav` reste `R.raw.klaxon`). L'utilisateur n'a de toute façon pas encore choisi ses sonneries. — Coût si faux : APK plus lourd de quelques centaines de kilo-octets et sonneries à remplacer avant publication ; CREDITS.md le dit explicitement.

Task 7: minor (deferred): `AlarmScheduler.pendingShowIntent()` utilise `Class.forName("com.cocorico.ui.MainActivity")` sans try/catch — à remplacer par une référence directe une fois MainActivity réécrite en T11, et à couvrir par une règle keep si R8 est activé
Task 7: minor (deferred): `AlarmScheduler` ne normalise pas vers `applicationContext`, contrairement à `AlarmState`
Task 7: minor (deferred): la coroutine de `BootReceiver` n'attrape pas les exceptions — `finish()` est garanti mais une erreur de lecture DataStore ferait planter le processus au démarrage
Task 7: plan corrigé — la section Interfaces annonçait `var alarmeEnCours: Boolean`, qui n'existe pas ; seules les trois fonctions existent
Task 7: complete (commits 16bfb4e..cf38102, review clean)
Task 8: implémenté (commit dfd66d6, DONE_WITH_CONCERNS, 30/30 verts, projet recompile) — revue en cours
Task 8: vérification indépendante du contrôleur : 30 tests dans les XML (3 + 5x4 + 7), SmokeTest supprimé, quatre WAV de 352 844 octets chacun (8 s x 22 050 Hz x 16 bits mono + en-tête) — cohérent

Ruling 13: le fichier de crédits des sonneries sort de `res/raw/` pour aller dans `docs/`. — Raison : l'implémenteur a correctement diagnostiqué que `res/raw/` impose des noms en `[a-z0-9_]` et a renommé en `credits.md`, mais un markdown dans `res/raw/` est compilé dans l'APK comme `R.raw.credits` — du poids mort embarqué chez l'utilisateur. Sa place est dans la documentation. — Coût si faux : nul, c'est un déplacement de fichier.

Ruling 14: `AlarmService.terminer()` replanifie dans la même coroutine, en `NonCancellable`, et n'appelle `stopForeground`/`stopSelf` qu'après. — Raison : le plan lançait la replanification puis appelait `stopSelf()` immédiatement ; `stopSelf()` mène à `onDestroy`, qui annule le scope, et `repo.current()` suspend réellement (DataStore). La replanification pouvait donc être annulée avant de s'exécuter : l'alarme ne sonnerait plus jamais après le premier réveil. C'est la promesse centrale du produit. — Coût si faux : nul, l'ordre est strictement plus sûr.

Ruling 15: le volume d'alarme d'origine est persisté sur disque et restauré aussi depuis `onDestroy`, `arreter()` devenant idempotent. — Raison : `STREAM_ALARM` est un réglage système persistant, non réinitialisé à la mort du processus. Un arrêt forcé pendant l'alarme laissait le téléphone à fond, et la sonnerie suivante aurait pris ce maximum pour l'état normal — dégât permanent. — Coût si faux : un fichier de préférences de plus, effacé à chaque arrêt propre.

Ruling 16: `MediaPlayer.create` nul déclenche un repli sur la première sonnerie embarquée plutôt qu'une NPE. — Raison : une alarme muette est le seul échec que ce produit n'a pas le droit de produire. — Coût si faux : au pire une sonnerie inattendue au lieu d'un plantage.
Task 8: fix round 1/5 (5 traités, 0 ouvert — replanification non annulable, volume persisté et restauré depuis onDestroy, repli MediaPlayer, référence directe à AlarmActivity, credits déplacé hors de res/raw ; commits dfd66d6..e80c45c)
Task 8: minor (deferred): `VolumeOrigine.ecrire/effacer` utilisent `commit()` sur le thread principal — écriture minuscule, même motif que `AlarmState`, mais à surveiller en revue finale
Task 8: minor (deferred): si la sonnerie de repli est elle aussi illisible, l'alarme reste muette sans planter (double corruption, cas pathologique)
Task 8: minor (deferred): si `schedule()` lève dans le bloc NonCancellable, `stopForeground`/`stopSelf` ne sont jamais atteints et l'exception remonte sans handler
Task 8: complete (commits 328e4d8..e80c45c, review clean après 1 round de correction)
Task 9: implémenté (commit bf3d5e0, DONE_WITH_CONCERNS, 30/30 verts) — revue en cours
Task 9: concern remonté : la rotation détruit et recrée AlarmActivity, ce qui remet la progression du défi à zéro. Réel. À trancher après revue.

Ruling 17: `AlarmActivity` est verrouillée en portrait et déclare `configChanges` pour ne plus être recréée. — Raison : la rotation détruisait l'activité, reconstruisait un `MathChallengeEngine` neuf et faisait perdre les calculs déjà résolus ; la nouvelle `VolumeStateMachine` repartant à PLEIN, une rotation téléphone en main pouvait aussi renvoyer le volume à fond. Un écran d'alarme n'a qu'une orientation ; hoister le moteur dans un ViewModel coûterait plus de machinerie que l'écran n'en mérite, et `rememberSaveable` ne peut pas porter les flux. Attribut de manifeste = plus petit changement qui supprime complètement le défaut. — Coût si faux : l'écran d'alarme ne tourne pas en paysage, ce qui est le comportement voulu.

Task 9: minor traité dans le même round : imports morts (auto-import dans AlarmActivity, VolumeState dans AlarmActivity et AlarmService)
Task 9: minor (deferred): `HandDetector` reste enregistré après avoir déclenché — no-op mais consomme un peu de batterie jusqu'à la sortie de l'écran
Task 9: minor (deferred): la lecture de configuration dans `lifecycleScope.launch` n'est pas protégée — une exception empêcherait l'affichage du défi pendant que le service continue de sonner
Task 9: fix round 1/5 (2 traités, 0 ouvert — portrait + configChanges sur AlarmActivity, imports morts ; commits bf3d5e0..4ea46e1)
Task 9: complete (commits b7a0575..4ea46e1, review clean après 1 round de correction)
Task 10: implémenté (commit c383e4d, DONE, 36 tests dont 6 nouveaux) — revue en cours
Task 10: vérification indépendante du contrôleur : 36 tests dans les XML (3 + 5x4 + 6 + 7) — cohérent

Ruling 18: `AlarmActivity.terminer()` exécute l'insertion en base ET l'arrêt du service dans `withContext(NonCancellable)`, l'insertion seule étant enveloppée dans `runCatching`. — Raison : la revue a signalé le risque d'annulation pendant l'insertion (réveil non enregistré), mais a sous-estimé la suite : `AlarmService.arreter()` se trouvait après l'insertion dans la même coroutine annulable. Une annulation ou une exception de base de données laissait donc la sonnerie hurler alors que le défi était résolu — le pire échec possible pour ce produit, déclenché par un simple hoquet de Room. — Coût si faux : nul, l'écriture est minuscule et l'ordre reste identique.
Task 10: fix round 1/5 (1 traité, 0 ouvert — insertion et arrêt du service en NonCancellable ; commits c383e4d..3ae5447)
Task 10: minor (deferred): horodatage `alarmeAt` pris à la création de l'activité, pas au déclenchement réel de l'alarme — écart de quelques centaines de millisecondes
Task 10: complete (commits 9a17199..3ae5447, review clean après 1 round de correction)
Ruling 4 appliqué au plan (init de `_prochaine` dans HomeViewModel) avant extraction du brief de la tâche 11.
Task 11: implémenté (commit 349e2c9, DONE, 36/36 verts) — revue en cours

Ruling 19: `MainActivity` gagne `onNewIntent` et un état `victoire` observable. — Raison : la revue a classé l'absence d'`onNewIntent` en « non vérifiable depuis ce diff ». Vérification faite du manifeste : `MainActivity` est en `singleTop`. Le défaut est donc réel — si l'utilisateur laisse l'application ouverte la veille (cas le plus courant), l'intent de victoire part dans `onNewIntent`, qui n'existait pas, et l'écran de victoire ne s'affiche jamais. C'est la boucle de récompense du produit. — Coût si faux : nul, `onNewIntent` est ignoré si l'activité est recréée.

Task 11: minor (deferred): `HomeViewModel.modifier()` relit la configuration après `repo.update` — lecture DataStore redondante à chaque appui
Task 11: minor (deferred): `VictoryScreen` accède à Room directement depuis un LaunchedEffect, sans ViewModel, contrairement au reste
Task 11: fix round 1/5 (1 traité, 0 ouvert — onNewIntent + état victoire observable ; commits 349e2c9..b3754f0)
Task 11: complete (commits aea3487..b3754f0, review clean après 1 round de correction)
Note : le bloc `setContent` de la tâche 12 a été réaligné sur le nouveau MainActivity (victoire.value + LaunchedEffect) avant extraction du brief.
Task 12: implémenté (commit 3c054af, DONE_WITH_CONCERNS, 39/39 verts) — revue en cours
Task 12: concern remonté par l'implémenteur : les trois `startActivity` de l'onboarding ne sont pas protégés contre ActivityNotFoundException. Réel, à corriger — c'est le tout premier écran de l'application.

Ruling 20: les trois boutons de l'onboarding essaient plusieurs cibles de réglages dans l'ordre et se rabattent sur la fiche de l'application, aucune ne pouvant lever `ActivityNotFoundException`. — Raison : signalé par l'implémenteur, confirmé par la revue. Un `startActivity` nu vers un écran de réglages absent d'une surcouche fait planter l'application sur son tout premier écran, avant même que l'utilisateur ait armé quoi que ce soit. La fiche de l'application est déclarée par toutes les versions d'Android. Pour la batterie, une cible intermédiaire est ajoutée : la liste générale d'optimisation, meilleur repli que la fiche. — Coût si faux : l'utilisateur atterrit sur un écran de réglages moins précis que prévu, au lieu d'un plantage.
Task 12: fix round 1/5 (1 traité, 0 ouvert — chaîne de repli sur les intents de réglages ; commits 3c054af..f27551f)
Task 12: complete (commits c2e35b9..f27551f, review clean après 1 round de correction)
Task 13: implémenté (commit 1bce718, DONE_WITH_CONCERNS, 39/39 verts) — revue en cours

Ruling 21: l'alarme de secours porte une action dédiée `com.cocorico.SECOURS`, et `AlarmReceiver` l'ignore quand `AlarmState.estEnCours` est faux. — Raison : la revue a contesté à juste titre l'analyse « auto-corrigée » de l'implémenteur. Cas non couvert : le secours a déjà *tiré*, son broadcast est en file, puis l'utilisateur résout le défi ; `annuler()` ne rappelle pas un broadcast déjà parti, le service s'arrête, puis le broadcast arrive et relance sonnerie et défi neuf — sans aucun `ACTION_DEFI_RESOLU` restant pour nettoyer. L'alarme revient après que l'utilisateur s'est levé. Fenêtre étroite, conséquence maximale, correctif à trois lignes. `marquerTerminee` s'écrit en `commit()` avant, donc le garde-fou lit un état fiable. — Coût si faux : le secours ne relancerait pas une alarme dont l'état persistant a été perdu, cas déjà couvert par `BOOT_COMPLETED`.
Task 13: fix round 1/5 (1 traité, 0 ouvert — action dédiée + garde-fou estEnCours ; commits 1bce718..4ec4c50)
Task 13: minor (deferred): un PendingIntent de secours armé par une version antérieure de l'app ne serait pas annulé par la nouvelle (identité d'Intent modifiée par l'ajout d'action) — cas de mise à jour uniquement, sans objet pour une V1
Task 13: complete (commits 8c9837d..4ec4c50, review clean après 1 round de correction)

TOUTES LES TÂCHES SONT TERMINÉES. Revue finale de branche à lancer.

## Revue finale de branche (opus) — verdict : ne pas fusionner

5 Critical, 8 Important. Vague de correction unique lancée.

Ruling 22: les cinq Critical, plus I1, I2, I3, I4, I7, I8 et deux minors (Class.forName, KDoc trompeur) sont corrigés dans cette vague. — Raison : C1 (onStartCommand non idempotent, MediaPlayer empilés toutes les 30 s), C2 (notification sans contentIntent), C3 (LocalContentColor noir, pavé illisible), C4 (AlarmState périmé), C5 (lecture de config non protégée) sont chacun un chemin vers l'échec que ce produit existe pour empêcher. Tous sont des correctifs localisés. I1/I2 concernent Android 14/15 en cible, I3 le crash au démarrage, I4 désarme le filet exactement quand il sert, I7 le routage audio, I8 une promesse de manifeste non tenue. — Coût si faux : vague plus large que nécessaire, rattrapable par la re-revue.

Ruling 23: I5 (comptage des triches) est réduit à corriger le libellé mensonger de l'écran de victoire ; le vrai comptage est reporté. — Raison : la statistique s'appelle « Réveils sans triche » alors que rien ne compte les triches — l'affirmation est fausse aujourd'hui et doit cesser. Mais décider ce qui compte comme triche (touche volume ? retour ? relance du service ?) est une décision produit, pas un correctif. — Coût si faux : la V1 affiche une série de réveils honnête au lieu d'une statistique de triche.

Ruling 24: I6 (indicateur de volume permanent et avertissement de remontée) est implémenté dans cette vague. — Raison : c'est explicitement dans la spec §6, `InactivityTracker.millisRestantes` existe déjà et n'est appelé que par les tests, et sans ça la remontée du volume est une punition inexpliquée au lieu de la règle lisible qui fait le produit. — Coût si faux : deux composables de plus sur l'écran du défi.

Vague de correction finale : commits 798f8ff, a663dcb, 0b6d156. 45 tests verts (39 + 6 nouveaux AlarmStateTest). Re-revue ciblée unique en cours.
Deux écarts signalés par l'implémenteur : lecture de config également non protégée dans `AlarmService.onStartCommand` (corrigée aussi — celle-là donne une alarme muette) ; écran du défi rendu défilable (débordement à grande taille de police = touche de validation inatteignable = alarme inarrêtable).
Point d'attention : `toutesAccordees` inclut désormais `canUseFullScreenIntent()`, donc barrière dure supplémentaire sur Android 14+.

## Re-revue de la vague finale — verdict : fusion possible

Les cinq Critical, les huit Important, les trois minors et la check-list sont
clos. Les deux pièges les plus dangereux ont été évités : le garde-fou
d'idempotence ne survit pas à la mort du processus (donc le filet de secours
relance bien une alarme tuée), et la fenêtre de validité du drapeau est un
horodatage glissant réarmé toutes les 30 s (donc une alarme qui sonne depuis
cinq heures reste valide).

Quatre points connus, non bloquants, assumés :

- La jauge de volume peut sortir de l'écran par défilement quand le défi est
  ouvert et que la taille de police système est très grande. Compromis délibéré
  contre une touche de validation inatteignable.
- L'avertissement de remontée en 15 sp sur fond rouge est à ~4,2:1, juste sous
  le seuil AA pour du texte normal. Même paire que la ligne voisine préexistante.
- `MainActivity.onResume` lit les SharedPreferences sur le thread principal.
- Le chemin réentrant ne répare plus un `MediaPlayer` mort alors que le service
  vit encore. Risque faible pour un lecteur `USAGE_ALARM` en boucle.

Observations hors périmètre consignées pour plus tard : la course entre un
broadcast de secours déjà parti et la résolution (couverte par une ligne de
recette), les démarrages d'activité en arrière-plan non garantis sur Android 10+,
la restauration du volume après un arrêt forcé qui dépend du fichier disque, et
l'absence d'état distinct sur l'accueil quand les alarmes exactes ont été
révoquées.

Faiblesse relevée dans les nouveaux tests : ils dérivent leur durée de
`AlarmState.FENETRE_VALIDITE_MS`, donc rien ne fixe la fenêtre à une heure —
réduire la constante à une minute les laisserait tous verts.

## Re-revue de la vague finale

Tous les points (C1-C5, I1-I4, I7, I8 et les quatre items annexes) confirmés
corrigés, vérifiés contre l'arbre de travail et non contre le rapport. Les deux
écarts pris par l'implémenteur — lecture de configuration également protégée
dans le service, écran du défi rendu défilable — sont jugés justes.

Ruling 25 : les deux régressions Important introduites par la vague sont
corrigées immédiatement, avec deux minors de la même zone. Raison : (a)
`runCatching` autour d'un appel suspendu avale `CancellationException` — un
service détruit pendant la lecture DataStore repartait démarrer un MediaPlayer
orphelin, à fond, sans référence pour l'arrêter ; (b) la branche de ré-entrée
ne retente jamais un démarrage raté, donc un lecteur nul vaut silence permanent
alors que le code d'avant retentait. Ce sont exactement les deux échecs
interdits, dans le code que la vague venait de réécrire, et chacun tient en
trois lignes. Le processus proscrit une seconde vague large, pas la correction
ciblée d'une régression que la vague a elle-même créée. Coût si faux : quatre
lignes à revoir, build et 45 tests en garde-fou.

Ruling 26 : la barrière dure d'onboarding sur `canUseFullScreenIntent()` reste
en l'état pour cette branche. Raison : la re-revue propose de laisser passer
l'utilisateur après refus explicite, avec bandeau persistant, en arguant que le
correctif C2 rend la dégradation survivable. C'est un arbitrage produit, pas un
défaut : le comportement actuel est le plus sûr des deux et l'écran de réglages
ciblé existe réellement. À soumettre à l'utilisateur plutôt qu'à trancher seul.
Coût si faux : sur Android 14+, un utilisateur qui ne trouve pas le réglage est
bloqué avant d'avoir pu régler quoi que ce soit.

## Résiduels connus, non corrigés

- `VolumeStateMachine` a deux états là où la spec en décrit trois avec une rampe
  progressive de 3 s. Divergence désormais visible, la jauge l'affiche.
- La jauge annonce « 30 % » nominal alors que l'application arrondit vers le
  cran inférieur du flux d'alarme.
- Contraste 4,1:1 sur l'écran rouge : sous WCAG AA pour du corps de texte, mais
  imposé par la charte de marque.
- La jauge est dans la zone défilante, donc plus « en permanence » à l'écran
  une fois descendu au pavé numérique.
- Les nouveaux tests d'`AlarmState` dérivent leur durée de la constante qu'ils
  vérifient : réduire la fenêtre ne les ferait pas échouer.
