package com.cocorico.challenge

import com.cocorico.data.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
    fun `un total inferieur a un est refuse a la construction`() {
        // Le défi de maths est le repli de tous les autres : à total = 0 il rendait
        // ChallengeProgress(0, 0), l'écran calculait 0 / 0 et poussait un NaN dans
        // la barre de progression. Le seul défi toujours disponible ne doit pas
        // pouvoir se casser.
        assertThrows(IllegalArgumentException::class.java) { engine(total = 0) }
        assertThrows(IllegalArgumentException::class.java) { engine(total = -1) }
    }

    @Test
    fun `une bonne reponse apres resolution ne s affiche pas comme une faute`() {
        // Le pavé reste affiché le temps de la bascule vers l'écran de victoire.
        // Un dernier appui sur ✓ était rejeté par le moteur, et l'écran, qui
        // lisait ce refus comme une erreur, affichait « Non. Et le coq a
        // entendu. » sur une réponse pourtant juste.
        val e = engine(total = 1)
        val bonne = e.current.value.answer
        e.submit(bonne)
        assertTrue(e.isSolved.value)
        assertFalse(MathChallengeEngine.estUneFaute(e.isSolved.value, e.submit(bonne)))
    }

    @Test
    fun `une faute n est signalee que sur une reponse fausse et un defi non resolu`() {
        assertTrue(MathChallengeEngine.estUneFaute(dejaResolu = false, reponseJuste = false))
        assertFalse(MathChallengeEngine.estUneFaute(dejaResolu = false, reponseJuste = true))
        assertFalse(MathChallengeEngine.estUneFaute(dejaResolu = true, reponseJuste = false))
        assertFalse(MathChallengeEngine.estUneFaute(dejaResolu = true, reponseJuste = true))
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
