package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'aperçu jouait toujours à la même amplitude, quel que soit le plafond réglé
 * par l'utilisateur : deux réglages différents s'entendaient pareil. Or
 * l'aperçu ne sert qu'à une chose — savoir ce qu'on entendra le matin — et il
 * répondait à côté de la question. Signalé par l'utilisateur le 18 août 2026.
 */
class VolumeApercuTest {

    @Test
    fun `le plafond maximal donne l'attenuation pleine`() {
        assertEquals(
            NiveauxVolume.ATTENUATION_APERCU,
            NiveauxVolume.volumeApercu(100),
            1e-6f,
        )
    }

    @Test
    fun `un plafond de moitie donne un apercu de moitie`() {
        assertEquals(
            NiveauxVolume.ATTENUATION_APERCU / 2f,
            NiveauxVolume.volumeApercu(50),
            1e-6f,
        )
    }

    /**
     * Même plancher que la sonnerie réelle : un aperçu ne doit pas laisser
     * espérer plus bas que ce que l'alarme sait faire.
     */
    @Test
    fun `sous le plancher produit, l'apercu ne descend pas plus bas`() {
        assertEquals(
            NiveauxVolume.volumeApercu(NiveauxVolume.POURCENT_MINIMAL),
            NiveauxVolume.volumeApercu(0),
            1e-6f,
        )
        assertEquals(
            NiveauxVolume.volumeApercu(NiveauxVolume.POURCENT_MINIMAL),
            NiveauxVolume.volumeApercu(-40),
            1e-6f,
        )
    }

    @Test
    fun `au-dela du maximum, l'apercu est borne`() {
        assertEquals(
            NiveauxVolume.volumeApercu(100),
            NiveauxVolume.volumeApercu(500),
            1e-6f,
        )
    }

    /** `MediaPlayer.setVolume` n'accepte que [0, 1] : sortir de la plage est une erreur. */
    @Test
    fun `le volume reste dans la plage acceptee par le lecteur`() {
        for (pourcent in -50..200) {
            val volume = NiveauxVolume.volumeApercu(pourcent)
            assertTrue("pourcent=$pourcent volume=$volume", volume in 0f..1f)
        }
    }

    /** Monotone : monter le plafond ne doit jamais baisser l'aperçu. */
    @Test
    fun `l'apercu croit avec le plafond`() {
        var precedent = -1f
        for (pourcent in NiveauxVolume.POURCENT_MINIMAL..NiveauxVolume.POURCENT_MAXIMAL) {
            val volume = NiveauxVolume.volumeApercu(pourcent)
            assertTrue("pourcent=$pourcent", volume >= precedent)
            precedent = volume
        }
    }
}
