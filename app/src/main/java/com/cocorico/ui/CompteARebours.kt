package com.cocorico.ui

import java.time.Duration
import java.time.LocalDateTime

/**
 * Formate le délai avant le prochain réveil. Classe pure — l'instant courant
 * est fourni par l'appelant, donc tout est vérifiable en test unitaire.
 *
 * L'accueil affichait « Réveil dans -2 min » : l'occurrence n'était recalculée
 * qu'au changement de réglage, et la soustraction devenait négative dès que
 * l'heure prévue était passée — pendant que l'alarme sonnait, ou juste avant
 * que la replanification n'ait eu lieu. Un compte à rebours négatif donne
 * l'impression que le réveil a été raté, ce qui est exactement l'inverse de
 * ce que l'application doit inspirer.
 *
 * Deux corrections : le cas passé a maintenant son propre libellé, et
 * l'appelant rafraîchit l'instant régulièrement au lieu de le figer à la
 * composition.
 */
object CompteARebours {

    /**
     * [cible] à `null` signifie qu'aucune occurrence n'est programmée.
     *
     * Une cible déjà passée n'est pas une erreur : entre la sonnerie et la
     * replanification, c'est l'état normal. On l'annonce comme imminent plutôt
     * que d'afficher un délai faux.
     */
    fun libelle(depuis: LocalDateTime, cible: LocalDateTime?): String {
        if (cible == null) return SANS_OCCURRENCE
        val duree = Duration.between(depuis, cible)
        if (duree.isZero || duree.isNegative) return IMMINENT

        val heures = duree.toHours()
        val minutes = duree.toMinutes() % 60
        return when {
            heures > 0 -> "Réveil dans $heures h $minutes min"
            minutes > 0 -> "Réveil dans $minutes min"
            // Sous la minute, « 0 min » se lirait comme un compteur bloqué.
            else -> "Réveil dans moins d'une minute"
        }
    }

    /**
     * Vrai quand l'occurrence affichée est périmée et doit être recalculée.
     * Séparé de [libelle] pour que l'écran décide quand recharger sans avoir à
     * interpréter une chaîne de caractères.
     */
    fun estPerimee(depuis: LocalDateTime, cible: LocalDateTime?): Boolean =
        cible != null && !cible.isAfter(depuis)

    const val SANS_OCCURRENCE = "Aucun jour actif. Le coq dort."
    const val IMMINENT = "Réveil imminent."
}
