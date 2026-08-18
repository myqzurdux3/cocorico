package com.cocorico.challenge.combine

import com.cocorico.data.ChallengeId

/**
 * Une épreuve du défi sur mesure : un type, et combien de fois.
 *
 * Le nombre n'a pas le même sens partout, et c'est voulu : deux calculs, cinq
 * pompes, une photo. Chaque défi sait déjà compter ses propres répétitions.
 */
data class EtapeCombine(val type: ChallengeId, val nombre: Int)

/**
 * La liste d'épreuves du défi sur mesure : ce qu'on en garde, comment elle se
 * range sur le disque, comment l'utilisateur la réordonne.
 *
 * Pur, sans import `android.*`. Ce n'est pas une préférence de style : cette
 * liste décide de ce que l'alarme demandera demain matin, et une liste vide
 * arrêterait la sonnerie sans rien demander du tout. Ça se vérifie sans
 * téléphone, donc ça doit l'être.
 */
object EtapesCombine {

    /**
     * Borne haute d'une épreuve. La liste est persistée : une valeur abîmée ne
     * doit pas produire quatre-vingt-dix mille pompes devant une sirène.
     */
    const val NOMBRE_MAX = 30

    /** Ce que l'application demande quand la liste ne dit rien d'utilisable. */
    val REPLI = listOf(EtapeCombine(ChallengeId.MATHS, 3))

    /** Proposition de départ, du plus facile au plus exigeant. */
    val DEFAUT = listOf(
        EtapeCombine(ChallengeId.MATHS, 2),
        EtapeCombine(ChallengeId.POMPES, 5),
        EtapeCombine(ChallengeId.PHOTO, 1),
    )

    /** Les types que l'écran de réglage propose, dans son ordre d'affichage. */
    val TYPES = listOf(ChallengeId.MATHS, ChallengeId.POMPES, ChallengeId.PHOTO)

    /**
     * Rend une liste utilisable, quoi qu'on lui donne.
     *
     * Retire les épreuves à zéro — c'est ainsi que l'écran en enlève une —,
     * borne les nombres, fusionne un type répété sur sa première place, et se
     * replie sur [REPLI] s'il ne reste rien. Ce dernier point est la garantie
     * qui compte : sans lui, une suite vide serait résolue d'emblée.
     */
    fun assainir(etapes: List<EtapeCombine>): List<EtapeCombine> {
        val vues = mutableSetOf<ChallengeId>()
        val gardees = etapes
            .filter { it.nombre > 0 }
            .map { EtapeCombine(it.type, it.nombre.coerceAtMost(NOMBRE_MAX)) }
            .filter { vues.add(it.type) }
        return gardees.ifEmpty { REPLI }
    }

    /**
     * Encodage volontairement lisible : `MATHS:2,POMPES:5`. Le fichier de
     * préférences se relit à l'œil pendant une recette, et l'ordre des entrées
     * **est** le réglage — un format qui le perdrait changerait le réveil sans
     * que rien ne le signale.
     */
    fun encoder(etapes: List<EtapeCombine>): String = etapes.joinToString(",") { "${it.type.name}:${it.nombre}" }

    /** Tout ce qui n'est pas compris est ignoré, sans emporter le reste. */
    fun decoder(texte: String): List<EtapeCombine> = assainir(
        texte.split(",").mapNotNull { entree ->
            val morceaux = entree.split(":")
            if (morceaux.size != 2) return@mapNotNull null
            val type = runCatching { ChallengeId.valueOf(morceaux[0].trim()) }.getOrNull()
            val nombre = morceaux[1].trim().toIntOrNull()
            if (type == null || nombre == null) null else EtapeCombine(type, nombre)
        },
    )

    /** Échange une épreuve avec celle du dessus. Hors bornes : rien ne bouge. */
    fun monter(etapes: List<EtapeCombine>, index: Int): List<EtapeCombine> = echanger(etapes, index, index - 1)

    /** Échange une épreuve avec celle du dessous. Hors bornes : rien ne bouge. */
    fun descendre(etapes: List<EtapeCombine>, index: Int): List<EtapeCombine> = echanger(etapes, index, index + 1)

    private fun echanger(etapes: List<EtapeCombine>, a: Int, b: Int): List<EtapeCombine> {
        if (a !in etapes.indices || b !in etapes.indices) return etapes
        return etapes.toMutableList().apply {
            val garde = this[a]
            this[a] = this[b]
            this[b] = garde
        }
    }
}
