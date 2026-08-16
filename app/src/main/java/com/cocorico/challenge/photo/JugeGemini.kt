package com.cocorico.challenge.photo

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le juge du défi photo : un modèle de vision distant, interrogé avec la clé
 * d'API de l'utilisateur.
 *
 * C'est désormais le **seul** juge. La reconnaissance embarquée qui le
 * précédait a été retirée : à l'essai sur appareil, elle nommait mal les
 * objets ordinaires d'un logement, et un défi qui refuse une photo correcte
 * laisse quelqu'un devant une sirène qu'il ne peut pas éteindre. Mieux vaut un
 * juge qui exige du réseau qu'un juge qui se trompe.
 *
 * Conséquence assumée : **sans réseau ni clé, le défi photo n'est pas
 * disponible**, et l'alarme se rabat sur le calcul mental avant tout
 * affichage. C'est ce repli qui garantit qu'aucune alarme ne devient
 * impossible à arrêter.
 *
 * Contrat de [JugePhoto] : aucune exception ne sort d'[accepte]. Délai
 * dépassé, code HTTP inattendu, réseau coupé, réponse illisible, clé vide —
 * tout vaut refus. Cette classe s'exécute pendant que l'alarme hurle.
 */
class JugeGemini(
    private val cle: String,
    private val timeoutMs: Long = DELAI_MAX_MS,
) : JugePhoto {

    override suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean {
        if (cle.isBlank()) return false
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                runCatching { interroger(image, objet) }.getOrDefault(false)
            } ?: false
        }
    }

    private fun interroger(image: Bitmap, objet: ObjetPhoto): Boolean {
        val connexion = (URL(RequeteVision.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // Bornés aussi côté connexion : `withTimeoutOrNull` ne peut pas
            // interrompre une lecture bloquée dans la pile réseau.
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            // La clé voyage dans un en-tête, jamais dans l'URL : une URL finit
            // dans les journaux des serveurs traversés, pas un en-tête.
            setRequestProperty("x-goog-api-key", cle)
        }
        return try {
            connexion.outputStream.use { flux ->
                flux.write(RequeteVision.corps(objet.nom, encoder(image)).toByteArray())
            }
            if (connexion.responseCode != HttpURLConnection.HTTP_OK) return false
            val reponse = connexion.inputStream.bufferedReader().use { it.readText() }
            RequeteVision.lireVerdict(reponse)
        } finally {
            runCatching { connexion.disconnect() }
        }
    }

    /** Encodage en mémoire : aucune image n'atteint le disque. */
    private fun encoder(image: Bitmap): String {
        val flux = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, flux)
        return Base64.encodeToString(flux.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        /**
         * Le défi n'attend jamais le réseau plus que ça : au-delà, refus, et
         * l'utilisateur reprend une photo. Un réveil ne peut pas dépendre de
         * la latence d'un serveur.
         */
        const val DELAI_MAX_MS = 8_000L

        private const val QUALITE_JPEG = 85
    }
}
