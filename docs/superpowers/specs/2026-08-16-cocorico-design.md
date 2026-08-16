# Cocorico — Spécification de conception

**Date :** 2026-08-16
**Statut :** validée en brainstorming, en attente de relecture
**Plateforme :** Android uniquement (minSdk 28, targetSdk 35)

`minSdk 28` (Android 9) évite les chemins de compatibilité hérités pour `setShowWhenLocked` / `setTurnScreenOn`, disponibles depuis l'API 27, et couvre la quasi-totalité du parc actif.

---

## 1. Le produit

Cocorico est un réveil Android à alarme unique. Il n'existe qu'une alarme, elle n'a pas de snooze, et elle ne se tait que lorsque l'utilisateur a résolu un défi qui prouve que son cerveau a redémarré.

Le pari produit : les réveils classiques échouent parce qu'ils rendent l'arrêt trop facile, et les réveils à défi (Alarmy, Sleep as Android) échouent parce qu'ils noient le défi sous des dizaines de réglages, de listes d'alarmes et d'options. Cocorico fait une seule chose et la rend impossible à contourner.

**Positionnement de marque :** complice avec humour. L'app se moque de la situation, jamais de l'utilisateur. Elle tutoie, elle est brève, elle ne culpabilise pas.

**Identité :** un coq (`assets/brand/cocorico-mark.svg`, `assets/brand/cocorico-icon.svg`). Palette nuit `#101638`, crête `#E3372B`, bec `#F7B32B`, craie `#FAF7F2`. Direction visuelle complète et maquettes des cinq écrans : `design/cocorico-identite.html`.

---

## 2. Décisions arrêtées

| Sujet | Décision | Raison |
|---|---|---|
| Stack | Kotlin natif + Jetpack Compose | L'alarme fiable exige `AlarmManager`, foreground service, `BOOT_COMPLETED`, `AudioManager`, capteurs. Aucun framework cross-platform n'évite ce code natif. |
| Modèle d'alarme | Une seule alarme, heure libre, jours de la semaine | C'est le produit. Pas de liste, pas de profils. |
| Baisse de volume | Conditionnelle, avec remontée automatique | Le volume descend à la prise en main, remonte après 10 s d'inactivité sur le défi. Récompense le réveil, punit le rendormissement. |
| Anti-triche | Fort (pas « maximum ») | Boutons volume, kill d'app et redémarrage bloqués. Pas de Device Admin : Google Play refuse souvent ces permissions. |
| Défis en V1 | Maths seul, derrière une interface `Challenge` | Un défi fini et poli plutôt que trois à moitié faits. Pompes et photo se branchent ensuite sans refonte. |
| Sonneries | Quatre pistes embarquées graduées + import d'un fichier perso | L'utilisateur garde la main sur ce qu'il subit ; pas de sonnerie imposée. |
| Historique | Room dès la V1 | La série de réveils est le mécanisme de rétention. |
| Compte utilisateur | Aucun | Tout reste sur l'appareil. |

**Hors périmètre définitif :** snooze, alarmes multiples, compte obligatoire, publicité pendant l'alarme.

---

## 3. Architecture

Module applicatif unique, découpé en trois briques qui ne se connaissent pas. C'est la contrainte structurante de tout le projet.

```
app/src/main/java/com/cocorico/
├── alarm/        planification et déclenchement
├── ring/         pilotage du son et détection de prise en main
├── challenge/    défis, derrière une interface commune
├── data/         AlarmConfig (DataStore) + historique (Room)
└── ui/           écrans Compose
```

### 3.1 `alarm/` — planification et déclenchement

| Composant | Rôle |
|---|---|
| `AlarmScheduler` | Calcule la prochaine occurrence à partir de `AlarmConfig` et la programme via `AlarmManager.setAlarmClock()`. Seule API exemptée du Doze mode. |
| `AlarmReceiver` | `BroadcastReceiver` déclenché à l'heure exacte. Démarre `AlarmService`. Ne fait rien d'autre. |
| `BootReceiver` | Écoute `BOOT_COMPLETED`. Replanifie l'alarme et, si une alarme était en cours au moment de l'extinction, la relance immédiatement. |
| `AlarmService` | Foreground service de type `mediaPlayback`. Tient le `WakeLock`, la sonnerie, et le cycle de vie complet de l'alarme. |

