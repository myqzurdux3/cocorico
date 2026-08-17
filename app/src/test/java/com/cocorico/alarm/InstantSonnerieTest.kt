package com.cocorico.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Les deux jours de bascule de l'heure d'été sont les seuls où l'heure murale
 * demandée peut ne pas exister, ou exister deux fois. Un réveil qui se trompe
 * ces jours-là sonne une heure trop tard — ou pas du tout.
 */
class InstantSonnerieTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `une heure ordinaire d ete est resolue sans surprise`() {
        assertEquals(
            Instant.parse("2026-08-19T04:30:00Z"),
            InstantSonnerie.resoudre(LocalDateTime.of(2026, 8, 19, 6, 30), paris),
        )
    }

    @Test
    fun `une heure ordinaire d hiver est resolue sans surprise`() {
        assertEquals(
            Instant.parse("2026-01-15T05:30:00Z"),
            InstantSonnerie.resoudre(LocalDateTime.of(2026, 1, 15, 6, 30), paris),
        )
    }

    @Test
    fun `une heure dans le trou du printemps sonne des la bascule`() {
        // 29 mars 2026 : 2 h 00 devient 3 h 00, 2 h 30 n'existe pas.
        assertEquals(
            Instant.parse("2026-03-29T01:00:00Z"),
            InstantSonnerie.resoudre(LocalDateTime.of(2026, 3, 29, 2, 30), paris),
        )
    }

    @Test
    fun `dans le trou du printemps la sonnerie est plus tot que la resolution par defaut`() {
        val demande = LocalDateTime.of(2026, 3, 29, 2, 30)
        val defaut = demande.atZone(paris).toInstant()
        assertTrue(InstantSonnerie.resoudre(demande, paris).isBefore(defaut))
    }

    @Test
    fun `une heure dans le recouvrement d automne sonne au premier passage`() {
        // 25 octobre 2026 : 3 h 00 redevient 2 h 00, 2 h 30 arrive deux fois.
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            InstantSonnerie.resoudre(LocalDateTime.of(2026, 10, 25, 2, 30), paris),
        )
    }

    @Test
    fun `une heure hors du recouvrement le jour du retour a l heure d hiver reste normale`() {
        // 6 h 30 le 25 octobre : après la bascule, donc en heure d'hiver.
        assertEquals(
            Instant.parse("2026-10-25T05:30:00Z"),
            InstantSonnerie.resoudre(LocalDateTime.of(2026, 10, 25, 6, 30), paris),
        )
    }
}
