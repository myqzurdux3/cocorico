# Cocorico — état du projet

Réveil Android à alarme unique, sans snooze. La sonnerie part à plein volume sur
`STREAM_ALARM` derrière un écran plein qui passe par-dessus le verrouillage, et
ne s'arrête qu'une fois le défi résolu — calcul mental, dix pompes comptées au
capteur de proximité, ou la photo d'un objet tiré au sort, jugée par l'API
Gemini. Prendre le téléphone en main baisse le volume ; dix secondes sans rien
faire le remontent. Le plafond sonore est réglable, jamais sous 50 % du maximum
de l'appareil.

**Documents de référence**
- Spec V1 : `superpowers/specs/2026-08-16-cocorico-design.md`
- Spec pompes : `superpowers/specs/2026-08-17-pompes-design.md`
- Spec photo : `superpowers/specs/2026-08-17-photo-design.md`
- Recette sur appareil : `recette-appareil.md`
- Audit complet du dépôt : `../AUDIT.md`

Le plan d'implémentation V1 a été retiré de l'arbre de travail : il est
entièrement exécuté et ses blocs de code ne correspondent plus au code livré,
les vagues de correction ayant modifié le service et le lecteur audio sans que
le plan suive. Un plan périmé induit en erreur. Il reste dans l'historique git,
au commit `540db8e`, avec le journal d'exécution complet et ses 26 décisions.

---

## Architecture

Trois briques indépendantes, plus la persistance et l'interface.

| Paquet | Responsabilité |
|---|---|
| `alarm/` | Planification (`setAlarmClock`), déclenchement, service de premier plan, filet de secours, redémarrage |
| `ring/` | Lecture de la sonnerie, volume système, détection de prise en main, lecture des capteurs de pompes |
| `challenge/` | Défis derrière l'interface `Challenge` — le service ne connaît que `isSolved` |
| `challenge/pompes/` | Comptage des pompes : machine à états pure et règles anti-triche |
| `challenge/photo/` | Défi photo : catalogue d'objets par pièce, tirage, capture CameraX, juge distant |
| `data/` | Configuration unique (DataStore), historique des réveils (Room) |
| `ui/` | Écrans Compose, onboarding des permissions |

Toute logique décidable vit dans une classe sans import `android.*` et est
couverte par des tests unitaires. Les composants Android ne font que du câblage.
C'est ce découpage qui permet de tout tester sans téléphone.

---

## Décisions qui lient encore

- **`setAlarmClock` et rien d'autre.** Seule API exemptée du Doze mode.
  Ne jamais la remplacer par `setExactAndAllowWhileIdle`.
- **`AlarmState` s'écrit en `commit()`**, pas `apply()` : la valeur doit être sur
  le disque avant que le processus puisse mourir.
- **La replanification se fait avant l'arrêt**, dans un bloc non annulable.
  Lancer puis s'arrêter la perdait une fois sur deux — l'alarme ne sonnait plus
  jamais après le premier réveil.
- **Le volume d'origine est persisté sur disque.** `STREAM_ALARM` n'est pas
  réinitialisé à la mort du processus ; sans cette trace, un arrêt forcé laissait
  le téléphone à fond définitivement.
- **L'arrêt du service ne dépend jamais d'une écriture en base.** Un hoquet de
  Room laissait la sirène hurler alors que le défi était résolu.
- **Le filet de secours porte une action dédiée** et le récepteur l'ignore si
  aucune alarme n'est en cours, sinon un secours déjà parti ressuscitait une
  alarme résolue.
- **Pas de Device Admin.** Le blocage de désinstallation n'est qu'une friction,
  et c'est un motif classique de refus sur le Play Store.
- **Commentaires et KDoc en français.** Rien sous 15 sp sur les écrans d'alarme.
  Chiffres en monospace.
- **Un seul calcul d'inclinaison**, dans `PriseEnMainDetector`, appelé aussi par
  `CapteurPompes`. Deux estimations du même angle réglées différemment seraient
  une source de bugs silencieux.
- **Deux canaux de filtrage, jamais un seul.** Lent (350 ms) pour l'orientation,
  rapide (100 ms) pour le mouvement. Un canal unique lent absorbe les
  oscillations : la garde « téléphone immobile » devient inopérante et une
  agitation à 2 Hz passe sous le seuil.
- **Une seule horloge pour tous les capteurs** (`SystemClock.elapsedRealtime`).
  Mélanger les horodatages matériels de deux capteurs dans une même soustraction
  fait tomber toutes les répétitions hors bornes sur les téléphones dont les
  bases diffèrent — plus rien n'est jamais compté.
