package com.cocorico.challenge.photo

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ce qui est arrivé à un essai, au-delà du verdict.
 *
 * La distinction qui compte est celle entre [REFUS_DU_MODELE] et
 * [JUGE_INDISPONIBLE] : dans le premier cas une autre photo peut réussir,
 * dans le second **aucune photo ne peut aboutir**. Les confondre laissait
 * l'utilisateur rephotographier indéfiniment un objet correct devant une
 * sirène, sans jamais apprendre que rien ne pouvait marcher.
 */
enum class IssueJuge {
    /** Le modèle a reconnu l'objet. */
    ACCEPTE,

    /** Le modèle a répondu, mais pas « oui ». Une autre photo peut réussir. */
    REFUS_DU_MODELE,

    /**
     * Aucun verdict : pas de clé, pas de réseau, quota dépassé, serveur en
     * panne, délai dépassé. Réessayer la même photo ne changera rien.
     */
    JUGE_INDISPONIBLE,
}

/**
 * Ce que le juge a réellement obtenu, au-delà du verdict.
 *
 * [accepte] est ce que le défi consomme pour avancer, [issue] ce dont il a
 * besoin pour ne pas mentir à l'utilisateur. [resume] et [reponseBrute]
 * servent l'affichage et le banc d'essai : un refus peut venir d'une clé
 * invalide, d'un réseau absent, d'un délai dépassé, d'une réponse illisible ou
 * d'un vrai « non » du modèle. Ces cinq causes appellent cinq corrections
 * différentes, et le booléen seul ne permet pas de les distinguer — c'est
 * précisément ce qui rendait un échec impossible à diagnostiquer.
 *
 * [issue] se déduit d'[accepte] par défaut, pour les appelants qui décrivent
 * un incident local sans interroger le modèle (le banc d'essai le fait quand
 * la photo n'a pas pu être préparée).
 */
