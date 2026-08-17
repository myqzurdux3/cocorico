package com.cocorico.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le filet de secours retombe sur le service toutes les 30 s. S'il relance la
 * lecture alors qu'un démarrage est encore en vol, deux MediaPlayer sonnent en
 * même temps et un seul est arrêtable. S'il ne la relance jamais, un premier
 * démarrage raté laisse un réveil muet.
 */
class RelanceLectureTest {

    @Test
    fun `un demarrage encore en vol interdit la relance`() {
        // La fenêtre du bug : la configuration est en cours de lecture, donc
        // rien ne sonne encore, et pourtant tout est déjà lancé.
        assertFalse(
            RelanceLecture.doitRelancer(demarrageEnCours = true, sonneEffectivement = false),
        )
    }

    @Test
    fun `une sonnerie deja en cours interdit la relance`() {
        assertFalse(
            RelanceLecture.doitRelancer(demarrageEnCours = false, sonneEffectivement = true),
        )
    }

    @Test
    fun `un demarrage termine sans sonnerie declenche la relance`() {
        assertTrue(
            RelanceLecture.doitRelancer(demarrageEnCours = false, sonneEffectivement = false),
        )
    }

    @Test
    fun `un demarrage en vol qui sonne deja n est pas relance`() {
        assertFalse(
            RelanceLecture.doitRelancer(demarrageEnCours = true, sonneEffectivement = true),
        )
    }
}
