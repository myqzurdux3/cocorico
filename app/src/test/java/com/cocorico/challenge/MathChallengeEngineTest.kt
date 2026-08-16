package com.cocorico.challenge

import com.cocorico.data.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeEngineTest {

    private fun engine(total: Int = 3) = MathChallengeEngine(
        generator = MathProblemGenerator(Random(99)),
        difficulty = Difficulty.MOYEN,
        total = total,
    )

    @Test
    fun `demarre non resolu avec zero progression`() {
        val e = engine()
        assertFalse(e.isSolved.value)
        assertEquals(ChallengeProgress(done = 0, total = 3), e.progress.value)
    }

    @Test
    fun `une bonne reponse fait avancer et change de probleme`() {
        val e = engine()
        val premier = e.current.value
        assertTrue(e.submit(premier.answer))
        assertEquals(1, e.progress.value.done)
        assertNotEquals(premier, e.current.value)
    }

    @Test
    fun `trois bonnes reponses resolvent le defi`() {
        val e = engine()
        repeat(3) { e.submit(e.current.value.answer) }
        assertTrue(e.isSolved.value)
        assertEquals(ChallengeProgress(done = 3, total = 3), e.progress.value)
    }

    @Test
    fun `une mauvaise reponse compte une erreur sans faire avancer`() {
        val e = engine()
        val avant = e.current.value
        assertFalse(e.submit(avant.answer + 1))
        assertEquals(0, e.progress.value.done)
        assertEquals(1, e.erreurs.value)
    }

    @Test
    fun `une mauvaise reponse regenere un probleme`() {
        val e = engine()
        val avant = e.current.value
        e.submit(avant.answer + 1)
        assertNotEquals(avant, e.current.value)
    }

    @Test
    fun `le probleme resolu reste affiche apres la resolution`() {
        // Verrouille l'ordre dans submit() : sur la bonne réponse finale, l'état
        // passe à résolu et la fonction sort AVANT de régénérer. Sans ça, l'écran
        // de victoire afficherait brièvement un nouveau calcul.
        val e = engine(total = 1)
        val dernier = e.current.value
        e.submit(dernier.answer)
        assertEquals(dernier, e.current.value)
    }

    @Test
    fun `soumettre apres resolution ne change plus rien`() {
        val e = engine(total = 1)
        e.submit(e.current.value.answer)
        val fige = e.current.value
        assertFalse(e.submit(fige.answer))
        assertEquals(ChallengeProgress(done = 1, total = 1), e.progress.value)
        assertEquals(0, e.erreurs.value)
        assertEquals(fige, e.current.value)
    }
}
