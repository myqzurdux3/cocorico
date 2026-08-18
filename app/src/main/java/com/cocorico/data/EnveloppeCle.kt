package com.cocorico.data

import java.util.Base64

/**
 * Le format sous lequel la clé d'API chiffrée est rangée sur le disque.
 *
 * Classe pure, sans import `android.*` : seule l'opération cryptographique a
 * besoin du Keystore, l'emballage n'en a pas besoin et se teste sans appareil.
 *
 * Le préfixe n'est pas décoratif. La clé était écrite en clair par les versions
 * précédentes, et le même champ doit continuer de se relire : c'est lui qui
 * distingue une enveloppe d'une clé héritée. Sans cette marque, la migration
 * chiffrerait du texte déjà chiffré, ou tenterait de déchiffrer une clé qui ne
 * l'a jamais été.
 *
 * `java.util.Base64` et non `android.util.Base64` : le premier existe depuis
 * l'API 26, sous le `minSdk 28` du projet, et existe aussi sur la machine de
 * test — ce qui rend ce fichier vérifiable sans téléphone.
 */
object EnveloppeCle {

    /** Porte la version : changer de format un jour ne doit pas rendre l'ancien illisible. */
    private const val PREFIXE = "cocorico-cle:v1:"

    data class Enveloppe(val vecteur: ByteArray, val chiffre: ByteArray) {
        // `ByteArray` compare par référence : sans ces deux redéfinitions, deux
        // enveloppes identiques seraient déclarées différentes.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Enveloppe) return false
            return vecteur.contentEquals(other.vecteur) && chiffre.contentEquals(other.chiffre)
        }

        override fun hashCode(): Int = 31 * vecteur.contentHashCode() + chiffre.contentHashCode()
    }

    fun emballer(vecteur: ByteArray, chiffre: ByteArray): String {
        val encodeur = Base64.getEncoder()
        return PREFIXE + encodeur.encodeToString(vecteur) + ":" + encodeur.encodeToString(chiffre)
    }

    /** Vrai si cette valeur a été écrite chiffrée ; faux pour une clé héritée en clair. */
    fun estEnveloppe(valeur: String): Boolean = valeur.startsWith(PREFIXE)

    /**
     * Rend `null` sur toute valeur qui n'est pas une enveloppe intacte, plutôt
     * que de lever : cette lecture a lieu au fond du chargement de la
     * configuration, dont tous les appelants avalent les exceptions. Une clé
     * perdue se ressaisit en dix secondes ; une exception ici ferait disparaître
     * l'alarme sans un mot.
     */
    fun deballer(valeur: String): Enveloppe? {
        if (!estEnveloppe(valeur)) return null
        val corps = valeur.removePrefix(PREFIXE).split(":")
        if (corps.size != 2) return null
        return runCatching {
            val decodeur = Base64.getDecoder()
            val vecteur = decodeur.decode(corps[0])
            val chiffre = decodeur.decode(corps[1])
            // Un GCM sans vecteur d'initialisation réutiliserait le même flux
            // pour deux clés successives : c'est une enveloppe invalide, pas
            // une enveloppe pauvre.
            if (vecteur.isEmpty() || chiffre.isEmpty()) null else Enveloppe(vecteur, chiffre)
        }.getOrNull()
    }
}
