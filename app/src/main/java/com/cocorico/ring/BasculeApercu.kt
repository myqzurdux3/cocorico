package com.cocorico.ring

/**
 * Ce que doit faire un appui sur une ligne de l'écran des sonneries, selon ce
 * qui est déjà en train d'être écouté.
 *
 * Pur, sans import `android.*` : la règle est courte mais elle décide de ce que
 * l'utilisateur entend, et une règle qu'on ne peut vérifier qu'en appuyant sur
 * un téléphone n'est pas vérifiée.
 */
object BasculeApercu {

    enum class Bascule { JOUER, ARRETER }

    /**
     * [enCours] est l'identifiant de la sonnerie dont l'extrait joue en ce
     * moment, ou `null` si rien ne joue — y compris quand l'extrait s'est
     * terminé tout seul.
     *
     * **Le choix de la sonnerie ne dépend pas de cette décision.** Appuyer une
     * seconde fois coupe le son sans désélectionner : l'utilisateur qui vient
     * de choisir sa sonnerie et veut simplement le silence ne doit pas perdre
     * son choix en même temps.
     */
    fun decider(enCours: String?, demande: String): Bascule = if (enCours == demande) Bascule.ARRETER else Bascule.JOUER
}
