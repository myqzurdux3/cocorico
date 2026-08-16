package com.cocorico.challenge.photo

/**
 * Une étiquette rendue par un juge (embarqué ou distant) pour une photo, avec
 * son indice de confiance entre 0 et 1.
 */
data class EtiquetteReconnue(val texte: String, val confiance: Float)

/**
 * Le verdict pur : un objet attendu est-il présent parmi des étiquettes déjà
 * reconnues ? Ni caméra, ni modèle de reconnaissance ici — cette classe ne
 * fait que comparer des chaînes, ce qui la rend testable sans photo.
 */
object JugementPhoto {

    /**
     * Seuil de confiance de départ, à calibrer sur appareil. Trop haut,
     * l'objet n'est jamais reconnu et l'alarme ne s'arrête plus ; trop bas,
     * n'importe quelle photo passe. Nommé plutôt que semé dans une
     * comparaison, précisément parce que ce réglage est un pari appelé à
     * changer.
     */
    const val SEUIL_CONFIANCE = 0.55f

    /**
     * Accepte si au moins une étiquette reconnue correspond — sans tenir
     * compte de la casse — à l'une des étiquettes attendues de [objet], avec
     * une confiance au moins égale à [seuil]. La position de l'étiquette dans
     * la liste n'entre pas en jeu : une photo encombrée rend souvent plusieurs
     * étiquettes, et exiger que la bonne arrive en tête rejetterait des
     * photos pourtant valables.
     */
    fun accepte(objet: ObjetPhoto, etiquettes: List<EtiquetteReconnue>, seuil: Float = SEUIL_CONFIANCE): Boolean =
        etiquettes.any { etiquette ->
            etiquette.confiance >= seuil && etiquette.texte.lowercase() in objet.etiquettes
        }
}
