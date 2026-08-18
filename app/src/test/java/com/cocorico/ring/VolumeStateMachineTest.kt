package com.cocorico.ring

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeStateMachineTest {

    private val transitions = mutableListOf<VolumeState>()
    private val machine = VolumeStateMachine { transitions += it }

    @Test
    fun `l alarme demarre a plein volume`() {
        assertEquals(VolumeState.PLEIN, machine.state)
        assertEquals(emptyList<VolumeState>(), transitions)
    }

    @Test
    fun `prendre le telephone baisse le volume`() {
        machine.onPhonePrisEnMain()
        assertEquals(VolumeState.BAISSE, machine.state)
        assertEquals(listOf(VolumeState.BAISSE), transitions)
    }

    @Test
    fun `reprendre le telephone deja en main ne renotifie pas`() {
        machine.onPhonePrisEnMain()
        machine.onPhonePrisEnMain()
        machine.onInteraction()
        assertEquals(listOf(VolumeState.BAISSE), transitions)
    }

    @Test
    fun `l inactivite fait remonter le volume`() {
        machine.onPhonePrisEnMain()
        machine.onInactiviteExpiree()
        assertEquals(VolumeState.PLEIN, machine.state)
        assertEquals(listOf(VolumeState.BAISSE, VolumeState.PLEIN), transitions)
    }

    @Test
    fun `reprendre l activite apres remontee rebaisse le volume`() {
        machine.onPhonePrisEnMain()
        machine.onInactiviteExpiree()
        machine.onInteraction()
        assertEquals(VolumeState.BAISSE, machine.state)
        assertEquals(
            listOf(VolumeState.BAISSE, VolumeState.PLEIN, VolumeState.BAISSE),
            transitions,
        )
    }
}
