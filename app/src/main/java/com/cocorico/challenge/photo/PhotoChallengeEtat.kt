package com.cocorico.challenge.photo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La machine à états de progression du défi photo : quel objet proposer,
 * combien ont été validés, combien d'essais ratés. Pure, sans caméra ni juge —
 * l'appelant lui fournit déjà le verdict via [soumettre].
 *
 * **Au moins un objet est exigé.** Une liste vide était auparavant lue comme
 * un défi déjà résolu : l'alarme s'arrêtait sans qu'aucune photo n'ait été
 * prise, c'est-à-dire exactement ce que ce défi existe pour empêcher. Aucun
 * appelant ne peut plus en produire une — [CatalogueObjets.tirer] rend
 * toujours au moins un objet — mais rien ne le maintenait, et le refus
 * ci-dessous le maintient. Le repli qui protège l'utilisateur d'un écran sans
 * objet ne vit pas ici : il vit dans [PhotoChallenge], qui garantit une liste
 * non vide et laisse de toute façon basculer sur le calcul mental.
 */
class PhotoChallengeEtat(private val objets: List<ObjetPhoto>) {

    init {
        require(objets.isNotEmpty()) {
            "Un défi photo sans objet s'arrêterait sans qu'aucune photo soit prise."
        }
    }

    private var index = 0

    private val _objetCourant = MutableStateFlow(objets.getOrNull(0))
    val objetCourant: StateFlow<ObjetPhoto?> = _objetCourant.asStateFlow()

    private val _progression = MutableStateFlow(0 to objets.size)
    val progression: StateFlow<Pair<Int, Int>> = _progression.asStateFlow()

    /** Essais ratés sur l'objet courant. Remis à zéro à chaque objet validé. */
    private val _essais = MutableStateFlow(0)
    val essais: StateFlow<Int> = _essais.asStateFlow()

    /**
     * Essais ratés depuis le début du réveil, tous objets confondus. Distinct
     * d'[essais], qui décrit l'objet courant et redescend à chaque validation :
     * l'historique a besoin du total de la session, et le lire sur [essais]
     * donnerait toujours le compte du dernier objet — donc zéro sur un réveil
     * réussi du premier coup.
     */
    private val _essaisTotal = MutableStateFlow(0)
    val essaisTotal: StateFlow<Int> = _essaisTotal.asStateFlow()

    // Faux d'emblée, sans condition : la liste ne peut pas être vide, et rien
    // ne doit pouvoir déclarer le défi résolu avant une première soumission.
    private val _isSolved = MutableStateFlow(false)
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
            _essaisTotal.value += 1
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
