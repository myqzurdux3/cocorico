package com.cocorico.challenge

import com.cocorico.data.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathProblemGeneratorTest {

    private val generator = MathProblemGenerator(Random(1234))

    /** Réévalue l'énoncé pour vérifier que la réponse annoncée est la bonne. */
    private fun evaluate(prompt: String): Int {
        val parts = prompt.split(" ")
        val left = parts[0].toInt()
        val right = parts[2].toInt()
        return when (parts[1]) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            else -> throw IllegalArgumentException("opérateur inconnu dans « $prompt »")
        }
    }

    @Test
    fun `la reponse annoncee correspond toujours a l enonce`() {
        Difficulty.entries.forEach { difficulty ->
            repeat(500) {
                val problem = generator.generate(difficulty)
                assertEquals(problem.prompt, evaluate(problem.prompt), problem.answer)
            }
        }
    }

    @Test
    fun `le niveau facile ne produit jamais de resultat negatif`() {
        repeat(500) {
            val problem = generator.generate(Difficulty.FACILE)
            assertTrue(problem.prompt, problem.answer >= 0)
        }
    }

    @Test
    fun `le niveau moyen multiplie un nombre a deux chiffres par un chiffre`() {
        repeat(500) {
            val problem = generator.generate(Difficulty.MOYEN)
            val parts = problem.prompt.split(" ")
            assertEquals("×", parts[1])
            assertTrue(problem.prompt, parts[0].toInt() in 11..99)
            assertTrue(problem.prompt, parts[2].toInt() in 2..9)
        }
    }

    @Test
    fun `le niveau difficile multiplie deux nombres a deux chiffres`() {
        repeat(500) {
            val problem = generator.generate(Difficulty.DIFFICILE)
            val parts = problem.prompt.split(" ")
            assertEquals("×", parts[1])
            assertTrue(problem.prompt, parts[0].toInt() in 11..99)
            assertTrue(problem.prompt, parts[2].toInt() in 11..99)
        }
    }

    @Test
    fun `deux generateurs de meme graine produisent la meme suite`() {
        val a = MathProblemGenerator(Random(7))
        val b = MathProblemGenerator(Random(7))
        repeat(20) {
            assertEquals(a.generate(Difficulty.MOYEN), b.generate(Difficulty.MOYEN))
        }
    }
}
