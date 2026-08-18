package com.cocorico.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Après un second refus, Android n'affiche plus jamais la boîte de dialogue :
 * `launch` ne fait plus rien et chaque appui sur l'option Photo devient un
 * échec silencieux. Distinguer ce cas est ce qui permet de proposer les
 * réglages Android plutôt que de redemander dans le vide.
 */
class RefusCameraDefinitifTest {

    @Test
    fun `permission accordee n est jamais un refus definitif`() {
        assertFalse(refusCameraDefinitif(accordee = true, justificationPossible = false))
        assertFalse(refusCameraDefinitif(accordee = true, justificationPossible = true))
    }

    @Test
    fun `un premier refus laisse une seconde chance`() {
        assertFalse(refusCameraDefinitif(accordee = false, justificationPossible = true))
    }

    @Test
    fun `un refus que le systeme ne justifiera plus est definitif`() {
        assertTrue(refusCameraDefinitif(accordee = false, justificationPossible = false))
    }
}
