# Défi pompes — spécification

**Date :** 2026-08-17
**Statut :** validée en brainstorming, en attente de relecture
**Dépend de :** `2026-08-16-cocorico-design.md` (spec V1)

---

## 1. Objectif

Deuxième défi de désarmement : dix pompes comptées par les capteurs. Il partage
l'interface `Challenge` avec le défi maths et devient sélectionnable dans les
réglages.

Cette tâche rend l'abstraction `Challenge` réelle. Aujourd'hui `AlarmActivity`
instancie `MathChallenge` en dur et ne lit jamais `config.challengeId` — la
revue finale de la V1 l'avait relevé. Brancher le second défi débloque du même
coup le défi photo.

---

## 2. Décisions arrêtées

| Sujet | Décision | Raison |
|---|---|---|
| Détection | Capteur de proximité, téléphone posé au sol écran vers le haut | Fonctionne dans le noir, aucun calibrage, aucune caméra |
| Anti-triche | Trois règles : à plat, immobile, durée plausible | Chacune est un prédicat pur, testable séparément |
| Régularité de cadence | **Non retenue** | Rejetterait un athlète régulier ; gain faible une fois les trois autres règles en place |
| Nombre | 5 / 10 / 20 selon `Difficulty` | Le réglage de difficulté existant pilote les deux défis |
| Issue de secours | Bouton « Je ne peux pas » immédiat, sans délai | Choix utilisateur explicite ; un capteur masqué ne doit jamais piéger |
| Coût du renoncement | Enregistré et affiché après coup | Le bouton reste accessible, il laisse une trace |
| Volume | Une pompe comptée réarme le compte à rebours d'inactivité | Même règle que les frappes du pavé numérique |
| Architecture | Machine à états pure, alimentée par un flux d'échantillons | Aligné sur `VolumeStateMachine` et `InactivityTracker`, testable sans appareil |

---

## 3. Architecture

```
challenge/pompes/
├── EchantillonPompe.kt     modèle d'échantillon (pur)
├── CompteurPompes.kt       machine à états + règles anti-triche (pur)
└── PompesChallenge.kt      implémentation Challenge + UI Compose
ring/
└── CapteurProximite.kt     coquille SensorEventListener
```

`CompteurPompes` n'a aucun import `android.*`. Il reçoit :

```kotlin
data class EchantillonPompe(
    val procheDuCapteur: Boolean,
    val inclinaisonDegres: Float,
    val ecartGravite: Float,
    val tMillis: Long,
)
```

et expose `etat`, `comptees`, `total`, plus `fun onEchantillon(e: EchantillonPompe)`.

Le booléen `procheDuCapteur` est délibéré : beaucoup de capteurs de proximité ne
rapportent que près/loin, pas une distance. L'algorithme ne s'appuie que sur le
franchissement, jamais sur une valeur absolue.

`inclinaisonDegres` et `ecartGravite` sont calculés par la même estimation de
gravité que `HandDetector` — filtre passe-bas sur l'accéléromètre. Les deux
défis partagent ce calcul plutôt que de le dupliquer.

---

## 4. Comptage

Quatre états : `ATTENTE_POSITION`, `PRET`, `BAS`, `REMONTEE`.

Une pompe est comptée sur la transition `BAS → PRET`, donc **à la remontée** —
pas à la descente. Un utilisateur qui descend et abandonne ne marque rien.

```
ATTENTE_POSITION ──(à plat & immobile & loin)──> PRET
PRET ──(proche)──> BAS
BAS ──(loin, après ≥150 ms en bas)──> PRET, comptees++
tout état ──(plus à plat OU plus immobile)──> ATTENTE_POSITION
```

### Règles anti-triche

| Règle | Seuil initial | Ce qu'elle bloque |
|---|---|---|
| À plat | inclinaison < 15° | Téléphone tenu en main |
| Immobile | écart à la gravité < 1,5 m/s² | Téléphone agité pendant qu'on passe la main |
| Durée de répétition | entre 0,6 s et 8 s | Main qui passe vite ; immobilité déguisée |
| Temps en position basse | ≥ 150 ms | Effleurement du capteur |

Une répétition hors bornes n'est pas comptée et ne pénalise rien — elle est
simplement ignorée, comme une mauvaise réponse aux calculs.

**Les seuils sont provisoires.** Ils demandent une session de calibration sur
appareil réel, documentée dans la recette.

---

## 5. Écran

- Compteur géant `3 / 10`, monospace, comme l'heure.
- Ligne d'état indiquant quoi faire : « Pose le téléphone au sol, écran vers le
  haut », « Prêt », « Descends », « Remonte ».
- Jauge de volume, identique au défi maths.
- Bouton « Je ne peux pas ».

Micro-copie normative : `Pose le téléphone au sol, écran vers le haut`,
`Je ne peux pas`, `Encore une`, `Debout.`

Contraintes héritées : rien sous 15 sp, chiffres en monospace, commentaires en
français.

---

## 6. Écran allumé

Défaut de la V1 révélé par cette conception : aucun `FLAG_KEEP_SCREEN_ON` sur
`AlarmActivity`. Aux calculs l'utilisateur tape toutes les deux secondes et
l'écran ne s'éteint jamais ; en pompes personne ne touche l'écran pendant une
minute et il s'éteindrait.

Correction dans cette tâche, pour les deux défis.

---

## 7. Persistance

`WakeRecord` gagne une colonne `defi: String` et une colonne `abandon: Boolean`.
Migration Room en version 2.

L'écran de victoire affiche le défi accompli, et le renoncement quand il a eu
lieu.

---

## 8. Tests

Le compteur pur se teste avec des séquences d'échantillons synthétiques :

- une série correcte de dix répétitions compte dix ;
- un téléphone à plat et immobile, sans franchissement, ne compte rien ;
- des franchissements plus rapides que 0,6 s ne comptent rien ;
- un téléphone incliné au-delà de 15° ne compte rien, même avec franchissements ;
- un téléphone agité ne compte rien ;
- une descente sans remontée ne compte rien ;
- une pause de trente secondes en milieu de série ne perd pas les répétitions
  déjà acquises.

Le reste — capteur réel, position au sol, écran maintenu allumé — passe par la
recette sur appareil.

---

## 9. Hors périmètre

- Le défi photo validé par IA, qui suit la même interface.
- Le comptage des triches proprement dit (volume, retour), déjà reporté en V1.
- Toute forme de calibration personnalisée.
