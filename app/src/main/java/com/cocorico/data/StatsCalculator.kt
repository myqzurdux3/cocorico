package com.cocorico.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

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
    /** Les sept derniers réveils, du plus ancien au plus récent, temps bruts. */
    val dureesRecentesSecondes: List<Long>,
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
    private const val DUREE_MIN_VALIDE_MS = 1_000L
    private const val DUREE_MAX_VALIDE_MS = 3_600_000L

    /** Nombre de réveils affichés dans la rangée « derniers réveils » de l'écran. */
    private const val NOMBRE_RECENTS = 7

    /** Taille de chaque groupe comparé pour la progression. */
    private const val TAILLE_GROUPE_PROGRESSION = 5

    fun calculer(records: List<WakeRecord>, zone: ZoneId): Statistiques {
        if (records.isEmpty()) {
            return Statistiques(
                nombreTotal = 0,
                dureeCeMatinSecondes = null,
                dureesRecentesSecondes = emptyList(),
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

        val recentesSecondes = dureesMillis.takeLast(NOMBRE_RECENTS).map { it / 1000L }

        val pompesTentees = records.filter { it.defi == ChallengeId.POMPES.name || it.abandon }
        val tauxAbandon = if (pompesTentees.isEmpty()) {
            null
        } else {
            pompesTentees.count { it.abandon }.toDouble() / pompesTentees.size
        }

        return Statistiques(
            nombreTotal = records.size,
            dureeCeMatinSecondes = dureesMillis.last() / 1000L,
            dureesRecentesSecondes = recentesSecondes,
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
}
