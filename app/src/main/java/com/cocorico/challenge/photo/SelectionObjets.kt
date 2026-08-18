package com.cocorico.challenge.photo

/**
 * Logique décidable de l'écran de sélection des objets du défi photo : le
 * comptage par pièce affiché à l'écran, et l'effet d'un geste de
 * l'utilisateur (cocher un objet, cocher ou décocher une pièce entière) sur
 * la sélection persistée. Pure, sans import `android.*` — testable sans
 * Compose ni DataStore, sur le modèle de [CatalogueObjets].
 *
 * Contrairement à [CatalogueObjets.tirer], cette classe ne connaît **aucun**
 * repli de sécurité : une sélection vide y reste une sélection vide, affichée
 * comme telle. Le repli qui empêche une sélection vide de bloquer le tirage
 * vit uniquement dans `tirer` — le mélanger ici ferait réafficher toutes les
 * cases comme cochées juste après que l'utilisateur ait décoché la dernière,
 * ce qui donnerait l'impression que son geste n'a rien fait.
 */
object SelectionObjets {

    /**
     * Nombre d'objets que la difficulté la plus exigeante peut demander. En
     * dessous de ce seuil, le tirage peut devoir compléter hors de la
     * sélection de l'utilisateur : l'écran en avertit sans jamais empêcher la
     * sélection.
     *
     * Dérivé de [PhotoChallenge.nombrePour], et non recopié : les deux
     * valeurs coïncidaient sans qu'aucun lien de code ne l'impose, et un
     * changement de difficulté aurait laissé l'écran avertir au mauvais
     * seuil — donc promettre un tirage dans la seule sélection alors qu'il en
     * sortirait.
     */
    val SEUIL_AVERTISSEMENT = PhotoChallenge.NOMBRE_OBJETS

    /** Le comptage d'une pièce : combien de ses objets sont cochés, sur combien au total. */
    data class ComptagePiece(val piece: Piece, val coches: Int, val total: Int) {
        /** Une pièce sans objet n'est jamais « entièrement cochée » : il n'y a rien à cocher. */
        val toutCoche: Boolean get() = total > 0 && coches == total
    }

    /**
     * Un [ComptagePiece] par pièce du découpage, dans l'ordre de [Piece], à
     * partir des seuls identifiants de [selection] qui correspondent
     * effectivement à un objet du catalogue — un identifiant devenu invalide
     * n'est compté nulle part, sans qu'il faille le filtrer en amont.
     */
    fun compterParPiece(selection: Set<String>): List<ComptagePiece> = Piece.entries.map { piece ->
        val objetsDeLaPiece = CatalogueObjets.tous.filter { it.piece == piece }
        val coches = objetsDeLaPiece.count { it.id in selection }
        ComptagePiece(piece, coches, objetsDeLaPiece.size)
    }

    /** Le nombre total d'objets cochés, toutes pièces confondues, identifiants inconnus ignorés. */
    fun totalCoche(selection: Set<String>): Int = CatalogueObjets.tous.count { it.id in selection }

    /** Coche [id] s'il ne l'était pas, le décoche sinon. */
    fun basculerObjet(selection: Set<String>, id: String): Set<String> =
        if (id in selection) selection - id else selection + id

    /**
     * Coche ou décoche [piece] entière d'un geste : si tous ses objets sont
     * déjà cochés, elle les décoche tous ; sinon elle les coche tous — y
     * compris ceux déjà cochés isolément, pour finir sur un état net plutôt
     * que de basculer objet par objet.
     */
    fun basculerPiece(selection: Set<String>, piece: Piece): Set<String> {
        val idsDeLaPiece = CatalogueObjets.tous.filter { it.piece == piece }.mapTo(mutableSetOf()) { it.id }
        val entierementCochee = idsDeLaPiece.isNotEmpty() && idsDeLaPiece.all { it in selection }
        return if (entierementCochee) selection - idsDeLaPiece else selection + idsDeLaPiece
    }
}
