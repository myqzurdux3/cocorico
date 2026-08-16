package com.cocorico.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Statistiques affichées après le réveil. Pur : la série est ce qui donne envie
 * de ne pas la casser demain, elle doit être exacte.
 */
object SerieCalculator {

    /** Nombre de jours consécutifs, en remontant depuis le réveil le plus récent. */
    fun serie(records: List<WakeRecord>, zone: ZoneId): Int {
        if (records.isEmpty()) return 0
        val jours: List<LocalDate> = records
            .map { Instant.ofEpochMilli(it.alarmeAt).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()

        var compte = 1
        for (i in 1 until jours.size) {
            if (jours[i] == jours[i - 1].minusDays(1)) compte++ else break
        }
        return compte
    }

    /** Écart moyen entre le déclenchement et la résolution, en secondes. */
    fun retardMoyenSecondes(records: List<WakeRecord>): Int {
        if (records.isEmpty()) return 0
        val total = records.sumOf { it.resoluAt - it.alarmeAt }
        return (total / records.size / 1000L).toInt()
    }
}
