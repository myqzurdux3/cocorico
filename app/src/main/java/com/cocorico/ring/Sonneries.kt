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

    private val coq = Sonnerie("coq", "Coq du village", R.raw.coq)
    private val reveilMatin = Sonnerie("reveil", "Réveil-matin", R.raw.reveil_matin)
    private val klaxon = Sonnerie("klaxon", "Klaxon d'enfer", R.raw.klaxon)
    private val sirene = Sonnerie("sirene", "Sirène", R.raw.sirene)

    /** Ordonnée de la plus douce à la plus violente : l'écran de choix affiche cet ordre. */
    val toutes = listOf(coq, reveilMatin, klaxon, sirene)

    /**
     * Repli quand la configuration référence un identifiant qui n'existe plus.
     * Nommé, et non désigné par sa position : ce chemin est traversé à chaque
     * réveil, et un indice réordonnerait silencieusement la sonnerie du matin —
     * ou sortirait de la liste si elle raccourcissait. Le klaxon plutôt que le
     * coq, parce qu'une configuration illisible ne doit pas dégrader le réveil.
     */
    val repliIdInconnu = klaxon

    /**
     * Repli quand la source choisie est illisible au moment de sonner : la plus
     * forte, jamais la plus douce. Quelqu'un qui a choisi la sirène l'a choisie
     * parce que le coq ne le réveille pas ; lui substituer le coq en silence,
     * c'est le laisser dormir.
     */
    val repliLaPlusForte = sirene

    /**
     * Entrée synthétique de la sonnerie personnalisée : pas de ressource
     * embarquée (`resId` vaut -1 et ne doit jamais être utilisé), juste de
     * quoi être reconnue par [parId] et affichée dans la liste des choix.
     */
    private val personnalisee = Sonnerie(ID_PERSONNALISEE, "Ma sonnerie", resId = -1, personnalisee = true)

    fun parId(id: String): Sonnerie =
        if (id == ID_PERSONNALISEE) personnalisee else toutes.firstOrNull { it.id == id } ?: repliIdInconnu
}
