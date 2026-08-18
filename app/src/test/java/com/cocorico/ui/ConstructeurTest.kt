package com.cocorico.ui

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructeurTest {

    @Test
    fun `samsung recoit une consigne dediee`() {
        val consigne = Constructeurs.reglageBatterie("samsung")
        assertTrue(consigne!!.contains("Batterie"))
    }

    @Test
    fun `la casse du fabricant est ignoree`() {
        assertTrue(Constructeurs.reglageBatterie("Xiaomi") != null)
        assertTrue(Constructeurs.reglageBatterie("XIAOMI") != null)
    }

    @Test
    fun `un fabricant inconnu ne recoit pas de consigne`() {
        assertNull(Constructeurs.reglageBatterie("fairphone"))
    }
}
