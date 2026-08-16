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

## Volume

- [ ] Au déclenchement, le volume est au maximum même si le téléphone était
      en mode silencieux.
- [ ] Prendre le téléphone en main baisse le son en moins de 3 s.
- [ ] Poser le téléphone sans toucher au défi : le son remonte à fond au bout
      de 10 s.
- [ ] Après résolution, le volume système d'alarme retrouve sa valeur d'avant.

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

## Défi

- [ ] Trois bonnes réponses coupent la sonnerie.
- [ ] Une mauvaise réponse affiche « Non. Et le coq a entendu. » et régénère
      un calcul, sans faire remonter le volume.
- [ ] Les trois difficultés produisent des calculs du bon calibre.

## Constructeurs

- [ ] Sur Samsung et Xiaomi, sans exemption de batterie : l'onboarding
      affiche la consigne propre au constructeur et refuse d'armer.
- [ ] Avec exemption accordée : l'alarme survit à une nuit complète.

## Statistiques

- [ ] Après un réveil, la série passe à 1 ; le lendemain, à 2.
- [ ] Sauter un jour remet la série à 1.
- [ ] Le retard moyen correspond à l'écart réel constaté.
