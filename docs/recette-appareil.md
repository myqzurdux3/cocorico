# Recette Cocorico — vérifications sur appareil réel

Aucun de ces points n'est couvert par les tests unitaires. À rejouer
intégralement avant chaque publication.

## Matériel minimum

Un appareil Pixel (Android de référence) et un Samsung ou Xiaomi (gestion
agressive de la batterie). L'émulateur ne reproduit pas le Doze mode réel.

## Déclenchement

- [ ] Alarme réglée à +2 min, écran verrouillé, téléphone posé : l'écran
      s'allume et la sonnerie part à l'heure exacte.
- [ ] Alarme réglée à +8 h, téléphone en veille toute la nuit sans être
      touché : elle sonne à l'heure. C'est le test qui valide le Doze mode.
- [ ] Alarme un jour non coché : elle ne sonne pas.
- [ ] Alarme désarmée : elle ne sonne pas.
- [ ] Toucher la notification d'alarme au lieu de passer par l'écran plein :
      le défi s'ouvre.
- [ ] Sur Android 14 et sur Android 15 : l'écran d'alarme apparaît réellement
      par-dessus l'écran verrouillé, sans déverrouillage préalable.
- [ ] Une nuit de changement d'heure (passage été/hiver) sur les deux
      appareils : l'alarme sonne à l'heure affichée, pas une heure à côté.

## Volume

- [ ] Au déclenchement, le volume est au maximum même si le téléphone était
      en mode silencieux.
- [ ] Prendre le téléphone en main baisse le son en moins de 3 s.
- [ ] Poser le téléphone sans toucher au défi : le son remonte à fond au bout
      de 10 s.
- [ ] Après résolution, le volume système d'alarme retrouve sa valeur d'avant.
- [ ] Laisser le défi non résolu pendant trois minutes pleines, téléphone en
      main, puis le résoudre : une seule sonnerie audible du début à la fin
      (jamais deux voix superposées), le volume reste bas tant que le téléphone
      est en main, et silence total après la résolution. C'est le test du filet
      de secours qui repasse toutes les 30 s.
- [ ] Pendant le défi, le niveau de volume et le compte à rebours avant
      remontée sont affichés en permanence et lisibles à bout de bras.

## Anti-triche

- [ ] Boutons de volume pendant l'alarme : aucun effet, aucune bulle système.
- [ ] Balayer l'application depuis les applications récentes : elle revient
      en moins de 30 s.
- [ ] Résoudre le défi à l'instant précis où le filet de secours se déclenche
      (répéter une dizaine de fois) : l'alarme ne revient jamais après coup.
- [ ] Redémarrer le téléphone pendant que l'alarme sonne : elle repart après
      le démarrage.
- [ ] Redémarrer le téléphone alarme armée mais non déclenchée : la prochaine
      occurrence est bien reprogrammée.
- [ ] Bouton retour pendant le défi : sans effet.
- [ ] Sur Android 15 : le geste de retour (balayage depuis le bord) pendant le
      défi ne ferme pas l'écran d'alarme. Le bouton retour n'y passe plus par le
      même chemin que sur les versions précédentes.
- [ ] Arrêt forcé de l'application depuis les réglages pendant que l'alarme
      sonne, puis redémarrage du téléphone : aucune alarme ne part au démarrage,
      et la prochaine occurrence est quand même reprogrammée.
- [ ] Sur Android 12 : révoquer l'autorisation d'alarmes exactes alarme armée,
      puis résoudre une alarme et redémarrer le téléphone : aucun plantage,
      l'onboarding redemande l'autorisation.

## Défi

- [ ] Trois bonnes réponses coupent la sonnerie.
- [ ] Une mauvaise réponse affiche « Non. Et le coq a entendu. » et régénère
      un calcul, sans faire remonter le volume.
- [ ] Les trois difficultés produisent des calculs du bon calibre.

## Lisibilité

- [ ] Parcourir les cinq écrans en thème sombre puis en thème clair : chaque
      libellé, chaque chiffre et chaque touche du pavé numérique est lisible,
      y compris la pastille de difficulté sélectionnée et le message
      « Non. Et le coq a entendu. » sur le fond rouge du défi.

## Constructeurs

- [ ] Sur Samsung et Xiaomi, sans exemption de batterie : l'onboarding
      affiche la consigne propre au constructeur et refuse d'armer.
- [ ] Avec exemption accordée : l'alarme survit à une nuit complète.

## Statistiques

- [ ] Après un réveil, la série passe à 1 ; le lendemain, à 2.
- [ ] Sauter un jour remet la série à 1.
- [ ] Le retard moyen correspond à l'écart réel constaté.
- [ ] La statistique s'appelle « Réveils d'affilée » : aucune triche n'est
      comptée, le libellé ne doit pas prétendre le contraire.

## Défi pompes

Le comptage repose sur le capteur de proximité et l'accéléromètre. Aucun de ces
seuils n'a été mesuré sur un vrai geste : ils viennent de simulations. Cette
section sert autant à valider qu'à calibrer.

### Mesures relevées le 16 août 2026 (Pixel 9a, téléphone au sol)

