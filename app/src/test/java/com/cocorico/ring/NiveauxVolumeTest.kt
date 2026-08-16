package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NiveauxVolumeTest {

    @Test fun `a cent pour cent le plein vaut le maximum de l appareil`() {
        assertEquals(7, NiveauxVolume.plein(maxAppareil = 7, pourcent = 100))
    }

    @Test fun `a cinquante pour cent le plein vaut la moitie du maximum`() {
        assertEquals(4, NiveauxVolume.plein(maxAppareil = 7, pourcent = 50))
        assertEquals(8, NiveauxVolume.plein(maxAppareil = 15, pourcent = 50))
    }

    @Test fun `un reglage sous le plancher est remonte a cinquante pour cent`() {
        // Le réglage est persisté : une valeur corrompue, une ancienne version,
        // ou un utilisateur qui aurait trouvé un moyen de descendre plus bas ne
        // doivent jamais produire une alarme qu'on n'entend pas. Le plancher
        // est appliqué ici, dans le calcul, pas seulement dans le curseur.
        assertEquals(
            NiveauxVolume.plein(maxAppareil = 7, pourcent = 50),
            NiveauxVolume.plein(maxAppareil = 7, pourcent = 10),
        )
        assertEquals(
            NiveauxVolume.plein(maxAppareil = 7, pourcent = 50),
            NiveauxVolume.plein(maxAppareil = 7, pourcent = 0),
        )
        assertEquals(
            NiveauxVolume.plein(maxAppareil = 7, pourcent = 50),
            NiveauxVolume.plein(maxAppareil = 7, pourcent = -30),
        )
    }

    @Test fun `un reglage au dessus de cent est ramene a cent`() {
        assertEquals(7, NiveauxVolume.plein(maxAppareil = 7, pourcent = 300))
    }

    @Test fun `le plein n est jamais nul`() {
        // Un flux minuscule ne doit pas produire un niveau zéro : une alarme
        // silencieuse est le seul échec que ce produit n'a pas le droit de
        // commettre.
        assertTrue(NiveauxVolume.plein(maxAppareil = 1, pourcent = 50) >= 1)
        assertTrue(NiveauxVolume.plein(maxAppareil = 2, pourcent = 50) >= 1)
    }

    @Test fun `la baisse reste strictement sous le plein`() {
        // C'est l'invariant central : si les deux niveaux se confondent,
        // prendre le téléphone en main ne baisse plus rien, et le mécanisme
        // qui récompense le réveil devient invisible — sans qu'aucun test
        // fonctionnel ne s'en aperçoive.
        for (max in 1..30) {
            for (pourcent in 50..100) {
                val plein = NiveauxVolume.plein(max, pourcent)
                val baisse = NiveauxVolume.baisse(max, pourcent)
                assertTrue(
                    "max=$max pourcent=$pourcent plein=$plein baisse=$baisse",
                    baisse < plein || plein == 1,
                )
                assertTrue("baisse doit rester audible", baisse >= 1)
            }
        }
    }

    @Test fun `la baisse suit le plafond choisi`() {
        // Baisser doit rester proportionnel à ce que l'utilisateur a accepté
        // d'entendre : quelqu'un qui a plafonné à 50 % ne doit pas retrouver
        // une baisse calculée sur un maximum qu'il a justement refusé.
        val fort = NiveauxVolume.baisse(maxAppareil = 15, pourcent = 100)
        val doux = NiveauxVolume.baisse(maxAppareil = 15, pourcent = 50)
        assertTrue("$doux devrait être sous $fort", doux < fort)
    }

    @Test fun `le plancher expose vaut cinquante`() {
        assertEquals(50, NiveauxVolume.POURCENT_MINIMAL)
    }
}
