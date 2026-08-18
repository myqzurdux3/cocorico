package com.cocorico.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MainActivity` est exportée : n'importe quelle application peut la lancer.
 * Sans cette vérification, un intent forgé affichait l'écran de victoire et
 * remettait `alarmeEnCours` à faux — masquant le seul chemin visible vers
 * `AlarmActivity` pendant que la sonnerie continuait. La preuve d'origine est
 * le paquet créateur du jeton : seul notre processus peut en fabriquer un.
 */
class VictoireLegitimeTest {

    @Test
    fun `une victoire annoncee par l application elle meme est acceptee`() {
        assertTrue(victoireLegitime(true, "com.cocorico", "com.cocorico"))
    }

    @Test
    fun `une victoire annoncee par une autre application est refusee`() {
        assertFalse(victoireLegitime(true, "com.pirate", "com.cocorico"))
    }

    @Test
    fun `une victoire sans jeton du tout est refusee`() {
        assertFalse(victoireLegitime(true, null, "com.cocorico"))
    }

    @Test
    fun `sans indicateur de victoire le jeton ne suffit pas`() {
        assertFalse(victoireLegitime(false, "com.cocorico", "com.cocorico"))
    }
}
