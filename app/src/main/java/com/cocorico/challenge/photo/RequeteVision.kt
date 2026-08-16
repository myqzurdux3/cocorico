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
     * Un budget large, pas un pari serré. Un « OUI » ou un « NON » tient en
     * un mot, mais si le modèle a de la réflexion active par défaut, cette
     * réflexion consomme des jetons *avant* le premier mot de texte ; avec
     * un budget de quelques jetons seulement, la réponse s'arrête avant
     * d'avoir produit le moindre texte, `lireVerdict` ne trouve rien, et le
     * mode en ligne — activé et payé par l'utilisateur pour rattraper les
     * photos que l'embarqué a refusées — se met à tout refuser
     * systématiquement. Le coût réel ne dépend que des jetons effectivement
     * produits (la réponse attendue en fait deux ou trois), donc un budget
     * large ne coûte rien de plus et supprime toute cette classe de défaut.
     *
     * On aurait pu à la place désactiver la réflexion via un champ
     * `thinking` dans le corps de requête, mais cette requête n'a jamais pu
     * être confrontée à l'API réelle dans cet environnement : un champ mal
     * formé ferait échouer l'appel avec un code d'erreur, donc un refus
     * systématique — exactement le défaut qu'on corrige. Un budget large
     * fonctionne que la réflexion soit active ou non ; c'est le correctif
     * sûr qui ne dépend d'aucune hypothèse invérifiable.
     */
    private const val MAX_TOKENS = 1024

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
     * uniquement si le dernier bloc de type `"text"` de la réponse contient
     * un « oui » franc — voir [estOuiFranc]. Tout le reste — refus, JSON
     * invalide, chaîne vide, absence de bloc de texte — vaut refus : c'est
     * le seul verdict sûr à opposer à une réponse qu'on ne maîtrise pas.
     *
     * On prend le *dernier* bloc de texte, pas le premier : si le modèle a
     * produit d'autres blocs avant (une réflexion, un préambule bavard « Je
     * regarde la photo... »), c'est dans le dernier que se trouve la
     * réponse finale à la consigne.
     */
    fun lireVerdict(reponse: String): Boolean {
        val blocsTexte = MOTIF_BLOC_TEXTE.findAll(reponse).map { it.groupValues[1] }.toList()
        val dernierBloc = blocsTexte.lastOrNull() ?: return false
        return estOuiFranc(dernierBloc)
    }

    /**
     * Capture la valeur du champ `"text"` de chaque bloc de contenu dont le
     * `"type"` vaut `"text"` — pas n'importe quel champ `"text"` de la
     * réponse (un bloc de réflexion, par exemple, porte son contenu dans un
     * champ `"thinking"`, jamais `"text"`).
     */
    private val MOTIF_BLOC_TEXTE =
        Regex("\"type\"\\s*:\\s*\"text\"[^}]*?\"text\"\\s*:\\s*\"([^\"]*)\"")

    /**
     * Un « oui franc » : le mot OUI apparaît dans le texte, entier (pas
     * comme fragment d'un autre mot), et rien juste avant lui dans la même
     * phrase ne le nie. Sans cette garde de négation, une phrase de refus
     * comme « je ne peux pas dire oui » serait lue comme un accord parce
     * qu'elle contient littéralement le mot « oui » — exactement le piège à
     * éviter : rendre la lecture plus tolérante à la forme (une phrase
     * autour du mot, une majuscule, une ponctuation) ne doit jamais la
     * rendre tolérante au *sens*. Si un « non » apparaît avant le premier
     * « oui » de la phrase, ou si aucun « oui » n'est trouvé du tout, c'est
     * un refus.
     */
    private fun estOuiFranc(texte: String): Boolean {
        val correspondance = MOTIF_MOT_VERDICT.find(texte) ?: return false
        if (!correspondance.value.equals("oui", ignoreCase = true)) return false
        val debutPhrase = texte
            .lastIndexOfAny(SEPARATEURS_PHRASE, startIndex = correspondance.range.first - 1)
            .let { if (it == -1) 0 else it + 1 }
        val avantLeMot = texte.substring(debutPhrase, correspondance.range.first)
        return !MOTIF_NEGATION.containsMatchIn(avantLeMot)
    }

    /** Le premier mot OUI ou NON entier (délimité par des frontières de mot). */
    private val MOTIF_MOT_VERDICT = Regex("(?i)\\b(oui|non)\\b")

    private val SEPARATEURS_PHRASE = charArrayOf('.', '!', '?', '\n')

    /** Négations françaises usuelles qui, juste avant le mot, l'invalident. */
    private val MOTIF_NEGATION = Regex("(?i)\\bne\\b|\\bpas\\b|\\bsans\\b|\\bjamais\\b|\\baucun")

    /** Échappe une chaîne pour l'insérer telle quelle dans un littéral JSON. */
    private fun echapper(valeur: String): String =
        valeur
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