data class DiagnosticJuge(
    val accepte: Boolean,
    val resume: String,
    val reponseBrute: String? = null,
    val issue: IssueJuge = if (accepte) IssueJuge.ACCEPTE else IssueJuge.REFUS_DU_MODELE,
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
 * Contrat de [JugePhoto] : aucune exception ne sort de [juger].
 */
class JugeGemini(private val cle: String, private val timeoutMs: Long = DELAI_MAX_MS) : JugePhoto {

    override suspend fun juger(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge = diagnostiquer(image, objet)

    /**
     * Même chemin exact que [juger] : le banc d'essai passe par ce nom, le
     * défi par celui de l'interface. Emprunter un autre chemin pour
     * diagnostiquer ne prouverait rien sur le chemin réel.
     */
    suspend fun diagnostiquer(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge {
        if (cle.isBlank()) {
            return DiagnosticJuge(
                accepte = false,
                resume = "Aucune clé d'API enregistrée.",
                issue = IssueJuge.JUGE_INDISPONIBLE,
            )
        }
        return withContext(Dispatchers.IO) {
            val debutNs = System.nanoTime()
            withTimeoutOrNull(timeoutMs) {
                val premiere = tenter(image, objet)
                // Un second essai, jamais plus, et seulement s'il reste de
                // quoi le mener : deux tentatives qui débordent le budget
                // valent moins qu'une seule suivie d'un message honnête.
                if (!premiere.reessayable || ecouleMs(debutNs) > timeoutMs / 2) {
                    premiere.diagnostic
                } else {
                    tenter(image, objet).diagnostic
                }
            } ?: DiagnosticJuge(
                accepte = false,
                resume = "Pas de réponse en ${timeoutMs / 1000} s.",
                issue = IssueJuge.JUGE_INDISPONIBLE,
            )
        }
    }

    /**
     * Une tentative et ce qu'on peut en faire. La réessayabilité ne sort pas
     * de cette classe : c'est une propriété de l'échec réseau, pas une
     * information à montrer à quelqu'un qui attend devant sa sonnerie.
     */
    private data class Tentative(val diagnostic: DiagnosticJuge, val reessayable: Boolean)

    private fun tenter(image: Bitmap, objet: ObjetPhoto): Tentative =
        runCatching { interroger(image, objet) }.getOrElse { erreur ->
            Tentative(
                DiagnosticJuge(
                    accepte = false,
                    resume = "Échec réseau : ${masquer(erreur.toString())}",
                    issue = IssueJuge.JUGE_INDISPONIBLE,
                ),
                // Une coupure de connexion au moment précis de l'essai est le
                // cas le plus banal à six heures du matin, et le plus facile à
                // rattraper : un second essai coûte moins qu'un échec rendu.
                reessayable = true,
            )
        }

    private fun ecouleMs(debutNs: Long): Long = (System.nanoTime() - debutNs) / 1_000_000

    private fun interroger(image: Bitmap, objet: ObjetPhoto): Tentative {
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
                return Tentative(
                    DiagnosticJuge(
                        accepte = false,
                        // Un code HTTP n'est jamais un « non » du modèle : le
                        // modèle n'a pas vu la photo. Le présenter comme un
                        // refus faisait rephotographier un objet correct
                        // pendant que le quota restait dépassé.
                        resume = causeHttp(code),
                        reponseBrute = masquer(erreur).ifBlank { null },
                        issue = IssueJuge.JUGE_INDISPONIBLE,
                    ),
                    reessayable = estReessayable(code),
                )
            }
            val reponse = connexion.inputStream.bufferedReader().use { it.readText() }
            val accepte = RequeteVision.lireVerdict(reponse)
            Tentative(
                DiagnosticJuge(
                    accepte = accepte,
                    resume = if (accepte) {
                        "Le modèle a reconnu l'objet."
                    } else {
                        "Le modèle a répondu, mais pas « oui »."
                    },
                    reponseBrute = masquer(reponse),
                    issue = if (accepte) IssueJuge.ACCEPTE else IssueJuge.REFUS_DU_MODELE,
                ),
                reessayable = false,
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
    internal fun masquer(texte: String): String = if (cle.isBlank()) texte else texte.replace(cle, MASQUE)

    /** Encodage en mémoire : aucune image n'atteint le disque. */
    private fun encoder(image: Bitmap): String {
        val flux = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, flux)
        return Base64.encodeToString(flux.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        /**
         * Le défi n'attend jamais le réseau plus que ça : au-delà, le juge est
         * déclaré indisponible et l'écran le dit. Un réveil ne peut pas
         * dépendre de la latence d'un serveur.
         */
        const val DELAI_MAX_MS = 8_000L

        /** Ce qui remplace la clé dans tout texte affiché. */
        internal const val MASQUE = "«clé masquée»"

        private const val QUALITE_JPEG = 85

        /**
         * Les codes qui décrivent un incident passager plutôt qu'une requête
         * fautive : ceux-là valent un second essai dans le budget. Une clé
         * invalide (401, 403) ou un modèle retiré (404) ne guérira pas d'un
         * essai de plus — insister ne ferait que consommer les secondes
         * pendant lesquelles l'alarme sonne.
         */
        internal fun estReessayable(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

        /**
         * La cause à montrer pour un code HTTP. Formulée pour quelqu'un qui
         * vient de photographier le bon objet : elle doit dire que le juge n'a
         * pas répondu, jamais laisser croire que la photo a été refusée.
         */
        internal fun causeHttp(code: Int): String = when (code) {
            401, 403 -> "clé d'API refusée (HTTP $code)"
            404 -> "modèle introuvable (HTTP $code)"
            408 -> "le serveur a coupé l'attente (HTTP $code)"
            429 -> "quota d'API dépassé (HTTP $code)"
            in 500..599 -> "serveur du juge en panne (HTTP $code)"
            else -> "le juge a rejeté la requête (HTTP $code)"
        }
    }
}
