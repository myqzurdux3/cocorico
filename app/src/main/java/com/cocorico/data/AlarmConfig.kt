package com.cocorico.data

import com.cocorico.challenge.combine.EtapeCombine
import com.cocorico.challenge.combine.EtapesCombine
import com.cocorico.challenge.photo.CatalogueObjets
import com.cocorico.ring.NiveauxVolume
import java.time.DayOfWeek

/**
 * `COMBINE` enchaîne plusieurs épreuves choisies par l'utilisateur ; sa
 * composition vit dans [AlarmConfig.etapesCombine], pas ici.
 *
 * Ajouté en fin d'énumération : ces noms sont écrits en toutes lettres dans le
 * DataStore et dans la colonne `defi` de l'historique, et relus par `valueOf`.
 * Insérer une valeur au milieu ne casserait rien — c'est le nom qui est
 * persisté, pas l'ordinal — mais R8 renommerait les constantes sans la règle de
 * conservation de `proguard-rules.pro`.
 */
enum class ChallengeId { MATHS, POMPES, PHOTO, COMBINE }

enum class Difficulty { FACILE, MOYEN, DIFFICILE }

/**
 * La configuration unique de l'application. Il n'existe qu'une alarme : ce modèle
 * est un singleton persisté, pas un élément de liste.
 */
data class AlarmConfig(
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val ringtoneId: String,
    val challengeId: ChallengeId,
    val difficulty: Difficulty,
    val armed: Boolean,
    /**
     * Plafond sonore, en pourcentage du maximum de l'appareil. Borné par le
     * bas dans [com.cocorico.ring.NiveauxVolume] : un réveil doit rester un
     * réveil.
     */
    val volumeMaxPourcent: Int = 100,
    /** Clé d'API du juge distant, fournie par l'utilisateur. Jamais livrée avec l'application. */
    val cleApi: String = "",
    /**
     * Identifiants des objets que le tirage du défi photo peut piocher,
     * cochés depuis l'écran de sélection des pièces. Des identifiants, pas
     * des index : le catalogue évolue, et un index se déplacerait sous les
     * pieds d'une sélection déjà persistée.
     *
     * Une sélection vide n'est pas empêchée par l'écran de sélection — voir
     * `SelectionObjetsScreen` — et n'a pas besoin de l'être :
     * [CatalogueObjets.tirer] s'en accommode déjà en repliant sur le
     * catalogue entier plutôt que de bloquer le tirage, exactement comme il
     * ignore déjà un identifiant persisté qui n'existe plus dans le
     * catalogue. Rester bloqué devant une sirène est pire que photographier
     * un objet que l'utilisateur n'a pas explicitement coché.
     *
     * La valeur par défaut du paramètre (un ensemble vide) n'est là que pour
     * des constructions ponctuelles, en test notamment ; [DEFAULT] ci-dessous
     * coche tout le catalogue à l'installation, pour que le comportement
     * d'aujourd'hui — piocher dans tout le catalogue — soit conservé sans
     * configuration.
     */
    val objetsSelectionnes: Set<String> = emptySet(),
    /**
     * Composition du défi sur mesure : les épreuves, dans l'ordre où elles
     * seront demandées. N'a d'effet que si [challengeId] vaut
     * [ChallengeId.COMBINE].
     *
     * Toujours non vide après [assaini] : une suite vide serait résolue
     * d'emblée et arrêterait l'alarme sans rien demander.
     */
    val etapesCombine: List<EtapeCombine> = EtapesCombine.DEFAUT,
) {
    /**
     * Ramène les trois champs numériques dans leur plage utilisable, et ne
     * touche à rien d'autre.
     *
     * Pourquoi corriger plutôt que refuser : cette configuration est relue
     * depuis le disque, donc telle qu'une version antérieure ou un fichier
     * abîmé l'a laissée. Un `require` ferait lever au fond de
     * `NextOccurrenceCalculator`, à l'intérieur de `AlarmScheduler.schedule`,
     * dont tous les appelants avalent les exceptions — l'alarme disparaîtrait
     * sans un mot. Un réveil approximatif vaut mieux qu'un réveil absent.
     */
    fun assaini(): AlarmConfig = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        volumeMaxPourcent = NiveauxVolume.normaliser(volumeMaxPourcent),
        // Une clé collée depuis un courriel ou un gestionnaire de mots de passe
        // arrive souvent avec une espace ou un saut de ligne. Posée telle
        // quelle en en-tête HTTP, elle fait échouer **tous** les verdicts du
        // juge, et l'écran ne sait dire que « pas reconnu ».
        cleApi = cleApi.trim(),
        etapesCombine = EtapesCombine.assainir(etapesCombine),
    )

    companion object {
        val DEFAULT = AlarmConfig(
            hour = 6,
            minute = 30,
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            ringtoneId = "klaxon",
            challengeId = ChallengeId.MATHS,
            difficulty = Difficulty.MOYEN,
            armed = false,
            cleApi = "",
            objetsSelectionnes = CatalogueObjets.tous.map { it.id }.toSet(),
        )
    }
}
