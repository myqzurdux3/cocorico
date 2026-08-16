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

    /** Début du cycle courant, c'est-à-dire dernier passage en position haute. */
    private var debutCycle = 0L

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
                    debutCycle = e.tMillis
                }
                false
            }

            EtatPompes.PRET -> {
                if (e.procheDuCapteur) {
                    _etat.value = EtatPompes.BAS
                    debutBas = e.tMillis
                } else {
                    debutCycle = e.tMillis
                }
                false
            }

            EtatPompes.BAS -> {
                if (e.procheDuCapteur) return false
                _etat.value = EtatPompes.PRET
                val tenueBasse = e.tMillis - debutBas
                val cycle = e.tMillis - debutCycle
                debutCycle = e.tMillis
                val valide = tenueBasse >= TENUE_BASSE_MIN_MS &&
                    cycle in CYCLE_MIN_MS..CYCLE_MAX_MS
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

        /** Plus court, c'est une main qui passe devant le capteur. */
        const val CYCLE_MIN_MS = 600L

        /** Plus long, ce n'est plus une pompe. */
        const val CYCLE_MAX_MS = 8_000L

        /** Temps minimum en position basse, contre l'effleurement. */
        const val TENUE_BASSE_MIN_MS = 150L
    }
}
