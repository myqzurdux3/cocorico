package com.cocorico.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Statistiques affichées après le réveil. Pur : la série est ce qui donne envie
 * de ne pas la casser demain, elle doit être exacte.
 */
object SerieCalculator {

    /**
     * Nombre de jours consécutifs, en remontant depuis le réveil le plus
     * récent — **à condition que cette suite touche encore le présent**.
     *
     * [aujourdhui] n'est pas un raffinement : sans lui, une série interrompue
     * depuis des semaines continuait de s'afficher comme si elle était en
     * cours, puisque la fonction se contentait de compter des jours contigus
     * quelque part dans l'historique. L'invariant « appelée juste après
     * l'insertion du réveil du jour » n'était écrit nulle part et n'était pas
     * vérifié.
     *
     * La veille est acceptée autant qu'aujourd'hui : le réveil du jour n'a pas
     * forcément encore eu lieu, et une série qui paraîtrait cassée chaque nuit
     * ne récompenserait plus rien.
     */
    fun serie(records: List<WakeRecord>, zone: ZoneId, aujourdhui: LocalDate): Int {
        if (records.isEmpty()) return 0
        val jours: List<LocalDate> = records
            .map { Instant.ofEpochMilli(it.alarmeAt).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()

        if (jours.first() < aujourdhui.minusDays(1)) return 0

        var compte = 1
        for (i in 1 until jours.size) {
            if (jours[i] == jours[i - 1].minusDays(1)) compte++ else break
        }
        return compte
    }

    /**
     * Écart moyen entre le déclenchement et la résolution, en secondes.
     *
     * Applique le **même** filtre de plausibilité que [StatsCalculator] : un
     * réveil laissé ouvert toute la journée, ou un horodatage abîmé, écrasait
     * la moyenne affichée à l'écran de victoire alors que l'écran de
     * statistiques, lui, l'écartait déjà. Deux moyennes du même chiffre ne
     * doivent pas répondre différemment.
     */
    fun retardMoyenSecondes(records: List<WakeRecord>): Int {
        val valides = records
            .map { it.resoluAt - it.alarmeAt }
            .filter { it in StatsCalculator.DUREE_MIN_VALIDE_MS..StatsCalculator.DUREE_MAX_VALIDE_MS }
        if (valides.isEmpty()) return 0
        return (valides.sum() / valides.size / 1000L).toInt()
    }
}
