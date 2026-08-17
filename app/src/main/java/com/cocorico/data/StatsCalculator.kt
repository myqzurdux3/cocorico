package com.cocorico.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Bloc de statistiques affiché sur l'écran de statistiques. Pur, comme
 * [SerieCalculator] : c'est la règle du projet, toute décision testable vit
 * hors d'Android.
 *
 * Les champs nullables signalent l'absence de matière (liste vide, ou aucune
 * tentative de pompes) plutôt qu'un zéro trompeur — un zéro affiché se lit
 * comme une vraie mesure, pas comme une absence.
 */
data class Statistiques(
    /** Nombre de réveils enregistrés, aberrants compris : c'est un compte d'événements, pas une moyenne. */
    val nombreTotal: Int,
    /** Temps du dernier réveil, brut — y compris s'il est aberrant : c'est une donnée individuelle, pas un agrégat. */
    val dureeCeMatinSecondes: Long?,
    /**
     * Les sept derniers réveils *valides*, du plus ancien au plus récent —
     * voir [ReveilRecent] et la note d'exclusion dans [StatsCalculator.calculer].
     */
    val reveilsRecents: List<ReveilRecent>,
    /** Moyenne des temps valides (voir [StatsCalculator.dureeValideMillis]). */
    val dureeMoyenneSecondes: Long?,
    val meilleureDureeSecondes: Long?,
    val pireDureeSecondes: Long?,
    /** Somme des temps valides depuis le tout premier réveil enregistré. */
    val dureeCumuleeSecondes: Long,
    /** Part de renoncements parmi les seules tentatives de pompes (réussies ou renoncées). */
    val tauxAbandonPompes: Double?,
    /** Total des erreurs de calcul, tous réveils confondus. */
    val erreursCumulees: Int,
    /** Jour de la semaine où le temps moyen (sur temps valides) est le plus long. */
    val jourLePlusLent: DayOfWeek?,
    /**
     * Écart, en secondes, entre la moyenne des cinq derniers réveils valides et
     * celle des cinq premiers. Négatif : l'utilisateur va plus vite qu'à ses
     * débuts. Positif : il traîne plus. `null` s'il n'y a pas encore dix
     * réveils valides et distincts à comparer.
     */
    val progressionSecondes: Long?,
)

/**
 * Détail d'un réveil affiché comme barre dans le graphique des derniers
 * réveils de l'écran de statistiques. Porte tout ce qu'il faut pour remplir la
 * fiche de détail au clic (date, temps, défi, renoncement) : cette résolution
 * (fuseau compris) se fait ici, dans la classe pure, pas dans le composable.
 */
data class ReveilRecent(
    /** Jour civil de l'alarme, dans le fuseau de l'utilisateur. */
    val date: LocalDate,
    /** Temps mis pour faire taire l'alarme, en secondes. Toujours dans les bornes valides : voir [StatsCalculator.calculer]. */
    val dureeSecondes: Long,
    /** Identifiant du défi effectivement accompli, au format [ChallengeId]. */
    val defi: String,
    /** Vrai si l'utilisateur a renoncé au défi initial pour se rabattre sur les calculs. */
    val abandon: Boolean,
)

/**
 * Calcule [Statistiques] à partir de l'historique complet des réveils. Pur :
 * aucun import `android.*`, testé unitairement, réutilisable tel quel côté
 * Compose comme côté test.
 */
object StatsCalculator {

    /**
     * Bornes de plausibilité d'un temps de résolution. En dessous d'une
     * seconde, personne ne résout un calcul ni ne compte une pompe — c'est un
     * artefact d'horloge. Au-delà d'une heure, ce n'est plus l'utilisateur qui
     * traîne, c'est le téléphone qui a fait autre chose. Ces réveils comptent
     * dans [Statistiques.nombreTotal] — ils ont bien eu lieu — mais sont exclus
     * de tout agrégat de durée : une seule valeur aberrante ferait sinon
     * exploser la moyenne, le meilleur, le pire ou le cumul.
     */
    // `internal` et non `private` : `SerieCalculator` applique exactement le
    // même filtre, et deux moyennes du même chiffre ne doivent pas répondre
    // différemment selon l'écran qui les demande.
    internal const val DUREE_MIN_VALIDE_MS = 1_000L
    internal const val DUREE_MAX_VALIDE_MS = 3_600_000L

    /** Nombre de réveils affichés dans la rangée « derniers réveils » de l'écran. */
    private const val NOMBRE_RECENTS = 7

