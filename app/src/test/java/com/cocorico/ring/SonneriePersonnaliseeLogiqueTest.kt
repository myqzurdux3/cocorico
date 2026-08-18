package com.cocorico.ring

import com.cocorico.ring.SonneriePersonnaliseeLogique.SourceAJouer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonneriePersonnaliseeLogiqueTest {

    // --- sourceAJouer -------------------------------------------------

    @Test
    fun `personnalisee choisie avec uri enregistree tente l uri`() {
        val source = SonneriePersonnaliseeLogique.sourceAJouer(
            personnalisee = true,
            uriPersistee = "content://truc/musique.mp3",
        )
        assertEquals(SourceAJouer.Personnalisee("content://truc/musique.mp3"), source)
    }

    @Test
    fun `personnalisee choisie sans uri enregistree se replie sur l embarquee`() {
        val source = SonneriePersonnaliseeLogique.sourceAJouer(
            personnalisee = true,
            uriPersistee = null,
        )
        assertEquals(SourceAJouer.Embarquee, source)
    }

    @Test
    fun `personnalisee choisie avec uri vide se replie sur l embarquee`() {
        val source = SonneriePersonnaliseeLogique.sourceAJouer(
            personnalisee = true,
            uriPersistee = "  ",
        )
        assertEquals(SourceAJouer.Embarquee, source)
    }

    @Test
    fun `sonnerie embarquee ignore l uri meme si une est enregistree`() {
        val source = SonneriePersonnaliseeLogique.sourceAJouer(
            personnalisee = false,
            uriPersistee = "content://truc/musique.mp3",
        )
        assertEquals(SourceAJouer.Embarquee, source)
    }

    // --- estJouable -----------------------------------------------------

    @Test
    fun `duree positive est jouable`() {
        assertTrue(SonneriePersonnaliseeLogique.estJouable(1500))
    }

    @Test
    fun `duree nulle n est pas jouable`() {
        assertFalse(SonneriePersonnaliseeLogique.estJouable(null))
    }

    @Test
    fun `duree zero n est pas jouable`() {
        assertFalse(SonneriePersonnaliseeLogique.estJouable(0))
    }

    @Test
    fun `duree negative n est pas jouable`() {
        assertFalse(SonneriePersonnaliseeLogique.estJouable(-1))
    }

    // --- nomAffichable ----------------------------------------------------

    @Test
    fun `nom interroge est prefere`() {
        val nom = SonneriePersonnaliseeLogique.nomAffichable(
            uri = "content://truc/document/raw%3A%2Fstorage%2Fmusique.mp3",
            nomInterroge = "Ma chanson préférée.mp3",
        )
        assertEquals("Ma chanson préférée.mp3", nom)
    }

    @Test
    fun `nom interroge vide se replie sur le dernier segment de l uri`() {
        val nom = SonneriePersonnaliseeLogique.nomAffichable(
            uri = "content://truc/document/musique.mp3",
            nomInterroge = "   ",
        )
        assertEquals("musique.mp3", nom)
    }

    @Test
    fun `nom interroge absent se replie sur le dernier segment de l uri`() {
        val nom = SonneriePersonnaliseeLogique.nomAffichable(
            uri = "file:///storage/emulated/0/Music/reveil.mp3",
            nomInterroge = null,
        )
        assertEquals("reveil.mp3", nom)
    }

    @Test
    fun `aucun nom exploitable retombe sur un libelle generique`() {
        val nom = SonneriePersonnaliseeLogique.nomAffichable(
            uri = "",
            nomInterroge = null,
        )
        assertEquals("Sonnerie personnalisée", nom)
    }
}