Premières valeurs réelles, contre des seuils qui n'étaient jusque-là que
simulés. Traces `CocoricoPompes` dans `logcat`, actives en version de débogage.

| Signal | Mesuré | Seuil | Marge |
|---|---|---|---|
| Inclinaison, posé à plat | 0,3 à 0,4° | ≤ 15° | facteur 35 |
| Écart de gravité, série en cours | 0,00 à 0,04 | ≤ 1,5 | facteur 35 |
| Phase basse d'une répétition | 766 ms | ≥ 150 ms | facteur 5 |
| Durée depuis la dernière position haute | 833 ms | 600 à 8 000 ms | dans la plage |

Deux enseignements. D'abord la garde « immobile » ne gêne pas : la crainte que
les chocs au sol d'une vraie série la fassent rejeter est démentie, l'écart
reste cinquante fois sous le seuil. Ensuite la borne basse de 600 ms est la
plus serrée des quatre : une répétition enchaînée vite passerait dessous.
À surveiller si des répétitions manquent.

**Ce qui reste incertain : le placement du téléphone.** Lors du premier essai
sous alarme, aucune répétition n'a été comptée ; au second, tout a fonctionné.
Le capteur de proximité est en haut de l'appareil et ne voit qu'à quelques
centimètres. Le téléphone doit être sous le sternum, pas sous le visage ni
sous le ventre. À confirmer, et à dire dans l'interface si ça se répète.

### Comptage

- [ ] Dix pompes réelles, téléphone au sol écran vers le haut : le compteur
      suit sans rater de répétition et sans en inventer.
- [ ] Sur vingt pompes réelles, noter combien de répétitions sont ratées.
      Au-delà de deux, ajuster les seuils de `CompteurPompes` — les valeurs
      viennent de simulations, pas de mesures.
- [ ] L'écran reste allumé pendant toute la série, sans y toucher.
- [ ] S'arrêter dix secondes en milieu de série : le volume remonte à fond, et
      les répétitions déjà acquises sont conservées.

### Anti-triche

Chacune de ces manipulations doit échouer à faire compter une répétition.

- [ ] Passer la main rapidement devant le capteur, téléphone posé.
- [ ] Passer la main devant le capteur en tenant le téléphone en main.
      C'est la triche que le canal rapide corrige : à valider en priorité.
- [ ] Agiter le téléphone à environ deux allers-retours par seconde tout en
      masquant le capteur. Si ça compte, `ECART_MAX` est trop haut face au
      bruit réel de l'accéléromètre.
- [ ] Descendre sans remonter.
- [ ] Rester en position basse plusieurs secondes puis remonter très lentement
      (cycle au-delà de huit secondes).
- [ ] **Triche connue, à mesurer.** Téléphone posé sur la table de nuit,
      tenir la paume deux centimètres au-dessus du capteur environ six dixièmes
      de seconde, retirer, recommencer dix fois. En simulation, le défi est
      validé en neuf secondes sans se lever. Vérifier que c'est bien le cas sur
      l'appareil, et noter combien de temps ça demande vraiment.

### Ce que le capteur ne peut pas savoir

Le capteur de proximité ne distingue pas un torse d'une paume : il ne voit
qu'un obstacle proche. Les quatre règles anti-triche n'observent que le
téléphone — inclinaison, immobilité, durées — et une main qui ne le touche pas
ne le fait ni pencher ni bouger.

Décision prise avec l'utilisateur le 16 août 2026 : **on accepte cette limite
pour l'instant**, et on décide après la calibration sur appareil. Raison :
aucun seuil du compteur n'a encore été mesuré sur un vrai geste, ils viennent
tous de simulations. Ajouter une règle supplémentaire non mesurée par-dessus
des seuils non mesurés risque surtout de faire échouer de vraies pompes.

La piste retenue si la triche s'avère gênante : exiger que l'accéléromètre voie
un choc au sol pendant la phase basse. Des mains et des pointes de pied en
transmettent un, une paume en l'air non. À calibrer avec précaution — moquette,
tapis épais et plancher souple amortissent le choc.

### Repli et renoncement

- [ ] Bouton « Je ne peux pas » : bascule immédiate sur les calculs, l'alarme
      continue de sonner, et le calcul se résout normalement.
- [ ] Après renoncement, faire des pompes ne compte plus rien et ne fait plus
      remonter le compte à rebours d'inactivité — les capteurs de l'ancien défi
      doivent être libérés.
- [ ] L'écran de victoire affiche « Calculs (renoncé) ».
- [ ] Coque épaisse ou étui posé sur le capteur : le comptage ne part pas en
      boucle, et le repli reste atteignable.

### Mise à jour depuis la version précédente

Le seul chemin irréversible de cette version : la base de données change de
schéma. Une migration ratée efface l'historique ou fait planter l'application
au démarrage. Ce scénario n'est couvert par aucun test automatique.

- [ ] Installer la version précédente, faire au moins un réveil complet pour
      remplir l'historique, puis installer par-dessus la nouvelle version sans
      désinstaller.