    /** Taille de chaque groupe comparé pour la progression. */
    private const val TAILLE_GROUPE_PROGRESSION = 5

    fun calculer(records: List<WakeRecord>, zone: ZoneId): Statistiques {
        if (records.isEmpty()) {
            return Statistiques(
                nombreTotal = 0,
                dureeCeMatinSecondes = null,
                reveilsRecents = emptyList(),
                dureeMoyenneSecondes = null,
                meilleureDureeSecondes = null,
                pireDureeSecondes = null,
                dureeCumuleeSecondes = 0L,
                tauxAbandonPompes = null,
                erreursCumulees = 0,
                jourLePlusLent = null,
                progressionSecondes = null,
            )
        }

        // records est trié par alarmeAt croissant (contrat de WakeRecordDao.tous()).
        val dureesMillis = records.map { it.resoluAt - it.alarmeAt }
        val validesMillis = dureesMillis.filter { it in DUREE_MIN_VALIDE_MS..DUREE_MAX_VALIDE_MS }
        val validesSecondes = validesMillis.map { it / 1000L }

        // Le graphique compare des matins entre eux, tout comme la moyenne, le
        // meilleur, le pire ou le cumul : une durée aberrante y fausserait la
        // lecture exactement comme dans un agrégat (une seule barre écraserait
        // l'échelle des six autres), donc elle en est exclue au même titre. Seul
        // « ce matin », donnée individuelle et non comparative, reste brut.
        val reveilsRecents = records.indices
            .filter { dureesMillis[it] in DUREE_MIN_VALIDE_MS..DUREE_MAX_VALIDE_MS }
            .takeLast(NOMBRE_RECENTS)
            .map { i ->
                ReveilRecent(
                    date = Instant.ofEpochMilli(records[i].alarmeAt).atZone(zone).toLocalDate(),
                    dureeSecondes = dureesMillis[i] / 1000L,
                    defi = records[i].defi,
                    abandon = records[i].abandon,
                )
            }

        val pompesTentees = records.filter { it.defi == ChallengeId.POMPES.name || it.abandon }
        val tauxAbandon = if (pompesTentees.isEmpty()) {
            null
        } else {
            pompesTentees.count { it.abandon }.toDouble() / pompesTentees.size
        }

        return Statistiques(
            nombreTotal = records.size,
            dureeCeMatinSecondes = dureesMillis.last() / 1000L,
            reveilsRecents = reveilsRecents,
            dureeMoyenneSecondes = validesSecondes.moyenneOuNull(),
            meilleureDureeSecondes = validesSecondes.minOrNull(),
            pireDureeSecondes = validesSecondes.maxOrNull(),
            dureeCumuleeSecondes = validesSecondes.sum(),
            tauxAbandonPompes = tauxAbandon,
            erreursCumulees = records.sumOf { it.erreurs },
            jourLePlusLent = jourLePlusLent(records, dureesMillis, zone),
            progressionSecondes = progression(validesSecondes),
        )
    }

    private fun List<Long>.moyenneOuNull(): Long? = if (isEmpty()) null else sum() / size

    /**
     * Regroupe les temps valides par jour de la semaine (fuseau de
     * l'utilisateur, comme [SerieCalculator.serie]) et renvoie celui dont la
     * moyenne est la plus longue. `null` s'il n'y a aucun temps valide.
     */
    private fun jourLePlusLent(
        records: List<WakeRecord>,
        dureesMillis: List<Long>,
        zone: ZoneId,
    ): DayOfWeek? {
        val parJour = records.indices
            .filter { dureesMillis[it] in DUREE_MIN_VALIDE_MS..DUREE_MAX_VALIDE_MS }
            .groupBy(
                keySelector = { i -> Instant.ofEpochMilli(records[i].alarmeAt).atZone(zone).dayOfWeek },
                valueTransform = { i -> dureesMillis[i] },
            )
        return parJour.maxByOrNull { (_, durees) -> durees.sum() / durees.size }?.key
    }

    /**
     * Compare la moyenne des [TAILLE_GROUPE_PROGRESSION] premiers réveils
     * valides à celle des [TAILLE_GROUPE_PROGRESSION] derniers. Exige deux
     * groupes disjoints — donc au moins deux fois la taille du groupe — sans
     * quoi un même réveil compterait dans les deux moyennes comparées, ce qui
     * n'aurait plus de sens.
     */
    private fun progression(validesSecondes: List<Long>): Long? {
        if (validesSecondes.size < TAILLE_GROUPE_PROGRESSION * 2) return null
        val premiers = validesSecondes.take(TAILLE_GROUPE_PROGRESSION)
        val derniers = validesSecondes.takeLast(TAILLE_GROUPE_PROGRESSION)
        val moyennePremiers = premiers.sum() / TAILLE_GROUPE_PROGRESSION
        val moyenneDerniers = derniers.sum() / TAILLE_GROUPE_PROGRESSION
        return moyenneDerniers - moyennePremiers
    }

