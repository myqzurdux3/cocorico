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
