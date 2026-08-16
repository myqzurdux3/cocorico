package com.cocorico.ui

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompteAReboursTest {

    private val maintenant = LocalDateTime.of(2026, 8, 16, 22, 30)

    @Test
    fun `un delai de plusieurs heures affiche heures et minutes`() {
        val cible = maintenant.plusHours(7).plusMinutes(42)
        assertEquals("Réveil dans 7 h 42 min", CompteARebours.libelle(maintenant, cible))
    }

    @Test
    fun `un delai de moins d une heure n affiche que les minutes`() {
        val cible = maintenant.plusMinutes(20)
        assertEquals("Réveil dans 20 min", CompteARebours.libelle(maintenant, cible))
    }

    @Test
    fun `une cible depassee n affiche jamais un delai negatif`() {
        val cible = maintenant.minusMinutes(2)
        assertEquals(CompteARebours.IMMINENT, CompteARebours.libelle(maintenant, cible))
    }

    @Test
    fun `une cible a l instant present est imminente et non nulle`() {
        assertEquals(CompteARebours.IMMINENT, CompteARebours.libelle(maintenant, maintenant))
    }

    @Test
    fun `sous la minute le libelle ne se lit pas comme un compteur bloque`() {
        val cible = maintenant.plusSeconds(30)
        assertEquals("Réveil dans moins d'une minute", CompteARebours.libelle(maintenant, cible))
    }

    @Test
    fun `sans occurrence programmee le coq dort`() {
        assertEquals(CompteARebours.SANS_OCCURRENCE, CompteARebours.libelle(maintenant, null))
    }

    @Test
    fun `une occurrence depassee est signalee comme perimee`() {
        assertTrue(CompteARebours.estPerimee(maintenant, maintenant.minusSeconds(1)))
        assertTrue(CompteARebours.estPerimee(maintenant, maintenant))
    }

    @Test
    fun `une occurrence a venir n est pas perimee`() {
        assertFalse(CompteARebours.estPerimee(maintenant, maintenant.plusSeconds(1)))
    }

    @Test
    fun `l absence d occurrence n est pas une occurrence perimee a recalculer`() {
        // Sinon l'accueil boucherait sur un recalcul permanent quand aucun jour
        // n'est actif : il n'y a rien à recalculer, il n'y a pas d'alarme.
        assertFalse(CompteARebours.estPerimee(maintenant, null))
    }
}