    /**
     * Formate une durée en unités humaines : heures, minutes ou secondes selon
     * l'ordre de grandeur. Sur ce format `Xh`, `Xmin` ou `Xs`, le plus grand cas
     * d'usage — le temps cumulé, potentiellement des milliers de secondes —
     * reste lisible, quand personne ne songerait à afficher un temps de
     * résolution du matin autrement qu'en secondes. Une seule fonction couvre
     * tous les cas, sans jamais perdre le signe pour les écarts négatifs de
     * progression.
     */
    fun formatDuree(secondes: Long): String {
        val signe = if (secondes < 0) "-" else ""
        val valeurAbsolue = abs(secondes)
        val heures = valeurAbsolue / 3600
        val minutes = (valeurAbsolue % 3600) / 60
        val restantSecondes = valeurAbsolue % 60
        return when {
            heures > 0 -> "$signe${heures}h${"%02d".format(minutes)}"
            minutes > 0 -> "$signe${minutes}min${"%02d".format(restantSecondes)}"
            else -> "$signe${valeurAbsolue}s"
        }
    }

    /**
     * Repères de lecture du graphique des derniers réveils : la valeur au
     * sommet (la plus longue durée affichée) et la position, en proportion de
     * la hauteur du graphique (0 = base, 1 = sommet), de la ligne de moyenne.
     *
     * Une rangée de sept barres à peine plus large qu'un écran de téléphone n'a
     * pas la place pour un axe gradué à intervalles réguliers sans casser la
     * règle des 15 sp du projet (il faudrait une étiquette par graduation).
     * Deux repères suffisent en revanche à situer n'importe quelle barre : le
     * sommet, étiqueté avec sa valeur, et la moyenne — déjà mise en avant
     * ailleurs sur l'écran sous « Temps moyen », donc immédiatement
     * réutilisable comme point de comparaison — la base à zéro restant
     * implicite et évidente sur un graphique qui part toujours du bas.
     */
    data class EchelleGraphique(
        /** Valeur, en secondes, représentée par une barre à 100 % de la hauteur. Jamais nulle : sans plancher, une liste vide ou nulle rendrait toute barre infinie ou indéfinie. */
        val maxSecondes: Long,
        /** Position 0..1 de la ligne de moyenne dans la hauteur du graphique ; `null` si aucune moyenne n'est disponible. */
        val positionMoyenne: Float?,
    )

    /**
     * Calcule [EchelleGraphique] à partir des durées affichées et de la
     * moyenne de référence. `dureesSecondes` est supposée déjà expurgée des
     * durées aberrantes par l'appelant (voir [calculer]) ; cette fonction se
     * contente de poser les repères, elle ne filtre rien.
     */
    fun echelle(dureesSecondes: List<Long>, moyenneSecondes: Long?): EchelleGraphique {
        // L'échelle doit contenir tout ce que le graphique dessine, la ligne de
        // moyenne comprise. Se caler sur les seules barres affichées plaquait
        // cette ligne au sommet dès que les sept derniers matins étaient plus
        // rapides que la moyenne historique — indiscernable, alors, d'une
        // moyenne exactement égale au pire matin affiché.
        val maxBarres = dureesSecondes.maxOrNull() ?: 1L
        val max = maxOf(maxBarres, moyenneSecondes ?: 1L).coerceAtLeast(1L)
        val position = moyenneSecondes?.let { moyenne -> (moyenne.toFloat() / max.toFloat()).coerceIn(0f, 1f) }
        return EchelleGraphique(maxSecondes = max, positionMoyenne = position)
    }

    /**
     * Bascule la sélection d'une barre du graphique par son rang dans la liste
     * affichée : un appui sur la barre déjà sélectionnée la désélectionne
     * (second appui), un appui sur une autre bascule directement dessus — la
     * sélection se déplace en un seul geste, sans repasser par « aucune
     * sélection ».
     */
    fun basculerSelection(rangSelectionne: Int?, rangClique: Int): Int? =
        if (rangSelectionne == rangClique) null else rangClique
}
