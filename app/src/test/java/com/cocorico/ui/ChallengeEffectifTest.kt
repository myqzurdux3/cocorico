package com.cocorico.ui

import com.cocorico.data.ChallengeId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C'est cette fonction qui garantit qu'aucun réveil ne laisse l'utilisateur
 * devant une alarme qu'il ne peut pas arrêter : chaque défi qui dépend d'une
 * capacité de l'appareil doit retomber sur les calculs dès que cette capacité
 * manque, jamais l'inverse.
 */
class ChallengeEffectifTest {

    @Test
    fun `les calculs sonnent toujours tels quels`() {
        assertEquals(ChallengeId.MATHS, challengeEffectif(ChallengeId.MATHS))
        assertEquals(
            ChallengeId.MATHS,
            challengeEffectif(
                ChallengeId.MATHS,
                capteurPompesDisponible = false,
                permissionCameraAccordee = false,
                camerasDisponibles = false,
            ),
        )
    }

    @Test
    fun `pompes avec capteur disponible reste pompes`() {
        assertEquals(
            ChallengeId.POMPES,
            challengeEffectif(ChallengeId.POMPES, capteurPompesDisponible = true),
        )
    }

    @Test
    fun `pompes sans capteur retombe sur les calculs`() {
        assertEquals(
            ChallengeId.MATHS,
            challengeEffectif(ChallengeId.POMPES, capteurPompesDisponible = false),
        )
    }

    @Test
    fun `photo avec permission et camera reste photo`() {
        assertEquals(
            ChallengeId.PHOTO,
            challengeEffectif(
                ChallengeId.PHOTO,
                permissionCameraAccordee = true,
                camerasDisponibles = true,
            ),
        )
    }

    @Test
    fun `photo sans permission retombe sur les calculs meme si la camera existe`() {
        assertEquals(
            ChallengeId.MATHS,
            challengeEffectif(
                ChallengeId.PHOTO,
                permissionCameraAccordee = false,
                camerasDisponibles = true,
            ),
        )
    }

    @Test
    fun `photo avec permission mais sans camera exploitable retombe sur les calculs`() {
        assertEquals(
            ChallengeId.MATHS,
            challengeEffectif(
                ChallengeId.PHOTO,
                permissionCameraAccordee = true,
                camerasDisponibles = false,
            ),
        )
    }

    @Test
    fun `photo sans rien fourni retombe sur les calculs par defaut`() {
        assertEquals(ChallengeId.MATHS, challengeEffectif(ChallengeId.PHOTO))
    }
}
