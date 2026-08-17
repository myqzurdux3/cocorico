package com.cocorico.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trois statistiques qui affichaient une valeur fausse avec l'aplomb d'une
 * valeur vraie. Une statistique fausse est pire qu'une statistique absente :
 * l'utilisateur n'a aucun moyen de s'apercevoir de l'erreur.
 */
class StatsCorrectionsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun jour(date: String, dureeSecondes: Long = 60): WakeRecord {
        val debut = LocalDate.parse(date).atTime(6, 30).atZone(zone).toInstant().toEpochMilli()
        return WakeRecord(
            alarmeAt = debut,
            resoluAt = debut + dureeSecondes * 1000,
            defi = ChallengeId.MATHS.name,
            erreurs = 0,
            triches = 0,
            abandon = false,
        )
    }

    // --- La série ---

    @Test fun `une serie interrompue depuis des semaines vaut zero`() {
        // Sans référence au jour courant, une série close il y a un mois
        // s'affichait encore comme si elle était en cours.
        val records = listOf(jour("2026-07-01"), jour("2026-07-02"), jour("2026-07-03"))
        assertEquals(0, SerieCalculator.serie(records, zone, aujourdhui = LocalDate.parse("2026-08-17")))
    }

    @Test fun `une serie qui va jusqu a aujourd hui compte`() {
        val records = listOf(jour("2026-08-15"), jour("2026-08-16"), jour("2026-08-17"))
        assertEquals(3, SerieCalculator.serie(records, zone, aujourdhui = LocalDate.parse("2026-08-17")))
    }

    @Test fun `une serie qui s arrete hier compte encore`() {
        // Le réveil d'aujourd'hui n'a pas encore eu lieu : la série est vivante
        // tant qu'hier est présent, sinon elle paraîtrait cassée chaque nuit.
        val records = listOf(jour("2026-08-15"), jour("2026-08-16"))
        assertEquals(2, SerieCalculator.serie(records, zone, aujourdhui = LocalDate.parse("2026-08-17")))
    }

    @Test fun `une serie sans reveil vaut zero`() {
        assertEquals(0, SerieCalculator.serie(emptyList(), zone, aujourdhui = LocalDate.parse("2026-08-17")))
    }

    // --- Le retard moyen ---

    @Test fun `un enregistrement aberrant ne fausse plus le retard moyen`() {
        // Un réveil laissé ouvert toute la journée (ou un horodatage abîmé)
        // écrasait la moyenne affichée à l'écran de victoire, là où
        // StatsCalculator écartait déjà les durées invraisemblables.
        val normaux = listOf(jour("2026-08-15", 30), jour("2026-08-16", 40), jour("2026-08-17", 50))
        val aberrant = jour("2026-08-14", dureeSecondes = 86_400)
        assertEquals(40, SerieCalculator.retardMoyenSecondes(normaux + aberrant))
    }

    @Test fun `un retard moyen sans donnee valide vaut zero`() {
        assertEquals(0, SerieCalculator.retardMoyenSecondes(emptyList()))
        assertEquals(0, SerieCalculator.retardMoyenSecondes(listOf(jour("2026-08-15", 86_400))))
    }

    // --- L'échelle du graphique ---

    @Test fun `la ligne de moyenne reste dans le graphique quand elle depasse les barres`() {
        // Les 7 barres affichées peuvent toutes être plus rapides que la
        // moyenne historique. L'échelle se calait sur le maximum des barres :
        // la ligne de moyenne était alors plaquée au sommet, indiscernable
        // d'une moyenne exactement égale au pire matin affiché.
        val echelle = StatsCalculator.echelle(dureesSecondes = listOf(10L, 20L, 30L), moyenneSecondes = 90L)
        assertEquals(90L, echelle.maxSecondes)
        val position = echelle.positionMoyenne!!
        assertTrue("la moyenne ne doit plus toucher le sommet par troncature", position <= 1f)
        assertEquals(1f, position, 0.001f)
    }

    @Test fun `l echelle suit les barres quand la moyenne est dedans`() {
        val echelle = StatsCalculator.echelle(dureesSecondes = listOf(10L, 60L), moyenneSecondes = 30L)
        assertEquals(60L, echelle.maxSecondes)
        assertEquals(0.5f, echelle.positionMoyenne!!, 0.001f)
    }

    @Test fun `une echelle sans moyenne n a pas de ligne`() {
        assertNull(StatsCalculator.echelle(listOf(10L), null).positionMoyenne)
    }

    @Test fun `une echelle sans barre garde un maximum utilisable`() {
        // Sans plancher, une barre serait divisée par zéro.
        assertTrue(StatsCalculator.echelle(emptyList(), null).maxSecondes >= 1L)
    }
}
