<p align="center">
  <img src="assets/brand/cocorico-mark.svg" alt="Cocorico" width="128" height="128">
</p>

<h1 align="center">Cocorico</h1>

<p align="center">
  <strong>Le réveil qu'on ne peut pas faire taire en dormant.</strong>
</p>

<p align="center">
  <a href="https://github.com/myqzurdux3/cocorico/actions/workflows/ci.yml"><img src="https://github.com/myqzurdux3/cocorico/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 9.0+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.1">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-MIT-750014" alt="Licence MIT"></a>
</p>

---

Réveil Android à **alarme unique**, sans snooze, qui ne s'arrête qu'une fois un
défi accompli. Prendre le téléphone en main baisse le volume sans jamais le
couper ; dix secondes d'inactivité le font remonter — le marché est affiché en
clair à l'écran du début à la fin.

## Les défis

| | Défi | Ce qu'il faut vraiment faire pour le passer |
|---|---|---|
| 🔢 | **Calcul mental** | Résoudre des opérations, sur trois niveaux de difficulté. Toujours disponible : c'est le repli de tous les autres. |
| 💪 | **Pompes** | Dix pompes comptées au capteur de proximité, avec une garde qui refuse de compter si le téléphone bouge. |
| 📷 | **Photo** | Photographier un objet tiré au sort dans ta maison, pièce par pièce. Un modèle de vision juge l'image. |
| 🧩 | **Sur mesure** | Enchaîner les trois : combien de chaque, dans l'ordre que tu poses. |

Un défi n'est jamais un cul-de-sac : sans capteur, sans caméra, sans réseau ou
sans clé d'API, l'application se replie sur le calcul mental **avant** d'afficher
quoi que ce soit. Rester bloqué devant une sirène serait pire que tout.

## Ce qui rend l'alarme fiable

- **`setAlarmClock`, et rien d'autre** — la seule API Android exemptée du Doze mode.
- **Replanification au démarrage, à la mise à jour, au changement d'heure et de fuseau.** Chacun de ces quatre événements efface ou périme une alarme programmée.
- **Filet de secours toutes les 30 secondes** tant que le défi n'est pas résolu : tuer l'application ne suffit pas.
- **Le volume système d'origine est écrit sur disque**, pas gardé en mémoire : même un arrêt forcé pendant la sonnerie le restaure.
- **Plafond sonore réglable**, jamais sous 50 % du maximum de l'appareil — le maximum d'un téléphone peut faire mal aux oreilles, mais un réveil doit réveiller.

## Construire

Il faut un **JDK 17** et le **SDK Android 36**. Le wrapper Gradle est versionné,
rien d'autre à installer.

Gradle doit savoir où est le SDK : soit la variable `ANDROID_HOME`, soit un
fichier `local.properties` à la racine contenant `sdk.dir=/chemin/vers/Sdk`. Ce
fichier n'est pas versionné, il contient un chemin propre à ta machine.

```bash
./gradlew testDebugUnitTest         # tests unitaires
./gradlew connectedDebugAndroidTest # migrations de base, appareil branché
./gradlew spotlessApply             # formatage
./gradlew assembleDebug             # APK de débogage
./gradlew installDebug              # installer sur un appareil branché
```

Aucune de ces commandes ne fait sonner l'alarme.

L'APK sort dans `app/build/outputs/apk/debug/`.

## Configurer

Tout se règle depuis l'application. Un seul réglage demande une donnée
extérieure : le **défi photo**, qui a besoin d'une clé d'API Google Gemini,
saisie dans *Réglages → Défi → Photo*.

La clé reste sur l'appareil, n'est jamais livrée avec l'application, et est
exclue de la sauvegarde Android. Sans elle, le défi photo n'est simplement pas
proposé. Un banc d'essai — *Essayer la reconnaissance* — permet de vérifier que
tout fonctionne **sans déclencher l'alarme**.

## Contribuer

Une règle porte tout le reste : **toute logique décidable vit dans une classe
sans import `android.*`, couverte par des tests unitaires**, et les composants
Android ne font que du câblage. C'est ce qui permet de tout tester sans
téléphone.

Le détail est dans [CONTRIBUTING.md](CONTRIBUTING.md). Le projet suit un
[code de conduite](CODE_OF_CONDUCT.md), et les failles se signalent en privé —
voir [SECURITY.md](SECURITY.md).

## Aller plus loin

| Document | Contenu |
|---|---|
| [`docs/cocorico.md`](docs/cocorico.md) | État du produit, décisions qui lient encore, écarts connus |
| [`docs/recette-appareil.md`](docs/recette-appareil.md) | Recette manuelle sur téléphone |
| [`docs/reprise.md`](docs/reprise.md) | Note de passation entre sessions de travail |
| [`docs/sonneries.md`](docs/sonneries.md) | Provenance des sonneries embarquées, et la licence de `coq.wav` |
| [`AUDIT.md`](AUDIT.md) | Audit du dépôt : défauts relevés, correctifs, dette restante |

## Licence

[MIT](LICENSE). Réutilisation, modification et redistribution libres, y compris
commerciales, tant que l'avis de copyright est conservé. C'est la licence
permissive la plus courte qui existe : elle tient en un paragraphe, et n'impose
rien d'autre.

Trois des quatre sonneries de `app/src/main/res/raw/` sont **synthétisées** par
`tools/generer_sonneries.py` : aucun échantillon tiers, aucun droit attaché.

**La quatrième, `coq.wav`, n'est pas couverte par cette licence.** C'est un
enregistrement tiers dont la licence d'origine n'est pas documentée. Si tu
redistribues ce dépôt, publies un paquet ou en fais un usage commercial,
remplace-le d'abord — [`docs/sonneries.md`](docs/sonneries.md) dit par quoi et
comment. Si tu en es l'ayant droit, dis-le : il sera retiré.
