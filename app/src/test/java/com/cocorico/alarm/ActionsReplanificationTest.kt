package com.cocorico.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le filtre d'actions de [BootReceiver], isolé pour être testable : c'est lui
 * qui décide si l'alarme du lendemain est reprogrammée ou perdue.
 */
class ActionsReplanificationTest {

    @Test fun `le demarrage replanifie`() {
        assertTrue(ActionsReplanification.doitReplanifier("android.intent.action.BOOT_COMPLETED"))
    }

    @Test fun `une mise a jour de l application replanifie`() {
        // Android annule les alarmes d'une application quand on la met à jour.
        assertTrue(ActionsReplanification.doitReplanifier("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test fun `un changement de fuseau replanifie`() {
        // `setAlarmClock` mémorise un instant absolu. Après un vol, cet instant
        // ne correspond plus à l'heure murale choisie : sans replanification,
        // le réveil sonne au mauvais moment, ou saute.
        assertTrue(ActionsReplanification.doitReplanifier("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test fun `un reglage manuel de l horloge replanifie`() {
        assertTrue(ActionsReplanification.doitReplanifier("android.intent.action.TIME_SET"))
    }

    @Test fun `une action inconnue ou absente ne replanifie pas`() {
        // Le récepteur est exporté : n'importe qui peut le réveiller avec
        // n'importe quelle action. Replanifier sur commande étrangère
        // déplacerait l'alarme sans que l'utilisateur l'ait demandé.
        assertFalse(ActionsReplanification.doitReplanifier("android.intent.action.VIEW"))
        assertFalse(ActionsReplanification.doitReplanifier(""))
        assertFalse(ActionsReplanification.doitReplanifier(null))
    }
}
