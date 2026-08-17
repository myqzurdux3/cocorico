package com.cocorico.challenge.pompes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompteurPompesTest {

    private companion object {
        /** Cadence de l'accelerometre sur l'appareil : SENSOR_DELAY_GAME. */
        const val PAS_CAPTEUR_MS = 20L
    }

    /** Téléphone à plat, immobile : la position est bonne. */
    private fun echantillon(proche: Boolean, t: Long, inclinaison: Float = 2f, ecart: Float = 0.2f) = EchantillonPompe(
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
        // 300 ms depuis le dernier echantillon haut (t=1_000) jusqu'a la
        // remontee (t=1_300), sous la borne minimale de 600 ms — bien plus
        // que les 200 ms reellement passees en position basse (1_100 a
        // 1_300), mais la main repasse quand meme trop vite devant le capteur
        c.onEchantillon(echantillon(proche = false, t = 1_000))
        c.onEchantillon(echantillon(proche = true, t = 1_100))
        c.onEchantillon(echantillon(proche = false, t = 1_300))
        assertEquals(0, c.comptees.value)
    }

    /**
     * Flux d'echantillons tel que l'appareil le produit : l'accelerometre
     * alimente le compteur toutes les 20 ms (SENSOR_DELAY_GAME, voir
     * CapteurPompes.demarrer), en portant la valeur courante de la proximite.
     * Renvoie l'instant du dernier echantillon emis.
     */
    private fun CompteurPompes.fluxReel(depart: Long, proche: Boolean, dureeMs: Long): Long {
        var t = depart
        while (t < depart + dureeMs) {
            onEchantillon(echantillon(proche = proche, t = t))
            t += PAS_CAPTEUR_MS
        }
        return t - PAS_CAPTEUR_MS
    }

    @Test
    fun `un effleurement du capteur ne compte pas`() {
        val c = CompteurPompes(total = 10)
        // Une main qui passe 100 ms devant le capteur, sur un flux realiste :
        // la reference haute a ete rafraichie jusqu'a 20 ms avant la descente,
        // donc la duree depuis le dernier haut vaut la tenue basse a un
        // echantillon pres — 120 ms, tres en dessous des 600 ms exiges.
        val finHaut = c.fluxReel(depart = 1_000, proche = false, dureeMs = 1_000)
        val finBas = c.fluxReel(depart = finHaut + PAS_CAPTEUR_MS, proche = true, dureeMs = 100)
        c.onEchantillon(echantillon(proche = false, t = finBas + PAS_CAPTEUR_MS))
        assertEquals(0, c.comptees.value)
    }

    /**
     * Caracterise ce qui remplace l'ancienne garde anti-effleurement de 150 ms,
     * supprimee parce qu'elle etait inatteignable : sur un flux continu, la
     * duree depuis le dernier echantillon haut vaut la tenue basse plus un
     * echantillon, si bien que la borne de 600 ms interdit deja toute tenue
     * basse inferieure a 580 ms. Un seuil a 150 ms ne pouvait donc jamais
     * trancher quoi que ce soit sur l'appareil — il ne rejetait que des
     * sequences que seuls des tests aux echantillons epars savaient produire.
     */
    @Test
    fun `sur un flux realiste la plus courte tenue basse comptee est de 580 ms`() {
        val comptees = (20L..800L step PAS_CAPTEUR_MS).filter { tenueBasseMs ->
            val c = CompteurPompes(total = 10)
            val finHaut = c.fluxReel(depart = 1_000, proche = false, dureeMs = 1_000)
            val finBas = c.fluxReel(
                depart = finHaut + PAS_CAPTEUR_MS,
                proche = true,
                dureeMs = tenueBasseMs,
            )
            c.onEchantillon(echantillon(proche = false, t = finBas + PAS_CAPTEUR_MS))
            c.comptees.value == 1
        }
        assertEquals(580L, comptees.min())
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
        // tenue basse suffisante (200 ms) mais duree depuis le dernier
        // echantillon haut de 8700 ms, au dela de
        // DUREE_DEPUIS_DERNIER_HAUT_MAX_MS (8000 ms) : ce n'est plus une pompe
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

        // le telephone est ramasse en pleine descente : tenue basse (200 ms)
        // et duree depuis le dernier echantillon haut (600 ms) seraient
        // valides si on ignorait la position, la garde doit quand meme
        // s'appliquer
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
    fun `une duree exactement a la borne basse compte`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 800)) // PRET, debutTenueHaute = 800
        c.onEchantillon(echantillon(proche = true, t = 1_000)) // BAS
        // exactement 600 ms depuis le dernier echantillon haut
        // (DUREE_DEPUIS_DERNIER_HAUT_MIN_MS) : la borne est inclusive
        assertTrue(c.onEchantillon(echantillon(proche = false, t = 1_400)))
        assertEquals(1, c.comptees.value)
    }

    @Test
    fun `une duree juste sous la borne basse ne compte pas`() {
        val c = CompteurPompes(total = 10)
        c.onEchantillon(echantillon(proche = false, t = 800))
        c.onEchantillon(echantillon(proche = true, t = 1_000))
        // 599 ms depuis le dernier echantillon haut : un de moins que la borne
        assertFalse(c.onEchantillon(echantillon(proche = false, t = 1_399)))
        assertEquals(0, c.comptees.value)
    }

    @Test
    fun `plusieurs echantillons hauts avant la descente decalent la reference de duree`() {
        val c = CompteurPompes(total = 10)
        // Le telephone reste pose, position haute, pendant un moment avant
        // que la pompe ne commence vraiment : chaque echantillon haut recu
        // en etat PRET doit decaler la reference utilisee pour borner la
        // duree depuis le dernier haut, pas seulement le tout premier.
        c.onEchantillon(echantillon(proche = false, t = 0)) // ATTENTE_POSITION -> PRET
        c.onEchantillon(echantillon(proche = false, t = 4_000)) // toujours PRET, decale la reference
        c.onEchantillon(echantillon(proche = false, t = 9_000)) // toujours PRET, la decale encore
        c.onEchantillon(echantillon(proche = true, t = 9_100)) // BAS, debutBas = 9100

        // Avec la reference correctement decalee a 9_000 (dernier echantillon
        // haut), la duree jusqu'a la remontee est de 700 ms : dans la plage
        // acceptee, la repetition compte. Sans ce decalage continu — c'est-a-
        // dire si la reference etait restee a 0, fixee une bonne fois pour
        // toutes a l'entree en PRET — cette duree serait de 9_700 ms, bien
        // au-dela de la borne maximale, et la repetition ne compterait pas :
        // ce test rougirait si la ligne qui rafraichit la reference a chaque
        // echantillon haut disparaissait.
        assertTrue(c.onEchantillon(echantillon(proche = false, t = 9_700)))
        assertEquals(1, c.comptees.value)
    }
}
