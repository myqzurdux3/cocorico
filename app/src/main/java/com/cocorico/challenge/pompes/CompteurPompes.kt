package com.cocorico.challenge.pompes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compte les pompes à partir du flux de capteurs. Sans horloge interne ni
 * dépendance Android : l'appelant fournit l'instant, ce qui rend la classe
 * entièrement testable.
 *
 * Une répétition est comptée à la REMONTÉE, pas à la descente : descendre puis
 * abandonner ne marque rien.
 */
class CompteurPompes(private val total: Int) {

    private val _etat = MutableStateFlow(EtatPompes.ATTENTE_POSITION)
    val etat: StateFlow<EtatPompes> = _etat.asStateFlow()

    private val _comptees = MutableStateFlow(0)
    val comptees: StateFlow<Int> = _comptees.asStateFlow()

    private val _isSolved = MutableStateFlow(false)
    val isSolved: StateFlow<Boolean> = _isSolved.asStateFlow()

    /**
     * Instant du dernier échantillon reçu en position haute. Rafraîchi à
     * chaque échantillon haut tant qu'on reste en [EtatPompes.PRET], pas
     * seulement à l'entrée dans cet état — c'est ce rafraîchissement continu
     * qui évite qu'une pause prolongée en haut (main immobile, avant de
     * repartir) fasse dépasser [DUREE_DEPUIS_DERNIER_HAUT_MAX_MS] à la
     * répétition suivante. Conséquence : cette référence est presque
     * toujours prise juste avant la descente, donc la durée mesurée depuis
     * elle est en pratique la tenue basse, pas la durée d'un cycle complet —
     * voir [DUREE_DEPUIS_DERNIER_HAUT_MIN_MS]/[DUREE_DEPUIS_DERNIER_HAUT_MAX_MS].
     */
    private var debutTenueHaute = 0L

    /** Instant d'entrée en position basse. */
    private var debutBas = 0L

    /**
     * Renvoie true si cet échantillon vient de valider une répétition. L'appelant
     * s'en sert pour réarmer le compte à rebours d'inactivité, exactement comme
     * une frappe sur le pavé numérique du défi maths.
     */
    fun onEchantillon(e: EchantillonPompe): Boolean {
        if (_isSolved.value) return false

        if (!positionValide(e)) {
            _etat.value = EtatPompes.ATTENTE_POSITION
            debutBas = 0L
            return false
        }

        return when (_etat.value) {
            EtatPompes.ATTENTE_POSITION -> {
                // On ne reprend qu'en position haute, pour ne pas compter une
                // répétition dont on a manqué la descente.
                if (!e.procheDuCapteur) {
                    _etat.value = EtatPompes.PRET
                    debutTenueHaute = e.tMillis
                }
                false
            }

            EtatPompes.PRET -> {
                if (e.procheDuCapteur) {
                    _etat.value = EtatPompes.BAS
                    debutBas = e.tMillis
                } else {
                    debutTenueHaute = e.tMillis
                }
                false
            }

            EtatPompes.BAS -> {
                if (e.procheDuCapteur) return false
                _etat.value = EtatPompes.PRET
                val tenueBasse = e.tMillis - debutBas
                val depuisDernierHaut = e.tMillis - debutTenueHaute
                debutTenueHaute = e.tMillis
                val valide = tenueBasse >= TENUE_BASSE_MIN_MS &&
                    depuisDernierHaut in DUREE_DEPUIS_DERNIER_HAUT_MIN_MS..DUREE_DEPUIS_DERNIER_HAUT_MAX_MS
                if (valide) compter() else false
            }
        }
    }

    private fun compter(): Boolean {
        val n = _comptees.value + 1
        _comptees.value = n
        if (n >= total) _isSolved.value = true
        return true
    }

    /** Téléphone posé à plat et immobile : ni tenu en main, ni agité. */
    private fun positionValide(e: EchantillonPompe): Boolean =
        e.inclinaisonDegres <= INCLINAISON_MAX_DEG && e.ecartGravite <= ECART_MAX

    private companion object {
        /** Au-delà, le téléphone est tenu plutôt que posé. */
        const val INCLINAISON_MAX_DEG = 15f

        /** Au-delà, le téléphone bouge : on ne compte pas. */
        const val ECART_MAX = 1.5f

        /**
         * Bornes de la durée écoulée depuis le dernier échantillon haut
         * jusqu'à la remontée. Comme [debutTenueHaute] est rafraîchi à chaque
         * échantillon haut tant qu'on reste en position haute, cette
         * référence est presque toujours prise juste avant la descente — ces
         * bornes contraignent donc en pratique la tenue basse elle-même, pas
         * la durée d'un cycle complet montée-descente-remontée. Le nom le dit
         * maintenant explicitement.
         *
         * Plus court, c'est une main qui passe devant le capteur.
         */
        const val DUREE_DEPUIS_DERNIER_HAUT_MIN_MS = 600L

        /** Plus long, ce n'est plus une pompe. */
        const val DUREE_DEPUIS_DERNIER_HAUT_MAX_MS = 8_000L

        /** Temps minimum en position basse, contre l'effleurement. */
        const val TENUE_BASSE_MIN_MS = 150L
    }
}
