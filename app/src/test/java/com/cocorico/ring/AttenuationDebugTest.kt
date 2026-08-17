package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atténuation réservée aux essais. Elle existe parce qu'essayer un réveil chez
 * soi, de jour, ne doit pas coûter une sirène à plein volume dans les oreilles.
 *
 * Elle est **volontairement en dehors** de [NiveauxVolume] : la logique produit
 * — le plafond utilisateur et son plancher à 50 % — n'est pas touchée. On
 * calcule le niveau exactement comme en production, puis on l'atténue en
 * dernier. Ce qui est mis à l'épreuve pendant l'essai reste donc le vrai code.
 */
class AttenuationDebugTest {

    @Test fun `sans consigne d essai le niveau produit passe intact`() {
        // Le cas normal, et le seul qui existe en version publiée.
        assertEquals(7, AttenuationDebug.appliquer(niveau = 7, pourcent = null))
        assertEquals(2, AttenuationDebug.appliquer(niveau = 2, pourcent = null))
    }

    @Test fun `une consigne d essai attenue proportionnellement`() {
        assertEquals(3, AttenuationDebug.appliquer(niveau = 30, pourcent = 10))
        assertEquals(5, AttenuationDebug.appliquer(niveau = 15, pourcent = 33))
    }

    @Test fun `l attenuation ne descend jamais au silence`() {
        // Un essai muet ne prouve rien : on ne saurait pas distinguer « ça
        // marche mais c'est bas » de « ça n'a jamais démarré ».
        assertEquals(1, AttenuationDebug.appliquer(niveau = 7, pourcent = 10))
        assertEquals(1, AttenuationDebug.appliquer(niveau = 1, pourcent = 1))
        for (niveau in 1..30) {
            assertTrue(AttenuationDebug.appliquer(niveau, pourcent = 1) >= 1)
        }
    }

    @Test fun `une consigne absurde est ramenee dans les bornes`() {
        assertEquals(7, AttenuationDebug.appliquer(niveau = 7, pourcent = 500))
        assertEquals(1, AttenuationDebug.appliquer(niveau = 7, pourcent = -20))
    }

    @Test fun `l attenuation n augmente jamais le niveau`() {
        for (niveau in 1..30) {
            for (pourcent in 1..100) {
                assertTrue(
                    "niveau=$niveau pourcent=$pourcent",
                    AttenuationDebug.appliquer(niveau, pourcent) <= niveau,
                )
            }
        }
    }

    @Test fun `une consigne illisible vaut absence de consigne`() {
        // Le fichier est écrit à la main depuis adb : il peut contenir
        // n'importe quoi. Dans le doute, on sonne normalement — se tromper
        // dans ce sens réveille, l'autre sens non.
        assertEquals(null, AttenuationDebug.lireConsigne("bonjour"))
        assertEquals(null, AttenuationDebug.lireConsigne(""))
        assertEquals(null, AttenuationDebug.lireConsigne("  "))
        assertEquals(10, AttenuationDebug.lireConsigne(" 10 \n"))
    }
}
