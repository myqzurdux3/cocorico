package com.cocorico.challenge.pompes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompteurPompesTest {

    /** Téléphone à plat, immobile : la position est bonne. */
    private fun echantillon(
        proche: Boolean,
        t: Long,
        inclinaison: Float = 2f,
        ecart: Float = 0.2f,
    ) = EchantillonPompe(
        procheDuCapteur = proche,
        inclinaisonDegres = inclinaison,
        ecartGravite = ecart,
        tMillis = t,
    )

    /** Une répétition complète : loin, proche pendant 400 ms, loin. */
    private fun CompteurPompes.pompe(depart: Long) {
        onEchantillon(echantillon(proche = false, t = depart))
        onEchantillon(echantillon(proche = true, t = depart + 400))
        onEchantillon(echantillon(proche = true, t = depart + 800))
        onEchantillon(echantillon(proche = false, t = depart + 1200))
    }

    @Test
    fun `dix repetitions correctes comptent dix`() {
        val c = CompteurPompes(total = 10)
        repeat(10) { i -> c.pompe(depart = 1_000L + i * 2_000L) }
        assertEquals(10, c.comptees.value)
        assertTrue(c.isSolved.value)
    }

    @Test
    fun `un telephone immobile sans franchissement ne compte rien`() {
        val c = CompteurPompes(total = 10)
        repeat(20) { i -> c.onEchantillon(echantillon(proche = false, t = 1_000L + i * 500L)) }
        assertEquals(0, c.comptees.value)
        assertEquals(EtatPompes.PRET, c.etat.value)
    }

    @Test
    fun `une main qui passe trop vite ne compte pas`() {
        val c = CompteurPompes(total = 10)
        // 300 ms de cycle total, sous la borne de 600 ms
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        c.onEchantillon(echantillon(proche = true, t = 1_100))
        c.onEchantillon(echantillon(proche = false, t = 1_300))
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `un effleurement du capteur ne compte pas`() {
        val c = CompteurPompes(total = 10)
        // cycle assez long, mais seulement 100 ms en position basse
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        c.onEchantillon(echantillon(proche = true, t = 1_600))
        c.onEchantillon(echantillon(proche = false, t = 1_700))
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `un telephone incline ne compte pas`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 1_000, inclinaison = 40f))
        c.onEchantillon(echantillon(proche = true, t = 1_400, inclinaison = 40f))
        c.onEchantillon(echantillon(proche = false, t = 1_800, inclinaison = 40f))
        assertEquals(0, c.comptees.value)
        assertEquals(EtatPompes.ATTENTE_POSITION, c.etat.value)
    }

    @Test
    fun `un telephone agite ne compte pas`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 1_000, ecart = 4f))
        c.onEchantillon(echantillon(proche = true, t = 1_400, ecart = 4f))
        c.onEchantillon(echantillon(proche = false, t = 1_800, ecart = 4f))
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `une descente sans remontee ne compte pas`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        c.onEchantillon(echantillon(proche = true, t = 1_400))
        assertEquals(0, c.comptees.value)
        assertEquals(EtatPompes.BAS, c.etat.value)
    }

    @Test
    fun `une pause en milieu de serie ne perd pas les repetitions acquises`() {
        val c = CompteurPompes(total = 10)
        c.pompe(depart = 1_000)
        c.pompe(depart = 3_000)
        // trente secondes sans rien
        c.onEchantillon(echantillon(proche = false, t = 35_000))
        c.pompe(depart = 36_000)
        assertEquals(3, c.comptees.value)
    }

    @Test
    fun `la repetition validante est signalee a l appelant`() {
        val c = CompteurPompes(total = 10)
        assertFalse(c.onEchantillon(echantillon(proche = false, t = 1_000)))
        assertFalse(c.onEchantillon(echantillon(proche = true, t = 1_400)))
        assertTrue(c.onEchantillon(echantillon(proche = false, t = 2_200)))
    }

    @Test
    fun `au dela du total le compteur n avance plus`() {
        val c = CompteurPompes(total = 2)
        c.pompe(depart = 1_000)
        c.pompe(depart = 3_000)
        c.pompe(depart = 5_000)
        assertEquals(2, c.comptees.value)
        assertTrue(c.isSolved.value)
    }

    @Test
    fun `un cycle trop lent ne compte pas`() {
        val c = CompteurPompes(total = 10)
        // tenue basse suffisante (200 ms) mais cycle total de 8700 ms, au dela
        // de CYCLE_MAX_MS (8000 ms) : ce n'est plus une pompe
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        c.onEchantillon(echantillon(proche = true, t = 9_500))
        assertFalse(c.onEchantillon(echantillon(proche = false, t = 9_700)))
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `une position invalide depuis pret ramene a attente position`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        assertEquals(EtatPompes.PRET, c.etat.value)

        // le telephone est ramasse en position haute : incline et agite
        assertFalse(
            c.onEchantillon(
                echantillon(proche = false, t = 1_400, inclinaison = 40f, ecart = 4f),
            ),
        )
        assertEquals(EtatPompes.ATTENTE_POSITION, c.etat.value)
    }

    @Test
    fun `une position invalide pendant la descente ramene a attente position et exige une nouvelle position haute`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 1_000)) // PRET
        c.onEchantillon(echantillon(proche = true, t = 1_400)) // BAS, debutBas = 1400
        assertEquals(EtatPompes.BAS, c.etat.value)

        // le telephone est ramasse en pleine descente : tenue basse (200 ms) et
        // cycle (600 ms) seraient valides si on ignorait la position, la garde
        // doit quand meme s'appliquer
        assertFalse(c.onEchantillon(echantillon(proche = false, t = 1_600, ecart = 4f)))
        assertEquals(EtatPompes.ATTENTE_POSITION, c.etat.value)
        assertEquals(0, c.comptees.value)

        // la remontee ne compte pas tant qu'on n'est pas repasse par une
        // position haute valide
        assertFalse(c.onEchantillon(echantillon(proche = false, t = 2_000)))
        assertEquals(EtatPompes.PRET, c.etat.value)
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `une tenue basse exactement a la limite basse compte`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 800)) // PRET, debutCycle = 800
        c.onEchantillon(echantillon(proche = true, t = 1_400)) // BAS, debutBas = 1400
        // tenue basse exactement 150 ms (TENUE_BASSE_MIN_MS) et cycle de 750 ms,
        // dans la plage acceptee
        assertTrue(c.onEchantillon(echantillon(proche = false, t = 1_550)))
        assertEquals(1, c.comptees.value)
    }
}
