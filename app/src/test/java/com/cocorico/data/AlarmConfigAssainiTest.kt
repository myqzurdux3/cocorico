package com.cocorico.data

import com.cocorico.ring.NiveauxVolume
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La configuration est persistée, donc relue telle qu'elle a été écrite — y
 * compris par une version antérieure, ou par un fichier abîmé. Une heure hors
 * bornes ne fait pas échouer bruyamment : elle fait lever une
 * `DateTimeException` au fond de `NextOccurrenceCalculator`, exception que
 * tous les appelants de `schedule()` avalent. Résultat : plus d'alarme, et
 * rien à l'écran pour le dire.
 */
class AlarmConfigAssainiTest {

    @Test fun `une configuration valide traverse sans changer`() {
        val config = AlarmConfig.DEFAULT.copy(hour = 7, minute = 30, volumeMaxPourcent = 80)
        assertEquals(config, config.assaini())
    }

    @Test fun `une heure hors bornes est ramenee dans le cadran`() {
        assertEquals(23, AlarmConfig.DEFAULT.copy(hour = 24).assaini().hour)
        assertEquals(23, AlarmConfig.DEFAULT.copy(hour = 99).assaini().hour)
        assertEquals(0, AlarmConfig.DEFAULT.copy(hour = -1).assaini().hour)
    }

    @Test fun `une minute hors bornes est ramenee dans l heure`() {
        assertEquals(59, AlarmConfig.DEFAULT.copy(minute = 60).assaini().minute)
        assertEquals(0, AlarmConfig.DEFAULT.copy(minute = -30).assaini().minute)
    }

    @Test fun `un plafond de volume corrompu repasse au dessus du plancher`() {
        // Le plancher est une décision produit : sous 50 %, l'alarme cesse
        // d'être une alarme. Une valeur persistée plus basse ne doit jamais
        // produire un réveil qu'on n'entend pas.
        assertEquals(
            NiveauxVolume.POURCENT_MINIMAL,
            AlarmConfig.DEFAULT.copy(volumeMaxPourcent = 0).assaini().volumeMaxPourcent,
        )
        assertEquals(
            NiveauxVolume.POURCENT_MAXIMAL,
            AlarmConfig.DEFAULT.copy(volumeMaxPourcent = 900).assaini().volumeMaxPourcent,
        )
    }

    @Test fun `l assainissement ne touche a rien d autre`() {
        val config = AlarmConfig.DEFAULT.copy(
            hour = 42,
            ringtoneId = "sirene",
            challengeId = ChallengeId.POMPES,
            armed = true,
            cleApi = "peu importe",
        )
        val assaini = config.assaini()
        assertEquals("sirene", assaini.ringtoneId)
        assertEquals(ChallengeId.POMPES, assaini.challengeId)
        assertEquals(true, assaini.armed)
        assertEquals("peu importe", assaini.cleApi)
        assertEquals(config.days, assaini.days)
    }
}
