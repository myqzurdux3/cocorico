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

    /**
     * Jour de référence des calculs : celui du réveil le plus récent. C'est la
     * situation réelle — l'écran de statistiques s'ouvre après un réveil — et
     * la passer explicitement évite que ces tests dépendent de la date du jour
     * où on les exécute.
     */
    private fun jourDe(records: List<WakeRecord>): LocalDate =
        java.time.Instant.ofEpochMilli(records.last().alarmeAt).atZone(zone).toLocalDate()

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
        defi = defi,
        abandon = abandon,
    )

    @Test
    fun `une liste vide rend des statistiques neutres`() {
        val stats = StatsCalculator.calculer(emptyList(), zone, LocalDate.of(2026, 8, 17))
        assertEquals(0, stats.nombreTotal)
        assertNull(stats.dureeCeMatinSecondes)
        assertEquals(emptyList<Any>(), stats.reveilsRecents)
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
        val jour = LocalDate.of(2026, 8, 10)
        val alarme = instant(jour)
        val stats = StatsCalculator.calculer(listOf(record(alarme, 120_000L)), zone, aujourdhui = jour)
        assertEquals(1, stats.nombreTotal)
        assertEquals(120L, stats.dureeCeMatinSecondes)
        assertEquals(1, stats.reveilsRecents.size)
        val reveil = stats.reveilsRecents.single()
        assertEquals(120L, reveil.dureeSecondes)
        assertEquals(jour, reveil.date)
        assertEquals(ChallengeId.MATHS.name, reveil.defi)
        assertEquals(false, reveil.abandon)
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
        val stats = StatsCalculator.calculer(records, zone, aujourdhui = LocalDate.of(2026, 8, 10))
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
        val stats = StatsCalculator.calculer(records, zone, aujourdhui = LocalDate.of(2026, 8, 10))
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
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
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
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        // 1 renoncement sur 3 tentatives pompes (2 réussies + 1 renoncée).
        assertEquals(1.0 / 3.0, stats.tauxAbandonPompes!!, 0.0001)
    }

    @Test
    fun `le taux de renoncement est indisponible sans aucune tentative de pompes`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(record(base, 60_000L, defi = ChallengeId.MATHS.name))
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
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
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertEquals(5, stats.erreursCumulees)
    }

    @Test
    fun `la liste des reveils recents se limite aux sept derniers dans l ordre chronologique`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val records = (0 until 9).map { i ->
            record(base + i * 3_600_000L, (i + 1) * 10_000L)
        }
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertEquals(listOf(30L, 40L, 50L, 60L, 70L, 80L, 90L), stats.reveilsRecents.map { it.dureeSecondes })
    }

    @Test
    fun `les reveils recents excluent les durees aberrantes comme les autres agregats`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 90_000L),
            // Sous la seconde : exclue du graphique, comme des autres agrégats.
            record(base + 60_000L, 500L),
            record(base + 120_000L, 60_000L),
            // Plus d'une heure : idem.
            record(base + 180_000L, 4_000_000L),
        )
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertEquals(4, stats.nombreTotal)
        assertEquals(listOf(90L, 60L), stats.reveilsRecents.map { it.dureeSecondes })
    }

    @Test
    fun `le detail d un reveil recent porte son defi et son renoncement`() {
        val base = instant(LocalDate.of(2026, 8, 10))
        val records = listOf(
            record(base, 60_000L, defi = ChallengeId.POMPES.name, abandon = false),
            record(base + 60_000L, 60_000L, defi = ChallengeId.MATHS.name, abandon = true),
        )
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertEquals(
            listOf(ChallengeId.POMPES.name, ChallengeId.MATHS.name),
            stats.reveilsRecents.map { it.defi },
        )
        assertEquals(listOf(false, true), stats.reveilsRecents.map { it.abandon })
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
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertEquals(DayOfWeek.WEDNESDAY, stats.jourLePlusLent)
    }

    @Test
    fun `la progression compare les cinq premiers et les cinq derniers reveils valides`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val premiers = (0 until 5).map { i -> record(base + i * 3_600_000L, 200_000L) }
        val derniers = (5 until 10).map { i -> record(base + i * 3_600_000L, 100_000L) }
        val tous = premiers + derniers
        val stats = StatsCalculator.calculer(tous, zone, jourDe(tous))
        // Négatif : les cinq derniers matins sont plus rapides que les cinq premiers.
        assertEquals(-100L, stats.progressionSecondes)
    }

    @Test
    fun `la progression est indisponible en dessous de dix reveils valides`() {
        val base = instant(LocalDate.of(2026, 8, 1))
        val records = (0 until 9).map { i -> record(base + i * 3_600_000L, 100_000L) }
        val stats = StatsCalculator.calculer(records, zone, jourDe(records))
        assertNull(stats.progressionSecondes)
    }

    // --- formatDuree : formatage des durées en unités humaines ---

    @Test
    fun `formatDuree choisit l unite selon la grandeur et garde les bornes exactes`() {
        assertEquals("0s", StatsCalculator.formatDuree(0L))
        assertEquals("59s", StatsCalculator.formatDuree(59L))
        // Bornes exactes des transitions d'unité : c'est là qu'un test complaisant
        // laisserait passer un décalage d'une seconde ou d'une minute.
        assertEquals("1min00", StatsCalculator.formatDuree(60L))
        assertEquals("1min01", StatsCalculator.formatDuree(61L))
        assertEquals("59min59", StatsCalculator.formatDuree(3599L))
        assertEquals("1h00", StatsCalculator.formatDuree(3600L))
        assertEquals("1h01", StatsCalculator.formatDuree(3661L))
        // Un écart négatif (progression) garde son signe.
        assertEquals("-1min30", StatsCalculator.formatDuree(-90L))
    }

    // --- echelle : repères de lecture du graphique ---

    @Test
    fun `l echelle place le sommet sur la plus longue duree affichee`() {
        val echelle = StatsCalculator.echelle(listOf(30L, 90L, 60L), moyenneSecondes = 50L)
        assertEquals(90L, echelle.maxSecondes)
        assertEquals(50f / 90f, echelle.positionMoyenne!!, 0.0001f)
    }

    @Test
    fun `l echelle plafonne la moyenne au sommet quand elle le depasse`() {
        val echelle = StatsCalculator.echelle(listOf(30L, 40L), moyenneSecondes = 100L)
        assertEquals(1f, echelle.positionMoyenne)
    }

    @Test
    fun `l echelle sans moyenne ne pose aucun repere`() {
        val echelle = StatsCalculator.echelle(listOf(30L, 40L), moyenneSecondes = null)
        assertNull(echelle.positionMoyenne)
    }

    @Test
    fun `l echelle sur une liste vide ne divise jamais par zero`() {
        // Le maximum vaut désormais la moyenne plutôt qu'un plancher à 1 :
        // l'échelle doit contenir tout ce que le graphique dessine, et sans
        // barre la ligne de moyenne est la seule chose à cadrer. Ce que ce
        // test protège — un maximum jamais nul — tient toujours.
        val echelle = StatsCalculator.echelle(emptyList(), moyenneSecondes = 10L)
        assertEquals(10L, echelle.maxSecondes)
        assertEquals(1f, echelle.positionMoyenne)
    }

    @Test
    fun `l echelle sans barre ni moyenne garde un maximum non nul`() {
        assertEquals(1L, StatsCalculator.echelle(emptyList(), moyenneSecondes = null).maxSecondes)
    }

    @Test
    fun `l echelle sur un seul reveil place le sommet et la moyenne ensemble`() {
        val echelle = StatsCalculator.echelle(listOf(120L), moyenneSecondes = 120L)
        assertEquals(120L, echelle.maxSecondes)
        assertEquals(1f, echelle.positionMoyenne)
    }

    // --- basculerSelection : sélection d'un réveil par son rang ---

    @Test
    fun `basculerSelection selectionne une barre non selectionnee`() {
        assertEquals(2, StatsCalculator.basculerSelection(null, 2))
    }

    @Test
    fun `basculerSelection desselectionne au second appui sur la meme barre`() {
        assertEquals(null, StatsCalculator.basculerSelection(2, 2))
    }

    @Test
    fun `basculerSelection bascule directement d une barre a l autre sans desselection intermediaire`() {
        assertEquals(5, StatsCalculator.basculerSelection(2, 5))
    }
}
