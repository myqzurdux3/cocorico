# Reprise de session

Note de passation, à lire en premier après une compaction de contexte ou au
début d'une nouvelle session. Décrit où en est le travail et comment le
reprendre. Le produit lui-même est décrit dans `cocorico.md`, l'audit dans
`../AUDIT.md`.

---

## Où en est le travail

**Branche :** `cocorico-v1`, poussée sur `origin`. Dépôt **privé**, et
l'utilisateur veut qu'il le reste.
**Pull request :** https://github.com/myqzurdux3/wake-up/pull/1 (ouverte).
**Base :** `main`.

**État mesuré** — 45 commits au-delà de `190d41c` :

| | |
|---|---|
| Tests unitaires | **331, 0 échec** |
| Tests instrumentés | **10, 0 échec** (Pixel 9a / Android 17) |
| Avertissements du compilateur | **0** |
| Lint | 66 constats, dont **2 hors versions de dépendances** |
| APK release | 5,0 Mo, signé, R8 actif |

Un audit complet a été mené (`../AUDIT.md`) : tous les constats des phases 1 à 6
sont traités. Depuis, quatre demandes livrées : une seule photo par défi photo,
tenue basse des pompes ramenée à 100 ms, mode **Sur mesure**, et le nettoyage
du lint.

---

## LA CHOSE À FAIRE ENSUITE

**Essayer le mode Sur mesure sur l'appareil.** Il n'a jamais sonné : son
enchaînement n'est vérifié que par tests unitaires et par des captures des
écrans de réglage.

**L'utilisateur a donné son accord pour faire sonner, à deux conditions :**
volume bridé à **10 %**, et **tout remis à son état d'origine ensuite**.

---

## Protocole d'essai sur appareil

### Règles de sécurité, non négociables

- **Ne jamais déclencher l'alarme sans accord explicite pour cette fois-là.**
  Téléphone personnel. Un essai à plein volume l'a déjà fait paniquer.
- **Capture d'écran avant chaque `adb input tap`.** Des appuis à l'aveugle ont
  déjà atterri dans ses réglages système.
- **Vérifier l'application au premier plan avant toute capture.** Si ce n'est
  pas Cocorico, ne pas capturer ; si une capture a été prise par erreur, la
  détruire sans l'ouvrir. C'est déjà arrivé deux fois (Telegram, Snapchat).
- **Remettre l'état d'origine à la fin**, et le vérifier.

### État d'origine du téléphone

| Réglage | Valeur |
|---|---|
| Volume du flux d'alarme | **5 sur 7** |
| `font_scale` | **1.0** |
| Application | installée, sur la liste blanche batterie |

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

Pour retirer l'atténuation :
`adb shell "run-as com.cocorico rm -f files/attenuation_essai"`

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
faut toucher chaque champ, effacer avec `KEYCODE_DEL`, puis saisir.

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

- **Essai des pompes en conditions réelles.** Il a testé et validé le comptage,
  puis demandé la tenue basse à 100 ms — c'est fait, mais **pas réessayé
  depuis**.
- **Essai du mode Sur mesure.** Jamais sonné.
- La version **release n'a jamais sonné** : la chaîne d'alarme n'a été éprouvée
  qu'en débogage, sans R8.

## Dette connue, assumée

- **Aucun test instrumenté Compose.** Tous les défauts d'écran de l'audit ont
  été trouvés à l'œil. C'est le seul manque de fond.
- **AGP 9 non migré** : il exige Gradle 9.5 puis entre en conflit avec le
  greffon Kotlin. Toute la dernière génération d'AndroidX est derrière lui.
- **Triche à la paume** sur les pompes, aggravée volontairement par la tenue
  basse à 100 ms. **Triche à l'écran** sur la photo. Les deux sont assumées.
- Pas de direct boot.

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
