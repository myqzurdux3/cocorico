package com.cocorico.challenge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cocorico.data.ChallengeId
import kotlinx.coroutines.flow.StateFlow

data class ChallengeProgress(val done: Int, val total: Int)

/**
 * Contrat commun à tous les défis. Le service d'alarme ne connaît qu'[isSolved] :
 * il ignore tout du contenu du défi. C'est ce qui permet de brancher les pompes
 * et la photo plus tard sans toucher à `alarm/` ni à `ring/`.
 */
interface Challenge {
    val id: ChallengeId
    val isSolved: StateFlow<Boolean>

    /**
     * Fautes commises pendant le défi, telles qu'elles partent dans
     * l'historique. Zéro par défaut : tous les défis n'en comptent pas, et un
     * défi qui n'en compte pas n'en invente pas.
     *
     * Existe parce que `AlarmActivity` reconnaissait les compteurs par le type
     * du défi (`when (challengeFinal) { is MathChallenge -> … }`). Un
     * `DefiCombine` ne correspondant à aucune branche, tout matin en Sur mesure
     * enregistrait zéro faute quel qu'ait été le nombre d'erreurs. Le contrat
     * appartient à l'interface : chaque défi répond pour lui-même, et ajouter
     * un défi ne peut plus faire disparaître un compteur en silence.
     */
    val fautes: Int get() = 0

    @Composable
    fun Content(modifier: Modifier)
}
