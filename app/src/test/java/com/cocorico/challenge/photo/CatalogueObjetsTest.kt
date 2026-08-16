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

    @Test fun `chaque objet porte au moins une etiquette et un nom francais`() {
        CatalogueObjets.tous.forEach {
            assertTrue(it.nom.isNotBlank())
            assertTrue(it.etiquettes.isNotEmpty())
        }
    }

    @Test fun `les identifiants sont uniques`() {
        assertEquals(CatalogueObjets.tous.size, CatalogueObjets.tous.map { it.id }.toSet().size)
    }

    @Test fun `les etiquettes sont normalisees en minuscules`() {
        // Le jugement compare sans tenir compte de la casse ; une étiquette
        // écrite « Mug » dans le catalogue et « mug » par le modèle doit
        // correspondre. On normalise à la source plutôt qu'à chaque comparaison.
        CatalogueObjets.tous.forEach { objet ->
            objet.etiquettes.forEach { assertEquals(it.lowercase(), it) }
        }
    }
}
