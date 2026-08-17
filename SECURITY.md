# Sécurité

## Signaler une faille

Ouvre un [avis de sécurité privé](https://github.com/myqzurdux3/wake-up/security/advisories/new)
plutôt qu'une issue publique. Ce projet est maintenu sur du temps libre : il n'y
a aucun engagement de délai, et le dire franchement vaut mieux qu'afficher une
promesse qui ne sera pas tenue.

Seule la branche par défaut reçoit des correctifs.

## Ce qui est sensible ici

Une application de réveil n'a pas de serveur, pas de compte, pas de base
d'utilisateurs. Trois choses méritent quand même de l'attention.

**La clé d'API Gemini.** Elle est fournie par l'utilisateur, stockée sur
l'appareil, et exclue de la sauvegarde Android
(`app/src/main/res/xml/regles_extraction.xml`). Elle est masquée avant tout
affichage — voir `JugeGemini.masquer`, couvert par des tests — et **chiffrée sur
le disque** en AES/GCM par une clé de l'`AndroidKeyStore`, qui n'est pas
extractible et ne part dans aucune sauvegarde. Une extraction du répertoire de
données ne rend donc que l'enveloppe chiffrée.

Si tu signales quoi que ce soit : **ne colle jamais ta propre clé** dans une
issue, un log ou une capture d'écran.

**Les photos du défi.** Elles ne touchent jamais le disque. Elles sont décodées
en mémoire, réduites, et envoyées à l'API Gemini uniquement pendant le défi.
Aucune autre donnée ne quitte l'appareil.

**Les composants exportés.** `MainActivity` et `BootReceiver` le sont — c'est
nécessaire, l'un est le lanceur, l'autre écoute des diffusions système. Tous
deux filtrent ce qu'ils acceptent. `AlarmActivity` et `AlarmService` ne sont pas
exportés : rien d'extérieur ne doit pouvoir arrêter une alarme en cours.

## Hors périmètre

Contourner le réveil **sur son propre téléphone** n'est pas une faille de
sécurité. Éteindre l'appareil, désinstaller l'application, ou tenir la main
au-dessus du capteur de proximité sont des limites assumées, décrites dans
[`docs/cocorico.md`](docs/cocorico.md). Ce sont des sujets d'issue ordinaire.
