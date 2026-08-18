package com.cocorico.challenge.combine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cocorico.challenge.Challenge
import com.cocorico.data.ChallengeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Un matin en Sur mesure enregistrait **toujours zéro faute** : `AlarmActivity`
 * reconnaissait les fautes par le type du défi, et un [DefiCombine] n'est ni un
 * défi de calculs ni un défi photo. Toutes les erreurs commises à l'intérieur
 * de la séquence étaient donc perdues pour les statistiques. Constaté sur
 * l'appareil le 18 août 2026.
 */
class FautesCombineTest {

    private class EpreuveFactice(override val id: ChallengeId, override val fautes: Int) : Challenge {
        override val isSolved: StateFlow<Boolean> = MutableStateFlow(false)

        @Composable
        override fun Content(modifier: Modifier) = Unit
    }

    private fun defi(etapes: List<EtapeCombine>, fautesParEpreuve: Int) = DefiCombine(etapes) { etape, _ ->
        EpreuveFactice(etape.type, fautesParEpreuve)
    }

    @Test
    fun `sans epreuve terminee, aucune faute`() {
        val combine = defi(listOf(EtapeCombine(ChallengeId.MATHS, 2)), fautesParEpreuve = 3)
        assertEquals(0, combine.fautes)
    }

    @Test
    fun `les fautes de chaque epreuve terminee s'additionnent`() {
        val etapes = listOf(
            EtapeCombine(ChallengeId.MATHS, 2),
            EtapeCombine(ChallengeId.PHOTO, 1),
        )
        val combine = defi(etapes, fautesParEpreuve = 4)

        combine.avancer(EpreuveFactice(ChallengeId.MATHS, fautes = 4))
        assertEquals(4, combine.fautes)

        combine.avancer(EpreuveFactice(ChallengeId.PHOTO, fautes = 4))
        assertEquals(8, combine.fautes)
    }

    /** Une épreuve qui n'en compte pas ne doit rien retirer au total. */
    @Test
    fun `une epreuve sans compteur laisse le total intact`() {
        val combine = defi(listOf(EtapeCombine(ChallengeId.POMPES, 5)), fautesParEpreuve = 0)
        combine.avancer(EpreuveFactice(ChallengeId.POMPES, fautes = 0))
        assertEquals(0, combine.fautes)
    }

    /** Le défaut de l'interface : tous les défis ne comptent pas de fautes. */
    @Test
    fun `un defi qui ne compte rien rend zero`() {
        val muet = object : Challenge {
            override val id = ChallengeId.POMPES
            override val isSolved: StateFlow<Boolean> = MutableStateFlow(false)

            @Composable
            override fun Content(modifier: Modifier) = Unit
        }
        assertEquals(0, muet.fautes)
    }
}
