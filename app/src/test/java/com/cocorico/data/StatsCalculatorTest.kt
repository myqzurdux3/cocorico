package com.cocorico.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun instant(date: LocalDate, heure: LocalTime = LocalTime.of(6, 30)): Long =
        LocalDateTime.of(date, heure).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        alarme: Long,
        dureeMillis: Long,
        erreurs: Int = 0,
        defi: String = ChallengeId.MATHS.name,
        abandon: Boolean = false,
    ): WakeRecord = WakeRecord(
        id = 0,
        alarmeAt = alarme,
        resoluAt = alarme + dureeMillis,
        erreurs = erreurs,
        triches = 0,
        defi = defi,
        abandon = abandon,
    )

    @Test
    fun `une liste vide rend des statistiques neutres`() {
        val stats = StatsCalculator.calculer(emptyList(), zone)
        assertEquals(0, stats.nombreTotal)
        assertNull(stats.dureeCeMatinSecondes)
        assertEquals(emptyList<Long>(), stats.dureesRecentesSecondes)
        assertNull(stats.dureeMoyenneSecondes)
        assertNull(stats.meilleureDureeSecondes)
        assertNull(stats.pireDureeSecondes)
        assertEquals(0L, stats.dureeCumuleeSecondes)
        assertNull(stats.tauxAbandonPompes)
        assertEquals(0, stats.erreursCumulees)
        assertNull(stats.jourLePlusLent)
        assertNull(stats.progressionSecondes)
    }

    @Test
    fun `un seul reveil valide alimente ce matin le meilleur et le pire`() {
        val alarme = instant(LocalDate.of(2026, 8, 10))
        val stats = StatsCalculator.calculer(listOf(record(alarme, 120_000L)), zone)
        assertEquals(1, stats.nombreTotal)
        assertEquals(120L, stats.dureeCeMatinSecondes)
        assertEquals(listOf(120L), stats.dureesRecentesSecondes)
        assertEquals(120L, stats.dureeMoyenneSecondes)
        assertEquals(120L, stats.meilleureDureeSecondes)
        assertEquals(120L, stats.pireDureeSecondes)
        assertEquals(120L, stats.dureeCumuleeSecondes)
        // Un seul matin, donc c'est trivialement le plus lent — la comparaison
        // n'a besoin que d'une valeur pour être vraie.
        assertEquals(DayOfWeek.MONDAY, stats.jourLePlusLent)
        assertNull(stats.progressionSecondes)
    }

    @Test
    fun `la moyenne le meilleur et le pire ignorent les durees aberrantes`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            // 500 ms : plus rapide qu'un humain ne peut résoudre un calcul, exclu.
            record(base, 500L),
            record(base + 60_000L, 90_000L),
            // Plus d'une heure : le téléphone a probablement traîné, pas l'utilisateur, exclu.
            record(base + 120_000L, 4_000_000L),
        )
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(3, stats.nombreTotal)
        assertEquals(90L, stats.dureeMoyenneSecondes)
        assertEquals(90L, stats.meilleureDureeSecondes)
        assertEquals(90L, stats.pireDureeSecondes)
        assertEquals(90L, stats.dureeCumuleeSecondes)
        // « Ce matin » reste la vérité brute du dernier réveil, même aberrante :
        // c'est une donnée individuelle affichée telle quelle, pas un agrégat.
        assertEquals(4000L, stats.dureeCeMatinSecondes)
    }

    @Test
    fun `un temps negatif est exclu des agregats mais reste compte`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 90_000L),
            // Horloge incohérente : resoluAt avant alarmeAt.
            record(base + 60_000L, -2_000L),
        )
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(2, stats.nombreTotal)
        assertEquals(90L, stats.dureeMoyenneSecondes)
        assertEquals(90L, stats.dureeCumuleeSecondes)
        assertEquals(-2L, stats.dureeCeMatinSecondes)
    }

    @Test
    fun `le temps cumule est la somme des durees valides depuis le debut`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 30_000L),
            record(base + 60_000L, 45_000L),
            record(base + 120_000L, 60_000L),
        )
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(135L, stats.dureeCumuleeSecondes)
    }

    @Test
    fun `le taux de renoncement aux pompes ne compte que les tentatives de pompes`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 60_000L, defi = ChallengeId.POMPES.name, abandon = false),
            record(base + 60_000L, 60_000L, defi = ChallengeId.POMPES.name, abandon = false),
            // Renoncement : le défi final retombe sur les calculs, mais c'était une tentative pompes.
            record(base + 120_000L, 60_000L, defi = ChallengeId.MATHS.name, abandon = true),
            record(base + 180_000L, 60_000L, defi = ChallengeId.MATHS.name, abandon = false),
            record(base + 240_000L, 60_000L, defi = ChallengeId.MATHS.name, abandon = false),
            record(base + 300_000L, 60_000L, defi = ChallengeId.MATHS.name, abandon = false),
        )
        val stats = StatsCalculator.calculer(records, zone)
        // 1 renoncement sur 3 tentatives pompes (2 réussies + 1 renoncée).
        assertEquals(1.0 / 3.0, stats.tauxAbandonPompes!!, 0.0001)
    }

    @Test
    fun `le taux de renoncement est indisponible sans aucune tentative de pompes`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(record(base, 60_000L, defi = ChallengeId.MATHS.name))
        val stats = StatsCalculator.calculer(records, zone)
        assertNull(stats.tauxAbandonPompes)
    }

    @Test
    fun `les erreurs de calcul cumulees additionnent toutes les erreurs`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 60_000L, erreurs = 2),
            // Y compris sur un réveil à la durée aberrante : le compteur d'erreurs
            // n'est pas un agrégat de durée, il n'a aucune raison d'être filtré.
            record(base + 60_000L, 4_000_000L, erreurs = 3),
        )
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(5, stats.erreursCumulees)
    }

    @Test
    fun `la liste des reveils recents se limite aux sept derniers dans l ordre chronologique`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val records = (0 until 9).map { i ->
            record(base + i * 3_600_000L, (i + 1) * 10_000L)
        }
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(listOf(30L, 40L, 50L, 60L, 70L, 80L, 90L), stats.dureesRecentesSecondes)
    }

    @Test
    fun `le jour de la semaine le plus lent est celui a la moyenne la plus longue`() {
        val records = listOf(
            // Lundi : moyenne de 60 s.
            record(instant(LocalDate.of(2026, 8, 10)), 60_000L),
            record(instant(LocalDate.of(2026, 8, 17)), 60_000L),
            // Mercredi : moyenne de 200 s, plus lente.
            record(instant(LocalDate.of(2026, 8, 12)), 200_000L),
        )
        val stats = StatsCalculator.calculer(records, zone)
        assertEquals(DayOfWeek.WEDNESDAY, stats.jourLePlusLent)
    }

    @Test
    fun `la progression compare les cinq premiers et les cinq derniers reveils valides`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val premiers = (0 until 5).map { i -> record(base + i * 3_600_000L, 200_000L) }
        val derniers = (5 until 10).map { i -> record(base + i * 3_600_000L, 100_000L) }
        val stats = StatsCalculator.calculer(premiers + derniers, zone)
        // Négatif : les cinq derniers matins sont plus rapides que les cinq premiers.
        assertEquals(-100L, stats.progressionSecondes)
    }

    @Test
    fun `la progression est indisponible en dessous de dix reveils valides`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val records = (0 until 9).map { i -> record(base + i * 3_600_000L, 100_000L) }
        val stats = StatsCalculator.calculer(records, zone)
        assertNull(stats.progressionSecondes)
    }
}
