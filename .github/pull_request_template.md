## Ce que ce changement empêche d'arriver

<!-- Pas « ce qu'il fait » : ce qu'il évite. Si la réponse est « c'est plus
     joli », relis la première règle de CONTRIBUTING.md. -->

## Comment c'est vérifié

- [ ] `./gradlew testDebugUnitTest assembleDebug lintDebug` passe
- [ ] Le test a été écrit **avant** la correction et son échec a été constaté
- [ ] Ou bien : ce changement n'est pas testable sans appareil, et c'est dit ci-dessous

<!-- Si une partie n'a été vérifiée que sur téléphone, dis laquelle et sur quel
     modèle. Si rien n'a été essayé sur téléphone, dis-le aussi. -->

## Décisions qui lient encore

- [ ] `setAlarmClock` n'est pas remplacé
- [ ] Aucun repli silencieux ajouté : tout repli est atteignable et visible
- [ ] Aucune clé d'API, aucune photo, ne sort de l'appareil autrement qu'annoncé
