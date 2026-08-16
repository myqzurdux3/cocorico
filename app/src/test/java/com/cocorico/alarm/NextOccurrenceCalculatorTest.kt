package com.cocorico.alarm

import com.cocorico.data.AlarmConfig
import com.cocorico.data.ChallengeId
import com.cocorico.data.Difficulty
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextOccurrenceCalculatorTest {

    private fun config(vararg days: DayOfWeek, hour: Int = 6, minute: Int = 30) = AlarmConfig(
        hour = hour,
        minute = minute,
        days = days.toSet(),
        ringtoneId = "klaxon",
        challengeId = ChallengeId.MATHS,
        difficulty = Difficulty.MOYEN,
        armed = true,
    )

    @Test
    fun `sonne le jour meme si l heure n est pas encore passee`() {
        // mercredi 15 h 00
        val from = LocalDateTime.of(2026, 8, 19, 15, 0)
        val next = NextOccurrenceCalculator.next(config(DayOfWeek.WEDNESDAY, hour = 22, minute = 0), from)
        assertEquals(LocalDateTime.of(2026, 8, 19, 22, 0), next)
    }

    @Test
    fun `passe au jour actif suivant si l heure est deja passee`() {
        // mercredi 8 h 00, alarme a 6 h 30 les mercredi et vendredi
        val from = LocalDateTime.of(2026, 8, 19, 8, 0)
        val next = NextOccurrenceCalculator.next(
            config(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), from,
        )
        assertEquals(LocalDateTime.of(2026, 8, 21, 6, 30), next)
    }

    @Test
    fun `boucle sur la semaine suivante quand un seul jour est actif`() {
        // mercredi 8 h 00, alarme le mercredi uniquement
        val from = LocalDateTime.of(2026, 8, 19, 8, 0)
        val next = NextOccurrenceCalculator.next(config(DayOfWeek.WEDNESDAY), from)
        assertEquals(LocalDateTime.of(2026, 8, 26, 6, 30), next)
    }

    @Test
    fun `retourne null quand aucun jour n est actif`() {
        val from = LocalDateTime.of(2026, 8, 19, 8, 0)
        assertNull(NextOccurrenceCalculator.next(config(), from))
    }

    @Test
    fun `une occurrence exactement a l instant courant est consideree passee`() {
        val from = LocalDateTime.of(2026, 8, 19, 6, 30)
        val next = NextOccurrenceCalculator.next(config(DayOfWeek.WEDNESDAY), from)
        assertEquals(LocalDateTime.of(2026, 8, 26, 6, 30), next)
    }
}
