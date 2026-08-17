package com.cocorico.ring

import kotlin.math.roundToInt

/**
 * Traduit le plafond sonore choisi par l'utilisateur en niveaux concrets du
 * flux d'alarme. Classe pure, sans dépendance Android : l'appelant fournit le
 * maximum de l'appareil, qui varie d'un modèle à l'autre — 7 crans sur
 * certains, 15 sur d'autres.
 *
 * Le réglage existe parce que le maximum d'un téléphone peut être douloureux
 * au réveil. Mais Cocorico ne promet qu'une chose : réveiller. Le plafond est
 * donc borné par le bas — voir [POURCENT_MINIMAL] — et cette borne est
 * appliquée **ici**, dans le calcul, pas seulement dans le curseur qui
 * l'affiche. Le réglage est persisté : une valeur corrompue, écrite par une
 * version antérieure ou par accident, ne doit jamais pouvoir produire une
 * alarme qu'on n'entend pas.
 */
object NiveauxVolume {

    /**
     * Sous ce plafond, l'alarme cesserait d'être une alarme. C'est une
     * décision produit, pas une limite technique.
     */
    const val POURCENT_MINIMAL = 50

    const val POURCENT_MAXIMAL = 100

    /** Le pourcentage réellement appliqué, quoi qu'on lui donne. */
    fun normaliser(pourcent: Int): Int = pourcent.coerceIn(POURCENT_MINIMAL, POURCENT_MAXIMAL)

    /**
     * Le niveau de la sonnerie à pleine puissance, une fois le plafond
     * appliqué. Jamais nul : une alarme silencieuse est le seul échec que ce
     * produit n'a pas le droit de commettre.
     */
    fun plein(maxAppareil: Int, pourcent: Int): Int {
        if (maxAppareil <= 0) return 0
        val vise = (maxAppareil * normaliser(pourcent) / 100f).roundToInt()
        return vise.coerceIn(1, maxAppareil)
    }

    /**
     * Le niveau une fois le téléphone pris en main, proportionnel au plafond
     * choisi — quelqu'un qui a refusé le maximum de son téléphone ne doit pas
     * retrouver une baisse calculée sur ce maximum.
     *
     * Garantie tenue par [FRACTION_BAISSE] et par le plafonnement à `plein - 1` :
     * la baisse reste **strictement** sous le plein. Sans cette borne, sur un
     * flux à deux ou trois crans les deux niveaux se confondraient, prendre le
     * téléphone ne baisserait plus rien, et rien dans l'application ne le
     * signalerait.
     *
     * Cas dégénéré assumé : si le plein vaut déjà 1, il n'existe aucun niveau
     * audible en dessous, et la baisse vaut 1 elle aussi.
     */
    fun baisse(maxAppareil: Int, pourcent: Int): Int {
        val plein = plein(maxAppareil, pourcent)
        if (plein <= 1) return plein
        val vise = (plein * FRACTION_BAISSE).roundToInt()
        return vise.coerceIn(1, plein - 1)
    }

    /**
     * Ce que la jauge de l'écran d'alarme annonce à pleine puissance : le
     * plafond lui-même. Elle affichait « 100 % » en dur, donc contredisait
     * ouvertement le réglage dès que l'utilisateur descendait le plafond.
     */
    fun pourcentAffichePlein(pourcent: Int): Int = normaliser(pourcent)

    /**
     * Ce que la jauge annonce téléphone en main : la même part du plafond que
     * celle appliquée par [baisse].
     *
     * Nominal, et pas au cran près : le flux d'alarme est quantifié, et le
     * niveau réellement posé est arrondi. Annoncer la valeur nominale reste
     * plus juste que l'ancien « 30 % » figé, qui se rapportait à un maximum
     * d'appareil que l'utilisateur venait justement de refuser.
     */
    fun pourcentAfficheBaisse(pourcent: Int): Int = (normaliser(pourcent) * FRACTION_BAISSE).roundToInt()

    /** Assez bas pour se sentir, assez haut pour rester audible d'un lit. */
    private const val FRACTION_BAISSE = 0.3f
}
