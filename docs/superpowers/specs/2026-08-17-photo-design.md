# Défi photo — conception

Troisième défi de Cocorico, après le calcul mental et les pompes. L'application
nomme un objet ; l'alarme ne se tait que lorsque l'utilisateur en a rapporté une
photo, prise en direct, et qu'un juge l'a acceptée.

Décisions prises avec l'utilisateur le 16 août 2026 : validation **embarquée
d'abord, en ligne si disponible**, et **objet tiré au sort par l'application**
dans une liste qu'elle maîtrise.

---

## Pourquoi ce défi

Le calcul mental se résout au lit. Les pompes se font au pied du lit. Photographier
un objet oblige à se lever, à traverser une pièce et à regarder autour de soi —
c'est le défi qui met le plus de distance entre l'utilisateur et son oreiller.

Il porte aussi le risque le plus élevé du produit : il dépend d'une caméra, d'une
permission, d'un modèle de reconnaissance et parfois d'un réseau. Chacune de ces
dépendances peut manquer au pire moment. La conception ci-dessous existe surtout
pour que **rien de tout cela ne puisse rendre l'alarme impossible à arrêter**.

---

## Parcours

1. L'alarme sonne. L'écran annonce l'objet : « Va photographier une **tasse**. »
2. L'utilisateur appuie sur « Prendre la photo ». L'aperçu caméra s'ouvre
   **dans l'écran d'alarme**, jamais dans une autre application.
3. Il déclenche. Le juge répond en une seconde ou deux.
4. Accepté : l'objet est barré. S'il en reste, l'objet suivant s'affiche.
5. Refusé : le compte d'essais monte, l'alarme continue, il recommence.
6. Tous les objets validés : l'alarme se tait.

Le nombre d'objets suit la difficulté, comme les autres défis : **1 en facile,
2 en moyen, 3 en difficile**.

---

## Architecture

Même découpage que les pompes, pour la même raison : toute décision vit dans du
code pur, testable sans appareil, sans caméra et sans réseau.

| Élément | Rôle | Dépendances |
|---|---|---|
| `CatalogueObjets` | La liste des objets et le tirage | pur |
| `JugementPhoto` | Le verdict à partir d'étiquettes reconnues | pur |
| `PhotoChallengeEtat` | Objets restants, essais, résolution | pur |
| `JugePhoto` | Interface : une image, un objet attendu, un verdict | — |
| `JugeEmbarque` | Reconnaissance sur l'appareil | ML Kit |
| `JugeDistant` | Reconnaissance par un modèle distant | réseau, clé |
| `PhotoChallenge` | `Challenge` : câble caméra, juges et écran | Android |

Le défi ne sait pas quel juge lui répond. C'est ce qui permet de tester la
logique de progression sans jamais prendre une photo.

---

## Le catalogue

Une liste figée dans le code, d'environ trente objets, restreinte à ceux que la
reconnaissance embarquée identifie de façon fiable et qu'on trouve dans un
logement ordinaire : tasse, chaussure, plante, livre, bouteille, clavier,
horloge, serviette, chaise, réfrigérateur, brosse à dents…

Chaque entrée porte trois choses : un identifiant, le nom affiché en français,
et **les étiquettes anglaises acceptées** du modèle embarqué, qui ne parle
qu'anglais. Plusieurs étiquettes par objet : le modèle rend `Mug` pour certaines
tasses et `Cup` pour d'autres, refuser l'une des deux serait arbitraire.

Le tirage exclut les objets déjà validés dans la même session, et évite de
répéter ceux du réveil précédent — sinon l'utilisateur les laisse à portée de
lit et le défi perd son sens.

---

## Les deux juges

### Embarqué, toujours présent

Reconnaissance d'images ML Kit, modèle embarqué dans l'application : aucun
réseau, aucune clé, aucun coût, réponse immédiate. Il rend une liste
d'étiquettes avec un indice de confiance.

**Le seuil de confiance est le seul réglage qui compte.** Trop haut, l'objet
n'est jamais reconnu et l'utilisateur reste bloqué devant une sirène. Trop bas,
n'importe quelle photo passe. Valeur de départ : **0,55**, à calibrer sur
appareil — comme tous les seuils de ce projet, elle est pour l'instant un pari,
et la recette doit le dire.

### Distant, en secours et sur demande

Trois conditions cumulatives, aucune par défaut :

1. l'utilisateur a **activé** le mode en ligne dans les réglages ;
2. une **clé d'API lui appartenant** est enregistrée ;
3. le réseau répond.

