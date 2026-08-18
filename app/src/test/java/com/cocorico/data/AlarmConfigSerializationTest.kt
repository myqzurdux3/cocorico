package com.cocorico.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class AlarmConfigSerializationTest {

    @Test
    fun `un aller-retour preserve les jours`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        assertEquals(days, AlarmConfigCodec.decodeDays(AlarmConfigCodec.encodeDays(days)))
    }

    @Test
    fun `un ensemble vide s encode et se decode sans erreur`() {
        assertTrue(AlarmConfigCodec.decodeDays(AlarmConfigCodec.encodeDays(emptySet())).isEmpty())
    }

    @Test
    fun `une valeur corrompue est ignoree au lieu de faire planter`() {
        assertEquals(
            setOf(DayOfWeek.MONDAY),
            AlarmConfigCodec.decodeDays("MONDAY,PLUTODAY,,42"),
        )
    }
}
