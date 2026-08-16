package com.cocorico.challenge

import com.cocorico.data.Difficulty
import kotlin.random.Random

data class MathProblem(val prompt: String, val answer: Int)

/**
 * Génère les calculs du défi. Le séparateur est l'espace insécable normal et le
 * signe moins est U+2212 : les tests réévaluent l'énoncé, donc le format compte.
 */
class MathProblemGenerator(private val random: Random = Random.Default) {

    fun generate(difficulty: Difficulty): MathProblem = when (difficulty) {
        Difficulty.FACILE -> facile()
        Difficulty.MOYEN -> {
            val a = random.nextInt(11, 100)
            val b = random.nextInt(2, 10)
            MathProblem("$a × $b", a * b)
        }
        Difficulty.DIFFICILE -> {
            val a = random.nextInt(11, 100)
            val b = random.nextInt(11, 100)
            MathProblem("$a × $b", a * b)
        }
    }

    private fun facile(): MathProblem {
        val a = random.nextInt(10, 100)
        val b = random.nextInt(10, 100)
        return if (random.nextBoolean()) {
            MathProblem("$a + $b", a + b)
        } else {
            val haut = maxOf(a, b)
            val bas = minOf(a, b)
            MathProblem("$haut − $bas", haut - bas)
        }
    }
}
