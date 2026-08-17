package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonneriesTest {

    @Test
    fun `un identifiant embarque connu resout la bonne sonnerie`() {
        assertEquals("Sirène", Sonneries.parId("sirene").nom)
    }

    @Test
    fun `un identifiant inconnu se replie sur le klaxon`() {
        assertEquals("klaxon", Sonneries.parId("n_existe_pas").id)
    }

    @Test
    fun `le repli d un identifiant inconnu est nomme et fait partie des embarquees`() {
        // Le repli est traversé à chaque réveil dont la configuration référence
        // une sonnerie disparue : il doit désigner une sonnerie par son nom, pas
        // une position dans la liste, sinon réordonner `toutes` change la
        // sonnerie du matin sans que rien ne le signale.
        assertEquals(Sonneries.repliIdInconnu, Sonneries.parId("n_existe_pas"))
        assertTrue(Sonneries.toutes.contains(Sonneries.repliIdInconnu))
    }

    @Test
    fun `le repli d une source illisible est la sonnerie la plus forte`() {
        // Quelqu'un qui a choisi la sirène l'a choisie parce que le coq ne le
        // réveille pas : quand sa source devient illisible, le repli doit tirer
        // vers le plus fort, jamais vers le plus doux.
        assertEquals(Sonneries.toutes.last(), Sonneries.repliLaPlusForte)
        assertFalse(Sonneries.repliLaPlusForte.personnalisee)
    }

    @Test
    fun `l identifiant personnalise resout l entree dediee`() {
        val sonnerie = Sonneries.parId(Sonneries.ID_PERSONNALISEE)
        assertEquals(Sonneries.ID_PERSONNALISEE, sonnerie.id)
        assertTrue(sonnerie.personnalisee)
    }

    @Test
    fun `les sonneries embarquees ne sont pas marquees personnalisees`() {
        assertTrue(Sonneries.toutes.none { it.personnalisee })
    }

    @Test
    fun `l entree personnalisee n apparait pas dans la liste des embarquees`() {
        assertFalse(Sonneries.toutes.any { it.id == Sonneries.ID_PERSONNALISEE })
    }
}
