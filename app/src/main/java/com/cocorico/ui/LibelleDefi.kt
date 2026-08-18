package com.cocorico.ui

import com.cocorico.data.ChallengeId

/**
 * Traduit l'identifiant de défi rangé dans l'historique en un nom lisible.
 *
 * Existe parce que l'écran de victoire et celui des statistiques faisaient
 * chacun leur propre traduction, et qu'aucun des deux n'avait été mis à jour
 * en même temps que le mode Sur mesure : un matin en Sur mesure s'affichait
 * « Calculs » sur l'un et « Maths » sur l'autre. Deux traductions valent deux
 * occasions d'oublier un cas ; il n'y en a plus qu'une, et elle est testée.
 *
 * Pur, sans import `android.*` : la valeur lue vient d'une base de données et
 * peut avoir été écrite par n'importe quelle version, présente ou future.
 */
object LibelleDefi {

    /** Un identifiant inconnu vaut « Calculs » : c'est le défi de repli partout ailleurs. */
    fun libelle(defi: String): String = when (defi) {
        ChallengeId.POMPES.name -> "Pompes"
        ChallengeId.PHOTO.name -> "Photo"
        ChallengeId.COMBINE.name -> "Sur mesure"
        else -> "Calculs"
    }

    /**
     * Le renoncement s'**ajoute** au nom du défi réglé, il ne le remplace pas.
     *
     * L'écran de victoire annonçait « Calculs (renoncé) » quel que soit le défi
     * abandonné : il disait ce qui avait fini par être fait, en effaçant ce qui
     * avait été prévu. Or c'est justement l'écart entre les deux que
     * l'utilisateur veut voir.
     */
    fun avecRenoncement(defi: String, abandon: Boolean): String =
        if (abandon) "${libelle(defi)} (renoncé)" else libelle(defi)
}
