package com.cocorico.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffre la clé d'API de l'utilisateur avec une clé qui ne quitte jamais le
 * matériel.
 *
 * Elle était rangée en clair dans le DataStore : exclue de la sauvegarde et
 * masquée à l'affichage, mais lisible par quiconque extrayait le répertoire de
 * données. Ici, la clé de chiffrement vit dans l'`AndroidKeyStore`, adossée au
 * matériel sécurisé quand l'appareil en a un ; elle n'est pas extractible, et
 * la sauvegarde ne l'emporte pas.
 *
 * Aucune dépendance ajoutée : `javax.crypto` et `java.security` suffisent.
 * `androidx.security:security-crypto` ferait la même chose en tirant une
 * bibliothèque de plus pour un unique champ.
 *
 * **Ne jamais exiger le déverrouillage.** L'alarme doit pouvoir lire cette clé
 * à six heures du matin, écran verrouillé, sans que personne ne touche le
 * téléphone : la clé Keystore est donc créée sans authentification utilisateur
 * et sans `setUnlockedDeviceRequired`.
 *
 * **Aucune fonction ne lève.** Toutes rendent `null` en cas d'échec. Une clé
 * illisible — Keystore réinitialisé après une restauration, matériel changé —
 * se ressaisit en dix secondes ; une exception dans le chargement de la
 * configuration ferait disparaître l'alarme sans un mot.
 */
object CoffreCle {

    private const val ALIAS = "cocorico_cle_api"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAILLE_TAG_BITS = 128
    private const val TAG = "Cocorico"

    /** Chiffre [clair] et rend l'enveloppe à stocker, ou `null` si le Keystore refuse. */
    fun chiffrer(clair: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, cleDeChiffrement())
        EnveloppeCle.emballer(cipher.iv, cipher.doFinal(clair.toByteArray()))
    }.onFailure { Log.w(TAG, "Chiffrement de la clé d'API impossible : $it") }.getOrNull()

    /** Rend la clé en clair, ou `null` si l'enveloppe est abîmée ou la clé Keystore perdue. */
    fun dechiffrer(enveloppe: String): String? = runCatching {
        val ouverte = EnveloppeCle.deballer(enveloppe) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            cleDeChiffrement(),
            GCMParameterSpec(TAILLE_TAG_BITS, ouverte.vecteur),
        )
        String(cipher.doFinal(ouverte.chiffre))
    }.onFailure { Log.w(TAG, "Déchiffrement de la clé d'API impossible : $it") }.getOrNull()

    /**
     * Ce que doit rendre la lecture de la configuration, quel que soit l'état
     * du stockage.
     *
     * Une valeur qui n'est pas une enveloppe vient d'une version antérieure et
     * est rendue telle quelle : la clé reste utilisable pendant la migration.
     * Une enveloppe indéchiffrable rend la chaîne vide, ce que l'application
     * traite déjà partout comme « pas de clé » — le défi photo n'est alors pas
     * proposé, au lieu d'échouer à chaque photo sans explication.
     */
    fun lire(stocke: String): String = when {
        stocke.isEmpty() -> ""
        EnveloppeCle.estEnveloppe(stocke) -> dechiffrer(stocke) ?: ""
        else -> stocke
    }

    /**
     * Ce qu'il faut écrire sur le disque. Un échec de chiffrement rend la
     * chaîne vide plutôt que la clé en clair : mieux vaut redemander la clé que
     * la ranger en clair à l'insu de l'utilisateur, alors même que ce fichier
     * existe pour éviter ça.
     */
    fun ecrire(clair: String): String = if (clair.isEmpty()) "" else chiffrer(clair) ?: ""

    private fun cleDeChiffrement(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generateur.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Pas d'authentification : l'alarme lit cette clé écran
                // verrouillé, sans personne pour déverrouiller.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generateur.generateKey()
    }
}
