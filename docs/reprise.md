# Reprise de session

Note de passation, à lire en premier après une compaction de contexte ou au
début d'une nouvelle session. Décrit où en est le travail et comment le
reprendre. Le produit lui-même est décrit dans `cocorico.md`, l'audit dans
`../AUDIT.md`.

---

## Où en est le travail

**Dépôt :** `myqzurdux3/cocorico` — renommé depuis `wake-up` le 18 août 2026,
en même temps que le dossier local `~/Documents/cocorico`.
**Branche de travail :** `main`. La V1 y a été fusionnée le 18 août 2026 par la
[PR #1](https://github.com/myqzurdux3/cocorico/pull/1), en **commit de fusion**
et non en `squash` : les 100 commits disent chacun pourquoi une décision a été
prise, et cet historique vaut mieux qu'une ligne propre. La branche
`cocorico-v1` a été supprimée après vérification qu'elle ne contenait rien que
`main` n'ait déjà : **`main` est désormais la seule branche**, locale comme
distante. Dépôt **public** depuis le 18 août 2026 — ce qui est écrit ici est
lisible par tout le monde, y compris les notes de travail.

**État mesuré** — 170 commits sur `main` :

| | |
|---|---|
| Tests unitaires | **352, 0 échec** |
| Tests instrumentés | **10, 0 échec** (Pixel 9a / Android 17) |
| Avertissements du compilateur | **0** |
| Lint | 75 constats, dont **57 sont des montées de version** bloquées par le mur AGP 9 ; les 18 autres sont listés dans « Dette connue » |
| APK release | 4,66 Mo, signé, R8 actif |

Un audit complet a été mené (`../AUDIT.md`) : tous les constats des phases 1 à 6
sont traités. Depuis, quatre demandes livrées : une seule photo par défi photo,
tenue basse des pompes ramenée à 100 ms, mode **Sur mesure**, et le nettoyage
du lint.

---

## Ce que l'essai du 18 août 2026 a donné

Le mode Sur mesure **a sonné pour de vrai**, à 1 sur 7, séquence
`1 calcul → 1 pompe → 1 photo` :

- l'enchaînement, l'en-tête « Épreuve N sur M » et la fin de séquence qui
  coupe la sonnerie : **corrects** ;
- le renoncement à une épreuve la remplace bien par des calculs **sans
  toucher aux autres** ni au rang affiché ;
- l'épreuve photo, faute de clé d'API, est remplacée **d'office** : rien ne
  piège l'utilisateur ;
- la composition (compteurs, flèches, ordre) est persistée telle quelle :
  `MATHS:1,POMPES:1,PHOTO:1`.

**Quatre défauts trouvés là où les tests unitaires ne regardaient pas**, tous
corrigés depuis : nom du défi dans l'historique, distinction désarmé / aucun
jour actif, comptage des fautes en Sur mesure, débordement de la carte « Défi ».

### Ce que cet essai-là n'avait pas pu couvrir

- **Une vraie pompe** — le geste ne se simule pas par `adb` — et **une vraie
  photo**, faute de clé d'API. **L'utilisateur les a essayées lui-même le même
  jour, dans une séquence Sur mesure : les deux marchent.** La tenue basse à
  100 ms est donc validée en conditions réelles.
- **La version publiée.** La chaîne d'alarme n'a jamais tourné sous R8. Ses
  invariants, eux, sont vérifiés sans bruit à chaque build : voir plus bas.

## LA CHOSE À FAIRE ENSUITE

**Rien d'obligatoire.** Le mode Sur mesure a été éprouvé de bout en bout, avec
de vraies pompes et une vraie photo. Ce qui reste est listé plus bas et demande
une décision de l'utilisateur, pas du travail de plus.

**Rappel : l'accord pour faire sonner vaut pour l'occasion où il est donné, pas
pour les suivantes.** Volume bridé à **10 %**, et **tout remis à son état
d'origine ensuite**.

---

## Protocole d'essai sur appareil

### Règles de sécurité, non négociables

- **Ne jamais déclencher l'alarme sans accord explicite pour cette fois-là.**
  C'est le téléphone dont quelqu'un se sert vraiment : une sonnerie non
  voulue n'est pas un désagrément d'essai, c'est une sonnerie non voulue.
- **Capture d'écran avant chaque `adb input tap`.** Des appuis à l'aveugle ont
  déjà atterri hors de l'application.
- **Vérifier l'application au premier plan avant toute capture.** Si ce n'est
  pas Cocorico, ne pas capturer ; si une capture a été prise par erreur, la
  détruire sans l'ouvrir. Le cas s'est déjà produit.
- **Remettre l'état d'origine à la fin**, et le vérifier.

### État d'origine du téléphone

| Réglage | Valeur |
|---|---|
| Volume du flux d'alarme | **5 sur 7** |
| `font_scale` | **1.0** |
| Application | **installée en version publiée 1.0**, désarmée, au 18 août 2026 |

**L'application ne disparaît pas toute seule.** Cette note l'a affirmé
plusieurs fois comme un fait établi ; c'était faux. Le 18 août 2026, une
disparition signalée a été tracée : les deux seules suppressions du journal
étaient des `adb uninstall` lancés par l'assistant lui-même, et l'application
était installée au moment du signalement. Un redémarrage du téléphone, ce
jour-là, ne l'avait pas retirée.

La leçon vaut au-delà de ce cas : une absence constatée n'est pas une cause
constatée. **Chercher la trace avant de conclure**, et vérifier l'état plutôt
que de le supposer :

```bash
adb shell pm list packages | grep cocorico
```

Il faut une version de **débogage** pour l'atténuation d'essai et pour
`run-as` : la version publiée n'en contient pas une ligne, R8 la supprime
entièrement.

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Atténuation d'essai — sans elle, ça sonne à fond

Elle n'existe qu'en version de débogage et **doit être posée à la main** :

```bash
adb shell "run-as com.cocorico sh -c 'echo 10 > files/attenuation_essai'"
```

**Vérifier qu'elle est lue avant de faire sonner quoi que ce soit.** Lancer
l'application et chercher dans le journal :

```bash
adb logcat -c && adb shell am start -n com.cocorico/.ui.MainActivity && sleep 3
adb logcat -d | grep -i ATTENUATION
```

Sans cette ligne, **la consigne n'est pas active**. C'est exactement l'erreur
commise la première fois : le fichier avait été posé dans le stockage externe,
qui n'existe pas encore à ce moment-là, et l'alarme a sonné à 7 sur 7.

`files/` **n'existe pas tant que l'application n'a jamais été lancée** : après
une installation neuve, la lancer une fois avant d'écrire le fichier.

Pour retirer l'atténuation :
`adb shell "run-as com.cocorico rm -f files/attenuation_essai"`

### La preuve qui ne ment pas : le journal audio du système

La trace `ATTENUATION` peut **disparaître du tampon** `logcat`, qui tourne. Le
18 août, une boucle d'attente `until` sans pause a saturé le tampon et effacé
la ligne de la sonnerie : impossible de savoir après coup si l'atténuation
avait joué. Deux leçons.

**Ne jamais interroger l'appareil en boucle serrée pendant l'attente.** Mettre
une pause entre deux sondages.

Le tampon fait **256 Ko par défaut** sur ce téléphone, soit quelques heures
d'historique à peine. `persist.logd.size` est refusée sans les droits
d'administration, mais la taille se règle pour la session :

```bash
adb logcat -b main -G 8M && adb logcat -b system -G 8M
```

À reposer après chaque redémarrage. C'est ce qui permet de retrouver, un jour
plus tard, **qui** a supprimé un paquet et pourquoi — `usagestats`, lui, ne
garde aucun événement d'installation.

**Et vérifier après coup sur le journal du système, qui enregistre chaque
changement de volume avec son auteur :**

```bash
adb shell dumpsys audio | grep 'STREAM_ALARM index' | tail -5
```

```
12:14:00 setStreamVolume(stream:STREAM_ALARM index:1 flags:0x0 oldIndex:5) from com.cocorico
```

`index:1` venant de `com.cocorico` **prouve** que l'atténuation s'est
appliquée. La trace applicative sert de feu vert avant de faire sonner ; ce
journal-là sert de constat après.

### Restaurer le volume

`adb shell cmd media_session volume --set` **ne fonctionne pas** sur ce flux.
La commande qui marche :

```bash
adb shell cmd audio set-volume 4 5
```

### Permissions, après chaque réinstallation

Une réinstallation les révoque. À redonner par adb, sinon l'onboarding bloque :

```bash
adb shell pm grant com.cocorico android.permission.POST_NOTIFICATIONS
adb shell pm grant com.cocorico android.permission.CAMERA
adb shell appops set com.cocorico USE_FULL_SCREEN_INTENT allow
adb shell dumpsys deviceidle whitelist +com.cocorico
```

### Régler une alarme par l'interface

`AlarmActivity` n'est **pas exportée** : impossible de l'ouvrir directement par
`am start`. Il faut une vraie alarme.

Coordonnées relevées sur ce Pixel 9a (1080 × 2424), écran d'accueil :

| Cible | Appui |
|---|---|
| Horloge (ouvre le sélecteur) | `538 529` |
| Bascule clavier du sélecteur | `255 1700` |
| Champ des heures | `290 1326` |
| Champ des minutes | `419 933` |
| OK | `800 1136` |
| Armer / Désarmer | `538 1615` |
| Carte « Défi » | `538 1186` |

Le sélecteur d'heure ne passe pas automatiquement des heures aux minutes : il
faut toucher chaque champ, effacer, puis saisir.

**Attention, le dialogue se déplace.** Toucher un champ fait apparaître le pavé
numérique du système, qui remonte le dialogue : les coordonnées relevées avant
ne valent plus après. Passer par les touches du pavé plutôt que par
`input text` / `KEYCODE_DEL`, et **recapturer entre chaque étape**. Sans ça, un
appui tombe à côté et ferme le dialogue sans rien changer — arrivé du premier
coup le 18 août.

| Cible, pavé numérique ouvert | Appui |
|---|---|
| Champ des heures | `290 934` |
| Champ des minutes | `419 934` |
| Retour arrière | `940 2009` |
| `1` / `2` / `3` | `138 1670` · `404 1670` · `670 1670` |
| `4` | `138 1839` |
| OK du dialogue | `801 1136` |

### L'alarme ne prend pas l'écran si le téléphone est en main

Android ne donne l'intention plein écran que si l'écran est verrouillé ou
éteint. Téléphone déverrouillé et en cours d'utilisation, la sonnerie part mais
`AlarmActivity` **ne passe pas au premier plan** : il n'y a qu'une notification,
et l'accueil affiche son écran de garde « Le coq n'a pas fini » avec
**Reprendre le défi**. Ce n'est pas une panne, c'est le comportement prévu — ne
pas le diagnostiquer comme un bug une seconde fois.

Un dialogue système « Compatibilité des applis Android » peut s'afficher au
lancement d'une version de débogage. Le fermer par **OK** (`560 1959`), jamais
par « Ne plus afficher » — c'est un réglage système de l'utilisateur.

### Vérifier que l'alarme est bien posée

```bash
adb shell dumpsys alarm | grep -E 'Next wakeup alarm|Next wake from idle'
```

`Next wake from idle: … com.cocorico` est la preuve que l'exemption Doze joue.

### Après l'essai

1. Résoudre le défi (ou couper : `adb shell am force-stop com.cocorico`).
2. **Désarmer l'alarme** — sinon elle sonnera le lendemain à la même heure.
3. Retirer l'atténuation.
4. Vérifier volume à 5, `font_scale` à 1.0, aucune alarme programmée.

---

## Ce que l'utilisateur a demandé et qui reste ouvert

- La version **release n'a jamais sonné** : la chaîne d'alarme n'a été éprouvée
  qu'en débogage, sans R8. **Et elle ne peut pas l'être à 10 %** :
  [NiveauxVolume.POURCENT_MINIMAL] plancher le plafond utilisateur à 50 %, soit
  4 crans sur 7 ici, et R8 retire l'atténuation d'essai. Un essai de la release
  suppose donc d'accepter 4 sur 7 — c'est-à-dire moins fort que le réglage
  d'alarme habituel du téléphone (5 sur 7), mais pas 10 %. **Demander avant.**
- **L'écran de victoire n'a pas été revu après la correction de sa mise en
  page** (valeur du défi ramenée à 17 sp) : il ne s'affiche qu'après une vraie
  sonnerie.
- **La licence de `coq.wav` n'est pas documentée.** C'est un enregistrement
  fourni par l'utilisateur, pas une synthèse de ce dépôt : à trancher avant
  toute publication, voir [`sonneries.md`](sonneries.md).

Réglé depuis :

- **Pompes et photo en conditions réelles, dans une séquence Sur mesure** :
  validé par l'utilisateur le 18 août 2026, avec de vraies pompes et une vraie
  photo. C'était le dernier morceau du mode que personne n'avait vu tourner de
  bout en bout.

## Vérifier la version publiée sans la faire sonner

Trois invariants qui, s'ils cassent, ne se voient pas à l'exécution avant qu'il
ne soit trop tard. Refait le 18 août 2026, après les corrections : les trois
tiennent.

**Les noms des constantes doivent survivre à R8.** La configuration et
l'historique rangent `ChallengeId.name` en clair. Si R8 les renomme, la
première mise à jour minifiée rend illisibles le défi réglé et tout
l'historique de l'utilisateur.

```bash
grep -E 'ChallengeId (MATHS|POMPES|PHOTO|COMBINE) ->' app/build/outputs/mapping/release/mapping.txt
```

Attendu : `COMBINE -> COMBINE`, et de même pour les trois autres.

**L'atténuation d'essai ne doit pas exister en release.**

```bash
unzip -p app/build/outputs/apk/release/app-release.apk 'classes*.dex' | grep -c attenuation_essai
```

Attendu : **0**.

**Les bibliothèques natives doivent être alignées sur 16 Ko**, sans quoi le Play
Store refuse la publication.

```bash
for f in lib/*/*.so; do readelf -lW "$f" | awk '$1=="LOAD"{print $NF; exit}'; done
```

Attendu : `0x4000` partout.

## Dette connue, assumée

- **Aucun test instrumenté Compose.** Tous les défauts d'écran de l'audit ont
  été trouvés à l'œil, et les quatre du 18 août l'ont été en faisant sonner
  pour de vrai. C'est le seul manque de fond, et il coûte à chaque fois.
- **AGP 9 non migré** : il exige Gradle 9.5 puis entre en conflit avec le
  greffon Kotlin. Toute la dernière génération d'AndroidX est derrière lui.
- **Triche à la paume** sur les pompes, aggravée volontairement par la tenue
  basse à 100 ms. **Triche à l'écran** sur la photo. Les deux sont assumées.
- Pas de direct boot.
- **17 constats `UseKtx`**, laissés tels quels. Ce sont des propositions de
  style (`SharedPreferences.edit {}`, `String.toUri()`, `Bitmap.scale()`), sauf
  que la variante KTX de `edit` appelle `apply()` par défaut : appliquer la
  suggestion là où le code écrit `commit()` — l'état d'alarme, qui doit
  survivre à la mort du processus — changerait le comportement sous couvert de
  cosmétique. À ne toucher qu'un par un, avec la raison de chacun.
- **1 constat `ObsoleteSdkInt`** sur `mipmap-anydpi-v26`. Le renommage a été
  tenté et cassait la résolution des ressources (`resource mipmap/ic_launcher
  not found`) : l'avertissement coûte moins cher que la panne.
- **Les quatre sonneries pèsent 1,2 Mo sur 4,66**, stockées **non compressées**
  dans l'APK. Les passer en OGG rendrait environ 1,1 Mo, soit un quart de
  l'APK — mais ajoute un décodage sur le chemin qui fait sonner l'alarme.
  Arbitrage non tranché.

---

## Conventions qui ont fait leurs preuves

- **Français** pour les échanges, l'interface, les commentaires et la KDoc.
  **Anglais** pour les messages de commit et les PR.
- **La KDoc explique le *pourquoi*.** Le *quoi* est déjà dans le code.
- **Toute logique décidable dans une classe sans import `android.*`**, testée.
  Les composants Android ne font que du câblage.
- **Test écrit avant la correction, échec réel capturé.** Une sortie d'échec
  prédite ne prouve rien. Pour prouver l'échec sur du code existant : déplacer
  le fichier hors de l'arbre avec `mv` vers `/tmp`, lancer, capturer, remettre.
  Jamais `git stash`, `checkout` ou `reset`.
- **Un test qui passe du premier coup est suspect** : le muter pour vérifier
  qu'il mord, puis restaurer.
- **`./gradlew spotlessCheck` avant de pousser.** Une fois oublié, la CI aurait
  cassé.
- **Sous-agents** : périmètres de fichiers disjoints, aucune commande `git`,
  le contrôleur commite. Ils ne touchent ni à `docs/` ni à `AUDIT.md`.
