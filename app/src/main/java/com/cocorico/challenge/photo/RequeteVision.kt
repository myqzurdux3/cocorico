package com.cocorico.challenge.photo

/**
 * La partie texte de l'appel au juge distant : construire le corps de la
 * requête et lire le verdict dans la réponse. Aucun import `android.*` ni
 * réseau ici — c'est du texte, ça se teste sans appareil.
 *
 * Le format suit l'API Messages d'Anthropic
 * (`POST https://api.anthropic.com/v1/messages`) au mieux de la
 * documentation connue au moment de l'écriture : elle n'a pas pu être
 * confrontée à l'API réelle dans cet environnement, faute d'accès réseau.
 */
object RequeteVision {

    /** Modèle interrogé — voir [JugeDistant]. */
    private const val MODELE = "claude-sonnet-5"

    /**
     * Un « OUI » ou un « NON » tient en un seul mot ; quelques jetons de
     * marge suffisent et gardent l'appel court pendant qu'une alarme sonne.
     */
    private const val MAX_TOKENS = 8

    /**
     * Construit le corps JSON de la requête : une consigne qui nomme
     * [objetNom] et exige une réponse à un seul mot, accompagnée de
     * l'image encodée en base64 dans [imageBase64].
     */
    fun corps(objetNom: String, imageBase64: String): String {
        val consigne = "Cette photo montre-t-elle l'objet suivant : \"$objetNom\" ? " +
            "Réponds uniquement par le mot OUI ou le mot NON, sans aucune autre parole, " +
            "sans ponctuation et sans explication."
        return "{" +
            "\"model\":\"$MODELE\"," +
            "\"max_tokens\":$MAX_TOKENS," +
            "\"messages\":[{" +
            "\"role\":\"user\"," +
            "\"content\":[" +
            "{\"type\":\"image\",\"source\":{\"type\":\"base64\"," +
            "\"media_type\":\"image/jpeg\",\"data\":\"${echapper(imageBase64)}\"}}," +
            "{\"type\":\"text\",\"text\":\"${echapper(consigne)}\"}" +
            "]" +
            "}]" +
            "}"
    }

    /**
     * Lit le verdict dans la réponse brute de l'API. Renvoie `true`
     * uniquement si un bloc de texte de la réponse, une fois débarrassé des
     * espaces qui l'entourent et comparé sans tenir compte de la casse, vaut
     * exactement « oui ». Tout le reste — refus, JSON invalide, chaîne vide,
     * modèle bavard qui ajoute un mot — vaut refus : c'est le seul verdict
     * sûr à opposer à une réponse qu'on ne maîtrise pas.
     */
    fun lireVerdict(reponse: String): Boolean {
        val texte = MOTIF_TEXTE.find(reponse)?.groupValues?.get(1) ?: return false
        return texte.trim().equals("oui", ignoreCase = true)
    }

    /** Capture la valeur du premier champ `"text"` d'une réponse JSON. */
    private val MOTIF_TEXTE = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")

    /** Échappe une chaîne pour l'insérer telle quelle dans un littéral JSON. */
    private fun echapper(valeur: String): String =
        valeur
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
