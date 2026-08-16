package com.cocorico.challenge.photo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La machine à états de progression du défi photo : quel objet proposer,
 * combien ont été validés, combien d'essais ratés. Pure, sans caméra ni juge —
 * l'appelant lui fournit déjà le verdict via [soumettre].
 *
 * Une liste vide résout le défi d'emblée plutôt que de bloquer l'utilisateur
 * devant un écran sans objet à photographier : c'est le repli si le catalogue
 * venait, par accident, à ne rien fournir.
 */
class PhotoChallengeEtat(private val objets: List<ObjetPhoto>) {

    private var index = 0

    private val _objetCourant = MutableStateFlow(objets.getOrNull(0))
    val objetCourant: StateFlow<ObjetPhoto?> = _objetCourant.asStateFlow()

    private val _progression = MutableStateFlow(0 to objets.size)
    val progression: StateFlow<Pair<Int, Int>> = _progression.asStateFlow()

    private val _essais = MutableStateFlow(0)
    val essais: StateFlow<Int> = _essais.asStateFlow()

    private val _isSolved = MutableStateFlow(objets.isEmpty())
    val isSolved: StateFlow<Boolean> = _isSolved.asStateFlow()

    /**
     * Enregistre le verdict rendu pour l'objet courant. Renvoie vrai
     * uniquement quand cet appel vient de valider un objet — c'est ce que
     * l'appelant utilise pour réarmer le compte à rebours du volume de
     * l'alarme, exactement comme une répétition de pompes. Un refus, ou un
     * appel après résolution, ne réarme rien.
     */
    fun soumettre(accepte: Boolean): Boolean {
        if (_isSolved.value) return false

        if (!accepte) {
            _essais.value += 1
            return false
        }

        index += 1
        _essais.value = 0
        _progression.value = index to objets.size
        if (index >= objets.size) {
            _isSolved.value = true
            _objetCourant.value = null
        } else {
            _objetCourant.value = objets[index]
        }
        return true
    }
}
