# Cocorico

Réveil Android à alarme unique, sans snooze, qui ne s'arrête qu'une fois un
défi accompli — calcul mental, dix pompes comptées au capteur, ou la photo d'un
objet tiré au sort. Prendre le téléphone en main baisse le volume, sans jamais
le couper ; dix secondes d'inactivité le font remonter.

## Construire

Il faut un JDK 17 et le SDK Android 35. Le wrapper Gradle est versionné, rien
d'autre à installer.

```bash
./gradlew testDebugUnitTest    # tests unitaires
./gradlew assembleDebug        # APK de débogage
./gradlew installDebug         # installer sur un appareil branché
```

L'APK sort dans `app/build/outputs/apk/debug/`.

## Utiliser

Au premier lancement, l'application demande les autorisations dont dépend la
fiabilité de l'alarme (alarmes exactes, notifications, plein écran). Régler
l'heure, les jours, la sonnerie et le défi, puis armer.

## Configuration

Tout se règle depuis l'application. Un seul réglage demande une donnée
extérieure : le **défi photo**, qui a besoin d'une clé d'API Google Gemini,
saisie dans *Réglages → Défi → Photo*.

La clé reste sur l'appareil et n'est jamais livrée avec l'application. Sans
elle, le défi photo n'est pas proposé et les autres défis fonctionnent
normalement. Un banc d'essai (*Essayer la reconnaissance*) permet de vérifier la
reconnaissance **sans déclencher l'alarme**.

## Contribuer

Une règle porte tout le reste : **toute logique décidable vit dans une classe
sans import `android.*`, couverte par des tests unitaires**, et les composants
Android ne font que du câblage. C'est ce qui permet de tout tester sans
téléphone.

Commentaires et KDoc en français, messages de commit en anglais. Les tests
passent avant la relecture : `./gradlew testDebugUnitTest`.

## Documentation

- `docs/cocorico.md` — état du produit, décisions qui lient encore, écarts connus
- `docs/reprise.md` — note de passation entre sessions de travail
- `docs/recette-appareil.md` — recette manuelle sur téléphone
- `AUDIT.md` — audit du dépôt, défauts relevés et dette restante

## Licence

Pas encore choisie.
