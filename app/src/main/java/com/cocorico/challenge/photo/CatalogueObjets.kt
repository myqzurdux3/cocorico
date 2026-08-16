package com.cocorico.challenge.photo

import kotlin.random.Random

/**
 * Un objet du défi photo.
 *
 * [id] est stable et sert de clé d'exclusion d'un réveil à l'autre. [nom] est
 * affiché à l'utilisateur, en français. [etiquettes] sont les étiquettes que
 * rend le modèle de reconnaissance embarqué de ML Kit pour cet objet — en
 * anglais et en minuscules, imposées par [JugementPhoto].
 */
data class ObjetPhoto(val id: String, val nom: String, val etiquettes: Set<String>)

/**
 * Le catalogue d'objets du défi photo : une liste figée dans le code, d'une
 * trentaine d'objets qu'on trouve dans un logement ordinaire, et que la
 * reconnaissance embarquée identifie de façon fiable.
 *
 * Chaque objet porte au moins une étiquette ML Kit ; plusieurs quand le modèle
 * rend des étiquettes voisines pour le même objet physique — refuser l'une des
 * deux serait arbitraire et laisserait l'utilisateur bloqué devant une sirène
 * pour une photo pourtant correcte.
 */
object CatalogueObjets {

    val tous: List<ObjetPhoto> = listOf(
        // Le modèle rend « mug » pour une tasse à anse épaisse et « cup » pour
        // une tasse plus fine ou une tasse à café d'appoint.
        ObjetPhoto("tasse", "Tasse", setOf("mug", "cup")),
        ObjetPhoto("bouteille", "Bouteille", setOf("bottle")),
        ObjetPhoto("livre", "Livre", setOf("book")),
        // « footwear » revient pour les chaussures qui ne ressemblent pas
        // assez à une basket ou un mocassin pour que le modèle rende « shoe ».
        ObjetPhoto("chaussure", "Chaussure", setOf("shoe", "footwear")),
        // Une plante en pot est parfois rendue par le contenant seul.
        ObjetPhoto("plante", "Plante", setOf("houseplant", "plant", "flowerpot")),
        ObjetPhoto("clavier", "Clavier", setOf("computer keyboard")),
        ObjetPhoto("souris_ordinateur", "Souris d'ordinateur", setOf("computer mouse")),
        // Un réveil à affichage numérique est parfois rendu « alarm clock »
        // plutôt que « clock » — les deux désignent le même objet ici.
        ObjetPhoto("horloge", "Horloge", setOf("clock", "alarm clock")),
        ObjetPhoto("serviette", "Serviette", setOf("towel")),
        ObjetPhoto("chaise", "Chaise", setOf("chair")),
        ObjetPhoto("refrigerateur", "Réfrigérateur", setOf("refrigerator")),
        ObjetPhoto("brosse_a_dents", "Brosse à dents", setOf("toothbrush")),
        // Des lunettes de vue sont parfois classées dans la catégorie
        // générique « eyewear » plutôt que « glasses ».
        ObjetPhoto("lunettes", "Lunettes", setOf("glasses", "eyewear")),
        // Objet distinct des lunettes de vue : verres teintés, monture
        // différente ; le modèle a une étiquette dédiée « sunglasses », et
        // « goggles » revient pour des montures plus enveloppantes.
        ObjetPhoto("lunettes_soleil", "Lunettes de soleil", setOf("sunglasses", "goggles")),
        ObjetPhoto("montre", "Montre", setOf("watch", "wristwatch")),
        ObjetPhoto("sac_a_dos", "Sac à dos", setOf("backpack")),
        // Un coussin décoratif est parfois rendu « cushion » plutôt que
        // « pillow », qui désigne plutôt l'oreiller de lit.
        ObjetPhoto("coussin", "Coussin", setOf("pillow", "cushion")),
        ObjetPhoto("lampe", "Lampe", setOf("lamp", "lighting")),
        ObjetPhoto("cadre_photo", "Cadre photo", setOf("picture frame")),
        ObjetPhoto("vase", "Vase", setOf("vase")),
        ObjetPhoto("telecommande", "Télécommande", setOf("remote control")),
        // « telephone » revient pour un téléphone fixe, rare aujourd'hui,
        // mais laissé au cas où ; le cas courant est le smartphone.
        ObjetPhoto("telephone", "Téléphone", setOf("mobile phone", "telephone")),
        ObjetPhoto("ordinateur_portable", "Ordinateur portable", setOf("laptop")),
        ObjetPhoto("ecouteurs", "Écouteurs", setOf("headphones", "earphone")),
        ObjetPhoto("cle", "Clé", setOf("key")),
        ObjetPhoto("portefeuille", "Portefeuille", setOf("wallet")),
        ObjetPhoto("chapeau", "Chapeau", setOf("hat", "cap")),
        ObjetPhoto("echarpe", "Écharpe", setOf("scarf")),
        ObjetPhoto("parapluie", "Parapluie", setOf("umbrella")),
        // « stuffed toy » est l'étiquette générique que rend le modèle quand
        // il n'identifie pas l'animal précis de la peluche.
        ObjetPhoto("peluche", "Peluche", setOf("teddy bear", "stuffed toy")),
    )

    /**
     * Tire [nombre] objets distincts, sans remise, en excluant les
     * identifiants d'[exclus] tant que le catalogue le permet.
     *
     * Deux garde-fous, pour qu'un écran de défi ne reste jamais vide devant
     * une alarme qui sonne :
     * - le nombre rendu est toujours borné à la taille du catalogue, jamais
     *   de boucle sans fin à chercher un objet de plus qui n'existe pas ;
     * - si l'exclusion ne laisse aucun objet disponible, on retombe sur le
     *   catalogue entier plutôt que de rendre une liste vide — répéter un
     *   objet déjà vu vaut mieux qu'un défi impossible à afficher.
     */
    fun tirer(nombre: Int, exclus: Set<String>, alea: Random): List<ObjetPhoto> {
        val nombreBorne = nombre.coerceIn(0, tous.size)
        if (nombreBorne == 0) return emptyList()
        val pool = tous.filter { it.id !in exclus }.ifEmpty { tous }
        return pool.shuffled(alea).take(nombreBorne.coerceAtMost(pool.size))
    }
}
