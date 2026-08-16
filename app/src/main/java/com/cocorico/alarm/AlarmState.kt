package com.cocorico.alarm

import android.content.Context

/**
 * Mémorise si une alarme est en train de sonner. Survit au kill du processus et
 * au redémarrage : c'est ce qui permet à BootReceiver de relancer une alarme
 * interrompue par une extinction du téléphone.
 */
object AlarmState {

    private const val FICHIER = "cocorico_alarm_state"
    private const val CLE_EN_COURS = "alarme_en_cours"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    fun marquerDemarree(context: Context) {
        prefs(context).edit().putBoolean(CLE_EN_COURS, true).commit()
    }

    fun marquerTerminee(context: Context) {
        prefs(context).edit().putBoolean(CLE_EN_COURS, false).commit()
    }

    fun estEnCours(context: Context): Boolean =
        prefs(context).getBoolean(CLE_EN_COURS, false)
}
