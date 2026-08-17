package com.cocorico.alarm

import android.content.Context

/**
 * Mémorise si une alarme est en train de sonner. Survit au kill du processus et
 * au redémarrage : c'est ce qui permet à BootReceiver de relancer une alarme
 * interrompue par une extinction du téléphone.
 *
 * Le drapeau seul ne suffit pas : il n'est effacé que par une résolution du
 * défi. Un arrêt forcé depuis les réglages, un tueur de tâches constructeur ou
 * un crash le laissent à `true` pour toujours, et au redémarrage suivant
 * l'application ferait sonner une alarme fantôme. On horodate donc chaque signe
 * de vie et on considère le drapeau comme faux au-delà de [FENETRE_VALIDITE_MS].
 */
object AlarmState {

    private const val FICHIER = "cocorico_alarm_state"
    private const val CLE_EN_COURS = "alarme_en_cours"
    private const val CLE_DERNIER_SIGNE = "alarme_dernier_signe"

    /**
     * Distincte de [CLE_DERNIER_SIGNE], et il faut que ça le reste : le signe
     * de vie est réécrit à chaque passage du filet de secours, donc toutes les
     * 30 secondes. Celle-ci n'est posée qu'une fois, au vrai déclenchement.
     */
    private const val CLE_DECLENCHEMENT = "alarme_declenchement"

    /**
     * Une alarme qui hurle depuis plus d'une heure sans être résolue est déjà
     * perdue : la relancer au démarrage suivant n'aiderait personne, alors
     * qu'une alarme fantôme au milieu de la journée est un bug visible.
     */
    const val FENETRE_VALIDITE_MS = 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /**
     * Décision pure, testable sans Android : le drapeau est-il encore crédible ?
     *
     * Un horodatage absent (installation antérieure à cette version) ou un delta
     * négatif (horloge reculée, changement de fuseau) rendent le drapeau
     * inexploitable : on tranche du côté du silence, jamais de l'alarme
     * fantôme — l'alarme réelle, elle, reste programmée par ailleurs.
     */
    fun estEncoreFraiche(
        enCours: Boolean,
        dernierSigneMs: Long,
        maintenantMs: Long,
        fenetreMs: Long = FENETRE_VALIDITE_MS,
    ): Boolean {
        if (!enCours || dernierSigneMs <= 0L) return false
        val ecart = maintenantMs - dernierSigneMs
        return ecart in 0L until fenetreMs
    }

    /** Signe de vie : l'alarme sonne, et elle sonnait encore à cet instant. */
    fun marquerDemarree(context: Context) {
        prefs(context).edit()
            .putBoolean(CLE_EN_COURS, true)
            .putLong(CLE_DERNIER_SIGNE, System.currentTimeMillis())
            .commit()
    }

    /**
     * Retient l'instant du **vrai** déclenchement, une seule fois par alarme.
     *
     * L'écran d'alarme horodatait son propre `onCreate`. Si le service doit le
     * relancer après une mort de l'activité, la durée enregistrée dans
     * l'historique repartait de zéro et faussait toutes les statistiques. Écrit
     * ici plutôt que dans l'activité : le service, lui, survit à l'écran.
     */
    fun marquerDeclenchement(context: Context) {
        val prefs = prefs(context)
        val retenu = instantARetenir(
            existant = prefs.getLong(CLE_DECLENCHEMENT, 0L),
            maintenant = System.currentTimeMillis(),
        )
        prefs.edit().putLong(CLE_DECLENCHEMENT, retenu).commit()
    }

    /** L'instant du déclenchement en cours, ou 0 si aucune alarme ne sonne. */
    fun instantDeclenchement(context: Context): Long =
        prefs(context).getLong(CLE_DECLENCHEMENT, 0L)

    /**
     * Décision pure derrière [marquerDeclenchement] : on garde le premier
     * horodatage plausible. Le service repasse par ce chemin à chaque relance
     * (`START_STICKY`, secours, processus tué) et réécrire ferait repartir la
     * durée de zéro à chaque fois ; à l'inverse, une valeur nulle ou négative
     * ne peut venir que d'un fichier abîmé et donnerait des durées de plusieurs
     * décennies dans les statistiques.
     */
    fun instantARetenir(existant: Long, maintenant: Long): Long =
        if (existant > 0L) existant else maintenant

    /** Arrêt délibéré : défi résolu. Le drapeau et ses horodatages disparaissent. */
    fun marquerTerminee(context: Context) {
        prefs(context).edit()
            .putBoolean(CLE_EN_COURS, false)
            .remove(CLE_DERNIER_SIGNE)
            .remove(CLE_DECLENCHEMENT)
            .commit()
    }

    fun estEnCours(context: Context): Boolean {
        val prefs = prefs(context)
        return estEncoreFraiche(
            enCours = prefs.getBoolean(CLE_EN_COURS, false),
            dernierSigneMs = prefs.getLong(CLE_DERNIER_SIGNE, 0L),
            maintenantMs = System.currentTimeMillis(),
        )
    }
}
