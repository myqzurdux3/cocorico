package com.cocorico.challenge.photo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueObjetsTest {
    @Test fun `le tirage rend le nombre demande`() {
        assertEquals(3, CatalogueObjets.tirer(3, emptySet(), Random(1)).size)
    }

    @Test fun `un nombre nul ou negatif tire quand meme un objet`() {
        // Un tirage vide alimente le pire scénario du défi : une liste sans
        // objet, un défi considéré comme déjà résolu, et l'alarme qui s'arrête
        // sans qu'aucune photo n'ait été prise. Mieux vaut un objet de trop
        // qu'un réveil qui se coupe tout seul.
        assertEquals(1, CatalogueObjets.tirer(0, emptySet(), Random(6)).size)
        assertEquals(1, CatalogueObjets.tirer(-3, emptySet(), Random(7)).size)
    }

    @Test fun `le tirage ne repete jamais un objet`() {
        val tires = CatalogueObjets.tirer(5, emptySet(), Random(2))
        assertEquals(5, tires.map { it.id }.toSet().size)
    }

    @Test fun `le tirage evite les objets exclus`() {
        val exclus = CatalogueObjets.tous.take(3).map { it.id }.toSet()
        val tires = CatalogueObjets.tirer(3, exclus, Random(3))
        assertTrue(tires.none { it.id in exclus })
    }

    @Test fun `demander plus d objets que le catalogue n en a ne boucle pas`() {
        // Sans borne, une boucle de tirage sans remise tournerait indéfiniment
        // et l'alarme resterait bloquée sur un écran vide, sirène en marche.
        val tires = CatalogueObjets.tirer(CatalogueObjets.tous.size + 5, emptySet(), Random(4))
        assertEquals(CatalogueObjets.tous.size, tires.size)
    }

    @Test fun `exclure tout le catalogue rend quand meme un objet`() {
        // Cas réel : la difficulté demande trois objets et les trois du réveil
        // précédent sont exclus alors que le catalogue est petit. Mieux vaut
        // répéter un objet que ne rien afficher.
        val tous = CatalogueObjets.tous.map { it.id }.toSet()
        assertTrue(CatalogueObjets.tirer(1, tous, Random(5)).isNotEmpty())
    }

    @Test fun `si l exclusion laisse moins d objets que demande, le tirage complete quand meme`() {
        // Pool disponible non vide (2 objets) mais plus petit que la demande
        // (3) : c'est le cas que le défaut laissait passer, en rendant 2
        // objets au lieu de 3 sans que rien ne le signale.
        val exclus = CatalogueObjets.tous.drop(2).map { it.id }.toSet()
        val tires = CatalogueObjets.tirer(3, exclus, Random(6))
        assertEquals(3, tires.size)
    }

    @Test fun `les objets non exclus sont servis en priorite avant de completer avec des exclus`() {
        val nonExclus = CatalogueObjets.tous.take(2).map { it.id }.toSet()
        val exclus = CatalogueObjets.tous.drop(2).map { it.id }.toSet()
        val tires = CatalogueObjets.tirer(3, exclus, Random(6))
        // Les deux seuls objets non exclus doivent figurer dans le tirage ;
        // un correctif qui piocherait au hasard dans tout le catalogue,
        // exclus compris, laisserait passer ce test si on ne vérifiait que
        // le compte.
        assertTrue(tires.map { it.id }.toSet().containsAll(nonExclus))
    }

    @Test fun `chaque objet porte un nom francais affichable`() {
        // Ce nom est à la fois montré à l'utilisateur et envoyé au juge : un
        // nom vide demanderait de photographier « rien » et ferait tout
        // refuser, sirène en marche.
        CatalogueObjets.tous.forEach { assertTrue(it.nom.isNotBlank()) }
    }

    @Test fun `les identifiants sont uniques`() {
        assertEquals(CatalogueObjets.tous.size, CatalogueObjets.tous.map { it.id }.toSet().size)
    }

    @Test fun `le catalogue est assez grand pour la difficulte la plus exigeante`() {
        // Trois objets tirés, trois exclus du réveil précédent : en dessous de
        // six, le tirage devrait piocher dans les exclus tous les matins.
        assertTrue(CatalogueObjets.tous.size >= 6)
    }

    @Test fun `le catalogue est nettement plus etoffe qu avant cette fonctionnalite`() {
        // Le catalogue comptait une trentaine d'objets avant l'ajout des
        // pièces. La demande explicite est « beaucoup plus complet ».
        assertTrue(CatalogueObjets.tous.size >= 50)
    }

    @Test fun `chaque piece du decoupage contient plusieurs objets`() {
        // Une pièce anecdotique (un seul objet) rendrait le découpage inutile
        // à l'écran de sélection : cocher ou décocher une pièce entière n'a
        // d'intérêt que si elle regroupe un choix réel.
        Piece.entries.forEach { piece ->
            assertTrue(
                "la pièce $piece devrait contenir plusieurs objets",
                CatalogueObjets.tous.count { it.piece == piece } >= 5,
            )
        }
    }

    @Test fun `tous les objets du catalogue sont repartis sur une piece`() {
        // Chaque piece du decoupage porte au moins un objet : une piece vide
        // s'afficherait dans l'ecran de selection sans rien a cocher.
        Piece.entries.forEach { piece -> assertTrue(CatalogueObjets.tous.any { it.piece == piece }) }
    }

    // --- Selection de l'utilisateur : le tirage ne doit piocher que dedans,
    // sauf repli de sécurité quand elle est vide ou trop petite. ---

    @Test fun `une selection suffisante restreint le tirage a ses identifiants`() {
        val selection = CatalogueObjets.tous.take(5).map { it.id }.toSet()
        val tires = CatalogueObjets.tirer(3, emptySet(), Random(10), selection)
        assertTrue(tires.all { it.id in selection })
    }

    @Test fun `une selection vide ne restreint pas le tirage`() {
        // Convention centrale : une selection vide vaut absence de
        // restriction, piochée dans tout le catalogue. C'est ce repli qui
        // empêche une sélection totalement décochée de bloquer le tirage.
        val tires = CatalogueObjets.tirer(5, emptySet(), Random(11), emptySet())
        assertEquals(5, tires.size)
    }

    @Test fun `un identifiant de selection inconnu du catalogue est ignore sans planter`() {
        val connu = CatalogueObjets.tous.first().id
        val tires = CatalogueObjets.tirer(1, emptySet(), Random(12), setOf(connu, "n_existe_pas"))
        assertEquals(listOf(connu), tires.map { it.id })
    }

    @Test fun `une selection dont tous les identifiants sont inconnus se replie sur tout le catalogue`() {
        val tires = CatalogueObjets.tirer(3, emptySet(), Random(13), setOf("a", "b", "c"))
        assertEquals(3, tires.size)
    }

    @Test fun `une selection plus petite que la demande se complete hors selection`() {
        // Cas explicitement signalé par la consigne : la sélection contient
        // moins d'objets que la difficulté n'en demande (jusqu'à trois). Le
        // tirage doit quand même rendre le compte promis, quitte à sortir de
        // la sélection de l'utilisateur.
        val selection = CatalogueObjets.tous.take(2).map { it.id }.toSet()
        val tires = CatalogueObjets.tirer(3, emptySet(), Random(14), selection)
        assertEquals(3, tires.size)
        assertTrue(tires.map { it.id }.toSet().containsAll(selection))
    }

    @Test fun `la selection est completee par ses propres exclus avant de sortir de la selection`() {
        // Priorité : sélection non exclue, puis sélection exclue, et
        // seulement en dernier recours hors sélection.
        val selection = CatalogueObjets.tous.take(3).map { it.id }.toSet()
        val exclus = setOf(selection.first())
        val tires = CatalogueObjets.tirer(3, exclus, Random(15), selection)
        assertEquals(selection, tires.map { it.id }.toSet())
    }

    @Test fun `idsValides ignore les identifiants qui n existent plus dans le catalogue`() {
        val connu = CatalogueObjets.tous.first().id
        assertEquals(setOf(connu), CatalogueObjets.idsValides(setOf(connu, "fantome")))
    }

    @Test fun `idsValides d un ensemble vide rend un ensemble vide`() {
        assertTrue(CatalogueObjets.idsValides(emptySet()).isEmpty())
    }
}