Il n'intervient que sur un **refus** de l'embarqué. Un accord embarqué n'est
jamais remis en cause : c'est gratuit, instantané, et il n'y a aucune raison de
payer pour contredire un juge qui a dit oui.

Cette place en second lui donne exactement le rôle utile : rattraper les cas que
le modèle embarqué rate — objet de travers, moitié hors cadre, lumière de 6 h du
matin.

Appel borné à **8 secondes**. Au-delà, verdict négatif et l'utilisateur
recommence. Le défi n'attend jamais le réseau.

---

## Ce qui ne doit jamais arriver

Une alarme qu'on ne peut pas arrêter. Trois portes de sortie, dans cet ordre :

1. **Avant tout affichage** : pas de caméra sur l'appareil, permission caméra
   refusée, ou reconnaissance embarquée indisponible → le défi bascule sur le
   calcul mental, exactement comme les pompes le font déjà quand les capteurs
   manquent.
2. **Pendant le défi** : le bouton « Je ne peux pas », appui long comme pour les
   pompes, bascule sur le calcul mental. Le renoncement est enregistré.
3. **Sur erreur imprévue** : toute exception de caméra ou de juge vaut refus,
   jamais plantage. L'écran reste, l'utilisateur peut réessayer ou renoncer.

La règle qui gouverne les trois : **le défi photo peut échouer, l'arrêt de
l'alarme ne peut pas.**

---

## Anti-triche

- **Capture en direct uniquement.** Aucun accès à la galerie : sinon l'objet se
  photographie la veille.
- **La caméra vit dans l'écran d'alarme.** Passer par l'application appareil
  photo du système ferait sortir de l'activité d'alarme et offrirait un chemin
  vers les réglages du téléphone.
- **Essais illimités mais comptés**, enregistrés dans le champ `erreurs` de
  l'historique — le même que les fautes de calcul, avec le même sens : combien
  de fois l'utilisateur s'est trompé avant d'y arriver.
- **Chaque déclenchement réarme le compte à rebours du volume**, comme une
  répétition de pompes ou une frappe sur le pavé numérique.

Limite assumée, à écrire dans la recette : **une photo d'écran affichant l'objet
est acceptée**. Ni le modèle embarqué ni le modèle distant ne distinguent de
façon fiable un objet réel d'une image de cet objet. Contourner demande de
trouver l'image, ce qui suppose d'être réveillé — le même raisonnement que pour
la triche à la paume des pompes, et la même décision : on l'accepte et on
l'écrit.

---

## Vie privée

Le mode en ligne envoie une photographie prise dans une chambre, au réveil, à un
serveur tiers. C'est la seule fonction de l'application qui fasse sortir une
donnée du téléphone.

- **Éteint par défaut**, et il le reste tant que l'utilisateur ne l'active pas
  lui-même.
- L'écran de réglages **dit en clair ce qui part et où**, avant l'activation, pas
  après.
- Aucune photo n'est écrite sur le disque, à aucun moment, dans aucun mode. Elle
  vit en mémoire le temps du verdict.
- La clé d'API appartient à l'utilisateur. Aucune clé n'est livrée avec
  l'application : une clé embarquée serait extractible par n'importe qui.

---

## Persistance

`WakeRecord` gagne la valeur `PHOTO` pour son champ `defi` — **aucune migration
de base**, la colonne existe déjà et c'est une chaîne. Le champ `erreurs`
accueille le nombre d'essais ratés.

Le mode en ligne, la clé d'API et le consentement vont dans la configuration
DataStore existante.

---

## Permissions

`CAMERA`, demandée à l'exécution. L'onboarding la réclame quand le défi photo est
sélectionné, sur le modèle des permissions déjà gérées.

Le cas d'un refus est déjà couvert par la première porte de sortie : repli sur le
calcul mental, décidé avant tout affichage.

---

## Hors périmètre

- Consignes libres du type « photographie quelque chose de rouge » : elles
  supposeraient le juge distant obligatoire, donc le réseau obligatoire.
- Catalogue configurable par l'utilisateur : l'imprévu fait le réveil.
- Détection d'une photo d'écran.
- Détection du lieu : rien ne garantit que l'objet est loin du lit.

---

## Ce qu'il faudra mesurer

Aucune valeur de cette conception n'a été vérifiée sur un appareil. À la recette :

- le seuil de confiance de **0,55** — combien d'essais faut-il pour valider un
  objet réellement présent, dans la lumière d'une chambre au réveil ;
- quels objets du catalogue sont mal reconnus, et doivent en sortir ;
- le temps entre le déclenchement et le verdict ;
- si les huit secondes du juge distant suffisent sur un réseau lent.
