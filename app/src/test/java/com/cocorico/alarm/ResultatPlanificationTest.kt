package com.cocorico.alarm

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `schedule()` renvoyait `null` pour cinq situations très différentes, dont
 * deux qui signifient « l'utilisateur a demandé un réveil et n'en aura pas ».
 * Les appelants jetaient ce `null`. Ce type existe pour que l'information
 * survive jusqu'à quelqu'un capable d'en faire quelque chose.
 */
class ResultatPlanificationTest {

    @Test fun `une alarme programmee porte son instant et ne previent personne`() {
        val quand = LocalDateTime.of(2026, 8, 18, 6, 30)
        val resultat = ResultatPlanification.Programmee(quand)
        assertEquals(quand, resultat.prochaine)
        assertTrue(resultat.alarmePosee)
        assertFalse(resultat.doitAlerter)
    }

    @Test fun `une alarme desarmee est un choix, pas une panne`() {
        // L'utilisateur a lui-même désarmé : le lui annoncer serait du bruit.
        assertNull(ResultatPlanification.Desarmee.prochaine)
        assertFalse(ResultatPlanification.Desarmee.alarmePosee)
        assertFalse(ResultatPlanification.Desarmee.doitAlerter)
    }

    @Test fun `aucun jour actif est un choix aussi`() {
        assertFalse(ResultatPlanification.AucunJourActif.alarmePosee)
        assertFalse(ResultatPlanification.AucunJourActif.doitAlerter)
    }

    @Test fun `une permission retiree doit alerter`() {
        // Android 12 laisse retirer SCHEDULE_EXACT_ALARM après coup. L'alarme
        // reste armée à l'écran et ne sonnera jamais : c'est le seul cas où
        // l'application ment à l'utilisateur sans le savoir.
        assertFalse(ResultatPlanification.PermissionManquante.alarmePosee)
        assertTrue(ResultatPlanification.PermissionManquante.doitAlerter)
    }

    @Test fun `un echec systeme doit alerter`() {
        assertFalse(ResultatPlanification.EchecSysteme.alarmePosee)
        assertTrue(ResultatPlanification.EchecSysteme.doitAlerter)
    }

    @Test fun `seuls les echecs subis alertent`() {
        val tous = listOf(
            ResultatPlanification.Programmee(LocalDateTime.of(2026, 8, 18, 6, 30)),
            ResultatPlanification.Desarmee,
            ResultatPlanification.AucunJourActif,
            ResultatPlanification.PermissionManquante,
            ResultatPlanification.EchecSysteme,
        )
        // Un résultat qui alerte est nécessairement un résultat sans alarme
        // posée ; l'inverse est faux, et c'est tout l'intérêt de la distinction.
        assertTrue(tous.filter { it.doitAlerter }.none { it.alarmePosee })
        assertEquals(2, tous.count { it.doitAlerter })
    }
}
