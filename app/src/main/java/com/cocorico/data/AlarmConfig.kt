package com.cocorico.data

import com.cocorico.challenge.photo.CatalogueObjets
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
     * Plafond sonore, en pourcentage du maximum de l'appareil. Borné par le
     * bas dans [com.cocorico.ring.NiveauxVolume] : un réveil doit rester un
     * réveil.
     */
    val volumeMaxPourcent: Int = 100,
    /** Clé d'API du juge distant, fournie par l'utilisateur. Jamais livrée avec l'application. */
    val cleApi: String = "",
    /**
     * Identifiants des objets que le tirage du défi photo peut piocher,
     * cochés depuis l'écran de sélection des pièces. Des identifiants, pas
     * des index : le catalogue évolue, et un index se déplacerait sous les
     * pieds d'une sélection déjà persistée.
     *
     * Une sélection vide n'est pas empêchée par l'écran de sélection — voir
     * `SelectionObjetsScreen` — et n'a pas besoin de l'être :
     * [CatalogueObjets.tirer] s'en accommode déjà en repliant sur le
     * catalogue entier plutôt que de bloquer le tirage, exactement comme il
     * ignore déjà un identifiant persisté qui n'existe plus dans le
     * catalogue. Rester bloqué devant une sirène est pire que photographier
     * un objet que l'utilisateur n'a pas explicitement coché.
     *
     * La valeur par défaut du paramètre (un ensemble vide) n'est là que pour
     * des constructions ponctuelles, en test notamment ; [DEFAULT] ci-dessous
     * coche tout le catalogue à l'installation, pour que le comportement
     * d'aujourd'hui — piocher dans tout le catalogue — soit conservé sans
     * configuration.
     */
    val objetsSelectionnes: Set<String> = emptySet(),
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
            cleApi = "",
            objetsSelectionnes = CatalogueObjets.tous.map { it.id }.toSet(),
        )
    }
}
