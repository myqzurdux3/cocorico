package com.cocorico.data

import java.time.DayOfWeek

enum class ChallengeId { MATHS, POMPES, PHOTO }

enum class Difficulty { FACILE, MOYEN, DIFFICILE }

/**
 * La configuration unique de l'application. Il n'existe qu'une alarme : ce modèle
 * est un singleton persisté, pas un élément de liste.
 */
data class AlarmConfig(
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val ringtoneId: String,
    val challengeId: ChallengeId,
    val difficulty: Difficulty,
    val armed: Boolean,
    /**
     * Juge distant du défi photo : envoie la photo à un serveur tiers quand la
     * reconnaissance embarquée a refusé. Éteint par défaut, et rien ne doit
     * l'activer sauf un geste explicite de l'utilisateur sur l'écran de réglages.
     */
    val iaDistanteActive: Boolean = false,
    /** Clé d'API du juge distant, fournie par l'utilisateur. Jamais livrée avec l'application. */
    val cleApi: String = "",
) {
    companion object {
        val DEFAULT = AlarmConfig(
            hour = 6,
            minute = 30,
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            ringtoneId = "klaxon",
            challengeId = ChallengeId.MATHS,
            difficulty = Difficulty.MOYEN,
            armed = false,
            iaDistanteActive = false,
            cleApi = "",
        )
    }
}
