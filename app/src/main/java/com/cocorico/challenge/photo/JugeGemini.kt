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
 * Ce que le juge a réellement obtenu, au-delà du verdict.
 *
 * [accepte] est tout ce dont le défi a besoin. [resume] et [reponseBrute]
 * n'existent que pour le banc d'essai : un refus peut venir d'une clé
 * invalide, d'un réseau absent, d'un délai dépassé, d'une réponse illisible ou
 * d'un vrai « non » du modèle. Ces cinq causes appellent cinq corrections
 * différentes, et le booléen seul ne permet pas de les distinguer — c'est
 * précisément ce qui rendait un échec impossible à diagnostiquer.
 */
data class DiagnosticJuge(
    val accepte: Boolean,
    val resume: String,
    val reponseBrute: String? = null,
)

/**
 * Le juge du défi photo : un modèle de vision distant, interrogé avec la clé
 * d'API de l'utilisateur.
 *
 * C'est le seul juge. La reconnaissance embarquée qui le précédait a été
 * retirée : à l'essai sur appareil, elle nommait mal les objets ordinaires
 * d'un logement, et un défi qui refuse une photo correcte laisse quelqu'un
 * devant une sirène qu'il ne peut pas éteindre.
 *
 * Conséquence assumée : **sans réseau ni clé, le défi photo n'est pas
 * disponible**, et l'alarme se rabat sur le calcul mental avant tout
 * affichage. C'est ce repli qui garantit qu'aucune alarme ne devient
 * impossible à arrêter.
 *
 * Contrat de [JugePhoto] : aucune exception ne sort d'[accepte].
 */
class JugeGemini(
    private val cle: String,
    private val timeoutMs: Long = DELAI_MAX_MS,
) : JugePhoto {

    override suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean =
        diagnostiquer(image, objet).accepte

    /**
     * Même chemin exact qu'[accepte], mais en rapportant ce qui s'est passé.
     * Le banc d'essai s'en sert ; le défi, lui, n'a que faire du détail.
     * Emprunter un autre chemin pour diagnostiquer ne prouverait rien sur le
     * chemin réel.
     */
    suspend fun diagnostiquer(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge {
        if (cle.isBlank()) {
            return DiagnosticJuge(false, "Aucune clé d'API enregistrée.")
        }
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                runCatching { interroger(image, objet) }.getOrElse { erreur ->
                    DiagnosticJuge(
                        accepte = false,
                        resume = "Échec réseau : ${masquer(erreur.toString())}",
                    )
                }
            } ?: DiagnosticJuge(
                accepte = false,
                resume = "Pas de réponse en ${timeoutMs / 1000} s. Au réveil, ce délai vaut refus.",
            )
        }
    }

    private fun interroger(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge {
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
            val code = connexion.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // Le corps d'erreur porte la vraie cause — clé invalide,
                // quota dépassé, modèle inconnu. Sans le lire, tout échec se
                // ressemble et rien n'est diagnosticable.
                val erreur = runCatching {
                    connexion.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                return DiagnosticJuge(
                    accepte = false,
                    resume = "Refus du serveur (HTTP $code).",
                    reponseBrute = masquer(erreur).ifBlank { null },
                )
            }
            val reponse = connexion.inputStream.bufferedReader().use { it.readText() }
            val accepte = RequeteVision.lireVerdict(reponse)
            DiagnosticJuge(
                accepte = accepte,
                resume = if (accepte) {
                    "Le modèle a reconnu l'objet."
                } else {
                    "Le modèle a répondu, mais pas « oui »."
                },
                reponseBrute = masquer(reponse),
            )
        } finally {
            runCatching { connexion.disconnect() }
        }
    }

    /**
     * Retire la clé de tout texte destiné à l'écran. Elle ne devrait jamais
     * s'y trouver — elle n'est ni dans l'URL ni dans le corps — mais un
     * message d'erreur n'est pas sous notre contrôle, et une clé affichée est
     * une clé qui fuite.
     */
    private fun masquer(texte: String): String =
        if (cle.isBlank()) texte else texte.replace(cle, "«clé masquée»")

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
