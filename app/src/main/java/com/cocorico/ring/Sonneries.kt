package com.cocorico.ring

import com.cocorico.R

object Sonneries {

    data class Sonnerie(
        val id: String,
        val nom: String,
        val resId: Int,
        /**
         * Vrai pour l'unique entrée représentant le fichier importé par
         * l'utilisateur. `resId` n'a alors aucun sens : la source réelle est
         * l'URI persistée dans `SonneriePersonnaliseeStore`, lue par
         * [RingtonePlayer] au moment de sonner — voir
         * [SonneriePersonnaliseeLogique.sourceAJouer].
         */
        val personnalisee: Boolean = false,
    )

    /**
     * Identifiant réservé à la sonnerie personnalisée. Jamais un identifiant
     * de sonnerie embarquée : le distinguer par un préfixe serait plus
     * fragile qu'une constante partagée par tout le code qui doit reconnaître
     * ce cas particulier (écran de choix, lecteur d'alarme).
     */
    const val ID_PERSONNALISEE = "personnalisee"

    val toutes = listOf(
        Sonnerie("coq", "Coq du village", R.raw.coq),
        Sonnerie("reveil", "Réveil-matin", R.raw.reveil_matin),
        Sonnerie("klaxon", "Klaxon d'enfer", R.raw.klaxon),
        Sonnerie("sirene", "Sirène", R.raw.sirene),
    )

    /**
     * Entrée synthétique de la sonnerie personnalisée : pas de ressource
     * embarquée (`resId` vaut -1 et ne doit jamais être utilisé), juste de
     * quoi être reconnue par [parId] et affichée dans la liste des choix.
     */
    private val personnalisee = Sonnerie(ID_PERSONNALISEE, "Ma sonnerie", resId = -1, personnalisee = true)

    fun parId(id: String): Sonnerie =
        if (id == ID_PERSONNALISEE) personnalisee else toutes.firstOrNull { it.id == id } ?: toutes[2]
}
