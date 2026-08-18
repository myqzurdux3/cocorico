package com.cocorico.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La fenêtre de validité est la seule chose qui distingue une alarme réellement
 * en train de sonner d'un drapeau resté à `true` après un arrêt forcé. S'y
 * tromper, c'est soit une alarme fantôme au démarrage, soit une alarme muette.
 */
class AlarmStateTest {

    private val heure = AlarmState.FENETRE_VALIDITE_MS

    @Test
    fun `un drapeau baisse n est jamais frais`() {
        assertFalse(
            AlarmState.estEncoreFraiche(
                enCours = false,
                dernierSigneMs = 1_000L,
                maintenantMs = 1_500L,
            ),
        )
    }

    @Test
    fun `une alarme qui vient de demarrer est fraiche`() {
        assertTrue(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 1_000L,
                maintenantMs = 1_000L,
            ),
        )
    }

    @Test
    fun `une alarme signalee il y a moins d une heure reste fraiche`() {
        assertTrue(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 1_000L,
                maintenantMs = 1_000L + heure - 1L,
            ),
        )
    }

    @Test
    fun `au dela d une heure le drapeau est perime`() {
        assertFalse(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 1_000L,
                maintenantMs = 1_000L + heure,
            ),
        )
        assertFalse(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 1_000L,
                maintenantMs = 1_000L + 24L * heure,
            ),
        )
    }

    @Test
    fun `un horodatage absent rend le drapeau inexploitable`() {
        // Cas d'une installation mise à jour depuis une version qui n'horodatait pas.
        assertFalse(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 0L,
                maintenantMs = System.currentTimeMillis(),
            ),
        )
    }

    @Test
    fun `une horloge reculee ne ressuscite pas une alarme`() {
        assertFalse(
            AlarmState.estEncoreFraiche(
                enCours = true,
                dernierSigneMs = 10_000L,
                maintenantMs = 9_000L,
            ),
        )
    }
}
