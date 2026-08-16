package com.cocorico.challenge.photo

import kotlin.random.Random

/**
 * Une pièce du logement, utilisée pour regrouper le catalogue à l'écran de
 * sélection. Le découpage retenu couvre un logement ordinaire sans viser
 * l'exhaustivité : mieux vaut six pièces bien remplies qu'une dizaine dont
 * certaines ne contiendraient qu'un ou deux objets.
 *
 * [nom] est affiché tel quel à l'écran de sélection, en français.
 */
enum class Piece(val nom: String) {
    CUISINE("Cuisine"),
    SALLE_DE_BAIN("Salle de bain"),
    CHAMBRE("Chambre"),
    SALON("Salon"),
    ENTREE("Entrée"),
    BUREAU("Bureau"),
}

/**
 * Un objet du défi photo.
 *
 * [id] est stable et sert de clé d'exclusion d'un réveil à l'autre, ainsi que
 * de clé de sélection persistée par l'utilisateur. [nom] est affiché à
 * l'utilisateur et envoyé tel quel au juge, en français : le modèle de vision
 * comprend la consigne sans qu'on ait à lui traduire quoi que ce soit.
 *
 * [piece] range l'objet dans le découpage de l'écran de sélection.
 * Sa valeur par défaut n'est là que pour les tests écrits avant l'existence
 * des pièces (ils construisent un [ObjetPhoto] sans s'en soucier) : tout
 * objet réel du catalogue ci-dessous précise la sienne explicitement.
 */
data class ObjetPhoto(val id: String, val nom: String, val piece: Piece = Piece.SALON)

/**
 * Le catalogue d'objets du défi photo, réparti par pièce du logement.
 *
 * Les objets restent volontairement courants et sans ambiguïté. Le tirage a
 * lieu pendant que l'alarme sonne : ce n'est pas le moment de faire chercher
 * un objet rare, ni de laisser un doute sur ce qui est demandé.
 */
object CatalogueObjets {

