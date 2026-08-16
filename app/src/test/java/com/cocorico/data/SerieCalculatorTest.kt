package com.cocorico.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SerieCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun record(date: LocalDate, retardSecondes: Int): WakeRecord {
        val alarme = LocalDateTime.of(date, java.time.LocalTime.of(6, 30))
            .atZone(zone).toInstant().toEpochMilli()
        return WakeRecord(
            id = 0,
            alarmeAt = alarme,
            resoluAt = alarme + retardSecondes * 1000L,
            erreurs = 0,
            triches = 0,
        )
    }

    @Test
    fun `une liste vide donne une serie de zero`() {
        assertEquals(0, SerieCalculator.serie(emptyList(), zone))
    }

    @Test
    fun `trois jours consecutifs donnent une serie de trois`() {
        val records = listOf(
            record(LocalDate.of(2026, 8, 12), 60),
            record(LocalDate.of(2026, 8, 13), 60),
            record(LocalDate.of(2026, 8, 14), 60),
        )
        assertEquals(3, SerieCalculator.serie(records, zone))
    }

    @Test
    fun `un jour manquant coupe la serie et seule la plus recente compte`() {
        val records = listOf(
            record(LocalDate.of(2026, 8, 10), 60),
            record(LocalDate.of(2026, 8, 11), 60),
            record(LocalDate.of(2026, 8, 14), 60),
        )
        assertEquals(1, SerieCalculator.serie(records, zone))
    }

    @Test
    fun `deux reveils le meme jour ne comptent qu une fois`() {
        val records = listOf(
            record(LocalDate.of(2026, 8, 14), 60),
            record(LocalDate.of(2026, 8, 14), 90),
        )
        assertEquals(1, SerieCalculator.serie(records, zone))
    }

    @Test
    fun `le retard moyen est la moyenne des ecarts en secondes`() {
        val records = listOf(
            record(LocalDate.of(2026, 8, 13), 60),
            record(LocalDate.of(2026, 8, 14), 120),
        )
        assertEquals(90, SerieCalculator.retardMoyenSecondes(records))
    }

    @Test
    fun `le retard moyen d une liste vide vaut zero`() {
        assertEquals(0, SerieCalculator.retardMoyenSecondes(emptyList()))
    }
}
