package com.cocorico.challenge.photo

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueObjetsTest {
    @Test fun `le tirage rend le nombre demande`() {
        assertEquals(3, CatalogueObjets.tirer(3, emptySet(), Random(1)).size)
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
}
