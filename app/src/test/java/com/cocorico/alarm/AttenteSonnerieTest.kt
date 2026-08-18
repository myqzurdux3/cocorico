package com.cocorico.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Le réveil ne savait pas dire qu'il avait échoué : téléphone éteint pendant
 * l'alarme, processus tué, permission d'alarme exacte retirée après coup —
 * l'utilisateur ne l'apprenait qu'en ne se réveillant pas. Demandé le
 * 18 août 2026.
 */
class AttenteSonnerieTest {

    private companion object {
        const val T = 1_000_000_000_000L
    }

    @Test
    fun `sans attente enregistree, rien n'est manque`() {
        assertFalse(AttenteSonnerie.estManquee(attendue = 0L, maintenant = T))
        assertFalse(AttenteSonnerie.estManquee(attendue = -1L, maintenant = T))
    }

    @Test
    fun `une attente encore a venir n'est pas manquee`() {
        assertFalse(AttenteSonnerie.estManquee(attendue = T + 60_000, maintenant = T))
    }

    /**
     * Le cœur du garde-fou. Entre l'instant où `AlarmManager` déclenche et
     * celui où le service marque la sonnerie, il s'écoule un court délai :
     * conclure trop tôt annoncerait un échec pendant que l'alarme est en train
     * de partir. Rien de pire qu'un réveil qui s'accuse à tort.
     */
    @Test
    fun `juste apres l'heure prevue, on ne conclut pas encore`() {
        assertFalse(AttenteSonnerie.estManquee(attendue = T, maintenant = T))
        assertFalse(AttenteSonnerie.estManquee(attendue = T, maintenant = T + 1_000))
        assertFalse(
            AttenteSonnerie.estManquee(attendue = T, maintenant = T + AttenteSonnerie.MARGE_MS - 1),
        )
    }

    @Test
    fun `passee la marge, une attente non honoree est manquee`() {
        assertTrue(AttenteSonnerie.estManquee(attendue = T, maintenant = T + AttenteSonnerie.MARGE_MS))
        assertTrue(AttenteSonnerie.estManquee(attendue = T, maintenant = T + 86_400_000))
    }

    /**
     * Une horloge qui recule — changement de fuseau, correction réseau — ne
     * doit pas transformer une attente passée en attente future puis la
     * ressusciter. Le cas est simplement « pas encore conclu ».
     */
    @Test
    fun `une horloge qui recule ne conclut pas`() {
        assertFalse(AttenteSonnerie.estManquee(attendue = T, maintenant = T - 3_600_000))
    }

    @Test
    fun `le libelle nomme le jour et l'heure manques`() {
        assertEquals(
            "Le réveil de mardi 18 août à 06:30 n'a pas sonné.",
            AttenteSonnerie.libelle(LocalDateTime.of(2026, 8, 18, 6, 30)),
        )
        assertEquals(
            "Le réveil de dimanche 1 février à 23:05 n'a pas sonné.",
            AttenteSonnerie.libelle(LocalDateTime.of(2026, 2, 1, 23, 5)),
        )
    }
}
