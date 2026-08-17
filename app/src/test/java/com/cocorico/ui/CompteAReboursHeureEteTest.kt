package com.cocorico.ui

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le compte à rebours soustrayait deux `LocalDateTime`, ce qui ignore le
 * changement d'offset. Les deux nuits de bascule, l'écart affiché s'écartait
 * jusqu'à une heure de l'instant réellement programmé — et c'est justement la
 * nuit où l'utilisateur a le plus besoin de croire son réveil.
 */
class CompteAReboursHeureEteTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    @Test fun `la nuit du passage a l heure d ete le delai tient compte de l heure perdue`() {
        // 2026-03-29 : 2 h 00 saute à 3 h 00. De 1 h 30 à 6 h 30 il ne s'écoule
        // que quatre heures réelles, pas cinq.
        val delai = CompteARebours.libelle(
            depuis = LocalDateTime.of(2026, 3, 29, 1, 30),
            cible = LocalDateTime.of(2026, 3, 29, 6, 30),
            zone = paris,
        )
        assertEquals("Réveil dans 4 h 0 min", delai)
    }

    @Test fun `la nuit du retour a l heure d hiver le delai tient compte de l heure gagnee`() {
        // 2026-10-25 : 3 h 00 revient à 2 h 00. De 1 h 30 à 6 h 30 il s'écoule
        // six heures réelles.
        val delai = CompteARebours.libelle(
            depuis = LocalDateTime.of(2026, 10, 25, 1, 30),
            cible = LocalDateTime.of(2026, 10, 25, 6, 30),
            zone = paris,
        )
        assertEquals("Réveil dans 6 h 0 min", delai)
    }

    @Test fun `un jour ordinaire n est pas affecte`() {
        val delai = CompteARebours.libelle(
            depuis = LocalDateTime.of(2026, 8, 17, 22, 0),
            cible = LocalDateTime.of(2026, 8, 18, 6, 30),
            zone = paris,
        )
        assertEquals("Réveil dans 8 h 30 min", delai)
    }

    @Test fun `une peremption se juge aussi sur des instants reels`() {
        assertTrue(
            CompteARebours.estPerimee(
                depuis = LocalDateTime.of(2026, 3, 29, 4, 0),
                cible = LocalDateTime.of(2026, 3, 29, 2, 30),
                zone = paris,
            ),
        )
    }
}