    val tous: List<ObjetPhoto> = listOf(
        // --- Cuisine ---
        ObjetPhoto("tasse", "Tasse", Piece.CUISINE),
        ObjetPhoto("bouteille", "Bouteille", Piece.CUISINE),
        ObjetPhoto("refrigerateur", "Réfrigérateur", Piece.CUISINE),
        ObjetPhoto("verre", "Verre", Piece.CUISINE),
        ObjetPhoto("assiette", "Assiette", Piece.CUISINE),
        ObjetPhoto("fourchette", "Fourchette", Piece.CUISINE),
        ObjetPhoto("couteau", "Couteau", Piece.CUISINE),
        ObjetPhoto("cuillere", "Cuillère", Piece.CUISINE),
        ObjetPhoto("casserole", "Casserole", Piece.CUISINE),
        // « poêle » désigne ici l'ustensile de cuisson, jamais l'appareil de
        // chauffage : le mot est courant dans les deux sens, mais seul le
        // premier se photographie dans une cuisine.
        ObjetPhoto("poele", "Poêle", Piece.CUISINE),
        ObjetPhoto("bouilloire", "Bouilloire", Piece.CUISINE),
        ObjetPhoto("grille_pain", "Grille-pain", Piece.CUISINE),
        ObjetPhoto("four_micro_ondes", "Four à micro-ondes", Piece.CUISINE),
        ObjetPhoto("eponge", "Éponge", Piece.CUISINE),
        ObjetPhoto("passoire", "Passoire", Piece.CUISINE),

        // --- Salle de bain ---
        ObjetPhoto("serviette", "Serviette", Piece.SALLE_DE_BAIN),
        ObjetPhoto("brosse_a_dents", "Brosse à dents", Piece.SALLE_DE_BAIN),
        ObjetPhoto("dentifrice", "Dentifrice", Piece.SALLE_DE_BAIN),
        ObjetPhoto("savon", "Savon", Piece.SALLE_DE_BAIN),
        ObjetPhoto("shampooing", "Shampooing", Piece.SALLE_DE_BAIN),
        ObjetPhoto("rasoir", "Rasoir", Piece.SALLE_DE_BAIN),
        ObjetPhoto("peigne", "Peigne", Piece.SALLE_DE_BAIN),
        ObjetPhoto("brosse_a_cheveux", "Brosse à cheveux", Piece.SALLE_DE_BAIN),
        ObjetPhoto("papier_toilette", "Papier toilette", Piece.SALLE_DE_BAIN),
        ObjetPhoto("pese_personne", "Pèse-personne", Piece.SALLE_DE_BAIN),

        // --- Chambre ---
        // Un réveil à affichage numérique est parfois rendu « alarm clock »
        // plutôt que « clock » — les deux désignent le même objet ici.
        ObjetPhoto("horloge", "Horloge", Piece.CHAMBRE),
        ObjetPhoto("lampe", "Lampe de chevet", Piece.CHAMBRE),
        // Des lunettes de vue sont parfois classées dans la catégorie
        // générique « eyewear » plutôt que « glasses ».
        ObjetPhoto("lunettes", "Lunettes", Piece.CHAMBRE),
        ObjetPhoto("montre", "Montre", Piece.CHAMBRE),
        ObjetPhoto("peluche", "Peluche", Piece.CHAMBRE),
        // Distinct du coussin décoratif du salon : l'oreiller de lit.
        ObjetPhoto("oreiller", "Oreiller", Piece.CHAMBRE),
        ObjetPhoto("couette", "Couette", Piece.CHAMBRE),
        ObjetPhoto("cintre", "Cintre", Piece.CHAMBRE),
        ObjetPhoto("chaussette", "Chaussette", Piece.CHAMBRE),
        ObjetPhoto("pantoufle", "Pantoufle", Piece.CHAMBRE),

        // --- Salon ---
        ObjetPhoto("livre", "Livre", Piece.SALON),
        ObjetPhoto("chaise", "Chaise", Piece.SALON),
        // Un coussin décoratif est parfois rendu « cushion » plutôt que
        // « pillow », qui désigne plutôt l'oreiller de lit.
        ObjetPhoto("coussin", "Coussin", Piece.SALON),
        ObjetPhoto("cadre_photo", "Cadre photo", Piece.SALON),
        ObjetPhoto("vase", "Vase", Piece.SALON),
        ObjetPhoto("telecommande", "Télécommande", Piece.SALON),
        // « telephone » revient pour un téléphone fixe, rare aujourd'hui,
        // mais laissé au cas où ; le cas courant est le smartphone.
        ObjetPhoto("telephone", "Téléphone", Piece.SALON),
        // Une plante en pot est parfois rendue par le contenant seul.
        ObjetPhoto("plante", "Plante", Piece.SALON),
        ObjetPhoto("tapis", "Tapis", Piece.SALON),
        ObjetPhoto("canape", "Canapé", Piece.SALON),
        ObjetPhoto("lampadaire", "Lampadaire", Piece.SALON),
        ObjetPhoto("enceinte", "Enceinte", Piece.SALON),

        // --- Entrée ---
        ObjetPhoto("cle", "Clé", Piece.ENTREE),
        ObjetPhoto("portefeuille", "Portefeuille", Piece.ENTREE),
        ObjetPhoto("chapeau", "Chapeau", Piece.ENTREE),
        ObjetPhoto("echarpe", "Écharpe", Piece.ENTREE),
        ObjetPhoto("parapluie", "Parapluie", Piece.ENTREE),
        ObjetPhoto("sac_a_dos", "Sac à dos", Piece.ENTREE),
        ObjetPhoto("lunettes_soleil", "Lunettes de soleil", Piece.ENTREE),
        // « footwear » revient pour les chaussures qui ne ressemblent pas
        // assez à une basket ou un mocassin pour que le modèle rende « shoe ».
        ObjetPhoto("chaussure", "Chaussure", Piece.ENTREE),
        ObjetPhoto("valise", "Valise", Piece.ENTREE),
        ObjetPhoto("gants", "Gants", Piece.ENTREE),

        // --- Bureau ---
        ObjetPhoto("clavier", "Clavier", Piece.BUREAU),
        ObjetPhoto("souris_ordinateur", "Souris d'ordinateur", Piece.BUREAU),
        ObjetPhoto("ordinateur_portable", "Ordinateur portable", Piece.BUREAU),
        ObjetPhoto("ecouteurs", "Écouteurs", Piece.BUREAU),
        ObjetPhoto("casque_audio", "Casque audio", Piece.BUREAU),
        ObjetPhoto("stylo", "Stylo", Piece.BUREAU),
        ObjetPhoto("cahier", "Cahier", Piece.BUREAU),
        ObjetPhoto("agrafeuse", "Agrafeuse", Piece.BUREAU),
        ObjetPhoto("calculatrice", "Calculatrice", Piece.BUREAU),
        ObjetPhoto("chargeur", "Chargeur", Piece.BUREAU),
    )

