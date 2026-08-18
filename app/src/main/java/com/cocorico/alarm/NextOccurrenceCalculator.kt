package com.cocorico.alarm

import com.cocorico.data.AlarmConfig
import java.time.LocalDateTime

/**
 * Calcul pur de la prochaine sonnerie. Aucune dépendance Android : c'est le cœur
 * testable de la planification.
 */
object NextOccurrenceCalculator {

    /**
     * Renvoie le prochain instant de sonnerie strictement postérieur à [from],
     * ou null si aucun jour n'est actif.
     *
     * On balaie 8 jours : le 8e couvre le cas d'un seul jour actif, où la
     * prochaine occurrence tombe la semaine suivante.
     */
    fun next(config: AlarmConfig, from: LocalDateTime): LocalDateTime? {
        if (config.days.isEmpty()) return null
        for (offset in 0L..7L) {
            val date = from.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in config.days) continue
            val candidate = date.atTime(config.hour, config.minute)
            if (candidate.isAfter(from)) return candidate
        }
        return null
    }
}
