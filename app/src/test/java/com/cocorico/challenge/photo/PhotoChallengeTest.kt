package com.cocorico.challenge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les décisions du défi photo qui se testent sans caméra ni appareil : que
 * faire d'un juge qui ne répond pas, et à quelle taille décoder la photo.
 *
 * Le reste de la classe — capture, conversion, composition — demande un
 * appareil et n'est pas couvert ici.
 */
class PhotoChallengeTest {

    // --- Juge indisponible ----------------------------------------------

    @Test fun `un premier echec du juge laisse reessayer`() {
        assertEquals(SuiteApresPanne.REESSAYER, PhotoChallenge.suiteApresPanne(0))
        assertEquals(SuiteApresPanne.REESSAYER, PhotoChallenge.suiteApresPanne(1))
    }

    @Test fun `apres deux echecs le repli calculs est propose, apres trois on bascule`() {
        // Rester bloqué devant une sirène est le pire échec possible : passé
        // deux essais que le juge n'a pas pu trancher, aucune photo ne peut
        // aboutir et il faut montrer la sortie, puis la prendre.
        assertEquals(SuiteApresPanne.PROPOSER_REPLI, PhotoChallenge.suiteApresPanne(2))
        assertEquals(SuiteApresPanne.BASCULER, PhotoChallenge.suiteApresPanne(3))
        assertEquals(SuiteApresPanne.BASCULER, PhotoChallenge.suiteApresPanne(9))
    }

    @Test fun `une panne du juge n est jamais presentee comme un refus`() {
        val message = PhotoChallenge.messagePanne("quota d'API dépassé (HTTP 429)", 1)
        // La cause, telle quelle : sans elle, une panne réseau, un quota
        // dépassé et une clé invalide s'affichaient tous « Pas encore
        // reconnu. Réessaie », et l'utilisateur rephotographiait un objet
        // correct en boucle.
        assertTrue(message.contains("429"))
        assertFalse(message.contains("Pas encore reconnu"))
        assertTrue(message.contains("n'est pas en cause"))
    }

    @Test fun `passe le seuil, le message nomme explicitement le repli calculs`() {
        val message = PhotoChallenge.messagePanne("serveur du juge en panne (HTTP 503)", 2)
        assertTrue(message.contains("calculs"))
        assertTrue(message.contains("503"))
    }

    // --- Décodage de la photo -------------------------------------------

    @Test fun `l echantillonnage evite de decoder la pleine resolution`() {
        // 12 Mpx décodés en entier, c'est près de 48 Mo alloués avant même de
        // réduire, sur un écran d'alarme qui sonne.
        assertEquals(2, PhotoChallenge.echantillonnage(4032, 3024, coteMax = 1568))
        assertEquals(4, PhotoChallenge.echantillonnage(12000, 9000, coteMax = 1568))
    }

    @Test fun `l echantillonnage ne descend jamais sous la taille visee`() {
        // Le facteur retenu doit laisser une image encore au moins aussi
        // grande que la cible : c'est `reduire` qui ajuste ensuite exactement.
        listOf(1200 to 900, 4032 to 3024, 12000 to 9000, 3000 to 4000).forEach { (l, h) ->
            val facteur = PhotoChallenge.echantillonnage(l, h, coteMax = 1568)
            assertTrue("facteur $facteur pour $l×$h", maxOf(l, h) / facteur >= 1568 || facteur == 1)
        }
    }

    @Test fun `une image deja petite se decode telle quelle`() {
        assertEquals(1, PhotoChallenge.echantillonnage(1200, 900, coteMax = 1568))
        assertEquals(1, PhotoChallenge.echantillonnage(1568, 1568, coteMax = 1568))
    }

    @Test fun `des dimensions inconnues ne bloquent pas le decodage`() {
        // `inJustDecodeBounds` rend -1 quand il n'a pas su lire l'en-tête.
        assertEquals(1, PhotoChallenge.echantillonnage(-1, -1, coteMax = 1568))
        assertEquals(1, PhotoChallenge.echantillonnage(0, 0, coteMax = 1568))
        assertEquals(1, PhotoChallenge.echantillonnage(4032, 3024, coteMax = 0))
    }
}
