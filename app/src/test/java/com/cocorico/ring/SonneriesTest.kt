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
