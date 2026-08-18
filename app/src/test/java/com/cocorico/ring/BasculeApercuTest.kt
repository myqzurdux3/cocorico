package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deuxième appui sur la sonnerie en cours d'écoute : elle s'arrête. Demandé
 * par l'utilisateur le 18 août 2026 — l'extrait durait trois secondes qu'on ne
 * pouvait qu'attendre, et quitter l'écran était la seule façon de le couper.
 */
class BasculeApercuTest {

    @Test
    fun `rien ne joue, l'appui lance l'ecoute`() {
        assertEquals(BasculeApercu.Bascule.JOUER, BasculeApercu.decider(enCours = null, demande = "coq"))
    }

    @Test
    fun `la sonnerie en cours d'ecoute s'arrete au deuxieme appui`() {
        assertEquals(BasculeApercu.Bascule.ARRETER, BasculeApercu.decider(enCours = "coq", demande = "coq"))
    }

    @Test
    fun `une autre sonnerie remplace celle en cours`() {
        assertEquals(BasculeApercu.Bascule.JOUER, BasculeApercu.decider(enCours = "coq", demande = "sirene"))
    }

    /**
     * L'extrait s'arrête aussi tout seul au bout de son temps. Le lecteur
     * remet alors [enCours] à `null`, et l'appui suivant doit relancer la même
     * sonnerie plutôt que de la « rarrêter ».
     */
    @Test
    fun `apres la fin de l'extrait, le meme appui relance`() {
        assertEquals(BasculeApercu.Bascule.JOUER, BasculeApercu.decider(enCours = null, demande = "coq"))
    }
}