    /**
     * Filtre un ensemble d'identifiants persistés : ceux qui ne correspondent
     * plus à aucun objet du catalogue actuel — un objet retiré depuis une
     * mise à jour — sont ignorés plutôt que de faire planter la lecture de la
     * configuration ou de fausser un comptage.
     */
    fun idsValides(bruts: Set<String>): Set<String> {
        if (bruts.isEmpty()) return emptySet()
        val connus = tous.mapTo(mutableSetOf()) { it.id }
        return bruts.filterTo(mutableSetOf()) { it in connus }
    }

    /**
     * Tire [nombre] objets distincts, sans remise, en piochant en priorité
     * dans [selection] et en excluant les identifiants d'[exclus] tant que le
     * catalogue le permet.
     *
     * Le nombre d'objets rendu est la promesse de la difficulté : l'écran de
     * défi affiche ce nombre-là, et l'utilisateur doit pouvoir tous les
     * valider. La fonction ne doit donc jamais rendre moins que ce qui est
     * demandé tant que le catalogue entier en contient assez — sans quoi le
     * défi serait plus facile que sa difficulté ne le promet, sans que rien
     * ne le signale.
     *
     * [selection] restreint le pool aux objets que l'utilisateur a cochés à
     * l'écran de sélection. **Une sélection vide vaut absence de
     * restriction** : c'est le repli qui évite qu'une sélection totalement
     * décochée — ou entièrement composée d'identifiants qui n'existent plus
     * dans le catalogue — ne rende le tirage impossible devant une sirène.
     * Rester bloqué est pire que photographier un objet non explicitement
     * coché ; voir la KDoc d'[com.cocorico.data.AlarmConfig.objetsSelectionnes].
     * Ce repli est une sécurité d'exécution, pas une convention d'affichage :
     * l'écran de sélection montre l'état réel de la sélection, coché ou non,
     * il ne la fait pas paraître pleine quand elle est vide.
     *
     * Priorité, du plus au moins désirable : objets sélectionnés et non
     * exclus, puis objets sélectionnés mais exclus (on répète un objet du
     * réveil précédent plutôt que de sortir du choix de l'utilisateur), et
     * enfin, seulement si la sélection ne suffit toujours pas, des objets hors
     * sélection — c'est le même repli que celui déjà appliqué à l'exclusion,
     * étendu d'un niveau.
     *
     * Garde-fou : le nombre rendu est toujours borné à la taille du
     * catalogue, jamais de boucle sans fin à chercher un objet de plus qui
     * n'existe pas.
     */
    fun tirer(
        nombre: Int,
        exclus: Set<String>,
        alea: Random,
        selection: Set<String> = emptySet(),
    ): List<ObjetPhoto> {
        val nombreBorne = nombre.coerceIn(0, tous.size)
        if (nombreBorne == 0) return emptyList()

        val poolSelectionne = if (selection.isEmpty()) tous else tous.filter { it.id in selection }
        // Repli supplémentaire : une sélection non vide en apparence peut ne
        // plus correspondre à aucun objet réel (tous ses identifiants ont
        // disparu du catalogue) — le pool serait alors vide malgré une
        // sélection non vide, pour la même raison que ci-dessus.
        val pool = poolSelectionne.ifEmpty { tous }

        val nonExclus = pool.filter { it.id !in exclus }
        if (nonExclus.size >= nombreBorne) {
            return nonExclus.shuffled(alea).take(nombreBorne)
        }

        // Le pool non exclu ne suffit pas : on le rend en entier et on
        // complète avec des objets du pool exclus, tirés au hasard parmi eux.
        val manquantDansPool = nombreBorne - nonExclus.size
        val complementExclusDuPool = pool.filter { it.id in exclus }.shuffled(alea).take(manquantDansPool)
        val partiel = nonExclus.shuffled(alea) + complementExclusDuPool
        if (partiel.size >= nombreBorne) return partiel

        // Même la sélection entière, exclus compris, ne suffit pas : on
        // complète avec le reste du catalogue plutôt que de rendre moins
        // d'objets que la difficulté ne le promet.
        val idsPartiel = partiel.mapTo(mutableSetOf()) { it.id }
        val manquantHorsPool = nombreBorne - partiel.size
        val complementHorsPool = tous.filter { it.id !in idsPartiel }.shuffled(alea).take(manquantHorsPool)
        return partiel + complementHorsPool
    }
}