### 3.2 `ring/` — son et détection

| Composant | Rôle |
|---|---|
| `RingtonePlayer` | Lecture en boucle sur `STREAM_ALARM`. Force le volume système au maximum au déclenchement, restaure la valeur d'origine à la fin. |
| `VolumeStateMachine` | Trois états : `FULL`, `LOWERED` (30 %), `RAMPING_UP`. Pure logique, sans dépendance Android. Testable unitairement. |
| `HandDetector` | Accéléromètre via `SensorManager` plus état de l'écran. Émet « en main » après 2 s de mouvement franc écran allumé. |
| `InactivityTimer` | 10 s. Réarmé à chaque interaction avec le défi. À expiration, fait passer la machine à états en `RAMPING_UP`. |

### 3.3 `challenge/` — défis

```kotlin
interface Challenge {
    val id: ChallengeId
    val progress: StateFlow<ChallengeProgress>   // étape courante / total
    val isSolved: StateFlow<Boolean>
    fun onUserInteraction()                       // réarme l'InactivityTimer
    @Composable fun Content(modifier: Modifier)
}
```

`MathChallenge` est la seule implémentation en V1. Trois difficultés :

| Difficulté | Opérations |
|---|---|
| Facile | Additions et soustractions à deux chiffres |
| Moyen | Multiplications à un chiffre par deux chiffres (`17 × 8`) |
| Difficile | Multiplications à deux chiffres, ou opérations en chaîne |

Trois problèmes à résoudre par réveil. Une mauvaise réponse ne pénalise pas le volume — elle affiche « Non. Et le coq a entendu. » et régénère un problème équivalent.

`AlarmService` ne connaît que `challenge.isSolved`. Le défi ne connaît pas le volume. `ring/` ne connaît pas les maths.

### 3.4 `data/`

- **`AlarmConfig`** — DataStore, une seule entité : heure, jours actifs, identifiant de sonnerie, type de défi, difficulté.
- **`WakeRecord`** — Room : horodatage de l'alarme, horodatage de la résolution, nombre d'erreurs, tentatives de triche détectées. Alimente la série de réveils et le retard moyen.

---

## 4. Flux d'alarme

Ordre exact, chaque étape dépendant de la précédente :

1. `AlarmManager` déclenche `AlarmReceiver` à l'heure exacte.
2. `AlarmReceiver` démarre `AlarmService` en foreground.
3. `AlarmService` prend un `WakeLock`, force le volume `STREAM_ALARM` au maximum, lance la sonnerie en boucle.
4. `AlarmService` publie une notification avec `FullScreenIntent` qui ouvre `AlarmActivity`, configurée avec `setShowWhenLocked(true)` et `setTurnScreenOn(true)`. C'est ce couple qui passe par-dessus l'écran verrouillé.
5. `AlarmActivity` démarre `HandDetector`. Dès l'événement « en main », `VolumeStateMachine` passe en `LOWERED` et le volume descend à 30 %.
6. L'utilisateur ouvre le défi. `InactivityTimer` tourne en continu ; chaque frappe appelle `onUserInteraction()` et le remet à zéro. À expiration, l'état passe en `RAMPING_UP` et le volume remonte progressivement à 100 % sur 3 s.
7. `isSolved` passe à `true` : `AlarmService` s'arrête, le volume système d'origine est restauré, un `WakeRecord` est écrit, l'écran de victoire s'affiche, l'occurrence suivante est planifiée.

### Permissions et compatibilité constructeurs

C'est le point qui décide si l'app marche ou pas. Permissions requises :

- `USE_EXACT_ALARM` — Android 13+, accordée sans dialogue aux apps de type réveil. Cocorico entre dans cette catégorie.
- `SCHEDULE_EXACT_ALARM` — repli pour Android 12. En dessous d'Android 12, les alarmes exactes ne demandent aucune permission ; le code doit brancher sur la version, pas supposer.
- `POST_NOTIFICATIONS` — Android 13+, obligatoire pour le `FullScreenIntent`.
- `USE_FULL_SCREEN_INTENT` — Android 14+.
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — sur demande explicite.

