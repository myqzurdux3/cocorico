package com.cocorico.ring

import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la propriété qui motive la séparation en deux canaux dans
 * [EstimateurGravite] : une oscillation de moyenne nulle à haute fréquence
 * (vibrations du haut-parleur) doit produire un [EstimateurGravite.ecartGravite]
 * faible, alors qu'un déplacement de corps rigide plus lent (une main qui
 * agite le téléphone) doit produire un écart franc, au-dessus du seuil
 * ECART_MAX = 1,5 utilisé par CompteurPompes pour sa garde « immobile ».
 * C'est exactement la propriété qui distingue une triche d'une alarme qui
 * vibre — voir le défaut documenté dans CapteurPompes.kt.
 *
 * Même fabrique de séquences synthétiques que [PriseEnMainDetectorTest], au
 * pas de 20 ms, la cadence demandée à l'accéléromètre (SENSOR_DELAY_GAME).
 * SENSOR_DELAY_UI, l'ancienne cadence, vaut 66,7 ms : le dernier test rejoue la
 * propriété à ce pas-là et montre qu'elle n'y tient pas du tout.
 */
class EstimateurGraviteTest {

    private val estimateur = EstimateurGravite()

    @Test
    fun `les vibrations du haut parleur produisent un ecart faible`() {
        var pic = 0f
        // 30 Hz, 2 m/s^2 : l'ordre de grandeur d'un haut-parleur à plein
        // volume posé sur un meuble dur, comme dans PriseEnMainDetectorTest.
        jouer(duree = 5_000L, echantillon = { t ->
            val v = 2f * sin(2f * Math.PI.toFloat() * 30f * t / 1_000f)
            Triple(0f, 0f, G + v)
        }, apres = { pic = maxOf(pic, estimateur.ecartGravite) })

        assertTrue("pic=$pic devrait rester sous le seuil ECART_MAX=1,5", pic < 0.5f)
    }

    @Test
    fun `une agitation de la main produit un ecart franc au dela du seuil`() {
        jouer(duree = 1_000L, echantillon = { plat() })

        var pic = 0f
        // Agitation type « main devant le capteur pour simuler des
        // répétitions » : 3 Hz, 4 m/s^2, un geste bien plus lent que les
        // vibrations du haut-parleur mais franchement un mouvement du corps
        // rigide du téléphone.
        jouer(duree = 2_000L, echantillon = { t ->
            val v = 4f * sin(2f * Math.PI.toFloat() * 3f * t / 1_000f)
            Triple(0f, 0f, G + v)
        }, apres = { pic = maxOf(pic, estimateur.ecartGravite) })

        assertTrue("pic=$pic devrait dépasser le seuil ECART_MAX=1,5", pic > 1.5f)
    }

    /**
     * La même vibration de haut-parleur, échantillonnée à 66,7 ms
     * (SENSOR_DELAY_UI, la cadence que `CapteurPompes` demandait avant
     * correction). Ce test n'affirme pas une propriété souhaitable : il consigne
     * une défaillance mesurée. À ce pas, 30 Hz se replie autour du continu — 30
     * Hz est presque exactement le double de la fréquence d'échantillonnage — et
     * la vibration devient un écart quasi permanent — 1,98 mesuré ici — au-dessus
     * du seuil ECART_MAX = 1,5, contre 0,21 au pas de 20 ms. Conséquence sur l'appareil : `positionValide` faux
     * à chaque échantillon, aucune pompe comptée, alarme qui ne s'arrête plus.
     * C'est la raison d'être de SENSOR_DELAY_GAME dans `CapteurPompes.demarrer`,
     * et ce test rougirait si quelqu'un revenait à l'ancienne cadence en croyant
     * la propriété acquise.
     */
    @Test
    fun `a la cadence SENSOR_DELAY_UI les vibrations passent pour un mouvement`() {
        var pic = 0f
        jouer(duree = 5_000L, pas = PAS_SENSOR_DELAY_UI_MS, echantillon = { t ->
            val v = 2f * sin(2f * Math.PI.toFloat() * 30f * t / 1_000f)
            Triple(0f, 0f, G + v)
        }, apres = { pic = maxOf(pic, estimateur.ecartGravite) })

        assertTrue(
            "pic=$pic : au pas de 66,7 ms la vibration devrait franchir ECART_MAX=1,5, " +
                "c'est le repliement de spectre qui rend cette cadence inutilisable",
            pic > 1.5f,
        )
    }

    // --- fabrique d'échantillons -------------------------------------------

    private var horloge = 0L
    private val alea = Random(7)

    private fun plat() = Triple(0f, 0f, G)

    /**
     * Joue [duree] millisecondes d'échantillons au pas de [pas] — 20 ms par
     * défaut, la cadence réelle — en avançant une horloge explicite : aucune
     * dépendance à l'heure réelle. [apres] est appelé après chaque échantillon
     * consommé, pour observer la sortie.
     */
    private fun jouer(
        duree: Long,
        bruit: Float = 0.05f,
        pas: Long = PAS_MS,
        echantillon: (t: Long) -> Triple<Float, Float, Float>,
        apres: () -> Unit = {},
    ) {
        val fin = horloge + duree
        var t = horloge
        while (t < fin) {
            val (x, y, z) = echantillon(t - horloge)
            estimateur.onEchantillon(
                x + bruit(bruit),
                y + bruit(bruit),
                z + bruit(bruit),
                t,
            )
            apres()
            t += pas
        }
        horloge = t
    }

    /** Bruit déterministe (graine fixe) : le test ne peut pas devenir capricieux. */
    private fun bruit(amplitude: Float): Float = (alea.nextFloat() * 2f - 1f) * amplitude

    private companion object {
        const val G = EstimateurGravite.GRAVITE

        /** Cadence réelle de l'accéléromètre : SENSOR_DELAY_GAME. */
        const val PAS_MS = 20L

        /** Ancienne cadence, conservée pour documenter ce qu'elle produit. */
        const val PAS_SENSOR_DELAY_UI_MS = 67L
    }
}
