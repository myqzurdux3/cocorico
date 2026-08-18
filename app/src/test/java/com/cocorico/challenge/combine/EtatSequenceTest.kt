package com.cocorico.challenge.combine

import com.cocorico.data.ChallengeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'avancement dans la suite d'épreuves. Pur : c'est ici que se décide si
 * l'alarme continue ou s'arrête, et cette décision ne doit pas dépendre d'un
 * téléphone pour être vérifiable.
 */
class EtatSequenceTest {

    private val trois = listOf(
        EtapeCombine(ChallengeId.MATHS, 2),
        EtapeCombine(ChallengeId.POMPES, 5),
        EtapeCombine(ChallengeId.PHOTO, 1),
    )

    @Test fun `une suite commence a sa premiere epreuve`() {
        val etat = EtatSequence(trois)
        assertEquals(trois[0], etat.courante)
        assertEquals(1, etat.numero)
        assertEquals(3, etat.total)
        assertFalse(etat.estTerminee)
    }

    @Test fun `resoudre avance d une epreuve`() {
        val etat = EtatSequence(trois).suivante()
        assertEquals(trois[1], etat.courante)
        assertEquals(2, etat.numero)
        assertFalse(etat.estTerminee)
    }

    @Test fun `la suite n est terminee qu apres la derniere epreuve`() {
        // C'est l'invariant qui tient l'alarme : tant qu'il reste une épreuve,
        // la sonnerie continue.
        var etat = EtatSequence(trois)
        repeat(3) {
            assertFalse("l'alarme s'arrêterait trop tôt", etat.estTerminee)
            etat = etat.suivante()
        }
        assertTrue(etat.estTerminee)
    }

    @Test fun `renoncer remplace l epreuve en cours par des calculs et garde son nombre`() {
        // Un bras bloqué ne doit pas annuler la photo qui suit, et le réveil
        // garde une charge comparable.
        val etat = EtatSequence(trois).suivante().remplacerParCalculs()
        assertEquals(EtapeCombine(ChallengeId.MATHS, 5), etat.courante)
        assertEquals(2, etat.numero)
        assertFalse(etat.estTerminee)
    }

    @Test fun `remplacer ne touche pas aux epreuves voisines`() {
        val etat = EtatSequence(trois).suivante().remplacerParCalculs()
        assertEquals(trois[0], etat.etapes[0])
        assertEquals(trois[2], etat.etapes[2])
    }

    @Test fun `remplacer une epreuve deja en calculs ne change rien`() {
        val etat = EtatSequence(trois).remplacerParCalculs()
        assertEquals(trois[0], etat.courante)
    }

    @Test fun `une suite vide est refusee a la construction`() {
        // Une suite sans épreuve serait terminée d'emblée : l'alarme s'arrêterait
        // sans rien demander. `EtapesCombine.assainir` garantit le contraire en
        // amont ; ce refus est la ceinture qui accompagne les bretelles.
        val leve = runCatching { EtatSequence(emptyList()) }.isFailure
        assertTrue("une suite vide doit être refusée", leve)
    }

    @Test fun `terminee, il n y a plus d epreuve courante`() {
        var etat = EtatSequence(listOf(EtapeCombine(ChallengeId.MATHS, 1)))
        etat = etat.suivante()
        assertTrue(etat.estTerminee)
        assertTrue(runCatching { etat.courante }.isFailure)
    }
}
