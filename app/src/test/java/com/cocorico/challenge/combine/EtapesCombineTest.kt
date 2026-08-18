package com.cocorico.challenge.combine

import com.cocorico.data.ChallengeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La liste d'épreuves du défi sur mesure : ce qu'on en garde, comment elle se
 * range sur le disque, et comment l'utilisateur la réordonne.
 *
 * Tout est pur, sans import `android.*`. C'est nécessaire : cette liste décide
 * de ce que l'alarme demandera demain matin, et une liste vide ou corrompue
 * arrêterait la sonnerie sans rien demander du tout.
 */
class EtapesCombineTest {

    // --- Assainissement ---

    @Test fun `une liste valide traverse sans changer`() {
        val etapes = listOf(
            EtapeCombine(ChallengeId.MATHS, 2),
            EtapeCombine(ChallengeId.POMPES, 5),
        )
        assertEquals(etapes, EtapesCombine.assainir(etapes))
    }

    @Test fun `une epreuve a zero est retiree`() {
        // Le compteur à zéro est la façon dont l'écran retire une épreuve.
        val etapes = listOf(
            EtapeCombine(ChallengeId.MATHS, 2),
            EtapeCombine(ChallengeId.PHOTO, 0),
        )
        assertEquals(listOf(EtapeCombine(ChallengeId.MATHS, 2)), EtapesCombine.assainir(etapes))
    }

    @Test fun `une liste entierement vide se replie sur des calculs`() {
        // Sans ce repli, le défi serait résolu d'emblée et l'alarme s'arrêterait
        // sans avoir rien demandé — le pire échec possible de ce produit.
        assertEquals(EtapesCombine.REPLI, EtapesCombine.assainir(emptyList()))
        assertEquals(
            EtapesCombine.REPLI,
            EtapesCombine.assainir(listOf(EtapeCombine(ChallengeId.PHOTO, 0))),
        )
    }

    @Test fun `un nombre absurde est borne`() {
        // La liste est persistée : une valeur abîmée ne doit pas produire
        // quatre-vingt-dix mille pompes devant une sirène.
        val trop = EtapesCombine.assainir(listOf(EtapeCombine(ChallengeId.POMPES, 90_000)))
        assertEquals(EtapesCombine.NOMBRE_MAX, trop.single().nombre)
        val negatif = EtapesCombine.assainir(listOf(EtapeCombine(ChallengeId.MATHS, -3)))
        assertEquals(EtapesCombine.REPLI, negatif)
    }

    @Test fun `un type repete est fusionne sur sa premiere place`() {
        // Deux lignes du même type n'ont pas de sens dans cet écran : il y a une
        // ligne par type. Une liste persistée qui en contient deux vient d'un
        // fichier abîmé, pas d'un geste de l'utilisateur.
        val fusionnee = EtapesCombine.assainir(
            listOf(
                EtapeCombine(ChallengeId.POMPES, 5),
                EtapeCombine(ChallengeId.MATHS, 2),
                EtapeCombine(ChallengeId.POMPES, 9),
            ),
        )
        assertEquals(2, fusionnee.size)
        assertEquals(ChallengeId.POMPES, fusionnee.first().type)
    }

    // --- Rangement sur le disque ---

    @Test fun `une liste se relit telle qu elle a ete ecrite`() {
        val etapes = listOf(
            EtapeCombine(ChallengeId.PHOTO, 1),
            EtapeCombine(ChallengeId.MATHS, 3),
            EtapeCombine(ChallengeId.POMPES, 10),
        )
        assertEquals(etapes, EtapesCombine.decoder(EtapesCombine.encoder(etapes)))
    }

    @Test fun `l ordre survit au rangement`() {
        // L'ordre **est** le réglage : le perdre changerait le réveil sans que
        // rien ne le signale.
        val a = listOf(EtapeCombine(ChallengeId.PHOTO, 1), EtapeCombine(ChallengeId.MATHS, 1))
        val b = listOf(EtapeCombine(ChallengeId.MATHS, 1), EtapeCombine(ChallengeId.PHOTO, 1))
        assertTrue(EtapesCombine.encoder(a) != EtapesCombine.encoder(b))
        assertEquals(a, EtapesCombine.decoder(EtapesCombine.encoder(a)))
    }

    @Test fun `un texte illisible se replie sur des calculs`() {
        assertEquals(EtapesCombine.REPLI, EtapesCombine.decoder(""))
        assertEquals(EtapesCombine.REPLI, EtapesCombine.decoder("n'importe quoi"))
        assertEquals(EtapesCombine.REPLI, EtapesCombine.decoder("MATHS:pasunnombre"))
    }

    @Test fun `un type inconnu est ignore sans emporter le reste`() {
        // Un identifiant écrit par une version future, ou effacé d'une version
        // ultérieure : on garde ce qu'on comprend.
        assertEquals(
            listOf(EtapeCombine(ChallengeId.MATHS, 2)),
            EtapesCombine.decoder("YOGA:4,MATHS:2"),
        )
    }

    // --- Réordonnancement ---

    @Test fun `monter echange une epreuve avec celle du dessus`() {
        val etapes = listOf(
            EtapeCombine(ChallengeId.MATHS, 1),
            EtapeCombine(ChallengeId.POMPES, 5),
        )
        assertEquals(
            listOf(EtapeCombine(ChallengeId.POMPES, 5), EtapeCombine(ChallengeId.MATHS, 1)),
            EtapesCombine.monter(etapes, 1),
        )
    }

    @Test fun `monter la premiere ou descendre la derniere ne fait rien`() {
        // Les flèches restent visibles aux extrémités ; elles ne doivent pas
        // faire disparaître une ligne par un index hors bornes.
        val etapes = listOf(
            EtapeCombine(ChallengeId.MATHS, 1),
            EtapeCombine(ChallengeId.POMPES, 5),
        )
        assertEquals(etapes, EtapesCombine.monter(etapes, 0))
        assertEquals(etapes, EtapesCombine.descendre(etapes, 1))
        assertEquals(etapes, EtapesCombine.monter(etapes, 42))
        assertEquals(etapes, EtapesCombine.descendre(etapes, -1))
    }
}
