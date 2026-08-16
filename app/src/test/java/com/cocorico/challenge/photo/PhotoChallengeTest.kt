package com.cocorico.challenge.photo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste la seule décision testable extraite de `PhotoChallenge` : le choix de
 * consulter ou non le juge distant après le verdict de l'embarqué. Le reste
 * de la classe est du câblage Android (caméra, permissions, écran) et n'a pas
 * de test unitaire, conformément à la règle du projet.
 */
class PhotoChallengeTest {

    @Test fun `un accord de l embarque n interroge jamais le distant`() {
        assertFalse(
            PhotoChallenge.fautInterrogerJugeDistant(
                embarqueAccepte = true,
                iaDistanteActive = true,
                cleApi = "cle",
            ),
        )
    }

    @Test fun `un refus interroge le distant si le mode est actif et la cle non vide`() {
        assertTrue(
            PhotoChallenge.fautInterrogerJugeDistant(
                embarqueAccepte = false,
                iaDistanteActive = true,
                cleApi = "cle",
            ),
        )
    }

    @Test fun `un refus n interroge pas le distant si le mode est eteint`() {
        assertFalse(
            PhotoChallenge.fautInterrogerJugeDistant(
                embarqueAccepte = false,
                iaDistanteActive = false,
                cleApi = "cle",
            ),
        )
    }

    @Test fun `un refus n interroge pas le distant si la cle est vide`() {
        assertFalse(
            PhotoChallenge.fautInterrogerJugeDistant(
                embarqueAccepte = false,
                iaDistanteActive = true,
                cleApi = "",
            ),
        )
    }

    @Test fun `une cle faite uniquement d espaces compte comme vide`() {
        assertFalse(
            PhotoChallenge.fautInterrogerJugeDistant(
                embarqueAccepte = false,
                iaDistanteActive = true,
                cleApi = "   ",
            ),
        )
    }
}
