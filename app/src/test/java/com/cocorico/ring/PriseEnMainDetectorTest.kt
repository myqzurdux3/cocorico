package com.cocorico.ring

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Séquences synthétiques d'accéléromètre, horodatées explicitement : la classe
 * pure ne lit aucune horloge, donc tout se rejoue sans appareil ni attente.
 *
 * Les scénarios sont écrits au pas de 20 ms, soit la cadence demandée au capteur
 * par [HandDetector] (`SENSOR_DELAY_GAME`).
 */
class PriseEnMainDetectorTest {

    private val detecteur = PriseEnMainDetector()

    @Test
    fun `un telephone pose a plat et immobile ne declenche pas`() {
        jouer(duree = 3_000L) { plat() }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `un telephone pose a plat pendant dix minutes ne declenche toujours pas`() {
        // Le vrai risque du signal d'énergie : une dérive qui finit par franchir
        // le budget à force d'accumuler du bruit. La fenêtre glissante l'exclut.
        jouer(duree = 600_000L) { plat() }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `un telephone pose avec un capteur bruyant ne declenche pas`() {
        jouer(duree = 60_000L, bruit = 0.25f) { plat() }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `une inclinaison franche et tenue declenche`() {
        jouer(duree = 1_000L) { plat() }
        assertFalse(detecteur.estPrisEnMain)
        jouer(duree = 2_000L) { incline(40f) }
        assertTrue(detecteur.estPrisEnMain)
    }

    @Test
    fun `une inclinaison juste au dela du seuil declenche aussi`() {
        jouer(duree = 1_000L) { plat() }
        jouer(duree = 3_000L) { incline(30f) }
        assertTrue(detecteur.estPrisEnMain)
    }

    @Test
    fun `une salve de mouvement a plat declenche sans aucune inclinaison`() {
        jouer(duree = 1_000L) { plat() }
        // Le téléphone est soulevé puis freiné, écran toujours horizontal :
        // aucun échantillon n'est énorme, c'est la somme qui déclenche.
        jouer(duree = 240L) { Triple(0f, 0f, G + 3f) }
        jouer(duree = 240L) { Triple(0f, 0f, G - 3f) }
        assertTrue(detecteur.estPrisEnMain)
    }

    @Test
    fun `le bruit du capteur autour du seuil d inclinaison ne declenche pas`() {
        jouer(duree = 1_000L) { plat() }
        // Posé de biais, juste sous le seuil, avec un bruit assez large pour le
        // franchir échantillon par échantillon : c'est exactement le faux
        // positif qui baisserait le volume d'une alarme jamais entendue.
        jouer(duree = 30_000L, bruit = 0.30f) { incline(24f) }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `une bascule tres breve ne declenche pas`() {
        jouer(duree = 1_000L) { plat() }
        jouer(duree = 120L) { incline(45f) }
        jouer(duree = 2_000L) { plat() }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `un telephone pose de biais sur un socle ne declenche pas tout seul`() {
        // L'inclinaison ne dit rien d'un téléphone déjà penché : le signal ne
        // s'arme qu'après avoir vu le téléphone à plat.
        jouer(duree = 30_000L) { incline(45f) }
        assertFalse(detecteur.estPrisEnMain)
    }

    @Test
    fun `un telephone pris depuis son socle declenche par l energie`() {
        jouer(duree = 2_000L) { incline(45f) }
        assertFalse(detecteur.estPrisEnMain)
        jouer(duree = 300L) { Triple(0f, 0f, G + 2.5f) }
        jouer(duree = 500L) { incline(60f) }
        assertTrue(detecteur.estPrisEnMain)
    }

    @Test
    fun `les vibrations du haut parleur ne declenchent pas`() {
        // L'alarme hurle sur un meuble dur : oscillation de moyenne nulle à
        // 30 Hz. Lisser avant de redresser doit l'annuler.
        jouer(duree = 30_000L) { t ->
            val v = 2f * sin(2f * Math.PI.toFloat() * 30f * t / 1_000f)
            Triple(0.3f * v, 0f, G + v)
        }
        assertFalse(detecteur.estPrisEnMain)
    }

    // --- fabrique d'échantillons -------------------------------------------

    private var horloge = 0L
    private val alea = Random(7)

    private fun plat() = Triple(0f, 0f, G)

    /** Téléphone penché de [angleDeg] autour de son axe X, gravité pure. */
    private fun incline(angleDeg: Float): Triple<Float, Float, Float> {
        val radians = angleDeg * Math.PI.toFloat() / 180f
        return Triple(0f, G * sin(radians), G * cos(radians))
    }

    /**
     * Joue [duree] millisecondes d'échantillons au pas de 20 ms, en avançant
     * une horloge explicite : aucune dépendance à l'heure réelle.
     */
    private fun jouer(
        duree: Long,
        bruit: Float = 0.05f,
        echantillon: (t: Long) -> Triple<Float, Float, Float>,
    ) {
        val fin = horloge + duree
        var t = horloge
        while (t < fin) {
            val (x, y, z) = echantillon(t - horloge)
            detecteur.onEchantillon(
                x + bruit(bruit),
                y + bruit(bruit),
                z + bruit(bruit),
                t,
            )
            t += PAS_MS
        }
        horloge = t
    }

    /** Bruit déterministe (graine fixe) : le test ne peut pas devenir capricieux. */
    private fun bruit(amplitude: Float): Float = (alea.nextFloat() * 2f - 1f) * amplitude

    private companion object {
        const val G = PriseEnMainDetector.GRAVITE
        const val PAS_MS = 20L
    }
}