Un écran d'onboarding vérifie chaque permission et l'exemption d'optimisation batterie, et refuse d'armer l'alarme tant qu'il en manque une critique. Sur Samsung, Xiaomi et Oppo, l'absence d'exemption batterie tue le service pendant la nuit : c'est la première cause d'avis à une étoile chez les concurrents. L'onboarding doit détecter ces constructeurs et afficher le chemin exact vers leurs réglages propriétaires.

---

## 5. Anti-triche

| Contournement | Contre-mesure |
|---|---|
| Boutons de volume | `AlarmActivity.onKeyDown` intercepte et consomme `KEYCODE_VOLUME_UP` / `DOWN` pendant l'alarme. |
| Kill de l'app | `AlarmService` en `START_STICKY`, plus une alarme de secours à 30 s annulée seulement à la résolution du défi. |
| Redémarrage du téléphone | `BootReceiver` détecte l'alarme en cours et relance le service immédiatement. |
| Retour arrière / accueil | Bloqués pendant le défi. La navigation système reste accessible (obligation Play Store), mais la sonnerie continue par-dessus. |
| Mode avion, mode silencieux | Sans effet : `STREAM_ALARM` ignore le mode silencieux, et rien ne dépend du réseau en V1. |

Chaque tentative détectée est enregistrée dans le `WakeRecord` et affichée après coup, sur le ton de la marque.

**Non retenu :** Device Admin pour bloquer la désinstallation. L'utilisateur a choisi le niveau « fort », pas « maximum ». Cette permission fait échouer la publication Play Store dans la majorité des cas.

---

## 6. Écrans

Cinq écrans, c'est toute l'app. Maquettes haute fidélité dans `design/cocorico-identite.html`.

| Écran | Contenu |
|---|---|
| **Accueil** | Le cadran *est* l'écran. Heure en grand, jours de la semaine, deux lignes de réglage (sonnerie, défi), un bouton « Armer le coq ». |
| **Choix du défi** | Maths sélectionnable, pompes et photo visibles mais marquées « bientôt ». Sélecteur de difficulté. |
| **Alarme** | Rouge plein écran par-dessus le verrouillage, coq bec ouvert, heure, un seul bouton qui n'arrête rien : il ouvre le défi. |
| **Défi** | Problème en grand, pavé numérique, progression 2/3, niveau de volume affiché en permanence, avertissement de remontée. |
| **Victoire** | Coq faisant un clin d'œil, heure de lever réelle, série de réveils, retard moyen. Deux chiffres, pas un tableau de bord. |

Contrainte d'accessibilité : rien sous 15 sp dans les écrans d'alarme. À 6 h du matin, les yeux ne s'accommodent pas.

---

## 7. Tests

**Testable sans appareil, en TDD :**

- `AlarmScheduler` — calcul de la prochaine occurrence selon les jours actifs, passage à la semaine suivante, changement d'heure.
- `VolumeStateMachine` — transitions entre les trois états, sur événements de prise en main, d'interaction et d'expiration.
- `MathProblemGenerator` — bornes de difficulté, unicité, exactitude des réponses attendues.
- `InactivityTimer` — réarmement, expiration.

**Vérifiable uniquement à la main sur appareil réel** — livré comme check-list de recette, pas comme tests instrumentés fragiles :

- Déclenchement à l'heure, écran verrouillé, téléphone en veille depuis plusieurs heures.
- Survie au redémarrage pendant que l'alarme sonne.
- Neutralisation des boutons de volume.
- Relance après kill depuis les applications récentes.
- Comportement sur Samsung et Xiaomi sans exemption batterie (doit avertir, pas échouer en silence).

---

## 8. Suite, sans refonte

- **Défi pompes** — capteur de proximité, comptage de répétitions. Implémente `Challenge`.
- **Défi photo** — objet imposé, photo validée par une IA de vision. Exige un repli hors-ligne obligatoire : sans réseau à 6 h du matin, l'app doit basculer sur le défi maths, jamais rester bloquée.
- **Défi code-barres** — scanner un objet de la cuisine. Entièrement hors-ligne.
- **Rituel du soir** et rappel de coucher.
- **iOS** — réécriture complète. Les alarmes y sont bien plus contraintes ; à traiter comme un produit distinct, pas comme un portage.
