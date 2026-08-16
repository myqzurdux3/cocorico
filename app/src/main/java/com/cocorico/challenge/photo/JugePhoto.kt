package com.cocorico.challenge.photo

import android.graphics.Bitmap

/**
 * Rend un verdict sur une photo : montre-t-elle l'objet demandé ?
 *
 * Deux implémentations existent — la reconnaissance embarquée, toujours
 * présente, et le juge distant, optionnel et éteint par défaut. Le défi ignore
 * laquelle lui répond : c'est ce qui permet de tester sa progression sans
 * caméra, sans modèle et sans réseau.
 *
 * **Contrat impératif : aucune implémentation ne lève d'exception.** Modèle
 * absent, réseau coupé, délai dépassé, réponse illisible — tout se traduit par
 * `false`. Ces classes s'exécutent pendant qu'une alarme hurle à plein volume
 * et qu'un utilisateur debout attend un verdict. Une exception qui remonterait
 * ferait planter l'application devant la sirène, et il ne resterait plus qu'à
 * forcer l'arrêt du téléphone pour la faire taire.
 *
 * Un refus n'est jamais définitif : l'utilisateur reprend une photo.
 */
interface JugePhoto {

    /**
     * L'image reste en mémoire : aucune implémentation ne l'écrit sur le
     * disque, dans aucun mode.
     */
    suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean

    /**
     * Libère ce que l'implémentation détient — un client de reconnaissance
     * garde des ressources natives et fuirait à chaque réveil. Vide par défaut :
     * un juge sans ressource n'a rien à fermer.
     */
    fun fermer() = Unit
}
