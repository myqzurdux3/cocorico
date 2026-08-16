package com.cocorico.challenge.photo

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Le juge de secours, optionnel et éteint par défaut : il n'intervient que
 * lorsque la reconnaissance embarquée a refusé une photo, et seulement si
 * l'utilisateur a activé le mode en ligne et fourni sa propre clé d'API
 * Anthropic. Il rattrape ce que le modèle embarqué rate — objet de travers,
 * à moitié hors cadre, lumière de 6 h du matin.
 *
 * **Tout échec vaut refus, jamais un plantage** : délai dépassé, code HTTP
 * autre que 200, exception réseau, JSON malformé, clé vide — tout se traduit
 * par `false`. Cette classe s'exécute pendant qu'une alarme hurle et qu'un
 * utilisateur debout attend ; aucune exception ne doit en sortir.
 *
 * L'appel est borné à [timeoutMs] : un réveil ne peut pas dépendre de la
 * latence d'un serveur. Aucune photo n'est écrite sur le disque — l'image est
 * encodée depuis la mémoire.
 *
 * [cle] appartient à l'utilisateur : elle ne doit jamais être journalisée, ni
 * dans un message d'erreur, ni dans une trace.
 */
class JugeDistant(
    private val cle: String,
    private val timeoutMs: Long = 8_000,
) : JugePhoto {

    override suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean {
        if (cle.isBlank()) return false
        val verdict = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                interroger(image, objet)
            }
        }
        return verdict ?: false
    }

    /**
     * Effectue l'appel réseau lui-même. Toute exception — réseau, HTTP,
     * lecture, encodage — est capturée ici et vaut refus : rien ne doit
     * remonter jusqu'à [accepte].
     */
    private fun interroger(image: Bitmap, objet: ObjetPhoto): Boolean =
        try {
            val corpsJson = RequeteVision.corps(objet.nom, encoderEnBase64(image))
            val connexion = ouvrirConnexion()
            try {
                envoyerCorps(connexion, corpsJson)
                if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
                    false
                } else {
                    val reponse = connexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    RequeteVision.lireVerdict(reponse)
                }
            } finally {
                connexion.disconnect()
            }
        } catch (e: Exception) {
            false
        }

    private fun ouvrirConnexion(): HttpURLConnection {
        val connexion = URL(URL_API).openConnection() as HttpURLConnection
        connexion.requestMethod = "POST"
        connexion.doOutput = true
        // Bornes supplémentaires côté connexion : withTimeoutOrNull délimite
        // la coroutine, mais un appel bloquant sur Dispatchers.IO peut rester
        // pendu au-delà si la connexion elle-même n'a pas de délai propre.
        val timeoutInt = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        connexion.connectTimeout = timeoutInt
        connexion.readTimeout = timeoutInt
        connexion.setRequestProperty("x-api-key", cle)
        connexion.setRequestProperty("anthropic-version", "2023-06-01")
        connexion.setRequestProperty("content-type", "application/json")
        return connexion
    }

    private fun envoyerCorps(connexion: HttpURLConnection, corpsJson: String) {
        connexion.outputStream.use { it.write(corpsJson.toByteArray(Charsets.UTF_8)) }
    }

    /** Encode [image] en JPEG puis en base64, entièrement en mémoire. */
    private fun encoderEnBase64(image: Bitmap): String {
        val flux = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, flux)
        return Base64.getEncoder().encodeToString(flux.toByteArray())
    }

    private companion object {
        const val URL_API = "https://api.anthropic.com/v1/messages"
        const val QUALITE_JPEG = 90
    }
}
