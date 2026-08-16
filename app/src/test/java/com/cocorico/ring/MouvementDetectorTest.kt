package com.cocorico.ring

import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Séquences synthétiques d'accéléromètre, horodatées explicitement : la classe
 * pure ne lit aucune horloge, donc tout se rejoue sans appareil ni attente.
 *
 * Les scénarios sont écrits au pas de 20 ms, la cadence demandée par
 * [HandDetector] (`SENSOR_DELAY_GAME`) — le seul flux qui alimente
 * [MouvementDetector] en pratique.
 */
class MouvementDetectorTest {

    private val detecteur = MouvementDetector()

    @Test
    fun `les vibrations du haut parleur ne declenchent pas`() {
        // Même signal que PriseEnMainDetectorTest et EstimateurGraviteTest :
        // l'alarme hurle sur un meuble dur, oscillation de moyenne nulle à
        // 30 Hz. Trente secondes, pas une salve courte : si le plancher ne
        // tenait pas, c'est ici que ça se verrait.
        jouer(duree = 30_000L) { t ->
            val v = 2f * sin(2f * Math.PI.toFloat() * 30f * t / 1_000f)
            Triple(0.3f * v, 0f, G + v)
        }
        assertFalse(detecteur.enMouvement)
    }

    @Test
    fun `un deplacement lent de corps rigide declenche`() {
        jouer(duree = 1_000L) { plat() }
        // Le téléphone est soulevé puis freiné, comme dans
        // PriseEnMainDetectorTest : aucun échantillon isolé n'est énorme,
        // mais le mouvement est franc et tenu plusieurs centaines de ms.
        jouer(duree = 240L) { Triple(0f, 0f, G + 3f) }
        jouer(duree = 240L) { Triple(0f, 0f, G - 3f) }
        assertTrue(detecteur.enMouvement)
    }

    @Test
    fun `le mouvement retombe quand il cesse`() {
        jouer(duree = 1_000L) { plat() }
        jouer(duree = 240L) { Triple(0f, 0f, G + 3f) }
        jouer(duree = 240L) { Triple(0f, 0f, G - 3f) }
        assertTrue(detecteur.enMouvement)

        // Immobile ensuite : l'état n'est pas verrouillé, il doit retomber.
        jouer(duree = 3_000L) { plat() }
        assertFalse(detecteur.enMouvement)
    }

    @Test
    fun `une secousse isolee tres breve ne suffit pas`() {
        jouer(duree = 1_000L) { plat() }
        // Une seule impulsion brève, sans la tenue de la prise en main réelle.
        jouer(duree = 100L) { Triple(0f, 0f, G + 4f) }
        jouer(duree = 2_000L) { plat() }
        assertFalse(detecteur.enMouvement)
    }

    @Test
    fun `un telephone pose et immobile pendant longtemps ne declenche pas`() {
        jouer(duree = 60_000L, bruit = 0.04f) { plat() }
        assertFalse(detecteur.enMouvement)
    }

    // --- fabrique d'échantillons -------------------------------------------

    private var horloge = 0L
    private val alea = Random(7)

    private fun plat() = Triple(0f, 0f, G)

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
