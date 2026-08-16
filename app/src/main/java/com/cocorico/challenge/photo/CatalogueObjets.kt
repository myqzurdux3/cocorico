package com.cocorico.challenge.photo

import kotlin.random.Random

/**
 * Un objet du défi photo.
 *
 * [id] est stable et sert de clé d'exclusion d'un réveil à l'autre. [nom] est
 * affiché à l'utilisateur et envoyé tel quel au juge, en français : le modèle
 * de vision comprend la consigne sans qu'on ait à lui traduire quoi que ce
 * soit.
 */
data class ObjetPhoto(val id: String, val nom: String)

/**
 * Le catalogue d'objets du défi photo : une liste figée dans le code, d'une
 * trentaine d'objets qu'on trouve dans un logement ordinaire.
 *
 * Les objets restent volontairement courants et sans ambiguïté. Le tirage a
 * lieu pendant que l'alarme sonne : ce n'est pas le moment de faire chercher
 * un objet rare, ni de laisser un doute sur ce qui est demandé.
 */
object CatalogueObjets {

    val tous: List<ObjetPhoto> = listOf(
        ObjetPhoto("tasse", "Tasse"),
        ObjetPhoto("bouteille", "Bouteille"),
        ObjetPhoto("livre", "Livre"),
        // « footwear » revient pour les chaussures qui ne ressemblent pas
        // assez à une basket ou un mocassin pour que le modèle rende « shoe ».
        ObjetPhoto("chaussure", "Chaussure"),
        // Une plante en pot est parfois rendue par le contenant seul.
        ObjetPhoto("plante", "Plante"),
        ObjetPhoto("clavier", "Clavier"),
        ObjetPhoto("souris_ordinateur", "Souris d'ordinateur"),
        // Un réveil à affichage numérique est parfois rendu « alarm clock »
        // plutôt que « clock » — les deux désignent le même objet ici.
        ObjetPhoto("horloge", "Horloge"),
        ObjetPhoto("serviette", "Serviette"),
        ObjetPhoto("chaise", "Chaise"),
        ObjetPhoto("refrigerateur", "Réfrigérateur"),
        ObjetPhoto("brosse_a_dents", "Brosse à dents"),
        // Des lunettes de vue sont parfois classées dans la catégorie
        // générique « eyewear » plutôt que « glasses ».
        ObjetPhoto("lunettes", "Lunettes"),
        // Objet distinct des lunettes de vue : verres teintés, monture
        ObjetPhoto("lunettes_soleil", "Lunettes de soleil"),
        ObjetPhoto("montre", "Montre"),
        ObjetPhoto("sac_a_dos", "Sac à dos"),
        // Un coussin décoratif est parfois rendu « cushion » plutôt que
        // « pillow », qui désigne plutôt l'oreiller de lit.
        ObjetPhoto("coussin", "Coussin"),
        ObjetPhoto("lampe", "Lampe"),
        ObjetPhoto("cadre_photo", "Cadre photo"),
        ObjetPhoto("vase", "Vase"),
        ObjetPhoto("telecommande", "Télécommande"),
        // « telephone » revient pour un téléphone fixe, rare aujourd'hui,
        // mais laissé au cas où ; le cas courant est le smartphone.
        ObjetPhoto("telephone", "Téléphone"),
        ObjetPhoto("ordinateur_portable", "Ordinateur portable"),
        ObjetPhoto("ecouteurs", "Écouteurs"),
        ObjetPhoto("cle", "Clé"),
        ObjetPhoto("portefeuille", "Portefeuille"),
        ObjetPhoto("chapeau", "Chapeau"),
        ObjetPhoto("echarpe", "Écharpe"),
        ObjetPhoto("parapluie", "Parapluie"),
        ObjetPhoto("peluche", "Peluche"),
    )

    /**
     * Tire [nombre] objets distincts, sans remise, en excluant les
     * identifiants d'[exclus] tant que le catalogue le permet.
     *
     * Le nombre d'objets rendu est la promesse de la difficulté : l'écran de
     * défi affiche ce nombre-là, et l'utilisateur doit pouvoir tous les
     * valider. La fonction ne doit donc jamais rendre moins que ce qui est
     * demandé tant que le catalogue entier en contient assez — sans quoi le
     * défi serait plus facile que sa difficulté ne le promet, sans que rien
     * ne le signale.
     *
     * Les objets non exclus sont prioritaires : on ne pioche dans les objets
     * exclus que pour combler ce que le pool non exclu ne suffit pas à
     * fournir, qu'il soit trop petit ou totalement vide.
     *
     * Garde-fou : le nombre rendu est toujours borné à la taille du
     * catalogue, jamais de boucle sans fin à chercher un objet de plus qui
     * n'existe pas.
     */
    fun tirer(nombre: Int, exclus: Set<String>, alea: Random): List<ObjetPhoto> {
        val nombreBorne = nombre.coerceIn(0, tous.size)
        if (nombreBorne == 0) return emptyList()
        val nonExclus = tous.filter { it.id !in exclus }
        if (nonExclus.size >= nombreBorne) {
            return nonExclus.shuffled(alea).take(nombreBorne)
        }
        // Le pool non exclu ne suffit pas : on le rend en entier et on
        // complète avec des objets exclus, tirés au hasard parmi eux.
        val manquant = nombreBorne - nonExclus.size
        val complement = tous.filter { it.id in exclus }.shuffled(alea).take(manquant)
        return nonExclus.shuffled(alea) + complement
    }
}
