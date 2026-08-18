package com.cocorico.challenge.combine

import com.cocorico.data.ChallengeId

/**
 * Où l'on en est dans la suite d'épreuves du défi sur mesure.
 *
 * Immuable : chaque transition rend un nouvel état. C'est ce qui la rend
 * vérifiable sans téléphone, et c'est ici que se décide si l'alarme continue
 * ou s'arrête.
 */
data class EtatSequence(val etapes: List<EtapeCombine>, val index: Int = 0) {

    init {
        // Une suite vide serait terminée d'emblée : l'alarme s'arrêterait sans
        // rien avoir demandé. `EtapesCombine.assainir` l'empêche déjà en amont ;
        // ce refus est la ceinture qui accompagne les bretelles, parce que la
        // conséquence — un réveil raté — ne se rattrape pas.
        require(etapes.isNotEmpty()) { "une suite d'épreuves ne peut pas être vide" }
    }

    val total: Int get() = etapes.size

    /** Rang affiché, à partir de 1 : « épreuve 2 sur 3 ». */
    val numero: Int get() = index + 1

    val estTerminee: Boolean get() = index >= etapes.size

    val courante: EtapeCombine
        get() {
            check(!estTerminee) { "la suite est terminée, il n'y a plus d'épreuve" }
            return etapes[index]
        }

    /** L'épreuve en cours est résolue : on passe à la suivante. */
    fun suivante(): EtatSequence = copy(index = index + 1)

    /**
     * Remplace l'épreuve en cours par le même nombre de calculs, sans avancer.
     *
     * Deux situations mènent ici, et c'est délibérément la même réponse :
     * l'utilisateur renonce à l'épreuve, ou celle-ci s'avère impossible au
     * réveil (pas de caméra, pas de clé, pas de capteur). Dans les deux cas le
     * réveil garde une charge comparable et la suite continue — un bras bloqué
     * ne doit pas annuler la photo qui vient après.
     */
    fun remplacerParCalculs(): EtatSequence {
        if (estTerminee || courante.type == ChallengeId.MATHS) return this
        val remplacees = etapes.toMutableList()
        remplacees[index] = EtapeCombine(ChallengeId.MATHS, courante.nombre)
        return copy(etapes = remplacees)
    }
}
