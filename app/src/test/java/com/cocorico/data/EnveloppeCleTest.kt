package com.cocorico.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le format sous lequel la clé d'API est rangée sur le disque. Partie pure du
 * chiffrement : l'emballage et le désemballage se testent sans Android, seule
 * l'opération cryptographique elle-même a besoin du Keystore.
 *
 * Une valeur qui n'est pas une enveloppe est une clé écrite en clair par une
 * version antérieure. Il faut savoir la reconnaître, sans quoi la migration
 * chiffrerait du texte déjà chiffré, ou rendrait une clé illisible.
 */
class EnveloppeCleTest {

    @Test fun `une enveloppe se relit telle qu elle a ete ecrite`() {
        val emballee = EnveloppeCle.emballer(vecteur = byteArrayOf(1, 2, 3), chiffre = byteArrayOf(9, 8))
        val relue = EnveloppeCle.deballer(emballee)!!
        assertEquals(listOf<Byte>(1, 2, 3), relue.vecteur.toList())
        assertEquals(listOf<Byte>(9, 8), relue.chiffre.toList())
    }

    @Test fun `une enveloppe est reconnaissable`() {
        assertTrue(EnveloppeCle.estEnveloppe(EnveloppeCle.emballer(byteArrayOf(1), byteArrayOf(2))))
    }

    @Test fun `une cle en clair n est pas prise pour une enveloppe`() {
        // Le cas de la migration : ces valeurs viennent d'une version qui
        // écrivait la clé telle quelle. Les prendre pour des enveloppes les
        // rendrait indéchiffrables, donc perdues.
        assertFalse(EnveloppeCle.estEnveloppe("AIzaSyEXEMPLE-factice-pour-le-test"))
        assertFalse(EnveloppeCle.estEnveloppe(""))
        assertFalse(EnveloppeCle.estEnveloppe("cocorico:v1:pas-une-enveloppe"))
    }

    @Test fun `une enveloppe abimee ne se deballe pas`() {
        // Un fichier tronqué, ou une valeur bricolée à la main : on rend `null`
        // plutôt que de lever. Une clé perdue se ressaisit ; une exception au
        // fond de la lecture de configuration ferait disparaître l'alarme.
        assertNull(EnveloppeCle.deballer("cocorico-cle:v1:pas-du-base64!!"))
        assertNull(EnveloppeCle.deballer("cocorico-cle:v1:AAAA"))
        assertNull(EnveloppeCle.deballer("n'importe quoi"))
        assertNull(EnveloppeCle.deballer(""))
    }

    @Test fun `le vecteur d initialisation n est jamais vide`() {
        // Un GCM sans vecteur réutiliserait le même flux pour deux clés.
        assertNull(EnveloppeCle.deballer(EnveloppeCle.emballer(byteArrayOf(), byteArrayOf(1))))
    }
}
