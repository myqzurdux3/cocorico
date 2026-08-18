package com.cocorico.challenge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionObjetsTest {

    @Test fun `le seuil d avertissement suit le nombre d objets demandes`() {
        // Le seuil n'avertit correctement que s'il vaut exactement ce que le
        // défi peut demander. Recopié à la main, il survivrait à un changement
        // de `NOMBRE_OBJETS` sans rien signaler : l'écran cesserait d'avertir,
        // ou avertirait à tort.
        assertEquals(
            PhotoChallenge.NOMBRE_OBJETS,
            SelectionObjets.SEUIL_AVERTISSEMENT,
        )
    }

    @Test fun `compterParPiece rend une entree par piece du decoupage`() {
        val comptage = SelectionObjets.compterParPiece(emptySet())
        assertEquals(Piece.entries.toSet(), comptage.map { it.piece }.toSet())
    }

    @Test fun `compterParPiece avec une selection vide ne coche rien`() {
        val comptage = SelectionObjets.compterParPiece(emptySet())
        comptage.forEach {
            assertEquals(0, it.coches)
            assertFalse(it.toutCoche)
        }
    }

    @Test fun `compterParPiece avec tout le catalogue coche chaque piece entierement`() {
        val tout = CatalogueObjets.tous.map { it.id }.toSet()
        val comptage = SelectionObjets.compterParPiece(tout)
        comptage.forEach { c ->
            assertEquals(c.total, c.coches)
            assertTrue(c.toutCoche)
        }
    }

    @Test fun `compterParPiece compte uniquement les objets de la piece concernee`() {
        val piece = CatalogueObjets.tous.first().piece
        val idsDeLaPiece = CatalogueObjets.tous.filter { it.piece == piece }.map { it.id }
        val unSeul = setOf(idsDeLaPiece.first())
        val comptage = SelectionObjets.compterParPiece(unSeul).first { it.piece == piece }
        assertEquals(1, comptage.coches)
        assertEquals(idsDeLaPiece.size, comptage.total)
    }

    @Test fun `compterParPiece ignore les identifiants inconnus du catalogue`() {
        val piece = CatalogueObjets.tous.first().piece
        val idDeLaPiece = CatalogueObjets.tous.first { it.piece == piece }.id
        val comptage = SelectionObjets
            .compterParPiece(setOf(idDeLaPiece, "fantome"))
            .first { it.piece == piece }
        assertEquals(1, comptage.coches)
    }

    @Test fun `basculerObjet ajoute un identifiant absent`() {
        val id = CatalogueObjets.tous.first().id
        assertEquals(setOf(id), SelectionObjets.basculerObjet(emptySet(), id))
    }

    @Test fun `basculerObjet retire un identifiant present`() {
        val id = CatalogueObjets.tous.first().id
        assertTrue(SelectionObjets.basculerObjet(setOf(id), id).isEmpty())
    }

    @Test fun `basculerObjet ne touche pas aux autres identifiants`() {
        val a = CatalogueObjets.tous[0].id
        val b = CatalogueObjets.tous[1].id
        assertEquals(setOf(a, b), SelectionObjets.basculerObjet(setOf(a), b))
    }

    @Test fun `basculerPiece coche tous les objets d une piece non entierement cochee`() {
        val piece = CatalogueObjets.tous.first().piece
        val idsDeLaPiece = CatalogueObjets.tous.filter { it.piece == piece }.map { it.id }.toSet()
        val resultat = SelectionObjets.basculerPiece(emptySet(), piece)
        assertEquals(idsDeLaPiece, resultat)
    }

    @Test fun `basculerPiece decoche tous les objets d une piece entierement cochee`() {
        val piece = CatalogueObjets.tous.first().piece
        val autrePiece = Piece.entries.first { it != piece }
        val idDeLAutrePiece = CatalogueObjets.tous.first { it.piece == autrePiece }.id
        val idsDeLaPiece = CatalogueObjets.tous.filter { it.piece == piece }.map { it.id }.toSet()
        val depart = idsDeLaPiece + idDeLAutrePiece

        val resultat = SelectionObjets.basculerPiece(depart, piece)

        assertEquals(setOf(idDeLAutrePiece), resultat)
    }

    @Test fun `basculerPiece ne modifie pas la selection des autres pieces`() {
        val piece = CatalogueObjets.tous.first().piece
        val autrePiece = Piece.entries.first { it != piece }
        val idDeLAutrePiece = CatalogueObjets.tous.first { it.piece == autrePiece }.id

        val resultat = SelectionObjets.basculerPiece(setOf(idDeLAutrePiece), piece)

        assertTrue(idDeLAutrePiece in resultat)
    }

    @Test fun `totalCoche compte les identifiants valides toutes pieces confondues`() {
        val ids = CatalogueObjets.tous.take(4).map { it.id }.toSet()
        assertEquals(4, SelectionObjets.totalCoche(ids))
    }

    @Test fun `totalCoche ignore les identifiants inconnus du catalogue`() {
        val id = CatalogueObjets.tous.first().id
        assertEquals(1, SelectionObjets.totalCoche(setOf(id, "fantome")))
    }

    @Test fun `le seuil d avertissement vaut un objet`() {
        // Décision produit, épinglée volontairement : le défi photo demande une
        // photo et une seule. Se lever, traverser le logement et cadrer un
        // objet est déjà l'effort ; en exiger deux n'ajouterait que du temps
        // devant une sirène. Si quelqu'un remonte ce nombre, ce test le dit.
        assertEquals(1, SelectionObjets.SEUIL_AVERTISSEMENT)
    }
}
