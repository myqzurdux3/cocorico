package com.cocorico.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'instant où l'alarme a réellement sonné, pour que la durée enregistrée dans
 * l'historique soit celle du réveil et non celle de la dernière fenêtre.
 *
 * Piège qui a coûté une fausse piste : `CLE_DERNIER_SIGNE` ne convient **pas**.
 * C'est un signe de vie, réécrit par `AlarmReceiver` à chaque passage du filet
 * de secours, donc toutes les 30 secondes. S'en servir aurait enregistré une
 * demi-minute pour tous les réveils, quelle qu'ait été leur durée réelle — un
 * chiffre faux et plausible, le pire des deux mondes.
 */
class InstantDeclenchementTest {

    @Test fun `le premier declenchement retient l instant courant`() {
        assertEquals(1_000L, AlarmState.instantARetenir(existant = 0L, maintenant = 1_000L))
    }

    @Test fun `une relance du service ne redemarre pas le chronometre`() {
        // START_STICKY, un secours, un processus tué et relancé : le service
        // repasse par ce chemin plusieurs fois pour une seule alarme. Réécrire
        // ferait repartir la durée de zéro à chaque fois.
        assertEquals(1_000L, AlarmState.instantARetenir(existant = 1_000L, maintenant = 9_999L))
    }

    @Test fun `un horodatage absurde est remplace plutot que conserve`() {
        // Une valeur négative ou nulle ne peut venir que d'un fichier abîmé ;
        // la garder produirait une durée de plusieurs décennies dans les stats.
        assertEquals(500L, AlarmState.instantARetenir(existant = -42L, maintenant = 500L))
        assertEquals(500L, AlarmState.instantARetenir(existant = 0L, maintenant = 500L))
    }
}
