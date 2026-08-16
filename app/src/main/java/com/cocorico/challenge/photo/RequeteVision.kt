package com.cocorico.challenge.photo

/**
 * Construit la requête envoyée au modèle de vision et lit son verdict.
 *
 * Partie purement textuelle, sans réseau ni Android : c'est là que vivent les
 * erreurs de format, et c'est donc là que les tests ont le plus de valeur.
 *
 * Le format visé est celui de l'API Gemini de Google
 * (`POST .../v1beta/models/<modèle>:generateContent`). Il n'a pas pu être
 * confronté à l'API réelle depuis cet environnement : les tests en vérifient
 * la structure, pas l'acceptation par le serveur.
 */
object RequeteVision {

    /**
     * `gemini-2.0-flash` plutôt qu'un modèle plus récent : il est rapide, il
     * voit, il est couvert par l'offre gratuite, et surtout il ne pratique pas
     * de réflexion préalable susceptible de consommer le budget de réponse
     * avant d'avoir écrit le moindre mot. Changer de modèle ne demande que de
     * modifier cette constante.
     */
    const val MODELE = "gemini-2.0-flash"

    /**
     * Large à dessein. La réponse attendue tient en un mot, mais un budget
     * serré est un pari sur le comportement exact du modèle : s'il est faux,
     * la réponse est tronquée avant tout texte utile, le verdict devient un
     * refus, et le défi refuse alors *toutes* les photos — l'utilisateur reste
     * devant sa sirène. La facturation suit les jetons réellement produits,
     * donc ce budget large ne coûte rien.
     */
    private const val MAX_TOKENS = 1024

    fun url(modele: String = MODELE): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$modele:generateContent"

    /**
     * Le corps JSON : l'image, puis la consigne. Le nom de l'objet part en
     * français, tel qu'il est affiché à l'utilisateur — le modèle comprend la
     * langue, il n'y a rien à traduire.
     */
    fun corps(objetNom: String, imageBase64: String): String {
        val consigne = "Cette photo montre-t-elle l'objet suivant : \"$objetNom\" ? " +
            "Réponds uniquement par le mot OUI ou le mot NON, sans aucune autre parole, " +
            "sans ponctuation et sans explication."
        return "{" +
            "\"contents\":[{" +
            "\"role\":\"user\"," +
            "\"parts\":[" +
            "{\"inline_data\":{\"mime_type\":\"image/jpeg\"," +
            "\"data\":\"${echapper(imageBase64)}\"}}," +
            "{\"text\":\"${echapper(consigne)}\"}" +
            "]" +
            "}]," +
            "\"generationConfig\":{\"maxOutputTokens\":$MAX_TOKENS,\"temperature\":0}" +
            "}"
    }

    /**
     * Lit le verdict dans la réponse brute. Renvoie `true` uniquement sur un
     * « oui » franc — voir [estOuiFranc]. Tout le reste vaut refus : réponse
     * d'erreur, JSON invalide, chaîne vide, absence de texte. C'est le seul
     * verdict sûr face à une réponse qu'on ne maîtrise pas, et un refus n'est
     * jamais définitif puisque l'utilisateur reprend une photo.
     *
     * On lit le **dernier** bloc de texte : si le modèle a produit un
     * préambule avant sa conclusion, c'est la conclusion qui compte.
     */
    fun lireVerdict(reponse: String): Boolean {
        val blocs = MOTIF_BLOC_TEXTE.findAll(reponse).map { it.groupValues[1] }.toList()
        val dernier = blocs.lastOrNull() ?: return false
        return estOuiFranc(dernier)
    }

    /**
     * Un « oui » compte s'il n'est pas nié dans sa propre phrase. Chercher
     * « oui » n'importe où ferait lire « je ne peux pas dire oui » comme un
     * accord — l'erreur qui rendrait le défi contournable par une réponse
     * hésitante du modèle.
     */
    private fun estOuiFranc(texte: String): Boolean {
        val mot = MOTIF_MOT_VERDICT.find(texte) ?: return false
        if (!mot.value.equals("oui", ignoreCase = true)) return false
        val debutPhrase = texte
            .lastIndexOfAny(SEPARATEURS_PHRASE, startIndex = (mot.range.first - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val avant = texte.substring(debutPhrase, mot.range.first)
        return !MOTIF_NEGATION.containsMatchIn(avant)
    }

    /** Échappe ce qui casserait le JSON construit à la main. */
    private fun echapper(valeur: String): String = buildString {
        valeur.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    private val MOTIF_BLOC_TEXTE = Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val MOTIF_MOT_VERDICT = Regex("(?i)\\b(oui|non)\\b")
    private val MOTIF_NEGATION = Regex("(?i)\\bne\\b|\\bpas\\b|\\bsans\\b|\\bjamais\\b|\\baucun")
    private val SEPARATEURS_PHRASE = charArrayOf('.', '!', '?', '\n')
}
