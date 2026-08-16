package com.cocorico.challenge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoChallengeEtatTest {
    private val a = ObjetPhoto("a", "A")
    private val b = ObjetPhoto("b", "B")

    @Test fun `le premier objet est propose d emblee`() {
        assertEquals(a, PhotoChallengeEtat(listOf(a, b)).objetCourant.value)
    }

    @Test fun `un refus ne fait pas avancer et compte un essai`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        assertFalse(etat.soumettre(accepte = false))
        assertEquals(a, etat.objetCourant.value)
        assertEquals(1, etat.essais.value)
        assertFalse(etat.isSolved.value)
    }

    @Test fun `un accord passe a l objet suivant sans compter d essai rate`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        assertTrue(etat.soumettre(accepte = true))
        assertEquals(b, etat.objetCourant.value)
        assertEquals(0, etat.essais.value)
    }

    @Test fun `valider tous les objets resout le defi`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        etat.soumettre(accepte = true)
        etat.soumettre(accepte = true)
        assertTrue(etat.isSolved.value)
        assertNull(etat.objetCourant.value)
    }

    @Test fun `une fois resolu plus rien ne change`() {
        val etat = PhotoChallengeEtat(listOf(a))
        etat.soumettre(accepte = true)
        assertFalse(etat.soumettre(accepte = true))
        assertEquals(1, etat.progression.value.first)
    }

    @Test fun `une liste vide est resolue et n affiche aucun objet`() {
        // Cas de repli : si le catalogue rendait une liste vide, le défi doit
        // se résoudre plutôt que bloquer l'utilisateur devant une sirène.
        val etat = PhotoChallengeEtat(emptyList())
        assertTrue(etat.isSolved.value)
        assertNull(etat.objetCourant.value)
    }

    @Test fun `la progression suit les objets valides`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        assertEquals(0 to 2, etat.progression.value)
        etat.soumettre(accepte = true)
        assertEquals(1 to 2, etat.progression.value)
    }

    // Le compteur affiché (`essais`) repart à zéro à chaque objet validé — c'est
    // voulu, voir le test ci-dessus. Mais l'historique du réveil a besoin du
    // total sur toute la session, distinct de ce compteur d'écran : ces tests
    // couvrent `essaisTotal`, qui ne redescend jamais.

    @Test fun `le compteur cumule commence a zero`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        assertEquals(0, etat.essaisTotal.value)
    }

    @Test fun `un refus incremente le compteur cumule comme le compteur affiche`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        etat.soumettre(accepte = false)
        assertEquals(1, etat.essaisTotal.value)
        assertEquals(1, etat.essais.value)
    }

    @Test fun `un accord remet le compteur affiche a zero mais pas le compteur cumule`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        etat.soumettre(accepte = false)
        etat.soumettre(accepte = true)
        assertEquals(0, etat.essais.value)
        assertEquals(1, etat.essaisTotal.value)
    }

    @Test fun `le compteur cumule additionne les echecs de plusieurs objets enchaines`() {
        val etat = PhotoChallengeEtat(listOf(a, b))
        etat.soumettre(accepte = false)
        etat.soumettre(accepte = false)
        etat.soumettre(accepte = true) // valide a, avec 2 essais ratés avant
        etat.soumettre(accepte = false)
        etat.soumettre(accepte = true) // valide b, avec 1 essai raté avant
        assertEquals(3, etat.essaisTotal.value)
        assertTrue(etat.isSolved.value)
    }
}
