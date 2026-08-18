package com.cocorico.ui

import com.cocorico.data.ChallengeId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le mode Sur mesure était rapporté comme « Calculs » sur l'écran de victoire
 * et comme « Maths » dans les statistiques : les deux écrans traduisaient
 * l'identifiant chacun de leur côté, et aucun des deux ne connaissait
 * [ChallengeId.COMBINE]. Constaté sur l'appareil le 18 août 2026, après un
 * vrai réveil en Sur mesure.
 */
class LibelleDefiTest {

    @Test
    fun `chaque defi a son nom`() {
        assertEquals("Calculs", LibelleDefi.libelle(ChallengeId.MATHS.name))
        assertEquals("Pompes", LibelleDefi.libelle(ChallengeId.POMPES.name))
        assertEquals("Photo", LibelleDefi.libelle(ChallengeId.PHOTO.name))
        assertEquals("Sur mesure", LibelleDefi.libelle(ChallengeId.COMBINE.name))
    }

    /** Un historique écrit par une version future ne doit pas casser l'écran. */
    @Test
    fun `un identifiant inconnu retombe sur les calculs`() {
        assertEquals("Calculs", LibelleDefi.libelle("PLONGEON"))
        assertEquals("Calculs", LibelleDefi.libelle(""))
    }

    /**
     * Le renoncement est un **suffixe**, pas un remplacement : l'écran de
     * victoire affichait « Calculs (renoncé) » quel que soit le défi réglé, ce
     * qui effaçait ce que l'utilisateur avait prévu de faire.
     */
    @Test
    fun `le renoncement s'ajoute sans effacer le defi regle`() {
        assertEquals(
            "Pompes (renoncé)",
            LibelleDefi.avecRenoncement(ChallengeId.POMPES.name, abandon = true),
        )
        assertEquals(
            "Sur mesure (renoncé)",
            LibelleDefi.avecRenoncement(ChallengeId.COMBINE.name, abandon = true),
        )
    }

    @Test
    fun `sans renoncement le nom reste nu`() {
        assertEquals(
            "Sur mesure",
            LibelleDefi.avecRenoncement(ChallengeId.COMBINE.name, abandon = false),
        )
    }
}