- [ ] L'application démarre, l'écran de victoire retrouve les anciens réveils,
      et la série n'est pas repartie de zéro.
- [ ] Les réveils antérieurs à cette version s'affichent comme « Calculs ».

## Défi photo

Le défi le plus fragile des trois : il dépend d'une caméra, d'une permission,
d'un modèle de reconnaissance et parfois d'un réseau. Aucun de ses réglages n'a
été mesuré. Cette section sert d'abord à trouver ce qui casse.

### Reconnaissance

- [ ] Photographier l'objet demandé, bien cadré, en pleine lumière : accepté du
      premier coup.
- [ ] **Compter les essais nécessaires** sur dix objets réellement présents,
      dans la lumière d'une chambre au réveil, pas en plein jour. Au-delà de
      deux essais en moyenne, le seuil de confiance de `JugementPhoto` est trop
      haut — c'est le réglage le plus dangereux du défi, celui qui peut laisser
      quelqu'un bloqué devant une sirène.
- [ ] **Noter les objets du catalogue jamais reconnus** et les retirer. Un objet
      que le modèle ne sait pas nommer rend le défi impossible ce matin-là.
- [ ] Photographier un objet quelconque, différent de celui demandé : refusé.
- [ ] Mesurer le délai entre le déclenchement et le verdict.
- [ ] **Comparer téléphone tenu en portrait et en paysage.** Si le portrait
      refuse beaucoup plus souvent, la rotation de la capture n'est pas
      appliquée avant la reconnaissance : le modèle reçoit une image couchée.
      Ce défaut est invisible depuis les seuils, qui restent corrects.

### Replis, à vérifier avant tout le reste

- [ ] Refuser la permission caméra, puis déclencher l'alarme : le défi doit
      basculer sur le calcul mental **avant tout affichage**, sans écran vide ni
      plantage.
- [ ] Bouton « Je ne peux pas », appui long : bascule sur le calcul mental,
      renoncement enregistré dans l'historique.
- [ ] Sans clé d'API renseignée : le défi photo n'est pas proposé du tout, et
      l'accueil comme les réglages annoncent le défi qui sonnera réellement.
- [ ] Mode avion, clé renseignée : le défi ne doit jamais rester suspendu au
      réseau. L'écran doit dire que le juge ne répond pas — et non « pas encore
      reconnu » — et le repli calculs doit rester atteignable.

### Le juge distant

Il n'existe qu'un juge, l'API Gemini, et il exige une clé fournie par
l'utilisateur. La reconnaissance embarquée a été retirée : les points qui
testaient un interrupteur « mode en ligne », un consentement préalable et un
second avis n'ont plus d'objet.

- [ ] Clé absente ou invalide : message explicite, jamais un refus déguisé.
- [ ] La clé n'apparaît **jamais** à l'écran, ni dans un message d'erreur, ni
      dans une trace. Vérifier avec une clé volontairement fausse.
- [ ] Quota dépassé (HTTP 429) : l'écran distingue la panne du refus.
- [ ] Vérifier sur un réseau lent que le budget de huit secondes suffit.
- [ ] Banc d'essai « Essayer la reconnaissance » : même chaîne que le défi réel,
      sans faire sonner l'alarme. C'est par là qu'on commence.

### Anti-triche

- [ ] Impossible de choisir une photo de la galerie : la capture est en direct.
- [ ] La caméra reste **dans l'écran d'alarme**. Aucune manipulation ne doit
      ouvrir l'application appareil photo du système, ce qui offrirait un chemin
      vers les réglages du téléphone pendant que l'alarme sonne.
- [ ] **Triche connue, à constater.** Photographier l'objet affiché sur un écran
      d'ordinateur : ce sera accepté. Le juge ne distingue pas de façon fiable
      un objet de son image. Décision assumée, au même titre que la
      triche à la paume des pompes : trouver l'image suppose d'être réveillé.

### Vie privée

- [ ] Après plusieurs réveils en photo, vérifier qu'**aucune image** ne traîne
      dans le stockage de l'application ni dans la galerie du téléphone.

## Limites connues

Ce ne sont pas des bugs à corriger dans cette version, mais des comportements
à connaître avant de conclure qu'une alarme a raté.

- **Pas de démarrage direct.** `BootReceiver` n'est pas `directBootAware` :
  sur un téléphone protégé par un code, `BOOT_COMPLETED` n'est délivré qu'au
  premier déverrouillage. Un téléphone qui redémarre à 3 h et qu'on ne touche
  pas ne reprogramme donc pas son alarme de 6 h. Le vrai support exigerait de
  basculer `AlarmState` et la configuration DataStore sur le stockage protégé
  par l'appareil, ce qui dépasse cette version. À vérifier en recette :
  redémarrer, laisser l'appareil verrouillé, et constater le comportement.
- **Aucun comptage de triche.** La série compte des matins consécutifs avec un
  défi résolu, rien de plus.
- **Fenêtre de validité d'une heure.** Une alarme laissée à sonner plus d'une
  heure n'est plus relancée après un redémarrage : le drapeau est considéré
  comme périmé, la prochaine occurrence restant programmée normalement.
