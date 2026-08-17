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

    @Composable
    fun Content(modifier: Modifier)
}
