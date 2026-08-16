package com.cocorico.challenge.photo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JugementPhotoTest {
    private val tasse = ObjetPhoto("tasse", "Tasse", setOf("mug", "cup"))

    @Test fun `une etiquette attendue au dessus du seuil accepte`() {
        assertTrue(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("Mug", 0.9f))))
    }

    @Test fun `la comparaison ignore la casse`() {
        assertTrue(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("MUG", 0.9f))))
    }

    @Test fun `un synonyme du catalogue accepte aussi`() {
        assertTrue(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("cup", 0.8f))))
    }

    @Test fun `une etiquette attendue sous le seuil refuse`() {
        assertFalse(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("mug", 0.2f))))
    }

    @Test fun `une etiquette exactement au seuil accepte`() {
        // Frontière explicite : sans ce test, passer de >= à > changerait le
        // comportement sans faire rougir quoi que ce soit.
        assertTrue(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("mug", 0.55f))))
    }

    @Test fun `un autre objet tres reconnaissable refuse`() {
        assertFalse(JugementPhoto.accepte(tasse, listOf(EtiquetteReconnue("shoe", 0.99f))))
    }

    @Test fun `aucune etiquette refuse`() {
        assertFalse(JugementPhoto.accepte(tasse, emptyList()))
    }

    @Test fun `le bon objet compte meme noye parmi d autres etiquettes`() {
        // Une photo de bureau rend beaucoup d'étiquettes ; exiger que la bonne
        // arrive en tête rejetterait des photos parfaitement valables.
        val etiquettes = listOf(
            EtiquetteReconnue("table", 0.95f),
            EtiquetteReconnue("wood", 0.9f),
            EtiquetteReconnue("mug", 0.7f),
        )
        assertTrue(JugementPhoto.accepte(tasse, etiquettes))
    }
}
