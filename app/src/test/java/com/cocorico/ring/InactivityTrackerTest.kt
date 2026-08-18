package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityTrackerTest {

    private val tracker = InactivityTracker(timeoutMillis = 10_000L)

    @Test
    fun `n est pas expire juste apres une interaction`() {
        tracker.onInteraction(1_000L)
        assertFalse(tracker.isExpired(5_000L))
    }

    @Test
    fun `est expire une fois le delai atteint`() {
        tracker.onInteraction(1_000L)
        assertTrue(tracker.isExpired(11_000L))
    }

    @Test
    fun `une interaction reinitialise le compte a rebours`() {
        tracker.onInteraction(1_000L)
        tracker.onInteraction(9_000L)
        assertFalse(tracker.isExpired(15_000L))
        assertTrue(tracker.isExpired(19_000L))
    }

    @Test
    fun `avant toute interaction le compte a rebours est deja expire`() {
        // Choix délibéré : l'oubli d'amorçage donne une alarme à fond, pas une
        // alarme muette.
        assertTrue(tracker.isExpired(System.currentTimeMillis()))
        assertEquals(0L, tracker.millisRestantes(System.currentTimeMillis()))
    }

    @Test
    fun `le temps restant est expose pour l affichage`() {
        tracker.onInteraction(1_000L)
        assertEquals(6_000L, tracker.millisRestantes(5_000L))
        assertEquals(0L, tracker.millisRestantes(30_000L))
    }
}
