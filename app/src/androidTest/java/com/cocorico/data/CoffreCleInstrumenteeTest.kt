package com.cocorico.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le chiffrement de la clé d'API contre le vrai `AndroidKeyStore`.
 *
 * Rien de ceci ne se vérifie hors d'un appareil : le Keystore n'existe pas sur
 * la machine de test, et un `Cipher` bouchonné prouverait seulement que le
 * bouchon fonctionne.
 *
 * **Toutes les clés utilisées ici sont inventées.** Aucune clé réelle n'entre
 * dans ce fichier, ni dans les traces qu'il produit.
 *
 * Se lance avec `./gradlew connectedDebugAndroidTest`. Aucune alarme n'est
 * programmée, aucun son n'est émis.
 */
@RunWith(AndroidJUnit4::class)
class CoffreCleInstrumenteeTest {

    private val cleFactice = "AIzaSy-CECI-EST-UNE-FAUSSE-CLE-DE-TEST-000"

    @Test
    fun une_cle_chiffree_se_relit_identique() {
        val enveloppe = CoffreCle.chiffrer(cleFactice)!!
        assertEquals(cleFactice, CoffreCle.dechiffrer(enveloppe))
    }

    @Test
    fun la_cle_n_apparait_jamais_en_clair_dans_l_enveloppe() {
        // C'est toute la raison d'être de ce fichier : ce qui est écrit sur le
        // disque ne doit pas contenir la clé.
        val enveloppe = CoffreCle.chiffrer(cleFactice)!!
        assertFalse(enveloppe.contains(cleFactice))
        assertFalse(enveloppe.contains("AIzaSy"))
        assertTrue(EnveloppeCle.estEnveloppe(enveloppe))
    }

    @Test
    fun deux_chiffrements_de_la_meme_cle_different() {
        // Vecteur d'initialisation tiré à chaque fois : sans ça, deux
        // utilisateurs ayant la même clé produiraient le même stockage, et une
        // clé inchangée serait reconnaissable à son enveloppe.
        val a = CoffreCle.chiffrer(cleFactice)!!
        val b = CoffreCle.chiffrer(cleFactice)!!
        assertNotEquals(a, b)
        assertEquals(CoffreCle.dechiffrer(a), CoffreCle.dechiffrer(b))
    }

    @Test
    fun une_enveloppe_abimee_ne_fait_pas_lever() {
        // Le stockage peut être tronqué, ou la clé Keystore perdue après une
        // restauration. Ce chemin doit rendre « pas de clé », jamais planter :
        // il est traversé au chargement de la configuration, dont l'échec
        // ferait disparaître l'alarme.
        assertEquals(null, CoffreCle.dechiffrer("cocorico-cle:v1:AAAA:BBBB"))
        assertEquals("", CoffreCle.lire("cocorico-cle:v1:AAAA:BBBB"))
    }

    @Test
    fun une_cle_heritee_en_clair_reste_lisible() {
        // Migration : la valeur écrite par les versions précédentes n'est pas
        // une enveloppe et doit continuer de fonctionner jusqu'à la réécriture.
        assertEquals(cleFactice, CoffreCle.lire(cleFactice))
        assertEquals("", CoffreCle.lire(""))
    }

    @Test
    fun le_cycle_complet_d_ecriture_et_de_lecture_conserve_la_cle() {
        assertEquals(cleFactice, CoffreCle.lire(CoffreCle.ecrire(cleFactice)))
        assertEquals("", CoffreCle.ecrire(""))
    }
}
