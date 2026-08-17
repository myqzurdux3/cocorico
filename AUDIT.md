# Audit de Cocorico

Audit complet du dépôt, mené le 17 août 2026 sur la branche `cocorico-v1`
(HEAD `190d41c`). Ce document est le compte rendu ; il est mis à jour au fil
des phases et se termine par un rapport final.

---

## Phase 0 — Cartographie

### Ce que c'est

Application Android native, **Kotlin + Jetpack Compose**, module unique
`:app`. Un réveil dont la promesse est qu'on ne peut pas l'arrêter sans
accomplir une tâche : sonnerie très forte, baisse du volume quand le
téléphone est pris en main, arrêt seulement après un défi résolu (calcul
mental, pompes comptées par les capteurs, ou photo d'un objet tiré au sort
jugée par l'API Gemini).

### Chaîne de construction

| Élément | Valeur |
|---|---|
| Build | Gradle 8.14.3 (wrapper versionné), AGP 8.7.2, Kotlin 2.0.21 |
| SDK | `minSdk 28`, `targetSdk 35`, `compileSdk 35`, JVM 17 |
| Dépendances | catalogue de versions `gradle/libs.versions.toml` |
| Persistance | Room 2.6.1 (schéma exporté et versionné) + DataStore 1.1.1 |
| Caméra | CameraX 1.3.4 |
| Tests | JUnit 4.13.2, `kotlinx-coroutines-test` 1.8.1 |
| Points d'entrée | `MainActivity` (réglages), `AlarmActivity` (écran qui sonne), `AlarmService` (premier plan), `AlarmReceiver` / `BootReceiver` |

Commandes : `./gradlew testDebugUnitTest` pour les tests,
`./gradlew assembleDebug` pour l'APK, `./gradlew clean` pour repartir de zéro.

### État réel, mesuré maintenant

`./gradlew clean testDebugUnitTest assembleDebug` — exécuté, pas supposé :

| Mesure | Valeur |
|---|---|
| Résultat | **BUILD SUCCESSFUL**, 46 tâches exécutées |
| Tests | **203 tests, 0 échec, 0 erreur, 0 ignoré**, 24 classes |
| Durée du build à froid | **39 s** |
| Durée des tests seuls | 0,21 s |
| Avertissements du compilateur | **2** (`LocalLifecycleOwner` déprécié, dans `PhotoChallenge.kt:157` et `EssaiPhotoScreen.kt:69`) |
| APK de debug | 29,8 Mo, non minifié |
| Lignes de Kotlin | 7 063 en production, 2 641 en test |
| `TODO` / `FIXME` / `HACK` | aucun |

**La base est verte.** Le refactoring peut commencer.

### Couverture

**Non mesurée** : aucun greffon de couverture n'est configuré, et en ajouter un
modifierait la chaîne de build avant l'audit. À défaut, une mesure indirecte :
**37 des 58 fichiers de production n'ont pas de test portant leur nom.** Ce
n'est pas un défaut en soi — l'architecture concentre volontairement la logique
décidable dans des classes sans dépendance Android, qui sont testées, et laisse
les composants Android au simple câblage. Mais c'est un fait à garder en tête :
la surface non testée est exactement celle où tous les bugs constatés sur
appareil réel sont apparus (permission caméra jamais demandée, alarmes annulées
à la mise à jour, sélection par pièce inerte au réveil).

### Ce qui manque au dépôt

Constaté, pas supposé : **pas de `README.md`, pas de `LICENSE`, pas de
`CONTRIBUTING.md`, pas de CI** (`.github/` n'existe pas). Le `.gitignore`
existe et couvre correctement Gradle, Android, les IDE et les artefacts de
build ; `local.properties` n'est pas versionné.

---

## Phase 1 — Défauts relevés

Six relecteurs indépendants, en lecture seule, un par domaine. Les constats
marqués « certain » ont été établis par lecture du code ; ceux marqués
« à vérifier » demandent un appareil ou une exécution. **Rien n'a été corrigé
à ce stade.**

Trié par sévérité. « bloquant » a un sens précis ici : l'alarme peut ne pas
sonner, ne pas pouvoir être arrêtée, laisser le téléphone à fond, ou
l'utilisateur peut rester coincé.

### Bloquant

| Fichier:ligne | Description | Correction proposée | Confiance |
|---|---|---|---|
| `alarm/SecoursScheduler.kt:18` | Le filet de secours à 30 s utilise `setExactAndAllowWhileIdle`, que `AlarmScheduler.kt:22` documente lui-même comme throttlé à une fois par 9 min en Doze — donc inopérant précisément dans le scénario pour lequel il existe (application tuée, appareil endormi). | Utiliser `setAlarmClock`, seule API exemptée de Doze. | certain |
| `alarm/SecoursScheduler.kt:18` | Aucune garde `canScheduleExact()` ni `try/catch` : sur Android 12 permission retirée, `setExactAndAllowWhileIdle` lève une `SecurityException` et fait planter le service **pendant que l'alarme sonne**. | Reprendre la protection de `AlarmScheduler.schedule`. | probable |
| `alarm/AlarmScheduler.kt:34` | `if (!canScheduleExact()) return null` sort sans rien programmer **ni rien signaler** : plus aucune alarme n'est posée et l'utilisateur l'ignore. | Repli `setAndAllowWhileIdle` + notification persistante « alarme non garantie ». | certain |
| `data/AlarmConfigRepository.kt:17` | `preferencesDataStore` sans `corruptionHandler` : un fichier tronqué fait échouer `current()` définitivement, et les trois appelants avalent l'exception — l'alarme n'est **plus jamais** reprogrammée. | `corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }`. | certain |
| `alarm/AlarmService.kt:143` | Le retour de `schedule()` est jeté dans un `runCatching` : un `null` signifie « plus jamais d'alarme » et passe inaperçu au moment le plus critique du cycle. | Tester le résultat, notifier l'échec de replanification. | certain |
| `AndroidManifest.xml:66` | Aucun récepteur `ACTION_TIMEZONE_CHANGED` ni `ACTION_TIME_SET` : `setAlarmClock` mémorise un instant absolu, donc un voyage ou un réglage manuel de l'horloge fait sonner à la mauvaise heure murale, voire saute l'alarme. | Ajouter les deux actions au filtre de `BootReceiver`, déjà idempotent. | certain |
| `ring/RingtonePlayer.kt:39` | `appliquer(PLEIN)` s'exécute avant toute création de lecteur, sans protection : `setStreamVolume(STREAM_ALARM)` peut lever une `SecurityException` sous « Ne pas déranger » (`ACCESS_NOTIFICATION_POLICY` absent du manifeste), exception qui remonte et laisse l'alarme **muette**. | Créer le lecteur d'abord, encapsuler `appliquer` dans un `runCatching` tracé. | à vérifier |
| `ring/RingtonePlayer.kt:34` | Un redémarrage du service après `arreter` recapture comme « volume d'origine » celui que l'alarme vient d'imposer : la restauration finale laisse l'appareil **au maximum**. | Ne capturer qu'en l'absence d'alarme en cours (`AlarmState.estEnCours`). | probable |
| `ring/RingtonePlayer.kt:151` | `appliquer` est public et piloté depuis une instance qui n'a jamais mémorisé `volumeOrigine` (celle d'`AlarmActivity`) : si le service meurt et restaure pendant que l'activité repousse PLEIN, le téléphone reste à fond. | Refuser `appliquer` tant que `volumeOrigine` est nul. | probable |
| `ui/OnboardingScreen.kt:41` | La colonne des autorisations n'a **aucun défilement** alors qu'un premier lancement empile 4 à 5 cartes : le bouton « J'ai tout autorisé », seul chemin de sortie, sort de l'écran. L'utilisateur ne peut plus quitter l'onboarding. | `.verticalScroll(rememberScrollState())`. | probable |

### Majeur

| Fichier:ligne | Description | Correction proposée | Confiance |
|---|---|---|---|
| `ui/StatsScreen.kt:209` | **La ligne de moyenne du graphique n'apparaît jamais** : `.height(2.dp)` puis `.padding(bottom = …)` avant `.background()` — la marge est retranchée à l'intérieur des 2 dp, le fond est peint sur une hauteur nulle. | Décaler par `offset(y = -hauteurMax * position)` au lieu de `padding`. | certain |
| `ui/HomeScreen.kt:145` | L'accueil annonce « Photo — N objets » sans consulter la permission caméra ni la clé d'API, alors qu'`AlarmActivity` se rabat en silence sur les calculs — le défaut déjà corrigé côté réglages, resté ici. | Mêmes critères que `challengeEffectif`, sinon annoncer le repli. | certain |
| `ring/CapteurPompes.kt:53` | L'accéléromètre est enregistré en `SENSOR_DELAY_UI` (66,7 ms) alors que `HandDetector.kt:41` documente cette cadence comme repliant les vibrations du haut-parleur dans la bande utile, et utilise `SENSOR_DELAY_GAME`. Alarme à fond sur un sol dur, plus aucune pompe n'est comptée. | Passer en `SENSOR_DELAY_GAME`. | probable |
| `test/ring/EstimateurGraviteTest.kt:19` | Le test annonce jouer « au pas de 20 ms, la cadence demandée à l'accéléromètre (`SENSOR_DELAY_UI`) ». 20 ms est `SENSOR_DELAY_GAME` ; `SENSOR_DELAY_UI` vaut 66,7 ms. **La garde anti-triche des pompes est validée à trois fois la cadence réelle du code.** | Rejouer à 66,7 ms, ou aligner le code sur `SENSOR_DELAY_GAME` et corriger la KDoc. | certain |
| `challenge/photo/PhotoChallenge.kt:303` | Une panne du juge (réseau coupé, quota 429, clé invalide, HTTP 500) est convertie en « Pas encore reconnu. Réessaie » : l'utilisateur photographie en boucle un objet correct sans jamais apprendre que rien ne peut aboutir. | Remonter la cause via `diagnostiquer`, et après 2–3 échecs non imputables au modèle, basculer sur les calculs. | certain |
| `challenge/photo/JugeGemini.kt:49` | `accepte` réduit `DiagnosticJuge` à un booléen : un refus du modèle devient indistinguable d'une panne, juste avant l'endroit qui a besoin de l'information. | Faire remonter `DiagnosticJuge` jusqu'à `PhotoChallenge`. | certain |
| `challenge/photo/JugeGemini.kt:95` | Un 429 (quota) ou 503 (surcharge) est traité comme un « non » définitif : au réveil, un quota dépassé rend le défi impassable en silence. | Distinguer les codes réessayables, une seconde tentative dans le budget, signaler à l'écran. | certain |
| `challenge/photo/PhotoChallenge.kt:290` | Décodage JPEG pleine résolution, réduction et rotation exécutés sur le **thread principal** (`getMainExecutor`) : plusieurs centaines de ms de gel de l'écran d'alarme à chaque photo. | Exécuteur dédié, ou conversion sur `Dispatchers.Default`. | certain |
| `challenge/photo/PhotoChallenge.kt:403` | `decodeByteArray` décode en pleine résolution (~48 Mo pour 12 Mpx) avant de réduire, et ni `brut` ni `reduit` ne sont recyclés : pic mémoire double à chaque capture. | `inSampleSize` calculé pour `COTE_MAX_PX`, puis `recycle()`. | certain |
| `challenge/photo/PhotoChallenge.kt:296` | Un décodage raté remet `enAttenteVerdict` à faux sans message : l'utilisateur appuie, rien ne se passe — exactement le défaut qu'`echecCamera` corrigeait côté caméra. | Message d'échec + repli calculs après deux captures perdues. | certain |
| `challenge/photo/PhotoChallenge.kt:309` | `onError` avale l'`ImageCaptureException` sans retour visible : une caméra qui échoue systématiquement donne un bouton qui ne fait rien, indéfiniment. | Compter les erreurs, afficher la cause, rendre le repli atteignable en appui simple. | certain |
| `challenge/photo/PhotoChallenge.kt:289` | Aucun délai maximal autour de `takePicture` : si aucun rappel n'arrive, le bouton reste bloqué sur « Vérification… » pour toujours. | `withTimeoutOrNull` (~10 s). | probable |
| `challenge/photo/PhotoChallenge.kt:137` | `camerasDisponibles` ne teste ni la connectivité ni l'existence d'une caméra arrière : hors ligne, le défi photo s'affiche quand même et n'est franchissable que par l'appui long. | Ajouter un contrôle `ConnectivityManager` et `hasCamera(DEFAULT_BACK_CAMERA)`. | probable |
| `ring/SondeSonnerie.kt:26` | `MediaPlayer.create` prépare le média **de façon synchrone sur le thread principal**, depuis le rappel d'`ActivityResult` : un fichier servi par un fournisseur distant bloque l'interface jusqu'à l'ANR. | Sonder sur `Dispatchers.IO` avec état d'attente. | certain |
| `ring/ApercuSonnerie.kt:55` | L'échec de création du lecteur fait sortir `jouer` en silence : l'utilisateur appuie sur sa sonnerie importée, n'entend rien, n'apprend rien — alors que c'est exactement la panne à découvrir avant la nuit. | Renvoyer l'échec et afficher le message d'erreur d'import. | certain |
| `ring/RingtonePlayer.kt:53` | Aucun `setOnErrorListener` : une mort du serveur média coupe la sonnerie, et rien ne le voit avant la relance du secours — jusqu'à 30 s de silence. | Poser un `setOnErrorListener` qui relance la lecture. | probable |
| `ring/RingtonePlayer.kt:27` | `demarrer` fait deux choses indissociables (volume plein + lecture) : la relance de lecture seule du secours repousse le volume à PLEIN contrairement à ce qu'affirme `AlarmService`, et la machine déjà en BAISSE ne renotifiera jamais. | Séparer `demarrerLecture()` d'`appliquer()`. | probable |
| `ring/InactivityTracker.kt:21` | Le contrat d'horloge n'est pas documenté et `AlarmActivity` fournit `System.currentTimeMillis()`, l'horloge murale : une resynchronisation NTP au réveil fige le compte à rebours ou fait remonter le volume aussitôt. | Exiger une horloge monotone, passer à `SystemClock.elapsedRealtime()`. | certain |
| `ring/HandDetector.kt:64` | Deux horloges mélangées échantillon par échantillon (`event.timestamp` en ns contre `SystemClock.elapsedRealtime`) — la faute exacte déjà corrigée dans `CapteurPompes`. | Utiliser `SystemClock.elapsedRealtime()` sans condition. | probable |
| `alarm/AlarmService.kt:132` | La prochaine occurrence n'est reprogrammée qu'à la résolution du défi ou au boot : une mort du service sans résolution (arrêt forcé, tueur constructeur) laisse le réveil du lendemain non programmé. | Replanifier dès `onStartCommand`. | certain |
| `alarm/AlarmService.kt:81` | `player.estEnLecture()` est faux pendant la lecture asynchrone de la config : un secours arrivant dans cette fenêtre relance `demarrer()` qui réapplique `PLEIN` — exactement ce que le commentaire promet d'éviter, alors que l'utilisateur a le téléphone en main. | Mémoriser le `Job` et ne relancer que s'il est terminé. | certain |
| `alarm/AlarmService.kt:152` | `startActivity` depuis un service en arrière-plan est bloqué depuis Android 10 : le chemin de récupération de l'écran d'alarme ne fonctionne probablement pas, seul le full-screen intent le fait. | Republier la notification full-screen comme unique chemin, corriger le commentaire. | à vérifier |
| `alarm/AlarmService.kt:164` | Le WakeLock est pris pour 30 min et jamais renouvelé, alors que le secours peut faire durer la sonnerie indéfiniment. | Renouveler à chaque passage du secours. | probable |
| `alarm/BootReceiver.kt:59` | L'échec de replanification au démarrage est avalé sans trace ni nouvelle tentative : si le DataStore n'est pas prêt au boot, l'alarme disparaît sans signal. | Journaliser et réarmer via un repli. | certain |
| `data/AlarmConfigRepository.kt:55` | `hour`, `minute` et `volumeMaxPourcent` sont relus **sans borne** : une valeur ≥ 24 fait lever `DateTimeException` dans `schedule()`, exception avalée par tous les appelants — alarme perdue en silence. | Borner à la lecture. | certain |
| `app/schemas/` | Le schéma `1.json` n'est pas versionné : `MIGRATION_1_2` n'est **jamais** exécutée contre un vrai SQLite (le test ne compare que des chaînes). Une migration cohérente en syntaxe mais fautive à l'exécution partirait en production. | Committer `1.json`, ajouter un test `MigrationTestHelper` en androidTest. | certain |
| `ui/AlarmActivity.kt:153` | `setContent` n'est appelé qu'après la lecture DataStore, dans la coroutine : entre l'affichage de la fenêtre et la reprise, l'écran d'alarme est **vide** par-dessus le verrouillage. | Appeler `setContent` dans `onCreate` et laisser la composition observer `defi`. | certain |
| `ui/AlarmActivity.kt:82` | `alarmeAt` est horodaté à la création de l'activité, pas au déclenchement réel : si le service relance l'écran après une mort de l'activité, la durée enregistrée repart de zéro et fausse toutes les statistiques. | Porter l'instant de déclenchement par l'intent ou `AlarmState`. | probable |
| `ui/MainActivity.kt:105` | L'écran courant est dans un `remember` et non un `rememberSaveable` : toute rotation ramène à l'accueil et perd la navigation. | `rememberSaveable`. | certain |
| `ui/MainActivity.kt:62` | `onCreate` relit `EXTRA_VICTOIRE` à chaque recréation, sans `configChanges` : une rotation après fermeture de l'écran de victoire le fait réapparaître indéfiniment. | Consommer l'extra, ou ne le lire que si `savedInstanceState == null`. | probable |
| `ui/HomeScreen.kt:83`, `ui/RingtoneScreen.kt:100` | Colonnes sans défilement : à grande taille de police, « Armer le coq » et le message d'erreur d'import deviennent inatteignables. | `.verticalScroll(rememberScrollState())`. | probable |
| `ui/AlarmActivity.kt:406` | Le défilement n'est activé que si `defiOuvert` : à taille de police maximale, le bouton « Faire taire ce coq » peut être rogné et le défi devient inaccessible. | Défilement inconditionnel. | à vérifier |
| `challenge/pompes/PompesChallenge.kt:141` | L'appui long de renoncement (600 ms) reste actif pendant la descente, alors que la tenue basse exigée est d'au moins 600 ms torse contre l'écran : un abandon accidentel bascule sur les calculs. | N'accepter l'appui long que hors position basse. | à vérifier |
| `AndroidManifest.xml:23` | `allowBackup="true"` sans `dataExtractionRules` : le DataStore contenant la clé d'API Gemini en clair entre dans la sauvegarde Android et le transfert d'appareil à appareil — ce que le commentaire du manifeste dément explicitement. | Exclure `cocorico_alarm.preferences_pb`, ou `allowBackup="false"`. | certain |
| `data/AlarmConfigRepository.kt:47` | La clé d'API est persistée **en clair** dans DataStore. | Chiffrer ce champ via le Keystore Android. | certain |
| `app/build.gradle.kts:21` | Le bloc `release` ne déclare aucun `signingConfig` : `assembleRelease` produit un APK non signé, impossible à installer. | Ajouter un `signingConfig` alimenté hors dépôt. | certain |

### Mineur

| Fichier:ligne | Description | Correction proposée | Confiance |
|---|---|---|---|
| `ui/AlarmActivity.kt:472` | La jauge affiche « Volume — 100 % » / « 30 % » **en dur**, ce qui contredit le plafond choisi : à 60 % de plafond, l'écran annonce 100 %. Défaut introduit par le commit `190d41c`. | Calculer depuis `NiveauxVolume` et `config.volumeMaxPourcent`. | certain |
| `challenge/photo/PhotoChallengeEtat.kt:40` | `_isSolved = MutableStateFlow(objets.isEmpty())` : une liste vide vaut « défi déjà résolu », donc alarme arrêtée sans aucune photo. Non atteignable aujourd'hui (les replis de `tirer` couvrent la sélection vide), mais aucun garde-fou ne le maintient. | `require(objets.isNotEmpty())`. | certain |
| `challenge/photo/CatalogueObjets.kt:194` | `nombre.coerceIn(0, tous.size)` transforme un nombre nul ou négatif en liste vide, qui alimente le cas ci-dessus. | Exiger `nombre >= 1`. | certain |
| `challenge/MathChallengeEngine.kt:15` | `total` n'est pas validé : à 0, `MathChallenge` calcule `0/0` → `NaN` passé à `LinearProgressIndicator`. | `require(total >= 1)`. | certain |
| `ring/Sonneries.kt:44` | Le repli de `parId` est l'indice magique `toutes[2]`, sur un chemin traversé à chaque réveil : réordonner la liste change silencieusement la sonnerie de repli. | Nommer la sonnerie de repli. | certain |
| `ring/RingtonePlayer.kt:47` | Le repli choisit `toutes.first()`, la sonnerie la plus douce, sans laisser de trace : quelqu'un qui avait choisi la sirène parce que le coq ne le réveille pas se réveille — ou pas — sans jamais le savoir. | Se replier sur la plus forte et poser un indicateur affiché au matin. | probable |
| `ring/RingtonePlayer.kt:95` | La branche `Embarquee` passe `resId = -1` pour l'entrée personnalisée, valeur que la KDoc dit « ne devoir jamais être utilisée » : ça ne marche que parce que `MediaPlayer.create` renvoie `null` et que la chaîne de replis rattrape. | Traiter explicitement `sonnerie.personnalisee`. | certain |
| `ring/SonneriePersonnaliseeStore.kt:30` | `ecrire` ne relâche jamais la permission URI persistée précédente : elles s'accumulent contre le quota de l'application. | `releasePersistableUriPermission` avant d'écrire. | certain |
| `ring/SondeSonnerie.kt:27` | Le `MediaPlayer` fuit si `duration` lève : `release()` n'est atteint que sur le chemin nominal. | Libérer dans un `finally`. | certain |
| `ring/HandDetector.kt:50` | Sans accéléromètre, `demarrer` ne fait rien en silence : la baisse à la prise en main devient inopérante sans signal, alors que `CapteurPompes` expose un `capteurDisponible()` pour ce cas. | Exposer la disponibilité et tracer la dégradation. | certain |
| `ring/CapteurPompes.kt:84` | Le seuil de proximité `maximumRange / 2f` dégénère aux deux bouts : nul si le pilote rapporte 0, 50 cm sur un capteur de portée 100 cm — dans les deux cas aucune répétition n'est comptée. | `values[0] < min(5f, maximumRange)`, refus si `maximumRange <= 0`. | à vérifier |
| `challenge/pompes/CompteurPompes.kt:125` | `TENUE_BASSE_MIN_MS` (150 ms) est inatteignable : la borne de 600 ms domine toujours. La garde anti-effleurement n'existe que dans les tests. | Supprimer la constante ou mesurer deux durées distinctes. | certain |
| `ring/MouvementDetector.kt:98` | Le plancher à 1,5 m/s² est documenté comme non calibré et produisant une énergie nulle sur une prise en main molle : le réarmement du compte à rebours par le mouvement peut n'avoir **jamais** fonctionné sur appareil. | Mesurer sur geste réel et abaisser. | certain |
| `data/SerieCalculator.kt:14` | `serie()` n'exige pas que le réveil le plus récent soit d'aujourd'hui ou d'hier : une série cassée depuis des semaines s'affiche encore comme active. | Prendre `aujourdhui: LocalDate` et renvoyer 0 si antérieur à hier. | certain |
| `data/SerieCalculator.kt:31` | `retardMoyenSecondes` duplique la moyenne de `StatsCalculator` **sans son filtre de plausibilité** : un seul enregistrement aberrant rend le « Retard moyen » absurde. | Réutiliser `StatsCalculator` et supprimer la fonction. | certain |
| `data/StatsCalculator.kt:240` | `positionMoyenne` divise la moyenne globale par le maximum des 7 barres affichées : dès que les réveils récents sont plus rapides que la moyenne historique, la ligne est plaquée au sommet. | Étendre le maximum, ou calculer la moyenne sur les durées affichées. | certain |
| `data/StatsCalculator.kt:139` | `dureeCeMatinSecondes` est le dernier enregistrement quelle que soit sa date : après un jour sans réveil, l'écran étiquette « ce matin » une valeur de la veille. | Renvoyer `null` hors du jour civil courant. | probable |
| `data/WakeRecord.kt:14` | `triches` est écrit en dur à 0 et n'est lu nulle part : colonne non nulle morte transportée dans toute migration future. | La supprimer (migration v3), ou la renseigner. | certain |
| `data/AlarmConfig.kt:15` | `hour`, `minute`, `volumeMaxPourcent` sont des `Int` nus sans plage documentée : le type autorise des états qui font planter la planification. | Bloc `init` avec `require`. | certain |
| `alarm/AlarmScheduler.kt:39` | Aucun traitement explicite ni test des transitions d'heure d'été : une alarme dans le trou du printemps est décalée en silence. | Résolution explicite + test. | certain |
| `alarm/AlarmState.kt:55` | `commit()` est une écriture disque synchrone sur le thread principal d'`onReceive`, à l'instant du déclenchement. | Garder `commit()` mais sous `goAsync()`. | certain |
| `alarm/AlarmReceiver.kt:22` | Aucun WakeLock entre le retour d'`onReceive` et son acquisition par le service, alors que `startForegroundService` est asynchrone. | WakeLock court dans le récepteur. | à vérifier |
| `alarm/AlarmService.kt:60` | Le chemin `ACTION_DEFI_RESOLU` sort avant `startForeground` : risque de `ForegroundServiceDidNotStartInTimeException`. | `startForeground` avant le test d'action. | à vérifier |
| `challenge/photo/RequeteVision.kt:124` | Seul le français est reconnu : si le modèle répond « Yes »/« No », toutes les photos sont rejetées sans cause visible. | Accepter aussi l'anglais. | probable |
| `challenge/photo/RequeteVision.kt:88` | Le groupe capturé n'est pas dé-échappé : un `\n` reste la suite littérale, donc les séparateurs de phrase ne coupent pas et une négation d'une ligne précédente peut faire rejeter un « oui » franc. | Dé-échapper avant `estOuiFranc`. | probable |
| `challenge/photo/RequeteVision.kt` (tests) | **Aucun test n'utilise la forme réelle d'une réponse Gemini** (`candidates[].content.parts[].text`) : tous emploient une enveloppe étrangère à l'API visée. La lecture du verdict n'est validée que par accident. | Test bâti sur une vraie réponse `generateContent`, erreur comprise. | certain |
| `challenge/photo/JugeGemini.kt:130` | `masquer`, seul garde-fou contre l'affichage de la clé, n'est couvert par **aucun** test. | Test JVM pur sur `masquer`. | certain |
| `challenge/photo/JugeGemini.kt:63` | `withTimeoutOrNull` ne peut pas interrompre la lecture bloquante : au dépassement, le thread IO reste occupé et chaque essai empile un thread bloqué. | `connectTimeout`/`readTimeout` fractionnaires du budget, fermeture sur annulation. | probable |
| `challenge/photo/RequeteVision.kt:110` | `echapper` parcourt les ~400 000 caractères du base64 et en construit une copie, alors que l'alphabet `NO_WRAP` ne contient rien à échapper : ~1 Mo d'allocations inutiles par photo, pendant que l'alarme sonne. | Ne l'appliquer qu'à la consigne. | certain |
| `challenge/photo/PhotoChallenge.kt:99` | Le tirage et l'écriture de l'exclusion ont lieu dans le constructeur, sur le thread principal, et **même quand `AlarmActivity` va finalement se rabattre sur les calculs** : des objets jamais montrés sont exclus du lendemain. | Initialisation paresseuse après la décision. | certain |
| `challenge/photo/PhotoChallenge.kt:155`, `pompes/PompesChallenge.kt:83` | `_progress.value` est écrit **pendant la composition** : effet de bord non autorisé, source de recompositions en cascade. | Dériver le flux au lieu d'écrire dans `Content`. | certain |
| `challenge/MathProblemGenerator.kt:15` | Contrat implicite non testé : le pavé n'a ni touche « moins » ni décimale et borne à 6 chiffres, donc une réponse négative ou non entière serait insaisissable. | Test sur toutes les difficultés : `answer in 0..999999`. | certain |
| `challenge/photo/SelectionObjets.kt:25` | `SEUIL_AVERTISSEMENT` duplique `PhotoChallenge.nombrePour(DIFFICILE)` sans lien de code. | Dériver la constante. | certain |
| `ui/AlarmActivity.kt:280` | `secondesAvantRemontee` est réécrit toutes les 500 ms même quand le volume est PLEIN, cas où la valeur n'est pas affichée : tout `EcranAlarme` est recomposé, aperçu caméra compris, deux fois par seconde pendant toute l'alarme. | Ne mettre à jour que hors PLEIN. | probable |
| `ui/HomeScreen.kt:68` | `lireNom` (SharedPreferences) est appelé pendant la composition, avec `config.ringtoneId` pour clé : lecture disque sur le fil principal, et « Remplacer le fichier » ne change pas l'identifiant, donc l'accueil affiche l'ancien nom. | Exposer le nom via le ViewModel. | probable |
| `ui/RingtoneScreen.kt:59` | `lireUri`/`lireNom` dans l'initialiseur de `remember` : deux lectures disque sur le fil principal à chaque entrée. | Charger hors composition. | probable |
| `ui/StatsScreen.kt:50`, `ui/VictoryScreen.kt:45` | Lectures de base non protégées dans un `LaunchedEffect` : une exception plante l'application, sur l'écran de victoire juste après l'écriture du `WakeRecord`. | `runCatching` + état d'erreur. | à vérifier |
| `ui/VictoryScreen.kt:38` | `serie` et `retard` valent 0 avant la fin du chargement : l'écran affiche « 0 réveil d'affilée », mesure fausse présentée comme vraie — là où `StatsScreen` a choisi le nullable pour éviter ça. | Initialiser à `null`. | probable |
| `ui/VictoryScreen.kt:94` | `Statistique` place libellé et valeur dans un `Row` sans `weight` : une valeur longue se replie caractère par caractère. | `Modifier.weight(1f)` au libellé. | probable |
| `ui/HomeScreen.kt:182` | Les pastilles de jour font 38 dp, sous le minimum de 48 dp d'une cible tactile. | 48 dp ou `minimumInteractiveComponentSize()`. | certain |
| `ui/StatsScreen.kt:63` (+ 4 autres écrans) | Le « ‹ Retour » est un `Text` cliquable sans marge : cible de ~20 dp. | Composable de retour commun. | certain |
| `ui/ChallengeSettingsScreen.kt:139` | Après un second refus caméra, `demanderCamera.launch` ne fait plus rien : échec silencieux, sans chemin vers les réglages Android. | Détecter le refus définitif et proposer la fiche de l'application. | probable |
| `ui/MainActivity.kt:100` | L'état des permissions n'est lu qu'à la composition : un retour depuis les réglages Android ne le rafraîchit pas. | Relire sur `ON_RESUME`. | probable |
| `ui/MainActivity.kt:62` | L'activité **exportée** lit `EXTRA_VICTOIRE` d'un intent non validé : une autre application peut afficher l'écran de victoire et masquer le seul chemin visible vers `AlarmActivity` pendant que la sonnerie continue. | N'accepter l'extra que de l'application elle-même. | probable |
| `ui/EssaiPhotoScreen.kt:72` | `remember { JugeGemini(cleApi) }` sans clé : un changement de clé pendant que l'écran est composé laisse l'ancienne en place. | `remember(cleApi)`. | probable |
| `ui/HomeViewModel.kt:81` | La clé d'API est persistée sans `trim` : un collage avec espace ou saut de ligne fait échouer tous les verdicts en silence. | `trim` et rejet des caractères de contrôle. | probable |
| `AndroidManifest.xml:13` | Permission `VIBRATE` demandée alors qu'aucun `Vibrator` n'est utilisé. | Retirer. | certain |
| `AndroidManifest.xml:15` | `CAMERA` demandée sans `<uses-feature required="false">` : le Play Store rendrait l'application invisible aux appareils sans caméra, alors qu'elle fonctionne sans. | Déclarer la fonctionnalité en `required="false"`. | probable |
| `AndroidManifest.xml:46` | `AlarmActivity` est `showWhenLocked` et le défi photo y envoie une image aux serveurs Google : toute personne ayant l'appareil en main au réveil déclenche des envois avec la clé de l'utilisateur, sans déverrouiller. | Exiger le déverrouillage, ou l'annoncer aux réglages. | probable |
| `app/build.gradle.kts:41` | `kotlinx.coroutines` est importé en production sans être déclaré : la compilation repose sur une remontée transitive. | Déclarer `kotlinx-coroutines-android`. | certain |
| `app/build.gradle.kts:22` | `isMinifyEnabled = false` et aucun `proguard-rules.pro` : ni R8 ni réduction des ressources. | Activer avec les règles par défaut d'AGP. | certain |
| `gradle/wrapper/gradle-wrapper.properties:5` | Aucune `distributionSha256Sum` : la distribution Gradle n'est vérifiée par aucune empreinte. | Ajouter l'empreinte publiée. | certain |
| `settings.gradle.kts:8` | Aucun `verification-metadata.xml` ni verrouillage : les artefacts résolus ne sont contrôlés ni par empreinte ni par signature. | Générer les métadonnées de vérification. | certain |
| `gradle/libs.versions.toml:2` | AGP 8.7.2 embarque dans sa **chaîne de build** des versions de `protobuf-java` et `commons-io` visées par CVE-2024-7254 et CVE-2024-47554. Rien n'est livré dans l'APK. | Monter AGP, ou contraindre ces deux artefacts. | à vérifier |
| `.github/`, `README.md`, `LICENSE` | Aucune CI, aucun README, aucune licence. | Voir phase 6. | certain |

### Cosmétique

| Fichier:ligne | Description | Confiance |
|---|---|---|
| `challenge/MathChallenge.kt:108` | Après résolution, `submit` rend `false` et l'écran affiche brièvement « Non. Et le coq a entendu. » sur une **bonne** réponse. | probable |
| `ui/AlarmActivity.kt:171` | La résolution du défi n'est constatée que par scrutation toutes les 500 ms : jusqu'à une demi-seconde de sonnerie après la victoire. | certain |
| `ui/AlarmActivity.kt:287` | Seul `onKeyDown` est consommé pour les touches de volume ; `KEYUP` atteint encore `PhoneWindow`, qui peut afficher le panneau de volume système par-dessus l'écran d'alarme. | à vérifier |
| `data/StatsCalculator.kt:170` | `maxByOrNull` sur une division entière, départage dépendant de l'ordre d'insertion. | certain |
| `challenge/photo/RequeteVision.kt:64` | Corps JSON concaténé à la main plutôt que construit par `org.json`, disponible sur la plateforme. L'échappement relu est correct. | certain |
| `challenge/photo/RequeteVision.kt:52` | `url(modele: String)` accepte n'importe quelle chaîne, interpolée dans le chemin. Inoffensif aujourd'hui. | certain |
| `gradle/libs.versions.toml:39` | La version du greffon KSP est en dur dans `[plugins]` alors que toutes les autres passent par `[versions]`. | certain |
| `.gitignore:9` | `.kotlin/`, présent dans l'arbre, n'est pas ignoré. | certain |

### Secrets

Recherche par motifs (`AIza…`, `sk-…`, `ghp_…`, `BEGIN PRIVATE KEY`,
`storePassword`, `api_key=…`) sur **toutes les révisions** et les 110 fichiers
suivis : **aucun secret versionné**. `local.properties` est présent dans
l'arbre de travail, correctement ignoré, et absent de toutes les révisions.

Réserve : un secret sans motif reconnaissable resterait invisible à cette
recherche.

---

## Phase 2 — Code mort et dépendances

### Supprimé — commit `ad62391`

Chaque élément est accompagné de la preuve qui a autorisé sa suppression :
une recherche dont le seul résultat était la déclaration elle-même.

| Élément | Preuve |
|---|---|
| `Challenge.progress` + ses 3 implémentations | `grep -rn '\bprogress\b' app/src` : aucun consommateur ne lit jamais `progress` **sur un `Challenge`**. `MathChallengeEngine.progress`, lu par `MathChallenge` pour sa propre jauge, reste. |
| `Challenge.onUserInteraction` + ses 3 implémentations | Déclaration et trois `override` qui délèguent, **aucun appelant**. Chaque défi appelle directement la lambda `onInteraction` qu'on lui injecte. |
| `Option(bientot:)` + sa branche `bientot -> "$titre — bientôt"` | Aucun des trois appels d'`Option` ne passe le paramètre : la branche était inatteignable. |
| 6 imports inutilisés | `Switch`, `Alignment`, `Row`, `ObjetPhoto`, puis `MutableStateFlow`/`asStateFlow`/`ChallengeProgress` devenus inutiles après la suppression ci-dessus. |
| 2 blocs KDoc orphelins dans `PhotoChallenge` | Documentaient `doitDemanderDistant`, fonction supprimée avec la reconnaissance embarquée. Le bloc survivant référençait `[JugeEmbarque]`, classe disparue : son argument sur la rotation reste vrai et a été réécrit sans le lien mort. |
| 1 KDoc orpheline dans `RingtonePlayer` | « PLEIN = maximum du flux alarme » contredit le code depuis l'introduction du plafond utilisateur. |
| Commentaire de fin de `ChallengeSettingsScreen` | Doublon d'une note déjà portée par la KDoc de `RingtoneScreen.kt`. |
| 1 assertion tautologique (`CatalogueObjetsTest`) | `assertEquals(tous.size, tous.map { … }.size)` — `List.map` conserve la taille par construction, l'égalité est vraie quelle que soit la donnée. |
| 3 dépendances déclarées et jamais utilisées | `lifecycle-viewmodel-compose` (le ViewModel vient de `by viewModels()`), `ui-tooling-preview` (aucun `@Preview` dans le dépôt), `kotlinx-coroutines-test` (aucune occurrence dans `app/src/test`). |

**Bilan : −66 lignes, +10.** Build vert, **203 tests, 0 échec** avant comme après
— aucun test n'a été perdu, la suppression ne portait que sur une assertion vide.

Gain non cosmétique en prime : retirer `Challenge.progress` supprime au passage
les écritures `_progress.value` **pendant la composition** dans `PompesChallenge`
et `PhotoChallenge`, effet de bord que Compose interdit et que la phase 1 avait
relevé séparément comme défaut.

### Écarté — le relecteur proposait, j'ai refusé

| Élément | Pourquoi je ne l'ai pas supprimé |
|---|---|
| Test `le plancher expose vaut cinquante` | Il épingle une décision produit explicite (le plancher à 50 %), pas un détail d'implémentation. Un test qui casse quand quelqu'un abaisse ce plancher fait exactement son travail. |
| Test `le seuil d avertissement correspond a la difficulte` | Le vrai défaut n'est pas le test mais la constante dupliquée qu'il surveille (`SelectionObjets.SEUIL_AVERTISSEMENT` vs `PhotoChallenge.nombrePour`). Supprimer le test enlèverait le seul garde-fou sans traiter la cause. Repoussé en phase 4. |
| Test `le catalogue est assez grand` (`size >= 6`) | Subsumé par `size >= 50`, donc sans valeur propre — mais le supprimer ne gagne rien de mesurable non plus. Laissé. |

### Probablement mort, non supprimé — à confirmer

Rien de tout ceci n'a été touché : la preuve de non-usage existe, mais la
suppression porte un risque ou demande une décision.

- **`JugePhoto.fermer()`** et ses deux appels : la seule implémentation restante ne la surcharge pas, les deux appels exécutent un corps vide. Vestige du juge ML Kit, qui détenait des ressources natives.
- **L'interface `JugePhoto` elle-même** : une seule implémentation depuis le retrait de la reconnaissance embarquée. Elle documente encore un contrat « aucune exception ne sort d'ici » qu'on peut vouloir garder.
- **La colonne `triches` de `WakeRecord`** : jamais lue, écrite en dur à 0. La retirer exige une migration Room v3 et la mise à jour du schéma versionné — trop lourd pour un commit de nettoyage.
- **Les 8 paramètres d'injection de `PriseEnMainDetector`**, les 4 de `MouvementDetector`, les 2 d'`EstimateurGravite`, les 2 de `HandDetector`, et les valeurs par défaut de `InactivityTracker`, `AlarmState.estEncoreFraiche`, `RequeteVision.url`, `JugeGemini(timeoutMs)` : aucun n'est jamais surchargé, ni en production ni en test. Ce sont des points d'injection prévus pour la calibration sur appareil, qui n'a pas encore eu lieu. Les retirer maintenant, c'est retirer l'outil avant de s'en servir.
- **La palette de `Theme.kt`** (`Nuit`, `Crete`, `Bec`, `Craie`, `ChiffresStyle`) : `public` alors que rien ne les lit hors du fichier. Visibilité trop large, pas code mort.
- **`PriseEnMainDetector.inclinaisonDeg` et `.energie`** : publics, usages internes seulement. Probablement exposés pour une sonde de calibration.

### Fichiers orphelins

- `tools/generer_sonneries.py` — **vivant** : c'est lui qui a produit les quatre `res/raw/*.wav`. Mais sa docstring renvoie à `docs/sonneries-placeholder.md`, **qui n'existe pas**, et il dépend de `numpy` sans qu'aucun fichier de dépendances ne le déclare.
- `assets/brand/*.svg`, `design/cocorico-identite.html` — non référencés par le code, ce qui est normal : ce sont des sources de charte graphique, pas des ressources d'exécution.
- `app/schemas/…/2.json` — vivant et volontairement versionné. **`1.json` manque** (voir phase 1).

---

## Phases 3 à 6 — ce qui a été corrigé

Quatorze commits, du plus grave au plus bénin. Chaque correctif testable a eu
son test écrit **avant** la correction, avec l'échec réel capturé — jamais une
sortie prédite.

| Commit | Ce qu'il ferme |
|---|---|
| `ad62391` | Code mort prouvé inatteignable (phase 2). |
| `02e1133` | Replanification sur changement d'heure et de fuseau. `setAlarmClock` mémorise un instant absolu : un vol ou un réglage manuel de l'horloge faisait sonner au mauvais moment, ou sautait l'alarme. |
| `e6c9ea4` | Le filet de secours à 30 s passait par une API throttlée à 9 min en Doze — inopérant dans le seul scénario qui le justifie. Passé sur `setAlarmClock`, avec garde et `runCatching` : une `SecurityException` tuait le service **pendant que l'alarme sonnait**. |
| `b19e1ae` | Un DataStore corrompu, ou une heure hors bornes relue du disque, faisait disparaître l'alarme en silence. Gestionnaire de corruption + bornage à la lecture. |
| `35675c0` | Trois statistiques fausses affichées comme vraies : série périmée toujours « en cours », retard moyen sans filtre de plausibilité, ligne de moyenne plaquée au sommet du graphe. |
| `caaad3d` | La jauge annonçait « 100 % » en dur, contredisant le plafond réglé par l'utilisateur. |
| `a77796b` | La ligne de moyenne du graphe **n'était jamais dessinée** : `padding` après `height(2.dp)` peignait un fond de hauteur nulle. |
| `43fa2c8` | Quatre écrans sans défilement (dont l'onboarding, dont le seul bouton de sortie pouvait passer sous le bord), navigation perdue à la rotation, écran de victoire ressuscité indéfiniment, `setContent` appelé depuis une coroutine. |
| `01ef70d` | Documentation fausse supprimée, README et CI ajoutés. |
| `6c7105c` | La clé d'API Gemini partait dans la sauvegarde Google. |
| `bd72333` | `kotlinx.coroutines`, importée en production, n'était pas déclarée. |
| `b302948` | Le défi photo présentait toute panne du juge comme un refus : l'utilisateur photographiait en boucle un objet correct sans jamais apprendre que rien ne pouvait aboutir. Plus neuf autres défauts du même défi. |
| `d355e1a` | Sonnerie, capteurs et horloges — voir ci-dessous. |
| `8cdc81c` | Le README ne nommait pas la seule chose qui manque à un clone neuf. |

### La mesure la plus importante de l'audit

`CapteurPompes` échantillonnait l'accéléromètre à `SENSOR_DELAY_UI` (66,7 ms),
la cadence exacte que `HandDetector` documente comme repliant les vibrations du
haut-parleur dans la bande utile. Rejouée à cette cadence, une vibration de
30 Hz à 2 m/s² produit un écart de gravité de **1,98** contre un seuil de
**1,5** — là où la même vibration donne **0,21** à 20 ms.

Autrement dit : alarme à fond sur une surface dure, la garde « téléphone posé
et immobile » était franchie **en permanence**, et les pompes pouvaient cesser
d'être comptées. Le test qui validait cette garde annonçait dans sa propre KDoc
tourner à `SENSOR_DELAY_UI` alors qu'il tournait à `SENSOR_DELAY_GAME` : la
protection était vérifiée à trois fois la cadence réelle du code.

Nuance honnête : les pompes fonctionnaient lors du dernier essai sur ton
téléphone. Le repliement dépend de la surface, du volume et du haut-parleur —
ce constat dit qu'il *peut* échouer, pas qu'il échouait chez toi. Le test fige
désormais le fait mesuré au lieu d'adoucir le seuil.

---

## Rapport final

### 1. État initial contre état final

| | Avant (`190d41c`) | Après (`8cdc81c`) |
|---|---|---|
| Tests | 203, 0 échec | **301 unitaires + 4 instrumentés**, 0 échec |
| Lignes Kotlin (production) | 7 063 | 9 056 |
| Lignes Kotlin (test) | 2 641 | 3 785 |
| Avertissements du compilateur | 2 | **0** |
| Lint | jamais exécuté | 62 avertissements, **0 erreur** |
| Dépendances déclarées | 20, dont 3 inutilisées et 1 manquante | 18, toutes utilisées et déclarées |
| README / LICENCE / CI | aucun | README, CI, **Apache 2.0**, CONTRIBUTING, SECURITY, code de conduite, modèles d'issue et de PR |
| Build à froid | 39 s | 55 s (lint compris) |

Vérifié depuis un **clone neuf** dans un répertoire vide, en suivant le seul
README : tests verts, APK produit. Ce clone a d'ailleurs révélé que le README
ne nommait pas `ANDROID_HOME`, la seule chose qui manque à une machine neuve.

### 2. Ce qui n'a pas été fait, et pourquoi

**C'est la section qui compte.**

**Rien n'a été essayé sur un téléphone.** Tu as demandé qu'aucun test ne fasse
sonner l'alarme, et je m'y suis tenu. Conséquence directe : les correctifs de
`SecoursScheduler`, `RingtonePlayer`, `CapteurPompes`, `HandDetector` et de tous
les écrans Compose **compilent et ne cassent aucun test — c'est tout ce que je
peux affirmer**. Aucun n'a été observé en fonctionnement. Le dépôt n'a aucun
test instrumenté Compose ni Android.

**R8 et la signature de release.** `isMinifyEnabled = false`, aucun
`proguard-rules.pro`, aucun `signingConfig` : `assembleRelease` produit un APK
non signé. Activer la minification sans pouvoir vérifier sur appareil est le
meilleur moyen de casser Room ou Compose à l'exécution, en silence.

**La clé d'API reste en clair sur l'appareil.** Elle est désormais exclue de la
sauvegarde, mais pas chiffrée. Le chiffrer demande une migration des clés déjà
stockées : c'est un changement à part entière, pas un correctif d'audit.

**Le schéma Room `1.json` n'est toujours pas versionné**, donc `MIGRATION_1_2`
n'est jamais jouée contre un vrai SQLite. Le corriger demande d'ajouter
`room-testing` et une source `androidTest`, donc un appareil ou un émulateur.

**L'écran d'alarme ne défile toujours pas défi fermé.** Rendre le défilement
inconditionnel supprime le centrage vertical de cet état : une régression
visuelle **certaine** contre un débordement **supposé**. Seul un rendu tranche.

**La licence n'est pas choisie.** C'est ta décision, pas la mienne.

### Seconde vague — tout le reste de la phase 1

Après le premier rapport, **tous** les constats de la phase 1 ont été traités,
majeurs et mineurs compris : `AlarmService` (replanification au déclenchement,
WakeLock renouvelé, course sur la relance, ordre de `startForeground`,
suppression d'un chemin de récupération que Android bloque depuis la version 10),
`AlarmReceiver` (écriture disque hors du thread principal, verrou de démarrage),
les transitions d'heure d'été côté planification **et** côté compte à rebours,
l'instant de déclenchement enfin lu chez le service, les statistiques « ce
matin » et « jour le plus lent », le générateur de calculs, une dizaine de
défauts d'écran dont un qui permettait à une application tierce de masquer le
seul chemin vers l'alarme en cours, et l'empreinte de la distribution Gradle.

Deux pièges méritent d'être retenus, parce qu'ils auraient produit un correctif
plausible et faux :

- **`CLE_DERNIER_SIGNE` ne pouvait pas servir d'instant de déclenchement.**
  C'est un signe de vie réécrit à chaque passage du filet de secours, donc
  toutes les 30 secondes : tous les réveils auraient été enregistrés à une
  demi-minute. Une clé distincte, posée une seule fois, a été ajoutée.
- **`referrer` ne pouvait pas valider l'appelant de l'écran de victoire.** Il
  est figé au lancement de l'activité, donc faux dans le cas courant où
  `MainActivity` est déjà vivante et reçoit `onNewIntent` — la validation aurait
  cassé le vrai chemin de victoire. C'est un `PendingIntent` qui sert de preuve.

Un test qui passait du premier coup a été traité comme suspect : celui du
générateur de calculs a été confronté à un générateur volontairement muté pour
vérifier qu'il échouait bien, puis remis en état. Un test vert qui n'a jamais
été rouge ne prouve rien.

### Troisième vague — sur appareil

Un Pixel 9a sous Android 17 a permis de fermer ce qui restait bloqué faute de
matériel. **Aucune alarme n'a sonné.**

**La migration de base est enfin jouée contre un vrai SQLite.** Le schéma `1.json`
manquait : il a été **régénéré par Room** depuis un worktree temporaire au commit
où la base était encore en version 1, et non écrit à la main — un schéma inventé
n'aurait rien prouvé. Quatre tests instrumentés couvrent 1→2, 2→3 et la chaîne
complète 1→3, sur base peuplée. Preuve qu'ils mordent : en changeant
`DEFAULT 0` en `DEFAULT 1`, les deux échouent sur l'appareil avec
`Migration didn't properly handle: wake_records`.

**La colonne morte `triches` est supprimée**, ce qui n'était pas envisageable
tant qu'aucune migration n'était vérifiable.

**R8 et la signature sont activés — et une catastrophe a été évitée de peu.**
`ChallengeId` et `Difficulty` sont écrits en toutes lettres sur le disque et
relus par `valueOf`. Sans règle de conservation, R8 les renomme :

```
ChallengeId.MATHS -> MATHS      (règle présente)
ChallengeId.MATHS -> H          (règle retirée)
```

À la première mise à jour minifiée, le défi choisi et **tout l'historique** de
chaque utilisateur seraient devenus illisibles, en silence, avec retour aux
valeurs par défaut. Les règles ont été retirées puis le build relancé pour
observer le renommage, avant restauration. L'APK passe de 30,2 Mo à 4,9 Mo,
signé en schéma v2, non debuggable, installé et lancé sans plantage.

**Zéro avertissement de compilation**, pour la première fois.

**Spotless et ktlint sont branchés.** L'audit avait refusé de le faire sans
mesurer ; mesuré, le reformatage vaut 51 fichiers et ~320 lignes, presque
uniquement de l'ordre d'imports. Fait, en un commit isolé qui ne cache aucun
correctif, et `spotlessCheck` passe en premier dans l'intégration continue.

### Chiffrement de la clé d'API

Dernier point de la liste, fermé et vérifié sur appareil. La clé est chiffrée
en AES/GCM par une clé de l'`AndroidKeyStore`, non extractible et absente des
sauvegardes. Aucune dépendance ajoutée : `javax.crypto` suffit.

Vérifié sur le disque du téléphone, pas seulement en test : après saisie d'une
clé factice, celle-ci apparaît **zéro fois** dans le fichier DataStore, qui
contient à la place `cocorico-cle:v1:<vecteur>:<chiffré>`. L'application la
relit correctement, et l'effacement laisse le champ vide.

Aucune fonction du coffre ne lève : une clé illisible — Keystore réinitialisé
après restauration — rend « pas de clé », ce que l'application traite déjà comme
« défi photo non proposé ». Une exception à cet endroit ferait disparaître
l'alarme, ce qui serait bien pire que redemander une clé.

Migration : une clé héritée en clair reste lisible et est rechiffrée au premier
démarrage suivant.

### Ce qui reste ouvert

- **L'écran d'alarme ne défile pas tant que le défi est fermé.** Trancher demande de l'afficher à taille de police maximale, donc de forcer le volume du flux d'alarme sur le téléphone de l'utilisateur : non fait sans son accord.
- **Aucun seuil de capteur n'a été mesuré sur un vrai geste.** Même raison : la mesure exige l'écran d'alarme.
- **La recette d'appareil complète** (`docs/recette-appareil.md`) n'a pas été jouée : elle fait sonner.

### 3. Risques restants, par priorité

1. **Presque aucun seuil de capteur n'a été mesuré sur un vrai geste.** L'audit en a mesuré un, par simulation : le repliement des vibrations à 66,7 ms (voir plus haut). Les autres restent des valeurs de simulation. Ceux de
   `CompteurPompes`, `PriseEnMainDetector`, `MouvementDetector` et
   `EstimateurGravite` viennent tous de simulations. `MouvementDetector` est
   documenté comme produisant une énergie nulle sur une prise en main molle : le
   réarmement du compte à rebours par le mouvement n'a peut-être **jamais**
   fonctionné sur appareil.
2. **La migration de base de données n'est couverte que par comparaison de
   chaînes.** Une migration cohérente en syntaxe mais fautive à l'exécution
   partirait en production et planterait au démarrage après mise à jour.
3. **Le juge distant n'a jamais répondu.** Aucun test n'a confronté
   `RequeteVision` à l'API réelle ; les tests reproduisent la forme documentée
   des réponses, pas des réponses observées.
4. **Deux triches connues et acceptées** : la paume au-dessus du capteur de
   proximité pour les pompes, la photo d'un écran pour le défi photo.
5. **Pas de direct boot** : un téléphone qui redémarre la nuit et reste
   verrouillé ne reprogramme pas l'alarme avant le premier déverrouillage.

### 4. Ce que tu dois vérifier toi-même

Rien de ceci n'est vérifiable sans toi.

- **Les pompes comptent toujours**, maintenant que la cadence de
  l'accéléromètre a changé. C'est le correctif au plus fort effet de bord.
- **La sonnerie part toujours à plein volume**, et le volume système d'origine
  est bien restauré après un arrêt forcé — `RingtonePlayer` a été le fichier le
  plus remanié de l'audit.
- **Le filet de secours relance bien le service** après un arrêt forcé de
  l'application, maintenant qu'il passe par `setAlarmClock`.
- **La ligne de moyenne apparaît enfin** sur le graphe des statistiques.
- **La jauge affiche le bon pourcentage** quand tu baisses le plafond.
- **Le défi photo dit « le juge ne répond pas »** en mode avion, au lieu de
  « pas encore reconnu ».
- **La reconnaissance Gemini sur une tasse**, via *Réglages → Défi → Photo →
  Essayer la reconnaissance* — toujours en attente depuis avant l'audit.

### 5. Ce que cet audit ne prouve pas

Il a exécuté 301 tests unitaires, 4 tests instrumentés sur un vrai téléphone, un
lint, un formateur et un clone neuf. Il a installé et lancé la version
minifiée. **Il n'a jamais fait sonner l'alarme.**

Tout ce qui ne se constate qu'une sirène en marche reste donc non vérifié : la
chaîne de déclenchement complète, la baisse de volume à la prise en main, le
comptage des pompes, la reconnaissance photo, le filet de secours. La couverture
n'est toujours pas mesurée, et la surface non testée reste le câblage Android et
Compose — exactement là où tous les défauts constatés sur téléphone sont apparus
jusqu'ici.