- **Le schéma Room est exporté et versionné**, et un test compare le SQL de
  migration à ce schéma colonne par colonne. Room n'infère jamais un défaut SQL
  depuis la valeur par défaut Kotlin ; l'écart fait planter l'application au
  démarrage après mise à jour.
- **Le défi se replie sur le calcul mental** quand les capteurs manquent, avant
  tout affichage. Sans ce repli, l'alarme serait impossible à arrêter.

---

## Vérifié sur appareil réel

Pixel 9a, Android 17, le 16 août 2026 :

- Planification exacte confirmée par le système — `Alarm clock` et surtout
  `Next wake from idle: com.cocorico`, donc sortie du Doze mode garantie.
- Déclenchement à la seconde, écran allumé tout seul, activité par-dessus le
  verrouillage.
- Volume forcé de 5 à 7 sur 7.
- Extinction du téléphone pendant la sonnerie : l'alarme repart au redémarrage.
- Baisse du volume pendant la résolution.

---

## Écarts connus

**Corrigés depuis, à revérifier sur appareil**
- Le contenu passait sous les barres système (`targetSdk 35` impose le
  bord-à-bord) : titre et bouton retour chevauchaient l'horloge.
- La détection de prise en main ne se déclenchait jamais : elle exigeait un
  dépassement de seuil continu pendant deux secondes, ce qu'un téléphone
  simplement tenu ne produit pas.

**Ouverts — défi pompes**
- **Aucun seuil n'a été mesuré sur un vrai geste.** Ceux de `CompteurPompes`,
  d'`EstimateurGravite` et de `PriseEnMainDetector` viennent tous de
  simulations. C'est le premier objet de la recette sur appareil.
- **Le capteur de proximité ne distingue pas un torse d'une paume.** Tenir la
  main au-dessus du capteur au bon rythme valide le défi en une dizaine de
  secondes, téléphone posé. **Aggravé volontairement** : la tenue basse exigée
  est passée de 600 ms à 100 ms après essai sur appareil, parce que 600 ms
  obligeaient à marquer une pause en bas — ce qui n'est pas une pompe. Un vrai
  geste compte enfin ; un balayage de main aussi, plus facilement. Limite acceptée pour l'instant ; la piste retenue
  est d'exiger un choc au sol pendant la phase basse, à calibrer après la
  recette. Détail dans `recette-appareil.md`.
- **Résolu.** La migration est jouée sur base peuplée par `MigrationInstrumenteeTest`
  (`room-testing`, `./gradlew connectedDebugAndroidTest`), qui couvre 1→2, 2→3 et
  la chaîne complète.

**Relevés par l'audit du 17 août 2026, non corrigés** — le détail, la sévérité
et le niveau de confiance de chacun sont dans `../AUDIT.md`, qui fait foi.
- L'écran d'alarme ne défile pas tant que le défi est fermé : à taille de police
  maximale, « Faire taire ce coq » pourrait être rogné. Rendre le défilement
  inconditionnel supprimerait le centrage vertical — arbitrage qui demande un
  rendu sur appareil.

**Ouverts**
- `VolumeStateMachine` a deux paliers là où la spec en décrit trois avec rampe
  progressive. La jauge rend l'écart visible.
- La jauge annonce un pourcentage nominal du plafond choisi, alors que
  l'application arrondit au cran du flux d'alarme, qui est quantifié.
- « Aucun jour actif. Le coq dort. » s'affiche aussi quand des jours sont cochés
  mais l'alarme désarmée. Trompeur.
- Le sélecteur d'heure est en thème système, sans rapport avec la charte.
- Contraste 4,1:1 sur l'écran rouge — sous WCAG AA, imposé par la charte.
- Les triches ne sont pas comptées ; la statistique a été renommée en
  conséquence.
- Pas de direct boot : un téléphone qui redémarre la nuit et reste verrouillé ne
  reprogramme pas l'alarme avant le premier déverrouillage.

---

## Sonneries

Les quatre sonneries sont des **remplacements synthétisés**, générés par
`tools/generer_sonneries.py` en WAV PCM mono 22 050 Hz, sans droits attachés.
La machine de développement n'avait ni encodeur audio ni accès réseau.

À remplacer par de vrais enregistrements avant toute publication. Le script
reste versionné pour pouvoir les régénérer ou en ajuster le caractère.
