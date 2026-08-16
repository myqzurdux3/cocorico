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

    /** Arrêt délibéré : défi résolu. Le drapeau et son horodatage disparaissent. */
    fun marquerTerminee(context: Context) {
        prefs(context).edit()
            .putBoolean(CLE_EN_COURS, false)
            .remove(CLE_DERNIER_SIGNE)
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
