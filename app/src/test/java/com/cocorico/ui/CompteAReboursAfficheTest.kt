package com.cocorico.ui

import com.cocorico.ring.VolumeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce prédicat sert deux fois : il décide de ce que la jauge affiche, et il
 * décide de ce que l'écran d'alarme daigne recalculer. Les séparer laissait
 * `AlarmActivity` réécrire le compte à rebours deux fois par seconde alors
 * qu'aucun œil ne le voyait — chaque écriture recomposant l'aperçu caméra du
 * défi photo. Un test sur la seule décision suffit à garantir qu'elles ne
 * peuvent plus diverger.
 */
class CompteAReboursAfficheTest {

    @Test
    fun `a plein volume il n y a rien a decompter`() {
        assertFalse(compteAReboursAffiche(VolumeState.PLEIN))
    }

    @Test
    fun `volume baisse le decompte devient la seule information utile`() {
        assertTrue(compteAReboursAffiche(VolumeState.BAISSE))
    }
}
